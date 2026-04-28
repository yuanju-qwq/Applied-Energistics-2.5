/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.integration.modules.jei;

import static appeng.helpers.ItemStackHelper.stackToNBT;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.transfer.RecipeTransferErrorInternal;

import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerWirelessCraftingTerminal;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.integration.modules.gregtech.CircuitHelper;
import appeng.util.Platform;

class RecipeTransferHandler<T extends Container> implements IRecipeTransferHandler<T> {

    private final Class<T> containerClass;

    RecipeTransferHandler(Class<T> containerClass) {
        this.containerClass = containerClass;
    }

    @Override
    public Class<T> getContainerClass() {
        return this.containerClass;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(@Nonnull T container, IRecipeLayout recipeLayout,
            @Nonnull EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        final String recipeType = recipeLayout.getRecipeCategory().getUid();

        if (recipeType.equals(VanillaRecipeCategoryUid.INFORMATION)
                || recipeType.equals(VanillaRecipeCategoryUid.FUEL)) {
            return RecipeTransferErrorInternal.INSTANCE;
        }

        if (!doTransfer) {
            if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING) && (container instanceof ContainerCraftingTerm
                    || container instanceof ContainerWirelessCraftingTerminal)) {
                JEIMissingItem error = new JEIMissingItem(container, recipeLayout);
                if (error.errored())
                    return error;
            }
            return null;
        }

        if (container instanceof ContainerPatternEncoder) {
            try {
                if (!((ContainerPatternEncoder) container).isCraftingMode()) {
                    if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    }
                } else if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        Map<Integer, ? extends IGuiIngredient<ItemStack>> ingredients = recipeLayout.getItemStacks()
                .getGuiIngredients();

        final NBTTagCompound recipe = new NBTTagCompound();
        final NBTTagList outputs = new NBTTagList();

        // GT programmable circuit integration
        final boolean isProcessingPattern = container instanceof ContainerPatternEncoder
                && !((ContainerPatternEncoder) container).isCraftingMode();
        final CircuitHelper circuitHelper = CircuitHelper.getInstance();
        final boolean shouldApplyToolkitRules = isProcessingPattern
                && circuitHelper.hasToolkitInInventory(player)
                && circuitHelper.isProgrammableCircuitAvailable();
        boolean wrappedCircuitAdded = false;
        boolean hasProgrammableCircuitInput = false;

        int slotIndex = 0;
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ingredientEntry : ingredients.entrySet()) {
            IGuiIngredient<ItemStack> ingredient = ingredientEntry.getValue();
            if (!ingredient.isInput()) {
                ItemStack output = ingredient.getDisplayedIngredient();
                if (output != null) {
                    final NBTTagCompound tag = stackToNBT(output);
                    outputs.appendTag(tag);
                }
                continue;
            }

            for (final Slot slot : container.inventorySlots) {
                if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakeCraftingMatrix) {
                    if (slot.getSlotIndex() == slotIndex) {
                        final NBTTagList tags = new NBTTagList();
                        final List<ItemStack> list = new ArrayList<>();
                        final ItemStack displayed = ingredient.getDisplayedIngredient();

                        // prefer currently displayed item
                        if (displayed != null && !displayed.isEmpty()) {
                            list.add(displayed);
                        }

                        // prefer pure crystals.
                        for (ItemStack stack : ingredient.getAllIngredients()) {
                            if (stack == null) {
                                continue;
                            }
                            if (Platform.isRecipePrioritized(stack)) {
                                list.add(0, stack);
                            } else {
                                list.add(stack);
                            }
                        }

                        // GT circuit wrapping: check for programmable circuit input
                        if (shouldApplyToolkitRules && !list.isEmpty()) {
                            ItemStack firstItem = list.get(0);
                            if (circuitHelper.isProgrammableCircuit(firstItem)) {
                                hasProgrammableCircuitInput = true;
                            }
                            if (!wrappedCircuitAdded
                                    && !circuitHelper.isProgrammableCircuit(firstItem)
                                    && circuitHelper.isIntegratedCircuit(firstItem)) {
                                ItemStack wrapped = circuitHelper.wrapItemAsProgrammableStack(firstItem);
                                if (wrapped != null) {
                                    list.clear();
                                    list.add(wrapped);
                                    wrappedCircuitAdded = true;
                                    hasProgrammableCircuitInput = true;
                                }
                            }
                        }

                        for (final ItemStack is : list) {
                            final NBTTagCompound tag = stackToNBT(is);
                            tags.appendTag(tag);
                        }

                        recipe.setTag("#" + slot.getSlotIndex(), tags);
                        break;
                    }
                }
            }

            slotIndex++;
        }

        // GT circuit: if no circuit was wrapped and no programmable circuit found, add one to an empty slot
        if (shouldApplyToolkitRules && !wrappedCircuitAdded && !hasProgrammableCircuitInput) {
            ItemStack pcStack = circuitHelper.getProgrammableCircuitStack();
            if (pcStack != null) {
                int emptySlotIndex = findFirstEmptySlot(recipe, container);
                if (emptySlotIndex >= 0) {
                    final NBTTagList tags = new NBTTagList();
                    tags.appendTag(stackToNBT(pcStack));
                    recipe.setTag("#" + emptySlotIndex, tags);
                }
            }
        }

        recipe.setTag("outputs", outputs);

        try {
            NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
        } catch (IOException e) {
            AELog.debug(e);
        }

        return null;
    }

    /**
     * Find the first empty crafting/pattern slot index that has no recipe data assigned.
     */
    private static int findFirstEmptySlot(NBTTagCompound recipe, Container container) {
        for (final Slot slot : container.inventorySlots) {
            if (slot instanceof SlotCraftingMatrix || slot instanceof SlotFakeCraftingMatrix) {
                int idx = slot.getSlotIndex();
                if (!recipe.hasKey("#" + idx)) {
                    return idx;
                }
            }
        }
        return -1;
    }
}

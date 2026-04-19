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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.VanillaRecipeCategoryUid;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.transfer.RecipeTransferErrorInternal;

import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerWirelessCraftingTerminal;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketJEIRecipe;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import gregtech.api.recipes.ingredients.IntCircuitIngredient;
import gtqt.common.items.GTQTMetaItems;
import gtqt.common.items.behaviors.ProgrammableCircuit;

class RecipeTransferHandler<T extends Container> implements IRecipeTransferHandler<T> {

    private static final class LegacyTransferIngredient {

        private final int slotKey;
        private final int sourceOrder;
        private final boolean input;
        private final List<ItemStack> options;

        private LegacyTransferIngredient(int slotKey, int sourceOrder, boolean input, List<ItemStack> options) {
            this.slotKey = slotKey;
            this.sourceOrder = sourceOrder;
            this.input = input;
            this.options = options;
        }
    }

    private static final class PatternTransferIngredient {

        private final int slotKey;
        private final int sourceOrder;
        private final boolean input;
        private final boolean notConsumed;
        @Nullable
        private final IAEStack<?> stack;

        private PatternTransferIngredient(int slotKey, int sourceOrder, boolean input, boolean notConsumed,
                @Nullable IAEStack<?> stack) {
            this.slotKey = slotKey;
            this.sourceOrder = sourceOrder;
            this.input = input;
            this.notConsumed = notConsumed;
            this.stack = stack;
        }
    }

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

        // 二合一接口终端的合成模式切换
        if (container instanceof ContainerWirelessDualInterfaceTerminal dualContainer) {
            try {
                if (!dualContainer.isCraftingMode()) {
                    if (recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                        NetworkHandler.instance()
                                .sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                    }
                } else if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                    NetworkHandler.instance()
                            .sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        final List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> ingredients = new ArrayList<>(
                recipeLayout.getItemStacks().getGuiIngredients().entrySet());
        ingredients.sort(Comparator.comparingInt(Map.Entry::getKey));
        final List<Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>>> fluidIngredients = new ArrayList<>(
                recipeLayout.getFluidStacks().getGuiIngredients().entrySet());
        fluidIngredients.sort(Comparator.comparingInt(Map.Entry::getKey));

        if (container instanceof ContainerPatternEncoder patternContainer) {
            transferToPatternVirtualSlots(patternContainer, recipeLayout, ingredients, fluidIngredients, recipeType,
                    player);
        } else {
            final NBTTagCompound recipe = new NBTTagCompound();
            final NBTTagList outputs = new NBTTagList();
            final boolean preserveLayout = recipeType.equals(VanillaRecipeCategoryUid.CRAFTING);

            final List<LegacyTransferIngredient> transferIngredients = collectLegacyIngredients(ingredients, fluidIngredients);
            int slotIndex = 0;
            for (LegacyTransferIngredient ingredient : transferIngredients) {
                final boolean hasOptions = !ingredient.options.isEmpty();
                if (!preserveLayout && !hasOptions) {
                    continue;
                }

                if (!ingredient.input) {
                    if (hasOptions) {
                        final NBTTagCompound tag = stackToNBT(ingredient.options.get(0));
                        outputs.appendTag(tag);
                    }
                    continue;
                }

                final NBTTagList tags = new NBTTagList();
                for (final ItemStack is : ingredient.options) {
                    final NBTTagCompound tag = stackToNBT(is);
                    tags.appendTag(tag);
                }

                recipe.setTag("#" + slotIndex, tags);
                slotIndex++;
            }

            recipe.setTag("outputs", outputs);

            try {
                NetworkHandler.instance().sendToServer(new PacketJEIRecipe(recipe));
            } catch (IOException e) {
                AELog.debug(e);
            }
        }

        // 二合一接口终端：将配方类别名称（机器名）设置为 Names 搜索框的建议文本
        if (container instanceof ContainerWirelessDualInterfaceTerminal) {
            final net.minecraft.client.gui.GuiScreen currentScreen =
                    net.minecraft.client.Minecraft.getMinecraft().currentScreen;
            if (currentScreen instanceof appeng.client.gui.implementations.GuiWirelessDualInterfaceTerminal gui) {
                // 获取配方类别的本地化标题（例如："Crafting Table"、"卷板机"、"高炉" 等）
                final String categoryTitle = recipeLayout.getRecipeCategory().getTitle();
                if (categoryTitle != null && !categoryTitle.isEmpty()) {
                    gui.setSearchFieldSuggestion(categoryTitle);
                }
            }
        }

        return null;
    }

    private static void transferToPatternVirtualSlots(ContainerPatternEncoder container,
            IRecipeLayout recipeLayout,
            List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> itemIngredients,
            List<Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>>> fluidIngredients,
            String recipeType,
            EntityPlayer player) {
        final IAEStackInventory craftingInv = container.getCraftingAEInv();
        final IAEStackInventory outputInv = container.getOutputAEInv();
        if (craftingInv == null || outputInv == null) {
            return;
        }

        final Int2ObjectMap<IAEStack<?>> craftingSlots = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < craftingInv.getSizeInventory(); i++) {
            craftingSlots.put(i, null);
        }

        final Int2ObjectMap<IAEStack<?>> outputSlots = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < outputInv.getSizeInventory(); i++) {
            outputSlots.put(i, null);
        }

        final boolean preserveLayout = recipeType.equals(VanillaRecipeCategoryUid.CRAFTING);
        final boolean shouldApplyToolkitRules = hasToolkitInInventory(player) &&
                GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null;
        boolean wrappedCircuitAdded = false;
        boolean hasProgrammableCircuitInput = false;
        int craftingIndex = 0;
        int outputIndex = 0;
        final List<PatternTransferIngredient> ingredients = collectPatternIngredients(
                recipeLayout, itemIngredients, fluidIngredients);
        for (PatternTransferIngredient ingredient : ingredients) {
            if (!preserveLayout && ingredient.stack == null) {
                continue;
            }

            if (ingredient.input) {
                IAEStack<?> transferStack = ingredient.stack == null ? null : ingredient.stack.copy();
                final ItemStack itemStack = toItemStack(transferStack);
                if (itemStack != null && isProgrammableCircuit(itemStack)) {
                    hasProgrammableCircuitInput = true;
                }
                if (shouldApplyToolkitRules && !wrappedCircuitAdded && itemStack != null
                        && !isProgrammableCircuit(itemStack)
                        && (ingredient.notConsumed || IntCircuitIngredient.isIntegratedCircuit(itemStack))) {
                    transferStack = wrapItemAsProgrammable(itemStack);
                    wrappedCircuitAdded = transferStack != null;
                    if (wrappedCircuitAdded) {
                        hasProgrammableCircuitInput = true;
                    }
                }
                if (craftingIndex < craftingInv.getSizeInventory()) {
                    craftingSlots.put(craftingIndex, transferStack);
                }
                craftingIndex++;
            } else if (!recipeType.equals(VanillaRecipeCategoryUid.CRAFTING)) {
                if (outputIndex < outputInv.getSizeInventory()) {
                    outputSlots.put(outputIndex, ingredient.stack == null ? null : ingredient.stack.copy());
                }
                outputIndex++;
            }
        }

        if (shouldApplyToolkitRules && !wrappedCircuitAdded && !hasProgrammableCircuitInput) {
            final int firstEmptySlot = findFirstEmptyInputSlot(craftingSlots, craftingInv.getSizeInventory());
            if (firstEmptySlot >= 0) {
                craftingSlots.put(firstEmptySlot, toAEStack(GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1)));
            }
        }

        // Apply immediately on client so JEI transfer visually reflects programmable-circuit replacement.
        container.receiveSlotStacks(StorageName.CRAFTING_INPUT, craftingSlots);
        container.receiveSlotStacks(StorageName.CRAFTING_OUTPUT, outputSlots);

        NetworkHandler.instance().sendToServer(new PacketVirtualSlot(StorageName.CRAFTING_INPUT, craftingSlots));
        NetworkHandler.instance().sendToServer(new PacketVirtualSlot(StorageName.CRAFTING_OUTPUT, outputSlots));
    }

    @Nullable
    private static ItemStack toItemStack(@Nullable IAEStack<?> stack) {
        if (!(stack instanceof IAEItemStack)) {
            return null;
        }
        final ItemStack itemStack = ((IAEItemStack) stack).createItemStack();
        return itemStack.isEmpty() ? null : itemStack;
    }

    @Nullable
    private static IAEStack<?> wrapItemAsProgrammable(ItemStack sourceItem) {
        if (sourceItem.isEmpty()) {
            return null;
        }

        if (GTQTMetaItems.PROGRAMMABLE_CIRCUIT == null) {
            return toAEStack(sourceItem);
        }

        final ItemStack wrappedItem;
        if (IntCircuitIngredient.isIntegratedCircuit(sourceItem)) {
            final int config = IntCircuitIngredient.getCircuitConfiguration(sourceItem);
            wrappedItem = IntCircuitIngredient.getIntegratedCircuit(config);
        } else {
            wrappedItem = sourceItem.copy();
            wrappedItem.setCount(1);
        }

        final ItemStack programmable = GTQTMetaItems.PROGRAMMABLE_CIRCUIT.getStackForm(1);
        ProgrammableCircuit.wrap(wrappedItem, programmable);
        return toAEStack(programmable);
    }

    private static boolean isProgrammableCircuit(ItemStack stack) {
        return GTQTMetaItems.PROGRAMMABLE_CIRCUIT != null
                && !stack.isEmpty()
                && GTQTMetaItems.PROGRAMMABLE_CIRCUIT.isItemEqual(stack);
    }

    private static int findFirstEmptyInputSlot(Int2ObjectMap<IAEStack<?>> craftingSlots, int size) {
        for (int i = 0; i < size; i++) {
            if (craftingSlots.get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasToolkitInInventory(@Nullable EntityPlayer player) {
        if (player == null || GTQTMetaItems.PROGRAMMING_TOOLKIT == null) {
            return false;
        }

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            final ItemStack invStack = player.inventory.getStackInSlot(i);
            if (!invStack.isEmpty() && GTQTMetaItems.PROGRAMMING_TOOLKIT.isItemEqual(invStack)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static IAEStack<?> toAEStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return AEItemStack.fromItemStack(stack);
    }

    @Nullable
    private static IAEStack<?> toAEStack(@Nullable FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return null;
        }
        return AEFluidStack.fromFluidStack(stack.copy());
    }

    private static List<PatternTransferIngredient> collectPatternIngredients(
            IRecipeLayout recipeLayout,
            List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> itemIngredients,
            List<Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>>> fluidIngredients) {
        final List<PatternTransferIngredient> result = new ArrayList<>(itemIngredients.size() + fluidIngredients.size());

        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ingredientEntry : itemIngredients) {
            final IGuiIngredient<ItemStack> ingredient = ingredientEntry.getValue();
            final boolean notConsumed = ingredient.isInput() && isNotConsumedSlot(recipeLayout, ingredientEntry.getKey());
            result.add(new PatternTransferIngredient(
                    ingredientEntry.getKey(),
                    0,
                    ingredient.isInput(),
                    notConsumed,
                    toAEStack(getDisplayedOrFirst(ingredient))));
        }

        for (Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>> ingredientEntry : fluidIngredients) {
            final IGuiIngredient<FluidStack> ingredient = ingredientEntry.getValue();
            result.add(new PatternTransferIngredient(
                    ingredientEntry.getKey(),
                    1,
                    ingredient.isInput(),
                    false,
                    toAEStack(getDisplayedOrFirst(ingredient))));
        }

        result.sort(Comparator
                .comparingInt((PatternTransferIngredient ingredient) -> ingredient.slotKey)
                .thenComparingInt(ingredient -> ingredient.sourceOrder));
        return result;
    }

    private static List<LegacyTransferIngredient> collectLegacyIngredients(
            List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> itemIngredients,
            List<Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>>> fluidIngredients) {
        final List<LegacyTransferIngredient> result = new ArrayList<>(itemIngredients.size() + fluidIngredients.size());

        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> ingredientEntry : itemIngredients) {
            final IGuiIngredient<ItemStack> ingredient = ingredientEntry.getValue();
            result.add(new LegacyTransferIngredient(
                    ingredientEntry.getKey(),
                    0,
                    ingredient.isInput(),
                    collectItemOptions(ingredient)));
        }

        for (Map.Entry<Integer, ? extends IGuiIngredient<FluidStack>> ingredientEntry : fluidIngredients) {
            final IGuiIngredient<FluidStack> ingredient = ingredientEntry.getValue();
            final List<ItemStack> options = new ArrayList<>(1);
            final ItemStack fluidDrop = toLegacyTransferItem(getDisplayedOrFirst(ingredient));
            if (!fluidDrop.isEmpty()) {
                options.add(fluidDrop);
            }
            result.add(new LegacyTransferIngredient(
                    ingredientEntry.getKey(),
                    1,
                    ingredient.isInput(),
                    options));
        }

        result.sort(Comparator
                .comparingInt((LegacyTransferIngredient ingredient) -> ingredient.slotKey)
                .thenComparingInt(ingredient -> ingredient.sourceOrder));
        return result;
    }

    private static List<ItemStack> collectItemOptions(IGuiIngredient<ItemStack> ingredient) {
        final List<ItemStack> result = new ArrayList<>();
        final ItemStack displayed = ingredient.getDisplayedIngredient();
        if (displayed != null && !displayed.isEmpty()) {
            result.add(displayed.copy());
        }

        for (ItemStack stack : ingredient.getAllIngredients()) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            final ItemStack copy = stack.copy();
            if (Platform.isRecipePrioritized(copy)) {
                result.add(0, copy);
            } else {
                result.add(copy);
            }
        }

        return result;
    }

    @Nullable
    private static ItemStack toLegacyTransferItem(@Nullable FluidStack stack) {
        if (stack == null || stack.amount <= 0) {
            return ItemStack.EMPTY;
        }
        return ItemFluidDrop.newStack(stack.copy());
    }

    @Nullable
    private static <V> V getDisplayedOrFirst(IGuiIngredient<V> ingredient) {
        final V displayed = ingredient.getDisplayedIngredient();
        if (displayed != null) {
            return displayed;
        }
        final List<V> all = ingredient.getAllIngredients();
        return all.isEmpty() ? null : all.get(0);
    }

    private static boolean isNotConsumedSlot(@Nullable IRecipeLayout recipeLayout, int slotIndex) {
        if (recipeLayout == null) {
            return false;
        }

        try {
            final Field wrapperField = recipeLayout.getClass().getDeclaredField("recipeWrapper");
            wrapperField.setAccessible(true);
            final Object recipeWrapper = wrapperField.get(recipeLayout);
            if (recipeWrapper == null) {
                return false;
            }

            final Method isNotConsumedItem = recipeWrapper.getClass().getMethod("isNotConsumedItem", int.class);
            final Object result = isNotConsumedItem.invoke(recipeWrapper, slotIndex);
            return result instanceof Boolean && (Boolean) result;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}

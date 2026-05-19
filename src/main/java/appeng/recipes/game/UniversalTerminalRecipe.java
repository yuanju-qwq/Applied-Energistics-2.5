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

package appeng.recipes.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.items.tools.powered.ToolWirelessUniversalTerminal;
import appeng.items.tools.powered.WirelessTerminalMode;
import appeng.util.Platform;

/**
 * Universal wireless terminal crafting recipe.
 * <p>
 * Supports two crafting modes:
 * <ul>
 *   <li>New creation: combine multiple wireless terminals in the crafting table
 *       to produce a universal terminal preloaded with the corresponding modes</li>
 *   <li>Append: combine one universal terminal + other wireless terminals
 *       to append modes to the existing universal terminal</li>
 * </ul>
 * At least 2 different terminals are required (or 1 universal terminal + 1 new terminal).
 * The output universal terminal retains the highest power level among input terminals.
 */
public final class UniversalTerminalRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe>
        implements IRecipe {

    /** Wireless terminal IItemDefinition to WirelessTerminalMode mapping */
    private final Map<IItemDefinition, WirelessTerminalMode> terminalModeMap = new HashMap<>();

    public UniversalTerminalRecipe() {
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessTerminal(),
                WirelessTerminalMode.TERMINAL);
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessCraftingTerminal(),
                WirelessTerminalMode.CRAFTING);
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessFluidTerminal(),
                WirelessTerminalMode.FLUID);
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessPatternTerminal(),
                WirelessTerminalMode.PATTERN);
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessInterfaceTerminal(),
                WirelessTerminalMode.INTERFACE);
        terminalModeMap.put(AEApi.instance().definitions().items().wirelessDualInterfaceTerminal(),
                WirelessTerminalMode.DUAL_INTERFACE);
    }

    @Override
    public boolean matches(@Nonnull final InventoryCrafting inv, @Nonnull final World world) {
        return !getCraftingResult(inv).isEmpty();
    }

    @Nonnull
    @Override
    public ItemStack getCraftingResult(@Nonnull final InventoryCrafting inv) {
        List<WirelessTerminalMode> modes = new ArrayList<>();
        ItemStack existingUniversal = ItemStack.EMPTY;
        double maxPower = 0;
        int itemCount = 0;

        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            itemCount++;

            // Check if it is a universal terminal
            if (AEApi.instance().definitions().items().wirelessUniversalTerminal().isSameAs(stack)) {
                if (!existingUniversal.isEmpty()) {
                    // Two universal terminals are not allowed
                    return ItemStack.EMPTY;
                }
                existingUniversal = stack;
                // Inherit existing modes
                int[] existingModes = ToolWirelessUniversalTerminal.getInstalledModes(stack);
                for (int m : existingModes) {
                    WirelessTerminalMode mode = WirelessTerminalMode.fromId((byte) m);
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                }
                // Track power level
                NBTTagCompound tag = stack.getTagCompound();
                if (tag != null && tag.hasKey("internalCurrentPower")) {
                    maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                }
                continue;
            }

            // Check if it is a known wireless terminal
            boolean matched = false;
            for (Map.Entry<IItemDefinition, WirelessTerminalMode> entry : terminalModeMap.entrySet()) {
                if (entry.getKey().isSameAs(stack)) {
                    WirelessTerminalMode mode = entry.getValue();
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                    // Track power level
                    NBTTagCompound tag = stack.getTagCompound();
                    if (tag != null && tag.hasKey("internalCurrentPower")) {
                        maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                    }
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                // Unknown item, recipe does not match
                return ItemStack.EMPTY;
            }
        }

        // At least 2 items with at least 2 modes (new), or 1 universal + 1 new terminal (append)
        if (itemCount < 2 || modes.size() < 2) {
            return ItemStack.EMPTY;
        }

        // Build output
        ItemStack result = AEApi.instance().definitions().items().wirelessUniversalTerminal()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(result);

        // Write mode list
        int[] modeIds = new int[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            modeIds[i] = modes.get(i).getId();
        }
        tag.setIntArray("modes", modeIds);

        // Default mode is the first one
        tag.setByte("mode", modes.get(0).getId());

        // Preserve highest power level
        if (maxPower > 0) {
            tag.setDouble("internalCurrentPower", maxPower);
        }

        return result;
    }

    @Nonnull
    @Override
    public ItemStack getRecipeOutput() {
        return AEApi.instance().definitions().items().wirelessUniversalTerminal()
                .maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public boolean canFit(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }
}

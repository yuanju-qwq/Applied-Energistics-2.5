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
 * 通用无线终端合成配方。
 * <p>
 * 支持两种合成模式：
 * <ul>
 *   <li>新建合成：将多种无线终端放在工作台中，合成出一个预装对应模式的通用终端</li>
 *   <li>追加合成：将一个通用终端 + 其他无线终端放在一起，追加模式到已有通用终端</li>
 * </ul>
 * 至少需要提供 2 种不同的终端（或 1 个通用终端 + 1 种新终端）。
 * 输出的通用终端将保留输入通用终端或无线终端中最高的电量。
 */
public final class UniversalTerminalRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe>
        implements IRecipe {

    /** 无线终端 IItemDefinition → WirelessTerminalMode 的映射 */
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

            // 检查是否是通用终端
            if (AEApi.instance().definitions().items().wirelessUniversalTerminal().isSameAs(stack)) {
                if (!existingUniversal.isEmpty()) {
                    // 不允许两个通用终端
                    return ItemStack.EMPTY;
                }
                existingUniversal = stack;
                // 继承已有模式
                int[] existingModes = ToolWirelessUniversalTerminal.getInstalledModes(stack);
                for (int m : existingModes) {
                    WirelessTerminalMode mode = WirelessTerminalMode.fromId((byte) m);
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                }
                // 记录电量
                NBTTagCompound tag = stack.getTagCompound();
                if (tag != null && tag.hasKey("internalCurrentPower")) {
                    maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                }
                continue;
            }

            // 检查是否是已知的无线终端
            boolean matched = false;
            for (Map.Entry<IItemDefinition, WirelessTerminalMode> entry : terminalModeMap.entrySet()) {
                if (entry.getKey().isSameAs(stack)) {
                    WirelessTerminalMode mode = entry.getValue();
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                    // 记录电量
                    NBTTagCompound tag = stack.getTagCompound();
                    if (tag != null && tag.hasKey("internalCurrentPower")) {
                        maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                    }
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                // 未知物品，配方不匹配
                return ItemStack.EMPTY;
            }
        }

        // 至少需要2个物品且至少2种模式（新建时），或1个通用终端+至少1个新终端（追加时）
        if (itemCount < 2 || modes.size() < 2) {
            return ItemStack.EMPTY;
        }

        // 构建输出
        ItemStack result = AEApi.instance().definitions().items().wirelessUniversalTerminal()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NBTTagCompound tag = Platform.openNbtData(result);

        // 写入模式列表
        int[] modeIds = new int[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            modeIds[i] = modes.get(i).getId();
        }
        tag.setIntArray("modes", modeIds);

        // 默认模式为第一个
        tag.setByte("mode", modes.get(0).getId());

        // 保留最高电量
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

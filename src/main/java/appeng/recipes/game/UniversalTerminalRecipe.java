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
 * 閫氱敤鏃犵嚎缁堢鍚堟垚閰嶆柟銆?
 * <p>
 * 鏀寔涓ょ鍚堟垚妯″紡锛?
 * <ul>
 *   <li>鏂板缓鍚堟垚锛氬皢澶氱鏃犵嚎缁堢鏀惧湪宸ヤ綔鍙颁腑锛屽悎鎴愬嚭涓€涓瑁呭搴旀ā寮忕殑閫氱敤缁堢</li>
 *   <li>杩藉姞鍚堟垚锛氬皢涓€涓€氱敤缁堢 + 鍏朵粬鏃犵嚎缁堢鏀惧湪涓€璧凤紝杩藉姞妯″紡鍒板凡鏈夐€氱敤缁堢</li>
 * </ul>
 * 鑷冲皯闇€瑕佹彁渚?2 绉嶄笉鍚岀殑缁堢锛堟垨 1 涓€氱敤缁堢 + 1 绉嶆柊缁堢锛夈€?
 * 杈撳嚭鐨勯€氱敤缁堢灏嗕繚鐣欒緭鍏ラ€氱敤缁堢鎴栨棤绾跨粓绔腑鏈€楂樼殑鐢甸噺銆?
 */
public final class UniversalTerminalRecipe extends net.minecraftforge.registries.IForgeRegistryEntry.Impl<IRecipe>
        implements IRecipe {

    /** 鏃犵嚎缁堢 IItemDefinition 鈫?WirelessTerminalMode 鐨勬槧灏?*/
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

            // 妫€鏌ユ槸鍚︽槸閫氱敤缁堢
            if (AEApi.instance().definitions().items().wirelessUniversalTerminal().isSameAs(stack)) {
                if (!existingUniversal.isEmpty()) {
                    // 涓嶅厑璁镐袱涓€氱敤缁堢
                    return ItemStack.EMPTY;
                }
                existingUniversal = stack;
                // 缁ф壙宸叉湁妯″紡
                int[] existingModes = ToolWirelessUniversalTerminal.getInstalledModes(stack);
                for (int m : existingModes) {
                    WirelessTerminalMode mode = WirelessTerminalMode.fromId((byte) m);
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                }
                // 璁板綍鐢甸噺
                NBTTagCompound tag = stack.getTagCompound();
                if (tag != null && tag.hasKey("internalCurrentPower")) {
                    maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                }
                continue;
            }

            // 妫€鏌ユ槸鍚︽槸宸茬煡鐨勬棤绾跨粓绔?
            boolean matched = false;
            for (Map.Entry<IItemDefinition, WirelessTerminalMode> entry : terminalModeMap.entrySet()) {
                if (entry.getKey().isSameAs(stack)) {
                    WirelessTerminalMode mode = entry.getValue();
                    if (!modes.contains(mode)) {
                        modes.add(mode);
                    }
                    // 璁板綍鐢甸噺
                    NBTTagCompound tag = stack.getTagCompound();
                    if (tag != null && tag.hasKey("internalCurrentPower")) {
                        maxPower = Math.max(maxPower, tag.getDouble("internalCurrentPower"));
                    }
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                // 鏈煡鐗╁搧锛岄厤鏂逛笉鍖归厤
                return ItemStack.EMPTY;
            }
        }

        // 鑷冲皯闇€瑕?涓墿鍝佷笖鑷冲皯2绉嶆ā寮忥紙鏂板缓鏃讹級锛屾垨1涓€氱敤缁堢+鑷冲皯1涓柊缁堢锛堣拷鍔犳椂锛?
        if (itemCount < 2 || modes.size() < 2) {
            return ItemStack.EMPTY;
        }

        // 鏋勫缓杈撳嚭
        ItemStack result = AEApi.instance().definitions().items().wirelessUniversalTerminal()
                .maybeStack(1).orElse(ItemStack.EMPTY);
        if (result.isEmpty()) {
            return ItemStack.EMPTY;
        }

        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(result);

        // 鍐欏叆妯″紡鍒楄〃
        int[] modeIds = new int[modes.size()];
        for (int i = 0; i < modes.size(); i++) {
            modeIds[i] = modes.get(i).getId();
        }
        tag.setIntArray("modes", modeIds);

        // 榛樿妯″紡涓虹涓€涓?
        tag.setByte("mode", modes.get(0).getId());

        // 淇濈暀鏈€楂樼數閲?
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

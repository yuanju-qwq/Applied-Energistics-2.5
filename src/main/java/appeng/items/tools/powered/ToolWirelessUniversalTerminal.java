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

package appeng.items.tools.powered;

import java.util.List;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

/**
 * 通用无线终端物品。
 * 将所有无线终端（合成终端、样板终端、流体终端、接口终端、双接口终端）合并为一个物品。
 * 通过 NBT 中的 "mode" 字段（{@link WirelessTerminalMode}）切换当前终端类型。
 * 通过 NBT 中的 "modes" 字段（int[]）记录已安装的终端模式列表。
 */
public class ToolWirelessUniversalTerminal extends ToolWirelessTerminal {

    public ToolWirelessUniversalTerminal() {
        super();
    }

    // ========== 模式管理 ==========

    /**
     * 获取物品当前的终端模式。
     */
    public static WirelessTerminalMode getMode(ItemStack stack) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        return WirelessTerminalMode.fromId(tag.getByte("mode"));
    }

    /**
     * 设置物品当前的终端模式。
     */
    public static void setMode(ItemStack stack, WirelessTerminalMode mode) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setByte("mode", mode.getId());
    }

    /**
     * 获取物品已安装的所有终端模式 ID 列表。
     */
    public static int[] getInstalledModes(ItemStack stack) {
        NBTTagCompound tag = Platform.openNbtData(stack);
        if (tag.hasKey("modes")) {
            return tag.getIntArray("modes");
        }
        return new int[0];
    }

    /**
     * 检查物品是否已安装指定的终端模式。
     */
    public static boolean hasMode(ItemStack stack, WirelessTerminalMode mode) {
        int[] modes = getInstalledModes(stack);
        for (int m : modes) {
            if (m == mode.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加一个终端模式到物品中（如果尚未安装）。
     */
    public static void addMode(ItemStack stack, WirelessTerminalMode mode) {
        if (hasMode(stack, mode)) {
            return;
        }
        int[] oldModes = getInstalledModes(stack);
        int[] newModes = new int[oldModes.length + 1];
        System.arraycopy(oldModes, 0, newModes, 0, oldModes.length);
        newModes[oldModes.length] = mode.getId();
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setIntArray("modes", newModes);
    }

    /**
     * 切换到下一个已安装的终端模式（循环切换）。
     */
    public static void cycleMode(ItemStack stack) {
        int[] modes = getInstalledModes(stack);
        if (modes.length == 0) {
            return;
        }
        WirelessTerminalMode current = getMode(stack);
        int currentIndex = -1;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] == current.getId()) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % modes.length;
        setMode(stack, WirelessTerminalMode.fromId((byte) modes[nextIndex]));
    }

    // ========== IWirelessTermHandler 实现 ==========

    @Override
    public boolean canHandle(final ItemStack is) {
        return AEApi.instance().definitions().items().wirelessUniversalTerminal().isSameAs(is);
    }

    @Override
    public IGuiHandler getGuiHandler(ItemStack is) {
        WirelessTerminalMode mode = getMode(is);
        return mode.getGuiBridge();
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(final World w, final EntityPlayer player, final EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        int[] modes = getInstalledModes(stack);
        if (modes.length == 0) {
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }

        // 如果当前模式无效（如尚未安装），切换到第一个已安装模式
        WirelessTerminalMode current = getMode(stack);
        if (!hasMode(stack, current)) {
            setMode(stack, WirelessTerminalMode.fromId((byte) modes[0]));
        }

        AEApi.instance().registries().wireless().openWirelessTerminalGui(stack, w, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    // ========== 显示相关 ==========

    @SideOnly(Side.CLIENT)
    @Override
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
            final ITooltipFlag advancedTooltips) {
        super.addCheckedInformation(stack, world, lines, advancedTooltips);

        WirelessTerminalMode mode = getMode(stack);
        lines.add(TextFormatting.AQUA + GuiText.CurrentMode.getLocal() + ": " + TextFormatting.WHITE
                + mode.getName());

        int[] modes = getInstalledModes(stack);
        if (modes.length > 0) {
            lines.add(TextFormatting.GRAY + GuiText.InstalledModes.getLocal() + ":");
            for (int m : modes) {
                WirelessTerminalMode installed = WirelessTerminalMode.fromId((byte) m);
                String prefix = (installed == mode) ? TextFormatting.GREEN + " > " : TextFormatting.GRAY + "   ";
                lines.add(prefix + installed.getName());
            }
        }
    }

    @Override
    protected void getCheckedSubItems(CreativeTabs creativeTab, NonNullList<ItemStack> itemStacks) {
        // 创造模式下提供一个满电、安装所有模式的版本
        ItemStack charged = new ItemStack(this, 1);
        NBTTagCompound tag = Platform.openNbtData(charged);
        tag.setDouble("internalCurrentPower", this.getAEMaxPower(charged));
        tag.setDouble("internalMaxPower", this.getAEMaxPower(charged));

        // 安装所有模式
        WirelessTerminalMode[] allModes = WirelessTerminalMode.values();
        int[] modeIds = new int[allModes.length];
        for (int i = 0; i < allModes.length; i++) {
            modeIds[i] = allModes[i].getId();
        }
        tag.setIntArray("modes", modeIds);
        tag.setByte("mode", WirelessTerminalMode.CRAFTING.getId());

        itemStacks.add(charged);
    }
}

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
 * 閫氱敤鏃犵嚎缁堢鐗╁搧銆?
 * 灏嗘墍鏈夋棤绾跨粓绔紙鍚堟垚缁堢銆佹牱鏉跨粓绔€佹祦浣撶粓绔€佹帴鍙ｇ粓绔€佸弻鎺ュ彛缁堢锛夊悎骞朵负涓€涓墿鍝併€?
 * 閫氳繃 NBT 涓殑 "mode" 瀛楁锛坽@link WirelessTerminalMode}锛夊垏鎹㈠綋鍓嶇粓绔被鍨嬨€?
 * 閫氳繃 NBT 涓殑 "modes" 瀛楁锛坕nt[]锛夎褰曞凡瀹夎鐨勭粓绔ā寮忓垪琛ㄣ€?
 */
public class ToolWirelessUniversalTerminal extends ToolWirelessTerminal {

    public ToolWirelessUniversalTerminal() {
        super();
    }

    // ========== 妯″紡绠＄悊 ==========

    /**
     * 鑾峰彇鐗╁搧褰撳墠鐨勭粓绔ā寮忋€?
     */
    public static WirelessTerminalMode getMode(ItemStack stack) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        return WirelessTerminalMode.fromId(tag.getByte("mode"));
    }

    /**
     * 璁剧疆鐗╁搧褰撳墠鐨勭粓绔ā寮忋€?
     */
    public static void setMode(ItemStack stack, WirelessTerminalMode mode) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        tag.setByte("mode", mode.getId());
    }

    /**
     * 鑾峰彇鐗╁搧宸插畨瑁呯殑鎵€鏈夌粓绔ā寮?ID 鍒楄〃銆?
     */
    public static int[] getInstalledModes(ItemStack stack) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        if (tag.hasKey("modes")) {
            return tag.getIntArray("modes");
        }
        return new int[0];
    }

    /**
     * 妫€鏌ョ墿鍝佹槸鍚﹀凡瀹夎鎸囧畾鐨勭粓绔ā寮忋€?
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
     * 娣诲姞涓€涓粓绔ā寮忓埌鐗╁搧涓紙濡傛灉灏氭湭瀹夎锛夈€?
     */
    public static void addMode(ItemStack stack, WirelessTerminalMode mode) {
        if (hasMode(stack, mode)) {
            return;
        }
        int[] oldModes = getInstalledModes(stack);
        int[] newModes = new int[oldModes.length + 1];
        System.arraycopy(oldModes, 0, newModes, 0, oldModes.length);
        newModes[oldModes.length] = mode.getId();
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        tag.setIntArray("modes", newModes);
    }

    /**
     * 鍒囨崲鍒颁笅涓€涓凡瀹夎鐨勭粓绔ā寮忥紙寰幆鍒囨崲锛夈€?
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

    // ========== IWirelessTermHandler 瀹炵幇 ==========

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

        // 濡傛灉褰撳墠妯″紡鏃犳晥锛堝灏氭湭瀹夎锛夛紝鍒囨崲鍒扮涓€涓凡瀹夎妯″紡
        WirelessTerminalMode current = getMode(stack);
        if (!hasMode(stack, current)) {
            setMode(stack, WirelessTerminalMode.fromId((byte) modes[0]));
        }

        AEApi.instance().registries().wireless().openWirelessTerminalGui(stack, w, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    // ========== 鏄剧ず鐩稿叧 ==========

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
        // 鍒涢€犳ā寮忎笅鎻愪緵涓€涓弧鐢点€佸畨瑁呮墍鏈夋ā寮忕殑鐗堟湰
        ItemStack charged = new ItemStack(this, 1);
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(charged);
        tag.setDouble("internalCurrentPower", this.getAEMaxPower(charged));
        tag.setDouble("internalMaxPower", this.getAEMaxPower(charged));

        // 瀹夎鎵€鏈夋ā寮?
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

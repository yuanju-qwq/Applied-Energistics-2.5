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
 * Universal wireless terminal item.
 * Merges all wireless terminals (crafting, pattern, fluid, interface, dual interface) into a single item.
 * Switches the current terminal type via the "mode" field in NBT ({@link WirelessTerminalMode}).
 * Records installed terminal mode list via the "modes" field in NBT (int[]).
 */
public class ToolWirelessUniversalTerminal extends ToolWirelessTerminal {

    public ToolWirelessUniversalTerminal() {
        super();
    }

    // ========== Mode management ==========

    /**
     * Get the current terminal mode of the item.
     */
    public static WirelessTerminalMode getMode(ItemStack stack) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        return WirelessTerminalMode.fromId(tag.getByte("mode"));
    }

    /**
     * Set the current terminal mode of the item.
     */
    public static void setMode(ItemStack stack, WirelessTerminalMode mode) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        tag.setByte("mode", mode.getId());
    }

    /**
     * Get the list of all installed terminal mode IDs on the item.
     */
    public static int[] getInstalledModes(ItemStack stack) {
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(stack);
        if (tag.hasKey("modes")) {
            return tag.getIntArray("modes");
        }
        return new int[0];
    }

    /**
     * Check whether the item has the specified terminal mode installed.
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
     * Add a terminal mode to the item (if not already installed).
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
     * Switch to the next installed terminal mode (cyclic switching).
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

    // ========== IWirelessTermHandler implementation ==========

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

        // If current mode is invalid (e.g. not yet installed), switch to the first installed mode
        WirelessTerminalMode current = getMode(stack);
        if (!hasMode(stack, current)) {
            setMode(stack, WirelessTerminalMode.fromId((byte) modes[0]));
        }

        AEApi.instance().registries().wireless().openWirelessTerminalGui(stack, w, player);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    // ========== Display related ==========

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
        // Provide a fully charged version with all modes installed in creative mode
        ItemStack charged = new ItemStack(this, 1);
        NBTTagCompound tag = appeng.util.ItemStackNbtHelper.openNbtData(charged);
        tag.setDouble("internalCurrentPower", this.getAEMaxPower(charged));
        tag.setDouble("internalMaxPower", this.getAEMaxPower(charged));

        // Install all modes
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

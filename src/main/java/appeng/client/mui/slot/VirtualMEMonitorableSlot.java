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

package appeng.client.mui.slot;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo;
import appeng.client.me.ItemRepo.RepoEntry;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

/**
 * Virtual slot for ME Monitorable terminals.
 * <p>
 * Reads display data from {@link ItemRepo} via {@link RepoEntry} (AEKey-based).
 * Click handling uses AEKey to determine the interaction type (item vs fluid)
 * and constructs the appropriate {@link InventoryAction}.
 */
public class VirtualMEMonitorableSlot extends VirtualMESlot {

    private final ItemRepo repo;

    public VirtualMEMonitorableSlot(int id, int x, int y, ItemRepo repo, int slotIndex) {
        super(id, x, y, slotIndex);
        this.repo = repo;
        this.showAmountAlways = true;
        this.showCraftableText = true;
        this.showCraftableIcon = true;
    }

    /**
     * Returns the RepoEntry directly from ItemRepo (no IAEStack intermediate).
     */
    @Override
    @Nullable
    public RepoEntry getRepoEntry() {
        return this.repo.getEntry(this.slotIndex);
    }

    @Override
    public boolean isVisible() {
        return this.repo.hasPower();
    }

    @Override
    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
        final EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        final RepoEntry entry = this.getRepoEntry();
        final AEKey what = entry != null ? entry.what() : null;
        final long amount = entry != null ? entry.amount() : 0;
        final boolean craftable = entry != null && entry.craftable();

        final ItemStack heldStack = player.inventory.getItemStack();
        final boolean hasItemInHand = !heldStack.isEmpty();
        final boolean hasFluidInHand = this.hasFluidInHand(heldStack);

        if (what == null && !hasItemInHand) {
            return;
        }

        InventoryAction action = null;

        // Determine interaction type using AEKey instead of IAEStack instanceof
        final boolean isFluid = what instanceof AEFluidKey;
        final boolean isItem = what instanceof AEItemKey;

        if (isFluid) {
            action = mouseButton == 1 || hasFluidInHand ? InventoryAction.EMPTY_ITEM : InventoryAction.FILL_ITEM;
        } else if (what == null && hasFluidInHand) {
            action = InventoryAction.EMPTY_ITEM;
        } else if (hasItemInHand) {
            action = mouseButton == 1 ? InventoryAction.SPLIT_OR_PLACE_SINGLE : InventoryAction.PICKUP_OR_SET_DOWN;
        } else if (GuiScreen.isShiftKeyDown()) {
            action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
        } else if (mouseButton == 1) {
            action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
        } else {
            action = InventoryAction.PICKUP_OR_SET_DOWN;

            // Auto-craft: if amount is 0 or Alt is held and no item in hand
            if (isItem && (amount == 0 || GuiScreen.isAltKeyDown()) && !hasItemInHand) {
                action = InventoryAction.AUTO_CRAFT;
            }
        }

        if (GuiScreen.isCtrlKeyDown() && mouseButton == 2) {
            if (craftable) {
                action = InventoryAction.AUTO_CRAFT;
            } else if (player.capabilities.isCreativeMode) {
                action = InventoryAction.CREATIVE_DUPLICATE;
            }
        }

        if (action != null) {
            if (player.openContainer instanceof AEBaseContainer container) {
                // Convert AEKey to IAEStack for network packet compatibility
                IAEStack<?> aeStack = entry != null ? entry.toIAEStack() : null;
                container.setTargetStack(aeStack);
                final int inventorySize = container.inventorySlots.size();
                final PacketInventoryAction p = new PacketInventoryAction(action, inventorySize, -1);
                NetworkHandler.instance().sendToServer(p);
            }
        }
    }

    private boolean hasFluidInHand(final ItemStack heldStack) {
        if (heldStack.isEmpty()) {
            return false;
        }

        final ItemStack singleStack = heldStack.copy();
        singleStack.setCount(1);
        return FluidUtil.getFluidContained(singleStack) != null;
    }
}

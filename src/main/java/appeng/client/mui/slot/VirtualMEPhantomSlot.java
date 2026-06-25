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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.client.me.ItemRepo.RepoEntry;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.mui.legacy.LegacyStackBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Virtual ME phantom slot for displaying and interacting with generic {@link IAEStack}
 * (items, fluids, etc.) in GUIs.
 * <p>
 * Unlike a normal phantom slot, this slot does not depend on Minecraft's
 * {@link net.minecraft.inventory.Slot}, but directly operates on
 * {@link IAEStackInventory}. User click actions are synchronized to the server
 * via {@link PacketVirtualSlot}.
 */
public class VirtualMEPhantomSlot extends VirtualMESlot {

    /**
     * Type acceptance predicate: determines whether this slot accepts a given key type.
     */
    @FunctionalInterface
    public interface KeyTypeAcceptPredicate {

        boolean test(VirtualMEPhantomSlot slot, AEKeyType type, int mouseButton);
    }

    private final IAEStackInventory inventory;
    private final KeyTypeAcceptPredicate acceptType;
    private boolean hidden = false;

    public VirtualMEPhantomSlot(int id, int x, int y, IAEStackInventory inventory, int slotIndex,
            KeyTypeAcceptPredicate acceptType) {
        super(id, x, y, slotIndex);
        this.inventory = inventory;
        this.showAmount = false;
        this.acceptType = acceptType;
    }

    @Nullable
    @Override
    public RepoEntry getRepoEntry() {
        GenericStack gs = this.inventory.getGenericStack(this.getSlotIndex());
        if (gs != null) {
            return new RepoEntry(gs.what(), gs.amount(), false);
        }
        return null;
    }

    public StorageName getStorageName() {
        return this.inventory.getStorageName();
    }

    @Override
    public boolean isSlotEnabled() {
        return !this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * Called by {@link appeng.client.mui.AEBasePanel}'s mouseClicked, bridges to {@link #handleMouseClicked}.
     */
    @Override
    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
        this.handleMouseClicked(clickStack, false, mouseButton);
    }

    /**
     * Handle mouse click events.
     * <p>
     * Operates entirely on AEKey-based {@link GenericStack}; legacy {@link IAEStack} is
     * only touched at the {@link AEKeyType} API boundary and immediately bridged via
     * {@link LegacyStackBridge#toGenericStack(IAEStack)}.
     *
     * @param itemStack     the player's held item stack (client-side)
     * @param isExtraAction whether this is an extended action (e.g. holding a special key)
     * @param mouseButton   the mouse button (0=left, 1=right)
     */
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        GenericStack currentStack = this.getGenericStack();
        final ItemStack hand = itemStack != null ? itemStack.copy() : null;

        if (hand != null && !this.showAmount) {
            hand.setCount(1);
        }

        // Collect all accepted key types for this slot
        final List<AEKeyType> acceptTypes = new ArrayList<>();
        for (AEKeyType type : AEKeyType.getAllTypes()) {
            if (this.acceptType.test(this, type, mouseButton)) {
                acceptTypes.add(type);
            }
        }

        // First try to convert held item to a non-item type (e.g. fluid container → fluid stack).
        // AEKeyType.convertStackFromItem still returns IAEStack, so bridge immediately.
        if (hand != null) {
            for (AEKeyType type : acceptTypes) {
                GenericStack converted = LegacyStackBridge.toGenericStack(type.convertStackFromItem(hand));
                if (converted != null) {
                    currentStack = converted;
                    acceptTypes.clear();
                    isExtraAction = false;
                    break;
                }
            }
        }

        final boolean acceptItem = acceptTypes.contains(AEKeyType.items());
        boolean acceptExtra = false;
        for (AEKeyType type : acceptTypes) {
            if (type != AEKeyType.items()) {
                acceptExtra = true;
                break;
            }
        }

        switch (mouseButton) {
            case 0: { // Left click
                if (hand != null) {
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        // Try to extract non-item stacks from container items first
                        for (AEKeyType type : acceptTypes) {
                            GenericStack stackFromContainer = LegacyStackBridge
                                    .toGenericStack(type.getStackFromContainerItem(hand));
                            if (stackFromContainer != null) {
                                currentStack = stackFromContainer;
                                break;
                            }
                        }
                    } else if (acceptItem) {
                        currentStack = GenericStack.fromItemStack(hand);
                    }
                } else {
                    currentStack = null;
                }
                break;
            }
            case 1: { // Right click
                if (hand != null) {
                    hand.setCount(1);

                    GenericStack stackFromContainer = null;
                    for (AEKeyType type : acceptTypes) {
                        stackFromContainer = LegacyStackBridge.toGenericStack(type.getStackFromContainerItem(hand));
                        if (stackFromContainer != null) {
                            break;
                        }
                    }

                    GenericStack stackForHand = null;
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        if (stackFromContainer != null) {
                            stackForHand = stackFromContainer;
                        }
                    } else if (acceptItem) {
                        stackForHand = GenericStack.fromItemStack(hand);
                    }

                    // Increment amount when clicking the same key that is already in the slot
                    if (stackForHand != null && this.showAmount
                            && acceptTypes.contains(stackForHand.what().getType())
                            && currentStack != null
                            && stackForHand.what().equals(currentStack.what())) {
                        currentStack = new GenericStack(currentStack.what(), currentStack.amount() + 1);
                    } else {
                        currentStack = stackForHand;
                    }
                } else if (currentStack != null) {
                    // Decrement amount; clear slot when it reaches zero
                    long newAmount = currentStack.amount() - 1;
                    currentStack = newAmount > 0 ? new GenericStack(currentStack.what(), newAmount) : null;
                }
                break;
            }
        }

        // Set immediately on client to avoid delay during slow network
        inventory.setGenericStack(this.getSlotIndex(), currentStack);

        // Send to server
        NetworkHandler.instance()
                .sendToServer(new PacketVirtualSlot(this.getStorageName(), this.getSlotIndex(), currentStack));
    }
}

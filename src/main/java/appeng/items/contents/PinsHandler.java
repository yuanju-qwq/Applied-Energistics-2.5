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

package appeng.items.contents;

import javax.annotation.Nullable;

import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.helpers.IPinsHandler;

/**
 * Server-side implementation of {@link IPinsHandler}.
 * <p>
 * Operates on data in {@link PinsHolder} and marks dirty flag to trigger persistence and network sync.
 */
public class PinsHandler implements IPinsHandler {

    private final PinsHolder holder;
    private boolean dirty = false;

    public PinsHandler(PinsHolder holder) {
        this.holder = holder;
    }

    @Override
    public PinList getPins() {
        return this.holder.getPinList();
    }

    @Override
    public PinsRows getMaxCraftingPinRows() {
        return this.holder.getMaxCraftingPinRows();
    }

    @Override
    public PinsRows getMaxPlayerPinRows() {
        return this.holder.getMaxPlayerPinRows();
    }

    @Override
    public void setMaxCraftingPinRows(PinsRows rows) {
        this.holder.setMaxCraftingPinRows(rows);
        this.dirty = true;
    }

    @Override
    public void setMaxPlayerPinRows(PinsRows rows) {
        this.holder.setMaxPlayerPinRows(rows);
        this.dirty = true;
    }

    @Override
    public PinSectionOrder getSectionOrder() {
        return this.holder.getSectionOrder();
    }

    @Override
    public void setSectionOrder(PinSectionOrder order) {
        this.holder.setSectionOrder(order);
        this.dirty = true;
    }

    @Override
    public boolean addPlayerPin(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return false;
        }

        PinList pins = this.holder.getPinList();

        // Check if already exists
        for (int i = PinList.PLAYER_OFFSET; i < PinList.PLAYER_OFFSET + PinList.PLAYER_SLOTS; i++) {
            IAEStack<?> existing = pins.getPin(i);
            if (existing != null && existing.isSameType(stack)) {
                return false;
            }
        }

        // Find the first empty slot
        for (int i = PinList.PLAYER_OFFSET; i < PinList.PLAYER_OFFSET + PinList.PLAYER_SLOTS; i++) {
            if (pins.getPin(i) == null) {
                IAEStack<?> pinStack = stack.copy();
                pinStack.setStackSize(0);
                pins.setPin(i, pinStack);
                this.markDirty();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean removePin(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return false;
        }

        boolean removed = false;
        PinList pins = this.holder.getPinList();

        for (int i = 0; i < PinList.TOTAL_SLOTS; i++) {
            IAEStack<?> existing = pins.getPin(i);
            if (existing != null && existing.isSameType(stack)) {
                pins.setPin(i, null);
                removed = true;
            }
        }

        if (removed) {
            this.markDirty();
        }

        return removed;
    }

    @Override
    public boolean isPinned(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return false;
        }

        PinList pins = this.holder.getPinList();
        for (int i = 0; i < PinList.TOTAL_SLOTS; i++) {
            IAEStack<?> existing = pins.getPin(i);
            if (existing != null && existing.isSameType(stack)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void markDirty() {
        this.dirty = true;
        this.holder.markDirty();
    }

    /**
     * @return whether there are unsynchronized changes
     */
    public boolean isDirty() {
        return this.dirty;
    }

    /**
     * Clear the dirty flag (called after sync is complete).
     */
    public void clearDirty() {
        this.dirty = false;
    }
}

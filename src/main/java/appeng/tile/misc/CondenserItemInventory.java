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

package appeng.tile.misc;

import java.util.HashMap;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.me.helpers.BaseActionSource;
import appeng.me.storage.ITickingMonitor;

class CondenserItemInventory implements IMEMonitor, ITickingMonitor {
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private final TileCondenser target;
    private IActionSource actionSource = new BaseActionSource();

    CondenserItemInventory(final TileCondenser te) {
        this.target = te;
    }

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable mode, final IActionSource src) {
        if (mode == Actionable.MODULATE && input != null && input.what() instanceof AEItemKey) {
            this.target.addPower(input.amount());
        }
        return null;
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request != null && request.what() instanceof AEItemKey requestKey) {
            ItemStack slotItem = this.target.getOutputSlot().getStackInSlot(0);
            if (!slotItem.isEmpty() && requestKey.matches(slotItem)) {
                int count = (int) Math.min(request.amount(), Integer.MAX_VALUE);
                ItemStack extracted = this.target.getOutputSlot().extractItem(0, count, mode == Actionable.SIMULATE);
                if (!extracted.isEmpty()) {
                    return GenericStack.fromItemStack(extracted);
                }
            }
        }
        return null;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter kc = new KeyCounter();
        if (!this.target.getOutputSlot().getStackInSlot(0).isEmpty()) {
            kc.add(AEItemKey.of(this.target.getOutputSlot().getStackInSlot(0)), 1);
        }
        return kc;
    }

    @Override
    public KeyCounter getKeyCounter() {
        return getAvailableKeyCounter();
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        return false;
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return i == 2;
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    public void updateOutput(ItemStack added, ItemStack removed) {
        // No-op: listener-based change tracking removed
    }

    @Override
    @SuppressWarnings("unchecked")
    public TickRateModulation onTick() {
        return TickRateModulation.IDLE;
    }

    @Override
    public void setActionSource(IActionSource actionSource) {
        this.actionSource = actionSource;
    }
}

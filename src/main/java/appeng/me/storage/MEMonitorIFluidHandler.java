/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.me.storage;

import java.util.*;
import java.util.Map.Entry;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

public class MEMonitorIFluidHandler implements IMEMonitor, ITickingMonitor {
    private final IFluidHandler handler;
    private KeyCounter cache = new KeyCounter();
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private IActionSource mySource;
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

    public MEMonitorIFluidHandler(final IFluidHandler handler) {
        this.handler = handler;
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable type, final IActionSource src) {
        if (input == null || !(input.what() instanceof AEFluidKey fluidKey)) {
            return input;
        }
        long amount = input.amount();
        FluidStack fs = fluidKey.toStack((int) amount);
        final int filled = this.handler.fill(fs, type == Actionable.MODULATE);

        if (filled == 0) {
            return input;
        }

        if (filled == amount) {
            return null;
        }

        if (type == Actionable.MODULATE) {
            long added = amount - filled;
            this.cache.add(fluidKey, added);
            this.postDifference(Collections.singletonList(new GenericStack(fluidKey, added)));
            this.onTick();
        }

        return new GenericStack(fluidKey, amount - filled);
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null || !(request.what() instanceof AEFluidKey fluidKey)) {
            return null;
        }
        FluidStack fs = fluidKey.toStack((int) request.amount());
        final FluidStack removed = this.handler.drain(fs, mode == Actionable.MODULATE);

        if (removed == null || removed.amount == 0) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            long cachedAmount = this.cache.get(fluidKey);
            if (cachedAmount > 0) {
                this.cache.add(fluidKey, -removed.amount);
                this.postDifference(Collections.singletonList(new GenericStack(fluidKey, -removed.amount)));
            }
        }
        return new GenericStack(fluidKey, removed.amount);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.cache;
    }

    @Override
    public KeyCounter getKeyCounter() {
        return getAvailableKeyCounter();
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    @Override
    public TickRateModulation onTick() {
        boolean changed = false;

        final List<GenericStack> changes = new ArrayList<>();
        final IFluidTankProperties[] tankProperties = this.handler.getTankProperties();

        KeyCounter currentlyOnStorage = new KeyCounter();

        for (IFluidTankProperties tankProperty : tankProperties) {
            if (this.mode == StorageFilter.EXTRACTABLE_ONLY && this.handler.drain(1, false) == null) {
                continue;
            }
            FluidStack contents = tankProperty.getContents();
            if (contents != null && contents.amount > 0) {
                currentlyOnStorage.add(AEFluidKey.of(contents), contents.amount);
            }
        }

        for (Object2LongMap.Entry<AEKey> entry : this.cache) {
            AEKey key = entry.getKey();
            long oldAmount = entry.getLongValue();
            long newAmount = currentlyOnStorage.get(key);
            if (oldAmount != newAmount) {
                changes.add(new GenericStack(key, newAmount - oldAmount));
            }
        }
        for (Object2LongMap.Entry<AEKey> entry : currentlyOnStorage) {
            AEKey key = entry.getKey();
            if (this.cache.get(key) == 0) {
                changes.add(new GenericStack(key, entry.getLongValue()));
            }
        }

        this.cache = currentlyOnStorage;

        if (!changes.isEmpty()) {
            this.postDifference(changes);
            changed = true;
        }

        return changed ? TickRateModulation.URGENT : TickRateModulation.SLOWER;
    }

    private void postDifference(final Iterable<GenericStack> a) {
        if (a != null) {
            final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet()
                    .iterator();
            while (i.hasNext()) {
                final Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
                final IMEMonitorHandlerReceiver key = l.getKey();
                if (key.isValid(l.getValue())) {
                    key.postChange(this, a, this.getActionSource());
                } else {
                    i.remove();
                }
            }
        }
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
        return true;
    }

    @Deprecated
    public KeyCounter getAvailableItems(final KeyCounter out) {
        for (Object2LongMap.Entry<AEKey> entry : cache) {
            out.add(entry.getKey(), entry.getLongValue());
        }

        return out;
    }

    @Deprecated
    public KeyCounter getStorageList() {
        return this.cache;
    }

    private StorageFilter getMode() {
        return this.mode;
    }

    public void setMode(final StorageFilter mode) {
        this.mode = mode;
    }

    private IActionSource getActionSource() {
        return this.mySource;
    }

    @Override
    public void setActionSource(final IActionSource mySource) {
        this.mySource = mySource;
    }

}

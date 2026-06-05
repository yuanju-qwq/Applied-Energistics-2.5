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

package appeng.fluids.parts;

import java.util.*;

import com.google.common.primitives.Ints;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.fluids.util.AEFluidStack;
import appeng.me.GridAccessException;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.ITickingMonitor;
import appeng.parts.misc.AbstractPartStorageBus;

/**
 * Wraps an Fluid Handler in such a way that it can be used as an IMEInventory for fluids.
 *
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class FluidHandlerAdapter implements IMEInventory, IBaseMonitor, ITickingMonitor {
    private final Map<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private IActionSource source;
    private final IFluidHandler fluidHandler;
    private final IGridProxyable proxyable;
    private final InventoryCache cache;
    private StorageFilter mode;
    private AccessRestriction access;

    FluidHandlerAdapter(IFluidHandler fluidHandler, IGridProxyable proxy) {
        this.fluidHandler = fluidHandler;
        this.proxyable = proxy;
        if (this.proxyable instanceof AbstractPartStorageBus) {
            AbstractPartStorageBus partStorageBus = (AbstractPartStorageBus) this.proxyable;
            this.mode = ((StorageFilter) partStorageBus.getConfigManager().getSetting(Settings.STORAGE_FILTER));
            this.access = ((AccessRestriction) partStorageBus.getConfigManager().getSetting(Settings.ACCESS));
        }
        this.cache = new InventoryCache(this.fluidHandler, this.mode);
        this.cache.update();
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        if (input == null) return null;
        if (!(input.what() instanceof AEFluidKey fluidKey)) return input;

        long amount = input.amount();
        FluidStack fs = fluidKey.toStack(Ints.saturatedCast(amount));
        int wasFilled = this.fluidHandler.fill(fs, type != Actionable.SIMULATE);
        int remaining = fs.amount - wasFilled;

        if (fs.amount == remaining) {
            return input;
        }

        if (type == Actionable.MODULATE) {
            long added = amount - remaining;
            this.cache.currentlyCached.add(fluidKey, added);
            this.postDifference(Collections.singletonList(new GenericStack(fluidKey, added)));
            try {
                this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
            } catch (GridAccessException ex) {
                // meh
            }
        }

        return remaining > 0 ? new GenericStack(fluidKey, remaining) : null;
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;
        if (!(request.what() instanceof AEFluidKey fluidKey)) return null;

        FluidStack fs = fluidKey.toStack(Ints.saturatedCast(request.amount()));
        FluidStack gathered = this.fluidHandler.drain(fs, mode == Actionable.MODULATE);

        if (gathered == null || gathered.amount == 0) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            long cachedAmount = this.cache.currentlyCached.get(fluidKey);
            if (cachedAmount > 0) {
                this.cache.currentlyCached.add(fluidKey, -gathered.amount);
                this.postDifference(Collections.singletonList(new GenericStack(fluidKey, -gathered.amount)));
            }
            try {
                this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
            } catch (GridAccessException ex) {
                // meh
            }
        }
        return new GenericStack(fluidKey, gathered.amount);
    }

    @Override
    public TickRateModulation onTick() {
        List<GenericStack> changes = this.cache.update();
        if (!changes.isEmpty() && access.hasPermission(AccessRestriction.READ)) {
            this.postDifference(changes);
            return TickRateModulation.URGENT;
        } else {
            return TickRateModulation.SLOWER;
        }
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.cache.getAvailableKeyCounter();
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    @Override
    public void setActionSource(IActionSource source) {
        this.source = source;
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    private void postDifference(Iterable<GenericStack> a) {
        final Iterator<Map.Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet()
                .iterator();
        while (i.hasNext()) {
            final Map.Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
            final IMEMonitorHandlerReceiver key = l.getKey();
            if (key.isValid(l.getValue())) {
                key.postChange(this, a, this.source);
            } else {
                i.remove();
            }
        }
    }

    private static class InventoryCache {
        private final IFluidHandler fluidHandler;
        private final StorageFilter mode;
        KeyCounter currentlyCached = new KeyCounter();

        public InventoryCache(IFluidHandler fluidHandler, StorageFilter mode) {
            this.mode = mode;
            this.fluidHandler = fluidHandler;
        }

        public List<GenericStack> update() {
            final List<GenericStack> changes = new ArrayList<>();
            final IFluidTankProperties[] tankProperties = this.fluidHandler.getTankProperties();

            KeyCounter currentlyOnStorage = new KeyCounter();

            for (IFluidTankProperties tankProperty : tankProperties) {
                var contents = tankProperty.getContents();
                if (this.mode == StorageFilter.EXTRACTABLE_ONLY
                        && (contents == null || this.fluidHandler.drain(contents, false) == null)) {
                    continue;
                }
                if (contents != null) {
                    AEFluidStack aeStack = AEFluidStack.fromFluidStack(contents);
                    if (aeStack != null) {
                        currentlyOnStorage.add(aeStack.toAEKey(), aeStack.getStackSize());
                    }
                }
            }

            // Items removed or changed
            for (var entry : currentlyCached) {
                AEKey key = entry.getKey();
                long oldAmount = entry.getLongValue();
                long newAmount = currentlyOnStorage.get(key);
                long diff = newAmount - oldAmount;
                if (diff != 0) {
                    changes.add(new GenericStack(key, diff));
                }
            }

            // New items
            for (var entry : currentlyOnStorage) {
                AEKey key = entry.getKey();
                if (currentlyCached.get(key) == 0) {
                    changes.add(new GenericStack(key, entry.getLongValue()));
                }
            }

            currentlyCached = currentlyOnStorage;

            return changes;
        }

        public KeyCounter getAvailableKeyCounter() {
            KeyCounter out = new KeyCounter();
            for (var entry : currentlyCached) {
                out.add(entry.getKey(), entry.getLongValue());
            }
            return out;
        }

    }
}

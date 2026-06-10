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

package appeng.me.cache;

import java.util.*;
import java.util.Map.Entry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;

@SuppressWarnings("rawtypes")
public class NetworkMonitor implements IMEMonitor {
    @Nonnull
    private static final HashMap<IActionSource, LinkedList<NetworkMonitor>> src2MonitorsMap = new HashMap<>();
    private static final Set<IActionSource> nestingSources = new HashSet<>();

    protected boolean wasNested = false;
    protected boolean isNested = false;

    @Nonnull
    private final GridStorageCache myGridCache;
    @Nonnull
    private final AEKeyType myKeyType;

    /**
     * AEKey-based primary cache. All inventory state is stored here.
     */
    @Nonnull
    private final KeyCounter keyCounter = new KeyCounter();

    /**
     * Tracks which AEKeys are craftable.
     */
    @Nonnull
    private final Set<AEKey> craftableKeys = new HashSet<>();

    @Nonnull
    private final Object2ObjectMap<IMEMonitorHandlerReceiver, Object> listeners;

    private boolean sendEvent = false;
    private long gridCount;
    public boolean forceUpdate;

    public NetworkMonitor(final GridStorageCache cache, final AEKeyType type) {
        this.myGridCache = cache;
        this.myKeyType = type;
        this.listeners = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return this.getHandler().canAccept(input);
    }

    @Override
    public AccessRestriction getAccess() {
        return this.getHandler().getAccess();
    }

    @Override
    public int getPriority() {
        return this.getHandler().getPriority();
    }

    @Override
    public int getSlot() {
        return this.getHandler().getSlot();
    }

    public long getGridCurrentCount() {
        return gridCount;
    }

    public void incGridCurrentCount(long count) {
        gridCount += count;
    }

    /**
     * @return the AEKey-based primary cache
     */
    @Nonnull
    public KeyCounter getKeyCounter() {
        return this.keyCounter;
    }

    @Override
    public AEKeyType getKeyType() {
        return this.myKeyType;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.keyCounter;
    }

    /**
     * @deprecated Use {@link #getKeyCounter()} instead.
     * Returns a legacy IItemList view built on-demand from the internal KeyCounter.
     */
    @Nonnull
    @Deprecated
    public IItemList getStorageList() {
        final IItemList out = new appeng.util.item.IAEStackList();

        for (Object2LongMap.Entry<AEKey> entry : this.keyCounter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount == 0) {
                continue;
            }
            IAEStack stack = key.toIAEStack(amount);
            if (stack != null) {
                if (this.craftableKeys.contains(key)) {
                    stack.setCraftable(true);
                }
                out.addGeneric(stack);
            }
        }

        for (AEKey key : this.craftableKeys) {
            if (this.keyCounter.get(key) == 0) {
                IAEStack stack = key.toIAEStack(0);
                if (stack != null) {
                    stack.setCraftable(true);
                    out.addGeneric(stack);
                }
            }
        }

        return out;
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        return this.getHandler().isPrioritized(input);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    @Override
    public boolean validForPass(final int i) {
        return this.getHandler().validForPass(i);
    }

    @Nullable
    private IMEInventoryHandler getHandler() {
        return this.myGridCache.getInventoryHandler(this.myKeyType);
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        IMEInventoryHandler handler = getHandler();
        if (handler == null) {
            return input;
        }
        return handler.injectItems(input, type, src);
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        IMEInventoryHandler handler = getHandler();
        if (handler == null) {
            return null;
        }
        return handler.extractItems(request, mode, src);
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    private void notifyListenersOfChange(final Iterable<GenericStack> diff, final IActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
            final IMEMonitorHandlerReceiver receiver = o.getKey();

            if (receiver.isValid(o.getValue())) {
                receiver.postChange(this, diff, src);
            } else {
                i.remove();
            }
        }
    }

    protected void updateCraftables(KeyCounter changes, IActionSource src) {
        for (Object2LongMap.Entry<AEKey> entry : changes) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                this.craftableKeys.add(entry.getKey());
            } else {
                this.craftableKeys.remove(entry.getKey());
            }
        }
        this.listDirty = true;
    }

    private boolean listDirty = true;

    protected void postChange(final boolean add, final Iterable<GenericStack> changes, final IActionSource src) {
        src2MonitorsMap.putIfAbsent(src, new LinkedList<>());
        if (src2MonitorsMap.get(src).contains(this)) {
            nestingSources.add(src);
            return;
        }
        src2MonitorsMap.get(src).add(this);

        this.sendEvent = true;

        List<GenericStack> diff = new ArrayList<>();

        for (final GenericStack change : changes) {
            long delta = change.amount();
            if (!add) {
                delta = -delta;
            }

            incGridCurrentCount(delta);

            AEKey key = change.what();
            this.keyCounter.add(key, delta);
            diff.add(new GenericStack(key, delta));
            this.listDirty = true;

            if (this.myGridCache.getInterestManager().containsKey(key)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(key);

                if (!list.isEmpty()) {
                    KeyCounter fullStack = this.getKeyCounter();

                    this.myGridCache.getInterestManager().enableTransactions();

                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(fullStack, this.keyCounter, src);
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        this.notifyListenersOfChange(diff, src);

        if (src2MonitorsMap.get(src).getFirst() == this) {
            boolean nested = nestingSources.contains(src);
            src2MonitorsMap.get(src).forEach(networkMonitor -> networkMonitor.isNested = nested);

            src2MonitorsMap.get(src).forEach(networkMonitor -> {
                if (networkMonitor.isNested != networkMonitor.wasNested) {
                    networkMonitor.wasNested = networkMonitor.isNested;
                    networkMonitor.setForceUpdate(true);
                }
            });
            src2MonitorsMap.remove(src);
            nestingSources.remove(src);
        }
    }

    public void setForceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate;
    }

    void forceUpdate() {
        forceUpdate = false;

        this.keyCounter.clear();
        this.craftableKeys.clear();

        IMEInventoryHandler handler = this.getHandler();
        if (handler != null) {
            KeyCounter available = handler.getAvailableKeyCounter();
            for (Object2LongMap.Entry<AEKey> entry : available) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                this.keyCounter.set(key, amount);
            }
        }

        for (Object2LongMap.Entry<AEKey> entry : this.keyCounter) {
            AEKey key = entry.getKey();

            if (this.myGridCache.getInterestManager().containsKey(key)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(key);

                if (!list.isEmpty()) {
                    KeyCounter fullStack = this.getKeyCounter();

                    this.myGridCache.getInterestManager().enableTransactions();

                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(fullStack, this.keyCounter, null);
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        long count = 0;
        for (Object2LongMap.Entry<AEKey> entry : this.keyCounter) {
            count += entry.getLongValue();
        }
        gridCount = count;
        this.listDirty = false;

        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
            final IMEMonitorHandlerReceiver receiver = o.getKey();

            if (receiver.isValid(o.getValue())) {
                receiver.onListUpdate();
            } else {
                i.remove();
            }
        }
    }

    void onTick() {
        if (forceUpdate) {
            forceUpdate();
        }
        if (this.sendEvent) {
            this.sendEvent = false;
            this.myGridCache.getGrid().postEvent(new MENetworkStorageEvent(this, this.myKeyType));
        }
    }
}

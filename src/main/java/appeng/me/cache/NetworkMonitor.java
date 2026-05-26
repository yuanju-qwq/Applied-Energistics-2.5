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

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.events.MENetworkStorageEvent;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.me.storage.ItemWatcher;

public class NetworkMonitor<T extends IAEStack<T>> implements IMEMonitor<T> {
    @Nonnull
    private static final HashMap<IActionSource, LinkedList<NetworkMonitor<?>>> src2MonitorsMap = new HashMap<>();
    private static final Set<IActionSource> nestingSources = new HashSet<>();

    protected boolean wasNested = false;
    protected boolean isNested = false;

    @Nonnull
    private final GridStorageCache myGridCache;
    @Nonnull
    private final IAEStackType<T> myStackType;
    @Nonnull
    private final IItemList<T> cachedList;

    /**
     * AEKey-based primary cache. All inventory state is stored here.
     * The legacy {@link #cachedList} is a lazy-derived view rebuilt on demand.
     */
    @Nonnull
    private final KeyCounter keyCounter = new KeyCounter();

    /**
     * Tracks which AEKeys are craftable. Used when rebuilding the legacy IItemList view.
     */
    @Nonnull
    private final Set<AEKey> craftableKeys = new HashSet<>();

    /**
     * When true, {@link #cachedList} is stale and must be rebuilt from {@link #keyCounter}
     * on the next call to {@link #getStorageList()}.
     */
    private boolean listDirty = true;

    @Nonnull
    private final Object2ObjectMap<IMEMonitorHandlerReceiver<? super T>, Object> listeners;

    private boolean sendEvent = false;
    private long gridCount;
    public boolean forceUpdate;

    public NetworkMonitor(final GridStorageCache cache, final IAEStackType<T> type) {
        this.myGridCache = cache;
        this.myStackType = type;
        this.cachedList = type.createList();
        this.listeners = new Object2ObjectOpenHashMap<>();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver<? super T> l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public boolean canAccept(final T input) {
        return this.getHandler().canAccept(input);
    }

    @Override
    public T extractItems(final T request, final Actionable mode, final IActionSource src) {
        return this.getHandler().extractItems(request, mode, src);
    }

    @Override
    public AccessRestriction getAccess() {
        return this.getHandler().getAccess();
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out) {
        return this.getHandler().getAvailableItems(out);
    }

    @Override
    public IAEStackType<T> getStackType() {
        return this.myStackType;
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

    /**
     * @deprecated Use {@link #getKeyCounter()} instead.
     * Returns a legacy IItemList view built on-demand from the internal KeyCounter.
     */
    @Nonnull
    @Override
    @Deprecated
    public IItemList<T> getStorageList() {
        if (this.listDirty) {
            rebuildCachedList();
        }
        return this.cachedList;
    }

    /**
     * Rebuilds the legacy {@link #cachedList} from the primary {@link #keyCounter}
     * and {@link #craftableKeys}. Called on-demand when {@link #getStorageList()} is invoked.
     */
    @SuppressWarnings("unchecked")
    private void rebuildCachedList() {
        this.cachedList.resetStatus();

        // Add all stored entries with their craftable flag
        for (var entry : this.keyCounter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount == 0) {
                continue;
            }
            IAEStackBase stack = key.toIAEStack(amount);
            if (stack != null) {
                if (this.craftableKeys.contains(key)) {
                    stack.setCraftable(true);
                }
                this.cachedList.addGeneric((IAEStack<?>) stack);
            }
        }

        // Add craftable-only entries (amount 0 but craftable)
        for (AEKey key : this.craftableKeys) {
            if (this.keyCounter.get(key) == 0) {
                IAEStackBase stack = key.toIAEStack(0);
                if (stack != null) {
                    stack.setCraftable(true);
                    this.cachedList.addGeneric((IAEStack<?>) stack);
                }
            }
        }

        this.listDirty = false;
    }

    @Override
    public T injectItems(final T input, final Actionable mode, final IActionSource src) {
        return this.getHandler().injectItems(input, mode, src);
    }

    @Override
    public boolean isPrioritized(final T input) {
        return this.getHandler().isPrioritized(input);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver<? super T> l) {
        this.listeners.remove(l);
    }

    @Override
    public boolean validForPass(final int i) {
        return this.getHandler().validForPass(i);
    }

    @Nullable
    private IMEInventoryHandler<T> getHandler() {
        return this.myGridCache.getInventoryHandler(this.myStackType);
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver<? super T>, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    @SuppressWarnings("unchecked")
    private void notifyListenersOfChange(final Iterable<T> diff, final IActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver<? super T>, Object>> i = this.getListeners();

        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver<? super T>, Object> o = i.next();
            final IMEMonitorHandlerReceiver<? super T> receiver = o.getKey();

            if (receiver.isValid(o.getValue())) {
                ((IMEMonitorHandlerReceiver) receiver).postChange(this, diff, src);
            } else {
                i.remove();
            }
        }
    }

    protected void updateCraftables(Iterable<T> input, IActionSource src) {
        for (final T changedItem : input) {
            AEKey key = changedItem.toAEKey();
            if (key == null) {
                continue;
            }
            if (changedItem.isCraftable()) {
                this.craftableKeys.add(key);
            } else {
                this.craftableKeys.remove(key);
            }
        }
        this.listDirty = true;
    }

    protected void postChange(final boolean add, final Iterable<T> changes, final IActionSource src) {
        src2MonitorsMap.putIfAbsent(src, new LinkedList<>());
        if (src2MonitorsMap.get(src).contains(this)) {
            nestingSources.add(src);
            return;
        }
        src2MonitorsMap.get(src).add(this);

        this.sendEvent = true;

        for (final T change : changes) {
            // T change = changed;
            if (!add && change != null) {
                // change = changed.copy();
                change.setStackSize(-change.getStackSize());
            }

            incGridCurrentCount(change.getStackSize());

            // Update primary KeyCounter cache
            AEKey key = change.toAEKey();
            if (key != null) {
                this.keyCounter.add(key, change.getStackSize());
            }
            this.listDirty = true;

            if (this.myGridCache.getInterestManager().containsKey(change)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(change);

                if (!list.isEmpty()) {
                    IAEStack<T> fullStack = this.getStorageList().findPrecise(change);

                    if (fullStack == null) {
                        fullStack = change.copy();
                        fullStack.setStackSize(0);
                    }

                    this.myGridCache.getInterestManager().enableTransactions();

                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(this.getStorageList(), fullStack, change, src, this.myStackType);
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        this.notifyListenersOfChange(changes, src);

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

        // Read from the handler into a temporary IItemList, then convert to KeyCounter
        this.keyCounter.clear();
        this.craftableKeys.clear();
        this.cachedList.resetStatus();
        this.getAvailableItems(this.cachedList);

        long count = 0;
        for (T stack : this.cachedList) {
            AEKey key = stack.toAEKey();
            if (key != null) {
                long amount = stack.getStackSize();
                this.keyCounter.set(key, amount);
                count += amount;
                if (stack.isCraftable()) {
                    this.craftableKeys.add(key);
                }
            }

            if (this.myGridCache.getInterestManager().containsKey(stack)) {
                final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(stack);

                if (!list.isEmpty()) {
                    IAEStack<T> fullStack = this.cachedList.findPrecise(stack);

                    if (fullStack == null) {
                        fullStack = stack.copy();
                        fullStack.setStackSize(0);
                    }

                    this.myGridCache.getInterestManager().enableTransactions();

                    for (final ItemWatcher iw : list) {
                        iw.getHost().onStackChange(this.cachedList, fullStack, stack, null, this.myStackType);
                    }

                    this.myGridCache.getInterestManager().disableTransactions();
                }
            }
        }

        gridCount = count;
        this.listDirty = false;

        final Iterator<Entry<IMEMonitorHandlerReceiver<? super T>, Object>> i = this.getListeners();
        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver<? super T>, Object> o = i.next();
            final IMEMonitorHandlerReceiver<? super T> receiver = o.getKey();

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
            this.myGridCache.getGrid().postEvent(new MENetworkStorageEvent(this, this.myStackType));
        }
    }
}

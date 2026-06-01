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
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
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
     * Tracks which AEKeys are craftable. Used when rebuilding the legacy IItemList view.
     */
    @Nonnull
    private final Set<AEKey> craftableKeys = new HashSet<>();

    /**
     * Legacy IItemList view, rebuilt on demand from {@link #keyCounter}.
     */
    @Nonnull
    private final IItemList cachedList;

    @Nonnull
    private final Object2ObjectMap<IMEMonitorHandlerReceiver, Object> listeners;

    private boolean sendEvent = false;
    private long gridCount;
    public boolean forceUpdate;

    public NetworkMonitor(final GridStorageCache cache, final AEKeyType type) {
        this.myGridCache = cache;
        this.myKeyType = type;
        var legacyType = AEStackTypeRegistry.getType(type.getId());
        this.cachedList = legacyType != null ? legacyType.createList() : null;
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
        if (this.cachedList == null) {
            return null;
        }
        rebuildCachedList();
        return this.cachedList;
    }

    /**
     * Rebuilds the legacy cachedList from the primary keyCounter and craftableKeys.
     */
    private void rebuildCachedList() {
        if (this.cachedList == null) return;
        this.cachedList.resetStatus();

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

        for (AEKey key : this.craftableKeys) {
            if (this.keyCounter.get(key) == 0) {
                IAEStackBase stack = key.toIAEStack(0);
                if (stack != null) {
                    stack.setCraftable(true);
                    this.cachedList.addGeneric((IAEStack<?>) stack);
                }
            }
        }
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
        var legacyType = AEStackTypeRegistry.getType(this.myKeyType.getId());
        if (legacyType == null) {
            return null;
        }
        return this.myGridCache.getInventoryHandler(legacyType);
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

    protected void updateCraftables(Iterable input, IActionSource src) {
        for (final Object obj : input) {
            if (obj instanceof IAEStack changedItem) {
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
        }
        this.listDirty = true;
    }

    private boolean listDirty = true;

    protected void postChange(final boolean add, final Iterable changes, final IActionSource src) {
        src2MonitorsMap.putIfAbsent(src, new LinkedList<>());
        if (src2MonitorsMap.get(src).contains(this)) {
            nestingSources.add(src);
            return;
        }
        src2MonitorsMap.get(src).add(this);

        this.sendEvent = true;

        List<GenericStack> diff = new ArrayList<>();

        for (final Object obj : changes) {
            if (obj instanceof IAEStack change) {
                long delta = change.getStackSize();
                if (!add && change != null) {
                    delta = -delta;
                }

                incGridCurrentCount(delta);

                AEKey key = change.toAEKey();
                if (key != null) {
                    this.keyCounter.add(key, delta);
                    diff.add(new GenericStack(key, delta));
                }
                this.listDirty = true;

                if (this.myGridCache.getInterestManager().containsKey(change)) {
                    final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(change);

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

        var handler = this.getHandler();
        if (handler != null) {
            KeyCounter available = handler.getAvailableKeyCounter();
            for (var entry : available) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                this.keyCounter.set(key, amount);
                IAEStackBase stack = key.toIAEStack(amount);
                if (stack != null && stack.isCraftable()) {
                    this.craftableKeys.add(key);
                }
            }
        }

        if (this.cachedList != null) {
            this.cachedList.resetStatus();
            for (var entry : this.keyCounter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                IAEStackBase stack = key.toIAEStack(amount);
                if (stack != null) {
                    this.cachedList.addGeneric((IAEStack<?>) stack);
                }

                if (this.myGridCache.getInterestManager().containsKey(stack)) {
                    final Collection<ItemWatcher> list = this.myGridCache.getInterestManager().get(stack);

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
        }

        long count = 0;
        for (var entry : this.keyCounter) {
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

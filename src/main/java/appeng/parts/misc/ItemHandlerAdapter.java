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

package appeng.parts.misc;

import java.util.*;

import com.google.common.primitives.Ints;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.core.AELog;
import appeng.me.GridAccessException;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.ITickingMonitor;
import appeng.util.inv.ItemHandlerIterator;
import appeng.util.inv.ItemSlot;

/**
 * Wraps an Item Handler in such a way that it can be used as an IMEInventory for items.
 */
class ItemHandlerAdapter implements IMEInventory, IBaseMonitor, ITickingMonitor {
    private final Object2ObjectMap<IMEMonitorHandlerReceiver, Object> listeners = new Object2ObjectOpenHashMap<>();
    private IActionSource mySource;
    private final IItemHandler itemHandler;
    private final IGridProxyable proxyable;
    private final InventoryCache cache;
    private StorageFilter mode;
    private AccessRestriction access;

    ItemHandlerAdapter(IItemHandler itemHandler, IGridProxyable proxy) {
        this.itemHandler = itemHandler;
        this.proxyable = proxy;
        if (this.proxyable instanceof AbstractPartStorageBus) {
            AbstractPartStorageBus partStorageBus = (AbstractPartStorageBus) this.proxyable;
            this.mode = ((StorageFilter) partStorageBus.getConfigManager().getSetting(Settings.STORAGE_FILTER));
            this.access = ((AccessRestriction) partStorageBus.getConfigManager().getSetting(Settings.ACCESS));
        }
        this.cache = new InventoryCache(this.itemHandler, this.mode);
        this.cache.update();
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        if (input == null) return null;
        if (!(input.what() instanceof AEItemKey itemKey)) return input;

        long amount = input.amount();
        ItemStack stack = itemKey.toStack(Ints.saturatedCast(amount));
        ItemStack remaining = stack;

        int slotCount = this.itemHandler.getSlots();
        for (int i = 0; i < slotCount && !remaining.isEmpty(); i++) {
            remaining = this.itemHandler.insertItem(i, remaining, type == Actionable.SIMULATE);
        }

        if (remaining == stack) {
            return input;
        }

        if (type == Actionable.MODULATE) {
            long added = amount - remaining.getCount();
            this.cache.currentlyCached.add(itemKey, added);
            this.postDifference(Collections.singletonList(new GenericStack(itemKey, added)));
            try {
                this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
            } catch (GridAccessException ex) {
                // meh
            }
        }

        return remaining.isEmpty() ? null : new GenericStack(itemKey, remaining.getCount());
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;
        if (!(request.what() instanceof AEItemKey itemKey)) return null;

        int remainingSize = Ints.saturatedCast(request.amount());
        ItemStack gathered = ItemStack.EMPTY;
        final boolean simulate = (mode == Actionable.SIMULATE);

        for (int i = 0; i < this.itemHandler.getSlots(); i++) {
            ItemStack stackInInventorySlot = this.itemHandler.getStackInSlot(i);

            AEItemKey slotKey = AEItemKey.of(stackInInventorySlot);
            if (!itemKey.equals(slotKey)) {
                continue;
            }

            ItemStack extracted;

            int stackSizeCurrentSlot = stackInInventorySlot.getCount();
            int remainingCurrentSlot = Math.min(remainingSize, stackSizeCurrentSlot);

            do {
                extracted = this.itemHandler.extractItem(i, remainingCurrentSlot, simulate);
                if (!extracted.isEmpty()) {
                    if (extracted == stackInInventorySlot) {
                        extracted = extracted.copy();
                    }

                    if (extracted.getCount() > remainingCurrentSlot) {
                        AELog.warn(
                                "Mod that provided item handler %s is broken. Returned %s items while only requesting %d.",
                                this.itemHandler.getClass().getName(), extracted.toString(), remainingCurrentSlot);
                        extracted.setCount(remainingCurrentSlot);
                    }

                    if (simulate && extracted.getCount() == extracted.getMaxStackSize()
                            && remainingCurrentSlot > extracted.getMaxStackSize()) {
                        extracted.setCount(remainingCurrentSlot);
                    }

                    if (gathered.isEmpty()) {
                        gathered = extracted;
                    } else {
                        gathered.grow(extracted.getCount());
                    }
                    remainingCurrentSlot -= extracted.getCount();
                }
            } while (!simulate && !extracted.isEmpty() && remainingCurrentSlot > 0);

            remainingSize -= stackSizeCurrentSlot - remainingCurrentSlot;
            if (remainingSize <= 0) {
                break;
            }
        }

        if (!gathered.isEmpty()) {
            if (mode == Actionable.MODULATE) {
                long cachedAmount = this.cache.currentlyCached.get(itemKey);
                if (cachedAmount > 0) {
                    this.cache.currentlyCached.add(itemKey, -gathered.getCount());
                    this.postDifference(Collections.singletonList(new GenericStack(itemKey, -gathered.getCount())));
                }
                try {
                    this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
                } catch (GridAccessException ex) {
                    // meh
                }
            }

            return new GenericStack(itemKey, gathered.getCount());
        }

        return null;
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
    public void setActionSource(final IActionSource mySource) {
        this.mySource = mySource;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.cache.getAvailableKeyCounter();
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
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
                key.postChange(this, a, this.mySource);
            } else {
                i.remove();
            }
        }
    }

    private static class InventoryCache implements Iterable<ItemSlot> {
        private final IItemHandler itemHandler;
        private final StorageFilter mode;
        KeyCounter currentlyCached = new KeyCounter();

        public InventoryCache(IItemHandler itemHandler, StorageFilter mode) {
            this.mode = mode;
            this.itemHandler = itemHandler;
        }

        public KeyCounter getAvailableKeyCounter() {
            KeyCounter out = new KeyCounter();
            for (var entry : currentlyCached) {
                out.add(entry.getKey(), entry.getLongValue());
            }
            return out;
        }

        private StorageFilter getMode() {
            return this.mode;
        }

        public List<GenericStack> update() {
            final List<GenericStack> changes = new ArrayList<>();

            KeyCounter currentlyOnStorage = new KeyCounter();

            for (final ItemSlot is : this) {
                if (this.mode == StorageFilter.EXTRACTABLE_ONLY && !is.isExtractable()) {
                    continue;
                }
                IAEItemStack aeStack = is.getAEItemStack();
                if (aeStack != null) {
                    currentlyOnStorage.add(aeStack.toAEKey(), aeStack.getStackSize());
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

        @Override
        public Iterator<ItemSlot> iterator() {
            return new ItemHandlerIterator(this.itemHandler);
        }

    }
}

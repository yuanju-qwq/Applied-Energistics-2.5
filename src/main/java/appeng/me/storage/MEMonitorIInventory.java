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

package appeng.me.storage;

import java.util.*;
import java.util.Map.Entry;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.ItemSlot;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

public class MEMonitorIInventory implements IMEMonitor, ITickingMonitor {

    private final InventoryAdaptor adaptor;
    private KeyCounter cache = new KeyCounter();

    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private IActionSource mySource;
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;

    public MEMonitorIInventory(final InventoryAdaptor adaptor) {
        this.adaptor = adaptor;
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
        if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
            return input;
        }
        long amount = input.amount();
        ItemStack stack = itemKey.toStack((int) amount);
        ItemStack out;

        if (type == Actionable.SIMULATE) {
            out = this.adaptor.simulateAdd(stack);
        } else {
            out = this.adaptor.addItems(stack);
        }

        if (out.isEmpty()) {
            return null;
        }

        if (type == Actionable.MODULATE) {
            long added = amount - out.getCount();
            this.cache.add(itemKey, added);
            this.postDifference(Collections.singletonList(new GenericStack(itemKey, added)));
            this.onTick();
        }

        return new GenericStack(itemKey, out.getCount());
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null || !(request.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        long amount = request.amount();
        ItemStack out;

        if (mode == Actionable.SIMULATE) {
            out = this.adaptor.simulateRemove((int) amount, itemKey.toStack(), null);
        } else {
            out = this.adaptor.removeItems((int) amount, itemKey.toStack(), null);
        }

        if (out.isEmpty()) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            long cachedAmount = this.cache.get(itemKey);
            if (cachedAmount > 0) {
                this.cache.add(itemKey, -out.getCount());
                this.postDifference(Collections.singletonList(new GenericStack(itemKey, -out.getCount())));
            }
            this.onTick();
        }

        return new GenericStack(itemKey, out.getCount());
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
        return AEKeyType.items();
    }

    @Override
    public TickRateModulation onTick() {
        boolean changed = false;

        final List<GenericStack> changes = new ArrayList<>();

        KeyCounter currentlyOnStorage = new KeyCounter();

        for (final ItemSlot is : adaptor) {
            if (this.mode == StorageFilter.EXTRACTABLE_ONLY && !is.isExtractable()) {
                continue;
            }
            ItemStack itemStack = is.getItemStack();
            if (!itemStack.isEmpty()) {
                currentlyOnStorage.add(AEItemKey.of(itemStack), itemStack.getCount());
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

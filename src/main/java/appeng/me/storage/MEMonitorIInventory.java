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
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.InventoryAdaptor;
import appeng.util.inv.ItemSlot;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

public class MEMonitorIInventory implements IMEMonitor, ITickingMonitor {

    private final InventoryAdaptor adaptor;
    private IItemList<IAEItemStack> cache = AEItemStackType.INSTANCE.createList();

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
    @Deprecated
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final IActionSource src) {
        ItemStack out = ItemStack.EMPTY;

        if (type == Actionable.SIMULATE) {
            out = this.adaptor.simulateAdd(input.createItemStack());
        } else {
            out = this.adaptor.addItems(input.createItemStack());
        }

        if (out.isEmpty()) {
            return null;
        }

        // better then doing construction from scratch :3
        final IAEItemStack o = input.copy();
        o.setStackSize(out.getCount());

        if (type == Actionable.MODULATE) {
            IAEItemStack added = o.copy();
            this.cache.add(added);
            this.postDifference(Collections.singletonList(GenericStack.fromIAEStack(added)));
            this.onTick();
        }

        return o;
    }

    @Override
    @Deprecated
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable type, final IActionSource src) {
        ItemStack out = ItemStack.EMPTY;

        if (type == Actionable.SIMULATE) {
            out = this.adaptor.simulateRemove((int) request.getStackSize(), request.getDefinition(), null);
        } else {
            out = this.adaptor.removeItems((int) request.getStackSize(), request.getDefinition(), null);
        }

        if (out.isEmpty()) {
            return null;
        }

        // better then doing construction from scratch :3
        final IAEItemStack o = request.copy();
        o.setStackSize(out.getCount());

        if (type == Actionable.MODULATE) {
            IAEItemStack cachedStack = this.cache.findPrecise(request);
            if (cachedStack != null) {
                cachedStack.decStackSize(o.getStackSize());
                this.postDifference(Collections.singletonList(GenericStack.fromIAEStack(o.copy().setStackSize(-o.getStackSize()))));
            }
            this.onTick();
        }

        return o;
    }

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable type, final IActionSource src) {
        if (input == null || !(input.what() instanceof AEItemKey itemKey)) {
            return input;
        }
        IAEItemStack aeInput = AEItemStack.fromItemStack(itemKey.toStack((int) input.amount()));
        IAEItemStack result = this.injectItems(aeInput, type, src);
        return GenericStack.fromIAEStack(result);
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null || !(request.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        IAEItemStack aeRequest = AEItemStack.fromItemStack(itemKey.toStack((int) request.amount()));
        IAEItemStack result = this.extractItems(aeRequest, mode, src);
        return GenericStack.fromIAEStack(result);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        for (IAEItemStack is : cache) {
            out.add(is.toAEKey(), is.getStackSize());
        }
        return out;
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

        final List<IAEItemStack> changes = new ArrayList<>();

        IItemList<IAEItemStack> currentlyOnStorage = AEItemStackType.INSTANCE.createList();

        for (final ItemSlot is : adaptor) {
            if (this.mode == StorageFilter.EXTRACTABLE_ONLY && !is.isExtractable()) {
                continue;
            }
            currentlyOnStorage.add(is.getAEItemStack());
        }

        for (final IAEItemStack is : cache) {
            is.setStackSize(-is.getStackSize());
        }

        for (final IAEItemStack is : currentlyOnStorage) {
            cache.add(is);
        }

        for (final IAEItemStack is : cache) {
            if (is.getStackSize() != 0) {
                changes.add(is);
            }
        }

        cache = currentlyOnStorage;

        if (!changes.isEmpty()) {
            final List<GenericStack> genericChanges = new ArrayList<>();
            for (IAEItemStack is : changes) {
                GenericStack gs = GenericStack.fromIAEStack(is);
                if (gs != null) {
                    genericChanges.add(gs);
                }
            }
            this.postDifference(genericChanges);
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

    @Override
    @Deprecated
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        for (IAEItemStack is : cache) {
            out.addStorage(is);
        }

        return out;
    }

    @Override
    @Deprecated
    public IItemList<IAEItemStack> getStorageList() {
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

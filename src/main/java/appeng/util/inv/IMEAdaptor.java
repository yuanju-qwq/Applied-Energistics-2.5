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

package appeng.util.inv;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.InventoryAdaptor;

public class IMEAdaptor extends InventoryAdaptor {

    private final IMEInventory target;
    private final IActionSource src;
    private int maxSlots = 0;

    public IMEAdaptor(final IMEInventory input, final IActionSource src) {
        this.target = input;
        this.src = src;
    }

    @Override
    public boolean hasSlots() {
        return true;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        final KeyCounter list = this.getList();
        final List<ItemSlot> slots = new ArrayList<>();
        int idx = 0;
        for (var entry : list) {
            if (entry.getKey() instanceof AEItemKey itemKey && entry.getLongValue() > 0) {
                ItemSlot slot = new ItemSlot();
                slot.setSlot(idx++);
                slot.setExtractable(true);
                IAEItemStack aeStack = (IAEItemStack) itemKey.toIAEStack(entry.getLongValue());
                if (aeStack != null) {
                    slot.setAEItemStack(aeStack);
                    slots.add(slot);
                }
            }
        }
        return slots.iterator();
    }

    private KeyCounter getList() {
        return this.target.getAvailableKeyCounter();
    }

    @Override
    public ItemStack removeItems(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.MODULATE);
    }

    private ItemStack doRemoveItems(final int amount, final ItemStack filter, final IInventoryDestination destination,
            final Actionable type) {
        AEItemKey reqKey = null;

        if (filter == null || filter.isEmpty()) {
            final KeyCounter list = this.getList();
            AEKey firstKey = list.getFirstKey();
            if (firstKey instanceof AEItemKey itemKey) {
                reqKey = itemKey;
            }
        } else {
            reqKey = AEItemKey.of(filter);
        }

        if (reqKey != null) {
            GenericStack extracted = this.target.extractItems(new GenericStack(reqKey, amount), type, this.src);
            if (extracted != null) {
                return ((AEItemKey) extracted.what()).toStack((int) extracted.amount());
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateRemove(final int amount, final ItemStack filter, final IInventoryDestination destination) {
        return this.doRemoveItems(amount, filter, destination, Actionable.SIMULATE);
    }

    @Override
    public ItemStack removeSimilarItems(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode,
            final IInventoryDestination destination) {
        if (filter.isEmpty()) {
            return this.doRemoveItems(amount, null, destination, Actionable.MODULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.MODULATE, fuzzyMode);
    }

    private ItemStack doRemoveItemsFuzzy(final int amount, final ItemStack filter,
            final IInventoryDestination destination, final Actionable type, final FuzzyMode fuzzyMode) {
        final AEItemKey reqKey = AEItemKey.of(filter);
        if (reqKey == null) {
            return ItemStack.EMPTY;
        }

        for (final var entry : ImmutableList.copyOf(this.getList().findFuzzy(reqKey, fuzzyMode))) {
            if (entry.getLongValue() > 0) {
                GenericStack extracted = this.target.extractItems(
                        new GenericStack(entry.getKey(), amount), type, this.src);
                if (extracted != null) {
                    return ((AEItemKey) extracted.what()).toStack((int) extracted.amount());
                }
            }
        }

        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateSimilarRemove(final int amount, final ItemStack filter, final FuzzyMode fuzzyMode,
            final IInventoryDestination destination) {
        if (filter.isEmpty()) {
            return this.doRemoveItems(amount, ItemStack.EMPTY, destination, Actionable.SIMULATE);
        }
        return this.doRemoveItemsFuzzy(amount, filter, destination, Actionable.SIMULATE, fuzzyMode);
    }

    @Override
    public ItemStack addItems(final ItemStack toBeAdded) {
        AEItemKey key = AEItemKey.of(toBeAdded);
        if (key != null) {
            final GenericStack out = this.target.injectItems(new GenericStack(key, toBeAdded.getCount()), Actionable.MODULATE, this.src);
            if (out != null) {
                return ((AEItemKey) out.what()).toStack((int) out.amount());
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateAdd(final ItemStack toBeSimulated) {
        AEItemKey key = AEItemKey.of(toBeSimulated);
        if (key != null) {
            final GenericStack out = this.target.injectItems(new GenericStack(key, toBeSimulated.getCount()), Actionable.SIMULATE, this.src);
            if (out != null) {
                return ((AEItemKey) out.what()).toStack((int) out.amount());
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean containsItems() {
        return !this.getList().isEmpty();
    }

    int getMaxSlots() {
        return this.maxSlots;
    }

    void setMaxSlots(final int maxSlots) {
        this.maxSlots = maxSlots;
    }

}
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

package appeng.util.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * Multi-type union {@link IItemList} implementation backed by {@link KeyCounter}.
 * <p>
 * Internally, stacks are partitioned by {@link AEKeyType} and stored in three
 * {@link KeyCounter} buckets: stored amounts, craftable flags, and requestable amounts.
 * This eliminates the dependency on the legacy {@code IAEStackType.createList()} and aligns
 * the internal data model with the new AEKey system.
 * <p>
 * The class still implements the legacy {@link IItemList<IAEStackBase>} interface
 * for backward compatibility. All legacy operations perform AEKey ↔ IAEStack
 * conversion at the boundary.
 */
public final class IAEStackList implements IItemList<IAEStackBase>, IMixedStackList {

    // ==================== Internal KeyCounter buckets ====================

    // Stored amounts per key
    private final KeyCounter stored = new KeyCounter();
    // Craftable keys (amount is always 1 for membership tracking)
    private final KeyCounter craftable = new KeyCounter();
    // Requestable amounts per key
    private final KeyCounter requestable = new KeyCounter();

    // Per-type sub-views for efficient iteration by type
    private final Map<AEKeyType, TypeBucket> buckets = new IdentityHashMap<>();

    public IAEStackList() {
        for (AEKeyType type : AEKeyType.getAllTypes()) {
            this.buckets.put(type, new TypeBucket(type));
        }
    }

    // ==================== Type bucket helper ====================

    /**
     * A per-type view over the three global KeyCounters.
     * Used for size counting and type-scoped iteration.
     */
    private final class TypeBucket {
        final AEKeyType type;

        TypeBucket(AEKeyType type) {
            this.type = type;
        }

        boolean isEmpty() {
            for (var entry : stored) {
                if (entry.getKey().getType() == type && entry.getLongValue() > 0) {
                    return false;
                }
            }
            for (var entry : craftable) {
                if (entry.getKey().getType() == type && entry.getLongValue() > 0) {
                    return false;
                }
            }
            return true;
        }

        int size() {
            Set<AEKey> seen = new java.util.HashSet<>();
            for (var entry : stored) {
                if (entry.getKey().getType() == type && entry.getLongValue() > 0) {
                    seen.add(entry.getKey());
                }
            }
            for (var entry : craftable) {
                if (entry.getKey().getType() == type && entry.getLongValue() > 0) {
                    seen.add(entry.getKey());
                }
            }
            return seen.size();
        }
    }

    // ==================== IItemList: add ====================

    @Override
    public void add(final IAEStackBase option) {
        if (option instanceof IAEStack<?>) {
            addFromStack((IAEStack<?>) option);
        }
    }

    @Override
    public void add(final IAEStack<?> option) {
        if (option != null) {
            addFromStack(option);
        }
    }

    private void addFromStack(IAEStack<?> option) {
        if (option == null) return;
        AEKey key = option.toAEKey();
        if (key == null) return;
        stored.add(key, option.getStackSize());
        if (option.isCraftable()) {
            craftable.add(key, 1);
        }
        requestable.add(key, option.getCountRequestable());
    }

    // ==================== IItemList: findPrecise ====================

    @Override
    public IAEStackBase findPrecise(final IAEStackBase stack) {
        return stack == null ? null : findPreciseInternal(stack);
    }

    @Override
    public IAEStack<?> findPrecise(final IAEStack<?> stack) {
        return stack == null ? null : findPreciseInternal(stack);
    }

    @Nullable
    private IAEStack<?> findPreciseInternal(final IAEStackBase stack) {
        if (!(stack instanceof IAEStack<?>)) return null;
        AEKey key = ((IAEStack<?>) stack).toAEKey();
        if (key == null) return null;

        long storedAmount = stored.get(key);
        boolean isCraftable = craftable.get(key) > 0;
        long requestableAmount = requestable.get(key);

        if (storedAmount == 0 && !isCraftable && requestableAmount == 0) {
            return null;
        }

        IAEStack<?> result = key.toIAEStack(storedAmount);
        if (result == null) return null;
        result.setCraftable(isCraftable);
        result.setCountRequestable(requestableAmount);
        return result;
    }

    // ==================== IItemList: findFuzzy ====================

    @Override
    public Collection<IAEStackBase> findFuzzy(final IAEStackBase filter, final FuzzyMode fuzzy) {
        return filter == null ? null : findFuzzyBase(filter, fuzzy);
    }

    @Override
    public Collection<IAEStack<?>> findFuzzy(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        return filter == null ? null : findFuzzyTyped(filter, fuzzy);
    }

    @SuppressWarnings("unchecked")
    private Collection<IAEStackBase> findFuzzyBase(final IAEStackBase filter, final FuzzyMode fuzzy) {
        return (Collection<IAEStackBase>) (Collection<?>) findFuzzyTyped((IAEStack<?>) filter, fuzzy);
    }

    private Collection<IAEStack<?>> findFuzzyTyped(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        AEKey key = filter.toAEKey();
        if (key == null) return new ArrayList<>();

        Collection<Object2LongMap.Entry<AEKey>> fuzzyEntries = stored.findFuzzy(key, fuzzy);
        List<IAEStack<?>> result = new ArrayList<>();
        for (var entry : fuzzyEntries) {
            AEKey foundKey = entry.getKey();
            long storedAmount = entry.getLongValue();
            boolean isCraftable = craftable.get(foundKey) > 0;
            long requestableAmount = requestable.get(foundKey);

            IAEStack<?> stack = foundKey.toIAEStack(storedAmount);
            if (stack != null) {
                stack.setCraftable(isCraftable);
                stack.setCountRequestable(requestableAmount);
                result.add(stack);
            }
        }
        return result;
    }

    // ==================== IItemList: isEmpty / size ====================

    @Override
    public boolean isEmpty() {
        return stored.isEmpty() && craftable.isEmpty();
    }

    @Override
    public int size() {
        Set<AEKey> seen = new java.util.HashSet<>();
        for (var entry : stored) {
            if (entry.getLongValue() > 0) {
                seen.add(entry.getKey());
            }
        }
        for (var entry : craftable) {
            if (entry.getLongValue() > 0) {
                seen.add(entry.getKey());
            }
        }
        return seen.size();
    }

    // ==================== IItemList: addStorage ====================

    @Override
    public void addStorage(final IAEStackBase option) {
        if (option instanceof IAEStack<?>) {
            addStorageFromStack((IAEStack<?>) option);
        }
    }

    @Override
    public void addStorage(final IAEStack<?> option) {
        if (option != null) {
            addStorageFromStack(option);
        }
    }

    private void addStorageFromStack(IAEStack<?> option) {
        if (option == null) return;
        AEKey key = option.toAEKey();
        if (key == null) return;
        stored.add(key, option.getStackSize());
    }

    // ==================== IItemList: addCrafting ====================

    @Override
    public void addCrafting(final IAEStackBase option) {
        if (option instanceof IAEStack<?>) {
            addCraftingFromStack((IAEStack<?>) option);
        }
    }

    @Override
    public void addCrafting(final IAEStack<?> option) {
        if (option != null) {
            addCraftingFromStack(option);
        }
    }

    private void addCraftingFromStack(IAEStack<?> option) {
        if (option == null) return;
        AEKey key = option.toAEKey();
        if (key == null) return;
        if (option.isCraftable()) {
            craftable.add(key, 1);
        }
    }

    // ==================== IItemList: addRequestable ====================

    @Override
    public void addRequestable(final IAEStackBase option) {
        if (option instanceof IAEStack<?>) {
            addRequestableFromStack((IAEStack<?>) option);
        }
    }

    @Override
    public void addRequestable(final IAEStack<?> option) {
        if (option != null) {
            addRequestableFromStack(option);
        }
    }

    private void addRequestableFromStack(IAEStack<?> option) {
        if (option == null) return;
        AEKey key = option.toAEKey();
        if (key == null) return;
        requestable.add(key, option.getCountRequestable());
    }

    // ==================== IItemList: getFirstItem / getFirstMixedItem ====================

    @Override
    public IAEStackBase getFirstItem() {
        return this.getFirstMixedItem();
    }

    @Override
    public IAEStack<?> getFirstMixedItem() {
        for (var entry : stored) {
            if (entry.getLongValue() > 0) {
                AEKey key = entry.getKey();
                IAEStack<?> stack = key.toIAEStack(entry.getLongValue());
                if (stack != null) {
                    stack.setCraftable(craftable.get(key) > 0);
                    stack.setCountRequestable(requestable.get(key));
                    return stack;
                }
            }
        }
        // If no stored items, check craftable-only items
        for (var entry : craftable) {
            if (entry.getLongValue() > 0) {
                AEKey key = entry.getKey();
                if (stored.get(key) == 0) {
                    IAEStack<?> stack = key.toIAEStack(0);
                    if (stack != null) {
                        stack.setCraftable(true);
                        stack.setCountRequestable(requestable.get(key));
                        return stack;
                    }
                }
            }
        }
        return null;
    }

    // ==================== IItemList: iterator ====================

    @Override
    @Nonnull
    public Iterator<IAEStackBase> iterator() {
        // Build a combined list: stored entries first, then craftable-only entries
        List<IAEStackBase> allStacks = new ArrayList<>();

        Set<AEKey> emitted = new java.util.HashSet<>();

        // Phase 1: emit all stored entries
        for (var entry : stored) {
            if (entry.getLongValue() > 0) {
                AEKey key = entry.getKey();
                IAEStack<?> stack = key.toIAEStack(entry.getLongValue());
                if (stack != null) {
                    stack.setCraftable(craftable.get(key) > 0);
                    stack.setCountRequestable(requestable.get(key));
                    allStacks.add(stack);
                    emitted.add(key);
                }
            }
        }

        // Phase 2: emit craftable-only entries (stored == 0 but craftable)
        for (var entry : craftable) {
            if (entry.getLongValue() > 0) {
                AEKey key = entry.getKey();
                if (!emitted.contains(key)) {
                    IAEStack<?> stack = key.toIAEStack(0);
                    if (stack != null) {
                        stack.setCraftable(true);
                        stack.setCountRequestable(requestable.get(key));
                        allStacks.add(stack);
                        emitted.add(key);
                    }
                }
            }
        }

        return new MeaningfulStackIterator(allStacks.iterator());
    }

    // ==================== IItemList: resetStatus ====================

    @Override
    public void resetStatus() {
        stored.reset();
        craftable.reset();
        requestable.reset();
    }

    // ==================== IItemList: getStackType ====================

    @Override
    @Nullable
    public IAEStackType getStackType() {
        // Multi-type union list returns null
        return null;
    }

    // ==================== Internal KeyCounter access ====================

    /**
     * @return the stored amounts KeyCounter
     */
    public KeyCounter getStoredCounter() {
        return stored;
    }

    /**
     * @return the craftable keys KeyCounter
     */
    public KeyCounter getCraftableCounter() {
        return craftable;
    }

    /**
     * @return the requestable amounts KeyCounter
     */
    public KeyCounter getRequestableCounter() {
        return requestable;
    }

    // ==================== Meaningful stack iterator ====================

    /**
     * A meaningful stack iterator that skips stacks with stackSize == 0
     * and isMeaningful() == false.
     */
    private static class MeaningfulStackIterator implements Iterator<IAEStackBase> {

        private final Iterator<IAEStackBase> parent;
        private IAEStackBase next;

        MeaningfulStackIterator(final Iterator<IAEStackBase> parent) {
            this.parent = parent;
            this.next = seekNext();
        }

        @Override
        public boolean hasNext() {
            return this.next != null;
        }

        @Override
        public IAEStackBase next() {
            if (this.next == null) {
                throw new NoSuchElementException();
            }
            IAEStackBase result = this.next;
            this.next = seekNext();
            return result;
        }

        @Override
        public void remove() {
            this.parent.remove();
        }

        private IAEStackBase seekNext() {
            while (this.parent.hasNext()) {
                IAEStackBase item = this.parent.next();
                if (item != null && item.isMeaningful()) {
                    return item;
                }
            }
            return null;
        }
    }
}

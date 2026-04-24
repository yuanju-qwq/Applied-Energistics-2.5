/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.stacks;

import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Iterators;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import appeng.api.config.FuzzyMode;

/**
 * Non-generic, heterogeneous counter that maps {@link AEKey} instances to {@code long} amounts.
 * <p/>
 * This is the AEKey replacement for the old generic {@code IItemList<T>}.
 * All key types (items, fluids, etc.) can coexist naturally in a single KeyCounter.
 * <p/>
 * Internally, keys are partitioned by their {@link AEKey#getPrimaryKey() primary key}
 * into {@link VariantCounter} sub-indices. This provides O(1) primary-key lookup
 * while still supporting fuzzy range queries for damageable items.
 */
public final class KeyCounter implements Iterable<Object2LongMap.Entry<AEKey>> {

    // Primary key → variant sub-index
    private final Map<Object, VariantCounter> lists = new IdentityHashMap<>();

    // ==================== Fuzzy search ====================

    /**
     * Finds all entries matching the given key under fuzzy semantics.
     *
     * @param key   the key to search for
     * @param fuzzy the fuzzy mode to apply
     * @return collection of matching entries (may be a live view)
     */
    public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
        Objects.requireNonNull(key, "key");
        return getSubIndex(key).findFuzzy(key, fuzzy);
    }

    // ==================== Mutation ====================

    /**
     * Adds the given amount to the current amount for the given key.
     * If the key is not yet tracked, it is created with the given amount.
     */
    public void add(AEKey key, long amount) {
        Objects.requireNonNull(key, "key");
        getSubIndex(key).add(key, amount);
    }

    /**
     * Subtracts the given amount from the current amount for the given key.
     */
    public void remove(AEKey key, long amount) {
        add(key, -amount);
    }

    /**
     * Sets the amount for the given key to exactly the given value.
     */
    public void set(AEKey key, long amount) {
        getSubIndex(key).set(key, amount);
    }

    /**
     * Adds all entries from another KeyCounter into this one.
     */
    public void addAll(KeyCounter other) {
        for (var entry : other.lists.entrySet()) {
            var ourSubIndex = lists.get(entry.getKey());
            if (ourSubIndex == null) {
                lists.put(entry.getKey(), entry.getValue().copy());
            } else {
                ourSubIndex.addAll(entry.getValue());
            }
        }
    }

    /**
     * Subtracts all entries from another KeyCounter from this one.
     */
    public void removeAll(KeyCounter other) {
        for (var entry : other.lists.entrySet()) {
            var ourSubIndex = lists.get(entry.getKey());
            if (ourSubIndex == null) {
                var copied = entry.getValue().copy();
                copied.invert();
                lists.put(entry.getKey(), copied);
            } else {
                ourSubIndex.removeAll(entry.getValue());
            }
        }
    }

    /**
     * Removes all entries whose amount is exactly zero.
     */
    public void removeZeros() {
        var iterator = lists.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var variantList = entry.getValue();
            variantList.removeZeros();
            if (variantList.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * Sets all amounts to zero without removing entries.
     */
    public void reset() {
        for (var list : lists.values()) {
            list.reset();
        }
    }

    /**
     * Removes all entries.
     */
    public void clear() {
        for (var list : lists.values()) {
            list.clear();
        }
    }

    // ==================== Query ====================

    /**
     * @return the current amount for the given key, or 0 if not tracked
     */
    public long get(AEKey key) {
        Objects.requireNonNull(key);
        var subIndex = lists.get(key.getPrimaryKey());
        if (subIndex == null) {
            return 0;
        }
        return subIndex.get(key);
    }

    /**
     * @return the first key in this counter, or null if empty
     */
    @Nullable
    public AEKey getFirstKey() {
        var e = getFirstEntry();
        return e != null ? e.getKey() : null;
    }

    /**
     * @return the first key of the given class, or null if none found
     */
    @Nullable
    public <T extends AEKey> T getFirstKey(Class<T> keyClass) {
        var e = getFirstEntry(keyClass);
        return e != null ? keyClass.cast(e.getKey()) : null;
    }

    /**
     * @return the first entry in this counter, or null if empty
     */
    @Nullable
    public Object2LongMap.Entry<AEKey> getFirstEntry() {
        for (var value : lists.values()) {
            var it = value.iterator();
            if (it.hasNext()) {
                return it.next();
            }
        }
        return null;
    }

    /**
     * @return the first entry whose key matches the given class, or null
     */
    @Nullable
    public <T extends AEKey> Object2LongMap.Entry<AEKey> getFirstEntry(Class<T> keyClass) {
        for (var value : lists.values()) {
            var it = value.iterator();
            if (it.hasNext()) {
                var entry = it.next();
                if (keyClass.isInstance(entry.getKey())) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * @return a snapshot set of all keys in this counter
     */
    public Set<AEKey> keySet() {
        var keys = new HashSet<AEKey>(size());
        for (var list : lists.values()) {
            for (var entry : list) {
                keys.add(entry.getKey());
            }
        }
        return keys;
    }

    // ==================== Size / emptiness ====================

    public boolean isEmpty() {
        for (var list : lists.values()) {
            if (!list.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        int tot = 0;
        for (var list : lists.values()) {
            tot += list.size();
        }
        return tot;
    }

    // ==================== Iteration ====================

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        return Iterators.concat(
                Iterators.transform(lists.values().iterator(), VariantCounter::iterator));
    }

    // ==================== Internal ====================

    private VariantCounter getSubIndex(AEKey key) {
        var subIndex = lists.get(key.getPrimaryKey());
        if (subIndex == null) {
            subIndex = VariantCounter.create(key);
            lists.put(key.getPrimaryKey(), subIndex);
        }
        return subIndex;
    }
}

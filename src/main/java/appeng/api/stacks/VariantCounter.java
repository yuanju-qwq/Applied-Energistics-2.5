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
import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongSortedMap;

import appeng.api.config.FuzzyMode;

/**
 * Tallies a negative or positive amount for sub-variants of a single primary key.
 * <p/>
 * Variants are stored in either a hash map (for keys that don't support fuzzy range search)
 * or an AVL tree map (for damageable items that support fuzzy range search).
 * <p/>
 * This is a package-private implementation detail of {@link KeyCounter}.
 */
abstract class VariantCounter implements Iterable<Object2LongMap.Entry<AEKey>> {

    /**
     * When true, keys mapped to zero are automatically skipped during iteration and removed.
     */
    private boolean dropZeros;

    /**
     * Creates a variant counter appropriate for the given key.
     * If the key supports fuzzy range search, an ordered (AVL tree) map is used;
     * otherwise a hash map is used.
     */
    public static VariantCounter create(AEKey keyTemplate) {
        if (keyTemplate.getFuzzySearchMaxValue() > 0) {
            return new FuzzyVariantMap();
        } else {
            return new UnorderedVariantMap();
        }
    }

    public boolean isDropZeros() {
        return dropZeros;
    }

    public void setDropZeros(boolean dropZeros) {
        this.dropZeros = dropZeros;
    }

    public long get(AEKey key) {
        return this.getRecords().getOrDefault(key, 0L);
    }

    public void add(AEKey key, long amount) {
        long currentValue = this.getRecords().getLong(key);
        this.getRecords().put(key, currentValue + amount);
    }

    public void set(AEKey key, long amount) {
        if (dropZeros && amount == 0) {
            getRecords().removeLong(key);
        } else {
            getRecords().put(key, amount);
        }
    }

    public void addAll(VariantCounter other) {
        for (var entry : other.getRecords().object2LongEntrySet()) {
            add(entry.getKey(), entry.getLongValue());
        }
    }

    public void removeAll(VariantCounter other) {
        for (var entry : other.getRecords().object2LongEntrySet()) {
            add(entry.getKey(), -entry.getLongValue());
        }
    }

    public abstract Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy);

    public int size() {
        if (!dropZeros) {
            return getRecords().size();
        }
        var size = 0;
        for (var value : getRecords().values()) {
            if (value != 0) {
                size++;
            }
        }
        return size;
    }

    public boolean isEmpty() {
        if (!dropZeros) {
            return getRecords().isEmpty();
        }
        for (var value : getRecords().values()) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Iterator<Object2LongMap.Entry<AEKey>> iterator() {
        if (!dropZeros) {
            return getRecords().object2LongEntrySet().iterator();
        }
        return new NonDefaultIterator();
    }

    abstract Object2LongMap<AEKey> getRecords();

    /**
     * Sets all amounts to zero (or clears if dropZeros is enabled).
     */
    public void reset() {
        if (dropZeros) {
            getRecords().clear();
        } else {
            getRecords().replaceAll((key, value) -> 0L);
        }
    }

    public void clear() {
        getRecords().clear();
    }

    public abstract VariantCounter copy();

    /**
     * Negates all amounts.
     */
    public void invert() {
        for (var entry : getRecords().object2LongEntrySet()) {
            entry.setValue(-entry.getLongValue());
        }
    }

    /**
     * Removes entries whose amount is exactly zero.
     */
    public void removeZeros() {
        var it = getRecords().values().iterator();
        while (it.hasNext()) {
            var entry = it.nextLong();
            if (entry == 0) {
                it.remove();
            }
        }
    }

    /**
     * Iterator that skips (and removes) entries with amount == 0.
     */
    private class NonDefaultIterator implements Iterator<Object2LongMap.Entry<AEKey>> {
        private final Iterator<Object2LongMap.Entry<AEKey>> parent;
        private Object2LongMap.Entry<AEKey> next;

        public NonDefaultIterator() {
            this.parent = getRecords().object2LongEntrySet().iterator();
            this.next = seekNext();
        }

        @Override
        public boolean hasNext() {
            return this.next != null;
        }

        @Override
        public Object2LongMap.Entry<AEKey> next() {
            if (this.next == null) {
                throw new NoSuchElementException();
            }
            var result = this.next;
            this.next = this.seekNext();
            return result;
        }

        private Object2LongMap.Entry<AEKey> seekNext() {
            while (this.parent.hasNext()) {
                var entry = this.parent.next();
                if (entry.getLongValue() == 0) {
                    this.parent.remove();
                } else {
                    return entry;
                }
            }
            return null;
        }
    }

    /**
     * Variant map for keys that do NOT support fuzzy range search (e.g. fluids, non-damageable items).
     * Uses a hash map for O(1) lookup.
     */
    private static class UnorderedVariantMap extends VariantCounter {
        private final Object2LongMap<AEKey> records = new Object2LongOpenHashMap<>();

        /**
         * Since this type does not support fuzzy range lookups,
         * findFuzzy returns ALL records (effectively matching by primary key only, ignoring NBT).
         */
        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey filter, FuzzyMode fuzzy) {
            return records.object2LongEntrySet();
        }

        @Override
        Object2LongMap<AEKey> getRecords() {
            return records;
        }

        @Override
        public VariantCounter copy() {
            var result = new UnorderedVariantMap();
            result.records.putAll(records);
            return result;
        }
    }

    /**
     * Variant map for damageable items that support fuzzy range search.
     * Uses an AVL tree map sorted by damage value, enabling efficient range queries.
     */
    private static class FuzzyVariantMap extends VariantCounter {
        private final Object2LongSortedMap<AEKey> records = FuzzySearch.createMap2Long();

        @Override
        public Collection<Object2LongMap.Entry<AEKey>> findFuzzy(AEKey key, FuzzyMode fuzzy) {
            return FuzzySearch.findFuzzy(records, key, fuzzy).object2LongEntrySet();
        }

        @Override
        Object2LongMap<AEKey> getRecords() {
            return this.records;
        }

        @Override
        public VariantCounter copy() {
            var result = new FuzzyVariantMap();
            result.records.putAll(records);
            return result;
        }
    }
}

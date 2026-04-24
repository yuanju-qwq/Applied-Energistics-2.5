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

import java.util.Comparator;
import java.util.SortedMap;

import com.github.bsideup.jabel.Desugar;
import com.google.common.base.Preconditions;

import it.unimi.dsi.fastutil.objects.Object2LongAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;

import appeng.api.config.FuzzyMode;

/**
 * Provides fuzzy search support for AEKey-based sorted maps.
 * <p/>
 * Keys are sorted by their {@link AEKey#getFuzzySearchValue() fuzzy search value} (i.e. damage)
 * using an AVL tree map, and range queries are done via {@link SortedMap#subMap}.
 */
final class FuzzySearch {

    static final KeyComparator COMPARATOR = new KeyComparator();

    private FuzzySearch() {
    }

    /**
     * Creates a sorted map whose keys are ordered by fuzzy search value,
     * suitable for range queries via {@link #findFuzzy}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <K extends AEKey, V> Object2ObjectAVLTreeMap<K, V> createMap() {
        return new Object2ObjectAVLTreeMap(COMPARATOR);
    }

    /**
     * Creates a sorted key-to-long map whose keys are ordered by fuzzy search value,
     * suitable for range queries via {@link #findFuzzy}.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <K extends AEKey> Object2LongAVLTreeMap<K> createMap2Long() {
        return new Object2LongAVLTreeMap(COMPARATOR);
    }

    /**
     * Performs a fuzzy search on a sorted map created by {@link #createMap()} or {@link #createMap2Long()}.
     *
     * @return a sub-map view containing all entries matching the fuzzy criteria
     */
    @SuppressWarnings({ "unchecked" })
    public static <T extends SortedMap<K, V>, K, V> T findFuzzy(T map, AEKey key, FuzzyMode fuzzy) {
        var lowerBound = makeLowerBound(key, fuzzy);
        var upperBound = makeUpperBound(key, fuzzy);
        Preconditions.checkState(lowerBound.itemDamage() > upperBound.itemDamage());

        // We can use lower/upper bound in this map for queries because our comparator
        // specifically supports dealing with FuzzyBound instances
        return (T) map.subMap((K) lowerBound, (K) upperBound);
    }

    /**
     * Represents a synthetic key used as a boundary for fuzzy subMap queries.
     * Not an actual AEKey — only used for comparison purposes.
     */
    @Desugar
    record FuzzyBound(int itemDamage) {
    }

    /**
     * Comparator that establishes a total ordering over AEKey instances of the same item,
     * sorted by damage value. Also accepts {@link FuzzyBound} for range query boundaries.
     * <p/>
     * Since only real AEKeys are stored in the map and FuzzyBounds are only used
     * as subMap boundaries, at most one argument will be a FuzzyBound.
     */
    private static class KeyComparator implements Comparator<Object> {
        @Override
        public int compare(Object a, Object b) {
            // Extract damage value and (optional) key from each argument
            FuzzyBound boundA = null;
            AEKey stackA = null;
            int fuzzyOrderB;
            if (a instanceof FuzzyBound fb) {
                boundA = fb;
                fuzzyOrderB = fb.itemDamage();
            } else {
                stackA = (AEKey) a;
                fuzzyOrderB = stackA.getFuzzySearchValue();
            }

            FuzzyBound boundB = null;
            AEKey stackB = null;
            int fuzzyOrderA;
            if (b instanceof FuzzyBound fb) {
                boundB = fb;
                fuzzyOrderA = fb.itemDamage();
            } else {
                stackB = (AEKey) b;
                fuzzyOrderA = stackB.getFuzzySearchValue();
            }

            // When either argument is a FuzzyBound, only compare damage values
            // (used to select a damage range from the map)
            if (boundA != null || boundB != null) {
                return Integer.compare(fuzzyOrderA, fuzzyOrderB);
            }

            if (stackA.equals(stackB)) {
                return 0;
            }

            // Damaged items are sorted before undamaged items
            var fuzzyOrder = Integer.compare(fuzzyOrderA, fuzzyOrderB);
            if (fuzzyOrder != 0) {
                return fuzzyOrder;
            }

            // Tie-breaker: identity hash code for a consistent total order
            // (only the damage ordering needs to be semantically meaningful)
            return Long.compare(System.identityHashCode(stackA), System.identityHashCode(stackB));
        }
    }

    /**
     * Minecraft damage is 0 for undamaged and increases as item wears out.
     * To include undamaged items in subMap ranges, we use -1 as a lower-than-min value.
     */
    private static final int MIN_DAMAGE_VALUE = -1;

    /**
     * Stack order is most-damaged to least-damaged, so the "lower bound"
     * is actually a higher damage number than the "upper bound".
     */
    static FuzzyBound makeLowerBound(AEKey key, FuzzyMode fuzzy) {
        var maxValue = key.getFuzzySearchMaxValue();
        Preconditions.checkState(maxValue > 0,
                "Cannot use fuzzy search on keys that don't have a fuzzy max value: %s", key);

        int damage;
        if (fuzzy == FuzzyMode.IGNORE_ALL) {
            damage = maxValue;
        } else {
            var breakpoint = fuzzy.calculateBreakPoint(maxValue);
            damage = key.getFuzzySearchValue() <= breakpoint ? breakpoint : maxValue;
        }
        return new FuzzyBound(damage);
    }

    /**
     * The upper bound is exclusive and has a lower damage number.
     */
    static FuzzyBound makeUpperBound(AEKey key, FuzzyMode fuzzy) {
        var maxValue = key.getFuzzySearchMaxValue();
        Preconditions.checkState(maxValue > 0,
                "Cannot use fuzzy search on keys that don't have a fuzzy max value: %s", key);

        int damage;
        if (fuzzy == FuzzyMode.IGNORE_ALL) {
            damage = MIN_DAMAGE_VALUE;
        } else {
            var breakpoint = fuzzy.calculateBreakPoint(maxValue);
            damage = key.getFuzzySearchValue() <= breakpoint ? MIN_DAMAGE_VALUE : breakpoint;
        }
        return new FuzzyBound(damage);
    }
}

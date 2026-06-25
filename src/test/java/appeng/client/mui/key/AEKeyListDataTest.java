/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.client.mui.key;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.testkey.TestKey;

/**
 * Unit tests for {@link AEKeyListData}.
 *
 * <p>Verifies each {@code postUpdate} overload, snapshot caching behavior, lookup helpers
 * ({@link AEKeyListData#getAmount}, {@link AEKeyListData#isCraftable}), indexed access,
 * and the {@link KeyCounter} ingress path used by the terminal list update flow.
 */
class AEKeyListDataTest {

    private AEKeyListData data;

    @BeforeEach
    void setUp() {
        data = new AEKeyListData();
    }

    // ========== Empty state ==========

    @Test
    void newDataIsEmpty() {
        assertTrue(data.isEmpty());
        assertEquals(0, data.size());
        assertTrue(data.getSnapshot().isEmpty());
    }

    @Test
    void getOnEmptyDataReturnsNull() {
        assertNull(data.get(0));
        assertNull(data.get(-1));
        assertNull(data.get(100));
    }

    @Test
    void getAmountOnUnknownKeyReturnsZero() {
        TestKey key = new TestKey(1);
        assertEquals(0L, data.getAmount(key));
        assertFalse(data.isCraftable(key));
    }

    // ========== postUpdate(AEKey, long, boolean) ==========

    @Test
    void postUpdateKeyAmountCraftableStoresEntry() {
        TestKey key = new TestKey(1);

        data.postUpdate(key, 100L, true);

        assertEquals(1, data.size());
        assertEquals(100L, data.getAmount(key));
        assertTrue(data.isCraftable(key));
    }

    @Test
    void postUpdateSameKeyOverwritesPreviousEntry() {
        TestKey key = new TestKey(1);
        data.postUpdate(key, 50L, false);
        data.postUpdate(key, 200L, true);

        assertEquals(1, data.size());
        assertEquals(200L, data.getAmount(key));
        assertTrue(data.isCraftable(key));
    }

    // ========== postUpdate(GenericStack, boolean) ==========

    @Test
    void postUpdateGenericStackDelegatesToKeyAmount() {
        TestKey key = new TestKey(2);
        GenericStack stack = new GenericStack(key, 75L);

        data.postUpdate(stack, false);

        assertEquals(75L, data.getAmount(key));
        assertFalse(data.isCraftable(key));
    }

    // ========== postUpdate(AEKeyDisplayEntry) ==========

    @Test
    void postUpdateEntryStoresIt() {
        TestKey key = new TestKey(3);
        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(key, 30L, true);

        data.postUpdate(entry);

        assertEquals(30L, data.getAmount(key));
        assertTrue(data.isCraftable(key));
    }

    // ========== Batch updates ==========

    @Test
    void postGenericStackUpdateAddsAllStacks() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        TestKey k3 = new TestKey(3);
        List<GenericStack> stacks = Arrays.asList(
                new GenericStack(k1, 10L),
                new GenericStack(k2, 20L),
                new GenericStack(k3, 30L));

        data.postGenericStackUpdate(stacks, false);

        assertEquals(3, data.size());
        assertEquals(10L, data.getAmount(k1));
        assertEquals(20L, data.getAmount(k2));
        assertEquals(30L, data.getAmount(k3));
    }

    @Test
    void postEntryUpdateAddsAllEntries() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        List<AEKeyDisplayEntry> entries = Arrays.asList(
                AEKeyDisplayEntry.of(k1, 5L, false),
                AEKeyDisplayEntry.of(k2, 8L, true));

        data.postEntryUpdate(entries);

        assertEquals(2, data.size());
        assertFalse(data.isCraftable(k1));
        assertTrue(data.isCraftable(k2));
    }

    // ========== KeyCounter ingress ==========

    @Test
    void postKeyCounterUpdateTransfersAllAmounts() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        KeyCounter counter = new KeyCounter();
        counter.add(k1, 100L);
        counter.add(k2, 250L);

        data.postKeyCounterUpdate(counter, false);

        assertEquals(2, data.size());
        assertEquals(100L, data.getAmount(k1));
        assertEquals(250L, data.getAmount(k2));
    }

    @Test
    void postKeyCounterUpdateWithEmptyCounterLeavesDataEmpty() {
        data.postKeyCounterUpdate(new KeyCounter(), false);
        assertTrue(data.isEmpty());
    }

    // ========== Snapshot caching ==========

    @Test
    void getSnapshotIsCachedUntilMutation() {
        TestKey k1 = new TestKey(1);
        data.postUpdate(k1, 10L, false);

        List<AEKeyDisplayEntry> first = data.getSnapshot();
        List<AEKeyDisplayEntry> second = data.getSnapshot();

        // Same instance until mutation
        assertTrue(first == second, "snapshot should be cached until mutation");

        TestKey k2 = new TestKey(2);
        data.postUpdate(k2, 20L, false);

        List<AEKeyDisplayEntry> third = data.getSnapshot();
        assertFalse(first == third, "snapshot should be regenerated after mutation");
        assertEquals(2, third.size());
    }

    @Test
    void getSnapshotIsUnmodifiable() {
        TestKey k1 = new TestKey(1);
        data.postUpdate(k1, 10L, false);

        List<AEKeyDisplayEntry> snapshot = data.getSnapshot();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.add(AEKeyDisplayEntry.of(new TestKey(2), 1L, false)));
    }

    // ========== Indexed access ==========

    @Test
    void getReturnsEntriesInInsertionOrder() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        TestKey k3 = new TestKey(3);

        data.postUpdate(k1, 10L, false);
        data.postUpdate(k2, 20L, false);
        data.postUpdate(k3, 30L, false);

        assertEquals(k1, data.get(0).key());
        assertEquals(k2, data.get(1).key());
        assertEquals(k3, data.get(2).key());
        assertEquals(10L, data.get(0).amount());
        assertEquals(30L, data.get(2).amount());
    }

    @Test
    void getReturnsNonNullForValidIndex() {
        data.postUpdate(new TestKey(1), 1L, false);
        assertNotNull(data.get(0));
    }

    // ========== clear ==========

    @Test
    void clearEmptiesDataAndInvalidatesSnapshot() {
        data.postUpdate(new TestKey(1), 10L, false);
        List<AEKeyDisplayEntry> oldSnapshot = data.getSnapshot();
        assertEquals(1, oldSnapshot.size());

        data.clear();

        assertTrue(data.isEmpty());
        assertEquals(0, data.size());
        assertTrue(data.getSnapshot().isEmpty());
        // New snapshot must be a different, empty instance
        assertFalse(data.getSnapshot() == oldSnapshot);
    }

    // ========== Iteration order stability ==========

    @Test
    void snapshotOrderStableAcrossMultipleUpdates() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);

        data.postUpdate(k1, 1L, false);
        data.postUpdate(k2, 2L, false);

        // Re-update k1; should NOT move it to the end (LinkedHashMap retains insertion order
        // for existing keys unless access-order is enabled, which it is not).
        data.postUpdate(k1, 100L, true);

        List<AEKeyDisplayEntry> snapshot = data.getSnapshot();
        assertEquals(k1, snapshot.get(0).key());
        assertEquals(k2, snapshot.get(1).key());
        assertEquals(100L, snapshot.get(0).amount());
        assertTrue(snapshot.get(0).craftable());
    }
}

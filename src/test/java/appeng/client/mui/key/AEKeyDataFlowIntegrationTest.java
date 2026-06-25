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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.testkey.TestKey;

/**
 * Integration test covering the primary AEKey-only data flow:
 *
 * <pre>
 *   KeyCounter (network/storage layer)
 *        |  postKeyCounterUpdate
 *        v
 *   AEKeyListData (panel data model)
 *        |  getSnapshot
 *        v
 *   List&lt;AEKeyDisplayEntry&gt; (UI rendering layer)
 *        |  toGenericStack / direct field access
 *        v
 *   GenericStack (slot-level model)
 * </pre>
 *
 * <p>These tests intentionally use multiple {@link TestKey} instances and exercise
 * realistic update sequences (initial population, partial replacement, mixed
 * craftable/stored flags, empty updates) to catch consistency bugs that pure unit
 * tests on each individual class would miss.
 */
class AEKeyDataFlowIntegrationTest {

    // ========== End-to-end: KeyCounter -> AEKeyListData -> snapshot ==========
    //
    // Note: KeyCounter uses IdentityHashMap internally, so its iteration order
    // (and therefore AEKeyListData's insertion order when populated via
    // postKeyCounterUpdate) is unspecified. These tests assert presence and
    // values, not positional order.

    @Test
    void fullFlowPreservesKeyAmountAndContents() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        TestKey k3 = new TestKey(3);

        KeyCounter counter = new KeyCounter();
        counter.add(k1, 100L);
        counter.add(k2, 200L);
        counter.add(k3, 300L);

        AEKeyListData data = new AEKeyListData();
        data.postKeyCounterUpdate(counter, false);

        List<AEKeyDisplayEntry> snapshot = data.getSnapshot();
        assertEquals(3, snapshot.size());

        assertEntryEquals(findByTestId(snapshot, 1), k1, 100L, false);
        assertEntryEquals(findByTestId(snapshot, 2), k2, 200L, false);
        assertEntryEquals(findByTestId(snapshot, 3), k3, 300L, false);
    }

    // ========== Craftable flag is propagated ==========

    @Test
    void craftableFlagPropagatesFromCounterToSnapshot() {
        TestKey k1 = new TestKey(1);
        KeyCounter counter = new KeyCounter();
        counter.add(k1, 0L);

        AEKeyListData data = new AEKeyListData();
        data.postKeyCounterUpdate(counter, true);

        AEKeyDisplayEntry entry = data.getSnapshot().get(0);
        assertTrue(entry.craftable());
        assertEquals(0L, entry.amount());
    }

    // ========== Mixed craftable / stored batches ==========

    @Test
    void mixedCraftableAndStoredBatchesUpdateSeparately() {
        TestKey storedKey = new TestKey(1);
        TestKey craftableKey = new TestKey(2);

        AEKeyListData data = new AEKeyListData();

        // First batch: stored
        KeyCounter storedBatch = new KeyCounter();
        storedBatch.add(storedKey, 50L);
        data.postKeyCounterUpdate(storedBatch, false);

        // Second batch: craftable
        KeyCounter craftableBatch = new KeyCounter();
        craftableBatch.add(craftableKey, 0L);
        data.postKeyCounterUpdate(craftableBatch, true);

        assertEquals(2, data.size());
        assertFalse(data.isCraftable(storedKey));
        assertTrue(data.isCraftable(craftableKey));
        assertEquals(50L, data.getAmount(storedKey));
        assertEquals(0L, data.getAmount(craftableKey));
    }

    // ========== Replacement: same key in second batch overwrites ==========

    @Test
    void secondBatchOverwritesAmountForSameKey() {
        TestKey k1 = new TestKey(1);

        AEKeyListData data = new AEKeyListData();

        KeyCounter first = new KeyCounter();
        first.add(k1, 100L);
        data.postKeyCounterUpdate(first, false);

        KeyCounter second = new KeyCounter();
        second.add(k1, 500L);
        data.postKeyCounterUpdate(second, false);

        // Single entry; latest amount wins.
        assertEquals(1, data.size());
        assertEquals(500L, data.getAmount(k1));
        assertFalse(data.isCraftable(k1));
    }

    @Test
    void secondBatchFlipsCraftableFlagForSameKey() {
        TestKey k1 = new TestKey(1);

        AEKeyListData data = new AEKeyListData();

        KeyCounter first = new KeyCounter();
        first.add(k1, 0L);
        data.postKeyCounterUpdate(first, true);

        KeyCounter second = new KeyCounter();
        second.add(k1, 50L);
        data.postKeyCounterUpdate(second, false);

        assertEquals(50L, data.getAmount(k1));
        assertFalse(data.isCraftable(k1));
    }

    // ========== Empty counter doesn't wipe existing data ==========

    @Test
    void emptyBatchDoesNotClearExistingEntries() {
        TestKey k1 = new TestKey(1);
        AEKeyListData data = new AEKeyListData();

        KeyCounter first = new KeyCounter();
        first.add(k1, 100L);
        data.postKeyCounterUpdate(first, false);

        // Empty update should not wipe existing entries.
        data.postKeyCounterUpdate(new KeyCounter(), false);

        assertEquals(1, data.size());
        assertEquals(100L, data.getAmount(k1));
    }

    // ========== Round-trip: snapshot -> GenericStack -> AEKeyVirtualSlot-equivalent ==========

    @Test
    void snapshotEntriesRoundTripToGenericStack() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);

        KeyCounter counter = new KeyCounter();
        counter.add(k1, 10L);
        counter.add(k2, 20L);

        AEKeyListData data = new AEKeyListData();
        data.postKeyCounterUpdate(counter, false);

        for (AEKeyDisplayEntry entry : data.getSnapshot()) {
            GenericStack stack = entry.toGenericStack();
            assertNotNull(stack);
            assertEquals(entry.key(), stack.what());
            assertEquals(entry.amount(), stack.amount());
        }
    }

    // ========== Update semantics across multiple batches ==========

    @Test
    void multipleBatchesPreserveAllEntriesWithLatestAmounts() {
        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        TestKey k3 = new TestKey(3);

        AEKeyListData data = new AEKeyListData();

        // First batch adds k1, k2
        KeyCounter first = new KeyCounter();
        first.add(k1, 1L);
        first.add(k2, 2L);
        data.postKeyCounterUpdate(first, false);

        // Second batch adds k3 (and updates k1's amount)
        KeyCounter second = new KeyCounter();
        second.add(k3, 3L);
        second.add(k1, 100L);
        data.postKeyCounterUpdate(second, false);

        // All three keys must be present; k1 must reflect the latest amount (100),
        // not the first one (1). Order is unspecified because KeyCounter uses
        // IdentityHashMap; we look up by key rather than asserting position.
        List<AEKeyDisplayEntry> snapshot = data.getSnapshot();
        assertEquals(3, snapshot.size());

        assertEntryEquals(findByTestId(snapshot, 1), k1, 100L, false);
        assertEntryEquals(findByTestId(snapshot, 2), k2, 2L, false);
        assertEntryEquals(findByTestId(snapshot, 3), k3, 3L, false);
    }

    // ========== Large batch sanity ==========

    @Test
    void largeBatchPreservesAllEntries() {
        final int n = 200;
        KeyCounter counter = new KeyCounter();
        for (int i = 0; i < n; i++) {
            counter.add(new TestKey(i), (long) i + 1);
        }

        AEKeyListData data = new AEKeyListData();
        data.postKeyCounterUpdate(counter, false);

        List<AEKeyDisplayEntry> snapshot = data.getSnapshot();
        assertEquals(n, snapshot.size());

        // Verify a few specific entries (outside the Integer cache to catch
        // IdentityHashMap-related bugs if the primary key strategy changes).
        for (int i : new int[]{0, 50, 127, 128, 199}) {
            TestKey k = new TestKey(i);
            assertEquals((long) i + 1, data.getAmount(k),
                    "wrong amount for id=" + i);
        }
    }

    // ========== Helpers ==========

    private static void assertEntryEquals(
            AEKeyDisplayEntry entry, TestKey expectedKey, long expectedAmount, boolean expectedCraftable) {
        assertEquals(expectedKey, entry.key());
        assertEquals(expectedAmount, entry.amount());
        assertEquals(expectedCraftable, entry.craftable());
    }

    /**
     * Finds the first entry in the snapshot whose key is a {@link TestKey} with the
     * given id. Fails the test if no such entry exists. Used to look up entries by
     * logical identity without depending on snapshot order (which is unspecified
     * when populated via {@link KeyCounter}).
     */
    private static AEKeyDisplayEntry findByTestId(List<AEKeyDisplayEntry> snapshot, int testId) {
        for (AEKeyDisplayEntry entry : snapshot) {
            if (entry.key() instanceof TestKey && ((TestKey) entry.key()).getId() == testId) {
                return entry;
            }
        }
        throw new AssertionError("No AEKeyDisplayEntry with TestKey id=" + testId + " in snapshot of size "
                + snapshot.size());
    }
}

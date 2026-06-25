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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import appeng.testkey.TestKey;

/**
 * Unit tests for {@link AEKeyDisplayEntry}.
 *
 * <p>Covers construction, factory methods, accessor behavior, and {@link GenericStack}
 * round-trip conversion. Uses {@link TestKey} as a lightweight AEKey test double that
 * does not require the Minecraft runtime.
 */
class AEKeyDisplayEntryTest {

    // ========== Construction ==========

    @Test
    void constructorStoresKeyAmountCraftable() {
        TestKey key = new TestKey(1);

        AEKeyDisplayEntry entry = new AEKeyDisplayEntry(key, 100L, true);

        assertEquals(key, entry.key());
        assertEquals(100L, entry.amount());
        assertTrue(entry.craftable());
    }

    @Test
    void constructorRejectsNullKey() {
        assertThrows(NullPointerException.class, () -> new AEKeyDisplayEntry(null, 0L, false));
    }

    @Test
    void constructorAllowsZeroAndNegativeAmounts() {
        // The entry itself does not validate business semantics of amount; it is a dumb carrier.
        TestKey key = new TestKey(1);

        AEKeyDisplayEntry zero = new AEKeyDisplayEntry(key, 0L, false);
        assertEquals(0L, zero.amount());

        AEKeyDisplayEntry negative = new AEKeyDisplayEntry(key, -5L, false);
        assertEquals(-5L, negative.amount());
    }

    // ========== Factory: of(AEKey, long, boolean) ==========

    @Test
    void ofKeyAmountCraftableCreatesEquivalentEntry() {
        TestKey key = new TestKey(2);

        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(key, 50L, false);

        assertEquals(key, entry.key());
        assertEquals(50L, entry.amount());
        assertFalse(entry.craftable());
    }

    // ========== Factory: of(GenericStack, boolean) ==========

    @Test
    void ofGenericStackCarriesKeyAndAmount() {
        TestKey key = new TestKey(3);
        GenericStack stack = new GenericStack(key, 250L);

        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(stack, true);

        assertEquals(key, entry.key());
        assertEquals(250L, entry.amount());
        assertTrue(entry.craftable());
    }

    @Test
    void ofGenericStackRejectsNullStack() {
        assertThrows(NullPointerException.class, () -> AEKeyDisplayEntry.of((GenericStack) null, false));
    }

    // ========== toGenericStack ==========

    @Test
    void toGenericStackPreservesKeyAndAmount() {
        TestKey key = new TestKey(4);
        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(key, 999L, false);

        GenericStack stack = entry.toGenericStack();

        assertEquals(key, stack.what());
        assertEquals(999L, stack.amount());
    }

    @Test
    void toGenericStackFromCraftableEntryKeepsAmount() {
        // craftable is not part of GenericStack; only key+amount survive round-trip.
        TestKey key = new TestKey(5);
        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(key, 0L, true);

        GenericStack stack = entry.toGenericStack();

        assertEquals(key, stack.what());
        assertEquals(0L, stack.amount());
    }

    // ========== Equality ==========

    @Test
    void entriesWithSameKeyAmountCraftableAreNotReferenceEqualButAccessorsMatch() {
        // AEKeyDisplayEntry does not override equals; instances are compared by reference.
        // This test documents that behavior so future changes are intentional.
        TestKey key = new TestKey(6);

        AEKeyDisplayEntry a = AEKeyDisplayEntry.of(key, 10L, true);
        AEKeyDisplayEntry b = AEKeyDisplayEntry.of(key, 10L, true);

        assertNotEquals(a, b);
        assertEquals(a.key(), b.key());
        assertEquals(a.amount(), b.amount());
        assertEquals(a.craftable(), b.craftable());
    }

    // ========== Null-safety on accessors ==========

    @Test
    void accessorResultsAreNonNullForValidEntry() {
        TestKey key = new TestKey(7);
        AEKeyDisplayEntry entry = AEKeyDisplayEntry.of(key, 1L, false);

        // key() must never return null for a validly constructed entry.
        assertEquals(key, entry.key());
    }
}

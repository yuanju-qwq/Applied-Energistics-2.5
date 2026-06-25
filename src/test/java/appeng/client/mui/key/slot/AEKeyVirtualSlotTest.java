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

package appeng.client.mui.key.slot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.annotation.Nullable;

import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import appeng.client.mui.key.AEKeyDisplayEntry;
import appeng.testkey.TestKey;

/**
 * Unit tests for {@link AEKeyVirtualSlot} non-render behavior.
 *
 * <p>Uses a minimal concrete subclass ({@link StubSlot}) so the abstract
 * getStack/setStack can be exercised without touching Minecraft runtime.
 * The {@link AEKeyVirtualSlot#render} method is intentionally not tested here
 * because it requires {@code Minecraft.getMinecraft()}.
 */
class AEKeyVirtualSlotTest {

    /** Concrete subclass that just stores a single GenericStack field. */
    static final class StubSlot extends AEKeyVirtualSlot {
        @Nullable
        private GenericStack stack;

        StubSlot(int id, int x, int y) {
            super(id, x, y);
        }

        StubSlot(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height);
        }

        @Nullable
        @Override
        public GenericStack getStack() {
            return stack;
        }

        @Override
        public void setStack(@Nullable GenericStack stack) {
            this.stack = stack;
        }
    }

    // ========== Construction & defaults ==========

    @Test
    void defaultConstructorUsesDefaultSize() {
        StubSlot slot = new StubSlot(1, 10, 20);

        assertEquals(1, slot.getId());
        assertEquals(10, slot.getX());
        assertEquals(20, slot.getY());
        assertEquals(AEKeyVirtualSlot.DEFAULT_SIZE, slot.getWidth());
        assertEquals(AEKeyVirtualSlot.DEFAULT_SIZE, slot.getHeight());
    }

    @Test
    void fullConstructorStoresAllDimensions() {
        StubSlot slot = new StubSlot(2, 5, 6, 24, 32);

        assertEquals(2, slot.getId());
        assertEquals(5, slot.getX());
        assertEquals(6, slot.getY());
        assertEquals(24, slot.getWidth());
        assertEquals(32, slot.getHeight());
    }

    @Test
    void newSlotIsVisibleAndEnabledByDefault() {
        StubSlot slot = new StubSlot(0, 0, 0);

        assertTrue(slot.isVisible());
        assertTrue(slot.isEnabled());
    }

    // ========== Stack management ==========

    @Test
    void setStackWithGenericStackStoresIt() {
        StubSlot slot = new StubSlot(0, 0, 0);
        TestKey key = new TestKey(1);
        GenericStack stack = new GenericStack(key, 50L);

        slot.setStack(stack);

        assertEquals(stack, slot.getStack());
    }

    @Test
    void setStackWithKeyAndAmountCreatesGenericStack() {
        StubSlot slot = new StubSlot(0, 0, 0);
        TestKey key = new TestKey(2);

        slot.setStack(key, 30L);

        GenericStack stored = slot.getStack();
        assertNotNull(stored);
        assertEquals(key, stored.what());
        assertEquals(30L, stored.amount());
    }

    @Test
    void setStackWithNullKeyClearsSlot() {
        StubSlot slot = new StubSlot(0, 0, 0);
        slot.setStack(new TestKey(1), 10L);

        slot.setStack(null, 0L);

        assertNull(slot.getStack());
    }

    @Test
    void setStackNullDirectlyClearsSlot() {
        StubSlot slot = new StubSlot(0, 0, 0);
        slot.setStack(new TestKey(1), 10L);

        slot.setStack((GenericStack) null);

        assertNull(slot.getStack());
    }

    // ========== getDisplayEntry ==========

    @Test
    void getDisplayEntryReturnsNullWhenSlotIsEmpty() {
        StubSlot slot = new StubSlot(0, 0, 0);
        assertNull(slot.getDisplayEntry(false));
    }

    @Test
    void getDisplayEntryWrapsStackWithCraftableFlag() {
        StubSlot slot = new StubSlot(0, 0, 0);
        TestKey key = new TestKey(1);
        slot.setStack(key, 100L);

        AEKeyDisplayEntry entry = slot.getDisplayEntry(true);

        assertNotNull(entry);
        assertEquals(key, entry.key());
        assertEquals(100L, entry.amount());
        assertTrue(entry.craftable());

        AEKeyDisplayEntry nonCraftable = slot.getDisplayEntry(false);
        assertFalse(nonCraftable.craftable());
    }

    // ========== containsLocal ==========

    @Test
    void containsLocalReturnsTrueForPointInsideBounds() {
        StubSlot slot = new StubSlot(0, 10, 20, 16, 16);

        // corners
        assertTrue(slot.containsLocal(10, 20));      // top-left (inclusive)
        assertTrue(slot.containsLocal(25, 35));      // bottom-right (exclusive)
        assertTrue(slot.containsLocal(15, 25));      // center
    }

    @Test
    void containsLocalReturnsFalseForPointOutsideBounds() {
        StubSlot slot = new StubSlot(0, 10, 20, 16, 16);

        assertFalse(slot.containsLocal(9, 20));      // x too small
        assertFalse(slot.containsLocal(26, 20));     // x too large
        assertFalse(slot.containsLocal(10, 19));     // y too small
        assertFalse(slot.containsLocal(10, 36));     // y too large
        assertFalse(slot.containsLocal(0, 0));       // far away
    }

    @Test
    void containsLocalReturnsFalseWhenSlotIsInvisible() {
        StubSlot slot = new StubSlot(0, 10, 20, 16, 16);
        slot.setVisible(false);

        // Even a point that would normally be inside returns false when invisible
        assertFalse(slot.containsLocal(15, 25));
    }

    // ========== containsScreen ==========

    @Test
    void containsScreenTranslatesByGuiOffset() {
        StubSlot slot = new StubSlot(0, 10, 20, 16, 16);
        final int guiLeft = 100;
        final int guiTop = 50;

        // screen-space center should map to local-space center
        assertTrue(slot.containsScreen(115, 70, guiLeft, guiTop));    // 115-100=15, 70-50=20
        assertFalse(slot.containsScreen(116, 87, guiLeft, guiTop));   // 116-100=16 < 26, but 87-50=37 > 35
    }

    @Test
    void containsScreenReturnsFalseWhenInvisible() {
        StubSlot slot = new StubSlot(0, 0, 0, 16, 16);
        slot.setVisible(false);

        assertFalse(slot.containsScreen(5, 5, 0, 0));
    }

    // ========== Position & size mutation ==========

    @Test
    void setPositionUpdatesXAndY() {
        StubSlot slot = new StubSlot(0, 0, 0);
        slot.setPosition(42, 99);

        assertEquals(42, slot.getX());
        assertEquals(99, slot.getY());
    }

    @Test
    void setSizeUpdatesWidthAndHeight() {
        StubSlot slot = new StubSlot(0, 0, 0);
        slot.setSize(20, 30);

        assertEquals(20, slot.getWidth());
        assertEquals(30, slot.getHeight());
    }

    @Test
    void containsLocalRespectsUpdatedSize() {
        StubSlot slot = new StubSlot(0, 0, 0, 16, 16);
        slot.setSize(32, 32);

        // Point at (31, 31) is inside new 32x32 bounds but was outside old 16x16
        assertTrue(slot.containsLocal(31, 31));
    }

    // ========== Visibility & enabled ==========

    @Test
    void setVisibleTogglesFlag() {
        StubSlot slot = new StubSlot(0, 0, 0);
        assertTrue(slot.isVisible());

        slot.setVisible(false);
        assertFalse(slot.isVisible());

        slot.setVisible(true);
        assertTrue(slot.isVisible());
    }

    @Test
    void setEnabledTogglesFlag() {
        StubSlot slot = new StubSlot(0, 0, 0);
        assertTrue(slot.isEnabled());

        slot.setEnabled(false);
        assertFalse(slot.isEnabled());

        slot.setEnabled(true);
        assertTrue(slot.isEnabled());
    }

    // ========== accepts ==========

    @Test
    void acceptsReturnsTrueByDefaultForAnyInput() {
        StubSlot slot = new StubSlot(0, 0, 0);

        // Default implementation accepts everything; subclasses override for filtering.
        assertTrue(slot.accepts(null, 0));
        assertTrue(slot.accepts(null, 1));
        assertTrue(slot.accepts(TestKey.class.cast(new TestKey(1)).getType(), 0));
    }
}

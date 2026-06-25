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

package appeng.client.mui.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.stacks.GenericStack;
import appeng.client.mui.key.AEKeyListData;
import appeng.client.mui.key.slot.AEKeyVirtualSlot;
import appeng.testkey.TestKey;

/**
 * Unit tests for {@link AEKeyModularPanel} non-render behavior.
 *
 * <p>Covers virtual slot management, JEI exclusion areas, GUI origin tracking,
 * and {@link AEKeyModularPanel#getStackUnderMouse(int, int)} hit-testing. The
 * {@code render(...)} method is intentionally not tested because it requires
 * the Minecraft runtime.
 */
class AEKeyModularPanelTest {

    private AEKeyModularPanel panel;
    private StubSlot slot1;
    private StubSlot slot2;

    /** Concrete slot subclass for testing. */
    static final class StubSlot extends AEKeyVirtualSlot {
        private GenericStack stack;

        StubSlot(int id, int x, int y, int width, int height) {
            super(id, x, y, width, height);
        }

        @Override
        public GenericStack getStack() {
            return stack;
        }

        @Override
        public void setStack(GenericStack stack) {
            this.stack = stack;
        }
    }

    @BeforeEach
    void setUp() {
        panel = new AEKeyModularPanel("test_panel");
        slot1 = new StubSlot(1, 10, 10, 16, 16);
        slot2 = new StubSlot(2, 50, 50, 16, 16);
        panel.setGuiOrigin(100, 100);
        panel.addVirtualSlot(slot1);
        panel.addVirtualSlot(slot2);
    }

    // ========== Construction & defaults ==========

    @Test
    void newListDataIsAvailableAndEmpty() {
        AEKeyListData data = panel.getListData();
        assertNotNull(data);
        assertTrue(data.isEmpty());
    }

    @Test
    void guiOriginDefaultsToZeroBeforeSet() {
        AEKeyModularPanel fresh = new AEKeyModularPanel("fresh");
        assertEquals(0, fresh.getGuiLeft());
        assertEquals(0, fresh.getGuiTop());
    }

    @Test
    void setGuiOriginUpdatesAccessors() {
        AEKeyModularPanel fresh = new AEKeyModularPanel("fresh");
        fresh.setGuiOrigin(42, 99);
        assertEquals(42, fresh.getGuiLeft());
        assertEquals(99, fresh.getGuiTop());
    }

    // ========== Virtual slot management ==========

    @Test
    void addVirtualSlotAppendsAndReturnsPanelForChaining() {
        AEKeyModularPanel fresh = new AEKeyModularPanel("fresh");
        StubSlot slot = new StubSlot(1, 0, 0, 16, 16);

        AEKeyModularPanel returned = fresh.addVirtualSlot(slot);

        assertEquals(fresh, returned);
        assertEquals(1, fresh.getVirtualSlots().size());
        assertEquals(slot, fresh.getVirtualSlots().get(0));
    }

    @Test
    void getVirtualSlotsIsUnmodifiable() {
        List<AEKeyVirtualSlot> slots = panel.getVirtualSlots();
        assertThrows(UnsupportedOperationException.class,
                () -> slots.add(new StubSlot(99, 0, 0, 16, 16)));
    }

    @Test
    void clearVirtualSlotsRemovesAll() {
        panel.clearVirtualSlots();
        assertTrue(panel.getVirtualSlots().isEmpty());
    }

    @Test
    void clearVirtualSlotsReturnsPanelForChaining() {
        AEKeyModularPanel returned = panel.clearVirtualSlots();
        assertEquals(panel, returned);
    }

    // ========== getStackUnderMouse ==========

    @Test
    void getStackUnderMouseReturnsNullWhenNoSlotHit() {
        // Point at (0, 0) screen-space maps to local (-100, -100) — outside any slot.
        assertNull(panel.getStackUnderMouse(0, 0));
    }

    @Test
    void getStackUnderMouseReturnsNullForHitSlotWithEmptyStack() {
        // slot1 is at local (10, 10), screen (110, 110) with gui origin (100, 100).
        // It's empty by default; a hit should return null.
        assertNull(panel.getStackUnderMouse(115, 115));
    }

    @Test
    void getStackUnderMouseReturnsStackForHitSlot() {
        TestKey key = new TestKey(1);
        GenericStack stack = new GenericStack(key, 50L);
        slot1.setStack(stack);

        // Center of slot1 in screen space.
        GenericStack result = panel.getStackUnderMouse(115, 115);
        assertEquals(stack, result);
    }

    @Test
    void getStackUnderMouseRespectsGuiOriginOffset() {
        // slot1 at local (10, 10); with gui origin (100, 100), screen coords (110, 110)
        TestKey key = new TestKey(1);
        GenericStack stack = new GenericStack(key, 1L);
        slot1.setStack(stack);

        // Outside slot1's bounds in screen space
        assertNull(panel.getStackUnderMouse(109, 110));   // x just before slot1
        assertNull(panel.getStackUnderMouse(126, 110));   // x just after slot1

        // Inside slot1's bounds in screen space
        assertEquals(stack, panel.getStackUnderMouse(110, 110));  // top-left corner
        assertEquals(stack, panel.getStackUnderMouse(125, 125));  // bottom-right corner
    }

    @Test
    void getStackUnderMouseReturnsFirstHitWhenSlotsOverlap() {
        // Build a panel where two slots overlap; the first registered should win.
        AEKeyModularPanel overlapPanel = new AEKeyModularPanel("overlap");
        overlapPanel.setGuiOrigin(0, 0);

        StubSlot first = new StubSlot(1, 0, 0, 32, 32);
        StubSlot second = new StubSlot(2, 0, 0, 32, 32); // same bounds
        overlapPanel.addVirtualSlot(first);
        overlapPanel.addVirtualSlot(second);

        TestKey k1 = new TestKey(1);
        TestKey k2 = new TestKey(2);
        first.setStack(new GenericStack(k1, 1L));
        second.setStack(new GenericStack(k2, 2L));

        GenericStack hit = overlapPanel.getStackUnderMouse(5, 5);
        assertEquals(k1, hit.what());
    }

    @Test
    void getStackUnderMouseSkipsInvisibleSlots() {
        TestKey key = new TestKey(1);
        GenericStack stack = new GenericStack(key, 1L);
        slot1.setStack(stack);
        slot1.setVisible(false);

        // Point is inside slot1's bounds, but slot1 is invisible — should fall through
        // to slot2 (also empty at this point), returning null.
        assertNull(panel.getStackUnderMouse(115, 115));
    }

    // ========== JEI exclusion areas ==========

    @Test
    void addJEIExclusionAreaAppendsAndReturnsPanelForChaining() {
        AEKeyModularPanel fresh = new AEKeyModularPanel("fresh");
        Rectangle r = new Rectangle(0, 0, 80, 30);

        AEKeyModularPanel returned = fresh.addJEIExclusionArea(r);

        assertEquals(fresh, returned);
        assertEquals(1, fresh.getJEIExclusionArea().size());
        assertEquals(r, fresh.getJEIExclusionArea().get(0));
    }

    @Test
    void getJEIExclusionAreaIsUnmodifiable() {
        panel.addJEIExclusionArea(new Rectangle(0, 0, 10, 10));
        List<Rectangle> areas = panel.getJEIExclusionArea();
        assertThrows(UnsupportedOperationException.class,
                () -> areas.add(new Rectangle(0, 0, 1, 1)));
    }

    @Test
    void clearJEIExclusionAreasRemovesAll() {
        panel.addJEIExclusionArea(new Rectangle(0, 0, 10, 10));
        panel.addJEIExclusionArea(new Rectangle(20, 20, 5, 5));
        assertEquals(2, panel.getJEIExclusionArea().size());

        AEKeyModularPanel returned = panel.clearJEIExclusionAreas();
        assertEquals(panel, returned);
        assertTrue(panel.getJEIExclusionArea().isEmpty());
    }

    @Test
    void newPanelHasNoJEIExclusionAreas() {
        AEKeyModularPanel fresh = new AEKeyModularPanel("fresh");
        assertTrue(fresh.getJEIExclusionArea().isEmpty());
    }

    // ========== listData integration ==========

    @Test
    void listDataSurvivesAcrossMultipleUpdates() {
        AEKeyListData data = panel.getListData();
        TestKey k1 = new TestKey(1);

        data.postUpdate(k1, 100L, false);
        assertEquals(100L, data.getAmount(k1));

        // Clear virtual slots; listData should be unaffected.
        panel.clearVirtualSlots();
        assertEquals(100L, data.getAmount(k1));
        assertEquals(1, data.size());
    }

    @Test
    void listDataIsSameInstanceAcrossCalls() {
        AEKeyListData first = panel.getListData();
        AEKeyListData second = panel.getListData();
        assertTrue(first == second, "getListData must return the same instance");
    }
}

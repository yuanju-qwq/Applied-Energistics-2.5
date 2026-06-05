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

package appeng.client.mui.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo;
import appeng.client.me.ItemRepo.RepoEntry;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.mui.widgets.MUIVirtualSlot;

/**
 * Dynamic list / slot module — reusable component for ME network resource browsing
 * with scrolling, highlighting, and GenericStack support.
 *
 * <p>Manages a grid of {@link MUIVirtualSlot} instances bound to an {@link ItemRepo},
 * handling visible area calculation, scroll synchronization, match highlighting,
 * hover / disabled overlay, and GenericStack-based data input.
 *
 * <p>This module replaces the ad-hoc slot grid management code previously scattered
 * across {@link MEItemBrowserModule} and various panels. It uses AEKey-based
 * {@link MUIVirtualSlot} instead of legacy {@code VirtualMEMonitorableSlot}.
 *
 * <h3>Capabilities</h3>
 * <ul>
 *   <li><b>Visible area calculation</b> — dynamic row count based on available height</li>
 *   <li><b>Dynamic slot refresh</b> — update slots when data changes via ItemRepo</li>
 *   <li><b>Scroll synchronization</b> — scrollbar range/position coordination</li>
 *   <li><b>Match highlighting</b> — visual highlight for items matching search criteria</li>
 *   <li><b>Hover / disabled overlay</b> — standard hover highlight and no-power darkening</li>
 *   <li><b>GenericStack input</b> — items and fluids unified via ItemRepo / RepoEntry pipeline</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * DynamicListModule list = new DynamicListModule(host, gridX, gridY, columns);
 * list.setRows(rows);
 * list.setOnSlotClicked((slot, btn) -> { ... });
 * list.postUpdate(key, amount, craftable);
 * </pre>
 *
 * <p>The module must be registered as a widget on the panel for overlay rendering:
 * <pre>
 * panel.addWidget(list);
 * </pre>
 *
 * <p>The individual virtual slots are also registered as widgets automatically
 * by {@link #init()}.
 */
@SideOnly(Side.CLIENT)
public class DynamicListModule implements IMUIWidget {

    // ========== Host interface ==========

    /**
     * The host GUI must implement this interface to provide context for the module.
     */
    public interface Host {
        AEBasePanel getPanel();

        int getGuiLeft();

        int getGuiTop();

        ItemRepo getRepo();

        MUIScrollBar getScrollBar();

        boolean hasPower();

        /**
         * Whether match highlighting is enabled.
         * When enabled, slots whose content matches the current search text
         * will have a highlight overlay.
         */
        default boolean isMatchHighlightEnabled() {
            return false;
        }

        /**
         * The search text used for match highlighting.
         */
        default String getSearchText() {
            return "";
        }
    }

    // ========== Constants ==========

    /** Default slot size (pixels). */
    public static final int DEFAULT_SLOT_SIZE = 18;

    /** Highlight color for matched items (yellow-green). */
    public static final int MATCH_HIGHLIGHT_COLOR = 0x44FFFF00;

    /** Disabled overlay color (dark semi-transparent). */
    public static final int DISABLED_OVERLAY_COLOR = 0x66111111;

    // ========== Fields ==========

    private final Host host;

    // Grid layout
    private final int gridX;
    private final int gridY;
    private final int columns;
    private final int slotSize;

    // Dynamic rows
    private int rows;

    // Virtual slot grid
    private final List<MUIVirtualSlot> slots = new ArrayList<>();

    // Display options
    private boolean showAmount = true;
    private boolean showCraftableText = true;

    // Click callback
    @Nullable
    private BiConsumer<MUIVirtualSlot, Integer> onSlotClicked;

    // Match highlighting
    @Nullable
    private IntPredicate highlightPredicate;

    // ========== Construction ==========

    /**
     * @param host     the host GUI
     * @param gridX    grid origin X (panel-relative)
     * @param gridY    grid origin Y (panel-relative)
     * @param columns  number of columns in the grid
     */
    public DynamicListModule(Host host, int gridX, int gridY, int columns) {
        this(host, gridX, gridY, columns, DEFAULT_SLOT_SIZE);
    }

    /**
     * @param host     the host GUI
     * @param gridX    grid origin X (panel-relative)
     * @param gridY    grid origin Y (panel-relative)
     * @param columns  number of columns in the grid
     * @param slotSize slot size in pixels (including spacing)
     */
    public DynamicListModule(Host host, int gridX, int gridY, int columns, int slotSize) {
        this.host = host;
        this.gridX = gridX;
        this.gridY = gridY;
        this.columns = columns;
        this.slotSize = slotSize;
    }

    // ========== Initialization ==========

    /**
     * Build or rebuild the virtual slot grid and register them on the panel.
     * <p>
     * Call from {@code initGui()} after rows are computed, typically from
     * {@code setupWidgets()} or after {@code super.initGui()}.
     * <p>
     * The panel's {@code initGui()} clears its widget list before calling
     * {@code setupWidgets()}, so old slots are automatically cleaned up.
     * When called after {@code super.initGui()}, the caller must ensure no
     * duplicate registration occurs.
     * <p>
     * After calling this, individual slots are registered as panel widgets
     * and participate in the normal MUI draw lifecycle.
     */
    public void init() {
        this.slots.clear();

        // Build new slot grid
        AEBasePanel panel = host.getPanel();
        ItemRepo repo = host.getRepo();
        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.columns; col++) {
                final int idx = row * this.columns + col;
                final int x = this.gridX + col * this.slotSize;
                final int y = this.gridY + row * this.slotSize;

                MUIVirtualSlot slot = new MUIVirtualSlot(x, y, idx, repo, this.slotSize)
                        .setShowAmount(this.showAmount)
                        .setShowCraftableText(this.showCraftableText)
                        .setOnClicked((s, btn) -> {
                            if (this.onSlotClicked != null) {
                                this.onSlotClicked.accept(s, btn);
                            }
                        });

                panel.addWidget(slot);
                this.slots.add(slot);
            }
        }
    }

    // ========== Grid layout ==========

    /**
     * Set the number of visible rows. Triggers slot grid rebuild on next
     * {@link #init()} call.
     */
    public void setRows(int rows) {
        this.rows = rows;
    }

    /**
     * @return current number of visible rows
     */
    public int getRows() {
        return this.rows;
    }

    /**
     * @return number of columns
     */
    public int getColumns() {
        return this.columns;
    }

    /**
     * @return total visible slot count (rows * columns)
     */
    public int getVisibleSlotCount() {
        return this.rows * this.columns;
    }

    // ========== Scroll synchronization ==========

    /**
     * Update the scrollbar range based on current repo size, column count,
     * and visible row count. Call after data changes or row count changes.
     */
    public void updateScrollBar() {
        MUIScrollBar scrollBar = host.getScrollBar();
        ItemRepo repo = host.getRepo();
        int totalRows = (repo.size() + this.columns - 1) / this.columns;
        int maxScroll = Math.max(0, totalRows - this.rows);
        scrollBar.setRange(0, maxScroll, Math.max(1, this.rows / 6));
    }

    /**
     * Refresh the repo view and update scrollbar range.
     * Call after data updates ({@link #postUpdate} or {@link #postRepoUpdate}).
     */
    public void refreshView() {
        host.getRepo().updateView();
        updateScrollBar();
    }

    // ========== Data updates (GenericStack / AEKey input) ==========

    /**
     * Update a resource entry using AEKey-based input.
     */
    public void postUpdate(AEKey key, long amount, boolean craftable) {
        host.getRepo().postUpdate(key, amount, craftable);
    }

    /**
     * Update a resource entry from a GenericStack with craftable flag.
     */
    public void postUpdate(GenericStack stack, boolean craftable) {
        host.getRepo().postUpdate(stack, craftable);
    }

    /**
     * Update a resource entry from a RepoEntry.
     */
    public void postUpdate(RepoEntry entry) {
        host.getRepo().postUpdate(entry);
    }

    /**
     * Batch update from a list of RepoEntry (preferred path).
     */
    public void postRepoUpdate(List<RepoEntry> entries) {
        ItemRepo repo = host.getRepo();
        for (RepoEntry entry : entries) {
            repo.postUpdate(entry);
        }
    }

    /**
     * Batch update from a list of GenericStack items.
     * Each entry has amount == 0 and craftable == false by default.
     */
    public void postGenericStackUpdate(List<GenericStack> stacks) {
        ItemRepo repo = host.getRepo();
        for (GenericStack stack : stacks) {
            repo.postUpdate(stack, false);
        }
    }

    /**
     * @deprecated Use {@link #postRepoUpdate(List)} or {@link #postUpdate(AEKey, long, boolean)} instead.
     *             Converts IAEStack list to RepoEntry and delegates.
     */
    @Deprecated
    public void postUpdate(List<IAEStack<?>> stacks) {
        for (IAEStack<?> is : stacks) {
            var key = is.toAEKey();
            if (key != null) {
                host.getRepo().postUpdate(key, is.getStackSize(), is.isCraftable());
            }
        }
    }

    /**
     * Clear all data in the repo.
     */
    public void clear() {
        host.getRepo().clear();
    }

    // ========== Match highlighting ==========

    /**
     * Set a custom highlight predicate. Slots whose index passes this predicate
     * will be rendered with a highlight overlay.
     *
     * @param predicate returns true for slot indices that should be highlighted,
     *                  or null to disable custom highlighting
     */
    public void setHighlightPredicate(@Nullable IntPredicate predicate) {
        this.highlightPredicate = predicate;
    }

    /**
     * Enable automatic match highlighting based on search text.
     * Slots whose content's display name contains the search text will be highlighted.
     */
    public void enableAutoMatchHighlight() {
        this.highlightPredicate = null; // null = use default auto mode
    }

    /**
     * Disable match highlighting entirely.
     */
    public void disableHighlight() {
        this.highlightPredicate = EMPTY_PREDICATE;
    }

    // ========== Display options ==========

    public DynamicListModule setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
        for (MUIVirtualSlot slot : this.slots) {
            slot.setShowAmount(showAmount);
        }
        return this;
    }

    public DynamicListModule setShowCraftableText(boolean showCraftableText) {
        this.showCraftableText = showCraftableText;
        for (MUIVirtualSlot slot : this.slots) {
            slot.setShowCraftableText(showCraftableText);
        }
        return this;
    }

    public DynamicListModule setOnSlotClicked(@Nullable BiConsumer<MUIVirtualSlot, Integer> handler) {
        this.onSlotClicked = handler;
        for (MUIVirtualSlot slot : this.slots) {
            slot.setOnClicked((s, btn) -> {
                if (this.onSlotClicked != null) {
                    this.onSlotClicked.accept(s, btn);
                }
            });
        }
        return this;
    }

    // ========== Accessors ==========

    public List<MUIVirtualSlot> getSlots() {
        return this.slots;
    }

    public Host getHost() {
        return this.host;
    }

    // ========== IMUIWidget: overlay rendering ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (this.rows <= 0 || this.columns <= 0) {
            return;
        }

        int gridScreenX = guiLeft + this.gridX;
        int gridScreenY = guiTop + this.gridY;
        int gridWidth = this.columns * this.slotSize;
        int gridHeight = this.rows * this.slotSize;

        // Disabled (no-power) overlay
        if (!host.hasPower()) {
            GlStateManager.disableDepth();
            GlStateManager.colorMask(true, true, true, false);
            AEBasePanel.drawSolidRect(gridScreenX, gridScreenY,
                    gridScreenX + gridWidth, gridScreenY + gridHeight,
                    DISABLED_OVERLAY_COLOR);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableDepth();
            return; // No need to draw highlights when disabled
        }

        // Match highlighting
        if (host.isMatchHighlightEnabled() || this.highlightPredicate != null) {
            drawMatchHighlights(gridScreenX, gridScreenY);
        }
    }

    /**
     * Draw match highlights on individual slots.
     */
    private void drawMatchHighlights(int gridScreenX, int gridScreenY) {
        boolean useAuto = (this.highlightPredicate == null && host.isMatchHighlightEnabled())
                || this.highlightPredicate == AUTO_PREDICATE;

        String searchText = useAuto ? host.getSearchText().toLowerCase(java.util.Locale.ROOT).trim() : null;

        for (int i = 0; i < this.slots.size(); i++) {
            boolean highlighted;

            if (this.highlightPredicate != null && this.highlightPredicate != AUTO_PREDICATE) {
                // Custom predicate
                highlighted = this.highlightPredicate.test(i);
            } else if (useAuto && searchText != null && !searchText.isEmpty()) {
                // Auto match: check display name
                MUIVirtualSlot slot = this.slots.get(i);
                RepoEntry entry = slot.getRepoEntry();
                if (entry != null) {
                    String name = entry.what().getDisplayName().toLowerCase(java.util.Locale.ROOT);
                    highlighted = name.contains(searchText);
                } else {
                    highlighted = false;
                }
            } else {
                highlighted = false;
            }

            if (highlighted) {
                int row = i / this.columns;
                int col = i % this.columns;
                int x = gridScreenX + col * this.slotSize;
                int y = gridScreenY + row * this.slotSize;

                GlStateManager.disableDepth();
                GlStateManager.colorMask(true, true, true, false);
                AEBasePanel.drawSolidRect(x, y, x + this.slotSize, y + this.slotSize, MATCH_HIGHLIGHT_COLOR);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableDepth();
            }
        }
    }

    // ========== Static helpers ==========

    private static final IntPredicate EMPTY_PREDICATE = i -> false;
    private static final IntPredicate AUTO_PREDICATE = i -> false; // marker only

    /**
     * Compute the number of visible rows that fit in the given pixel height.
     *
     * @param availableHeight available height in pixels
     * @param slotSize        slot size in pixels (including spacing)
     * @return number of full rows that fit
     */
    public static int computeRows(int availableHeight, int slotSize) {
        return Math.max(0, availableHeight / slotSize);
    }
}

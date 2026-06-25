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

package appeng.client.mui.module;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.client.mui.slot.VirtualMEMonitorableSlot;
import appeng.client.mui.widgets.IMUISortSource;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.util.Platform;

/**
 * ME item browser module - reusable component for ME network item browsing.
 */
public class MEItemBrowserModule implements IMUISortSource {

    // ========== Layout configuration ==========

    /**
     * Layout configuration for the ME browser module.
     * Encapsulates all layout parameters that differ between compact and standard modes.
     */
    public static final class LayoutConfig {

        // Grid dimensions
        private final int cols;
        private final int fixedRows; // 0 = dynamic rows (calculated from screen height)

        // Panel size
        private final int panelWidth;
        private final int panelHeight; // 0 = dynamic height (calculated from rows)

        // Grid offsets within the panel
        private final int gridOffsetX;
        private final int gridOffsetY;

        // Search field
        private final int searchFieldX;
        private final int searchFieldY;
        private final int searchFieldWidth;
        private final int searchFieldHeight;
        private final int searchFieldMaxLength;

        // Scrollbar
        private final int scrollbarLeftOffset; // relative to panel right edge
        private final int scrollbarTopOffset; // relative to grid top

        // Texture
        private final ResourceLocation texture;
        private final boolean hasOwnTexture; // false = host draws background

        // Features
        private final boolean draggable;
        private final boolean hasSortButtons; // compact mode has its own sort buttons

        // Positioning mode
        private final boolean sidePanel; // true = positioned left of main GUI, false = main GUI itself

        private LayoutConfig(Builder builder) {
            this.cols = builder.cols;
            this.fixedRows = builder.fixedRows;
            this.panelWidth = builder.panelWidth;
            this.panelHeight = builder.panelHeight;
            this.gridOffsetX = builder.gridOffsetX;
            this.gridOffsetY = builder.gridOffsetY;
            this.searchFieldX = builder.searchFieldX;
            this.searchFieldY = builder.searchFieldY;
            this.searchFieldWidth = builder.searchFieldWidth;
            this.searchFieldHeight = builder.searchFieldHeight;
            this.searchFieldMaxLength = builder.searchFieldMaxLength;
            this.scrollbarLeftOffset = builder.scrollbarLeftOffset;
            this.scrollbarTopOffset = builder.scrollbarTopOffset;
            this.texture = builder.texture;
            this.hasOwnTexture = builder.hasOwnTexture;
            this.draggable = builder.draggable;
            this.hasSortButtons = builder.hasSortButtons;
            this.sidePanel = builder.sidePanel;
        }

        public int getCols() {
            return cols;
        }

        public int getFixedRows() {
            return fixedRows;
        }

        public int getPanelWidth() {
            return panelWidth;
        }

        public int getPanelHeight() {
            return panelHeight;
        }

        public int getGridOffsetX() {
            return gridOffsetX;
        }

        public int getGridOffsetY() {
            return gridOffsetY;
        }

        public int getSearchFieldX() {
            return searchFieldX;
        }

        public int getSearchFieldY() {
            return searchFieldY;
        }

        public int getSearchFieldWidth() {
            return searchFieldWidth;
        }

        public int getSearchFieldHeight() {
            return searchFieldHeight;
        }

        public int getSearchFieldMaxLength() {
            return searchFieldMaxLength;
        }

        public int getScrollbarLeftOffset() {
            return scrollbarLeftOffset;
        }

        public int getScrollbarTopOffset() {
            return scrollbarTopOffset;
        }

        public ResourceLocation getTexture() {
            return texture;
        }

        public boolean hasOwnTexture() {
            return hasOwnTexture;
        }

        public boolean isDraggable() {
            return draggable;
        }

        public boolean hasSortButtons() {
            return hasSortButtons;
        }

        public boolean isSidePanel() {
            return sidePanel;
        }

        /**
         * Compact layout: 4-column side panel with own texture and sort buttons.
         * Used by WirelessDualInterfaceTerminal.
         */
        public static LayoutConfig compact() {
            return new Builder()
                    .cols(4)
                    .fixedRows(4)
                    .panelWidth(101)
                    .panelHeight(96)
                    .gridOffsetX(5)
                    .gridOffsetY(18)
                    .searchFieldX(3)
                    .searchFieldY(4)
                    .searchFieldWidth(72)
                    .searchFieldHeight(12)
                    .searchFieldMaxLength(25)
                    .scrollbarLeftOffset(14)
                    .scrollbarTopOffset(0)
                    .texture(new ResourceLocation("appliedenergistics2", "textures/gui/widget/items.png"))
                    .hasOwnTexture(true)
                    .draggable(true)
                    .hasSortButtons(true)
                    .sidePanel(true)
                    .build();
        }

        /**
         * Standard layout: 9-column main panel.
         * Used by MUIMEMonitorablePanel.
         * The host panel handles background drawing; this module manages the grid and search.
         */
        public static LayoutConfig standard() {
            return new Builder()
                    .cols(9)
                    .fixedRows(0) // dynamic
                    .panelWidth(197)
                    .panelHeight(0) // dynamic
                    .gridOffsetX(0)
                    .gridOffsetY(18)
                    .searchFieldX(80) // minimum X, adjusted at runtime
                    .searchFieldY(4)
                    .searchFieldWidth(90)
                    .searchFieldHeight(12)
                    .searchFieldMaxLength(50)
                    .scrollbarLeftOffset(22) // 197 - 175 = 22
                    .scrollbarTopOffset(0)
                    .texture(null)
                    .hasOwnTexture(false)
                    .draggable(false)
                    .hasSortButtons(true) // sort buttons now managed by this module
                    .sidePanel(false)
                    .build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private int cols = 9;
            private int fixedRows = 0;
            private int panelWidth = 197;
            private int panelHeight = 0;
            private int gridOffsetX = 0;
            private int gridOffsetY = 18;
            private int searchFieldX = 80;
            private int searchFieldY = 4;
            private int searchFieldWidth = 90;
            private int searchFieldHeight = 12;
            private int searchFieldMaxLength = 50;
            private int scrollbarLeftOffset = 22;
            private int scrollbarTopOffset = 0;
            private ResourceLocation texture = null;
            private boolean hasOwnTexture = false;
            private boolean draggable = false;
            private boolean hasSortButtons = false;
            private boolean sidePanel = false;

            public Builder cols(int v) { this.cols = v; return this; }
            public Builder fixedRows(int v) { this.fixedRows = v; return this; }
            public Builder panelWidth(int v) { this.panelWidth = v; return this; }
            public Builder panelHeight(int v) { this.panelHeight = v; return this; }
            public Builder gridOffsetX(int v) { this.gridOffsetX = v; return this; }
            public Builder gridOffsetY(int v) { this.gridOffsetY = v; return this; }
            public Builder searchFieldX(int v) { this.searchFieldX = v; return this; }
            public Builder searchFieldY(int v) { this.searchFieldY = v; return this; }
            public Builder searchFieldWidth(int v) { this.searchFieldWidth = v; return this; }
            public Builder searchFieldHeight(int v) { this.searchFieldHeight = v; return this; }
            public Builder searchFieldMaxLength(int v) { this.searchFieldMaxLength = v; return this; }
            public Builder scrollbarLeftOffset(int v) { this.scrollbarLeftOffset = v; return this; }
            public Builder scrollbarTopOffset(int v) { this.scrollbarTopOffset = v; return this; }
            public Builder texture(ResourceLocation v) { this.texture = v; return this; }
            public Builder hasOwnTexture(boolean v) { this.hasOwnTexture = v; return this; }
            public Builder draggable(boolean v) { this.draggable = v; return this; }
            public Builder hasSortButtons(boolean v) { this.hasSortButtons = v; return this; }
            public Builder sidePanel(boolean v) { this.sidePanel = v; return this; }
            public LayoutConfig build() { return new LayoutConfig(this); }
        }
    }

    // ========== Host interface ==========

    /**
     * The host GUI must implement this interface to provide context for the module.
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getXSize();

        int getYSize();

        FontRenderer getFontRenderer();

        AEBasePanel getPanel();

        IConfigManager getConfigSrc();

        List<GuiButton> getButtonList();

        /**
         * Whether the terminal has view cells (affects view mode button visibility).
         */
        boolean hasViewCell();

        /**
         * JEI offset for button positioning.
         */
        int getJeiOffset();

        /**
         * Request the host to reinitialize the GUI.
         */
        void requestReinitialize();

        /**
         * Request the host to update the scrollbar.
         */
        void requestScrollBarUpdate();
    }

    // ========== Data ==========

    private final Host host;
    private final LayoutConfig layout;
    private final ItemRepo itemRepo;
    private final MUIScrollBar itemPanelScrollbar;

    private MUITextFieldWidget itemSearchField;
    private static String memoryText = "";

    // Search bar module (manages search field lifecycle, terminal search config, JEI sync)
    private final SearchBarModule searchBarModule;

    // Panel drag (compact mode only)
    private PanelDragState dragState;

    // Dynamic rows (standard mode)
    private int rows = 0;

    // ========== Construction ==========

    /**
     * Create module with compact layout (backward compatible).
     */
    public MEItemBrowserModule(Host host) {
        this(host, LayoutConfig.compact());
    }

    /**
     * Create module with specified layout configuration.
     */
    public MEItemBrowserModule(Host host, LayoutConfig layout) {
        this.host = host;
        this.layout = layout;
        this.itemPanelScrollbar = new MUIScrollBar();
        this.itemRepo = new ItemRepo(this.itemPanelScrollbar, this);
        this.itemRepo.setRowSize(layout.getCols());
        this.searchBarModule = new SearchBarModule(new SearchBarHost(), SearchBarModule.SearchMode.SINGLE);

        if (layout.getFixedRows() > 0) {
            this.rows = layout.getFixedRows();
        }
    }

    // ========== Accessors ==========

    public LayoutConfig getLayout() {
        return layout;
    }

    public ItemRepo getItemRepo() {
        return itemRepo;
    }

    public MUIScrollBar getItemPanelScrollbar() {
        return itemPanelScrollbar;
    }

    public MUITextFieldWidget getItemSearchField() {
        return itemSearchField;
    }

    public PanelDragState getDragState() {
        return dragState;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    /**
     * Set the search field from an external source (e.g. the host panel).
     * Used when the host creates the search field externally and syncs it to the module.
     *
     * @deprecated Prefer letting the module create the search field via
     *             {@link #initStandardPanel(int, int)} or {@link #initPanel()}.
     */
    @Deprecated
    public void setItemSearchField(MUITextFieldWidget searchField) {
        this.itemSearchField = searchField;
    }

    // ========== Standard mode initialization ==========

    /**
     * Initialize the standard mode panel: create search field, VirtualMEMonitorableSlot grid,
     * and scrollbar. Call from the host panel's initGui after rows and pin offsets are calculated.
     *
     * @param normalSlotRows   the number of rows available for normal ME slots (after pin rows)
     * @param normalSlotOffsetY the absolute Y offset where normal ME slots should start
     *                         (accounts for pin rows: 18 + totalPinRows * 18)
     */
    public void initStandardPanel(int normalSlotRows, int normalSlotOffsetY) {
        if (layout.isSidePanel()) {
            return;
        }

        final int guiLeft = host.getGuiLeft();

        // Create search field via SearchBarModule
        this.itemSearchField = this.searchBarModule.initSingleField(
                SearchBarModule.SearchFieldSpec.builder(
                        Math.max(layout.getSearchFieldX(), guiLeft),
                        layout.getSearchFieldY(),
                        layout.getSearchFieldWidth())
                        .height(layout.getSearchFieldHeight())
                        .onTextChange(this::updateSearchText)
                        .build());

        // SearchBoxMode JEI sync
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        if (isJEIEnabled && Platform.isJEIEnabled()) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (!memoryText.isEmpty()) {
            this.itemSearchField.setText(memoryText);
            this.itemRepo.setSearchString(memoryText);
        }

        // Clear old ME Virtual slots
        host.getPanel().getGuiSlots().removeIf(s -> s instanceof VirtualMEMonitorableSlot);

        // Create VirtualMEMonitorableSlot grid with pin offset
        createStandardVirtualSlots(normalSlotRows, normalSlotOffsetY);

        // Setup scrollbar
        setupStandardScrollbar(normalSlotRows);

        this.itemRepo.setPower(true);
    }

    /**
     * Create VirtualMEMonitorableSlot grid for standard mode with pin offset.
     */
    private void createStandardVirtualSlots(int normalSlotRows, int normalSlotOffsetY) {
        for (int row = 0; row < normalSlotRows; row++) {
            for (int col = 0; col < layout.getCols(); col++) {
                final int slotIdx = col + row * layout.getCols();
                final int slotX = host.getGuiLeft() + col * 18;
                final int slotY = normalSlotOffsetY + row * 18;
                host.getPanel().getGuiSlots().add(new VirtualMEMonitorableSlot(
                        slotIdx, slotX, slotY, this.itemRepo, slotIdx));
            }
        }
    }

    /**
     * Setup the scrollbar for standard mode.
     */
    private void setupStandardScrollbar(int normalSlotRows) {
        this.itemPanelScrollbar.setLeft(host.getGuiLeft() + layout.getPanelWidth() - layout.getScrollbarLeftOffset())
                .setTop(host.getGuiTop() + layout.getGridOffsetY() + layout.getScrollbarTopOffset())
                .setHeight(normalSlotRows * 18 - 2);
        this.updateItemPanelScrollbar();
    }

    /**
     * Apply terminal search configuration to the search field.
     * Call from the host panel's initGui after initStandardPanel().
     *
     * @param searchConfig the terminal search configuration
     * @param memoryText   the persisted search text
     * @param textChangeListener callback when search text changes (updates repo + scrollbar)
     * @deprecated Use {@link #getSearchBarModule()} and
     *             {@link SearchBarModule#applyTerminalSearchConfig(SearchBarModule.TerminalSearchConfig, Consumer)} instead.
     */
    @Deprecated
    public void applySearchConfig(MUITextFieldWidget.TerminalSearchConfig searchConfig,
            String memoryText, Consumer<String> textChangeListener) {
        if (this.itemSearchField != null) {
            this.itemSearchField.applyTerminalSearchConfig(searchConfig, memoryText, textChangeListener);
        }
    }

    /**
     * Apply terminal search configuration via SearchBarModule.
     * Call from the host panel's initGui after initStandardPanel().
     *
     * @param searchConfig the terminal search configuration
     * @param textChangeListener callback when search text changes (updates repo + scrollbar)
     */
    public void applySearchConfig(SearchBarModule.TerminalSearchConfig searchConfig,
            Consumer<String> textChangeListener) {
        this.searchBarModule.setMemoryText(memoryText);
        this.searchBarModule.applyTerminalSearchConfig(searchConfig, textChangeListener);
        memoryText = this.searchBarModule.getMemoryText();
    }

    // ========== Position calculation (compact mode) ==========

    /**
     * Get the panel's absolute X coordinate (compact mode).
     */
    public int getPanelAbsX() {
        if (layout.isSidePanel()) {
            return host.getGuiLeft() - layout.getPanelWidth()
                    + (dragState != null ? dragState.getDragOffsetX() : 0);
        }
        return host.getGuiLeft();
    }

    /**
     * Get the panel's absolute Y coordinate (compact mode).
     */
    public int getPanelAbsY() {
        if (layout.isSidePanel()) {
            int height = layout.getPanelHeight() > 0 ? layout.getPanelHeight() : rows * 18 + layout.getGridOffsetY();
            return host.getGuiTop() + host.getYSize() - height
                    + (dragState != null ? dragState.getDragOffsetY() : 0);
        }
        return host.getGuiTop();
    }

    /**
     * Get the panel's X offset relative to guiLeft (compact mode).
     */
    public int getPanelRelX() {
        if (layout.isSidePanel()) {
            return -layout.getPanelWidth() + (dragState != null ? dragState.getDragOffsetX() : 0);
        }
        return 0;
    }

    /**
     * Get the panel's Y offset relative to guiTop (compact mode).
     */
    public int getPanelRelY() {
        if (layout.isSidePanel()) {
            int height = layout.getPanelHeight() > 0 ? layout.getPanelHeight() : rows * 18 + layout.getGridOffsetY();
            return host.getYSize() - height + (dragState != null ? dragState.getDragOffsetY() : 0);
        }
        return 0;
    }

    // ========== Initialization ==========

    /**
     * Initialize drag state. Call at the beginning of initGui (compact mode only).
     */
    public void initDragState() {
        if (!layout.isDraggable()) {
            return;
        }
        this.dragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = getPanelAbsX();
            final int absY = getPanelAbsY();
            return mouseX >= absX && mouseX < absX + layout.getPanelWidth()
                    && mouseY >= absY && mouseY < absY + layout.getGridOffsetY();
        });
    }

    /**
     * Create search field, sort buttons, and VirtualMEMonitorableSlot grid.
     * Call from initGui after rows are calculated.
     */
    public void initPanel() {
        final int itemAbsX = getPanelAbsX();
        final int itemAbsY = getPanelAbsY();
        final int itemRelX = getPanelRelX();
        final int itemRelY = getPanelRelY();
        // Search field (created via SearchBarModule)
        this.itemSearchField = this.searchBarModule.initSingleField(
                SearchBarModule.SearchFieldSpec.builder(
                        layout.getSearchFieldX(),
                        layout.getSearchFieldY(),
                        layout.getSearchFieldWidth())
                        .height(layout.getSearchFieldHeight())
                        .onTextChange(this::updateSearchText)
                        .build());

        // SearchBoxMode JEI sync
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        if (isJEIEnabled && Platform.isJEIEnabled()) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (!memoryText.isEmpty()) {
            this.itemSearchField.setText(memoryText);
            this.itemRepo.setSearchString(memoryText);
        }

        // Clear old ME Virtual slots
        host.getPanel().getGuiSlots().removeIf(s -> s instanceof VirtualMEMonitorableSlot);

        // Create VirtualMEMonitorableSlot grid
        createVirtualSlots(itemRelX, itemRelY);

        // Setup scrollbar
        setupScrollbar(itemRelX, itemRelY);

        this.itemRepo.setPower(true);
    }

    /**
     * Create VirtualMEMonitorableSlot grid at the specified relative position.
     */
    private void createVirtualSlots(int relX, int relY) {
        int effectiveRows = layout.getFixedRows() > 0 ? layout.getFixedRows() : rows;
        for (int row = 0; row < effectiveRows; row++) {
            for (int col = 0; col < layout.getCols(); col++) {
                final int slotIdx = col + row * layout.getCols();
                final int slotX = relX + layout.getGridOffsetX() + col * 18;
                final int slotY = relY + layout.getGridOffsetY() + row * 18;
                host.getPanel().getGuiSlots().add(new VirtualMEMonitorableSlot(
                        slotIdx, slotX, slotY, this.itemRepo, slotIdx));
            }
        }
    }

    /**
     * Setup the scrollbar position and range.
     */
    private void setupScrollbar(int relX, int relY) {
        int effectiveRows = layout.getFixedRows() > 0 ? layout.getFixedRows() : rows;
        this.itemPanelScrollbar.setLeft(relX + layout.getPanelWidth() - layout.getScrollbarLeftOffset())
                .setTop(relY + layout.getGridOffsetY() + layout.getScrollbarTopOffset())
                .setHeight(effectiveRows * 18 - 2);
        this.updateItemPanelScrollbar();
    }

    // ========== Scrollbar ==========

    public void updateItemPanelScrollbar() {
        int effectiveRows = layout.getFixedRows() > 0 ? layout.getFixedRows() : rows;
        this.itemPanelScrollbar.setRange(0,
                (this.itemRepo.size() + layout.getCols() - 1) / layout.getCols() - effectiveRows,
                Math.max(1, effectiveRows / 6));
    }

    // ========== Data updates ==========

    /**
     * Receive ME network inventory updates using RepoEntry (preferred path).
     */
    public void postRepoEntryUpdate(final List<ItemRepo.RepoEntry> entries) {
        for (final ItemRepo.RepoEntry entry : entries) {
            this.itemRepo.postUpdate(entry);
        }
        this.itemRepo.updateView();
        this.updateItemPanelScrollbar();
    }

    // ========== Rendering: drawBG ==========

    /**
     * Draw the ME browser panel background (compact mode with own texture).
     * For standard mode, the host panel handles background drawing.
     */
    public void drawBG(int offsetX, int offsetY) {
        if (!layout.hasOwnTexture()) {
            return;
        }

        final int panelX = getPanelAbsX();
        final int panelY = getPanelAbsY();

        GlStateManager.color(1, 1, 1, 1);
        host.getPanel().mc.getTextureManager().bindTexture(layout.getTexture());
        host.getPanel().drawTexturedModalRect(panelX, panelY, 0, 0, layout.getPanelWidth(), layout.getPanelHeight());

        // Draw scrollbar
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        this.itemPanelScrollbar.draw(host.getPanel());
        GlStateManager.popMatrix();

        // Draw search field background
        if (this.itemSearchField != null) {
            this.itemSearchField.setPosition(
                    getPanelRelX() + layout.getSearchFieldX(),
                    getPanelRelY() + layout.getSearchFieldY());
            this.itemSearchField.drawBackground(host.getPanel(), host.getGuiLeft(), host.getGuiTop(), 0, 0, 0.0F);
        }
    }

    // ========== Input: keyTyped ==========

    /**
     * Handle keyboard events (search field input).
     *
     * @return true if the event was consumed
     */
    public boolean keyTyped(char character, int key) {
        if (this.itemSearchField != null && this.itemSearchField.isFocused()
                && this.itemSearchField.textboxKeyTyped(character, key)) {
            return true;
        }
        return false;
    }

    // ========== Input: mouseClicked ==========

    /**
     * Handle mouse clicks (search field focus + right-click clear).
     */
    public void mouseClicked(int xCoord, int yCoord, int btn) {
        if (this.itemSearchField != null) {
            if (layout.isSidePanel()) {
                this.itemSearchField.setPosition(
                        getPanelRelX() + layout.getSearchFieldX(),
                        getPanelRelY() + layout.getSearchFieldY());
            }
            this.searchBarModule.handleMouseClicked(xCoord, yCoord, btn);
            if (btn == 1 && this.isMouseOverSearchField(xCoord, yCoord)) {
                this.itemSearchField.setText("");
            }
        }
    }

    // ========== Input: mouseWheel ==========

    /**
     * Handle mouse wheel scrolling within the browser area.
     *
     * @return true if the event was consumed
     */
    public boolean mouseWheelEvent(int x, int y, int wheel) {
        if (layout.isSidePanel()) {
            final int panelX = getPanelAbsX();
            final int panelY = getPanelAbsY();
            int panelHeight = layout.getPanelHeight() > 0 ? layout.getPanelHeight()
                    : rows * 18 + layout.getGridOffsetY();

            if (x >= panelX && x < panelX + layout.getPanelWidth()
                    && y >= panelY && y < panelY + panelHeight) {
                this.itemPanelScrollbar.wheel(wheel);
                this.itemRepo.updateView();
                return true;
            }
        }
        return false;
    }

    /**
     * Try to handle scrollbar mouse drag.
     *
     * @return true if the scrollbar scroll position changed
     */
    public boolean handleScrollbarClick(int mouseX, int mouseY) {
        final int oldScroll = this.itemPanelScrollbar.getCurrentScroll();
        this.itemPanelScrollbar.click(host.getPanel(), mouseX - host.getGuiLeft(), mouseY - host.getGuiTop());
        if (oldScroll != this.itemPanelScrollbar.getCurrentScroll()) {
            this.itemRepo.updateView();
            return true;
        }
        return false;
    }

    // ========== GUI close ==========

    /**
     * Save search field text to memoryText. Call from onGuiClosed.
     */
    public void onGuiClosed() {
        this.searchBarModule.onGuiClosed();
        memoryText = this.searchBarModule.getMemoryText();
    }

    // ========== Search field focus ==========

    /**
     * Check if the search field has focus.
     */
    public boolean isSearchFieldFocused() {
        return this.searchBarModule.isAnyFieldFocused();
    }

    /**
     * Set search field focus.
     */
    public void setSearchFieldFocused(boolean focused) {
        this.searchBarModule.setFocused(focused);
    }

    // ========== Config update callback ==========

    /**
     * Refresh the item repo view when a config setting changes.
     * Sort button states are now managed by {@link TerminalToolbar#updateSetting}.
     */
    public void updateSetting() {
        this.itemRepo.updateView();
    }

    // ========== ISortSource ==========

    @Override
    public Enum getSortBy() {
        return host.getConfigSrc().getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return host.getConfigSrc().getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return host.getConfigSrc().getSetting(Settings.VIEW_MODE);
    }

    // ========== Internal helpers ==========

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        final int absX = getPanelAbsX();
        final int absY = getPanelAbsY();
        return mouseX >= absX + layout.getSearchFieldX()
                && mouseX < absX + layout.getSearchFieldX() + layout.getSearchFieldWidth()
                && mouseY >= absY + layout.getSearchFieldY()
                && mouseY < absY + layout.getSearchFieldY() + layout.getSearchFieldHeight();
    }

    private void updateSearchText(String searchText) {
        this.itemRepo.setSearchString(searchText);
        this.itemRepo.updateView();
        this.updateItemPanelScrollbar();

        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
        if (isJEISync && Platform.isJEIEnabled()) {
            Integrations.jei().setSearchText(searchText);
        }
    }

    /**
     * Get the panel's absolute screen coordinates (JEI exclusion area).
     */
    public java.awt.Rectangle getJEIExclusionRect() {
        int panelHeight = layout.getPanelHeight() > 0 ? layout.getPanelHeight()
                : rows * 18 + layout.getGridOffsetY();
        return new java.awt.Rectangle(getPanelAbsX(), getPanelAbsY(), layout.getPanelWidth(), panelHeight);
    }

    // ========== SearchBarModule accessor ==========

    /**
     * Get the search bar module for external access (e.g. from host panel).
     */
    public SearchBarModule getSearchBarModule() {
        return this.searchBarModule;
    }

    // ========== SearchBarModule.Host implementation ==========

    private final class SearchBarHost implements SearchBarModule.Host {
        @Override
        public AEBasePanel getPanel() {
            return host.getPanel();
        }

        @Override
        public int getGuiLeft() {
            return host.getGuiLeft();
        }

        @Override
        public int getGuiTop() {
            return host.getGuiTop();
        }

        @Override
        public void requestReinitialize() {
            host.requestReinitialize();
        }
    }
}

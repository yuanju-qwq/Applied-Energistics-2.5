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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import appeng.api.config.ActionItems;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.stacks.AEKeyType;
import appeng.api.util.IConfigManager;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.client.mui.widgets.MUITypeFilterButton;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;

/**
 * Terminal toolbar module — panel-level buttons for terminal GUIs.
 *
 * <p>Supports two layout modes:
 * <ul>
 *   <li>{@link #STANDARD} — left-margin button column (MUIButtonWidget), used by MUIMEMonitorablePanel</li>
 *   <li>{@link #COMPACT} — side panel left-edge buttons (MUIButtonWidget), used by WirelessDualInterfaceTerminal</li>
 * </ul>
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Terminal style button</li>
 *   <li>Sort / view / sort direction / search mode buttons</li>
 *   <li>Type filter toggle buttons (using {@link MUITypeFilterButton} driven by {@link AEKeyType})</li>
 *   <li>Pins state button</li>
 *   <li>Crafting status tab button</li>
 * </ul>
 */
public class TerminalToolbar {

    // ========== Layout mode ==========

    public static final String LAYOUT_STANDARD = "standard";
    public static final String LAYOUT_COMPACT = "compact";

    // ========== Host interface ==========

    public interface Host {
        IConfigManager getConfigManager();

        ItemRepo getRepo();

        AEBasePanel getPanel();

        boolean hasViewCell();

        boolean isWirelessTerm();

        boolean isPortableCell();

        boolean isSecurityStation();

        int getJeiOffset();

        int getGuiLeft();

        int getGuiTop();

        int getRows();

        void reinitializeGui();

        void updateScrollBar();

        TerminalPinSystem getPinSystem();

        // --- Compact mode support ---

        /**
         * Returns {@code true} for compact side-panel layout, {@code false} for standard main-panel layout.
         */
        boolean isCompactLayout();

        /**
         * X offset of the compact panel relative to {@code guiLeft} (for MUIButtonWidget positioning).
         * Returns 0 in standard layout.
         */
        int getCompactPanelRelX();

        /**
         * Y offset of the compact panel relative to {@code guiTop} (for MUIButtonWidget positioning).
         * Returns 0 in standard layout.
         */
        int getCompactPanelRelY();

        /**
         * Width of the compact panel. Only meaningful in compact mode.
         */
        int getCompactPanelWidth();

        /**
         * Height of the compact panel. Only meaningful in compact mode.
         */
        int getCompactPanelHeight();
    }

    // ========== Panel-level buttons ==========

    private final Host host;

    private MUIButtonWidget terminalStyleBox;
    private MUIButtonWidget pinsStateButton;
    private MUITabContainer craftingStatusBtn;

    // ========== Sort/view buttons ==========

    private MUIButtonWidget sortByBox;
    private MUIButtonWidget sortDirBox;
    private MUIButtonWidget viewBox;
    private MUIButtonWidget searchBoxSettings;

    // ========== Type filter buttons ==========

    private final List<MUITypeFilterButton> typeFilterButtons = new ArrayList<>();
    private int typeFilterCount = 0;

    public TerminalToolbar(Host host) {
        this.host = host;
    }

    // ========== Accessors ==========

    public MUIButtonWidget getTerminalStyleBox() {
        return terminalStyleBox;
    }

    public int getTypeFilterButtonCount() {
        return typeFilterCount;
    }

    public List<MUITypeFilterButton> getTypeFilterButtons() {
        return typeFilterButtons;
    }

    // ========== Build & register ==========

    /**
     * Create and register all toolbar buttons.
     *
     * <p>In standard layout this creates all buttons (sort + terminal style + type filter + pins + crafting).
     * In compact layout this only creates the sort buttons.
     *
     * <p>Call from {@code setupWidgets()} for standard mode, or from {@code initGui()} for compact mode.
     */
    public void buildAndRegister() {
        if (host.isCompactLayout()) {
            buildCompactSortButtons();
        } else {
            buildStandardSortButtons();
            buildStandardToolbarButtons();
        }
    }

    /**
     * Build compact-mode sort buttons ({@link MUIButtonWidget}).
     * Positioned relative to the compact panel's offset from {@code guiLeft}/{@code guiTop}.
     */
    private void buildCompactSortButtons() {
        AEBasePanel panel = host.getPanel();
        IConfigManager configSrc = host.getConfigManager();
        int relX = host.getCompactPanelRelX() - 18;
        int relY = host.getCompactPanelRelY() + 18;

        this.sortByBox = new MUIButtonWidget(relX, relY, Settings.SORT_BY,
                configSrc.getSetting(Settings.SORT_BY));
        this.sortByBox.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.sortByBox);
        relY += 20;

        this.viewBox = new MUIButtonWidget(relX, relY, Settings.VIEW_MODE,
                configSrc.getSetting(Settings.VIEW_MODE));
        this.viewBox.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.viewBox);
        relY += 20;

        this.sortDirBox = new MUIButtonWidget(relX, relY, Settings.SORT_DIRECTION,
                configSrc.getSetting(Settings.SORT_DIRECTION));
        this.sortDirBox.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.sortDirBox);
        relY += 20;

        this.searchBoxSettings = new MUIButtonWidget(relX, relY, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        this.searchBoxSettings.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.searchBoxSettings);
    }

    /**
     * Build standard-mode sort buttons ({@link MUIButtonWidget}, registered via {@code addWidget}).
     */
    private void buildStandardSortButtons() {
        AEBasePanel panel = host.getPanel();
        IConfigManager configSrc = host.getConfigManager();
        int offset = 8 + host.getJeiOffset();

        this.sortByBox = new MUIButtonWidget(-18, offset, Settings.SORT_BY,
                configSrc.getSetting(Settings.SORT_BY));
        this.sortByBox.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.sortByBox);
        offset += 20;

        if (host.hasViewCell()) {
            this.viewBox = new MUIButtonWidget(-18, offset, Settings.VIEW_MODE,
                    configSrc.getSetting(Settings.VIEW_MODE));
            this.viewBox.setOnClick(btn -> handleSortButtonClick(btn));
            panel.addWidget(this.viewBox);
            offset += 20;
        }

        this.sortDirBox = new MUIButtonWidget(-18, offset, Settings.SORT_DIRECTION,
                configSrc.getSetting(Settings.SORT_DIRECTION));
        this.sortDirBox.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.sortDirBox);
        offset += 20;

        this.searchBoxSettings = new MUIButtonWidget(-18, offset, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        this.searchBoxSettings.setOnClick(btn -> handleSortButtonClick(btn));
        panel.addWidget(this.searchBoxSettings);
    }

    /**
     * Build standard-mode toolbar buttons (terminal style, type filter, pins, crafting status).
     */
    private void buildStandardToolbarButtons() {
        AEBasePanel panel = host.getPanel();
        boolean isPortableCell = host.isPortableCell();
        boolean isWireless = host.isWirelessTerm();
        boolean viewCell = host.hasViewCell();

        // --- Terminal style button ---
        if (!isPortableCell || isWireless) {
            terminalStyleBox = new MUIButtonWidget(-18, 0, Settings.TERMINAL_STYLE,
                    AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));
            terminalStyleBox.setOnClick(btn -> handleSettingsButtonClick(btn));
            panel.addWidget(terminalStyleBox);
        }

        // --- Type filter buttons (AEKeyType-based) ---
        buildTypeFilterButtons(panel);

        // --- Pins button ---
        pinsStateButton = new MUIButtonWidget(178, 18 + (host.getRows() * 18) + 25,
                Settings.ACTIONS, ActionItems.PINS);
        pinsStateButton.setOnClick(btn -> host.getPinSystem().handlePinsButtonClick());
        panel.addWidget(pinsStateButton);

        // --- Crafting status tab ---
        if (viewCell || isWireless) {
            craftingStatusBtn = new MUITabContainer(170, -4, 2 + 11 * 16,
                    GuiText.CraftingStatus.getLocal());
            craftingStatusBtn.setHideEdge(13);
            craftingStatusBtn.setOnClick(tab -> {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.CRAFTING_STATUS));
            });
            panel.addWidget(craftingStatusBtn);
        }
    }

    /**
     * Build type filter toggle buttons from all registered {@link AEKeyType}s.
     */
    private void buildTypeFilterButtons(AEBasePanel panel) {
        typeFilterButtons.clear();

        List<AEKeyType> sortedTypes = AEKeyType.getSortedTypes();
        if (sortedTypes.size() <= 1) {
            typeFilterCount = 0;
            return;
        }

        for (AEKeyType keyType : sortedTypes) {
            boolean currentlyEnabled = host.getRepo().isTypeEnabled(keyType);
            MUITypeFilterButton btn = new MUITypeFilterButton(0, 0, keyType, clicked -> {
                host.getRepo().setTypeFilter(clicked.getKeyType(), clicked.isTypeEnabled());
                host.getRepo().updateView();
                host.updateScrollBar();
            });
            btn.setTypeEnabled(currentlyEnabled);
            panel.addWidget(btn);
            typeFilterButtons.add(btn);
        }

        typeFilterCount = typeFilterButtons.size();
    }

    /**
     * Position type filter buttons and terminal style button after layout is known.
     * Call from {@code initGui()} after guiLeft/guiTop are finalized (standard mode only).
     */
    public void positionTypeFilterButtons() {
        if (host.isCompactLayout()) {
            return;
        }

        int sortButtonCount = getVisibleSortButtonCount();
        int settingsButtonCount = sortButtonCount + (terminalStyleBox != null ? 1 : 0);

        int typeOffset = host.getGuiTop() + 8 + host.getJeiOffset() + settingsButtonCount * 20;

        // Position terminal style button right after sort buttons
        if (terminalStyleBox != null) {
            terminalStyleBox.setPosition(-18, 8 + host.getJeiOffset() + sortButtonCount * 20 - host.getGuiTop());
        }

        // Position type filter buttons after terminal style button
        for (MUITypeFilterButton btn : typeFilterButtons) {
            btn.setPosition(-18, typeOffset - host.getGuiTop());
            typeOffset += 18;
        }
    }

    // ========== Sort button click (standard mode) ==========

    /**
     * Common click handler for sort/view/search-mode MUI buttons (standard mode).
     */
    private void handleSortButtonClick(MUIButtonWidget btn) {
        final Settings setting = btn.getSetting();
        if (setting == null || setting == Settings.ACTIONS) {
            return;
        }

        final boolean backwards = Mouse.isButtonDown(1);
        final Enum<?> cv = btn.getCurrentValue();
        final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                setting.getPossibleValues());

        if (btn == this.searchBoxSettings) {
            AEConfig.instance().getConfigManager().putSetting(setting, next);
        } else {
            try {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(setting.name(), next.name()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        btn.set(next);

        if (next.getClass() == SearchBoxMode.class) {
            host.reinitializeGui();
        }
    }

    // ========== Settings button click ==========

    /**
     * Common click handler for panel-level settings buttons (terminal style, etc.).
     */
    private void handleSettingsButtonClick(MUIButtonWidget btn) {
        final Settings setting = btn.getSetting();
        if (setting == null || setting == Settings.ACTIONS) {
            return;
        }

        final boolean backwards = Mouse.isButtonDown(1);
        final Enum<?> cv = btn.getCurrentValue();
        final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                setting.getPossibleValues());

        if (btn == this.terminalStyleBox) {
            AEConfig.instance().getConfigManager().putSetting(setting, next);
        } else {
            try {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(setting.name(), next.name()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        btn.set(next);

        if (next.getClass() == TerminalStyle.class) {
            host.reinitializeGui();
        }
    }

    // ========== updateSetting callback ==========

    /**
     * Update all button states from the config manager after a setting changed on the server side.
     */
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
        if (this.sortByBox != null) {
            this.sortByBox.set(manager.getSetting(Settings.SORT_BY));
        }
        if (this.sortDirBox != null) {
            this.sortDirBox.set(manager.getSetting(Settings.SORT_DIRECTION));
        }
        if (this.viewBox != null) {
            this.viewBox.set(manager.getSetting(Settings.VIEW_MODE));
        }
    }

    // ========== JEI exclusion helpers ==========

    /**
     * Get the number of visible sort buttons (for type filter positioning and JEI exclusion).
     */
    public int getVisibleSortButtonCount() {
        int count = 0;
        if (sortByBox != null && sortByBox.isVisible()) count++;
        if (viewBox != null && viewBox.isVisible()) count++;
        if (sortDirBox != null && sortDirBox.isVisible()) count++;
        if (searchBoxSettings != null && searchBoxSettings.isVisible()) count++;
        return count;
    }

    public int getVisibleSettingsButtonCount() {
        int count = 0;
        if (terminalStyleBox != null && terminalStyleBox.isVisible())
            count++;
        return count;
    }

    public int getVisibleTypeFilterButtonCount() {
        return (int) typeFilterButtons.stream().filter(MUITypeFilterButton::isVisible).count();
    }
}

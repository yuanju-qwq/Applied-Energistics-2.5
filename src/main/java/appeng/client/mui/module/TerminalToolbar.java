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
import java.util.Collection;
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
 * Terminal toolbar module extracted from {@code MUIMEMonitorablePanel}.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Left-side settings buttons: sort, view, sort direction, search mode, terminal style</li>
 *   <li>Type filter toggle buttons (using {@link MUITypeFilterButton} driven by {@link AEKeyType})</li>
 *   <li>Pins state button</li>
 *   <li>Crafting status tab button</li>
 *   <li>Settings button click handling (cycle + network send)</li>
 *   <li>{@code updateSetting} callback synchronization</li>
 * </ul>
 */
public class TerminalToolbar {

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
    }

    // ========== Settings buttons ==========

    private final Host host;

    private MUIButtonWidget sortByBox;
    private MUIButtonWidget viewBox;
    private MUIButtonWidget sortDirBox;
    private MUIButtonWidget searchBoxSettings;
    private MUIButtonWidget terminalStyleBox;
    private MUIButtonWidget pinsStateButton;
    private MUITabContainer craftingStatusBtn;

    // ========== Type filter buttons ==========

    private final List<MUITypeFilterButton> typeFilterButtons = new ArrayList<>();
    private int typeFilterCount = 0;

    public TerminalToolbar(Host host) {
        this.host = host;
    }

    // ========== Accessors ==========

    public MUIButtonWidget getSortByBox() {
        return sortByBox;
    }

    public MUIButtonWidget getViewBox() {
        return viewBox;
    }

    public MUIButtonWidget getSortDirBox() {
        return sortDirBox;
    }

    public MUIButtonWidget getSearchBoxSettings() {
        return searchBoxSettings;
    }

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
     * Create and register all toolbar buttons onto the panel.
     * Call from {@code setupWidgets()}.
     */
    public void buildAndRegister() {
        AEBasePanel panel = host.getPanel();
        IConfigManager configSrc = host.getConfigManager();
        boolean viewCell = host.hasViewCell();
        boolean isWireless = host.isWirelessTerm();

        // --- settings buttons ---
        int offset = 8 + host.getJeiOffset();

        sortByBox = new MUIButtonWidget(-18, offset, Settings.SORT_BY,
                configSrc.getSetting(Settings.SORT_BY));
        sortByBox.setOnClick(btn -> handleSettingsButtonClick(btn));
        panel.addWidget(sortByBox);
        offset += 20;

        if (viewCell || isWireless) {
            viewBox = new MUIButtonWidget(-18, offset, Settings.VIEW_MODE,
                    configSrc.getSetting(Settings.VIEW_MODE));
            viewBox.setOnClick(btn -> handleSettingsButtonClick(btn));
            panel.addWidget(viewBox);
            offset += 20;
        }

        sortDirBox = new MUIButtonWidget(-18, offset, Settings.SORT_DIRECTION,
                configSrc.getSetting(Settings.SORT_DIRECTION));
        sortDirBox.setOnClick(btn -> handleSettingsButtonClick(btn));
        panel.addWidget(sortDirBox);
        offset += 20;

        searchBoxSettings = new MUIButtonWidget(-18, offset, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        searchBoxSettings.setOnClick(btn -> handleSettingsButtonClick(btn));
        panel.addWidget(searchBoxSettings);
        offset += 20;

        if (!host.isPortableCell() || isWireless) {
            terminalStyleBox = new MUIButtonWidget(-18, offset, Settings.TERMINAL_STYLE,
                    AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));
            terminalStyleBox.setOnClick(btn -> handleSettingsButtonClick(btn));
            panel.addWidget(terminalStyleBox);
            offset += 20;
        }

        // --- type filter buttons (AEKeyType-based, replacing legacy TypeToggleButton) ---
        buildTypeFilterButtons(panel);

        // --- pins button ---
        pinsStateButton = new MUIButtonWidget(178, 18 + (host.getRows() * 18) + 25,
                Settings.ACTIONS, ActionItems.PINS);
        pinsStateButton.setOnClick(btn -> host.getPinSystem().handlePinsButtonClick());
        panel.addWidget(pinsStateButton);

        // --- crafting status tab ---
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
     * Position type filter buttons after layout is known.
     * Call from {@code initGui()} after guiLeft/guiTop are finalized.
     */
    public void positionTypeFilterButtons() {
        if (typeFilterCount == 0) {
            return;
        }

        int settingsButtonCount = 0;
        if (sortByBox != null)
            settingsButtonCount++;
        if (viewBox != null)
            settingsButtonCount++;
        if (sortDirBox != null)
            settingsButtonCount++;
        if (searchBoxSettings != null)
            settingsButtonCount++;
        if (terminalStyleBox != null)
            settingsButtonCount++;

        int typeOffset = host.getGuiTop() + 8 + host.getJeiOffset() + settingsButtonCount * 20;
        for (MUITypeFilterButton btn : typeFilterButtons) {
            btn.setPosition(-18, typeOffset - host.getGuiTop());
            typeOffset += 18;
        }
    }

    // ========== Settings button click ==========

    /**
     * Common click handler for all settings-mode MUI buttons.
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

        if (btn == this.terminalStyleBox || btn == this.searchBoxSettings) {
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

        if (next.getClass() == SearchBoxMode.class || next.getClass() == TerminalStyle.class) {
            host.reinitializeGui();
        }
    }

    // ========== updateSetting callback ==========

    /**
     * Update button states from the config manager after a setting changed on the server side.
     */
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
        if (sortByBox != null) {
            sortByBox.set(manager.getSetting(Settings.SORT_BY));
        }
        if (sortDirBox != null) {
            sortDirBox.set(manager.getSetting(Settings.SORT_DIRECTION));
        }
        if (viewBox != null) {
            viewBox.set(manager.getSetting(Settings.VIEW_MODE));
        }
    }

    // ========== JEI exclusion helpers ==========

    public int getVisibleSettingsButtonCount() {
        int count = 0;
        if (sortByBox != null && sortByBox.isVisible())
            count++;
        if (viewBox != null && viewBox.isVisible())
            count++;
        if (sortDirBox != null && sortDirBox.isVisible())
            count++;
        if (searchBoxSettings != null && searchBoxSettings.isVisible())
            count++;
        if (terminalStyleBox != null && terminalStyleBox.isVisible())
            count++;
        return count;
    }

    public int getVisibleTypeFilterButtonCount() {
        return (int) typeFilterButtons.stream().filter(MUITypeFilterButton::isVisible).count();
    }
}
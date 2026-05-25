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
 * Terminal toolbar module — panel-level buttons for MUIMEMonitorablePanel.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Terminal style button</li>
 *   <li>Type filter toggle buttons (using {@link MUITypeFilterButton} driven by {@link AEKeyType})</li>
 *   <li>Pins state button</li>
 *   <li>Crafting status tab button</li>
 * </ul>
 *
 * <p>Sort / view / sort direction / search mode buttons are managed by
 * {@link MEItemBrowserModule} (both compact and standard layouts).
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

        /**
         * Get the ME item browser module (for sort button count and positioning).
         */
        MEItemBrowserModule getBrowserModule();
    }

    // ========== Panel-level buttons ==========

    private final Host host;

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
     * Create and register panel-level toolbar buttons onto the panel.
     * Call from {@code setupWidgets()} after {@link MEItemBrowserModule#buildAndRegisterSortButtons()}.
     */
    public void buildAndRegister() {
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
     * Call from {@code initGui()} after guiLeft/guiTop are finalized.
     */
    public void positionTypeFilterButtons() {
        // Calculate offset: sort buttons (from MEItemBrowserModule) + terminal style button
        MEItemBrowserModule browserModule = host.getBrowserModule();
        int sortButtonCount = browserModule != null ? browserModule.getVisibleSortButtonCount() : 0;
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

    // ========== Settings button click ==========

    /**
     * Common click handler for panel-level settings buttons.
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
     * Update button states from the config manager after a setting changed on the server side.
     * Note: sort/view/sortDir/searchMode buttons are now handled by MEItemBrowserModule.updateSetting().
     */
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
        // No sort/view buttons to update here — they are managed by MEItemBrowserModule
    }

    // ========== JEI exclusion helpers ==========

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

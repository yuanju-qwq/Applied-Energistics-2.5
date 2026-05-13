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

package appeng.client.mui.screen;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.mui.AEMUITheme;
import appeng.client.gui.widgets.GuiImgLabel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.client.mui.widgets.MUIToggleButton;
import appeng.container.implementations.ContainerPatternProvider;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * MUI pattern provider GUI panel.
 *
 * Manages 36 pattern slots (4 rows x 9 columns, rows 2-3 unlocked via PATTERN_EXPANSION upgrade),
 * along with block mode, unlock mode, interface terminal visibility, and other config buttons.
 */
public class MUIPatternProviderPanel extends MUIUpgradeablePanel {

    private final ContainerPatternProvider container;

    // ========== Buttons ==========
    private MUITabContainer priority;
    private MUIButtonWidget blockMode;
    private MUIButtonWidget unlockMode;
    private MUIToggleButton interfaceMode;
    private GuiImgLabel lockReason;

    public MUIPatternProviderPanel(final ContainerPatternProvider container) {
        super(container);
        this.container = container;
        this.ySize = 204;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addLabel();
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.priority = new MUITabContainer(154, 0, 2 + 4 * 16, GuiText.Priority.getLocal());
        this.priority.setOnClick(tab -> {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        });
        this.addWidget(this.priority);

        this.blockMode = new MUIButtonWidget(-18, 8, Settings.BLOCK, YesNo.NO);
        this.blockMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.blockMode);

        this.unlockMode = new MUIButtonWidget(-18, 26, Settings.UNLOCK, LockCraftingMode.NONE);
        this.unlockMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.unlockMode);

        this.interfaceMode = new MUIToggleButton(-18, 44, 84, 85,
                GuiText.InterfaceTerminal.getLocal(), GuiText.InterfaceTerminalHint.getLocal());
        this.interfaceMode.setOnToggle(btn -> {
            final boolean backwards = org.lwjgl.input.Mouse.isButtonDown(1);
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
        });
        this.addWidget(this.interfaceMode);
    }

    protected void addLabel() {
        if (this.lockReason != null) {
            this.labelList.remove(this.lockReason);
        }
        this.lockReason = new GuiImgLabel(this.fontRenderer, this.guiLeft + 40, this.guiTop + 12, Settings.UNLOCK,
                LockCraftingMode.NONE);
        this.labelList.add(this.lockReason);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        if (this.blockMode != null) {
            this.blockMode.set(this.container.getBlockingMode());
        }
        if (this.unlockMode != null) {
            this.unlockMode.set(this.container.getUnlockMode());
        }
        if (this.interfaceMode != null) {
            this.interfaceMode.setState(this.container.getInterfaceTerminalMode() == YesNo.YES);
        }
        if (this.lockReason != null) {
            this.lockReason.set(this.container.getCraftingLockedReason());
        }

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.PatternProvider.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.Patterns.getLocal(), 8, 25, AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected String getBackground() {
        int upgrades = this.container.getPatternUpgrades();
        if (upgrades == 0) {
            return "guis/patternprovider.png";
        } else {
            return "guis/patternprovider" + upgrades + ".png";
        }
    }
}

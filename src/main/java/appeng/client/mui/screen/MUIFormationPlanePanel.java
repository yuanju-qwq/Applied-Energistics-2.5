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

import org.lwjgl.input.Mouse;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.container.implementations.ContainerFormationPlane;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * MUI formation plane GUI panel.
 *
 * Provides priority, place mode, and fuzzy mode configuration buttons.
 * Filter slots are managed by Container layer SlotFake.
 */
public class MUIFormationPlanePanel extends MUIUpgradeablePanel {

    private final ContainerFormationPlane container;

    // ========== Buttons ==========
    private MUITabContainer priority;
    private MUIButtonWidget placeMode;

    public MUIFormationPlanePanel(final ContainerFormationPlane container) {
        super(container);
        this.container = container;
        this.ySize = 251;
    }

    // ========== Button management ==========

    @Override
    protected void addButtons() {
        this.placeMode = new MUIButtonWidget(-18, 28, Settings.PLACE_BLOCK, YesNo.YES);
        this.placeMode.setOnClick(btn -> {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.PLACE_BLOCK, backwards));
        });
        this.addWidget(this.placeMode);

        this.fuzzyMode = new MUIButtonWidget(-18, 48, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.fuzzyMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.fuzzyMode);

        this.priority = new MUITabContainer(154, 0, 2 + 4 * 16, GuiText.Priority.getLocal());
        this.priority.setOnClick(tab -> {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        });
        this.addWidget(this.priority);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.FormationPlane.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.placeMode != null) {
            this.placeMode.set(this.container.getPlaceMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }
}

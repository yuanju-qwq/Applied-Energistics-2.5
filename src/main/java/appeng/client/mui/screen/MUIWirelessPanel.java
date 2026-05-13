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

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.Settings;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.container.implementations.ContainerWireless;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.tile.networking.TileWireless;
import appeng.util.Platform;

/**
 * MUI wireless access point GUI panel.
 *
 * Displays wireless signal range and power consumption, with a power unit toggle button.
 */
public class MUIWirelessPanel extends AEBasePanel {

    // ========== Buttons ==========
    private MUIButtonWidget units;

    public MUIWirelessPanel(final InventoryPlayer ip, final TileWireless te) {
        this(new ContainerWireless(ip, te));
    }

    public MUIWirelessPanel(final ContainerWireless container) {
        super(container);
        this.ySize = 166;
    }

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        this.units = new MUIButtonWidget(-18, 8, Settings.POWER_UNITS,
                AEConfig.instance().selectedPowerUnit());
        this.units.setOnClick(btn -> {
            final boolean backwards = Mouse.isButtonDown(1);
            AEConfig.instance().nextPowerUnit(backwards);
            this.units.set(AEConfig.instance().selectedPowerUnit());
        });
        this.addWidget(this.units);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Wireless.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        final ContainerWireless cw = (ContainerWireless) this.inventorySlots;

        if (cw.getRange() > 0) {
            final String firstMessage = GuiText.Range.getLocal() + ": " + (cw.getRange() / 10.0) + " m";
            final String secondMessage = GuiText.PowerUsageRate.getLocal() + ": "
                    + Platform.formatPowerLong(cw.getDrain(), true);

            final int strWidth = Math.max(this.fontRenderer.getStringWidth(firstMessage),
                    this.fontRenderer.getStringWidth(secondMessage));
            final int cOffset = (this.xSize / 2) - (strWidth / 2);
            this.fontRenderer.drawString(firstMessage, cOffset, 20, AEMUITheme.COLOR_TITLE);
            this.fontRenderer.drawString(secondMessage, cOffset, 20 + 12, AEMUITheme.COLOR_TITLE);
        }
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/wireless.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

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

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIProgressWidget;
import appeng.client.mui.widgets.MUIProgressWidget.Direction;
import appeng.container.implementations.ContainerVibrationChamber;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileVibrationChamber;

/**
 * MUI vibration chamber GUI panel.
 *
 * Displays AE/t power output progress bar and burning flame animation.
 */
public class MUIVibrationChamberPanel extends AEBasePanel {

    private final ContainerVibrationChamber cvc;

    private static final int PB_X = 99;
    private static final int PB_Y = 36;

    private MUIProgressWidget pb;

    public MUIVibrationChamberPanel(final InventoryPlayer ip, final TileVibrationChamber te) {
        this(new ContainerVibrationChamber(ip, te));
    }

    public MUIVibrationChamberPanel(final ContainerVibrationChamber container) {
        super(container);
        this.cvc = container;
        this.ySize = 166;
    }

    @Override
    protected void setupWidgets() {
        this.pb = this.addWidget(new MUIProgressWidget(this.cvc, "guis/vibchamber.png",
                PB_X, PB_Y, 176, 14, 6, 18, Direction.VERTICAL));
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.VibrationChamber.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        this.pb.setFullMsg(TileVibrationChamber.POWER_PER_TICK * this.cvc.getCurrentProgress()
                / TileVibrationChamber.DILATION_SCALING + " AE/t");

        if (this.cvc.getRemainingBurnTime() > 0) {
            final int i1 = this.cvc.getRemainingBurnTime() * 12 / 100;
            this.bindTexture("guis/vibchamber.png");
            GlStateManager.color(1, 1, 1);
            final int l = -15;
            final int k = 25;
            this.drawTexturedModalRect(k + 56, l + 36 + 12 - i1, 176, 12 - i1, 14, i1 + 2);
        }
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/vibchamber.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

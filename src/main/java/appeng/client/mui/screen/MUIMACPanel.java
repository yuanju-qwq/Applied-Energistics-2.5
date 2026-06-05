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

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUIProgressWidget;
import appeng.client.mui.widgets.MUIProgressWidget.Direction;
import appeng.container.implementations.ContainerMAC;
import appeng.core.localization.GuiText;
import appeng.tile.crafting.TileMolecularAssembler;

/**
 * MUI molecular assembler GUI panel.
 *
 * Extends {@link MUIUpgradeablePanel}, contains redstone mode button and crafting progress bar.
 */
public class MUIMACPanel extends MUIUpgradeablePanel {

    private final ContainerMAC container;

    private static final int PB_X = 148;
    private static final int PB_Y = 48;

    private MUIProgressWidget pb;

    public MUIMACPanel(final InventoryPlayer ip, final TileMolecularAssembler te) {
        this(new ContainerMAC(ip, te));
    }

    public MUIMACPanel(final ContainerMAC container) {
        super(container);
        this.container = container;
        this.ySize = 197;
    }

    @Override
    protected void setupWidgets() {
        super.setupWidgets();
        this.pb = this.addWidget(new MUIProgressWidget(this.container, "guis/mac.png",
                PB_X, PB_Y, 148, 201, 6, 18, Direction.VERTICAL));
    }

    @Override
    protected void addButtons() {
        this.redstoneMode = new MUIButtonWidget(-18, 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.redstoneMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.redstoneMode);
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.pb.setFullMsg(this.container.getCurrentProgress() + "%");
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    protected String getBackground() {
        return "guis/mac.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.MolecularAssembler;
    }
}

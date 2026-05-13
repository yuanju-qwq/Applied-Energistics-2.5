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
import appeng.client.gui.widgets.GuiProgressBar;
import appeng.client.gui.widgets.GuiProgressBar.Direction;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.container.implementations.ContainerCondenser;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.tile.misc.TileCondenser;

/**
 * MUI condenser GUI panel.
 *
 * Contains a stored-energy progress bar and an output mode button.
 */
public class MUICondenserPanel extends AEBasePanel {

    private final ContainerCondenser cvc;

    // ========== Buttons / Progress bar ==========
    private GuiProgressBar pb;
    private MUIButtonWidget mode;

    public MUICondenserPanel(final InventoryPlayer ip, final TileCondenser te) {
        this(new ContainerCondenser(ip, te));
    }

    public MUICondenserPanel(final ContainerCondenser container) {
        super(container);
        this.cvc = container;
        this.ySize = 197;
    }

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        this.mode = new MUIButtonWidget(128, 52, Settings.CONDENSER_OUTPUT, this.cvc.getOutput());
        this.mode.setOnClick(btn -> {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.CONDENSER_OUTPUT, backwards));
        });
        this.addWidget(this.mode);
    }

    @Override
    public void initGui() {
        super.initGui();

        this.pb = new GuiProgressBar(this.cvc, "guis/condenser.png", 120 + this.guiLeft, 25 + this.guiTop, 178, 25, 6,
                18, Direction.VERTICAL, GuiText.StoredEnergy.getLocal());
        this.buttonList.add(this.pb);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Condenser.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        this.mode.set(this.cvc.getOutput());
        this.mode.setFillVar(String.valueOf(this.cvc.getOutput().requiredPower));
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/condenser.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

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

import java.io.IOException;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.Settings;
import appeng.client.mui.AEMUITheme;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerWireless;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.tile.networking.TileWireless;
import appeng.util.Platform;

/**
 * MUI 版无线接入点 GUI 面板�?
 *
 * 显示无线信号范围和功率消耗，以及电源单位切换按钮�?
 */
public class MUIWirelessPanel extends AEBasePanel {

    // ========== 按钮 ==========
    private GuiImgButton units;

    public MUIWirelessPanel(final InventoryPlayer ip, final TileWireless te) {
        this(new ContainerWireless(ip, te));
    }

    public MUIWirelessPanel(final ContainerWireless container) {
        super(container);
        this.ySize = 166;
    }

    // ========== 初始�?==========

    @Override
    protected void setupWidgets() {
        // initGui 处理按钮初始�?
    }

    @Override
    public void initGui() {
        super.initGui();

        this.units = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.POWER_UNITS,
                AEConfig.instance().selectedPowerUnit());
        this.buttonList.add(this.units);
    }

    // ========== 渲染 ==========

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

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.units) {
            AEConfig.instance().nextPowerUnit(backwards);
            this.units.set(AEConfig.instance().selectedPowerUnit());
        }
    }
}

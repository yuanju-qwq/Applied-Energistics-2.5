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

import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerGrinder;
import appeng.core.localization.GuiText;
import appeng.tile.grindstone.TileGrinder;

/**
 * MUI 版石英磨�?GUI 面板�?
 *
 * 纯展示型面板，仅包含背景贴图和标题文字，无按�?交互�?
 */
public class MUIGrinderPanel extends AEBasePanel {

    public MUIGrinderPanel(final InventoryPlayer ip, final TileGrinder te) {
        this(new ContainerGrinder(ip, te));
    }

    public MUIGrinderPanel(final ContainerGrinder container) {
        super(container);
        this.ySize = 176;
    }

    // ========== 初始�?==========

    @Override
    protected void setupWidgets() {
        // 无需额外控件
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.GrindStone.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/grinder.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}

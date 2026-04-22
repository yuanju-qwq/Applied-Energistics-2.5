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

package appeng.client.mui.screen;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerSkyChest;
import appeng.core.localization.GuiText;
import appeng.integration.Integrations;
import appeng.tile.storage.TileSkyChest;

/**
 * MUI 版陨石箱 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiSkyChest}。
 * 纯展示型面板，包含背景贴图、标题文字，以及 InvTweaks 兼容的空格键处理。
 */
public class MUISkyChestPanel extends AEBasePanel {

    public MUISkyChestPanel(final InventoryPlayer ip, final TileSkyChest te) {
        this(new ContainerSkyChest(ip, te));
    }

    public MUISkyChestPanel(final ContainerSkyChest container) {
        super(container);
        this.ySize = 195;
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // 无需额外控件
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.SkyChest.getLocal()), 8, 8, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 2, 4210752);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/skychest.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    // ========== InvTweaks 兼容 ==========

    @Override
    protected boolean enableSpaceClicking() {
        return !Integrations.invTweaks().isEnabled();
    }
}

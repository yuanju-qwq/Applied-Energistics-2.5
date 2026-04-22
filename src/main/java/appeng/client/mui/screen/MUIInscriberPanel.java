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

import appeng.client.gui.widgets.GuiProgressBar;
import appeng.client.gui.widgets.GuiProgressBar.Direction;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerInscriber;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.localization.GuiText;
import appeng.tile.misc.TileInscriber;

/**
 * MUI 版压印器 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiInscriber}。
 * 显示压印进度条、升级区域和工具箱区域。
 */
public class MUIInscriberPanel extends AEBasePanel {

    private final ContainerInscriber cvc;

    // ========== 进度条 ==========
    private GuiProgressBar pb;

    public MUIInscriberPanel(final InventoryPlayer ip, final TileInscriber te) {
        this(new ContainerInscriber(ip, te));
    }

    public MUIInscriberPanel(final ContainerInscriber container) {
        super(container);
        this.cvc = container;
        this.ySize = 176;
        this.xSize = this.hasToolbox() ? 246 : 211;
    }

    private boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // initGui 处理初始化
    }

    @Override
    public void initGui() {
        super.initGui();

        this.pb = new GuiProgressBar(this.cvc, "guis/inscriber.png", 135, 39, 135, 177, 6, 18, Direction.VERTICAL);
        this.buttonList.add(this.pb);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.pb.setFullMsg(this.cvc.getCurrentProgress() * 100 / this.cvc.getMaxProgress() + "%");

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Inscriber.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/inscriber.png");
        this.pb.x = 135 + this.guiLeft;
        this.pb.y = 39 + this.guiTop;

        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, this.ySize);

        if (this.drawUpgrades()) {
            this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + this.cvc.availableUpgrades() * 18);
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, this.ySize - 90, 68, 68);
        }
    }

    private boolean drawUpgrades() {
        return true;
    }
}

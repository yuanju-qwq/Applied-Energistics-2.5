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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerChest;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.tile.storage.TileChest;

/**
 * MUI 版 ME 箱子 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiChest}。
 * 包含 1 个存储单元槽位和一个优先级标签按钮。
 */
public class MUIChestPanel extends AEBasePanel {

    // ========== 按钮 ==========
    private GuiTabButton priority;

    public MUIChestPanel(final InventoryPlayer ip, final TileChest te) {
        this(new ContainerChest(ip, te));
    }

    public MUIChestPanel(final ContainerChest container) {
        super(container);
        this.ySize = 166;
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // initGui 处理按钮初始化
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16,
                GuiText.Priority.getLocal(), this.itemRender));
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Chest.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/chest.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        }
    }
}

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

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerWirelessInterfaceTerminal;
import appeng.helpers.WirelessTerminalGuiObject;

/**
 * MUI 版无线接口终端面板。
 * <p>
 * 继承 {@link MUIInterfaceTerminalPanel}，添加无线升级图标和终端模式切换按钮。
 */
@SideOnly(Side.CLIENT)
public class MUIWirelessInterfaceTermPanelImpl extends MUIInterfaceTerminalPanel implements MUIWirelessTermPanel {

    private final WirelessTerminalHelper wirelessHelper = new WirelessTerminalHelper();

    public MUIWirelessInterfaceTermPanelImpl(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(new ContainerWirelessInterfaceTerminal(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        super.initGui();
        this.wirelessHelper.initButtons(
                ((AEBaseContainer) this.inventorySlots).getPlayerInv(),
                this.guiLeft, this.guiTop, this.buttonList, 200, this.itemRender);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (this.wirelessHelper.handleButtonClick(btn)) {
            return;
        }
        super.actionPerformed(btn);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.wirelessHelper.drawWirelessIcon(offsetX, offsetY, 189, 165);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }
}

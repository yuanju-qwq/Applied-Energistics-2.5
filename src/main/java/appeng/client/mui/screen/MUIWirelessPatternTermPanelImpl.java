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
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.helpers.WirelessTerminalGuiObject;

/**
 * MUI 版无线样板终端面板。
 * <p>
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiWirelessPatternTerminal}。
 * 继承 {@link MUIPatternTermPanel}（通过其无线构造函数），
 * 添加无线升级图标和终端模式切换按钮。
 * <p>
 * 样板编码功能（合成/处理模式、虚拟槽位、4×4 扩展处理布局等）全部复用父类逻辑。
 */
@SideOnly(Side.CLIENT)
public class MUIWirelessPatternTermPanelImpl extends MUIPatternTermPanel implements MUIWirelessTermPanel {

    private final WirelessTerminalHelper wirelessHelper = new WirelessTerminalHelper();

    public MUIWirelessPatternTermPanelImpl(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(inventoryPlayer, te, new ContainerWirelessPatternTerminal(inventoryPlayer, te));
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
        this.wirelessHelper.drawWirelessIcon(offsetX, offsetY, 198, 127);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }
}

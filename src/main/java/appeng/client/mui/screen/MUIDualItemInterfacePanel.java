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
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerDualItemInterface;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * MUI 版 GuiDualItemInterface。
 *
 * 对应旧代码的 {@link appeng.client.gui.implementations.GuiDualItemInterface}。
 * 二合一接口的物品面配置面板，继承 {@link MUIInterfacePanel}，
 * 增加切换到流体面的按钮。
 * @deprecated 使用 {@link MUIMEInterfacePanel} 替代，二合一接口已被取代。
 */
@Deprecated
public class MUIDualItemInterfacePanel extends MUIInterfacePanel {

    private GuiTabButton switchToFluid;

    public MUIDualItemInterfacePanel(final ContainerDualItemInterface container) {
        super(container);
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        // 切换到流体面的按钮
        ItemStack fluidIcon = AEApi.instance().definitions().blocks().fluidIface().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        this.switchToFluid = new GuiTabButton(this.guiLeft + 133, this.guiTop, fluidIcon,
                fluidIcon.getDisplayName(), this.itemRender);
        this.buttonList.add(this.switchToFluid);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.switchToFluid) {
            // 切换到流体面
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.DUAL_FLUID_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

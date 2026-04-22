/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.container.ContainerDualFluidInterface;
import appeng.fluids.helper.IFluidInterfaceHost;

/**
 * MUI 版 GuiDualFluidInterface。
 *
 * 对应旧代码的 {@link appeng.fluids.client.gui.GuiDualFluidInterface}。
 * 二合一接口的流体面配置面板，继承 {@link MUIFluidInterfacePanel}，
 * 增加切换到物品面的按钮。
 * @deprecated 使用 {@link MUIMEInterfacePanel} 替代，二合一接口已被取代。
 */
@Deprecated
public class MUIDualFluidInterfacePanel extends MUIFluidInterfacePanel {

    private GuiTabButton switchToItem;

    public MUIDualFluidInterfacePanel(final ContainerDualFluidInterface container,
            final IFluidInterfaceHost host) {
        super(container, host);
    }

    @Override
    public void initGui() {
        super.initGui();
        // 切换到物品面的按钮
        ItemStack itemIcon = AEApi.instance().definitions().blocks().iface().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        this.switchToItem = new GuiTabButton(this.guiLeft + 133, this.guiTop, itemIcon,
                itemIcon.getDisplayName(), this.itemRender);
        this.buttonList.add(this.switchToItem);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.switchToItem) {
            // 切换到物品面
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.DUAL_ITEM_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

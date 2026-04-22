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

import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.client.gui.widgets.GuiFluidSlot;
import appeng.fluids.client.gui.widgets.GuiOptionalFluidSlot;
import appeng.fluids.container.ContainerFluidFormationPlane;
import appeng.fluids.parts.PartFluidFormationPlane;
import appeng.fluids.util.IAEFluidTank;

/**
 * MUI 版流体成型面板 GUI 面板。
 *
 * 63 个流体过滤槽（7 行 × 9 列，前 2 行始终可见，后 5 行根据 CAPACITY 升级解锁），
 * 以及优先级按钮。
 */
public class MUIFluidFormationPlanePanel extends MUIUpgradeablePanel {

    private final ContainerFluidFormationPlane container;
    private final PartFluidFormationPlane plane;

    // ========== 按钮 ==========
    private GuiTabButton priority;

    public MUIFluidFormationPlanePanel(final ContainerFluidFormationPlane container) {
        super(container);
        this.container = container;
        this.plane = (PartFluidFormationPlane) container.getTarget();
        this.ySize = 251;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        final int xo = 8;
        final int yo = 23 + 6;

        final IAEFluidTank config = this.plane.getConfig();

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                final int idx = y * 9 + x;
                if (y < 2) {
                    this.guiSlots.add(new GuiFluidSlot(config, idx, idx, xo + x * 18, yo + y * 18));
                } else {
                    this.guiSlots.add(new GuiOptionalFluidSlot(config, this.container, idx, idx, y - 2, xo, yo, x, y));
                }
            }
        }
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.buttonList.add(this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16,
                GuiText.Priority.getLocal(), this.itemRender));
    }

    // ========== 渲染 ==========

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.FluidFormationPlane;
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

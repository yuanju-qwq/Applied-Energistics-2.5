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

import appeng.api.util.IConfigManager;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.client.gui.widgets.GuiFluidSlot;
import appeng.fluids.client.gui.widgets.GuiFluidTank;
import appeng.fluids.container.ContainerFluidInterface;
import appeng.fluids.helper.DualityFluidInterface;
import appeng.fluids.helper.IFluidInterfaceHost;
import appeng.fluids.util.IAEFluidTank;
import appeng.util.IConfigManagerHost;

/**
 * MUI 版 GuiFluidInterface。
 *
 * 对应旧代码的 {@link appeng.fluids.client.gui.GuiFluidInterface}。
 * 流体接口配置面板：流体配置槽 + 流体储罐显示 + 优先级按钮。
 * @deprecated 使用 {@link MUIMEInterfacePanel} 替代，新的 ME 接口统一处理物品和流体。
 */
@Deprecated
public class MUIFluidInterfacePanel extends MUIUpgradeablePanel implements IConfigManagerHost {

    private final IFluidInterfaceHost host;
    private final ContainerFluidInterface fluidContainer;
    private GuiTabButton priority;

    public MUIFluidInterfacePanel(final ContainerFluidInterface container, final IFluidInterfaceHost host) {
        super(container);
        this.ySize = 231;
        this.xSize = 245;
        this.host = host;
        this.fluidContainer = container;
        container.setGui(this);
    }

    @Override
    public void initGui() {
        super.initGui();

        final IAEFluidTank configFluids = this.host.getDualityFluidInterface().getConfig();
        final IAEFluidTank fluidTank = this.host.getDualityFluidInterface().getTanks();

        for (int i = 0; i < DualityFluidInterface.NUMBER_OF_TANKS; ++i) {
            this.guiSlots.add(
                    new GuiFluidTank(fluidTank, i, DualityFluidInterface.NUMBER_OF_TANKS + i, 8 + 18 * i, 53, 16, 68));
            this.guiSlots.add(new GuiFluidSlot(configFluids, i, i, 8 + 18 * i, 35));
        }

        this.priority = new GuiTabButton(this.getGuiLeft() + 154, this.getGuiTop(), 2 + 4 * 16,
                GuiText.Priority.getLocal(), this.itemRender);
        this.buttonList.add(this.priority);
    }

    // ========== 按钮 ==========

    @Override
    protected void addButtons() {
        // 流体接口无通用按钮
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.FluidInterface.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 6 + 11 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.StoredFluids.getLocal(), 8, 6 + 112 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/interfacefluidextendedlife.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected boolean drawUpgrades() {
        return false;
    }

    // ========== 鼠标事件 ==========

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        for (GuiCustomSlot slot : this.guiSlots) {
            if (slot instanceof GuiFluidTank) {
                if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), xCoord, yCoord)
                        && slot.canClick(this.mc.player)) {
                    this.fluidContainer.setTargetStack(((GuiFluidTank) slot).getFluidStack());
                    slot.slotClicked(this.mc.player.inventory.getItemStack(), btn);
                    return;
                }
            }
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        }
    }

    // ========== IConfigManagerHost ==========

    @Override
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
    }
}

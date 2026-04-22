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

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiImgLabel;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerInterface;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;

/**
 * MUI 版 GuiInterface。
 *
 * 对应旧代码的 {@link appeng.client.gui.implementations.GuiInterface}。
 * 物品接口配置面板：优先级、阻塞模式、解锁模式、接口终端可见性、锁定原因标签。
 * @deprecated 使用 {@link MUIPatternProviderPanel} 或 {@link MUIMEInterfacePanel} 替代。
 */
@Deprecated
public class MUIInterfacePanel extends MUIUpgradeablePanel {

    private GuiTabButton priority;
    private GuiImgButton unlockMode;
    private GuiImgButton blockMode;
    private GuiToggleButton interfaceMode;
    private GuiImgLabel lockReason;

    public MUIInterfacePanel(final ContainerInterface container) {
        super(container);
        this.ySize = 256;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addLabel();
    }

    // ========== 按钮 ==========

    @Override
    protected void addButtons() {
        this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16, GuiText.Priority.getLocal(),
                this.itemRender);
        this.buttonList.add(this.priority);

        this.blockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.BLOCK, YesNo.NO);
        this.buttonList.add(this.blockMode);

        this.unlockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 26, Settings.UNLOCK,
                LockCraftingMode.NONE);
        this.buttonList.add(this.unlockMode);

        this.interfaceMode = new GuiToggleButton(this.guiLeft - 18, this.guiTop + 44, 84, 85,
                GuiText.InterfaceTerminal.getLocal(), GuiText.InterfaceTerminalHint.getLocal());
        this.buttonList.add(this.interfaceMode);
    }

    protected void addLabel() {
        if (this.lockReason != null) {
            this.labelList.remove(this.lockReason);
        }
        this.lockReason = new GuiImgLabel(this.fontRenderer, this.guiLeft + 40, this.guiTop + 12, Settings.UNLOCK,
                LockCraftingMode.NONE);
        this.labelList.add(this.lockReason);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        final ContainerInterface ci = (ContainerInterface) this.cvb;

        if (this.blockMode != null) {
            this.blockMode.set(ci.getBlockingMode());
        }
        if (this.unlockMode != null) {
            this.unlockMode.set(ci.getUnlockMode());
        }
        if (this.interfaceMode != null) {
            this.interfaceMode.setState(ci.getInterfaceTerminalMode() == YesNo.YES);
        }
        if (this.lockReason != null) {
            this.lockReason.set(ci.getCraftingLockedReason());
        }

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Interface.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 6 + 11 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.StoredItems.getLocal(), 8, 6 + 60 + 7, 4210752);
        this.fontRenderer.drawString(GuiText.Patterns.getLocal(), 8, 6 + 73 + 7, 4210752);
    }

    @Override
    protected String getBackground() {
        int upgrades = ((ContainerInterface) this.cvb).getPatternUpgrades();
        if (upgrades == 0) {
            return "guis/newinterface.png";
        } else {
            return "guis/newinterface" + upgrades + ".png";
        }
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        }
        if (btn == this.interfaceMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
        }
        if (btn == this.blockMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.blockMode.getSetting(), backwards));
        }
        if (btn == this.unlockMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.unlockMode.getSetting(), backwards));
        }
    }
}

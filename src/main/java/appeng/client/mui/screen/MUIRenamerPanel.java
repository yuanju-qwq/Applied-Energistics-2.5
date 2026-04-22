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

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerRenamer;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;

/**
 * MUI 版重命名器 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiRenamer}。
 * 提供文本输入框和确认按钮，用于重命名方块/物品。
 * ESC/Enter 键关闭面板并发送重命名数据包。
 */
public class MUIRenamerPanel extends AEBasePanel {

    // ========== 控件 ==========
    private MEGuiTextField textField;
    private GuiButton confirmButton;

    public MUIRenamerPanel(final InventoryPlayer ip, final ICustomNameObject te) {
        this(new ContainerRenamer(ip, te));
    }

    public MUIRenamerPanel(final ContainerRenamer container) {
        super(container);
        this.xSize = 256;
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // initGui 处理初始化
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        super.initGui();

        this.textField = new MEGuiTextField(this.fontRenderer, this.guiLeft + 9, this.guiTop + 33, 229, 12);
        this.textField.setEnableBackgroundDrawing(false);
        this.textField.setMaxStringLength(32);
        this.textField.setFocused(true);

        this.buttonList.add(this.confirmButton = new GuiButton(0, this.guiLeft + 238, this.guiTop + 33, 12, 12, "↵"));

        ((ContainerRenamer) this.inventorySlots).setTextField(this.textField);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Renamer.getLocal()), 12, 8, 4210752);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/renamer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.textField.drawTextBox();
    }

    // ========== 输入事件 ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.textField.isMouseIn(xCoord, yCoord)) {
            if (btn == 1) {
                this.textField.setText("");
            }
            this.textField.mouseClicked(xCoord, yCoord, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            try {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("QuartzKnife.ReName", this.textField.getText()));
            } catch (IOException e) {
                AELog.debug(e);
            }
            this.mc.player.closeScreen();
        } else if (!this.textField.textboxKeyTyped(character, key)) {
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.confirmButton) {
            try {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("QuartzKnife.ReName", this.textField.getText()));
                this.mc.player.closeScreen();
            } catch (IOException e) {
                AELog.debug(e);
            }
        }
    }
}

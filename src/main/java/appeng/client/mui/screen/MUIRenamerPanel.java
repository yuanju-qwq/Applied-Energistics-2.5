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

import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.implementations.ContainerRenamer;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;

import appeng.util.Platform;

/**
 * MUI renamer GUI panel.
 * Provides text input field and confirm button for renaming blocks/items.
 * ESC/Enter keys close the panel and send rename packet.
 */
public class MUIRenamerPanel extends AEBasePanel {

    private static final int TEXT_FIELD_X = 9;
    private static final int TEXT_FIELD_Y = 33;
    private static final int TEXT_FIELD_WIDTH = 229;
    private static final int TEXT_FIELD_HEIGHT = 12;

    private MUITextFieldWidget textField;
    private MUIButtonWidget confirmButton;

    public MUIRenamerPanel(final InventoryPlayer ip, final ICustomNameObject te) {
        this(new ContainerRenamer(ip, te));
    }

    public MUIRenamerPanel(final ContainerRenamer container) {
        super(container);
        this.xSize = 256;
    }

    @Override
    protected void setupWidgets() {
        this.textField = this.addWidget(new MUITextFieldWidget(
                TEXT_FIELD_X,
                TEXT_FIELD_Y,
                TEXT_FIELD_WIDTH,
                TEXT_FIELD_HEIGHT)
                        .setEnableBackground(false)
                        .setMaxStringLength(32)
                        .setFocused(true)
                        .setClearOnRightClick(true));

        if (!Platform.isServer()) {
            ((ContainerRenamer) this.inventorySlots).setTextField(this.textField);
        }

        this.confirmButton = new MUIButtonWidget(
                TEXT_FIELD_X + TEXT_FIELD_WIDTH, TEXT_FIELD_Y, 12, 12);
        this.confirmButton.setTooltip("\u2192");
        this.confirmButton.setOnClick(btn -> this.sendRenameAndClose());
        this.addWidget(this.confirmButton);
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.Renamer.getLocal()), 12, 8, AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/renamer.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            this.sendRenameAndClose();
            return;
        }
        super.keyTyped(character, key);
    }

    private void sendRenameAndClose() {
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("QuartzKnife.ReName", this.textField == null ? "" : this.textField.getText()));
        } catch (IOException e) {
            AELog.debug(e);
        }
        this.mc.player.closeScreen();
    }
}

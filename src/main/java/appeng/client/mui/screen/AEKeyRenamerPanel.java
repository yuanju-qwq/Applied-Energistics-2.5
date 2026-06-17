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

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.TextWidget;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;

import appeng.client.mui.core.AEKeyModularPanel;
import appeng.container.implementations.ContainerRenamer;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.ICustomNameObject;

/**
 * AEKey-only MUI bottom-layer trial panel for the renamer.
 */
public final class AEKeyRenamerPanel extends AEKeyModularPanel {

    private static final int WIDTH = 256;
    private static final int HEIGHT = 166;
    private static final int TEXT_FIELD_X = 9;
    private static final int TEXT_FIELD_Y = 33;
    private static final int TEXT_FIELD_WIDTH = 229;
    private static final int TEXT_FIELD_HEIGHT = 12;

    private final ContainerRenamer container;
    private final TextFieldWidget textField = new TextFieldWidget();

    public AEKeyRenamerPanel(final InventoryPlayer ip, final ICustomNameObject host) {
        this(new ContainerRenamer(ip, host));
    }

    public AEKeyRenamerPanel(final ContainerRenamer container) {
        super("ae2_renamer_aekey");
        this.container = container;
        this.size(WIDTH, HEIGHT);
        this.container.setTextSink(this.textField::setText);
        this.buildWidgets();
    }

    private void buildWidgets() {
        this.child(new TextWidget<>(GuiText.Renamer.getLocal())
                .pos(12, 8)
                .size(120, 12)
                .color(0x404040));

        this.child(this.textField
                .pos(TEXT_FIELD_X, TEXT_FIELD_Y)
                .size(TEXT_FIELD_WIDTH, TEXT_FIELD_HEIGHT)
                .setMaxLength(32)
                .setFocusOnGuiOpen(true));

        this.child(new RenameButton()
                .background(GuiTextures.MC_BUTTON)
                .hoverBackground(GuiTextures.MC_BUTTON_HOVERED)
                .child(new TextWidget<>(">")
                        .size(12, 12)
                        .center())
                .pos(TEXT_FIELD_X + TEXT_FIELD_WIDTH, TEXT_FIELD_Y)
                .size(12, 12));
    }

    private void sendRenameAndClose() {
        try {
            NetworkHandler.instance()
                    .sendToServer(new PacketValueConfig("QuartzKnife.ReName", this.textField.getText()));
        } catch (final IOException e) {
            AELog.debug(e);
        }
        Minecraft.getMinecraft().player.closeScreen();
    }

    private final class RenameButton extends ButtonWidget<RenameButton> {

        @Override
        public Interactable.Result onMouseTapped(final int mouseButton) {
            sendRenameAndClose();
            return Interactable.Result.SUCCESS;
        }

        @Override
        public Interactable.Result onKeyPressed(final char character, final int key) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                sendRenameAndClose();
                return Interactable.Result.SUCCESS;
            }
            return super.onKeyPressed(character, key);
        }
    }
}

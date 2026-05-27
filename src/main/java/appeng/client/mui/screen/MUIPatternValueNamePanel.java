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
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternValueName;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternNameSet;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;

/**
 * MUI pattern value name panel.
 * <p>
 * Opened via Ctrl+middle-click on a pattern Virtual slot, allows customizing the item name.
 */
@SideOnly(Side.CLIENT)
public class MUIPatternValueNamePanel extends AEBasePanel {

    private static final int FIELD_X = 62;
    private static final int FIELD_Y = 57;
    private static final int FIELD_WIDTH = 59;
    private static final int SUBMIT_X = 128;
    private static final int SUBMIT_Y = 51;
    private static final int SUBMIT_W = 38;
    private static final int SUBMIT_H = 20;
    private static final int CLEAR_X = 20;
    private static final int CLEAR_Y = 26;
    private static final int CLEAR_W = 100;
    private static final int CLEAR_H = 20;

    private MUITextFieldWidget nameBox;
    private MUITabContainer originalGuiBtn;
    private MUIButtonWidget submit;
    private MUIButtonWidget clearName;

    private AEGuiKey originalGui;

    public MUIPatternValueNamePanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternValueName(inventoryPlayer, te));
    }

    @Override
    protected void setupWidgets() {
        this.submit = new MUIButtonWidget(SUBMIT_X, SUBMIT_Y, SUBMIT_W, SUBMIT_H);
        this.submit.setText(GuiText.SetAmount.getLocal());
        this.submit.setEnabled(false);
        this.submit.setOnClick(btn -> {
            if (btn.isEnabled()) {
                final ContainerPatternValueName cpn = (ContainerPatternValueName) this.inventorySlots;
                NetworkHandler.instance().sendToServer(
                        new PacketPatternNameSet(this.originalGui, this.nameBox.getText(), cpn.getValueIndex()));
            }
        });
        this.addWidget(this.submit);

        this.clearName = new MUIButtonWidget(CLEAR_X, CLEAR_Y, CLEAR_W, CLEAR_H);
        this.clearName.setText(GuiText.Cancel.getLocal());
        this.clearName.setOnClick(btn -> {
            this.nameBox.setText("");
        });
        this.addWidget(this.clearName);

        ItemStack myIcon = ItemStack.EMPTY;
        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            Object guiHandler = AEApi.instance().registries().wireless()
                            .getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
            if (guiHandler instanceof appeng.core.sync.AEGuiKey key) {
                this.originalGui = key;
            } else if (guiHandler instanceof appeng.core.sync.GuiBridge gb) {
                this.originalGui = AEGuiKeys.fromLegacy(gb);
            }
        }

        if (target instanceof PartPatternTerminal) {
            myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        if (this.originalGui != null && !myIcon.isEmpty()) {
            this.originalGuiBtn = new MUITabContainer(154, 0);
            this.originalGuiBtn.setIconItem(myIcon);
            this.originalGuiBtn.setTooltip(myIcon.getDisplayName());
            final AEGuiKey origGui = this.originalGui;
            this.originalGuiBtn.setOnClick(tab -> {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(origGui));
            });
            this.addWidget(this.originalGuiBtn);
        }

        this.nameBox = this.addWidget(new MUITextFieldWidget(FIELD_X, FIELD_Y, FIELD_WIDTH,
                this.fontRenderer.FONT_HEIGHT));
        this.nameBox.setEnableBackground(false);
        this.nameBox.setMaxStringLength(32);
        this.nameBox.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
        this.nameBox.setVisible(true);
        this.nameBox.setFocused(true);

        final ContainerPatternValueName cpn = (ContainerPatternValueName) this.inventorySlots;
        if (cpn.getPatternValue().getHasStack()) {
            final ItemStack stack = cpn.getPatternValue().getStack();
            if (stack.hasDisplayName()) {
                this.nameBox.setText(stack.getDisplayName());
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.PatternRename.getLocal(), 8, 6, AEMUITheme.COLOR_TITLE);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                final ContainerPatternValueName cpn = (ContainerPatternValueName) this.inventorySlots;
                NetworkHandler.instance().sendToServer(
                        new PacketPatternNameSet(this.originalGui, this.nameBox.getText(), cpn.getValueIndex()));
                return;
            }
            super.keyTyped(character, key);
        }
    }
}

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
import appeng.client.gui.MathExpressionParser;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;

/**
 * MUI pattern value amount panel.
 * <p>
 * Supports math expressions (e.g. "64*9").
 */
@SideOnly(Side.CLIENT)
public class MUIPatternValueAmountPanel extends AEBasePanel {

    private static final int FIELD_X = 62;
    private static final int FIELD_Y = 57;
    private static final int FIELD_WIDTH = 59;
    private static final int ROW1_Y = 26;
    private static final int ROW2_Y = 75;
    private static final int COL1_X = 20;
    private static final int COL2_X = 48;
    private static final int COL3_X = 82;
    private static final int COL4_X = 120;
    private static final int SUBMIT_X = 128;
    private static final int SUBMIT_Y = 51;
    private static final int SUBMIT_W = 38;
    private static final int SUBMIT_H = 20;

    private MUITextFieldWidget amountBox;
    private MUITabContainer originalGuiBtn;
    private MUIButtonWidget submit;

    private AEGuiKey originalGui;

    public MUIPatternValueAmountPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternValueAmount(inventoryPlayer, te));
    }

    @Override
    protected void setupWidgets() {
        final int a = AEConfig.instance().craftItemsByStackAmounts(0);
        final int b = AEConfig.instance().craftItemsByStackAmounts(1);
        final int c = AEConfig.instance().craftItemsByStackAmounts(2);
        final int d = AEConfig.instance().craftItemsByStackAmounts(3);

        this.addIncrementButton(COL1_X, ROW1_Y, 22, 20, "+" + a, a);
        this.addIncrementButton(COL2_X, ROW1_Y, 28, 20, "+" + b, b);
        this.addIncrementButton(COL3_X, ROW1_Y, 32, 20, "+" + c, c);
        this.addIncrementButton(COL4_X, ROW1_Y, 38, 20, "+" + d, d);

        this.addIncrementButton(COL1_X, ROW2_Y, 22, 20, "-" + a, -a);
        this.addIncrementButton(COL2_X, ROW2_Y, 28, 20, "-" + b, -b);
        this.addIncrementButton(COL3_X, ROW2_Y, 32, 20, "-" + c, -c);
        this.addIncrementButton(COL4_X, ROW2_Y, 38, 20, "-" + d, -d);

        this.submit = new MUIButtonWidget(SUBMIT_X, SUBMIT_Y, SUBMIT_W, SUBMIT_H);
        this.submit.setText(GuiText.SetAmount.getLocal());
        this.submit.setOnClick(btn -> {
            this.submitPatternValue();
        });
        this.addWidget(this.submit);

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

        this.amountBox = this.addWidget(new MUITextFieldWidget(FIELD_X, FIELD_Y, FIELD_WIDTH,
                this.fontRenderer.FONT_HEIGHT));
        this.amountBox.setEnableBackground(false);
        this.amountBox.setMaxStringLength(16);
        this.amountBox.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
        this.amountBox.setVisible(true);
        this.amountBox.setFocused(true);

        final ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) this.inventorySlots;
        if (cpv.getPatternValue().getHasStack()) {
            this.amountBox.setText(String.valueOf(cpv.getPatternValue().getStack().getCount()));
        } else {
            this.amountBox.setText("1");
        }
    }

    private void addIncrementButton(int x, int y, int w, int h, String label, int delta) {
        final MUIButtonWidget btn = new MUIButtonWidget(x, y, w, h);
        btn.setText(label);
        btn.setOnClick(b -> this.addQty(delta));
        this.addWidget(btn);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.SelectAmount.getLocal(), 8, 6, AEMUITheme.COLOR_TITLE);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        try {
            String out = this.amountBox.getText();
            double resultD = MathExpressionParser.parse(out);
            int amt;
            if (resultD <= 0 || Double.isNaN(resultD)) {
                amt = 0;
            } else {
                amt = (int) MathExpressionParser.round(resultD, 0);
            }
            this.submit.setEnabled(amt > 0);
        } catch (final NumberFormatException e) {
            this.submit.setEnabled(false);
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.submitPatternValue();
                return;
            }
            super.keyTyped(character, key);
        }
    }

    private void submitPatternValue() {
        try {
            double resultD = MathExpressionParser.parse(this.amountBox.getText());
            int result;
            if (resultD <= 0 || Double.isNaN(resultD)) {
                result = 1;
            } else {
                result = (int) MathExpressionParser.round(resultD, 0);
            }
            final ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) this.inventorySlots;
            NetworkHandler.instance().sendToServer(
                    new PacketPatternValueSet(this.originalGui, result, cpv.getValueIndex()));
        } catch (final NumberFormatException e) {
            this.amountBox.setText("1");
        }
    }

    private void addQty(final int i) {
        try {
            String out = this.amountBox.getText();
            double resultD = MathExpressionParser.parse(out);
            int result;
            if (resultD <= 0 || Double.isNaN(resultD)) {
                result = 0;
            } else {
                result = (int) MathExpressionParser.round(resultD, 0);
            }
            if (result == 1 && i > 1) {
                result = 0;
            }
            result += i;
            if (result < 1) {
                result = 1;
            }
            out = Integer.toString(result);
            this.amountBox.setText(out);
        } catch (final NumberFormatException e) {
        }
    }
}

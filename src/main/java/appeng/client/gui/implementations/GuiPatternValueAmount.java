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

package appeng.client.gui.implementations;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.MathExpressionParser;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternValueSet;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.Reflected;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;

/**
 * 样板数值设置 GUI：中键点击样板槽位后打开，允许用户精确输入物品数量。
 * 参照 {@link GuiCraftAmount} 的结构。
 */
public class GuiPatternValueAmount extends AEBaseGui {

    private GuiTextField amountBox;
    private GuiTabButton originalGuiBtn;
    private GuiButton submit;
    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;
    private GuiBridge originalGui;

    @Reflected
    public GuiPatternValueAmount(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternValueAmount(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        super.initGui();

        final int a = AEConfig.instance().craftItemsByStackAmounts(0);
        final int b = AEConfig.instance().craftItemsByStackAmounts(1);
        final int c = AEConfig.instance().craftItemsByStackAmounts(2);
        final int d = AEConfig.instance().craftItemsByStackAmounts(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 26, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 26, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 26, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 26, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 75, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 75, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 75, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 75, 38, 20, "-" + d));

        this.buttonList.add(
                this.submit = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20,
                        GuiText.SetAmount.getLocal()));

        // 检测原始 GUI 类型，以便返回时正确切换
        ItemStack myIcon = ItemStack.EMPTY;
        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = (GuiBridge) AEApi.instance().registries().wireless()
                    .getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (target instanceof PartPatternTerminal) {
            myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        if (this.originalGui != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon,
                    myIcon.getDisplayName(), this.itemRender));
        }

        this.amountBox = new GuiTextField(0, this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59,
                this.fontRenderer.FONT_HEIGHT);
        this.amountBox.setEnableBackgroundDrawing(false);
        this.amountBox.setMaxStringLength(16);
        this.amountBox.setTextColor(0xFFFFFF);
        this.amountBox.setVisible(true);
        this.amountBox.setFocused(true);

        // 从 Container 的展示槽获取当前数量作为默认值
        final ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) this.inventorySlots;
        if (cpv.getPatternValue().getHasStack()) {
            this.amountBox.setText(String.valueOf(cpv.getPatternValue().getStack().getCount()));
            this.amountBox.setSelectionPos(0);
        } else {
            this.amountBox.setText("1");
            this.amountBox.setSelectionPos(0);
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.SelectAmount.getLocal(), 8, 6, 4210752);
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
            this.submit.enabled = amt > 0;
        } catch (final NumberFormatException e) {
            this.submit.enabled = false;
        }

        this.amountBox.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.submit);
            }
            if (!this.amountBox.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        try {
            if (btn == this.originalGuiBtn) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
            }

            if (btn == this.submit && btn.enabled) {
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
            }
        } catch (final NumberFormatException e) {
            this.amountBox.setText("1");
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty(this.getQty(btn));
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
            // ignore
        }
    }
}

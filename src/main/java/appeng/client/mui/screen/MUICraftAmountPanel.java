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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
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
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerCraftAmount;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftRequest;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;

/**
 * MUI 版合成数量输入面板�?
 * <p>
 * 功能：输入合成数�?�?点击 Next/Start 发起合成请求�?
 * <p>
 * 特性：
 * <ul>
 *   <li>数量输入框支持数学表达式（加减乘除）</li>
 *   <li>4 组增减按钮（可在 AEConfig 中配置增量）</li>
 *   <li>回车键快捷提�?/li>
 *   <li>Shift+Next 直接开始合成（跳过确认�?/li>
 *   <li>左上角返回按钮（返回来源终端�?/li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUICraftAmountPanel extends AEBasePanel {

    // ========== UI 控件 ==========

    private GuiTextField amountToCraft;
    private GuiTabButton originalGuiBtn;

    private GuiButton next;

    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    /** 来源终端�?AEGuiKey（用于返回按钮） */
    private AEGuiKey originalGui;

    public MUICraftAmountPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftAmount(inventoryPlayer, te));
    }

    // ========== 初始�?==========

    @Override
    protected void setupWidgets() {
        // 从配置读取增量�?
        final int a = AEConfig.instance().craftItemsByStackAmounts(0);
        final int b = AEConfig.instance().craftItemsByStackAmounts(1);
        final int c = AEConfig.instance().craftItemsByStackAmounts(2);
        final int d = AEConfig.instance().craftItemsByStackAmounts(3);

        // 增加按钮�?
        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 26, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 26, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 26, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 26, 38, 20, "+" + d));

        // 减少按钮�?
        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 75, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 75, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 75, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 75, 38, 20, "-" + d));

        // Next/Start 按钮
        this.buttonList.add(
                this.next = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.Next.getLocal()));

        // 返回按钮（根据来源终端决定图标和目标�?
        ItemStack myIcon = null;
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

        if (target instanceof PartTerminal) {
            myIcon = parts.terminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.ME_TERMINAL;
        }

        if (target instanceof PartCraftingTerminal) {
            myIcon = parts.craftingTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        if (this.originalGui != null && myIcon != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon,
                    myIcon.getDisplayName(), this.itemRender));
        }

        // 数量输入�?
        this.amountToCraft = new GuiTextField(0, this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59,
                this.fontRenderer.FONT_HEIGHT);
        this.amountToCraft.setEnableBackgroundDrawing(false);
        this.amountToCraft.setMaxStringLength(16);
        this.amountToCraft.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
        this.amountToCraft.setVisible(true);
        this.amountToCraft.setFocused(true);
        this.amountToCraft.setText("1");
        this.amountToCraft.setSelectionPos(0);
    }

    // ========== 绘制 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(GuiText.SelectAmount.getLocal(), 8, 6, AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // Shift 切换按钮文字
        this.next.displayString = isShiftKeyDown() ? GuiText.Start.getLocal() : GuiText.Next.getLocal();

        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        // 验证输入并启�?禁用 Next 按钮
        try {
            String out = this.amountToCraft.getText();
            double resultD = MathExpressionParser.parse(out);
            long amt;

            if (resultD <= 0 || Double.isNaN(resultD)) {
                amt = 0;
            } else {
                amt = (long) MathExpressionParser.round(resultD, 0);
            }

            this.next.enabled = amt > 0;
        } catch (final NumberFormatException e) {
            this.next.enabled = false;
        }

        this.amountToCraft.drawTextBox();
    }

    // ========== 输入事件 ==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            // 回车键提�?
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.next);
            }
            // 输入框处�?
            if (!this.amountToCraft.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        try {
            // 返回按钮
            if (btn == this.originalGuiBtn) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
            }

            // Next/Start 按钮
            if (btn == this.next) {
                double resultD = MathExpressionParser.parse(this.amountToCraft.getText());
                int result;
                if (resultD <= 0 || Double.isNaN(resultD)) {
                    result = 1;
                } else {
                    result = (int) MathExpressionParser.round(resultD, 0);
                }

                NetworkHandler.instance().sendToServer(new PacketCraftRequest(result, isShiftKeyDown()));
            }
        } catch (final NumberFormatException e) {
            // 解析失败，重置为 1
            this.amountToCraft.setText("1");
        }

        // 增减按钮
        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty(this.getQty(btn));
        }
    }

    // ========== 内部方法 ==========

    /**
     * 向当前数量添加增量值�?
     */
    private void addQty(final int i) {
        try {
            String out = this.amountToCraft.getText();

            double resultD = MathExpressionParser.parse(out);
            int result;

            if (resultD <= 0 || Double.isNaN(resultD)) {
                result = 0;
            } else {
                result = (int) MathExpressionParser.round(resultD, 0);
            }

            // 如果当前�?1 且增量大�?1，从 0 开始加
            if (result == 1 && i > 1) {
                result = 0;
            }

            result += i;
            if (result < 1) {
                result = 1;
            }

            out = Integer.toString(result);
            this.amountToCraft.setText(out);
        } catch (final NumberFormatException e) {
            // :P
        }
    }
}

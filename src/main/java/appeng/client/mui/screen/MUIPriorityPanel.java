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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerPriority;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IPriorityHost;

/**
 * MUI 版优先级设置 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiPriority}。
 * 包含数字输入框、8 个加减按钮（±1/±10/±100/±1000）和返回原始 GUI 的标签按钮。
 */
public class MUIPriorityPanel extends AEBasePanel {

    // ========== 控件 ==========
    private GuiNumberBox priority;
    private GuiTabButton originalGuiBtn;

    // ========== 加减按钮 ==========
    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    private AEGuiKey originalGui;

    public MUIPriorityPanel(final InventoryPlayer ip, final IPriorityHost te) {
        this(new ContainerPriority(ip, te));
    }

    public MUIPriorityPanel(final ContainerPriority container) {
        super(container);
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // initGui 处理初始化
    }

    @Override
    public void initGui() {
        super.initGui();

        final int a = AEConfig.instance().priorityByStacksAmounts(0);
        final int b = AEConfig.instance().priorityByStacksAmounts(1);
        final int c = AEConfig.instance().priorityByStacksAmounts(2);
        final int d = AEConfig.instance().priorityByStacksAmounts(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 32, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 32, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 32, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 32, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 69, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 69, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 69, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 69, 38, 20, "-" + d));

        final ContainerPriority con = (ContainerPriority) this.inventorySlots;
        final ItemStack myIcon = con.getPriorityHost().getItemStackRepresentation();
        this.originalGui = con.getPriorityHost().getGuiKey();

        if (this.originalGui != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon,
                    myIcon.getDisplayName(), this.itemRender));
        }

        this.priority = new GuiNumberBox(this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59,
                this.fontRenderer.FONT_HEIGHT, Long.class);
        this.priority.setEnableBackgroundDrawing(false);
        this.priority.setMaxStringLength(16);
        this.priority.setTextColor(0xFFFFFF);
        this.priority.setVisible(true);
        this.priority.setFocused(true);
        ((ContainerPriority) this.inventorySlots).setTextField(this.priority);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.Priority.getLocal(), 8, 6, 4210752);
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/priority.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.priority.drawTextBox();
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty(this.getQty(btn));
        }
    }

    // ========== 数值增减 ==========

    private void addQty(final int i) {
        try {
            String out = this.priority.getText();

            boolean fixed = false;
            while (out.startsWith("0") && out.length() > 1) {
                out = out.substring(1);
                fixed = true;
            }

            if (fixed) {
                this.priority.setText(out);
            }

            if (out.isEmpty()) {
                out = "0";
            }

            long result = Long.parseLong(out);
            result += i;

            this.priority.setText(out = Long.toString(result));

            NetworkHandler.instance().sendToServer(new PacketValueConfig("PriorityHost.Priority", out));
        } catch (final NumberFormatException e) {
            this.priority.setText("0");
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // ========== 键盘输入 ==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == 211
                    || key == 205 || key == 203 || key == 14 || character == '-' || Character.isDigit(character))
                    && this.priority.textboxKeyTyped(character, key)) {
                try {
                    String out = this.priority.getText();

                    boolean fixed = false;
                    while (out.startsWith("0") && out.length() > 1) {
                        out = out.substring(1);
                        fixed = true;
                    }

                    if (fixed) {
                        this.priority.setText(out);
                    }

                    if (out.isEmpty()) {
                        out = "0";
                    }

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PriorityHost.Priority", out));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }
}

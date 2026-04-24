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

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.container.ContainerFluidLevelEmitter;
import appeng.fluids.util.AEFluidStackType;
import appeng.tile.inventory.IAEStackInventory;

/**
 * MUI 版流体级别发射器 GUI 面板。
 *
 * 包含数字输入框（阈值设置，单位为 millibucket）、±增减按钮、
 * 红石发射模式按钮，以及 1 个 VirtualMEPhantomSlot 配置槽（fluid-only）。
 */
public class MUIFluidLevelEmitterPanel extends MUIUpgradeablePanel {

    private final ContainerFluidLevelEmitter container;

    // ========== 数字输入框 ==========
    private GuiNumberBox level;

    // ========== ±增减按钮 ==========
    private GuiButton plus1;
    private GuiButton plus10;
    private GuiButton plus100;
    private GuiButton plus1000;
    private GuiButton minus1;
    private GuiButton minus10;
    private GuiButton minus100;
    private GuiButton minus1000;

    public MUIFluidLevelEmitterPanel(final ContainerFluidLevelEmitter container) {
        super(container);
        this.container = container;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        this.level = new GuiNumberBox(this.fontRenderer, this.guiLeft + 24, this.guiTop + 43, 79,
                this.fontRenderer.FONT_HEIGHT, Long.class);
        this.level.setEnableBackgroundDrawing(false);
        this.level.setMaxStringLength(16);
        this.level.setTextColor(0xFFFFFF);
        this.level.setVisible(true);
        this.level.setFocused(true);
        this.container.setTextField(this.level);

        // VirtualMEPhantomSlot for fluid config (fluid-only)
        final int y = 40;
        final int x = 80 + 44;
        final IAEStackInventory configInv = this.container.getConfig();
        this.guiSlots.add(new VirtualMEPhantomSlot(0, x, y, configInv, 0, this::acceptType));
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == AEFluidStackType.INSTANCE;
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.REDSTONE_EMITTER,
                RedstoneMode.LOW_SIGNAL);

        final int a = AEConfig.instance().levelByMillyBuckets(0);
        final int b = AEConfig.instance().levelByMillyBuckets(1);
        final int c = AEConfig.instance().levelByMillyBuckets(2);
        final int d = AEConfig.instance().levelByMillyBuckets(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 17, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 17, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 17, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 17, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 59, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 59, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 59, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 59, 38, 20, "-" + d));

        this.buttonList.add(this.redstoneMode);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.level.drawTextBox();
    }

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 数字输入框 tooltip：鼠标悬浮时提示单位为 millibuckets
        if (isPointInRegion(24, 43, 89, this.fontRenderer.FONT_HEIGHT, mouseX + this.guiLeft, mouseY + this.guiTop)) {
            drawTooltip(mouseX - 7, mouseY + 25, "Amount in millibuckets");
        }
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    protected boolean drawUpgrades() {
        return false;
    }

    @Override
    protected String getBackground() {
        return "guis/lvlemitter.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.FluidLevelEmitter;
    }

    @Override
    protected void handleButtonVisibility() {
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean isPlus = btn == this.plus1 || btn == this.plus10 || btn == this.plus100 || btn == this.plus1000;
        final boolean isMinus = btn == this.minus1 || btn == this.minus10 || btn == this.minus100
                || btn == this.minus1000;

        if (isPlus || isMinus) {
            this.addQty(this.getQty(btn));
        }
    }

    // ========== 数字输入处理 ==========

    private void addQty(final long i) {
        try {
            String Out = this.level.getText();

            boolean Fixed = false;
            while (Out.startsWith("0") && Out.length() > 1) {
                Out = Out.substring(1);
                Fixed = true;
            }

            if (Fixed) {
                this.level.setText(Out);
            }

            if (Out.isEmpty()) {
                Out = "0";
            }

            long result = Long.parseLong(Out);
            result += i;
            if (result < 0) {
                result = 0;
            }

            this.level.setText(Out = Long.toString(result));

            NetworkHandler.instance().sendToServer(new PacketValueConfig("FluidLevelEmitter.Value", Out));
        } catch (final NumberFormatException e) {
            this.level.setText("0");
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == 211 || key == 205 || key == 203 || key == 14 || Character.isDigit(character))
                    && this.level.textboxKeyTyped(character, key)) {
                try {
                    String Out = this.level.getText();

                    boolean Fixed = false;
                    while (Out.startsWith("0") && Out.length() > 1) {
                        Out = Out.substring(1);
                        Fixed = true;
                    }

                    if (Fixed) {
                        this.level.setText(Out);
                    }

                    if (Out.isEmpty()) {
                        Out = "0";
                    }

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("FluidLevelEmitter.Value", Out));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }
}

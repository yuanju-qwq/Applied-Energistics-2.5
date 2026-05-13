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

import appeng.api.config.*;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiNumberBox;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

/**
 * Contains a number input field (threshold setting), +/- increment buttons, level mode,
 *
 * When a crafting upgrade is installed, number-related controls and level mode button are disabled.
 * fuzzy mode, crafting mode buttons, and 1 VirtualMEPhantomSlot config slot.
 * When a crafting upgrade is installed, number-related controls and level mode button are disabled.
 */
public class MUILevelEmitterPanel extends MUIUpgradeablePanel {

    private final ContainerLevelEmitter container;

    // ========== Number input field ==========
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

    // ========== Config buttons ==========
    private MUIButtonWidget levelMode;
    private MUIButtonWidget craftingMode;

    // ========== 虚拟配置槽位 ==========
    private VirtualMEPhantomSlot configSlot;

    public MUILevelEmitterPanel(final ContainerLevelEmitter container) {
        super(container);
        this.container = container;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();

        this.level = new GuiNumberBox(this.fontRenderer, this.guiLeft + 24, this.guiTop + 43, 79,
                this.fontRenderer.FONT_HEIGHT, Long.class);
        this.level.setEnableBackgroundDrawing(false);
        this.level.setMaxStringLength(16);
        this.level.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
        this.level.setVisible(true);
        this.level.setFocused(true);
        this.container.setTextField(this.level);

        this.initVirtualSlots();
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.levelMode = new MUIButtonWidget(-18, 8, Settings.LEVEL_TYPE, LevelType.ITEM_LEVEL);
        this.levelMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.levelMode);

        this.redstoneMode = new MUIButtonWidget(-18, 28, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL);
        this.redstoneMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.redstoneMode);

        this.fuzzyMode = new MUIButtonWidget(-18, 48, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.fuzzyMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.fuzzyMode);

        this.craftingMode = new MUIButtonWidget(-18, 48, Settings.CRAFT_VIA_REDSTONE, YesNo.NO);
        this.craftingMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.craftingMode);

        final int a = AEConfig.instance().levelByStackAmounts(0);
        final int b = AEConfig.instance().levelByStackAmounts(1);
        final int c = AEConfig.instance().levelByStackAmounts(2);
        final int d = AEConfig.instance().levelByStackAmounts(3);

        this.buttonList.add(this.plus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 17, 22, 20, "+" + a));
        this.buttonList.add(this.plus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 17, 28, 20, "+" + b));
        this.buttonList.add(this.plus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 17, 32, 20, "+" + c));
        this.buttonList.add(this.plus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 17, 38, 20, "+" + d));

        this.buttonList.add(this.minus1 = new GuiButton(0, this.guiLeft + 20, this.guiTop + 59, 22, 20, "-" + a));
        this.buttonList.add(this.minus10 = new GuiButton(0, this.guiLeft + 48, this.guiTop + 59, 28, 20, "-" + b));
        this.buttonList.add(this.minus100 = new GuiButton(0, this.guiLeft + 82, this.guiTop + 59, 32, 20, "-" + c));
        this.buttonList.add(this.minus1000 = new GuiButton(0, this.guiLeft + 120, this.guiTop + 59, 38, 20, "-" + d));
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        final boolean notCraftingMode = this.bc.getInstalledUpgrades(Upgrades.CRAFTING) == 0;

        // Disable/enable number-related controls based on crafting upgrade installation status
        this.level.setEnabled(notCraftingMode);
        this.plus1.enabled = notCraftingMode;
        this.plus10.enabled = notCraftingMode;
        this.plus100.enabled = notCraftingMode;
        this.plus1000.enabled = notCraftingMode;
        this.minus1.enabled = notCraftingMode;
        this.minus10.enabled = notCraftingMode;
        this.minus100.enabled = notCraftingMode;
        this.minus1000.enabled = notCraftingMode;
        this.levelMode.setEnabled(notCraftingMode);
        this.redstoneMode.setEnabled(notCraftingMode);

        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (this.craftingMode != null) {
            this.craftingMode.set(this.cvb.getCraftingMode());
        }

        if (this.levelMode != null) {
            this.levelMode.set(this.container.getLevelMode());
        }
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        this.level.drawTextBox();
    }

    @Override
    protected void handleButtonVisibility() {
        this.craftingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.CRAFTING) > 0);
        this.fuzzyMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.FUZZY) > 0);
    }

    @Override
    protected String getBackground() {
        return "guis/lvlemitter.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.LevelEmitter;
    }

    // ========== Button events ==========

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

            NetworkHandler.instance().sendToServer(new PacketValueConfig("LevelEmitter.Value", Out));
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

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("LevelEmitter.Value", Out));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    // ========== Virtual slot管理 ==========

    private void initVirtualSlots() {
        this.guiSlots.clear();
        final IAEStackInventory inputInv = this.container.getConfig();
        this.configSlot = new VirtualMEPhantomSlot(
                0,
                155,
                9,
                inputInv,
                0,
                this::acceptType);
        this.guiSlots.add(this.configSlot);
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == AEItemStackType.INSTANCE;
    }
}

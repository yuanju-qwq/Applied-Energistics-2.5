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

import appeng.api.config.*;
import appeng.api.stacks.AEKeyType;
import appeng.client.mui.slot.VirtualMEPhantomSlot;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUINumberFieldWidget;
import appeng.container.implementations.ContainerLevelEmitter;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;

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
    private MUINumberFieldWidget level;

    // ========== +/- increment buttons ==========
    private MUIButtonWidget plus1;
    private MUIButtonWidget plus10;
    private MUIButtonWidget plus100;
    private MUIButtonWidget plus1000;
    private MUIButtonWidget minus1;
    private MUIButtonWidget minus10;
    private MUIButtonWidget minus100;
    private MUIButtonWidget minus1000;

    // ========== Config buttons ==========
    private MUIButtonWidget levelMode;
    private MUIButtonWidget craftingMode;

    // ========== Virtual config slot ==========
    private VirtualMEPhantomSlot configSlot;

    public MUILevelEmitterPanel(final ContainerLevelEmitter container) {
        super(container);
        this.container = container;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();

        this.level = new MUINumberFieldWidget(this.fontRenderer, 24, 43, 79,
                this.fontRenderer.FONT_HEIGHT, Long.class);
        this.level.applyValidator();
        this.level.setEnableBackgroundDrawing(false);
        this.level.setMaxStringLength(16);
        this.level.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
        this.level.setVisible(true);
        this.level.setFocused(true);
        this.addWidget(this.level.getDelegate());
        this.container.setTextField(this.level.getDelegate().getTextField());

        this.initVirtualSlots();
    }

    // ========== Button management ==========

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

        this.plus1 = makeQtyButton(20, 17, 22, 20, "+" + a, a);
        this.plus10 = makeQtyButton(48, 17, 28, 20, "+" + b, b);
        this.plus100 = makeQtyButton(82, 17, 32, 20, "+" + c, c);
        this.plus1000 = makeQtyButton(120, 17, 38, 20, "+" + d, d);

        this.minus1 = makeQtyButton(20, 59, 22, 20, "-" + a, -a);
        this.minus10 = makeQtyButton(48, 59, 28, 20, "-" + b, -b);
        this.minus100 = makeQtyButton(82, 59, 32, 20, "-" + c, -c);
        this.minus1000 = makeQtyButton(120, 59, 38, 20, "-" + d, -d);
    }

    /**
     * Helper to build a +/- quantity adjustment button. The button is
     * registered as a MUI widget with an onClick callback that calls
     * {@link #addQty(long)} directly, eliminating the need to override
     * {@code actionPerformed()}.
     */
    private MUIButtonWidget makeQtyButton(int x, int y, int width, int height, String text, long delta) {
        MUIButtonWidget btn = new MUIButtonWidget(x, y, width, height);
        btn.setText(text);
        btn.setOnClick(b -> this.addQty(delta));
        this.addWidget(btn);
        return btn;
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        final boolean notCraftingMode = this.bc.getInstalledUpgrades(Upgrades.CRAFTING) == 0;

        // Disable/enable number-related controls based on crafting upgrade installation status
        this.level.setEnabled(notCraftingMode);
        this.plus1.setEnabled(notCraftingMode);
        this.plus10.setEnabled(notCraftingMode);
        this.plus100.setEnabled(notCraftingMode);
        this.plus1000.setEnabled(notCraftingMode);
        this.minus1.setEnabled(notCraftingMode);
        this.minus10.setEnabled(notCraftingMode);
        this.minus100.setEnabled(notCraftingMode);
        this.minus1000.setEnabled(notCraftingMode);
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
        this.level.getDelegate().drawTextBox();
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
    //
    // The +/- quantity buttons now use MUIButtonWidget setOnClick callbacks
    // registered in addButtons(); no actionPerformed override is needed.

    // ========== Number input handling ==========

    private void addQty(final long i) {
        try {
            String out = this.level.getText();

            boolean fixed = false;
            while (out.startsWith("0") && out.length() > 1) {
                out = out.substring(1);
                fixed = true;
            }

            if (fixed) {
                this.level.setText(out);
            }

            if (out.isEmpty()) {
                out = "0";
            }

            long result = Long.parseLong(out);
            result += i;
            if (result < 0) {
                result = 0;
            }

            this.level.setText(out = Long.toString(result));

            NetworkHandler.instance().sendToServer(new PacketValueConfig("LevelEmitter.Value", out));
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
                    && this.level.getDelegate().textboxKeyTyped(character, key)) {
                try {
                    String out = this.level.getText();

                    boolean fixed = false;
                    while (out.startsWith("0") && out.length() > 1) {
                        out = out.substring(1);
                        fixed = true;
                    }

                    if (fixed) {
                        this.level.setText(out);
                    }

                    if (out.isEmpty()) {
                        out = "0";
                    }

                    NetworkHandler.instance().sendToServer(new PacketValueConfig("LevelEmitter.Value", out));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    // ========== Virtual slot management ==========

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

    private boolean acceptType(VirtualMEPhantomSlot slot, AEKeyType type, int mouseButton) {
        return type == AEKeyType.items();
    }
}

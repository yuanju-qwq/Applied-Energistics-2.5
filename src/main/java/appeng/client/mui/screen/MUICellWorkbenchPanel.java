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
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.ActionItems;
import appeng.api.config.CopyMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerCellWorkbench;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.misc.TileCellWorkbench;

/**
 * MUI version of the Cell Workbench GUI panel.
 *
 * Extends {@link MUIUpgradeablePanel}, includes Clear/Partition/Copy Mode/Fuzzy Mode buttons,
 * and supports multi-column upgrade area rendering for up to 24 upgrade slots.
 */
public class MUICellWorkbenchPanel extends MUIUpgradeablePanel {

    private final ContainerCellWorkbench workbench;

    // ========== Buttons ==========
    private GuiImgButton clear;
    private GuiImgButton partition;
    private GuiToggleButton copyMode;
    private GuiImgButton fuzzyBtn;

    // ========== Virtual slots ==========
    private VirtualMEPhantomSlot[] configSlots;

    public MUICellWorkbenchPanel(final ContainerCellWorkbench container) {
        super(container);
        this.workbench = container;
        this.ySize = 251;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    // ========== Button management ==========

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.partition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.WRENCH);
        this.copyMode = new GuiToggleButton(this.guiLeft - 18, this.guiTop + 48, 11 * 16 + 5, 12 * 16 + 5,
                GuiText.CopyMode.getLocal(), GuiText.CopyModeDesc.getLocal());
        this.fuzzyBtn = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68, Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);

        this.buttonList.add(this.fuzzyBtn);
        this.buttonList.add(this.partition);
        this.buttonList.add(this.clear);
        this.buttonList.add(this.copyMode);
    }

    // ========== Rendering ==========

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.handleButtonVisibility();

        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, this.ySize);
        if (this.drawUpgrades()) {
            // Draw multi-column upgrade area based on available upgrade slot count
            if (this.workbench.availableUpgrades() <= 8) {
                this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35,
                        7 + this.workbench.availableUpgrades() * 18);
                this.drawTexturedModalRect(offsetX + 177, offsetY + (7 + (this.workbench.availableUpgrades()) * 18),
                        177, 151, 35, 7);
            } else if (this.workbench.availableUpgrades() <= 16) {
                // First column (8 slots)
                this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 7 + 8 * 18);
                this.drawTexturedModalRect(offsetX + 177, offsetY + (7 + (8) * 18), 177, 151, 35, 7);
                // Second column (remaining)
                final int dx = this.workbench.availableUpgrades() - 8;
                this.drawTexturedModalRect(offsetX + 177 + 27, offsetY, 186, 0, 35 - 8, 7 + dx * 18);
                if (dx == 8) {
                    this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + (7 + (dx) * 18), 186, 151, 35 - 8, 7);
                } else {
                    this.drawTexturedModalRect(offsetX + 177 + 27 + 4, offsetY + (7 + (dx) * 18), 186 + 4, 151, 35 - 8,
                            7);
                }
            } else {
                // First column (8 slots)
                this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 7 + 8 * 18);
                this.drawTexturedModalRect(offsetX + 177, offsetY + (7 + (8) * 18), 177, 151, 35, 7);
                // Second column (8 slots)
                this.drawTexturedModalRect(offsetX + 177 + 27, offsetY, 186, 0, 35 - 8, 7 + 8 * 18);
                this.drawTexturedModalRect(offsetX + 177 + 27, offsetY + (7 + (8) * 18), 186, 151, 35 - 8, 7);
                // Third column (remaining)
                final int dx = this.workbench.availableUpgrades() - 16;
                this.drawTexturedModalRect(offsetX + 177 + 27 + 18, offsetY, 186, 0, 35 - 8, 7 + dx * 18);
                if (dx == 8) {
                    this.drawTexturedModalRect(offsetX + 177 + 27 + 18, offsetY + (7 + (dx) * 18), 186, 151, 35 - 8, 7);
                } else {
                    this.drawTexturedModalRect(offsetX + 177 + 27 + 18 + 4, offsetY + (7 + (dx) * 18), 186 + 4, 151,
                            35 - 8, 7);
                }
            }
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, 161, 68, 68);
        }
    }

    @Override
    protected void handleButtonVisibility() {
        this.copyMode.setState(this.workbench.getCopyMode() == CopyMode.CLEAR_ON_REMOVE);

        boolean hasFuzzy = false;
        final IItemHandler inv = this.workbench.getCellUpgradeInventory();
        for (int x = 0; x < inv.getSlots(); x++) {
            final ItemStack is = inv.getStackInSlot(x);
            if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                if (((IUpgradeModule) is.getItem()).getType(is) == Upgrades.FUZZY) {
                    hasFuzzy = true;
                }
            }
        }
        this.fuzzyBtn.setVisibility(hasFuzzy);
    }

    @Override
    protected String getBackground() {
        return "guis/cellworkbench.png";
    }

    @Override
    protected boolean drawUpgrades() {
        return this.workbench.availableUpgrades() > 0;
    }

    @Override
    protected GuiText getName() {
        return GuiText.CellWorkbench;
    }

    // ========== Button events ==========

    @Override
    protected void actionPerformed(final GuiButton btn) {
        try {
            if (btn == this.copyMode) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("CellWorkbench.Action", "CopyMode"));
            } else if (btn == this.partition) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("CellWorkbench.Action", "Partition"));
            } else if (btn == this.clear) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("CellWorkbench.Action", "Clear"));
            } else if (btn == this.fuzzyBtn) {
                final boolean backwards = Mouse.isButtonDown(1);

                FuzzyMode fz = (FuzzyMode) this.fuzzyBtn.getCurrentValue();
                fz = appeng.util.EnumCycler.rotateEnum(fz, backwards, Settings.FUZZY_MODE.getPossibleValues());

                NetworkHandler.instance().sendToServer(new PacketValueConfig("CellWorkbench.Fuzzy", fz.name()));
            } else {
                super.actionPerformed(btn);
            }
        } catch (final IOException ignored) {
        }
    }

    // ========== Virtual slot management ==========

    private void initVirtualSlots() {
        this.guiSlots.removeIf(s -> s instanceof VirtualMEPhantomSlot);
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory inputInv = this.workbench.getConfig();
        final int xo = 8;
        final int yo = 29;

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                final int slotIdx = x + y * 9;
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        slotIdx,
                        xo + x * 18,
                        yo + y * 18,
                        inputInv,
                        slotIdx,
                        this::acceptType);
                this.configSlots[slotIdx] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    /**
     * Determines whether to accept a given stack type based on the current cell item's stack type.
     */
    private boolean acceptType(VirtualMEPhantomSlot slot, AEKeyType type, int mouseButton) {
        final ICellWorkbenchItem cell = this.workbench.getCell();
        if (cell != null) {
            return type == AEKeyType.fromLegacyType(cell.getStackType());
        }
        return false;
    }
}

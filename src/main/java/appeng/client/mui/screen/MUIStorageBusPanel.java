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
import appeng.client.mui.AEMUITheme;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.container.implementations.ContainerStorageBus;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Unified MUI storage bus GUI panel for all storage types (item, fluid, etc.).
 *
 * 63 VirtualMEPhantomSlot filter slots (7 rows x 9 columns, last 5 rows
 * unlocked by CAPACITY upgrades), plus read-write mode, storage filter,
 * priority, partition, and clear buttons.
 *
 * The accepted stack type and title text are passed at construction time,
 * so no per-type subclass is needed.
 */
public class MUIStorageBusPanel extends MUIUpgradeablePanel {

    private final ContainerStorageBus container;
    private final IAEStackType<?> acceptedType;
    private final GuiText title;

    // ========== Buttons ==========
    private MUIButtonWidget rwMode;
    private MUIButtonWidget storageFilter;
    private MUITabContainer priority;
    private MUIButtonWidget partition;
    private MUIButtonWidget clear;

    // ========== Virtual Slots ==========
    private VirtualMEPhantomSlot[] configSlots;

    public MUIStorageBusPanel(final ContainerStorageBus container, final IAEStackType<?> acceptedType,
            final GuiText title) {
        super(container);
        this.container = container;
        this.acceptedType = acceptedType;
        this.title = title;
        this.ySize = 251;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    // ========== Button Management ==========

    @Override
    protected void addButtons() {
        this.clear = new MUIButtonWidget(-18, 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.clear.setOnClick(btn -> {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("StorageBus.Action", "Clear"));
            } catch (IOException e) {
                AELog.debug(e);
            }
        });
        this.addWidget(this.clear);

        this.partition = new MUIButtonWidget(-18, 28, Settings.ACTIONS, ActionItems.WRENCH);
        this.partition.setOnClick(btn -> {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("StorageBus.Action", "Partition"));
            } catch (IOException e) {
                AELog.debug(e);
            }
        });
        this.addWidget(this.partition);

        this.rwMode = new MUIButtonWidget(-18, 48, Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.rwMode.setOnClick(btn -> {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.ACCESS, backwards));
        });
        this.addWidget(this.rwMode);

        this.storageFilter = new MUIButtonWidget(-18, 68, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.storageFilter.setOnClick(btn -> {
            final boolean backwards = Mouse.isButtonDown(1);
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.STORAGE_FILTER, backwards));
        });
        this.addWidget(this.storageFilter);

        this.fuzzyMode = new MUIButtonWidget(-18, 88, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.fuzzyMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.fuzzyMode);

        this.priority = new MUITabContainer(154, 0, 2 + 4 * 16, GuiText.Priority.getLocal());
        this.priority.setOnClick(tab -> {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        });
        this.addWidget(this.priority);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.title.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        if (this.configSlots != null) {
            this.updateVirtualSlotVisibility();
        }

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.storageFilter != null) {
            this.storageFilter.set(this.container.getStorageFilter());
        }

        if (this.rwMode != null) {
            this.rwMode.set(this.container.getReadWriteMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    // ========== Virtual Slot Management ==========

    private void initVirtualSlots() {
        this.guiSlots.clear();
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory inputInv = this.container.getConfig();
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

        this.updateVirtualSlotVisibility();
    }

    private void updateVirtualSlotVisibility() {
        final int capacity = this.bc.getInstalledUpgrades(Upgrades.CAPACITY);

        for (VirtualMEPhantomSlot slot : this.configSlots) {
            slot.setHidden(slot.getSlotIndex() >= (18 + (9 * capacity)));
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == this.acceptedType;
    }
}

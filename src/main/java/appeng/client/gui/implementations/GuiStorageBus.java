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

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.*;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerStorageBus;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

public class GuiStorageBus extends GuiUpgradeable {

    private GuiImgButton rwMode;
    private GuiImgButton storageFilter;
    private GuiTabButton priority;
    private GuiImgButton partition;
    private GuiImgButton clear;
    private VirtualMEPhantomSlot[] configSlots;
    private final ContainerStorageBus containerStorageBus;

    public GuiStorageBus(final InventoryPlayer inventoryPlayer, final PartStorageBus te) {
        super(new ContainerStorageBus(inventoryPlayer, te));
        this.containerStorageBus = (ContainerStorageBus) this.inventorySlots;
        this.ySize = 251;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    @Override
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.partition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.WRENCH);
        this.rwMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.ACCESS,
                AccessRestriction.READ_WRITE);
        this.storageFilter = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68, Settings.STORAGE_FILTER,
                StorageFilter.EXTRACTABLE_ONLY);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 88, Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);

        this.buttonList.add(this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16,
                GuiText.Priority.getLocal(), this.itemRender));

        this.buttonList.add(this.storageFilter);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.rwMode);
        this.buttonList.add(this.partition);
        this.buttonList.add(this.clear);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.StorageBus.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        this.updateVirtualSlotVisibility();

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }

        if (this.storageFilter != null) {
            this.storageFilter.set(((ContainerStorageBus) this.cvb).getStorageFilter());
        }

        if (this.rwMode != null) {
            this.rwMode.set(((ContainerStorageBus) this.cvb).getReadWriteMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        try {
            if (btn == this.partition) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("StorageBus.Action", "Partition"));
            } else if (btn == this.clear) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("StorageBus.Action", "Clear"));
            } else if (btn == this.priority) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
            } else if (btn == this.rwMode) {
                NetworkHandler.instance().sendToServer(new PacketConfigButton(this.rwMode.getSetting(), backwards));
            } else if (btn == this.storageFilter) {
                NetworkHandler.instance()
                        .sendToServer(new PacketConfigButton(this.storageFilter.getSetting(), backwards));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // ---- 虚拟槽位初始化 ----

    private void initVirtualSlots() {
        this.guiSlots.clear();
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory inputInv = this.containerStorageBus.getConfig();
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
        final int capacity = this.bc.getInstalledUpgrades(
                appeng.api.config.Upgrades.CAPACITY);

        for (VirtualMEPhantomSlot slot : this.configSlots) {
            slot.setHidden(slot.getSlotIndex() >= (18 + (9 * capacity)));
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        // 当前只接受物品类型，未来可扩展为支持流体等
        return type == AEItemStackType.INSTANCE;
    }
}

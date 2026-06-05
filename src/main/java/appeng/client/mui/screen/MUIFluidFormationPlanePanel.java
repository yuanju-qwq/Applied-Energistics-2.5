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

import appeng.api.config.Upgrades;
import appeng.api.stacks.AEKeyType;
import appeng.client.mui.slot.VirtualMEPhantomSlot;
import appeng.client.mui.widgets.MUITabContainer;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.container.ContainerFluidFormationPlane;
import appeng.tile.inventory.IAEStackInventory;

/**
 * MUI fluid formation plane GUI panel.
 *
 * 63 VirtualMEPhantomSlot fluid filter slots (7 rows x 9 columns, first 2 rows always visible, last 5 rows unlocked by CAPACITY upgrade),
 * and a priority button.
 */
public class MUIFluidFormationPlanePanel extends MUIUpgradeablePanel {

    private final ContainerFluidFormationPlane container;

    // ========== Buttons ==========
    private MUITabContainer priority;

    // ========== Virtual slot ==========
    private VirtualMEPhantomSlot[] configSlots;

    public MUIFluidFormationPlanePanel(final ContainerFluidFormationPlane container) {
        super(container);
        this.container = container;
        this.ySize = 251;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    // ========== Virtual slot management ==========

    private void initVirtualSlots() {
        this.guiSlots.clear();
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory configInv = this.container.getConfig();
        final int xo = 8;
        final int yo = 23 + 6;

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                final int slotIdx = x + y * 9;
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        slotIdx,
                        xo + x * 18,
                        yo + y * 18,
                        configInv,
                        slotIdx,
                        this::acceptType);
                this.configSlots[slotIdx] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    private void updateVirtualSlotVisibility() {
        final int capacity = this.bc.getInstalledUpgrades(Upgrades.CAPACITY);

        for (VirtualMEPhantomSlot slot : this.configSlots) {
            slot.setHidden(slot.getSlotIndex() >= (18 + (9 * capacity)));
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, AEKeyType type, int mouseButton) {
        return type == AEKeyType.fluids();
    }

    // ========== Button management ==========

    @Override
    protected void addButtons() {
        this.priority = new MUITabContainer(154, 0, 2 + 4 * 16, GuiText.Priority.getLocal());
        this.priority.setOnClick(tab -> {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
        });
        this.addWidget(this.priority);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        if (this.configSlots != null) {
            this.updateVirtualSlotVisibility();
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected GuiText getName() {
        return GuiText.FluidFormationPlane;
    }
}

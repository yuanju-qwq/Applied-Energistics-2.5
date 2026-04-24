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
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.core.localization.GuiText;
import appeng.fluids.container.ContainerFluidIO;
import appeng.fluids.parts.PartFluidImportBus;
import appeng.fluids.util.AEFluidStackType;
import appeng.tile.inventory.IAEStackInventory;

/**
 * MUI 版 GuiFluidIO。
 *
 * 流体 IO 总线配置面板：中心 1 + 十字 4 + 对角 4 = 9 个 VirtualMEPhantomSlot 配置槽（fluid-only）。
 */
public class MUIFluidIOPanel extends MUIUpgradeablePanel {

    private VirtualMEPhantomSlot[] configSlots;

    public MUIFluidIOPanel(final ContainerFluidIO container) {
        super(container);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.initVirtualSlots();
    }

    // ========== 虚拟槽位管理 ==========

    private void initVirtualSlots() {
        this.guiSlots.clear();
        this.configSlots = new VirtualMEPhantomSlot[9];
        final ContainerFluidIO container = (ContainerFluidIO) this.inventorySlots;
        final IAEStackInventory inv = container.getConfig();
        final int y = 40;
        final int x = 80;

        // Center slot (always visible)
        this.configSlots[0] = addSlot(0, inv, x, y);
        // Cross 4 slots (capacity upgrade >= 1)
        this.configSlots[1] = addSlot(1, inv, x + (-1) * 18, y);
        this.configSlots[2] = addSlot(2, inv, x + (1) * 18, y);
        this.configSlots[3] = addSlot(3, inv, x, y + (-1) * 18);
        this.configSlots[4] = addSlot(4, inv, x, y + (1) * 18);
        // Diagonal 4 slots (capacity upgrade >= 2)
        this.configSlots[5] = addSlot(5, inv, x + (-1) * 18, y + (-1) * 18);
        this.configSlots[6] = addSlot(6, inv, x + (1) * 18, y + (-1) * 18);
        this.configSlots[7] = addSlot(7, inv, x + (-1) * 18, y + (1) * 18);
        this.configSlots[8] = addSlot(8, inv, x + (1) * 18, y + (1) * 18);
    }

    private VirtualMEPhantomSlot addSlot(int idx, IAEStackInventory inv, int x, int y) {
        VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(idx, x, y, inv, idx, this::acceptType);
        this.guiSlots.add(slot);
        return slot;
    }

    private void updateVirtualSlotVisibility() {
        final int upgrades = this.bc.getInstalledUpgrades(Upgrades.CAPACITY);
        // Slot 0: always visible
        this.configSlots[0].setHidden(false);
        // Slots 1-4: visible with capacity >= 1
        for (int i = 1; i <= 4; i++) {
            this.configSlots[i].setHidden(upgrades < 1);
        }
        // Slots 5-8: visible with capacity >= 2
        for (int i = 5; i <= 8; i++) {
            this.configSlots[i].setHidden(upgrades < 2);
        }
    }

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return type == AEFluidStackType.INSTANCE;
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        if (this.configSlots != null) {
            this.updateVirtualSlotVisibility();
        }
    }

    @Override
    protected GuiText getName() {
        return this.bc instanceof PartFluidImportBus ? GuiText.ImportBusFluids : GuiText.ExportBusFluids;
    }
}

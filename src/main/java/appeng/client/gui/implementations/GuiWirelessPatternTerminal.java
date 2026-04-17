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

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.IAEStackInventory;

/**
 * 无线样板终端 GUI。
 * 合成模式使用父类的 3x3+3 布局；
 * 处理模式覆盖为 4x4 输入 + 6 输出布局（与扩展处理样板终端一致）。
 */
public class GuiWirelessPatternTerminal extends GuiPatternTerm {

    private static final String BACKGROUND_EXPANDED_PROCESSING_MODE = "guis/pattern_processing_expanded.png";

    private UniversalTerminalButtons universalButtons;

    public GuiWirelessPatternTerminal(final InventoryPlayer inventoryPlayer, final WirelessTerminalGuiObject te) {
        super(inventoryPlayer, te, new ContainerWirelessPatternTerminal(inventoryPlayer, te));
        this.setReservedSpace(81);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.universalButtons = new UniversalTerminalButtons(
                ((appeng.container.AEBaseContainer) this.inventorySlots).getPlayerInv());
        this.universalButtons.initButtons(this.guiLeft, this.guiTop, this.buttonList, 200, this.itemRender);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (this.universalButtons != null && this.universalButtons.handleButtonClick(btn)) {
            return;
        }
        super.actionPerformed(btn);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + 198, offsetY + 127, 0, 0, 32, 32, 32, 32);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    // ---- 覆盖虚拟槽位初始化，处理模式使用 16 输入(4x4) + 6 输出布局 ----

    @Override
    protected void initVirtualSlots() {
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();

        // 16 输入槽位（4x4 网格）
        if (craftInv != null) {
            this.craftingVSlots = new VirtualMEPatternSlot[craftInv.getSizeInventory()];
            for (int i = 0; i < craftInv.getSizeInventory(); i++) {
                final int x = (i % 4) * 18;
                final int y = (i / 4) * 18;
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        i, 15 + x, -76 + y,
                        craftInv, i, this::acceptTypeWireless);
                this.craftingVSlots[i] = slot;
                this.guiSlots.add(slot);
            }
        }

        // 6 输出槽位
        if (outInv != null) {
            this.outputVSlots = new VirtualMEPatternSlot[outInv.getSizeInventory()];
            for (int i = 0; i < outInv.getSizeInventory(); i++) {
                final int x = (i % 4) * 18;
                final int y = (i / 4) * 18;
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        i, 109 + x, -76 + y,
                        outInv, i, this::acceptTypeWireless);
                this.outputVSlots[i] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    private boolean acceptTypeWireless(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        // 处理模式接受所有类型（物品+流体）；合成模式仅接受物品
        if (this.container.isCraftingMode()) {
            return type == appeng.util.item.AEItemStackType.INSTANCE;
        }
        return true;
    }

    @Override
    protected String getBackground() {
        if (this.container.isCraftingMode()) {
            return super.getBackground();
        }
        // 处理模式使用扩展处理终端的背景贴图
        return BACKGROUND_EXPANDED_PROCESSING_MODE;
    }
}

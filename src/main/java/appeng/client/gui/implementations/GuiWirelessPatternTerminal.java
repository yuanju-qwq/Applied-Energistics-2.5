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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.helpers.PatternHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.IAEStackInventory;

/**
 * 无线样板终端 GUI。
 * 合成模式使用父类的 3x3+3 布局；
 * 处理模式覆盖为 4x4 输入 + 6 输出布局（与扩展处理样板终端一致）。
 */
public class GuiWirelessPatternTerminal extends GuiPatternTerm {

    private static final String BACKGROUND_EXPANDED_PROCESSING_MODE = "guis/pattern_processing_expanded.png";
    private static final int PROCESSING_INPUT_OFFSET_X = 4;
    private static final int PROCESSING_INPUT_OFFSET_Y = -85;
    private static final int PROCESSING_INPUT_SCROLLBAR_OFFSET_X = PROCESSING_INPUT_OFFSET_X + 4 * 18 + 4;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = -76;
    private static final int PROCESSING_INPUT_ROWS = 4;

    private UniversalTerminalButtons universalButtons;
    private final GuiScrollbar processingInputScrollbar = new GuiScrollbar();
    private int processingInputPage = 0;

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
    protected void actionPerformed(final GuiButton btn) throws java.io.IOException {
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

        if (!this.container.isCraftingMode() && this.getTotalProcessingInputPages() > 1) {
            this.updateProcessingInputScrollbar();
            GlStateManager.pushMatrix();
            GlStateManager.translate(offsetX, offsetY, 0);
            this.processingInputScrollbar.draw(this);
            GlStateManager.popMatrix();
        }
    }

    // ---- 覆盖虚拟槽位初始化，处理模式使用 16 输入(4x4) + 6 输出布局 ----

    @Override
    protected void initVirtualSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPatternSlot);
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();
        final boolean craftingMode = this.container.isCraftingMode();

        // 16 输入槽位（4x4 网格）
        if (craftInv != null) {
            if (craftingMode) {
                this.craftingVSlots = new VirtualMEPatternSlot[9];
                for (int y = 0; y < 3; y++) {
                    for (int x = 0; x < 3; x++) {
                        final int slotIdx = x + y * 3;
                        final VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                                slotIdx, 18 + x * 18, this.patternGuiY(-76 + y * 18),
                                craftInv, slotIdx, this::acceptTypeWireless);
                        this.craftingVSlots[slotIdx] = slot;
                        this.guiSlots.add(slot);
                    }
                }
            } else {
                final int pageStart = this.processingInputPage * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
                final int pageEnd = Math.min(craftInv.getSizeInventory(), pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
                this.craftingVSlots = new VirtualMEPatternSlot[Math.max(0, pageEnd - pageStart)];
                for (int i = pageStart; i < pageEnd; i++) {
                    final int visibleIndex = i - pageStart;
                    final int x = (visibleIndex % 4) * 18;
                    final int y = (visibleIndex / 4) * 18;
                    final VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                            i, PROCESSING_INPUT_OFFSET_X + x, this.patternGuiY(PROCESSING_INPUT_OFFSET_Y + y),
                            craftInv, i, this::acceptTypeWireless);
                    this.craftingVSlots[visibleIndex] = slot;
                    this.guiSlots.add(slot);
                }
            }
        }

        // 6 输出槽位
        if (outInv != null) {
            this.outputVSlots = new VirtualMEPatternSlot[outInv.getSizeInventory()];
            if (!craftingMode) {
                for (int i = 0; i < outInv.getSizeInventory(); i++) {
                    final int x = (i % 2) * 18;
                    final int y = (i / 2) * 18;
                    final VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                            i, PROCESSING_OUTPUT_OFFSET_X + x, this.patternGuiY(PROCESSING_OUTPUT_OFFSET_Y + y),
                            outInv, i, this::acceptTypeWireless);
                    this.outputVSlots[i] = slot;
                    this.guiSlots.add(slot);
                }
            }
        }

        this.updateVirtualSlotVisibility();
        this.lastCraftingMode = craftingMode;
    }

    private boolean acceptTypeWireless(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        // 处理模式接受所有类型（物品+流体）；合成模式仅接受物品
        if (this.container.isCraftingMode()) {
            return type == appeng.util.item.AEItemStackType.INSTANCE;
        }
        return true;
    }

    @Override
    protected int getMultiplyButtonX() {
        return 131;
    }

    @Override
    protected int getDivideButtonX() {
        return 87;
    }

    @Override
    protected String getBackground() {
        if (this.container.isCraftingMode()) {
            return super.getBackground();
        }
        // 处理模式使用扩展处理终端的背景贴图
        return BACKGROUND_EXPANDED_PROCESSING_MODE;
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws java.io.IOException {
        if (!this.container.isCraftingMode() && btn == 0 && this.updateProcessingInputScrollFromMouse(xCoord, yCoord)) {
            return;
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!this.container.isCraftingMode() && clickedMouseButton == 0
                && this.updateProcessingInputScrollFromMouse(mouseX, mouseY)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        if (!this.container.isCraftingMode() && this.isMouseOverProcessingInputArea(x, y)
                && this.getTotalProcessingInputPages() > 1) {
            final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
            this.processingInputScrollbar.wheel(wheel);
            if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
                this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
                return;
            }
        }
        super.mouseWheelEvent(x, y, wheel);
    }

    private int getTotalProcessingInputPages() {
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        if (craftInv == null) {
            return 1;
        }
        return Math.max(1, (craftInv.getSizeInventory() + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS - 1)
                / PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
    }

    private void updateProcessingInputScrollbar() {
        this.processingInputPage = Math.min(this.processingInputPage, this.getTotalProcessingInputPages() - 1);
        this.processingInputScrollbar
                .setLeft(PROCESSING_INPUT_SCROLLBAR_OFFSET_X)
                .setTop(this.patternGuiY(PROCESSING_INPUT_OFFSET_Y))
                .setHeight(PROCESSING_INPUT_ROWS * 18 - 2);
        this.processingInputScrollbar.setRange(0, Math.max(0, this.getTotalProcessingInputPages() - 1), 1);
        this.processingInputScrollbar.setCurrentScroll(this.processingInputPage);
    }

    private boolean updateProcessingInputScrollFromMouse(final int mouseX, final int mouseY) {
        if (this.getTotalProcessingInputPages() <= 1) {
            return false;
        }

        final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
        this.processingInputScrollbar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
            this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
            return true;
        }
        return false;
    }

    private void setProcessingInputPage(final int page) {
        final int clampedPage = Math.max(0, Math.min(page, this.getTotalProcessingInputPages() - 1));
        if (this.processingInputPage != clampedPage) {
            this.processingInputPage = clampedPage;
            this.initVirtualSlots();
        } else {
            this.processingInputPage = clampedPage;
        }
    }

    private boolean isMouseOverProcessingInputArea(final int mouseX, final int mouseY) {
        final int left = this.guiLeft + PROCESSING_INPUT_OFFSET_X;
        final int top = this.guiTop + this.patternGuiY(PROCESSING_INPUT_OFFSET_Y);
        final int right = this.guiLeft + PROCESSING_INPUT_SCROLLBAR_OFFSET_X + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }
}

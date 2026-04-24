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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerMEInterface;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotOversized;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.helpers.InterfaceLogic;
import appeng.tile.inventory.IAEStackInventory;

/**
 * MUI 版 ME 接口 GUI 面板。
 *
 * 显示 18 个 Config 槽（VirtualMEPhantomSlot，可标记物品或流体）和 18 个 Storage 槽
 * （物品使用 Container 层的 SlotOversized，流体使用 VirtualSlot 从服务端同步显示，
 * 根据 Config 类型动态切换显示）。
 *
 * <h3>布局</h3>
 * <pre>
 * Config 行0 (y=30):  [VirtualMEPhantomSlot × 9]
 * Storage 行0 (y=48): [SlotOversized / FluidStorageVirtualSlot × 9]
 * Config 行1 (y=70):  [VirtualMEPhantomSlot × 9]
 * Storage 行1 (y=88): [SlotOversized / FluidStorageVirtualSlot × 9]
 * </pre>
 */
public class MUIMEInterfacePanel extends MUIUpgradeablePanel implements IJEIGhostIngredients {

    // ========== JEI Ghost 拖放支持 ==========
    protected final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    private final ContainerMEInterface container;

    // ========== Config 虚拟槽位 ==========
    private VirtualMEPhantomSlot[] configSlots;

    // ========== 流体 Storage 虚拟槽位（只读，可控可见性）==========
    private FluidStorageVirtualSlot[] fluidStorageSlots;

    // ========== 物品 Storage slot 引用及原始 xPos（用于隐藏/恢复）==========
    private AppEngSlot[] itemStorageSlots;
    private int[] itemStorageOrigX;

    // ========== 按钮 ==========
    private GuiToggleButton interfaceMode;

    public MUIMEInterfacePanel(final ContainerMEInterface container) {
        super(container);
        this.container = container;
        this.ySize = 222;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();
        this.initConfigSlots();
        this.initFluidStorageSlots();
        this.saveItemStorageSlotPositions();
        this.updateStorageSlotVisibility();
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.interfaceMode = new GuiToggleButton(this.guiLeft - 18, this.guiTop + 8, 84, 85,
                GuiText.InterfaceTerminal.getLocal(), GuiText.InterfaceTerminalHint.getLocal());
        this.buttonList.add(this.interfaceMode);
    }

    // ========== Config 虚拟槽位初始化 ==========

    private void initConfigSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPhantomSlot);
        this.configSlots = new VirtualMEPhantomSlot[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];
        final IAEStackInventory config = this.container.getConfig();

        for (int row = 0; row < 2; row++) {
            final int rowY = (row == 0) ? ContainerMEInterface.CONFIG_ROW_0_Y : ContainerMEInterface.CONFIG_ROW_1_Y;
            for (int col = 0; col < InterfaceLogic.SLOTS_PER_ROW; col++) {
                final int slotIdx = row * InterfaceLogic.SLOTS_PER_ROW + col;
                final int x = ContainerMEInterface.SLOT_X_OFFSET + col * 18;
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        slotIdx, x, rowY, config, slotIdx, this::acceptType);
                this.configSlots[slotIdx] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    // ========== 流体 Storage 槽位初始化 ==========

    private void initFluidStorageSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof FluidStorageVirtualSlot);
        this.fluidStorageSlots = new FluidStorageVirtualSlot[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];
        final IAEStackInventory fluidStorage = this.container.getFluidStorageClientInv();

        for (int row = 0; row < 2; row++) {
            final int rowY = (row == 0) ? ContainerMEInterface.STORAGE_ROW_0_Y : ContainerMEInterface.STORAGE_ROW_1_Y;
            for (int col = 0; col < InterfaceLogic.SLOTS_PER_ROW; col++) {
                final int slotIdx = row * InterfaceLogic.SLOTS_PER_ROW + col;
                final int x = ContainerMEInterface.SLOT_X_OFFSET + col * 18;
                FluidStorageVirtualSlot slot = new FluidStorageVirtualSlot(
                        slotIdx + InterfaceLogic.NUMBER_OF_CONFIG_SLOTS,
                        x, rowY, fluidStorage, slotIdx);
                this.fluidStorageSlots[slotIdx] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    // ========== 物品 Storage slot 位置记录 ==========

    private void saveItemStorageSlotPositions() {
        this.itemStorageSlots = new AppEngSlot[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];
        this.itemStorageOrigX = new int[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];
        int idx = 0;
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotOversized && idx < InterfaceLogic.NUMBER_OF_CONFIG_SLOTS) {
                this.itemStorageSlots[idx] = (AppEngSlot) slot;
                this.itemStorageOrigX[idx] = slot.xPos;
                idx++;
            }
        }
    }

    /**
     * Dynamically toggle each storage slot between item slot and fluid virtual slot
     * based on the Config stack type.
     * <ul>
     *   <li>Config = non-item type (fluid, etc.) → hide item slot, show FluidStorageVirtualSlot</li>
     *   <li>Config = item type or null → show item slot, hide FluidStorageVirtualSlot</li>
     * </ul>
     */
    private void updateStorageSlotVisibility() {
        final IAEStackInventory config = this.container.getConfig();

        for (int i = 0; i < InterfaceLogic.NUMBER_OF_CONFIG_SLOTS; i++) {
            final IAEStack<?> configStack = config.getAEStackInSlot(i);
            // Non-item types (fluids, etc.) use FluidStorageVirtualSlot;
            // items and null use the regular item slot.
            // This is a Minecraft Slot system limitation: Slot only holds ItemStack,
            // so non-item types must use virtual slots.
            final boolean isNonItem = configStack != null && !configStack.isItem();

            if (this.itemStorageSlots != null && this.itemStorageSlots[i] != null) {
                this.itemStorageSlots[i].xPos = isNonItem ? -9999 : this.itemStorageOrigX[i];
            }

            if (this.fluidStorageSlots != null && this.fluidStorageSlots[i] != null) {
                this.fluidStorageSlots[i].setHidden(!isNonItem);
            }
        }
    }

    // ========== Config 槽位类型接受判断 ==========

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        return true;
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.updateStorageSlotVisibility();

        if (this.interfaceMode != null) {
            this.interfaceMode.setState(this.container.getInterfaceTerminalMode() == YesNo.YES);
        }

        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.MEInterface.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.Config.getLocal(), 8, 20, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);
    }

    @Override
    protected String getBackground() {
        return "guis/meinterface.png";
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.interfaceMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
        }
    }

    // ========== JEI Ghost 拖放 ==========

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        mapTargetSlot.clear();

        FluidStack fluidStack = null;
        ItemStack itemStack = ItemStack.EMPTY;

        if (ingredient instanceof ItemStack) {
            itemStack = (ItemStack) ingredient;
            fluidStack = FluidUtil.getFluidContained(itemStack);
        } else if (ingredient instanceof FluidStack) {
            fluidStack = (FluidStack) ingredient;
        }

        if (!(ingredient instanceof ItemStack) && !(ingredient instanceof FluidStack)) {
            return Collections.emptyList();
        }

        List<Target<?>> targets = new ArrayList<>();

        // Config VirtualMEPhantomSlot — 接受物品和流体
        for (GuiCustomSlot slot : this.getGuiSlots()) {
            if (slot instanceof VirtualMEPhantomSlot phantomSlot && phantomSlot.isSlotEnabled()) {
                addConfigTarget(targets, phantomSlot, itemStack, fluidStack);
            }
        }

        return targets;
    }

    private void addConfigTarget(List<Target<?>> targets, VirtualMEPhantomSlot phantomSlot,
            ItemStack itemStack, FluidStack fluidStack) {
        final ItemStack finalItemStack = itemStack;
        final FluidStack finalFluidStack = fluidStack;
        Target<Object> target = new Target<>() {
            @Nonnull
            @Override
            public java.awt.Rectangle getArea() {
                return new java.awt.Rectangle(getGuiLeft() + phantomSlot.xPos(),
                        getGuiTop() + phantomSlot.yPos(), 16, 16);
            }

            @Override
            public void accept(@Nonnull Object ingredient) {
                if (finalFluidStack != null) {
                    phantomSlot.handleMouseClicked(
                            FluidUtil.getFilledBucket(finalFluidStack), false, 0);
                } else if (!finalItemStack.isEmpty()) {
                    phantomSlot.handleMouseClicked(finalItemStack, false, 0);
                }
            }
        };
        targets.add(target);
        mapTargetSlot.putIfAbsent(target, phantomSlot);
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }

    // ========== 只读流体 Storage 虚拟槽位 ==========

    /**
     * Read-only VirtualMESlot that displays fluid storage data synced from server
     * via {@link appeng.api.storage.StorageName#STORAGE} VirtualSlot channel.
     * Does not handle clicks (storage is managed by the server).
     */
    private static class FluidStorageVirtualSlot extends VirtualMESlot {

        private final IAEStackInventory inventory;
        private final int inventorySlot;
        private boolean hidden = false;

        FluidStorageVirtualSlot(int id, int x, int y, IAEStackInventory inventory, int inventorySlot) {
            super(id, x, y, inventorySlot);
            this.inventory = inventory;
            this.inventorySlot = inventorySlot;
        }

        @Override
        @Nullable
        public IAEStack<?> getAEStack() {
            return this.inventory.getAEStackInSlot(this.inventorySlot);
        }

        @Override
        public boolean isSlotEnabled() {
            return !this.hidden;
        }

        @Override
        public boolean isVisible() {
            return !this.hidden;
        }

        @Override
        public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (this.hidden) {
                return;
            }
            super.drawContent(mc, mouseX, mouseY, partialTicks);
        }

        @Override
        public boolean canClick(EntityPlayer player) {
            return false;
        }

        public void setHidden(boolean hidden) {
            this.hidden = hidden;
        }
    }
}

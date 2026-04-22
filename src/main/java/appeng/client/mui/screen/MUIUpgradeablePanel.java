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

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.*;
import appeng.api.implementations.IUpgradeableHost;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.client.gui.widgets.GuiFluidSlot;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import appeng.util.item.AEItemStack;

/**
 * MUI 版 GuiUpgradeable 基类。
 *
 * 作为所有可升级配置 GUI 的 MUI 移植基础，对应旧代码的
 * {@link appeng.client.gui.implementations.GuiUpgradeable}。
 *
 * 子类通过覆写 {@link #getBackground()}, {@link #getName()},
 * {@link #addButtons()}, {@link #handleButtonVisibility()}, {@link #drawUpgrades()}
 * 来定制具体 GUI 的行为。
 */
public class MUIUpgradeablePanel extends AEBasePanel implements IJEIGhostIngredients {

    // ========== JEI Ghost 拖放支持 ==========
    protected final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    // ========== Container 引用 ==========
    protected final ContainerUpgradeable cvb;
    protected final IUpgradeableHost bc;

    // ========== 通用按钮 ==========
    protected GuiImgButton redstoneMode;
    protected GuiImgButton fuzzyMode;
    protected GuiImgButton craftMode;
    protected GuiImgButton schedulingMode;

    public MUIUpgradeablePanel(final ContainerUpgradeable te) {
        super(te);
        this.cvb = te;
        this.bc = (IUpgradeableHost) te.getTarget();
        this.xSize = this.hasToolbox() ? 246 : 211;
        this.ySize = 184;
    }

    protected boolean hasToolbox() {
        return ((ContainerUpgradeable) this.inventorySlots).hasToolbox();
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // 子类在覆写 setupWidgets() 时应先调用 super.setupWidgets()
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addButtons();
    }

    // ========== 按钮管理 ==========

    /**
     * 添加通用按钮（redstone, fuzzy, craft, scheduling）。
     * 子类覆写此方法来替换或增加按钮。
     */
    protected void addButtons() {
        this.redstoneMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.REDSTONE_CONTROLLED,
                RedstoneMode.IGNORE);
        this.fuzzyMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);
        this.craftMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.CRAFT_ONLY, YesNo.NO);
        this.schedulingMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68, Settings.SCHEDULING_MODE,
                SchedulingMode.DEFAULT);

        this.buttonList.add(this.craftMode);
        this.buttonList.add(this.redstoneMode);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.schedulingMode);
    }

    /**
     * 根据已安装的升级卡来控制按钮的可见性。
     * 子类可覆写以定制可见性逻辑。
     */
    protected void handleButtonVisibility() {
        if (this.redstoneMode != null) {
            this.redstoneMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.REDSTONE) > 0);
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.FUZZY) > 0);
        }
        if (this.craftMode != null) {
            this.craftMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.CRAFTING) > 0);
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.setVisibility(
                    this.bc.getInstalledUpgrades(Upgrades.CAPACITY) > 0 && this.bc instanceof PartExportBus);
        }
    }

    // ========== 渲染 ==========

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.handleButtonVisibility();

        this.bindTexture(this.getBackground());
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, 211 - 34, this.ySize);
        if (this.drawUpgrades()) {
            this.drawTexturedModalRect(offsetX + 177, offsetY, 177, 0, 35, 14 + this.cvb.availableUpgrades() * 18);
        }
        if (this.hasToolbox()) {
            this.drawTexturedModalRect(offsetX + 178, offsetY + this.ySize - 90, 178, this.ySize - 90, 68, 68);
        }
    }

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.getName().getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        if (this.redstoneMode != null) {
            this.redstoneMode.set(this.cvb.getRedStoneMode());
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(this.cvb.getFuzzyMode());
        }
        if (this.craftMode != null) {
            this.craftMode.set(this.cvb.getCraftingMode());
        }
        if (this.schedulingMode != null) {
            this.schedulingMode.set(this.cvb.getSchedulingMode());
        }
    }

    // ========== 子类可覆写的模板方法 ==========

    /**
     * 返回背景贴图路径。子类覆写以使用不同的贴图。
     */
    protected String getBackground() {
        return "guis/bus.png";
    }

    /**
     * 是否绘制右侧的升级槽区域。子类覆写以隐藏。
     */
    protected boolean drawUpgrades() {
        return true;
    }

    /**
     * 返回 GUI 标题文本。子类覆写以显示不同标题。
     */
    protected GuiText getName() {
        return this.bc instanceof PartImportBus ? GuiText.ImportBus : GuiText.ExportBus;
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.redstoneMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.redstoneMode.getSetting(), backwards));
        }
        if (btn == this.craftMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.craftMode.getSetting(), backwards));
        }
        if (btn == this.fuzzyMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.fuzzyMode.getSetting(), backwards));
        }
        if (btn == this.schedulingMode) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(this.schedulingMode.getSetting(), backwards));
        }
    }

    // ========== JEI 排除区域 ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8;
        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        Rectangle sortDir = new Rectangle(guiLeft - 18, yOffset, 18, visibleButtons * 18 + visibleButtons - 2);
        exclusionArea.add(sortDir);

        return exclusionArea;
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

        List<IJEITargetSlot> slots = new ArrayList<>();
        if (!this.inventorySlots.inventorySlots.isEmpty()) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                if (slot instanceof SlotFake && !itemStack.isEmpty()) {
                    slots.add((IJEITargetSlot) slot);
                }
            }
        }
        if (!this.getGuiSlots().isEmpty()) {
            for (GuiCustomSlot slot : this.getGuiSlots()) {
                if (slot instanceof GuiFluidSlot && fluidStack != null) {
                    slots.add((IJEITargetSlot) slot);
                }
            }
        }
        for (IJEITargetSlot slot : slots) {
            ItemStack finalItemStack = itemStack;
            FluidStack finalFluidStack = fluidStack;
            Target<Object> targetItem = new Target<>() {
                @Nonnull
                @Override
                public Rectangle getArea() {
                    if (slot instanceof SlotFake slotFake && slotFake.isSlotEnabled()) {
                        return new Rectangle(getGuiLeft() + slotFake.xPos,
                                getGuiTop() + slotFake.yPos, 16, 16);
                    } else if (slot instanceof GuiFluidSlot fluidSlot && fluidSlot.isSlotEnabled()) {
                        return new Rectangle(getGuiLeft() + fluidSlot.xPos(),
                                getGuiTop() + fluidSlot.yPos(), 16, 16);
                    }
                    return new Rectangle();
                }

                @Override
                public void accept(@Nonnull Object ingredient) {
                    PacketInventoryAction p = null;
                    try {
                        if (slot instanceof SlotFake slotFake && slotFake.isSlotEnabled()) {
                            if (finalItemStack.isEmpty() && finalFluidStack != null) {
                                p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot,
                                        AEItemStack.fromItemStack(FluidUtil.getFilledBucket(finalFluidStack)));
                            } else if (!finalItemStack.isEmpty()) {
                                p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot,
                                        AEItemStack.fromItemStack(finalItemStack));
                            }
                        } else {
                            if (finalFluidStack == null) {
                                return;
                            }
                            p = new PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, slot,
                                    AEItemStack.fromItemStack(
                                            AEFluidStack.fromFluidStack(finalFluidStack).asItemStackRepresentation()));
                        }
                        NetworkHandler.instance().sendToServer(p);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };
            targets.add(targetItem);
            mapTargetSlot.putIfAbsent(targetItem, slot);
        }
        return targets;
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}

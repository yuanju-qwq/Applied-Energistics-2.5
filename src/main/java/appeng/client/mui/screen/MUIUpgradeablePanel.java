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

import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.*;
import appeng.api.implementations.IUpgradeableHost;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.IJEITargetSlot;
import appeng.container.slot.SlotFake;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.parts.automation.PartExportBus;
import appeng.parts.automation.PartImportBus;
import appeng.util.item.AEItemStack;

/**
 * MUI base class for GuiUpgradeable.
 *
 * Serves as the MUI port foundation for all upgradeable config GUIs.
 *
 * 子类通过覆写 {@link #getBackground()}, {@link #getName()},
 * {@link #addButtons()}, {@link #handleButtonVisibility()}, {@link #drawUpgrades()}
 * to customize specific GUI behavior.
 */
public class MUIUpgradeablePanel extends AEBasePanel implements IJEIGhostIngredients {

    // ========== JEI Ghost 拖放支持 ==========
    protected final Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    // ========== Container 引用 ==========
    protected final ContainerUpgradeable cvb;
    protected final IUpgradeableHost bc;

    // ========== Common buttons ==========
    protected MUIButtonWidget redstoneMode;
    protected MUIButtonWidget fuzzyMode;
    protected MUIButtonWidget craftMode;
    protected MUIButtonWidget schedulingMode;

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

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        // Subclasses should call super.setupWidgets() first when overriding setupWidgets()
    }

    @Override
    public void initGui() {
        super.initGui();
        this.addButtons();
    }

    // ========== 按钮管理 ==========

    /**
     * Add common buttons (redstone, fuzzy, craft, scheduling).
     * Subclasses override to replace or add buttons.
     * <p>
     * Buttons use panel-relative coordinates and are registered as MUI widgets.
     */
    protected void addButtons() {
        this.redstoneMode = new MUIButtonWidget(-18, 8, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
        this.redstoneMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.redstoneMode);

        this.fuzzyMode = new MUIButtonWidget(-18, 28, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.fuzzyMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.fuzzyMode);

        this.craftMode = new MUIButtonWidget(-18, 48, Settings.CRAFT_ONLY, YesNo.NO);
        this.craftMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.craftMode);

        this.schedulingMode = new MUIButtonWidget(-18, 68, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT);
        this.schedulingMode.setOnClick(btn -> sendConfigButton(btn));
        this.addWidget(this.schedulingMode);
    }

    /**
     * Send a config button packet for the given MUI settings button.
     */
    protected void sendConfigButton(MUIButtonWidget btn) {
        final boolean backwards = Mouse.isButtonDown(1);
        final Settings setting = btn.getSetting();
        if (setting != null) {
            NetworkHandler.instance().sendToServer(new PacketConfigButton(setting, backwards));
        }
    }

    /**
    // ========== Rendering ==========
     * Subclasses may override to customize visibility logic.
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
        this.fontRenderer.drawString(this.getGuiDisplayName(this.getName().getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

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
     * Returns the background texture path. Subclasses override to use a different texture.
     */
    protected String getBackground() {
        return "guis/bus.png";
    }

    /**
     * Whether to draw the upgrade slot area on the right. Subclasses override to hide it.
     */
    protected boolean drawUpgrades() {
        return true;
    }

    /**
     * Returns the GUI title text. Subclasses override to display a different title.
     */
    protected GuiText getName() {
        return this.bc instanceof PartImportBus ? GuiText.ImportBus : GuiText.ExportBus;
    }

    // ========== Button events ==========

    // Button click handling is now done via onClick callbacks set in addButtons().
    // Subclasses that still use legacy buttonList can override actionPerformed().

    // ========== JEI exclusion area ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8;
        // Count visible MUI buttons on the left side (x < 0 in panel-relative)
        int visibleButtons = 0;
        if (this.redstoneMode != null && this.redstoneMode.isVisible()) {
            visibleButtons++;
        }
        if (this.fuzzyMode != null && this.fuzzyMode.isVisible()) {
            visibleButtons++;
        }
        if (this.craftMode != null && this.craftMode.isVisible()) {
            visibleButtons++;
        }
        if (this.schedulingMode != null && this.schedulingMode.isVisible()) {
            visibleButtons++;
        }
        // Also count legacy buttons still in buttonList
        visibleButtons += (int) this.buttonList.stream()
                .filter(v -> v.enabled && v.x < guiLeft).count();
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
                        }
                        if (p != null) {
                            NetworkHandler.instance().sendToServer(p);
                        }

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

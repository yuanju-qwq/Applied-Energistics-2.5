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
import java.util.*;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

/**
 * MUI 版样板终端面板。
 * <p>
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiPatternTerm}。
 * 继承 {@link MUIMEMonitorablePanel}，增加样板编码功能：
 * <ul>
 *   <li>合成/处理双模式切换</li>
 *   <li>3×3 虚拟合成网格（VirtualMEPatternSlot）</li>
 *   <li>输出槽虚拟显示</li>
 *   <li>材料替代开关</li>
 *   <li>数量乘除按钮</li>
 *   <li>编码/清除按钮</li>
 *   <li>JEI 幽灵拖拽支持</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUIPatternTermPanel extends MUIMEMonitorablePanel implements IJEIGhostIngredients {

    // ========== 常量 ==========

    private static final String BACKGROUND_CRAFTING_MODE = "guis/pattern.png";
    private static final String BACKGROUND_PROCESSING_MODE = "guis/pattern2.png";

    private static final String SUBSITUTION_DISABLE = "0";
    private static final String SUBSITUTION_ENABLE = "1";

    private static final String CRAFTMODE_CRFTING = "1";
    private static final String CRAFTMODE_PROCESSING = "0";

    // ========== 数据 ==========

    protected final ContainerPatternEncoder container;

    // ========== UI 控件 ==========

    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton maxCountBtn;
    public Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    protected VirtualMEPatternSlot[] craftingVSlots;
    protected VirtualMEPatternSlot[] outputVSlots;
    protected Boolean lastCraftingMode;

    // ========== 构造 ==========

    public MUIPatternTermPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternTerm) this.inventorySlots;
        this.setReservedSpace(81);
    }

    public MUIPatternTermPanel(final InventoryPlayer inventoryPlayer, WirelessTerminalGuiObject te,
            final ContainerWirelessPatternTerminal wpt) {
        super(inventoryPlayer, te, wpt);
        this.container = (ContainerWirelessPatternTerminal) this.inventorySlots;
        this.setReservedSpace(81);
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        try {

            if (this.tabCraftButton == btn || this.tabProcessButton == btn) {
                NetworkHandler.instance()
                        .sendToServer(
                                new PacketValueConfig("PatternTerminal.CraftMode",
                                        this.tabProcessButton == btn ? CRAFTMODE_CRFTING : CRAFTMODE_PROCESSING));
            }

            if (this.encodeBtn == btn) {
                if (isShiftKeyDown()) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "2"));
                } else {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "1"));
                }
            }

            if (this.clearBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            }

            if (this.x2Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DivideByTwo" : "PatternTerminal.MultiplyByTwo", "1"));
            }

            if (this.x3Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DivideByThree" : "PatternTerminal.MultiplyByThree", "1"));
            }

            if (this.plusOneBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DecreaseByOne" : "PatternTerminal.IncreaseByOne", "1"));
            }

            if (this.maxCountBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.MaximizeCount", "1"));
            }

            if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                NetworkHandler.instance()
                        .sendToServer(
                                new PacketValueConfig("PatternTerminal.Substitute",
                                        this.substitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE
                                                : SUBSITUTION_ENABLE));
            }
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        this.tabCraftButton = new GuiTabButton(this.guiLeft + 173, this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.CRAFTING_TABLE), GuiText.CraftingPattern
                        .getLocal(),
                this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(this.guiLeft + 173, this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.FURNACE), GuiText.ProcessingPattern
                        .getLocal(),
                this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        this.substitutionsEnabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(this.guiLeft + 74, this.guiTop + this.ySize - 163, Settings.ACTIONS,
                ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        this.x3Btn = new GuiImgButton(this.guiLeft + this.getMultiplyButtonX(),
                this.guiTop + this.ySize - 158, Settings.ACTIONS,
                ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(this.guiLeft + this.getMultiplyButtonX(),
                this.guiTop + this.ySize - 148, Settings.ACTIONS,
                ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(this.guiLeft + this.getMultiplyButtonX(),
                this.guiTop + this.ySize - 138, Settings.ACTIONS,
                ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(this.guiLeft + this.getDivideButtonX(),
                this.guiTop + this.ySize - 158, Settings.ACTIONS,
                ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.divThreeBtn.visible = false;
        this.divThreeBtn.enabled = false;
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(this.guiLeft + this.getDivideButtonX(),
                this.guiTop + this.ySize - 148, Settings.ACTIONS,
                ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.divTwoBtn.visible = false;
        this.divTwoBtn.enabled = false;
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(this.guiLeft + this.getDivideButtonX(),
                this.guiTop + this.ySize - 138, Settings.ACTIONS,
                ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.minusOneBtn.visible = false;
        this.minusOneBtn.enabled = false;
        this.buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(this.guiLeft + 147, this.guiTop + this.ySize - 142, Settings.ACTIONS,
                ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        this.initVirtualSlots();
    }

    // ========== 绘制 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.refreshVirtualSlots();

        if (this.container.isCraftingMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;

            if (this.container.substitute) {
                this.substitutionsEnabledBtn.visible = true;
                this.substitutionsDisabledBtn.visible = false;
            } else {
                this.substitutionsEnabledBtn.visible = false;
                this.substitutionsDisabledBtn.visible = true;
            }
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.x2Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = true;
            this.plusOneBtn.set(isShiftKeyDown() ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
            this.minusOneBtn.visible = false;
        }

        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8,
                this.ySize - 96 + 2 - this.getReservedSpace(), 4210752);
    }

    @Override
    protected String getBackground() {
        if (this.container.isCraftingMode()) {
            return BACKGROUND_CRAFTING_MODE;
        }

        return BACKGROUND_PROCESSING_MODE;
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        final int offsetPlayerSide = s.isPlayerSide() ? 5 : 3;

        s.yPos = s.getY() + this.ySize - 78 - offsetPlayerSide;
    }

    // ========== JEI 幽灵拖拽 ==========

    @Override
    public List<Target<?>> getPhantomTargets(Object ingredient) {
        final ItemStack itemIngredient = ingredient instanceof ItemStack ? (ItemStack) ingredient : ItemStack.EMPTY;
        final IAEStack<?> aeIngredient = this.toJeiGhostStack(ingredient);

        if (itemIngredient.isEmpty() && aeIngredient == null) {
            return Collections.emptyList();
        }

        if (aeIngredient != null && this.container.isCraftingMode()) {
            return Collections.emptyList();
        }

        this.mapTargetSlot.clear();
        final List<Target<?>> targets = new ArrayList<>();
        this.addVirtualTargets(targets, this.craftingVSlots, itemIngredient, aeIngredient);
        this.addVirtualTargets(targets, this.outputVSlots, itemIngredient, aeIngredient);
        return targets;
    }

    protected IAEStack<?> toJeiGhostStack(final Object ingredient) {
        if (ingredient instanceof FluidStack fluidStack) {
            return AEFluidStack.fromFluidStack(fluidStack.copy());
        }
        return null;
    }

    private void addVirtualTargets(List<Target<?>> targets, VirtualMEPatternSlot[] slots, ItemStack itemIngredient,
            IAEStack<?> aeIngredient) {
        if (slots == null) {
            return;
        }

        for (VirtualMEPatternSlot slot : slots) {
            if (slot == null || !slot.isVisible() || !slot.isSlotEnabled()) {
                continue;
            }

            final Target<Object> target = new Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + slot.xPos(), getGuiTop() + slot.yPos(), 16, 16);
                }

                @Override
                public void accept(Object ignored) {
                    if (aeIngredient != null) {
                        final IAEStackInventory targetInv = slot.getStorageName() == StorageName.CRAFTING_OUTPUT
                                ? container.getOutputAEInv()
                                : container.getCraftingAEInv();
                        if (targetInv != null) {
                            targetInv.putAEStackInSlot(slot.getSlotIndex(), aeIngredient.copy());
                        }
                        NetworkHandler.instance().sendToServer(
                                new PacketVirtualSlot(slot.getStorageName(), slot.getSlotIndex(), aeIngredient));
                    } else {
                        slot.handleMouseClicked(itemIngredient, false, 0);
                    }
                }
            };

            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }

    // ========== 虚拟槽位管理 ==========

    protected void initVirtualSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPatternSlot);

        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();

        if (craftInv != null) {
            this.craftingVSlots = new VirtualMEPatternSlot[craftInv.getSizeInventory()];
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    final int slotIdx = x + y * 3;
                    VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                            slotIdx, 18 + x * 18, this.patternGuiY(-76 + y * 18),
                            craftInv, slotIdx, this::acceptType);
                    this.craftingVSlots[slotIdx] = slot;
                    this.guiSlots.add(slot);
                }
            }
        }

        if (outInv != null) {
            this.outputVSlots = new VirtualMEPatternSlot[outInv.getSizeInventory()];
            for (int y = 0; y < outInv.getSizeInventory(); y++) {
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        y, 110, this.patternGuiY(-76 + y * 18),
                        outInv, y, this::acceptType);
                this.outputVSlots[y] = slot;
                this.guiSlots.add(slot);
            }
        }

        this.updateVirtualSlotVisibility();
        this.lastCraftingMode = this.container.isCraftingMode();
    }

    protected boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        // 物品类型始终接受；流体类型只在处理模式下接受
        if (type == AEItemStackType.INSTANCE) {
            return true;
        }
        return !this.container.isCraftingMode();
    }

    protected int getMultiplyButtonX() {
        return 128;
    }

    protected int getDivideButtonX() {
        return 100;
    }

    protected void refreshVirtualSlots() {
        final boolean craftingMode = this.container.isCraftingMode();
        if (this.lastCraftingMode == null || this.lastCraftingMode.booleanValue() != craftingMode) {
            this.initVirtualSlots();
            return;
        }

        this.updateVirtualSlotVisibility();
    }

    protected void updateVirtualSlotVisibility() {
        final boolean craftingMode = this.container.isCraftingMode();

        if (this.craftingVSlots != null) {
            for (final VirtualMEPatternSlot slot : this.craftingVSlots) {
                if (slot != null) {
                    slot.setHidden(false);
                }
            }
        }

        if (this.outputVSlots != null) {
            for (final VirtualMEPatternSlot slot : this.outputVSlots) {
                if (slot != null) {
                    slot.setHidden(craftingMode);
                }
            }
        }
    }

    protected int patternGuiY(final int y) {
        return y + this.ySize - 81;
    }
}

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

package appeng.client.mui.module;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.ActionItems;
import appeng.api.config.CombineMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PatternHelper;

/**
 * 样板编码模块 — 从 GuiWirelessDualInterfaceTerminal 中提取的可复用组件。
 *
 * <p>负责：
 * <ul>
 *   <li>样板编码按钮的创建、布局、可见性管理</li>
 *   <li>绘制 pattern.png / pattern3.png 面板背景</li>
 *   <li>按钮点击事件 → PacketValueConfig 发送</li>
 *   <li>repositionSlots: 根据 crafting/processing 模式动态定位槽位</li>
 *   <li>processing 模式输入/输出滚动条管理</li>
 *   <li>PlacePattern 自动放入功能</li>
 *   <li>面板拖拽支持</li>
 * </ul>
 */
public class PatternEncodingModule {

    // ========== 纹理 ==========

    private static final ResourceLocation PATTERN_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern.png");
    private static final ResourceLocation PATTERN3_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern3.png");

    // ========== 布局常量 ==========

    private static final int CRAFTING_INPUT_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;

    private static final int PATTERN_PANEL_X_OFFSET = 209;
    private static final int PATTERN_PANEL_WIDTH = 133;
    private static final int PATTERN_PANEL_UPPER_HEIGHT = 93;
    private static final int PATTERN_PANEL_LOWER_WIDTH = 40;
    private static final int PATTERN_PANEL_LOWER_HEIGHT = 77;
    private static final int PATTERN_PANEL_FOOTER_WIDTH = 32;
    private static final int PATTERN_PANEL_FOOTER_HEIGHT = 32;
    private static final int PATTERN_PANEL_HEIGHT = PATTERN_PANEL_UPPER_HEIGHT + PATTERN_PANEL_LOWER_HEIGHT;
    static final int PATTERN_PANEL_TOTAL_HEIGHT = PATTERN_PANEL_HEIGHT + PATTERN_PANEL_FOOTER_HEIGHT;

    private static final int CRAFTING_GRID_OFFSET_X = 15;
    private static final int CRAFTING_GRID_OFFSET_Y = 18;
    private static final int PROCESSING_GRID_OFFSET_X = 15;
    private static final int PROCESSING_GRID_OFFSET_Y = 9;
    private static final int PROCESSING_INPUT_ROWS = 4;

    private static final int CRAFTING_OUTPUT_OFFSET_X = 108;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = 9;
    private static final int PROCESSING_OUTPUT_COLUMNS = 1;
    private static final int PROCESSING_OUTPUT_ROWS = 4;
    private static final int PROCESSING_OUTPUT_NORMAL_OFFSET_X = 112;
    private static final int PROCESSING_OUTPUT_INVERTED_OFFSET_X = 15;
    private static final int PROCESSING_INVERTED_GRID_OFFSET_X = 58;

    private static final int PATTERN_IN_OFFSET_X = 10;
    private static final int PATTERN_IN_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 6;
    private static final int PATTERN_OUT_OFFSET_X = 11;
    private static final int PATTERN_OUT_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 49;

    // ========== 宿主接口 ==========

    /**
     * 宿主 GUI 必须实现此接口来提供模块所需的上下文。
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getYSize();

        AEBasePanel getPanel();

        RenderItem getItemRenderer();

        ContainerWirelessDualInterfaceTerminal getDualContainer();

        List<GuiButton> getButtonList();

        /**
         * 请求宿主重新初始化 GUI。
         */
        void requestReinitialize();

        /**
         * 获取接口列表模块（用于 PlacePattern）。
         */
        InterfaceListModule getInterfaceListModule();
    }

    // ========== 数据 ==========

    private final Host host;

    // 按钮
    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton beSubstitutionsEnabledBtn;
    private GuiImgButton beSubstitutionsDisabledBtn;
    private GuiImgButton invertBtn;
    private GuiImgButton combineEnabledBtn;
    private GuiImgButton combineDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;

    // 数量调节按钮
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton doubleBtn;

    // 滚动条
    private final GuiScrollbar processingInputScrollbar;
    private int processingInputPage = 0;
    private final GuiScrollbar processingScrollBar;

    // PlacePattern
    private boolean pendingPlacePattern = false;

    // 面板拖拽
    private PanelDragState dragState;

    // ========== 构造 ==========

    public PatternEncodingModule(Host host) {
        this.host = host;
        this.processingInputScrollbar = new GuiScrollbar();
        this.processingScrollBar = new GuiScrollbar();
    }

    // ========== 访问器 ==========

    public int getPanelX() {
        return PATTERN_PANEL_X_OFFSET + (dragState != null ? dragState.getDragOffsetX() : 0);
    }

    public int getPanelY() {
        return (dragState != null ? dragState.getDragOffsetY() : 0);
    }

    public int getPanelWidth() {
        return PATTERN_PANEL_WIDTH;
    }

    public int getPanelTotalHeight() {
        return PATTERN_PANEL_TOTAL_HEIGHT;
    }

    public GuiScrollbar getProcessingInputScrollbar() {
        return processingInputScrollbar;
    }

    public GuiScrollbar getProcessingScrollBar() {
        return processingScrollBar;
    }

    public PanelDragState getDragState() {
        return dragState;
    }

    // ========== 初始化 ==========

    /**
     * 初始化拖拽状态。必须在 initGui 开始时调用。
     */
    public void initDragState() {
        this.dragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = host.getGuiLeft() + getPanelX();
            final int absY = host.getGuiTop() + getPanelY();
            return mouseX >= absX && mouseX < absX + PATTERN_PANEL_WIDTH
                    && mouseY >= absY + PATTERN_PANEL_HEIGHT
                    && mouseY < absY + PATTERN_PANEL_HEIGHT + 16;
        });
    }

    /**
     * 创建所有按钮。在 initGui 中调用。
     */
    public void initButtons() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();
        final List<GuiButton> buttonList = host.getButtonList();

        // 编码按钮
        this.encodeBtn = new GuiImgButton(panelScreenX + 11, panelScreenY + 118,
                Settings.ACTIONS, ActionItems.ENCODE);
        buttonList.add(this.encodeBtn);

        // 清除按钮
        this.clearBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 10,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        buttonList.add(this.clearBtn);

        // 制作/加工模式切换按钮
        this.tabCraftButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.CRAFTING_TABLE),
                GuiText.CraftingPattern.getLocal(), host.getItemRenderer());
        buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.FURNACE),
                GuiText.ProcessingPattern.getLocal(), host.getItemRenderer());
        buttonList.add(this.tabProcessButton);

        // 替代品按钮
        this.substitutionsEnabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        buttonList.add(this.substitutionsDisabledBtn);

        // beSubstitute 按钮
        this.beSubstitutionsEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.beSubstitutionsEnabledBtn.setHalfSize(true);
        buttonList.add(this.beSubstitutionsEnabledBtn);

        this.beSubstitutionsDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.beSubstitutionsDisabledBtn.setHalfSize(true);
        buttonList.add(this.beSubstitutionsDisabledBtn);

        // 反转按钮
        this.invertBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 20,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.invertBtn.setHalfSize(true);
        buttonList.add(this.invertBtn);

        // 合并按钮
        this.combineEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.ENABLED);
        this.combineEnabledBtn.setHalfSize(true);
        buttonList.add(this.combineEnabledBtn);

        this.combineDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.DISABLED);
        this.combineDisabledBtn.setHalfSize(true);
        buttonList.add(this.combineDisabledBtn);

        // 数量调节按钮
        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28;

        this.x3Btn = new GuiImgButton(adjBtnX1, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(adjBtnX1, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(adjBtnX1, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(adjBtnX2, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(adjBtnX2, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(adjBtnX2, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        buttonList.add(this.minusOneBtn);

        this.doubleBtn = new GuiImgButton(adjBtnX2, panelScreenY + 36,
                Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
        this.doubleBtn.setHalfSize(true);
        buttonList.add(this.doubleBtn);

        // 初始化滚动条
        this.updateProcessingScrollbar();
        this.updateProcessingInputScrollbar();
    }

    // ========== 槽位定位 ==========

    /**
     * 重新定位所有样板相关槽位（crafting/processing/pattern IN/OUT）。
     * 必须在 initGui 和 updateScreen 中调用。
     */
    public void repositionSlots() {
        final int panelX = getPanelX();
        final int panelY = getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();

        for (final Object obj : host.getPanel().inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (slot instanceof SlotFakeCraftingMatrix) {
                    final int craftIdx = slot.getSlotIndex();
                    if (ct.isCraftingMode()) {
                        if (craftIdx >= CRAFTING_INPUT_SLOTS) {
                            slot.xPos = -9000;
                            slot.yPos = -9000;
                        } else {
                            final int gridX = craftIdx % CRAFTING_GRID_DIMENSION;
                            final int gridY = craftIdx / CRAFTING_GRID_DIMENSION;
                            slot.xPos = panelX + CRAFTING_GRID_OFFSET_X + gridX * 18;
                            slot.yPos = panelY + CRAFTING_GRID_OFFSET_Y + gridY * 18;
                        }
                    } else {
                        final boolean inverted = ct.isInverted();
                        final int processingGridOffsetX = inverted
                                ? PROCESSING_INVERTED_GRID_OFFSET_X
                                : PROCESSING_GRID_OFFSET_X;
                        final int pageStart = this.processingInputPage
                                * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
                        final int pageEnd = Math.min(pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS,
                                PatternHelper.PROCESSING_INPUT_LIMIT);
                        if (craftIdx < pageStart || craftIdx >= pageEnd) {
                            slot.xPos = -9000;
                            slot.yPos = -9000;
                        } else {
                            final int visibleIndex = craftIdx - pageStart;
                            final int gridX = visibleIndex % PROCESSING_INPUT_WIDTH;
                            final int gridY = visibleIndex / PROCESSING_INPUT_WIDTH;
                            slot.xPos = panelX + processingGridOffsetX + gridX * 18;
                            slot.yPos = panelY + PROCESSING_GRID_OFFSET_Y + gridY * 18;
                        }
                    }
                } else if (slot instanceof SlotPatternTerm) {
                    if (ct.isCraftingMode()) {
                        slot.xPos = panelX + CRAFTING_OUTPUT_OFFSET_X;
                        slot.yPos = panelY + 37;
                    } else {
                        slot.xPos = -9000;
                        slot.yPos = -9000;
                    }
                } else if (slot instanceof SlotPatternOutputs) {
                    if (ct.isCraftingMode()) {
                        slot.xPos = -9000;
                        slot.yPos = -9000;
                    } else {
                        final int processingOutputOffsetX = ct.isInverted()
                                ? PROCESSING_OUTPUT_INVERTED_OFFSET_X
                                : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
                        final int outIdx = slot.getSlotIndex();
                        final int outX = outIdx % PROCESSING_OUTPUT_COLUMNS;
                        final int outY = outIdx / PROCESSING_OUTPUT_COLUMNS;
                        slot.xPos = panelX + processingOutputOffsetX + outX * 18;
                        slot.yPos = panelY + PROCESSING_OUTPUT_OFFSET_Y + outY * 18;
                    }
                } else if (slot instanceof SlotRestrictedInput restrictedSlot) {
                    if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN) {
                        slot.xPos = panelX + PATTERN_IN_OFFSET_X;
                        slot.yPos = panelY + PATTERN_IN_OFFSET_Y;
                    } else if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        slot.xPos = panelX + PATTERN_OUT_OFFSET_X;
                        slot.yPos = panelY + PATTERN_OUT_OFFSET_Y;
                    } else {
                        // 玩家物品栏槽位 — 由宿主处理
                        slot.yPos = host.getYSize() + slot.getY() - 78 - 7;
                        slot.xPos = slot.getX() + 14;
                    }
                } else {
                    // 其他常规槽位
                    slot.yPos = host.getYSize() + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== 渲染: drawBG ==========

    /**
     * 绘制样板编码面板的背景。
     */
    public void drawBG(int offsetX, int offsetY) {
        final int panelX = offsetX + getPanelX();
        final int panelY = offsetY + getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        final AEBasePanel panel = host.getPanel();

        GlStateManager.color(1, 1, 1, 1);

        // 上半部分：制作模式=pattern3.png, 加工模式=pattern.png (正/反)
        if (ct.isCraftingMode()) {
            panel.mc.getTextureManager().bindTexture(PATTERN3_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else if (ct.isInverted()) {
            panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else {
            panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, PATTERN_PANEL_UPPER_HEIGHT,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        }

        // 下半部分：IN/OUT 槽位背景
        panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
        panel.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_UPPER_HEIGHT,
                133, 0, PATTERN_PANEL_LOWER_WIDTH, PATTERN_PANEL_LOWER_HEIGHT);
        panel.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_HEIGHT,
                173, 0, PATTERN_PANEL_FOOTER_WIDTH, PATTERN_PANEL_FOOTER_HEIGHT);

        // processing 模式滚动条
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        if (!ct.isCraftingMode()) {
            if (this.getTotalProcessingInputPages() > 1) {
                this.processingInputScrollbar.draw(panel);
            }
            if (ct.getTotalPages() > 1) {
                this.processingScrollBar.draw(panel);
            }
        }
        GlStateManager.popMatrix();
    }

    // ========== 渲染: drawFG ==========

    /**
     * 绘制样板编码面板的前景（标题文字和按钮可见性管理）。
     */
    public void drawFG() {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        final int panelX = getPanelX();
        final int panelY = getPanelY();

        host.getPanel().mc.fontRenderer.drawString(GuiText.PatternEncoding.getLocal(),
                panelX + 4, panelY + 4, 4210752);

        // 按钮可见性
        if (ct.isCraftingMode()) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.substitutionsEnabledBtn.visible = ct.isSubstitute();
            this.substitutionsDisabledBtn.visible = !ct.isSubstitute();
            this.beSubstitutionsEnabledBtn.visible = ct.isBeSubstitute();
            this.beSubstitutionsDisabledBtn.visible = !ct.isBeSubstitute();
            this.invertBtn.visible = false;
            this.combineEnabledBtn.visible = false;
            this.combineDisabledBtn.visible = false;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
            this.doubleBtn.visible = false;
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.beSubstitutionsEnabledBtn.visible = false;
            this.beSubstitutionsDisabledBtn.visible = false;
            this.invertBtn.visible = true;
            this.combineEnabledBtn.visible = ct.isCombine();
            this.combineDisabledBtn.visible = !ct.isCombine();
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.x2Btn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = true;
            this.plusOneBtn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
            this.minusOneBtn.visible = false;
            this.doubleBtn.visible = true;
        }
    }

    // ========== drawScreen: 按钮重建 ==========

    /**
     * 在 drawScreen 中调用，更新按钮位置和添加到 buttonList。
     * 在 buttonList.clear() 之后调用。
     */
    public void populateButtons() {
        final List<GuiButton> buttonList = host.getButtonList();

        this.updatePatternControlPositions();

        addIfNotNull(buttonList, this.encodeBtn);
        addIfNotNull(buttonList, this.clearBtn);
        addIfNotNull(buttonList, this.tabCraftButton);
        addIfNotNull(buttonList, this.tabProcessButton);
        addIfNotNull(buttonList, this.substitutionsEnabledBtn);
        addIfNotNull(buttonList, this.substitutionsDisabledBtn);
        addIfNotNull(buttonList, this.beSubstitutionsEnabledBtn);
        addIfNotNull(buttonList, this.beSubstitutionsDisabledBtn);
        addIfNotNull(buttonList, this.invertBtn);
        addIfNotNull(buttonList, this.combineEnabledBtn);
        addIfNotNull(buttonList, this.combineDisabledBtn);
        addIfNotNull(buttonList, this.x2Btn);
        addIfNotNull(buttonList, this.x3Btn);
        addIfNotNull(buttonList, this.plusOneBtn);
        addIfNotNull(buttonList, this.divTwoBtn);
        addIfNotNull(buttonList, this.divThreeBtn);
        addIfNotNull(buttonList, this.minusOneBtn);
        addIfNotNull(buttonList, this.doubleBtn);
    }

    private static void addIfNotNull(List<GuiButton> list, GuiButton btn) {
        if (btn != null) {
            list.add(btn);
        }
    }

    // ========== 按钮位置更新 ==========

    private void updatePatternControlPositions() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();

        setButtonPos(this.encodeBtn, panelScreenX + 11, panelScreenY + 118);
        setButtonPos(this.tabCraftButton, panelScreenX + 39, panelScreenY + 93);
        setButtonPos(this.tabProcessButton, panelScreenX + 39, panelScreenY + 93);

        if (ct.isCraftingMode()) {
            setButtonPos(this.clearBtn, panelScreenX + 72, panelScreenY + 14);
            setButtonPos(this.substitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 14);
            setButtonPos(this.substitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 14);
            setButtonPos(this.beSubstitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 24);
            setButtonPos(this.beSubstitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 24);
            return;
        }

        final int offset = ct.isInverted() ? -3 * 18 : 0;
        setButtonPos(this.clearBtn, panelScreenX + 87 + offset, panelScreenY + 10);
        setButtonPos(this.substitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        setButtonPos(this.substitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        setButtonPos(this.beSubstitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        setButtonPos(this.beSubstitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        setButtonPos(this.invertBtn, panelScreenX + 87 + offset, panelScreenY + 20);
        setButtonPos(this.combineEnabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);
        setButtonPos(this.combineDisabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);

        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38 + offset;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28 + offset;
        setButtonPos(this.x3Btn, adjBtnX1, panelScreenY + 6);
        setButtonPos(this.x2Btn, adjBtnX1, panelScreenY + 16);
        setButtonPos(this.plusOneBtn, adjBtnX1, panelScreenY + 26);
        setButtonPos(this.divThreeBtn, adjBtnX2, panelScreenY + 6);
        setButtonPos(this.divTwoBtn, adjBtnX2, panelScreenY + 16);
        setButtonPos(this.minusOneBtn, adjBtnX2, panelScreenY + 26);
        setButtonPos(this.doubleBtn, adjBtnX2, panelScreenY + 36);
    }

    private static void setButtonPos(GuiButton button, int x, int y) {
        if (button != null) {
            button.x = x;
            button.y = y;
        }
    }

    // ========== 输入处理: actionPerformed ==========

    /**
     * 处理按钮点击。
     *
     * @return true 如果事件被消费
     */
    public boolean actionPerformed(GuiButton btn) {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();

        try {
            if (btn == this.tabCraftButton) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "0"));
            } else if (btn == this.tabProcessButton) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode", "1"));
            } else if (btn == this.encodeBtn) {
                final int value = (AEBasePanel.isCtrlKeyDown() ? 1 : 0) << 1
                        | (AEBasePanel.isShiftKeyDown() ? 1 : 0);
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Encode", String.valueOf(value)));
                // Alt + 编码 → PlacePattern
                if (value == 0 && AEBasePanel.isAltKeyDown()) {
                    this.pendingPlacePattern = true;
                }
            } else if (btn == this.clearBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            } else if (btn == this.substitutionsEnabledBtn || btn == this.substitutionsDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Substitute",
                        this.substitutionsEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.beSubstitutionsEnabledBtn || btn == this.beSubstitutionsDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.beSubstitute",
                        this.beSubstitutionsEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.invertBtn) {
                final boolean newInverted = !ct.isInverted();
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.Invert", newInverted ? "1" : "0"));
            } else if (btn == this.combineEnabledBtn || btn == this.combineDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Combine",
                        this.combineEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.x2Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByTwo"
                                        : "PatternTerminal.MultiplyByTwo",
                                "1"));
            } else if (btn == this.x3Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByThree"
                                        : "PatternTerminal.MultiplyByThree",
                                "1"));
            } else if (btn == this.divTwoBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByTwo", "1"));
            } else if (btn == this.divThreeBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByThree", "1"));
            } else if (btn == this.plusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig(
                                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DecreaseByOne"
                                        : "PatternTerminal.IncreaseByOne",
                                "1"));
            } else if (btn == this.minusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DecreaseByOne", "1"));
            } else if (btn == this.doubleBtn) {
                final boolean backwards = Mouse.isButtonDown(1);
                int val = AEBasePanel.isShiftKeyDown() ? 1 : 0;
                if (backwards) {
                    val |= 0b10;
                }
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Double", String.valueOf(val)));
            } else {
                return false;
            }
        } catch (IOException e) {
            // ignore
        }
        return true;
    }

    // ========== 输入处理: mouseWheel ==========

    /**
     * 处理鼠标滚轮（processing 模式输入/输出区域）。
     *
     * @return true 如果事件被消费
     */
    public boolean mouseWheelEvent(int x, int y, int wheel) {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        if (ct.isCraftingMode()) {
            return false;
        }

        final int patPanelAbsX = host.getGuiLeft() + getPanelX();
        final int patPanelAbsY = host.getGuiTop() + getPanelY();
        if (x >= patPanelAbsX && x < patPanelAbsX + PATTERN_PANEL_WIDTH
                && y >= patPanelAbsY && y < patPanelAbsY + PATTERN_PANEL_UPPER_HEIGHT) {
            if (this.isMouseOverProcessingInputArea(x, y) && this.getTotalProcessingInputPages() > 1) {
                this.updateProcessingInputScrollbar();
                final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
                this.processingInputScrollbar.wheel(wheel);
                if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
                    this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
                    return true;
                }
            }

            if (this.isMouseOverProcessingOutputArea(x, y) && ct.getTotalPages() > 1) {
                this.processingScrollBar.wheel(wheel);
                this.sendActivePageUpdate();
                return true;
            }
        }
        return false;
    }

    // ========== 输入处理: 鼠标点击（滚动条拖动） ==========

    /**
     * 尝试处理 processing 模式滚动条的鼠标拖动。
     *
     * @return true 如果事件被消费
     */
    public boolean handleScrollbarClick(int mouseX, int mouseY) {
        return this.updatePatternInputScrollFromMouse(mouseX, mouseY)
                || this.updatePatternOutputScrollFromMouse(mouseX, mouseY);
    }

    // ========== updateScreen ==========

    /**
     * 每 tick 更新。在 updateScreen 中调用。
     */
    public void updateScreen() {
        this.updateProcessingInputScrollbar();
        this.repositionSlots();
        this.updateProcessingScrollbar();

        // PlacePattern: 编码完成后自动放入接口空位
        if (this.pendingPlacePattern) {
            this.pendingPlacePattern = false;
            final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
            if (ct.getPatternSlotOUT() != null && ct.getPatternSlotOUT().getHasStack()) {
                this.tryPlacePatternToHighlightedInterface();
            }
        }
    }

    // ========== PlacePattern ==========

    private void tryPlacePatternToHighlightedInterface() {
        final InterfaceListModule listModule = host.getInterfaceListModule();
        for (final ClientDCInternalInv inv : listModule.getById().values()) {
            final int slotLimit = inv.getInventory().getSlots();
            final int extraLines = listModule.getNumUpgradesMap().getOrDefault(inv, 0);
            final int maxSlots = Math.min(slotLimit, 9 * (1 + extraLines));

            for (int i = 0; i < maxSlots; i++) {
                if (inv.getInventory().getStackInSlot(i).isEmpty()) {
                    try {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig(
                                "PatternTerminal.PlacePattern",
                                inv.getId() + "," + i));
                    } catch (IOException e) {
                        // ignore
                    }
                    return;
                }
            }
        }
    }

    // ========== 滚动条管理 ==========

    private void sendActivePageUpdate() {
        final int newPage = this.processingScrollBar.getCurrentScroll();
        host.getDualContainer().setActivePage(newPage);
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("PatternTerminal.ActivePage", String.valueOf(newPage)));
        } catch (IOException e) {
            // ignore
        }
    }

    private int getProcessingGridOffsetX() {
        return host.getDualContainer().isInverted()
                ? PROCESSING_INVERTED_GRID_OFFSET_X : PROCESSING_GRID_OFFSET_X;
    }

    private int getProcessingOutputOffsetX() {
        return host.getDualContainer().isInverted()
                ? PROCESSING_OUTPUT_INVERTED_OFFSET_X : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
    }

    public void updateProcessingScrollbar() {
        final int panelRelX = getPanelX();
        final int panelRelY = getPanelY();
        this.processingScrollBar.setLeft(panelRelX + this.getProcessingOutputOffsetX()
                + PROCESSING_OUTPUT_COLUMNS * 18)
                .setTop(panelRelY + PROCESSING_OUTPUT_OFFSET_Y)
                .setHeight(PROCESSING_OUTPUT_ROWS * 18 - 2);

        final ContainerWirelessDualInterfaceTerminal container = host.getDualContainer();
        final int totalPages = container.getTotalPages();
        this.processingScrollBar.setRange(0, Math.max(0, totalPages - 1), 1);
        this.processingScrollBar.setCurrentScroll(container.getActivePage());
    }

    private int getTotalProcessingInputPages() {
        return Math.max(1, (PatternHelper.PROCESSING_INPUT_LIMIT
                + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS - 1)
                / PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
    }

    public void updateProcessingInputScrollbar() {
        final int panelRelX = getPanelX();
        final int panelRelY = getPanelY();
        this.processingInputPage = Math.min(this.processingInputPage,
                this.getTotalProcessingInputPages() - 1);
        this.processingInputScrollbar.setLeft(panelRelX + this.getProcessingGridOffsetX()
                + PROCESSING_INPUT_WIDTH * 18 + 4)
                .setTop(panelRelY + PROCESSING_GRID_OFFSET_Y)
                .setHeight(PROCESSING_INPUT_ROWS * 18 - 2);
        this.processingInputScrollbar.setRange(0,
                Math.max(0, this.getTotalProcessingInputPages() - 1), 1);
        this.processingInputScrollbar.setCurrentScroll(this.processingInputPage);
    }

    private boolean updatePatternInputScrollFromMouse(final int mouseX, final int mouseY) {
        if (host.getDualContainer().isCraftingMode() || this.getTotalProcessingInputPages() <= 1) {
            return false;
        }

        this.updateProcessingInputScrollbar();
        final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
        this.processingInputScrollbar.click(host.getPanel(), mouseX - host.getGuiLeft(),
                mouseY - host.getGuiTop());
        if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
            this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
            return true;
        }
        return false;
    }

    private boolean updatePatternOutputScrollFromMouse(final int mouseX, final int mouseY) {
        if (host.getDualContainer().isCraftingMode() || host.getDualContainer().getTotalPages() <= 1) {
            return false;
        }

        final int oldScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.click(host.getPanel(), mouseX - host.getGuiLeft(),
                mouseY - host.getGuiTop());
        if (oldScroll != this.processingScrollBar.getCurrentScroll()) {
            this.sendActivePageUpdate();
            return true;
        }
        return false;
    }

    private void setProcessingInputPage(final int page) {
        this.processingInputPage = Math.max(0,
                Math.min(page, this.getTotalProcessingInputPages() - 1));
        this.repositionSlots();
    }

    // ========== 区域检测 ==========

    private boolean isMouseOverProcessingInputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = host.getGuiLeft() + getPanelX();
        final int panelAbsY = host.getGuiTop() + getPanelY();
        final int left = panelAbsX + this.getProcessingGridOffsetX();
        final int top = panelAbsY + PROCESSING_GRID_OFFSET_Y;
        final int right = left + PROCESSING_INPUT_WIDTH * 18 + 4 + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isMouseOverProcessingOutputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = host.getGuiLeft() + getPanelX();
        final int panelAbsY = host.getGuiTop() + getPanelY();
        final int left = panelAbsX + this.getProcessingOutputOffsetX();
        final int top = panelAbsY + PROCESSING_OUTPUT_OFFSET_Y;
        final int right = left + PROCESSING_OUTPUT_COLUMNS * 18;
        final int bottom = top + PROCESSING_OUTPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    /**
     * 获取面板的绝对屏幕坐标（JEI 排除区用）。
     */
    public java.awt.Rectangle getJEIExclusionRect() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();
        return new java.awt.Rectangle(panelScreenX, panelScreenY,
                PATTERN_PANEL_WIDTH, PATTERN_PANEL_TOTAL_HEIGHT);
    }

    // ========== 面板拖拽 ==========

    /**
     * 面板拖拽状态管理器。
     * 支持中键拖拽来调整面板位置。
     */
    public static class PanelDragState {

        private int dragOffsetX = 0;
        private int dragOffsetY = 0;

        private boolean dragging = false;

        private int dragStartMouseX;
        private int dragStartMouseY;
        private int dragStartOffsetX;
        private int dragStartOffsetY;

        private final BiPredicate<Integer, Integer> dragAreaChecker;

        public PanelDragState(BiPredicate<Integer, Integer> dragAreaChecker) {
            this.dragAreaChecker = dragAreaChecker;
        }

        public boolean isInDragArea(int mouseX, int mouseY) {
            return dragAreaChecker.test(mouseX, mouseY);
        }

        public void startDrag(int mouseX, int mouseY) {
            this.dragging = true;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartOffsetX = this.dragOffsetX;
            this.dragStartOffsetY = this.dragOffsetY;
        }

        public void updateDrag(int mouseX, int mouseY) {
            this.dragOffsetX = this.dragStartOffsetX + (mouseX - this.dragStartMouseX);
            this.dragOffsetY = this.dragStartOffsetY + (mouseY - this.dragStartMouseY);
        }

        public void endDrag() {
            this.dragging = false;
        }

        public int getDragOffsetX() {
            return dragOffsetX;
        }

        public int getDragOffsetY() {
            return dragOffsetY;
        }

        public boolean isDragging() {
            return dragging;
        }
    }
}

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
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.Integrations;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * 无线二合一接口终端的GUI
 *
 * 以 AE2Things 的二合一接口终端为参考，布局如下：
 * - 中间：接口列表面板 + 玩家背包（继承自 GuiInterfaceTerminal）
 * - 左侧：ME物品网格（显示AE网络中的物品，带搜索框和滚动条）
 * - 右侧：样板编写区域（3x3输入网格 + 输出槽 + 空白/编码样板槽 + 数量调整按钮）
 */
public class GuiWirelessDualInterfaceTerminal extends GuiInterfaceTerminal
        implements ContainerWirelessDualInterfaceTerminal.IMEInventoryUpdateReceiver,
        ISortSource, IConfigManagerHost {

    // ========== 样板编写面板常量 ==========
    /** 样板面板距主界面右边的间距 */
    private static final int PATTERN_PANEL_GAP = 2;
    /** 样板面板的宽度 */
    private static final int PATTERN_PANEL_WIDTH = 100;
    /** 样板面板的高度 */
    private static final int PATTERN_PANEL_HEIGHT = 120;

    /** 样板面板内3x3网格的起始偏移（相对于面板左上角） */
    private static final int GRID_OFFSET_X = 6;
    private static final int GRID_OFFSET_Y = 6;

    /** 样板面板内输出槽的起始偏移（相对于面板左上角） */
    private static final int OUTPUT_OFFSET_X = 66;
    private static final int OUTPUT_OFFSET_Y = 6;

    /** 样板面板内空白样板输入槽的偏移 */
    private static final int PATTERN_IN_OFFSET_X = 6;
    private static final int PATTERN_IN_OFFSET_Y = 71;
    /** 样板面板内编码样板输出槽的偏移 */
    private static final int PATTERN_OUT_OFFSET_X = 46;
    private static final int PATTERN_OUT_OFFSET_Y = 71;

    // ========== ME物品面板常量（参考 AE2Things: 4x4 网格, 101宽度, 96高度, 左下角对齐） ==========
    /** ME物品面板的宽度（与 AE2Things 的 101 一致） */
    private static final int ITEM_PANEL_WIDTH = 101;
    /** ME物品面板每列显示的行数 */
    private static final int ITEM_PANEL_ROWS = 4;
    /** ME物品面板每行显示的列数 */
    private static final int ITEM_PANEL_COLS = 4;
    /** ME物品面板内网格相对面板左上角的X偏移 */
    private static final int ITEM_GRID_OFFSET_X = 5;
    /** ME物品面板内网格相对面板左上角的Y偏移（搜索框下方） */
    private static final int ITEM_GRID_OFFSET_Y = 18;
    /** ME物品面板总高度（与 AE2Things 的 96 一致） */
    private static final int ITEM_PANEL_HEIGHT = 96;

    /** 搜索框记忆文本 */
    private static String memoryText = "";

    // ========== 样板面板按钮 ==========
    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;

    // ========== 数量调整按钮（处理模式下显示，与 GuiPatternTerm 一致） ==========
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;

    // ========== Crafting Status 按钮 ==========
    private GuiTabButton craftingStatusBtn;

    // ========== ME物品面板排序/过滤按钮（参考 GuiMEMonitorable） ==========
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton ViewBox;
    private GuiImgButton searchBoxSettings;

    // ========== ME物品面板数据 ==========
    private final ItemRepo itemRepo;
    private final GuiScrollbar itemPanelScrollbar;
    private MEGuiTextField itemSearchField;

    private final IConfigManager configSrc;
    private final WirelessTerminalGuiObject wirelessGuiObject;

    // ========== 面板拖拽状态 ==========
    /** 左侧面板拖拽偏移 */
    private int itemPanelDragOffsetX = 0;
    private int itemPanelDragOffsetY = 0;
    private boolean itemPanelDragging = false;
    private int itemPanelDragStartMouseX;
    private int itemPanelDragStartMouseY;
    private int itemPanelDragStartOffsetX;
    private int itemPanelDragStartOffsetY;

    /** 右侧面板拖拽偏移 */
    private int patternPanelDragOffsetX = 0;
    private int patternPanelDragOffsetY = 0;
    private boolean patternPanelDragging = false;
    private int patternPanelDragStartMouseX;
    private int patternPanelDragStartMouseY;
    private int patternPanelDragStartOffsetX;
    private int patternPanelDragStartOffsetY;

    public GuiWirelessDualInterfaceTerminal(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(new ContainerWirelessDualInterfaceTerminal(inventoryPlayer, te),
                GuiText.WirelessTerminal);
        this.wirelessGuiObject = te;

        // 从 Container 获取配置管理器用于排序/视图设置
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();

        // 初始化左侧 ME 物品面板的滚动条和 ItemRepo
        this.itemPanelScrollbar = new GuiScrollbar();
        this.itemRepo = new ItemRepo(this.itemPanelScrollbar, this);
        this.itemRepo.setRowSize(ITEM_PANEL_COLS);

        // 注册自身为 ME 库存更新接收者
        getDualContainer().setMeGui(this);
    }

    private ContainerWirelessDualInterfaceTerminal getDualContainer() {
        return (ContainerWirelessDualInterfaceTerminal) this.inventorySlots;
    }

    // ========== IMEInventoryUpdateReceiver 接口实现 ==========

    /**
     * 接收来自服务端的 ME 网络库存更新，转发到 ItemRepo
     */
    @Override
    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.itemRepo.postUpdate(is);
        }
        this.itemRepo.updateView();
        this.updateItemPanelScrollbar();
    }

    // ========== 面板坐标计算辅助方法 ==========

    /**
     * 获取样板面板的起始X坐标（相对于guiLeft）
     */
    private int getPatternPanelX() {
        return this.xSize + PATTERN_PANEL_GAP + this.patternPanelDragOffsetX;
    }

    /**
     * 获取样板面板的起始Y坐标偏移（相对于guiTop）
     */
    private int getPatternPanelY() {
        return this.patternPanelDragOffsetY;
    }

    /**
     * 获取ME物品面板的绝对X坐标（屏幕坐标）
     * 参考 AE2Things: absX = guiLeft - 101
     */
    private int getItemPanelAbsX() {
        return this.guiLeft - ITEM_PANEL_WIDTH + this.itemPanelDragOffsetX;
    }

    /**
     * 获取ME物品面板的绝对Y坐标（屏幕坐标）
     * 参考 AE2Things: absY = guiTop + ySize - 96（左下角对齐）
     */
    private int getItemPanelAbsY() {
        return this.guiTop + this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragOffsetY;
    }

    /**
     * 获取ME物品面板的起始X坐标（相对于guiLeft，用于虚拟槽位定位）
     */
    private int getItemPanelRelX() {
        return -ITEM_PANEL_WIDTH + this.itemPanelDragOffsetX;
    }

    /**
     * 获取ME物品面板的起始Y坐标（相对于guiTop，用于虚拟槽位定位）
     */
    private int getItemPanelRelY() {
        return this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragOffsetY;
    }

    // ========== 覆写槽位重定位 ==========

    /**
     * 重定位所有槽位。
     * 玩家背包槽位和无线终端升级槽位使用标准的接口终端定位逻辑；
     * 样板编写相关的槽位使用右侧面板的绝对坐标。
     */
    @Override
    protected void repositionSlots() {
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();

        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (slot instanceof SlotFakeCraftingMatrix) {
                    // 3x3合成网格槽位
                    final int craftIdx = slot.getSlotIndex();
                    final int gridX = craftIdx % 3;
                    final int gridY = craftIdx / 3;
                    slot.xPos = panelX + GRID_OFFSET_X + gridX * 18;
                    slot.yPos = panelY + GRID_OFFSET_Y + gridY * 18;
                } else if (slot instanceof SlotPatternTerm) {
                    // 样板编码输出槽（合成模式下的结果槽）
                    slot.xPos = panelX + OUTPUT_OFFSET_X;
                    slot.yPos = panelY + OUTPUT_OFFSET_Y + 18;
                } else if (slot instanceof SlotPatternOutputs) {
                    // 处理模式的输出槽
                    final int outIdx = slot.getSlotIndex();
                    slot.xPos = panelX + OUTPUT_OFFSET_X;
                    slot.yPos = panelY + OUTPUT_OFFSET_Y + outIdx * 18;
                } else if (slot instanceof SlotRestrictedInput restrictedSlot) {
                    // 区分空白样板输入槽和编码样板输出槽
                    if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN) {
                        slot.xPos = panelX + PATTERN_IN_OFFSET_X;
                        slot.yPos = panelY + PATTERN_IN_OFFSET_Y;
                    } else if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        slot.xPos = panelX + PATTERN_OUT_OFFSET_X;
                        slot.yPos = panelY + PATTERN_OUT_OFFSET_Y;
                    } else {
                        // 其他 SlotRestrictedInput（如无线终端升级槽），使用标准逻辑
                        slot.yPos = this.ySize + slot.getY() - 78 - 7;
                        slot.xPos = slot.getX() + 14;
                    }
                } else {
                    // 玩家背包槽位和其他标准槽位，使用接口终端的标准逻辑
                    slot.yPos = this.ySize + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== ME物品面板滚动条更新 ==========

    private void updateItemPanelScrollbar() {
        this.itemPanelScrollbar.setRange(0,
                (this.itemRepo.size() + ITEM_PANEL_COLS - 1) / ITEM_PANEL_COLS - ITEM_PANEL_ROWS,
                Math.max(1, ITEM_PANEL_ROWS / 6));
    }

    // ========== GUI 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        // ===== Crafting Status 按钮（主界面右上角，与 GuiMEMonitorable 一致） =====
        this.craftingStatusBtn = new GuiTabButton(this.guiLeft + 170, this.guiTop - 4,
                2 + 11 * 16, GuiText.CraftingStatus.getLocal(), this.itemRender);
        this.craftingStatusBtn.setHideEdge(13);
        this.buttonList.add(this.craftingStatusBtn);

        // ===== 样板面板按钮 =====
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();

        // 编码按钮
        this.encodeBtn = new GuiImgButton(panelScreenX + PATTERN_PANEL_WIDTH - 20, panelScreenY + 95,
                Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        // 清除按钮（半尺寸，与 GuiPatternTerm 一致）
        this.clearBtn = new GuiImgButton(panelScreenX + 6, panelScreenY + 95,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 合成/处理模式切换标签
        this.tabCraftButton = new GuiTabButton(panelScreenX + 44, panelScreenY + 95,
                new ItemStack(Blocks.CRAFTING_TABLE),
                GuiText.CraftingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(panelScreenX + 44, panelScreenY + 95,
                new ItemStack(Blocks.FURNACE),
                GuiText.ProcessingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // 替代品开关按钮（使用 ItemSubstitution 枚举图标，半尺寸，与 GuiPatternTerm 一致）
        this.substitutionsEnabledBtn = new GuiImgButton(panelScreenX + 16, panelScreenY + 95,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(panelScreenX + 16, panelScreenY + 95,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        // ===== 数量调整按钮（处理模式下显示，放在输出槽右侧） =====
        final int adjBtnX1 = panelScreenX + OUTPUT_OFFSET_X + 22;
        final int adjBtnX2 = panelScreenX + OUTPUT_OFFSET_X + 12;

        // x3 按钮
        this.x3Btn = new GuiImgButton(adjBtnX1, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        // x2 按钮
        this.x2Btn = new GuiImgButton(adjBtnX1, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        // +1 按钮
        this.plusOneBtn = new GuiImgButton(adjBtnX1, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        // ÷3 按钮
        this.divThreeBtn = new GuiImgButton(adjBtnX2, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        // ÷2 按钮
        this.divTwoBtn = new GuiImgButton(adjBtnX2, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        // -1 按钮
        this.minusOneBtn = new GuiImgButton(adjBtnX2, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        // ===== ME物品面板 =====
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        final int itemRelX = getItemPanelRelX();
        final int itemRelY = getItemPanelRelY();

        // ===== ME物品面板排序/过滤按钮（面板左侧 -18 的位置，参考 AE2Things） =====
        int sortBtnOffset = itemAbsY + 18;

        this.SortByBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_BY,
                this.configSrc.getSetting(Settings.SORT_BY));
        this.buttonList.add(this.SortByBox);
        sortBtnOffset += 20;

        this.ViewBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.VIEW_MODE,
                this.configSrc.getSetting(Settings.VIEW_MODE));
        this.buttonList.add(this.ViewBox);
        sortBtnOffset += 20;

        this.SortDirBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_DIRECTION,
                this.configSrc.getSetting(Settings.SORT_DIRECTION));
        this.buttonList.add(this.SortDirBox);
        sortBtnOffset += 20;

        this.searchBoxSettings = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        this.buttonList.add(this.searchBoxSettings);

        // 搜索框（面板顶部，参考 AE2Things: absX+3, absY+4, 72x12）
        this.itemSearchField = new MEGuiTextField(this.fontRenderer,
                itemAbsX + 3, itemAbsY + 4, 72, 12);
        this.itemSearchField.setEnableBackgroundDrawing(false);
        this.itemSearchField.setMaxStringLength(25);
        this.itemSearchField.setTextColor(0xFFFFFF);
        this.itemSearchField.setVisible(true);

        // SearchBoxMode 逻辑：根据设置决定是否从 JEI 同步搜索文本
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        if (isJEIEnabled && Platform.isJEIEnabled()) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (!memoryText.isEmpty()) {
            this.itemSearchField.setText(memoryText);
            this.itemRepo.setSearchString(memoryText);
        }

        // 移除旧的虚拟 ME 槽位
        this.guiSlots.removeIf(s -> s instanceof VirtualMEMonitorableSlot);

        // 创建 4x4 虚拟 ME 槽位
        for (int row = 0; row < ITEM_PANEL_ROWS; row++) {
            for (int col = 0; col < ITEM_PANEL_COLS; col++) {
                final int slotIdx = col + row * ITEM_PANEL_COLS;
                final int slotX = itemRelX + ITEM_GRID_OFFSET_X + col * 18;
                final int slotY = itemRelY + ITEM_GRID_OFFSET_Y + row * 18;
                this.guiSlots.add(new VirtualMEMonitorableSlot(
                        slotIdx, slotX, slotY, this.itemRepo, slotIdx));
            }
        }

        // 设置ME物品面板滚动条位置（相对于 guiLeft/guiTop）
        this.itemPanelScrollbar.setLeft(itemRelX + ITEM_PANEL_WIDTH - 14)
                .setTop(itemRelY + ITEM_GRID_OFFSET_Y)
                .setHeight(ITEM_PANEL_ROWS * 18 - 2);
        this.updateItemPanelScrollbar();

        // 设置 ItemRepo 的 power 状态
        this.itemRepo.setPower(true);
    }

    // ========== 按钮处理 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        // Crafting Status 按钮
        if (btn == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
            return;
        }

        // ===== ME 物品面板排序/过滤按钮 =====
        if (btn instanceof GuiImgButton iBtn && iBtn.getSetting() != Settings.ACTIONS) {
            final boolean backwards = Mouse.isButtonDown(1);
            final Enum cv = iBtn.getCurrentValue();
            final Enum next = Platform.rotateEnum(cv, backwards, iBtn.getSetting().getPossibleValues());

            if (btn == this.searchBoxSettings) {
                AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
            } else {
                try {
                    NetworkHandler.instance()
                            .sendToServer(new PacketValueConfig(iBtn.getSetting().name(), next.name()));
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }

            iBtn.set(next);

            if (next.getClass() == SearchBoxMode.class) {
                this.reinitialize();
            }
            return;
        }

        try {
            if (btn == this.tabCraftButton || btn == this.tabProcessButton) {
                // 切换合成/处理模式
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode",
                        ct.isCraftingMode() ? "0" : "1"));
            } else if (btn == this.encodeBtn) {
                // 编码样板：Ctrl+Shift 组合处理（参考 AE2Things: (ctrl?1:0)<<1|(shift?1:0)）
                final int value = (isCtrlKeyDown() ? 1 : 0) << 1 | (isShiftKeyDown() ? 1 : 0);
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Encode", String.valueOf(value)));
            } else if (btn == this.clearBtn) {
                // 清除
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            } else if (btn == this.substitutionsEnabledBtn || btn == this.substitutionsDisabledBtn) {
                // 切换替代品
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Substitute",
                        this.substitutionsEnabledBtn == btn ? "0" : "1"));
            } else if (btn == this.x2Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.MultiplyByTwo", "1"));
            } else if (btn == this.x3Btn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.MultiplyByThree", "1"));
            } else if (btn == this.divTwoBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByTwo", "1"));
            } else if (btn == this.divThreeBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DivideByThree", "1"));
            } else if (btn == this.plusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.IncreaseByOne", "1"));
            } else if (btn == this.minusOneBtn) {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.DecreaseByOne", "1"));
            } else {
                // 调用父类处理接口终端的按钮
                super.actionPerformed(btn);
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // ========== 键盘输入处理 ==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        // TAB 键处理：
        // 1. 先将 Names 搜索框的建议文本（suggestion）确认为正式文本（与 AE2Things 一致）
        // 2. 然后进行焦点切换
        if (key == org.lwjgl.input.Keyboard.KEY_TAB) {
            this.searchFieldNames.setSuggestionToText();
        }
        if (character == '\t') {
            if (this.handleTab()) {
                return;
            }
        }

        // ME 搜索框聚焦时，禁止空格作为第一个字符
        if (character == ' ' && this.itemSearchField != null
                && this.itemSearchField.isFocused()
                && this.itemSearchField.getText().isEmpty()) {
            return;
        }

        // 尝试让 ME 搜索框处理键盘输入
        if (this.itemSearchField != null && this.itemSearchField.isFocused()
                && this.itemSearchField.textboxKeyTyped(character, key)) {
            final String searchText = this.itemSearchField.getText();
            this.itemRepo.setSearchString(searchText);
            this.itemRepo.updateView();
            this.updateItemPanelScrollbar();
            // 根据 SearchBoxMode 同步搜索文本到 JEI
            final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                    || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
            if (isJEISync && Platform.isJEIEnabled()) {
                Integrations.jei().setSearchText(searchText);
            }
            return;
        }
        super.keyTyped(character, key);
    }

    /**
     * 覆写父类的 TAB 焦点切换逻辑，将 ME 物品搜索框加入循环。
     * 焦点循环顺序：Inputs → Outputs → Names → ME物品搜索 → Inputs...
     * Shift 反向：Inputs → ME物品搜索 → Names → Outputs → Inputs...
     */
    @Override
    protected boolean handleTab() {
        if (this.itemSearchField != null && this.itemSearchField.isFocused()) {
            // 从 ME 搜索框切出
            this.itemSearchField.setFocused(false);
            if (isShiftKeyDown()) {
                this.searchFieldNames.setFocused(true);
            } else {
                this.searchFieldInputs.setFocused(true);
            }
            return true;
        }
        if (this.searchFieldNames.isFocused()) {
            // Names → 下一个是 ME 搜索框（正向），或 Outputs（反向）
            this.searchFieldNames.setFocused(false);
            if (isShiftKeyDown()) {
                this.searchFieldOutputs.setFocused(true);
            } else if (this.itemSearchField != null) {
                this.itemSearchField.setFocused(true);
            } else {
                this.searchFieldInputs.setFocused(true);
            }
            return true;
        }
        // 对于 Inputs 和 Outputs，调用父类逻辑
        // 但需要特殊处理 Inputs 的反向（Shift+TAB 应到 ME 搜索框）
        if (this.searchFieldInputs.isFocused() && isShiftKeyDown()) {
            this.searchFieldInputs.setFocused(false);
            if (this.itemSearchField != null) {
                this.itemSearchField.setFocused(true);
            } else {
                this.searchFieldNames.setFocused(true);
            }
            return true;
        }
        // 其他情况交给父类处理（Inputs正向→Outputs, Outputs正向→Names, Outputs反向→Inputs）
        return super.handleTab();
    }

    // ========== 滚轮事件处理 ==========

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        // 检查鼠标是否在左侧ME物品面板区域内
        final int panelX = getItemPanelAbsX();
        final int panelY = getItemPanelAbsY();

        if (x >= panelX && x < panelX + ITEM_PANEL_WIDTH
                && y >= panelY && y < panelY + ITEM_PANEL_HEIGHT) {
            this.itemPanelScrollbar.wheel(wheel);
            this.itemRepo.updateView();
            return;
        }

        // 其他区域交由父类处理
        super.mouseWheelEvent(x, y, wheel);
    }

    // ========== GUI 关闭时保存搜索框文本并同步到 JEI ==========

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (this.itemSearchField != null) {
            memoryText = this.itemSearchField.getText();
            // 根据 SearchBoxMode 同步搜索文本到 JEI
            final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                    || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
            if (isJEISync && Platform.isJEIEnabled()) {
                Integrations.jei().setSearchText(memoryText);
            }
        }
    }

    // ========== 绘制 ==========

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 绘制接口终端主体（中间部分）
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        // 绘制无线终端升级槽背景
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + 198, offsetY + 127, 0, 0, 32, 32, 32, 32);

        // 绘制右侧样板编写面板背景
        drawPatternPanelBG(offsetX, offsetY);

        // 绘制左侧ME物品面板背景
        drawItemPanelBG(offsetX, offsetY);

        // 绘制左侧ME物品面板滚动条（需要在 guiLeft/guiTop 的相对坐标系中绘制）
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        this.itemPanelScrollbar.draw(this);
        GlStateManager.popMatrix();

        // 绘制搜索框
        if (this.itemSearchField != null) {
            this.itemSearchField.drawTextBox();
        }
    }

    /**
     * 绘制右侧样板编写面板的背景
     */
    private void drawPatternPanelBG(int offsetX, int offsetY) {
        final int panelX = offsetX + getPatternPanelX();
        final int panelY = offsetY + getPatternPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        GlStateManager.color(1, 1, 1, 1);

        // 绘制面板背景
        Gui.drawRect(panelX, panelY, panelX + PATTERN_PANEL_WIDTH, panelY + PATTERN_PANEL_HEIGHT, 0xFFC6C6C6);
        // 绘制面板边框
        Gui.drawRect(panelX, panelY, panelX + PATTERN_PANEL_WIDTH, panelY + 1, 0xFF555555);
        Gui.drawRect(panelX, panelY + PATTERN_PANEL_HEIGHT - 1, panelX + PATTERN_PANEL_WIDTH,
                panelY + PATTERN_PANEL_HEIGHT, 0xFF555555);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + PATTERN_PANEL_HEIGHT, 0xFF555555);
        Gui.drawRect(panelX + PATTERN_PANEL_WIDTH - 1, panelY, panelX + PATTERN_PANEL_WIDTH,
                panelY + PATTERN_PANEL_HEIGHT, 0xFF555555);

        // 绘制3x3合成网格的槽位背景
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                final int slotX = panelX + GRID_OFFSET_X - 1 + x * 18;
                final int slotY = panelY + GRID_OFFSET_Y - 1 + y * 18;
                Gui.drawRect(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
                Gui.drawRect(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFFC6C6C6);
            }
        }

        if (ct.isCraftingMode()) {
            // 合成模式：绘制单个输出槽
            final int outSlotX = panelX + OUTPUT_OFFSET_X - 1;
            final int outSlotY = panelY + OUTPUT_OFFSET_Y + 18 - 1;
            Gui.drawRect(outSlotX, outSlotY, outSlotX + 26, outSlotY + 26, 0xFF8B8B8B);
            Gui.drawRect(outSlotX + 1, outSlotY + 1, outSlotX + 25, outSlotY + 25, 0xFFC6C6C6);
        } else {
            // 处理模式：绘制3个输出槽
            for (int y = 0; y < 3; y++) {
                final int outSlotX = panelX + OUTPUT_OFFSET_X - 1;
                final int outSlotY = panelY + OUTPUT_OFFSET_Y - 1 + y * 18;
                Gui.drawRect(outSlotX, outSlotY, outSlotX + 18, outSlotY + 18, 0xFF8B8B8B);
                Gui.drawRect(outSlotX + 1, outSlotY + 1, outSlotX + 17, outSlotY + 17, 0xFFC6C6C6);
            }
        }

        // 箭头（简化为文本箭头）
        this.fontRenderer.drawString("\u2192", panelX + 57, panelY + 25, 0xFF404040);

        // 绘制空白样板输入槽背景
        final int patInX = panelX + PATTERN_IN_OFFSET_X - 1;
        final int patInY = panelY + PATTERN_IN_OFFSET_Y - 1;
        Gui.drawRect(patInX, patInY, patInX + 18, patInY + 18, 0xFF8B8B8B);
        Gui.drawRect(patInX + 1, patInY + 1, patInX + 17, patInY + 17, 0xFFC6C6C6);

        // 编码箭头
        this.fontRenderer.drawString("\u2192", panelX + 27, panelY + PATTERN_IN_OFFSET_Y + 3, 0xFF404040);

        // 编码样板输出槽背景
        final int patOutX = panelX + PATTERN_OUT_OFFSET_X - 1;
        final int patOutY = panelY + PATTERN_OUT_OFFSET_Y - 1;
        Gui.drawRect(patOutX, patOutY, patOutX + 18, patOutY + 18, 0xFF8B8B8B);
        Gui.drawRect(patOutX + 1, patOutY + 1, patOutX + 17, patOutY + 17, 0xFFC6C6C6);
    }

    /**
     * 绘制左侧ME物品面板的背景（左下角对齐）
     */
    private void drawItemPanelBG(int offsetX, int offsetY) {
        final int panelX = getItemPanelAbsX();
        final int panelY = getItemPanelAbsY();

        GlStateManager.color(1, 1, 1, 1);

        // 绘制面板背景
        Gui.drawRect(panelX, panelY, panelX + ITEM_PANEL_WIDTH, panelY + ITEM_PANEL_HEIGHT, 0xFFC6C6C6);
        // 绘制面板边框
        Gui.drawRect(panelX, panelY, panelX + ITEM_PANEL_WIDTH, panelY + 1, 0xFF555555);
        Gui.drawRect(panelX, panelY + ITEM_PANEL_HEIGHT - 1, panelX + ITEM_PANEL_WIDTH,
                panelY + ITEM_PANEL_HEIGHT, 0xFF555555);
        Gui.drawRect(panelX, panelY, panelX + 1, panelY + ITEM_PANEL_HEIGHT, 0xFF555555);
        Gui.drawRect(panelX + ITEM_PANEL_WIDTH - 1, panelY, panelX + ITEM_PANEL_WIDTH,
                panelY + ITEM_PANEL_HEIGHT, 0xFF555555);

        // 绘制物品网格的槽位背景
        for (int row = 0; row < ITEM_PANEL_ROWS; row++) {
            for (int col = 0; col < ITEM_PANEL_COLS; col++) {
                final int slotX = panelX + ITEM_GRID_OFFSET_X + col * 18;
                final int slotY = panelY + ITEM_GRID_OFFSET_Y + row * 18;
                Gui.drawRect(slotX, slotY, slotX + 18, slotY + 18, 0xFF8B8B8B);
                Gui.drawRect(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFFC6C6C6);
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // 绘制接口终端主体的前景
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();

        // 绘制右侧样板面板标题
        this.fontRenderer.drawString(GuiText.PatternEncoding.getLocal(), panelX + 4,
                panelY + PATTERN_PANEL_HEIGHT + 2, 4210752);

        // 按钮可见性逻辑（与 GuiPatternTerm 一致）
        if (ct.isCraftingMode()) {
            // 合成模式
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;

            // 替代品按钮根据当前状态显示
            this.substitutionsEnabledBtn.visible = ct.isSubstitute();
            this.substitutionsDisabledBtn.visible = !ct.isSubstitute();

            // 数量调整按钮在合成模式下隐藏
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;
        } else {
            // 处理模式
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;

            // 替代品按钮在处理模式下隐藏
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;

            // 数量调整按钮在处理模式下显示
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.divTwoBtn.visible = true;
            this.divThreeBtn.visible = true;
            this.plusOneBtn.visible = true;
            this.minusOneBtn.visible = true;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // ========== 面板拖拽处理 ==========

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        // 面板拖拽处理
        if (this.itemPanelDragging) {
            this.itemPanelDragOffsetX = this.itemPanelDragStartOffsetX + (mouseX - this.itemPanelDragStartMouseX);
            this.itemPanelDragOffsetY = this.itemPanelDragStartOffsetY + (mouseY - this.itemPanelDragStartMouseY);
            this.reinitialize();
            return;
        }
        if (this.patternPanelDragging) {
            this.patternPanelDragOffsetX = this.patternPanelDragStartOffsetX
                    + (mouseX - this.patternPanelDragStartMouseX);
            this.patternPanelDragOffsetY = this.patternPanelDragStartOffsetY
                    + (mouseY - this.patternPanelDragStartMouseY);
            this.reinitialize();
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (this.itemPanelDragging) {
            this.itemPanelDragging = false;
            return;
        }
        if (this.patternPanelDragging) {
            this.patternPanelDragging = false;
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // 中键拖拽面板
        if (btn == 2) {
            // 检查是否在 ME 物品面板的标题栏（搜索框所在的顶部区域）
            final int itemAbsX = getItemPanelAbsX();
            final int itemAbsY = getItemPanelAbsY();
            if (xCoord >= itemAbsX && xCoord < itemAbsX + ITEM_PANEL_WIDTH
                    && yCoord >= itemAbsY && yCoord < itemAbsY + ITEM_GRID_OFFSET_Y) {
                this.itemPanelDragging = true;
                this.itemPanelDragStartMouseX = xCoord;
                this.itemPanelDragStartMouseY = yCoord;
                this.itemPanelDragStartOffsetX = this.itemPanelDragOffsetX;
                this.itemPanelDragStartOffsetY = this.itemPanelDragOffsetY;
                return;
            }

            // 检查是否在样板面板的按钮栏区域（底部按钮区域附近的空白处）
            final int patternAbsX = this.guiLeft + getPatternPanelX();
            final int patternAbsY = this.guiTop + getPatternPanelY();
            if (xCoord >= patternAbsX && xCoord < patternAbsX + PATTERN_PANEL_WIDTH
                    && yCoord >= patternAbsY + PATTERN_PANEL_HEIGHT
                    && yCoord < patternAbsY + PATTERN_PANEL_HEIGHT + 16) {
                this.patternPanelDragging = true;
                this.patternPanelDragStartMouseX = xCoord;
                this.patternPanelDragStartMouseY = yCoord;
                this.patternPanelDragStartOffsetX = this.patternPanelDragOffsetX;
                this.patternPanelDragStartOffsetY = this.patternPanelDragOffsetY;
                return;
            }
        }

        // 让搜索框处理鼠标点击
        if (this.itemSearchField != null) {
            this.itemSearchField.mouseClicked(xCoord, yCoord, btn);
            // 右键清空搜索框
            if (btn == 1 && this.isMouseOverSearchField(xCoord, yCoord)) {
                this.itemSearchField.setText("");
                this.itemRepo.setSearchString("");
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
            }
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    // ========== 辅助方法 ==========

    /**
     * 判断鼠标是否在搜索框上方
     */
    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        return mouseX >= itemAbsX + 3 && mouseX < itemAbsX + 3 + 72
                && mouseY >= itemAbsY + 4 && mouseY < itemAbsY + 4 + 12;
    }

    /**
     * 重新初始化 GUI（按钮和槽位重新布局）
     */
    private void reinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    // ========== ISortSource 接口实现 ==========

    @Override
    public Enum getSortBy() {
        return this.configSrc.getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return this.configSrc.getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return this.configSrc.getSetting(Settings.VIEW_MODE);
    }

    // ========== IConfigManagerHost 接口实现 ==========

    @Override
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }
        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }
        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }
        this.itemRepo.updateView();
    }

    // ========== 外部 API：JEI Recipe Transfer 调用 ==========

    /**
     * 设置 Names 搜索框的建议文本（灰色提示文字）。
     * 由 RecipeTransferHandler 在 JEI 填入配方后调用。
     * 用户按 TAB 可将建议文本确认为正式搜索文本。
     *
     * @param suggestion 建议文本（通常是配方输出物品的显示名称）
     */
    public void setSearchFieldSuggestion(final String suggestion) {
        this.searchFieldNames.setSuggestion(suggestion);
    }

    /**
     * 直接设置 Names 搜索框的文本。
     *
     * @param text 搜索文本
     */
    public void setSearchFieldText(final String text) {
        this.searchFieldNames.setText(text);
    }
}

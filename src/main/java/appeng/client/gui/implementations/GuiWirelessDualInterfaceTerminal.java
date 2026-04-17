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

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.helpers.ItemStackHelper.stackFromNBT;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.function.BiPredicate;

import com.google.common.collect.HashMultimap;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.ActionItems;
import appeng.api.config.CombineMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.gui.widgets.MEGuiTooltipTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotDisconnected;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.DualityInterface;
import appeng.helpers.InventoryAction;
import appeng.helpers.PatternHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.Integrations;
import appeng.util.BlockPosUtils;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 无线二合一接口终端的GUI
 *
 * 直接继承 AEBaseGui，不再继承 GuiInterfaceTerminal。
 * 参考 AE2Things 的 GuiBaseInterfaceWireless + GuiWirelessDualInterfaceTerminal 的架构：
 * - 中间：接口列表面板 + 玩家背包（从 GuiInterfaceTerminal 移植的逻辑）
 * - 左侧：ME物品网格（显示AE网络中的物品，带搜索框和滚动条）
 * - 右侧：样板编写区域（与主GUI部分重叠，xSize=240）
 *
 * 采用 AE2Things 的特殊面板布局：xSize = 240，样板面板从 guiLeft+209 开始绘制，
 * 与主 GUI 有 31px 的重叠区域。
 */
public class GuiWirelessDualInterfaceTerminal extends AEBaseGui
        implements ContainerWirelessDualInterfaceTerminal.IMEInventoryUpdateReceiver,
        ISortSource, IConfigManagerHost {

    // ========== 贴图资源 ==========
    private static final ResourceLocation ITEMS_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/items.png");
    private static final ResourceLocation PATTERN_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern.png");
    private static final ResourceLocation PATTERN3_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern3.png");

    // ========== 接口终端布局常量（从 GuiInterfaceTerminal 移植） ==========

    /** 接口列表标题等的 X 偏移 */
    private static final int OFFSET_X = 21;
    /** 接口终端主体的固定宽度 */
    private static final int MAIN_GUI_WIDTH = 208;
    /** 接口终端头部高度 + 底部背包高度的固定值 */
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    // ========== 样板编写面板常量（匹配 AE2Things pattern.png/pattern3.png 贴图） ==========

    /**
     * 样板面板在主 GUI 内的起始X偏移（相对 guiLeft）
     * 参考 AE2Things: drawTexturedModalRect(offsetX + 209, ...)
     */
    private static final int PATTERN_PANEL_X_OFFSET = 209;

    /**
     * 样板面板上半部分尺寸（合成/处理网格区域）
     * 对应贴图 pattern3.png (0,0)→133×93 或 pattern.png (0,93)→133×93
     */
    private static final int PATTERN_PANEL_WIDTH = 133;
    private static final int PATTERN_PANEL_UPPER_HEIGHT = 93;

    /**
     * 样板面板下半部分尺寸（样板IN/OUT槽区域）
     * 对应贴图 pattern.png (133,0)→40×77
     */
    private static final int PATTERN_PANEL_LOWER_WIDTH = 40;
    private static final int PATTERN_PANEL_LOWER_HEIGHT = 77;

    /**
     * 样板面板总高度
     */
    private static final int PATTERN_PANEL_HEIGHT = PATTERN_PANEL_UPPER_HEIGHT + PATTERN_PANEL_LOWER_HEIGHT;

    // ===== 合成网格在上半贴图中的位置（相对于面板左上角） =====
    /** 合成网格起始X/Y偏移（相对面板左上角，对齐贴图中的3×3网格第一个槽位左上角） */
    private static final int GRID_OFFSET_X = 15;
    private static final int GRID_OFFSET_Y = 18;

    // ===== 输出槽在上半贴图中的位置 =====
    /**
     * 输出槽物品渲染位置（相对面板左上角）
     * 合成模式大槽 (26×26) 边框在贴图 (103, 32)，物品居中 +5
     * 处理模式 3 个标准槽 (18×18) 与合成模式输出对齐
     */
    private static final int OUTPUT_OFFSET_X = 108;
    private static final int OUTPUT_OFFSET_Y = 18;

    // ===== 样板IN/OUT在下半贴图中的位置（相对面板左上角） =====
    /** 空白样板输入槽物品渲染位置（18×18 标准槽，边框在底部贴图 (9,5)，物品 +1） */
    private static final int PATTERN_IN_OFFSET_X = 10;
    private static final int PATTERN_IN_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 6;

    /** 编码样板输出槽物品渲染位置（24×24 大槽，边框在底部贴图 (7,45)，物品居中 +4） */
    private static final int PATTERN_OUT_OFFSET_X = 11;
    private static final int PATTERN_OUT_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 49;

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

    // ========== 接口终端数据（从 GuiInterfaceTerminal 移植） ==========

    // To make JEI look nicer. Otherwise, the buttons will make JEI in a strange place.
    private final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final Map<Long, ClientDCInternalInv> providerById = new HashMap<>();

    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> guiButtonHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalInv> doubleButtonHashMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();
    private final Map<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();

    /** 接口终端的搜索框 */
    private final MEGuiTooltipTextField searchFieldOutputs;
    private final MEGuiTooltipTextField searchFieldInputs;
    private final MEGuiTooltipTextField searchFieldNames;

    /** 接口终端的功能按钮 */
    private final GuiImgButton guiButtonHideFull;
    private final GuiImgButton guiButtonAssemblersOnly;
    private final GuiImgButton guiButtonBrokenRecipes;
    private final GuiImgButton terminalStyleBox;

    private boolean refreshList = false;

    /* These are worded so that the intended default is false */
    private boolean onlyShowWithSpace = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;
    /** 接口列表的可见行数 */
    private int rows = 6;

    // ========== ME物品面板搜索框记忆文本 ==========
    private static String memoryText = "";

    // ========== 样板面板按钮 ==========
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

    // ========== 数量调整按钮（处理模式下显示，与 GuiPatternTerm 一致） ==========
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton doubleBtn;
    private UniversalTerminalButtons universalButtons;

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

    // ========== 处理模式输出槽翻页滚动条 ==========
    private final GuiScrollbar processingScrollBar;

    private final IConfigManager configSrc;
    private final WirelessTerminalGuiObject wirelessGuiObject;

    // ========== PlacePattern（编码后自动放入接口） ==========
    /** 当编码按钮按下时 Alt 被按住，设置为 true，下一帧 tick 时尝试放置 */
    private boolean pendingPlacePattern = false;

    // ========== 面板拖拽状态 ==========
    /** 左侧ME物品面板的拖拽状态 */
    private PanelDragState itemPanelDragState;
    /** 右侧样板面板的拖拽状态 */
    private PanelDragState patternPanelDragState;

    public GuiWirelessDualInterfaceTerminal(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(new ContainerWirelessDualInterfaceTerminal(inventoryPlayer, te));
        this.wirelessGuiObject = te;

        // xSize = 240，与 AE2Things 一致，使样板面板可以与主 GUI 重叠
        this.xSize = 240;
        this.ySize = 255;

        // 接口终端滚动条
        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        // 接口终端搜索框（从 GuiInterfaceTerminal 移植）
        searchFieldInputs = createInterfaceTextField(86, 12, ButtonToolTips.SearchFieldInputs.getLocal());
        searchFieldOutputs = createInterfaceTextField(86, 12, ButtonToolTips.SearchFieldOutputs.getLocal());
        searchFieldNames = createInterfaceTextField(71, 12, ButtonToolTips.SearchFieldNames.getLocal());

        // 接口终端功能按钮
        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);

        // 从 Container 获取配置管理器用于排序/视图设置
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();

        // 初始化左侧 ME 物品面板的滚动条和 ItemRepo
        this.itemPanelScrollbar = new GuiScrollbar();
        this.itemRepo = new ItemRepo(this.itemPanelScrollbar, this);
        this.itemRepo.setRowSize(ITEM_PANEL_COLS);

        // 初始化处理模式输出槽翻页滚动条
        this.processingScrollBar = new GuiScrollbar();

        // 初始化面板拖拽状态
        this.initDragStates();

        // 注册自身为 ME 库存更新接收者
        getDualContainer().setMeGui(this);
    }

    /**
     * 创建接口终端搜索框（文本变更时刷新接口列表）
     */
    private MEGuiTooltipTextField createInterfaceTextField(final int width, final int height, final String tooltip) {
        MEGuiTooltipTextField textField = new MEGuiTooltipTextField(width, height, tooltip) {
            @Override
            public void onTextChange(String oldText) {
                refreshList();
            }
        };
        textField.setEnableBackgroundDrawing(false);
        textField.setMaxStringLength(25);
        textField.setTextColor(0xFFFFFF);
        textField.setCursorPositionZero();
        return textField;
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
     * 使用 AE2Things 的布局：面板从 guiLeft + 209 开始
     */
    private int getPatternPanelX() {
        return PATTERN_PANEL_X_OFFSET + this.patternPanelDragState.getDragOffsetX();
    }

    /**
     * 获取样板面板的起始Y坐标偏移（相对于guiTop）
     */
    private int getPatternPanelY() {
        return this.patternPanelDragState.getDragOffsetY();
    }

    /**
     * 获取ME物品面板的绝对X坐标（屏幕坐标）
     * 参考 AE2Things: absX = guiLeft - 101
     */
    private int getItemPanelAbsX() {
        return this.guiLeft - ITEM_PANEL_WIDTH + this.itemPanelDragState.getDragOffsetX();
    }

    /**
     * 获取ME物品面板的绝对Y坐标（屏幕坐标）
     * 参考 AE2Things: absY = guiTop + ySize - 96（左下角对齐）
     */
    private int getItemPanelAbsY() {
        return this.guiTop + this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragState.getDragOffsetY();
    }

    /**
     * 获取ME物品面板的起始X坐标（相对于guiLeft，用于虚拟槽位定位）
     */
    private int getItemPanelRelX() {
        return -ITEM_PANEL_WIDTH + this.itemPanelDragState.getDragOffsetX();
    }

    /**
     * 获取ME物品面板的起始Y坐标（相对于guiTop，用于虚拟槽位定位）
     */
    private int getItemPanelRelY() {
        return this.ySize - ITEM_PANEL_HEIGHT + this.itemPanelDragState.getDragOffsetY();
    }

    // ========== 接口终端滚动条设置 ==========

    private void setInterfaceScrollBar() {
        this.getScrollBar().setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    // ========== ME物品面板滚动条更新 ==========

    private void updateItemPanelScrollbar() {
        this.itemPanelScrollbar.setRange(0,
                (this.itemRepo.size() + ITEM_PANEL_COLS - 1) / ITEM_PANEL_COLS - ITEM_PANEL_ROWS,
                Math.max(1, ITEM_PANEL_ROWS / 6));
    }

    // ========== 槽位重定位 ==========

    /**
     * 重定位所有槽位。
     * 样板编写相关槽位使用 AE2Things 的公式：ySize + getY() - viewHeight - 78 - 4
     * 其中 viewHeight = rows * 18（接口列表可见区域高度）
     * 玩家背包和其他标准槽位使用：ySize + getY() - 78 - 7
     */
    private void repositionSlots() {
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();
        final int viewHeight = this.rows * 18;

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
                    // 样板编码输出槽（合成模式下的大结果槽 26×26）
                    // 物品渲染位置 = 大槽边框(103,32) + 居中偏移(5,5)
                    slot.xPos = panelX + OUTPUT_OFFSET_X;
                    slot.yPos = panelY + 37;
                } else if (slot instanceof SlotPatternOutputs) {
                    // 处理模式的输出槽（3个标准 18×18 槽位）
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
                    // 玩家背包槽位和其他标准槽位
                    slot.yPos = this.ySize + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== GUI 初始化 ==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // ===== 计算接口列表行数（与 GuiInterfaceTerminal 逻辑一致） =====
        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math.floor(
                (double) (this.height - MAGIC_HEIGHT_NUMBER - jeiSearchOffset) / 18);

        final Enum<?> terminalStyle = AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE);

        if (terminalStyle == TerminalStyle.FULL) {
            this.rows = maxScreenRows;
        } else if (terminalStyle == TerminalStyle.TALL) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.75);
        } else if (terminalStyle == TerminalStyle.MEDIUM) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.5);
        } else if (terminalStyle == TerminalStyle.SMALL) {
            this.rows = (int) Math.ceil(maxScreenRows * 0.25);
        } else {
            this.rows = maxScreenRows;
        }

        this.rows = Math.min(this.rows, Integer.MAX_VALUE);
        this.rows = Math.max(this.rows, 6);

        super.initGui();

        // ===== 计算 ySize 和 guiTop =====
        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        // ===== 接口终端搜索框定位 =====
        searchFieldInputs.x = guiLeft + 32;
        searchFieldInputs.y = guiTop + 25;
        searchFieldOutputs.x = guiLeft + 32;
        searchFieldOutputs.y = guiTop + 38;
        searchFieldNames.x = guiLeft + 32 + 99;
        searchFieldNames.y = guiTop + 38;

        searchFieldNames.setFocused(true);

        // ===== 接口终端功能按钮定位 =====
        terminalStyleBox.x = guiLeft - 18;
        terminalStyleBox.y = guiTop + 8 + this.jeiOffset;
        guiButtonBrokenRecipes.x = guiLeft - 18;
        guiButtonBrokenRecipes.y = terminalStyleBox.y + 20;
        guiButtonHideFull.x = guiLeft - 18;
        guiButtonHideFull.y = guiButtonBrokenRecipes.y + 20;
        guiButtonAssemblersOnly.x = guiLeft - 18;
        guiButtonAssemblersOnly.y = guiButtonHideFull.y + 20;

        // ===== 接口终端滚动条 =====
        this.setInterfaceScrollBar();
        this.repositionSlots();

        // ===== Crafting Status 按钮（主界面右上角） =====
        this.craftingStatusBtn = new GuiTabButton(this.guiLeft + 170, this.guiTop - 4,
                2 + 11 * 16, GuiText.CraftingStatus.getLocal(), this.itemRender);
        this.craftingStatusBtn.setHideEdge(13);
        this.buttonList.add(this.craftingStatusBtn);

        // ===== 样板面板按钮（位置匹配 AE2Things 的 PatternPanel） =====
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();

        // 编码按钮（面板内 (11, 118)，在下半区域，与 AE2Things 一致）
        this.encodeBtn = new GuiImgButton(panelScreenX + 11, panelScreenY + 118,
                Settings.ACTIONS, ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        // 清除按钮（半尺寸，面板内 (87, 10)，上半区域右侧）
        this.clearBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 10,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        // 合成/处理模式切换标签（面板内 (39, 93)，上下交界处）
        this.tabCraftButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.CRAFTING_TABLE),
                GuiText.CraftingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.FURNACE),
                GuiText.ProcessingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        // 替代品开关按钮（面板内 (97, 10)，上半区域右侧，半尺寸）
        this.substitutionsEnabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        // 绝对替换按钮（面板内 (87, 20)，替代品下方，半尺寸）
        this.beSubstitutionsEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.beSubstitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsEnabledBtn);

        this.beSubstitutionsDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.beSubstitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.beSubstitutionsDisabledBtn);

        // 反转按钮（面板内 (97, 20)，半尺寸，处理模式下可见）
        this.invertBtn = new GuiImgButton(panelScreenX + 97, panelScreenY + 20,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.invertBtn.setHalfSize(true);
        this.buttonList.add(this.invertBtn);

        // 合并模式按钮（面板内 (87, 30)，半尺寸，处理模式下可见）
        this.combineEnabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.ENABLED);
        this.combineEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.combineEnabledBtn);

        this.combineDisabledBtn = new GuiImgButton(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.DISABLED);
        this.combineDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.combineDisabledBtn);

        // ===== 数量调整按钮（处理模式下显示，放在输出槽右侧） =====
        final int adjBtnX1 = panelScreenX + OUTPUT_OFFSET_X + 22;
        final int adjBtnX2 = panelScreenX + OUTPUT_OFFSET_X + 12;

        this.x3Btn = new GuiImgButton(adjBtnX1, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(adjBtnX1, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(adjBtnX1, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(adjBtnX2, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(adjBtnX2, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(adjBtnX2, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.buttonList.add(this.minusOneBtn);

        // 翻倍/减半按钮（左键×2/×8，右键÷2/÷8）
        this.doubleBtn = new GuiImgButton(adjBtnX2, panelScreenY + 36,
                Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
        this.doubleBtn.setHalfSize(true);
        this.buttonList.add(this.doubleBtn);

        // ===== ME物品面板 =====
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        final int itemRelX = getItemPanelRelX();
        final int itemRelY = getItemPanelRelY();

        // ME物品面板排序/过滤按钮
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

        // ME物品搜索框
        this.itemSearchField = new MEGuiTextField(this.fontRenderer,
                itemAbsX + 3, itemAbsY + 4, 72, 12);
        this.itemSearchField.setEnableBackgroundDrawing(false);
        this.itemSearchField.setMaxStringLength(25);
        this.itemSearchField.setTextColor(0xFFFFFF);
        this.itemSearchField.setVisible(true);

        // SearchBoxMode 逻辑
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

        // 设置ME物品面板滚动条位置
        this.itemPanelScrollbar.setLeft(itemRelX + ITEM_PANEL_WIDTH - 14)
                .setTop(itemRelY + ITEM_GRID_OFFSET_Y)
                .setHeight(ITEM_PANEL_ROWS * 18 - 2);
        this.updateItemPanelScrollbar();

        // ===== 处理模式输出槽翻页滚动条（位于输出槽右侧） =====
        final int panelRelX = getPatternPanelX();
        final int panelRelY = getPatternPanelY();
        this.processingScrollBar.setLeft(panelRelX + OUTPUT_OFFSET_X + 18)
                .setTop(panelRelY + OUTPUT_OFFSET_Y)
                .setHeight(3 * 18 - 2);
        final int totalPages = getDualContainer().getTotalPages();
        this.processingScrollBar.setRange(0, Math.max(0, totalPages - 1), 1);

        this.itemRepo.setPower(true);

        // 通用无线终端切换按钮
        this.universalButtons = new UniversalTerminalButtons(
                ((appeng.container.AEBaseContainer) this.inventorySlots).getPlayerInv());
        this.universalButtons.initButtons(this.guiLeft, this.guiTop, this.buttonList, 500, this.itemRender);
    }

    // ========== JEI 排除区域（防止 JEI 面板遮挡侧面板） ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> area = new ArrayList<>();
        // 左侧按钮排除区域
        area.add(new Rectangle(this.guiLeft - 18, this.guiTop + 24 + 24, 18, 18));
        // 右侧样板面板排除区域
        final int panelScreenX = this.guiLeft + getPatternPanelX();
        final int panelScreenY = this.guiTop + getPatternPanelY();
        area.add(new Rectangle(panelScreenX, panelScreenY, PATTERN_PANEL_WIDTH, PATTERN_PANEL_HEIGHT));
        // 左侧ME物品面板排除区域
        area.add(new Rectangle(getItemPanelAbsX(), getItemPanelAbsY(), ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT));
        return area;
    }

    // ========== 按钮处理 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        // 通用无线终端切换按钮
        if (this.universalButtons != null && this.universalButtons.handleButtonClick(btn)) {
            return;
        }

        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        // Crafting Status 按钮
        if (btn == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_CRAFTING_STATUS));
            return;
        }

        // ===== 接口终端的高亮按钮 =====
        if (guiButtonHashMap.containsKey(btn)) {
            BlockPos blockPos = blockPosHashMap.get(guiButtonHashMap.get(this.selectedButton));
            BlockPos blockPos2 = mc.player.getPosition();
            int playerDim = mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.get(guiButtonHashMap.get(this.selectedButton));
            if (playerDim != interfaceDim) {
                try {
                    mc.player.sendStatusMessage(
                            PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim,
                                    DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()),
                            false);
                } catch (Exception e) {
                    mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
                }
            } else {
                hilightBlock(blockPos,
                        System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
                mc.player.sendStatusMessage(
                        PlayerMessages.InterfaceHighlighted.get(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                        false);
            }
            mc.player.closeScreen();
            return;
        }

        // ===== 接口终端的翻倍/减半按钮 =====
        if (doubleButtonHashMap.containsKey(btn)) {
            final ClientDCInternalInv inv = doubleButtonHashMap.get(btn);
            final boolean backwards = Mouse.isButtonDown(1);
            int val = isShiftKeyDown() ? 1 : 0;
            if (backwards) {
                val |= 0b10;
            }
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        "InterfaceTerminal.Double", val + "," + inv.getId()));
            } catch (final IOException e) {
                // ignore
            }
            return;
        }

        // ===== 接口终端的筛选按钮 =====
        if (btn == guiButtonHideFull) {
            onlyShowWithSpace = !onlyShowWithSpace;
            this.refreshList();
            return;
        }
        if (btn == guiButtonAssemblersOnly) {
            onlyMolecularAssemblers = !onlyMolecularAssemblers;
            this.refreshList();
            return;
        }
        if (btn == guiButtonBrokenRecipes) {
            onlyBrokenRecipes = !onlyBrokenRecipes;
            this.refreshList();
            return;
        }

        // ===== 终端样式按钮 =====
        if (btn == this.terminalStyleBox) {
            final Enum<?> cv = terminalStyleBox.getCurrentValue();
            final boolean backwards = Mouse.isButtonDown(1);
            final Enum<?> next = Platform.rotateEnum(cv, backwards, terminalStyleBox.getSetting().getPossibleValues());
            AEConfig.instance().getConfigManager().putSetting(terminalStyleBox.getSetting(), next);
            terminalStyleBox.set(next);
            this.reinitialize();
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

        // ===== 样板面板按钮 =====
        try {
            if (btn == this.tabCraftButton || btn == this.tabProcessButton) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode",
                        ct.isCraftingMode() ? "0" : "1"));
            } else if (btn == this.encodeBtn) {
                final int value = (isCtrlKeyDown() ? 1 : 0) << 1 | (isShiftKeyDown() ? 1 : 0);
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Encode", String.valueOf(value)));
                // Alt + 编码：编码完成后自动放入接口
                if (value == 0 && isAltKeyDown()) {
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
                final boolean newInverted = !getDualContainer().isInverted();
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.Invert", newInverted ? "1" : "0"));
            } else if (btn == this.combineEnabledBtn || btn == this.combineDisabledBtn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Combine",
                        this.combineEnabledBtn == btn ? "0" : "1"));
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
            } else if (btn == this.doubleBtn) {
                final boolean backwards = Mouse.isButtonDown(1);
                int val = isShiftKeyDown() ? 1 : 0;
                if (backwards) {
                    val |= 0b10;
                }
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("PatternTerminal.Double", String.valueOf(val)));
            }
        } catch (IOException e) {
            // ignore
        }
    }

    // ========== 键盘输入处理 ==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            // TAB 键处理
            if (key == Keyboard.KEY_TAB) {
                this.searchFieldNames.setSuggestionToText();
            }
            if (character == '\t') {
                if (this.handleTab()) {
                    return;
                }
            }

            // 空格作为第一个字符时禁止（所有搜索框）
            if (character == ' ') {
                if ((this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused())
                        || (this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused())
                        || (this.searchFieldNames.getText().isEmpty() && this.searchFieldNames.isFocused())
                        || (this.itemSearchField != null && this.itemSearchField.isFocused()
                                && this.itemSearchField.getText().isEmpty())) {
                    return;
                }
            }

            // ME 搜索框处理键盘输入
            if (this.itemSearchField != null && this.itemSearchField.isFocused()
                    && this.itemSearchField.textboxKeyTyped(character, key)) {
                final String searchText = this.itemSearchField.getText();
                this.itemRepo.setSearchString(searchText);
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
                final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
                final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                        || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
                if (isJEISync && Platform.isJEIEnabled()) {
                    Integrations.jei().setSearchText(searchText);
                }
                return;
            }

            // 接口终端搜索框处理键盘输入
            if (this.searchFieldInputs.textboxKeyTyped(character, key)
                    || this.searchFieldOutputs.textboxKeyTyped(character, key)
                    || this.searchFieldNames.textboxKeyTyped(character, key)) {
                this.refreshList();
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    /**
     * TAB 焦点切换逻辑，将 ME 物品搜索框加入循环。
     * 焦点循环顺序：Inputs → Outputs → Names → ME物品搜索 → Inputs...
     * Shift 反向：Inputs → ME物品搜索 → Names → Outputs → Inputs...
     */
    private boolean handleTab() {
        if (this.itemSearchField != null && this.itemSearchField.isFocused()) {
            this.itemSearchField.setFocused(false);
            if (isShiftKeyDown()) {
                this.searchFieldNames.setFocused(true);
            } else {
                this.searchFieldInputs.setFocused(true);
            }
            return true;
        }
        if (searchFieldInputs.isFocused()) {
            searchFieldInputs.setFocused(false);
            if (isShiftKeyDown()) {
                if (this.itemSearchField != null) {
                    this.itemSearchField.setFocused(true);
                } else {
                    searchFieldNames.setFocused(true);
                }
            } else {
                searchFieldOutputs.setFocused(true);
            }
            return true;
        }
        if (searchFieldOutputs.isFocused()) {
            searchFieldOutputs.setFocused(false);
            if (isShiftKeyDown()) {
                searchFieldInputs.setFocused(true);
            } else {
                searchFieldNames.setFocused(true);
            }
            return true;
        }
        if (searchFieldNames.isFocused()) {
            searchFieldNames.setFocused(false);
            if (isShiftKeyDown()) {
                searchFieldOutputs.setFocused(true);
            } else if (this.itemSearchField != null) {
                this.itemSearchField.setFocused(true);
            } else {
                searchFieldInputs.setFocused(true);
            }
            return true;
        }
        return false;
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

        // 检查鼠标是否在右侧样板面板区域内（处理模式下翻页滚动）
        if (!getDualContainer().isCraftingMode()) {
            final int patPanelAbsX = this.guiLeft + getPatternPanelX();
            final int patPanelAbsY = this.guiTop + getPatternPanelY();
            if (x >= patPanelAbsX && x < patPanelAbsX + PATTERN_PANEL_WIDTH
                    && y >= patPanelAbsY && y < patPanelAbsY + PATTERN_PANEL_UPPER_HEIGHT) {
                this.processingScrollBar.wheel(wheel);
                this.sendActivePageUpdate();
                return;
            }
        }

        // 其他区域交由父类处理（接口终端滚动条）
        super.mouseWheelEvent(x, y, wheel);
    }

    // ========== GUI 关闭 ==========

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        if (this.itemSearchField != null) {
            memoryText = this.itemSearchField.getText();
            final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
            final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                    || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
            if (isJEISync && Platform.isJEIEnabled()) {
                Integrations.jei().setSearchText(memoryText);
            }
        }
    }

    // ========== Tick 更新（PlacePattern 自动放置逻辑） ==========

    @Override
    public void updateScreen() {
        super.updateScreen();

        // PlacePattern: 编码完成后自动将样板放入高亮接口的空闲槽位
        if (this.pendingPlacePattern) {
            this.pendingPlacePattern = false;
            final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();
            if (ct.getPatternSlotOUT() != null && ct.getPatternSlotOUT().getHasStack()) {
                this.tryPlacePatternToHighlightedInterface();
            }
        }
    }

    /**
     * 尝试将编码输出的样板放入当前可见接口列表中第一个有空闲槽位的接口。
     * 遍历当前可见的 lines 列表，找到第一个 ClientDCInternalInv 有空槽的条目。
     */
    private void tryPlacePatternToHighlightedInterface() {
        for (final ClientDCInternalInv inv : this.byId.values()) {
            final int slotLimit = inv.getInventory().getSlots();
            final int extraLines = numUpgradesMap.getOrDefault(inv, 0);
            final int maxSlots = Math.min(slotLimit, 9 * (1 + extraLines));

            for (int i = 0; i < maxSlots; i++) {
                if (inv.getInventory().getStackInSlot(i).isEmpty()) {
                    // 找到空闲槽位，发送 PlacePattern 请求
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

    /**
     * 发送 ActivePage 更新到服务端
     */
    private void sendActivePageUpdate() {
        final int newPage = this.processingScrollBar.getCurrentScroll();
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("PatternTerminal.ActivePage", String.valueOf(newPage)));
        } catch (IOException e) {
            // ignore
        }
    }

    // ========== 绘制背景 ==========

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // ===== 绘制接口终端主体背景（使用 208px 宽度的贴图） =====
        this.bindTexture("guis/newinterfaceterminal.png");

        // 顶部
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, MAIN_GUI_WIDTH, 53);

        // 接口列表行
        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 53 + x * 18, 0, 52, MAIN_GUI_WIDTH, 18);
        }

        // 接口列表中的槽位背景
        int offset = 51;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        for (int x = 0; x < this.rows && linesDraw < rows && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                GlStateManager.color(1, 1, 1, 1);

                final int extraLines = numUpgradesMap.get(lineObj);
                final int slotLimit = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;
                    final int actualSlots = Math.min(9, slotLimit - baseSlot);

                    if (actualSlots > 0) {
                        final int actualWidth = actualSlots * 18;
                        this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 173, actualWidth, 18);
                    }

                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }

        // 底部（玩家背包区域）
        this.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, MAIN_GUI_WIDTH, 99);

        // 接口终端搜索框
        this.searchFieldInputs.drawTextBox();
        this.searchFieldOutputs.drawTextBox();
        this.searchFieldNames.drawTextBox();

        // ===== 绘制无线终端升级槽背景 =====
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + 198, offsetY + 127, 0, 0, 32, 32, 32, 32);

        // ===== 绘制右侧样板编写面板背景 =====
        drawPatternPanelBG(offsetX, offsetY);

        // ===== 绘制左侧ME物品面板背景 =====
        drawItemPanelBG(offsetX, offsetY);

        // 绘制左侧ME物品面板滚动条
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        this.itemPanelScrollbar.draw(this);
        // 处理模式下绘制输出槽翻页滚动条
        if (!getDualContainer().isCraftingMode()) {
            this.processingScrollBar.draw(this);
        }
        GlStateManager.popMatrix();

        // ME物品搜索框
        if (this.itemSearchField != null) {
            this.itemSearchField.drawTextBox();
        }
    }

    /**
     * 绘制右侧样板编写面板的背景（使用 AE2Things 的 pattern.png/pattern3.png 贴图）
     */
    private void drawPatternPanelBG(int offsetX, int offsetY) {
        final int panelX = offsetX + getPatternPanelX();
        final int panelY = offsetY + getPatternPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();

        GlStateManager.color(1, 1, 1, 1);

        // 上半部分：合成模式使用 pattern3.png，处理模式使用 pattern.png
        if (ct.isCraftingMode()) {
            this.mc.getTextureManager().bindTexture(PATTERN3_TEXTURE);
            this.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else {
            this.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            this.drawTexturedModalRect(panelX, panelY, 0, PATTERN_PANEL_UPPER_HEIGHT,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        }

        // 下半部分（样板 IN/OUT 槽区域）
        this.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
        this.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_UPPER_HEIGHT,
                133, 0, PATTERN_PANEL_LOWER_WIDTH, PATTERN_PANEL_LOWER_HEIGHT);
    }

    /**
     * 绘制左侧ME物品面板的背景（左下角对齐）
     */
    private void drawItemPanelBG(int offsetX, int offsetY) {
        final int panelX = getItemPanelAbsX();
        final int panelY = getItemPanelAbsY();

        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(ITEMS_TEXTURE);
        this.drawTexturedModalRect(panelX, panelY, 0, 0, ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT);
    }

    // ========== 绘制前景 ==========

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // ===== 接口终端前景（标题和匹配高亮） =====
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.WirelessTerminal.getLocal()),
                OFFSET_X + 2, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96, 4210752);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        int offset = 51;
        int linesDraw = 0;
        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                final int extraLines = numUpgradesMap.get(inv);
                final int totalSlots = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;

                    for (int z = 0; z < 9; z++) {
                        final int slotIndex = baseSlot + z;

                        if (slotIndex < totalSlots) {
                            final ItemStack stack = inv.getInventory().getStackInSlot(slotIndex);
                            if (this.matchedStacks.contains(stack)) {
                                drawRect(z * 18 + 22, 1 + offset, z * 18 + 22 + 16, 1 + offset + 16, 0x2A00FF00);
                            }
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String name) {
                final int nameRows = this.byName.get(name).size();
                if (nameRows > 1) {
                    name = name + " (" + nameRows + ')';
                }

                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 158) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, OFFSET_X + 3, 6 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }

        // ===== 样板面板前景（标题和按钮可见性） =====
        final ContainerWirelessDualInterfaceTerminal ct = getDualContainer();
        final int panelX = getPatternPanelX();
        final int panelY = getPatternPanelY();

        this.fontRenderer.drawString(GuiText.PatternEncoding.getLocal(), panelX + 4,
                panelY + 4, 4210752);

        // 按钮可见性逻辑
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
            this.divTwoBtn.visible = true;
            this.divThreeBtn.visible = true;
            this.plusOneBtn.visible = true;
            this.minusOneBtn.visible = true;
            this.doubleBtn.visible = true;
        }
    }

    // ========== drawScreen ==========

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // ===== 接口终端 drawScreen 逻辑（动态创建按钮和 SlotDisconnected） =====
        buttonList.clear();
        guiButtonHashMap.clear();
        doubleButtonHashMap.clear();
        inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        guiButtonAssemblersOnly.set(
                onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLERS_ON : ActionItems.MOLECULAR_ASSEMBLERS_OFF);
        guiButtonHideFull.set(onlyShowWithSpace ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON
                : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF);
        terminalStyleBox.set(AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));

        buttonList.add(guiButtonAssemblersOnly);
        buttonList.add(guiButtonHideFull);
        buttonList.add(guiButtonBrokenRecipes);
        buttonList.add(terminalStyleBox);

        // 重新添加二合一终端专有按钮
        if (this.craftingStatusBtn != null) {
            buttonList.add(this.craftingStatusBtn);
        }
        if (this.encodeBtn != null) {
            buttonList.add(this.encodeBtn);
        }
        if (this.clearBtn != null) {
            buttonList.add(this.clearBtn);
        }
        if (this.tabCraftButton != null) {
            buttonList.add(this.tabCraftButton);
        }
        if (this.tabProcessButton != null) {
            buttonList.add(this.tabProcessButton);
        }
        if (this.substitutionsEnabledBtn != null) {
            buttonList.add(this.substitutionsEnabledBtn);
        }
        if (this.substitutionsDisabledBtn != null) {
            buttonList.add(this.substitutionsDisabledBtn);
        }
        if (this.beSubstitutionsEnabledBtn != null) {
            buttonList.add(this.beSubstitutionsEnabledBtn);
        }
        if (this.beSubstitutionsDisabledBtn != null) {
            buttonList.add(this.beSubstitutionsDisabledBtn);
        }
        if (this.invertBtn != null) {
            buttonList.add(this.invertBtn);
        }
        if (this.combineEnabledBtn != null) {
            buttonList.add(this.combineEnabledBtn);
        }
        if (this.combineDisabledBtn != null) {
            buttonList.add(this.combineDisabledBtn);
        }
        if (this.x2Btn != null) {
            buttonList.add(this.x2Btn);
        }
        if (this.x3Btn != null) {
            buttonList.add(this.x3Btn);
        }
        if (this.plusOneBtn != null) {
            buttonList.add(this.plusOneBtn);
        }
        if (this.divTwoBtn != null) {
            buttonList.add(this.divTwoBtn);
        }
        if (this.divThreeBtn != null) {
            buttonList.add(this.divThreeBtn);
        }
        if (this.minusOneBtn != null) {
            buttonList.add(this.minusOneBtn);
        }
        if (this.doubleBtn != null) {
            buttonList.add(this.doubleBtn);
        }
        if (this.SortByBox != null) {
            buttonList.add(this.SortByBox);
        }
        if (this.SortDirBox != null) {
            buttonList.add(this.SortDirBox);
        }
        if (this.ViewBox != null) {
            buttonList.add(this.ViewBox);
        }
        if (this.searchBoxSettings != null) {
            buttonList.add(this.searchBoxSettings);
        }

        // 动态生成接口列表的按钮和 SlotDisconnected
        int offset = 51;
        final int currentScroll = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;

        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset + 1, Settings.ACTIONS,
                        ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                this.buttonList.add(guiButton);

                // 每个接口的翻倍/减半按钮（半尺寸，位于高亮按钮下方）
                GuiImgButton interfaceDoubleBtn = new GuiImgButton(guiLeft + 8, guiTop + offset + 10,
                        Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
                interfaceDoubleBtn.setHalfSize(true);
                doubleButtonHashMap.put(interfaceDoubleBtn, inv);
                this.buttonList.add(interfaceDoubleBtn);

                final int extraLines = numUpgradesMap.get(inv);
                final int slotLimit = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;

                    for (int z = 0; z < 9; z++) {
                        final int slotIndex = baseSlot + z;
                        if (slotIndex < slotLimit) {
                            this.inventorySlots.inventorySlots.add(
                                    new SlotDisconnected(inv, slotIndex, z * 18 + 22, 1 + offset));
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }

            } else if (lineObj instanceof String) {
                linesDraw++;
                offset += 18;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        drawTooltip(searchFieldInputs, mouseX, mouseY);
        drawTooltip(searchFieldOutputs, mouseX, mouseY);
        drawTooltip(searchFieldNames, mouseX, mouseY);
    }

    // ========== 鼠标事件处理 ==========

    // ========== 中键点击样板槽位 → SET_PATTERN_VALUE ==========

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton,
            final ClickType clickType) {
        // 中键点击样板编码槽位（SlotFakeCraftingMatrix / OptionalSlotFake）
        if (clickType == ClickType.CLONE && slot instanceof SlotFake && slot.getHasStack()) {
            final IAEItemStack stack = AEItemStack.fromItemStack(slot.getStack());
            if (stack != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                if (isCtrlKeyDown()) {
                    // Ctrl+中键 → 打开名称设置界面
                    final PacketInventoryAction p = new PacketInventoryAction(
                            InventoryAction.SET_PATTERN_NAME, slot.slotNumber, 0);
                    NetworkHandler.instance().sendToServer(p);
                } else {
                    // 中键 → 打开数值设置界面
                    final PacketInventoryAction p = new PacketInventoryAction(
                            InventoryAction.SET_PATTERN_VALUE, slot.slotNumber, 0);
                    NetworkHandler.instance().sendToServer(p);
                }
                return;
            }
        }
        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // 中键拖拽面板
        if (btn == 2) {
            for (PanelDragState dragState : getAllDragStates()) {
                if (dragState.isInDragArea(xCoord, yCoord)) {
                    dragState.startDrag(xCoord, yCoord);
                    return;
                }
            }
        }

        // ME物品搜索框
        if (this.itemSearchField != null) {
            this.itemSearchField.mouseClicked(xCoord, yCoord, btn);
            if (btn == 1 && this.isMouseOverSearchField(xCoord, yCoord)) {
                this.itemSearchField.setText("");
                this.itemRepo.setSearchString("");
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
            }
        }

        // 接口终端搜索框
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldNames.mouseClicked(xCoord, yCoord, btn);

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        for (PanelDragState state : getAllDragStates()) {
            if (state.isDragging()) {
                state.updateDrag(mouseX, mouseY);
                this.reinitialize();
                return;
            }
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for (PanelDragState dragState : getAllDragStates()) {
            if (dragState.isDragging()) {
                dragState.endDrag();
                return;
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    // ========== 接口终端数据更新（从 GuiInterfaceTerminal 移植） ==========

    /**
     * 接收来自服务端的接口终端 NBT 更新
     */
    public void postUpdate(final NBTTagCompound in) {
        if (in.getBoolean("clear")) {
            this.byId.clear();
            this.providerById.clear();
            this.refreshList = true;
        }

        for (final Object oKey : in.getKeySet()) {
            final String key = (String) oKey;
            if (key.startsWith("=")) {
                try {
                    final long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(key);
                    final boolean isProvider = invData.getBoolean("provider");

                    final ClientDCInternalInv current;
                    if (isProvider) {
                        int slotCount = invData.getInteger("slots");
                        current = this.getProviderById(id, invData.getLong("sortBy"), invData.getString("un"),
                                slotCount);
                    } else {
                        current = this.getById(id, invData.getLong("sortBy"), invData.getString("un"));
                    }

                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));

                    if (!isProvider) {
                        numUpgradesMap.put(current, invData.getInteger("numUpgrades"));
                    } else {
                        int tier = invData.getInteger("tier");
                        int slotCount = (int) Math.pow(2, 1 + Math.min(9, tier));
                        int lines = slotCount / 9;
                        numUpgradesMap.put(current, lines);
                    }

                    for (int x = 0; x < current.getInventory().getSlots(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setStackInSlot(x, stackFromNBT(invData.getCompoundTag(which)));
                        }
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        if (this.refreshList) {
            this.refreshList = false;
            this.cachedSearches.clear();
            this.refreshList();
        }
    }

    /**
     * 刷新接口列表（从 GuiInterfaceTerminal 移植）
     */
    private void refreshList() {
        this.byName.clear();
        this.buttonList.clear();
        this.matchedStacks.clear();

        final String searchFieldInputs = this.searchFieldInputs.getText().toLowerCase();
        final String searchFieldOutputs = this.searchFieldOutputs.getText().toLowerCase();
        final String searchFieldNames = this.searchFieldNames.getText().toLowerCase();

        final Set<Object> cachedSearch = this
                .getCacheForSearchTerm("IN:" + searchFieldInputs + " OUT:" + searchFieldOutputs
                        + "NAME:" + searchFieldNames + onlyShowWithSpace + onlyMolecularAssemblers + onlyBrokenRecipes);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            if (!found || onlyShowWithSpace || onlyBrokenRecipes) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }

                    if (itemStack.isEmpty()) {
                        interfaceHasFreeSlots = true;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    if ((!searchFieldInputs.isEmpty()
                            && itemStackMatchesSearchTerm(itemStack, searchFieldInputs, 0))
                            || (!searchFieldOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchFieldOutputs, 1))) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }

                    slot++;
                }
            }

            if (!found) {
                cachedSearch.remove(entry);
                continue;
            }
            if (!entry.getName().toLowerCase().contains(searchFieldNames)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyMolecularAssemblers && !entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyShowWithSpace && !interfaceHasFreeSlots) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyBrokenRecipes && !interfaceHasBrokenRecipes) {
                cachedSearch.remove(entry);
                continue;
            }

            this.byName.put(entry.getName(), entry);
            cachedSearch.add(entry);
        }

        for (final ClientDCInternalInv entry : this.providerById.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchFieldInputs.isEmpty() && searchFieldOutputs.isEmpty();
            boolean interfaceHasFreeSlots = false;
            boolean interfaceHasBrokenRecipes = false;

            if (!found || onlyShowWithSpace || onlyBrokenRecipes) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }

                    if (itemStack.isEmpty()) {
                        interfaceHasFreeSlots = true;
                    }

                    if (onlyBrokenRecipes && recipeIsBroken(itemStack)) {
                        interfaceHasBrokenRecipes = true;
                    }

                    if ((!searchFieldInputs.isEmpty()
                            && itemStackMatchesSearchTerm(itemStack, searchFieldInputs, 0))
                            || (!searchFieldOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchFieldOutputs, 1))) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }

                    slot++;
                }
            }

            if (!found) {
                cachedSearch.remove(entry);
                continue;
            }
            if (!entry.getName().toLowerCase().contains(searchFieldNames)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyMolecularAssemblers && !entry.getUnlocalizedName().equals(MOLECULAR_ASSEMBLER)) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyShowWithSpace && !interfaceHasFreeSlots) {
                cachedSearch.remove(entry);
                continue;
            }
            if (onlyBrokenRecipes && !interfaceHasBrokenRecipes) {
                cachedSearch.remove(entry);
                continue;
            }

            this.byName.put(entry.getName(), entry);
            cachedSearch.add(entry);
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());
        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.names.size() + this.byId.size() + this.providerById.size());

        for (final String n : this.names) {
            this.lines.add(n);
            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.setInterfaceScrollBar();
    }

    private boolean recipeIsBroken(final ItemStack stack) {
        if (stack == null) {
            return false;
        }
        if (stack.isEmpty()) {
            return false;
        }

        final NBTTagCompound encodedValue = stack.getTagCompound();
        if (encodedValue == null) {
            return true;
        }

        final World w = AppEng.proxy.getWorld();
        if (w == null) {
            return false;
        }

        try {
            new PatternHelper(stack, w);
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean itemStackMatchesSearchTerm(final ItemStack itemStack, final String searchTerm, int pass) {
        if (itemStack.isEmpty()) {
            return false;
        }

        final NBTTagCompound encodedValue = itemStack.getTagCompound();

        if (encodedValue == null) {
            return searchTerm.matches(GuiText.InvalidPattern.getLocal());
        }

        final NBTTagList tag;
        if (pass == 0) {
            tag = encodedValue.getTagList("in", Constants.NBT.TAG_COMPOUND);
        } else {
            tag = encodedValue.getTagList("out", Constants.NBT.TAG_COMPOUND);
        }

        boolean foundMatchingItemStack = false;
        final String[] splitTerm = searchTerm.split(" ");

        for (int i = 0; i < tag.tagCount(); i++) {
            final ItemStack parsedItemStack = new ItemStack(tag.getCompoundTagAt(i));
            if (!parsedItemStack.isEmpty()) {
                final String displayName = Platform
                        .getItemDisplayName(AEItemStackType.INSTANCE.getStorageChannel()
                                .createStack(parsedItemStack))
                        .toLowerCase();

                for (String term : splitTerm) {
                    if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                        term = term.substring(1);
                        if (displayName.contains(term)) {
                            return false;
                        }
                    } else if (displayName.contains(term)) {
                        foundMatchingItemStack = true;
                    }
                }
            }
        }
        return foundMatchingItemStack;
    }

    private Set<Object> getCacheForSearchTerm(final String searchTerm) {
        if (!this.cachedSearches.containsKey(searchTerm)) {
            this.cachedSearches.put(searchTerm, new HashSet<>());
        }

        final Set<Object> cache = this.cachedSearches.get(searchTerm);

        if (cache.isEmpty() && searchTerm.length() > 1) {
            cache.addAll(this.getCacheForSearchTerm(searchTerm.substring(0, searchTerm.length() - 1)));
            return cache;
        }

        return cache;
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalInv o = this.byId.get(id);

        if (o == null) {
            this.byId.put(id,
                    o = new ClientDCInternalInv(DualityInterface.NUMBER_OF_PATTERN_SLOTS, id, sortBy, string));
            this.refreshList = true;
        }

        return o;
    }

    private ClientDCInternalInv getProviderById(final long id, final long sortBy, final String string,
            final int stackSize) {
        ClientDCInternalInv o = this.providerById.get(id);

        if (o == null) {
            this.providerById.put(id,
                    o = new ClientDCInternalInv(stackSize, id, sortBy, string));
            this.refreshList = true;
        }

        return o;
    }

    // ========== 辅助方法 ==========

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        final int itemAbsX = getItemPanelAbsX();
        final int itemAbsY = getItemPanelAbsY();
        return mouseX >= itemAbsX + 3 && mouseX < itemAbsX + 3 + 72
                && mouseY >= itemAbsY + 4 && mouseY < itemAbsY + 4 + 12;
    }

    private void reinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    private void initDragStates() {
        // ME物品面板：拖拽区域为搜索框所在的顶部区域
        this.itemPanelDragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = getItemPanelAbsX();
            final int absY = getItemPanelAbsY();
            return mouseX >= absX && mouseX < absX + ITEM_PANEL_WIDTH
                    && mouseY >= absY && mouseY < absY + ITEM_GRID_OFFSET_Y;
        });

        // 样板面板：拖拽区域为底部按钮区域
        this.patternPanelDragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = this.guiLeft + getPatternPanelX();
            final int absY = this.guiTop + getPatternPanelY();
            return mouseX >= absX && mouseX < absX + PATTERN_PANEL_WIDTH
                    && mouseY >= absY + PATTERN_PANEL_HEIGHT
                    && mouseY < absY + PATTERN_PANEL_HEIGHT + 16;
        });
    }

    private List<PanelDragState> getAllDragStates() {
        return Arrays.asList(this.itemPanelDragState, this.patternPanelDragState);
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

    // ========== 面板拖拽状态封装类 ==========

    /**
     * 封装单个面板的拖拽状态和行为。
     * 包括拖拽偏移量、拖拽过程中的起始坐标，以及拖拽区域检测逻辑。
     */
    private static class PanelDragState {

        /** 当前拖拽偏移量 */
        private int dragOffsetX = 0;
        private int dragOffsetY = 0;

        /** 是否正在拖拽 */
        private boolean dragging = false;

        /** 拖拽起始时的鼠标坐标 */
        private int dragStartMouseX;
        private int dragStartMouseY;

        /** 拖拽起始时的偏移量 */
        private int dragStartOffsetX;
        private int dragStartOffsetY;

        /** 拖拽区域检测器 */
        private final BiPredicate<Integer, Integer> dragAreaChecker;

        PanelDragState(BiPredicate<Integer, Integer> dragAreaChecker) {
            this.dragAreaChecker = dragAreaChecker;
        }

        /**
         * 检查指定坐标是否在拖拽区域内
         */
        boolean isInDragArea(int mouseX, int mouseY) {
            return dragAreaChecker.test(mouseX, mouseY);
        }

        /**
         * 开始拖拽
         */
        void startDrag(int mouseX, int mouseY) {
            this.dragging = true;
            this.dragStartMouseX = mouseX;
            this.dragStartMouseY = mouseY;
            this.dragStartOffsetX = this.dragOffsetX;
            this.dragStartOffsetY = this.dragOffsetY;
        }

        /**
         * 更新拖拽偏移
         */
        void updateDrag(int mouseX, int mouseY) {
            this.dragOffsetX = this.dragStartOffsetX + (mouseX - this.dragStartMouseX);
            this.dragOffsetY = this.dragStartOffsetY + (mouseY - this.dragStartMouseY);
        }

        /**
         * 结束拖拽
         */
        void endDrag() {
            this.dragging = false;
        }

        int getDragOffsetX() {
            return dragOffsetX;
        }

        int getDragOffsetY() {
            return dragOffsetY;
        }

        boolean isDragging() {
            return dragging;
        }
    }
}

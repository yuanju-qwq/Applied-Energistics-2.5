/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.ActionItems;
import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.api.config.YesNo;
import appeng.api.config.SearchBoxFocusPriority;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.widgets.*;
import appeng.client.me.InternalSlotME;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotME;
import appeng.client.mui.AEBaseMEPanel;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.interfaces.IMEMonitorableGuiCallback;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.integration.Integrations;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.misc.TileSecurityStation;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * MUI 版 ME 终端面板。
 * <p>
 * 这是所有终端（合成终端、样板终端等）的基类。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>可变行数的物品网格（9列 × N行，根据终端样式和屏幕大小动态调整）</li>
 *   <li>搜索框（支持自动聚焦、JEI同步、正则搜索、记忆搜索）</li>
 *   <li>排序/视图/搜索模式/终端样式按钮</li>
 *   <li>类型过滤切换按钮（物品/流体等）</li>
 *   <li>ViewCell 支持</li>
 *   <li>合成状态返回按钮</li>
 *   <li>动态槽位重新定位</li>
 *   <li>VirtualMEMonitorableSlot 的滚轮交互</li>
 *   <li>Shift+悬停暂停更新</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUIMEMonitorablePanel extends AEBaseMEPanel
        implements ISortSource, IConfigManagerHost, IMEMonitorableGuiCallback {

    // ========== 静态字段 ==========

    private static int craftingGridOffsetX;
    private static int craftingGridOffsetY;
    private static String memoryText = "";

    // ========== 常量 ==========

    // So mysterious, so magic.
    private static final int MAGIC_HEIGHT_NUMBER = 114 + 1;

    // ========== 数据字段 ==========

    protected final ItemRepo repo;
    private final int offsetX = 9;
    private final int lowerTextureOffset = 0;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;

    // ========== UI 控件 ==========

    private GuiTabButton craftingStatusBtn;
    private MEGuiTextField searchField;
    private GuiText myName;
    private int perRow = 9;
    private int reservedSpace = 0;
    private boolean customSortOrder = true;
    private int rows = 0;
    private GuiImgButton ViewBox;
    private GuiImgButton SortByBox;
    private GuiImgButton SortDirBox;
    private GuiImgButton searchBoxSettings;
    private GuiImgButton terminalStyleBox;
    private GuiImgButton pinsStateButton;
    private boolean isAutoFocus = false;
    private int currentMouseX = 0;
    private int currentMouseY = 0;
    private boolean delayedUpdate;

    // ========== Pin system ==========

    private PinsRows craftingPinsRows = PinsRows.DISABLED;
    private PinsRows playerPinsRows = PinsRows.DISABLED;
    private VirtualMEPinSlot[] pinSlots = null;
    private int totalPinRows = 0;

    // 类型过滤切换按钮（每种 IAEStackType 一个）
    private final List<TypeToggleButton> typeToggleButtons = new ArrayList<>();
    // 各类型启用状态缓存
    private final Map<IAEStackType<?>, Boolean> enabledTypes = new HashMap<>();

    // To make JEI look nicer. Otherwise, the buttons will make JEI in a strange place.
    protected final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    // ========== 构造 ==========

    public MUIMEMonitorablePanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    /**
     * 仅通过 Container 实例构造，host 从 container 中获取。
     * 用于子类（如 MUISecurityStationPanelImpl、MUIMEPortableCellPanelImpl）
     * 需要自行创建 Container 后再传入面板的场景。
     */
    protected MUIMEMonitorablePanel(final ContainerMEMonitorable c) {
        this(c.getPlayerInv(), c.getHost(), c);
    }

    public MUIMEMonitorablePanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te,
            final ContainerMEMonitorable c) {

        super(c);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);

        this.xSize = 185;
        this.ySize = 204;

        if (te instanceof IViewCellStorage) {
            this.xSize += 33;
        }

        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        (this.monitorableContainer = (ContainerMEMonitorable) this.inventorySlots).setGui(this);
        this.monitorableContainer.setPinsUpdateCallback(this::onPinsUpdated);

        this.viewCell = te instanceof IViewCellStorage;

        if (te instanceof TileSecurityStation) {
            this.myName = GuiText.Security;
        } else if (te instanceof WirelessTerminalGuiObject) {
            this.myName = GuiText.WirelessTerminal;
        } else if (te instanceof IPortableCell) {
            this.myName = GuiText.PortableCell;
        } else if (te instanceof IMEChest) {
            this.myName = GuiText.Chest;
        } else if (te instanceof AbstractPartTerminal) {
            this.myName = GuiText.Terminal;
        }
    }

    // ========== IMEMonitorableGuiCallback ==========

    @Override
    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.repo.postUpdate(is);
        }

        final boolean pauseEnabled = AEConfig.instance().getConfigManager()
                .getSetting(Settings.PAUSE_WHEN_HOLDING_SHIFT) == YesNo.YES;

        if (pauseEnabled && isShiftKeyDown()) {
            for (Slot slot : this.inventorySlots.inventorySlots) {
                if (slot instanceof SlotME) {
                    if (this.isPointInRegion(slot.xPos, slot.yPos, 18, 18, currentMouseX, currentMouseY)) {
                        this.delayedUpdate = true;
                        break;
                    }
                }
            }
        }

        if (!this.delayedUpdate) {
            this.repo.updateView();
            this.updateScrollBar();
        }
    }

    private void updateScrollBar() {
        this.getScrollBar().setTop(18).setLeft(175).setHeight(this.rows * 18 - 2);
        this.getScrollBar().setRange(0, (this.repo.size() + this.perRow - 1) / this.perRow - this.rows,
                Math.max(1, this.rows / 6));
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math
                .floor((double) (this.height - MAGIC_HEIGHT_NUMBER - this.reservedSpace - jeiSearchOffset) / 18);

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

        this.rows = Math.min(this.rows, this.getMaxRows());
        this.rows = Math.max(this.rows, this.getMinRows());

        // ========== Pin rows calculation ==========
        this.syncPinRowsFromContainer();
        int craftingRows = Math.min(this.craftingPinsRows.ordinal(), 16);
        int playerRows = Math.min(this.playerPinsRows.ordinal(), 16);
        int pinMaxSize = Math.max(0, this.rows - 1);
        int totalRequested = craftingRows + playerRows;
        if (totalRequested > pinMaxSize) {
            if (playerRows > pinMaxSize) {
                playerRows = pinMaxSize;
                craftingRows = 0;
            } else {
                craftingRows = Math.min(craftingRows, pinMaxSize - playerRows);
            }
        }
        this.totalPinRows = craftingRows + playerRows;
        int normalSlotRows = Math.max(0, this.rows - this.totalPinRows);

        // ========== Pin slots creation ==========
        this.pinSlots = new VirtualMEPinSlot[this.totalPinRows * this.perRow];
        final boolean playerFirst = this.monitorableContainer.getClientPinSectionOrder()
                == PinSectionOrder.PLAYER_FIRST;
        int slotIdx = 0;
        int firstRows = playerFirst ? playerRows : craftingRows;
        int secondRows = playerFirst ? craftingRows : playerRows;
        boolean firstIsCrafting = !playerFirst;
        slotIdx = createPinSection(slotIdx, firstRows, this.perRow, 0, firstIsCrafting);
        slotIdx = createPinSection(slotIdx, secondRows, this.perRow, firstRows, !firstIsCrafting);

        // ========== Normal ME slots ==========
        int normalSlotOffsetY = 18 + this.totalPinRows * 18;
        this.getMeSlots().clear();
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.getMeSlots()
                        .add(new InternalSlotME(this.repo, x + y * this.perRow,
                                this.offsetX + x * 18, normalSlotOffsetY + y * 18));
            }
        }

        super.initGui();

        // Rebuild virtual GUI slots (pin slots + normal slots)
        this.guiSlots.removeIf(s -> s instanceof VirtualMEMonitorableSlot || s instanceof VirtualMEPinSlot);
        for (VirtualMEPinSlot pinSlot : this.pinSlots) {
            this.guiSlots.add(pinSlot);
        }
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                final int idx = x + y * this.perRow;
                this.guiSlots.add(new VirtualMEMonitorableSlot(
                        idx, this.offsetX + x * 18, normalSlotOffsetY + y * 18, this.repo, idx));
            }
        }

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18 + this.reservedSpace;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        int offset = this.guiTop + 8 + jeiOffset;

        // 排序按钮
        if (this.customSortOrder) {
            this.buttonList
                    .add(this.SortByBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.SORT_BY,
                            this.configSrc.getSetting(Settings.SORT_BY)));
            offset += 20;
        }

        // 视图模式按钮
        if (this.viewCell || this instanceof MUIWirelessTermPanel) {
            this.buttonList
                    .add(this.ViewBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.VIEW_MODE,
                            this.configSrc.getSetting(Settings.VIEW_MODE)));
            offset += 20;
        }

        // 排序方向按钮
        this.buttonList.add(
                this.SortDirBox = new GuiImgButton(this.guiLeft - 18, offset, Settings.SORT_DIRECTION, this.configSrc
                        .getSetting(Settings.SORT_DIRECTION)));
        offset += 20;

        // 搜索模式按钮
        this.buttonList.add(
                this.searchBoxSettings = new GuiImgButton(this.guiLeft - 18, offset, Settings.SEARCH_MODE,
                        AEConfig.instance()
                                .getConfigManager()
                                .getSetting(Settings.SEARCH_MODE)));
        offset += 20;

        // 终端样式按钮
        if (!(this instanceof MUIPortableCellPanel) || this instanceof MUIWirelessTermPanel) {
            this.buttonList.add(this.terminalStyleBox = new GuiImgButton(this.guiLeft - 18, offset,
                    Settings.TERMINAL_STYLE, AEConfig.instance()
                            .getConfigManager()
                            .getSetting(Settings.TERMINAL_STYLE)));
            offset += 20;
        }

        // 类型过滤按钮（当注册的栈类型多于1种时显示）
        this.typeToggleButtons.clear();
        if (AEStackTypeRegistry.getAllTypes().size() > 1) {
            int typeButtonX = this.guiLeft - 18;
            for (IAEStackType<?> type : AEStackTypeRegistry.getSortedTypes()) {
                TypeToggleButton btn = new TypeToggleButton(typeButtonX, offset, type);
                btn.setTypeEnabled(this.enabledTypes.getOrDefault(type, true));
                this.typeToggleButtons.add(btn);
                this.buttonList.add(btn);
                offset += 18;
            }
        }

        // 搜索框
        this.searchField = new MEGuiTextField(this.fontRenderer, this.guiLeft + Math.max(80, this.offsetX),
                this.guiTop + 4, 90, 12);

        // Pins button (placed at bottom-right of the ME grid area)
        this.buttonList.add(
                this.pinsStateButton = new GuiImgButton(
                        this.guiLeft + 178,
                        this.guiTop + 18 + (this.rows * 18) + 25,
                        Settings.ACTIONS,
                        ActionItems.PINS));
        this.searchField.setEnableBackgroundDrawing(false);
        this.searchField.setMaxStringLength(50);
        this.searchField.setTextColor(0xFFFFFF);
        this.searchField.setSelectionColor(0xFF008000);
        this.searchField.setVisible(true);

        // 合成状态按钮
        if (this.viewCell || this instanceof MUIWirelessTermPanel) {
            this.buttonList.add(this.craftingStatusBtn = new GuiTabButton(this.guiLeft + 170, this.guiTop - 4,
                    2 + 11 * 16, GuiText.CraftingStatus.getLocal(), this.itemRender));
            this.craftingStatusBtn.setHideEdge(13);
        }

        // 搜索模式设置
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);

        this.isAutoFocus = SearchBoxMode.AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.AUTOSEARCH_KEEP == searchModeSetting
                || SearchBoxMode.JEI_AUTOSEARCH_KEEP == searchModeSetting;
        final boolean isKeepFilter = SearchBoxMode.AUTOSEARCH_KEEP == searchModeSetting
                || SearchBoxMode.JEI_AUTOSEARCH_KEEP == searchModeSetting
                || SearchBoxMode.MANUAL_SEARCH_KEEP == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH_KEEP == searchModeSetting;
        final boolean isJEIEnabled = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;

        this.searchField.setFocused(this.isAutoFocus);

        if (isJEIEnabled) {
            memoryText = Integrations.jei().getSearchText();
        }

        if (isKeepFilter && memoryText != null && !memoryText.isEmpty()) {
            this.searchField.setText(memoryText);
            this.searchField.selectAll();
            this.repo.setSearchString(memoryText);
            this.updateScrollBar();
        }

        // 合成网格偏移计算
        craftingGridOffsetX = Integer.MAX_VALUE;
        craftingGridOffsetY = Integer.MAX_VALUE;

        for (final Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot) {
                if (((Slot) s).xPos < 197) {
                    this.repositionSlot((AppEngSlot) s);
                }
            }

            if (s instanceof SlotCraftingMatrix || s instanceof SlotFakeCraftingMatrix) {
                final Slot g = (Slot) s;
                if (g.xPos > 0 && g.yPos > 0) {
                    craftingGridOffsetX = Math.min(craftingGridOffsetX, g.xPos);
                    craftingGridOffsetY = Math.min(craftingGridOffsetY, g.yPos);
                }
            }
        }

        craftingGridOffsetX -= 25;
        craftingGridOffsetY -= 6;
    }

    @Override
    protected void setupWidgets() {
        // initGui 已经处理了所有初始化，此方法留空
    }

    // ========== 绘制 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.myName.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, 4210752);

        // Draw pin slot backgrounds and icons
        if (this.pinSlots != null && this.pinSlots.length > 0) {
            VirtualMEPinSlot.drawSlotsBackground(this.pinSlots, this.mc, this.zLevel);
        }

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture(this.getBackground());
        final int x_width = 197;
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, x_width, 18);

        if (this.viewCell || (this instanceof MUISecurityStationPanel)) {
            this.drawTexturedModalRect(offsetX + x_width, offsetY + this.jeiOffset, x_width, 0, 46, 128);
        }

        for (int x = 0; x < this.rows; x++) {
            this.drawTexturedModalRect(offsetX, offsetY + 18 + x * 18, 0, 18, x_width, 18);
        }

        this.drawTexturedModalRect(offsetX, offsetY + 16 + this.rows * 18 + this.lowerTextureOffset, 0, 106 - 18 - 18,
                x_width,
                99 + this.reservedSpace - this.lowerTextureOffset);

        if (this.viewCell) {
            boolean update = false;

            for (int i = 0; i < 5; i++) {
                if (this.myCurrentViewCells[i] != this.monitorableContainer.getCellViewSlot(i).getStack()) {
                    update = true;
                    this.myCurrentViewCells[i] = this.monitorableContainer.getCellViewSlot(i).getStack();
                }
            }

            if (update) {
                this.repo.setViewCell(this.myCurrentViewCells);
            }
        }

        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    @Override
    protected boolean isPowered() {
        return this.repo.hasPower();
    }

    // ========== 输入事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.craftingStatusBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.CRAFTING_STATUS));
        }

        // Pins button: left-click cycles player rows, right-click cycles crafting rows
        if (btn == this.pinsStateButton) {
            final boolean rmb = Mouse.isButtonDown(1);
            if (rmb) {
                int c = Math.min(this.craftingPinsRows.ordinal() + 1, 16);
                if (c + this.playerPinsRows.ordinal() >= this.rows) {
                    c = 0;
                }
                this.sendPinRowsUpdate(PinsRows.fromOrdinal(c), this.playerPinsRows);
            } else {
                int p = Math.min(this.playerPinsRows.ordinal() + 1, 16);
                if (p + this.craftingPinsRows.ordinal() >= this.rows) {
                    p = 0;
                }
                this.sendPinRowsUpdate(this.craftingPinsRows, PinsRows.fromOrdinal(p));
            }
            return;
        }

        // 类型过滤切换按钮
        if (btn instanceof TypeToggleButton typeBtn) {
            typeBtn.setTypeEnabled(!typeBtn.isTypeEnabled());
            this.enabledTypes.put(typeBtn.getStackType(), typeBtn.isTypeEnabled());
            this.repo.setTypeFilter(typeBtn.getStackType(), typeBtn.isTypeEnabled());
            this.repo.updateView();
            this.updateScrollBar();
            return;
        }

        if (btn instanceof GuiImgButton iBtn) {
            final boolean backwards = Mouse.isButtonDown(1);

            if (iBtn.getSetting() != Settings.ACTIONS) {
                final Enum cv = iBtn.getCurrentValue();
                final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                        iBtn.getSetting().getPossibleValues());

                if (btn == this.terminalStyleBox) {
                    AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
                } else if (btn == this.searchBoxSettings) {
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

                if (next.getClass() == SearchBoxMode.class || next.getClass() == TerminalStyle.class) {
                    this.reinitalize();
                }
            }
        }
    }

    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchField.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchField.isMouseIn(xCoord, yCoord)) {
            this.searchField.setText("");
            this.repo.setSearchString("");
            this.updateScrollBar();
        }

        // Pin interaction: Ctrl+left-click on ME slot to pin; Shift+right-click on pin slot to unpin
        for (final GuiCustomSlot slot : this.guiSlots) {
            if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), xCoord, yCoord)) {
                if (slot instanceof VirtualMEPinSlot pinSlot && btn == 1 && isShiftKeyDown()) {
                    // Shift+right-click on pin slot: unpin
                    final IAEStack<?> stack = pinSlot.getAEStack();
                    if (stack != null) {
                        ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                        final PacketInventoryAction p = new PacketInventoryAction(
                                InventoryAction.UNSET_PIN,
                                this.inventorySlots.inventorySlots.size(), -1);
                        NetworkHandler.instance().sendToServer(p);
                        return;
                    }
                } else if (slot instanceof VirtualMEMonitorableSlot meSlot && btn == 0 && isCtrlKeyDown()) {
                    // Ctrl+left-click on normal ME slot: pin
                    final IAEStack<?> stack = meSlot.getAEStack();
                    if (stack != null) {
                        ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                        final PacketInventoryAction p = new PacketInventoryAction(
                                InventoryAction.SET_ITEM_PIN,
                                this.inventorySlots.inventorySlots.size(), -1);
                        NetworkHandler.instance().sendToServer(p);
                        return;
                    }
                }
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        memoryText = this.searchField.getText();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (AppEng.proxy.isActionKey(ActionKey.TOGGLE_FOCUS, key)) {
                this.searchField.setFocused(!this.searchField.isFocused());
                return;
            }

            if (this.searchField.isFocused() && key == Keyboard.KEY_RETURN) {
                this.searchField.setFocused(false);
                return;
            }

            if (character == ' ' && this.searchField.getText().isEmpty()) {
                return;
            }

            final boolean mouseInGui = this.isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX,
                    this.currentMouseY);
            final boolean wasSearchFieldFocused = this.searchField.isFocused();

            // 搜索框焦点优先级
            final SearchBoxFocusPriority focusPriority = (SearchBoxFocusPriority) AEConfig.instance()
                    .getConfigManager().getSetting(Settings.SEARCH_BOX_FOCUS_PRIORITY);

            if (this.isAutoFocus && !this.searchField.isFocused() && mouseInGui) {
                if (focusPriority != SearchBoxFocusPriority.NEVER) {
                    this.searchField.setFocused(true);
                }
            }

            if (this.searchField.textboxKeyTyped(character, key)) {
                this.repo.setSearchString(this.searchField.getText());
                this.updateScrollBar();
                // tell forge the key event is handled and should not be sent out
                this.keyHandled = mouseInGui;
            } else {
                if (!wasSearchFieldFocused) {
                    // prevent unhandled keys (like shift) from focusing the search field
                    searchField.setFocused(false);
                }
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    public void updateScreen() {
        this.repo.setPower(this.monitorableContainer.isPowered());
        if (this.delayedUpdate) {
            final boolean pauseEnabled = AEConfig.instance().getConfigManager()
                    .getSetting(Settings.PAUSE_WHEN_HOLDING_SHIFT) == YesNo.YES;

            if (pauseEnabled && isShiftKeyDown()) {
                this.delayedUpdate = false;
                for (Slot slot : this.inventorySlots.inventorySlots) {
                    if (slot instanceof SlotME) {
                        if (this.isPointInRegion(slot.xPos, slot.yPos, 18, 18, currentMouseX, currentMouseY)) {
                            this.delayedUpdate = true;
                            break;
                        }
                    }
                }
            } else {
                this.delayedUpdate = false;
            }
        }
        if (!this.delayedUpdate) {
            this.repo.updateView();
            this.updateScrollBar();
        }
        super.updateScreen();
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        // Ctrl+scroll over the ME grid area adjusts pin rows
        if (isCtrlKeyDown() && this.isPointInRegion(this.offsetX, 18, this.perRow * 18, this.rows * 18, x, y)) {
            final boolean rmb = Mouse.isButtonDown(1);
            final boolean ctrl = isCtrlKeyDown();
            int c = Math.min(this.craftingPinsRows.ordinal(), 16);
            int p = Math.min(this.playerPinsRows.ordinal(), 16);
            if (ctrl) {
                if (wheel < 0) {
                    c = Math.max(0, c - 1);
                } else {
                    c = Math.min(16, c + 1);
                }
            } else {
                if (wheel < 0) {
                    p = Math.max(0, p - 1);
                } else {
                    p = Math.min(16, p + 1);
                }
            }
            if (c + p < this.rows) {
                this.sendPinRowsUpdate(PinsRows.fromOrdinal(c), PinsRows.fromOrdinal(p));
            }
            return;
        }

        // Check VirtualMEMonitorableSlot scroll interaction
        for (final GuiCustomSlot slot : this.guiSlots) {
            if (slot instanceof VirtualMEMonitorableSlot virtualSlot) {
                if (this.isPointInRegion(slot.xPos(), slot.yPos(),
                        slot.getWidth(), slot.getHeight(), x, y)) {
                    final IAEStack<?> stack = virtualSlot.getAEStack();
                    if (stack instanceof IAEItemStack itemStack) {
                        ((AEBaseContainer) this.inventorySlots).setTargetStack(itemStack);
                        final InventoryAction direction = wheel > 0
                                ? InventoryAction.ROLL_DOWN
                                : InventoryAction.ROLL_UP;
                        final int times = Math.abs(wheel);
                        final int inventorySize = this.inventorySlots.inventorySlots.size();
                        for (int h = 0; h < times; h++) {
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    direction, inventorySize, -1);
                            NetworkHandler.instance().sendToServer(p);
                        }
                        return;
                    }
                }
            }
        }

        super.mouseWheelEvent(x, y, wheel);
    }

    // ========== ISortSource ==========

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

    // ========== IConfigManagerHost ==========

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.SortByBox != null) {
            this.SortByBox.set(this.configSrc.getSetting(Settings.SORT_BY));
        }

        if (this.SortDirBox != null) {
            this.SortDirBox.set(this.configSrc.getSetting(Settings.SORT_DIRECTION));
        }

        if (this.ViewBox != null) {
            this.ViewBox.set(this.configSrc.getSetting(Settings.VIEW_MODE));
        }

        this.repo.updateView();
    }

    // ========== JEI 集成 ==========

    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8 + this.jeiOffset;

        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        Rectangle sortDir = new Rectangle(guiLeft - 18, yOffset, 20, visibleButtons * 20 + visibleButtons - 2);
        exclusionArea.add(sortDir);

        if (this.viewCell) {
            Rectangle viewMode = new Rectangle(guiLeft + 205, yOffset - 4, 24,
                    19 * monitorableContainer.getViewCells().length);
            exclusionArea.add(viewMode);
        }

        return exclusionArea;
    }

    // ========== 子类可扩展方法 ==========

    // For some special cases, like the Portable Cell, which only has 63 slots.
    protected int getMaxRows() {
        return Integer.MAX_VALUE;
    }

    protected int getMinRows() {
        return 2;
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = s.getY() + this.ySize - 78 - 5;
    }

    // ========== 访问器 ==========

    int getReservedSpace() {
        return this.reservedSpace;
    }

    void setReservedSpace(final int reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public boolean isCustomSortOrder() {
        return this.customSortOrder;
    }

    void setCustomSortOrder(final boolean customSortOrder) {
        this.customSortOrder = customSortOrder;
    }

    /**
     * @return 指定类型是否在终端中启用显示
     */
    public boolean isTypeEnabled(IAEStackType<?> type) {
        return this.enabledTypes.getOrDefault(type, true);
    }

    protected ContainerMEMonitorable getMonitorableContainer() {
        return this.monitorableContainer;
    }

    protected int getRows() {
        return this.rows;
    }

    public static int getCraftingGridOffsetX() {
        return craftingGridOffsetX;
    }

    public static int getCraftingGridOffsetY() {
        return craftingGridOffsetY;
    }

    // ========== Pin system helpers ==========

    /**
     * Sync pin row configuration from the container's client-side data.
     */
    private void syncPinRowsFromContainer() {
        this.craftingPinsRows = this.monitorableContainer.getClientMaxCraftingPinRows();
        this.playerPinsRows = this.monitorableContainer.getClientMaxPlayerPinRows();
    }

    /**
     * Create pin slots for one section (crafting or player).
     *
     * @return the next available slot index
     */
    private int createPinSection(int slotIdx, int sectionRows, int pinsPerRow, int rowOffset, boolean isCrafting) {
        int baseIndex = isCrafting ? 0 : appeng.items.contents.PinList.PLAYER_OFFSET;
        for (int y = 0; y < sectionRows; y++) {
            for (int x = 0; x < pinsPerRow; x++) {
                VirtualMEPinSlot slot = new VirtualMEPinSlot(
                        this.offsetX + x * 18,
                        18 + (rowOffset + y) * 18,
                        this.monitorableContainer.getClientPinList(),
                        baseIndex + y * pinsPerRow + x,
                        isCrafting);
                this.pinSlots[slotIdx++] = slot;
            }
        }
        return slotIdx;
    }

    /**
     * Send a pin rows update packet to the server and reinitialize the GUI.
     */
    private void sendPinRowsUpdate(PinsRows craftingRows, PinsRows playerRows) {
        try {
            NetworkHandler.instance().sendToServer(new PacketPinsUpdate(craftingRows, playerRows));
        } catch (final Exception e) {
            AELog.debug(e);
        }
    }

    /**
     * Called by the container when pin data is updated from the server.
     * Triggers a GUI reinitialize to reflect the new pin layout.
     */
    public void onPinsUpdated() {
        PinsRows newCrafting = this.monitorableContainer.getClientMaxCraftingPinRows();
        PinsRows newPlayer = this.monitorableContainer.getClientMaxPlayerPinRows();
        if (newCrafting != this.craftingPinsRows || newPlayer != this.playerPinsRows) {
            this.craftingPinsRows = newCrafting;
            this.playerPinsRows = newPlayer;
            this.reinitalize();
        }
    }
}

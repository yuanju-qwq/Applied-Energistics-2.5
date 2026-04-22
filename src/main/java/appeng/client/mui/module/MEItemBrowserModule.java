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

import java.io.IOException;
import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.integration.Integrations;
import appeng.util.Platform;

/**
 * ME 物品浏览模块 — 从 GuiWirelessDualInterfaceTerminal 中提取的可复用组件。
 *
 * <p>负责：
 * <ul>
 *   <li>ItemRepo + VirtualMEMonitorableSlot 的 4x4 网格</li>
 *   <li>排序/视图/排序方向/搜索模式按钮</li>
 *   <li>搜索框 + JEI 同步</li>
 *   <li>物品面板滚动条</li>
 *   <li>面板拖拽支持</li>
 *   <li>IMEInventoryUpdateReceiver 数据转发</li>
 * </ul>
 */
public class MEItemBrowserModule implements ISortSource {

    // ========== 纹理 ==========

    private static final ResourceLocation ITEMS_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/items.png");

    // ========== 布局常量 ==========

    static final int ITEM_PANEL_WIDTH = 101;
    private static final int ITEM_PANEL_ROWS = 4;
    private static final int ITEM_PANEL_COLS = 4;
    private static final int ITEM_GRID_OFFSET_X = 5;
    private static final int ITEM_GRID_OFFSET_Y = 18;
    static final int ITEM_PANEL_HEIGHT = 96;

    // ========== 宿主接口 ==========

    /**
     * 宿主 GUI 必须实现此接口来提供模块所需的上下文。
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getXSize();

        int getYSize();

        FontRenderer getFontRenderer();

        AEBasePanel getPanel();

        IConfigManager getConfigSrc();

        List<GuiButton> getButtonList();

        /**
         * 请求宿主重新初始化 GUI。
         */
        void requestReinitialize();
    }

    // ========== 数据 ==========

    private final Host host;
    private final ItemRepo itemRepo;
    private final GuiScrollbar itemPanelScrollbar;

    private MEGuiTextField itemSearchField;
    private static String memoryText = "";

    // 按钮
    private GuiImgButton sortByBox;
    private GuiImgButton sortDirBox;
    private GuiImgButton viewBox;
    private GuiImgButton searchBoxSettings;

    // 面板拖拽
    private PatternEncodingModule.PanelDragState dragState;

    // ========== 构造 ==========

    public MEItemBrowserModule(Host host) {
        this.host = host;
        this.itemPanelScrollbar = new GuiScrollbar();
        this.itemRepo = new ItemRepo(this.itemPanelScrollbar, this);
        this.itemRepo.setRowSize(ITEM_PANEL_COLS);
    }

    // ========== 访问器 ==========

    public ItemRepo getItemRepo() {
        return itemRepo;
    }

    public GuiScrollbar getItemPanelScrollbar() {
        return itemPanelScrollbar;
    }

    public MEGuiTextField getItemSearchField() {
        return itemSearchField;
    }

    public PatternEncodingModule.PanelDragState getDragState() {
        return dragState;
    }

    /**
     * 获取面板的绝对 X 坐标。
     */
    public int getPanelAbsX() {
        return host.getGuiLeft() - ITEM_PANEL_WIDTH + (dragState != null ? dragState.getDragOffsetX() : 0);
    }

    /**
     * 获取面板的绝对 Y 坐标。
     */
    public int getPanelAbsY() {
        return host.getGuiTop() + host.getYSize() - ITEM_PANEL_HEIGHT
                + (dragState != null ? dragState.getDragOffsetY() : 0);
    }

    /**
     * 获取面板相对于 guiLeft 的 X 偏移。
     */
    public int getPanelRelX() {
        return -ITEM_PANEL_WIDTH + (dragState != null ? dragState.getDragOffsetX() : 0);
    }

    /**
     * 获取面板相对于 guiTop 的 Y 偏移。
     */
    public int getPanelRelY() {
        return host.getYSize() - ITEM_PANEL_HEIGHT + (dragState != null ? dragState.getDragOffsetY() : 0);
    }

    // ========== 初始化 ==========

    /**
     * 初始化拖拽状态。在 initGui 开始时调用。
     */
    public void initDragState() {
        this.dragState = new PatternEncodingModule.PanelDragState((mouseX, mouseY) -> {
            final int absX = getPanelAbsX();
            final int absY = getPanelAbsY();
            return mouseX >= absX && mouseX < absX + ITEM_PANEL_WIDTH
                    && mouseY >= absY && mouseY < absY + ITEM_GRID_OFFSET_Y;
        });
    }

    /**
     * 创建搜索框和按钮、VirtualMEMonitorableSlot 网格。在 initGui 中调用。
     */
    public void initPanel() {
        final int itemAbsX = getPanelAbsX();
        final int itemAbsY = getPanelAbsY();
        final int itemRelX = getPanelRelX();
        final int itemRelY = getPanelRelY();
        final List<GuiButton> buttonList = host.getButtonList();
        final IConfigManager configSrc = host.getConfigSrc();

        // 排序/视图按钮
        int sortBtnOffset = itemAbsY + 18;

        this.sortByBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_BY,
                configSrc.getSetting(Settings.SORT_BY));
        buttonList.add(this.sortByBox);
        sortBtnOffset += 20;

        this.viewBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.VIEW_MODE,
                configSrc.getSetting(Settings.VIEW_MODE));
        buttonList.add(this.viewBox);
        sortBtnOffset += 20;

        this.sortDirBox = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SORT_DIRECTION,
                configSrc.getSetting(Settings.SORT_DIRECTION));
        buttonList.add(this.sortDirBox);
        sortBtnOffset += 20;

        this.searchBoxSettings = new GuiImgButton(itemAbsX - 18, sortBtnOffset, Settings.SEARCH_MODE,
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE));
        buttonList.add(this.searchBoxSettings);

        // 搜索框
        this.itemSearchField = new MEGuiTextField(host.getFontRenderer(),
                itemAbsX + 3, itemAbsY + 4, 72, 12);
        this.itemSearchField.setEnableBackgroundDrawing(false);
        this.itemSearchField.setMaxStringLength(25);
        this.itemSearchField.setTextColor(0xFFFFFF);
        this.itemSearchField.setVisible(true);

        // SearchBoxMode JEI 同步
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

        // 清除旧的 ME 虚拟槽位
        host.getPanel().guiSlots.removeIf(s -> s instanceof VirtualMEMonitorableSlot);

        // 创建 4x4 ME 虚拟槽位
        for (int row = 0; row < ITEM_PANEL_ROWS; row++) {
            for (int col = 0; col < ITEM_PANEL_COLS; col++) {
                final int slotIdx = col + row * ITEM_PANEL_COLS;
                final int slotX = itemRelX + ITEM_GRID_OFFSET_X + col * 18;
                final int slotY = itemRelY + ITEM_GRID_OFFSET_Y + row * 18;
                host.getPanel().guiSlots.add(new VirtualMEMonitorableSlot(
                        slotIdx, slotX, slotY, this.itemRepo, slotIdx));
            }
        }

        // 设置滚动条
        this.itemPanelScrollbar.setLeft(itemRelX + ITEM_PANEL_WIDTH - 14)
                .setTop(itemRelY + ITEM_GRID_OFFSET_Y)
                .setHeight(ITEM_PANEL_ROWS * 18 - 2);
        this.updateItemPanelScrollbar();

        this.itemRepo.setPower(true);
    }

    // ========== 滚动条 ==========

    public void updateItemPanelScrollbar() {
        this.itemPanelScrollbar.setRange(0,
                (this.itemRepo.size() + ITEM_PANEL_COLS - 1) / ITEM_PANEL_COLS - ITEM_PANEL_ROWS,
                Math.max(1, ITEM_PANEL_ROWS / 6));
    }

    // ========== IMEInventoryUpdateReceiver 数据接收 ==========

    /**
     * 接收 ME 网络库存变化通知，更新 ItemRepo。
     * 由宿主的 IMEInventoryUpdateReceiver.postUpdate 转发调用。
     */
    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.itemRepo.postUpdate(is);
        }
        this.itemRepo.updateView();
        this.updateItemPanelScrollbar();
    }

    // ========== 渲染: drawBG ==========

    /**
     * 绘制 ME 物品浏览面板的背景。
     */
    public void drawBG(int offsetX, int offsetY) {
        final int panelX = getPanelAbsX();
        final int panelY = getPanelAbsY();

        GlStateManager.color(1, 1, 1, 1);
        host.getPanel().mc.getTextureManager().bindTexture(ITEMS_TEXTURE);
        host.getPanel().drawTexturedModalRect(panelX, panelY, 0, 0, ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT);

        // 绘制滚动条
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        this.itemPanelScrollbar.draw(host.getPanel());
        GlStateManager.popMatrix();

        // 搜索框
        if (this.itemSearchField != null) {
            this.itemSearchField.drawTextBox();
        }
    }

    // ========== drawScreen: 按钮重建 ==========

    /**
     * 在 drawScreen 中调用，将按钮添加到 buttonList。
     * 在 buttonList.clear() 之后调用。
     */
    public void populateButtons() {
        final List<GuiButton> buttonList = host.getButtonList();
        addIfNotNull(buttonList, this.sortByBox);
        addIfNotNull(buttonList, this.sortDirBox);
        addIfNotNull(buttonList, this.viewBox);
        addIfNotNull(buttonList, this.searchBoxSettings);
    }

    private static void addIfNotNull(List<GuiButton> list, GuiButton btn) {
        if (btn != null) {
            list.add(btn);
        }
    }

    // ========== 输入处理: actionPerformed ==========

    /**
     * 处理 ME 面板的按钮点击（排序/视图/搜索模式）。
     *
     * @return true 如果事件被消费
     */
    public boolean actionPerformed(GuiButton btn) {
        if (!(btn instanceof GuiImgButton iBtn) || iBtn.getSetting() == Settings.ACTIONS) {
            return false;
        }

        final boolean backwards = org.lwjgl.input.Mouse.isButtonDown(1);
        final Enum cv = iBtn.getCurrentValue();
        final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                iBtn.getSetting().getPossibleValues());

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
            host.requestReinitialize();
        }
        return true;
    }

    // ========== 输入处理: keyTyped ==========

    /**
     * 处理键盘事件（ME 搜索框输入）。
     *
     * @return true 如果事件被消费
     */
    public boolean keyTyped(char character, int key) {
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
            return true;
        }
        return false;
    }

    // ========== 输入处理: mouseClicked ==========

    /**
     * 处理鼠标点击（搜索框焦点 + 右键清除）。
     */
    public void mouseClicked(int xCoord, int yCoord, int btn) {
        if (this.itemSearchField != null) {
            this.itemSearchField.mouseClicked(xCoord, yCoord, btn);
            if (btn == 1 && this.isMouseOverSearchField(xCoord, yCoord)) {
                this.itemSearchField.setText("");
                this.itemRepo.setSearchString("");
                this.itemRepo.updateView();
                this.updateItemPanelScrollbar();
            }
        }
    }

    // ========== 输入处理: mouseWheel ==========

    /**
     * 处理鼠标滚轮（ME 面板区域内的滚动）。
     *
     * @return true 如果事件被消费
     */
    public boolean mouseWheelEvent(int x, int y, int wheel) {
        final int panelX = getPanelAbsX();
        final int panelY = getPanelAbsY();

        if (x >= panelX && x < panelX + ITEM_PANEL_WIDTH
                && y >= panelY && y < panelY + ITEM_PANEL_HEIGHT) {
            this.itemPanelScrollbar.wheel(wheel);
            this.itemRepo.updateView();
            return true;
        }
        return false;
    }

    /**
     * 尝试处理滚动条的鼠标拖动。
     *
     * @return true 如果滚动条滚动位置发生了改变
     */
    public boolean handleScrollbarClick(int mouseX, int mouseY) {
        final int oldScroll = this.itemPanelScrollbar.getCurrentScroll();
        this.itemPanelScrollbar.click(host.getPanel(), mouseX - host.getGuiLeft(), mouseY - host.getGuiTop());
        if (oldScroll != this.itemPanelScrollbar.getCurrentScroll()) {
            this.itemRepo.updateView();
            return true;
        }
        return false;
    }

    // ========== GUI 关闭 ==========

    /**
     * 保存搜索框文本到 memoryText。在 onGuiClosed 中调用。
     */
    public void onGuiClosed() {
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

    // ========== 搜索框焦点管理 ==========

    /**
     * 搜索框是否有焦点。
     */
    public boolean isSearchFieldFocused() {
        return this.itemSearchField != null && this.itemSearchField.isFocused();
    }

    /**
     * 设置搜索框焦点。
     */
    public void setSearchFieldFocused(boolean focused) {
        if (this.itemSearchField != null) {
            this.itemSearchField.setFocused(focused);
        }
    }

    // ========== IConfigManagerHost 回调 ==========

    /**
     * 配置变更时更新按钮和视图。
     * 由宿主的 IConfigManagerHost.updateSetting 转发调用。
     */
    public void updateSetting() {
        final IConfigManager configSrc = host.getConfigSrc();
        if (this.sortByBox != null) {
            this.sortByBox.set(configSrc.getSetting(Settings.SORT_BY));
        }
        if (this.sortDirBox != null) {
            this.sortDirBox.set(configSrc.getSetting(Settings.SORT_DIRECTION));
        }
        if (this.viewBox != null) {
            this.viewBox.set(configSrc.getSetting(Settings.VIEW_MODE));
        }
        this.itemRepo.updateView();
    }

    // ========== ISortSource 实现 ==========

    @Override
    public Enum getSortBy() {
        return host.getConfigSrc().getSetting(Settings.SORT_BY);
    }

    @Override
    public Enum getSortDir() {
        return host.getConfigSrc().getSetting(Settings.SORT_DIRECTION);
    }

    @Override
    public Enum getSortDisplay() {
        return host.getConfigSrc().getSetting(Settings.VIEW_MODE);
    }

    // ========== 区域检测 ==========

    private boolean isMouseOverSearchField(int mouseX, int mouseY) {
        final int itemAbsX = getPanelAbsX();
        final int itemAbsY = getPanelAbsY();
        return mouseX >= itemAbsX + 3 && mouseX < itemAbsX + 3 + 72
                && mouseY >= itemAbsY + 4 && mouseY < itemAbsY + 4 + 12;
    }

    /**
     * 获取面板的绝对屏幕坐标（JEI 排除区用）。
     */
    public java.awt.Rectangle getJEIExclusionRect() {
        return new java.awt.Rectangle(getPanelAbsX(), getPanelAbsY(), ITEM_PANEL_WIDTH, ITEM_PANEL_HEIGHT);
    }
}

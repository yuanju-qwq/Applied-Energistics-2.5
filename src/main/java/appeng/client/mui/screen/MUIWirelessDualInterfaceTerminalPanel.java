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
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Settings;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.mui.AEBaseMEPanel;
import appeng.client.mui.module.InterfaceListModule;
import appeng.client.mui.module.MEItemBrowserModule;
import appeng.client.mui.module.PatternEncodingModule;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * MUI 版无线双接口终端面板。
 * <p>
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiWirelessDualInterfaceTerminal}。
 * 采用模块化组合架构，由三个独立模块组成：
 * <ul>
 *   <li>{@link InterfaceListModule} — 接口列表面板（中央区域，滚动列表+搜索+高亮）</li>
 *   <li>{@link PatternEncodingModule} — 样板编码面板（右侧，编码按钮+输入输出网格）</li>
 *   <li>{@link MEItemBrowserModule} — ME物品浏览面板（左侧，4x4网格+搜索+排序）</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUIWirelessDualInterfaceTerminalPanel extends AEBaseMEPanel
        implements IInterfaceTerminalGuiCallback,
        ContainerWirelessDualInterfaceTerminal.IMEInventoryUpdateReceiver,
        IConfigManagerHost,
        InterfaceListModule.Host,
        PatternEncodingModule.Host,
        MEItemBrowserModule.Host {

    // ========== 常量 ==========

    private static final int MAIN_GUI_WIDTH = 208;

    // JEI 偏移量
    private final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    // ========== 数据 ==========

    private final ContainerWirelessDualInterfaceTerminal dualContainer;
    private final IConfigManager configSrc;

    // 三大模块
    private InterfaceListModule interfaceListModule;
    private PatternEncodingModule patternEncodingModule;
    private MEItemBrowserModule meItemBrowserModule;

    // 无线终端共通功能（无线升级图标）
    private final WirelessTerminalHelper wirelessHelper = new WirelessTerminalHelper();

    // ========== 构造 ==========

    public MUIWirelessDualInterfaceTerminalPanel(final ContainerWirelessDualInterfaceTerminal container) {
        super(container);

        this.dualContainer = container;
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        container.setMeGui(this);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        this.xSize = MAIN_GUI_WIDTH;
        this.ySize = 255;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);

        // 创建模块
        this.interfaceListModule = new InterfaceListModule(this);
        this.interfaceListModule.setEnableDoubleButton(true);

        this.patternEncodingModule = new PatternEncodingModule(this);
        this.patternEncodingModule.initDragState();

        this.meItemBrowserModule = new MEItemBrowserModule(this);
        this.meItemBrowserModule.initDragState();

        // 计算行数
        this.interfaceListModule.calculateRows();
        final int rows = this.interfaceListModule.getRows();

        super.initGui();

        // 设置面板尺寸
        final int MAGIC_HEIGHT_NUMBER = 52 + 99;
        this.ySize = MAGIC_HEIGHT_NUMBER + rows * 18;
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));

        // 初始化三个模块
        this.interfaceListModule.initSearchFieldsAndButtons();
        this.patternEncodingModule.initButtons();
        this.meItemBrowserModule.initPanel();

        // 定位槽位
        this.patternEncodingModule.repositionSlots();
        this.repositionPlayerSlots();
    }

    @Override
    protected void setupWidgets() {
        // initGui 已处理所有初始化
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.onGuiClosed();
        }
    }

    private void repositionPlayerSlots() {
        for (final Object obj : this.inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (slot.isPlayerSide()) {
                    slot.yPos = this.ySize + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== 回调接口实现 ==========

    // --- IInterfaceTerminalGuiCallback ---

    @Override
    public void postUpdate(final NBTTagCompound in) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.postUpdate(in);
        }
    }

    // --- IMEInventoryUpdateReceiver ---

    @Override
    public void postUpdate(final List<IAEStack<?>> list) {
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.postUpdate(list);
        }
    }

    // --- IConfigManagerHost ---

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.updateSetting();
        }
    }

    // ========== 渲染 ==========

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // 无线升级图标
        this.wirelessHelper.drawWirelessIcon(offsetX, offsetY, 198, 127);

        // 接口列表面板背景
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawBG(offsetX, offsetY);
        }

        // 样板编码面板背景
        if (this.patternEncodingModule != null) {
            this.patternEncodingModule.drawBG(offsetX, offsetY);
        }

        // ME物品浏览面板背景
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.drawBG(offsetX, offsetY);
        }
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // 接口列表面板前景
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawFG(offsetX, offsetY,
                    this.getGuiDisplayName(GuiText.InterfaceTerminal.getLocal()));
        }

        // 样板编码面板前景
        if (this.patternEncodingModule != null) {
            this.patternEncodingModule.drawFG();
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        // 清除并重建 buttonList
        this.buttonList.clear();

        // 各模块填充按钮和槽位
        if (this.interfaceListModule != null) {
            this.interfaceListModule.populateDynamicSlots();
        }
        if (this.patternEncodingModule != null) {
            this.patternEncodingModule.populateButtons();
        }
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.populateButtons();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // 搜索框 tooltip
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawSearchFieldTooltips(this, mouseX, mouseY);
        }
    }

    // ========== 输入事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        // 接口列表模块的按钮
        if (this.interfaceListModule != null && this.interfaceListModule.actionPerformed(btn, this.selectedButton)) {
            return;
        }

        // 加倍按钮
        if (this.interfaceListModule != null
                && this.interfaceListModule.getDoubleButtonHashMap().containsKey(btn)) {
            final var inv = this.interfaceListModule.getDoubleButtonHashMap().get(btn);
            if (inv != null) {
                try {
                    appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                            new appeng.core.sync.packets.PacketValueConfig(
                                    "WirelessDualInterface.Double", String.valueOf(inv.getId())));
                } catch (IOException e) {
                    // ignore
                }
            }
            return;
        }

        // 样板编码模块的按钮
        if (this.patternEncodingModule != null && this.patternEncodingModule.actionPerformed(btn)) {
            return;
        }

        // ME物品浏览模块的按钮
        if (this.meItemBrowserModule != null && this.meItemBrowserModule.actionPerformed(btn)) {
            return;
        }
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // 接口列表搜索框
        if (this.interfaceListModule != null) {
            this.interfaceListModule.mouseClicked(xCoord, yCoord, btn);
        }

        // ME物品搜索框
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.mouseClicked(xCoord, yCoord, btn);
        }

        // 面板拖拽（中键）
        if (btn == 2) {
            if (this.patternEncodingModule != null && this.patternEncodingModule.getDragState() != null
                    && this.patternEncodingModule.getDragState().isInDragArea(xCoord, yCoord)) {
                this.patternEncodingModule.getDragState().startDrag(xCoord, yCoord);
                return;
            }
            if (this.meItemBrowserModule != null && this.meItemBrowserModule.getDragState() != null
                    && this.meItemBrowserModule.getDragState().isInDragArea(xCoord, yCoord)) {
                this.meItemBrowserModule.getDragState().startDrag(xCoord, yCoord);
                return;
            }
        }

        // 样板编码滚动条点击
        if (this.patternEncodingModule != null && this.patternEncodingModule.handleScrollbarClick(xCoord, yCoord)) {
            return;
        }

        // ME物品浏览滚动条点击
        if (this.meItemBrowserModule != null && this.meItemBrowserModule.handleScrollbarClick(xCoord, yCoord)) {
            return;
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        // 面板拖拽更新
        if (clickedMouseButton == 2) {
            if (this.patternEncodingModule != null && this.patternEncodingModule.getDragState() != null
                    && this.patternEncodingModule.getDragState().isDragging()) {
                this.patternEncodingModule.getDragState().updateDrag(mouseX, mouseY);
                this.patternEncodingModule.repositionSlots();
                return;
            }
            if (this.meItemBrowserModule != null && this.meItemBrowserModule.getDragState() != null
                    && this.meItemBrowserModule.getDragState().isDragging()) {
                this.meItemBrowserModule.getDragState().updateDrag(mouseX, mouseY);
                return;
            }
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        // 结束拖拽
        if (state == 2) {
            if (this.patternEncodingModule != null && this.patternEncodingModule.getDragState() != null) {
                this.patternEncodingModule.getDragState().endDrag();
            }
            if (this.meItemBrowserModule != null && this.meItemBrowserModule.getDragState() != null) {
                this.meItemBrowserModule.getDragState().endDrag();
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        // 接口列表搜索框
        if (this.interfaceListModule != null && this.interfaceListModule.keyTyped(character, key)) {
            return;
        }

        // ME物品搜索框
        if (this.meItemBrowserModule != null && this.meItemBrowserModule.keyTyped(character, key)) {
            return;
        }

        if (!this.checkHotbarKeys(key)) {
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        // 样板编码面板滚轮
        if (this.patternEncodingModule != null && this.patternEncodingModule.mouseWheelEvent(x, y, wheel)) {
            return;
        }

        // ME物品浏览面板滚轮
        if (this.meItemBrowserModule != null && this.meItemBrowserModule.mouseWheelEvent(x, y, wheel)) {
            return;
        }

        // 接口列表主滚动条滚轮
        super.mouseWheelEvent(x, y, wheel);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // 更新样板编码模块（包括 PlacePattern 和槽位重定位）
        if (this.patternEncodingModule != null) {
            this.patternEncodingModule.updateScreen();
        }
    }

    // ========== JEI 兼容 ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> exclusionArea = new ArrayList<>();

        // 接口列表左侧按钮区域
        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        if (visibleButtons > 0) {
            exclusionArea.add(new Rectangle(guiLeft - 18, guiTop + 8 + jeiOffset, 20,
                    visibleButtons * 20 + visibleButtons - 2));
        }

        // 样板编码面板区域
        if (this.patternEncodingModule != null) {
            exclusionArea.add(this.patternEncodingModule.getJEIExclusionRect());
        }

        // ME物品浏览面板区域
        if (this.meItemBrowserModule != null) {
            exclusionArea.add(this.meItemBrowserModule.getJEIExclusionRect());
        }

        return exclusionArea;
    }

    // ========== InterfaceListModule.Host 实现 ==========

    @Override
    public int getScreenWidth() {
        return this.width;
    }

    @Override
    public int getScreenHeight() {
        return this.height;
    }

    @Override
    public GuiScrollbar getInterfaceScrollBar() {
        return this.getScrollBar();
    }

    @Override
    public List<GuiButton> getButtonList() {
        return this.buttonList;
    }

    @Override
    public AEBaseMEPanel getPanel() {
        return this;
    }

    @Override
    public void requestReinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void bindTexture(String file) {
        super.bindTexture(file);
    }

    @Override
    public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int w, int h) {
        super.drawTexturedModalRect(x, y, textureX, textureY, w, h);
    }

    @Override
    public int getJeiOffset() {
        return this.jeiOffset;
    }

    // ========== PatternEncodingModule.Host 实现 ==========

    @Override
    public ContainerWirelessDualInterfaceTerminal getDualContainer() {
        return this.dualContainer;
    }

    @Override
    public net.minecraft.client.renderer.RenderItem getItemRenderer() {
        return this.itemRender;
    }

    @Override
    public InterfaceListModule getInterfaceListModule() {
        return this.interfaceListModule;
    }

    // ========== MEItemBrowserModule.Host 实现 ==========

    @Override
    public IConfigManager getConfigSrc() {
        return this.configSrc;
    }
}

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

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.helpers.ItemStackHelper.stackFromNBT;

import java.io.IOException;
import java.util.*;

import com.google.common.collect.HashMultimap;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.TerminalStyle;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTooltipTextField;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.mui.AEBasePanel;
import appeng.core.AEConfig;
import appeng.core.AppEng;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.PatternProviderLogic;
import appeng.helpers.PatternHelper;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import appeng.util.item.AEItemStackType;

/**
 * 接口列表模块 — 从 MUIInterfaceTerminalPanel / GuiWirelessDualInterfaceTerminal 中提取的可复用组件。
 *
 * <p>负责：
 * <ul>
 *   <li>管理 byId / providerById 数据（通过 {@link #postUpdate(NBTTagCompound)} 接收服务端同步）</li>
 *   <li>三个搜索字段（输入/输出/名称）及过滤按钮（装配器过滤/空位过滤/坏配方过滤/终端样式）</li>
 *   <li>刷新列表、滚动条管理</li>
 *   <li>drawBG/drawFG 中渲染接口列表行</li>
 *   <li>drawScreen 中动态创建 SlotDisconnected 和高亮按钮</li>
 *   <li>键盘/鼠标事件转发</li>
 *   <li>方块高亮定位</li>
 * </ul>
 *
 * <p>宿主 GUI 通过 {@link Host} 接口提供上下文。
 */
public class InterfaceListModule {

    // ========== 常量 ==========

    private static final int OFFSET_X = 21;
    private static final int MAIN_GUI_WIDTH = 208;
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    // ========== 宿主接口 ==========

    /**
     * 宿主 GUI 必须实现此接口来提供模块所需的上下文。
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getScreenWidth();

        int getScreenHeight();

        int getXSize();

        int getYSize();

        FontRenderer getFontRenderer();

        GuiScrollbar getInterfaceScrollBar();

        List<GuiButton> getButtonList();

        AEBasePanel getPanel();

        /**
         * 请求宿主重新初始化 GUI。
         */
        void requestReinitialize();

        /**
         * 绑定纹理。
         */
        void bindTexture(String file);

        /**
         * 在指定位置绘制纹理矩形。
         */
        void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height);

        /**
         * 获取 JEI 偏移量（如果 JEI 启用则为 24，否则为 0）。
         */
        int getJeiOffset();
    }

    // ========== 数据存储 ==========

    private final Host host;

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

    private MEGuiTooltipTextField searchFieldOutputs;
    private MEGuiTooltipTextField searchFieldInputs;
    private MEGuiTooltipTextField searchFieldNames;

    private GuiImgButton guiButtonHideFull;
    private GuiImgButton guiButtonAssemblersOnly;
    private GuiImgButton guiButtonBrokenRecipes;
    private GuiImgButton terminalStyleBox;

    private boolean refreshList = false;
    private boolean onlyShowWithSpace = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;
    private int rows = 6;

    /** 是否启用每行"加倍"按钮（WirelessDualInterfaceTerminal 独有功能） */
    private boolean enableDoubleButton = false;

    // ========== 构造 ==========

    public InterfaceListModule(Host host) {
        this.host = host;
    }

    // ========== 配置 ==========

    /**
     * 设置是否启用每行"加倍"按钮。
     * WirelessDualInterfaceTerminal 使用此功能，普通接口终端不使用。
     */
    public void setEnableDoubleButton(boolean enable) {
        this.enableDoubleButton = enable;
    }

    // ========== 访问器 ==========

    public int getRows() {
        return rows;
    }

    public ArrayList<Object> getLines() {
        return lines;
    }

    public HashMap<GuiButton, ClientDCInternalInv> getGuiButtonHashMap() {
        return guiButtonHashMap;
    }

    public HashMap<GuiButton, ClientDCInternalInv> getDoubleButtonHashMap() {
        return doubleButtonHashMap;
    }

    public Map<ClientDCInternalInv, Integer> getNumUpgradesMap() {
        return numUpgradesMap;
    }

    public HashMap<ClientDCInternalInv, BlockPos> getBlockPosHashMap() {
        return blockPosHashMap;
    }

    public Map<ClientDCInternalInv, Integer> getDimHashMap() {
        return dimHashMap;
    }

    public HashMap<Long, ClientDCInternalInv> getById() {
        return byId;
    }

    public MEGuiTooltipTextField getSearchFieldNames() {
        return searchFieldNames;
    }

    // ========== 搜索字段创建 ==========

    private MEGuiTooltipTextField createTextField(final int width, final int height, final String tooltip) {
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

    // ========== 初始化 ==========

    /**
     * 计算行数（根据终端样式和屏幕高度）。
     * 必须在 initGui 流程中调用。
     */
    public void calculateRows() {
        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math.floor(
                (double) (host.getScreenHeight() - MAGIC_HEIGHT_NUMBER - jeiSearchOffset) / 18);

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
    }

    /**
     * 初始化搜索字段和过滤按钮。
     * 必须在 calculateRows 之后、宿主设置 ySize/guiTop 之后调用。
     */
    public void initSearchFieldsAndButtons() {
        final int guiLeft = host.getGuiLeft();
        final int guiTop = host.getGuiTop();

        searchFieldInputs = createTextField(86, 12, ButtonToolTips.SearchFieldInputs.getLocal());
        searchFieldOutputs = createTextField(86, 12, ButtonToolTips.SearchFieldOutputs.getLocal());
        searchFieldNames = createTextField(71, 12, ButtonToolTips.SearchFieldNames.getLocal());

        searchFieldInputs.x = guiLeft + 32;
        searchFieldInputs.y = guiTop + 25;
        searchFieldOutputs.x = guiLeft + 32;
        searchFieldOutputs.y = guiTop + 38;
        searchFieldNames.x = guiLeft + 32 + 99;
        searchFieldNames.y = guiTop + 38;

        searchFieldNames.setFocused(true);

        guiButtonAssemblersOnly = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonHideFull = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        guiButtonBrokenRecipes = new GuiImgButton(0, 0, Settings.ACTIONS, null);
        terminalStyleBox = new GuiImgButton(0, 0, Settings.TERMINAL_STYLE, null);

        terminalStyleBox.x = guiLeft - 18;
        terminalStyleBox.y = guiTop + 8 + host.getJeiOffset();
        guiButtonBrokenRecipes.x = guiLeft - 18;
        guiButtonBrokenRecipes.y = terminalStyleBox.y + 20;
        guiButtonHideFull.x = guiLeft - 18;
        guiButtonHideFull.y = guiButtonBrokenRecipes.y + 20;
        guiButtonAssemblersOnly.x = guiLeft - 18;
        guiButtonAssemblersOnly.y = guiButtonHideFull.y + 20;

        this.updateScrollBar();
    }

    // ========== 滚动条 ==========

    public void updateScrollBar() {
        GuiScrollbar sb = host.getInterfaceScrollBar();
        sb.setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        sb.setRange(0, this.lines.size() - 1, 1);
    }

    // ========== 渲染: drawBG ==========

    /**
     * 绘制接口列表的背景部分（行背景 + 槽位背景 + 搜索框）。
     * 不包括顶部和底部（由宿主绘制）。
     */
    public void drawBG(int offsetX, int offsetY) {
        host.bindTexture("guis/newinterfaceterminal.png");

        // 顶部背景
        host.drawTexturedModalRect(offsetX, offsetY, 0, 0, MAIN_GUI_WIDTH, 53);

        // 行背景
        for (int x = 0; x < this.rows; x++) {
            host.drawTexturedModalRect(offsetX, offsetY + 53 + x * 18, 0, 52, MAIN_GUI_WIDTH, 18);
        }

        // 槽位背景
        int offset = 51;
        final int ex = host.getInterfaceScrollBar().getCurrentScroll();
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
                        host.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 173, actualWidth, 18);
                    }

                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }

        // 底部背景（玩家物品栏）
        host.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, MAIN_GUI_WIDTH, 99);

        // 搜索框
        this.searchFieldInputs.drawTextBox();
        this.searchFieldOutputs.drawTextBox();
        this.searchFieldNames.drawTextBox();
    }

    // ========== 渲染: drawFG ==========

    /**
     * 绘制接口列表的前景部分（标题、接口名、匹配高亮）。
     */
    public void drawFG(int offsetX, int offsetY, String title) {
        FontRenderer fr = host.getFontRenderer();
        fr.drawString(title, OFFSET_X + 2, 6, 4210752);
        fr.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, host.getYSize() - 96, 4210752);

        final int currentScroll = host.getInterfaceScrollBar().getCurrentScroll();

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
                                AEBasePanel.drawRect(z * 18 + 22, 1 + offset, z * 18 + 22 + 16,
                                        1 + offset + 16, 0x2A00FF00);
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

                while (name.length() > 2 && fr.getStringWidth(name) > 158) {
                    name = name.substring(0, name.length() - 1);
                }
                fr.drawString(name, OFFSET_X + 3, 6 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }
    }

    // ========== 渲染: drawScreen（动态槽位/按钮创建） ==========

    /**
     * 在 drawScreen 中调用，创建动态 SlotDisconnected 和高亮/加倍按钮。
     * 必须在 buttonList.clear() 之后、super.drawScreen() 之前调用。
     */
    public void populateDynamicSlots() {
        final List<GuiButton> buttonList = host.getButtonList();
        final AEBasePanel panel = host.getPanel();

        guiButtonHashMap.clear();
        doubleButtonHashMap.clear();
        panel.inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        guiButtonAssemblersOnly.set(
                onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLERS_ON
                        : ActionItems.MOLECULAR_ASSEMBLERS_OFF);
        guiButtonHideFull.set(onlyShowWithSpace ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON
                : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF);
        terminalStyleBox.set(AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));

        buttonList.add(guiButtonAssemblersOnly);
        buttonList.add(guiButtonHideFull);
        buttonList.add(guiButtonBrokenRecipes);
        buttonList.add(terminalStyleBox);

        int offset = 51;
        final int currentScroll = host.getInterfaceScrollBar().getCurrentScroll();
        int linesDraw = 0;

        final int guiLeft = host.getGuiLeft();
        final int guiTop = host.getGuiTop();

        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset + 1, Settings.ACTIONS,
                        ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                buttonList.add(guiButton);

                if (enableDoubleButton) {
                    GuiImgButton interfaceDoubleBtn = new GuiImgButton(guiLeft + 8, guiTop + offset + 10,
                            Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
                    interfaceDoubleBtn.setHalfSize(true);
                    doubleButtonHashMap.put(interfaceDoubleBtn, inv);
                    buttonList.add(interfaceDoubleBtn);
                }

                final int extraLines = numUpgradesMap.get(inv);
                final int slotLimit = inv.getInventory().getSlots();

                for (int row = 0; row < 1 + extraLines && linesDraw < rows; ++row) {
                    final int baseSlot = row * 9;

                    for (int z = 0; z < 9; z++) {
                        final int slotIndex = baseSlot + z;
                        if (slotIndex < slotLimit) {
                            panel.inventorySlots.inventorySlots.add(
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
    }

    /**
     * 绘制搜索框 tooltip。在 super.drawScreen 之后调用。
     */
    public void drawSearchFieldTooltips(AEBasePanel panel, int mouseX, int mouseY) {
        panel.drawTooltip(searchFieldInputs, mouseX, mouseY);
        panel.drawTooltip(searchFieldOutputs, mouseX, mouseY);
        panel.drawTooltip(searchFieldNames, mouseX, mouseY);
    }

    // ========== 输入处理 ==========

    /**
     * 处理鼠标点击（搜索框焦点）。
     */
    public void mouseClicked(int xCoord, int yCoord, int btn) {
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldOutputs.mouseClicked(xCoord, yCoord, btn);
        this.searchFieldNames.mouseClicked(xCoord, yCoord, btn);
    }

    /**
     * 处理键盘事件。
     *
     * @return true 如果事件被消费（不应传递给宿主 super.keyTyped）
     */
    public boolean keyTyped(char character, int key) {
        if (character == ' ') {
            if ((this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused())
                    || (this.searchFieldOutputs.getText().isEmpty() && this.searchFieldOutputs.isFocused())
                    || (this.searchFieldNames.getText().isEmpty() && this.searchFieldNames.isFocused())) {
                return true;
            }
        } else if (character == '\t') {
            if (handleTab()) {
                return true;
            }
        }

        if (this.searchFieldInputs.textboxKeyTyped(character, key)
                || this.searchFieldOutputs.textboxKeyTyped(character, key)
                || this.searchFieldNames.textboxKeyTyped(character, key)) {
            this.refreshList();
            return true;
        }

        return false;
    }

    /**
     * 处理按钮点击。
     *
     * @return true 如果事件被消费
     */
    public boolean actionPerformed(GuiButton btn, GuiButton selectedButton) {
        if (guiButtonHashMap.containsKey(btn)) {
            ClientDCInternalInv inv = guiButtonHashMap.get(selectedButton);
            if (inv == null) {
                return true;
            }
            BlockPos blockPos = blockPosHashMap.get(inv);
            BlockPos blockPos2 = host.getPanel().mc.player.getPosition();
            int playerDim = host.getPanel().mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.getOrDefault(inv, playerDim);
            if (playerDim != interfaceDim) {
                try {
                    host.getPanel().mc.player.sendStatusMessage(
                            PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim,
                                    DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()),
                            false);
                } catch (Exception e) {
                    host.getPanel().mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
                }
            } else {
                hilightBlock(blockPos,
                        System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
                host.getPanel().mc.player.sendStatusMessage(
                        PlayerMessages.InterfaceHighlighted.get(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                        false);
            }
            host.getPanel().mc.player.closeScreen();
            return true;
        }

        if (btn == guiButtonHideFull) {
            onlyShowWithSpace = !onlyShowWithSpace;
            this.refreshList();
            return true;
        }
        if (btn == guiButtonAssemblersOnly) {
            onlyMolecularAssemblers = !onlyMolecularAssemblers;
            this.refreshList();
            return true;
        }
        if (btn == guiButtonBrokenRecipes) {
            onlyBrokenRecipes = !onlyBrokenRecipes;
            this.refreshList();
            return true;
        }

        if (btn instanceof GuiImgButton iBtn && iBtn.getSetting() != Settings.ACTIONS) {
            if (btn == this.terminalStyleBox) {
                final Enum<?> cv = iBtn.getCurrentValue();
                final boolean backwards = Mouse.isButtonDown(1);
                final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                        iBtn.getSetting().getPossibleValues());
                AEConfig.instance().getConfigManager().putSetting(iBtn.getSetting(), next);
                iBtn.set(next);
                host.requestReinitialize();
                return true;
            }
        }

        return false;
    }

    // ========== Tab 键循环 ==========

    /**
     * Tab 键循环搜索框焦点。
     *
     * @return true 如果焦点切换成功
     */
    public boolean handleTab() {
        if (searchFieldInputs.isFocused()) {
            searchFieldInputs.setFocused(false);
            if (AEBasePanel.isShiftKeyDown()) {
                searchFieldNames.setFocused(true);
            } else {
                searchFieldOutputs.setFocused(true);
            }
            return true;
        } else if (searchFieldOutputs.isFocused()) {
            searchFieldOutputs.setFocused(false);
            if (AEBasePanel.isShiftKeyDown()) {
                searchFieldInputs.setFocused(true);
            } else {
                searchFieldNames.setFocused(true);
            }
            return true;
        } else if (searchFieldNames.isFocused()) {
            searchFieldNames.setFocused(false);
            if (AEBasePanel.isShiftKeyDown()) {
                searchFieldOutputs.setFocused(true);
            } else {
                searchFieldInputs.setFocused(true);
            }
            return true;
        }
        return false;
    }

    /**
     * 检查任一搜索框是否有焦点。
     */
    public boolean isAnySearchFieldFocused() {
        return (searchFieldInputs != null && searchFieldInputs.isFocused())
                || (searchFieldOutputs != null && searchFieldOutputs.isFocused())
                || (searchFieldNames != null && searchFieldNames.isFocused());
    }

    /**
     * 获取当前有焦点的搜索框索引（0=Inputs, 1=Outputs, 2=Names, -1=无）。
     */
    public int getFocusedFieldIndex() {
        if (searchFieldInputs != null && searchFieldInputs.isFocused()) return 0;
        if (searchFieldOutputs != null && searchFieldOutputs.isFocused()) return 1;
        if (searchFieldNames != null && searchFieldNames.isFocused()) return 2;
        return -1;
    }

    /**
     * 设置搜索框焦点。
     */
    public void setFocusedField(int index) {
        if (searchFieldInputs != null) searchFieldInputs.setFocused(index == 0);
        if (searchFieldOutputs != null) searchFieldOutputs.setFocused(index == 1);
        if (searchFieldNames != null) searchFieldNames.setFocused(index == 2);
    }

    /**
     * 取消所有搜索框焦点。
     */
    public void clearFocus() {
        if (searchFieldInputs != null) searchFieldInputs.setFocused(false);
        if (searchFieldOutputs != null) searchFieldOutputs.setFocused(false);
        if (searchFieldNames != null) searchFieldNames.setFocused(false);
    }

    // ========== 数据更新 ==========

    /**
     * 处理来自服务端的 NBT 增量更新。
     * 由宿主的 IInterfaceTerminalGuiCallback.postUpdate 转发调用。
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

    // ========== 列表管理 ==========

    /**
     * 重建接口列表。
     * 根据搜索条件过滤，并更新 lines 和滚动条。
     */
    public void refreshList() {
        this.byName.clear();
        this.matchedStacks.clear();

        final String searchInputs = this.searchFieldInputs.getText().toLowerCase();
        final String searchOutputs = this.searchFieldOutputs.getText().toLowerCase();
        final String searchNames = this.searchFieldNames.getText().toLowerCase();

        final Set<Object> cachedSearch = this
                .getCacheForSearchTerm("IN:" + searchInputs + " OUT:" + searchOutputs
                        + "NAME:" + searchNames + onlyShowWithSpace + onlyMolecularAssemblers + onlyBrokenRecipes);
        final boolean rebuild = cachedSearch.isEmpty();

        // 搜索旧接口
        this.filterEntries(this.byId.values(), cachedSearch, rebuild, searchInputs, searchOutputs,
                searchNames);

        // 搜索样板供应器
        this.filterEntries(this.providerById.values(), cachedSearch, rebuild, searchInputs, searchOutputs,
                searchNames);

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

        this.updateScrollBar();
    }

    private void filterEntries(Collection<ClientDCInternalInv> entries, Set<Object> cachedSearch, boolean rebuild,
            String searchInputs, String searchOutputs, String searchNames) {
        for (final ClientDCInternalInv entry : entries) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchInputs.isEmpty() && searchOutputs.isEmpty();
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

                    if ((!searchInputs.isEmpty() && itemStackMatchesSearchTerm(itemStack, searchInputs, 0))
                            || (!searchOutputs.isEmpty()
                                    && itemStackMatchesSearchTerm(itemStack, searchOutputs, 1))) {
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
            if (!entry.getName().toLowerCase().contains(searchNames)) {
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
    }

    // ========== 工具方法 ==========

    private boolean recipeIsBroken(final ItemStack stack) {
        if (stack == null) return false;
        if (stack.isEmpty()) return false;

        final NBTTagCompound encodedValue = stack.getTagCompound();
        if (encodedValue == null) return true;

        final World w = AppEng.proxy.getWorld();
        if (w == null) return false;

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
                        .getItemDisplayName(AEItemStackType.INSTANCE.createStack(parsedItemStack))
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
                    o = new ClientDCInternalInv(PatternProviderLogic.NUMBER_OF_PATTERN_SLOTS, id, sortBy, string));
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
}
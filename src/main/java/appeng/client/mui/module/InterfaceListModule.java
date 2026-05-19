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
import java.util.function.Consumer;

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
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.widgets.MUIButtonPool;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITextFieldWidget;
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
 * Interface list module extracted from MUIInterfaceTerminalPanel / GuiWirelessDualInterfaceTerminal as a reusable component.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Managing byId / providerById data (receiving server sync via {@link #postUpdate(NBTTagCompound)})</li>
 *   <li>Three search fields (inputs/outputs/names) and filter buttons (assembler filter/empty slot filter/broken recipe filter/terminal style)</li>
 *   <li>Refreshing the list, scrollbar management</li>
 *   <li>Rendering interface list rows in drawBG/drawFG</li>
 *   <li>Dynamically creating SlotDisconnected and highlight buttons in drawScreen</li>
 *   <li>Keyboard/mouse event forwarding</li>
 *   <li>Block highlight positioning</li>
 * </ul>
 *
 * <p>The host GUI provides context via the {@link Host} interface.
 */
public class InterfaceListModule {

    // ========== Constants ==========

    private static final int OFFSET_X = 21;
    private static final int MAIN_GUI_WIDTH = 208;
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;
    private static final String MOLECULAR_ASSEMBLER = "tile.appliedenergistics2.molecular_assembler";

    // ========== 宿主接口 ==========

    /**
     * The host GUI must implement this interface to provide the context required by the module.
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getScreenWidth();

        int getScreenHeight();

        int getXSize();

        int getYSize();

        FontRenderer getFontRenderer();

        MUIScrollBar getInterfaceScrollBar();

        /**
         * Register an MUI widget on the host panel.
         * Called during module initialization to add toolbar buttons and button pools.
         */
        <T extends IMUIWidget> T addModuleWidget(T widget);

        AEBasePanel getPanel();

        /**
         * Request the host to reinitialize the GUI.
         */
        void requestReinitialize();

        /**
         * Bind a texture.
         */
        void bindTexture(String file);

        /**
         * Draw a textured rectangle at the specified position.
         */
        void drawTexturedModalRect(int x, int y, int textureX, int textureY, int width, int height);

        /**
         * Get the JEI offset (24 if JEI is enabled, 0 otherwise).
         */
        int getJeiOffset();
    }

    // ========== 数据存储 ==========

    private final Host host;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final Map<Long, ClientDCInternalInv> providerById = new HashMap<>();

    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<MUIButtonWidget, ClientDCInternalInv> highlightButtonMap = new HashMap<>();
    private final HashMap<MUIButtonWidget, ClientDCInternalInv> doubleButtonMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();
    private final Map<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();

    private MUITextFieldWidget searchFieldOutputs;
    private MUITextFieldWidget searchFieldInputs;
    private MUITextFieldWidget searchFieldNames;

    // Toolbar buttons (MUI widgets, registered on host panel)
    private MUIButtonWidget guiButtonHideFull;
    private MUIButtonWidget guiButtonAssemblersOnly;
    private MUIButtonWidget guiButtonBrokenRecipes;
    private MUIButtonWidget terminalStyleBox;

    /** Dynamic highlight button pool */
    private MUIButtonPool highlightButtonPool;

    /** Dynamic double-stacks button pool (only active if enableDoubleButton is true) */
    private MUIButtonPool doubleButtonPool;

    private boolean refreshList = false;
    private boolean onlyShowWithSpace = false;
    private boolean onlyMolecularAssemblers = false;
    private boolean onlyBrokenRecipes = false;
    private int rows = 6;

    /** Whether to enable per-row double-stacks button (WirelessDualInterfaceTerminal specific feature) */
    private boolean enableDoubleButton = false;

    /** Callback for double-stacks button clicks, set by the host panel */
    @javax.annotation.Nullable
    private Consumer<ClientDCInternalInv> doubleStacksHandler;

    // ========== Constructor ==========

    public InterfaceListModule(Host host) {
        this.host = host;
    }

    // ========== 配置 ==========

    /**
     * Set whether to enable the per-row double-stacks button.
     * WirelessDualInterfaceTerminal uses this feature; regular interface terminals do not.
     */
    public void setEnableDoubleButton(boolean enable) {
        this.enableDoubleButton = enable;
    }

    /**
     * Set the callback handler for double-stacks button clicks.
     * The host panel provides this to handle the actual double-stacks inventory action.
     */
    public void setDoubleStacksHandler(@javax.annotation.Nullable Consumer<ClientDCInternalInv> handler) {
        this.doubleStacksHandler = handler;
    }

    // ========== Accessors ==========

    public int getRows() {
        return rows;
    }

    public ArrayList<Object> getLines() {
        return lines;
    }

    public HashMap<MUIButtonWidget, ClientDCInternalInv> getHighlightButtonMap() {
        return highlightButtonMap;
    }

    public HashMap<MUIButtonWidget, ClientDCInternalInv> getDoubleButtonMap() {
        return doubleButtonMap;
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

    public MUITextFieldWidget getSearchFieldNames() {
        return searchFieldNames;
    }

    // ========== 搜索字段创建 ==========

    private MUITextFieldWidget.SearchFieldGroup createSearchFieldGroup() {
        return MUITextFieldWidget.SearchFieldGroup.builder()
                .inputs(MUITextFieldWidget.SearchFieldSpec.builder(32, 25, 86)
                        .tooltip(ButtonToolTips.SearchFieldInputs.getLocal())
                        .onTextChange(text -> refreshList())
                        .build())
                .outputs(MUITextFieldWidget.SearchFieldSpec.builder(32, 38, 86)
                        .tooltip(ButtonToolTips.SearchFieldOutputs.getLocal())
                        .onTextChange(text -> refreshList())
                        .build())
                .names(MUITextFieldWidget.SearchFieldSpec.builder(32 + 99, 38, 71)
                        .tooltip(ButtonToolTips.SearchFieldNames.getLocal())
                        .onTextChange(text -> refreshList())
                        .focused(true)
                        .build())
                .build();
    }

    // ========== Initialization ==========

    /**
     * Calculate the number of rows (based on terminal style and screen height).
     * Must be called during the initGui flow.
     */
    public void calculateRows() {
        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math.floor(
                (double) (host.getScreenHeight() - MAGIC_HEIGHT_NUMBER - jeiSearchOffset) / 18);

        this.rows = AEBasePanel.computeTerminalRows(maxScreenRows);

        this.rows = Math.min(this.rows, Integer.MAX_VALUE);
        this.rows = Math.max(this.rows, 6);
    }

    /**
     * Initialize search fields and filter buttons.
     * Must be called after calculateRows and after the host sets ySize/guiTop.
     */
    public void initSearchFieldsAndButtons() {
        MUITextFieldWidget.SearchFieldWidgets searchFields = MUITextFieldWidget.addSearchFieldGroup(
                host.getPanel(),
                this.createSearchFieldGroup());
        searchFieldInputs = searchFields.getInputs();
        searchFieldOutputs = searchFields.getOutputs();
        searchFieldNames = searchFields.getNames();

        // Toolbar buttons (MUI widgets — panel-relative coordinates)
        final int btnX = -18;
        final int jeiOff = host.getJeiOffset();

        this.terminalStyleBox = host.addModuleWidget(
                new MUIButtonWidget(btnX, 8 + jeiOff, Settings.TERMINAL_STYLE, null)
                        .setOnClick(btn -> this.onTerminalStyleClicked()));
        this.guiButtonBrokenRecipes = host.addModuleWidget(
                new MUIButtonWidget(btnX, 8 + jeiOff + 20, Settings.ACTIONS, null)
                        .setOnClick(btn -> {
                            this.onlyBrokenRecipes = !this.onlyBrokenRecipes;
                            this.refreshList();
                        }));
        this.guiButtonHideFull = host.addModuleWidget(
                new MUIButtonWidget(btnX, 8 + jeiOff + 40, Settings.ACTIONS, null)
                        .setOnClick(btn -> {
                            this.onlyShowWithSpace = !this.onlyShowWithSpace;
                            this.refreshList();
                        }));
        this.guiButtonAssemblersOnly = host.addModuleWidget(
                new MUIButtonWidget(btnX, 8 + jeiOff + 60, Settings.ACTIONS, null)
                        .setOnClick(btn -> {
                            this.onlyMolecularAssemblers = !this.onlyMolecularAssemblers;
                            this.refreshList();
                        }));

        // Dynamic highlight button pool
        this.highlightButtonPool = new MUIButtonPool()
                .setDefaultOnClick(btn -> this.onHighlightClicked(btn));
        host.addModuleWidget(this.highlightButtonPool);

        // Dynamic double-stacks button pool (only used when enableDoubleButton is true)
        this.doubleButtonPool = new MUIButtonPool()
                .setDefaultOnClick(btn -> this.onDoubleStacksClicked(btn));
        host.addModuleWidget(this.doubleButtonPool);

        this.updateScrollBar();
    }

    // ========== Scrolling ==========

    public void updateScrollBar() {
        MUIScrollBar sb = host.getInterfaceScrollBar();
        sb.setTop(52).setLeft(189).setHeight(this.rows * 18 - 2);
        sb.setRange(0, this.lines.size() - 1, 1);
    }

    // ========== 渲染: drawBG ==========

    /**
     * Draw the background portion of the interface list (row backgrounds + slot backgrounds + search boxes).
     * Does not include the top and bottom (drawn by the host).
     */
    public void drawBG(int offsetX, int offsetY) {
        host.bindTexture("guis/newinterfaceterminal.png");

        // 顶部背景
        host.drawTexturedModalRect(offsetX, offsetY, 0, 0, MAIN_GUI_WIDTH, 53);

        // Row backgrounds
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

        // Bottom background (player inventory area)
        host.drawTexturedModalRect(offsetX, offsetY + 50 + this.rows * 18, 0, 158, MAIN_GUI_WIDTH, 99);

        // 搜索框由 panel widget 生命周期统一绘制
    }

    // ========== 渲染: drawFG ==========

    /**
     * Draw the foreground portion of the interface list (title, interface names, match highlights).
     */
    public void drawFG(int offsetX, int offsetY, String title) {
        FontRenderer fr = host.getFontRenderer();
        fr.drawString(title, OFFSET_X + 2, 6, AEMUITheme.COLOR_TITLE);
        fr.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, host.getYSize() - 96, AEMUITheme.COLOR_TITLE);

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
                fr.drawString(name, OFFSET_X + 3, 6 + offset, AEMUITheme.COLOR_TITLE);
                linesDraw++;
                offset += 18;
            }
        }
    }

    // ========== Rendering: drawScreen (dynamic slot/button creation) ==========

    /**
     * Called in drawScreen to create dynamic SlotDisconnected and highlight/double buttons.
     * Must be called after buttonList.clear() and before super.drawScreen().
     */
    public void populateDynamicSlots() {
        final AEBasePanel panel = host.getPanel();

        // Reset dynamic button pools
        highlightButtonPool.reset();
        highlightButtonMap.clear();
        doubleButtonPool.reset();
        doubleButtonMap.clear();
        panel.inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        // Update toolbar button values
        guiButtonAssemblersOnly.set(
                onlyMolecularAssemblers ? ActionItems.MOLECULAR_ASSEMBLERS_ON
                        : ActionItems.MOLECULAR_ASSEMBLERS_OFF);
        guiButtonHideFull.set(onlyShowWithSpace ? ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF
                : ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON);
        guiButtonBrokenRecipes.set(onlyBrokenRecipes ? ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON
                : ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF);
        terminalStyleBox.set(AEConfig.instance().getConfigManager().getSetting(Settings.TERMINAL_STYLE));

        int offset = 51;
        final int currentScroll = host.getInterfaceScrollBar().getCurrentScroll();
        int linesDraw = 0;

        for (int x = 0; x < rows && linesDraw < rows && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                // Acquire highlight button from pool (panel-relative coordinates)
                MUIButtonWidget hlBtn = this.highlightButtonPool.acquireSettings(
                        4, offset + 1, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
                highlightButtonMap.put(hlBtn, inv);

                // Acquire double-stacks button from pool (if enabled)
                if (enableDoubleButton) {
                    MUIButtonWidget dblBtn = this.doubleButtonPool.acquireSettings(
                            8, offset + 10, Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
                    dblBtn.setHalfSize(true);
                    doubleButtonMap.put(dblBtn, inv);
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
     * Search field tooltips are now handled by the unified widget lifecycle.
     */
    public void drawSearchFieldTooltips(AEBasePanel panel, int mouseX, int mouseY) {
    }

    // ========== 输入处理 ==========

    /**
     * Handle mouse clicks (search field focus).
     */
    public void mouseClicked(int xCoord, int yCoord, int btn) {
        this.searchFieldInputs.mouseClicked(xCoord - host.getGuiLeft(), yCoord - host.getGuiTop(), btn);
        this.searchFieldOutputs.mouseClicked(xCoord - host.getGuiLeft(), yCoord - host.getGuiTop(), btn);
        this.searchFieldNames.mouseClicked(xCoord - host.getGuiLeft(), yCoord - host.getGuiTop(), btn);
    }

    /**
     * Handle keyboard events.
     *
     * @return true if the event was consumed (should not be passed to the host super.keyTyped)
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
     * Handle legacy button clicks (for backward compatibility with host actionPerformed).
     * All button logic has been migrated to MUI onClick callbacks, so this always returns false.
     *
     * @return true if the event was consumed (always false now)
     * @deprecated All button handling is now via MUI widget onClick callbacks.
     */
    @Deprecated
    public boolean actionPerformed(GuiButton btn, GuiButton selectedButton) {
        return false;
    }

    // ========== MUI button onClick handlers ==========

    /**
     * Handle highlight button click (from MUIButtonPool onClick callback).
     * Locates the interface in the world and highlights its block position.
     */
    private void onHighlightClicked(MUIButtonWidget btn) {
        ClientDCInternalInv inv = highlightButtonMap.get(btn);
        if (inv == null) {
            return;
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
    }

    /**
     * Handle double-stacks button click (from MUIButtonPool onClick callback).
     */
    private void onDoubleStacksClicked(MUIButtonWidget btn) {
        ClientDCInternalInv inv = doubleButtonMap.get(btn);
        if (inv == null) {
            return;
        }
        // Delegate to the host panel for the actual double-stacks logic
        // (The host panel has access to the network handler and inventory actions)
        if (this.doubleStacksHandler != null) {
            this.doubleStacksHandler.accept(inv);
        }
    }

    /**
     * Handle terminal style button click — cycle the terminal style setting and reinitialize the GUI.
     */
    private void onTerminalStyleClicked() {
        final Enum<?> cv = this.terminalStyleBox.getCurrentValue();
        final boolean backwards = Mouse.isButtonDown(1);
        final Enum<?> next = appeng.util.EnumCycler.rotateEnumWildcard(cv, backwards,
                Settings.TERMINAL_STYLE.getPossibleValues());
        AEConfig.instance().getConfigManager().putSetting(Settings.TERMINAL_STYLE, next);
        this.terminalStyleBox.set(next);
        host.requestReinitialize();
    }

    // ========== Tab key cycling ==========

    /**
     * Cycle search field focus with Tab key.
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
     * Check if any search field has focus.
     */
    public boolean isAnySearchFieldFocused() {
        return (searchFieldInputs != null && searchFieldInputs.isFocused())
                || (searchFieldOutputs != null && searchFieldOutputs.isFocused())
                || (searchFieldNames != null && searchFieldNames.isFocused());
    }

    /**
     * Get the index of the currently focused search field (0=Inputs, 1=Outputs, 2=Names, -1=none).
     */
    public int getFocusedFieldIndex() {
        if (searchFieldInputs != null && searchFieldInputs.isFocused()) return 0;
        if (searchFieldOutputs != null && searchFieldOutputs.isFocused()) return 1;
        if (searchFieldNames != null && searchFieldNames.isFocused()) return 2;
        return -1;
    }

    /**
     * Set search field focus.
     */
    public void setFocusedField(int index) {
        if (searchFieldInputs != null) searchFieldInputs.setFocused(index == 0);
        if (searchFieldOutputs != null) searchFieldOutputs.setFocused(index == 1);
        if (searchFieldNames != null) searchFieldNames.setFocused(index == 2);
    }

    /**
     * Clear all search field focus.
     */
    public void clearFocus() {
        if (searchFieldInputs != null) searchFieldInputs.setFocused(false);
        if (searchFieldOutputs != null) searchFieldOutputs.setFocused(false);
        if (searchFieldNames != null) searchFieldNames.setFocused(false);
    }

    // ========== 数据更新 ==========

    /**
     * Handle incremental NBT updates from the server.
     * Forwarded by the host IInterfaceTerminalGuiCallback.postUpdate.
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
     * Rebuild the interface list.
     * Filter by search criteria and update lines and scrollbar.
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

        // Search legacy interfaces
        this.filterEntries(this.byId.values(), cachedSearch, rebuild, searchInputs, searchOutputs,
                searchNames);

        // Search pattern providers
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

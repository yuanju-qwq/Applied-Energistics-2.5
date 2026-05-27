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

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.me.ItemRepo;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.mui.AEBaseMEPanel;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.module.InterfaceListModule;
import appeng.client.mui.module.MEItemBrowserModule;
import appeng.client.mui.module.PatternEncodingModule;
import appeng.client.mui.module.TerminalPinSystem;
import appeng.client.mui.module.TerminalToolbar;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * MUI wireless dual-interface terminal panel.
 *
 * Modular architecture composed of three independent modules:
 * <ul>
 *   <li>{@link InterfaceListModule} — interface list panel (central area, scrollable list + search + highlight)</li>
 *   <li>{@link PatternEncodingModule} — pattern encoding panel (right side, encode buttons + input/output grid)</li>
 *   <li>{@link MEItemBrowserModule} — ME item browser panel (left side, 4x4 grid + search + sort)</li>
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

    // ========== Constants ==========

    private static final int MAIN_GUI_WIDTH = 208;

    // JEI offset
    private final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    // ========== Data ==========

    private final ContainerWirelessDualInterfaceTerminal dualContainer;
    private final IConfigManager configSrc;

    // Three modules
    private InterfaceListModule interfaceListModule;
    private PatternEncodingModule patternEncodingModule;
    private MEItemBrowserModule meItemBrowserModule;

    // Toolbar module
    private TerminalToolbar toolbar;

    // Wireless terminal helper (wireless upgrade icon)
    private final WirelessTerminalHelper wirelessHelper = new WirelessTerminalHelper();

    // ========== Construction ==========

    public MUIWirelessDualInterfaceTerminalPanel(final ContainerWirelessDualInterfaceTerminal container) {
        super(container);

        this.dualContainer = container;
        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        container.setMeGui(this);

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);

        this.xSize = MAIN_GUI_WIDTH;
        this.ySize = 255;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        // Create modules
        this.interfaceListModule = new InterfaceListModule(this);
        this.interfaceListModule.setEnableDoubleButton(true);
        this.interfaceListModule.setDoubleStacksHandler(inv -> {
            try {
                appeng.core.sync.network.NetworkHandler.instance().sendToServer(
                        new appeng.core.sync.packets.PacketValueConfig(
                                "WirelessDualInterface.Double", String.valueOf(inv.getId())));
            } catch (IOException e) {
                // ignore
            }
        });

        this.patternEncodingModule = new PatternEncodingModule(this);
        this.patternEncodingModule.initDragState();

        this.meItemBrowserModule = new MEItemBrowserModule(this);
        this.meItemBrowserModule.initDragState();

        this.toolbar = new TerminalToolbar(new ToolbarHost());

        // Calculate rows
        this.interfaceListModule.calculateRows();
        final int rows = this.interfaceListModule.getRows();

        super.initGui();

        // Set panel size
        final int MAGIC_HEIGHT_NUMBER = 52 + 99;
        this.ySize = MAGIC_HEIGHT_NUMBER + rows * 18;
        this.centerVertically();

        // Initialize the three modules
        this.interfaceListModule.initSearchFieldsAndButtons();
        this.patternEncodingModule.initButtons();
        this.patternEncodingModule.initVirtualSlots();
        this.meItemBrowserModule.initPanel();

        // Toolbar buttons (sort, view, search mode, etc.)
        this.toolbar.buildAndRegister();

        // Reposition slots
        this.patternEncodingModule.repositionSlots();
        this.repositionPlayerSlots();
    }

    @Override
    protected void setupWidgets() {
        // All initialization is handled in initGui
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
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

    // ========== Callback implementations ==========

    // --- IInterfaceTerminalGuiCallback ---

    @Override
    public void postUpdate(final NBTTagCompound in) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.postUpdate(in);
        }
    }

    // --- IMEInventoryUpdateReceiver ---

    @Override
    public void postRepoEntryUpdate(final List<ItemRepo.RepoEntry> entries) {
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.postRepoEntryUpdate(entries);
        }
    }

    // --- IConfigManagerHost ---

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.toolbar != null) {
            this.toolbar.updateSetting(manager, settingName, newValue);
        }
        if (this.meItemBrowserModule != null) {
            this.meItemBrowserModule.updateSetting();
        }
    }

    // ========== Rendering ==========

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Wireless upgrade icon
        this.wirelessHelper.drawWirelessIcon(offsetX, offsetY, 198, 127);

        // Interface list panel background
        this.interfaceListModule.drawBG(offsetX, offsetY);

        // Pattern encoding panel background
        this.patternEncodingModule.drawBG(offsetX, offsetY);

        // ME item browser panel background
        this.meItemBrowserModule.drawBG(offsetX, offsetY);
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        // Interface list panel foreground
        this.interfaceListModule.drawFG(offsetX, offsetY,
                this.getGuiDisplayName(GuiText.InterfaceTerminal.getLocal()));

        // Pattern encoding panel foreground
        this.patternEncodingModule.drawFG();
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        // Each module populates its buttons and slots
        this.interfaceListModule.populateDynamicSlots();
        this.patternEncodingModule.populateButtons();

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Search field tooltip
        this.interfaceListModule.drawSearchFieldTooltips(this, mouseX, mouseY);
    }

    // ========== Input events ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        // Pattern encoding module buttons
        if (this.patternEncodingModule.actionPerformed(btn)) {
            return;
        }

    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // Interface list search field
        this.interfaceListModule.mouseClicked(xCoord, yCoord, btn);

        // ME item search field
        this.meItemBrowserModule.mouseClicked(xCoord, yCoord, btn);

        // Panel drag (middle button)
        if (btn == 2) {
            if (this.patternEncodingModule.getDragState() != null
                    && this.patternEncodingModule.getDragState().isInDragArea(xCoord, yCoord)) {
                this.patternEncodingModule.getDragState().startDrag(xCoord, yCoord);
                return;
            }
            if (this.meItemBrowserModule.getDragState() != null
                    && this.meItemBrowserModule.getDragState().isInDragArea(xCoord, yCoord)) {
                this.meItemBrowserModule.getDragState().startDrag(xCoord, yCoord);
                return;
            }
        }

        // Pattern encoding scrollbar click
        if (this.patternEncodingModule.handleScrollbarClick(xCoord, yCoord)) {
            return;
        }

        // ME item browser scrollbar click
        if (this.meItemBrowserModule.handleScrollbarClick(xCoord, yCoord)) {
            return;
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        // Panel drag update
        if (clickedMouseButton == 2) {
            if (this.patternEncodingModule.getDragState() != null
                    && this.patternEncodingModule.getDragState().isDragging()) {
                this.patternEncodingModule.getDragState().updateDrag(mouseX, mouseY);
                this.patternEncodingModule.repositionSlots();
                return;
            }
            if (this.meItemBrowserModule.getDragState() != null
                    && this.meItemBrowserModule.getDragState().isDragging()) {
                this.meItemBrowserModule.getDragState().updateDrag(mouseX, mouseY);
                return;
            }
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        // End drag
        if (state == 2) {
            if (this.patternEncodingModule.getDragState() != null) {
                this.patternEncodingModule.getDragState().endDrag();
            }
            if (this.meItemBrowserModule.getDragState() != null) {
                this.meItemBrowserModule.getDragState().endDrag();
            }
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        // Interface list search field
        if (this.interfaceListModule.keyTyped(character, key)) {
            return;
        }

        // ME item search field
        if (this.meItemBrowserModule.keyTyped(character, key)) {
            return;
        }

        if (!this.checkHotbarKeys(key)) {
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        // Pattern encoding panel scroll wheel
        if (this.patternEncodingModule.mouseWheelEvent(x, y, wheel)) {
            return;
        }

        // ME item browser panel scroll wheel
        if (this.meItemBrowserModule.mouseWheelEvent(x, y, wheel)) {
            return;
        }

        // Interface list main scrollbar wheel
        super.mouseWheelEvent(x, y, wheel);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // Update pattern encoding module (including PlacePattern and slot repositioning)
        this.patternEncodingModule.updateScreen();
    }

    // ========== JEI compatibility ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> exclusionArea = new ArrayList<>();

        // Toolbar sort button area
        int sortButtonCount = this.toolbar.getVisibleSortButtonCount();
        if (sortButtonCount > 0) {
            exclusionArea.add(new Rectangle(guiLeft - 18, guiTop + 8 + jeiOffset, 20,
                    sortButtonCount * 20 + sortButtonCount - 2));
        }

        // Pattern encoding panel area
        exclusionArea.add(this.patternEncodingModule.getJEIExclusionRect());

        // ME item browser panel area
        exclusionArea.add(this.meItemBrowserModule.getJEIExclusionRect());

        return exclusionArea;
    }

    // ========== InterfaceListModule.Host implementation ==========

    @Override
    public int getScreenWidth() {
        return this.width;
    }

    @Override
    public int getScreenHeight() {
        return this.height;
    }

    @Override
    public FontRenderer getFontRenderer() {
        return this.fontRenderer;
    }

    @Override
    public MUIScrollBar getInterfaceScrollBar() {
        return this.getScrollBar();
    }

    @Override
    public <T extends appeng.client.mui.IMUIWidget> T addModuleWidget(T widget) {
        return this.addWidget(widget);
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

    // ========== PatternEncodingModule.Host implementation ==========

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

    // ========== JEI search suggestion ==========

    /**
     * Set the Names search field suggestion text (used by JEI recipe transfer to
     * pre-fill the machine name when a recipe is transferred).
     */
    public void setSearchFieldSuggestion(final String suggestion) {
        if (this.interfaceListModule.getSearchFieldNames() != null) {
            this.interfaceListModule.getSearchFieldNames().setSuggestion(suggestion);
        }
    }

    // ========== MEItemBrowserModule.Host implementation ==========

    @Override
    public List<GuiButton> getButtonList() {
        return this.buttonList;
    }

    @Override
    public IConfigManager getConfigSrc() {
        return this.configSrc;
    }

    @Override
    public boolean hasViewCell() {
        return false;
    }

    @Override
    public void requestScrollBarUpdate() {
        // No-op for compact mode — scrollbar is managed internally by the module
    }

    // ========== TerminalToolbar.Host implementation ==========

    private final class ToolbarHost implements TerminalToolbar.Host {
        @Override
        public IConfigManager getConfigManager() {
            return configSrc;
        }

        @Override
        public ItemRepo getRepo() {
            return meItemBrowserModule.getItemRepo();
        }

        @Override
        public AEBasePanel getPanel() {
            return MUIWirelessDualInterfaceTerminalPanel.this;
        }

        @Override
        public boolean hasViewCell() {
            return false;
        }

        @Override
        public boolean isWirelessTerm() {
            return true;
        }

        @Override
        public boolean isPortableCell() {
            return false;
        }

        @Override
        public boolean isSecurityStation() {
            return false;
        }

        @Override
        public int getJeiOffset() {
            return jeiOffset;
        }

        @Override
        public int getGuiLeft() {
            return guiLeft;
        }

        @Override
        public int getGuiTop() {
            return guiTop;
        }

        @Override
        public int getRows() {
            return meItemBrowserModule.getRows();
        }

        @Override
        public void reinitializeGui() {
            buttonList.clear();
            initGui();
        }

        @Override
        public void updateScrollBar() {
            // No-op for compact mode — scrollbar is managed internally by the module
        }

        @Override
        public TerminalPinSystem getPinSystem() {
            return null; // Not used in compact mode
        }

        @Override
        public boolean isCompactLayout() {
            return true;
        }

        @Override
        public int getCompactPanelRelX() {
            return meItemBrowserModule.getPanelRelX();
        }

        @Override
        public int getCompactPanelRelY() {
            return meItemBrowserModule.getPanelRelY();
        }

        @Override
        public int getCompactPanelWidth() {
            return meItemBrowserModule.getLayout().getPanelWidth();
        }

        @Override
        public int getCompactPanelHeight() {
            int ph = meItemBrowserModule.getLayout().getPanelHeight();
            if (ph > 0) return ph;
            return meItemBrowserModule.getRows() * 18 + meItemBrowserModule.getLayout().getGridOffsetY();
        }
    }
}

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
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.PinsRows;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.ActionKey;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.me.InternalSlotME;
import appeng.client.me.ItemRepo;
import appeng.client.me.SlotME;
import appeng.client.mui.AEBaseMEPanel;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.module.TerminalPinSystem;
import appeng.client.mui.module.TerminalToolbar;
import appeng.client.mui.widgets.MUITextFieldWidget;
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
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.AbstractPartTerminal;
import appeng.tile.misc.TileSecurityStation;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * MUI ME terminal panel — base class for all terminals.
 * <p>
 * Delegates complex subsystems to:
 * <ul>
 *   <li>{@link TerminalToolbar} — settings buttons, type filter toggles (AEKeyType-based),
 *       pins button, crafting status tab</li>
 *   <li>{@link TerminalPinSystem} — pin row calculation, VirtualMEPinSlot creation,
 *       pin interactions</li>
 * </ul>
 * <p>
 * Subclasses: crafting terminal, pattern terminal, wireless terminals,
 * portable cell, security station, expanded processing pattern terminal.
 */
@SideOnly(Side.CLIENT)
public class MUIMEMonitorablePanel extends AEBaseMEPanel
        implements ISortSource, IConfigManagerHost, IMEMonitorableGuiCallback {

    // ========== Static fields ==========

    private static int craftingGridOffsetX;
    private static int craftingGridOffsetY;
    private static String memoryText = "";

    // ========== Layout constants ==========

    private static final int MAGIC_HEIGHT_NUMBER = 114 + 1;

    private static final int SEARCH_FIELD_X_MIN = 80;
    private static final int SEARCH_FIELD_Y = 4;
    private static final int SEARCH_FIELD_WIDTH = 90;
    private static final int SEARCH_FIELD_HEIGHT = 12;
    private static final int SEARCH_FIELD_MAX_LENGTH = 50;
    private static final int SEARCH_FIELD_SELECTION_COLOR = 0xFF008000;

    // ========== Core data ==========

    protected final ItemRepo repo;
    private final IConfigManager configSrc;
    private final boolean viewCell;
    private final ItemStack[] myCurrentViewCells = new ItemStack[5];
    private final ContainerMEMonitorable monitorableContainer;

    // ========== Extracted modules ==========

    private final TerminalToolbar toolbar;
    private final TerminalPinSystem pinSystem;

    // ========== Terminal state ==========

    private GuiText myName;
    private MUITextFieldWidget searchField;
    protected int perRow = 9;
    protected int reservedSpace = 0;
    protected boolean customSortOrder = true;
    protected int rows = 0;
    private boolean isAutoFocus = false;
    private int currentMouseX = 0;
    private int currentMouseY = 0;
    private boolean delayedUpdate;

    protected final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    // ========== Constructor ==========

    public MUIMEMonitorablePanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        this(inventoryPlayer, te, new ContainerMEMonitorable(inventoryPlayer, te));
    }

    protected MUIMEMonitorablePanel(final ContainerMEMonitorable c) {
        this(c.getPlayerInv(), c.getHost(), c);
    }

    public MUIMEMonitorablePanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te,
            final ContainerMEMonitorable c) {
        super(c);

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);
        this.repo = new ItemRepo(scrollbar, this);

        this.xSize = 185;
        this.ySize = 204;

        if (te instanceof IViewCellStorage) {
            this.xSize += 33;
        }

        this.configSrc = ((IConfigurableObject) this.inventorySlots).getConfigManager();
        this.monitorableContainer = (ContainerMEMonitorable) this.inventorySlots;
        this.monitorableContainer.setGui(this);
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

        this.pinSystem = new TerminalPinSystem(new PinSystemHost());
        this.toolbar = new TerminalToolbar(new ToolbarHost());
    }

    // ========== Host implementations ==========

    private final class PinSystemHost implements TerminalPinSystem.Host {
        @Override
        public ContainerMEMonitorable getMonitorableContainer() {
            return monitorableContainer;
        }

        @Override
        public ItemRepo getRepo() {
            return repo;
        }

        @Override
        public int getRows() {
            return rows;
        }

        @Override
        public int getPerRow() {
            return perRow;
        }

        @Override
        public int getOffsetX() {
            return offsetX;
        }

        @Override
        public List<GuiCustomSlot> getGuiSlots() {
            return guiSlots;
        }

        @Override
        public net.minecraft.client.gui.FontRenderer getFontRenderer() {
            return fontRenderer;
        }

        @Override
        public boolean isPointInRegion(int x, int y, int w, int h, int mx, int my) {
            return MUIMEMonitorablePanel.this.isPointInRegion(x, y, w, h, mx, my);
        }

        @Override
        public void sendPinRowsUpdatePacket(PinsRows crafting, PinsRows player) {
            MUIMEMonitorablePanel.this.sendPinRowsUpdate(crafting, player);
        }

        @Override
        public void reinitializeGui() {
            MUIMEMonitorablePanel.this.reinitalize();
        }
    }

    private final class ToolbarHost implements TerminalToolbar.Host {
        @Override
        public IConfigManager getConfigManager() {
            return configSrc;
        }

        @Override
        public ItemRepo getRepo() {
            return repo;
        }

        @Override
        public AEBasePanel getPanel() {
            return MUIMEMonitorablePanel.this;
        }

        @Override
        public boolean hasViewCell() {
            return viewCell;
        }

        @Override
        public boolean isWirelessTerm() {
            return MUIMEMonitorablePanel.this instanceof MUIWirelessTermPanel;
        }

        @Override
        public boolean isPortableCell() {
            return MUIMEMonitorablePanel.this instanceof MUIPortableCellPanel;
        }

        @Override
        public boolean isSecurityStation() {
            return MUIMEMonitorablePanel.this instanceof MUISecurityStationPanel;
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
            return rows;
        }

        @Override
        public void reinitializeGui() {
            MUIMEMonitorablePanel.this.reinitalize();
        }

        @Override
        public void updateScrollBar() {
            MUIMEMonitorablePanel.this.updateScrollBar();
        }

        @Override
        public TerminalPinSystem getPinSystem() {
            return pinSystem;
        }
    }

    // ========== IMEMonitorableGuiCallback ==========

    @Override
    public void postRepoEntryUpdate(final List<ItemRepo.RepoEntry> entries) {
        for (final ItemRepo.RepoEntry entry : entries) {
            this.repo.postUpdate(entry);
        }
        handlePostUpdatePauseAndRefresh();
    }

    @Override
    public void postUpdate(final List<IAEStack<?>> list) {
        for (final IAEStack<?> is : list) {
            this.repo.postUpdate(is);
        }
        handlePostUpdatePauseAndRefresh();
    }

    private void handlePostUpdatePauseAndRefresh() {
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

    // ========== Initialization ==========

    @Override
    public void initGui() {
        final int jeiSearchOffset = Platform.isJEICenterSearchBarEnabled() ? 40 : 0;
        final int maxScreenRows = (int) Math
                .floor((double) (this.height - MAGIC_HEIGHT_NUMBER - this.reservedSpace - jeiSearchOffset) / 18);

        this.rows = computeTerminalRows(maxScreenRows);
        this.rows = Math.min(this.rows, this.getMaxRows());
        this.rows = Math.max(this.rows, this.getMinRows());

        // --- Pin system: sync & calculate ---
        this.pinSystem.syncFromContainer();
        int normalSlotOffsetY = this.pinSystem.calculateAndCreateSlots();
        int normalSlotRows = Math.max(0, this.rows - this.pinSystem.getTotalPinRows());

        // --- Normal ME slots ---
        clearAndBuildInternalSlots(normalSlotRows, normalSlotOffsetY);

        super.initGui();

        // --- Register pin + normal virtual slots ---
        this.guiSlots.removeIf(s -> s instanceof VirtualMEMonitorableSlot || s instanceof VirtualMEPinSlot);
        this.pinSystem.registerToGuiSlots();
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                final int idx = x + y * this.perRow;
                this.guiSlots.add(new VirtualMEMonitorableSlot(
                        idx, this.offsetX + x * 18, normalSlotOffsetY + y * 18, this.repo, idx));
            }
        }

        this.ySize = MAGIC_HEIGHT_NUMBER + this.rows * 18 + this.reservedSpace;
        this.centerVertically();

        // --- Position type filter buttons (after guiTop is finalized) ---
        this.toolbar.positionTypeFilterButtons();

        // --- Search mode configuration ---
        final MUITextFieldWidget.TerminalSearchConfig searchConfig =
                MUITextFieldWidget.TerminalSearchConfig.fromCurrentSetting();
        this.isAutoFocus = searchConfig.isAutoFocus();

        this.searchField.applyTerminalSearchConfig(searchConfig, memoryText, text -> {
            this.repo.setSearchString(text);
            this.updateScrollBar();
        });

        // --- Crafting grid offset ---
        craftingGridOffsetX = Integer.MAX_VALUE;
        craftingGridOffsetY = Integer.MAX_VALUE;

        for (final Object s : this.inventorySlots.inventorySlots) {
            if (s instanceof AppEngSlot && ((Slot) s).xPos < 197) {
                this.repositionSlot((AppEngSlot) s);
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

    private void clearAndBuildInternalSlots(int normalSlotRows, int normalSlotOffsetY) {
        this.getMeSlots().clear();
        for (int y = 0; y < normalSlotRows; y++) {
            for (int x = 0; x < this.perRow; x++) {
                this.getMeSlots()
                        .add(new InternalSlotME(this.repo, x + y * this.perRow,
                                this.offsetX + x * 18, normalSlotOffsetY + y * 18));
            }
        }
    }

    @Override
    protected void setupWidgets() {
        // Terminal search field
        this.searchField = MUITextFieldWidget.addSearchField(this,
                MUITextFieldWidget.SearchFieldSpec.builder(
                        Math.max(SEARCH_FIELD_X_MIN, this.offsetX),
                        SEARCH_FIELD_Y,
                        SEARCH_FIELD_WIDTH)
                        .height(SEARCH_FIELD_HEIGHT)
                        .onTextChange(text -> {
                            this.repo.setSearchString(text);
                            this.updateScrollBar();
                        })
                        .build());
        this.searchField.setMaxStringLength(SEARCH_FIELD_MAX_LENGTH);
        this.searchField.setSelectionColor(SEARCH_FIELD_SELECTION_COLOR);

        // Toolbar: settings buttons + type filter buttons (AEKeyType-based) + pins + crafting status
        this.toolbar.buildAndRegister();
    }

    // ========== Drawing ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(this.myName.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), 8, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        this.pinSystem.drawPinBackgrounds(this.mc, this.zLevel);

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

        this.drawTexturedModalRect(offsetX, offsetY + 16 + this.rows * 18 + this.lowerTextureOffset, 0,
                106 - 18 - 18, x_width, 99 + this.reservedSpace - this.lowerTextureOffset);

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
    }

    protected String getBackground() {
        return "guis/terminal.png";
    }

    @Override
    protected boolean isPowered() {
        return this.repo.hasPower();
    }

    // ========== Input events ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        // Legacy TypeToggleButton handling removed — type filter is now handled by TerminalToolbar
        // Subclasses may add their own buttons here via super.actionPerformed()
    }

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        // Pin interaction delegation
        if (pinSystem.handleMouseClicked(
                xCoord - this.guiLeft, yCoord - this.guiTop, btn, isShiftKeyDown(), isCtrlKeyDown())) {
            return;
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
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

            final boolean mouseInGui = this.isPointInRegion(0, 0, this.xSize, this.ySize, this.currentMouseX,
                    this.currentMouseY);

            final MUITextFieldWidget.TerminalKeyResult result =
                    this.searchField.handleTerminalKeyTyped(character, key, this.isAutoFocus, mouseInGui);

            switch (result) {
                case HANDLED:
                    this.keyHandled = mouseInGui;
                    break;
                case SUPPRESSED:
                    return;
                case NOT_HANDLED:
                    super.keyTyped(character, key);
                    break;
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
        // Pin row adjustment via Ctrl+scroll
        if (isCtrlKeyDown()) {
            if (pinSystem.handleMouseWheel(x - this.guiLeft, y - this.guiTop, wheel)) {
                return;
            }
        }

        // VirtualMEMonitorableSlot scroll interaction
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

    // ========== Private helpers ==========

    private void reinitalize() {
        this.buttonList.clear();
        this.initGui();
    }

    private void sendPinRowsUpdate(PinsRows craftingRows, PinsRows playerRows) {
        try {
            NetworkHandler.instance().sendToServer(new appeng.core.sync.packets.PacketPinsUpdate(craftingRows,
                    playerRows));
        } catch (final Exception e) {
            AELog.debug(e);
        }
    }

    private void onPinsUpdated() {
        this.pinSystem.onPinsUpdated();
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
        this.toolbar.updateSetting(manager, settingName, newValue);
        this.repo.updateView();
    }

    // ========== JEI integration ==========

    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> exclusionArea = new ArrayList<>();

        int yOffset = guiTop + 8 + this.jeiOffset;

        int totalVisibleButtons = toolbar.getVisibleSettingsButtonCount()
                + toolbar.getVisibleTypeFilterButtonCount();

        Rectangle sortDir = new Rectangle(guiLeft - 18, yOffset, 20,
                totalVisibleButtons * 20 + totalVisibleButtons - 2);
        exclusionArea.add(sortDir);

        if (this.viewCell) {
            Rectangle viewMode = new Rectangle(guiLeft + 205, yOffset - 4, 24,
                    19 * monitorableContainer.getViewCells().length);
            exclusionArea.add(viewMode);
        }

        return exclusionArea;
    }

    // ========== Subclass extension methods ==========

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

    // ========== Accessors ==========

    public int getReservedSpace() {
        return this.reservedSpace;
    }

    public void setReservedSpace(final int reservedSpace) {
        this.reservedSpace = reservedSpace;
    }

    public boolean isCustomSortOrder() {
        return this.customSortOrder;
    }

    public void setCustomSortOrder(final boolean customSortOrder) {
        this.customSortOrder = customSortOrder;
    }

    public boolean isTypeEnabled(IAEStackType<?> type) {
        AEKeyType keyType = AEKeyType.fromLegacyType(type);
        return keyType != null && this.repo.isTypeEnabled(keyType);
    }

    public boolean isTypeEnabled(AEKeyType type) {
        return this.repo.isTypeEnabled(type);
    }

    public ContainerMEMonitorable getMonitorableContainer() {
        return this.monitorableContainer;
    }

    public int getRows() {
        return this.rows;
    }

    public static int getCraftingGridOffsetX() {
        return craftingGridOffsetX;
    }

    public static int getCraftingGridOffsetY() {
        return craftingGridOffsetY;
    }
}
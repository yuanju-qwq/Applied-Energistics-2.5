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

package appeng.client.mui.module;

import java.io.IOException;
import java.util.List;

import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMEPinSlot;
import appeng.client.gui.slots.VirtualMEMonitorableSlot;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.me.ItemRepo;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.helpers.InventoryAction;

/**
 * Terminal pin system module extracted from {@code MUIMEMonitorablePanel}.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Pin row calculation (crafting vs player pin rows, total pin rows)</li>
 *   <li>{@link VirtualMEPinSlot} array creation and lifecycle</li>
 *   <li>Pin interaction events:
 *       <ul>
 *         <li>Ctrl+left-click on ME slot → pin</li>
 *         <li>Shift+right-click on pin slot → unpin</li>
 *         <li>Ctrl+scroll over ME grid → adjust pin rows</li>
 *       </ul>
 *   </li>
 *   <li>Pins button click handling (left/right-click to cycle rows)</li>
 *   <li>Pin background drawing in drawFG</li>
 *   <li>Pin data sync from container</li>
 * </ul>
 */
public class TerminalPinSystem {

    // ========== Host interface ==========

    public interface Host {
        ContainerMEMonitorable getMonitorableContainer();

        ItemRepo getRepo();

        int getRows();

        int getPerRow();

        int getOffsetX();

        List<GuiCustomSlot> getGuiSlots();

        FontRenderer getFontRenderer();

        boolean isPointInRegion(int x, int y, int w, int h, int mx, int my);

        void sendPinRowsUpdatePacket(PinsRows crafting, PinsRows player);

        void reinitializeGui();
    }

    // ========== State ==========

    private final Host host;

    private PinsRows craftingPinsRows = PinsRows.DISABLED;
    private PinsRows playerPinsRows = PinsRows.DISABLED;
    private VirtualMEPinSlot[] pinSlots = null;
    private int totalPinRows = 0;

    public TerminalPinSystem(Host host) {
        this.host = host;
    }

    // ========== Accessors ==========

    public PinsRows getCraftingPinsRows() {
        return craftingPinsRows;
    }

    public PinsRows getPlayerPinsRows() {
        return playerPinsRows;
    }

    public int getTotalPinRows() {
        return totalPinRows;
    }

    public VirtualMEPinSlot[] getPinSlots() {
        return pinSlots;
    }

    // ========== Initialization ==========

    /**
     * Sync pin row configuration from the container's client-side data.
     */
    public void syncFromContainer() {
        ContainerMEMonitorable container = host.getMonitorableContainer();
        this.craftingPinsRows = container.getClientMaxCraftingPinRows();
        this.playerPinsRows = container.getClientMaxPlayerPinRows();
    }

    /**
     * Calculate pin rows and create pin slots.
     * <p>
     * This must be called during initGui, after rows and perRow have been computed.
     *
     * @return the Y offset (in panel-local coordinates) where normal ME slots should start
     */
    public int calculateAndCreateSlots() {
        int rows = host.getRows();
        int perRow = host.getPerRow();
        int offsetX = host.getOffsetX();
        ContainerMEMonitorable container = host.getMonitorableContainer();

        int craftingRows = Math.min(this.craftingPinsRows.ordinal(), 16);
        int playerRows = Math.min(this.playerPinsRows.ordinal(), 16);
        int pinMaxSize = Math.max(0, rows - 1);
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

        this.pinSlots = new VirtualMEPinSlot[this.totalPinRows * perRow];
        final boolean playerFirst = container.getClientPinSectionOrder() == PinSectionOrder.PLAYER_FIRST;
        int slotIdx = 0;
        int firstRows = playerFirst ? playerRows : craftingRows;
        int secondRows = playerFirst ? craftingRows : playerRows;
        boolean firstIsCrafting = !playerFirst;
        slotIdx = createPinSection(slotIdx, firstRows, perRow, 0, firstIsCrafting, offsetX);
        slotIdx = createPinSection(slotIdx, secondRows, perRow, firstRows, !firstIsCrafting, offsetX);

        return 18 + this.totalPinRows * 18;
    }

    private int createPinSection(int slotIdx, int sectionRows, int pinsPerRow, int rowOffset,
            boolean isCrafting, int offsetX) {
        int baseIndex = isCrafting ? 0 : appeng.items.contents.PinList.PLAYER_OFFSET;
        for (int y = 0; y < sectionRows; y++) {
            for (int x = 0; x < pinsPerRow; x++) {
                VirtualMEPinSlot slot = new VirtualMEPinSlot(
                        offsetX + x * 18,
                        18 + (rowOffset + y) * 18,
                        host.getMonitorableContainer().getClientPinList(),
                        baseIndex + y * pinsPerRow + x,
                        isCrafting);
                this.pinSlots[slotIdx++] = slot;
            }
        }
        return slotIdx;
    }

    /**
     * Register pin slots into the panel's guiSlots list.
     */
    public void registerToGuiSlots() {
        List<GuiCustomSlot> guiSlots = host.getGuiSlots();
        guiSlots.removeIf(s -> s instanceof VirtualMEPinSlot);
        if (this.pinSlots != null) {
            for (VirtualMEPinSlot slot : this.pinSlots) {
                guiSlots.add(slot);
            }
        }
    }

    // ========== Drawing ==========

    /**
     * Draw pin slot backgrounds. Call from the panel's drawFG.
     */
    public void drawPinBackgrounds(net.minecraft.client.Minecraft mc, float zLevel) {
        if (this.pinSlots != null && this.pinSlots.length > 0) {
            VirtualMEPinSlot.drawSlotsBackground(this.pinSlots, mc, zLevel);
        }
    }

    // ========== Mouse events ==========

    /**
     * Handle mouse click for pin interactions.
     *
     * @param localX mouse X relative to panel
     * @param localY mouse Y relative to panel
     * @param btn    mouse button
     * @param isShiftDown whether shift is held
     * @param isCtrlDown  whether ctrl is held
     * @return true if the event was consumed
     */
    public boolean handleMouseClicked(int localX, int localY, int btn,
            boolean isShiftDown, boolean isCtrlDown) throws IOException {
        List<GuiCustomSlot> guiSlots = host.getGuiSlots();

        for (GuiCustomSlot slot : guiSlots) {
            if (host.isPointInRegion(slot.xPos(), slot.yPos(),
                    slot.getWidth(), slot.getHeight(), localX, localY)) {
                if (slot instanceof VirtualMEPinSlot pinSlot && btn == 1 && isShiftDown) {
                    IAEStack<?> stack = pinSlot.getAEStack();
                    if (stack != null) {
                        ContainerMEMonitorable c = host.getMonitorableContainer();
                        ((AEBaseContainer) c).setTargetStack(stack);
                        PacketInventoryAction p = new PacketInventoryAction(
                                InventoryAction.UNSET_PIN,
                                c.inventorySlots.size(), -1);
                        NetworkHandler.instance().sendToServer(p);
                        return true;
                    }
                } else if (slot instanceof VirtualMEMonitorableSlot meSlot && btn == 0 && isCtrlDown) {
                    IAEStack<?> stack = meSlot.getAEStack();
                    if (stack != null) {
                        ContainerMEMonitorable c = host.getMonitorableContainer();
                        ((AEBaseContainer) c).setTargetStack(stack);
                        PacketInventoryAction p = new PacketInventoryAction(
                                InventoryAction.SET_ITEM_PIN,
                                c.inventorySlots.size(), -1);
                        NetworkHandler.instance().sendToServer(p);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    // ========== Scroll events ==========

    /**
     * Handle Ctrl+scroll over the ME grid area to adjust pin rows.
     *
     * @param localX mouse X relative to panel
     * @param localY mouse Y relative to panel
     * @param wheel  scroll delta
     * @return true if the event was consumed
     */
    public boolean handleMouseWheel(int localX, int localY, int wheel) {
        int perRow = host.getPerRow();
        int rows = host.getRows();
        int offsetX = host.getOffsetX();

        if (host.isPointInRegion(offsetX, 18, perRow * 18, rows * 18, localX, localY)) {
            boolean rmb = Mouse.isButtonDown(1);
            boolean ctrl = GuiScreen.isCtrlKeyDown();
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
            if (c + p < rows) {
                sendPinRowsUpdate(PinsRows.fromOrdinal(c), PinsRows.fromOrdinal(p));
            }
            return true;
        }

        return false;
    }

    // ========== Pins button ==========

    /**
     * Handle left/right-click on the pins button.
     * Left-click cycles player pin rows; right-click cycles crafting pin rows.
     */
    public void handlePinsButtonClick() {
        boolean rmb = Mouse.isButtonDown(1);
        if (rmb) {
            int c = Math.min(this.craftingPinsRows.ordinal() + 1, 16);
            if (c + this.playerPinsRows.ordinal() >= host.getRows()) {
                c = 0;
            }
            sendPinRowsUpdate(PinsRows.fromOrdinal(c), this.playerPinsRows);
        } else {
            int p = Math.min(this.playerPinsRows.ordinal() + 1, 16);
            if (p + this.craftingPinsRows.ordinal() >= host.getRows()) {
                p = 0;
            }
            sendPinRowsUpdate(this.craftingPinsRows, PinsRows.fromOrdinal(p));
        }
    }

    // ========== Container sync ==========

    /**
     * Send a pin rows update packet to the server and reinitialize the GUI.
     */
    private void sendPinRowsUpdate(PinsRows craftingRows, PinsRows playerRows) {
        try {
            NetworkHandler.instance().sendToServer(new PacketPinsUpdate(craftingRows, playerRows));
        } catch (Exception e) {
            AELog.debug(e);
        }
    }

    /**
     * Called by the panel when pin data is updated from the server.
     * Triggers a GUI reinitialize to reflect the new pin layout.
     */
    public void onPinsUpdated() {
        ContainerMEMonitorable container = host.getMonitorableContainer();
        PinsRows newCrafting = container.getClientMaxCraftingPinRows();
        PinsRows newPlayer = container.getClientMaxPlayerPinRows();
        if (newCrafting != this.craftingPinsRows || newPlayer != this.playerPinsRows) {
            this.craftingPinsRows = newCrafting;
            this.playerPinsRows = newPlayer;
            host.reinitializeGui();
        }
    }
}
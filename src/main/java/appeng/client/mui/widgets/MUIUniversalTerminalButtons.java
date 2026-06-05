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

package appeng.client.mui.widgets;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.client.mui.IMUIWidget;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.items.tools.powered.ToolWirelessUniversalTerminal;
import appeng.items.tools.powered.WirelessTerminalMode;

/**
 * Universal wireless terminal mode-switch button manager (MUI version).
 * <p>
 * Used by all wireless terminal MUI panels. When the player is holding a
 * {@link ToolWirelessUniversalTerminal}, this helper adds a vertical column of
 * {@link MUITabButton}s to the left side of the GUI — one for each installed
 * mode other than the currently-active mode.
 * <p>
 * Equivalent to the legacy {@code UniversalTerminalButtons}, but uses MUI
 * widgets and callback handlers instead of the legacy {@code actionPerformed}
 * pattern.
 */
public class MUIUniversalTerminalButtons {

    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final ItemStack terminalStack;
    private final boolean isUniversalTerminal;

    public MUIUniversalTerminalButtons(InventoryPlayer ip) {
        this.terminalStack = findUniversalTerminal(ip);
        this.isUniversalTerminal = !this.terminalStack.isEmpty();
    }

    /**
     * Initialize mode-switch buttons. Call from the host panel's {@code initGui()}.
     *
     * @param panelX        GUI left edge in screen coordinates
     * @param panelY        GUI top edge in screen coordinates
     * @param buttonList    the host panel's MUI widget list (typically
     *                      {@code panel.widgets} or a module's container)
     * @param baseButtonId  base id for buttons (kept for API compatibility)
     * @param itemRender    item renderer used by the tab buttons
     * @return number of buttons created
     */
    public int initButtons(int panelX, int panelY, List<IMUIWidget> buttonList, int baseButtonId,
            @Nullable RenderItem itemRender) {
        this.modeButtons.clear();
        if (!this.isUniversalTerminal) {
            return 0;
        }

        WirelessTerminalMode currentMode = ToolWirelessUniversalTerminal.getMode(this.terminalStack);
        int[] installedModes = ToolWirelessUniversalTerminal.getInstalledModes(this.terminalStack);
        int count = 0;

        for (int modeId : installedModes) {
            WirelessTerminalMode mode = WirelessTerminalMode.fromId((byte) modeId);
            if (mode == currentMode) {
                continue;
            }

            ItemStack iconStack = getIconForMode(mode);
            String tooltip = mode.getName();

            MUITabButton btn = new MUITabButton(panelX - 22, panelY + 4 + count * 24,
                    iconStack, tooltip, itemRender);
            btn.setOnClick(tab -> sendModeSwitchPacket(mode));

            ModeButton modeButton = new ModeButton(btn, mode);
            this.modeButtons.add(modeButton);
            buttonList.add(btn);
            count++;
        }

        return count;
    }

    private void sendModeSwitchPacket(WirelessTerminalMode mode) {
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("UniversalTerminal.SwitchMode", String.valueOf(mode.getId())));
        } catch (Exception e) {
            // ignore network errors
        }
    }

    /**
     * Returns true if the player is holding a universal terminal.
     */
    public boolean isUniversalTerminal() {
        return this.isUniversalTerminal;
    }

    /**
     * Icon item for a given wireless terminal mode. Falls back to empty stack.
     */
    private static ItemStack getIconForMode(WirelessTerminalMode mode) {
        switch (mode) {
            case TERMINAL:
                return AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            case CRAFTING:
                return AEApi.instance().definitions().items().wirelessCraftingTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case FLUID:
                return AEApi.instance().definitions().items().wirelessFluidTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case PATTERN:
                return AEApi.instance().definitions().items().wirelessPatternTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case INTERFACE:
                return AEApi.instance().definitions().items().wirelessInterfaceTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case DUAL_INTERFACE:
                return AEApi.instance().definitions().items().wirelessDualInterfaceTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * Locate the universal terminal in the player's main or off hand.
     */
    private static ItemStack findUniversalTerminal(InventoryPlayer ip) {
        ItemStack mainHand = ip.player.getHeldItemMainhand();
        if (mainHand.getItem() instanceof ToolWirelessUniversalTerminal) {
            return mainHand;
        }
        ItemStack offHand = ip.player.getHeldItemOffhand();
        if (offHand.getItem() instanceof ToolWirelessUniversalTerminal) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Internal data record pairing a tab button with its associated mode.
     */
    private static final class ModeButton {
        final MUITabButton button;
        final WirelessTerminalMode mode;

        ModeButton(MUITabButton button, WirelessTerminalMode mode) {
            this.button = button;
            this.mode = mode;
        }
    }
}

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

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.mui.widgets.MUIButtonPool;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.render.BlockPosHighlighter;
import appeng.core.localization.PlayerMessages;
import appeng.util.BlockPosUtils;

/**
 * Highlight / positioning module — reusable component for block highlight functionality
 * in interface terminal panels.
 *
 * <p>Consolidates the following capabilities that were previously duplicated across
 * {@link InterfaceListModule} and {@code MUIInterfaceConfigurationTerminalPanel}:
 *
 * <ul>
 *   <li><b>Block highlight</b> — highlight a block position in the world via
 *       {@link BlockPosHighlighter}</li>
 *   <li><b>Cross-dimension detection</b> — detect when the target block is in a different
 *       dimension and show an appropriate message instead of highlighting</li>
 *   <li><b>Position prompt</b> — send localized chat messages with the highlighted
 *       block coordinates or a cross-dimension notice</li>
 *   <li><b>Highlight button behavior</b> — create and wire highlight buttons via
 *       {@link MUIButtonPool}, handle click events</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Create the module
 * HighlightModule highlight = new HighlightModule(host);
 *
 * // Update position data when NBT arrives
 * highlight.updatePosition(inv, pos, dim);
 *
 * // Create a highlight button from pool
 * highlight.createHighlightButton(pool, x, y, inv);
 *
 * // Wire pool onClick to module handler
 * pool.setDefaultOnClick(btn -> highlight.handleClick(btn));
 * </pre>
 */
public class HighlightModule {

    // ========== Host interface ==========

    /**
     * The host GUI must implement this interface to provide context for the module.
     */
    public interface Host {
        EntityPlayer getPlayer();

        /**
         * Close the current screen after a successful highlight.
         */
        void closeScreen();
    }

    // ========== Fields ==========

    private final Host host;

    /** Maps highlight buttons to their associated inventory. */
    private final Map<MUIButtonWidget, ClientDCInternalInv> buttonToInv = new HashMap<>();

    /** Maps each inventory to its block position. */
    private final Map<ClientDCInternalInv, BlockPos> invToPos = new HashMap<>();

    /** Maps each inventory to its dimension ID. */
    private final Map<ClientDCInternalInv, Integer> invToDim = new HashMap<>();

    // ========== Construction ==========

    public HighlightModule(Host host) {
        this.host = host;
    }

    // ========== Data management ==========

    /**
     * Update the block position and dimension for the given inventory.
     * Called when NBT data arrives from the server.
     *
     * @param inv the client-side inventory representation
     * @param pos the block position of the interface/block
     * @param dim the dimension ID of the interface/block
     */
    public void updatePosition(ClientDCInternalInv inv, BlockPos pos, int dim) {
        this.invToPos.put(inv, pos);
        this.invToDim.put(inv, dim);
    }

    /**
     * Remove all stored position data.
     */
    public void clear() {
        this.buttonToInv.clear();
        this.invToPos.clear();
        this.invToDim.clear();
    }

    // ========== Button management ==========

    /**
     * Register a button-to-inventory mapping. Called when a highlight button
     * is created so the click handler can look up the target.
     *
     * @param btn the highlight button
     * @param inv the associated inventory
     */
    public void registerButton(MUIButtonWidget btn, ClientDCInternalInv inv) {
        this.buttonToInv.put(btn, inv);
    }

    /**
     * Convenience factory: acquire a highlight button from the pool, register it,
     * and return the created button.
     *
     * @param pool the button pool to acquire from
     * @param relX panel-relative X position
     * @param relY panel-relative Y position
     * @param inv  the associated inventory
     * @return the acquired button
     */
    public MUIButtonWidget createHighlightButton(MUIButtonPool pool, int relX, int relY, ClientDCInternalInv inv) {
        MUIButtonWidget btn = pool.acquireSettings(relX, relY, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
        registerButton(btn, inv);
        return btn;
    }

    /**
     * Wire the pool's default onClick to this module's handler.
     * <p>
     * Call once after creating the pool:
     * <pre>
     * highlightModule.wirePoolClickHandler(pool);
     * </pre>
     */
    public void wirePoolClickHandler(MUIButtonPool pool) {
        pool.setDefaultOnClick(btn -> handleClick(btn));
    }

    // ========== Highlight click handling ==========

    /**
     * Handle a highlight button click.
     * <p>
     * Looks up the associated inventory, checks for cross-dimension,
     * and either highlights the block or shows a dimension mismatch message.
     *
     * @param btn the clicked highlight button
     */
    public void handleClick(MUIButtonWidget btn) {
        ClientDCInternalInv inv = buttonToInv.get(btn);
        if (inv == null) {
            return;
        }

        BlockPos blockPos = invToPos.get(inv);
        if (blockPos == null) {
            return;
        }

        EntityPlayer player = host.getPlayer();
        BlockPos playerPos = player.getPosition();
        int playerDim = player.world.provider.getDimension();
        int interfaceDim = invToDim.getOrDefault(inv, playerDim);

        if (playerDim != interfaceDim) {
            sendCrossDimensionMessage(player, interfaceDim);
        } else {
            BlockPosHighlighter.hilightBlock(blockPos,
                    System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, playerPos),
                    playerDim);
            player.sendStatusMessage(
                    PlayerMessages.InterfaceHighlighted.get(
                            blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                    false);
        }

        host.closeScreen();
    }

    // ========== Internal helpers ==========

    /**
     * Send a cross-dimension message to the player, with fallback.
     */
    private static void sendCrossDimensionMessage(EntityPlayer player, int interfaceDim) {
        try {
            player.sendStatusMessage(
                    PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim,
                            DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()),
                    false);
        } catch (Exception e) {
            player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
        }
    }
}

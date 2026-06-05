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

import java.io.IOException;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.SortOrder;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIToggleButton;
import appeng.container.implementations.ContainerSecurityStation;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

/**
 * MUI security station GUI panel.
 *
 * Extends {@link MUIMEMonitorablePanel} and implements {@link MUISecurityStationPanel} marker interface.
 * Adds 5 security permission toggle buttons (inject/extract/craft/build/security) using
 * {@link MUIToggleButton} with callback-based event handling.
 */
public class MUISecurityStationPanelImpl extends MUIMEMonitorablePanel implements MUISecurityStationPanel {

    // ========== Permission buttons ==========
    private MUIToggleButton inject;
    private MUIToggleButton extract;
    private MUIToggleButton craft;
    private MUIToggleButton build;
    private MUIToggleButton security;

    public MUISecurityStationPanelImpl(final ContainerSecurityStation container) {
        super(container);
        this.setCustomSortOrder(false);
        this.setReservedSpace(33);

        // Increase width to accommodate permission buttons
        this.xSize += 56;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();

        final int top = this.guiTop + this.ySize - 116;

        this.inject = new MUIToggleButton(this.guiLeft + 56, top, 11 * 16, 12 * 16,
                SecurityPermissions.INJECT.getUnlocalizedName(),
                SecurityPermissions.INJECT.getUnlocalizedTip());
        this.inject.setOnToggle(btn -> sendTogglePacket(SecurityPermissions.INJECT));
        this.addWidget(this.inject);

        this.extract = new MUIToggleButton(this.guiLeft + 56 + 18, top, 11 * 16 + 1, 12 * 16 + 1,
                SecurityPermissions.EXTRACT.getUnlocalizedName(),
                SecurityPermissions.EXTRACT.getUnlocalizedTip());
        this.extract.setOnToggle(btn -> sendTogglePacket(SecurityPermissions.EXTRACT));
        this.addWidget(this.extract);

        this.craft = new MUIToggleButton(this.guiLeft + 56 + 18 * 2, top, 11 * 16 + 2, 12 * 16 + 2,
                SecurityPermissions.CRAFT.getUnlocalizedName(),
                SecurityPermissions.CRAFT.getUnlocalizedTip());
        this.craft.setOnToggle(btn -> sendTogglePacket(SecurityPermissions.CRAFT));
        this.addWidget(this.craft);

        this.build = new MUIToggleButton(this.guiLeft + 56 + 18 * 3, top, 11 * 16 + 3, 12 * 16 + 3,
                SecurityPermissions.BUILD.getUnlocalizedName(),
                SecurityPermissions.BUILD.getUnlocalizedTip());
        this.build.setOnToggle(btn -> sendTogglePacket(SecurityPermissions.BUILD));
        this.addWidget(this.build);

        this.security = new MUIToggleButton(this.guiLeft + 56 + 18 * 4, top, 11 * 16 + 4, 12 * 16 + 4,
                SecurityPermissions.SECURITY.getUnlocalizedName(),
                SecurityPermissions.SECURITY.getUnlocalizedTip());
        this.security.setOnToggle(btn -> sendTogglePacket(SecurityPermissions.SECURITY));
        this.addWidget(this.security);
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.SecurityCardEditor.getLocal(), 8,
                this.ySize - 96 + 1 - this.getReservedSpace(), AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected String getBackground() {
        final ContainerSecurityStation cs = (ContainerSecurityStation) this.inventorySlots;

        this.inject.setState((cs.getPermissionMode() & (1 << SecurityPermissions.INJECT.ordinal())) > 0);
        this.extract.setState((cs.getPermissionMode() & (1 << SecurityPermissions.EXTRACT.ordinal())) > 0);
        this.craft.setState((cs.getPermissionMode() & (1 << SecurityPermissions.CRAFT.ordinal())) > 0);
        this.build.setState((cs.getPermissionMode() & (1 << SecurityPermissions.BUILD.ordinal())) > 0);
        this.security.setState((cs.getPermissionMode() & (1 << SecurityPermissions.SECURITY.ordinal())) > 0);

        return "guis/security_station.png";
    }

    // ========== Button events ==========
    //
    // Each toggle button registers its own onToggle callback in initGui() that calls
    // sendTogglePacket(SecurityPermissions), so no central actionPerformed override is required.

    /**
     * Sends a server-side packet to toggle the given security permission. Used by every
     * permission button's onToggle callback.
     */
    private void sendTogglePacket(SecurityPermissions permission) {
        try {
            NetworkHandler.instance()
                    .sendToServer(new PacketValueConfig("TileSecurityStation.ToggleOption", permission.name()));
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // ========== Sorting ==========

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }
}

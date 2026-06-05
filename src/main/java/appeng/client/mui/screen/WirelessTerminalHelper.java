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

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.widgets.MUIUniversalTerminalButtons;
import appeng.core.AppEng;

/**
 * Wireless terminal GUI common functionality helper class.
 * <p>
 * Encapsulates common functionality for all wireless terminal MUI panels:
 * <ul>
 *   <li>Wireless upgrade icon drawing (32x32 wirelessupgrades.png in top right corner)</li>
 *   <li>{@link MUIUniversalTerminalButtons} terminal mode switch button management</li>
 * </ul>
 * <p>
 * Used by composition in each {@link MUIWirelessTermPanel} implementation class (composition over inheritance).
 */
final class WirelessTerminalHelper {

    private static final ResourceLocation WIRELESS_ICON_TEX =
            new ResourceLocation(AppEng.MOD_ID, "textures/guis/wirelessupgrades.png");

    @Nullable
    private MUIUniversalTerminalButtons universalButtons;

    WirelessTerminalHelper() {
    }

    /**
     * Called in initGui(), initializes terminal mode switch buttons.
     * <p>
     * Adds {@link appeng.client.mui.widgets.MUITabButton} widgets to the supplied widget list.
     * Each button has its own onClick callback registered in
     * {@link MUIUniversalTerminalButtons}, so no central actionPerformed handling is required.
     *
     * @param ip            the player's inventory (used to detect a held universal terminal)
     * @param guiLeft       GUI absolute X
     * @param guiTop        GUI absolute Y
     * @param widgetList    the host panel's MUI widget list (typically {@code panel.widgets})
     * @param nextButtonId  base id (kept for API compatibility, no longer used by MUI widgets)
     * @param itemRender    item renderer used by the tab buttons
     */
    void initButtons(InventoryPlayer ip, int guiLeft, int guiTop,
            List<IMUIWidget> widgetList, int nextButtonId, @Nullable RenderItem itemRender) {
        this.universalButtons = new MUIUniversalTerminalButtons(ip);
        this.universalButtons.initButtons(guiLeft, guiTop, widgetList, nextButtonId, itemRender);
    }

    /**
     * Returns true if the helper has installed universal-terminal mode switch buttons.
     * Retained for compatibility with callers that need to check state.
     */
    boolean isUniversalTerminalActive() {
        return this.universalButtons != null && this.universalButtons.isUniversalTerminal();
    }

    /**
     * Called in drawBG(), draws wireless upgrade icon.
     * <p>
     * The drawing is delegated to the supplied MUI panel so the helper class
     * does not need to import {@code net.minecraft.client.gui.Gui} directly.
     *
     * @param panel  the MUI panel that is currently drawing
     * @param offsetX GUI absolute X
     * @param offsetY GUI absolute Y
     * @param iconX   Icon offset relative to offsetX (198 for item terminal, 175 for fluid terminal)
     * @param iconY   Icon offset relative to offsetY (default 127, 131 for fluid terminal)
     */
    void drawWirelessIcon(AEBasePanel panel, int offsetX, int offsetY, int iconX, int iconY) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(WIRELESS_ICON_TEX);
        panel.drawModalRectWithCustomSizedTexture(offsetX + iconX, offsetY + iconY, 0, 0, 32, 32, 32, 32);
    }
}

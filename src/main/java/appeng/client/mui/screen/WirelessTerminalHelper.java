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

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.core.AppEng;

/**
 * Wireless terminal GUI common functionality helper class.
 * <p>
 * Encapsulates common functionality for all wireless terminal MUI panels:
 * <ul>
 *   <li>Wireless upgrade icon drawing (32x32 wirelessupgrades.png in top right corner)</li>
 *   <li>{@link UniversalTerminalButtons} Terminal mode switch button management</li>
 * </ul>
 * <p>
 * Used by composition in each {@link MUIWirelessTermPanel} implementation class (composition over inheritance).
 */
final class WirelessTerminalHelper {

    private static final ResourceLocation WIRELESS_ICON_TEX =
            new ResourceLocation(AppEng.MOD_ID, "textures/guis/wirelessupgrades.png");

    private UniversalTerminalButtons universalButtons;

    WirelessTerminalHelper() {
    }

    /**
     * Called in initGui(), initializes terminal mode switch buttons.
     */
    void initButtons(InventoryPlayer ip, int guiLeft, int guiTop,
            List<GuiButton> buttonList, int nextButtonId, RenderItem itemRender) {
        this.universalButtons = new UniversalTerminalButtons(ip);
        this.universalButtons.initButtons(guiLeft, guiTop, buttonList, nextButtonId, itemRender);
    }

    /**
     * Called in actionPerformed(), handles terminal switch buttons first.
     *
     * @return true if the button has been handled
     */
    boolean handleButtonClick(GuiButton btn) {
        return this.universalButtons != null && this.universalButtons.handleButtonClick(btn);
    }

    /**
     * Called in drawBG(), draws wireless upgrade icon.
     *
     * @param offsetX GUI absolute X
     * @param offsetY GUI absolute Y
     * @param iconX   Icon offset relative to offsetX (198 for item terminal, 175 for fluid terminal)
     * @param iconY   Icon offset relative to offsetY (default 127, 131 for fluid terminal)
     */
    void drawWirelessIcon(int offsetX, int offsetY, int iconX, int iconY) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(WIRELESS_ICON_TEX);
        Gui.drawModalRectWithCustomSizedTexture(offsetX + iconX, offsetY + iconY, 0, 0, 32, 32, 32, 32);
    }
}

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

package appeng.client.mui;

import net.minecraft.util.ResourceLocation;

/**
 * AE2 unified visual theme.
 * <p>
 * Centralized management of color constants, font rendering parameters, and texture paths for MUI GUI,
 * ensuring all MUI panels have a consistent visual style.
 * <p>
 * <b>Usage guideline:</b> All MUI panels should reference constants from this class
 * instead of hardcoding color/size values directly in drawFG/drawBG methods.
 */
public final class AEMUITheme {

    private AEMUITheme() {
    }

    // ========== Color Constants ==========

    /** Title text color (dark gray, used by drawString in drawFG) */
    public static final int COLOR_TITLE = 0x404040;

    /** General text color */
    public static final int COLOR_TEXT = 0x404040;

    /** Text field text color (used by search boxes, name inputs, amount inputs, etc.) */
    public static final int COLOR_TEXT_FIELD = 0xFFFFFF;

    /** @deprecated Use {@link #COLOR_TEXT_FIELD} instead. */
    @Deprecated
    public static final int COLOR_SEARCH_TEXT = COLOR_TEXT_FIELD;

    /** Stack count text color */
    public static final int COLOR_STACK_COUNT = 0xFFFFFF;

    /** Craftable marker color (green + sign) */
    public static final int COLOR_CRAFTABLE = 0x00FF00;

    /** Tooltip background color */
    public static final int COLOR_TOOLTIP_BG = 0xF0100010;

    /** Panel background color (semi-transparent dark) */
    public static final int COLOR_PANEL_BG = 0xC8000000;

    /** Type tab: item highlight color */
    public static final int COLOR_TAB_ITEM = 0xFF8B4513;

    /** Type tab: fluid highlight color */
    public static final int COLOR_TAB_FLUID = 0xFF1E90FF;

    // ========== Font Parameters ==========

    /** Default font scale factor */
    public static final float FONT_SCALE_DEFAULT = 1.0f;

    /** Stack count font scale factor (small) */
    public static final float FONT_SCALE_STACK_COUNT = 0.5f;

    // ========== Texture Paths ==========

    /** MUI texture base path */
    private static final String TEXTURE_BASE = "appliedenergistics2:textures/gui/mui/";

    /** Generic panel background texture */
    public static final ResourceLocation TEX_PANEL_BG = new ResourceLocation(TEXTURE_BASE + "panel_bg.png");

    /** Generic button texture */
    public static final ResourceLocation TEX_BUTTONS = new ResourceLocation(TEXTURE_BASE + "buttons.png");

    /** Search bar texture */
    public static final ResourceLocation TEX_SEARCH_BAR = new ResourceLocation(TEXTURE_BASE + "search_bar.png");

    /** Type tab texture */
    public static final ResourceLocation TEX_TYPE_TABS = new ResourceLocation(TEXTURE_BASE + "type_tabs.png");

    /** Scrollbar texture */
    public static final ResourceLocation TEX_SCROLLBAR = new ResourceLocation(TEXTURE_BASE + "scrollbar.png");

    // ========== Size Constants ==========

    /** Standard slot size (pixels) */
    public static final int SLOT_SIZE = 18;

    /** Standard panel padding */
    public static final int PANEL_PADDING = 8;

    /** Standard button height */
    public static final int BUTTON_HEIGHT = 20;

    /** Search bar height */
    public static final int SEARCH_BAR_HEIGHT = 12;

    /** Type tab width */
    public static final int TYPE_TAB_WIDTH = 28;

    /** Type tab height */
    public static final int TYPE_TAB_HEIGHT = 28;
}

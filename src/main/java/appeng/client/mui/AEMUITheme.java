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
 * AE2 统一视觉主题。
 * <p>
 * 集中管理 MUI GUI 的颜色常量、字体渲染参数和贴图路径，
 * 确保所有 MUI 面板具有一致的视觉风格。
 */
public final class AEMUITheme {

    private AEMUITheme() {
    }

    // ========== 颜色常量 ==========

    /** 标题文字颜色（深灰） */
    public static final int COLOR_TITLE = 0x404040;

    /** 普通文字颜色 */
    public static final int COLOR_TEXT = 0x404040;

    /** 搜索框文字颜色 */
    public static final int COLOR_SEARCH_TEXT = 0xFFFFFF;

    /** 物品数量文字颜色 */
    public static final int COLOR_STACK_COUNT = 0xFFFFFF;

    /** 可合成标记颜色（绿色 + 号） */
    public static final int COLOR_CRAFTABLE = 0x00FF00;

    /** 工具提示背景颜色 */
    public static final int COLOR_TOOLTIP_BG = 0xF0100010;

    /** 面板背景颜色（半透明深色） */
    public static final int COLOR_PANEL_BG = 0xC8000000;

    /** 类型切换标签：物品高亮色 */
    public static final int COLOR_TAB_ITEM = 0xFF8B4513;

    /** 类型切换标签：流体高亮色 */
    public static final int COLOR_TAB_FLUID = 0xFF1E90FF;

    // ========== 字体参数 ==========

    /** 默认字体缩放因子 */
    public static final float FONT_SCALE_DEFAULT = 1.0f;

    /** 物品数量字体缩放因子（小号） */
    public static final float FONT_SCALE_STACK_COUNT = 0.5f;

    // ========== 贴图路径 ==========

    /** MUI 贴图根路径 */
    private static final String TEXTURE_BASE = "appliedenergistics2:textures/gui/mui/";

    /** 通用面板背景贴图 */
    public static final ResourceLocation TEX_PANEL_BG = new ResourceLocation(TEXTURE_BASE + "panel_bg.png");

    /** 按钮通用贴图 */
    public static final ResourceLocation TEX_BUTTONS = new ResourceLocation(TEXTURE_BASE + "buttons.png");

    /** 搜索框贴图 */
    public static final ResourceLocation TEX_SEARCH_BAR = new ResourceLocation(TEXTURE_BASE + "search_bar.png");

    /** 类型切换标签贴图 */
    public static final ResourceLocation TEX_TYPE_TABS = new ResourceLocation(TEXTURE_BASE + "type_tabs.png");

    /** 滚动条贴图 */
    public static final ResourceLocation TEX_SCROLLBAR = new ResourceLocation(TEXTURE_BASE + "scrollbar.png");

    // ========== 尺寸常量 ==========

    /** 标准槽位大小（像素） */
    public static final int SLOT_SIZE = 18;

    /** 标准面板内边距 */
    public static final int PANEL_PADDING = 8;

    /** 标准按钮高度 */
    public static final int BUTTON_HEIGHT = 20;

    /** 搜索框高度 */
    public static final int SEARCH_BAR_HEIGHT = 12;

    /** 类型切换标签宽度 */
    public static final int TYPE_TAB_WIDTH = 28;

    /** 类型切换标签高度 */
    public static final int TYPE_TAB_HEIGHT = 28;
}

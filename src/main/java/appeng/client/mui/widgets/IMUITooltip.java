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

/**
 * MUI 命名空间下的 Tooltip 契约。
 * <p>
 * 由 {@link appeng.client.mui.AEBasePanel} 统一扫描并渲染。完全等价于已废弃的
 * {@code appeng.client.gui.widgets.ITooltip}，仅迁移至 MUI 包以收敛依赖方向。
 */
public interface IMUITooltip {

    /**
     * @return the tooltip message.
     */
    String getMessage();

    /**
     * @return the x position of the object that triggers the tooltip.
     */
    int xPos();

    /**
     * @return the y position of the object that triggers the tooltip.
     */
    int yPos();

    /**
     * @return the width of the object that triggers the tooltip.
     */
    int getWidth();

    /**
     * @return the height of the object that triggers the tooltip.
     */
    int getHeight();

    /**
     * @return true if the object is currently being drawn / is visible.
     */
    boolean isVisible();
}

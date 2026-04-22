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

import appeng.container.implementations.ContainerMEPortableCell;

/**
 * MUI 版 ME 便携单元 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiMEPortableCell}。
 * 继承 {@link MUIMEMonitorablePanel} 并实现 {@link MUIPortableCellPanel} 标记接口，
 * 唯一的差异是将最大行数限制为 3。
 */
public class MUIMEPortableCellPanelImpl extends MUIMEMonitorablePanel implements MUIPortableCellPanel {

    public MUIMEPortableCellPanelImpl(final ContainerMEPortableCell container) {
        super(container);
    }

    @Override
    protected int getMaxRows() {
        return 3;
    }
}

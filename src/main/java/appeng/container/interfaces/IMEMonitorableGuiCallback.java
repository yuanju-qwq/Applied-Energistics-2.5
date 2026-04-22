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

package appeng.container.interfaces;

import java.util.List;

import appeng.api.storage.data.IAEStack;
import appeng.util.IConfigManagerHost;

/**
 * ME 终端 GUI 的回调接口。
 * <p>
 * 允许 {@link appeng.container.implementations.ContainerMEMonitorable} 与不同的 GUI 实现通信。
 * 旧 GUI（{@link appeng.client.gui.implementations.GuiMEMonitorable}）和新 MUI 面板都可以实现此接口。
 */
public interface IMEMonitorableGuiCallback extends IConfigManagerHost {

    /**
     * 接收 ME 网络的物品/流体列表更新。
     *
     * @param list 更新的栈列表
     */
    void postUpdate(List<IAEStack<?>> list);
}

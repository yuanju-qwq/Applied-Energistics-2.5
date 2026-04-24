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

/**
 * 合成 CPU 状态 GUI 的回调接口。
 * <p>
 * 允许 {@link appeng.container.implementations.ContainerCraftingCPU} 与不同的 GUI 实现通信，
 * 而不必依赖具体的 GUI 类。MUI 面板通过实现此接口接收回调。
 */
public interface ICraftingCPUGuiCallback {

    /**
     * 接收合成 CPU 的混合物品/流体状态更新。
     *
     * @param list 更新的栈列表
     * @param ref  更新类型：0=已存储, 1=正在合成, 2=待处理
     */
    void postGenericUpdate(List<IAEStack<?>> list, byte ref);

    /**
     * 清空所有显示物品。
     */
    void clearItems();
}

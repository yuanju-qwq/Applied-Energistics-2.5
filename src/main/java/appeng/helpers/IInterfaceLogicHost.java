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

package appeng.helpers;

import java.util.EnumSet;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.IGridProxyable;

/**
 * 统一接口的宿主接口。
 *
 * 方块 (TileEntity) 或部件 (Part) 实现此接口，以承载 {@link InterfaceLogic}。
 * 合并了旧的 {@link IInterfaceHost} 和 {@link appeng.fluids.helper.IFluidInterfaceHost}。
 * 对应高版本 AE2 的 {@code InterfaceLogicHost}。
 */
public interface IInterfaceLogicHost extends IActionHost, IGridProxyable, IUpgradeableHost {

    InterfaceLogic getInterfaceLogic();

    /**
     * 返回接口面向的目标方向集合。
     */
    EnumSet<EnumFacing> getTargets();

    TileEntity getTileEntity();

    void saveChanges();

    /**
     * 当物品/流体从 Storage 被返回到网络时触发的回调。
     * 用于通知样板供应器锁定系统。
     */
    default void onStackReturnNetwork(IAEStack<?> stack) {
    }
}

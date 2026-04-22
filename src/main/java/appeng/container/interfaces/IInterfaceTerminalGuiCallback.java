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

package appeng.container.interfaces;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 接口终端 GUI 的回调接口。
 *
 * 用于解耦 {@link appeng.core.sync.packets.PacketCompressedNBT} 与具体 GUI 类的依赖。
 * 所有接收 NBT 格式库存更新的接口终端 GUI（包括 MUI 版）都应实现此接口。
 *
 * 对应的旧 GUI：
 * <ul>
 *   <li>{@code GuiInterfaceTerminal}</li>
 *   <li>{@code GuiInterfaceConfigurationTerminal}</li>
 *   <li>{@code GuiFluidInterfaceConfigurationTerminal}</li>
 *   <li>{@code GuiWirelessDualInterfaceTerminal}</li>
 * </ul>
 */
public interface IInterfaceTerminalGuiCallback {

    /**
     * 接收来自服务端的接口终端 NBT 数据更新。
     *
     * @param in 包含接口库存变更的压缩 NBT 数据
     */
    void postUpdate(NBTTagCompound in);
}

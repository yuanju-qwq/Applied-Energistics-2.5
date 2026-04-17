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

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * 由客户端 Container 或 GUI 实现，用于接收服务端通过
 * {@link appeng.core.sync.packets.PacketVirtualSlot} 同步过来的虚拟槽位数据。
 */
public interface IVirtualSlotHolder {

    /**
     * 接收批量虚拟槽位栈数据。
     *
     * @param invName    库存名称
     * @param slotStacks 槽位索引到 IAEStack 的映射（null 值表示该槽位被清空）
     */
    void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks);
}

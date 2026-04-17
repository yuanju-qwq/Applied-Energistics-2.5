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

/**
 * 由服务端 Container 实现，当客户端通过 {@link appeng.core.sync.packets.PacketVirtualSlot}
 * 发送虚拟槽位更新请求时，调用此接口将变更应用到服务端 IAEStackInventory。
 */
public interface IVirtualSlotSource {

    /**
     * 更新指定库存名称和槽位索引的虚拟栈。
     *
     * @param invName 库存名称
     * @param slotId  槽位索引
     * @param aes     新的栈内容（null 表示清空）
     */
    void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes);
}

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

package appeng.tile.inventory;

import appeng.api.storage.StorageName;

/**
 * 实现此接口的对象持有一个或多个 {@link IAEStackInventory}，
 * 并负责在库存变更时保存数据。
 */
public interface IIAEStackInventory {

    /**
     * 当 IAEStackInventory 内容发生变更时，由库存调用此方法请求保存。
     */
    void saveAEStackInv();

    /**
     * 根据 {@link StorageName} 获取对应的 {@link IAEStackInventory}。
     *
     * @param name 库存名称
     * @return 对应的库存，如果没有匹配的名称则返回 null
     */
    IAEStackInventory getAEInventoryByName(StorageName name);
}

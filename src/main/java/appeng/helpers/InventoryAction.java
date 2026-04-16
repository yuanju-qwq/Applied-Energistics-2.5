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

public enum InventoryAction {
    // standard vanilla mechanics.
    PICKUP_OR_SET_DOWN,
    SPLIT_OR_PLACE_SINGLE,
    CREATIVE_DUPLICATE,
    SHIFT_CLICK,

    // crafting term
    CRAFT_STACK,
    CRAFT_ITEM,
    CRAFT_SHIFT,

    // fluid term
    FILL_ITEM,
    EMPTY_ITEM,

    // extra...
    MOVE_REGION,
    PICKUP_SINGLE,
    UPDATE_HAND,
    ROLL_UP,
    ROLL_DOWN,
    AUTO_CRAFT,
    PLACE_SINGLE,
    DOUBLE,
    HALVE,
    PLACE_JEI_GHOST_ITEM,

    // 流体容器交互（从终端填充/抽取流体到手持容器）
    FILL_SINGLE_CONTAINER,
    FILL_CONTAINERS,
    DRAIN_SINGLE_CONTAINER,
    DRAIN_CONTAINERS,
    CONTAINER_QUICK_TRANSFER,

    // Pins 操作（物品钉选/置顶）
    SET_ITEM_PIN,
    SET_CONTAINER_PIN,
    UNSET_PIN
}

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

package appeng.api.storage;

/**
 * 用于标识 IAEStackInventory 的用途名称枚举。
 * 不同的 StorageName 表示不同语义的库存（如配置、合成输入、合成输出等）。
 */
public enum StorageName {

    NONE(""),
    CRAFTING_INPUT("crafting"),
    CRAFTING_OUTPUT("output"),
    CRAFTING_PATTERN("pattern"),
    CONFIG("config"),
    STORAGE("storage");

    private final String name;

    StorageName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}

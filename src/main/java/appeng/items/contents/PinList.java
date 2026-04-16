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

package appeng.items.contents;

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;

/**
 * 存储终端 Pins 数据的数组容器。
 * <p>
 * 分为两段：
 * <ul>
 * <li>合成 Pins：索引 0 ~ {@link #CRAFTING_SLOTS}-1</li>
 * <li>玩家 Pins：索引 {@link #PLAYER_OFFSET} ~ {@link #PLAYER_OFFSET}+{@link #PLAYER_SLOTS}-1</li>
 * </ul>
 */
public class PinList {

    /** 合成 Pin 槽位数（最多 16 行 × 9 列） */
    public static final int CRAFTING_SLOTS = 16 * 9;

    /** 玩家 Pin 区段在数组中的起始偏移 */
    public static final int PLAYER_OFFSET = CRAFTING_SLOTS;

    /** 玩家 Pin 槽位数（最多 16 行 × 9 列） */
    public static final int PLAYER_SLOTS = 16 * 9;

    /** 总槽位数 */
    public static final int TOTAL_SLOTS = CRAFTING_SLOTS + PLAYER_SLOTS;

    private final IAEStack<?>[] pins;

    public PinList() {
        this.pins = new IAEStack[TOTAL_SLOTS];
    }

    public int size() {
        return this.pins.length;
    }

    @Nullable
    public IAEStack<?> getPin(int index) {
        if (index < 0 || index >= this.pins.length) {
            return null;
        }
        return this.pins[index];
    }

    public void setPin(int index, @Nullable IAEStack<?> pin) {
        if (index >= 0 && index < this.pins.length) {
            this.pins[index] = pin;
        }
    }
}

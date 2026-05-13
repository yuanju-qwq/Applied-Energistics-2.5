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
 * Array container for storing terminal Pins data.
 * <p>
 * Divided into two segments:
 * <ul>
 * <li>Crafting Pins: index 0 ~ {@link #CRAFTING_SLOTS}-1</li>
 * <li>Player Pins: index {@link #PLAYER_OFFSET} ~ {@link #PLAYER_OFFSET}+{@link #PLAYER_SLOTS}-1</li>
 * </ul>
 */
public class PinList {

    /** Number of crafting pin slots (up to 16 rows x 9 columns) */
    public static final int CRAFTING_SLOTS = 16 * 9;

    /** Start offset of the player pin segment in the array */
    public static final int PLAYER_OFFSET = CRAFTING_SLOTS;

    /** Number of player pin slots (up to 16 rows x 9 columns) */
    public static final int PLAYER_SLOTS = 16 * 9;

    /** Total number of slots */
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

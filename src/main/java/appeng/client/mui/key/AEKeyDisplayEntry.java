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

package appeng.client.mui.key;

import java.util.Objects;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * AEKey-only display data for the new MUI bottom layer.
 */
public final class AEKeyDisplayEntry {

    private final AEKey key;
    private final long amount;
    private final boolean craftable;

    public AEKeyDisplayEntry(final AEKey key, final long amount, final boolean craftable) {
        this.key = Objects.requireNonNull(key, "key");
        this.amount = amount;
        this.craftable = craftable;
    }

    public static AEKeyDisplayEntry of(final AEKey key, final long amount, final boolean craftable) {
        return new AEKeyDisplayEntry(key, amount, craftable);
    }

    public static AEKeyDisplayEntry of(final GenericStack stack, final boolean craftable) {
        Objects.requireNonNull(stack, "stack");
        return new AEKeyDisplayEntry(stack.what(), stack.amount(), craftable);
    }

    public AEKey key() {
        return this.key;
    }

    public long amount() {
        return this.amount;
    }

    public boolean craftable() {
        return this.craftable;
    }

    public GenericStack toGenericStack() {
        return new GenericStack(this.key, this.amount);
    }
}

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

package appeng.util.prioritylist;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;

/**
 * AEKey-based fuzzy partition list. Uses {@link AEKey#fuzzyEquals(AEKey, FuzzyMode)} so that the
 * secondary component (e.g. NBT) and the fuzzy search value (e.g. damage) are evaluated
 * according to the supplied {@link FuzzyMode}.
 */
public final class FuzzyAEKeyPriorityList implements IAEKeyPartitionList {

    private final Iterable<AEKey> keys;
    private final FuzzyMode mode;

    public FuzzyAEKeyPriorityList(Iterable<AEKey> keys, FuzzyMode mode) {
        this.keys = keys;
        this.mode = mode;
    }

    @Override
    public boolean isListed(AEKey input) {
        if (input == null) {
            return false;
        }
        for (AEKey candidate : this.keys) {
            if (candidate != null && candidate.fuzzyEquals(input, this.mode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        return !this.keys.iterator().hasNext();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return this.keys;
    }
}

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

import java.util.HashSet;
import java.util.Set;

import appeng.api.stacks.AEKey;

/**
 * AEKey-based precise partition list. Membership is decided by a primary-key plus secondary
 * component (i.e. NBT) match — the same as the legacy {@link PrecisePriorityList} but working
 * directly on immutable {@link AEKey} instances.
 */
public final class PreciseAEKeyPriorityList implements IAEKeyPartitionList {

    private final Set<AEKey> keys;

    public PreciseAEKeyPriorityList(Iterable<AEKey> keys) {
        this.keys = new HashSet<>();
        for (AEKey key : keys) {
            if (key != null) {
                this.keys.add(key);
            }
        }
    }

    @Override
    public boolean isListed(AEKey input) {
        return input != null && this.keys.contains(input);
    }

    @Override
    public boolean isEmpty() {
        return this.keys.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return this.keys;
    }
}

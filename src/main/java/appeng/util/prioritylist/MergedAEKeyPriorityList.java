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

import java.util.ArrayList;
import java.util.Collection;

import appeng.api.stacks.AEKey;

/**
 * Merges several {@link AEKeyPartitionList} into a single list. A key is listed if it is not
 * blocked by any negative sublist and either there are no positive sublists or at least one
 * positive sublist accepts it.
 */
public final class MergedAEKeyPriorityList implements AEKeyPartitionList {

    private final Collection<AEKeyPartitionList> positive = new ArrayList<>();
    private final Collection<AEKeyPartitionList> negative = new ArrayList<>();

    public void addNewList(AEKeyPartitionList list, boolean isWhitelist) {
        if (isWhitelist) {
            this.positive.add(list);
        } else {
            this.negative.add(list);
        }
    }

    @Override
    public boolean isListed(AEKey input) {
        for (AEKeyPartitionList l : this.negative) {
            if (l.isListed(input)) {
                return false;
            }
        }

        if (!this.positive.isEmpty()) {
            for (AEKeyPartitionList l : this.positive) {
                if (l.isListed(input)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    @Override
    public boolean isEmpty() {
        return this.positive.isEmpty() && this.negative.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        throw new UnsupportedOperationException();
    }
}

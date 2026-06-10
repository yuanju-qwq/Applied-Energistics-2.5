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

import java.util.Collection;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * AEKey-based fuzzy partition list. Membership is decided by fuzzy-matching
 * against the keys stored in a {@link KeyCounter}, using the same semantics as
 * the legacy {@link FuzzyPriorityList} but working directly on immutable
 * {@link AEKey} instances.
 */
public final class FuzzyAEKeyPriorityList implements IAEKeyPartitionList {

    private final KeyCounter list;
    private final FuzzyMode mode;

    public FuzzyAEKeyPriorityList(final KeyCounter in, final FuzzyMode mode) {
        this.list = in;
        this.mode = mode;
    }

    @Override
    public boolean isListed(final AEKey input) {
        final Collection<?> out = this.list.findFuzzy(input, this.mode);
        return out != null && !out.isEmpty();
    }

    @Override
    public boolean isEmpty() {
        return this.list.isEmpty();
    }

    @Override
    public Iterable<AEKey> getItems() {
        return this.list.keySet();
    }
}

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

package appeng.util.item;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;

/**
 * Heterogeneous stack list for item/fluid mixed views.
 *
 * Unlike {@code IItemList<T>}, this interface models a list that may contain multiple
 * {@link IAEStackType} buckets at once.
 */
public interface IMixedStackList extends Iterable<IAEStackBase> {

    void add(IAEStack<?> option);

    @Nullable
    IAEStack<?> findPrecise(IAEStack<?> stack);

    Collection<IAEStack<?>> findFuzzy(IAEStack<?> filter, FuzzyMode fuzzy);

    void addStorage(IAEStack<?> option);

    void addCrafting(IAEStack<?> option);

    void addRequestable(IAEStack<?> option);

    @Nullable
    IAEStack<?> getFirstMixedItem();

    int size();

    void resetStatus();

    default boolean isEmpty() {
        return size() == 0;
    }

    @Nullable
    default IAEStackType<?> getMixedStackType() {
        return null;
    }

    /**
     * Returns a typed view of this list that yields {@link IAEStack} instead of {@link IAEStackBase}.
     * All elements stored in an {@link IMixedStackList} are guaranteed to be {@link IAEStack} instances,
     * so this is a safe unchecked cast wrapper.
     */
    @SuppressWarnings("unchecked")
    default Iterable<IAEStack<?>> typedView() {
        return () -> {
            Iterator<IAEStackBase> base = IMixedStackList.this.iterator();
            return (Iterator<IAEStack<?>>) (Iterator<?>) base;
        };
    }
}

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

import appeng.api.stacks.AEKey;

/**
 * AEKey-based partition list used by the storage layer to filter which resources may enter or
 * leave a given inventory.
 * <p>
 * This is the native counterpart to the legacy {@link IPartitionList} (which is parameterized on
 * {@code IAEStack}) and is the preferred interface for new code. During the migration from the
 * pre-AEKey stack model, both may coexist; new partition lists should be created against this
 * interface so that the {@code MEInventoryHandler} does not need to allocate transient
 * {@code IAEStack} instances for every membership check.
 */
public interface AEKeyPartitionList {

    /**
     * @return true if the given key is a member of this partition list.
     */
    boolean isListed(AEKey input);

    /**
     * @return true if this list contains no keys.
     */
    boolean isEmpty();

    /**
     * @return an iterable view of the keys contained in this list.
     */
    Iterable<AEKey> getItems();
}

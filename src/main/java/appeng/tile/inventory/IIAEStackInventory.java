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

package appeng.tile.inventory;

import appeng.api.storage.StorageName;

/**
 * Objects implementing this interface hold one or more {@link IAEStackInventory}
 * and are responsible for saving data when inventory contents change.
 */
public interface IIAEStackInventory {

    /**
     * Called by the inventory to request a save when IAEStackInventory contents change.
     */
    void saveAEStackInv();

    /**
     * Get the corresponding {@link IAEStackInventory} by {@link StorageName}.
     *
     * @param name the inventory name
     * @return the corresponding inventory, or null if no matching name is found
     */
    IAEStackInventory getAEInventoryByName(StorageName name);
}

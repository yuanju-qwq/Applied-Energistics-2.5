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

package appeng.container.interfaces;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;

/**
 * Implemented by server-side Container. When the client sends a virtual slot update request
 * via {@link appeng.core.sync.packets.PacketVirtualSlot}, this interface is called to apply
 * the changes to the server-side IAEStackInventory.
 */
public interface IVirtualSlotSource {

    /**
     * Update the virtual stack at the specified inventory name and slot index.
     *
     * @param invName inventory name
     * @param slotId  slot index
     * @param aes     new stack content (null means clear)
     */
    void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes);
}

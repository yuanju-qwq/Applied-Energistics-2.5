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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Implemented by client-side Container or GUI to receive virtual slot data synchronized
 * from the server via {@link appeng.core.sync.packets.PacketVirtualSlot}.
 */
public interface IVirtualSlotHolder {

    /**
     * Receive batch virtual slot stack data.
     *
     * @param invName    inventory name
     * @param slotStacks mapping from slot index to IAEStack (null value means the slot is cleared)
     */
    void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks);
}

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

package appeng.container.interfaces;

import appeng.container.implementations.CraftingCPUStatus;

/**
 * Crafting status GUI callback interface.
 * <p>
 * Allows {@link appeng.core.sync.packets.PacketCraftingCPUsUpdate} to push
 * CPU status updates to the active GUI without depending on a concrete GUI class.
 */
public interface ICraftingStatusGuiCallback {

    /**
     * Called when the server sends back the list of available crafting CPUs.
     *
     * @param cpus the array of CPU status objects
     */
    void postCPUUpdate(CraftingCPUStatus[] cpus);
}

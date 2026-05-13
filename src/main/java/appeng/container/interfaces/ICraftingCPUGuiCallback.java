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

import java.util.List;

import appeng.api.storage.data.IAEStack;

/**
 * Callback interface for Crafting CPU status GUI.
 * <p>
 * Allows {@link appeng.container.implementations.ContainerCraftingCPU} to communicate with different GUI implementations
 * without depending on a specific GUI class. MUI panels receive callbacks by implementing this interface.
 */
public interface ICraftingCPUGuiCallback {

    /**
     * Receive mixed item/fluid status updates from the Crafting CPU.
     *
     * @param list the updated stack list
     * @param ref  update type: 0=stored, 1=crafting, 2=pending
     */
    void postGenericUpdate(List<IAEStack<?>> list, byte ref);

    /**
     * Clear all displayed items.
     */
    void clearItems();
}

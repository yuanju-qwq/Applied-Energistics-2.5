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

import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.mui.legacy.LegacyStackBridge;

/**
 * Callback interface for Crafting confirm GUI.
 * <p>
 * Allows {@link appeng.container.implementations.ContainerCraftConfirm} to communicate with different GUI implementations
 * without depending on a specific GUI class. MUI panels receive callbacks by implementing this interface.
 */
public interface ICraftConfirmGuiCallback {

    /**
     * Receive mixed item/fluid status updates for the crafting confirm plan as
     * AEKey-based {@link GenericStack}. This is the preferred AEKey-only entry point
     * that GUIs must implement.
     * <p>
     * Note: GenericStack does not carry {@code countRequestableCrafts}; the legacy
     * IAEStack-only "pattern execution count" display is unavailable on this path.
     *
     * @param list the updated generic stack list
     * @param ref  update type: 0=stored, 1=to craft, 2=missing
     */
    void postGenericStackUpdate(List<GenericStack> list, byte ref);

    /**
     * @deprecated Use {@link #postGenericStackUpdate(List, byte)} instead. Retained for
     *             the legacy IAEStack dispatch path in {@link PacketMEInventoryUpdate}.
     *             Default implementation bridges to the GenericStack entry point.
     *
     * @param list the updated stack list
     * @param ref  update type: 0=stored, 1=to craft, 2=missing
     */
    @Deprecated
    default void postGenericUpdate(List<IAEStack<?>> list, byte ref) {
        // Bridge legacy IAEStack path to the GenericStack entry point
        List<GenericStack> generic = new java.util.ArrayList<>(list.size());
        for (IAEStack<?> stack : list) {
            GenericStack gs = LegacyStackBridge.toGenericStack(stack);
            if (gs != null) {
                generic.add(gs);
            }
        }
        postGenericStackUpdate(generic, ref);
    }
}

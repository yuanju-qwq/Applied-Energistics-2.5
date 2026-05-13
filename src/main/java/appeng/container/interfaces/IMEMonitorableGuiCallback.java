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
import appeng.client.me.ItemRepo;
import appeng.util.IConfigManagerHost;

/**
 * Callback interface for ME terminal GUI.
 * <p>
 * Allows {@link appeng.container.implementations.ContainerMEMonitorable} to communicate with different GUI implementations.
 * MUI panels receive callbacks by implementing this interface.
 */
public interface IMEMonitorableGuiCallback extends IConfigManagerHost {

    /**
     * Receive ME network resource updates using AEKey-based RepoEntry list.
     * <p>
     * This is the preferred update path. Implementations should override this method
     * and feed entries directly into {@link ItemRepo#postUpdate(ItemRepo.RepoEntry)}.
     *
     * @param entries the updated entries
     */
    default void postRepoEntryUpdate(List<ItemRepo.RepoEntry> entries) {
        // Default: fall back to legacy IAEStack path
        java.util.ArrayList<IAEStack<?>> legacyList = new java.util.ArrayList<>(entries.size());
        for (ItemRepo.RepoEntry entry : entries) {
            IAEStack<?> stack = entry.toIAEStack();
            if (stack != null) {
                legacyList.add(stack);
            }
        }
        postUpdate(legacyList);
    }

    /**
     * @deprecated Use {@link #postRepoEntryUpdate(List)} instead.
     * Receive item/fluid list updates from the ME network.
     *
     * @param list the updated stack list
     */
    @Deprecated
    void postUpdate(List<IAEStack<?>> list);
}

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
    void postRepoEntryUpdate(List<ItemRepo.RepoEntry> entries);

    /**
     * @deprecated Use {@link #postRepoEntryUpdate(List)} instead.
     * Receive item/fluid list updates from the ME network.
     * <p>
     * Default implementation converts to RepoEntry and delegates.
     *
     * @param list the updated stack list
     */
    @Deprecated
    default void postUpdate(List<IAEStack<?>> list) {
        java.util.ArrayList<ItemRepo.RepoEntry> entries = new java.util.ArrayList<>(list.size());
        for (IAEStack<?> stack : list) {
            var key = stack.toAEKey();
            if (key != null) {
                entries.add(new ItemRepo.RepoEntry(key, stack.getStackSize(), stack.isCraftable()));
            }
        }
        postRepoEntryUpdate(entries);
    }
}

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

package appeng.helpers.iface;

import javax.annotation.Nonnull;

import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Upgrades;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.inv.AdaptorItemHandler;

/**
 * Context object passed to {@link IInterfaceSlotHandler} methods.
 * Provides access to the shared state of the {@link appeng.helpers.InterfaceLogic}.
 */
public interface InterfaceSlotContext {

    /**
     * @return the ME network proxy
     */
    @Nonnull
    AENetworkProxy getProxy();

    /**
     * @return the action source for ME network operations (identifies this interface)
     */
    @Nonnull
    IActionSource getRequestSource();

    /**
     * @return the normal action source
     */
    @Nonnull
    IActionSource getActionSource();

    /**
     * @return number of installed upgrades of the given type
     */
    int getInstalledUpgrades(Upgrades u);

    /**
     * @return the interface's priority for storage operations
     */
    int getPriority();

    /**
     * @return the world where this interface exists
     */
    @Nonnull
    World getWorld();

    // ========== Item Storage Access ==========

    /**
     * @return the item storage handler for all slots
     */
    @Nonnull
    IItemHandler getItemStorage();

    /**
     * Get an item inventory adaptor for a specific slot.
     *
     * @param slot the slot index
     * @return the adaptor
     */
    @Nonnull
    AdaptorItemHandler getItemAdaptor(int slot);

    // ========== Fluid Storage Access ==========

    /**
     * @return the fluid tank storage for all slots
     */
    @Nonnull
    IAEFluidTank getFluidTanks();

    // ========== Crafting ==========

    /**
     * Handle a crafting request for the given slot.
     * Supports any {@link IAEStack} type; the implementation determines whether
     * physical storage has space and delegates to the crafting tracker.
     *
     * @param slot  the slot index
     * @param stack the stack to craft (any type)
     * @return true if crafting was initiated
     */
    boolean handleCrafting(int slot, @Nonnull IAEStack<?> stack);

    /**
     * @return true if the crafting tracker is busy for the given slot
     */
    boolean isCraftingBusy(int slot);

    // ========== Storage Helper Methods ==========

    /**
     * Get the ME network inventory for the given stack type.
     */
    @Nonnull
    <T extends IAEStack<T>> IMEMonitor<T> getNetworkInventory(IAEStackType<T> type);
}

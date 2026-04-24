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
import javax.annotation.Nullable;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Handles type-specific storage operations for a single slot in an ME Interface.
 * <p>
 * Each {@link IAEStackType} registers an implementation of this interface that knows
 * how to:
 * <ul>
 *   <li>Calculate what work needs to be done for a slot (plan computation)</li>
 *   <li>Execute the work plan (extract from / inject into ME network)</li>
 *   <li>Interact with the slot's internal storage</li>
 *   <li>Provide an ME-compatible inventory wrapper for the network</li>
 * </ul>
 * <p>
 * This abstraction replaces all {@code instanceof IAEItemStack / IAEFluidStack}
 * dispatching in {@link appeng.helpers.InterfaceLogic}.
 *
 * @param <T> the concrete stack type
 */
public interface IInterfaceSlotHandler<T extends IAEStack<T>> {

    /**
     * @return the stack type this handler manages
     */
    @Nonnull
    IAEStackType<T> getStackType();

    // ========== Slot Storage Init ==========

    /**
     * Get the capacity for this slot type based on installed upgrades.
     *
     * @param capacityUpgrades number of installed Capacity upgrades
     * @return capacity in native units (e.g., item count for items, mB for fluids)
     */
    long getSlotCapacity(int capacityUpgrades);

    // ========== Plan Computation ==========

    /**
     * Compute the work plan for a slot. The plan describes what needs to happen
     * to make the slot's current storage match the desired config.
     * <p>
     * Positive stack size = need to fill (extract from network).
     * Negative stack size = need to empty (return to network).
     * Null = nothing to do.
     *
     * @param slot             the slot index
     * @param desired          the desired config stack (guaranteed to be of this handler's type)
     * @param context          slot context for accessing storage state
     * @return the work plan, or null if the slot is already satisfied
     */
    @Nullable
    IAEStack<?> computePlan(int slot, @Nonnull T desired, @Nonnull InterfaceSlotContext context);

    // ========== Plan Execution ==========

    /**
     * Execute the work plan for a slot: transfer items/fluids between the slot's
     * internal storage and the ME network.
     *
     * @param slot    the slot index
     * @param plan    the work plan (previously computed by {@link #computePlan})
     * @param context slot context for accessing storage and network
     * @return true if any transfer occurred
     */
    boolean executePlan(int slot, @Nonnull T plan, @Nonnull InterfaceSlotContext context);

    // ========== Network Integration ==========

    /**
     * Create an {@link IMEMonitor} wrapper that exposes this slot handler's storage
     * to the ME network. This is used when the interface has config of this type,
     * providing a filtered view that respects priority.
     *
     * @param context slot context
     * @return the ME monitor wrapper, or null if not applicable
     */
    @Nullable
    IMEMonitor<T> createConfiguredMonitor(@Nonnull InterfaceSlotContext context);

    /**
     * Get the pass-through monitor for this type (used when no config is set,
     * allowing direct access to the ME network's storage of this type).
     *
     * @param context slot context
     * @return the pass-through monitor
     */
    @Nonnull
    IMEMonitor<T> getPassThroughMonitor(@Nonnull InterfaceSlotContext context);

    // ========== Grid Changed ==========

    /**
     * Called when the grid changes. Update the internal pass-through monitor
     * to point to the new network storage.
     *
     * @param networkInventory the new network inventory for this type (may be null if not connected)
     * @param context          slot context
     */
    void onGridChanged(@Nullable IMEInventory<T> networkInventory, @Nonnull InterfaceSlotContext context);
}

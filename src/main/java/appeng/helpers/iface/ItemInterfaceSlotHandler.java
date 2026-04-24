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

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.primitives.Ints;

import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.me.GridAccessException;
import appeng.me.storage.MEMonitorIInventoryHandler;
import appeng.me.storage.NullInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.StorageHelper;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.item.AEItemStackType;

/**
 * Item-specific slot handler for the ME Interface.
 * <p>
 * Handles item storage, plan computation, and plan execution for slots
 * configured with item stacks.
 */
public final class ItemInterfaceSlotHandler implements IInterfaceSlotHandler<IAEItemStack> {

    public static final ItemInterfaceSlotHandler INSTANCE = new ItemInterfaceSlotHandler();

    private ItemInterfaceSlotHandler() {}

    @Nonnull
    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return AEItemStackType.INSTANCE;
    }

    @Override
    public long getSlotCapacity(int capacityUpgrades) {
        return 64;
    }

    // ========== Plan Computation ==========

    @Nullable
    @Override
    public IAEStack<?> computePlan(int slot, @Nonnull IAEItemStack desired,
            @Nonnull InterfaceSlotContext context) {
        if (desired.getStackSize() <= 0) {
            return null;
        }

        final ItemStack stored = context.getItemStorage().getStackInSlot(slot);

        if (stored.isEmpty()) {
            return desired.copy();
        } else if (desired.isSameType(stored)) {
            if (desired.getStackSize() == stored.getCount()) {
                return null;
            } else {
                IAEItemStack work = desired.copy();
                work.setStackSize(desired.getStackSize() - stored.getCount());
                return work;
            }
        } else {
            // Type mismatch: return old items first (negative = push back to network)
            final IAEItemStack work = AEItemStackType.INSTANCE.createStack(stored);
            work.setStackSize(-work.getStackSize());
            return work;
        }
    }

    // ========== Plan Execution ==========

    @Override
    public boolean executePlan(int slot, @Nonnull IAEItemStack plan,
            @Nonnull InterfaceSlotContext context) {
        final AdaptorItemHandler adaptor = context.getItemAdaptor(slot);

        boolean changed = false;
        try {
            final IMEMonitor<IAEItemStack> dest = context.getNetworkInventory(AEItemStackType.INSTANCE);
            final appeng.api.networking.energy.IEnergySource src = context.getProxy().getEnergy();

            // --- Negative: push items back to network ---
            if (plan.getStackSize() < 0) {
                IAEItemStack toStore = plan.copy();
                toStore.setStackSize(-toStore.getStackSize());
                long diff = toStore.getStackSize();

                final ItemStack canExtract = adaptor.simulateRemove((int) diff, toStore.getDefinition(), null);
                if (canExtract.isEmpty()) {
                    changed = true;
                    throw new GridAccessException();
                }

                toStore = StorageHelper.poweredInsert(src, dest, toStore, context.getRequestSource());
                if (toStore != null) {
                    diff -= toStore.getStackSize();
                }

                if (diff != 0) {
                    changed = true;
                    final ItemStack removed = adaptor.removeItems((int) diff, ItemStack.EMPTY, null);
                    if (removed.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    }
                }
            }

            // --- Crafting busy: check if crafting result arrived ---
            if (context.isCraftingBusy(slot)) {
                changed = context.handleCrafting(slot, plan) || changed;
            }
            // --- Positive: pull items from network ---
            else if (plan.getStackSize() > 0) {
                ItemStack inputStack = plan.getCachedItemStack(plan.getStackSize());
                ItemStack remaining = adaptor.simulateAdd(inputStack);

                if (!remaining.isEmpty()) {
                    plan.setCachedItemStack(remaining);
                    changed = true;
                    throw new GridAccessException();
                }

                IAEItemStack storedInNetwork = context.getNetworkInventory(AEItemStackType.INSTANCE)
                        .getStorageList().findPrecise(plan);
                if (storedInNetwork != null) {
                    final IAEItemStack acquired = StorageHelper.poweredExtraction(
                            src, dest, plan, context.getRequestSource());
                    if (acquired != null) {
                        changed = true;
                        inputStack.setCount(Ints.saturatedCast(acquired.getStackSize()));
                        final ItemStack issue = adaptor.addItems(inputStack);
                        if (!issue.isEmpty()) {
                            throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
                        }
                    } else if (storedInNetwork.isCraftable()) {
                        plan.setCachedItemStack(inputStack);
                        changed = context.handleCrafting(slot, plan) || changed;
                    }
                    if (acquired == null) {
                        plan.setCachedItemStack(inputStack);
                    }
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }

        return changed;
    }

    // ========== Network Integration ==========

    @Nullable
    @Override
    public IMEMonitor<IAEItemStack> createConfiguredMonitor(@Nonnull InterfaceSlotContext context) {
        return new ItemInterfaceInventory(context);
    }

    @Nonnull
    @Override
    public IMEMonitor<IAEItemStack> getPassThroughMonitor(@Nonnull InterfaceSlotContext context) {
        // This is handled externally by InterfaceLogic's MEMonitorPassThrough
        throw new UnsupportedOperationException("Use InterfaceLogic.items passthrough");
    }

    @Override
    public void onGridChanged(@Nullable IMEInventory<IAEItemStack> networkInventory,
            @Nonnull InterfaceSlotContext context) {
        // Handled by InterfaceLogic directly via MEMonitorPassThrough
    }

    // ========== Inner class: Config-mode ME inventory wrapper ==========

    private static class ItemInterfaceInventory extends MEMonitorIInventoryHandler {
        private final InterfaceSlotContext context;

        ItemInterfaceInventory(InterfaceSlotContext context) {
            super(context.getItemStorage());
            this.context = context;
        }

        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final IActionSource src) {
            final Optional<Comparable<Integer>> ctx = src.context(Comparable.class);
            if (ctx.isPresent()) {
                return input;
            }
            return super.injectItems(input, type, src);
        }

        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable type, final IActionSource src) {
            final Optional<Comparable<Integer>> ctx = src.context(Comparable.class);
            final boolean hasLowerOrEqualPriority = ctx
                    .map(c -> c.compareTo(0) <= 0).orElse(false);
            if (hasLowerOrEqualPriority) {
                return null;
            }
            return super.extractItems(request, type, src);
        }
    }
}

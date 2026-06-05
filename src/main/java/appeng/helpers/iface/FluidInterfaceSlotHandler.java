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

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStackType;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.GridAccessException;
import appeng.me.storage.MEMonitorIFluidHandler;
import appeng.me.storage.NullInventory;
import appeng.util.StorageHelper;

/**
 * Fluid-specific slot handler for the ME Interface.
 * <p>
 * Handles fluid tank storage, plan computation, and plan execution for slots
 * configured with fluid stacks.
 */
public final class FluidInterfaceSlotHandler implements IInterfaceSlotHandler {

    public static final FluidInterfaceSlotHandler INSTANCE = new FluidInterfaceSlotHandler();

    private FluidInterfaceSlotHandler() {}

    @Nonnull
    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    @Override
    public long getSlotCapacity(int capacityUpgrades) {
        return (long) (Math.pow(4, capacityUpgrades + 1) * Fluid.BUCKET_VOLUME);
    }

    // ========== Plan Computation ==========

    @Nullable
    @Override
    public GenericStack computePlan(int slot, @Nonnull GenericStack desired,
            @Nonnull InterfaceSlotContext context) {
        final IAEFluidTank tanks = context.getFluidTanks();
        final IAEFluidStack stored = tanks.getFluidInSlot(slot);
        final long tankSize = getSlotCapacity(context.getInstalledUpgrades(Upgrades.CAPACITY));

        if (stored == null || stored.getStackSize() == 0) {
            return new GenericStack(desired.what(), tankSize);
        } else if (desired.what().equals(stored.toAEKey())) {
            if (stored.getStackSize() == tankSize) {
                return null;
            } else {
                return new GenericStack(desired.what(), tankSize - stored.getStackSize());
            }
        } else {
            // Type mismatch: return old fluid first (negative = push back to network)
            return new GenericStack(stored.toAEKey(), -stored.getStackSize());
        }
    }

    // ========== Plan Execution ==========

    @Override
    public boolean executePlan(int slot, @Nonnull GenericStack plan,
            @Nonnull InterfaceSlotContext context) {
        final IAEFluidTank tanks = context.getFluidTanks();
        final AEFluidKey fluidKey = (AEFluidKey) plan.what();

        boolean changed = false;
        try {
            final IMEMonitor dest = context.getNetworkInventory(AEKeyType.fluids());
            final appeng.api.networking.energy.IEnergySource src = context.getProxy().getEnergy();

            // --- Positive: pull fluid from network into tank ---
            if (plan.amount() > 0) {
                if (tanks.fill(slot, fluidKey.toStack((int) plan.amount()), false) != plan.amount()) {
                    changed = true;
                } else if (context.getNetworkInventory(AEKeyType.fluids())
                        .getKeyCounter().get(plan.what()) > 0) {
                    final GenericStack acquired = StorageHelper.poweredExtraction(
                            src, dest, plan, context.getRequestSource());
                    if (acquired != null) {
                        changed = true;
                        final int filled = tanks.fill(slot, ((AEFluidKey) acquired.what()).toStack((int) acquired.amount()), true);
                        if (filled != acquired.amount()) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( fill )");
                        }
                    }
                }
            }
            // --- Negative: push fluid from tank back to network ---
            else if (plan.amount() < 0) {
                GenericStack toStore = new GenericStack(plan.what(), -plan.amount());

                final FluidStack canExtract = tanks.drain(slot, fluidKey.toStack((int) toStore.amount()), false);
                if (canExtract == null || canExtract.amount != toStore.amount()) {
                    changed = true;
                } else {
                    GenericStack notStored = StorageHelper.poweredInsert(
                            src, dest, toStore, context.getRequestSource());
                    long remaining = notStored == null ? 0 : notStored.amount();

                    if (toStore.amount() - remaining > 0) {
                        changed = true;
                        final FluidStack removed = tanks.drain(slot, fluidKey.toStack((int) (toStore.amount() - remaining)), true);
                        if (removed == null || (toStore.amount() - remaining) != removed.amount) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( drain )");
                        }
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
    public IMEMonitor createConfiguredMonitor(@Nonnull InterfaceSlotContext context) {
        return new FluidInterfaceInventory(context);
    }

    @Nonnull
    @Override
    public IMEMonitor getPassThroughMonitor(@Nonnull InterfaceSlotContext context) {
        // This is handled externally by InterfaceLogic's MEMonitorPassThrough
        throw new UnsupportedOperationException("Use InterfaceLogic.fluids passthrough");
    }

    @Override
    public void onGridChanged(@Nullable IMEInventory networkInventory,
            @Nonnull InterfaceSlotContext context) {
        // Handled by InterfaceLogic directly via MEMonitorPassThrough
    }

    // ========== Inner class: Config-mode ME inventory wrapper ==========

    private static class FluidInterfaceInventory extends MEMonitorIFluidHandler {
        private final InterfaceSlotContext context;

        FluidInterfaceInventory(InterfaceSlotContext context) {
            super(context.getFluidTanks());
            this.context = context;
        }

        @Override
        public GenericStack injectItems(final GenericStack input, final Actionable type, final IActionSource src) {
            if (input == null) return null;
            @SuppressWarnings({"unchecked", "rawtypes"})
            final Optional<Comparable> ctx = src.context(Comparable.class);
            if (ctx.isPresent()) {
                return input;
            }
            return super.injectItems(input, type, src);
        }

        @Override
        public GenericStack extractItems(final GenericStack request, final Actionable type,
                final IActionSource src) {
            if (request == null) return null;
            @SuppressWarnings({"unchecked", "rawtypes"})
            final Optional<Comparable> ctx = src.context(Comparable.class);
            final boolean hasLowerOrEqualPriority = ctx
                    .map(c -> c.compareTo(context.getPriority()) <= 0).orElse(false);
            if (hasLowerOrEqualPriority) {
                return null;
            }
            return super.extractItems(request, type, src);
        }
    }
}

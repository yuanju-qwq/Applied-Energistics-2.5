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

import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
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
public final class FluidInterfaceSlotHandler implements IInterfaceSlotHandler<IAEFluidStack> {

    public static final FluidInterfaceSlotHandler INSTANCE = new FluidInterfaceSlotHandler();

    private FluidInterfaceSlotHandler() {}

    @Nonnull
    @Override
    public IAEStackType<IAEFluidStack> getStackType() {
        return AEFluidStackType.INSTANCE;
    }

    @Override
    public long getSlotCapacity(int capacityUpgrades) {
        return (long) (Math.pow(4, capacityUpgrades + 1) * Fluid.BUCKET_VOLUME);
    }

    // ========== Plan Computation ==========

    @Nullable
    @Override
    public IAEStack<?> computePlan(int slot, @Nonnull IAEFluidStack desired,
            @Nonnull InterfaceSlotContext context) {
        final IAEFluidTank tanks = context.getFluidTanks();
        final IAEFluidStack stored = tanks.getFluidInSlot(slot);
        final long tankSize = getSlotCapacity(context.getInstalledUpgrades(Upgrades.CAPACITY));

        if (stored == null || stored.getStackSize() == 0) {
            IAEFluidStack work = desired.copy();
            work.setStackSize(tankSize);
            return work;
        } else if (desired.equals(stored)) {
            if (stored.getStackSize() == tankSize) {
                return null;
            } else {
                IAEFluidStack work = desired.copy();
                work.setStackSize(tankSize - stored.getStackSize());
                return work;
            }
        } else {
            // Type mismatch: return old fluid first (negative = push back to network)
            final IAEFluidStack work = stored.copy();
            work.setStackSize(-work.getStackSize());
            return work;
        }
    }

    // ========== Plan Execution ==========

    @Override
    public boolean executePlan(int slot, @Nonnull IAEFluidStack plan,
            @Nonnull InterfaceSlotContext context) {
        final IAEFluidTank tanks = context.getFluidTanks();

        boolean changed = false;
        try {
            final IMEInventory<IAEFluidStack> dest = context.getNetworkInventory(AEFluidStackType.INSTANCE);
            final appeng.api.networking.energy.IEnergySource src = context.getProxy().getEnergy();

            // --- Positive: pull fluid from network into tank ---
            if (plan.getStackSize() > 0) {
                if (tanks.fill(slot, plan.getFluidStack(), false) != plan.getStackSize()) {
                    changed = true;
                } else if (context.getNetworkInventory(AEFluidStackType.INSTANCE)
                        .getStorageList().findPrecise(plan) != null) {
                    final IAEFluidStack acquired = StorageHelper.poweredExtraction(
                            src, dest, plan, context.getRequestSource());
                    if (acquired != null) {
                        changed = true;
                        final int filled = tanks.fill(slot, acquired.getFluidStack(), true);
                        if (filled != acquired.getStackSize()) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( fill )");
                        }
                    }
                }
            }
            // --- Negative: push fluid from tank back to network ---
            else if (plan.getStackSize() < 0) {
                IAEFluidStack toStore = plan.copy();
                toStore.setStackSize(-toStore.getStackSize());

                final FluidStack canExtract = tanks.drain(slot, toStore.getFluidStack(), false);
                if (canExtract == null || canExtract.amount != toStore.getStackSize()) {
                    changed = true;
                } else {
                    IAEFluidStack notStored = StorageHelper.poweredInsert(
                            src, dest, toStore, context.getRequestSource());
                    toStore.setStackSize(
                            toStore.getStackSize() - (notStored == null ? 0 : notStored.getStackSize()));

                    if (toStore.getStackSize() > 0) {
                        changed = true;
                        final FluidStack removed = tanks.drain(slot, toStore.getFluidStack(), true);
                        if (removed == null || toStore.getStackSize() != removed.amount) {
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
    public IMEMonitor<IAEFluidStack> createConfiguredMonitor(@Nonnull InterfaceSlotContext context) {
        return new FluidInterfaceInventory(context);
    }

    @Nonnull
    @Override
    public IMEMonitor<IAEFluidStack> getPassThroughMonitor(@Nonnull InterfaceSlotContext context) {
        // This is handled externally by InterfaceLogic's MEMonitorPassThrough
        throw new UnsupportedOperationException("Use InterfaceLogic.fluids passthrough");
    }

    @Override
    public void onGridChanged(@Nullable IMEInventory<IAEFluidStack> networkInventory,
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
        public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable type, final IActionSource src) {
            final Optional<Comparable<Integer>> ctx = src.context(Comparable.class);
            if (ctx.isPresent()) {
                return input;
            }
            return super.injectItems(input, type, src);
        }

        @Override
        public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable type,
                final IActionSource src) {
            final Optional<Comparable<Integer>> ctx = src.context(Comparable.class);
            final boolean hasLowerOrEqualPriority = ctx
                    .map(c -> c.compareTo(context.getPriority()) <= 0).orElse(false);
            if (hasLowerOrEqualPriority) {
                return null;
            }
            return super.extractItems(request, type, src);
        }
    }
}

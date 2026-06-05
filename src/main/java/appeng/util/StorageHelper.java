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

package appeng.util;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.core.stats.Stats;

public final class StorageHelper {

    private StorageHelper() {}

    public static GenericStack poweredExtraction(IEnergySource energy, IMEInventory cell,
            GenericStack request, IActionSource src) {
        return poweredExtraction(energy, cell, request, src, Actionable.MODULATE);
    }

    public static GenericStack poweredExtraction(IEnergySource energy, IMEInventory cell,
            GenericStack request, IActionSource src, Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        GenericStack possible = cell.extractItems(request, Actionable.SIMULATE, src);

        long retrieved = possible != null ? possible.amount() : 0;

        double energyFactor = Math.max(1.0, cell.getKeyType().getAmountPerUnit());
        double availablePower = energy.extractAEPower(retrieved / energyFactor, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        long itemToExtract = Math.min((long) ((availablePower * energyFactor) + 0.9), retrieved);

        if (itemToExtract > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(retrieved / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                GenericStack toExtract = new GenericStack(request.what(), itemToExtract);
                GenericStack ret = cell.extractItems(toExtract, Actionable.MODULATE, src);

                if (ret != null) {
                    src.player().ifPresent(player -> Stats.ItemsExtracted.addToPlayer(player, (int) ret.amount()));
                }
                return ret;
            } else {
                return new GenericStack(request.what(), itemToExtract);
            }
        }

        return null;
    }

    public static GenericStack poweredInsert(IEnergySource energy, IMEInventory cell,
            GenericStack input, IActionSource src) {
        return poweredInsert(energy, cell, input, src, Actionable.MODULATE);
    }

    @Nullable
    public static GenericStack poweredInsert(IEnergySource energy, IMEInventory cell,
            GenericStack input, IActionSource src, Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        GenericStack possible = cell.injectItems(input, Actionable.SIMULATE, src);

        long stored = input.amount();
        if (possible != null) {
            stored -= possible.amount();
        }

        double energyFactor = Math.max(1.0, cell.getKeyType().getAmountPerUnit());
        double availablePower = energy.extractAEPower(stored / energyFactor, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        long itemToAdd = Math.min((long) ((availablePower * energyFactor) + 0.9), stored);

        if (itemToAdd > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(stored / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (itemToAdd < input.amount()) {
                    long original = input.amount();
                    GenericStack toInject = new GenericStack(input.what(), itemToAdd);
                    GenericStack injectResult = cell.injectItems(toInject, Actionable.MODULATE, src);
                    long actuallyInserted = itemToAdd;
                    if (injectResult != null) {
                        actuallyInserted -= injectResult.amount();
                    }
                    long leftoverAmount = original - actuallyInserted;
                    final long inserted = actuallyInserted;

                    src.player().ifPresent(player -> {
                        Stats.ItemsInserted.addToPlayer(player, (int) inserted);
                    });

                    return leftoverAmount > 0 ? new GenericStack(input.what(), leftoverAmount) : null;
                }

                GenericStack ret = cell.injectItems(input, Actionable.MODULATE, src);

                src.player().ifPresent(player -> {
                    long diff = ret == null ? input.amount() : input.amount() - ret.amount();
                    Stats.ItemsInserted.addToPlayer(player, (int) diff);
                });

                return ret;
            } else {
                long leftover = input.amount() - itemToAdd;
                return leftover > 0 ? new GenericStack(input.what(), leftover) : null;
            }
        }

        return input;
    }

    public static void postChanges(final IStorageGrid gs, final ItemStack removed, final ItemStack added,
            final IActionSource src) {
        for (final AEKeyType keyType : AEKeyType.getAllTypes()) {
            final KeyCounter myChanges = new KeyCounter();

            if (!removed.isEmpty()) {
                var myInv = AEApi.instance().registries().cell().getCellInventory(removed, null, keyType);
                if (myInv != null) {
                    KeyCounter removedItems = myInv.getAvailableKeyCounter();
                    for (var entry : removedItems) {
                        myChanges.add(entry.getKey(), -entry.getLongValue());
                    }
                }
            }
            if (!added.isEmpty()) {
                var myInv = AEApi.instance().registries().cell().getCellInventory(added, null, keyType);
                if (myInv != null) {
                    myChanges.addAll(myInv.getAvailableKeyCounter());
                }
            }
            gs.postAlterationOfStoredItems(keyType, myChanges, src);
        }
    }

    public static KeyCounter getAvailableItems(final IMEInventory inv) {
        return inv.getAvailableKeyCounter();
    }

    public static KeyCounter getStorageView(final IMEInventory inv) {
        if (inv instanceof IMEMonitor) {
            return ((IMEMonitor) inv).getKeyCounter();
        }
        return inv.getAvailableKeyCounter();
    }

    public static GenericStack injectTyped(final IMEInventory inv, final GenericStack input,
            final Actionable mode, final IActionSource src) {
        return inv.injectItems(input, mode, src);
    }

    public static GenericStack extractTyped(final IMEInventory inv, final GenericStack request,
            final Actionable mode, final IActionSource src) {
        return inv.extractItems(request, mode, src);
    }

    public static IAEStack<?> injectItems(final IMEInventory inv, final IAEStack<?> input,
            final Actionable mode, final IActionSource src) {
        AEKey key = input.toAEKey();
        if (key == null) {
            return null;
        }
        GenericStack gs = new GenericStack(key, input.getStackSize());
        GenericStack result = inv.injectItems(gs, mode, src);
        return result != null ? result.toIAEStack() : null;
    }

    public static IAEStack<?> extractItems(final IMEInventory inv, final IAEStack<?> request,
            final Actionable mode, final IActionSource src) {
        AEKey key = request.toAEKey();
        if (key == null) {
            return null;
        }
        GenericStack gs = new GenericStack(key, request.getStackSize());
        GenericStack result = inv.extractItems(gs, mode, src);
        return result != null ? result.toIAEStack() : null;
    }

    @Nullable
    public static IAEStack<?> poweredInsertWildcard(final IEnergySource energy, final IMEInventory inv,
            final IAEStack<?> input, final IActionSource src) {
        AEKey key = input.toAEKey();
        if (key == null) {
            return null;
        }
        GenericStack result = poweredInsert(energy, inv, new GenericStack(key, input.getStackSize()), src);
        return result != null ? result.toIAEStack() : null;
    }

    public static void postListChanges(final KeyCounter before, final KeyCounter after,
            final IMEMonitorHandlerReceiver monitorReceiver, final IActionSource source) {
        final List<GenericStack> changes = new ArrayList<>();

        KeyCounter diff = new KeyCounter();
        for (var entry : before) {
            diff.add(entry.getKey(), -entry.getLongValue());
        }
        diff.addAll(after);
        diff.removeZeros();

        for (var entry : diff) {
            changes.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }

        if (!changes.isEmpty()) {
            monitorReceiver.postChange(null, changes, source);
        }
    }

    public static KeyCounter getAvailableKeyCounter(IMEInventory inv) {
        return inv.getAvailableKeyCounter();
    }
}
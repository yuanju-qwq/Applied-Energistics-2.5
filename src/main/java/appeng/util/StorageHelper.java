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

import com.google.common.base.Preconditions;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.stats.Stats;

public final class StorageHelper {

    private StorageHelper() {}

    public static <T extends IAEStack<T>> T poweredExtraction(final IEnergySource energy, final IMEInventory<T> cell,
            final T request, final IActionSource src) {
        return poweredExtraction(energy, cell, request, src, Actionable.MODULATE);
    }

    public static <T extends IAEStack<T>> T poweredExtraction(final IEnergySource energy, final IMEInventory<T> cell,
            final T request, final IActionSource src, final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(request);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        final T possible = cell.extractItems(request.copy(), Actionable.SIMULATE, src);

        long retrieved = 0;
        if (possible != null) {
            retrieved = possible.getStackSize();
        }

        final double energyFactor = Math.max(1.0, cell.getStackType().transferFactor());
        final double availablePower = energy.extractAEPower(retrieved / energyFactor, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        final long itemToExtract = Math.min((long) ((availablePower * energyFactor) + 0.9), retrieved);

        if (itemToExtract > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(retrieved / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                possible.setStackSize(itemToExtract);
                final T ret = cell.extractItems(possible, Actionable.MODULATE, src);

                if (ret != null) {
                    src.player().ifPresent(player -> Stats.ItemsExtracted.addToPlayer(player, (int) ret.getStackSize()));
                }
                return ret;
            } else {
                return possible.setStackSize(itemToExtract);
            }
        }

        return null;
    }

    public static <T extends IAEStack<T>> T poweredInsert(final IEnergySource energy, final IMEInventory<T> cell,
            final T input, final IActionSource src) {
        return poweredInsert(energy, cell, input, src, Actionable.MODULATE);
    }

    public static <T extends IAEStack<T>> T poweredInsert(final IEnergySource energy, final IMEInventory<T> cell,
            final T input, final IActionSource src, final Actionable mode) {
        Preconditions.checkNotNull(energy);
        Preconditions.checkNotNull(cell);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(mode);

        final T possible = cell.injectItems(input, Actionable.SIMULATE, src);

        long stored = input.getStackSize();
        if (possible != null) {
            stored -= possible.getStackSize();
        }

        final double energyFactor = Math.max(1.0, cell.getStackType().transferFactor());
        final double availablePower = energy.extractAEPower(stored / energyFactor, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        final long itemToAdd = Math.min((long) ((availablePower * energyFactor) + 0.9), stored);

        if (itemToAdd > 0) {
            if (mode == Actionable.MODULATE) {
                energy.extractAEPower(stored / energyFactor, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (itemToAdd < input.getStackSize()) {
                    final long original = input.getStackSize();
                    final T leftover = input.copy();
                    final T split = input.copy();

                    leftover.decStackSize(itemToAdd);
                    split.setStackSize(itemToAdd);
                    leftover.add(cell.injectItems(split, Actionable.MODULATE, src));

                    src.player().ifPresent(player -> {
                        final long diff = original - leftover.getStackSize();
                        Stats.ItemsInserted.addToPlayer(player, (int) diff);
                    });

                    return leftover;
                }

                final T ret = cell.injectItems(input, Actionable.MODULATE, src);

                src.player().ifPresent(player -> {
                    final long diff = ret == null ? input.getStackSize() : input.getStackSize() - ret.getStackSize();
                    Stats.ItemsInserted.addToPlayer(player, (int) diff);
                });

                return ret;
            } else {
                final T ret = input.copy().setStackSize(input.getStackSize() - itemToAdd);
                return (ret != null && ret.getStackSize() > 0) ? ret : null;
            }
        }

        return input;
    }

    public static void postChanges(final IStorageGrid gs, final ItemStack removed, final ItemStack added,
            final IActionSource src) {
        for (final IStorageChannel<?> chan : AEApi.instance().storage().storageChannels()) {
            final IItemList<? extends IAEStack<?>> myChanges;

            if (!removed.isEmpty()) {
                final IMEInventory<?> myInv = AEApi.instance().registries().cell().getCellInventory(removed, null, chan);
                if (myInv != null) {
                    myChanges = getAvailableItems(myInv);
                    for (final IAEStack<?> is : myChanges) {
                        is.setStackSize(-is.getStackSize());
                    }
                } else {
                    myChanges = chan.createList();
                }
            } else {
                myChanges = chan.createList();
            }
            if (!added.isEmpty()) {
                final IMEInventory<?> myInv = AEApi.instance().registries().cell().getCellInventory(added, null, chan);
                if (myInv != null) {
                    getAvailableItemsInto(myInv, myChanges);
                }
            }
            gs.postAlterationOfStoredItems(chan.getStackType(), myChanges, src);
        }
    }

    private static void getAvailableItemsInto(final IMEInventory<?> inv, final IItemList<?> out) {
        inv.getAvailableItemsGeneric(out);
    }

    public static IItemList<? extends IAEStack<?>> getAvailableItems(final IMEInventory<?> inv) {
        return inv.getAvailableItems();
    }

    public static <T extends IAEStack<T>> IItemList<T> getStorageView(final IMEInventory<T> inv) {
        if (inv instanceof IMEMonitor<?>) {
            return getStorageViewFromMonitor(inv);
        }

        return inv.getAvailableItems(inv.getStackType().createList());
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IItemList<T> getStorageViewFromMonitor(final IMEInventory<T> inv) {
        return ((IMEMonitor<T>) inv).getStorageList();
    }

    public static <T extends IAEStack<T>> T injectTyped(final IMEInventory<T> inv, final T input,
            final Actionable mode, final IActionSource src) {
        return inv.injectItems(input, mode, src);
    }

    public static <T extends IAEStack<T>> T extractTyped(final IMEInventory<T> inv, final T request,
            final Actionable mode, final IActionSource src) {
        return inv.extractItems(request, mode, src);
    }

    public static IAEStack<?> injectItems(final IMEInventory<?> inv, final IAEStack<?> input,
            final Actionable mode, final IActionSource src) {
        return inv.injectItemsGeneric(input, mode, src);
    }

    public static IAEStack<?> extractItems(final IMEInventory<?> inv, final IAEStack<?> request,
            final Actionable mode, final IActionSource src) {
        return inv.extractItemsGeneric(request, mode, src);
    }

    @SuppressWarnings("unchecked")
    public static IAEStack<?> poweredInsertWildcard(final IEnergySource energy, final IMEInventory<?> inv,
            final IAEStack<?> input, final IActionSource src) {
        return (IAEStack<?>) poweredInsert(energy, (IMEInventory) inv, (IAEStack) input, src);
    }

    public static <T extends IAEStack<T>> void postListChanges(final IItemList<T> before, final IItemList<T> after,
            final IMEMonitorHandlerReceiver<T> monitorReceiver, final IActionSource source) {
        final List<T> changes = new ArrayList<>();

        for (final T is : before) {
            is.setStackSize(-is.getStackSize());
        }

        for (final T is : after) {
            before.add(is);
        }

        for (final T is : before) {
            if (is.getStackSize() != 0) {
                changes.add(is);
            }
        }

        if (!changes.isEmpty()) {
            monitorReceiver.postChange(null, changes, source);
        }
    }
}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.items.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.stacks.KeyCounterAdapter;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.AEBaseItem;
import appeng.items.contents.CellAEConfig;
import appeng.items.contents.CellUpgrades;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.item.ItemList;
import appeng.util.prioritylist.FuzzyAEKeyPriorityList;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.AEKeyPartitionList;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.prioritylist.MergedAEKeyPriorityList;
import appeng.util.prioritylist.MergedPriorityList;
import appeng.util.prioritylist.PreciseAEKeyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;

public class ItemViewCell extends AEBaseItem implements ICellWorkbenchItem {
    public ItemViewCell() {
        this.setMaxStackSize(1);
    }

    // ==================== AEKey-based filter (preferred) ====================

    /**
     * Creates an {@link AEKeyPartitionList} filter from the given view cells.
     * This is the AEKey-native replacement for {@link #createFilter(ItemStack[])}.
     *
     * @param list array of view cell ItemStacks
     * @return the partition list, or null if no active filters
     */
    @javax.annotation.Nullable
    public static AEKeyPartitionList createAEKeyFilter(final ItemStack[] list) {
        AEKeyPartitionList myPartitionList = null;

        final MergedAEKeyPriorityList myMergedList = new MergedAEKeyPriorityList();

        for (final ItemStack currentViewCell : list) {
            if (currentViewCell == null) {
                continue;
            }

            if ((currentViewCell.getItem() instanceof ItemViewCell)) {
                final ItemViewCell viewCellItem = (ItemViewCell) currentViewCell.getItem();

                // Skip disabled ViewCell
                if (!viewCellItem.getViewMode(currentViewCell)) {
                    continue;
                }
                final KeyCounter priorityList = new KeyCounter();

                final ICellWorkbenchItem vc = (ICellWorkbenchItem) currentViewCell.getItem();
                final IItemHandler upgrades = vc.getUpgradesInventory(currentViewCell);
                final IAEStackInventory config = vc.getConfigAEInventory(currentViewCell);
                final FuzzyMode fzMode = vc.getFuzzyMode(currentViewCell);

                boolean hasInverter = false;
                boolean hasFuzzy = false;

                for (int x = 0; x < upgrades.getSlots(); x++) {
                    final ItemStack is = upgrades.getStackInSlot(x);
                    if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                        final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                        if (u != null) {
                            switch (u) {
                                case FUZZY:
                                    hasFuzzy = true;
                                    break;
                                case INVERTER:
                                    hasInverter = true;
                                    break;
                                default:
                            }
                        }
                    }
                }

                for (int x = 0; x < config.getSizeInventory(); x++) {
                    final GenericStack gs = config.getGenericStack(x);
                    if (gs != null && gs.what() instanceof AEItemKey itemKey) {
                        priorityList.add(itemKey, 1);
                    }
                }

                if (!priorityList.isEmpty()) {
                    if (hasFuzzy) {
                        myMergedList.addNewList(new FuzzyAEKeyPriorityList(priorityList, fzMode), !hasInverter);
                    } else {
                        myMergedList.addNewList(new PreciseAEKeyPriorityList(priorityList.keySet()), !hasInverter);
                    }

                    myPartitionList = myMergedList;
                }
            }
        }

        return myPartitionList;
    }

    // ==================== Legacy filter (deprecated) ====================

    /**
     * @deprecated Use {@link #createAEKeyFilter(ItemStack[])} instead.
     */
    @Deprecated
    public static IPartitionList<IAEItemStack> createFilter(final ItemStack[] list) {
        IPartitionList<IAEItemStack> myPartitionList = null;

        final MergedPriorityList<IAEItemStack> myMergedList = new MergedPriorityList<>();

        for (final ItemStack currentViewCell : list) {
            if (currentViewCell == null) {
                continue;
            }

            if ((currentViewCell.getItem() instanceof ItemViewCell)) {
                final ItemViewCell viewCellItem = (ItemViewCell) currentViewCell.getItem();

                // Skip disabled ViewCell
                if (!viewCellItem.getViewMode(currentViewCell)) {
                    continue;
                }
                final KeyCounter priorityList = new KeyCounter();

                final ICellWorkbenchItem vc = (ICellWorkbenchItem) currentViewCell.getItem();
                final IItemHandler upgrades = vc.getUpgradesInventory(currentViewCell);
                final IAEStackInventory config = vc.getConfigAEInventory(currentViewCell);
                final FuzzyMode fzMode = vc.getFuzzyMode(currentViewCell);

                boolean hasInverter = false;
                boolean hasFuzzy = false;

                for (int x = 0; x < upgrades.getSlots(); x++) {
                    final ItemStack is = upgrades.getStackInSlot(x);
                    if (!is.isEmpty() && is.getItem() instanceof IUpgradeModule) {
                        final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                        if (u != null) {
                            switch (u) {
                                case FUZZY:
                                    hasFuzzy = true;
                                    break;
                                case INVERTER:
                                    hasInverter = true;
                                    break;
                                default:
                            }
                        }
                    }
                }

                for (int x = 0; x < config.getSizeInventory(); x++) {
                    final GenericStack gs = config.getGenericStack(x);
                    if (gs != null && gs.what() instanceof AEItemKey itemKey) {
                        priorityList.add(itemKey, 1);
                    }
                }

                if (!priorityList.isEmpty()) {
                    final IItemList<IAEItemStack> legacyList = new ItemList();
                    KeyCounterAdapter.toIItemList(priorityList, legacyList);
                    if (hasFuzzy) {
                        myMergedList.addNewList(new FuzzyPriorityList<>(legacyList, fzMode), !hasInverter);
                    } else {
                        myMergedList.addNewList(new PrecisePriorityList<>(legacyList), !hasInverter);
                    }

                    myPartitionList = myMergedList;
                }
            }
        }

        return myPartitionList;
    }

    @Override
    public boolean isEditable(final ItemStack is) {
        return true;
    }

    @Override
    public IItemHandler getUpgradesInventory(final ItemStack is) {
        return new CellUpgrades(is, 2);
    }

    @Override
    public IAEStackInventory getConfigAEInventory(final ItemStack is) {
        return new CellAEConfig(is);
    }

    @Override
    public FuzzyMode getFuzzyMode(final ItemStack is) {
        final String fz = appeng.util.ItemStackNbtHelper.openNbtData(is).getString("FuzzyMode");
        try {
            return FuzzyMode.valueOf(fz);
        } catch (final Throwable t) {
            return FuzzyMode.IGNORE_ALL;
        }
    }

    @Override
    public void setFuzzyMode(final ItemStack is, final FuzzyMode fzMode) {
        appeng.util.ItemStackNbtHelper.openNbtData(is).setString("FuzzyMode", fzMode.name());
    }

    /**
     * Toggle the ViewCell enabled/disabled state.
     * When disabled, this ViewCell's filter rules do not take effect.
     */
    public void toggleViewMode(final ItemStack is) {
        appeng.util.ItemStackNbtHelper.openNbtData(is).setBoolean("ViewMode", !getViewMode(is));
    }

    /**
     * @return whether the ViewCell is in enabled state (default true)
     */
    public boolean getViewMode(final ItemStack is) {
        if (appeng.util.ItemStackNbtHelper.openNbtData(is).hasKey("ViewMode")) {
            return appeng.util.ItemStackNbtHelper.openNbtData(is).getBoolean("ViewMode");
        }
        return true;
    }
}

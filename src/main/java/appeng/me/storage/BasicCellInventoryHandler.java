/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.me.storage;

import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;

/**
 * @author DrummerMC
 * @version rv6 - 2018-01-23
 * @since rv6 2018-01-23
 */
@SuppressWarnings("rawtypes")
public class BasicCellInventoryHandler extends MEInventoryHandler
        implements ICellInventoryHandler {

    @SuppressWarnings("unchecked")
    public BasicCellInventoryHandler(final IMEInventory c, final AEKeyType type) {
        super(c, type);

        final ICellInventory ci = this.getCellInv();
        if (ci != null) {
            final IItemList priorityList = ci.getStackType().createList();

            final IItemHandler upgrades = ci.getUpgradesInventory();
            final IAEStackInventory config = ci.getConfigAEInventory();
            final FuzzyMode fzMode = ci.getFuzzyMode();

            boolean hasInverter = false;
            boolean hasFuzzy = false;
            boolean hasSticky = false;

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
                            case STICKY:
                                hasSticky = true;
                                break;
                            default:
                        }
                    }
                }
            }

            // Use AEKey-based filter config if available (preferred path)
            if (ci instanceof AbstractCellInventory abstractCell) {
                final Set<AEKey> filterKeys = abstractCell.getFilterKeys();
                if (!filterKeys.isEmpty()) {
                    for (AEKey key : filterKeys) {
                        final IAEStack<?> stack = key.toIAEStack(1);
                        if (stack != null) {
                            priorityList.addGeneric(stack);
                        }
                    }
                }
            } else {
                // Legacy path: read from IAEStackInventory config
                for (int x = 0; x < config.getSizeInventory(); x++) {
                    final GenericStack gs = config.getGenericStack(x);
                    if (gs != null && gs.what() instanceof AEItemKey itemKey) {
                        final IAEStack configItem = ci.getStackType().createStack(itemKey.toStack());
                        if (configItem != null) {
                            priorityList.add(configItem);
                        }
                    }
                }
            }

            this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);

            if (hasSticky) {
                setSticky(true);
            }

            if (!priorityList.isEmpty()) {
                if (hasFuzzy) {
                    this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
                } else {
                    this.setPartitionList(new PrecisePriorityList<>(priorityList));
                }
            }
        }
    }

    @Override
    public ICellInventory getCellInv() {
        Object o = this.getInternal();

        if (o instanceof MEPassThrough) {
            o = ((MEPassThrough) o).getInternal();
        }

        return (ICellInventory) (o instanceof ICellInventory ? o : null);
    }

    @Override
    public boolean isPreformatted() {
        return !this.getPartitionList().isEmpty();
    }

    @Override
    public boolean isFuzzy() {
        return this.getPartitionList() instanceof FuzzyPriorityList;
    }

    @Override
    public IncludeExclude getIncludeExcludeMode() {
        return this.getWhitelist();
    }

    NBTTagCompound openNbtData() {
        return appeng.util.ItemStackNbtHelper.openNbtData(this.getCellInv().getItemStack());
    }

    @Override
    protected boolean canExtract(AEKey request) {
        return this.hasReadAccess();
    }

    @Override
    protected boolean shouldItemBeAvailable(AEKey request) {
        return this.hasReadAccess();
    }
}

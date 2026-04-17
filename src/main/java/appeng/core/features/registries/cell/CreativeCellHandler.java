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

package appeng.core.features.registries.cell;

import net.minecraft.item.ItemStack;

import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.items.storage.ItemCreativeStorageCell;
import appeng.me.storage.CreativeCellInventory;
import appeng.util.item.AEItemStackType;

public final class CreativeCellHandler implements ICellHandler {

    @Override
    public boolean isCell(final ItemStack is) {
        return !is.isEmpty() && is.getItem() instanceof ItemCreativeStorageCell;
    }

    @Override
    public <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(final ItemStack is, final ISaveProvider container,
            final IAEStackType<T> type) {
        if (type == AEItemStackType.INSTANCE && !is.isEmpty() && is
                .getItem() instanceof ItemCreativeStorageCell) {
            return (ICellInventoryHandler<T>) CreativeCellInventory.getCell(is);
        }
        return null;
    }

    @Override
    public <T extends IAEStack<T>> int getStatusForCell(final ItemStack is, final ICellInventoryHandler<T> handler) {
        return 2;
    }

    @Override
    public <T extends IAEStack<T>> double cellIdleDrain(final ItemStack is, final ICellInventoryHandler<T> handler) {
        return 0;
    }
}

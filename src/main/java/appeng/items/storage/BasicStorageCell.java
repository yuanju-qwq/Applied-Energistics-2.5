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

package appeng.items.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.items.materials.MaterialType;
import appeng.util.InventoryAdaptor;

/**
 * A unified, data-driven storage cell implementation.
 * All type-specific behavior (item, fluid, etc.) is determined by the {@link CellSpec}
 * passed at construction time, eliminating the need for per-type subclasses.
 *
 * @param <T> the type of {@link IAEStack} this cell stores
 */
public final class BasicStorageCell<T extends IAEStack<T>> extends AbstractStorageCell<T> {

    private final CellSpec<T> spec;

    public BasicStorageCell(final MaterialType whichCell, final int kilobytes, final CellSpec<T> spec) {
        super(whichCell, kilobytes);
        this.spec = spec;
    }

    @Override
    public int getBytesPerType(ItemStack cellItem) {
        return this.spec.getBytesPerType();
    }

    @Override
    public double getIdleDrain() {
        return this.spec.getIdleDrain();
    }

    @Override
    public IAEStackType<T> getStackType() {
        return this.spec.getStackType();
    }

    @Override
    public int getTotalTypes(final ItemStack cellItem) {
        return this.spec.getTotalTypes();
    }

    @Override
    public IItemHandler getConfigInventory(final ItemStack is) {
        return this.spec.getConfigInventoryFactory().apply(is);
    }

    @Override
    protected void dropEmptyStorageCellCase(final InventoryAdaptor ia, final EntityPlayer player) {
        AEApi.instance().definitions().materials().emptyStorageCell().maybeStack(1).ifPresent(is -> {
            final ItemStack extraA = ia.addItems(is);
            if (!extraA.isEmpty()) {
                player.dropItem(extraA, false);
            }
        });
    }
}

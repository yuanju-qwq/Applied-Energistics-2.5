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

import java.util.function.Function;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Data-driven specification for storage cells.
 * Instead of creating subclasses for each storage type (item, fluid, etc.),
 * this class holds all the type-specific configuration for a storage cell.
 *
 * To add a new storage type, simply create a new {@code CellSpec} instance
 * with the appropriate parameters.
 */
public class CellSpec<T extends IAEStack<T>> {

    private final AEKeyType keyType;
    private final int bytesPerType;
    private final double idleDrain;
    private final int totalTypes;
    private final Function<ItemStack, IAEStackInventory> configAEInventoryFactory;

    public CellSpec(
            @Nonnull final AEKeyType keyType,
            final int bytesPerType,
            final double idleDrain,
            final int totalTypes,
            final Function<ItemStack, IAEStackInventory> configAEInventoryFactory) {
        this.keyType = keyType;
        this.bytesPerType = bytesPerType;
        this.idleDrain = idleDrain;
        this.totalTypes = totalTypes;
        this.configAEInventoryFactory = configAEInventoryFactory;
    }

    /**
     * @deprecated Use {@link #getKeyType()} instead. Kept for backward compatibility.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public IAEStackType<T> getStackType() {
        return (IAEStackType<T>) AEStackTypeRegistry.getType(keyType.getId());
    }

    @Nonnull
    public AEKeyType getKeyType() {
        return this.keyType;
    }

    public int getBytesPerType() {
        return this.bytesPerType;
    }

    public double getIdleDrain() {
        return this.idleDrain;
    }

    public int getTotalTypes() {
        return this.totalTypes;
    }

    public Function<ItemStack, IAEStackInventory> getConfigAEInventoryFactory() {
        return this.configAEInventoryFactory;
    }
}

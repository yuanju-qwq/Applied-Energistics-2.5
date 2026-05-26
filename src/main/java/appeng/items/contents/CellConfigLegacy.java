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

package appeng.items.contents;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Compatibility adapter that wraps {@link IAEStackInventory} as {@link IItemHandler}.
 * <p>
 * Used when the legacy getConfigInventory() interface (returning IItemHandler) is called,
 * internally delegating to the new IAEStackInventory.
 * Can only handle item-type stacks; non-item-type slots return empty.
 * </p>
 */
public class CellConfigLegacy implements IItemHandler {

    private final IAEStackInventory config;
    private final IAEStackType<?> type;

    public CellConfigLegacy(IAEStackInventory config, IAEStackType<?> type) {
        this.config = config;
        this.type = type;
    }

    @Override
    public int getSlots() {
        return this.config.getSizeInventory();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        GenericStack gs = this.config.getGenericStack(slot);
        if (gs != null && gs.what() instanceof AEItemKey itemKey) {
            return itemKey.toStack((int) Math.min(gs.amount(), Integer.MAX_VALUE));
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            this.config.setGenericStack(slot, GenericStack.fromItemStack(stack));
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        GenericStack gs = this.config.getGenericStack(slot);
        if (gs != null && gs.what() instanceof AEItemKey itemKey) {
            ItemStack result = itemKey.toStack((int) Math.min(gs.amount(), Integer.MAX_VALUE));
            if (!simulate) {
                this.config.setGenericStack(slot, null);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }
}

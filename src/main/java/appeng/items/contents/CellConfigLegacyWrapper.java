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

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

/**
 * Compatibility adapter that wraps a legacy {@link IItemHandler} as {@link IAEStackInventory}.
 * <p>
 * Used when legacy code only provides getConfigInventory() (IItemHandler) but new code needs
 * getConfigAEInventory() (IAEStackInventory).
 * Converts ItemStack to AEItemStack on read, and IAEItemStack back to ItemStack on write.
 * </p>
 */
public class CellConfigLegacyWrapper extends IAEStackInventory {

    private final IItemHandler inventory;

    public CellConfigLegacyWrapper(IItemHandler inventory) {
        super(null, inventory.getSlots());
        this.inventory = inventory;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStackInSlot(final int slot) {
        ItemStack stack = this.inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return null;
        }
        return AEItemStack.fromItemStack(stack);
    }

    @Override
    public void putAEStackInSlot(final int slot, @Nullable IAEStack<?> stack) {
        // IItemHandler doesn't provide a direct set method, need to extract then insert
        // However we assume this is a config-type inventory (usually phantom slot), so we operate directly
        if (this.inventory instanceof net.minecraftforge.items.IItemHandlerModifiable) {
            net.minecraftforge.items.IItemHandlerModifiable modifiable =
                    (net.minecraftforge.items.IItemHandlerModifiable) this.inventory;
            if (stack instanceof appeng.api.storage.data.IAEItemStack) {
                modifiable.setStackInSlot(slot, ((appeng.api.storage.data.IAEItemStack) stack).createItemStack());
            } else {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        this.markDirty();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data, final String name) {
        // Delegate to the original inventory for its own handling, no NBT write here
    }

    @Override
    public void readFromNBT(@Nullable final NBTTagCompound data, final String name) {
        // Delegate to the original inventory for its own handling, no NBT read here
    }

    @Override
    public int getSizeInventory() {
        return this.inventory.getSlots();
    }

    @Override
    public void markDirty() {
        // Cannot notify IItemHandler to save, relies on external mechanism
    }

    @Override
    public StorageName getStorageName() {
        return StorageName.NONE;
    }
}

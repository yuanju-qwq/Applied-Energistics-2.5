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

package appeng.tile.inventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.Platform;

/**
 * Generic AE stack inventory that can store any type of {@link IAEStack} (items, fluids, etc.).
 * <p>
 * Unlike {@link AppEngInternalAEInventory}, this class is not limited to {@link appeng.api.storage.data.IAEItemStack},
 * and can mix-store different types of {@link IAEStack} in the same inventory.
 * </p>
 */
public class IAEStackInventory {

    private final IIAEStackInventory owner;
    private final IAEStack<?>[] inv;
    private final int size;
    private final StorageName storageName;

    /**
     * @param owner       the object holding this inventory, called back via {@link IIAEStackInventory#saveAEStackInv()} on changes
     * @param size        the number of slots
     * @param storageName the storage name identifier
     */
    public IAEStackInventory(final IIAEStackInventory owner, final int size, StorageName storageName) {
        this.owner = owner;
        this.size = size;
        this.inv = new IAEStack<?>[size];
        this.storageName = storageName;
    }

    /**
     * @param owner the object holding this inventory
     * @param size  the number of slots
     */
    public IAEStackInventory(final IIAEStackInventory owner, final int size) {
        this(owner, size, StorageName.NONE);
    }

    /**
     * @return whether the inventory is empty (all slots are null)
     */
    public boolean isEmpty() {
        for (int x = 0; x < this.size; x++) {
            if (this.inv[x] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get the generic AE stack at the specified slot.
     *
     * @param slot the slot index
     * @return the IAEStack at that slot, possibly null
     */
    @Nullable
    public IAEStack<?> getAEStackInSlot(final int slot) {
        return this.inv[slot];
    }

    /**
     * Set the generic AE stack at the specified slot, and trigger {@link #markDirty()}.
     *
     * @param slot  the slot index
     * @param stack the stack to place, or null to clear
     */
    public void putAEStackInSlot(final int slot, @Nullable IAEStack<?> stack) {
        this.inv[slot] = stack;
        this.markDirty();
    }

    // ---- NBT serialization/deserialization ----

    /**
     * Write inventory data to an ItemStack's NBT.
     *
     * @param stack the target ItemStack
     * @param name  the NBT key name
     */
    public void writeToNBT(@Nonnull ItemStack stack, String name) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        this.writeToNBT(stack.getTagCompound(), name);
        if (stack.getTagCompound().isEmpty()) {
            stack.setTagCompound(null);
        }
    }

    /**
     * Write inventory data to the specified NBT compound tag.
     *
     * @param data the write target
     * @param name the NBT key name
     */
    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = new NBTTagCompound();
        this.writeToNBTInternal(c);
        if (c.isEmpty()) {
            data.removeTag(name);
        } else {
            data.setTag(name, c);
        }
    }

    private void writeToNBTInternal(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                if (this.inv[x] != null) {
                    final NBTTagCompound c = new NBTTagCompound();
                    appeng.util.AEStackSerialization.writeStackNBT(this.inv[x], c);
                    target.setTag("#" + x, c);
                }
            } catch (final Exception ignored) {
            }
        }
    }

    /**
     * Read inventory data from an NBT compound tag.
     *
     * @param data the outer NBT containing inventory data (may be null)
     * @param name the NBT key name
     */
    public void readFromNBT(@Nullable final NBTTagCompound data, final String name) {
        if (data != null && data.hasKey(name, NBT.TAG_COMPOUND)) {
            this.readFromNBTInternal(data.getCompoundTag(name));
        }
    }

    private void readFromNBTInternal(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final String key = "#" + x;
                if (target.hasKey(key, NBT.TAG_COMPOUND)) {
                    final NBTTagCompound c = target.getCompoundTag(key);
                    this.inv[x] = Platform.readStackNBT(c, true);
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    /**
     * @return the total number of inventory slots
     */
    public int getSizeInventory() {
        return this.size;
    }

    /**
     * Alias for {@link #getSizeInventory()} for consistency with Collection-style APIs.
     *
     * @return the number of slots
     */
    public int size() {
        return this.size;
    }

    /**
     * Mark the inventory as modified, notifying the owner to save.
     */
    public void markDirty() {
        if (this.owner != null && Platform.isServer()) {
            this.owner.saveAEStackInv();
        }
    }

    /**
     * @return the name identifier of this inventory
     */
    public StorageName getStorageName() {
        return this.storageName;
    }

    /**
     * Returns a read-only {@link IItemHandler} view of this inventory.
     * Only IAEItemStack entries are visible as ItemStack; other types (e.g. fluids) appear as empty.
     * Mutations through the IItemHandler (insert/extract) write back to this IAEStackInventory.
     */
    public IItemHandler asItemHandler() {
        return new ItemHandlerView();
    }

    /**
     * Read-write IItemHandler adapter that delegates to the parent IAEStackInventory.
     * Only IAEItemStack slots are accessible; fluid/other stack types appear as empty.
     */
    private class ItemHandlerView implements IItemHandler {
        @Override
        public int getSlots() {
            return size;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            final IAEStack<?> s = inv[slot];
            if (s instanceof IAEItemStack is) {
                return is.createItemStack();
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
                putAEStackInSlot(slot, appeng.util.item.AEItemStack.fromItemStack(stack));
            }
            return ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            final IAEStack<?> s = inv[slot];
            if (!simulate) {
                putAEStackInSlot(slot, null);
            }
            if (s instanceof IAEItemStack is) {
                return is.createItemStack();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }
    }
}

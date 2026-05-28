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

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import java.util.Collections;
import java.util.Set;

import appeng.api.config.FuzzyMode;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.tile.inventory.IAEStackInventory;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;

/**
 * @author DrummerMC
 * @version rv6 - 2018-01-17
 * @since rv6 2018-01-17
 */
public abstract class AbstractCellInventory<T extends IAEStack<T>> implements ICellInventory<T> {
    private static final int MAX_ITEM_TYPES = 63;
    private static final String ITEM_TYPE_TAG = "it";
    private static final String ITEM_COUNT_TAG = "ic";
    private static final String ITEM_SLOT = "#";
    private static final String ITEM_SLOT_COUNT = "@";
    protected static final String ITEM_PRE_FORMATTED_COUNT = "PF";
    protected static final String ITEM_PRE_FORMATTED_SLOT = "PF#";
    protected static final String ITEM_PRE_FORMATTED_NAME = "PN";
    protected static final String ITEM_PRE_FORMATTED_FUZZY = "FP";
    private static final String[] ITEM_SLOT_KEYS = new String[MAX_ITEM_TYPES];
    private static final String[] ITEM_SLOT_COUNT_KEYS = new String[MAX_ITEM_TYPES];
    private final NBTTagCompound tagCompound;
    protected final ISaveProvider container;
    private int maxItemTypes = MAX_ITEM_TYPES;
    private short storedItemTypes = 0;
    private long storedItemCount = 0;
    protected IItemList<T> cellItems;

    /**
     * AEKey-based primary storage. All mutating operations go through this counter.
     * The legacy {@link #cellItems} is a lazy-derived view that gets rebuilt on access if stale.
     */
    @javax.annotation.Nullable
    private KeyCounter cellKeyCounter = null;

    /**
     * When true, {@link #cellItems} is stale and must be rebuilt from {@link #cellKeyCounter}.
     */
    private boolean cellItemsDirty = false;

    private final ItemStack i;
    protected final IStorageCell<T> cellType;
    protected final int itemsPerByte;
    private boolean isPersisted = true;

    static {
        for (int x = 0; x < MAX_ITEM_TYPES; x++) {
            ITEM_SLOT_KEYS[x] = ITEM_SLOT + x;
            ITEM_SLOT_COUNT_KEYS[x] = ITEM_SLOT_COUNT + x;
        }
    }

    protected AbstractCellInventory(final IStorageCell<T> cellType, final ItemStack o, final ISaveProvider container) {
        this.i = o;
        this.cellType = cellType;
        this.itemsPerByte = this.cellType.getStackType().getUnitsPerByte();
        this.maxItemTypes = this.cellType.getTotalTypes(this.i);

        if (this.maxItemTypes > MAX_ITEM_TYPES) {
            this.maxItemTypes = MAX_ITEM_TYPES;
        }
        if (this.maxItemTypes < 1) {
            this.maxItemTypes = 1;
        }

        this.container = container;
        this.tagCompound = appeng.util.ItemStackNbtHelper.openNbtData(o);
        this.storedItemTypes = this.tagCompound.getShort(ITEM_TYPE_TAG);
        this.storedItemCount = this.tagCompound.getLong(ITEM_COUNT_TAG);
        this.cellItems = null;
    }

    /**
     * Reads the cell's config inventory (whitelist/blacklist filter) and returns the set of AEKeys.
     * Each slot may hold an ItemStack representing an item or fluid that the cell is configured to accept or reject.
     *
     * @return unmodifiable set of keys from the config, or empty set if none configured
     */
    public Set<AEKey> getFilterKeys() {
        final IAEStackInventory config = this.cellType.getConfigAEInventory(this.i);
        if (config == null || config.getSizeInventory() == 0) {
            return Collections.emptySet();
        }
        final java.util.HashSet<AEKey> keys = new java.util.HashSet<>();
        for (int slot = 0; slot < config.getSizeInventory(); slot++) {
            final GenericStack gs = config.getGenericStack(slot);
            if (gs == null) {
                continue;
            }
            keys.add(gs.what());
        }
        return Collections.unmodifiableSet(keys);
    }

    /**
     * @return an {@link AEKeyFilter} that matches keys in the cell's configured filter,
     *         or {@link AEKeyFilter#none()} if no filter is configured.
     */
    public AEKeyFilter getAEKeyFilter() {
        final Set<AEKey> filterKeys = getFilterKeys();
        if (filterKeys.isEmpty()) {
            return AEKeyFilter.none();
        }
        return filterKeys::contains;
    }

    /**
     * @return the AEKey-based primary storage counter
     */
    public KeyCounter getKeyCounter() {
        if (this.cellKeyCounter == null) {
            this.cellKeyCounter = new KeyCounter();
            // Populate from cellItems if already loaded
            if (this.cellItems != null) {
                for (final T v : this.cellItems) {
                    AEKey key = v.toAEKey();
                    if (key != null) {
                        this.cellKeyCounter.set(key, v.getStackSize());
                    }
                }
            }
        }
        return this.cellKeyCounter;
    }

    private void rebuildCellItemsFromKeyCounter() {
        if (this.cellItems == null) {
            this.cellItems = this.getStackType().createList();
        } else {
            this.cellItems.resetStatus();
        }
        for (var entry : this.cellKeyCounter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            @SuppressWarnings("unchecked")
            T stack = (T) key.toIAEStack(amount);
            if (stack != null) {
                this.cellItems.add(stack);
            }
        }
        this.cellItemsDirty = false;
    }

    protected IItemList<T> getCellItems() {
        if (this.cellItems == null) {
            this.cellItems = this.getStackType().createList();
            this.loadCellItems();
        } else if (this.cellItemsDirty && this.cellKeyCounter != null) {
            rebuildCellItemsFromKeyCounter();
        }

        return this.cellItems;
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        long itemCount = 0;

        // write from primary KeyCounter (old NBT format for backward compat)
        int x = 0;
        final KeyCounter kc = this.getKeyCounter();
        for (var entry : kc) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            itemCount += amount;

            final NBTTagCompound g = new NBTTagCompound();
            key.toIAEStack(amount).writeToNBT(g);
            this.tagCompound.setTag(ITEM_SLOT_KEYS[x], g);
            this.tagCompound.setLong(ITEM_SLOT_COUNT_KEYS[x], amount);

            x++;
        }

        final short oldStoredItems = this.storedItemTypes;

        this.storedItemTypes = (short) x;
        if (x == 0) {
            this.tagCompound.removeTag(ITEM_TYPE_TAG);
        } else {
            this.tagCompound.setShort(ITEM_TYPE_TAG, this.storedItemTypes);
        }

        this.storedItemCount = itemCount;
        if (itemCount == 0) {
            this.tagCompound.removeTag(ITEM_COUNT_TAG);
        } else {
            this.tagCompound.setLong(ITEM_COUNT_TAG, itemCount);
        }

        // clean any old crusty stuff...
        for (; x >= oldStoredItems && x < this.maxItemTypes; x++) {
            this.tagCompound.removeTag(ITEM_SLOT_KEYS[x]);
            this.tagCompound.removeTag(ITEM_SLOT_COUNT_KEYS[x]);
        }

        this.isPersisted = true;
    }

    protected void saveChanges() {
        // recalculate values from primary KeyCounter
        final KeyCounter kc = this.getKeyCounter();
        this.storedItemTypes = 0;
        this.storedItemCount = 0;
        for (var entry : kc) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                this.storedItemTypes++;
                this.storedItemCount += amount;
            }
        }

        this.cellItemsDirty = true;
        this.isPersisted = false;
        if (this.container != null) {
            this.container.saveChanges(this);
        } else {
            // if there is no ISaveProvider, store to NBT immediately
            this.persist();
        }
    }

    private void loadCellItems() {
        if (this.cellItems == null) {
            this.cellItems = this.getStackType().createList();
        }

        this.cellItems.resetStatus(); // clears totals and stuff.
        if (this.cellKeyCounter != null) {
            this.cellKeyCounter.reset();
        }

        final long types = this.getStoredItemTypes();
        boolean needsUpdate = false;

        for (int slot = 0; slot < types; slot++) {
            NBTTagCompound compoundTag = this.tagCompound.getCompoundTag(ITEM_SLOT_KEYS[slot]);
            long stackSize = this.tagCompound.getLong(ITEM_SLOT_COUNT_KEYS[slot]);
            needsUpdate |= !this.loadCellItem(compoundTag, stackSize);
        }

        this.cellItemsDirty = false;

        if (needsUpdate) {
            this.saveChanges();
        }
    }

    /**
     * Load a single item.
     *
     * @param compoundTag
     * @param stackSize
     * @return true when successfully loaded
     */
    protected abstract boolean loadCellItem(NBTTagCompound compoundTag, long stackSize);

    @Override
    public abstract IAEStackType<T> getStackType();

    /**
     * @return the AEKey-based {@link KeyCounter} of all stored items
     */
    public KeyCounter getAvailableKeyCounter() {
        return this.getKeyCounter();
    }

    /**
     * @deprecated Use {@link #getAvailableKeyCounter()} instead.
     * Fills the provided legacy IItemList from the internal KeyCounter.
     */
    @Override
    @Deprecated
    public IItemList<T> getAvailableItems(final IItemList<T> out) {
        for (var entry : this.getKeyCounter()) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            @SuppressWarnings("unchecked")
            T stack = (T) key.toIAEStack(amount);
            if (stack != null) {
                out.add(stack);
            }
        }
        return out;
    }

    @Override
    public ItemStack getItemStack() {
        return this.i;
    }

    @Override
    public double getIdleDrain() {
        return this.cellType.getIdleDrain();
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.i);
    }

    @Override
    public IItemHandler getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.i);
    }

    @Override
    public int getBytesPerType() {
        return this.cellType.getBytesPerType(this.i);
    }

    @Override
    public boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                || (bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0))
                && this
                        .getRemainingItemTypes() > 0;
    }

    @Override
    public long getTotalBytes() {
        return this.cellType.getBytes(this.i);
    }

    @Override
    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    @Override
    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    @Override
    public long getStoredItemCount() {
        return this.storedItemCount;
    }

    @Override
    public long getStoredItemTypes() {
        return this.storedItemTypes;
    }

    @Override
    public long getRemainingItemTypes() {
        final long basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        final long baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    @Override
    public long getUsedBytes() {
        final long bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / this.itemsPerByte;
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    @Override
    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * this.itemsPerByte + this.getUnusedItemCount();
        return remaining > 0 ? remaining : 0;
    }

    @Override
    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % 8);

        if (div == 0) {
            return 0;
        }

        return this.itemsPerByte - div;
    }

    @Override
    public int getStatusForCell() {
        if (this.getUsedBytes() == 0) {
            return 4;
        }
        if (this.canHoldNewItem()) {
            return 1;
        }
        if (this.getRemainingItemCount() > 0) {
            return 2;
        }
        return 3;
    }
}

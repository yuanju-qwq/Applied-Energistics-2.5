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

package appeng.container.implementations;

import java.util.Iterator;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.*;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.misc.AbstractPartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.iterators.NullIterator;

/**
 * Unified container for all storage bus types (item, fluid, etc.).
 * Operates on {@link AbstractPartStorageBus} through the common base class,
 * eliminating the need for per-type container subclasses.
 */
public class ContainerStorageBus extends ContainerUpgradeable implements IStorageBusContainer {

    private final AbstractPartStorageBus<?> storageBus;

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    public ContainerStorageBus(final InventoryPlayer ip, final AbstractPartStorageBus<?> te) {
        super(ip, te);
        this.storageBus = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        // config slots are handled by GUI-side VirtualMEPhantomSlot.
        // Only add upgrade slots here.

        final IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer((new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0,
                187, 8, this.getInventoryPlayer()))
                .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 8 + 18,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 8 + 18 * 2,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 8 + 18 * 3,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 4, 187, 8 + 18 * 4,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setReadWriteMode(
                    (AccessRestriction) this.getUpgradeable().getConfigManager().getSetting(Settings.ACCESS));
            this.setStorageFilter(
                    (StorageFilter) this.getUpgradeable().getConfigManager().getSetting(Settings.STORAGE_FILTER));
            try {
                this.setStickyMode(
                        (YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.STICKY_MODE));
            } catch (final IllegalArgumentException ignored) {
                // STICKY_MODE may not be registered for all storage bus types (e.g. fluid)
            }

            // Clear config slots that exceed current capacity (e.g. capacity upgrade removed)
            final IAEStackInventory cfg = this.getConfig();
            if (cfg != null) {
                final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
                final int maxSlots = 18 + (9 * upgrades);
                for (int i = maxSlots; i < cfg.getSizeInventory(); i++) {
                    if (cfg.getAEStackInSlot(i) != null) {
                        cfg.putAEStackInSlot(i, null);
                    }
                }
            }
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > idx;
    }

    @Override
    public void clear() {
        final IAEStackInventory inv = this.getConfig();
        if (inv != null) {
            for (int x = 0; x < inv.getSizeInventory(); x++) {
                inv.putAEStackInSlot(x, null);
            }
        }
        this.detectAndSendChanges();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void partition() {
        final IAEStackInventory inv = this.getConfig();
        if (inv == null) {
            return;
        }

        final IMEInventory cellInv = this.storageBus.getInternalHandler();

        Iterator<IAEStack<?>> i = new NullIterator<>();
        if (cellInv != null) {
            final IItemList list = cellInv
                    .getAvailableItems(
                            this.storageBus.getStackType().createList());
            i = list.iterator();
        }

        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (i.hasNext() && this.isSlotEnabled((x / 9) - 2)) {
                final IAEStack<?> next = i.next();
                final IAEStack<?> copy = next.copy();
                copy.setStackSize(1);
                inv.putAEStackInSlot(x, copy);
            } else {
                inv.putAEStackInSlot(x, null);
            }
        }

        this.detectAndSendChanges();
    }

    public AbstractPartStorageBus<?> getStorageBus() {
        return this.storageBus;
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    private void setReadWriteMode(final AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    private void setStorageFilter(final StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }

    public YesNo getStickyMode() {
        return this.stickyMode;
    }

    private void setStickyMode(final YesNo stickyMode) {
        this.stickyMode = stickyMode;
    }
}

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
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.*;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.SlotRestrictedInput;
import appeng.parts.misc.PartStorageBus;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import appeng.util.iterators.NullIterator;
import appeng.util.item.AEItemStackType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerStorageBus extends ContainerUpgradeable implements IVirtualSlotHolder, IVirtualSlotSource {

    private final PartStorageBus storageBus;

    @GuiSync(3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    @GuiSync(7)
    public YesNo stickyMode = YesNo.NO;

    // 服务端用于增量同步的客户端快照
    private final IAEStack<?>[] configClientSlot = new IAEStack[63];

    public ContainerStorageBus(final InventoryPlayer ip, final PartStorageBus te) {
        super(ip, te);
        this.storageBus = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        // config 槽位不再使用 Minecraft Slot，改为由 GUI 侧的 VirtualMEPhantomSlot 处理。
        // 这里只添加升级槽位。

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
            this.setStickyMode((YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.STICKY_MODE));

            // 使用虚拟槽位同步 config
            final IAEStackInventory config = this.storageBus.getAEInventoryByName(StorageName.CONFIG);
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > idx;
    }

    public void clear() {
        final IAEStackInventory inv = this.storageBus.getAEInventoryByName(StorageName.CONFIG);
        for (int x = 0; x < inv.getSizeInventory(); x++) {
            inv.putAEStackInSlot(x, null);
        }
        this.detectAndSendChanges();
    }

    public void partition() {
        final IAEStackInventory inv = this.storageBus.getAEInventoryByName(StorageName.CONFIG);

        final IMEInventory<IAEItemStack> cellInv = this.storageBus.getInternalHandler();

        Iterator<IAEItemStack> i = new NullIterator<>();
        if (cellInv != null) {
            final IItemList<IAEItemStack> list = cellInv
                    .getAvailableItems(
                            AEItemStackType.INSTANCE.createList());
            i = list.iterator();
        }

        for (int x = 0; x < inv.getSizeInventory(); x++) {
            if (i.hasNext() && this.isSlotEnabled((x / 9) - 2)) {
                final IAEItemStack next = i.next();
                final IAEItemStack copy = next.copy();
                copy.setStackSize(1);
                inv.putAEStackInSlot(x, copy);
            } else {
                inv.putAEStackInSlot(x, null);
            }
        }

        this.detectAndSendChanges();
    }

    // ---- IVirtualSlotHolder 实现（接收服务端推送的虚拟槽位数据，客户端侧）----

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory config = this.storageBus.getAEInventoryByName(StorageName.CONFIG);
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
    }

    // ---- IVirtualSlotSource 实现（接收客户端发来的虚拟槽位更新，服务端侧）----

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        final IAEStackInventory config = this.storageBus.getAEInventoryByName(StorageName.CONFIG);
        if (config != null && slotId >= 0 && slotId < config.getSizeInventory()) {
            config.putAEStackInSlot(slotId, aes);
        }
    }

    /**
     * 获取 config IAEStackInventory，供 GUI 层使用。
     */
    public IAEStackInventory getConfig() {
        return this.storageBus.getAEInventoryByName(StorageName.CONFIG);
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

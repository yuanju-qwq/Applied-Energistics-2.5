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

package appeng.parts.reporting;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IConversionMonitorHandler;
import appeng.api.parts.IConversionMonitorHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.util.InventoryAdaptor;
import appeng.util.StorageHelper;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * Conversion Monitor handler for item stack type.
 * <p>
 * Handles direct item insertion/extraction between the player's inventory and the ME network.
 * Items do not have a "container" concept in this context (unlike fluids with buckets),
 * so container-related methods return false/null.
 */
public final class ItemConversionMonitorHandler implements IConversionMonitorHandler<IAEItemStack> {

    public static final ItemConversionMonitorHandler INSTANCE = new ItemConversionMonitorHandler();

    private ItemConversionMonitorHandler() {}

    @Nonnull
    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return AEItemStackType.INSTANCE;
    }

    // Items don't have a "container" concept for conversion monitor interactions
    @Override
    public boolean canInteractWithContainer(@Nonnull ItemStack heldItem) {
        return false;
    }

    @Nullable
    @Override
    public IAEItemStack getStackFromContainer(@Nonnull ItemStack heldItem) {
        return null;
    }

    @Override
    public void insertFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEItemStack> monitor,
            @Nonnull IActionSource src) {
        final IAEItemStack input = AEItemStack.fromItemStack(player.getHeldItem(hand));
        if (input == null) {
            return;
        }
        final IAEItemStack failedToInsert = StorageHelper.poweredInsert(energy, monitor, input, src);
        player.setHeldItem(hand, failedToInsert == null ? ItemStack.EMPTY : failedToInsert.createItemStack());
    }

    @Override
    public void insertAllFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull IAEItemStack displayed,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEItemStack> monitor,
            @Nonnull IActionSource src) {
        final IAEItemStack template = displayed.copy();
        final IItemHandler inv = new PlayerMainInvWrapper(player.inventory);

        for (int x = 0; x < inv.getSlots(); x++) {
            final ItemStack targetStack = inv.getStackInSlot(x);
            if (template.equals(targetStack)) {
                final ItemStack canExtract = inv.extractItem(x, targetStack.getCount(), true);
                if (!canExtract.isEmpty()) {
                    template.setStackSize(canExtract.getCount());
                    final IAEItemStack failedToInsert = StorageHelper.poweredInsert(
                            energy, monitor, template, src);
                    inv.extractItem(x,
                            failedToInsert == null
                                    ? canExtract.getCount()
                                    : canExtract.getCount() - (int) failedToInsert.getStackSize(),
                            false);
                }
            }
        }
    }

    @Override
    public void extractToPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IAEItemStack displayed,
            long count,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEItemStack> monitor,
            @Nonnull IActionSource src,
            @Nonnull IConversionMonitorHost host) {
        final IAEItemStack request = displayed.copy();
        request.setStackSize(count);

        final IAEItemStack retrieved = StorageHelper.poweredExtraction(energy, monitor, request, src);
        if (retrieved != null) {
            ItemStack newItems = retrieved.createItemStack();
            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(player);
            newItems = adaptor.addItems(newItems);
            if (!newItems.isEmpty()) {
                final TileEntity te = host.getTile();
                final List<ItemStack> list = Collections.singletonList(newItems);
                appeng.util.WorldHelper.spawnDrops(
                        player.world, te.getPos().offset(host.getSideFacing()), list);
            }

            if (player.openContainer != null) {
                player.openContainer.detectAndSendChanges();
            }
        }
    }

    @Nullable
    @Override
    public IAEItemStack resolveConfiguredStack(@Nonnull ItemStack heldItem) {
        if (heldItem.isEmpty()) {
            return null;
        }
        final IAEItemStack stack = AEItemStack.fromItemStack(heldItem);
        return stack != null ? (IAEItemStack) stack.setStackSize(0) : null;
    }
}

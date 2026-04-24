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

package appeng.util;

import javax.annotation.Nullable;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.*;
import appeng.util.item.AEItemStack;

/**
 * Universal Facade for other inventories. Used to conveniently interact with various types of inventories. This is not
 * used for actually monitoring an inventory. It is just for insertion and extraction, and is primarily used by
 * import/export buses.
 */
public abstract class InventoryAdaptor implements Iterable<ItemSlot> {
    @CapabilityInject(IItemRepository.class)
    public static Capability<IItemRepository> ITEM_REPOSITORY_CAPABILITY = null;

    public static InventoryAdaptor getAdaptor(final TileEntity te, final EnumFacing d) {
        if (te != null) {
            // 首先检查是否同时具有 IItemHandler 和 IFluidHandler
            IItemHandler itemHandler = null;
            IFluidHandler fluidHandler = null;

            if (ITEM_REPOSITORY_CAPABILITY != null && te.hasCapability(ITEM_REPOSITORY_CAPABILITY, d)) {
                IItemRepository itemRepository = te.getCapability(ITEM_REPOSITORY_CAPABILITY, d);
                if (itemRepository != null) {
                    return new AdaptorItemRepository(itemRepository);
                }
            }

            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d)) {
                itemHandler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, d);
            }

            if (te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, d)) {
                fluidHandler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, d);
            }

            // 如果同时有 item 和 fluid 能力，创建复合适配器
            if (itemHandler != null && fluidHandler != null) {
                return new AdaptorFluidAndItemHandler(itemHandler, fluidHandler, d);
            }

            // 只有 fluid 能力
            if (fluidHandler != null) {
                return new AdaptorFluidHandler(fluidHandler, d);
            }

            // 只有 item 能力
            if (itemHandler != null) {
                return new AdaptorItemHandler(itemHandler);
            }
        }
        return null;
    }

    public static InventoryAdaptor getAdaptor(final EntityPlayer te) {
        if (te != null) {
            return new AdaptorItemHandlerPlayerInv(te);
        }
        return null;
    }

    // return what was extracted.
    public abstract ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination);

    public abstract ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination);

    // return what was extracted.
    public abstract ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination);

    public abstract ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination);

    // return what isn't used...
    public abstract ItemStack addItems(ItemStack toBeAdded);

    public abstract ItemStack simulateAdd(ItemStack toBeSimulated);

    public abstract boolean containsItems();

    public abstract boolean hasSlots();

    /**
     * Add a generic AE stack (item, fluid, or future types) to the target inventory.
     * Items are handled natively; unknown types fall back to asItemStackRepresentation().
     * Subclasses may override to support additional types (e.g., fluids).
     *
     * @return the remainder that could not be inserted, or null if fully inserted
     */
    @Nullable
    public IAEStack<?> addStack(IAEStack<?> toBeAdded) {
        if (toBeAdded instanceof IAEItemStack itemStack) {
            ItemStack result = this.addItems(itemStack.createItemStack());
            return AEItemStack.fromItemStack(result);
        }
        // Fallback: convert to item representation and try to insert
        ItemStack repr = toBeAdded.asItemStackRepresentation();
        if (!repr.isEmpty()) {
            ItemStack result = this.addItems(repr);
            if (result.isEmpty()) {
                return null;
            }
        }
        return toBeAdded;
    }

    /**
     * Simulate adding a generic AE stack to the target inventory.
     * Items are handled natively; unknown types fall back to asItemStackRepresentation().
     * Subclasses may override to support additional types (e.g., fluids).
     *
     * @return the simulated remainder, or null if fully insertable
     */
    @Nullable
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated) {
        if (toBeSimulated instanceof IAEItemStack itemStack) {
            ItemStack result = this.simulateAdd(itemStack.createItemStack());
            return AEItemStack.fromItemStack(result);
        }
        // Fallback: convert to item representation and try to simulate
        ItemStack repr = toBeSimulated.asItemStackRepresentation();
        if (!repr.isEmpty()) {
            ItemStack result = this.simulateAdd(repr);
            if (result.isEmpty()) {
                return null;
            }
        }
        return toBeSimulated;
    }

}

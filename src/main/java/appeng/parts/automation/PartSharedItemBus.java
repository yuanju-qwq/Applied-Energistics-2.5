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

package appeng.parts.automation;

import net.minecraft.item.ItemStack;

import appeng.core.settings.TickRates;
import appeng.util.InventoryAdaptor;

/**
 * Shared base class for item I/O buses (Import Bus and Export Bus).
 * <p>
 * Extends {@link AbstractPartIOBus} with item-specific functionality:
 * <ul>
 *   <li>{@link InventoryAdaptor} for interacting with adjacent item inventories</li>
 *   <li>Item-based transfer amount (1 per base unit)</li>
 * </ul>
 */
public abstract class PartSharedItemBus extends AbstractPartIOBus {

    private final TickRates tickRates;

    public PartSharedItemBus(final TickRates tickRates, final ItemStack is) {
        super(is);
        this.tickRates = tickRates;
    }

    @Override
    protected TickRates getTickRates() {
        return this.tickRates;
    }

    /**
     * Get the item inventory adaptor for the adjacent block.
     */
    protected InventoryAdaptor getHandler() {
        final net.minecraft.tileentity.TileEntity target = this.getConnectedTE();
        return InventoryAdaptor.getAdaptor(target, this.getSide().getFacing().getOpposite());
    }

    /**
     * Calculate the number of items to transfer based on Speed upgrades.
     * Item transfer uses a base factor of 1.
     */
    protected int calculateItemsToSend() {
        return this.calculateAmountToSend(1);
    }
}

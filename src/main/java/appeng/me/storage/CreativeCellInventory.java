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

package appeng.me.storage;

import net.minecraft.item.ItemStack;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.contents.CellConfig;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

@SuppressWarnings("rawtypes")
public class CreativeCellInventory implements IMEInventoryHandler {

    private final IItemList itemListCache = new ItemList();

    protected CreativeCellInventory(final ItemStack o) {
        final CellConfig cc = new CellConfig(o);
        for (final ItemStack is : cc) {
            if (!is.isEmpty()) {
                final IAEItemStack i = AEItemStack.fromItemStack(is);
                i.setStackSize(Integer.MAX_VALUE);
                this.itemListCache.add(i);
            }
        }
    }

    public static ICellInventoryHandler getCell(final ItemStack o) {
        return new BasicCellInventoryHandler(new CreativeCellInventory(o),
                AEKeyType.items());
    }

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable mode, final IActionSource src) {
        if (input == null) return null;
        if (this.itemListCache.findPreciseGeneric(input.what()) != null) {
            return null; // accepted
        }
        return input; // rejected
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null) return null;
        if (this.itemListCache.findPreciseGeneric(request.what()) == null) {
            return null;
        }
        return request;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter kc = new KeyCounter();
        for (final Object ais : this.itemListCache) {
            if (ais instanceof IAEItemStack itemStack) {
                AEItemKey key = (AEItemKey) itemStack.toAEKey();
                if (key != null) {
                    kc.add(key, itemStack.getStackSize());
                }
            }
        }
        return kc;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        return this.itemListCache.findPreciseGeneric(input) != null;
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return this.itemListCache.findPreciseGeneric(input) != null;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }
}

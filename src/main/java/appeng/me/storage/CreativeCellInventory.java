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
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.items.contents.CellConfig;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

@SuppressWarnings("rawtypes")
public class CreativeCellInventory implements IMEInventoryHandler {

    private final KeyCounter itemListCache = new KeyCounter();

    protected CreativeCellInventory(final ItemStack o) {
        final CellConfig cc = new CellConfig(o);
        for (final ItemStack is : cc) {
            if (!is.isEmpty()) {
                final AEItemKey key = AEItemKey.of(is);
                if (key != null) {
                    this.itemListCache.add(key, Integer.MAX_VALUE);
                }
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
        if (this.itemListCache.get(input.what()) > 0) {
            return null; // accepted
        }
        return input; // rejected
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null) return null;
        if (this.itemListCache.get(request.what()) == 0) {
            return null;
        }
        return request;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter kc = new KeyCounter();
        for (Object2LongMap.Entry<AEKey> entry : this.itemListCache) {
            kc.add(entry.getKey(), entry.getLongValue());
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
        return this.itemListCache.get(input) > 0;
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return this.itemListCache.get(input) > 0;
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

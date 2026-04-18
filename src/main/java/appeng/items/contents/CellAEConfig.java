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

import net.minecraft.item.ItemStack;

import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;

/**
 * 娉涘瀷鐗堟湰鐨?CellConfig锛屽熀浜?{@link IAEStackInventory} 瀛樺偍 {@link appeng.api.storage.data.IAEStack}銆?
 * 鍙互瀛樺偍鐗╁搧銆佹祦浣撶瓑浠绘剰绫诲瀷鐨?AE 鏍堜綔涓鸿繃婊ら厤缃€?
 */
public class CellAEConfig extends IAEStackInventory {

    protected final ItemStack is;

    public CellAEConfig(final ItemStack is) {
        super(null, 63);
        this.is = is;
        this.readFromNBT(appeng.util.ItemStackNbtHelper.openNbtData(is), "list");
    }

    @Override
    public void markDirty() {
        this.writeToNBT(appeng.util.ItemStackNbtHelper.openNbtData(this.is), "list");
    }
}

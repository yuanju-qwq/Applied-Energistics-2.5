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

package appeng.tile.crafting;

import net.minecraft.item.ItemStack;

import appeng.block.crafting.BlockCraftingUnit;

public class TileCraftingStorageTile extends TileCraftingTile {
    private static final int KILO_SCALAR = 1024;

    public static int getStorageBytesFromItemStack(ItemStack stack) {
        return CraftingStorageType.fromItemStack(stack)
                .map(CraftingStorageType::getStorageBytes)
                .orElse(0);
    }

    @Override
    protected ItemStack getItemFromTile(final Object obj) {
        final int storageKiloBytes = ((TileCraftingTile) obj).getStorageBytes() / KILO_SCALAR;

        return CraftingStorageType.fromKiloBytes(storageKiloBytes)
                .flatMap(CraftingStorageType::getItemStack)
                .orElseGet(() -> super.getItemFromTile(obj));
    }

    @Override
    public boolean isAccelerator() {
        return false;
    }

    @Override
    public boolean isStorage() {
        return true;
    }

    @Override
    public int getStorageBytes() {
        if (this.world == null || this.notLoaded() || this.isInvalid()) {
            return 0;
        }

        final BlockCraftingUnit unit = (BlockCraftingUnit) this.world.getBlockState(this.pos).getBlock();
        // 使用CraftingStorageType枚举来获取字节数
        return CraftingStorageType.fromBlockType(unit.type)
                .map(CraftingStorageType::getStorageBytes)
                .orElse(0);
    }
}

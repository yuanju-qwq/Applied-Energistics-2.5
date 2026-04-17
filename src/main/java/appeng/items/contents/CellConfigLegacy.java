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

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

/**
 * 将 {@link IAEStackInventory} 包装为 {@link IItemHandler} 的兼容适配器。
 * <p>
 * 当调用旧版 getConfigInventory() 接口（返回 IItemHandler）时使用，
 * 内部委托给新的 IAEStackInventory。
 * 只能处理物品类型的栈，非物品类型的槽位返回空。
 * </p>
 */
public class CellConfigLegacy implements IItemHandler {

    private final IAEStackInventory config;
    private final IAEStackType<?> type;

    public CellConfigLegacy(IAEStackInventory config, IAEStackType<?> type) {
        this.config = config;
        this.type = type;
    }

    @Override
    public int getSlots() {
        return this.config.getSizeInventory();
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        IAEStack<?> stack = this.config.getAEStackInSlot(slot);
        if (stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).createItemStack();
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!simulate) {
            this.config.putAEStackInSlot(slot, AEItemStack.fromItemStack(stack));
        }
        return ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        IAEStack<?> stack = this.config.getAEStackInSlot(slot);
        if (stack instanceof IAEItemStack) {
            ItemStack result = ((IAEItemStack) stack).createItemStack();
            if (!simulate) {
                this.config.putAEStackInSlot(slot, null);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }
}

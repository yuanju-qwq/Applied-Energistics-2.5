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

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

/**
 * 将旧版 {@link IItemHandler} 包装为 {@link IAEStackInventory} 的兼容适配器。
 * <p>
 * 当旧代码只提供 getConfigInventory()（IItemHandler）而新代码需要
 * getConfigAEInventory()（IAEStackInventory）时，使用此包装器。
 * 读取时将 ItemStack 转为 AEItemStack，写入时将 IAEItemStack 转回 ItemStack。
 * </p>
 */
public class CellConfigLegacyWrapper extends IAEStackInventory {

    private final IItemHandler inventory;

    public CellConfigLegacyWrapper(IItemHandler inventory) {
        super(null, inventory.getSlots());
        this.inventory = inventory;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStackInSlot(final int slot) {
        ItemStack stack = this.inventory.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return null;
        }
        return AEItemStack.fromItemStack(stack);
    }

    @Override
    public void putAEStackInSlot(final int slot, @Nullable IAEStack<?> stack) {
        // IItemHandler 不提供直接 set 的方法，需要先 extract 再 insert
        // 不过我们假定这是 config 类的 inventory（通常是 phantom slot），直接操作
        if (this.inventory instanceof net.minecraftforge.items.IItemHandlerModifiable) {
            net.minecraftforge.items.IItemHandlerModifiable modifiable =
                    (net.minecraftforge.items.IItemHandlerModifiable) this.inventory;
            if (stack instanceof appeng.api.storage.data.IAEItemStack) {
                modifiable.setStackInSlot(slot, ((appeng.api.storage.data.IAEItemStack) stack).createItemStack());
            } else {
                modifiable.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
        this.markDirty();
    }

    @Override
    public void writeToNBT(final NBTTagCompound data, final String name) {
        // 委托给原始 inventory 自行处理，不做 NBT 写入
    }

    @Override
    public void readFromNBT(@Nullable final NBTTagCompound data, final String name) {
        // 委托给原始 inventory 自行处理，不做 NBT 读取
    }

    @Override
    public int getSizeInventory() {
        return this.inventory.getSlots();
    }

    @Override
    public void markDirty() {
        // 无法通知 IItemHandler 保存，依赖外部机制
    }

    @Override
    public StorageName getStorageName() {
        return StorageName.NONE;
    }
}

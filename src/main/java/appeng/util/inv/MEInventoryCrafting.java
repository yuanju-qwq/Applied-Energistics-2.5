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

package appeng.util.inv;

import javax.annotation.Nullable;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * Extended version of {@link InventoryCrafting} that maintains a parallel
 * {@link GenericStack} array alongside the parent ItemStack slots.
 * <p>
 * This allows crafting execution to carry full generic stack info
 * (items/fluids/etc.) while staying compatible with vanilla
 * {@link InventoryCrafting}. Downstream {@code ICraftingMedium} implementations
 * can obtain the original {@link GenericStack} without relying on
 * {@code FluidDummyItem}.
 * <p>
 * For consumers unaware of this extension, behavior matches normal
 * {@link InventoryCrafting} exactly, with the parent slots containing properly
 * converted {@link ItemStack}s.
 */
public class MEInventoryCrafting extends InventoryCrafting {

    private final GenericStack[] genericStackList;

    public MEInventoryCrafting(Container container, int width, int height) {
        super(container, width, height);
        this.genericStackList = new GenericStack[width * height];
    }

    @Nullable
    public GenericStack getGenericStackInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.genericStackList.length) {
            return null;
        }
        return this.genericStackList[slotIndex];
    }

    public void setInventorySlotContents(int index, @Nullable GenericStack stack) {
        if (index < 0 || index >= this.genericStackList.length) {
            return;
        }

        this.genericStackList[index] = stack;

        ItemStack itemStack = ItemStack.EMPTY;
        if (stack != null) {
            itemStack = stack.what().asItemStackRepresentation();
            if (stack.what() instanceof appeng.api.stacks.AEItemKey) {
                itemStack.setCount((int) Math.min(stack.amount(), Integer.MAX_VALUE));
            }
        }
        super.setInventorySlotContents(index, itemStack);
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index >= 0 && index < this.genericStackList.length) {
            this.genericStackList[index] = null;
        }
        super.setInventorySlotContents(index, stack);
    }

    @Override
    public void clear() {
        super.clear();
        for (int i = 0; i < this.genericStackList.length; i++) {
            this.genericStackList[i] = null;
        }
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index >= 0 && index < this.genericStackList.length) {
            this.genericStackList[index] = null;
        }
        return super.removeStackFromSlot(index);
    }

    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack result = super.decrStackSize(index, count);
        if (index >= 0 && index < this.genericStackList.length) {
            ItemStack remaining = super.getStackInSlot(index);
            if (remaining.isEmpty()) {
                this.genericStackList[index] = null;
            }
        }
        return result;
    }
}

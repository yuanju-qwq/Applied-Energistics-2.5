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

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

/**
 * 继承 {@link InventoryCrafting} 的扩展版本，额外维护一个 {@link IAEStack} 并行数组。
 * <p>
 * 这样做的目的是让合成执行链路能够在保持与 MC 原生 {@link InventoryCrafting} 兼容的同时，
 * 携带完整的泛型栈信息（物品/流体等），使下游的 {@code ICraftingMedium} 实现
 * Can be cast to obtain the original {@link IAEStack}, avoiding dependency on {@code FluidDummyItem}.
 * <p>
 * 对于不感知此扩展的消费者，它的行为与普通 {@link InventoryCrafting} 完全一致，
 * 父类槽位中会存放经过适当转换后的 {@link ItemStack}。
 */
public class MEInventoryCrafting extends InventoryCrafting {

    /**
     * 与父类 InventoryCrafting 的槽位一一对应的泛型栈并行数组。
     * 可能包含 {@link IAEItemStack}、{@link appeng.api.storage.data.IAEFluidStack} 或其他类型。
     */
    private final IAEStack<?>[] aeStackList;

    public MEInventoryCrafting(Container container, int width, int height) {
        super(container, width, height);
        this.aeStackList = new IAEStack<?>[width * height];
    }

    /**
     * 获取指定槽位的原始 {@link IAEStack}。
     *
     * @param slotIndex 槽位索引
     * @return 该槽位的泛型栈，若槽位为空或索引越界则返回 {@code null}
     */
    @Nullable
    public IAEStack<?> getAEStackInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.aeStackList.length) {
            return null;
        }
        return this.aeStackList[slotIndex];
    }

    /**
     * 使用 {@link IAEStack} 设置指定槽位的内容。
     * <p>
     * 会同时更新并行数组和父类的 {@link ItemStack} 槽位。
     * 转换规则：
     * <ul>
     *   <li>{@link IAEItemStack} → {@link IAEItemStack#createItemStack()}</li>
     *   <li>其他类型（流体等） → {@link IAEStack#asItemStackRepresentation()}</li>
     *   <li>{@code null} → {@link ItemStack#EMPTY}</li>
     * </ul>
     *
     * @param index 槽位索引
     * @param stack 泛型栈，可为 {@code null} 表示清空槽位
     */
    public void setInventorySlotContents(int index, @Nullable IAEStack<?> stack) {
        if (index < 0 || index >= this.aeStackList.length) {
            return;
        }

        this.aeStackList[index] = stack;

        // 同步更新父类的 ItemStack 槽位
        ItemStack itemStack = ItemStack.EMPTY;
        if (stack != null) {
            if (stack instanceof IAEItemStack itemAEStack) {
                itemStack = itemAEStack.createItemStack();
            } else {
                // 流体等非物品类型：使用通用的 ItemStack 表示（如 FluidDummyItem）
                itemStack = stack.asItemStackRepresentation();
            }
        }
        super.setInventorySlotContents(index, itemStack);
    }

    /**
     * 覆写父类的 {@link ItemStack} 版本，确保并行数组与父类槽位保持同步。
     * <p>
     * 当通过原生 {@link ItemStack} 设置槽位时（例如 MC 原生合成逻辑），
     * 并行数组中对应的泛型栈将被清除为 {@code null}，因为无法从 ItemStack 反推泛型栈。
     */
    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (index >= 0 && index < this.aeStackList.length) {
            // 通过原生 ItemStack 设置时，清除并行数组中的泛型栈
            this.aeStackList[index] = null;
        }
        super.setInventorySlotContents(index, stack);
    }

    /**
     * 覆写清除方法，同步清除并行数组。
     */
    @Override
    public void clear() {
        super.clear();
        for (int i = 0; i < this.aeStackList.length; i++) {
            this.aeStackList[i] = null;
        }
    }

    /**
     * 覆写移除槽位内容方法，同步清除并行数组。
     */
    @Override
    public ItemStack removeStackFromSlot(int index) {
        if (index >= 0 && index < this.aeStackList.length) {
            this.aeStackList[index] = null;
        }
        return super.removeStackFromSlot(index);
    }

    /**
     * 覆写扣减方法，同步清除并行数组中对应的泛型栈。
     * <p>
     * 当扣减导致槽位清空时，并行数组也应清除。
     * 由于无法精确调整泛型栈的数量（扣减是 ItemStack 层面的操作），
     * 安全起见直接清除泛型栈引用。
     */
    @Override
    public ItemStack decrStackSize(int index, int count) {
        ItemStack result = super.decrStackSize(index, count);
        if (index >= 0 && index < this.aeStackList.length) {
            // 如果父类槽位已空，则清除并行数组
            ItemStack remaining = super.getStackInSlot(index);
            if (remaining.isEmpty()) {
                this.aeStackList[index] = null;
            }
        }
        return result;
    }
}

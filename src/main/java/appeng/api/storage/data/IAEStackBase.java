/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage.data;

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nonnull;

/**
 * 非递归泛型的基接口，用于容器/列表的泛型约束。
 * <p>
 * 包含所有与具体栈类型无关的通用方法。
 * {@link IAEStack} 继承此接口并添加递归泛型约束以提供类型安全的方法。
 * <p>
 * 容器类（如 {@link IItemList}、{@link appeng.api.storage.IMEInventory}）使用
 * {@code <T extends IAEStackBase>} 作为约束，使得 {@code IItemList<IAEStackBase>}
 * 可以合法存储任意类型的栈。
 */
public interface IAEStackBase {

    /**
     * number of items in the stack.
     *
     * @return basically ItemStack.stackSize
     */
    long getStackSize();

    /**
     * changes the number of items in the stack.
     *
     * @param stackSize , ItemStack.stackSize = N
     * @return this
     */
    IAEStackBase setStackSize(long stackSize);

    /**
     * Same as getStackSize, but for requestable items. ( LP )
     *
     * @return basically itemStack.stackSize but for requestable items.
     */
    long getCountRequestable();

    /**
     * Same as setStackSize, but for requestable items. ( LP )
     *
     * @return this
     */
    IAEStackBase setCountRequestable(long countRequestable);

    /**
     * true, if the item can be crafted.
     *
     * @return true, if it can be crafted.
     */
    boolean isCraftable();

    /**
     * change weather the item can be crafted.
     *
     * @param isCraftable can item be crafted
     * @return this
     */
    IAEStackBase setCraftable(boolean isCraftable);

    /**
     * clears, requestable, craftable, and stack sizes.
     *
     * @return this
     */
    IAEStackBase reset();

    /**
     * returns true, if the item can be crafted, requested, or extracted.
     *
     * @return isThisRecordMeaningful
     */
    boolean isMeaningful();

    /**
     * 判断给定对象是否与此栈代表同一种物品/流体（忽略数量）。
     * 支持 ItemStack、FluidStack 及 IAEStack 等。
     *
     * @param obj 比较对象
     * @return 如果同种则为 true
     */
    boolean isSameType(Object obj);

    /**
     * @return 此栈的本地化显示名称
     */
    String getDisplayName();

    /**
     * @return 每单位的数量（物品=1，流体=1000 mB）
     */
    int getAmountPerUnit();

    /**
     * 设置可请求的合成次数。默认实现不做任何事情。
     *
     * @return this
     */
    default long getCountRequestableCrafts() {
        return 0;
    }

    IAEStackBase setCountRequestableCrafts(long countRequestableCrafts);

    /**
     * Adds more to the stack size...
     *
     * @param i additional stack size
     */
    void incStackSize(long i);

    /**
     * removes some from the stack size.
     */
    void decStackSize(long i);

    /**
     * adds items to the requestable
     *
     * @param i increased amount of requested items
     */
    void incCountRequestable(long i);

    /**
     * removes items from the requestable
     *
     * @param i decreased amount of requested items
     */
    void decCountRequestable(long i);

    /**
     * write to a NBTTagCompound.
     *
     * @param i to be written data
     */
    void writeToNBT(NBTTagCompound i);

    /**
     * Compare stacks using precise logic.
     *
     * @param obj compared object
     * @return true if they are the same.
     */
    @Override
    boolean equals(Object obj);

    /**
     * Slower for disk saving, but smaller/more efficient for packets.
     *
     * @param data to be written data
     * @throws IOException on write error
     */
    void writeToPacket(ByteBuf data) throws IOException;

    /**
     * Clone the Item / Fluid Stack
     *
     * @return a new Stack, which is copied from the original.
     */
    IAEStackBase copy();

    /**
     * @return true if the stack is a {@link IAEItemStack}
     */
    boolean isItem();

    /**
     * @return true if the stack is a {@link IAEFluidStack}
     */
    boolean isFluid();

    /**
     * Returns itemstack for display and similar purposes. Always has a count of 1.
     *
     * @return itemstack
     */
    ItemStack asItemStackRepresentation();

    /**
     * @return 此栈对应的 {@link IAEStackType} 实例（非泛型版本）
     */
    IAEStackType<?> getStackTypeBase();

    /**
     * 将栈（含类型标识）写入 NBT。
     *
     * @param tag 写入目标
     */
    default void writeToNBTGeneric(@Nonnull NBTTagCompound tag) {
        tag.setString("StackType", this.getStackTypeBase().getId());
        this.writeToNBT(tag);
    }

    /**
     * @return 一个包含类型标识的 NBT
     */
    @Nonnull
    default NBTTagCompound toNBTGeneric() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("StackType", this.getStackTypeBase().getId());
        this.writeToNBT(tag);
        return tag;
    }
}

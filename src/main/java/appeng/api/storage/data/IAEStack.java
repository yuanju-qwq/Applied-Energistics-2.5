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
import javax.annotation.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;
import appeng.core.AELog;

public interface IAEStack<T extends IAEStack<T>> {

    /**
     * add two stacks together
     *
     * @param is added item
     */
    void add(T is);

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
     */
    T setStackSize(long stackSize);

    /**
     * Same as getStackSize, but for requestable items. ( LP )
     *
     * @return basically itemStack.stackSize but for requestable items.
     */
    long getCountRequestable();

    /**
     * Same as setStackSize, but for requestable items. ( LP )
     *
     * @return basically itemStack.stackSize = N but for setStackSize items.
     */
    T setCountRequestable(long countRequestable);

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
     */
    T setCraftable(boolean isCraftable);

    /**
     * clears, requestable, craftable, and stack sizes.
     */
    T reset();

    /**
     * returns true, if the item can be crafted, requested, or extracted.
     *
     * @return isThisRecordMeaningful
     */
    boolean isMeaningful();

    /**
     * 判断两个同类型的栈是否代表同种物品/流体（忽略数量）。
     *
     * @param other 另一个栈
     * @return 如果两个栈类型和标识相同则为 true
     */
    boolean isSameType(T other);

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
    default int getAmountPerUnit() {
        return getStackType().getAmountPerUnit();
    }

    /**
     * 设置可请求的合成次数。默认实现不做任何事情。
     */
    default T setCountRequestableCrafts(long countRequestableCrafts) {
        return (T) this;
    }

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
     * a IAEItemStack to another AEItemStack or a ItemStack.
     *
     * or
     *
     * IAEFluidStack, FluidStack
     *
     * @param obj compared object
     *
     * @return true if they are the same.
     */
    @Override
    boolean equals(Object obj);

    /**
     * Compare the same subtype of {@link IAEStack} with another using a fuzzy comparison.
     *
     * @param other The stack to compare.
     * @param mode  Which {@link FuzzyMode} should be used.
     *
     * @return true if two stacks are equal based on AE Fuzzy Comparison.
     */
    boolean fuzzyComparison(T other, FuzzyMode mode);

    /**
     * Slower for disk saving, but smaller/more efficient for packets.
     *
     * @param data to be written data
     *
     * @throws IOException
     */
    void writeToPacket(ByteBuf data) throws IOException;

    /**
     * Clone the Item / Fluid Stack
     *
     * @return a new Stack, which is copied from the original.
     */
    T copy();

    /**
     * create an empty stack.
     *
     * @return a new stack, which represents an empty copy of the original.
     */
    T empty();

    /**
     * @return true if the stack is a {@link IAEItemStack}
     */
    boolean isItem();

    /**
     * @return true if the stack is a {@link IAEFluidStack}
     */
    boolean isFluid();

    /**
     * @return ITEM or FLUID
     */
    IStorageChannel<T> getChannel();

    /**
     * Returns itemstack for display and similar purposes. Always has a count of 1.
     *
     * @return itemstack
     */
    ItemStack asItemStackRepresentation();

    /**
     * @return 此栈对应的 {@link IAEStackType} 实例
     */
    IAEStackType<T> getStackType();

    /**
     * 将栈（含类型标识）写入 NBT。
     *
     * @param tag 写入目标
     */
    default void writeToNBTGeneric(@Nonnull NBTTagCompound tag) {
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
    }

    /**
     * @return 一个包含类型标识的 NBT
     */
    @Nonnull
    default NBTTagCompound toNBTGeneric() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
        return tag;
    }

    /**
     * 从包含 StackType 键的 NBT 中反序列化栈。
     *
     * @param tag 包含 "StackType" 键的 NBT
     * @return 反序列化的栈，如果 tag 为空或类型未注册则返回 null
     */
    @Nullable
    static IAEStack<?> fromNBTGeneric(@Nonnull NBTTagCompound tag) {
        if (tag.isEmpty()) {
            return null;
        }

        String id = tag.getString("StackType");
        if (id.isEmpty()) {
            AELog.warn("Cannot deserialize generic stack from nbt %s because key 'StackType' is missing.", tag);
            return null;
        }

        IAEStackType<?> type = AEStackTypeRegistry.getType(id);
        if (type == null) {
            AELog.warn("Cannot deserialize generic stack from nbt %s because stack type '%s' is not registered.", tag, id);
            return null;
        }
        return type.loadStackFromNBT(tag);
    }

    /**
     * 将栈（含类型网络 ID）写入网络包。
     *
     * @param buffer 写入目标
     * @param stack  要写入的栈，可以为 null
     */
    static void writeToPacketGeneric(@Nonnull ByteBuf buffer, @Nullable IAEStack<?> stack) throws IOException {
        if (stack == null) {
            buffer.writeByte(AEStackTypeRegistry.NULL_NETWORK_ID);
        } else {
            buffer.writeByte(AEStackTypeRegistry.getNetworkId(stack.getStackType()));
            stack.writeToPacket(buffer);
        }
    }

    /**
     * 从网络包中读取一个带类型标识的栈。
     *
     * @param buffer 读取源
     * @return 反序列化的栈，或 null
     */
    @Nullable
    static IAEStack<?> fromPacketGeneric(@Nonnull ByteBuf buffer) throws IOException {
        final byte id = buffer.readByte();
        if (id == AEStackTypeRegistry.NULL_NETWORK_ID) {
            return null;
        }

        IAEStackType<?> type = AEStackTypeRegistry.getTypeFromNetworkId(id);
        if (type == null) {
            AELog.warn("Cannot deserialize generic stack from ByteBuf because stack type network id %d is not registered.", id);
            return null;
        }
        return type.loadStackFromPacket(buffer);
    }
}

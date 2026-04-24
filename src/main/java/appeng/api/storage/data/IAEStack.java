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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageChannel;
import appeng.core.AELog;

public interface IAEStack<T extends IAEStack<T>> extends IAEStackBase {

    /**
     * add two stacks together
     *
     * @param is added item
     */
    void add(T is);

    /**
     * 通配符安全的 add 操作。
     * <p>
     * 当调用方持有 {@code IAEStack<?>} 时使用此方法以避免 raw type cast。
     * 调用方负责确保传入的栈类型匹配。
     */
    @SuppressWarnings("unchecked")
    default void addGeneric(final IAEStack<?> is) {
        add((T) is);
    }

    @Override
    long getStackSize();

    @Override
    T setStackSize(long stackSize);

    @Override
    long getCountRequestable();

    @Override
    T setCountRequestable(long countRequestable);

    @Override
    boolean isCraftable();

    @Override
    T setCraftable(boolean isCraftable);

    @Override
    T reset();

    @Override
    boolean isMeaningful();

    /**
     * 判断两个同类型的栈是否代表同种物品/流体（忽略数量）。
     *
     * @param other 另一个栈
     * @return 如果两个栈类型和标识相同则为 true
     */
    boolean isSameType(T other);

    @Override
    boolean isSameType(Object obj);

    @Override
    String getDisplayName();

    @Override
    default int getAmountPerUnit() {
        return getStackType().getAmountPerUnit();
    }

    @Override
    @SuppressWarnings("unchecked")
    default T setCountRequestableCrafts(long countRequestableCrafts) {
        return (T) this;
    }

    @Override
    void incStackSize(long i);

    @Override
    void decStackSize(long i);

    @Override
    void incCountRequestable(long i);

    @Override
    void decCountRequestable(long i);

    @Override
    void writeToNBT(NBTTagCompound i);

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

    @Override
    void writeToPacket(ByteBuf data) throws IOException;

    @Override
    T copy();

    /**
     * create an empty stack.
     *
     * @return a new stack, which represents an empty copy of the original.
     */
    T empty();

    @Override
    boolean isItem();

    @Override
    boolean isFluid();

    /**
     * @return ITEM or FLUID
     */
    IStorageChannel<T> getChannel();

    @Override
    ItemStack asItemStackRepresentation();

    /**
     * @return 此栈对应的 {@link IAEStackType} 实例
     */
    IAEStackType<T> getStackType();

    @Override
    default IAEStackType<?> getStackTypeBase() {
        return getStackType();
    }

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

    // ==================== AEKey bridge methods ====================

    /**
     * Converts this stack's identity (without quantity) to an immutable {@link AEKey}.
     *
     * @return the corresponding AEKey, or null if conversion is not supported
     */
    @Nullable
    default AEKey toAEKey() {
        return null;
    }

    /**
     * Converts this stack to a {@link GenericStack} (AEKey + amount).
     *
     * @return the corresponding GenericStack, or null if {@link #toAEKey()} returns null
     */
    @Nullable
    default GenericStack toGenericStack() {
        AEKey key = toAEKey();
        if (key == null) {
            return null;
        }
        return new GenericStack(key, getStackSize());
    }
}

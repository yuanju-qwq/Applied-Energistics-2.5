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
     * Wildcard-safe add operation.
     * <p>
     * Use this method when the caller holds an {@code IAEStack<?>} to avoid raw type cast.
     * The caller is responsible for ensuring that the stack type matches.
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
     * Determine whether two stacks of the same type represent the same item/fluid (ignoring quantity).
     *
     * @param other the other stack
     * @return true if both stacks have the same type and identity
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
     * @return the {@link IAEStackType} instance corresponding to this stack
     */
    IAEStackType<T> getStackType();

    @Override
    default IAEStackType<?> getStackTypeBase() {
        return getStackType();
    }

    /**
     * Write the stack (including type identifier) to NBT.
     *
     * @param tag the target to write to
     */
    default void writeToNBTGeneric(@Nonnull NBTTagCompound tag) {
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
    }

    /**
     * @return an NBT compound containing the type identifier
     */
    @Nonnull
    default NBTTagCompound toNBTGeneric() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("StackType", this.getStackType().getId());
        this.writeToNBT(tag);
        return tag;
    }

    /**
     * Deserialize a stack from an NBT compound containing the StackType key.
     *
     * @param tag NBT compound containing the "StackType" key
     * @return the deserialized stack, or null if the tag is empty or the type is not registered
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
     * Write the stack (including type network ID) to a network packet.
     *
     * @param buffer the target to write to
     * @param stack  the stack to write, may be null
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
     * Read a typed stack from a network packet.
     *
     * @param buffer the source to read from
     * @return the deserialized stack, or null
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
     * <p>
     * This is a compatibility bridge for legacy {@link IAEStack}-based APIs.
     * New code should prefer accepting and returning {@link AEKey} directly instead of
     * going through mutable stack wrappers.
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

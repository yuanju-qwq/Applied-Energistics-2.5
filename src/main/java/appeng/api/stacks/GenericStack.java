/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
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

package appeng.api.stacks;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import com.github.bsideup.jabel.Desugar;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEStack;

/**
 * Represents some amount of some generic resource that AE can store or handle in crafting.
 * <p/>
 * This is the AEKey equivalent of the old {@code IAEStack} — an immutable key paired with a quantity.
 * Unlike {@code IAEStack}, the key ({@link #what}) is immutable and quantity is a plain {@code long}.
 */
@Desugar
public record GenericStack(AEKey what, long amount) {

    public GenericStack {
        Objects.requireNonNull(what, "what");
    }

    // ==================== Packet serialization ====================

    @Nullable
    public static GenericStack readBuffer(PacketBuffer buffer) throws IOException {
        if (!buffer.readBoolean()) {
            return null;
        }

        var what = AEKey.readKey(buffer);
        if (what == null) {
            return null;
        }

        return new GenericStack(what, buffer.readVarLong());
    }

    public static void writeBuffer(@Nullable GenericStack stack, PacketBuffer buffer) {
        if (stack == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            AEKey.writeKey(buffer, stack.what);
            buffer.writeVarLong(stack.amount);
        }
    }

    // ==================== NBT serialization ====================

    @Nullable
    public static GenericStack readTag(NBTTagCompound tag) {
        if (tag.isEmpty()) {
            return null;
        }
        var key = AEKey.fromTagGeneric(tag);
        if (key == null) {
            return null;
        }
        return new GenericStack(key, tag.getLong("#"));
    }

    public static NBTTagCompound writeTag(@Nullable GenericStack stack) {
        if (stack == null) {
            return new NBTTagCompound();
        }
        var tag = stack.what.toTagGeneric();
        tag.setLong("#", stack.amount);
        return tag;
    }

    // ==================== Convenience factory methods ====================

    /**
     * Converts a given ItemStack into a GenericStack.
     *
     * @return null if the stack is empty
     */
    @Nullable
    public static GenericStack fromItemStack(ItemStack stack) {
        var key = AEItemKey.of(stack);
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.getCount());
    }

    /**
     * Converts a given FluidStack into a GenericStack.
     *
     * @return null if the fluid stack is null or empty
     */
    @Nullable
    public static GenericStack fromFluidStack(FluidStack stack) {
        var key = AEFluidKey.of(stack);
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.amount);
    }

    /**
     * @return the amount of the given stack, or 0 if null
     */
    public static long getStackSizeOrZero(@Nullable GenericStack stack) {
        return stack == null ? 0 : stack.amount;
    }

    /**
     * Adds two stacks of the same key together.
     *
     * @throws IllegalArgumentException if the keys are not equal
     */
    public static GenericStack sum(GenericStack left, GenericStack right) {
        if (!left.what.equals(right.what)) {
            throw new IllegalArgumentException("Cannot sum generic stacks of " + left.what + " and " + right.what);
        }
        return new GenericStack(left.what, left.amount + right.amount);
    }

    // ==================== Legacy bridge ====================

    /**
     * Converts this GenericStack to a legacy {@link IAEStack}.
     *
     * @return the legacy stack, or null if conversion fails
     */
    @Nullable
    public IAEStack<?> toIAEStack() {
        return what.toIAEStack(amount);
    }

    /**
     * Creates a GenericStack from a legacy {@link IAEStack}.
     *
     * @return the GenericStack, or null if the input is null or conversion fails
     */
    @Nullable
    public static GenericStack fromIAEStack(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }
        var key = stack.toAEKey();
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.getStackSize());
    }
}

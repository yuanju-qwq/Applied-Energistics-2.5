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

package appeng.api.stacks;

import java.io.IOException;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.Api;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;

/**
 * Immutable identifier for a fluid stored in an ME network.
 * <p/>
 * Holds a {@link Fluid} and optional {@link NBTTagCompound} (defensive-copied on construction).
 * Does NOT hold amount or any mutable state.
 * <p/>
 * Hash code is cached at construction time for safe use as a HashMap key.
 */
public final class AEFluidKey extends AEKey {

    public static final int AMOUNT_BUCKET = 1000;
    public static final int AMOUNT_BLOCK = 1000;

    private final Fluid fluid;
    @Nullable
    private final NBTTagCompound tag;
    private final int hashCode;

    private AEFluidKey(Fluid fluid, @Nullable NBTTagCompound tag) {
        this.fluid = fluid;
        this.tag = tag;
        this.hashCode = Objects.hash(fluid, tag);
    }

    // ==================== Static factory methods ====================

    /**
     * Creates an AEFluidKey from a Fluid with no NBT.
     */
    public static AEFluidKey of(Fluid fluid) {
        return of(fluid, null);
    }

    /**
     * Creates an AEFluidKey from a Fluid and optional NBT.
     * The tag is defensively copied.
     */
    public static AEFluidKey of(Fluid fluid, @Nullable NBTTagCompound tag) {
        return new AEFluidKey(fluid, tag != null ? tag.copy() : null);
    }

    /**
     * Creates an AEFluidKey from a FluidStack.
     *
     * @return null if the fluidStack is null or has a null fluid
     */
    @Nullable
    public static AEFluidKey of(FluidStack fluidStack) {
        if (fluidStack == null || fluidStack.getFluid() == null) {
            return null;
        }
        return of(fluidStack.getFluid(), fluidStack.tag);
    }

    // ==================== Matching ====================

    /**
     * Checks if the given AEKey is an AEFluidKey that matches the given FluidStack.
     */
    public static boolean matches(AEKey what, FluidStack fluid) {
        return what instanceof AEFluidKey fluidKey && fluidKey.matches(fluid);
    }

    /** @return true if the given AEKey is an AEFluidKey */
    public static boolean is(AEKey what) {
        return what instanceof AEFluidKey;
    }

    /** @return true if the given GenericStack wraps an AEFluidKey */
    public static boolean is(@Nullable GenericStack stack) {
        return stack != null && stack.what() instanceof AEFluidKey;
    }

    /** @return an AEKeyFilter that matches only fluid keys */
    public static AEKeyFilter filter() {
        return AEFluidKey::is;
    }

    /**
     * Checks if this key matches the given FluidStack (same fluid and NBT).
     */
    public boolean matches(FluidStack variant) {
        return variant != null && variant.getFluid() != null && variant.amount > 0
                && fluid == variant.getFluid() && Objects.equals(tag, variant.tag);
    }

    // ==================== Conversion ====================

    /**
     * @return a new FluidStack with the given amount
     */
    public FluidStack toStack(int amount) {
        return new FluidStack(fluid, amount, tag != null ? tag.copy() : null);
    }

    // ==================== Accessors ====================

    public Fluid getFluid() {
        return fluid;
    }

    /**
     * @return the internal NBT tag. <strong>NEVER MODIFY THE RETURNED TAG.</strong>
     */
    @Nullable
    public NBTTagCompound getTag() {
        return tag;
    }

    /** @return a defensive copy of the NBT tag, or null */
    @Nullable
    public NBTTagCompound copyTag() {
        return tag != null ? tag.copy() : null;
    }

    public boolean hasTag() {
        return tag != null;
    }

    // ==================== AEKey implementation ====================

    @Override
    public AEKeyType getType() {
        return AEKeyType.fluids();
    }

    @Override
    public AEFluidKey dropSecondary() {
        return of(fluid, null);
    }

    @Override
    public Object getPrimaryKey() {
        return fluid;
    }

    @Override
    public String getModId() {
        return FluidRegistry.getModId(new FluidStack(fluid, 1));
    }

    @Override
    public String getDisplayName() {
        var fluidStack = new FluidStack(fluid, 1);
        return fluidStack.getLocalizedName();
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        ItemStack is = Api.INSTANCE.definitions().items().dummyFluidItem().maybeStack(1).orElse(ItemStack.EMPTY);
        if (!is.isEmpty()) {
            FluidDummyItem item = (FluidDummyItem) is.getItem();
            item.setFluidStack(is, new FluidStack(fluid, AMOUNT_BUCKET, tag != null ? tag.copy() : null));
            return is;
        }
        return ItemStack.EMPTY;
    }

    // ==================== Legacy bridge ====================

    @Override
    public IAEStack<?> toIAEStack(long amount) {
        var fluidStack = toStack((int) Math.min(amount, Integer.MAX_VALUE));
        IAEFluidStack aeStack = AEFluidStack.fromFluidStack(fluidStack);
        if (aeStack != null) {
            aeStack.setStackSize(amount);
        }
        return aeStack;
    }

    /**
     * Creates an AEFluidKey from a legacy IAEFluidStack.
     *
     * @return null if the input is null
     */
    @Nullable
    public static AEFluidKey fromIAEFluidStack(@Nullable IAEFluidStack stack) {
        if (stack == null) {
            return null;
        }
        return of(stack.getFluidStack());
    }

    // ==================== Serialization ====================

    /** Deserialize from NBT (without type marker). */
    @Nullable
    public static AEFluidKey fromTag(NBTTagCompound tag) {
        try {
            var fluidId = tag.getString("id");
            if (fluidId.isEmpty()) {
                throw new IllegalArgumentException("Missing fluid id in NBT");
            }

            var fluid = FluidRegistry.getFluid(fluidId);
            if (fluid == null) {
                throw new IllegalArgumentException("Unknown fluid id: " + fluidId);
            }

            NBTTagCompound extraTag = tag.hasKey("tag") ? tag.getCompoundTag("tag") : null;
            return of(fluid, extraTag);
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid fluid key from NBT: %s", tag, e);
            return null;
        }
    }

    @Override
    public NBTTagCompound toTag() {
        NBTTagCompound result = new NBTTagCompound();

        String fluidName = FluidRegistry.getFluidName(fluid);
        if (fluidName == null) {
            fluidName = fluid.getName();
            if (fluidName == null || fluidName.isEmpty()) {
                fluidName = "unknown";
            }
        }

        result.setString("id", fluidName);

        if (tag != null) {
            result.setTag("tag", tag.copy());
        }

        return result;
    }

    @Override
    public void writeToPacket(PacketBuffer data) {
        String fluidName = FluidRegistry.getFluidName(fluid);
        if (fluidName == null) {
            fluidName = "unknown";
        }
        data.writeString(fluidName);
        data.writeCompoundTag(tag);
    }

    /** Deserialize from network packet. */
    public static AEFluidKey fromPacket(PacketBuffer data) throws IOException {
        String fluidName = data.readString(32767);
        var tag = data.readCompoundTag();
        var fluid = FluidRegistry.getFluid(fluidName);
        if (fluid == null) {
            throw new IllegalArgumentException("Unknown fluid: " + fluidName);
        }

        return new AEFluidKey(fluid, tag);
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AEFluidKey aeFluidKey = (AEFluidKey) o;
        return hashCode == aeFluidKey.hashCode && fluid == aeFluidKey.fluid && Objects.equals(tag, aeFluidKey.tag);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        String fluidName = FluidRegistry.getFluidName(fluid);
        if (fluidName == null) {
            fluidName = "unknown";
        }
        return tag != null ? fluidName + " " + tag : fluidName;
    }
}

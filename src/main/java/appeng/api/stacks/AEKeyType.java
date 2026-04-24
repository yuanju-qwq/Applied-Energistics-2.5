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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Defines the properties of a specific subclass of {@link AEKey}.
 * <p/>
 * Each AEKeyType wraps an existing {@link IAEStackType} to reuse its metadata
 * (display name, unit info, button textures, container interactions, etc.)
 * while providing a non-generic API for the AEKey system.
 * <p/>
 * For items there is {@link AEItemKeyType}, for fluids there is {@link AEFluidKeyType}.
 */
public abstract class AEKeyType {

    private final IAEStackType<?> legacyType;
    private final Class<? extends AEKey> keyClass;
    private final AEKeyFilter filter;

    protected AEKeyType(@Nonnull IAEStackType<?> legacyType, @Nonnull Class<? extends AEKey> keyClass) {
        this.legacyType = legacyType;
        this.keyClass = keyClass;
        this.filter = what -> what.getType() == this;
    }

    // ==================== Static accessor shortcuts ====================

    /** @return AE2's key type for items. */
    public static AEKeyType items() {
        return AEItemKeyType.INSTANCE;
    }

    /** @return AE2's key type for fluids. */
    public static AEKeyType fluids() {
        return AEFluidKeyType.INSTANCE;
    }

    // ==================== Static lookup methods ====================

    /**
     * Look up an AEKeyType by its string id (e.g. "item", "fluid").
     *
     * @return the matching AEKeyType, or null if not found
     */
    @Nullable
    public static AEKeyType fromId(@Nonnull String id) {
        var legacy = AEStackTypeRegistry.getType(id);
        if (legacy == null) {
            return null;
        }
        return fromLegacyType(legacy);
    }

    /**
     * Look up an AEKeyType by its network raw id.
     *
     * @return the matching AEKeyType, or null if not found
     */
    @Nullable
    public static AEKeyType fromRawId(int id) {
        if (id < 0 || id > Byte.MAX_VALUE) {
            return null;
        }
        var legacy = AEStackTypeRegistry.getTypeFromNetworkId((byte) id);
        if (legacy == null) {
            return null;
        }
        return fromLegacyType(legacy);
    }

    /**
     * Resolves the AEKeyType for a given legacy IAEStackType.
     * This mapping is determined by the string id.
     */
    @Nullable
    public static AEKeyType fromLegacyType(@Nonnull IAEStackType<?> legacyType) {
        if (legacyType == AEItemKeyType.INSTANCE.getLegacyType()) {
            return AEItemKeyType.INSTANCE;
        }
        if (legacyType == AEFluidKeyType.INSTANCE.getLegacyType()) {
            return AEFluidKeyType.INSTANCE;
        }
        return null;
    }

    // ==================== Identity & metadata (delegated to IAEStackType) ====================

    /** @return the unique string id for this type (e.g. "item", "fluid") */
    public final String getId() {
        return legacyType.getId();
    }

    /** @return the localized display name for this type */
    public final String getDisplayName() {
        return legacyType.getDisplayName();
    }

    /** @return the unit display string (e.g. "" for items, "mB" for fluids) */
    @Nullable
    public String getUnitSymbol() {
        var unit = legacyType.getDisplayUnit();
        return unit.isEmpty() ? null : unit;
    }

    /** @return the amount per unit (e.g. 1 for items, 1000 for fluids) */
    public int getAmountPerUnit() {
        return legacyType.getAmountPerUnit();
    }

    /**
     * How much of this key will be transferred as part of a transfer operation.
     * Used to balance item vs. fluid transfers.
     * <p/>
     * E.g. IO Ports transfer 1000 mB to match items transferring one bucket per operation.
     */
    public int getAmountPerOperation() {
        return legacyType.transferFactor();
    }

    /**
     * The amount of this key type that can be stored per byte used in a storage cell.
     * Standard value: 8 for items, 8000 for fluids.
     */
    public int getAmountPerByte() {
        return legacyType.getUnitsPerByte();
    }

    /** @return the color used in chat/tooltip for this type */
    public final TextFormatting getColorDefinition() {
        return legacyType.getColorDefinition();
    }

    // ==================== GUI button resources (delegated) ====================

    /** @return the texture resource location for the GUI button, or null */
    @Nullable
    public final ResourceLocation getButtonTexture() {
        return legacyType.getButtonTexture();
    }

    /** @return button icon U coordinate in texture (0~256) */
    public final int getButtonIconU() {
        return legacyType.getButtonIconU();
    }

    /** @return button icon V coordinate in texture (0~256) */
    public final int getButtonIconV() {
        return legacyType.getButtonIconV();
    }

    // ==================== Container interaction (delegated) ====================

    /**
     * Check if the given ItemStack is a container for this type (e.g. a bucket for fluids).
     */
    public final boolean isContainerItemForType(@Nullable ItemStack container) {
        return legacyType.isContainerItemForType(container);
    }

    /**
     * Drain a resource from a container item.
     *
     * @see IAEStackType#drainFromContainer(ItemStack, long, boolean)
     */
    @Nonnull
    public final ContainerInteractionResult<? extends IAEStack<?>> drainFromContainer(
            @Nonnull ItemStack container, long maxAmount, boolean simulate) {
        return legacyType.drainFromContainer(container, maxAmount, simulate);
    }

    /**
     * Fill a resource into a container item.
     *
     * @see IAEStackType#fillToContainer(ItemStack, IAEStack, boolean)
     */
    @Nonnull
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public final ContainerInteractionResult<? extends IAEStack<?>> fillToContainer(
            @Nonnull ItemStack container, @Nonnull IAEStack<?> stack, boolean simulate) {
        return ((IAEStackType) legacyType).fillToContainer(container, stack, simulate);
    }

    // ==================== Network id ====================

    /**
     * @return the raw network id assigned to this type via {@link AEStackTypeRegistry}.
     * @throws IllegalStateException if network ids have not been initialized
     */
    public final byte getRawId() {
        return AEStackTypeRegistry.getNetworkId(legacyType);
    }

    // ==================== Key class & filtering ====================

    /** @return the concrete {@link AEKey} subclass for this type */
    public final Class<? extends AEKey> getKeyClass() {
        return keyClass;
    }

    /**
     * Attempts to cast the given key to this type's key class.
     *
     * @return the casted key, or null if the key doesn't belong to this type
     */
    @Nullable
    public final AEKey tryCast(@Nullable AEKey key) {
        return key != null && keyClass.isInstance(key) ? keyClass.cast(key) : null;
    }

    /** @return true if the given key belongs to this type */
    public final boolean contains(@Nullable AEKey key) {
        return key != null && keyClass.isInstance(key);
    }

    /** @return a filter that matches all keys of this type */
    public final AEKeyFilter filter() {
        return filter;
    }

    // ==================== Amount formatting ====================

    /**
     * Formats the given amount for display in the UI.
     * Subclasses can override this for type-specific formatting (e.g. mB for fluids).
     *
     * @param amount the amount to format
     * @param format the desired format style
     * @return the formatted string
     */
    public String formatAmount(long amount, AmountFormat format) {
        return Long.toString(amount);
    }

    // ==================== Fuzzy search support ====================

    /**
     * @return true if this key type supports range-based fuzzy search
     *         (e.g. item damage percentage ranges)
     */
    public boolean supportsFuzzyRangeSearch() {
        return false;
    }

    // ==================== Abstract methods (subclass must implement) ====================

    /**
     * Loads a key of this type from an NBT tag.
     *
     * @param tag the tag to read from (does NOT include the "#c" type marker)
     * @return the deserialized key, or null if the tag is invalid
     */
    @Nullable
    public abstract AEKey loadKeyFromTag(@Nonnull NBTTagCompound tag);

    /**
     * Reads a key of this type from a network packet buffer.
     *
     * @param input the buffer to read from (type id has already been consumed)
     * @return the deserialized key, or null if reading fails
     */
    @Nullable
    public abstract AEKey readFromPacket(@Nonnull PacketBuffer input) throws IOException;

    // ==================== Legacy bridge ====================

    /**
     * @return the underlying {@link IAEStackType} instance this AEKeyType delegates to
     */
    @Nonnull
    public final IAEStackType<?> getLegacyType() {
        return legacyType;
    }

    @Override
    public String toString() {
        return getId();
    }
}

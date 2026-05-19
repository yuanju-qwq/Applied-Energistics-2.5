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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
import appeng.util.ReadableNumberConverter;

/**
 * Defines the properties of a specific subclass of {@link AEKey}.
 * <p/>
 * Each registered {@link AEKeyType} is self-registered via its constructor
 * and provides type-specific identity, metadata, GUI resources, and
 * formatting for the AEKey system.
 * <p/>
 * For items there is {@link AEItemKeyType}, for fluids there is {@link AEFluidKeyType}.
 */
public abstract class AEKeyType {

    // ==================== Static registry ====================

    private static final Map<String, AEKeyType> REGISTRY = new LinkedHashMap<>();

    /**
     * @return all registered AEKeyTypes in insertion order
     */
    @Nonnull
    public static Collection<AEKeyType> getAllTypes() {
        return REGISTRY.values();
    }

    /**
     * @return sorted list of registered AEKeyTypes: ITEM and FLUID first, then by id alphabetically
     */
    @Nonnull
    public static List<AEKeyType> getSortedTypes() {
        List<AEKeyType> result = new ArrayList<>();
        AEKeyType itemType = REGISTRY.get("item");
        AEKeyType fluidType = REGISTRY.get("fluid");
        List<AEKeyType> others = new ArrayList<>();

        for (AEKeyType type : REGISTRY.values()) {
            if (type == itemType || type == fluidType) {
                continue;
            }
            others.add(type);
        }
        others.sort(Comparator.comparing(AEKeyType::getId));

        if (itemType != null) {
            result.add(itemType);
        }
        if (fluidType != null) {
            result.add(fluidType);
        }
        result.addAll(others);
        return result;
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
        return REGISTRY.get(id);
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
        return fromId(legacyType.getId());
    }

    // ==================== Instance fields ====================

    private final String id;
    private final Class<? extends AEKey> keyClass;
    private final String description;
    private final AEKeyFilter filter;

    // ==================== Constructor ====================

    /**
     * Creates and registers a new AEKeyType.
     *
     * @param id          unique string id (e.g. "item", "fluid")
     * @param keyClass    the concrete AEKey subclass for this type
     * @param description localized display name
     */
    protected AEKeyType(@Nonnull String id, @Nonnull Class<? extends AEKey> keyClass, @Nonnull String description) {
        this.id = id;
        this.keyClass = keyClass;
        this.description = description;
        this.filter = what -> what.getType() == this;
        REGISTRY.put(id, this);
    }

    // ==================== Identity & metadata ====================

    /** @return the unique string id for this type (e.g. "item", "fluid") */
    public final String getId() {
        return id;
    }

    /** @return the localized display name for this type */
    public String getDescription() {
        return description;
    }

    /** @return the localized display name for this type */
    public String getDisplayName() {
        return getDescription();
    }

    /** @return the unit display string (e.g. null for items, "mB" for fluids) */
    @Nullable
    public String getUnitSymbol() {
        return null;
    }

    /** @return the amount per unit (e.g. 1 for items, 1000 for fluids) */
    public int getAmountPerUnit() {
        return 1;
    }

    /**
     * How much of this key will be transferred as part of a transfer operation.
     * Used to balance item vs. fluid transfers.
     * <p/>
     * E.g. IO Ports transfer 1000 mB to match items transferring one bucket per operation.
     */
    public int getAmountPerOperation() {
        return 1;
    }

    /**
     * The amount of this key type that can be stored per byte used in a storage cell.
     * Standard value: 8 for items, 8000 for fluids.
     */
    public int getAmountPerByte() {
        return 8;
    }

    /** @return the color used in chat/tooltip for this type */
    public abstract TextFormatting getColorDefinition();

    // ==================== GUI button resources ====================

    /** @return the texture resource location for the GUI button, or null */
    @Nullable
    public abstract ResourceLocation getButtonTexture();

    /** @return button icon U coordinate in texture (0~256) */
    public abstract int getButtonIconU();

    /** @return button icon V coordinate in texture (0~256) */
    public abstract int getButtonIconV();

    // ==================== Container interaction ====================

    /**
     * Check if the given ItemStack is a container for this type (e.g. a bucket for fluids).
     */
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        return false;
    }

    /**
     * Drain a resource from a container item.
     */
    @Nonnull
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ContainerInteractionResult<? extends IAEStack<?>> drainFromContainer(
            @Nonnull ItemStack container, long maxAmount, boolean simulate) {
        return ContainerInteractionResult.empty();
    }

    /**
     * Fill a resource into a container item.
     */
    @Nonnull
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ContainerInteractionResult<? extends IAEStack<?>> fillToContainer(
            @Nonnull ItemStack container, @Nonnull IAEStack<?> stack, boolean simulate) {
        return ContainerInteractionResult.empty();
    }

    // ==================== Network id ====================

    /**
     * @return the raw network id assigned to this type via {@link AEStackTypeRegistry}.
     * @throws IllegalStateException if network ids have not been initialized
     */
    public byte getRawId() {
        var legacy = AEStackTypeRegistry.getType(id);
        if (legacy == null) {
            throw new IllegalStateException("No legacy type registered for id: " + id);
        }
        return AEStackTypeRegistry.getNetworkId(legacy);
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
     * <p>
     * Subclasses should override this for type-specific formatting
     * (e.g. items use SI suffixes, fluids use mB→Bucket conversion).
     * The default implementation uses {@link ReadableNumberConverter}.
     *
     * @param amount the amount to format
     * @param format the desired format style
     * @return the formatted string
     */
    public String formatAmount(long amount, AmountFormat format) {
        switch (format) {
            case FULL:
                return NumberFormat.getNumberInstance(Locale.US).format(amount);
            case PREVIEW_LARGE_FONT:
                return ReadableNumberConverter.INSTANCE.toSlimReadableForm(amount);
            case PREVIEW_REGULAR:
            default:
                return ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
        }
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

    // ==================== Object overrides ====================

    @Override
    public String toString() {
        return id;
    }
}
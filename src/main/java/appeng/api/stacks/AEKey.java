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

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;

/**
 * Immutable identifier for something that "stacks" within an ME inventory.
 * <p/>
 * For example, for items this is the combination of an {@link net.minecraft.item.Item} and optional
 * {@link NBTTagCompound}. A key is (optionally) split into a primary and secondary component:
 * <ul>
 * <li>Fuzzy cards allow setting filters for the primary component of a key, i.e. for an
 * {@link net.minecraft.item.Item}, while disregarding the compound tag.</li>
 * <li>When indexing resources, indexing by the primary key alone offers a good trade-off
 * between memory usage and lookup speed.</li>
 * </ul>
 * <p/>
 * Unlike {@link appeng.api.storage.data.IAEStack}, AEKey does <b>not</b> contain mutable state
 * such as stack size, craftable flag, or requestable count. Quantities are tracked externally
 * (e.g. in {@link GenericStack} or {@code KeyCounter}).
 */
public abstract class AEKey {

    // ==================== Static serialization methods ====================

    /**
     * Deserializes a key from a generic NBT tag that includes type information.
     * The tag must contain a {@code "#c"} key with the {@link AEKeyType} id.
     *
     * @param tag the compound tag to read from
     * @return the deserialized key, or null if the tag is malformed or the type is unknown
     */
    @Nullable
    public static AEKey fromTagGeneric(NBTTagCompound tag) {
        var channelId = tag.getString("#c");
        if (channelId.isEmpty()) {
            AELog.warn("Cannot deserialize generic key from %s because key '#c' is missing.", tag);
            return null;
        }

        AEKeyType channel = AEKeyType.fromId(channelId);
        if (channel == null) {
            AELog.warn("Cannot deserialize generic key from %s because channel '%s' is missing.", tag, channelId);
            return null;
        }

        return channel.loadKeyFromTag(tag);
    }

    /**
     * Writes a generic, nullable key to the given buffer.
     */
    public static void writeOptionalKey(PacketBuffer buffer, @Nullable AEKey key) {
        buffer.writeBoolean(key != null);
        if (key != null) {
            writeKey(buffer, key);
        }
    }

    /**
     * Writes a non-null key to the given buffer, prefixed with its type's raw network id.
     */
    public static void writeKey(PacketBuffer buffer, AEKey key) {
        buffer.writeVarInt(key.getType().getRawId());
        key.writeToPacket(buffer);
    }

    /**
     * Tries reading a key written using {@link #writeOptionalKey}.
     *
     * @return the deserialized key, or null if the written value was null
     */
    @Nullable
    public static AEKey readOptionalKey(PacketBuffer buffer) throws IOException {
        if (!buffer.readBoolean()) {
            return null;
        }
        return readKey(buffer);
    }

    /**
     * Reads a key written using {@link #writeKey}.
     *
     * @return the deserialized key, or null if the type id is unknown
     */
    @Nullable
    public static AEKey readKey(PacketBuffer buffer) throws IOException {
        var id = buffer.readVarInt();
        var type = AEKeyType.fromRawId(id);
        if (type == null) {
            AELog.error("Received unknown key space id %d", id);
            return null;
        }
        return type.readFromPacket(buffer);
    }

    // ==================== Instance methods ====================

    /**
     * Same as {@link #toTag()}, but includes type information so that {@link #fromTagGeneric(NBTTagCompound)}
     * can restore this particular type of key without knowing the actual type beforehand.
     */
    public final NBTTagCompound toTagGeneric() {
        var tag = toTag();
        tag.setString("#c", getType().getId());
        return tag;
    }

    /**
     * How much of this key is in one unit (i.e. one bucket).
     * Delegated to {@link AEKeyType#getAmountPerUnit()}.
     */
    public final int getAmountPerUnit() {
        return getType().getAmountPerUnit();
    }

    /**
     * @return the unit symbol for display (e.g. "mB" for fluids), or null if none.
     */
    @Nullable
    public final String getUnitSymbol() {
        return getType().getUnitSymbol();
    }

    /**
     * @return the amount transferred per IO operation
     * @see AEKeyType#getAmountPerOperation()
     */
    public final int getAmountPerOperation() {
        return getType().getAmountPerOperation();
    }

    /**
     * @return the amount stored per byte of cell storage
     * @see AEKeyType#getAmountPerByte()
     */
    public final int getAmountPerByte() {
        return getType().getAmountPerByte();
    }

    /**
     * @return true if this type supports range-based fuzzy search (e.g. item damage)
     * @see AEKeyType#supportsFuzzyRangeSearch()
     */
    public final boolean supportsFuzzyRangeSearch() {
        return getType().supportsFuzzyRangeSearch();
    }

    // ==================== Abstract methods (subclass must implement) ====================

    /**
     * @return the key type descriptor for this key
     */
    public abstract AEKeyType getType();

    /**
     * @return this key with the secondary component (e.g. NBT) removed,
     *         or this key itself if it has no secondary component
     */
    public abstract AEKey dropSecondary();

    /**
     * Serializes this key to an NBT tag.
     * <p/>
     * Serialized keys MUST NOT contain keys that start with {@code #},
     * because this prefix is reserved for additional metadata (e.g. {@code #c} for channel id).
     */
    public abstract NBTTagCompound toTag();

    /**
     * @return the primary key object used for indexing (e.g. {@link net.minecraft.item.Item} for items)
     */
    public abstract Object getPrimaryKey();

    /**
     * @return the mod id that owns this key's primary object
     */
    public abstract String getModId();

    /**
     * @return the localized display name for this key
     */
    public abstract String getDisplayName();

    /**
     * Writes this key's data to a network packet buffer.
     * The type id is NOT written by this method; use {@link #writeKey(PacketBuffer, AEKey)} for that.
     */
    public abstract void writeToPacket(PacketBuffer data);

    /**
     * Returns an {@link ItemStack} representation of this key for display or filter purposes.
     * <p/>
     * For item keys this returns the actual item stack (count=1).
     * For other types, subclasses should provide an appropriate representation.
     */
    public abstract ItemStack asItemStackRepresentation();

    // ==================== Legacy bridge ====================

    /**
     * Creates a legacy {@link IAEStack} from this key with the given amount.
     * Used during the migration period for interop with old APIs.
     *
     * @param amount the stack size
     * @return a new IAEStack instance
     */
    public abstract IAEStack<?> toIAEStack(long amount);

    // ==================== Fuzzy search ====================

    /**
     * @return if {@link #getFuzzySearchMaxValue()} is greater than 0, this is the value in the range
     *         [0, getFuzzySearchMaxValue()] used to index keys by. Used by fuzzy mode search with percentage ranges.
     */
    public int getFuzzySearchValue() {
        return 0;
    }

    /**
     * @return the upper bound for values returned by {@link #getFuzzySearchValue()}.
     *         If equal to 0, no fuzzy range-search is possible for this type of key.
     */
    public int getFuzzySearchMaxValue() {
        return 0;
    }

    /**
     * Tests if this and the given AE key are in the same fuzzy partition given a specific fuzzy matching mode.
     *
     * @param other     the other key to compare with
     * @param fuzzyMode the fuzzy mode to apply
     * @return true if the two keys are fuzzy-equal under the given mode
     */
    public final boolean fuzzyEquals(AEKey other, FuzzyMode fuzzyMode) {
        if (other == null || other.getClass() != getClass()) {
            return false;
        }

        if (getPrimaryKey() != other.getPrimaryKey()) {
            return false;
        }

        if (!supportsFuzzyRangeSearch()) {
            return true;
        } else if (fuzzyMode == FuzzyMode.IGNORE_ALL) {
            return true;
        } else if (fuzzyMode == FuzzyMode.PERCENT_99) {
            return getFuzzySearchValue() > 0 == other.getFuzzySearchValue() > 0;
        } else {
            final float percentA = (float) getFuzzySearchValue() / getFuzzySearchMaxValue();
            final float percentB = (float) other.getFuzzySearchValue() / other.getFuzzySearchMaxValue();
            return percentA > fuzzyMode.breakPoint == percentB > fuzzyMode.breakPoint;
        }
    }
}

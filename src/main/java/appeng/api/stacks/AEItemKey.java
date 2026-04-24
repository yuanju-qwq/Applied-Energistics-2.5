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

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.item.AEItemStack;

/**
 * Immutable identifier for an item stored in an ME network.
 * <p/>
 * Holds an {@link Item} and optional {@link NBTTagCompound} (defensive-copied on construction).
 * Does NOT hold stack size or any mutable state.
 * <p/>
 * Hash code is cached at construction time for safe use as a HashMap key.
 */
public final class AEItemKey extends AEKey {

    private final Item item;
    @Nullable
    private final NBTTagCompound tag;
    private final int hashCode;

    private AEItemKey(Item item, @Nullable NBTTagCompound tag) {
        this.item = item;
        this.tag = tag;
        this.hashCode = Objects.hash(item, tag);
    }

    // ==================== Static factory methods ====================

    /**
     * Creates an AEItemKey from an ItemStack.
     *
     * @return null if the stack is empty
     */
    @Nullable
    public static AEItemKey of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return of(stack.getItem(), stack.getTagCompound());
    }

    /**
     * Creates an AEItemKey from an Item with no NBT.
     */
    public static AEItemKey of(Item item) {
        return of(item, null);
    }

    /**
     * Creates an AEItemKey from an Item and optional NBT.
     * The tag is defensively copied.
     */
    public static AEItemKey of(Item item, @Nullable NBTTagCompound tag) {
        return new AEItemKey(item, tag != null ? tag.copy() : null);
    }

    // ==================== Matching ====================

    /**
     * Checks if the given AEKey is an AEItemKey that matches the given ItemStack.
     */
    public static boolean matches(AEKey what, ItemStack itemStack) {
        return what instanceof AEItemKey itemKey && itemKey.matches(itemStack);
    }

    /** @return true if the given AEKey is an AEItemKey */
    public static boolean is(AEKey what) {
        return what instanceof AEItemKey;
    }

    /** @return an AEKeyFilter that matches only item keys */
    public static AEKeyFilter filter() {
        return AEItemKey::is;
    }

    /**
     * Checks if this key matches the given ItemStack (same item and NBT).
     */
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == item && Objects.equals(stack.getTagCompound(), tag);
    }

    // ==================== Conversion ====================

    /**
     * @return a new ItemStack with count=1
     */
    public ItemStack toStack() {
        return toStack(1);
    }

    /**
     * @return a new ItemStack with the given count, or EMPTY if count <= 0
     */
    public ItemStack toStack(int count) {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        var result = new ItemStack(item);
        result.setTagCompound(copyTag());
        result.setCount(count);
        return result;
    }

    // ==================== Accessors ====================

    public Item getItem() {
        return item;
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

    /** @return true if the item represented by this key is damaged */
    public boolean isDamaged() {
        return tag != null && tag.getInteger("Damage") > 0;
    }

    // ==================== AEKey implementation ====================

    @Override
    public AEKeyType getType() {
        return AEKeyType.items();
    }

    @Override
    public AEItemKey dropSecondary() {
        return of(item, null);
    }

    @Override
    public Object getPrimaryKey() {
        return item;
    }

    @Override
    public String getModId() {
        ResourceLocation regName = item.getRegistryName();
        return regName != null ? regName.getNamespace() : "unknown";
    }

    @Override
    public String getDisplayName() {
        return toStack().getDisplayName();
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        return toStack();
    }

    // ==================== Legacy bridge ====================

    @Override
    public IAEStack<?> toIAEStack(long amount) {
        var stack = toStack();
        IAEItemStack aeStack = AEItemStack.fromItemStack(stack);
        if (aeStack != null) {
            aeStack.setStackSize(amount);
        }
        return aeStack;
    }

    /**
     * Creates an AEItemKey from a legacy IAEItemStack.
     *
     * @return null if the input is null
     */
    @Nullable
    public static AEItemKey fromIAEItemStack(@Nullable IAEItemStack stack) {
        if (stack == null) {
            return null;
        }
        return of(stack.getDefinition());
    }

    // ==================== Fuzzy search ====================

    @Override
    public int getFuzzySearchValue() {
        return this.tag == null ? 0 : this.tag.getInteger("Damage");
    }

    @Override
    public int getFuzzySearchMaxValue() {
        return item.getMaxDamage();
    }

    // ==================== Serialization ====================

    /** Deserialize from NBT (without type marker). */
    @Nullable
    public static AEItemKey fromTag(NBTTagCompound tag) {
        try {
            String itemId = tag.getString("id");
            ResourceLocation resourceLocation = new ResourceLocation(itemId);
            Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);

            if (item == null) {
                AELog.debug("Unknown item id in NBT: %s", itemId);
                return null;
            }

            NBTTagCompound extraTag = tag.hasKey("tag") ? tag.getCompoundTag("tag") : null;
            return of(item, extraTag);
        } catch (Exception e) {
            AELog.debug("Tried to load an invalid item key from NBT: %s", tag, e);
            return null;
        }
    }

    @Override
    public NBTTagCompound toTag() {
        NBTTagCompound result = new NBTTagCompound();
        ResourceLocation regName = ForgeRegistries.ITEMS.getKey(item);
        result.setString("id", regName != null ? regName.toString() : "minecraft:air");

        if (tag != null) {
            result.setTag("tag", tag.copy());
        }

        return result;
    }

    @Override
    public void writeToPacket(PacketBuffer data) {
        data.writeVarInt(Item.getIdFromItem(item));
        NBTTagCompound compoundTag = null;
        if (item.isDamageable() || item.getShareTag()) {
            compoundTag = tag;
        }
        data.writeCompoundTag(compoundTag);
    }

    /** Deserialize from network packet. */
    public static AEItemKey fromPacket(PacketBuffer data) throws IOException {
        int i = data.readVarInt();
        Item item = Item.getItemById(i);
        NBTTagCompound tag = data.readCompoundTag();
        return new AEItemKey(item, tag);
    }

    // ==================== equals / hashCode ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AEItemKey aeItemKey = (AEItemKey) o;
        return hashCode == aeItemKey.hashCode && item == aeItemKey.item && Objects.equals(tag, aeItemKey.tag);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        var regName = item.getRegistryName();
        var name = regName != null ? regName.toString() : "unknown";
        return tag != null ? name + " " + tag : name;
    }
}

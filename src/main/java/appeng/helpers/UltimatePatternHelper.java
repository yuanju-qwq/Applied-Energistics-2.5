/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.helpers;

import static appeng.helpers.PatternHelper.convertToCondensedAEList;
import static appeng.helpers.PatternHelper.convertToCondensedList;
import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.stackConvert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;

/**
 * Unified processing recipe (non-crafting-table recipe) parser with native support for generic stacks (items + fluids, etc.).
 * <p>
 * Replaces {@link FluidPatternHelper} and {@link SpecialPatternHelper},
 * reads stack data from NBT via {@link appeng.util.Platform#readStackNBT},
 * Automatically handles both new format (with "StackType" key) and legacy format (plain ItemStack / FluidDummyItem) migration.
 * <p>
 * Design reference from the class of the same name in Applied-Energistics-2-Unofficial.
 */
public class UltimatePatternHelper implements ICraftingPatternDetails, Comparable<UltimatePatternHelper> {

    private final ItemStack patternItem;
    private final IAEItemStack pattern;
    private final boolean canSubstitute;
    private final boolean canBeSubstitute;
    private int priority = 0;

    // Legacy item-type arrays (backward compatible with old interface)
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;

    // Generic stack arrays (main entry, supports items + fluids and all types)
    private final IAEStack<?>[] aeInputs;
    private final IAEStack<?>[] aeOutputs;
    private final IAEStack<?>[] condensedAEInputs;
    private final IAEStack<?>[] condensedAEOutputs;

    // Input-only recipe support (inputOnly / tunnel pattern)
    private final boolean inputOnly;
    private final UUID inputOnlyUuid;

    /**
     * Construct from an {@link ItemStack} that encodes a processing recipe.
     *
     * @param is pattern item encoded with a processing recipe (with NBT)
     * @throws IllegalArgumentException if NBT is missing or marked invalid
     * @throws IllegalStateException    if there are no valid inputs or no valid outputs (non-inputOnly)
     */
    public UltimatePatternHelper(final ItemStack is) {
        final NBTTagCompound encodedValue = is.getTagCompound();

        if (encodedValue == null || encodedValue.getBoolean("InvalidPattern")) {
            throw new IllegalArgumentException("No pattern here!");
        }

        this.canSubstitute = encodedValue.getBoolean("substitute");
        this.canBeSubstitute = encodedValue.getBoolean("beSubstitute");
        this.patternItem = is;
        this.inputOnly = encodedValue.getBoolean("tunnel");
        this.inputOnlyUuid = readInputOnlyUuid(encodedValue, this.inputOnly);

        // Pattern item excludes "author" tag for equals/hashCode comparison
        if (encodedValue.hasKey("author")) {
            final ItemStack forComparison = this.patternItem.copy();
            forComparison.getTagCompound().removeTag("author");
            this.pattern = AEItemStack.fromItemStack(forComparison);
        } else {
            this.pattern = AEItemStack.fromItemStack(is);
        }

        final NBTTagList inTag = encodedValue.getTagList("in", NBT.TAG_COMPOUND);
        final NBTTagList outTag = encodedValue.getTagList("out", NBT.TAG_COMPOUND);

        // Legacy item list (compatible with old API getInputs/getOutputs)
        final List<IAEItemStack> inLegacy = new ArrayList<>();
        final List<IAEItemStack> outLegacy = new ArrayList<>();

        // Generic list (main entry)
        final List<IAEStack<?>> in = new ArrayList<>();
        final List<IAEStack<?>> out = new ArrayList<>();

        // ========== Parse inputs ==========
        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            // readStackNBT(tag, true): enable legacy FluidDummyItem auto-conversion
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.isEmpty()) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("No pattern here!");
            }

            // Legacy item list: convert fluids to FluidDummyItem items via stackConvert
            inLegacy.add(stackConvert(aeStack));
            in.add(aeStack);
        }

        // ========== Parse outputs ==========
        for (int x = 0; x < outTag.tagCount(); x++) {
            final NBTTagCompound tag = outTag.getCompoundTagAt(x);
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.isEmpty()) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("No pattern here!");
            }

            outLegacy.add(stackConvert(aeStack));
            out.add(aeStack);
        }

        // ========== Build arrays ==========
        this.inputs = inLegacy.toArray(new IAEItemStack[0]);
        this.outputs = outLegacy.toArray(new IAEItemStack[0]);
        this.condensedInputs = convertToCondensedList(this.inputs);
        this.condensedOutputs = convertToCondensedList(this.outputs);

        this.aeInputs = in.toArray(new IAEStack<?>[0]);
        this.aeOutputs = out.toArray(new IAEStack<?>[0]);
        this.condensedAEInputs = convertToCondensedAEList(this.aeInputs);
        this.condensedAEOutputs = convertToCondensedAEList(this.aeOutputs);

        // ========== Validity check ==========
        if (this.condensedAEInputs.length == 0) {
            encodedValue.setBoolean("InvalidPattern", true);
            throw new IllegalStateException("No pattern here!");
        }

        if (this.inputOnly) {
            if (this.condensedAEOutputs.length != 0) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("Input-only pattern has outputs");
            }
        } else if (this.condensedAEOutputs.length == 0) {
            encodedValue.setBoolean("InvalidPattern", true);
            throw new IllegalStateException("No pattern here!");
        }
    }

    // ========== ICraftingPatternDetails implementation ==========

    @Override
    public ItemStack getPattern() {
        return this.patternItem;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    // --- Generic main entry methods ---

    @Override
    public IAEStack<?>[] getAEInputs() {
        return this.aeInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return this.condensedAEInputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return this.aeOutputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return this.condensedAEOutputs;
    }

    // --- Legacy item-type methods (override default implementations to avoid redundant filtering) ---

    @Override
    public IAEItemStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.condensedInputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.condensedOutputs;
    }

    // --- Other interface methods ---

    @Override
    public boolean canSubstitute() {
        return this.canSubstitute;
    }

    @Override
    public boolean canBeSubstitute() {
        return this.canBeSubstitute;
    }

    @Override
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getOutput(final InventoryCrafting craftingInv, final World w) {
        throw new IllegalStateException("Not a crafting recipe!");
    }

    @Override
    public boolean isValidItemForSlot(final int slotIndex, final ItemStack i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public boolean isValidItemForSlot(final int slotIndex, final IAEStack<?> i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public boolean isInputOnly() {
        return this.inputOnly;
    }

    @Override
    public UUID getInputOnlyUuid() {
        return this.inputOnlyUuid;
    }

    // ========== Comparable / equals / hashCode ==========

    @Override
    public int compareTo(final UltimatePatternHelper o) {
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        final UltimatePatternHelper other = (UltimatePatternHelper) obj;
        if (this.pattern != null && other.pattern != null) {
            return this.pattern.equals(other.pattern);
        }
        return false;
    }

    // ========== Static utility methods ==========

    /**
     * Load generic stack array from NBT tag list.
     *
     * @param tags        NBTTagList (each entry is an NBTTagCompound)
     * @param saveOrder   whether to keep null entries to maintain slot order
     * @param unknownItem fallback item to use when an entry cannot be parsed (may be null)
     * @return generic stack array
     */
    public static IAEStack<?>[] loadIAEStackFromNBT(final NBTTagList tags, boolean saveOrder,
            final ItemStack unknownItem) {
        final List<IAEStack<?>> items = new ArrayList<>();
        for (int x = 0; x < tags.tagCount(); x++) {
            final NBTTagCompound tag = tags.getCompoundTagAt(x);
            if (tag.isEmpty()) {
                if (saveOrder) {
                    items.add(null);
                }
                continue;
            }

            IAEStack<?> gs = readStackNBT(tag, true);
            if (gs == null && unknownItem != null && !unknownItem.isEmpty()) {
                gs = AEItemStack.fromItemStack(unknownItem);
            }
            if (gs != null || saveOrder) {
                items.add(gs);
            }
        }
        return items.toArray(new IAEStack<?>[0]);
    }

    /**
     * Parse the UUID of an inputOnly-type recipe.
     */
    private static UUID readInputOnlyUuid(final NBTTagCompound encodedValue, boolean inputOnly) {
        if (!inputOnly) {
            return null;
        }
        final String rawUuid = encodedValue.getString("tunnelUuid");
        if (rawUuid == null || rawUuid.isEmpty()) {
            throw new IllegalStateException("No pattern here!");
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("No pattern here!");
        }
    }
}

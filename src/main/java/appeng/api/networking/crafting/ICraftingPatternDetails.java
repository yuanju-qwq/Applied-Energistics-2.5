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

package appeng.api.networking.crafting;


import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;


public interface ICraftingPatternDetails {

    /**
     * @return the pattern item that encodes this recipe
     */
    ItemStack getPattern();

    /**
     * Check whether the item in the specified slot can be used as a valid input for this pattern.
     * Only applicable to crafting table recipes (isCraftable() == true).
     *
     * @param slotIndex slot index
     * @param itemStack item stack
     * @param world     crafting world
     * @return whether it is valid
     */
    boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world);

    /**
     * Generic version: check whether the stack (item/fluid etc.) in the specified slot can be used as a valid input.
     * Defaults to delegating to the ItemStack version.
     */
    default boolean isValidItemForSlot(int slotIndex, IAEStack<?> aes, World world) {
        if (aes instanceof IAEItemStack) {
            return isValidItemForSlot(slotIndex, ((IAEItemStack) aes).getItemStack(), world);
        }
        return false;
    }

    /**
     * @return whether this is a crafting table recipe (true) or a processing recipe (false)
     */
    boolean isCraftable();

    // ========== Generic main entry methods (supporting item + fluid and other types) ==========

    /**
     * Get raw inputs (supporting item + fluid and other types), includes null placeholders to preserve slot positions.
     */
    IAEStack<?>[] getAEInputs();

    /**
     * Get condensed inputs (supporting item + fluid and other types), merges identical stacks, no nulls.
     */
    IAEStack<?>[] getCondensedAEInputs();

    /**
     * Get condensed outputs (supporting item + fluid and other types), merges identical stacks, no nulls.
     */
    IAEStack<?>[] getCondensedAEOutputs();

    /**
     * Get raw outputs (supporting item + fluid and other types).
     */
    IAEStack<?>[] getAEOutputs();

    // ========== Legacy item-type methods (deprecated, default to converting from generic methods) ==========

    /**
     * Get raw inputs (item type), includes null placeholders to preserve slot positions.
     *
     * @deprecated Use {@link #getAEInputs()} instead
     */
    @Deprecated
    default IAEItemStack[] getInputs() {
        return filterItemStacks(getAEInputs());
    }

    /**
     * Get condensed inputs (item type), merges identical items, no nulls.
     *
     * @deprecated Use {@link #getCondensedAEInputs()} instead
     */
    @Deprecated
    default IAEItemStack[] getCondensedInputs() {
        return filterItemStacks(getCondensedAEInputs());
    }

    /**
     * Get condensed outputs (item type), merges identical items, no nulls.
     *
     * @deprecated Use {@link #getCondensedAEOutputs()} instead
     */
    @Deprecated
    default IAEItemStack[] getCondensedOutputs() {
        return filterItemStacks(getCondensedAEOutputs());
    }

    /**
     * Get raw outputs (item type).
     *
     * @deprecated Use {@link #getAEOutputs()} instead
     */
    @Deprecated
    default IAEItemStack[] getOutputs() {
        return filterItemStacks(getAEOutputs());
    }

    /**
     * Filter out item-type stacks from a generic stack array, preserving array size and null positions.
     */
    static IAEItemStack[] filterItemStacks(IAEStack<?>[] stacks) {
        IAEItemStack[] result = new IAEItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] instanceof IAEItemStack) {
                result[i] = (IAEItemStack) stacks[i];
            }
        }
        return result;
    }

    /**
     * Whether substitution materials are allowed.
     */
    boolean canSubstitute();

    /**
     * @return whether the output of this pattern can be used as a substitute input for other patterns
     */
    default boolean canBeSubstitute() {
        return true;
    }

    /**
     * Get the list of allowed substitute inputs for the specified slot.
     */
    List<IAEItemStack> getSubstituteInputs(int slot);

    /**
     * Get the output result of a crafting table recipe.
     * Only applicable to crafting table recipes (isCraftable() == true).
     *
     * @param craftingInv crafting table inventory
     * @param world       crafting world
     * @return crafting output item
     */
    ItemStack getOutput(InventoryCrafting craftingInv, World world);

    /**
     * @return pattern priority
     */
    int getPriority();

    /**
     * Set pattern priority.
     *
     * @param priority priority value
     */
    void setPriority(int priority);

    /**
     * @return true if this pattern is input-only and should be inlined during resolution.
     */
    default boolean isInputOnly() {
        return false;
    }

    /**
     * @return the unique identifier for an input-only pattern, or null if not applicable.
     */
    default java.util.UUID getInputOnlyUuid() {
        return null;
    }
}

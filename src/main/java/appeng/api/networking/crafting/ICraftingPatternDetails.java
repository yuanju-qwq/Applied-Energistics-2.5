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

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;


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
    default boolean isValidItemForSlot(int slotIndex, GenericStack aes, World world) {
        if (aes.what() instanceof AEItemKey itemKey) {
            return isValidItemForSlot(slotIndex, itemKey.toStack(), world);
        }
        return false;
    }

    /**
     * @return whether this is a crafting table recipe (true) or a processing recipe (false)
     */
    boolean isCraftable();

    // ========== GenericStack main entry methods (primary / abstract) ==========

    /**
     * Get raw inputs as GenericStack[], includes null placeholders to preserve slot positions.
     */
    GenericStack[] getInputStacks();

    /**
     * Get raw outputs as GenericStack[].
     */
    GenericStack[] getOutputStacks();

    /**
     * Get condensed inputs as GenericStack[], merges identical stacks, no nulls.
     */
    GenericStack[] getCondensedInputStacks();

    /**
     * Get condensed outputs as GenericStack[], merges identical stacks, no nulls.
     */
    GenericStack[] getCondensedOutputStacks();

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
    List<GenericStack> getSubstituteInputs(int slot);

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

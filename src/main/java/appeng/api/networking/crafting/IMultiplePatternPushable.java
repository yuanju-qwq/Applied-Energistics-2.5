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

import net.minecraft.inventory.InventoryCrafting;

/**
 * Extends {@link ICraftingMedium} to support pushing materials for multiple copies of the same pattern at once.
 * Ported from the IMultiplePatternPushable interface in Programmable-Hatches-Mod.
 *
 * <p>When the Crafting CPU detects that a medium implements this interface, it will extract
 * multiple copies of materials from the AE storage at once and call {@link #pushPatternMulti},
 * greatly reducing the number of interactions.</p>
 */
public interface IMultiplePatternPushable extends ICraftingMedium {

    /**
     * Push materials for multiple copies of the same pattern into the medium at once.
     *
     * @param patternDetails crafting pattern details
     * @param table          InventoryCrafting for a single copy of materials (same as pushPattern)
     * @param maxTodo        maximum number of copies allowed to push (determined by AE storage inventory and remaining task count)
     * @return array of actual pushed counts:
     *         if length==1, [0] is the number of copies actually pushed and consumed;
     *         if length==2, [0] is the number pushed, [1] is the number consumed
     */
    int[] pushPatternMulti(ICraftingPatternDetails patternDetails, InventoryCrafting table, int maxTodo);
}

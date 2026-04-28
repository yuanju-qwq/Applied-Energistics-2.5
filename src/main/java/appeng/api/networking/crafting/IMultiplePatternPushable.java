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
 * Extends {@link ICraftingMedium} to support pushing multiple copies of the same pattern's materials at once.
 * Ported from Programmable-Hatches-Mod's IMultiplePatternPushable interface.
 *
 * <p>When the crafting CPU detects that a medium implements this interface, it will extract
 * multiple copies of materials from the AE storage at once and call {@link #pushPatternMulti},
 * greatly reducing the number of interactions.</p>
 */
public interface IMultiplePatternPushable extends ICraftingMedium {

    /**
     * Push multiple copies of the same pattern's materials to the medium at once.
     *
     * @param patternDetails the crafting pattern details
     * @param table          single-copy material InventoryCrafting (same as pushPattern)
     * @param maxTodo        maximum number of copies allowed to push (determined by AE storage and remaining tasks)
     * @return actual number of copies pushed as an array;
     *         length==1: [0] is the number of copies actually pushed and consumed;
     *         length==2: [0] is pushed count, [1] is consumed count
     */
    int[] pushPatternMulti(ICraftingPatternDetails patternDetails, InventoryCrafting table, int maxTodo);
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 TeamAppliedEnergistics
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

import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;

/**
 * Utility class for converting between the new {@link KeyCounter} and the legacy {@link IItemList}.
 * <p/>
 * Used during the migration period to bridge old and new API code.
 */
public final class KeyCounterAdapter {

    private KeyCounterAdapter() {
    }

    /**
     * Creates a new {@link KeyCounter} from a legacy {@link IItemList}.
     * Each entry in the IItemList is converted to its AEKey equivalent.
     *
     * @param list the legacy item list
     * @return a new KeyCounter containing all converted entries
     */
    public static KeyCounter fromIItemList(IItemList<? extends IAEStackBase> list) {
        var counter = new KeyCounter();
        for (var stack : list) {
            if (stack instanceof IAEStack<?> aeStack) {
                var key = aeStack.toAEKey();
                if (key != null) {
                    counter.add(key, aeStack.getStackSize());
                }
            }
        }
        return counter;
    }

    /**
     * Adds all entries from a {@link KeyCounter} into a legacy {@link IItemList}.
     * Each AEKey entry is converted back to its legacy IAEStack equivalent.
     *
     * @param counter the KeyCounter to read from
     * @param list    the legacy item list to write into
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void toIItemList(KeyCounter counter, IItemList list) {
        for (var entry : counter) {
            var key = entry.getKey();
            var amount = entry.getLongValue();
            if (amount == 0) {
                continue;
            }
            var stack = key.toIAEStack(amount);
            if (stack != null) {
                list.addGeneric(stack);
            }
        }
    }

    /**
     * Creates a {@link GenericStack} from a legacy {@link IAEStackBase}, if possible.
     *
     * @return the GenericStack, or null if conversion is not possible
     */
    @Nullable
    public static GenericStack toGenericStack(@Nullable IAEStackBase stack) {
        if (stack instanceof IAEStack<?> aeStack) {
            return GenericStack.fromIAEStack(aeStack);
        }
        return null;
    }
}

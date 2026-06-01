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

package appeng.api.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * AE's Equivalent to IInventory, used to reading contents, and manipulating contents of ME Inventories.
 *
 * Implementations should COMPLETELY ignore stack size limits from an external view point, Meaning that you can inject
 * Integer.MAX_VALUE items and it should work as defined, or be able to extract Integer.MAX_VALUE and have it work as
 * defined, Translations to MC's max stack size are external to the AE API.
 *
 * If you want to request a stack of an item, you should should determine that prior to requesting the stack from the
 * inventory.
 */
public interface IMEInventory {

    /**
     * Store new items, or simulate the addition of new items into the ME Inventory.
     *
     * @param input item (as GenericStack) to add.
     * @param type  action type
     * @param src   action source
     *
     * @return the stack of items not added, or null if fully injected
     */
    GenericStack injectItems(GenericStack input, Actionable type, IActionSource src);

    /**
     * Extract the specified item from the ME Inventory.
     *
     * @param request item to request (as GenericStack, with stack size)
     * @param mode    simulate, or perform action?
     * @param src     action source
     *
     * @return the extracted stack, or null if nothing could be extracted
     */
    GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src);

    /**
     * Returns a {@link KeyCounter} view of all available items in this inventory.
     *
     * @return a KeyCounter with amounts per AEKey
     */
    KeyCounter getAvailableKeyCounter();

    /**
     * @return the {@link AEKeyType} corresponding to this inventory
     */
    AEKeyType getKeyType();

    // ===================== Legacy bridge defaults (for migration) =====================

    /**
     * @deprecated Use {@link #getKeyType()} instead.
     */
    @Deprecated
    default appeng.api.storage.data.IAEStackType<?> getStackType() {
        return appeng.api.storage.data.AEStackTypeRegistry.getType(getKeyType().getId());
    }
}

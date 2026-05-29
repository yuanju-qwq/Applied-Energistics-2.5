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
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.stacks.AEKey;
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
public interface IMEInventory<T extends IAEStackBase> {

    /**
     * Store new items, or simulate the addition of new items into the ME Inventory.
     *
     * @param input item to add.
     * @param type  action type
     * @param src   action source
     *
     * @return returns the number of items not added.
     * @deprecated Use {@link #injectItems(GenericStack, Actionable, IActionSource)} instead.
     */
    @Deprecated
    T injectItems(T input, Actionable type, IActionSource src);

    /**
     * Extract the specified item from the ME Inventory
     *
     * @param request item to request ( with stack size. )
     * @param mode    simulate, or perform action?
     *
     * @return returns the number of items extracted, null
     * @deprecated Use {@link #extractItems(GenericStack, Actionable, IActionSource)} instead.
     */
    @Deprecated
    T extractItems(T request, Actionable mode, IActionSource src);

    /**
     * request a full report of all available items, storage.
     *
     * @param out the IItemList the results will be written too
     *
     * @return returns same list that was passed in, is passed out
     * @deprecated Use {@link #getAvailableKeyCounter()} instead.
     */
    @Deprecated
    IItemList<T> getAvailableItems(IItemList<T> out);

    /**
     * request a full report of all available items, storage.
     *
     * @return a new list of this inventories content
     * @deprecated Use {@link #getAvailableKeyCounter()} instead.
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    default IItemList<T> getAvailableItems() {
        return getAvailableItems((IItemList<T>) getStackType().createList());
    }

    // ===================== GenericStack / KeyCounter entry points =====================

    /**
     * Store new items, or simulate the addition of new items into the ME Inventory.
     *
     * @param input item (as GenericStack) to add.
     * @param type  action type
     * @param src   action source
     *
     * @return the stack of items not added, or null if fully injected
     */
    default GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        if (input == null) {
            return null;
        }
        IAEStack<?> aeInput = input.toIAEStack();
        if (aeInput == null) {
            return null;
        }
        IAEStack<?> remaining = injectItemsGeneric(aeInput, type, src);
        return remaining != null ? GenericStack.fromIAEStack(remaining) : null;
    }

    /**
     * Extract the specified item from the ME Inventory.
     *
     * @param request item to request (as GenericStack, with stack size)
     * @param mode    simulate, or perform action?
     * @param src     action source
     *
     * @return the extracted stack, or null if nothing could be extracted
     */
    default GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        if (request == null) {
            return null;
        }
        IAEStack<?> aeRequest = request.toIAEStack();
        if (aeRequest == null) {
            return null;
        }
        IAEStack<?> extracted = extractItemsGeneric(aeRequest, mode, src);
        return extracted != null ? GenericStack.fromIAEStack(extracted) : null;
    }

    /**
     * Returns a {@link KeyCounter} view of all available items in this inventory.
     * <p>
     * The default implementation adapts from {@link #getAvailableItems(IItemList)}.
     *
     * @return a KeyCounter with amounts per AEKey
     */
    @SuppressWarnings("unchecked")
    default KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        IItemList<?> list = getAvailableItemsGeneric(getStackType().createList());
        for (Object obj : list) {
            if (obj instanceof IAEStack<?> stack) {
                AEKey key = stack.toAEKey();
                if (key != null) {
                    out.add(key, stack.getStackSize());
                }
            }
        }
        return out;
    }

    /**
     * @return the {@link IAEStackType} corresponding to this inventory
     */
    IAEStackType<?> getStackType();

    // ===================== Wildcard-safe bridge methods =====================

    /**
     * Wildcard-safe inject operation.
     * <p>
     * Use this method when the caller holds an {@code IMEInventory<?>} and an {@code IAEStack<?>},
     * to avoid raw type cast. The caller is responsible for ensuring that the stack type matches the inventory.
     *
     * @return the remaining portion that was not injected, or null if fully injected
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default IAEStack<?> injectItemsGeneric(final IAEStack<?> input, final Actionable type, final IActionSource src) {
        IMEInventory raw = (IMEInventory) this;
        return (IAEStack<?>) raw.injectItems((IAEStackBase) input, type, src);
    }

    /**
     * Wildcard-safe extract operation.
     *
     * @return the extracted stack, or null
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default IAEStack<?> extractItemsGeneric(final IAEStack<?> request, final Actionable mode, final IActionSource src) {
        IMEInventory raw = (IMEInventory) this;
        return (IAEStack<?>) raw.extractItems((IAEStackBase) request, mode, src);
    }

    /**
     * Wildcard-safe retrieval of the available items list.
     *
     * @param out the list to receive results (type must match this inventory)
     * @return the passed-in list
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default IItemList<?> getAvailableItemsGeneric(final IItemList<?> out) {
        IMEInventory raw = (IMEInventory) this;
        return (IItemList<?>) raw.getAvailableItems(out);
    }
}

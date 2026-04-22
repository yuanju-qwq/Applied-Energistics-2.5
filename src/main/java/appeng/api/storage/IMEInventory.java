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
     */
    T injectItems(T input, Actionable type, IActionSource src);

    /**
     * Extract the specified item from the ME Inventory
     *
     * @param request item to request ( with stack size. )
     * @param mode    simulate, or perform action?
     *
     * @return returns the number of items extracted, null
     */
    T extractItems(T request, Actionable mode, IActionSource src);

    /**
     * request a full report of all available items, storage.
     *
     * @param out the IItemList the results will be written too
     *
     * @return returns same list that was passed in, is passed out
     */
    IItemList<T> getAvailableItems(IItemList<T> out);

    /**
     * request a full report of all available items, storage.
     *
     * @return a new list of this inventories content
     */
    @SuppressWarnings("unchecked")
    default IItemList<T> getAvailableItems() {
        return getAvailableItems((IItemList<T>) getStackType().createList());
    }

    /**
     * @deprecated 请使用 {@link #getStackType()} 代替。
     * @return the type of channel your handler should be part of
     */
    @Deprecated
    @SuppressWarnings({ "rawtypes", "unchecked" })
    default IStorageChannel getChannel() {
        return getStackType().getStorageChannel();
    }

    /**
     * @return 此 inventory 对应的 {@link IAEStackType}
     */
    IAEStackType<?> getStackType();

    // ===================== 通配符安全桥接方法 =====================

    /**
     * 通配符安全的注入操作。
     * <p>
     * 当调用方持有 {@code IMEInventory<?>} 和 {@code IAEStack<?>} 时使用此方法，
     * 以避免 raw type cast。调用方负责确保栈的类型与 inventory 匹配。
     *
     * @return 未注入的部分，或 null 表示全部注入成功
     */
    @SuppressWarnings("unchecked")
    default IAEStack<?> injectItemsGeneric(final IAEStack<?> input, final Actionable type, final IActionSource src) {
        return ((IMEInventory) this).injectItems(input, type, src);
    }

    /**
     * 通配符安全的提取操作。
     *
     * @return 提取出的栈，或 null
     */
    @SuppressWarnings("unchecked")
    default IAEStack<?> extractItemsGeneric(final IAEStack<?> request, final Actionable mode, final IActionSource src) {
        return ((IMEInventory) this).extractItems(request, mode, src);
    }

    /**
     * 通配符安全地获取可用物品列表。
     *
     * @param out 接收结果的列表（类型必须与此 inventory 匹配）
     * @return 传入的列表
     */
    @SuppressWarnings("unchecked")
    default IItemList<?> getAvailableItemsGeneric(final IItemList<?> out) {
        return ((IMEInventory) this).getAvailableItems(out);
    }
}

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

package appeng.api.storage.data;

import java.util.Collection;
import java.util.Iterator;

import javax.annotation.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.IStorageChannel;

/**
 * Represents a list of items in AE.
 *
 * Don't Implement.
 *
 * Construct with - For items: AEApi.instance().storage().getStorageChannel( IItemStorageChannel.class).createList() -
 * For fluids: AEApi.instance().storage().getStorageChannel( IFluidStorageChannel.class).createList() - Replace with the
 * corresponding {@link IStorageChannel} type for non native channels
 */
public interface IItemList<T extends IAEStackBase> extends IItemContainer<T>, Iterable<T> {

    /**
     * add a stack to the list stackSize is used to add to stackSize, this will merge the stack with an item already in
     * the list if found.
     *
     * @param option stacktype option
     */
    void addStorage(T option); // adds a stack as stored

    /**
     * add a stack to the list as craftable, this will merge the stack with an item already in the list if found.
     *
     * @param option stacktype option
     */
    void addCrafting(T option);

    /**
     * add a stack to the list, stack size is used to add to requestable, this will merge the stack with an item already
     * in the list if found.
     *
     * @param option stacktype option
     */
    void addRequestable(T option); // adds a stack as requestable

    /**
     * @return the first item in the list
     */
    T getFirstItem();

    /**
     * @return the number of items in the list
     */
    int size();

    /**
     * allows you to iterate the list.
     */
    @Override
    Iterator<T> iterator();

    /**
     * resets stack sizes to 0.
     */
    void resetStatus();

    /**
     * 返回此列表中存储的栈类型。
     * <p>
     * 当列表只存储单一类型时返回对应的 {@link IAEStackType}，
     * 当列表是多类型联合列表时返回 null。
     *
     * @return 栈类型，可能为 null
     */
    @Nullable
    default IAEStackType<?> getStackType() {
        return null;
    }

    /**
     * @return 列表是否为空（无任何栈）
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    // ===================== 通配符安全桥接方法 =====================

    /**
     * 类型安全地向此列表添加一个通配符类型的栈。
     * <p>
     * 当调用方持有 {@code IAEStack<?>} 而列表的具体类型 {@code T} 在编译时未知时使用此方法，
     * 以避免 raw type cast。调用方负责确保栈的类型与列表匹配。
     */
    @SuppressWarnings("unchecked")
    default void addGeneric(final IAEStack<?> option) {
        add((T) option);
    }

    /**
     * 类型安全地向此列表添加存储栈。
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addStorageGeneric(final IAEStack<?> option) {
        addStorage((T) option);
    }

    /**
     * 类型安全地向此列表添加可合成栈。
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addCraftingGeneric(final IAEStack<?> option) {
        addCrafting((T) option);
    }

    /**
     * 类型安全地向此列表添加可请求栈。
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addRequestableGeneric(final IAEStack<?> option) {
        addRequestable((T) option);
    }

    /**
     * 通配符安全的精确查找。
     * <p>
     * 当调用方持有 {@code IAEStack<?>} 而列表的具体类型 {@code T} 在编译时未知时使用此方法。
     *
     * @return 匹配的栈，或 null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    default IAEStack<?> findPreciseGeneric(final IAEStack<?> i) {
        return (IAEStack<?>) findPrecise((T) i);
    }

    /**
     * 通配符安全的模糊查找。
     *
     * @return 匹配的栈集合
     */
    @SuppressWarnings("unchecked")
    default Collection<? extends IAEStackBase> findFuzzyGeneric(final IAEStack<?> input, final FuzzyMode fuzzy) {
        return findFuzzy((T) input, fuzzy);
    }
}

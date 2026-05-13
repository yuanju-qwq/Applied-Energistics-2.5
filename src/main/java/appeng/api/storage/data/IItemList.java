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
 * Represents a legacy typed list of AE stacks.
 * <p>
 * New code should prefer {@link KeyCounter} for heterogeneous counting and aggregation,
 * while this interface remains as a compatibility layer for the pre-AEKey stack model.
 * <p>
 * Don't Implement.
 * <p>
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
     * Returns the stack type stored in this list.
     * <p>
     * Returns the corresponding {@link IAEStackType} when the list stores a single type,
     * or null when the list is a multi-type union list.
     *
     * @return the stack type, possibly null
     */
    @Nullable
    default IAEStackType<?> getStackType() {
        return null;
    }

    /**
     * @return whether the list is empty (contains no stacks)
     */
    default boolean isEmpty() {
        return size() == 0;
    }

    // ===================== Wildcard-safe bridge methods =====================

    /**
     * Type-safely add a wildcard-typed stack to this list.
     * <p>
     * Use this method when the caller holds an {@code IAEStack<?>} and the concrete type {@code T}
     * of the list is unknown at compile time, to avoid raw type cast.
     * The caller is responsible for ensuring that the stack type matches the list.
     */
    @SuppressWarnings("unchecked")
    default void addGeneric(final IAEStack<?> option) {
        add((T) option);
    }

    /**
     * Type-safely add a storage stack to this list.
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addStorageGeneric(final IAEStack<?> option) {
        addStorage((T) option);
    }

    /**
     * Type-safely add a craftable stack to this list.
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addCraftingGeneric(final IAEStack<?> option) {
        addCrafting((T) option);
    }

    /**
     * Type-safely add a requestable stack to this list.
     * @see #addGeneric(IAEStack)
     */
    @SuppressWarnings("unchecked")
    default void addRequestableGeneric(final IAEStack<?> option) {
        addRequestable((T) option);
    }

    /**
     * Wildcard-safe exact lookup.
     * <p>
     * Use this method when the caller holds an {@code IAEStack<?>} and the concrete type {@code T}
     * of the list is unknown at compile time.
     *
     * @return the matching stack, or null
     */
    @SuppressWarnings("unchecked")
    @Nullable
    default IAEStack<?> findPreciseGeneric(final IAEStack<?> i) {
        return (IAEStack<?>) findPrecise((T) i);
    }

    /**
     * Wildcard-safe fuzzy lookup.
     *
     * @return the collection of matching stacks
     */
    @SuppressWarnings("unchecked")
    default Collection<? extends IAEStackBase> findFuzzyGeneric(final IAEStack<?> input, final FuzzyMode fuzzy) {
        return findFuzzy((T) input, fuzzy);
    }
}

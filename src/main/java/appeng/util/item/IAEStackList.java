/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util.item;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * 多类型联合 IItemList 实现。
 * <p>
 * 内部按 {@link IAEStackType} 分类存储，每种类型使用对应的 {@link IItemList} 实现。
 * <p>
 * 由于 AE2.5 的 {@code IItemList<T extends IAEStack<T>>} 递归泛型约束，
 * 不同类型的 {@link IItemList} 无法在同一集合中精确持有泛型参数，
 * 因此使用通配符 {@code IItemList<?>} 存储，通过 capture helper 方法桥接类型安全调用。
 */
public final class IAEStackList implements IItemList<IAEStackBase> {

    private final Map<IAEStackType<?>, IItemList<?>> lists = new IdentityHashMap<>();

    public IAEStackList() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.lists.put(type, type.createList());
        }
    }

    /**
     * 通配符捕获辅助方法：安全地向对应的子列表执行操作。
     * 由于 IAEStackBase 是所有 IAEStack<T> 的公共基类，且 lists 保证 key 与 value 的类型一致，
     * 这里的 unchecked cast 在运行时是安全的。
     */
    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IItemList<T> castList(IItemList<?> list) {
        return (IItemList<T>) list;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> T castStack(IAEStackBase stack) {
        return (T) stack;
    }

    @Override
    public void add(final IAEStackBase option) {
        if (option != null) {
            addHelper(this.lists.get(option.getStackTypeBase()), option);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addHelper(IItemList<?> list, IAEStackBase option) {
        ((IItemList<T>) list).add((T) option);
    }

    @Override
    public IAEStackBase findPrecise(final IAEStackBase stack) {
        if (stack != null) {
            return findPreciseHelper(this.lists.get(stack.getStackTypeBase()), stack);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IAEStackBase findPreciseHelper(IItemList<?> list, IAEStackBase stack) {
        return (IAEStackBase) ((IItemList<T>) list).findPrecise((T) stack);
    }

    @Override
    public Collection<IAEStackBase> findFuzzy(final IAEStackBase filter, final FuzzyMode fuzzy) {
        if (filter != null) {
            return findFuzzyHelper(this.lists.get(filter.getStackTypeBase()), filter, fuzzy);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> Collection<IAEStackBase> findFuzzyHelper(IItemList<?> list, IAEStackBase filter, FuzzyMode fuzzy) {
        return (Collection<IAEStackBase>) (Collection<?>) ((IItemList<T>) list).findFuzzy((T) filter, fuzzy);
    }

    @Override
    public boolean isEmpty() {
        for (IItemList<?> list : this.lists.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void addStorage(final IAEStackBase option) {
        if (option != null) {
            addStorageHelper(this.lists.get(option.getStackTypeBase()), option);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addStorageHelper(IItemList<?> list, IAEStackBase option) {
        ((IItemList<T>) list).addStorage((T) option);
    }

    @Override
    public void addCrafting(final IAEStackBase option) {
        if (option != null) {
            addCraftingHelper(this.lists.get(option.getStackTypeBase()), option);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addCraftingHelper(IItemList<?> list, IAEStackBase option) {
        ((IItemList<T>) list).addCrafting((T) option);
    }

    @Override
    public void addRequestable(final IAEStackBase option) {
        if (option != null) {
            addRequestablelHelper(this.lists.get(option.getStackTypeBase()), option);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> void addRequestablelHelper(IItemList<?> list, IAEStackBase option) {
        ((IItemList<T>) list).addRequestable((T) option);
    }

    @Override
    public IAEStackBase getFirstItem() {
        for (final IAEStackBase stack : this) {
            return stack;
        }
        return null;
    }

    @Override
    public int size() {
        int size = 0;
        for (IItemList<?> list : this.lists.values()) {
            size += list.size();
        }
        return size;
    }

    @Override
    @Nonnull
    public Iterator<IAEStackBase> iterator() {
        return new MeaningfulStackIterator(new Iterator<>() {

            private final Iterator<IItemList<?>> listIterator = lists.values().iterator();
            private Iterator<?> currentIterator;

            @Override
            public boolean hasNext() {
                if (currentIterator == null || !currentIterator.hasNext()) {
                    while (listIterator.hasNext()) {
                        currentIterator = listIterator.next().iterator();
                        if (currentIterator.hasNext()) return true;
                    }
                    return false;
                }
                return true;
            }

            @Override
            public IAEStackBase next() {
                if (currentIterator == null || !currentIterator.hasNext()) {
                    throw new NoSuchElementException();
                }
                return (IAEStackBase) currentIterator.next();
            }

            @Override
            public void remove() {
                if (currentIterator != null) {
                    currentIterator.remove();
                }
            }
        });
    }

    @Override
    public void resetStatus() {
        for (IItemList<?> list : this.lists.values()) {
            list.resetStatus();
        }
    }

    @Override
    @Nullable
    public IAEStackType getStackType() {
        // 多类型联合列表返回 null
        return null;
    }

    /**
     * 有意义（meaningful）的栈迭代器 — 跳过 stackSize == 0 等无意义的栈。
     */
    private static class MeaningfulStackIterator implements Iterator<IAEStackBase> {

        private final Iterator<IAEStackBase> parent;
        private IAEStackBase next;

        MeaningfulStackIterator(final Iterator<IAEStackBase> parent) {
            this.parent = parent;
            this.next = seekNext();
        }

        @Override
        public boolean hasNext() {
            return this.next != null;
        }

        @Override
        public IAEStackBase next() {
            if (this.next == null) {
                throw new NoSuchElementException();
            }
            IAEStackBase result = this.next;
            this.next = seekNext();
            return result;
        }

        @Override
        public void remove() {
            this.parent.remove();
        }

        private IAEStackBase seekNext() {
            while (this.parent.hasNext()) {
                IAEStackBase item = this.parent.next();
                if (item.isMeaningful()) {
                    return item;
                } else {
                    this.parent.remove();
                }
            }
            return null;
        }
    }
}

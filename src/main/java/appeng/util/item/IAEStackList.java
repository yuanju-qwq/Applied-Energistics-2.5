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
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * 多类型联合 IItemList 实现。
 * <p>
 * 内部按 {@link IAEStackType} 分类存储，每种类型使用对应的 {@link IItemList} 实现。
 * 由于 AE2.5 的 {@code IItemList<T extends IAEStack<T>>} 递归泛型约束，
 * 此类需要使用 raw type 来实现 {@code IItemList<IAEStack<?>>} 语义。
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class IAEStackList implements IItemList {

    private final Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();

    public IAEStackList() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.lists.put(type, type.createList());
        }
    }

    @Override
    public void add(final IAEStack option) {
        if (option != null) {
            this.lists.get(option.getStackType()).add(option);
        }
    }

    @Override
    public IAEStack findPrecise(final IAEStack stack) {
        if (stack != null) {
            return (IAEStack) this.lists.get(stack.getStackType()).findPrecise(stack);
        }
        return null;
    }

    @Override
    public Collection findFuzzy(final IAEStack filter, final FuzzyMode fuzzy) {
        if (filter != null) {
            return this.lists.get(filter.getStackType()).findFuzzy(filter, fuzzy);
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        for (IItemList list : this.lists.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void addStorage(final IAEStack option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addStorage(option);
        }
    }

    @Override
    public void addCrafting(final IAEStack option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addCrafting(option);
        }
    }

    @Override
    public void addRequestable(final IAEStack option) {
        if (option != null) {
            this.lists.get(option.getStackType()).addRequestable(option);
        }
    }

    @Override
    public IAEStack getFirstItem() {
        for (final IAEStack stackType : this) {
            return stackType;
        }
        return null;
    }

    @Override
    public int size() {
        int size = 0;
        for (IItemList list : this.lists.values()) {
            size += list.size();
        }
        return size;
    }

    @Override
    @Nonnull
    public Iterator<IAEStack> iterator() {
        return new MeaningfulStackIterator(new Iterator<>() {

            private final Iterator<IItemList> listIterator = lists.values().iterator();
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
            public IAEStack next() {
                if (currentIterator == null || !currentIterator.hasNext()) {
                    throw new NoSuchElementException();
                }
                return (IAEStack) currentIterator.next();
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
        for (IItemList list : this.lists.values()) {
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
    private static class MeaningfulStackIterator implements Iterator<IAEStack> {

        private final Iterator<IAEStack> parent;
        private IAEStack next;

        MeaningfulStackIterator(final Iterator<IAEStack> parent) {
            this.parent = parent;
            this.next = seekNext();
        }

        @Override
        public boolean hasNext() {
            return this.next != null;
        }

        @Override
        public IAEStack next() {
            if (this.next == null) {
                throw new NoSuchElementException();
            }
            IAEStack result = this.next;
            this.next = seekNext();
            return result;
        }

        @Override
        public void remove() {
            this.parent.remove();
        }

        private IAEStack seekNext() {
            while (this.parent.hasNext()) {
                IAEStack item = this.parent.next();
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

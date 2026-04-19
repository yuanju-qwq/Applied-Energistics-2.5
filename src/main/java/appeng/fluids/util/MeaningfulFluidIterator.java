/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.fluids.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import appeng.api.storage.data.IAEStack;

public class MeaningfulFluidIterator<T extends IAEStack<T>> implements Iterator<T> {

    private final Iterator<T> parent;
    private T next;

    public MeaningfulFluidIterator(final Iterator<T> iterator) {
        this.parent = iterator;
        this.next = seekNext();
    }

    @Override
    public boolean hasNext() {
        return this.next != null;
    }

    @Override
    public T next() {
        if (this.next == null) {
            throw new NoSuchElementException();
        }

        final T result = this.next;
        this.next = seekNext();
        return result;
    }

    @Override
    public void remove() {
        this.parent.remove();
    }

    private T seekNext() {
        while (this.parent.hasNext()) {
            final T candidate = this.parent.next();
            if (candidate.isMeaningful()) {
                return candidate;
            }
            this.parent.remove(); // self cleaning :3
        }
        return null;
    }
}

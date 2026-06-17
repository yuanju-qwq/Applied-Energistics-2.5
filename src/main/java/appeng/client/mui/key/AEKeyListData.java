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

package appeng.client.mui.key;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

/**
 * AEKey-only list data ingress for the new MUI bottom layer.
 */
public final class AEKeyListData {

    private final Map<AEKey, AEKeyDisplayEntry> entries = new LinkedHashMap<>();
    private boolean changed = true;
    private List<AEKeyDisplayEntry> snapshot = Collections.emptyList();

    public void clear() {
        this.entries.clear();
        this.markChanged();
    }

    public void postUpdate(final AEKey key, final long amount, final boolean craftable) {
        this.entries.put(key, AEKeyDisplayEntry.of(key, amount, craftable));
        this.markChanged();
    }

    public void postUpdate(final GenericStack stack, final boolean craftable) {
        this.postUpdate(stack.what(), stack.amount(), craftable);
    }

    public void postUpdate(final AEKeyDisplayEntry entry) {
        this.entries.put(entry.key(), entry);
        this.markChanged();
    }

    public void postGenericStackUpdate(final Collection<GenericStack> stacks, final boolean craftable) {
        for (final GenericStack stack : stacks) {
            this.postUpdate(stack, craftable);
        }
    }

    public void postEntryUpdate(final Collection<AEKeyDisplayEntry> entries) {
        for (final AEKeyDisplayEntry entry : entries) {
            this.postUpdate(entry);
        }
    }

    public void postKeyCounterUpdate(final KeyCounter counter, final boolean craftable) {
        for (final var entry : counter) {
            this.postUpdate(entry.getKey(), entry.getLongValue(), craftable);
        }
    }

    public long getAmount(final AEKey key) {
        final AEKeyDisplayEntry entry = this.entries.get(key);
        return entry != null ? entry.amount() : 0;
    }

    public boolean isCraftable(final AEKey key) {
        final AEKeyDisplayEntry entry = this.entries.get(key);
        return entry != null && entry.craftable();
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    @Nullable
    public AEKeyDisplayEntry get(final int index) {
        final List<AEKeyDisplayEntry> view = this.getSnapshot();
        return index >= 0 && index < view.size() ? view.get(index) : null;
    }

    public List<AEKeyDisplayEntry> getSnapshot() {
        if (this.changed) {
            this.snapshot = Collections.unmodifiableList(new ArrayList<>(this.entries.values()));
            this.changed = false;
        }
        return this.snapshot;
    }

    private void markChanged() {
        this.changed = true;
    }
}

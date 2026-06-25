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

package appeng.client.mui.legacy;

import javax.annotation.Nullable;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo.RepoEntry;

/**
 * Centralized bridge for converting between legacy {@link IAEStack} objects and the
 * AEKey-based data model ({@link AEKey}, {@link GenericStack}, {@link RepoEntry}).
 *
 * <p>All IAEStack&lt;-&gt;AEKey conversions in the {@code appeng.client.mui} tree should
 * route through this class so that the legacy dependency surface is isolated to a
 * single location. Once the remaining callers (notably
 * {@link appeng.client.mui.slot.VirtualMEPhantomSlot#handleMouseClicked} and
 * {@link appeng.client.mui.AEBasePanel}'s SlotME interactions) are migrated to work
 * directly with AEKey/GenericStack, this class can be deleted.
 *
 * <p><b>Note on {@code countRequestable}:</b> The AEKey data model does not carry a
 * requestable count. Conversions via this bridge always produce IAEStacks whose
 * {@link IAEStack#getCountRequestable()} returns 0. Any UI code that relied on a
 * non-zero requestable value from these conversions was dead code and has been
 * removed; see the Phase B migration notes.
 */
public final class LegacyStackBridge {

    private LegacyStackBridge() {
        // Utility class, no instances.
    }

    // ========== IAEStack -> AEKey model ==========

    /**
     * Extracts the {@link AEKey} identity from a legacy {@link IAEStack}.
     *
     * @param stack the legacy stack, may be null
     * @return the AEKey, or null if the input is null or conversion fails
     */
    @Nullable
    public static AEKey toAEKey(@Nullable IAEStack<?> stack) {
        return stack != null ? stack.toAEKey() : null;
    }

    /**
     * Creates a {@link GenericStack} from a legacy {@link IAEStack}.
     *
     * <p>Only the key identity and stored amount are transferred; the craftable flag
     * and requestable count are dropped because {@link GenericStack} does not model them.
     *
     * @param stack the legacy stack, may be null
     * @return the GenericStack, or null if the input is null or conversion fails
     */
    @Nullable
    public static GenericStack toGenericStack(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }
        AEKey key = stack.toAEKey();
        if (key == null) {
            return null;
        }
        return new GenericStack(key, stack.getStackSize());
    }

    /**
     * Creates a {@link RepoEntry} from a legacy {@link IAEStack}.
     *
     * <p>The craftable flag is transferred; the requestable count is dropped.
     *
     * @param stack the legacy stack, may be null
     * @return the RepoEntry, or null if the input is null or conversion fails
     */
    @Nullable
    public static RepoEntry toRepoEntry(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return null;
        }
        AEKey key = stack.toAEKey();
        if (key == null) {
            return null;
        }
        return new RepoEntry(key, stack.getStackSize(), stack.isCraftable());
    }

    // ========== AEKey model -> IAEStack ==========

    /**
     * Creates a legacy {@link IAEStack} from an {@link AEKey} and amount.
     *
     * <p>The resulting IAEStack has {@code countRequestable = 0} and
     * {@code craftable = false}. Callers that need the craftable flag should use
     * {@link #toIAEStack(RepoEntry)} instead.
     *
     * @param key    the resource identity, may be null
     * @param amount the stored amount
     * @return the legacy stack, or null if the input key is null or conversion fails
     */
    @Nullable
    public static IAEStack<?> toIAEStack(@Nullable AEKey key, long amount) {
        return key != null ? key.toIAEStack(amount) : null;
    }

    /**
     * Creates a legacy {@link IAEStack} from a {@link GenericStack}.
     *
     * <p>The resulting IAEStack has {@code countRequestable = 0} and
     * {@code craftable = false}.
     *
     * @param stack the GenericStack, may be null
     * @return the legacy stack, or null if the input is null or conversion fails
     */
    @Nullable
    public static IAEStack<?> toIAEStack(@Nullable GenericStack stack) {
        return stack != null ? stack.what().toIAEStack(stack.amount()) : null;
    }

    /**
     * Creates a legacy {@link IAEStack} from a {@link RepoEntry}.
     *
     * <p>The craftable flag is transferred; {@code countRequestable} is always 0.
     *
     * @param entry the RepoEntry, may be null
     * @return the legacy stack, or null if the input is null or conversion fails
     */
    @Nullable
    public static IAEStack<?> toIAEStack(@Nullable RepoEntry entry) {
        if (entry == null) {
            return null;
        }
        IAEStack<?> stack = entry.what().toIAEStack(entry.amount());
        if (stack != null) {
            stack.setCraftable(entry.craftable());
        }
        return stack;
    }
}

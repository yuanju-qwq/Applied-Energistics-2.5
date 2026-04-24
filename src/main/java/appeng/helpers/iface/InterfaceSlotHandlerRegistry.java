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

package appeng.helpers.iface;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Registry for {@link IInterfaceSlotHandler} implementations.
 * <p>
 * Each {@link IAEStackType} should register its handler during mod initialization.
 * The {@link appeng.helpers.InterfaceLogic} uses this registry to dispatch
 * slot operations to the correct handler based on stack type.
 */
public final class InterfaceSlotHandlerRegistry {

    private static final Map<IAEStackType<?>, IInterfaceSlotHandler<?>> REGISTRY = new IdentityHashMap<>();

    private InterfaceSlotHandlerRegistry() {}

    /**
     * Register a slot handler for the given stack type.
     */
    public static <T extends IAEStack<T>> void register(
            @Nonnull IAEStackType<T> type, @Nonnull IInterfaceSlotHandler<T> handler) {
        REGISTRY.put(type, handler);
    }

    /**
     * Get the slot handler for the given stack type.
     *
     * @return the handler, or null if no handler is registered for this type
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> IInterfaceSlotHandler<T> getHandler(@Nonnull IAEStackType<T> type) {
        return (IInterfaceSlotHandler<T>) REGISTRY.get(type);
    }

    /**
     * Get the slot handler for the given stack.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> IInterfaceSlotHandler<T> getHandler(@Nonnull IAEStack<?> stack) {
        return (IInterfaceSlotHandler<T>) REGISTRY.get(stack.getStackTypeBase());
    }

    /**
     * @return all registered handlers
     */
    @Nonnull
    public static Collection<IInterfaceSlotHandler<?>> getAllHandlers() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * @return true if a handler is registered for the given type
     */
    public static boolean hasHandler(@Nullable IAEStackType<?> type) {
        return type != null && REGISTRY.containsKey(type);
    }
}

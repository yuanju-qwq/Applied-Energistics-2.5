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

package appeng.api.parts;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import appeng.api.storage.data.IAEStack;
import appeng.api.stacks.AEKeyType;

/**
 * Registry for {@link IConversionMonitorHandler} implementations.
 * <p>
 * Each {@link AEKeyType} should register its handler during mod initialization.
 * The Conversion Monitor part uses this registry to dispatch player interactions
 * to the correct handler based on the displayed or interacted stack type.
 */
public final class ConversionMonitorHandlerRegistry {

    private static final Map<AEKeyType, IConversionMonitorHandler> REGISTRY = new IdentityHashMap<>();

    private ConversionMonitorHandlerRegistry() {}

    /**
     * Register a conversion monitor handler for the given stack type.
     */
    public static void register(@Nonnull AEKeyType type, @Nonnull IConversionMonitorHandler handler) {
        REGISTRY.put(type, handler);
    }

    /**
     * Get the handler for the given stack type.
     *
     * @return the handler, or null if no handler is registered for this type
     */
    @Nullable
    public static IConversionMonitorHandler getHandler(@Nonnull AEKeyType type) {
        return REGISTRY.get(type);
    }

    /**
     * Get the handler for the given stack's type.
     */
    @Nullable
    public static IConversionMonitorHandler getHandler(@Nonnull IAEStack<?> stack) {
        return REGISTRY.get(AEKeyType.fromLegacyType(stack.getStackTypeBase()));
    }

    /**
     * @return all registered handlers (unmodifiable)
     */
    @Nonnull
    public static Collection<IConversionMonitorHandler> getAllHandlers() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * @return true if a handler is registered for the given type
     */
    public static boolean hasHandler(@Nullable AEKeyType type) {
        return type != null && REGISTRY.containsKey(type);
    }
}

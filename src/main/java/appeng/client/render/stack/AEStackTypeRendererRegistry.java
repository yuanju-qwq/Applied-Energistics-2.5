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

package appeng.client.render.stack;

import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Client-side registry mapping each {@link IAEStackType} to its {@link IAEStackTypeRenderer}.
 * <p>
 * Renderers are registered during client initialization. All rendering call sites
 * should use this registry instead of {@code instanceof} checks to obtain the
 * appropriate renderer for a given stack type.
 */
@SideOnly(Side.CLIENT)
public final class AEStackTypeRendererRegistry {

    private static final Map<IAEStackType<?>, IAEStackTypeRenderer> REGISTRY = new IdentityHashMap<>();

    /**
     * Fallback renderer that uses {@link IAEStack#asItemStackRepresentation()} for unknown types.
     */
    private static final IAEStackTypeRenderer FALLBACK = new FallbackStackRenderer();

    private AEStackTypeRendererRegistry() {}

    /**
     * Register a renderer for the given stack type.
     *
     * @param type     the stack type
     * @param renderer the renderer instance
     */
    public static void register(@Nonnull IAEStackType<?> type, @Nonnull IAEStackTypeRenderer renderer) {
        REGISTRY.put(type, renderer);
    }

    /**
     * Get the renderer for the given stack type.
     * Returns a fallback renderer if no specific renderer is registered.
     *
     * @param type the stack type
     * @return the renderer (never null)
     */
    @Nonnull
    public static IAEStackTypeRenderer getRenderer(@Nonnull IAEStackType<?> type) {
        IAEStackTypeRenderer renderer = REGISTRY.get(type);
        return renderer != null ? renderer : FALLBACK;
    }

    /**
     * Get the renderer for the given stack.
     * Returns a fallback renderer if no specific renderer is registered.
     *
     * @param stack the AE stack
     * @return the renderer (never null)
     */
    @Nonnull
    public static IAEStackTypeRenderer getRenderer(@Nonnull IAEStack<?> stack) {
        return getRenderer(stack.getStackTypeBase());
    }

    /**
     * Check whether a renderer is registered for the given type.
     */
    public static boolean hasRenderer(@Nullable IAEStackType<?> type) {
        return type != null && REGISTRY.containsKey(type);
    }
}

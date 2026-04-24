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

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;

/**
 * Client-side renderer for a specific {@link appeng.api.storage.data.IAEStackType}.
 * <p>
 * Each registered stack type should have a corresponding renderer that knows how to:
 * <ul>
 *   <li>Render its icon in GUI (16x16)</li>
 *   <li>Render in 2D for TESR (monitors, panels)</li>
 *   <li>Format stack sizes for overlay text</li>
 *   <li>Build tooltip lines</li>
 *   <li>Provide display name</li>
 *   <li>Provide a JEI-compatible ingredient object</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public interface IAEStackTypeRenderer {

    // ========== GUI Icon Rendering ==========

    /**
     * Render the stack's 16x16 icon at the given GUI position.
     *
     * @param mc    Minecraft instance
     * @param stack the AE stack to render
     * @param x     left position
     * @param y     top position
     */
    void renderIcon(@Nonnull Minecraft mc, @Nonnull IAEStack<?> stack, int x, int y);

    // ========== TESR 2D Rendering ==========

    /**
     * Render the stack in 2D for TESR usage (e.g., storage monitors, conversion monitors).
     *
     * @param stack the AE stack to render
     * @param scale scale factor for the icon
     */
    void render2d(@Nonnull IAEStack<?> stack, float scale);

    /**
     * Render the stack in 2D with amount text below it (for TESR monitors).
     *
     * @param stack   the AE stack to render
     * @param scale   scale factor for the icon
     * @param spacing vertical spacing between icon and amount text
     */
    void render2dWithAmount(@Nonnull IAEStack<?> stack, float scale, float spacing);

    /**
     * Render the stack in 2D with a rate indicator text below it (for TESR rate monitors).
     * <p>
     * Default implementation delegates to {@link #render2d} followed by text rendering.
     *
     * @param stack    the AE stack to render
     * @param scale    scale factor for the icon
     * @param spacing  vertical spacing between icon and rate text
     * @param rateText formatted rate text to display (e.g., "+114/s")
     * @param color    ARGB color for the rate text
     */
    default void render2dWithRate(@Nonnull IAEStack<?> stack, float scale, float spacing,
                                  @Nonnull String rateText, int color) {
        render2d(stack, scale);

        final net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        final int width = fr.getStringWidth(rateText);

        net.minecraft.client.renderer.GlStateManager.translate(0.0f, spacing, 0);
        net.minecraft.client.renderer.GlStateManager.scale(1.0f / 62.0f, 1.0f / 62.0f, 1.0f / 62.0f);
        net.minecraft.client.renderer.GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
        fr.drawString(rateText, 0, 0, color);
    }

    // ========== Stack Size Formatting ==========

    /**
     * Format the stack size for terminal overlay display.
     *
     * @param size      raw stack size
     * @param largeFont whether the terminal uses large font mode
     * @return formatted string (e.g., "1.5K" for items, "1.5" for fluids in buckets)
     */
    @Nonnull
    String formatStackSize(long size, boolean largeFont);

    // ========== GUI Stack Size Rendering ==========

    /**
     * Render the stack size overlay text at the given GUI position.
     * <p>
     * This handles the formatted number display (e.g., "1.5K" for items, "1.500" for fluids),
     * as well as craftable indicators. Each type renders its own format.
     *
     * @param fontRenderer the font renderer to use
     * @param stack        the AE stack (may be null, in which case nothing is rendered)
     * @param xPos         left position of the slot
     * @param yPos         top position of the slot
     */
    void renderStackSize(@Nonnull FontRenderer fontRenderer, @Nullable IAEStack<?> stack, int xPos, int yPos);

    // ========== Tooltip ==========

    /**
     * Build the tooltip lines for this stack.
     * Should include the display name, stack-specific info, but NOT the
     * common AE info (quantity, craftable) — those are appended by the caller.
     *
     * @param stack the AE stack
     * @return tooltip lines
     */
    @Nonnull
    List<String> buildBaseTooltip(@Nonnull IAEStack<?> stack);

    // ========== Display Name ==========

    /**
     * Get the localized display name for this stack.
     *
     * @param stack the AE stack
     * @return display name
     */
    @Nonnull
    String getDisplayName(@Nonnull IAEStack<?> stack);

    // ========== JEI Integration ==========

    /**
     * Get the JEI-compatible ingredient object for this stack.
     * For items: {@code ItemStack}, for fluids: {@code FluidStack}.
     *
     * @param stack the AE stack
     * @return the native ingredient object, or null if not applicable
     */
    @Nullable
    Object getIngredient(@Nonnull IAEStack<?> stack);
}

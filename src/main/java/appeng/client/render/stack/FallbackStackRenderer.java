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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;
import appeng.client.render.TesrRenderHelper;
import appeng.core.AEConfig;
import appeng.util.ReadableNumberConverter;

/**
 * Fallback renderer for unknown stack types.
 * Uses {@link IAEStack#asItemStackRepresentation()} to render as an item.
 */
@SideOnly(Side.CLIENT)
final class FallbackStackRenderer implements IAEStackTypeRenderer {

    @Override
    public void renderIcon(@Nonnull Minecraft mc, @Nonnull IAEStack<?> stack, int x, int y) {
        ItemStack displayStack = stack.asItemStackRepresentation();
        if (!displayStack.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().zLevel = 100.0F;
            mc.getRenderItem().renderItemAndEffectIntoGUI(displayStack, x, y);
            mc.getRenderItem().zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    @Override
    public void render2d(@Nonnull IAEStack<?> stack, float scale) {
        ItemStack displayStack = stack.asItemStackRepresentation();
        TesrRenderHelper.renderItem2d(displayStack, scale);
    }

    @Override
    public void render2dWithAmount(@Nonnull IAEStack<?> stack, float scale, float spacing) {
        render2d(stack, scale);

        final long stackSize = stack.getStackSize();
        final String renderedStackSize = ReadableNumberConverter.INSTANCE.toWideReadableForm(stackSize);

        final net.minecraft.client.gui.FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        final int width = fr.getStringWidth(renderedStackSize);
        GlStateManager.translate(0.0f, spacing, 0);
        GlStateManager.scale(1.0f / 62.0f, 1.0f / 62.0f, 1.0f / 62.0f);
        GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
        fr.drawString(renderedStackSize, 0, 0, 0);
    }

    @Nonnull
    @Override
    public String formatStackSize(long size, boolean largeFont) {
        if (largeFont) {
            return ReadableNumberConverter.INSTANCE.toSlimReadableForm(size);
        } else {
            return ReadableNumberConverter.INSTANCE.toWideReadableForm(size);
        }
    }

    @Override
    public void renderStackSize(@Nonnull FontRenderer fontRenderer, @Nullable IAEStack<?> stack, int xPos, int yPos) {
        if (stack == null || stack.getStackSize() <= 0) {
            return;
        }
        final boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();
        final float scaleFactor = largeFont ? 0.85f : 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = largeFont ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        final String stackSize = formatStackSize(stack.getStackSize(), largeFont);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
        final int x = (int) (((float) xPos + offset + 16.0f
                - fontRenderer.getStringWidth(stackSize) * scaleFactor) * inverseScaleFactor);
        final int y = (int) (((float) yPos + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(stackSize, x, y, 16777215);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    @Nonnull
    @Override
    public List<String> buildBaseTooltip(@Nonnull IAEStack<?> stack) {
        ItemStack repr = stack.asItemStackRepresentation();
        if (!repr.isEmpty()) {
            return new ArrayList<>(Collections.singletonList(repr.getDisplayName()));
        }
        return new ArrayList<>();
    }

    @Nonnull
    @Override
    public String getDisplayName(@Nonnull IAEStack<?> stack) {
        return stack.asItemStackRepresentation().getDisplayName();
    }

    @Nullable
    @Override
    public Object getIngredient(@Nonnull IAEStack<?> stack) {
        ItemStack repr = stack.asItemStackRepresentation();
        return repr.isEmpty() ? null : repr;
    }
}

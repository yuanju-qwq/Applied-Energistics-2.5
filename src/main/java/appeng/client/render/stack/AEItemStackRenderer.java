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
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.render.TesrRenderHelper;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.util.ISlimReadableNumberConverter;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;

/**
 * Client-side renderer for {@link appeng.util.item.AEItemStackType}.
 */
@SideOnly(Side.CLIENT)
public final class AEItemStackRenderer implements IAEStackTypeRenderer {

    public static final AEItemStackRenderer INSTANCE = new AEItemStackRenderer();

    private static final ISlimReadableNumberConverter SLIM_CONVERTER = ReadableNumberConverter.INSTANCE;
    private static final IWideReadableNumberConverter WIDE_CONVERTER = ReadableNumberConverter.INSTANCE;

    private AEItemStackRenderer() {}

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
        ItemStack renderStack = stack.asItemStackRepresentation();
        TesrRenderHelper.renderItem2d(renderStack, scale);
    }

    @Override
    public void render2dWithAmount(@Nonnull IAEStack<?> stack, float scale, float spacing) {
        render2d(stack, scale);

        final long stackSize = stack.getStackSize();
        final String renderedStackSize = WIDE_CONVERTER.toWideReadableForm(stackSize);

        final FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
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
            return SLIM_CONVERTER.toSlimReadableForm(size);
        } else {
            return WIDE_CONVERTER.toWideReadableForm(size);
        }
    }

    @Override
    public void renderStackSize(@Nonnull FontRenderer fontRenderer, @Nullable IAEStack<?> stack, int xPos, int yPos) {
        if (stack == null) {
            return;
        }
        final boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();
        final float scaleFactor = largeFont ? 0.85f : 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = largeFont ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        if ((stack.getStackSize() == 0 || GuiScreen.isAltKeyDown()) && stack.isCraftable()) {
            final String craftLabelText = largeFont
                    ? GuiText.LargeFontCraft.getLocal()
                    : GuiText.SmallFontCraft.getLocal();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
            final int x = (int) (((float) xPos + offset + 16.0f
                    - fontRenderer.getStringWidth(craftLabelText) * scaleFactor) * inverseScaleFactor);
            final int y = (int) (((float) yPos + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
            fontRenderer.drawStringWithShadow(craftLabelText, x, y, 16777215);
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
        } else if (stack.getStackSize() > 0) {
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
        }

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    @Nonnull
    @Override
    public List<String> buildBaseTooltip(@Nonnull IAEStack<?> stack) {
        List<String> lines = new ArrayList<>();
        if (stack instanceof IAEItemStack itemStack) {
            ItemStack is = itemStack.createItemStack();
            try {
                lines.addAll(is.getTooltip(
                        Minecraft.getMinecraft().player,
                        Minecraft.getMinecraft().gameSettings.advancedItemTooltips
                                ? net.minecraft.client.util.ITooltipFlag.TooltipFlags.ADVANCED
                                : net.minecraft.client.util.ITooltipFlag.TooltipFlags.NORMAL));
            } catch (Exception ignored) {
            }
        } else {
            ItemStack repr = stack.asItemStackRepresentation();
            if (!repr.isEmpty()) {
                lines.add(repr.getDisplayName());
            }
        }
        return lines;
    }

    @Nonnull
    @Override
    public String getDisplayName(@Nonnull IAEStack<?> stack) {
        return stack.asItemStackRepresentation().getDisplayName();
    }

    @Nullable
    @Override
    public Object getIngredient(@Nonnull IAEStack<?> stack) {
        if (stack instanceof IAEItemStack itemStack) {
            return itemStack.createItemStack();
        }
        ItemStack repr = stack.asItemStackRepresentation();
        return repr.isEmpty() ? null : repr;
    }
}

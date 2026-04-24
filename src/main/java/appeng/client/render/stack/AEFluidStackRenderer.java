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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AEConfig;
import appeng.util.ISlimReadableNumberConverter;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;

/**
 * Client-side renderer for {@link appeng.fluids.util.AEFluidStackType}.
 */
@SideOnly(Side.CLIENT)
public final class AEFluidStackRenderer implements IAEStackTypeRenderer {

    public static final AEFluidStackRenderer INSTANCE = new AEFluidStackRenderer();

    private static final String[] FLUID_NUMBER_FORMATS = new String[] { "#.000", "#.00", "#.0", "#" };
    private static final ISlimReadableNumberConverter SLIM_CONVERTER = ReadableNumberConverter.INSTANCE;
    private static final IWideReadableNumberConverter WIDE_CONVERTER = ReadableNumberConverter.INSTANCE;

    private AEFluidStackRenderer() {}

    // ========== GUI Icon Rendering ==========

    @Override
    public void renderIcon(@Nonnull Minecraft mc, @Nonnull IAEStack<?> stack, int x, int y) {
        if (!(stack instanceof IAEFluidStack fluidStack)) {
            return;
        }
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) {
            return;
        }

        GlStateManager.disableLighting();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());

        int color = fluid.getColor();
        float red = (color >> 16 & 0xFF) / 255.0f;
        float green = (color >> 8 & 0xFF) / 255.0f;
        float blue = (color & 0xFF) / 255.0f;
        GlStateManager.color(red, green, blue, 1.0f);

        drawSprite(x, y, 16, 16, sprite);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableLighting();
    }

    private static void drawSprite(int x, int y, int w, int h, TextureAtlasSprite sprite) {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(7, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x, y + h, 0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buf.pos(x + w, y + h, 0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buf.pos(x + w, y, 0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buf.pos(x, y, 0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        tessellator.draw();
    }

    // ========== TESR 2D Rendering ==========

    @Override
    public void render2d(@Nonnull IAEStack<?> stack, float scale) {
        if (!(stack instanceof IAEFluidStack fluidStack)) {
            return;
        }
        FluidStack renderStack = fluidStack.getFluidStack();
        if (renderStack == null) {
            return;
        }

        GlStateManager.pushMatrix();
        int color = renderStack.getFluid().getColor(renderStack);
        float r = (color >> 16 & 255) / 255.0f;
        float g = (color >> 8 & 255) / 255.0f;
        float b = (color & 255) / 255.0f;
        TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite(renderStack.getFluid().getStill(renderStack).toString());
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        float width = 0.4f;
        float height = 0.4f;
        float alpha = 1.0f;
        float z = 0.0001f;
        float x = -0.20f;
        float y = -0.25f;

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        double uMin = sprite.getInterpolatedU(16D - width * 16D);
        double uMax = sprite.getInterpolatedU(width * 16D);
        double vMin = sprite.getMinV();
        double vMax = sprite.getInterpolatedV(height * 16D);
        buf.pos(x, y, z).tex(uMin, vMin).color(r, g, b, alpha).endVertex();
        buf.pos(x, y + height, z).tex(uMin, vMax).color(r, g, b, alpha).endVertex();
        buf.pos(x + width, y + height, z).tex(uMax, vMax).color(r, g, b, alpha).endVertex();
        buf.pos(x + width, y, z).tex(uMax, vMin).color(r, g, b, alpha).endVertex();

        tess.draw();
        GlStateManager.enableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1F, 1F, 1F, 1F);
        GlStateManager.popMatrix();
    }

    @Override
    public void render2dWithAmount(@Nonnull IAEStack<?> stack, float scale, float spacing) {
        render2d(stack, scale);

        final long stackSize = stack.getStackSize() / 1000;
        final String renderedStackSize = WIDE_CONVERTER.toWideReadableForm(stackSize) + "B";

        final FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        final int width = fr.getStringWidth(renderedStackSize);
        GlStateManager.translate(0.0f, spacing, 0);
        GlStateManager.scale(1.0f / 62.0f, 1.0f / 62.0f, 1.0f / 62.0f);
        GlStateManager.translate(-0.5f * width, 0.0f, 0.5f);
        fr.drawString(renderedStackSize, 0, 0, 0);
    }

    // ========== Stack Size Formatting ==========

    @Nonnull
    @Override
    public String formatStackSize(long size, boolean largeFont) {
        if (size < 1000 * 100 && largeFont) {
            return getSlimFluidStackSize(size);
        } else if (size < 1000 * 1000 && !largeFont) {
            return getWideFluidStackSize(size);
        }

        if (largeFont) {
            return SLIM_CONVERTER.toSlimReadableForm(size / 1000);
        } else {
            return WIDE_CONVERTER.toWideReadableForm(size / 1000);
        }
    }

    private String getSlimFluidStackSize(long originalSize) {
        final int log = 1 + (int) Math.floor(Math.log10(originalSize)) / 2;
        return getFormattedFluidStackSize(originalSize, log);
    }

    private String getWideFluidStackSize(long originalSize) {
        final int log = (int) Math.floor(Math.log10(originalSize)) / 2;
        return getFormattedFluidStackSize(originalSize, log);
    }

    private String getFormattedFluidStackSize(long originalSize, int log) {
        final int index = Math.max(0, Math.min(3, log));
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        final DecimalFormat format = new DecimalFormat(FLUID_NUMBER_FORMATS[index]);
        format.setDecimalFormatSymbols(symbols);
        format.setRoundingMode(RoundingMode.DOWN);
        return format.format(originalSize / 1000d);
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

        if (stack.getStackSize() > 0) {
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

    // ========== Tooltip ==========

    @Nonnull
    @Override
    public List<String> buildBaseTooltip(@Nonnull IAEStack<?> stack) {
        List<String> lines = new ArrayList<>();
        if (stack instanceof IAEFluidStack fluidStack) {
            lines.add(fluidStack.getFluid().getLocalizedName(fluidStack.getFluidStack()));
        }
        return lines;
    }

    // ========== Display Name ==========

    @Nonnull
    @Override
    public String getDisplayName(@Nonnull IAEStack<?> stack) {
        if (stack instanceof IAEFluidStack fluidStack) {
            return fluidStack.getFluid().getLocalizedName(fluidStack.getFluidStack());
        }
        return stack.asItemStackRepresentation().getDisplayName();
    }

    // ========== JEI Integration ==========

    @Nullable
    @Override
    public Object getIngredient(@Nonnull IAEStack<?> stack) {
        if (stack instanceof IAEFluidStack fluidStack) {
            return fluidStack.getFluidStack();
        }
        return null;
    }
}

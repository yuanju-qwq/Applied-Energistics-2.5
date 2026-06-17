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

package appeng.client.mui.render;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.client.mui.key.AEKeyDisplayEntry;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;

/**
 * AEKey-only stack renderer for the new MUI bottom layer.
 */
@SideOnly(Side.CLIENT)
public final class AEKeyStackRenderer {

    private static final int ICON_SIZE = 16;

    private AEKeyStackRenderer() {
    }

    public static void renderIcon(final Minecraft mc, @Nullable final GenericStack stack, final int x, final int y) {
        if (stack != null) {
            renderIcon(mc, stack.what(), x, y);
        }
    }

    public static void renderIcon(final Minecraft mc, @Nullable final AEKeyDisplayEntry entry, final int x,
            final int y) {
        if (entry != null) {
            renderIcon(mc, entry.key(), x, y);
        }
    }

    public static void renderIcon(final Minecraft mc, @Nullable final AEKey key, final int x, final int y) {
        if (key == null) {
            return;
        }

        if (key instanceof AEItemKey itemKey) {
            renderItemIcon(mc, itemKey.toStack(), x, y);
            return;
        }

        if (key instanceof AEFluidKey fluidKey) {
            renderFluidIcon(mc, fluidKey.toStack(AEFluidKey.AMOUNT_BUCKET), x, y, ICON_SIZE, ICON_SIZE);
            return;
        }

        // Unknown key types need a dedicated AEKey renderer. Do not fall back to
        // legacy item-representation helpers because fluid keys use a dummy item
        // in old compatibility paths.
    }

    public static void renderOverlay(final Minecraft mc, @Nullable final GenericStack stack,
            final boolean craftable, final int x, final int y) {
        if (stack != null) {
            renderOverlay(mc, stack.what(), stack.amount(), craftable, x, y);
        }
    }

    public static void renderOverlay(final Minecraft mc, @Nullable final AEKeyDisplayEntry entry, final int x,
            final int y) {
        if (entry != null) {
            renderOverlay(mc, entry.key(), entry.amount(), entry.craftable(), x, y);
        }
    }

    public static void renderOverlay(final Minecraft mc, @Nullable final AEKey key, final long amount,
            final boolean craftable, final int x, final int y) {
        if (key == null) {
            return;
        }

        final FontRenderer fr = mc.fontRenderer;
        final boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();
        final float scale = largeFont ? 0.85f : 0.5f;
        final float invScale = 1.0f / scale;
        final int offset = largeFont ? 0 : -1;
        final boolean unicode = fr.getUnicodeFlag();

        fr.setUnicodeFlag(false);

        if ((amount == 0 || GuiScreen.isAltKeyDown()) && craftable) {
            final String craftLabel = largeFont
                    ? GuiText.LargeFontCraft.getLocal()
                    : GuiText.SmallFontCraft.getLocal();
            drawOverlayText(fr, craftLabel, x, y, scale, invScale, offset, 0xFFFFFF);
        } else if (amount > 0) {
            final AmountFormat amountFormat = largeFont
                    ? AmountFormat.PREVIEW_LARGE_FONT
                    : AmountFormat.PREVIEW_REGULAR;
            drawOverlayText(fr, key.getType().formatAmount(amount, amountFormat), x, y, scale, invScale, offset,
                    0xFFFFFF);
        }

        fr.setUnicodeFlag(unicode);
    }

    public static List<String> buildTooltip(@Nullable final GenericStack stack, final boolean craftable) {
        if (stack == null) {
            return new ArrayList<>();
        }
        return buildTooltip(stack.what(), stack.amount(), craftable);
    }

    public static List<String> buildTooltip(@Nullable final AEKeyDisplayEntry entry) {
        if (entry == null) {
            return new ArrayList<>();
        }
        return buildTooltip(entry.key(), entry.amount(), entry.craftable());
    }

    public static List<String> buildTooltip(@Nullable final AEKey key, final long amount, final boolean craftable) {
        final List<String> lines = new ArrayList<>();
        if (key == null) {
            return lines;
        }

        if (key instanceof AEItemKey itemKey) {
            final ItemStack displayStack = itemKey.toStack();
            if (!displayStack.isEmpty()) {
                final Minecraft mc = Minecraft.getMinecraft();
                final ITooltipFlag.TooltipFlags tooltipFlag = mc.gameSettings.advancedItemTooltips
                        ? ITooltipFlag.TooltipFlags.ADVANCED
                        : ITooltipFlag.TooltipFlags.NORMAL;
                lines.addAll(displayStack.getTooltip(mc.player, tooltipFlag));
            } else {
                lines.add(key.getDisplayName());
            }
        } else if (key instanceof AEFluidKey fluidKey) {
            final FluidStack fluidStack = fluidKey.toStack((int) Math.min(Math.max(amount, 1), Integer.MAX_VALUE));
            lines.add(fluidStack.getLocalizedName());
            final String modId = FluidRegistry.getModId(fluidStack);
            if (modId != null && Minecraft.getMinecraft().gameSettings.advancedItemTooltips) {
                lines.add(TextFormatting.DARK_GRAY + modId);
            }
        } else {
            lines.add(key.getDisplayName());
        }

        if (amount > 999) {
            lines.add(TextFormatting.GRAY + NumberFormat.getNumberInstance(Locale.US).format(amount));
        }

        if (craftable) {
            lines.add(TextFormatting.YELLOW + GuiText.Craftable.getLocal());
        }

        return lines;
    }

    public static Object getIngredient(@Nullable final AEKey key, final long amount) {
        if (key instanceof AEFluidKey fluidKey) {
            final int fluidAmount = (int) Math.min(Math.max(amount, 1), Integer.MAX_VALUE);
            return fluidKey.toStack(fluidAmount);
        }

        if (key instanceof AEItemKey itemKey) {
            final int stackSize = (int) Math.min(Math.max(amount, 1), key.getAmountPerOperation());
            return itemKey.toStack(stackSize);
        }

        return null;
    }

    private static void renderItemIcon(final Minecraft mc, final ItemStack stack, final int x, final int y) {
        if (stack.isEmpty()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableDepth();
        RenderHelper.enableGUIStandardItemLighting();
        mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
        RenderHelper.disableStandardItemLighting();
        GlStateManager.popMatrix();
    }

    private static void renderFluidIcon(final Minecraft mc, @Nullable final FluidStack stack, final int x, final int y,
            final int width, final int height) {
        if (stack == null || stack.getFluid() == null) {
            return;
        }

        final Fluid fluid = stack.getFluid();
        final ResourceLocation still = fluid.getStill(stack);
        if (still == null) {
            return;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        final TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(still.toString());
        final int color = fluid.getColor(stack);
        final float red = (color >> 16 & 255) / 255.0f;
        final float green = (color >> 8 & 255) / 255.0f;
        final float blue = (color & 255) / 255.0f;
        GlStateManager.color(red, green, blue, 1.0f);

        drawSprite(x, y, width, height, sprite);

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.enableBlend();
        GlStateManager.enableLighting();
    }

    private static void drawOverlayText(final FontRenderer fr, final String text, final int x, final int y,
            final float scale, final float invScale, final int offset, final int color) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        final int textX = (int) (((float) x + offset + ICON_SIZE - fr.getStringWidth(text) * scale) * invScale);
        final int textY = (int) (((float) y + offset + ICON_SIZE - 7.0f * scale) * invScale);
        fr.drawStringWithShadow(text, textX, textY, color);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    private static void drawSprite(final int x, final int y, final int width, final int height,
            final TextureAtlasSprite sprite) {
        final Tessellator tessellator = Tessellator.getInstance();
        final BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y + height, 0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
        buffer.pos(x + width, y, 0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
        buffer.pos(x, y, 0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
        tessellator.draw();
    }
}

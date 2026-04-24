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

package appeng.client.mui.widgets;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;
import appeng.client.render.stack.AEStackTypeRendererRegistry;
import appeng.client.render.stack.IAEStackTypeRenderer;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;

/**
 * 多类型栈渲染器。
 * <p>
 * 统一物品和流体（以及未来扩展类型）的图标渲染、数量叠加层和工具提示。
 * 独立于 GUI 框架，可在 MUI 和旧 GUI 系统中复用。
 * <p>
 * All type-specific rendering is delegated to {@link IAEStackTypeRenderer} instances
 * obtained from {@link AEStackTypeRendererRegistry}.
 */
@SideOnly(Side.CLIENT)
public final class MUIStackRenderer {

    private MUIStackRenderer() {
    }

    // ========== 图标渲染 ==========

    /**
     * 在指定位置渲染任意 AE 栈的 16x16 图标。
     * 自动根据栈类型选择渲染方式（物品/流体/其他）。
     */
    public static void renderStackIcon(Minecraft mc, @Nullable IAEStack<?> stack, int x, int y) {
        if (stack == null) {
            return;
        }
        AEStackTypeRendererRegistry.getRenderer(stack).renderIcon(mc, stack, x, y);
    }

    /**
     * 渲染物品图标（保留以供直接调用）。
     */
    public static void renderItemIcon(Minecraft mc, ItemStack itemStack, int x, int y) {
        appeng.client.render.stack.AEItemStackRenderer.INSTANCE.renderIcon(
                mc,
                appeng.util.item.AEItemStackType.INSTANCE.createStack(itemStack),
                x, y);
    }

    // ========== 数量叠加层 ==========

    /**
     * 渲染栈的数量叠加层。
     *
     * @param stack            AE 栈
     * @param x                左上角 X
     * @param y                左上角 Y
     * @param showAmount       是否显示数量
     * @param showCraftable    是否显示 "Craft" 标签
     */
    public static void renderStackOverlay(Minecraft mc, @Nullable IAEStack<?> stack, int x, int y,
            boolean showAmount, boolean showCraftable) {
        if (stack == null) {
            return;
        }

        FontRenderer fr = mc.fontRenderer;
        boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();
        float scale = largeFont ? 0.85f : 0.5f;
        float invScale = 1.0f / scale;
        int offset = largeFont ? 0 : -1;

        boolean unicode = fr.getUnicodeFlag();
        fr.setUnicodeFlag(false);

        if ((stack.getStackSize() == 0 || GuiScreen.isAltKeyDown()) && stack.isCraftable() && showCraftable) {
            String craftLabel = largeFont
                    ? GuiText.LargeFontCraft.getLocal()
                    : GuiText.SmallFontCraft.getLocal();
            renderOverlayText(fr, craftLabel, x, y, scale, invScale, offset, 0xFFFFFF);
        } else if (stack.getStackSize() > 0 && showAmount) {
            IAEStackTypeRenderer renderer = AEStackTypeRendererRegistry.getRenderer(stack);
            String sizeStr = renderer.formatStackSize(stack.getStackSize(), largeFont);
            renderOverlayText(fr, sizeStr, x, y, scale, invScale, offset, 0xFFFFFF);
        }

        fr.setUnicodeFlag(unicode);
    }

    private static void renderOverlayText(FontRenderer fr, String text, int x, int y,
            float scale, float invScale, int offset, int color) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        int textX = (int) (((float) x + offset + 16.0f - fr.getStringWidth(text) * scale) * invScale);
        int textY = (int) (((float) y + offset + 16.0f - 7.0f * scale) * invScale);
        fr.drawStringWithShadow(text, textX, textY, color);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    // ========== 工具提示 ==========

    /**
     * 为任意 AE 栈构建工具提示。
     */
    public static List<String> buildTooltip(@Nullable IAEStack<?> stack) {
        List<String> lines = new ArrayList<>();
        if (stack == null) {
            return lines;
        }

        // Delegate base tooltip to the type-specific renderer
        IAEStackTypeRenderer renderer = AEStackTypeRendererRegistry.getRenderer(stack);
        lines.addAll(renderer.buildBaseTooltip(stack));

        if (stack.getStackSize() > 999) {
            String formatted = NumberFormat.getNumberInstance(Locale.US).format(stack.getStackSize());
            lines.add("\u00a77" + formatted);
        }

        if (stack.isCraftable()) {
            lines.add("\u00a7e" + GuiText.Craftable.getLocal());
        }

        return lines;
    }

    /**
     * 获取栈的显示名称。
     */
    public static String getDisplayName(@Nullable IAEStack<?> stack) {
        if (stack == null) {
            return "";
        }
        return AEStackTypeRendererRegistry.getRenderer(stack).getDisplayName(stack);
    }
}

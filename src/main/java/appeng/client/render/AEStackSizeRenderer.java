/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.client.render;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;

import appeng.api.storage.data.IAEStack;
import appeng.client.render.stack.AEStackTypeRendererRegistry;
import appeng.client.render.stack.IAEStackTypeRenderer;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;

public class AEStackSizeRenderer {

    public void renderStackSize(FontRenderer fontRenderer, Object stack, int xPos, int yPos) {
        if (stack instanceof IAEStack<?> aeStack) {
            renderStackSizeInternal(fontRenderer, aeStack, xPos, yPos);
        }
    }

    private void renderStackSizeInternal(FontRenderer fontRenderer, IAEStack<?> stack, int xPos, int yPos) {
        if (stack == null) {
            return;
        }

        final float scaleFactor = AEConfig.instance().useTerminalUseLargeFont() ? 0.85f : 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = AEConfig.instance().useTerminalUseLargeFont() ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        try {
            boolean isCraftable = stack.isCraftable();
            long stackSize = stack.getStackSize();
            boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();

            String displayText;

            if ((stackSize == 0 || GuiScreen.isAltKeyDown()) && isCraftable) {
                displayText = largeFont
                        ? GuiText.LargeFontCraft.getLocal()
                        : GuiText.SmallFontCraft.getLocal();
            } else if (stackSize > 0) {
                IAEStackTypeRenderer renderer = AEStackTypeRendererRegistry.getRenderer(stack);
                displayText = renderer.formatStackSize(stackSize, largeFont);
            } else {
                return;
            }

            renderText(fontRenderer, displayText, xPos, yPos, scaleFactor, inverseScaleFactor, offset);

        } finally {
            fontRenderer.setUnicodeFlag(unicodeFlag);
        }
    }

    private void renderText(FontRenderer fontRenderer, String text, int xPos, int yPos,
            float scaleFactor, float inverseScaleFactor, int offset) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
        final int X = (int) (((float) xPos + offset + 16.0f
                - fontRenderer.getStringWidth(text) * scaleFactor) * inverseScaleFactor);
        final int Y = (int) (((float) yPos + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(text, X, Y, 16777215);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

}

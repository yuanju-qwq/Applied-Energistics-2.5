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

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 通用绘制控件。
 * <p>
 * 用于显示纹理图标和/或文字标签。
 * 替代旧的 GuiImgLabel，提供更灵活的绘制能力。
 */
public class MUIDrawableWidget implements IMUIWidget {

    private int x;
    private int y;
    private boolean visible = true;

    @Nullable
    private ResourceLocation texture;
    private int texU;
    private int texV;
    private int texWidth = 16;
    private int texHeight = 16;

    @Nullable
    private String label;
    private int labelColor = AEMUITheme.COLOR_TEXT;
    private boolean labelShadow = false;

    public MUIDrawableWidget(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        if (this.texture != null) {
            Minecraft mc = Minecraft.getMinecraft();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(this.texture);
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY,
                    this.texU, this.texV, this.texWidth, this.texHeight, 256, 256);
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        if (!this.visible || this.label == null) {
            return;
        }

        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (this.labelShadow) {
            fr.drawStringWithShadow(this.label, this.x, this.y, this.labelColor);
        } else {
            fr.drawString(this.label, this.x, this.y, this.labelColor);
        }
    }

    // ========== 属性 ==========

    public MUIDrawableWidget setTexture(@Nullable ResourceLocation texture, int u, int v, int w, int h) {
        this.texture = texture;
        this.texU = u;
        this.texV = v;
        this.texWidth = w;
        this.texHeight = h;
        return this;
    }

    public MUIDrawableWidget setLabel(@Nullable String label) {
        this.label = label;
        return this;
    }

    public MUIDrawableWidget setLabelColor(int color) {
        this.labelColor = color;
        return this;
    }

    public MUIDrawableWidget setLabelShadow(boolean shadow) {
        this.labelShadow = shadow;
        return this;
    }

    public MUIDrawableWidget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUIDrawableWidget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

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
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fluids.Fluid;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;
import appeng.fluids.util.IAEFluidTank;

/**
 * MUI 流体槽位控件。
 * <p>
 * 显示 {@link IAEFluidTank} 中指定槽位的流体，
 * 支持流体图标渲染和工具提示。
 */
public class MUIFluidSlotWidget implements IMUIWidget {

    private final IAEFluidTank tank;
    private final int slot;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    public MUIFluidSlotWidget(IAEFluidTank tank, int slot, int x, int y) {
        this(tank, slot, x, y, AEMUITheme.SLOT_SIZE - 2, AEMUITheme.SLOT_SIZE - 2);
    }

    public MUIFluidSlotWidget(IAEFluidTank tank, int slot, int x, int y, int width, int height) {
        this.tank = tank;
        this.slot = slot;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        IAEFluidStack fs = this.getFluidStack();
        if (fs != null) {
            renderFluid(Minecraft.getMinecraft(), screenX, screenY, this.width, this.height, fs);
        }
    }

    /**
     * 渲染流体图标。
     */
    public static void renderFluid(Minecraft mc, int x, int y, int w, int h, IAEFluidStack fluidStack) {
        Fluid fluid = fluidStack.getFluid();
        if (fluid == null) {
            return;
        }

        GlStateManager.disableLighting();
        GlStateManager.disableBlend();

        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());

        int color = fluid.getColor();
        float red = (color >> 16 & 255) / 255.0f;
        float green = (color >> 8 & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        GlStateManager.color(red, green, blue, 1.0f);

        drawSprite(x, y, w, h, sprite);

        GlStateManager.enableLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ========== 数据 ==========

    @Nullable
    public IAEFluidStack getFluidStack() {
        return this.tank.getFluidInSlot(this.slot);
    }

    /**
     * 使用 Tessellator 直接渲染 TextureAtlasSprite。
     */
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

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }
}

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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;

import appeng.client.gui.widgets.IScrollSource;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 滚动条控件。
 * <p>
 * 支持鼠标拖拽和滚轮滚动，可设置最小/最大滚动范围和每页大小。
 */
public class MUIScrollBar implements IMUIWidget, IScrollSource {

    private int x;
    private int y;
    private int width = 12;
    private int height = 16;
    private int pageSize = 1;

    private int minScroll = 0;
    private int maxScroll = 0;
    private int currentScroll = 0;

    private boolean dragging = false;

    public MUIScrollBar(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation(
                "minecraft", "textures/gui/container/creative_inventory/tabs.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        if (this.getRange() == 0) {
            Gui.drawModalRectWithCustomSizedTexture(
                    screenX, screenY, 232 + this.width, 0, this.width, 15, 256, 256);
        } else {
            int offset = (this.currentScroll - this.minScroll) * (this.height - 15) / this.getRange();
            Gui.drawModalRectWithCustomSizedTexture(
                    screenX, offset + screenY, 232, 0, this.width, 15, 256, 256);
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (mouseButton == 0 && isMouseOver(localX, localY)) {
            this.dragging = true;
            applyDragPosition(localY);
            return true;
        }
        return false;
    }

    /**
     * 鼠标拖拽时调用（由面板转发）。
     */
    public void mouseDragged(int localY) {
        if (this.dragging) {
            applyDragPosition(localY);
        }
    }

    /**
     * 鼠标释放时调用。
     */
    public void mouseReleased() {
        this.dragging = false;
    }

    /**
     * 鼠标滚轮滚动。
     *
     * @param delta 滚轮方向（正=向上, 负=向下）
     */
    public void wheel(int delta) {
        this.currentScroll -= delta;
        this.clamp();
    }

    private void applyDragPosition(int localY) {
        if (this.getRange() == 0) {
            return;
        }
        int relativeY = localY - this.y;
        float ratio = (float) relativeY / (float) (this.height - 15);
        this.currentScroll = this.minScroll + Math.round(ratio * this.getRange());
        this.clamp();
    }

    private boolean isMouseOver(int localX, int localY) {
        return localX >= this.x && localX < this.x + this.width
                && localY >= this.y && localY < this.y + this.height;
    }

    private void clamp() {
        if (this.currentScroll < this.minScroll) {
            this.currentScroll = this.minScroll;
        }
        if (this.currentScroll > this.maxScroll) {
            this.currentScroll = this.maxScroll;
        }
    }

    private int getRange() {
        return this.maxScroll - this.minScroll;
    }

    // ========== 属性 ==========

    @Override
    public int getCurrentScroll() {
        return this.currentScroll;
    }

    public MUIScrollBar setCurrentScroll(int scroll) {
        this.currentScroll = scroll;
        this.clamp();
        return this;
    }

    public MUIScrollBar setRange(int min, int max, int pageSize) {
        this.minScroll = min;
        this.maxScroll = max;
        this.pageSize = pageSize;
        this.clamp();
        return this;
    }

    public int getPageSize() {
        return this.pageSize;
    }

    public MUIScrollBar setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public MUIScrollBar setHeight(int height) {
        this.height = height;
        return this;
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

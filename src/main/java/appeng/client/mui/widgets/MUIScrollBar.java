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
 * MUI scrollbar widget aligned with upstream {@code appeng.client.gui.widgets.Scrollbar}.
 * <p>
 * Supports mouse-wheel scrolling, drag with relative-offset, region-based track clicks
 * (page up / handle drag / page down) and repeated paging via {@link EventRepeater}.
 */
public class MUIScrollBar implements IMUIWidget, IScrollSource {

    private static final int HANDLE_HEIGHT = 15;

    private int x;
    private int y;
    private int width = 12;
    private int height = 16;
    private int pageSize = 1;

    private int minScroll = 0;
    private int maxScroll = 0;
    private int currentScroll = 0;

    private boolean dragging = false;
    private int dragYOffset = 0;

    private boolean visible = true;

    private final EventRepeater eventRepeater = new EventRepeater(250, 150);

    public MUIScrollBar() {
    }

    public MUIScrollBar(int x, int y, int height) {
        this.x = x;
        this.y = y;
        this.height = height;
    }

    // ========== Drawing ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(new net.minecraft.util.ResourceLocation(
                "minecraft", "textures/gui/container/creative_inventory/tabs.png"));
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        int handleYOffset = getHandleYOffset();
        int texU = getRange() == 0 ? 232 + this.width : 232;
        Gui.drawModalRectWithCustomSizedTexture(
                screenX, screenY + handleYOffset, texU, 0, this.width, HANDLE_HEIGHT, 256, 256);
    }

    // ========== Input events ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (mouseButton != 0 || !isMouseOver(localX, localY)) {
            return false;
        }

        if (getRange() == 0) {
            return true;
        }

        int relY = localY - this.y;
        int handleYOffset = getHandleYOffset();

        if (relY < handleYOffset) {
            pageUp();
            eventRepeater.repeat(this::pageUp);
        } else if (relY < handleYOffset + HANDLE_HEIGHT) {
            this.dragging = true;
            this.dragYOffset = relY - handleYOffset;
        } else {
            pageDown();
            eventRepeater.repeat(this::pageDown);
        }
        return true;
    }

    /**
     * Called by the panel when the mouse is dragged.
     */
    public void mouseDragged(int localY) {
        if (getRange() == 0 || !this.dragging || this.eventRepeater.isRepeating()) {
            return;
        }
        double handleUpperEdgeY = localY - this.y - this.dragYOffset;
        double availableHeight = this.height - HANDLE_HEIGHT;
        double position = Math.max(0.0, Math.min(1.0, handleUpperEdgeY / availableHeight));
        this.currentScroll = this.minScroll + (int) Math.round(position * getRange());
        this.clamp();
    }

    /**
     * Called by the panel when the mouse is released.
     */
    public void mouseReleased() {
        this.dragging = false;
        this.eventRepeater.stop();
    }

    /**
     * Called every tick to drive the {@link EventRepeater}.
     */
    public void tick() {
        this.eventRepeater.tick();
    }

    /**
     * Mouse wheel scroll.
     *
     * @param delta scroll direction (positive = up, negative = down)
     */
    public void wheel(int delta) {
        if (getRange() == 0) {
            return;
        }
        delta = Math.max(Math.min(-delta, 1), -1);
        this.currentScroll += delta * this.pageSize;
        this.clamp();
    }

    private void pageUp() {
        this.currentScroll -= this.pageSize;
        this.clamp();
    }

    private void pageDown() {
        this.currentScroll += this.pageSize;
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

    int getRange() {
        return this.maxScroll - this.minScroll;
    }

    int getHandleYOffset() {
        if (getRange() == 0) {
            return 0;
        }
        int availableHeight = this.height - HANDLE_HEIGHT;
        return (this.currentScroll - this.minScroll) * availableHeight / getRange();
    }

    // ========== Properties ==========

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
        if (this.minScroll > this.maxScroll) {
            this.maxScroll = this.minScroll;
        }
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

    // ========== GuiScrollbar API compatibility ==========

    public int getLeft() {
        return this.x;
    }

    public MUIScrollBar setLeft(int v) {
        this.x = v;
        return this;
    }

    public int getTop() {
        return this.y;
    }

    public MUIScrollBar setTop(int v) {
        this.y = v;
        return this;
    }

    public int getWidth() {
        return this.width;
    }

    public MUIScrollBar setWidth(int v) {
        this.width = v;
        return this;
    }

    public int getHeight() {
        return this.height;
    }

    // ========== Visibility ==========

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    // ========== Legacy GuiScrollbar bridge ==========

    /**
     * Legacy draw entry point — called from panel drawBG hooks.
     * Renders the scrollbar handle at its current panel-local (x, y) position
     * using the vanilla creative-inventory scrollbar texture.
     *
     * @deprecated Prefer registering as an IMUIWidget via addWidget().
     */
    @Deprecated
    public void draw(AEBasePanel panel) {
        panel.bindTexture("minecraft", "gui/container/creative_inventory/tabs.png");
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int handleYOffset = getHandleYOffset();
        int texU = getRange() == 0 ? 232 + this.width : 232;
        Gui.drawModalRectWithCustomSizedTexture(
                this.x, this.y + handleYOffset, texU, 0, this.width, HANDLE_HEIGHT, 256, 256);
    }

    /**
     * Legacy click entry point — converts absolute screen coordinates to
     * panel-local and delegates to {@link #mouseClicked(int, int, int)}.
     *
     * @deprecated Prefer using mouseClicked via the IMUIWidget system.
     */
    @Deprecated
    public void click(AEBasePanel panel, int x, int y) {
        this.mouseClicked(x, y, 0);
    }
}
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

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 按钮控件。
 * <p>
 * 支持自定义图标渲染器（{@link IIconRenderer}）和点击回调。
 * 可用于替代旧的 GuiImgButton 和通用按钮。
 */
public class MUIButtonWidget implements IMUIWidget {

    /**
     * 图标渲染器接口。
     */
    @FunctionalInterface
    public interface IIconRenderer {
        /**
         * 在按钮区域内绘制图标。
         *
         * @param mc      Minecraft 实例
         * @param screenX 按钮在屏幕中的左上角 X
         * @param screenY 按钮在屏幕中的左上角 Y
         * @param width   按钮宽度
         * @param height  按钮高度
         * @param hovered 是否被鼠标悬停
         */
        void render(Minecraft mc, int screenX, int screenY, int width, int height, boolean hovered);
    }

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean halfSize = false;

    @Nullable
    private IIconRenderer iconRenderer;
    @Nullable
    private Consumer<MUIButtonWidget> onClick;
    @Nullable
    private String tooltip;

    private boolean hovered = false;

    public MUIButtonWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public MUIButtonWidget(int x, int y) {
        this(x, y, 16, 16);
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

        int finalW = this.halfSize ? this.width / 2 : this.width;
        int finalH = this.halfSize ? this.height / 2 : this.height;

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + finalW && mouseY < screenY + finalH;

        if (!this.enabled) {
            GlStateManager.color(0.5f, 0.5f, 0.5f, 1.0f);
        } else {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }

        Minecraft mc = Minecraft.getMinecraft();

        if (this.halfSize) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(screenX, screenY, 0);
            GlStateManager.scale(0.5f, 0.5f, 1.0f);

            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 256 - this.width, 256 - this.height,
                    this.width, this.height, 256, 256);

            if (this.iconRenderer != null) {
                this.iconRenderer.render(mc, 0, 0, this.width, this.height, this.hovered);
            }

            GlStateManager.popMatrix();
        } else {
            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY,
                    256 - this.width, 256 - this.height, this.width, this.height, 256, 256);

            if (this.iconRenderer != null) {
                this.iconRenderer.render(mc, screenX, screenY, this.width, this.height, this.hovered);
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        if (!this.visible || this.tooltip == null || !this.hovered) {
            return;
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible || !this.enabled) {
            return false;
        }

        int finalW = this.halfSize ? this.width / 2 : this.width;
        int finalH = this.halfSize ? this.height / 2 : this.height;

        if (localX >= this.x && localY >= this.y
                && localX < this.x + finalW && localY < this.y + finalH) {
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public MUIButtonWidget setIconRenderer(@Nullable IIconRenderer renderer) {
        this.iconRenderer = renderer;
        return this;
    }

    public MUIButtonWidget setOnClick(@Nullable Consumer<MUIButtonWidget> onClick) {
        this.onClick = onClick;
        return this;
    }

    public MUIButtonWidget setTooltip(@Nullable String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public MUIButtonWidget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUIButtonWidget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public MUIButtonWidget setHalfSize(boolean halfSize) {
        this.halfSize = halfSize;
        return this;
    }

    public MUIButtonWidget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public boolean isVisible() {
        return this.visible;
    }

    @Nullable
    public String getTooltip() {
        return this.tooltip;
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

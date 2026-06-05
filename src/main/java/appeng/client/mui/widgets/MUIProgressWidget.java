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
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.container.interfaces.IProgressProvider;
import appeng.core.localization.GuiText;

/**
 * MUI 进度条控件。
 * <p>
 * 根据 {@link IProgressProvider} 提供的进度值，绘制水平或垂直的进度条。
 * 支持 tooltip（通过 {@link IMUITooltip} 接口）。
 * <p>
 * 该控件将 {@link Direction} 作为内部 enum 收敛进 MUI 命名空间，
 * 替代原 {@code appeng.client.gui.widgets.GuiProgressBar.Direction}。
 */
public class MUIProgressWidget implements IMUIWidget, IMUITooltip {

    /**
     * 进度条填充方向。
     * <p>
     * 完全等价于已废弃的 {@code appeng.client.gui.widgets.GuiProgressBar.Direction}。
     */
    public enum Direction {
        HORIZONTAL,
        VERTICAL
    }

    private final IProgressProvider source;
    private final ResourceLocation texture;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int fillU;
    private final int fillV;
    private final Direction direction;

    @Nullable
    private String title;
    @Nullable
    private String fullMsg;

    public MUIProgressWidget(IProgressProvider source, String texture,
            int x, int y, int u, int v, int width, int height, Direction direction) {
        this.source = source;
        this.texture = new ResourceLocation("appliedenergistics2", "textures/" + texture);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fillU = u;
        this.fillV = v;
        this.direction = direction;
    }

    public MUIProgressWidget(IProgressProvider source, String texture,
            int x, int y, int u, int v, int width, int height, Direction direction, @Nullable String title) {
        this(source, texture, x, y, u, v, width, height, direction);
        this.title = title;
    }

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(this.texture);

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        int max = this.source.getMaxProgress();
        int current = this.source.getCurrentProgress();

        if (max > 0) {
            switch (this.direction) {
                case HORIZONTAL:
                    int filledWidth = (int) ((float) this.width * ((float) current / (float) max));
                    panel.drawModalRectWithCustomSizedTexture(screenX, screenY,
                            this.fillU, this.fillV, filledWidth, this.height, 256, 256);
                    break;
                case VERTICAL:
                    int filledHeight = (int) ((float) this.height * ((float) current / (float) max));
                    int yOffset = this.height - filledHeight;
                    panel.drawModalRectWithCustomSizedTexture(screenX, screenY + yOffset,
                            this.fillU, this.fillV + yOffset, this.width, filledHeight, 256, 256);
                    break;
            }
        }
    }

    public void setFullMsg(@Nullable String msg) {
        this.fullMsg = msg;
    }

    @Nullable
    public String getFullMsg() {
        return this.fullMsg;
    }

    // ========== IMUITooltip ==========

    @Override
    public String getMessage() {
        if (this.fullMsg != null) {
            return this.fullMsg;
        }

        if (this.title != null) {
            return this.title + '\n' + this.source.getCurrentProgress() + ' '
                    + GuiText.Of.getLocal() + ' ' + this.source.getMaxProgress();
        }
        return null;
    }

    @Override
    public int xPos() {
        return this.x - 2;
    }

    @Override
    public int yPos() {
        return this.y - 2;
    }

    @Override
    public int getWidth() {
        return this.width + 4;
    }

    @Override
    public int getHeight() {
        return this.height + 4;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    // ========== Position getters ==========

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

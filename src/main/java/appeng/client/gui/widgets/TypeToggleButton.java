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

package appeng.client.gui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.storage.data.IAEStackType;
import appeng.core.localization.ButtonToolTips;

/**
 * ME 终端中用于启用/禁用某种存储类型显示的切换按钮。
 * <p>
 * 每种 {@link IAEStackType} 对应一个按钮，图标使用类型注册时提供的纹理坐标。
 */
public class TypeToggleButton extends GuiButton implements ITooltip {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private final IAEStackType<?> stackType;
    private boolean typeEnabled = true;

    public TypeToggleButton(int x, int y, IAEStackType<?> stackType) {
        super(0, x, y, 16, 16, "");
        this.stackType = stackType;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partial) {
        if (!this.visible) {
            return;
        }

        this.hovered = mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width
                && mouseY < this.y + this.height;

        if (this.typeEnabled) {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            GlStateManager.color(0.5f, 0.5f, 0.5f, 1.0f);
        }

        mc.renderEngine.bindTexture(STATES_TEXTURE);

        // 绘制按钮背景
        this.drawTexturedModalRect(this.x, this.y, 256 - 16, 256 - 16, 16, 16);

        // 绘制类型图标
        ResourceLocation typeTexture = this.stackType.getButtonTexture();
        if (typeTexture != null) {
            mc.renderEngine.bindTexture(typeTexture);
            int u = this.stackType.getButtonIconU();
            int v = this.stackType.getButtonIconV();
            this.drawTexturedModalRect(this.x, this.y, u, v, 16, 16);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public boolean isTypeEnabled() {
        return this.typeEnabled;
    }

    public void setTypeEnabled(boolean enabled) {
        this.typeEnabled = enabled;
    }

    public IAEStackType<?> getStackType() {
        return this.stackType;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.stackType.getDisplayName()).append("\n");

        if (this.typeEnabled) {
            sb.append(TextFormatting.GRAY).append(ButtonToolTips.Enable.getLocal()).append(TextFormatting.RESET);
        } else {
            sb.append(TextFormatting.GRAY).append(ButtonToolTips.Disabled.getLocal()).append(TextFormatting.RESET);
        }

        return sb.toString();
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }
}

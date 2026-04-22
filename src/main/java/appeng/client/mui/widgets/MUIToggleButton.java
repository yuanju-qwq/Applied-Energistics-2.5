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
import appeng.client.mui.IMUIWidget;

/**
 * MUI 切换按钮控件。
 * <p>
 * 两态按钮，点击时在 on/off 之间切换。
 * 每个状态使用不同的图标（通过 states.png 的纹理坐标指定）。
 */
public class MUIToggleButton implements IMUIWidget {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private final int iconIdxOn;
    private final int iconIdxOff;

    private boolean active = false;
    private boolean visible = true;
    private boolean hovered = false;

    @Nullable
    private String displayName;
    @Nullable
    private String displayHint;
    @Nullable
    private Consumer<MUIToggleButton> onToggle;

    public MUIToggleButton(int x, int y, int iconOn, int iconOff) {
        this.x = x;
        this.y = y;
        this.iconIdxOn = iconOn;
        this.iconIdxOff = iconOff;
    }

    public MUIToggleButton(int x, int y, int iconOn, int iconOff,
            String displayName, String displayHint) {
        this(x, y, iconOn, iconOff);
        this.displayName = displayName;
        this.displayHint = displayHint;
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
        Minecraft mc = Minecraft.getMinecraft();

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + 16 && mouseY < screenY + 16;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);

        // 背景
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 240, 240, 16, 16, 256, 256);

        // 图标
        int iconIdx = this.active ? this.iconIdxOn : this.iconIdxOff;
        int iconU = (iconIdx % 16) * 16;
        int iconV = (iconIdx / 16) * 16;
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, iconU, iconV, 16, 16, 256, 256);
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible) {
            return false;
        }
        if (localX >= this.x && localY >= this.y
                && localX < this.x + 16 && localY < this.y + 16) {
            this.active = !this.active;
            if (this.onToggle != null) {
                this.onToggle.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public boolean isActive() {
        return this.active;
    }

    public MUIToggleButton setActive(boolean active) {
        this.active = active;
        return this;
    }

    public MUIToggleButton setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUIToggleButton setOnToggle(@Nullable Consumer<MUIToggleButton> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    public MUIToggleButton setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    @Nullable
    public String getDisplayName() {
        return this.displayName;
    }

    @Nullable
    public String getDisplayHint() {
        return this.displayHint;
    }
}

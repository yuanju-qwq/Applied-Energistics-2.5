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
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 标签页按钮控件。
 * <p>
 * 替代旧的 GuiTabButton，支持图标编号或 ItemStack 作为标签图标。
 */
public class MUITabContainer implements IMUIWidget {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private final int tabWidth = 22;
    private final int tabHeight = 22;
    private boolean visible = true;

    private int iconIndex = -1;
    @Nullable
    private ItemStack iconItem;
    @Nullable
    private String tooltip;
    @Nullable
    private Consumer<MUITabContainer> onClick;

    private boolean hovered = false;

    public MUITabContainer(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 使用图标编号创建标签。
     */
    public MUITabContainer(int x, int y, int iconIndex, String tooltip) {
        this.x = x;
        this.y = y;
        this.iconIndex = iconIndex;
        this.tooltip = tooltip;
    }

    /**
     * 使用 ItemStack 作为标签图标。
     */
    public MUITabContainer(int x, int y, ItemStack iconItem, String tooltip) {
        this.x = x;
        this.y = y;
        this.iconItem = iconItem;
        this.tooltip = tooltip;
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
                && mouseX < screenX + this.tabWidth && mouseY < screenY + this.tabHeight;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);

        // 标签背景（左偏移形成标签页效果）
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 0, 226, this.tabWidth, this.tabHeight, 256, 256);

        // 图标
        if (this.iconItem != null && !this.iconItem.isEmpty()) {
            RenderHelper.enableGUIStandardItemLighting();
            mc.getRenderItem().renderItemAndEffectIntoGUI(this.iconItem, screenX + 3, screenY + 3);
            RenderHelper.disableStandardItemLighting();
        } else if (this.iconIndex >= 0) {
            int iconU = (this.iconIndex % 16) * 16;
            int iconV = (this.iconIndex / 16) * 16;
            Gui.drawModalRectWithCustomSizedTexture(screenX + 3, screenY + 3,
                    iconU, iconV, 16, 16, 256, 256);
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible) {
            return false;
        }
        if (localX >= this.x && localY >= this.y
                && localX < this.x + this.tabWidth && localY < this.y + this.tabHeight) {
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public MUITabContainer setOnClick(@Nullable Consumer<MUITabContainer> onClick) {
        this.onClick = onClick;
        return this;
    }

    public MUITabContainer setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUITabContainer setIconItem(@Nullable ItemStack item) {
        this.iconItem = item;
        return this;
    }

    public MUITabContainer setIconIndex(int index) {
        this.iconIndex = index;
        return this;
    }

    public MUITabContainer setTooltip(@Nullable String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Nullable
    public String getTooltip() {
        return this.tooltip;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

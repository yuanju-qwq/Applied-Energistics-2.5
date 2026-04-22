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

import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 槽位控件。
 * <p>
 * 通用的自定义槽位（不依赖 MC Slot），用于显示自定义内容。
 * 子类可以覆写 {@link #drawSlotContent} 来绘制物品/流体/其他图标。
 */
public class MUISlotWidget implements IMUIWidget {

    private final int id;
    private final int x;
    private final int y;
    private final int size;
    private boolean drawBackground = true;

    @Nullable
    private BiConsumer<MUISlotWidget, Integer> onSlotClicked;
    @Nullable
    private ISlotContentRenderer contentRenderer;

    /**
     * 槽位内容渲染器接口。
     */
    @FunctionalInterface
    public interface ISlotContentRenderer {
        void render(Minecraft mc, int screenX, int screenY, int size, float partialTicks);
    }

    public MUISlotWidget(int id, int x, int y) {
        this(id, x, y, AEMUITheme.SLOT_SIZE);
    }

    public MUISlotWidget(int id, int x, int y, int size) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.size = size;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        if (this.drawBackground) {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            Gui.drawRect(screenX, screenY, screenX + this.size, screenY + this.size, 0x80808080);
        }

        if (this.contentRenderer != null) {
            RenderHelper.enableGUIStandardItemLighting();
            this.contentRenderer.render(Minecraft.getMinecraft(), screenX, screenY, this.size, partialTicks);
            RenderHelper.disableStandardItemLighting();
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (localX >= this.x && localY >= this.y
                && localX < this.x + this.size && localY < this.y + this.size) {
            if (this.onSlotClicked != null) {
                this.onSlotClicked.accept(this, mouseButton);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public int getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getSize() {
        return this.size;
    }

    public MUISlotWidget setDrawBackground(boolean draw) {
        this.drawBackground = draw;
        return this;
    }

    public MUISlotWidget setContentRenderer(@Nullable ISlotContentRenderer renderer) {
        this.contentRenderer = renderer;
        return this;
    }

    public MUISlotWidget setOnSlotClicked(@Nullable BiConsumer<MUISlotWidget, Integer> handler) {
        this.onSlotClicked = handler;
        return this;
    }
}

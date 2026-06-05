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

package appeng.client.mui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.client.mui.AEBasePanel;
import appeng.container.interfaces.ISpecialSlotIngredient;

/**
 * MUI 命名空间下的自定义 slot 抽象基类。
 * <p>
 * 等价于已废弃的 {@code appeng.client.gui.widgets.GuiCustomSlot}，但已脱离
 * 对 {@code net.minecraft.client.gui.Gui} 的继承，转而持有 {@link AEBasePanel}
 * 引用来获取绘制辅助方法。仍实现 {@link IMUITooltip} 与
 * {@link ISpecialSlotIngredient} 以兼容 JEI 拖拽。
 * <p>
 * <b>MUI 改造要点：</b>
 * <ul>
 *   <li>{@code x}/{@code y} 为<em>面板相对坐标</em>（与 IMUIWidget 一致），
 *       不再是屏幕绝对坐标。</li>
 *   <li>不再继承 {@code Gui}，绘制所需的 {@code drawTexturedModalRect} 等方法
 *       通过 {@link AEBasePanel} 暴露的 {@code bindTexture} 配合
 *       {@code GlStateManager} 在子类中按需调用。</li>
 *   <li>{@link #drawContent} 接收 {@link AEBasePanel}，子类可通过该面板
 *       完成纹理绑定、ZLevel 设置等操作。</li>
 * </ul>
 */
public abstract class MUICustomSlot implements IMUITooltip, ISpecialSlotIngredient {
    protected int x;
    protected int y;
    protected final int id;

    public MUICustomSlot(final int id, final int x, final int y) {
        this.x = x;
        this.y = y;
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public boolean canClick(final EntityPlayer player) {
        return true;
    }

    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
    }

    /**
     * 绘制槽位内容。
     * <p>
     * 子类通过传入的 {@link AEBasePanel} 调用纹理绑定等绘制辅助方法，
     * 避免直接依赖 {@code net.minecraft.client.gui.Gui}。
     *
     * @param panel       宿主面板
     * @param mc          Minecraft 实例
     * @param mouseX      鼠标屏幕 X
     * @param mouseY      鼠标屏幕 Y
     * @param partialTicks 渲染插值
     */
    public abstract void drawContent(AEBasePanel panel, Minecraft mc, int mouseX, int mouseY, float partialTicks);

    /**
     * 绘制槽位背景。
     * <p>
     * 子类通过传入的 {@link AEBasePanel} 调用绘制辅助方法。
     *
     * @param panel  宿主面板
     * @param guiLeft 面板左上角屏幕 X
     * @param guiTop  面板左上角屏幕 Y
     */
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop) {
    }

    @Override
    public String getMessage() {
        return null;
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
        return false;
    }

    public boolean isSlotEnabled() {
        return true;
    }

    @Override
    public Object getIngredient() {
        return null;
    }
}

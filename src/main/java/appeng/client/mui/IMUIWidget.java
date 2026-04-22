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

package appeng.client.mui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * MUI 控件接口。
 * <p>
 * 所有可添加到 {@link AEBasePanel} 的子控件都应实现此接口。
 * 提供统一的绘制和输入处理回调。
 */
@SideOnly(Side.CLIENT)
public interface IMUIWidget {

    /**
     * 绘制控件的背景层。
     *
     * @param panel        所属面板
     * @param guiLeft      面板左上角屏幕 X
     * @param guiTop       面板左上角屏幕 Y
     * @param mouseX       鼠标屏幕 X
     * @param mouseY       鼠标屏幕 Y
     * @param partialTicks 渲染插值
     */
    default void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
    }

    /**
     * 绘制控件的前景层（相对于面板左上角的坐标）。
     *
     * @param panel  所属面板
     * @param localX 鼠标相对面板的 X
     * @param localY 鼠标相对面板的 Y
     */
    default void drawForeground(AEBasePanel panel, int localX, int localY) {
    }

    /**
     * 处理鼠标点击事件。
     *
     * @param localX      鼠标相对面板的 X
     * @param localY      鼠标相对面板的 Y
     * @param mouseButton 鼠标按钮（0=左键, 1=右键, 2=中键）
     * @return true 如果事件被消费，不再传播
     */
    default boolean mouseClicked(int localX, int localY, int mouseButton) {
        return false;
    }

    /**
     * 处理键盘输入事件。
     *
     * @param typedChar 输入的字符
     * @param keyCode   按键代码
     * @return true 如果事件被消费，不再传播
     */
    default boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }
}

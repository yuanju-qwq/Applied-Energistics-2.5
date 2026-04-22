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

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 文本输入框控件。
 * <p>
 * 功能：
 * <ul>
 *   <li>文本输入、选择、复制粘贴</li>
 *   <li>可选的输入验证器（{@link ITextValidator}）</li>
 *   <li>可选的占位符文字</li>
 *   <li>自动 focus 管理</li>
 * </ul>
 */
public class MUITextFieldWidget implements IMUIWidget {

    /**
     * 文本验证器接口。
     */
    @FunctionalInterface
    public interface ITextValidator {
        /**
         * @param text 当前文本
         * @return true 如果文本合法
         */
        boolean isValid(String text);
    }

    private final GuiTextField delegate;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    @Nullable
    private ITextValidator validator;
    @Nullable
    private String placeholder;

    public MUITextFieldWidget(int x, int y, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.delegate = new GuiTextField(0, fr, x, y, width, height);
        this.delegate.setEnableBackgroundDrawing(true);
        this.delegate.setTextColor(AEMUITheme.COLOR_SEARCH_TEXT);
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        this.delegate.x = guiLeft + this.x;
        this.delegate.y = guiTop + this.y;
        this.delegate.drawTextBox();
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        if (this.placeholder != null && !this.delegate.isFocused() && this.delegate.getText().isEmpty()) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawStringWithShadow(this.placeholder,
                    this.x + 4, this.y + (this.height - 8) / 2.0f, 0x808080);
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        boolean wasInside = localX >= this.x && localX < this.x + this.width
                && localY >= this.y && localY < this.y + this.height;
        this.delegate.setFocused(wasInside);
        if (wasInside && mouseButton == 1) {
            this.delegate.setText("");
            return true;
        }
        return wasInside;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.delegate.isFocused()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.delegate.setFocused(false);
            return false;
        }

        String oldText = this.delegate.getText();
        boolean handled = this.delegate.textboxKeyTyped(typedChar, keyCode);

        if (handled && this.validator != null && !this.validator.isValid(this.delegate.getText())) {
            this.delegate.setText(oldText);
        }

        return handled;
    }

    // ========== 属性 ==========

    public String getText() {
        return this.delegate.getText();
    }

    public MUITextFieldWidget setText(String text) {
        this.delegate.setText(text);
        return this;
    }

    public boolean isFocused() {
        return this.delegate.isFocused();
    }

    public MUITextFieldWidget setFocused(boolean focused) {
        this.delegate.setFocused(focused);
        return this;
    }

    public MUITextFieldWidget setMaxStringLength(int length) {
        this.delegate.setMaxStringLength(length);
        return this;
    }

    public MUITextFieldWidget setValidator(@Nullable ITextValidator validator) {
        this.validator = validator;
        return this;
    }

    public MUITextFieldWidget setPlaceholder(@Nullable String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    public MUITextFieldWidget setTextColor(int color) {
        this.delegate.setTextColor(color);
        return this;
    }

    public MUITextFieldWidget setEnableBackground(boolean enable) {
        this.delegate.setEnableBackgroundDrawing(enable);
        return this;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

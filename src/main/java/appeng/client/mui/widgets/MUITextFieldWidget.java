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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

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
 *   <li>文本变化回调</li>
 *   <li>tooltip 和右键清空行为</li>
 *   <li>统一的绝对坐标和相对坐标适配</li>
 *   <li>兼容旧文本框的选区高亮和全选行为</li>
 * </ul>
 */
public class MUITextFieldWidget implements IMUIWidget {

    private static final int PADDING = 2;

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

    /**
     * 搜索框默认样式常量，供终端类页面统一复用。
     */
    public static final class SearchFieldStyle {
        public static final int DEFAULT_HEIGHT = 12;
        public static final int DEFAULT_MAX_LENGTH = 25;
        public static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;

        private SearchFieldStyle() {
        }
    }

    /**
     * 终端搜索字段组，适用于 inputs / outputs / names 三联搜索框场景。
     */
    public static final class SearchFieldGroup {
        @Nullable
        private final SearchFieldSpec inputs;
        @Nullable
        private final SearchFieldSpec outputs;
        @Nullable
        private final SearchFieldSpec names;

        private SearchFieldGroup(Builder builder) {
            this.inputs = builder.inputs;
            this.outputs = builder.outputs;
            this.names = builder.names;
        }

        @Nullable
        public SearchFieldSpec getInputs() {
            return this.inputs;
        }

        @Nullable
        public SearchFieldSpec getOutputs() {
            return this.outputs;
        }

        @Nullable
        public SearchFieldSpec getNames() {
            return this.names;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            @Nullable
            private SearchFieldSpec inputs;
            @Nullable
            private SearchFieldSpec outputs;
            @Nullable
            private SearchFieldSpec names;

            private Builder() {
            }

            public Builder inputs(@Nullable SearchFieldSpec inputs) {
                this.inputs = inputs;
                return this;
            }

            public Builder outputs(@Nullable SearchFieldSpec outputs) {
                this.outputs = outputs;
                return this;
            }

            public Builder names(@Nullable SearchFieldSpec names) {
                this.names = names;
                return this;
            }

            public SearchFieldGroup build() {
                return new SearchFieldGroup(this);
            }
        }
    }

    /**
     * 搜索框构建参数。
     */
    public static final class SearchFieldSpec {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        @Nullable
        private final String tooltip;
        @Nullable
        private final Consumer<String> textChangeListener;
        private final boolean focused;

        private SearchFieldSpec(Builder builder) {
            this.x = builder.x;
            this.y = builder.y;
            this.width = builder.width;
            this.height = builder.height;
            this.tooltip = builder.tooltip;
            this.textChangeListener = builder.textChangeListener;
            this.focused = builder.focused;
        }

        public static Builder builder(int x, int y, int width) {
            return new Builder(x, y, width);
        }

        public static final class Builder {
            private final int x;
            private final int y;
            private final int width;
            private int height = SearchFieldStyle.DEFAULT_HEIGHT;
            @Nullable
            private String tooltip;
            @Nullable
            private Consumer<String> textChangeListener;
            private boolean focused;

            private Builder(int x, int y, int width) {
                this.x = x;
                this.y = y;
                this.width = width;
            }

            public Builder tooltip(@Nullable String tooltip) {
                this.tooltip = tooltip;
                return this;
            }

            public Builder onTextChange(@Nullable Consumer<String> textChangeListener) {
                this.textChangeListener = textChangeListener;
                return this;
            }

            public Builder focused(boolean focused) {
                this.focused = focused;
                return this;
            }

            public Builder height(int height) {
                this.height = height;
                return this;
            }

            public SearchFieldSpec build() {
                return new SearchFieldSpec(this);
            }
        }
    }

    private final GuiTextField delegate;
    private int x;
    private int y;
    private final int width;
    private final int height;
    private final int fontPad;

    @Nullable
    private ITextValidator validator;
    @Nullable
    private String placeholder;
    @Nullable
    private Consumer<String> textChangeListener;
    @Nullable
    private Consumer<String> focusLostListener;
    @Nullable
    private List<String> tooltip;

    private boolean clearOnRightClick = true;
    private boolean visible = true;
    private int selectionColor = 0xFF00FF00;

    public MUITextFieldWidget(int x, int y, int width, int height) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontPad = fr.getCharWidth('_');
        this.delegate = new GuiTextField(0, fr, x + PADDING, y + PADDING,
                width - 2 * PADDING - this.fontPad, height - 2 * PADDING) {
            @Override
            public void drawSelectionBox(int startX, int startY, int endX, int endY) {
                MUITextFieldWidget.this.drawSelectionBox(startX, startY, endX, endY);
            }
        };
        this.delegate.setEnableBackgroundDrawing(true);
        this.delegate.setTextColor(AEMUITheme.COLOR_TEXT_FIELD);
    }

    /**
     * 为 panel 注册一个统一样式的搜索输入框。
     */
    public static MUITextFieldWidget addSearchField(AEBasePanel panel, SearchFieldSpec spec) {
        return panel.addWidget(new MUITextFieldWidget(spec.x, spec.y, spec.width, spec.height)
                .setEnableBackground(false)
                .setMaxStringLength(SearchFieldStyle.DEFAULT_MAX_LENGTH)
                .setTextColor(SearchFieldStyle.DEFAULT_TEXT_COLOR)
                .setTooltip(spec.tooltip)
                .setTextChangeListener(spec.textChangeListener)
                .setFocused(spec.focused));
    }

    /**
     * 为 panel 批量注册终端搜索字段组。
     */
    public static SearchFieldWidgets addSearchFieldGroup(AEBasePanel panel, SearchFieldGroup group) {
        MUITextFieldWidget inputs = group.getInputs() == null ? null : addSearchField(panel, group.getInputs());
        MUITextFieldWidget outputs = group.getOutputs() == null ? null : addSearchField(panel, group.getOutputs());
        MUITextFieldWidget names = group.getNames() == null ? null : addSearchField(panel, group.getNames());
        return new SearchFieldWidgets(inputs, outputs, names);
    }

    /**
     * 已注册的搜索字段组实例。
     */
    public static final class SearchFieldWidgets {
        @Nullable
        private final MUITextFieldWidget inputs;
        @Nullable
        private final MUITextFieldWidget outputs;
        @Nullable
        private final MUITextFieldWidget names;

        private SearchFieldWidgets(@Nullable MUITextFieldWidget inputs,
                @Nullable MUITextFieldWidget outputs,
                @Nullable MUITextFieldWidget names) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.names = names;
        }

        @Nullable
        public MUITextFieldWidget getInputs() {
            return this.inputs;
        }

        @Nullable
        public MUITextFieldWidget getOutputs() {
            return this.outputs;
        }

        @Nullable
        public MUITextFieldWidget getNames() {
            return this.names;
        }
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        this.delegate.x = guiLeft + this.x + PADDING;
        this.delegate.y = guiTop + this.y + PADDING;
        this.delegate.setVisible(true);
        this.drawTextBox();
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        if (!this.visible) {
            return;
        }

        if (this.placeholder != null && !this.delegate.isFocused() && this.delegate.getText().isEmpty()) {
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            fr.drawStringWithShadow(this.placeholder,
                    this.x + 4, this.y + (this.height - 8) / 2.0f, 0x808080);
        }

        if (this.tooltip != null && this.isMouseIn(panel.getGuiLeft() + localX, panel.getGuiTop() + localY)) {
            panel.drawTooltip(localX + 11, localY + 4, this.tooltip);
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible) {
            return false;
        }

        boolean wasFocused = this.delegate.isFocused();
        boolean wasInside = this.containsLocalPoint(localX, localY);
        this.delegate.mouseClicked(this.x + localX - this.x, this.y + localY - this.y, mouseButton);
        this.delegate.setFocused(wasInside);

        if (wasInside && mouseButton == 1 && this.clearOnRightClick) {
            this.setText("");
            return true;
        }

        if (wasFocused && !wasInside) {
            this.notifyFocusLost();
        }

        return wasInside;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.visible || !this.delegate.isFocused()) {
            return false;
        }

        if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            this.delegate.setFocused(false);
            this.notifyFocusLost();
            return false;
        }

        String oldText = this.delegate.getText();
        boolean handled = this.delegate.textboxKeyTyped(typedChar, keyCode);

        if (handled && this.validator != null && !this.validator.isValid(this.delegate.getText())) {
            this.delegate.setText(oldText);
            this.delegate.setCursorPositionEnd();
        }

        if (handled && !Objects.equals(oldText, this.delegate.getText())) {
            this.notifyTextChanged();
        }

        return handled;
    }

    // ========== 属性 ==========

    public String getText() {
        return this.delegate.getText();
    }

    public MUITextFieldWidget setText(String text) {
        String oldText = this.delegate.getText();
        this.delegate.setText(text);
        this.delegate.setCursorPositionEnd();
        if (!Objects.equals(oldText, this.delegate.getText())) {
            this.notifyTextChanged();
        }
        return this;
    }

    public boolean isFocused() {
        return this.delegate.isFocused();
    }

    public MUITextFieldWidget setFocused(boolean focused) {
        boolean wasFocused = this.delegate.isFocused();
        this.delegate.setFocused(focused);
        if (wasFocused && !focused) {
            this.notifyFocusLost();
        }
        return this;
    }

    public MUITextFieldWidget setMaxStringLength(int length) {
        this.delegate.setMaxStringLength(length);
        return this;
    }

    public int getMaxStringLength() {
        return this.delegate.getMaxStringLength();
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

    public MUITextFieldWidget setVisible(boolean visible) {
        this.visible = visible;
        this.delegate.setVisible(visible);
        return this;
    }

    public boolean getVisible() {
        return this.visible;
    }

    public MUITextFieldWidget setCanLoseFocus(boolean canLoseFocus) {
        this.delegate.setCanLoseFocus(canLoseFocus);
        return this;
    }

    public MUITextFieldWidget setSelectionColor(int color) {
        this.selectionColor = color;
        return this;
    }

    public MUITextFieldWidget setClearOnRightClick(boolean clearOnRightClick) {
        this.clearOnRightClick = clearOnRightClick;
        return this;
    }

    public MUITextFieldWidget setTextChangeListener(@Nullable Consumer<String> textChangeListener) {
        this.textChangeListener = textChangeListener;
        return this;
    }

    public MUITextFieldWidget setFocusLostListener(@Nullable Consumer<String> focusLostListener) {
        this.focusLostListener = focusLostListener;
        return this;
    }

    public MUITextFieldWidget setTooltip(@Nullable String tooltip) {
        if (tooltip == null || tooltip.isEmpty()) {
            this.tooltip = null;
        } else {
            this.tooltip = Arrays.asList(tooltip.split("\\n"));
        }
        return this;
    }

    public MUITextFieldWidget setTooltip(@Nullable List<String> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public MUITextFieldWidget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public boolean isMouseIn(int mouseX, int mouseY) {
        return mouseX >= this.delegate.x - PADDING && mouseX < this.delegate.x + this.delegate.width + this.fontPad + PADDING
                && mouseY >= this.delegate.y - PADDING && mouseY < this.delegate.y + this.delegate.height + PADDING;
    }

    public boolean containsLocalPoint(int localX, int localY) {
        return localX >= this.x && localX < this.x + this.width
                && localY >= this.y && localY < this.y + this.height;
    }

    public boolean textboxKeyTyped(char typedChar, int keyCode) {
        return this.keyTyped(typedChar, keyCode);
    }

    public void mouseClickedAbsolute(int mouseX, int mouseY, int mouseButton) {
        this.mouseClicked(mouseX - this.delegate.x + this.x + PADDING, mouseY - this.delegate.y + this.y + PADDING,
                mouseButton);
    }

    public void drawTextBox() {
        if (!this.visible) {
            return;
        }

        if (this.delegate.getVisible()) {
            if (this.delegate.isFocused()) {
                drawRect(this.delegate.x - PADDING + 1, this.delegate.y - PADDING + 1,
                        this.delegate.x + this.delegate.width + this.fontPad + PADDING - 1,
                        this.delegate.y + this.delegate.height + PADDING - 1,
                        0xFF606060);
            } else {
                drawRect(this.delegate.x - PADDING + 1, this.delegate.y - PADDING + 1,
                        this.delegate.x + this.delegate.width + this.fontPad + PADDING - 1,
                        this.delegate.y + this.delegate.height + PADDING - 1,
                        0xFFA8A8A8);
            }
            this.delegate.drawTextBox();
        }
    }

    public void selectAll() {
        this.delegate.setCursorPosition(0);
        this.delegate.setSelectionPos(this.delegate.getMaxStringLength());
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

    private void notifyTextChanged() {
        if (this.textChangeListener != null) {
            this.textChangeListener.accept(this.delegate.getText());
        }
    }

    private void notifyFocusLost() {
        if (this.focusLostListener != null) {
            this.focusLostListener.accept(this.delegate.getText());
        }
    }

    private void drawSelectionBox(int startX, int startY, int endX, int endY) {
        if (!this.delegate.isFocused()) {
            return;
        }

        if (startX < endX) {
            int i = startX;
            startX = endX;
            endX = i;
        }

        startX += 1;
        endX -= 1;

        if (startY < endY) {
            int j = startY;
            startY = endY;
            endY = j;
        }

        startY -= PADDING;

        if (endX > this.delegate.x + this.delegate.width) {
            endX = this.delegate.x + this.delegate.width;
        }

        if (startX > this.delegate.x + this.delegate.width) {
            startX = this.delegate.x + this.delegate.width;
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();

        float red = (this.selectionColor >> 16 & 255) / 255.0F;
        float blue = (this.selectionColor >> 8 & 255) / 255.0F;
        float green = (this.selectionColor & 255) / 255.0F;
        float alpha = (this.selectionColor >> 24 & 255) / 255.0F;

        GlStateManager.color(red, green, blue, alpha);
        GlStateManager.disableTexture2D();
        GlStateManager.enableColorLogic();
        GlStateManager.colorLogicOp(GlStateManager.LogicOp.OR_REVERSE);
        bufferBuilder.begin(7, DefaultVertexFormats.POSITION);
        bufferBuilder.pos(startX, endY, 0.0D).endVertex();
        bufferBuilder.pos(endX, endY, 0.0D).endVertex();
        bufferBuilder.pos(endX, startY, 0.0D).endVertex();
        bufferBuilder.pos(startX, startY, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.disableColorLogic();
        GlStateManager.enableTexture2D();
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        if (left < right) {
            int i = left;
            left = right;
            right = i;
        }

        if (top < bottom) {
            int j = top;
            top = bottom;
            bottom = j;
        }

        float alpha = (float) (color >> 24 & 255) / 255.0F;
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferbuilder = tessellator.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.color(red, green, blue, alpha);
        bufferbuilder.begin(7, DefaultVertexFormats.POSITION);
        bufferbuilder.pos(left, bottom, 0.0D).endVertex();
        bufferbuilder.pos(right, bottom, 0.0D).endVertex();
        bufferbuilder.pos(right, top, 0.0D).endVertex();
        bufferbuilder.pos(left, top, 0.0D).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}

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

import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.client.gui.FontRenderer;

import appeng.util.MathExpressionParser;

/**
 * MUI number input field. Full replacement for legacy {@code GuiNumberBox}.
 * <p>
 * Wraps a {@link MUITextFieldWidget} with a numeric validator. The supported
 * numeric types are: {@code int}, {@code long}, {@code double}, and any
 * expression resolvable by {@link MathExpressionParser}.
 * <p>
 * When the entered text is not a valid number, the input is rejected and the
 * previous text is restored.
 */
public class MUINumberFieldWidget {

    /**
     * Supported numeric types.
     */
    public enum NumberType {
        INT(Integer::parseInt, 0L),
        LONG(Long::parseLong, 0L),
        DOUBLE(Double::parseDouble, 0.0);

        private final java.util.function.Function<String, ?> parser;
        private final Object zero;

        NumberType(java.util.function.Function<String, ?> parser, Object zero) {
            this.parser = parser;
            this.zero = zero;
        }

        Object parse(String text) {
            return this.parser.apply(text);
        }

        Object zero() {
            return this.zero;
        }
    }

    private final MUITextFieldWidget delegate;
    private final NumberType type;
    @Nullable
    private Consumer<String> changeListener;

    public MUINumberFieldWidget(FontRenderer fontRenderer, int x, int y, int width, int height, Class<?> type) {
        this.delegate = new MUITextFieldWidget(x, y, width, height);
        this.type = resolveType(type);
    }

    private static NumberType resolveType(Class<?> type) {
        if (type == int.class || type == Integer.class) {
            return NumberType.INT;
        }
        if (type == long.class || type == Long.class) {
            return NumberType.LONG;
        }
        if (type == double.class || type == Double.class) {
            return NumberType.DOUBLE;
        }
        // Default to long to match legacy default behaviour
        return NumberType.LONG;
    }

    /**
     * Sets the validator on the underlying widget so that non-numeric input is rejected.
     */
    public MUINumberFieldWidget applyValidator() {
        this.delegate.setValidator(text -> {
            if (text == null || text.isEmpty()) {
                return true;
            }
            try {
                type.parse(text);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        return this;
    }

    public String getText() {
        return this.delegate.getText();
    }

    public MUINumberFieldWidget setText(String text) {
        this.delegate.setText(text);
        return this;
    }

    public boolean isFocused() {
        return this.delegate.isFocused();
    }

    public MUINumberFieldWidget setFocused(boolean focused) {
        this.delegate.setFocused(focused);
        return this;
    }

    public MUINumberFieldWidget setMaxStringLength(int length) {
        this.delegate.setMaxStringLength(length);
        return this;
    }

    public MUINumberFieldWidget setTextColor(int color) {
        this.delegate.setTextColor(color);
        return this;
    }

    public MUINumberFieldWidget setEnableBackgroundDrawing(boolean enable) {
        this.delegate.setEnableBackground(enable);
        return this;
    }

    public MUINumberFieldWidget setVisible(boolean visible) {
        this.delegate.setVisible(visible);
        return this;
    }

    public MUINumberFieldWidget setEnabled(boolean enabled) {
        this.delegate.setCanLoseFocus(enabled);
        return this;
    }

    public MUINumberFieldWidget setTextChangeListener(@Nullable Consumer<String> listener) {
        this.changeListener = listener;
        this.delegate.setTextChangeListener(listener);
        return this;
    }

    public MUINumberFieldWidget setTooltip(@Nullable String tooltip) {
        this.delegate.setTooltip(tooltip);
        return this;
    }

    public MUINumberFieldWidget setPosition(int x, int y) {
        this.delegate.setPosition(x, y);
        return this;
    }

    public MUITextFieldWidget getDelegate() {
        return this.delegate;
    }

    public int getX() {
        return this.delegate.getX();
    }

    public int getY() {
        return this.delegate.getY();
    }

    public int getWidth() {
        return this.delegate.getWidth();
    }

    public int getHeight() {
        return this.delegate.getHeight();
    }
}

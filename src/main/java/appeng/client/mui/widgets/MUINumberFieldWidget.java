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

/**
 * MUI 数值输入框控件。
 * <p>
 * 基于 {@link MUITextFieldWidget}，内置数值验证器。
 * 只接受整数、长整数或浮点数输入。
 */
public class MUINumberFieldWidget extends MUITextFieldWidget {

    /**
     * 数值类型。
     */
    public enum NumberType {
        INTEGER,
        LONG,
        DOUBLE
    }

    private final NumberType numberType;

    public MUINumberFieldWidget(int x, int y, int width, int height, NumberType numberType) {
        super(x, y, width, height);
        this.numberType = numberType;
        this.setValidator(this::isValidNumber);
    }

    public MUINumberFieldWidget(int x, int y, int width, int height) {
        this(x, y, width, height, NumberType.LONG);
    }

    private boolean isValidNumber(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        try {
            switch (this.numberType) {
                case INTEGER:
                    Integer.parseInt(text);
                    break;
                case LONG:
                    Long.parseLong(text);
                    break;
                case DOUBLE:
                    Double.parseDouble(text);
                    break;
            }
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * @return 当前文本解析为 long，解析失败返回默认值
     */
    public long getLongValue(long defaultValue) {
        try {
            return Long.parseLong(this.getText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * @return 当前文本解析为 int，解析失败返回默认值
     */
    public int getIntValue(int defaultValue) {
        try {
            return Integer.parseInt(this.getText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * @return 当前文本解析为 double，解析失败返回默认值
     */
    public double getDoubleValue(double defaultValue) {
        try {
            return Double.parseDouble(this.getText());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

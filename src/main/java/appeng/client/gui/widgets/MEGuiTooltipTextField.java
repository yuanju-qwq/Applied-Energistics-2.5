package appeng.client.gui.widgets;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/**
 * Different implementation of a text field that wraps instead of extends MC's {@link GuiTextField}. This is necessary
 * because of deobfuscated name collision between {@link ITooltip} and GuiTextField, which would cause crashes in an
 * obfuscated environment. Additionally, since we are not extending that class, we can construct this object differently
 * and allow its position to be mutable like most other widgets.
 */
public class MEGuiTooltipTextField implements ITooltip {

    protected GuiTextField field;

    private static final int PADDING = 2;
    private static boolean previousKeyboardRepeatEnabled;
    private static MEGuiTooltipTextField previousKeyboardRepeatEnabledField;
    private String tooltip;
    private int fontPad;

    /**
     * 建议文本（suggestion）：按 TAB 可将其确认为正式搜索文本。
     * suggestion 是从 rawSuggestion 中去除当前已输入前缀后的剩余部分。
     * rawSuggestion 是完整的建议文本。
     */
    private String suggestion = "";
    private String rawSuggestion = "";

    public int x;
    public int y;
    public int w;
    public int h;

    /**
     * Uses the values to instantiate a padded version of a text field. Pays attention to the '_' caret.
     *
     * @param width   absolute width
     * @param height  absolute height
     * @param tooltip tooltip message
     */
    public MEGuiTooltipTextField(final int width, final int height, final String tooltip) {
        final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
        field = new GuiTextField(0, fontRenderer, 0, 0, 0, 0);

        w = width;
        h = height;

        setMessage(tooltip);

        this.fontPad = fontRenderer.getCharWidth('_');

        setDimensionsAndColor();
    }

    public MEGuiTooltipTextField(final int width, final int height) {
        this(width, height, "");
    }

    public MEGuiTooltipTextField() {
        this(0, 0);
    }

    protected void setDimensionsAndColor() {
        field.x = this.x + PADDING;
        field.y = this.y + PADDING;
        field.width = this.w - PADDING * 2 - this.fontPad;
        field.height = this.h - PADDING * 2;
    }

    public void onTextChange(final String oldText) {
    }

    public void mouseClicked(final int xPos, final int yPos, final int button) {

        if (!this.isMouseIn(xPos, yPos)) {
            setFocused(false);
            return;
        }

        field.setCanLoseFocus(false);
        setFocused(true);

        if (button == 1) {
            setText("");
        } else {
            field.mouseClicked(xPos, yPos, button);
        }

        field.setCanLoseFocus(true);
    }

    /**
     * Checks if the mouse is within the element
     *
     * @param xCoord current x coord of the mouse
     * @param yCoord current y coord of the mouse
     * @return true if mouse position is within the getText field area
     */
    public boolean isMouseIn(final int xCoord, final int yCoord) {
        final boolean withinXRange = this.x <= xCoord && xCoord < this.x + this.w;
        final boolean withinYRange = this.y <= yCoord && yCoord < this.y + this.h;

        return withinXRange && withinYRange;
    }

    public boolean textboxKeyTyped(final char keyChar, final int keyID) {
        if (!isFocused()) {
            return false;
        }

        final String oldText = getText();
        boolean handled = field.textboxKeyTyped(keyChar, keyID);

        if (!handled && (keyID == Keyboard.KEY_RETURN || keyID == Keyboard.KEY_NUMPADENTER
                || keyID == Keyboard.KEY_ESCAPE)) {
            setFocused(false);
        }

        if (handled) {
            onTextChange(oldText);
        }

        return handled;
    }

    public void drawTextBox() {
        if (field.getVisible()) {
            setDimensionsAndColor();
            GuiTextField.drawRect(
                    this.x + 1,
                    this.y + 1,
                    this.x + this.w - 1,
                    this.y + this.h - 1,
                    isFocused() ? 0xFF606060 : 0xFFA8A8A8);
            // 绘制建议文本（灰色半透明，紧跟在已输入文本后面）
            drawSuggestion();
            field.drawTextBox();
        }
    }

    /**
     * 绘制建议文本（suggestion），灰色显示在当前文本后面
     */
    private void drawSuggestion() {
        if (!this.suggestion.isEmpty()) {
            final FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
            final int textWidth = fontRenderer.getStringWidth(this.getText());
            final int availableWidth = this.w - PADDING * 2 - this.fontPad;

            // 截断过长的建议文本
            String drawString = this.suggestion;
            if (textWidth + fontRenderer.getStringWidth(drawString) > availableWidth) {
                StringBuilder builder = new StringBuilder();
                int currentWidth = textWidth;
                for (char c : this.suggestion.toCharArray()) {
                    int charWidth = fontRenderer.getCharWidth(c);
                    if (currentWidth + charWidth * 3 < availableWidth) {
                        currentWidth += charWidth;
                        builder.append(c);
                    } else {
                        break;
                    }
                }
                drawString = builder.toString();
            }

            if (!drawString.isEmpty()) {
                fontRenderer.drawString(drawString,
                        this.x + PADDING + textWidth, this.y + PADDING, 0xC0C0C0);
            }
        }
    }

    public void setText(String text, boolean ignoreTrigger) {
        final String oldText = getText();

        int currentCursorPos = field.getCursorPosition();
        field.setText(text);
        field.setCursorPosition(currentCursorPos);

        if (!ignoreTrigger) {
            onTextChange(oldText);
        }
    }

    public void setText(String text) {
        setText(text, false);
    }

    public void setCursorPositionEnd() {
        field.setCursorPositionEnd();
    }

    public void setFocused(boolean focus) {
        if (field.isFocused() == focus) {
            return;
        }

        field.setFocused(focus);

        if (focus) {

            if (previousKeyboardRepeatEnabledField == null) {
                previousKeyboardRepeatEnabled = Keyboard.areRepeatEventsEnabled();
            }

            previousKeyboardRepeatEnabledField = this;
            Keyboard.enableRepeatEvents(true);
        } else {

            if (previousKeyboardRepeatEnabledField == this) {
                previousKeyboardRepeatEnabledField = null;
                Keyboard.enableRepeatEvents(previousKeyboardRepeatEnabled);
            }
        }
    }

    public void setMaxStringLength(final int size) {
        field.setMaxStringLength(size);
    }

    public void setEnableBackgroundDrawing(final boolean b) {
        field.setEnableBackgroundDrawing(b);
    }

    public void setTextColor(final int color) {
        field.setTextColor(color);
    }

    public void setCursorPositionZero() {
        field.setCursorPositionZero();
    }

    public boolean isFocused() {
        return field.isFocused();
    }

    public String getText() {
        return field.getText();
    }

    public void setMessage(String t) {
        tooltip = t;
    }

    @Override
    public String getMessage() {
        return tooltip;
    }

    @Override
    public boolean isVisible() {
        return field.getVisible();
    }

    @Override
    public int xPos() {
        return x;
    }

    @Override
    public int yPos() {
        return y;
    }

    @Override
    public int getWidth() {
        return w;
    }

    @Override
    public int getHeight() {
        return h;
    }

    // ========== Suggestion（建议文本）相关方法 ==========

    /**
     * 设置建议文本。如果建议文本以当前已输入文本开头，则只显示剩余部分。
     *
     * @param rawSuggestion 完整的建议文本
     */
    public void setSuggestion(final String rawSuggestion) {
        this.rawSuggestion = rawSuggestion;
        final String currentText = this.getText();
        if (rawSuggestion.startsWith(currentText)) {
            this.suggestion = rawSuggestion.substring(currentText.length());
        } else {
            this.suggestion = "";
        }
    }

    /**
     * 根据当前文本刷新建议文本的显示部分
     */
    public void updateSuggestion() {
        setSuggestion(this.rawSuggestion);
    }

    /**
     * 获取当前显示的建议文本片段（去除已输入前缀后的部分）
     */
    public String getSuggestion() {
        return this.suggestion;
    }

    /**
     * 获取完整的建议文本
     */
    public String getRawSuggestion() {
        return this.rawSuggestion;
    }

    /**
     * 将建议文本确认为正式文本（TAB 补全功能）
     */
    public void setSuggestionToText() {
        if (!this.rawSuggestion.isEmpty()) {
            this.setText(this.rawSuggestion);
            this.setSuggestion(this.rawSuggestion);
        }
    }
}

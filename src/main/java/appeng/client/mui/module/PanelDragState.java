package appeng.client.mui.module;

import java.util.function.BiPredicate;

/**
 * Panel drag state manager.
 * <p>
 * Supports middle-click drag to adjust panel position.
 * Extracted from {@link PatternEncodingModule} to eliminate cross-module dependency
 * (previously {@code PatternEncodingModule.PanelDragState} was referenced by
 * {@link MEItemBrowserModule}).
 */
public class PanelDragState {

    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    private boolean dragging = false;

    private int dragStartMouseX;
    private int dragStartMouseY;
    private int dragStartOffsetX;
    private int dragStartOffsetY;

    private final BiPredicate<Integer, Integer> dragAreaChecker;

    public PanelDragState(BiPredicate<Integer, Integer> dragAreaChecker) {
        this.dragAreaChecker = dragAreaChecker;
    }

    public boolean isInDragArea(int mouseX, int mouseY) {
        return dragAreaChecker.test(mouseX, mouseY);
    }

    public void startDrag(int mouseX, int mouseY) {
        this.dragging = true;
        this.dragStartMouseX = mouseX;
        this.dragStartMouseY = mouseY;
        this.dragStartOffsetX = this.dragOffsetX;
        this.dragStartOffsetY = this.dragOffsetY;
    }

    public void updateDrag(int mouseX, int mouseY) {
        this.dragOffsetX = this.dragStartOffsetX + (mouseX - this.dragStartMouseX);
        this.dragOffsetY = this.dragStartOffsetY + (mouseY - this.dragStartMouseY);
    }

    public void endDrag() {
        this.dragging = false;
    }

    public int getDragOffsetX() {
        return dragOffsetX;
    }

    public int getDragOffsetY() {
        return dragOffsetY;
    }

    public boolean isDragging() {
        return dragging;
    }
}

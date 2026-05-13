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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import appeng.client.gui.widgets.ITooltip;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * A pool-based MUI widget for managing dynamically created buttons that are recreated every frame.
 * <p>
 * Replaces the legacy pattern of creating {@code new GuiImgButton(...)} in drawScreen/drawFG
 * and adding them to {@code buttonList} each frame.
 * <p>
 * Usage pattern:
 * <ol>
 *   <li>Register this pool as a widget in {@code setupWidgets()}</li>
 *   <li>Each frame (in drawScreen or populateDynamicSlots), call {@link #reset()} first</li>
 *   <li>Call {@link #acquireSettings(int, int, Enum, Enum)} or {@link #acquireCustom(int, int, int, int)}
 *       for each dynamic button needed</li>
 *   <li>The pool handles drawing and click detection automatically via the IMUIWidget lifecycle</li>
 * </ol>
 */
public class MUIButtonPool implements IMUIWidget {

    private final List<MUIButtonWidget> pool = new ArrayList<>();
    private int activeCount = 0;

    @Nullable
    private Consumer<MUIButtonWidget> defaultOnClick;

    public MUIButtonPool() {
    }

    /**
     * Set the default onClick handler for all buttons acquired from this pool.
     * Individual buttons can override this after acquisition.
     */
    public MUIButtonPool setDefaultOnClick(@Nullable Consumer<MUIButtonWidget> onClick) {
        this.defaultOnClick = onClick;
        return this;
    }

    /**
     * Reset the pool for the current frame. Must be called at the beginning of each
     * frame's dynamic button creation phase (before any acquire calls).
     */
    public void reset() {
        // Hide all previously active buttons
        for (int i = 0; i < this.activeCount; i++) {
            this.pool.get(i).setVisible(false);
        }
        this.activeCount = 0;
    }

    /**
     * Acquire a settings-mode button from the pool. Creates a new one if needed.
     * <p>
     * The returned button uses panel-relative coordinates (not screen coordinates).
     *
     * @param relX    panel-relative X
     * @param relY    panel-relative Y
     * @param setting the Settings enum
     * @param value   the initial value enum
     * @return the acquired button, ready for customization
     */
    public MUIButtonWidget acquireSettings(int relX, int relY, Enum<?> setting, Enum<?> value) {
        MUIButtonWidget btn = ensureCapacity();
        btn.reinitAsSettings(relX, relY, setting, value);
        btn.setVisible(true).setEnabled(true);
        btn.setHalfSize(false);
        btn.setOnClick(this.defaultOnClick);
        btn.setTooltip(null);
        btn.setFillVar(null);
        return btn;
    }

    /**
     * Acquire a custom-mode button from the pool. Creates a new one if needed.
     *
     * @param relX   panel-relative X
     * @param relY   panel-relative Y
     * @param width  button width
     * @param height button height
     * @return the acquired button, ready for customization
     */
    public MUIButtonWidget acquireCustom(int relX, int relY, int width, int height) {
        MUIButtonWidget btn = ensureCapacity();
        btn.reinitAsCustom(relX, relY, width, height);
        btn.setVisible(true).setEnabled(true);
        btn.setHalfSize(false);
        btn.setOnClick(this.defaultOnClick);
        btn.setTooltip(null);
        return btn;
    }

    /**
     * Get the number of currently active buttons in this frame.
     */
    public int getActiveCount() {
        return this.activeCount;
    }

    /**
     * Get an active button by index (0..activeCount-1).
     */
    public MUIButtonWidget getActive(int index) {
        if (index < 0 || index >= this.activeCount) {
            throw new IndexOutOfBoundsException("index=" + index + ", activeCount=" + this.activeCount);
        }
        return this.pool.get(index);
    }

    private MUIButtonWidget ensureCapacity() {
        if (this.activeCount >= this.pool.size()) {
            MUIButtonWidget btn = new MUIButtonWidget(0, 0);
            btn.setVisible(false);
            this.pool.add(btn);
        }
        MUIButtonWidget btn = this.pool.get(this.activeCount);
        this.activeCount++;
        return btn;
    }

    // ========== IMUIWidget lifecycle ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        for (int i = 0; i < this.activeCount; i++) {
            this.pool.get(i).drawBackground(panel, guiLeft, guiTop, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        for (int i = 0; i < this.activeCount; i++) {
            this.pool.get(i).drawForeground(panel, localX, localY);
        }
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        for (int i = 0; i < this.activeCount; i++) {
            if (this.pool.get(i).mouseClicked(localX, localY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    // ========== Tooltip support ==========

    /**
     * Draw tooltips for all active buttons. Called by {@link AEBasePanel#drawScreen}
     * to provide tooltip rendering for dynamically pooled buttons.
     *
     * @param panel  the host panel (provides drawTooltip)
     * @param mouseX mouse screen X
     * @param mouseY mouse screen Y
     */
    public void drawTooltips(AEBasePanel panel, int mouseX, int mouseY) {
        for (int i = 0; i < this.activeCount; i++) {
            MUIButtonWidget btn = this.pool.get(i);
            if (btn.isVisible()) {
                panel.drawTooltip(btn, mouseX, mouseY);
            }
        }
    }
}

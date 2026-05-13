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

import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import appeng.client.gui.widgets.ITooltip;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI toggle button widget. Full replacement for legacy {@code GuiToggleButton}.
 * <p>
 * Two-state button that toggles between on/off. Each state uses a different icon
 * from the states.png atlas (specified by icon index).
 * <p>
 * Implements {@link ITooltip} so that {@link AEBasePanel} can display tooltip
 * text automatically using the same mechanism as legacy buttons.
 */
public class MUIToggleButton implements IMUIWidget, ITooltip {

    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", Pattern.LITERAL);

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private final int iconIdxOn;
    private final int iconIdxOff;

    private boolean active = false;
    private boolean visible = true;
    private boolean hovered = false;

    @Nullable
    private String displayName;
    @Nullable
    private String displayHint;
    @Nullable
    private Consumer<MUIToggleButton> onToggle;

    public MUIToggleButton(int x, int y, int iconOn, int iconOff) {
        this.x = x;
        this.y = y;
        this.iconIdxOn = iconOn;
        this.iconIdxOff = iconOff;
    }

    public MUIToggleButton(int x, int y, int iconOn, int iconOff,
            String displayName, String displayHint) {
        this(x, y, iconOn, iconOff);
        this.displayName = displayName;
        this.displayHint = displayHint;
    }

    // ========== Drawing ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;
        Minecraft mc = Minecraft.getMinecraft();

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + 16 && mouseY < screenY + 16;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);

        // Background
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 240, 240, 16, 16, 256, 256);

        // Icon
        int iconIdx = this.active ? this.iconIdxOn : this.iconIdxOff;
        int iconU = (iconIdx % 16) * 16;
        int iconV = (iconIdx / 16) * 16;
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, iconU, iconV, 16, 16, 256, 256);
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        // Tooltip rendering is handled by AEBasePanel.drawTooltip(ITooltip, ...) via the ITooltip interface.
    }

    // ========== Input events ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible) {
            return false;
        }
        if (localX >= this.x && localY >= this.y
                && localX < this.x + 16 && localY < this.y + 16) {
            this.active = !this.active;
            if (this.onToggle != null) {
                this.onToggle.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== ITooltip implementation ==========

    /**
     * Generate tooltip text using the same format as legacy {@code GuiToggleButton.getMessage()}.
     * <p>
     * Format: {@code "Localized Name\nLocalized Hint"} with word-wrap at 30-char boundaries.
     */
    @Override
    public String getMessage() {
        if (this.displayName != null) {
            String name = I18n.translateToLocal(this.displayName);
            String value = I18n.translateToLocal(this.displayHint);

            if (name == null || name.isEmpty()) {
                name = this.displayName;
            }
            if (value == null || value.isEmpty()) {
                value = this.displayHint;
            }

            value = PATTERN_NEW_LINE.matcher(value).replaceAll("\n");
            final StringBuilder sb = new StringBuilder(value);

            int i = sb.lastIndexOf("\n");
            if (i <= 0) {
                i = 0;
            }
            while (i + 30 < sb.length() && (i = sb.lastIndexOf(" ", i + 30)) != -1) {
                sb.replace(i, i + 1, "\n");
            }

            return name + '\n' + sb;
        }
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
        return this.visible;
    }

    // ========== Property accessors ==========

    public boolean isActive() {
        return this.active;
    }

    /**
     * Set the active state. Alias for legacy {@code GuiToggleButton.setState(boolean)}.
     */
    public MUIToggleButton setActive(boolean active) {
        this.active = active;
        return this;
    }

    /**
     * Alias matching legacy {@code GuiToggleButton.setState(boolean)}.
     */
    public void setState(boolean isOn) {
        this.active = isOn;
    }

    public MUIToggleButton setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUIToggleButton setOnToggle(@Nullable Consumer<MUIToggleButton> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    public MUIToggleButton setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    @Nullable
    public String getDisplayName() {
        return this.displayName;
    }

    @Nullable
    public String getDisplayHint() {
        return this.displayHint;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

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

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.stacks.AEKeyType;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.core.localization.ButtonToolTips;

/**
 * MUI type filter toggle button driven by {@link AEKeyType}.
 * <p>
 * Replaces the legacy {@code TypeToggleButton} which was based on {@code IAEStackType}.
 * <p>
 * Each button controls whether its associated {@link AEKeyType} is visible in the terminal grid.
 * When disabled, the button renders at 50% opacity.
 */
public class MUITypeFilterButton implements IMUIWidget, ITooltip, net.minecraft.client.gui.GuiButtonAccessor {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");
    private static final int SIZE = 16;

    private final AEKeyType keyType;
    private int x;
    private int y;
    private boolean enabled = true;
    private boolean visible = true;
    private boolean hovered;

    @Nullable
    private Consumer<MUITypeFilterButton> onClick;

    public MUITypeFilterButton(int x, int y, AEKeyType keyType) {
        this.x = x;
        this.y = y;
        this.keyType = keyType;
    }

    public MUITypeFilterButton(int x, int y, AEKeyType keyType, Consumer<MUITypeFilterButton> onClick) {
        this(x, y, keyType);
        this.onClick = onClick;
    }

    // ========== State accessors ==========

    public AEKeyType getKeyType() {
        return keyType;
    }

    public boolean isTypeEnabled() {
        return enabled;
    }

    public void setTypeEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    // ========== IMUIWidget ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + SIZE && mouseY < screenY + SIZE;

        Minecraft mc = Minecraft.getMinecraft();

        if (this.enabled) {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            GlStateManager.color(0.5f, 0.5f, 0.5f, 1.0f);
        }

        mc.renderEngine.bindTexture(STATES_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 256 - SIZE, 256 - SIZE, SIZE, SIZE, 256, 256);

        ResourceLocation typeTexture = this.keyType.getButtonTexture();
        if (typeTexture != null) {
            mc.renderEngine.bindTexture(typeTexture);
            int u = this.keyType.getButtonIconU();
            int v = this.keyType.getButtonIconV();
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, u, v, SIZE, SIZE, 256, 256);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible || mouseButton != 0) {
            return false;
        }

        if (localX >= this.x && localX < this.x + SIZE
                && localY >= this.y && localY < this.y + SIZE) {
            this.enabled = !this.enabled;
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }

        return false;
    }

    // ========== ITooltip ==========

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.keyType.getDisplayName()).append("\n");

        if (this.enabled) {
            sb.append(TextFormatting.GRAY).append(ButtonToolTips.Enable.getLocal()).append(TextFormatting.RESET);
        } else {
            sb.append(TextFormatting.GRAY).append(ButtonToolTips.Disabled.getLocal()).append(TextFormatting.RESET);
        }

        return sb.toString();
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
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    // ========== GuiButtonAccessor (for MC button list compatibility) ==========

    /**
     * GuiButtonAccessor marker interface to allow drawing tooltips
     * for MUI widgets that appear in left-side button area.
     */
    public interface GuiButtonAccessor {
    }
}
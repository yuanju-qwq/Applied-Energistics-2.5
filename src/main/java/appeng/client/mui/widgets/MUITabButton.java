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

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI tab-style button. Renders a 22x22 background frame from {@code states.png}
 * and overlays an icon (either a states-atlas index or an {@link ItemStack}).
 * <p>
 * Full replacement for legacy {@code GuiTabButton}. Uses an {@link Consumer} click
 * callback instead of the legacy {@code actionPerformed(GuiButton)} flow, and
 * participates in the MUI widget lifecycle (registered with {@link AEBasePanel#addWidget}).
 */
public class MUITabButton implements IMUIWidget, IMUITooltip {

    private static final ResourceLocation STATES_TEXTURE =
            new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    private final int iconIndex;
    @Nullable
    private final ItemStack iconItem;
    private final String message;
    @Nullable
    private final RenderItem itemRenderer;

    private int x;
    private int y;
    private int hideEdge = 0;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean hovered = false;

    @Nullable
    private Consumer<MUITabButton> onClick;

    /**
     * Construct a tab button that displays a states-atlas icon.
     *
     * @param x       screen X
     * @param y       screen Y
     * @param icon    states-atlas icon index, or -1 for no icon
     * @param message tooltip text
     */
    public MUITabButton(int x, int y, int icon, String message) {
        this(x, y, icon, null, message, null);
    }

    /**
     * Construct a tab button that displays an item icon.
     *
     * @param x       screen X
     * @param y       screen Y
     * @param item    item to render as icon
     * @param message tooltip text
     * @param ir      item renderer (used only when {@code item} is not null)
     */
    public MUITabButton(int x, int y, ItemStack item, String message, RenderItem ir) {
        this(x, y, -1, item, message, ir);
    }

    private MUITabButton(int x, int y, int icon, @Nullable ItemStack item, String message,
            @Nullable RenderItem ir) {
        this.x = x;
        this.y = y;
        this.iconIndex = icon;
        this.iconItem = item;
        this.message = message;
        this.itemRenderer = ir;
    }

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        final int screenX = guiLeft + this.x;
        final int screenY = guiTop + this.y;
        final Minecraft mc = Minecraft.getMinecraft();

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + 22 && mouseY < screenY + 22;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);

        int uvX = (this.hideEdge > 0) ? 11 : 13;
        final int offsetX = (this.hideEdge > 0) ? 1 : 0;

        panel.drawModalRectWithCustomSizedTexture(screenX, screenY, uvX * 16, 0, 25, 22, 256, 256);

        if (this.iconIndex >= 0) {
            int uvY = (int) Math.floor(this.iconIndex / 16.0);
            uvX = this.iconIndex - uvY * 16;
            panel.drawModalRectWithCustomSizedTexture(offsetX + screenX + 3, screenY + 3,
                    uvX * 16, uvY * 16, 16, 16, 256, 256);
        }

        if (this.iconItem != null && this.itemRenderer != null) {
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRenderer.renderItemAndEffectIntoGUI(this.iconItem,
                    offsetX + screenX + 3, screenY + 3);
            GlStateManager.disableDepth();
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        // Tooltip rendering is handled by AEBasePanel.drawTooltip(IMUITooltip, ...).
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible || !this.enabled) {
            return false;
        }
        if (localX >= this.x && localY >= this.y
                && localX < this.x + 22 && localY < this.y + 22) {
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    // ========== IMUITooltip ==========

    @Override
    public String getMessage() {
        return this.message;
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
        return 22;
    }

    @Override
    public int getHeight() {
        return 22;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    // ========== Property accessors ==========

    public MUITabButton setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUITabButton setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public MUITabButton setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public MUITabButton setOnClick(@Nullable Consumer<MUITabButton> onClick) {
        this.onClick = onClick;
        return this;
    }

    public int getHideEdge() {
        return this.hideEdge;
    }

    public MUITabButton setHideEdge(int hideEdge) {
        this.hideEdge = hideEdge;
        return this;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}

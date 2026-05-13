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
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.ITooltip;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI tab button widget. Full replacement for legacy {@code GuiTabButton}.
 * <p>
 * Supports icon by atlas index or by {@link ItemStack}. Implements {@link ITooltip}
 * so that {@link AEBasePanel} can display tooltip text automatically.
 * <p>
 * The {@code hideEdge} parameter controls the tab background variant:
 * when non-zero, uses a narrower tab sprite (uv_x = 11 * 16) with a 1px X offset.
 */
public class MUITabContainer implements IMUIWidget, ITooltip {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private final int tabWidth = 22;
    private final int tabHeight = 22;
    private boolean visible = true;
    private int hideEdge = 0;

    private int iconIndex = -1;
    @Nullable
    private ItemStack iconItem;
    @Nullable
    private String tooltip;
    @Nullable
    private Consumer<MUITabContainer> onClick;

    private boolean hovered = false;

    // ========== Constructors ==========

    public MUITabContainer(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * Create a tab with an icon atlas index.
     */
    public MUITabContainer(int x, int y, int iconIndex, String tooltip) {
        this.x = x;
        this.y = y;
        this.iconIndex = iconIndex;
        this.tooltip = tooltip;
    }

    /**
     * Create a tab with an ItemStack icon.
     */
    public MUITabContainer(int x, int y, ItemStack iconItem, String tooltip) {
        this.x = x;
        this.y = y;
        this.iconItem = iconItem;
        this.tooltip = tooltip;
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
                && mouseX < screenX + this.tabWidth && mouseY < screenY + this.tabHeight;

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(STATES_TEXTURE);

        // Tab background (hideEdge controls sprite variant, matching legacy GuiTabButton)
        int uvX = (this.hideEdge > 0 ? 11 : 13);
        final int offsetX = this.hideEdge > 0 ? 1 : 0;
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, uvX * 16, 0, 25, 22, 256, 256);

        // Icon (atlas index)
        if (this.iconIndex >= 0) {
            int iconUvY = this.iconIndex / 16;
            int iconUvX = this.iconIndex - iconUvY * 16;
            Gui.drawModalRectWithCustomSizedTexture(offsetX + screenX + 3, screenY + 3,
                    iconUvX * 16, iconUvY * 16, 16, 16, 256, 256);
        }

        // Icon (ItemStack)
        if (this.iconItem != null && !this.iconItem.isEmpty()) {
            RenderItem itemRenderer = mc.getRenderItem();
            float prevZLevel = itemRenderer.zLevel;

            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();
            itemRenderer.zLevel = 100.0F;
            itemRenderer.renderItemAndEffectIntoGUI(this.iconItem, offsetX + screenX + 3, screenY + 3);
            GlStateManager.disableDepth();
            itemRenderer.zLevel = prevZLevel;
        }
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
                && localX < this.x + this.tabWidth && localY < this.y + this.tabHeight) {
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== ITooltip implementation ==========

    @Override
    public String getMessage() {
        return this.tooltip;
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
        return this.tabWidth;
    }

    @Override
    public int getHeight() {
        return this.tabHeight;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    // ========== Property accessors ==========

    public MUITabContainer setOnClick(@Nullable Consumer<MUITabContainer> onClick) {
        this.onClick = onClick;
        return this;
    }

    public MUITabContainer setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUITabContainer setIconItem(@Nullable ItemStack item) {
        this.iconItem = item;
        return this;
    }

    public MUITabContainer setIconIndex(int index) {
        this.iconIndex = index;
        return this;
    }

    public MUITabContainer setTooltip(@Nullable String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Nullable
    public String getTooltip() {
        return this.tooltip;
    }

    /**
     * Set the hide-edge mode. When non-zero, uses a narrower tab background sprite.
     * Mirrors legacy {@code GuiTabButton.setHideEdge(int)}.
     */
    public MUITabContainer setHideEdge(int hideEdge) {
        this.hideEdge = hideEdge;
        return this;
    }

    public int getHideEdge() {
        return this.hideEdge;
    }

    public MUITabContainer setPosition(int x, int y) {
        this.x = x;
        this.y = y;
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

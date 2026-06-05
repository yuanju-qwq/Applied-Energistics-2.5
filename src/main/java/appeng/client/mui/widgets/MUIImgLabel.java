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

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.core.localization.GuiText;

/**
 * MUI label widget. Renders a settings-based icon followed by a localized label
 * string on the same line. Full replacement for legacy {@code GuiImgLabel}.
 * <p>
 * The label supports an optional hidden tooltip value (shown on mouse hover) that
 * is separate from the on-screen label text, matching the legacy behaviour used by
 * the pattern provider's lock-reason indicator.
 */
public class MUIImgLabel implements IMUIWidget, IMUITooltip {

    private static final ResourceLocation STATES_TEXTURE =
            new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    private static Map<MUIButtonWidget.EnumPair, LabelAppearance> appearances;

    private final FontRenderer fontRenderer;
    private final Enum<?> labelSetting;
    private Enum<?> currentValue;
    private int x;
    private int y;
    private int renderedWidth = 16;
    private boolean visible = true;

    public MUIImgLabel(FontRenderer fontRenderer, int x, int y, Enum<?> idx, Enum<?> val) {
        this.fontRenderer = fontRenderer;
        this.x = x;
        this.y = y;
        this.currentValue = val;
        this.labelSetting = idx;
        ensureAppearancesInitialized();
    }

    /**
     * Initialize the appearance registry (mirrors the legacy {@code GuiImgLabel} table).
     */
    private static void ensureAppearancesInitialized() {
        if (appearances != null) {
            return;
        }
        appearances = new HashMap<>();
        registerApp(10, Settings.UNLOCK, LockCraftingMode.NONE, GuiText.NoneLock, null, 0x00FF00);
        registerApp(9, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_LOW, GuiText.CraftingLock,
                GuiText.LowRedstoneLock, 0xFF0000);
        registerApp(9, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_HIGH, GuiText.CraftingLock,
                GuiText.HighRedstoneLock, 0xFF0000);
        registerApp(9, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_PULSE, GuiText.CraftingLock,
                GuiText.UntilPulseUnlock, 0xFF0000);
        registerApp(9, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_RESULT, GuiText.CraftingLock,
                GuiText.ResultLock, 0xFF0000);
    }

    private static void registerApp(int iconIndex, Settings setting, Enum<?> val, GuiText label,
            Object hint, int color) {
        LabelAppearance a = new LabelAppearance();
        if (hint != null) {
            a.hiddenValue = (hint instanceof String) ? (String) hint : ((GuiText) hint).getUnlocalized();
        } else {
            a.hiddenValue = null;
        }
        a.index = iconIndex;
        a.displayLabel = label.getUnlocalized();
        a.color = color;
        appearances.put(new MUIButtonWidget.EnumPair(setting, val), a);
    }

    public void setVisibility(boolean vis) {
        this.visible = vis;
    }

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        int iconIndex = getIconIndex();
        if (iconIndex == -1) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().bindTexture(STATES_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        int uvY = (int) Math.floor(iconIndex / 16.0);
        int uvX = iconIndex - uvY * 16;

        panel.drawModalRectWithCustomSizedTexture(guiLeft + this.x, guiTop + this.y,
                uvX * 16, uvY * 16, 16, 16, 256, 256);

        if (this.labelSetting != null && this.currentValue != null) {
            LabelAppearance app = appearances
                    .get(new MUIButtonWidget.EnumPair(this.labelSetting, this.currentValue));
            if (app != null) {
                String translated = I18n.translateToLocal(app.displayLabel);
                this.fontRenderer.drawString(translated,
                        guiLeft + this.x + 16, guiTop + this.y + 5, app.color);
                this.renderedWidth = 16 + this.fontRenderer.getStringWidth(translated);
            }
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        // Tooltip rendering is handled by AEBasePanel.drawTooltip(IMUITooltip, ...) via IMUITooltip.
    }

    private int getIconIndex() {
        if (this.labelSetting != null && this.currentValue != null) {
            LabelAppearance app = appearances
                    .get(new MUIButtonWidget.EnumPair(this.labelSetting, this.currentValue));
            if (app != null) {
                return app.index;
            }
        }
        return -1;
    }

    public void set(Enum<?> e) {
        if (this.currentValue != e) {
            this.currentValue = e;
        }
    }

    @Override
    public String getMessage() {
        if (this.labelSetting != null && this.currentValue != null) {
            LabelAppearance app = appearances
                    .get(new MUIButtonWidget.EnumPair(this.labelSetting, this.currentValue));
            if (app != null && app.hiddenValue != null) {
                return I18n.translateToLocal(app.hiddenValue);
            }
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
        return this.renderedWidth;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        return false;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        return false;
    }

    /**
     * Internal appearance record for the icon/label/hidden-value table.
     */
    private static final class LabelAppearance {
        int index;
        String displayLabel;
        String hiddenValue;
        int color;
    }
}

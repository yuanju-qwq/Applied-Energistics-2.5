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

import appeng.api.storage.data.IAEStackType;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 循环切换按钮控件。
 * <p>
 * 用于在多个值之间循环切换（如 TypeToggleButton 的物品/流体切换）。
 * 每次点击切换到下一个值。
 */
public class MUICycleButtonWidget implements IMUIWidget {

    /**
     * 循环选项。
     */
    public static final class Option {
        private final String label;
        private final ResourceLocation texture;
        private final int iconU;
        private final int iconV;
        private final int tintColor;

        public Option(String label, ResourceLocation texture, int iconU, int iconV, int tintColor) {
            this.label = label;
            this.texture = texture;
            this.iconU = iconU;
            this.iconV = iconV;
            this.tintColor = tintColor;
        }

        public Option(String label, ResourceLocation texture, int iconU, int iconV) {
            this(label, texture, iconU, iconV, 0xFFFFFF);
        }

        public String getLabel() {
            return label;
        }
    }

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    private int x;
    private int y;
    private boolean visible = true;
    private boolean[] optionEnabled;

    private final Option[] options;
    private int currentIndex = 0;

    @Nullable
    private Consumer<MUICycleButtonWidget> onCycle;

    public MUICycleButtonWidget(int x, int y, Option... options) {
        this.x = x;
        this.y = y;
        this.options = options;
        this.optionEnabled = new boolean[options.length];
        for (int i = 0; i < optionEnabled.length; i++) {
            optionEnabled[i] = true;
        }
    }

    /**
     * 从 {@link IAEStackType} 列表创建类型切换按钮。
     */
    public static MUICycleButtonWidget fromStackTypes(int x, int y, IAEStackType<?>... types) {
        Option[] opts = new Option[types.length];
        for (int i = 0; i < types.length; i++) {
            IAEStackType<?> t = types[i];
            ResourceLocation tex = t.getButtonTexture();
            opts[i] = new Option(t.getDisplayName(),
                    tex != null ? tex : STATES_TEXTURE,
                    t.getButtonIconU(), t.getButtonIconV());
        }
        return new MUICycleButtonWidget(x, y, opts);
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible || this.options.length == 0) {
            return;
        }

        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;
        Minecraft mc = Minecraft.getMinecraft();

        Option current = this.options[this.currentIndex];
        boolean enabled = this.optionEnabled[this.currentIndex];

        if (enabled) {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            GlStateManager.color(0.5f, 0.5f, 0.5f, 1.0f);
        }

        mc.getTextureManager().bindTexture(STATES_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 240, 240, 16, 16, 256, 256);

        if (current.texture != null) {
            mc.getTextureManager().bindTexture(current.texture);
            float r = ((current.tintColor >> 16) & 0xFF) / 255.0f;
            float g = ((current.tintColor >> 8) & 0xFF) / 255.0f;
            float b = (current.tintColor & 0xFF) / 255.0f;
            GlStateManager.color(r, g, b, 1.0f);
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY,
                    current.iconU, current.iconV, 16, 16, 256, 256);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible || this.options.length == 0) {
            return false;
        }
        if (localX >= this.x && localY >= this.y
                && localX < this.x + 16 && localY < this.y + 16) {
            this.currentIndex = (this.currentIndex + 1) % this.options.length;
            if (this.onCycle != null) {
                this.onCycle.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public int getCurrentIndex() {
        return this.currentIndex;
    }

    public MUICycleButtonWidget setCurrentIndex(int index) {
        this.currentIndex = index % this.options.length;
        return this;
    }

    public Option getCurrentOption() {
        return this.options[this.currentIndex];
    }

    public MUICycleButtonWidget setOptionEnabled(int index, boolean enabled) {
        this.optionEnabled[index] = enabled;
        return this;
    }

    public MUICycleButtonWidget setOnCycle(@Nullable Consumer<MUICycleButtonWidget> onCycle) {
        this.onCycle = onCycle;
        return this;
    }

    public MUICycleButtonWidget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUICycleButtonWidget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }
}

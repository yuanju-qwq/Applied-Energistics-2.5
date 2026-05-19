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

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStackType;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 类型筛选控件。
 * <p>
 * 显示一排类型切换按钮（物品、流体等），点击切换某类型的显示/隐藏。
 * 与 {@link ItemRepo} 联动：点击时自动调用 {@link ItemRepo#setTypeFilter(AEKeyType, boolean)}。
 */
@SideOnly(Side.CLIENT)
public class MUITypeFilter implements IMUIWidget {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    /**
     * Type button data, driven by AEKeyType.
     */
    private static final class TypeButton {
        final AEKeyType keyType;
        boolean enabled;
        int x;
        int y;

        TypeButton(AEKeyType keyType, boolean enabled, int x, int y) {
            this.keyType = keyType;
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }

    private final List<TypeButton> buttons = new ArrayList<>();
    private final int baseX;
    private final int baseY;
    private final int spacing;

    @Nullable
    private ItemRepo repo;

    /**
     * @param baseX   第一个按钮的面板内 X
     * @param baseY   第一个按钮的面板内 Y
     * @param spacing 按钮间距（水平排列）
     */
    public MUITypeFilter(int baseX, int baseY, int spacing) {
        this.baseX = baseX;
        this.baseY = baseY;
        this.spacing = spacing;
    }

    /**
     * Build buttons from all registered AEKeyTypes.
     * All types default to enabled.
     */
    public MUITypeFilter buildFromRegistry() {
        this.buttons.clear();
        int idx = 0;
        for (AEKeyType keyType : AEKeyType.getAllTypes()) {
            int x = this.baseX + idx * this.spacing;
            this.buttons.add(new TypeButton(keyType, true, x, this.baseY));
            idx++;
        }
        syncToRepo();
        return this;
    }

    /**
     * Build buttons from an array of AEKeyTypes.
     */
    public MUITypeFilter buildFromKeyTypes(AEKeyType... types) {
        this.buttons.clear();
        for (int i = 0; i < types.length; i++) {
            int x = this.baseX + i * this.spacing;
            this.buttons.add(new TypeButton(types[i], true, x, this.baseY));
        }
        syncToRepo();
        return this;
    }

    /**
     * @deprecated Use {@link #buildFromKeyTypes(AEKeyType...)} instead.
     */
    @Deprecated
    public MUITypeFilter buildFromTypes(IAEStackType<?>... types) {
        this.buttons.clear();
        for (int i = 0; i < types.length; i++) {
            AEKeyType keyType = AEKeyType.fromLegacyType(types[i]);
            if (keyType != null) {
                int x = this.baseX + i * this.spacing;
                this.buttons.add(new TypeButton(keyType, true, x, this.baseY));
            }
        }
        syncToRepo();
        return this;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();

        for (TypeButton btn : this.buttons) {
            int screenX = guiLeft + btn.x;
            int screenY = guiTop + btn.y;

            // 背景
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, 240, 240, 16, 16, 256, 256);

            // 类型图标
            ResourceLocation tex = btn.keyType.getButtonTexture();
            if (tex != null) {
                mc.getTextureManager().bindTexture(tex);
            }
            int iconU = btn.keyType.getButtonIconU();
            int iconV = btn.keyType.getButtonIconV();

            if (btn.enabled) {
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GlStateManager.color(0.4f, 0.4f, 0.4f, 0.6f);
            }
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, iconU, iconV, 16, 16, 256, 256);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // ========== Input events ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        for (TypeButton btn : this.buttons) {
            if (localX >= btn.x && localY >= btn.y
                    && localX < btn.x + 16 && localY < btn.y + 16) {
                btn.enabled = !btn.enabled;
                syncToRepo();
                return true;
            }
        }
        return false;
    }

    // ========== 联动 ==========

    /**
     * Sync current button states to the associated ItemRepo using AEKeyType.
     */
    private void syncToRepo() {
        if (this.repo == null) {
            return;
        }
        for (TypeButton btn : this.buttons) {
            this.repo.setTypeFilter(btn.keyType, btn.enabled);
        }
    }

    // ========== 属性 ==========

    public MUITypeFilter setRepo(@Nullable ItemRepo repo) {
        this.repo = repo;
        syncToRepo();
        return this;
    }

    /**
     * Check if a given AEKeyType is enabled.
     */
    public boolean isTypeEnabled(AEKeyType type) {
        for (TypeButton btn : this.buttons) {
            if (btn.keyType == type) {
                return btn.enabled;
            }
        }
        return true;
    }

    /**
     * @deprecated Use {@link #isTypeEnabled(AEKeyType)} instead.
     * 判断指定类型是否启用。
     */
    @Deprecated
    public boolean isTypeEnabled(IAEStackType<?> type) {
        AEKeyType keyType = AEKeyType.fromLegacyType(type);
        return keyType != null && isTypeEnabled(keyType);
    }

    /**
     * Set enable state for an AEKeyType.
     */
    public MUITypeFilter setTypeEnabled(AEKeyType type, boolean enabled) {
        for (TypeButton btn : this.buttons) {
            if (btn.keyType == type) {
                btn.enabled = enabled;
                break;
            }
        }
        syncToRepo();
        return this;
    }

    /**
     * @deprecated Use {@link #setTypeEnabled(AEKeyType, boolean)} instead.
     * 设置指定类型的启用状态。
     */
    @Deprecated
    public MUITypeFilter setTypeEnabled(IAEStackType<?> type, boolean enabled) {
        AEKeyType keyType = AEKeyType.fromLegacyType(type);
        if (keyType != null) {
            setTypeEnabled(keyType, enabled);
        }
        return this;
    }
}

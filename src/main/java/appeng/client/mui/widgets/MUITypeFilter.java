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

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 类型筛选控件。
 * <p>
 * 显示一排类型切换按钮（物品、流体等），点击切换某类型的显示/隐藏。
 * 与 {@link ItemRepo} 联动：点击时自动调用 {@link ItemRepo#setTypeFilter(IAEStackType, boolean)}。
 */
@SideOnly(Side.CLIENT)
public class MUITypeFilter implements IMUIWidget {

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    /**
     * 类型按钮数据。
     */
    private static final class TypeButton {
        final IAEStackType<?> type;
        boolean enabled;
        int x;
        int y;

        TypeButton(IAEStackType<?> type, boolean enabled, int x, int y) {
            this.type = type;
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
     * 根据已注册的所有 IAEStackType 自动构建按钮。
     * 全部默认启用。
     */
    public MUITypeFilter buildFromRegistry() {
        this.buttons.clear();
        int idx = 0;
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            int x = this.baseX + idx * this.spacing;
            this.buttons.add(new TypeButton(type, true, x, this.baseY));
            idx++;
        }
        syncToRepo();
        return this;
    }

    /**
     * 从指定类型列表构建按钮。
     */
    public MUITypeFilter buildFromTypes(IAEStackType<?>... types) {
        this.buttons.clear();
        for (int i = 0; i < types.length; i++) {
            int x = this.baseX + i * this.spacing;
            this.buttons.add(new TypeButton(types[i], true, x, this.baseY));
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
            ResourceLocation tex = btn.type.getButtonTexture();
            if (tex != null) {
                mc.getTextureManager().bindTexture(tex);
            }
            int iconU = btn.type.getButtonIconU();
            int iconV = btn.type.getButtonIconV();

            if (btn.enabled) {
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            } else {
                GlStateManager.color(0.4f, 0.4f, 0.4f, 0.6f);
            }
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY, iconU, iconV, 16, 16, 256, 256);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    // ========== 输入事件 ==========

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
     * 将当前按钮状态同步到关联的 ItemRepo。
     */
    private void syncToRepo() {
        if (this.repo == null) {
            return;
        }
        for (TypeButton btn : this.buttons) {
            this.repo.setTypeFilter(btn.type, btn.enabled);
        }
    }

    // ========== 属性 ==========

    public MUITypeFilter setRepo(@Nullable ItemRepo repo) {
        this.repo = repo;
        syncToRepo();
        return this;
    }

    /**
     * 判断指定类型是否启用。
     */
    public boolean isTypeEnabled(IAEStackType<?> type) {
        for (TypeButton btn : this.buttons) {
            if (btn.type == type) {
                return btn.enabled;
            }
        }
        return true;
    }

    /**
     * 设置指定类型的启用状态。
     */
    public MUITypeFilter setTypeEnabled(IAEStackType<?> type, boolean enabled) {
        for (TypeButton btn : this.buttons) {
            if (btn.type == type) {
                btn.enabled = enabled;
                break;
            }
        }
        syncToRepo();
        return this;
    }
}

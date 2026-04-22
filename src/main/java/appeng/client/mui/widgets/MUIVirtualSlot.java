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

import java.util.List;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 版 ME 终端虚拟槽位。
 * <p>
 * 独立于旧 {@link appeng.client.gui.widgets.GuiCustomSlot} 体系，
 * 直接 implements {@link IMUIWidget}，从 {@link ItemRepo} 读取显示数据。
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>渲染 AE 栈图标（物品/流体统一）</li>
 *   <li>渲染数量叠加层（数字/合成标记）</li>
 *   <li>鼠标悬停高亮和点击回调</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUIVirtualSlot implements IMUIWidget {

    private final int x;
    private final int y;
    private final int slotIndex;
    private final int size;

    private ItemRepo repo;
    private boolean showAmount = true;
    private boolean showCraftableText = false;
    private boolean hovered = false;

    @Nullable
    private BiConsumer<MUIVirtualSlot, Integer> onClicked;

    /**
     * @param x         面板内 X 坐标
     * @param y         面板内 Y 坐标
     * @param slotIndex 在 ItemRepo 视图中的槽位索引（0-based，不含滚动偏移）
     * @param repo      关联的 ItemRepo
     */
    public MUIVirtualSlot(int x, int y, int slotIndex, ItemRepo repo) {
        this(x, y, slotIndex, repo, 16);
    }

    public MUIVirtualSlot(int x, int y, int slotIndex, ItemRepo repo, int size) {
        this.x = x;
        this.y = y;
        this.slotIndex = slotIndex;
        this.repo = repo;
        this.size = size;
    }

    // ========== 数据 ==========

    /**
     * @return 此槽位当前应显示的 AE 栈（从 Repo 中根据索引+滚动偏移获取）
     */
    @Nullable
    public IAEStack<?> getAEStack() {
        return this.repo != null ? this.repo.getReferenceItem(this.slotIndex) : null;
    }

    // ========== 绘制 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        int screenX = guiLeft + this.x;
        int screenY = guiTop + this.y;

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + this.size && mouseY < screenY + this.size;

        IAEStack<?> stack = this.getAEStack();
        if (stack == null) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // 图标
        MUIStackRenderer.renderStackIcon(mc, stack, screenX, screenY);

        // 数量叠加
        MUIStackRenderer.renderStackOverlay(mc, stack, screenX, screenY,
                this.showAmount, this.showCraftableText);

        // 悬停高亮
        if (this.hovered) {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.colorMask(true, true, true, false);
            Gui.drawRect(screenX, screenY, screenX + this.size, screenY + this.size, 0x80FFFFFF);
            GlStateManager.colorMask(true, true, true, true);
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        if (this.hovered) {
            IAEStack<?> stack = this.getAEStack();
            if (stack != null) {
                List<String> tooltip = MUIStackRenderer.buildTooltip(stack);
                if (!tooltip.isEmpty()) {
                    Minecraft mc = Minecraft.getMinecraft();
                    net.minecraftforge.fml.client.config.GuiUtils.drawHoveringText(
                            tooltip, localX, localY, mc.displayWidth, mc.displayHeight, -1, mc.fontRenderer);
                }
            }
        }
    }

    // ========== 输入事件 ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (localX >= this.x && localY >= this.y
                && localX < this.x + this.size && localY < this.y + this.size) {
            if (this.onClicked != null) {
                this.onClicked.accept(this, mouseButton);
            }
            return true;
        }
        return false;
    }

    // ========== 属性 ==========

    public int getSlotIndex() {
        return this.slotIndex;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public MUIVirtualSlot setRepo(ItemRepo repo) {
        this.repo = repo;
        return this;
    }

    public MUIVirtualSlot setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
        return this;
    }

    public MUIVirtualSlot setShowCraftableText(boolean showCraftableText) {
        this.showCraftableText = showCraftableText;
        return this;
    }

    public MUIVirtualSlot setOnClicked(@Nullable BiConsumer<MUIVirtualSlot, Integer> handler) {
        this.onClicked = handler;
        return this;
    }
}

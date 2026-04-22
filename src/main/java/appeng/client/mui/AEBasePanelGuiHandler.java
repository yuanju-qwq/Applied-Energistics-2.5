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

package appeng.client.mui;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;

import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.api.storage.data.IAEStack;
import appeng.client.ClientHelper;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.interfaces.ISpecialSlotIngredient;
import appeng.core.AELog;

/**
 * MUI 面板的 JEI 集成处理器。
 * <p>
 * 功能与 {@link appeng.client.gui.AEGuiHandler} 完全对等，但绑定到 {@link AEBasePanel}：
 * <ul>
 *   <li>{@link IAdvancedGuiHandler} — JEI 排除区域 + 鼠标下物品识别</li>
 *   <li>{@link IGhostIngredientHandler} — JEI 拖拽物品到幽灵槽位</li>
 * </ul>
 *
 * @see AEBasePanel#getJEIExclusionArea()
 * @see AEBasePanel#getGuiSlots()
 */
public class AEBasePanelGuiHandler
        implements IAdvancedGuiHandler<AEBasePanel>, IGhostIngredientHandler<AEBasePanel> {

    // ========== IAdvancedGuiHandler ==========

    @Override
    @Nonnull
    public Class<AEBasePanel> getGuiContainerClass() {
        return AEBasePanel.class;
    }

    @Nullable
    @Override
    public List<Rectangle> getGuiExtraAreas(@Nonnull AEBasePanel panel) {
        return panel.getJEIExclusionArea();
    }

    @Nullable
    @Override
    public Object getIngredientUnderMouse(@Nonnull AEBasePanel panel, int mouseX, int mouseY) {
        // 特殊面板：合成确认（CraftConfirm）和合成 CPU（CraftingCPU）
        // 它们有 getVisual() + getDisplayedRows() 的虚拟物品列表
        if (panel instanceof IMUIVisualListPanel) {
            IMUIVisualListPanel visualPanel = (IMUIVisualListPanel) panel;
            int guiSlotIdx = getSlotIdx(panel, mouseX, mouseY, visualPanel.getDisplayedRows());
            List<IAEStack<?>> visual = visualPanel.getVisual();
            if (guiSlotIdx >= 0 && guiSlotIdx < visual.size()) {
                return visual.get(guiSlotIdx).asItemStackRepresentation();
            }
            return null;
        }

        // 标准槽位：检查 Forge Slot
        Slot slot = panel.getSlotUnderMouse();
        if (slot instanceof ISpecialSlotIngredient) {
            return ((ISpecialSlotIngredient) slot).getIngredient();
        }

        // 自定义槽位：检查 GuiCustomSlot
        for (GuiCustomSlot customSlot : panel.getGuiSlots()) {
            if (checkSlotArea(panel, customSlot, mouseX, mouseY)) {
                return customSlot.getIngredient();
            }
        }

        return null;
    }

    // ========== IGhostIngredientHandler ==========

    @SuppressWarnings("unchecked")
    @Override
    @Nonnull
    public <I> List<Target<I>> getTargets(@Nonnull AEBasePanel panel, @Nonnull I ingredient,
            boolean doStart) {
        // HEI 书签物品兼容
        if (ClientHelper.isHei) {
            Object ingToUse = getIngFromBookmarkItem(ingredient);
            if (ingToUse != null) {
                return this.getTargets(panel, (I) ingToUse, doStart);
            }
        }

        // VirtualMEPhantomSlot 虚拟幽灵槽位
        final List<Target<I>> virtualTargets = this.getVirtualTargets(panel, ingredient);
        if (!virtualTargets.isEmpty()) {
            return virtualTargets;
        }

        // IJEIGhostIngredients 接口（面板自定义实现）
        if (panel instanceof IJEIGhostIngredients) {
            IJEIGhostIngredients g = (IJEIGhostIngredients) panel;
            return (List<Target<I>>) (Object) g.getPhantomTargets(ingredient);
        }

        return Collections.emptyList();
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    // ========== 内部辅助方法 ==========

    /**
     * 检查鼠标是否在自定义槽位区域内。
     */
    private boolean checkSlotArea(GuiContainer gui, GuiCustomSlot slot, int mouseX, int mouseY) {
        int i = gui.guiLeft;
        int j = gui.guiTop;
        mouseX = mouseX - i;
        mouseY = mouseY - j;
        return mouseX >= slot.xPos() - 1
                && mouseX < slot.xPos() + slot.getWidth() + 1
                && mouseY >= slot.yPos() - 1
                && mouseY < slot.yPos() + slot.getHeight() + 1;
    }

    /**
     * 从 CraftConfirm / CraftingCPU 的虚拟物品列表中计算槽位索引。
     */
    private int getSlotIdx(AEBasePanel panel, int mouseX, int mouseY, int rows) {
        int guileft = panel.getGuiLeft();
        int guitop = panel.getGuiTop();
        int currentScroll = panel.getScrollBar() != null ? panel.getScrollBar().getCurrentScroll() : 0;
        final int xo = 9;
        final int yo = 19;

        int guiSlotx = (mouseX - guileft - xo) / 67;
        if (guiSlotx > 2 || mouseX < guileft + xo) {
            return -1;
        }
        int guiSloty = (mouseY - guitop - yo) / 23;
        if (guiSloty > (rows - 1) || mouseY < guitop + yo) {
            return -1;
        }
        return (guiSloty * 3) + guiSlotx + (currentScroll * 3);
    }

    /**
     * 获取 VirtualMEPhantomSlot 的 JEI 拖拽目标。
     */
    private <I> List<Target<I>> getVirtualTargets(@Nonnull AEBasePanel panel, @Nonnull I ingredient) {
        if (!(ingredient instanceof net.minecraft.item.ItemStack)) {
            return Collections.emptyList();
        }
        net.minecraft.item.ItemStack itemStack = (net.minecraft.item.ItemStack) ingredient;

        final List<Target<I>> result = new ArrayList<>();
        for (GuiCustomSlot customSlot : panel.getGuiSlots()) {
            if (!(customSlot instanceof VirtualMEPhantomSlot)) {
                continue;
            }
            VirtualMEPhantomSlot virtualSlot = (VirtualMEPhantomSlot) customSlot;
            if (!virtualSlot.isVisible() || !virtualSlot.isSlotEnabled()) {
                continue;
            }

            result.add(new Target<I>() {
                @Override
                public @NotNull Rectangle getArea() {
                    return new Rectangle(
                            panel.getGuiLeft() + virtualSlot.xPos(),
                            panel.getGuiTop() + virtualSlot.yPos(),
                            virtualSlot.getWidth(), virtualSlot.getHeight());
                }

                @Override
                public void accept(@NotNull I ignored) {
                    virtualSlot.handleMouseClicked(itemStack, false, 0);
                }
            });
        }

        return result;
    }

    /**
     * HEI 书签物品解包（反射获取内部 ingredient）。
     */
    @Nullable
    private Object getIngFromBookmarkItem(Object ingredient) {
        try {
            Class<?> bookmarkItemClass = Class.forName("mezz.jei.bookmarks.BookmarkItem");
            if (bookmarkItemClass.isAssignableFrom(ingredient.getClass())) {
                Field ingredientField = bookmarkItemClass.getDeclaredField("ingredient");
                return ingredientField.get(ingredient);
            }
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
            AELog.error("Could not normalise bookmark item ingredient: ", e);
        }
        return null;
    }

    /**
     * MUI 面板中有虚拟物品列表（如合成确认/合成 CPU）的通用接口。
     * <p>
     * 实现此接口的面板可以让 JEI 识别虚拟列表中鼠标下的物品。
     */
    public interface IMUIVisualListPanel {
        List<IAEStack<?>> getVisual();

        int getDisplayedRows();
    }
}

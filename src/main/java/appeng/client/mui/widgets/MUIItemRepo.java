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
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 版 ItemRepo 显示控件。
 * <p>
 * 组合 {@link ItemRepo}（数据层）和一组 {@link MUIVirtualSlot}（渲染层），
 * 在 {@link AEBasePanel} 中统一管理终端的网格显示。
 *
 * <h3>使用方式</h3>
 * <pre>
 * MUIItemRepo repoWidget = new MUIItemRepo(8, 18, 9, 6, scrollBar, sortSource);
 * repoWidget.setOnSlotClicked((slot, button) -> { ... });
 * panel.addWidget(repoWidget);
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class MUIItemRepo implements IMUIWidget {

    /** 底层数据仓库 */
    private final ItemRepo repo;

    /** 虚拟槽位网格 */
    private final List<MUIVirtualSlot> slots = new ArrayList<>();

    /** 网格左上角在面板中的 X */
    private final int gridX;
    /** 网格左上角在面板中的 Y */
    private final int gridY;
    /** 每行列数 */
    private final int columns;
    /** 可见行数 */
    private final int rows;
    /** 槽位大小（含间距） */
    private final int slotSize;

    @Nullable
    private BiConsumer<MUIVirtualSlot, Integer> onSlotClicked;

    /**
     * @param gridX      网格起始 X（面板内坐标）
     * @param gridY      网格起始 Y（面板内坐标）
     * @param columns    每行列数
     * @param rows       可见行数
     * @param scrollSrc  滚动数据源
     * @param sortSrc    排序数据源
     */
    public MUIItemRepo(int gridX, int gridY, int columns, int rows,
            IScrollSource scrollSrc, ISortSource sortSrc) {
        this(gridX, gridY, columns, rows, 18, scrollSrc, sortSrc);
    }

    public MUIItemRepo(int gridX, int gridY, int columns, int rows, int slotSize,
            IScrollSource scrollSrc, ISortSource sortSrc) {
        this.gridX = gridX;
        this.gridY = gridY;
        this.columns = columns;
        this.rows = rows;
        this.slotSize = slotSize;
        this.repo = new ItemRepo(scrollSrc, sortSrc);

        buildSlotGrid();
    }

    private void buildSlotGrid() {
        this.slots.clear();
        for (int row = 0; row < this.rows; row++) {
            for (int col = 0; col < this.columns; col++) {
                int idx = row * this.columns + col;
                int x = this.gridX + col * this.slotSize;
                int y = this.gridY + row * this.slotSize;
                MUIVirtualSlot slot = new MUIVirtualSlot(x, y, idx, this.repo, this.slotSize);
                slot.setShowAmount(true);
                slot.setShowCraftableText(true);
                slot.setOnClicked((s, btn) -> {
                    if (this.onSlotClicked != null) {
                        this.onSlotClicked.accept(s, btn);
                    }
                });
                this.slots.add(slot);
            }
        }
    }

    // ========== IMUIWidget 委托 ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        this.repo.updateView();
        for (MUIVirtualSlot slot : this.slots) {
            slot.drawBackground(panel, guiLeft, guiTop, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        for (MUIVirtualSlot slot : this.slots) {
            slot.drawForeground(panel, localX, localY);
        }
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        for (MUIVirtualSlot slot : this.slots) {
            if (slot.mouseClicked(localX, localY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    // ========== 数据操作（委托到 ItemRepo） ==========

    /**
     * 更新一个 AE 栈。
     */
    public void postUpdate(IAEStack<?> stack) {
        this.repo.postUpdate(stack);
    }

    /**
     * 清空所有数据。
     */
    public void clear() {
        this.repo.clear();
    }

    /**
     * 设置搜索关键词。
     */
    public void setSearchText(String search) {
        this.repo.setSearch(search);
    }

    /**
     * 设置是否有电力。
     */
    public void setPowered(boolean powered) {
        this.repo.setPower(powered);
    }

    /**
     * @return 底层 ItemRepo（供高级操作使用）
     */
    public ItemRepo getRepo() {
        return this.repo;
    }

    /**
     * @return 虚拟槽位列表
     */
    public List<MUIVirtualSlot> getSlots() {
        return this.slots;
    }

    // ========== 属性 ==========

    public MUIItemRepo setOnSlotClicked(@Nullable BiConsumer<MUIVirtualSlot, Integer> handler) {
        this.onSlotClicked = handler;
        return this;
    }

    public int getColumns() {
        return this.columns;
    }

    public int getRows() {
        return this.rows;
    }

    /**
     * @return 总可见槽位数
     */
    public int getVisibleSlotCount() {
        return this.columns * this.rows;
    }
}

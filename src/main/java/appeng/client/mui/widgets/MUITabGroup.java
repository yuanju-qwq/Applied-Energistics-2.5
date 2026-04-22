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
import java.util.function.Consumer;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;

/**
 * MUI 标签页组控件。
 * <p>
 * 管理一组 {@link MUITabContainer} 标签按钮，支持自动布局。
 * 替代旧的 UniversalTerminalButtons，提供通用的标签组管理。
 */
public class MUITabGroup implements IMUIWidget {

    /**
     * 标签页数据。
     */
    public static final class TabEntry {
        private final String id;
        private final String tooltip;
        @Nullable
        private final ItemStack iconItem;
        private final int iconIndex;

        public TabEntry(String id, String tooltip, ItemStack iconItem) {
            this.id = id;
            this.tooltip = tooltip;
            this.iconItem = iconItem;
            this.iconIndex = -1;
        }

        public TabEntry(String id, String tooltip, int iconIndex) {
            this.id = id;
            this.tooltip = tooltip;
            this.iconItem = null;
            this.iconIndex = iconIndex;
        }

        public String getId() {
            return this.id;
        }
    }

    private int baseX;
    private int baseY;
    private int spacing = 24;
    private boolean vertical = true;

    private final List<TabEntry> entries = new ArrayList<>();
    private final List<MUITabContainer> tabs = new ArrayList<>();

    @Nullable
    private Consumer<String> onTabSelected;

    public MUITabGroup(int baseX, int baseY) {
        this.baseX = baseX;
        this.baseY = baseY;
    }

    /**
     * 添加一个标签页。
     */
    public MUITabGroup addTab(TabEntry entry) {
        this.entries.add(entry);
        return this;
    }

    /**
     * 构建所有标签按钮。在所有 addTab 完成后调用。
     */
    public MUITabGroup build() {
        this.tabs.clear();
        for (int i = 0; i < this.entries.size(); i++) {
            TabEntry entry = this.entries.get(i);
            int tabX, tabY;
            if (this.vertical) {
                tabX = this.baseX;
                tabY = this.baseY + i * this.spacing;
            } else {
                tabX = this.baseX + i * this.spacing;
                tabY = this.baseY;
            }

            MUITabContainer tab;
            if (entry.iconItem != null) {
                tab = new MUITabContainer(tabX, tabY, entry.iconItem, entry.tooltip);
            } else {
                tab = new MUITabContainer(tabX, tabY, entry.iconIndex, entry.tooltip);
            }

            final String entryId = entry.id;
            tab.setOnClick(t -> {
                if (this.onTabSelected != null) {
                    this.onTabSelected.accept(entryId);
                }
            });

            this.tabs.add(tab);
        }
        return this;
    }

    // ========== 绘制（委托给子标签） ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        for (MUITabContainer tab : this.tabs) {
            tab.drawBackground(panel, guiLeft, guiTop, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        for (MUITabContainer tab : this.tabs) {
            tab.drawForeground(panel, localX, localY);
        }
    }

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        for (MUITabContainer tab : this.tabs) {
            if (tab.mouseClicked(localX, localY, mouseButton)) {
                return true;
            }
        }
        return false;
    }

    // ========== 属性 ==========

    public MUITabGroup setOnTabSelected(@Nullable Consumer<String> handler) {
        this.onTabSelected = handler;
        return this;
    }

    public MUITabGroup setSpacing(int spacing) {
        this.spacing = spacing;
        return this;
    }

    public MUITabGroup setVertical(boolean vertical) {
        this.vertical = vertical;
        return this;
    }

    public List<MUITabContainer> getTabs() {
        return this.tabs;
    }
}

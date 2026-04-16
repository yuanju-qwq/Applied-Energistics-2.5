
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

package appeng.client.me;


import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import java.util.*;

public class ItemRepo {

    /**
     * 多类型存储列表：每种 IAEStackType 对应一个 IItemList。
     */
    @SuppressWarnings("rawtypes")
    private final Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();

    /**
     * 视图列表：经过搜索、过滤、排序后的展示列表，包含所有类型的栈。
     */
    private List<IAEStack<?>> view = new ArrayList<>();
    private final IScrollSource src;
    private final ISortSource sortSrc;

    /**
     * 类型过滤：某种类型是否在终端中启用显示。
     */
    private final Map<IAEStackType<?>, Boolean> typeFilters = new IdentityHashMap<>();

    private int rowSize = 9;

    private String searchString = "";
    private IPartitionList<IAEItemStack> myPartitionList;
    private boolean hasPower;

    private Enum lastView;
    private Enum lastSearchMode;
    private Enum lastSortBy;
    private Enum lastSortDir;
    private String lastSearch = "";

    private boolean resort = true;
    private boolean changed = false;


    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    /**
     * 获取指定索引处的 AE 栈（考虑滚动偏移）。
     */
    public IAEStack<?> getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    void setSearch(final String search) {
        this.searchString = search == null ? "" : search;
    }

    /**
     * 更新一个 AE 栈（任意类型：物品、流体等）。
     */
    @SuppressWarnings("unchecked")
    public void postUpdate(final IAEStack<?> is) {
        IAEStackType<?> type = is.getStackType();
        IItemList list = this.lists.computeIfAbsent(type, t -> t.createList());

        final IAEStack st = list.findPrecise(is);
        if (st != null) {
            st.reset();
            st.add(is);
        } else {
            list.add(is);
        }

        changed = true;
    }

    public long getItemCount(final IAEItemStack is) {
        IItemList<IAEItemStack> list = this.getTypedList(is);
        if (list == null) {
            return 0;
        }
        IAEItemStack st = list.findPrecise(is);
        return st == null ? 0 : st.getStackSize();
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> IItemList<T> getTypedList(T stack) {
        return this.lists.get(stack.getStackType());
    }

    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.changed = true;
    }

    @SuppressWarnings("unchecked")
    public void updateView() {

        final Enum viewMode = this.sortSrc.getSortDisplay();

        if (lastView != viewMode) {
            resort = true;
            lastView = viewMode;
        }

        final Enum searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        if (lastSearchMode != searchMode) {
            resort = true;
            lastSearchMode = searchMode;
        }

        if (searchMode == SearchBoxMode.JEI_AUTOSEARCH || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH || searchMode == SearchBoxMode.JEI_AUTOSEARCH_KEEP || searchMode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP) {
            this.updateJEI(this.searchString);
        }

        if (!lastSearch.equals(searchString)) {
            resort = true;
            lastSearch = searchString;
        }

        final Enum sortBy = this.sortSrc.getSortBy();
        final Enum sortDir = this.sortSrc.getSortDir();

        if (lastSortBy != sortBy) {
            resort = true;
            lastSortBy = sortBy;
        }

        if (lastSortDir != sortDir) {
            resort = true;
            lastSortDir = sortDir;
        }

        if (changed || resort) {
            changed = false;
            resort = false;

            view = new ArrayList<>();

            ItemSorters.setDirection((appeng.api.config.SortDir) sortDir);
            ItemSorters.init();

            // 遍历所有类型的列表
            for (var entry : this.lists.entrySet()) {
                IAEStackType<?> type = entry.getKey();

                // 检查类型过滤
                if (!this.typeFilters.getOrDefault(type, true)) {
                    continue;
                }

                IItemList list = entry.getValue();
                for (Object obj : list) {
                    IAEStack<?> is = (IAEStack<?>) obj;
                    addIAE(is, viewMode);
                }
            }

            // 排序：物品使用 ItemSorters，流体按名称排序
            Comparator<IAEStack<?>> c = getGenericComparator(sortBy);
            view.sort(c);
        }
    }

    /**
     * 获取支持多类型的排序比较器。
     */
    private static Comparator<IAEStack<?>> getGenericComparator(Enum sortBy) {
        return (a, b) -> {
            // 不同类型之间：物品优先于流体
            if (a.getStackType() != b.getStackType()) {
                return a.getStackType().getId().compareTo(b.getStackType().getId());
            }

            // 同一类型内部：使用对应的排序逻辑
            if (a instanceof IAEItemStack ia && b instanceof IAEItemStack ib) {
                return getItemComparator(sortBy).compare(ia, ib);
            }

            // 流体或其他类型：按名称 → 数量排序
            String nameA = a.asItemStackRepresentation().getDisplayName();
            String nameB = b.asItemStackRepresentation().getDisplayName();
            int cmp = nameA.compareToIgnoreCase(nameB);
            if (cmp != 0) return cmp;
            return Long.compare(b.getStackSize(), a.getStackSize());
        };
    }

    private static Comparator<IAEItemStack> getItemComparator(Enum sortBy) {
        Comparator<IAEItemStack> c;

        if (sortBy == SortOrder.MOD) {
            c = ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == SortOrder.AMOUNT) {
            c = ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else if (sortBy == SortOrder.INVTWEAKS) {
            if (InventoryBogoSortModule.isLoaded()) {
                c = InventoryBogoSortModule.COMPARATOR;
            } else {
                c = ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
            }
        } else {
            c = ItemSorters.CONFIG_BASED_SORT_BY_NAME;
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private void addIAE(IAEStack<?> is, Enum viewMode) {

        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;

        // ViewCell 过滤只对物品类型生效
        if (is instanceof IAEItemStack itemStack) {
            if (this.myPartitionList != null && !this.myPartitionList.isListed(itemStack)) {
                return;
            }
        }

        if (viewMode == ViewItems.CRAFTABLE && !is.isCraftable()) {
            return;
        }
        if (viewMode == ViewItems.STORED && is.getStackSize() == 0) {
            return;
        }

        final String query = lower(this.searchString).trim();
        if (query.isEmpty()) {
            if (needsZeroCopy) {
                IAEStack<?> copy = is.copy();
                copy.setStackSize(0);
                this.view.add(copy);
            } else {
                this.view.add(is);
            }
            return;
        }

        // 对于非物品类型，仅按显示名称搜索
        if (!(is instanceof IAEItemStack)) {
            String displayName = lower(is.asItemStackRepresentation().getDisplayName());
            boolean found = matchSimpleSearch(displayName, query);
            if (found) {
                if (needsZeroCopy) {
                    IAEStack<?> copy = is.copy();
                    copy.setStackSize(0);
                    this.view.add(copy);
                } else {
                    this.view.add(is);
                }
            }
            return;
        }

        IAEItemStack itemStack = (IAEItemStack) is;

        // Original setting behavior:
        // enabled = normal terms also search tooltip
        // disabled = only # searches tooltip
        final boolean tooltipSearchEnabled =
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        // Base strings (null-safe)
        final String itemName = lower(Platform.getItemDisplayName(itemStack));
        String modId = null;
        String modName = null;

        // Lazy stuff only computed if a term needs it
        ItemStack stack = null;
        String registryId = null;

        // Two tooltip caches:
        // tooltipLower: normal lowercase tooltip (keeps spaces), used for "old setting" behavior
        // tooltipText: normalized tooltip (spaces removed), used for explicit # searching like modern
        String tooltipLower = null;
        String tooltipText = null;

        int[] oreIds = null;

        boolean found = false;

        // OR groups split by |
        for (String orPart : query.split("\\|")) {
            String part = orPart.trim();

            // Empty OR part matches everything
            if (part.isEmpty()) {
                found = true;
                break;
            }

            boolean groupMatches = true;

            // AND terms split by spaces
            for (String raw : splitSearchTerms(part)) {
                if (raw.isEmpty()) {
                    continue;
                }

                boolean neg = false;
                char c0 = raw.charAt(0);
                if (c0 == '-' || c0 == '!') {
                    neg = true;
                    raw = raw.substring(1);
                    if (raw.isEmpty()) {
                        continue;
                    }
                }

                char prefix = raw.charAt(0);
                String term = raw;

                enum Target { NAME, MOD, TOOLTIP, OREDICT, REGISTRY }
                Target target = Target.NAME;

                if (prefix == '@' || prefix == '#' || prefix == '$' || prefix == '&' || prefix == '*') {
                    term = raw.substring(1);
                    if (term.isEmpty()) {
                        continue;
                    }

                    if (prefix == '@') target = Target.MOD;
                    else if (prefix == '#') target = Target.TOOLTIP;
                    else if (prefix == '$') target = Target.OREDICT;
                    else target = Target.REGISTRY; // & or *
                }

                boolean termMatches = false;

                switch (target) {
                    case NAME:
                        termMatches = itemName.contains(term);

                        if (!termMatches && tooltipSearchEnabled) {
                            if (tooltipLower == null) {
                                List<String> lines = Platform.getTooltip(itemStack);
                                StringBuilder sb = new StringBuilder();
                                for (int i = 0; i < lines.size(); i++) {
                                    String line = lines.get(i);
                                    if (line == null) continue;
                                    if (sb.length() > 0) sb.append('\n');
                                    sb.append(line);
                                }

                                String joined = sb.toString();
                                tooltipLower = lower(joined);
                                tooltipText = normalizeTooltip(joined);
                            }

                            termMatches = tooltipLower.contains(term);
                        }
                        break;

                    case MOD:
                        if (modId == null) {
                            modId = lower(Platform.getModId(itemStack));
                        }

                        if (modId.contains(term)) {
                            termMatches = true;
                            break;
                        }

                        if (modName == null) {
                            modName = getModNameSafe(modId);
                        }
                        termMatches = modName.contains(term);
                        break;

                    case TOOLTIP:
                        if (tooltipText == null) {
                            List<String> lines = Platform.getTooltip(itemStack);
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < lines.size(); i++) {
                                String line = lines.get(i);
                                if (line == null) continue;
                                if (sb.length() > 0) sb.append('\n');
                                sb.append(line);
                            }
                            String joined = sb.toString();
                            tooltipLower = lower(joined);
                            tooltipText = normalizeTooltip(joined);
                        }
                        termMatches = tooltipText.contains(normalizeTooltip(term));
                        break;

                    case OREDICT:
                        if (stack == null) {
                            stack = safeItemStack(itemStack);
                        }
                        if (!stack.isEmpty()) {
                            if (oreIds == null) {
                                oreIds = OreDictionary.getOreIDs(stack);
                                if (oreIds == null) oreIds = new int[0];
                            }
                            for (int id : oreIds) {
                                String oreName = OreDictionary.getOreName(id);
                                if (oreName != null && lower(oreName).contains(term)) {
                                    termMatches = true;
                                    break;
                                }
                            }
                        }
                        break;

                    case REGISTRY:
                        if (stack == null) {
                            stack = safeItemStack(itemStack);
                        }
                        if (!stack.isEmpty()) {
                            if (registryId == null) {
                                ResourceLocation rl = stack.getItem() == null ? null : stack.getItem().getRegistryName();
                                registryId = lower(rl == null ? "" : rl.toString());
                            }
                            termMatches = registryId.contains(term);
                        }
                        break;
                }

                boolean passes = neg ? !termMatches : termMatches;
                if (!passes) {
                    groupMatches = false;
                    break;
                }
            }

            if (groupMatches) {
                found = true;
                break;
            }
        }

        if (found) {
            if (needsZeroCopy) {
                itemStack = itemStack.copy();
                itemStack.setStackSize(0);
            }
            this.view.add(itemStack);
        }
    }



    private void updateJEI(String filter) {
        Integrations.jei().setSearchText(filter);
    }

    public int size() {
        return this.view.size();
    }

    public void clear() {
        for (IItemList<?> list : this.lists.values()) {
            list.resetStatus();
        }
    }

    public boolean hasPower() {
        return this.hasPower;
    }

    public void setPower(final boolean hasPower) {
        this.hasPower = hasPower;
    }

    public int getRowSize() {
        return this.rowSize;
    }

    public void setRowSize(final int rowSize) {
        this.rowSize = rowSize;
    }

    public String getSearchString() {
        return this.searchString;
    }

    public void setSearchString(@Nonnull final String searchString) {
        this.searchString = searchString;
    }

    @SuppressWarnings("unchecked")
    public IItemList<IAEItemStack> getList() {
        IAEStackType<?> itemType = appeng.api.storage.data.AEStackTypeRegistry.getType("item");
        if (itemType != null) {
            IItemList<?> list = this.lists.get(itemType);
            if (list != null) {
                return (IItemList<IAEItemStack>) list;
            }
        }
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
    }

    /**
     * 设置某种类型在终端中是否启用显示。
     */
    public void setTypeFilter(IAEStackType<?> type, boolean enabled) {
        this.typeFilters.put(type, enabled);
        this.resort = true;
    }

    /**
     * @return 某种类型在终端中是否启用显示
     */
    public boolean isTypeEnabled(IAEStackType<?> type) {
        return this.typeFilters.getOrDefault(type, true);
    }

    /**
     * 简单搜索：支持 OR (|) 和 NOT (-) 逻辑，用于非物品类型。
     */
    private static boolean matchSimpleSearch(String displayName, String query) {
        final String[] orGroups = query.split("\\|");
        for (String group : orGroups) {
            final String trimmed = group.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("-")) {
                if (!displayName.contains(trimmed.substring(1))) {
                    return true;
                }
            } else {
                if (displayName.contains(trimmed)) {
                    return true;
                }
            }
        }
        return false;
    }


    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTooltip(String s) {
        return lower(s).replace(" ", "");
    }

    private static ItemStack safeItemStack(IAEItemStack ae) {
        try {
            ItemStack s = ae.createItemStack();
            return s == null ? ItemStack.EMPTY : s;
        } catch (Throwable t) {
            try {
                ItemStack s = ae.getDefinition();
                return s == null ? ItemStack.EMPTY : s;
            } catch (Throwable t2) {
                return ItemStack.EMPTY;
            }
        }
    }

    private static String getModNameSafe(String modId) {
        if (modId == null || modId.isEmpty()) {
            return "";
        }

        try {
            ModContainer c = Loader.instance().getIndexedModList().get(modId);
            if (c != null && c.getName() != null) {
                return lower(c.getName());
            }
        } catch (Throwable ignored) {
        }

        return "";
    }

    private static List<String> splitSearchTerms(String input) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }

            if (!inQuotes && Character.isWhitespace(ch)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }

            cur.append(ch);
        }

        if (cur.length() > 0) {
            out.add(cur.toString());
        }

        return out;
    }
}


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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class ItemRepo {

    private final IItemList<IAEItemStack> list = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
    private List<IAEItemStack> view = new ArrayList<>();
    private final IScrollSource src;
    private final ISortSource sortSrc;

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

    public IAEItemStack getReferenceItem(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    void setSearch(final String search) {
        this.searchString = search == null ? "" : search;
    }

    public void postUpdate(final IAEItemStack is) {
        final IAEItemStack st = this.list.findPrecise(is);

        if (st != null) {
            st.reset();
            st.add(is);
        } else {
            this.list.add(is);
        }

        changed = true;
    }

    public long getItemCount(final IAEItemStack is) {
        IAEItemStack st = this.list.findPrecise(is);
        return st == null ? 0 : st.getStackSize();
    }

    public void setViewCell(final ItemStack[] list) {
        this.myPartitionList = ItemViewCell.createFilter(list);
        this.changed = true;
    }

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

            Comparator<IAEItemStack> c = getComparator(sortBy);

            for (IAEItemStack is : this.list) {
                addIAE(is, viewMode);
            }

            view.sort(c);
        }
    }

    private static Comparator<IAEItemStack> getComparator(Enum sortBy) {
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

    private void addIAE(IAEItemStack is, Enum viewMode) {

        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;

        if (this.myPartitionList != null && !this.myPartitionList.isListed(is)) {
            return;
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
                IAEItemStack copy = is.copy();
                copy.setStackSize(0);
                this.view.add(copy);
            } else {
                this.view.add(is);
            }
            return;
        }

        // Original setting behavior:
        // enabled = normal terms also search tooltip
        // disabled = only # searches tooltip
        final boolean tooltipSearchEnabled =
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        // Base strings (null-safe)
        final String itemName = lower(Platform.getItemDisplayName(is));
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
                                List<String> lines = Platform.getTooltip(is);
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
                            modId = lower(Platform.getModId(is));
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
                            List<String> lines = Platform.getTooltip(is);
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
                            stack = safeItemStack(is);
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
                            stack = safeItemStack(is);
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
                is = is.copy();
                is.setStackSize(0);
            }
            this.view.add(is);
        }
    }



    private void updateJEI(String filter) {
        Integrations.jei().setSearchText(filter);
    }

    public int size() {
        return this.view.size();
    }

    public void clear() {
        this.list.resetStatus();
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

    public IItemList<IAEItemStack> getList() {
        return list;
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

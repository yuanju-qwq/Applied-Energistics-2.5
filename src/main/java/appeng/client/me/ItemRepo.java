
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


import appeng.api.config.*;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.widgets.IScrollSource;
import appeng.client.gui.widgets.ISortSource;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.items.storage.ItemViewCell;
import appeng.util.ItemSorters;
import appeng.util.Platform;
import appeng.util.prioritylist.IPartitionList;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

/**
 * Client-side item repository for ME terminal display.
 * <p>
 * Stores all resource entries (items, fluids, etc.) using the AEKey system
 * ({@link KeyCounter} + craftable set), applies search/filter/sort to produce
 * a flat view of {@link RepoEntry} for rendering.
 * <p>
 * Legacy {@link IAEStack}-based input is supported via deprecated bridge methods.
 */
public class ItemRepo {

    // ==================== RepoEntry: self-contained view entry ====================

    /**
     * A self-contained view entry for rendering in the terminal grid.
     * <p>
     * Combines an {@link AEKey} identity, a quantity, and a craftable flag into a single
     * immutable record. This replaces the old approach of passing mutable {@link IAEStack}
     * objects through the view pipeline.
     *
     * @param what      the resource identity (item, fluid, etc.)
     * @param amount    the stored quantity (0 if only craftable)
     * @param craftable whether this resource can be auto-crafted
     */
    public record RepoEntry(AEKey what, long amount, boolean craftable) {

        public RepoEntry {
            Objects.requireNonNull(what, "what");
        }

        /**
         * @return a GenericStack projection of this entry (without craftable info)
         */
        public GenericStack toGenericStack() {
            return new GenericStack(what, amount);
        }

        /**
         * Bridge: create a RepoEntry from a legacy IAEStack.
         *
         * @return the converted entry, or null if input is null or conversion fails
         */
        @Nullable
        public static RepoEntry fromIAEStack(@Nullable IAEStack<?> stack) {
            if (stack == null) {
                return null;
            }
            var key = stack.toAEKey();
            if (key == null) {
                return null;
            }
            return new RepoEntry(key, stack.getStackSize(), stack.isCraftable());
        }

        /**
         * Bridge: convert this entry to a legacy IAEStack.
         *
         * @return the legacy stack, or null if conversion fails
         */
        @Nullable
        public IAEStack<?> toIAEStack() {
            IAEStack<?> stack = what.toIAEStack(amount);
            if (stack != null) {
                stack.setCraftable(craftable);
            }
            return stack;
        }
    }

    // ==================== Internal storage (AEKey-based) ====================

    /**
     * Primary counter: maps AEKey -> amount for all resource types.
     */
    private final KeyCounter counter = new KeyCounter();

    /**
     * Craftable flag set: keys present here are craftable.
     */
    private final Set<AEKey> craftableKeys = new HashSet<>();

    /**
     * Sorted/filtered view list produced by {@link #updateView()}.
     */
    private List<RepoEntry> view = new ArrayList<>();

    private final IScrollSource src;
    private final ISortSource sortSrc;

    /**
     * Type filter: whether a given AEKeyType is enabled for display.
     */
    private final Map<AEKeyType, Boolean> typeFilters = new IdentityHashMap<>();

    private int rowSize = 9;

    private String searchString = "";
    @Nullable
    private Predicate<AEItemKey> myItemFilter;
    private boolean hasPower;

    private Enum<?> lastView;
    private Enum<?> lastSearchMode;
    private Enum<?> lastSortBy;
    private Enum<?> lastSortDir;
    private String lastSearch = "";

    private boolean resort = true;
    private boolean changed = false;


    public ItemRepo(final IScrollSource src, final ISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    // ==================== View access ====================

    /**
     * Get the RepoEntry at the given view index (scroll-offset aware).
     *
     * @return the entry, or null if index is out of bounds
     */
    @Nullable
    public RepoEntry getEntry(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    /**
     * @deprecated Use {@link #getEntry(int)} instead. This method converts to legacy IAEStack.
     */
    @Deprecated
    @Nullable
    public IAEStack<?> getReferenceItem(int idx) {
        RepoEntry entry = getEntry(idx);
        return entry != null ? entry.toIAEStack() : null;
    }

    // ==================== Data mutation (AEKey-based primary API) ====================

    /**
     * Update a resource entry using AEKey-based input.
     *
     * @param key       the resource identity
     * @param amount    the new total amount
     * @param craftable whether this resource is craftable
     */
    public void postUpdate(AEKey key, long amount, boolean craftable) {
        this.counter.set(key, amount);
        if (craftable) {
            this.craftableKeys.add(key);
        } else {
            this.craftableKeys.remove(key);
        }
        this.changed = true;
    }

    /**
     * Update a resource entry using a GenericStack with craftable flag.
     */
    public void postUpdate(GenericStack stack, boolean craftable) {
        postUpdate(stack.what(), stack.amount(), craftable);
    }

    /**
     * Update a resource entry from a RepoEntry.
     */
    public void postUpdate(RepoEntry entry) {
        postUpdate(entry.what(), entry.amount(), entry.craftable());
    }

    /**
     * @deprecated Use {@link #postUpdate(AEKey, long, boolean)} instead.
     *             Legacy bridge: accepts IAEStack and converts to AEKey internally.
     */
    @Deprecated
    public void postUpdate(final IAEStack<?> is) {
        var key = is.toAEKey();
        if (key != null) {
            postUpdate(key, is.getStackSize(), is.isCraftable());
        }
    }

    // ==================== Query ====================

    /**
     * Get the stored amount for the given AEKey.
     */
    public long getAmount(AEKey key) {
        return this.counter.get(key);
    }

    /**
     * Check if a key is craftable.
     */
    public boolean isCraftable(AEKey key) {
        return this.craftableKeys.contains(key);
    }

    /**
     * @deprecated Use {@link #getAmount(AEKey)} instead. Legacy bridge.
     */
    @Deprecated
    public long getItemCount(final IAEItemStack is) {
        var key = AEItemKey.fromIAEItemStack(is);
        if (key == null) {
            return 0;
        }
        return this.counter.get(key);
    }

    // ==================== Configuration ====================

    public void setSearch(final String search) {
        this.searchString = search == null ? "" : search;
    }

    public void setViewCell(final ItemStack[] list) {
        IPartitionList<IAEItemStack> partitionList = ItemViewCell.createFilter(list);
        if (partitionList != null && !partitionList.isEmpty()) {
            this.myItemFilter = itemKey -> {
                IAEStack<?> stack = itemKey.toIAEStack(1);
                return stack instanceof IAEItemStack && partitionList.isListed((IAEItemStack) stack);
            };
        } else {
            this.myItemFilter = null;
        }
        this.changed = true;
    }

    // ==================== View update ====================

    public void updateView() {

        final Enum<?> viewMode = this.sortSrc.getSortDisplay();

        if (lastView != viewMode) {
            resort = true;
            lastView = viewMode;
        }

        final Enum<?> searchMode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
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

        final Enum<?> sortBy = this.sortSrc.getSortBy();
        final Enum<?> sortDir = this.sortSrc.getSortDir();

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

            // Iterate all entries in the KeyCounter
            for (var entry : this.counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                boolean craftable = this.craftableKeys.contains(key);

                // Type filter check
                AEKeyType keyType = key.getType();
                if (!this.typeFilters.getOrDefault(keyType, true)) {
                    continue;
                }

                addEntry(key, amount, craftable, viewMode);
            }

            // Sort the view
            Comparator<RepoEntry> c = getRepoEntryComparator(sortBy);
            view.sort(c);
        }
    }

    // ==================== Sorting ====================

    /**
     * Build a comparator for RepoEntry based on the current sort settings.
     */
    private static Comparator<RepoEntry> getRepoEntryComparator(final Enum<?> sortBy) {
        return (a, b) -> {
            // Different types: items before fluids (alphabetical by type id)
            if (a.what().getType() != b.what().getType()) {
                return a.what().getType().getId().compareTo(b.what().getType().getId());
            }

            // Same type: use type-specific sorting
            if (a.what() instanceof AEItemKey && b.what() instanceof AEItemKey) {
                return getItemKeyComparator(sortBy).compare(a, b);
            }

            // Fluids or other types: sort by name -> amount
            String nameA = a.what().getDisplayName();
            String nameB = b.what().getDisplayName();
            int cmp = nameA.compareToIgnoreCase(nameB);
            if (cmp != 0) return cmp;
            return Long.compare(b.amount(), a.amount());
        };
    }

    /**
     * Item-specific comparator that delegates to the existing ItemSorters infrastructure.
     */
    private static Comparator<RepoEntry> getItemKeyComparator(final Enum<?> sortBy) {
        if (sortBy == SortOrder.MOD) {
            return (a, b) -> {
                int cmp = a.what().getModId().compareToIgnoreCase(b.what().getModId());
                if (cmp == 0) {
                    cmp = a.what().getDisplayName().compareToIgnoreCase(b.what().getDisplayName());
                }
                return ItemSorters.applyCurrentDirection(cmp);
            };
        } else if (sortBy == SortOrder.AMOUNT) {
            return (a, b) -> {
                int cmp = Long.compare(b.amount(), a.amount());
                return ItemSorters.applyCurrentDirection(cmp);
            };
        } else if (sortBy == SortOrder.INVTWEAKS) {
            if (InventoryBogoSortModule.isLoaded()) {
                return (a, b) -> {
                    return InventoryBogoSortModule.COMPARATOR.compare(
                            toAEItemStack(a), toAEItemStack(b));
                };
            } else {
                return (a, b) -> {
                    return ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS.compare(
                            toAEItemStack(a), toAEItemStack(b));
                };
            }
        } else {
            // Default: sort by name
            return (a, b) -> {
                int cmp = a.what().getDisplayName().compareToIgnoreCase(b.what().getDisplayName());
                return ItemSorters.applyCurrentDirection(cmp);
            };
        }
    }

    @Nullable
    private static AEItemStack toAEItemStack(RepoEntry entry) {
        IAEStack<?> stack = ((AEItemKey) entry.what()).toIAEStack(1);
        return stack instanceof AEItemStack ? (AEItemStack) stack : null;
    }

    // ==================== Filter & search ====================

    private void addEntry(AEKey key, long amount, boolean craftable, final Enum<?> viewMode) {

        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;

        // ViewCell filter: only applies to item keys
        if (key instanceof AEItemKey itemKey) {
            if (this.myItemFilter != null && !this.myItemFilter.test(itemKey)) {
                return;
            }
        }

        // View mode filter
        if (viewMode == ViewItems.CRAFTABLE && !craftable) {
            return;
        }
        if (viewMode == ViewItems.STORED && amount == 0) {
            return;
        }

        final String query = lower(this.searchString).trim();
        if (query.isEmpty()) {
            if (needsZeroCopy) {
                this.view.add(new RepoEntry(key, 0, craftable));
            } else {
                this.view.add(new RepoEntry(key, amount, craftable));
            }
            return;
        }

        // For non-item types, simple display name search
        if (!(key instanceof AEItemKey)) {
            String displayName = lower(key.getDisplayName());
            boolean found = matchSimpleSearch(displayName, query);
            if (found) {
                if (needsZeroCopy) {
                    this.view.add(new RepoEntry(key, 0, craftable));
                } else {
                    this.view.add(new RepoEntry(key, amount, craftable));
                }
            }
            return;
        }

        // Item-specific advanced search
        AEItemKey itemKey = (AEItemKey) key;

        // Original setting behavior:
        // enabled = normal terms also search tooltip
        // disabled = only # searches tooltip
        final boolean tooltipSearchEnabled =
                AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        // Base strings (null-safe)
        final String itemName = lower(itemKey.getDisplayName());
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
                                if (stack == null) {
                                    stack = safeItemStack(itemKey);
                                }
                                List<String> lines = Platform.getTooltip(stack);
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
                            modId = lower(itemKey.getModId());
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
                            if (stack == null) {
                                stack = safeItemStack(itemKey);
                            }
                            List<String> lines = Platform.getTooltip(stack);
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
                            stack = safeItemStack(itemKey);
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
                            stack = safeItemStack(itemKey);
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
                this.view.add(new RepoEntry(key, 0, craftable));
            } else {
                this.view.add(new RepoEntry(key, amount, craftable));
            }
        }
    }

    // ==================== JEI sync ====================

    private void updateJEI(String filter) {
        Integrations.jei().setSearchText(filter);
    }

    // ==================== Size / state ====================

    public int size() {
        return this.view.size();
    }

    public void clear() {
        this.counter.reset();
        this.craftableKeys.clear();
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

    // ==================== KeyCounter access ====================

    /**
     * @return the internal KeyCounter (read-only view recommended)
     */
    public KeyCounter getKeyCounter() {
        return this.counter;
    }

    // ==================== Type filter (AEKeyType-based) ====================

    /**
     * Set whether a given AEKeyType is enabled for display.
     */
    public void setTypeFilter(AEKeyType type, boolean enabled) {
        this.typeFilters.put(type, enabled);
        this.resort = true;
    }

    /**
     * @return whether a given AEKeyType is enabled for display
     */
    public boolean isTypeEnabled(AEKeyType type) {
        return this.typeFilters.getOrDefault(type, true);
    }

    /**
     * @deprecated Use {@link #setTypeFilter(AEKeyType, boolean)} instead.
     *             Legacy bridge: accepts IAEStackType and converts to AEKeyType.
     */
    @Deprecated
    public void setTypeFilter(IAEStackType<?> type, boolean enabled) {
        AEKeyType keyType = AEKeyType.fromLegacyType(type);
        if (keyType != null) {
            setTypeFilter(keyType, enabled);
        }
    }

    /**
     * @deprecated Use {@link #isTypeEnabled(AEKeyType)} instead.
     */
    @Deprecated
    public boolean isTypeEnabled(IAEStackType<?> type) {
        AEKeyType keyType = AEKeyType.fromLegacyType(type);
        return keyType != null && isTypeEnabled(keyType);
    }

    // ==================== Simple search for non-item types ====================

    /**
     * Simple search: supports OR (|) and NOT (-) logic for non-item types.
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

    // ==================== String utilities ====================

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTooltip(String s) {
        return lower(s).replace(" ", "");
    }

    private static ItemStack safeItemStack(AEItemKey itemKey) {
        try {
            return itemKey.toStack();
        } catch (Throwable t) {
            return ItemStack.EMPTY;
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

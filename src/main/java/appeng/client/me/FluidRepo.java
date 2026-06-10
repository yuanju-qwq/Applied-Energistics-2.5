/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;

import appeng.api.config.Settings;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.config.YesNo;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEFluidStack;
import appeng.client.mui.widgets.IMUIScrollSource;
import appeng.client.mui.widgets.IMUISortSource;
import appeng.core.AEConfig;
import appeng.fluids.util.FluidSorters;
import appeng.util.Platform;
import appeng.util.prioritylist.AEKeyPartitionList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class FluidRepo {
    private final KeyCounter list = new KeyCounter();
    private final ArrayList<IAEFluidStack> view = new ArrayList<>();
    private final IMUIScrollSource src;
    private final IMUISortSource sortSrc;

    private int rowSize = 9;

    private String searchString = "";
    private AEKeyPartitionList myPartitionList;
    private boolean hasPower;

    public FluidRepo(final IMUIScrollSource src, final IMUISortSource sortSrc) {
        this.src = src;
        this.sortSrc = sortSrc;
    }

    public void updateView() {
        this.view.clear();

        this.view.ensureCapacity(this.list.size());

        String innerSearch = this.searchString;

        boolean searchMod = false;
        if (innerSearch.startsWith("@")) {
            searchMod = true;
            innerSearch = innerSearch.substring(1);
        }

        Pattern m;
        try {
            m = Pattern.compile(innerSearch.toLowerCase(), Pattern.CASE_INSENSITIVE);
        } catch (final Exception ignore1) {
            try {
                m = Pattern.compile(Pattern.quote(innerSearch.toLowerCase()), Pattern.CASE_INSENSITIVE);
            } catch (final Exception ignore2) {
                return;
            }
        }

        final Enum viewMode = this.sortSrc.getSortDisplay();
        final boolean needsZeroCopy = viewMode == ViewItems.CRAFTABLE;
        final boolean terminalSearchToolTips = AEConfig.instance().getConfigManager()
                .getSetting(Settings.SEARCH_TOOLTIPS) != YesNo.NO;

        boolean notDone = false;
        for (Object2LongMap.Entry<AEKey> entry : this.list) {
            final AEKey key = entry.getKey();
            final long amount = entry.getLongValue();
            IAEFluidStack fs = (IAEFluidStack) key.toIAEStack(amount);

            if (this.myPartitionList != null && !this.myPartitionList.isListed(key)) {
                continue;
            }

            if (viewMode == ViewItems.CRAFTABLE && !fs.isCraftable()) {
                continue;
            }

            if (viewMode == ViewItems.STORED && amount == 0) {
                continue;
            }

            final String dspName = searchMod ? Platform.getModId(fs) : Platform.getFluidDisplayName(fs);
            boolean foundMatchingFluidStack = false;
            notDone = true;

            if (m.matcher(dspName.toLowerCase()).find()) {
                notDone = false;
                foundMatchingFluidStack = true;
            }

            if (terminalSearchToolTips && notDone && !searchMod) {
                final List<String> tooltip = Platform.getTooltip(fs);

                for (final String line : tooltip) {
                    if (m.matcher(line).find()) {
                        foundMatchingFluidStack = true;
                        break;
                    }
                }
            }

            if (foundMatchingFluidStack) {
                if (needsZeroCopy) {
                    fs = fs.copy();
                    fs.setStackSize(0);
                }

                this.view.add(fs);
            }
        }

        final Enum sortBy = this.sortSrc.getSortBy();
        final Enum sortDir = this.sortSrc.getSortDir();

        FluidSorters.setDirection((appeng.api.config.SortDir) sortDir);

        if (sortBy == SortOrder.MOD) {
            Collections.sort(this.view, FluidSorters.CONFIG_BASED_SORT_BY_MOD);
        } else if (sortBy == SortOrder.AMOUNT) {
            Collections.sort(this.view, FluidSorters.CONFIG_BASED_SORT_BY_SIZE);
        } else {
            Collections.sort(this.view, FluidSorters.CONFIG_BASED_SORT_BY_NAME);
        }
    }

    public void postUpdate(final IAEFluidStack is) {
        final AEKey key = is.toAEKey();
        if (key != null) {
            this.list.set(key, is.getStackSize());
        }
    }

    public IAEFluidStack getReferenceFluid(int idx) {
        idx += this.src.getCurrentScroll() * this.rowSize;

        if (idx >= this.view.size()) {
            return null;
        }
        return this.view.get(idx);
    }

    public int size() {
        return this.view.size();
    }

    public void clear() {
        this.list.reset();
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
}

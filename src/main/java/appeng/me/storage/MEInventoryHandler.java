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

package appeng.me.storage;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.util.prioritylist.DefaultPriorityList;
import appeng.util.prioritylist.IPartitionList;

@SuppressWarnings("rawtypes")
public class MEInventoryHandler implements IMEInventoryHandler {

    private final IMEInventoryHandler internal;
    private int myPriority;
    private IncludeExclude myWhitelist;
    private AccessRestriction myAccess;
    private StorageFilter storageFilter;
    private IPartitionList myPartitionList;

    private AccessRestriction cachedAccessRestriction;

    protected final boolean hasReadAccess() {
        return hasReadAccess;
    }

    protected final boolean hasWriteAccess() {
        return hasWriteAccess;
    }

    private boolean hasReadAccess;
    private boolean hasWriteAccess;
    private boolean isSticky;
    private boolean gettingAvailableContent;

    public MEInventoryHandler(final IMEInventory i, final AEKeyType type) {
        if (i instanceof IMEInventoryHandler) {
            this.internal = (IMEInventoryHandler) i;
        } else {
            this.internal = new MEPassThrough(i, type);
        }

        this.myPriority = 0;
        this.myWhitelist = IncludeExclude.WHITELIST;
        this.setBaseAccess(AccessRestriction.READ_WRITE);
        this.myPartitionList = new DefaultPriorityList();
    }

    IncludeExclude getWhitelist() {
        return this.myWhitelist;
    }

    public void setWhitelist(final IncludeExclude myWhitelist) {
        this.myWhitelist = myWhitelist;
    }

    public AccessRestriction getBaseAccess() {
        return this.myAccess;
    }

    public void setBaseAccess(final AccessRestriction myAccess) {
        this.myAccess = myAccess;
        this.cachedAccessRestriction = this.myAccess.restrictPermissions(this.internal.getAccess());
        this.hasReadAccess = this.cachedAccessRestriction.hasPermission(AccessRestriction.READ);
        this.hasWriteAccess = this.cachedAccessRestriction.hasPermission(AccessRestriction.WRITE);
    }

    public IPartitionList getPartitionList() {
        return this.myPartitionList;
    }

    public void setPartitionList(final IPartitionList myPartitionList) {
        this.myPartitionList = myPartitionList;
    }

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable type, final IActionSource src) {
        if (input == null) return null;
        if (!this.hasWriteAccess) return input;
        if (!this.canAccept(input.what())) return input;
        return this.internal.injectItems(input, type, src);
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable type, final IActionSource src) {
        if (request == null) return null;
        if (!this.canExtract(request.what())) return null;
        return this.internal.extractItems(request, type, src);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        if (this.gettingAvailableContent || !this.hasReadAccess) {
            return out;
        }

        this.gettingAvailableContent = true;
        try {
            if (this.storageFilter == StorageFilter.EXTRACTABLE_ONLY) {
                var kc = this.internal.getAvailableKeyCounter();
                for (var entry : kc) {
                    if (this.shouldItemBeAvailable(entry.getKey())) {
                        out.add(entry.getKey(), entry.getLongValue());
                    }
                }
            } else {
                return this.internal.getAvailableKeyCounter();
            }
        } finally {
            this.gettingAvailableContent = false;
        }

        return out;
    }

    @Override
    public AEKeyType getKeyType() {
        return this.internal.getKeyType();
    }

    @Override
    public AccessRestriction getAccess() {
        return this.cachedAccessRestriction;
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        if (this.myWhitelist == IncludeExclude.WHITELIST) {
            return this.myPartitionList.isListed(input) || this.internal.isPrioritized(input);
        }
        return false;
    }

    @Override
    public boolean canAccept(final AEKey input) {
        if (!this.hasWriteAccess) {
            return false;
        }

        if (!this.passesBlackOrWhitelist(input)) {
            return false;
        }

        return this.internal.canAccept(input);
    }

    @Override
    public int getPriority() {
        return this.myPriority;
    }

    public void setPriority(final int myPriority) {
        this.myPriority = myPriority;
    }

    @Override
    public int getSlot() {
        return this.internal.getSlot();
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }

    public IMEInventory getInternal() {
        return this.internal;
    }

    @Override
    public boolean isSticky() {
        return isSticky;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }

    protected boolean canExtract(AEKey request) {
        return this.hasReadAccess && this.passesBlackOrWhitelist(request);
    }

    protected boolean shouldItemBeAvailable(AEKey request) {
        return this.hasReadAccess && this.passesBlackOrWhitelist(request);
    }

    public boolean passesBlackOrWhitelist(AEKey input) {
        if (this.myPartitionList.isEmpty()) {
            return true;
        }

        return switch (this.myWhitelist) {
            case WHITELIST -> this.myPartitionList.isListed(input);
            case BLACKLIST -> !this.myPartitionList.isListed(input);
        };
    }

    public StorageFilter getStorageFilter() {
        return storageFilter;
    }

    public void setStorageFilter(StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }
}

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

import java.util.*;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.me.cache.SecurityCache;

@SuppressWarnings("rawtypes")
public class NetworkInventoryHandler implements IMEInventoryHandler {

    private static final ThreadLocal<Deque> DEPTH_MOD = new ThreadLocal<>();
    private static final ThreadLocal<Deque> DEPTH_SIM = new ThreadLocal<>();
    private static final Comparator<Integer> PRIORITY_SORTER = (o1, o2) -> Integer.compare(o2, o1);

    private static int currentPass = 0;
    private final AEKeyType myKeyType;
    private final SecurityCache security;
    private final NavigableMap<Integer, List<IMEInventoryHandler>> craftingPriorityInventory;
    private final NavigableMap<Integer, List<IMEInventoryHandler>> priorityInventory;
    private final NavigableMap<Integer, List<IMEInventoryHandler>> stickyPriorityInventory;
    private int myPass = 0;

    public NetworkInventoryHandler(final AEKeyType type, final SecurityCache security) {
        this.myKeyType = type;
        this.security = security;
        this.priorityInventory = new TreeMap<>(PRIORITY_SORTER);
        this.stickyPriorityInventory = new TreeMap<>(PRIORITY_SORTER);
        this.craftingPriorityInventory = new TreeMap<>(PRIORITY_SORTER);
    }

    public void addNewStorage(final IMEInventoryHandler h) {
        final int priority = h.getPriority();

        final NavigableMap<Integer, List<IMEInventoryHandler>> list;
        if (h instanceof ICraftingGrid) {
            list = this.craftingPriorityInventory;
        } else if (h.isSticky()) {
            list = this.stickyPriorityInventory;
        } else {
            list = this.priorityInventory;
        }

        list.computeIfAbsent(priority, $ -> new ArrayList<>()).add(h);
    }

    @Override
    public GenericStack injectItems(GenericStack input, final Actionable type, final IActionSource src) {
        if (this.diveList(this, type)) {
            return input;
        }

        if (this.testPermission(src, SecurityPermissions.INJECT)) {
            this.surface(this, type);
            return input;
        }

        // First pass. Check if the crafting grid is awaiting the input.
        for (final List<IMEInventoryHandler> invList : this.craftingPriorityInventory.values()) {
            Iterator<IMEInventoryHandler> ii = invList.iterator();
            while (ii.hasNext() && input != null) {
                final IMEInventoryHandler inv = ii.next();
                AEKey key = input.what();

                if (inv.canAccept(key)
                        && (inv.isPrioritized(key) || inv.extractItems(new GenericStack(key, input.amount()), Actionable.SIMULATE, src) != null)) {
                    input = inv.injectItems(input, type, src);
                }
            }
        }

        if (input == null) {
            this.surface(this, type);
            return input;
        }

        boolean stickyInventoryFound = false;
        for (final List<IMEInventoryHandler> stickyInvList : this.stickyPriorityInventory.values()) {
            Iterator<IMEInventoryHandler> ii = stickyInvList.iterator();
            while (ii.hasNext() && input != null) {
                final IMEInventoryHandler inv = ii.next();
                AEKey key = input.what();
                if (inv.validForPass(1) && inv.canAccept(key)
                        && (inv.isPrioritized(key) || inv.extractItems(new GenericStack(key, input.amount()), Actionable.SIMULATE, src) != null)) {
                    input = inv.injectItems(input, type, src);
                    stickyInventoryFound = true;
                }
            }
        }

        if (stickyInventoryFound) {
            this.surface(this, type);
            return input;
        }

        for (final List<IMEInventoryHandler> invList : this.priorityInventory.values()) {
            Iterator<IMEInventoryHandler> ii = invList.iterator();
            while (ii.hasNext() && input != null) {
                final IMEInventoryHandler inv = ii.next();
                AEKey key = input.what();

                if (inv.validForPass(1) && inv
                        .canAccept(key)
                        && (inv.isPrioritized(key) || inv.extractItems(new GenericStack(key, input.amount()), Actionable.SIMULATE, src) != null)) {
                    input = inv.injectItems(input, type, src);
                }
            }

            ii = invList.iterator();
            while (ii.hasNext() && input != null) {
                final IMEInventoryHandler inv = ii.next();
                AEKey key = input.what();

                if (inv.validForPass(2) && inv.canAccept(key) && !inv.isPrioritized(key)) {
                    input = inv.injectItems(input, type, src);
                }
            }
        }

        this.surface(this, type);

        return input;
    }

    private boolean diveList(final NetworkInventoryHandler networkInventoryHandler, final Actionable type) {
        final Deque cDepth = this.getDepth(type);
        if (cDepth.contains(networkInventoryHandler)) {
            return true;
        }

        cDepth.push(this);
        return false;
    }

    private boolean testPermission(final IActionSource src, final SecurityPermissions permission) {
        if (src.player().isPresent()) {
            return !this.security.hasPermission(src.player().get(), permission);
        } else if (src.machine().isPresent()) {
            if (this.security.isAvailable()) {
                final IGridNode n = src.machine().get().getActionableNode();
                if (n == null) {
                    return true;
                }

                final IGrid gn = n.getGrid();
                if (gn != this.security.getGrid()) {

                    final ISecurityGrid sg = gn.getCache(ISecurityGrid.class);
                    final int playerID = sg.getOwner();

                    return !this.security.hasPermission(playerID, permission);
                }
            }
        }

        return false;
    }

    private void surface(final NetworkInventoryHandler networkInventoryHandler, final Actionable type) {
        if (this.getDepth(type).pop() != this) {
            throw new IllegalStateException("Invalid Access to Networked Storage API detected.");
        }
    }

    private Deque getDepth(final Actionable type) {
        final ThreadLocal<Deque> depth = type == Actionable.MODULATE ? DEPTH_MOD : DEPTH_SIM;

        Deque s = depth.get();

        if (s == null) {
            depth.set(s = new ArrayDeque<>());
        }

        return s;
    }

    @Override
    public GenericStack extractItems(GenericStack request, final Actionable mode, final IActionSource src) {
        if (this.diveList(this, mode)) {
            return null;
        }

        if (this.testPermission(src, SecurityPermissions.EXTRACT)) {
            this.surface(this, mode);
            return null;
        }

        long extracted = 0;
        final long req = request.amount();

        for (final List<IMEInventoryHandler> invList : this.priorityInventory.descendingMap().values()) {
            for (final IMEInventoryHandler inv : invList) {
                if (extracted >= req) break;
                long toExtract = req - extracted;
                var result = inv.extractItems(new GenericStack(request.what(), toExtract), mode, src);
                if (result != null) {
                    extracted += result.amount();
                }
            }
        }

        for (final List<IMEInventoryHandler> invList : this.stickyPriorityInventory.descendingMap().values()) {
            for (final IMEInventoryHandler inv : invList) {
                if (extracted >= req) break;
                long toExtract = req - extracted;
                var result = inv.extractItems(new GenericStack(request.what(), toExtract), mode, src);
                if (result != null) {
                    extracted += result.amount();
                }
            }
        }

        this.surface(this, mode);

        if (extracted <= 0) {
            return null;
        }
        return new GenericStack(request.what(), extracted);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        if (this.diveIteration(this, Actionable.SIMULATE)) {
            return out;
        }

        iterateKeyCounters(out, priorityInventory);
        iterateKeyCounters(out, stickyPriorityInventory);
        iterateKeyCounters(out, craftingPriorityInventory);

        this.surface(this, Actionable.SIMULATE);

        return out;
    }

    private void iterateKeyCounters(KeyCounter out, final NavigableMap<Integer, List<IMEInventoryHandler>> map) {
        for (final List<IMEInventoryHandler> i : map.values()) {
            for (final IMEInventoryHandler j : i) {
                out.addAll(j.getAvailableKeyCounter());
            }
        }
    }

    private boolean diveIteration(final NetworkInventoryHandler networkInventoryHandler, final Actionable type) {
        final Deque cDepth = this.getDepth(type);
        if (cDepth.isEmpty()) {
            currentPass++;
            this.myPass = currentPass;
        } else {
            if (currentPass == this.myPass) {
                return true;
            } else {
                this.myPass = currentPass;
            }
        }

        cDepth.push(this);
        return false;
    }

    @Override
    public AEKeyType getKeyType() {
        return this.myKeyType;
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        return false;
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return true;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(final int i) {
        return true;
    }
}

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

package appeng.me.cluster.implementations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import com.google.common.collect.ImmutableList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.crafting.CraftingWatcher;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.MBCalculator;
import appeng.me.helpers.MachineSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;

public final class CraftingCPUCluster implements IAECluster, ICraftingCPU {

    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private final List<TileCraftingTile> tiles = new ArrayList<>();
    private final List<TileCraftingTile> storage = new ArrayList<>();
    private final List<TileCraftingMonitorTile> status = new ArrayList<>();
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();
    private String myName = "";
    private boolean isDestroyed = false;
    private long availableStorage = 0;
    MachineSource machineSrc = null;
    private int accelerator = 0;

    public final CraftingCpuLogic craftingLogic = new CraftingCpuLogic(this);

    public CraftingCPUCluster(final BlockPos boundsMin, final BlockPos boundsMax) {
        this.boundsMin = boundsMin.toImmutable();
        this.boundsMax = boundsMax.toImmutable();
    }

    // ============================================================
    // ICraftingCPU
    // ============================================================

    @Override
    public boolean isPause() {
        return craftingLogic.isPause();
    }

    @Override
    public GenericStack getFinalMultiOutput() {
        return craftingLogic.getFinalOutput();
    }

    @Override
    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    @Override
    public boolean isBusy() {
        return craftingLogic.isBusy();
    }

    @Override
    public IActionSource getActionSource() {
        return this.machineSrc;
    }

    @Override
    public long getAvailableStorage() {
        return this.availableStorage;
    }

    @Override
    public int getCoProcessors() {
        return this.accelerator;
    }

    @Override
    public String getName() {
        return this.myName;
    }

    @Override
    public long getRemainingItemCount() {
        return craftingLogic.getRemainingItemCount();
    }

    @Override
    public long getStartItemCount() {
        return craftingLogic.getStartItemCount();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    // ============================================================
    // IAECluster
    // ============================================================

    @Override
    public BlockPos getBoundsMin() {
        return boundsMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return boundsMax;
    }

    @Override
    public void updateStatus(final boolean updateGrid) {
        for (final TileCraftingTile r : this.tiles) {
            r.updateMeta(true);
        }
    }

    @Override
    public void destroy() {
        if (this.isDestroyed) {
            return;
        }
        this.isDestroyed = true;

        boolean ownsModification = !MBCalculator.isModificationInProgress();
        if (ownsModification) {
            MBCalculator.setModificationInProgress(this);
        }
        try {
            boolean posted = false;

            for (final TileCraftingTile r : this.tiles) {
                final IGridNode n = r.getActionableNode();
                if (n != null && !posted) {
                    final IGrid g = n.getGrid();
                    if (g != null) {
                        g.postEvent(new MENetworkCraftingCpuChange(n));
                        posted = true;
                    }
                }
                r.updateStatus(null);
            }
        } finally {
            if (ownsModification) {
                MBCalculator.setModificationInProgress(null);
            }
        }
    }

    @Override
    public Iterator<TileCraftingTile> getTiles() {
        return this.tiles.iterator();
    }

    // ============================================================
    // Cluster management
    // ============================================================

    void addTile(final TileCraftingTile te) {
        if (this.machineSrc == null || te.isCoreBlock()) {
            this.machineSrc = new MachineSource(te);
        }

        te.setCoreBlock(false);
        te.saveChanges();
        this.tiles.add(0, te);

        if (te.isStorage()) {
            this.availableStorage += te.getStorageBytes();
            this.storage.add(te);
        } else if (te.isStatus()) {
            this.status.add((TileCraftingMonitorTile) te);
        } else if (te.isAccelerator()) {
            this.accelerator++;
        }
    }

    public ICraftingLink getLastCraftingLink() {
        return craftingLogic.getLastCraftingLink();
    }

    // ============================================================
    // Public API delegated to CraftingCpuLogic
    // ============================================================

    public boolean canAccept(final GenericStack input) {
        return craftingLogic.canAccept(input);
    }

    public GenericStack injectItems(final GenericStack input, final Actionable type, final IActionSource src) {
        return craftingLogic.injectItems(input, type, src);
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final IActionSource src) {
        GenericStack gs = input != null ? GenericStack.fromIAEStack(input) : null;
        GenericStack result = craftingLogic.injectItems(gs, type, src);
        if (result == null) return null;
        return result.toIAEStack();
    }

    public void cancel() {
        craftingLogic.cancel();
    }

    public void switchCrafting() {
        craftingLogic.switchCrafting();
    }

    public void trackCrafting() {
        craftingLogic.trackCrafting();
    }

    public void updateCraftingLogic(final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc) {
        craftingLogic.updateCraftingLogic(grid, eg, cc);
    }

    public ICraftingLink submitJob(final IGrid g, final ICraftingJob job, final IActionSource src,
            final ICraftingRequester requestingMachine) {
        return craftingLogic.submitJob(g, job, src, requestingMachine);
    }

    public void getGenericListOfItem(final IItemList<IAEStackBase> list, final CraftingItemList whichList) {
        craftingLogic.getGenericListOfItem(list, whichList);
    }

    public void getGenericListOfItem(final KeyCounter out, final CraftingItemList whichList) {
        craftingLogic.getGenericListOfItem(out, whichList);
    }

    public void addStorage(final GenericStack stack) {
        craftingLogic.addStorage(stack);
    }

    public void addEmitable(final GenericStack stack) {
        craftingLogic.addEmitable(stack);
    }

    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        craftingLogic.addCrafting(details, crafts);
    }

    public IAEStack<?> getItemStack(final IAEStack<?> what, final CraftingItemList storage2) {
        return craftingLogic.getItemStack(what, storage2);
    }

    // ============================================================
    // Infrastructure methods accessible from CraftingCpuLogic
    // ============================================================

    public void postChange(final GenericStack stack, final IActionSource src) {
        if (stack == null) return;
        postChange(stack.toIAEStack(), src);
    }

    @SuppressWarnings("unchecked")
    public void postChange(final IAEStack<?> diff, final IActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();

        if (i.hasNext()) {
            final ImmutableList<GenericStack> single = ImmutableList.of(
                    new GenericStack(diff.toAEKey(), diff.getStackSize()));

            while (i.hasNext()) {
                final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
                final IMEMonitorHandlerReceiver receiver = o.getKey();

                if (receiver.isValid(o.getValue())) {
                    receiver.postChange(null, single, src);
                } else {
                    i.remove();
                }
            }
        }
    }

    public void postCraftingStatusChange(final GenericStack stack) {
        if (stack == null) return;
        postCraftingStatusChange(stack.toIAEStack());
    }

    public void postCraftingStatusChange(final IAEStack<?> diff) {
        if (this.getGrid() == null) {
            return;
        }

        final CraftingGridCache sg = this.getGrid().getCache(ICraftingGrid.class);

        if (sg.getInterestManager().containsKey(diff)) {
            final Collection<CraftingWatcher> list = sg.getInterestManager().get(diff);

            if (!list.isEmpty()) {
                for (final CraftingWatcher iw : list) {
                    iw.getHost().onRequestChange(sg, diff);
                }
            }
        }
    }

    public void updateOutput(GenericStack output) {
        IAEStack<?> send = output != null ? output.toIAEStack() : null;
        if (output != null && output.amount() <= 0) {
            send = null;
        }
        for (final TileCraftingMonitorTile t : this.status) {
            t.setJob(send);
        }
    }

    public long getElapsedTime() {
        return craftingLogic.getElapsedTime();
    }

    public IMEInventory getInventory() {
        return craftingLogic.getInventory();
    }

    // ============================================================
    // NBT serialization (delegates to CraftingCpuLogic for job state)
    // ============================================================

    public void writeToNBT(final NBTTagCompound data) {
        craftingLogic.writeToNBT(data);
    }

    public void readFromNBT(final NBTTagCompound data) {
        craftingLogic.readFromNBT(data);
    }

    // ============================================================
    // Lifecycle
    // ============================================================

    void done() {
        final TileCraftingTile core = this.getCore();
        core.setCoreBlock(true);

        if (core.getPreviousState() != null) {
            this.readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }

        this.updateOutput(craftingLogic.getFinalOutput());
        this.updateName();
    }

    public void updateName() {
        this.myName = "";
        for (final TileCraftingTile te : this.tiles) {
            if (te.hasCustomInventoryName()) {
                if (this.myName.length() > 0) {
                    this.myName += ' ' + te.getCustomInventoryName();
                } else {
                    this.myName = te.getCustomInventoryName();
                }
            }
        }
    }

    public void breakCluster() {
        final TileCraftingTile t = this.getCore();
        if (t != null) {
            t.breakCluster();
        }
    }

    // ============================================================
    // Internal helpers
    // ============================================================

    private Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    public TileCraftingTile getCore() {
        if (this.machineSrc == null) {
            return null;
        }
        return (TileCraftingTile) this.machineSrc.machine().get();
    }

    public IGrid getGrid() {
        for (final TileCraftingTile r : this.tiles) {
            final IGridNode gn = r.getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    return r.getActionableNode().getGrid();
                }
            }
        }
        return null;
    }

    public World getWorld() {
        return this.getCore().getWorld();
    }

    public boolean isActive() {
        final TileCraftingTile core = this.getCore();
        if (core == null) {
            return false;
        }
        final IGridNode node = core.getActionableNode();
        if (node == null) {
            return false;
        }
        return node.isActive();
    }

    public int getAccelerator() {
        return this.accelerator;
    }
}

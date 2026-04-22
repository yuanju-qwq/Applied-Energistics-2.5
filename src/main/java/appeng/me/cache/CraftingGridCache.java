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

package appeng.me.cache;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.stream.StreamSupport;

import com.google.common.collect.*;

import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectRBTreeSet;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPostCacheConstruction;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.ICellProvider;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.CraftingWatcher;
import appeng.crafting.v2.CraftingJobV2;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.GenericInterestManager;
import appeng.tile.crafting.TileCraftingStorageTile;
import appeng.tile.crafting.TileCraftingTile;

public class CraftingGridCache
        implements ICraftingGrid, ICraftingProviderHelper, ICellProvider {

    private static final ExecutorService CRAFTING_POOL;
    private static final Comparator<ICraftingPatternDetails> COMPARATOR = (firstDetail,
            nextDetail) -> nextDetail.getPriority() - firstDetail.getPriority();

    static {
        final ThreadFactory factory = ar -> {
            final Thread crafting = new Thread(ar, "AE Crafting Calculator");
            crafting.setDaemon(true);
            return crafting;
        };

        CRAFTING_POOL = Executors.newCachedThreadPool(factory);
    }

    private final Map<IAEStackType<?>, CraftingInventoryHandler<?>> handlers = new HashMap<>();

    private final Set<CraftingCPUCluster> craftingCPUClusters = new HashSet<>();
    private final Set<ICraftingProvider> craftingProviders = new HashSet<>();
    private final Map<IGridNode, ICraftingWatcher> craftingWatchers = new HashMap<>();
    private final IGrid grid;
    private final Object2ObjectMap<ICraftingPatternDetails, List<ICraftingMedium>> craftingMethods = new Object2ObjectOpenHashMap<>();
    private final Map<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> craftableItems = new HashMap<>();
    private final Set<IAEStack<?>> emitableItems = new HashSet<>();
    private final Map<String, CraftingLinkNexus> craftingLinks = new HashMap<>();
    private final Multimap<IAEStack, CraftingWatcher> interests = HashMultimap.create();
    private final GenericInterestManager<CraftingWatcher> interestManager = new GenericInterestManager<>(
            this.interests);
    private IStorageGrid storageGrid;
    private IEnergyGrid energyGrid;
    int i;
    private boolean updateList = false;
    private boolean updatePatterns = false;

    public CraftingGridCache(final IGrid grid) {
        this.grid = grid;
    }

    @MENetworkEventSubscribe
    public void afterCacheConstruction(final MENetworkPostCacheConstruction cacheConstruction) {
        this.storageGrid = this.grid.getCache(IStorageGrid.class);
        this.energyGrid = this.grid.getCache(IEnergyGrid.class);

        this.storageGrid.registerCellProvider(this);
    }

    @Override
    public void onUpdateTick() {
        if (this.updateList) {
            this.updateList = false;
            this.updateCPUClusters();
        }

        if (updatePatterns) {
            this.recalculateCraftingPatterns();
            this.updatePatterns = false;
        }

        final Iterator<CraftingLinkNexus> craftingLinkIterator = this.craftingLinks.values().iterator();
        while (craftingLinkIterator.hasNext()) {
            if (craftingLinkIterator.next().isDead(this.grid, this)) {
                craftingLinkIterator.remove();
            }
        }

        for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
            if (!cpu.isPause())
                cpu.updateCraftingLogic(this.grid, this.energyGrid, this);
        }
    }

    @Override
    public void removeNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcher craftingWatcher = this.craftingWatchers.get(gridNode);
            if (craftingWatcher != null) {
                craftingWatcher.reset();
                this.craftingWatchers.remove(gridNode);
            }
        }

        if (machine instanceof ICraftingRequester) {
            for (final CraftingLinkNexus link : this.craftingLinks.values()) {
                if (link.isMachine(machine)) {
                    link.removeNode();
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.remove(machine);
            this.updatePatterns = true;
        }
    }

    @Override
    public void addNode(final IGridNode gridNode, final IGridHost machine) {
        if (machine instanceof ICraftingWatcherHost) {
            final ICraftingWatcherHost watcherHost = (ICraftingWatcherHost) machine;
            final CraftingWatcher watcher = new CraftingWatcher(this, watcherHost);
            this.craftingWatchers.put(gridNode, watcher);
            watcherHost.updateWatcher(watcher);
        }

        if (machine instanceof ICraftingRequester) {
            for (final ICraftingLink link : ((ICraftingRequester) machine).getRequestedJobs()) {
                if (link instanceof CraftingLink) {
                    this.addLink((CraftingLink) link);
                }
            }
        }

        if (machine instanceof TileCraftingTile) {
            this.updateList = true;
        }

        if (machine instanceof ICraftingProvider) {
            this.craftingProviders.add((ICraftingProvider) machine);
            this.updatePatterns = true;
        }
    }

    @Override
    public void onSplit(final IGridStorage destinationStorage) { // nothing!
    }

    @Override
    public void onJoin(final IGridStorage sourceStorage) {
        // nothing!
    }

    @Override
    public void populateGridStorage(final IGridStorage destinationStorage) {
        // nothing!
    }

    private void updatePatterns() {
        this.updatePatterns = true;
    }

    private void recalculateCraftingPatterns() {
        final Map<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> oldItems = new HashMap<>(
                this.craftableItems);
        final Set<IAEStack<?>> oldEmitableItems = new HashSet<>(this.emitableItems);

        // erase list.
        this.craftingMethods.clear();
        this.craftableItems.clear();
        this.emitableItems.clear();
        this.handlers.clear();

        // re-create list..
        for (final ICraftingProvider provider : this.craftingProviders) {
            provider.provideCrafting(this);
        }

        final Map<IAEStack<?>, Set<ICraftingPatternDetails>> tmpCraft = new HashMap<>();

        // new craftables!
        for (final ICraftingPatternDetails details : this.craftingMethods.keySet()) {
            for (IAEStack<?> out : details.getAEOutputs()) {
                if (out == null) {
                    continue;
                }
                out = out.copy();
                out.reset();
                out.setCraftable(true);

                Set<ICraftingPatternDetails> methods = tmpCraft.get(out);

                if (methods == null) {
                    tmpCraft.put(out, methods = new ObjectRBTreeSet<>(COMPARATOR));
                }

                methods.add(details);

                ensureHandlerForType(out.getStackType());
            }
        }

        // make them immutable
        for (final Entry<IAEStack<?>, Set<ICraftingPatternDetails>> e : tmpCraft.entrySet()) {
            this.craftableItems.put(e.getKey(), ImmutableList.copyOf(e.getValue()));
        }

        // 按类型分组 craftables 变更
        Map<IAEStackType<?>, List<IAEStack<?>>> craftablesChangedByType = new HashMap<>();

        for (Entry<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> ais : oldItems.entrySet()) {
            if (!this.craftableItems.containsKey(ais.getKey())) {
                var changedStack = ais.getKey().copy();
                changedStack.reset();
                changedStack.setCraftable(false);
                craftablesChangedByType.computeIfAbsent(changedStack.getStackType(), k -> new ArrayList<>())
                        .add(changedStack);
            }
        }

        for (Entry<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> ais : this.craftableItems.entrySet()) {
            if (!oldItems.containsKey(ais.getKey())) {
                var changedStack = ais.getKey().copy();
                changedStack.reset();
                changedStack.setCraftable(true);
                craftablesChangedByType.computeIfAbsent(changedStack.getStackType(), k -> new ArrayList<>())
                        .add(changedStack);
            }
        }

        for (final IAEStack<?> st : oldEmitableItems) {
            if (!emitableItems.contains(st)) {
                var changedStack = st.copy();
                changedStack.reset();
                changedStack.setCraftable(false);
                craftablesChangedByType.computeIfAbsent(changedStack.getStackType(), k -> new ArrayList<>())
                        .add(changedStack);
            }
        }

        for (final IAEStack<?> st : this.emitableItems) {
            if (!oldEmitableItems.contains(st)) {
                var changedStack = st.copy();
                changedStack.reset();
                changedStack.setCraftable(true);
                craftablesChangedByType.computeIfAbsent(changedStack.getStackType(), k -> new ArrayList<>())
                        .add(changedStack);
            }
        }

        // 按类型通知存储系统
        var src = new BaseActionSource();
        for (var entry : craftablesChangedByType.entrySet()) {
            this.storageGrid.postCraftablesChanges(entry.getKey(), entry.getValue(), src);
        }

        // 确保所有出现过的类型都有 handler
        for (var type : craftablesChangedByType.keySet()) {
            ensureHandlerForType(type);
        }
    }

    private void updateCPUClusters() {
        this.craftingCPUClusters.clear();

        for (Object cls : StreamSupport.stream(grid.getMachinesClasses().spliterator(), false)
                .filter(TileCraftingStorageTile.class::isAssignableFrom).toArray()) {
            for (final IGridNode cst : this.grid.getMachines((Class<? extends IGridHost>) cls)) {
                final TileCraftingStorageTile tile = (TileCraftingStorageTile) cst.getMachine();
                final CraftingCPUCluster cluster = (CraftingCPUCluster) tile.getCluster();
                if (cluster != null) {
                    this.craftingCPUClusters.add(cluster);

                    if (cluster.getLastCraftingLink() != null) {
                        this.addLink((CraftingLink) cluster.getLastCraftingLink());
                    }
                }
            }
        }

    }

    public void addLink(final CraftingLink link) {
        if (link.isStandalone()) {
            return;
        }

        CraftingLinkNexus nexus = this.craftingLinks.get(link.getCraftingID());
        if (nexus == null) {
            this.craftingLinks.put(link.getCraftingID(), nexus = new CraftingLinkNexus(link.getCraftingID()));
        }

        link.setNexus(nexus);
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingCpuChange c) {
        this.updateList = true;
    }

    @MENetworkEventSubscribe
    public void updateCPUClusters(final MENetworkCraftingPatternChange c) {
        this.updatePatterns();
    }

    @Override
    public void addCraftingOption(final ICraftingMedium medium, final ICraftingPatternDetails api) {
        List<ICraftingMedium> details = this.craftingMethods.get(api);
        if (details == null) {
            details = new ArrayList<>();
            details.add(medium);
            this.craftingMethods.put(api, details);
        } else {
            details.add(medium);
        }
    }

    @Override
    public void setEmitable(final IAEStack<?> someItem) {
        this.emitableItems.add(someItem.copy());
        this.ensureHandlerForType(someItem.getStackType());
    }

    private void ensureHandlerForType(IAEStackType<?> type) {
        this.handlers.computeIfAbsent(type,
                t -> new CraftingInventoryHandler<>(t, this));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends IAEStack<T>> List<IMEInventoryHandler<T>> getCellArray(final IAEStackType<T> type) {
        var handler = this.handlers.get(type);
        if (handler != null) {
            return Collections.singletonList((IMEInventoryHandler<T>) handler);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> CraftingInventoryHandler<T> getOrCreateHandler(IAEStackType<T> type) {
        return (CraftingInventoryHandler<T>) this.handlers.computeIfAbsent(type,
                t -> new CraftingInventoryHandler<>((IAEStackType<T>) t, this));
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Future<ICraftingJob> beginCraftingJob(final World world, final IGrid grid, final IActionSource actionSrc,
            final IAEStack<?> craftWhat, final ICraftingCallback cb) {
        if (world == null || grid == null || actionSrc == null || craftWhat == null) {
            throw new IllegalArgumentException("Invalid Crafting Job Request");
        }

        final CraftingJobV2 job = new CraftingJobV2(
                craftWhat, grid, actionSrc, cb, world, CraftingMode.STANDARD);
        return (Future) job.schedule();
    }

    @Override
    public ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns() {
        return ImmutableMap.copyOf(this.craftableItems);
    }

    @Override
    public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(final IAEStack<?> whatToCraft,
            final ICraftingPatternDetails details, final int slotIndex, final World world) {
        final ImmutableList<ICraftingPatternDetails> res = this.craftableItems.get(whatToCraft);
        return res == null ? ImmutableSet.of() : res;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ICraftingLink submitJob(final ICraftingJob job, final ICraftingRequester requestingMachine,
            final ICraftingCPU target, final boolean prioritizePower, final IActionSource src) {
        if (job.isSimulation()) {
            return null;
        }

        CraftingCPUCluster cpuCluster = null;

        if (target instanceof CraftingCPUCluster) {
            cpuCluster = (CraftingCPUCluster) target;
        }

        if (target == null) {
            final List<CraftingCPUCluster> validCpusClusters = new ArrayList<>();
            for (final CraftingCPUCluster cpu : this.craftingCPUClusters) {
                if (cpu.isActive() && !cpu.isBusy() && cpu.getAvailableStorage() >= job.getByteTotal()) {
                    validCpusClusters.add(cpu);
                }
            }

            Collections.sort(validCpusClusters, (firstCluster, nextCluster) -> {
                if (prioritizePower) {
                    final int comparison1 = Long.compare(nextCluster.getCoProcessors(), firstCluster.getCoProcessors());
                    if (comparison1 != 0) {
                        return comparison1;
                    }
                    return Long.compare(nextCluster.getAvailableStorage(), firstCluster.getAvailableStorage());
                }

                final int comparison2 = Long.compare(firstCluster.getCoProcessors(), nextCluster.getCoProcessors());
                if (comparison2 != 0) {
                    return comparison2;
                }
                return Long.compare(firstCluster.getAvailableStorage(), nextCluster.getAvailableStorage());
            });

            if (!validCpusClusters.isEmpty()) {
                cpuCluster = validCpusClusters.get(0);
            }
        }

        if (cpuCluster != null) {
            return cpuCluster.submitJob(this.grid, job, src, requestingMachine);
        }

        return null;
    }

    @Override
    public ImmutableSet<ICraftingCPU> getCpus() {
        return ImmutableSet.copyOf(new ActiveCpuIterator(this.craftingCPUClusters));
    }

    @Override
    public boolean canEmitFor(final IAEStack<?> someItem) {
        return this.emitableItems.contains(someItem);
    }

    @Override
    public boolean isRequesting(final IAEStack<?> what) {
        return this.requesting(what) > 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public long requesting(IAEStack<?> what) {
        long requested = 0;
        for (final CraftingCPUCluster cluster : this.craftingCPUClusters) {
            IAEStack<?> finalOut = cluster.getFinalMultiOutput();
            if (finalOut != null && finalOut.isSameType(what)) {
                requested += finalOut.getStackSize();
            }
        }
        return requested;
    }

    public List<ICraftingMedium> getMediums(final ICraftingPatternDetails key) {
        List<ICraftingMedium> mediums = this.craftingMethods.get(key);

        if (mediums == null) {
            mediums = ImmutableList.of();
        }

        return mediums;
    }

    public boolean hasCpu(final ICraftingCPU cpu) {
        if (cpu instanceof CraftingCPUCluster) {
            return this.craftingCPUClusters.contains((CraftingCPUCluster) cpu);
        }
        return false;
    }

    public GenericInterestManager<CraftingWatcher> getInterestManager() {
        return this.interestManager;
    }

    /**
     * 泛型合成库存处理器。
     * <p>
     * 每个 {@link IAEStackType} 对应一个实例，向网络暴露该类型的可合成物品列表，
     * 并将注入操作转发给合成 CPU。
     */
    private static class CraftingInventoryHandler<T extends IAEStack<T>> implements IMEInventoryHandler<T> {

        private final IAEStackType<T> type;
        private final CraftingGridCache cache;

        CraftingInventoryHandler(IAEStackType<?> type, CraftingGridCache cache) {
            @SuppressWarnings("unchecked")
            var t = (IAEStackType<T>) type;
            this.type = t;
            this.cache = cache;
        }

        @Override
        public AccessRestriction getAccess() {
            return AccessRestriction.WRITE;
        }

        @Override
        public boolean isPrioritized(T input) {
            return true;
        }

        @Override
        public boolean canAccept(T input) {
            for (final CraftingCPUCluster cpu : this.cache.craftingCPUClusters) {
                if (cpu.canAccept(input)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public int getPriority() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getSlot() {
            return 0;
        }

        @Override
        public boolean validForPass(int i) {
            return i == 1;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T injectItems(T input, Actionable type, IActionSource src) {
            for (final CraftingCPUCluster cpu : this.cache.craftingCPUClusters) {
                IAEStack<?> result = cpu.injectItems((IAEStack<?>) input, type, src);
                input = (T) result;
            }
            return input;
        }

        @Override
        public T extractItems(T request, Actionable mode, IActionSource src) {
            return null;
        }

        @Override
        public IItemList<T> getAvailableItems(IItemList<T> out) {
            for (final IAEStack<?> stack : this.cache.craftableItems.keySet()) {
                if (stack.getStackType() == this.type) {
                    out.addCraftingGeneric(stack);
                }
            }
            for (final IAEStack<?> st : this.cache.emitableItems) {
                if (st.getStackType() == this.type) {
                    out.addCraftingGeneric(st);
                }
            }
            return out;
        }

        @Override
        public IAEStackType<T> getStackType() {
            return this.type;
        }
    }

    private static class ActiveCpuIterator implements Iterator<ICraftingCPU> {

        private final Iterator<CraftingCPUCluster> iterator;
        private CraftingCPUCluster cpuCluster;

        public ActiveCpuIterator(final Collection<CraftingCPUCluster> o) {
            this.iterator = o.iterator();
            this.cpuCluster = null;
        }

        @Override
        public boolean hasNext() {
            this.findNext();

            return this.cpuCluster != null;
        }

        private void findNext() {
            while (this.iterator.hasNext() && this.cpuCluster == null) {
                this.cpuCluster = this.iterator.next();
                if (!this.cpuCluster.isActive() || this.cpuCluster.isDestroyed()) {
                    this.cpuCluster = null;
                }
            }
        }

        @Override
        public ICraftingCPU next() {
            final ICraftingCPU o = this.cpuCluster;
            this.cpuCluster = null;

            return o;
        }

        @Override
        public void remove() {
            // no..
        }
    }
}

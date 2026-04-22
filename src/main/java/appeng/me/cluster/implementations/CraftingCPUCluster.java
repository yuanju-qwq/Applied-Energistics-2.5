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

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.*;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.events.MENetworkCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AETrack;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingToast;
import appeng.crafting.*;
import appeng.crafting.v2.CraftingJobV2;
import appeng.fluids.items.ItemFluidDrop;
import appeng.helpers.PatternHelper;
import appeng.integration.modules.betterquesting.BQEventHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.MBCalculator;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.tile.crafting.TileCraftingMonitorTile;
import appeng.tile.crafting.TileCraftingTile;
import appeng.util.Platform;
import appeng.util.StorageHelper;
import appeng.util.item.AEItemStack;
import appeng.util.item.IMixedStackList;

public final class CraftingCPUCluster implements IAECluster, ICraftingCPU {

    private static final String LOG_MARK_AS_COMPLETE = "Completed job for %s.";

    private final BlockPos boundsMin;
    private final BlockPos boundsMax;
    private final int[] usedOps = new int[3];
    private final Map<ICraftingPatternDetails, TaskProgress> tasks = new HashMap<>();
    // INSTANCE sate
    private final List<TileCraftingTile> tiles = new ArrayList<>();
    private final List<TileCraftingTile> storage = new ArrayList<>();
    private final List<TileCraftingMonitorTile> status = new ArrayList<>();
    private final HashMap<IMEMonitorHandlerReceiver<IAEStackBase>, Object> listeners = new HashMap<>();
    private final Map<ICraftingPatternDetails, Queue<ICraftingMedium>> visitedMediums = new HashMap<>();
    private ICraftingMedium LatestMedium;
    private ICraftingLink myLastLink;
    private String myName = "";
    private boolean isDestroyed = false;
    /**
     * crafting job info
     */
    private MECraftingInventory inventory = new MECraftingInventory();
    private IAEStack<?> finalOutput;
    private long amount;
    private boolean waiting = false;
    private IMixedStackList waitingFor = new appeng.util.item.IAEStackList();
    private long availableStorage = 0;
    private MachineSource machineSrc = null;
    private int accelerator = 0;
    private boolean isComplete = true;
    private int remainingOperations;
    private boolean somethingChanged;
    private boolean pause;

    private long lastTime;
    private long elapsedTime;
    private long startItemCount;
    private long remainingItemCount;
    private UUID requestingPlayerUUID;

    public CraftingCPUCluster(final BlockPos boundsMin, final BlockPos boundsMax) {
        this.boundsMin = boundsMin.toImmutable();
        this.boundsMax = boundsMax.toImmutable();
    }

    @Override
    public boolean isPause() {
        return pause;
    }

    @Override
    public IAEStack<?> getFinalMultiOutput() {
        return finalOutput;
    }

    @Override
    public boolean isDestroyed() {
        return this.isDestroyed;
    }

    public ICraftingLink getLastCraftingLink() {
        return this.myLastLink;
    }

    @Override
    public BlockPos getBoundsMin() {
        return boundsMin;
    }

    @Override
    public BlockPos getBoundsMax() {
        return boundsMax;
    }

    /**
     * add a new Listener to the monitor, be sure to properly remove yourself when your done.
     */
    @Override
    public void addListener(final IMEMonitorHandlerReceiver<IAEStackBase> l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    /**
     * remove a Listener to the monitor.
     */
    @Override
    public void removeListener(final IMEMonitorHandlerReceiver<IAEStackBase> l) {
        this.listeners.remove(l);
    }

    public IMEInventory<IAEItemStack> getInventory() {
        return this.inventory;
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

    public boolean canAccept(final IAEStack<?> input) {
        if (input != null) {
            final IAEStack<?> is = this.waitingFor.findPrecise(input);
            return is != null && is.getStackSize() > 0;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public IAEStack<?> injectItems(final IAEStack<?> input, final Actionable type, final IActionSource src) {
        // also stop accepting items when the job is complete, i.e. to prevent re-insertion when pushing out
        // items during storeItems
        if (input == null || isComplete) {
            return input;
        }

        final IAEStack what = input.copy();
        final IAEStack<?> is = this.waitingFor.findPrecise(what);

        if (type == Actionable.SIMULATE)// causes crafting to lock up?
        {
            if (is != null && is.getStackSize() > 0) {
                if (is.getStackSize() >= what.getStackSize()) {
                    if (this.finalOutput != null && this.finalOutput.isSameType(what)) {
                        if (this.myLastLink != null) {
                            return ((CraftingLink) this.myLastLink).injectItems(what.copy(), type);
                        }

                        return what; // ignore it.
                    }

                    return null;
                }

                final IAEStack leftOver = what.copy();
                leftOver.decStackSize(is.getStackSize());

                final IAEStack<?> used = what.copy();
                used.setStackSize(is.getStackSize());

                if (this.finalOutput != null && this.finalOutput.isSameType(what)) {
                    if (this.myLastLink != null) {
                        leftOver.add(((CraftingLink) this.myLastLink).injectItems(used.copy(), type));
                        return leftOver;
                    }

                    return what; // ignore it.
                }

                return leftOver;
            }
        } else if (type == Actionable.MODULATE) {
            if (is != null && is.getStackSize() > 0) {
                this.waiting = false;

                this.postChange(what, src);

                if (is.getStackSize() >= what.getStackSize()) {
                    is.decStackSize(what.getStackSize());

                    this.updateRemainingItemCount(what);
                    this.markDirty();
                    this.postCraftingStatusChange(what.copy().setStackSize(-what.getStackSize()));

                    if (this.finalOutput != null && this.finalOutput.isSameType(what)) {
                        IAEStack leftover = what;

                        this.finalOutput.decStackSize(what.getStackSize());

                        if (this.myLastLink != null) {
                            leftover = ((CraftingLink) this.myLastLink).injectItems(what, type);
                        }

                        if (this.finalOutput.getStackSize() <= 0) {
                            this.completeJob();
                        }

                        this.updateCPU();

                        return leftover; // ignore it.
                    }

                    // 2000
                    this.inventory.injectItems(what, Actionable.MODULATE);
                    return null;
                }

                final IAEStack insert = what.copy();
                insert.setStackSize(is.getStackSize());
                what.decStackSize(is.getStackSize());

                is.setStackSize(0);
                this.postCraftingStatusChange(insert.copy().setStackSize(-insert.getStackSize()));

                if (this.finalOutput != null && this.finalOutput.isSameType(insert)) {
                    IAEStack leftover = input;

                    this.finalOutput.decStackSize(insert.getStackSize());

                    if (this.myLastLink != null) {
                        what.add(((CraftingLink) this.myLastLink).injectItems(insert.copy(), type));
                        leftover = what;
                    }

                    if (this.finalOutput.getStackSize() <= 0) {
                        this.completeJob();
                    }

                    this.updateCPU();
                    this.markDirty();

                    return leftover; // ignore it.
                }

                this.inventory.injectItems(insert, Actionable.MODULATE);
                this.markDirty();

                return what;
            }
        }

        return input;
    }

    @SuppressWarnings("unchecked")
    private void postChange(final IAEStack<?> diff, final IActionSource src) {
        final Iterator<Entry<IMEMonitorHandlerReceiver<IAEStackBase>, Object>> i = this.getListeners();

        // protect integrity
        if (i.hasNext()) {
            final ImmutableList<IAEStackBase> single = ImmutableList.of(diff.copy());

            while (i.hasNext()) {
                final Entry<IMEMonitorHandlerReceiver<IAEStackBase>, Object> o = i.next();
                final IMEMonitorHandlerReceiver<IAEStackBase> receiver = o.getKey();

                if (receiver.isValid(o.getValue())) {
                    receiver.postChange(null, single, src);
                } else {
                    i.remove();
                }
            }
        }
    }

    private void markDirty() {
        this.getCore().saveChanges();
    }

    private void postCraftingStatusChange(final IAEStack<?> diff) {
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

    private void completeJob() {
        if (this.myLastLink != null) {
            ((CraftingLink) this.myLastLink).markDone();
        }

        if (AELog.isCraftingLogEnabled()) {
            final IAEStack<?> logStack = this.finalOutput.copy();
            logStack.setStackSize(this.startItemCount);
            AELog.crafting(LOG_MARK_AS_COMPLETE, logStack);
        }

        // Waiting for can potentially contain items at this point, if the user has a 64xplank->64xbutton processing
        // recipe for example, but only requested 1xbutton. We just ignore the rest since it will be dumped
        // back into the network inventory regardless. For this to work it's important that injectItems in this CPU
        // does not accept any further items if isComplete is true.
        this.waitingFor.resetStatus();
        this.remainingItemCount = 0;
        this.startItemCount = 0;
        this.lastTime = 0;
        this.elapsedTime = 0;
        this.isComplete = true;

        notifyRequester(false);
        this.requestingPlayerUUID = null;
    }

    private void notifyRequester(boolean cancelled) {
        if (!Platform.isServer())
            return;
        if (this.requestingPlayerUUID == null)
            return;
        if (this.finalOutput == null)
            return;
        if (!AEConfig.instance().isFeatureEnabled(AEFeature.CRAFTING_TOASTS))
            return;

        var player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
        if (player instanceof EntityPlayerMP playerMP) {
            try {
                ItemStack itemStack = this.finalOutput.asItemStackRepresentation();
                // PacketCraftingToast 目前只支持 IAEItemStack
                if (this.finalOutput instanceof IAEItemStack itemOutput) {
                    NetworkHandler.instance().sendTo(new PacketCraftingToast(itemOutput, amount, cancelled),
                            playerMP);
                }
                if (Platform.isModLoaded("betterquesting"))
                    BQEventHelper.sendMessage(itemStack, playerMP);
            } catch (IOException ignored) {
            }
        }
    }

    private void updateCPU() {
        IAEStack<?> send = this.finalOutput;

        if (this.finalOutput != null && this.finalOutput.getStackSize() <= 0) {
            send = null;
        }

        for (final TileCraftingMonitorTile t : this.status) {
            t.setJob(send);
        }
    }

    private Iterator<Entry<IMEMonitorHandlerReceiver<IAEStackBase>, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    private TileCraftingTile getCore() {
        if (this.machineSrc == null) {
            return null;
        }
        return (TileCraftingTile) this.machineSrc.machine().get();
    }

    private IGrid getGrid() {
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

    private boolean canCraft(final ICraftingPatternDetails details, final IAEStack<?>[] condensedInputs) {
        if (!details.isCraftable()) {
            // 加工模式：使用泛型提取检查所有类型（物品+流体等）
            for (IAEStack<?> input : condensedInputs) {
                if (input == null) continue;
                final IAEStack<?> ais = this.inventory.extractAny(input.copy(), Actionable.SIMULATE);

                if (ais == null || ais.getStackSize() < input.getStackSize()) {
                    return false;
                }
            }
        } else if (details.canSubstitute()) {
            // When substitutions are allowed, we have to keep track of which items we've reserved
            IAEItemStack[] inputs = details.getInputs();
            Map<IAEItemStack, Integer> consumedCount = new HashMap<>();
            for (int i = 0; i < inputs.length; i++) {
                List<IAEItemStack> substitutes = details.getSubstituteInputs(i);
                if (substitutes.isEmpty()) {
                    continue;
                }

                boolean found = false;
                for (IAEItemStack substitute : substitutes) {
                    for (IAEItemStack fuzz : this.inventory.findFuzzyItems(substitute, FuzzyMode.IGNORE_ALL)) {
                        int alreadyConsumed = consumedCount.getOrDefault(fuzz, 0);
                        if (fuzz.getStackSize() - alreadyConsumed <= 0) {
                            continue; // Already fully consumed by a previous slot of this recipe
                        }

                        fuzz = fuzz.copy();
                        fuzz.setStackSize(1); // We're iterating over non condensed inputs which means there's 1 of each
                        // needed
                        final IAEItemStack ais = this.inventory.extractItems(fuzz, Actionable.SIMULATE,
                                this.machineSrc);

                        if (ais != null && ais.getStackSize() > 0) {
                            // Mark 1 of the stack as consumed
                            consumedCount.merge(fuzz, 1, Integer::sum);
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        break;
                    }
                }

                if (!found) {
                    return false;
                }
            }

        } else {
            // When no substitutions can occur, we can simply check that all items are accounted since
            // each type of item should only occur once
            for (IAEStack<?> condensedInput : condensedInputs) {
                if (!(condensedInput instanceof IAEItemStack g)) {
                    return false;
                }
                boolean found = false;

                for (IAEItemStack fuzz : this.inventory.findFuzzyItems(g, FuzzyMode.IGNORE_ALL)) {
                    fuzz = fuzz.copy();
                    fuzz.setStackSize(g.getStackSize());
                    final IAEItemStack ais = this.inventory.extractItems(fuzz, Actionable.SIMULATE, this.machineSrc);

                    if (ais != null && ais.getStackSize() >= g.getStackSize()) {
                        found = true;
                        break;
                    } else if (ais != null) {
                        g = g.copy();
                        g.decStackSize(ais.getStackSize());
                    }
                }

                if (!found) {
                    return false;
                }
            }

        }

        return true;
    }

    public void cancel() {
        if (this.myLastLink != null) {
            this.myLastLink.cancel();
        }

        final IItemList<IAEStackBase> list;
        this.getGenericListOfItem(list = new appeng.util.item.IAEStackList(), CraftingItemList.ALL);
        for (final IAEStackBase is : list) {
            this.postChange(asGenericStack(is), this.machineSrc);
        }

        this.isComplete = true;
        this.myLastLink = null;
        this.tasks.clear();

        final List<IAEStack<?>> items = new ArrayList<>(this.waitingFor.size());
        for (final IAEStackBase stackBase : this.waitingFor) {
            if (stackBase instanceof IAEStack<?> aeStack) {
                items.add(aeStack.copy().setStackSize(-aeStack.getStackSize()));
            }
        }

        this.waitingFor.resetStatus();

        for (final IAEStack<?> is : items) {
            this.postCraftingStatusChange(is);
        }

        notifyRequester(true);
        this.requestingPlayerUUID = null;
        this.finalOutput = null;
        this.amount = 0;
        this.updateCPU();

        this.storeItems(); // marks dirty
    }

    public void switchCrafting() {
        this.pause = !pause;
    }

    public void trackCrafting() {
        EntityPlayer player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
        AETrack.trackCrafting(player, LatestMedium);
    }

    public void updateCraftingLogic(final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc) {
        if (!this.getCore().isActive()) {
            return;
        }

        if (this.myLastLink != null) {
            if (this.myLastLink.isCanceled()) {
                this.myLastLink = null;
                this.cancel();
            }
        }

        if (this.isComplete) {
            if (this.inventory.isEmpty()) {
                return;
            }

            this.storeItems();
            return;
        }

        this.waiting = false;
        if (this.waiting || this.tasks.isEmpty()) // nothing to do here...
        {
            return;
        }

        this.remainingOperations = this.accelerator + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
        final int started = this.remainingOperations;

        if (this.remainingOperations > 0) {
            do {
                this.somethingChanged = false;
                this.executeCrafting(eg, cc);
            } while (this.somethingChanged && this.remainingOperations > 0);
        }
        this.usedOps[2] = this.usedOps[1];
        this.usedOps[1] = this.usedOps[0];
        this.usedOps[0] = started - this.remainingOperations;

        if (this.remainingOperations > 0 && !this.somethingChanged) {
            this.waiting = true;
        }
    }

    private void executeCrafting(final IEnergyGrid eg, final CraftingGridCache cc) {
        final Iterator<Entry<ICraftingPatternDetails, TaskProgress>> i = this.tasks.entrySet().iterator();

        while (i.hasNext()) {
            final Entry<ICraftingPatternDetails, TaskProgress> e = i.next();

            if (e.getValue().value <= 0) {
                i.remove();
                continue;
            }

            final ICraftingPatternDetails details = e.getKey();
            boolean isCraftable = details.isCraftable();

            int BATCH_SIZE = AEConfig.instance().getCraftingMaxBatchSize();
            if (isCraftable) {
                BATCH_SIZE = 1;
            } else {
                remainingOperations = Math.max(this.remainingOperations, BATCH_SIZE);
            }

            for (int times = 0; times < BATCH_SIZE && e.getValue().value > 0; times++) {
                if (this.remainingOperations <= 0) {
                    break;
                }

                if (this.canCraft(details, details.getCondensedAEInputs())) {
                    InventoryCrafting ic = null;

                    if (!visitedMediums.containsKey(details) || visitedMediums.get(details).isEmpty()) {
                        visitedMediums.put(details, new ArrayDeque<>(
                                cc.getMediums(details).stream().filter(Objects::nonNull).collect(Collectors.toList())));
                    }

                    while (!visitedMediums.get(details).isEmpty()) {

                        ICraftingMedium m = visitedMediums.get(details).poll();

                        if (e.getValue().value <= 0) {
                            continue;
                        }

                        if (m != null && !m.isBusy()) {
                            if (ic == null) {
                                final IAEStack<?>[] input = details.getAEInputs();
                                double sum = 0;

                                for (final IAEStack<?> anInput : input) {
                                    if (anInput != null) {
                                        sum += anInput.getStackSize();
                                    }
                                }

                                // power...
                                if (eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG) < sum - 0.01) {
                                    continue;
                                }
                                if (details.isCraftable()) {
                                    ic = new InventoryCrafting(new ContainerNull(), 3, 3);
                                } else {
                                    ic = new InventoryCrafting(new ContainerNull(),
                                            PatternHelper.PROCESSING_INPUT_WIDTH,
                                            PatternHelper.PROCESSING_INPUT_HEIGHT);
                                }

                                boolean found = false;

                                for (int x = 0; x < input.length; x++) {
                                    if (input[x] != null) {
                                        found = false;

                                        if (details.isCraftable()) {
                                            // 合成台模式：只支持物品，使用旧接口
                                            final Collection<IAEItemStack> itemList;

                                            if (details.canSubstitute()) {
                                                final List<IAEItemStack> substitutes = details.getSubstituteInputs(x);
                                                itemList = new ArrayList<>(substitutes.size());

                                                for (IAEItemStack stack : substitutes) {
                                                    itemList.addAll(
                                                            this.inventory.findFuzzyItems(stack, FuzzyMode.IGNORE_ALL));
                                                }
                                            } else {
                                                itemList = new ArrayList<>(1);

                                                final IAEItemStack item = this.inventory
                                                        .findPreciseItem((IAEItemStack) input[x]);
                                                if (item != null) {
                                                    itemList.add(item);
                                                } else if (((IAEItemStack) input[x]).getDefinition().getItem().isDamageable() || Platform
                                                        .isGTDamageableItem(((IAEItemStack) input[x]).getDefinition().getItem())) {
                                                    itemList.addAll(this.inventory.findFuzzyItems(
                                                            (IAEItemStack) input[x], FuzzyMode.IGNORE_ALL));
                                                }
                                            }

                                            for (IAEItemStack fuzz : itemList) {
                                                fuzz = fuzz.copy();
                                                fuzz.setStackSize(input[x].getStackSize());

                                                if (details.isValidItemForSlot(x, fuzz.createItemStack(),
                                                        this.getWorld())) {
                                                    final IAEItemStack ais = this.inventory.extractItems(fuzz,
                                                            Actionable.MODULATE, this.machineSrc);
                                                    final ItemStack is = ais == null ? ItemStack.EMPTY
                                                            : ais.createItemStack();

                                                    if (!is.isEmpty()) {
                                                        this.postChange(AEItemStack.fromItemStack(is), this.machineSrc);
                                                        ic.setInventorySlotContents(x, is);
                                                        found = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        } else {
                                            // 加工模式：使用泛型接口提取所有类型
                                            final IAEStack<?> ais = this.inventory.extractAny(input[x].copy(),
                                                    Actionable.MODULATE);

                                            if (ais != null && ais.getStackSize() > 0) {
                                                this.postChange(input[x], this.machineSrc);
                                                // 将泛型栈转换为 ItemStack 放入 InventoryCrafting
                                                // 物品类型直接 createItemStack()，流体类型通过 ItemFluidDrop 桥接
                                                final ItemStack is;
                                                if (ais instanceof IAEItemStack itemAIS) {
                                                    is = itemAIS.createItemStack();
                                                } else if (ais instanceof IAEFluidStack fluidAIS) {
                                                    is = ItemFluidDrop.newStack(fluidAIS.getFluidStack());
                                                } else {
                                                    is = ais.asItemStackRepresentation();
                                                }
                                                ic.setInventorySlotContents(x, is);
                                                if (ais.getStackSize() >= input[x].getStackSize()) {
                                                    found = true;
                                                    continue;
                                                }
                                            }
                                        }

                                        if (!found) {
                                            break;
                                        }
                                    }
                                }

                                if (!found) {
                                    // put stuff back..
                                    for (int x = 0; x < ic.getSizeInventory(); x++) {
                                        final ItemStack is = ic.getStackInSlot(x);
                                        if (!is.isEmpty()) {
                                            this.inventory.injectItems(AEItemStack.fromItemStack(is),
                                                    Actionable.MODULATE,
                                                    this.machineSrc);
                                        }
                                    }
                                    ic = null;
                                    break;
                                }
                            }

                            if (m.pushPattern(details, ic)) {
                                if (m != LatestMedium)
                                    LatestMedium = m;
                                this.somethingChanged = true;
                                this.remainingOperations--; // 消耗1次额度

                                for (final IAEStack<?> out : details.getCondensedAEOutputs()) {
                                    this.postChange(out, this.machineSrc);
                                    this.waitingFor.add(out.copy());
                                    this.postCraftingStatusChange(out.copy());
                                }

                                if (details.isCraftable()) {
                                    for (int x = 0; x < ic.getSizeInventory(); x++) {
                                        final ItemStack output = Platform.getContainerItem(ic.getStackInSlot(x));
                                        if (!output.isEmpty()) {
                                            final IAEItemStack cItem = AEItemStack.fromItemStack(output);
                                            this.postChange(cItem, this.machineSrc);
                                            this.waitingFor.add(cItem);
                                            this.postCraftingStatusChange(cItem);
                                        }
                                    }
                                }

                                ic = null; // hand off complete!
                                this.markDirty();

                                e.getValue().value--;

                                // ==================== 批量推送扩展（移植自 PH-Mod 合成转储器） ====================
                                // 默认启用无限转储模式：批量推送不消耗 remainingOperations
                                if (m instanceof IMultiplePatternPushable && !details.isCraftable() && e.getValue().value > 0) {
                                    this.executeBatchPush(eg, (IMultiplePatternPushable) m, details, e);
                                }
                                // ==================== 批量推送扩展结束 ====================

                                if (e.getValue().value <= 0) {
                                    continue;
                                }

                                if (this.remainingOperations == 0) {
                                    return;
                                }
                            }
                        }
                    }

                    if (ic != null) {
                        // put stuff back..
                        for (int x = 0; x < ic.getSizeInventory(); x++) {
                            final ItemStack is = ic.getStackInSlot(x);
                            if (!is.isEmpty()) {
                                this.inventory.injectItems(AEItemStack.fromItemStack(is), Actionable.MODULATE,
                                        this.machineSrc);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 批量推送扩展方法（移植自 PH-Mod 的 MixinMultiPattern）。
     * 当 medium 支持 IMultiplePatternPushable 时，一次性从 AE 仓库提取多份材料并推送。
     *
     * @param eg      能量网格
     * @param medium  支持批量推送的 medium
     * @param details 合成样板详情
     * @param e       当前任务条目
     * @return 额外推送的份数（不含已由 pushPattern 推送的第一份）
     */
    private int executeBatchPush(final IEnergyGrid eg, final IMultiplePatternPushable medium,
                                  final ICraftingPatternDetails details,
                                  final Entry<ICraftingPatternDetails, TaskProgress> e) {
        final IAEStack<?>[] input = details.getAEInputs();

        // 第一步：计算每种输入在仓库中的可用数量
        long[] available = new long[input.length];
        IAEStack<?>[] extracted = new IAEStack<?>[input.length];
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null && input[x].getStackSize() > 0) {
                IAEStack<?> toExtract = input[x].copy();
                toExtract.setStackSize(Long.MAX_VALUE);
                final IAEStack<?> ais = this.inventory.extractAny(toExtract, Actionable.MODULATE);
                if (ais != null) {
                    available[x] = ais.getStackSize();
                    extracted[x] = ais;
                    this.postChange(ais, this.machineSrc);
                }
            }
        }

        // 第二步：计算最大可推送份数
        long maxByInventory = Long.MAX_VALUE;
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null && input[x].getStackSize() > 0) {
                long perRecipe = input[x].getStackSize();
                long canDo = available[x] / perRecipe;
                if (canDo < maxByInventory) {
                    maxByInventory = canDo;
                }
            }
        }

        // 合成转储器模式：不受 remainingOperations 限制，等效于 maxSkips = Integer.MAX_VALUE

        int maxTodo = (int) Math.min(maxByInventory, e.getValue().value);
        if (maxTodo <= 0) {
            // 没有额外份数可以推送，退还所有已提取的材料
            for (int x = 0; x < input.length; x++) {
                if (extracted[x] != null) {
                    this.inventory.injectItems(extracted[x].copy(), Actionable.MODULATE);
                }
            }
            return 0;
        }

        // 第三步：构建 InventoryCrafting 并调用批量推送
        InventoryCrafting ic = new InventoryCrafting(new appeng.container.ContainerNull(),
                appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH,
                appeng.helpers.PatternHelper.PROCESSING_INPUT_HEIGHT);
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null) {
                // 物品类型直接 createItemStack()，流体类型通过 ItemFluidDrop 桥接
                final ItemStack is;
                if (input[x] instanceof IAEItemStack itemInput) {
                    is = itemInput.createItemStack();
                } else if (input[x] instanceof IAEFluidStack fluidInput) {
                    is = ItemFluidDrop.newStack(fluidInput.getFluidStack());
                } else {
                    is = input[x].asItemStackRepresentation();
                }
                ic.setInventorySlotContents(x, is);
            }
        }

        int[] result = medium.pushPatternMulti(details, ic, maxTodo);
        int pushed = (result != null && result.length > 0) ? result[0] : 0;

        // 第四步：更新 waitingFor 和任务进度
        if (pushed > 0) {
            for (final IAEStack<?> out : details.getCondensedAEOutputs()) {
                IAEStack<?> outCopy = out.copy();
                outCopy.setStackSize(pushed * out.getStackSize());
                this.postChange(outCopy, this.machineSrc);
                this.waitingFor.add(outCopy.copy());
                this.postCraftingStatusChange(outCopy.copy());
            }
            e.getValue().value -= pushed;
            this.somethingChanged = true;
            this.markDirty();
        }

        // 第五步：退还未使用的材料
        for (int x = 0; x < input.length; x++) {
            if (extracted[x] != null && input[x] != null) {
                long used = (long) pushed * input[x].getStackSize();
                long remaining = available[x] - used;
                if (remaining > 0) {
                    IAEStack<?> toReturn = input[x].copy();
                    toReturn.setStackSize(remaining);
                    this.inventory.injectItems(toReturn, Actionable.MODULATE);
                }
            }
        }

        return pushed;
    }

    private void storeItems() {
        Preconditions.checkState(isComplete, "CPU should be complete to prevent re-insertion when dumping items");
        final IGrid g = this.getGrid();

        if (g == null) {
            return;
        }

        final IStorageGrid sg = g.getCache(IStorageGrid.class);

        // 统一处理所有类型（物品、流体等）
        for (var entry : this.inventory.getInventoryMap().entrySet()) {
            final var type = entry.getKey();
            final IMEMonitor<?> monitor = sg.getInventory(type);
            if (monitor == null) continue;
            final IItemList<?> list = entry.getValue();
            for (IAEStackBase stackBase : asBaseList(list)) {
                IAEStack<?> aeStack = asGenericStack(stackBase);
                aeStack = this.inventory.extractAny(aeStack.copy(), Actionable.MODULATE);

                if (aeStack != null) {
                    this.postChange(aeStack, this.machineSrc);
                    aeStack = StorageHelper.injectItems(monitor, aeStack, Actionable.MODULATE, this.machineSrc);
                }

                if (aeStack != null) {
                    this.inventory.injectItems(aeStack, Actionable.MODULATE);
                }
            }
        }

        if (this.inventory.isEmpty()) {
            this.inventory = new MECraftingInventory();
        }

        this.markDirty();
    }

    @SuppressWarnings("unchecked")
    private static IItemList<IAEStackBase> asBaseList(final IItemList<?> list) {
        return (IItemList<IAEStackBase>) list;
    }

    private static IAEStack<?> asGenericStack(final IAEStackBase stackBase) {
        return (IAEStack<?>) stackBase;
    }

    @SuppressWarnings("unchecked")
    public ICraftingLink submitJob(final IGrid g, final ICraftingJob job, final IActionSource src,
            final ICraftingRequester requestingMachine) {
        if (!this.tasks.isEmpty() || !this.waitingFor.isEmpty()) {
            return null;
        }

        if (this.isBusy() || !this.isActive() || this.availableStorage < job.getByteTotal()) {
            return null;
        }

        final IStorageGrid sg = g.getCache(IStorageGrid.class);
        final MECraftingInventory ci = new MECraftingInventory(sg, true, false, false);

        this.waitingFor.resetStatus();
        if (job instanceof CraftingJobV2) {
            ((CraftingJobV2) job).startCrafting(ci, this, src);
        } else {
            return null;
        }
        if (ci.commit(src)) {
                this.finalOutput = job.getOutput();
                this.amount = job.getOutput().getStackSize();
                this.waiting = false;
                this.isComplete = false;

                // Store the requesting player if present.
                if (src instanceof PlayerSource playerSource && playerSource.player().isPresent()) {
                    this.requestingPlayerUUID = playerSource.player().get().getUniqueID();
                } else {
                    this.requestingPlayerUUID = null;
                }

                this.markDirty();

                this.updateCPU();
                final String craftID = this.generateCraftingID();

                this.myLastLink = new CraftingLink(this.generateLinkData(craftID, requestingMachine == null, false),
                        this);

                this.prepareElapsedTime();

                if (requestingMachine == null) {
                    return this.myLastLink;
                }

                final ICraftingLink whatLink = new CraftingLink(this.generateLinkData(craftID, false, true),
                        requestingMachine);

                this.submitLink(this.myLastLink);
                this.submitLink(whatLink);

                final IItemList<IAEStackBase> list = new appeng.util.item.IAEStackList();
                this.getGenericListOfItem(list, CraftingItemList.ALL);
                for (final IAEStackBase ge : list) {
                    this.postChange(asGenericStack(ge), this.machineSrc);
                }

                return whatLink;
        } else {
            this.tasks.clear();
            this.inventory.resetStatus();
        }

        return null;
    }

    @Override
    public boolean isBusy() {

        this.tasks.entrySet().removeIf(taskProgressEntry -> taskProgressEntry.getValue().value <= 0);

        if (!this.waitingFor.isEmpty() || !this.tasks.isEmpty()) {
            this.updateElapsedTime();
        }

        return !this.tasks.isEmpty() || !this.waitingFor.isEmpty();
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

    private String generateCraftingID() {
        final long now = System.currentTimeMillis();
        final int hash = System.identityHashCode(this);
        final int hmm = this.finalOutput == null ? 0 : this.finalOutput.hashCode();

        return Long.toString(now, Character.MAX_RADIX) + '-' + Integer.toString(hash, Character.MAX_RADIX) + '-'
                + Integer.toString(hmm, Character.MAX_RADIX);
    }

    private NBTTagCompound generateLinkData(final String craftingID, final boolean standalone, final boolean req) {
        final NBTTagCompound tag = new NBTTagCompound();

        tag.setString("CraftID", craftingID);
        tag.setBoolean("canceled", false);
        tag.setBoolean("done", false);
        tag.setBoolean("standalone", standalone);
        tag.setBoolean("req", req);

        return tag;
    }

    private void submitLink(final ICraftingLink myLastLink2) {
        if (this.getGrid() != null) {
            final CraftingGridCache cc = this.getGrid().getCache(ICraftingGrid.class);
            cc.addLink((CraftingLink) myLastLink2);
        }
    }

    /**
     * 获取指定类别的物品列表（支持物品/流体等所有类型）。
     */
    @SuppressWarnings("unchecked")
    public void getGenericListOfItem(final IItemList<IAEStackBase> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE:
                for (final IAEStackBase ais : this.waitingFor) {
                    list.add(ais);
                }
                break;
            case PENDING:
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
                break;
            case STORAGE:
                this.inventory.getAvailableStacks(list);
                break;
            default:
            case ALL:
                this.inventory.getAvailableStacks(list);

                for (final IAEStackBase ais : this.waitingFor) {
                    list.add(ais);
                }

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        ais = ais.copy();
                        ais.setStackSize(ais.getStackSize() * t.getValue().value);
                        list.add(ais);
                    }
                }
                break;
        }
    }

    public void addStorage(final IAEStack<?> stack) {
        this.inventory.injectItems(stack, Actionable.MODULATE);
    }

    public void addEmitable(final IAEStack<?> stack) {
        this.waitingFor.add(stack);
        this.postCraftingStatusChange(stack);
    }

    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        TaskProgress i = this.tasks.get(details);

        if (i == null) {
            this.tasks.put(details, i = new TaskProgress());
        }

        i.value += crafts;
    }

    /**
     * 获取指定栈在指定类别中的数据（支持物品/流体等所有类型）。
     */
    public IAEStack<?> getItemStack(final IAEStack<?> what, final CraftingItemList storage2) {
        IAEStack<?> is;

        switch (storage2) {
            case STORAGE:
                is = this.inventory.findPreciseAny(what);
                break;
            case ACTIVE:
                is = this.waitingFor.findPrecise(what);
                break;
            case PENDING:
                is = what.copy();
                is.setStackSize(0);

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final IAEStack<?> ais : t.getKey().getCondensedAEOutputs()) {
                        if (ais.isSameType(is)) {
                            is.setStackSize(is.getStackSize() + ais.getStackSize() * t.getValue().value);
                        }
                    }
                }

                break;
            default:
            case ALL:
                throw new IllegalStateException("Invalid Operation");
        }

        if (is != null) {
            return is.copy();
        }

        return what.copy().setStackSize(0);
    }

    public void writeToNBT(final NBTTagCompound data) {
        // finalOutput 使用泛型序列化（支持物品和流体）
        final NBTTagCompound outputTag = new NBTTagCompound();
        if (this.finalOutput != null) {
            this.finalOutput.writeToNBTGeneric(outputTag);
        }
        data.setTag("finalOutput", outputTag);
        data.setTag("inventory", this.inventory.writeInventory());
        data.setBoolean("waiting", this.waiting);
        data.setBoolean("isComplete", this.isComplete);
        data.setBoolean("pause", this.pause);

        if (this.myLastLink != null) {
            final NBTTagCompound link = new NBTTagCompound();
            this.myLastLink.writeToNBT(link);
            data.setTag("link", link);
        }

        final NBTTagList list = new NBTTagList();
        for (final Entry<ICraftingPatternDetails, TaskProgress> e : this.tasks.entrySet()) {
            final NBTTagCompound item = this.writeItem(AEItemStack.fromItemStack(e.getKey().getPattern()));
            item.setLong("craftingProgress", e.getValue().value);
            list.appendTag(item);
        }
        data.setTag("tasks", list);

        data.setTag("waitingFor", this.writeList(this.waitingFor));

        data.setLong("elapsedTime", this.getElapsedTime());
        data.setLong("startItemCount", this.getStartItemCount());
        data.setLong("remainingItemCount", this.getRemainingItemCount());
        data.setLong("amount", amount);

        if (Platform.isServer() && this.requestingPlayerUUID != null) {
            data.setUniqueId("requestingPlayerUUID", this.requestingPlayerUUID);
        }
    }

    private NBTTagCompound writeItem(final IAEItemStack finalOutput2) {
        final NBTTagCompound out = new NBTTagCompound();

        if (finalOutput2 != null) {
            finalOutput2.writeToNBT(out);
        }

        return out;
    }

    private NBTTagList writeList(final IMixedStackList myList) {
        return appeng.util.AEStackSerialization.writeAEStackListNBT(myList);
    }

    void done() {
        final TileCraftingTile core = this.getCore();

        core.setCoreBlock(true);

        if (core.getPreviousState() != null) {
            this.readFromNBT(core.getPreviousState());
            core.setPreviousState(null);
        }

        this.updateCPU();
        this.updateName();
    }

    @SuppressWarnings("unchecked")
    public void readFromNBT(final NBTTagCompound data) {
        // finalOutput 使用泛型反序列化（支持物品和流体）
        final NBTTagCompound outputTag = (NBTTagCompound) data.getTag("finalOutput");
        if (outputTag != null) {
            this.finalOutput = IAEStack.fromNBTGeneric(outputTag);
            // 兼容旧存档：如果泛型反序列化失败，尝试物品类型反序列化
            if (this.finalOutput == null) {
                this.finalOutput = AEItemStack.fromNBT(outputTag);
            }
        }

        // inventory 使用泛型反序列化
        final NBTTagList invTag = (NBTTagList) data.getTag("inventory");
        if (invTag != null) {
            this.inventory.readInventory(invTag);
        }

        this.waiting = data.getBoolean("waiting");
        this.isComplete = data.getBoolean("isComplete");
        this.pause = data.getBoolean("pause");

        if (data.hasKey("link")) {
            final NBTTagCompound link = data.getCompoundTag("link");
            this.myLastLink = new CraftingLink(link, this);
            this.submitLink(this.myLastLink);
        }

        final NBTTagList list = data.getTagList("tasks", 10);
        for (int x = 0; x < list.tagCount(); x++) {
            final NBTTagCompound item = list.getCompoundTagAt(x);
            final IAEItemStack pattern = AEItemStack.fromNBT(item);
            if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem cpi) {
                final ICraftingPatternDetails details = cpi.getPatternForItemWithNest(pattern.createItemStack(),
                        this.getWorld());
                if (details != null) {
                    final TaskProgress tp = new TaskProgress();
                    tp.value = item.getLong("craftingProgress");
                    this.tasks.put(details, tp);
                }
            }
        }

        this.waitingFor = this.readList((NBTTagList) data.getTag("waitingFor"));
        for (final IAEStackBase stackBase : this.waitingFor) {
            if (stackBase instanceof IAEStack<?> stack) {
                this.postCraftingStatusChange(stack.copy());
            }
        }

        this.lastTime = System.nanoTime();
        this.elapsedTime = data.getLong("elapsedTime");
        this.startItemCount = data.getLong("startItemCount");
        this.remainingItemCount = data.getLong("remainingItemCount");
        this.amount = data.getLong("amount");

        if (Platform.isServer() && data.hasUniqueId("requestingPlayerUUID")) {
            this.requestingPlayerUUID = data.getUniqueId("requestingPlayerUUID");
        }
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

    private IMixedStackList readList(final NBTTagList tag) {
        final IMixedStackList out = new appeng.util.item.IAEStackList();

        if (tag == null) {
            return out;
        }

        appeng.util.AEStackSerialization.readAEStackListNBT(out, tag);

        return out;
    }

    private World getWorld() {
        return this.getCore().getWorld();
    }

    public void breakCluster() {
        final TileCraftingTile t = this.getCore();

        if (t != null) {
            t.breakCluster();
        }
    }

    private void prepareElapsedTime() {
        this.lastTime = System.nanoTime();
        this.elapsedTime = 0;

        final IItemList<IAEStackBase> list = new appeng.util.item.IAEStackList();

        this.getGenericListOfItem(list, CraftingItemList.ACTIVE);
        this.getGenericListOfItem(list, CraftingItemList.PENDING);

        long itemCount = 0;
        for (final IAEStackBase ge : list) {
            itemCount += ge.getStackSize();
        }

        this.startItemCount = itemCount;
        this.remainingItemCount = itemCount;
    }

    private void updateRemainingItemCount(final IAEStack<?> is) {
        this.remainingItemCount = this.getRemainingItemCount() - is.getStackSize();
    }

    private void updateElapsedTime() {
        final long nextStartTime = System.nanoTime();
        this.elapsedTime = this.getElapsedTime() + nextStartTime - this.lastTime;
        this.lastTime = nextStartTime;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    @Override
    public long getRemainingItemCount() {
        return this.remainingItemCount;
    }

    @Override
    public long getStartItemCount() {
        return this.startItemCount;
    }

    private static class TaskProgress {
        private long value;
    }
}

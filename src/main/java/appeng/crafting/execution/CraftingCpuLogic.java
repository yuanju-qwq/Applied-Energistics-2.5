/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2021, TeamAppliedEnergistics, All rights reserved.
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

package appeng.crafting.execution;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.common.base.Preconditions;


import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.PowerMultiplier;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CraftingItemList;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.IMultiplePatternPushable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCraftingToast;
import appeng.crafting.CraftingLink;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingJobV2;
import appeng.helpers.PatternHelper;
import appeng.integration.modules.betterquesting.BQEventHelper;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import appeng.util.inv.MEInventoryCrafting;
import appeng.util.item.AEItemStack;

public class CraftingCpuLogic {

    private static final String LOG_MARK_AS_COMPLETE = "Completed job for %s.";

    final CraftingCPUCluster cluster;

    private final int[] usedOps = new int[3];
    private final Map<ICraftingPatternDetails, TaskProgress> tasks = new HashMap<>();
    private final Map<ICraftingPatternDetails, Queue<ICraftingMedium>> visitedMediums = new HashMap<>();
    private ICraftingMedium LatestMedium;
    private ICraftingLink myLastLink;
    private MECraftingInventory inventory = new MECraftingInventory();
    private GenericStack finalOutput;
    private boolean waiting = false;
    private KeyCounter waitingFor = new KeyCounter();
    private boolean isComplete = true;
    private int remainingOperations;
    private boolean somethingChanged;
    private boolean pause;

    private long lastTime;
    private long elapsedTime;
    private long startItemCount;
    private long remainingItemCount;
    private UUID requestingPlayerUUID;

    public CraftingCpuLogic(CraftingCPUCluster cluster) {
        this.cluster = cluster;
    }

    // ============================================================
    // Accessors for cluster
    // ============================================================

    @Nullable
    public GenericStack getFinalOutput() {
        return finalOutput;
    }

    public long getStartItemCount() {
        return startItemCount;
    }

    public long getRemainingItemCount() {
        return remainingItemCount;
    }

    public long getElapsedTime() {
        return elapsedTime;
    }

    public boolean isPause() {
        return pause;
    }

    public boolean isBusy() {
        this.tasks.entrySet().removeIf(taskProgressEntry -> taskProgressEntry.getValue().value <= 0);
        if (!this.waitingFor.isEmpty() || !this.tasks.isEmpty()) {
            this.updateElapsedTime();
        }
        return !this.tasks.isEmpty() || !this.waitingFor.isEmpty();
    }

    // ============================================================
    // Accessors for cluster
    // ============================================================

    public ICraftingLink getLastCraftingLink() {
        return myLastLink;
    }

    public MECraftingInventory getInventory() {
        return inventory;
    }

    // ============================================================
    // Item injection
    // ============================================================

    public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        if (input == null || isComplete) {
            return input;
        }

        final AEKey what = input.what();
        final long amount = input.amount();
        final long current = this.waitingFor.get(what);

        if (type == Actionable.SIMULATE) {
            if (current > 0) {
                if (current >= amount) {
                    if (isFinalOutput(what)) {
                        if (this.myLastLink != null) {
                            return doLinkInject(input, type);
                        }
                        return input;
                    }
                    return null;
                }

                long leftoverAmount = amount - current;
                if (isFinalOutput(what)) {
                    if (this.myLastLink != null) {
                        GenericStack used = new GenericStack(what, current);
                        GenericStack linkLeftover = doLinkInject(used, type);
                        if (linkLeftover != null) {
                            leftoverAmount += linkLeftover.amount();
                        }
                        return new GenericStack(what, leftoverAmount);
                    }
                    return input;
                }
                return new GenericStack(what, leftoverAmount);
            }
        } else if (type == Actionable.MODULATE) {
            if (current > 0) {
                cluster.postChange(input, src);

                if (current >= amount) {
                    this.waitingFor.remove(what, amount);
                    this.updateRemainingItemCount(what, amount);
                    this.markDirty();
                    cluster.postCraftingStatusChange(new GenericStack(what, -amount));

                    if (isFinalOutput(what)) {
                        GenericStack leftover = input;
                        this.finalOutput = new GenericStack(what, this.finalOutput.amount() - amount);
                        if (this.myLastLink != null) {
                            leftover = doLinkInject(input, type);
                        }
                        if (this.finalOutput.amount() <= 0) {
                            this.completeJob();
                        }
                        cluster.updateOutput(this.finalOutput);
                        return leftover;
                    }

                    this.inventory.injectItems(input, Actionable.MODULATE);
                    return null;
                }

                GenericStack insert = new GenericStack(what, current);
                GenericStack whatsLeft = new GenericStack(what, amount - current);

                this.waitingFor.remove(what, current);
                cluster.postCraftingStatusChange(new GenericStack(what, -current));

                if (isFinalOutput(what)) {
                    GenericStack leftover = new GenericStack(what, amount - current);
                    this.finalOutput = new GenericStack(what, this.finalOutput.amount() - current);
                    if (this.myLastLink != null) {
                        GenericStack linkResult = doLinkInject(insert, type);
                        if (linkResult != null) {
                            leftover = new GenericStack(what, whatsLeft.amount() + linkResult.amount());
                        }
                    }
                    if (this.finalOutput.amount() <= 0) {
                        this.completeJob();
                    }
                    cluster.updateOutput(this.finalOutput);
                    this.markDirty();
                    return leftover;
                }

                this.inventory.injectItems(insert, Actionable.MODULATE);
                this.markDirty();
                return whatsLeft;
            }
        }

        return input;
    }

    private boolean isFinalOutput(AEKey what) {
        return this.finalOutput != null && this.finalOutput.what().equals(what);
    }

    private GenericStack doLinkInject(GenericStack input, Actionable type) {
        if (this.myLastLink == null) return input;
        IAEStack<?> result = ((CraftingLink) this.myLastLink).injectItems(input.toIAEStack(), type);
        if (result == null) return null;
        return GenericStack.fromIAEStack(result);
    }

    // ============================================================
    // canAccept
    // ============================================================

    public boolean canAccept(final GenericStack input) {
        if (input != null) {
            return this.waitingFor.get(input.what()) > 0;
        }
        return false;
    }

    // ============================================================
    // Internal helpers (delegate to cluster)
    // ============================================================

    private void markDirty() {
        if (cluster.getCore() != null) {
            cluster.getCore().saveChanges();
        }
    }

    // ============================================================
    // Job completion
    // ============================================================

    private void completeJob() {
        if (this.myLastLink != null) {
            ((CraftingLink) this.myLastLink).markDone();
        }

        if (AELog.isCraftingLogEnabled()) {
            AELog.crafting(LOG_MARK_AS_COMPLETE, this.finalOutput);
        }

        this.waitingFor.reset();
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
                var what = this.finalOutput.what();
                ItemStack itemStack;
                if (what instanceof AEItemKey itemKey) {
                    itemStack = itemKey.toStack();
                    NetworkHandler.instance().sendTo(
                            new PacketCraftingToast(AEItemStack.fromItemStack(itemKey.toStack()),
                                    this.finalOutput.amount(), cancelled),
                            playerMP);
                } else {
                    itemStack = ItemStack.EMPTY;
                }
                if (Platform.isModLoaded("betterquesting"))
                    BQEventHelper.sendMessage(itemStack, playerMP);
            } catch (IOException ignored) {
            }
        }
    }

    // ============================================================
    // canCraft
    // ============================================================

    private boolean canCraft(final ICraftingPatternDetails details, final GenericStack[] condensedInputs) {
        if (!details.isCraftable()) {
            for (GenericStack condensedInput : condensedInputs) {
                if (condensedInput == null) continue;
                final GenericStack ais = this.inventory.extractAny(condensedInput, Actionable.SIMULATE);
                if (ais == null || ais.amount() < condensedInput.amount()) {
                    return false;
                }
            }
        } else if (details.canSubstitute()) {
            GenericStack[] inputs = details.getInputStacks();
            Map<AEKey, Integer> consumedCount = new HashMap<>();
            for (int i = 0; i < inputs.length; i++) {
                List<GenericStack> substitutes = details.getSubstituteInputs(i);
                if (substitutes.isEmpty()) {
                    continue;
                }

                boolean found = false;
                for (GenericStack substitute : substitutes) {
                    for (GenericStack fuzz : this.inventory.findFuzzyAny(substitute, FuzzyMode.IGNORE_ALL)) {
                        int alreadyConsumed = consumedCount.getOrDefault(fuzz.what(), 0);
                        if (fuzz.amount() - alreadyConsumed <= 0) {
                            continue;
                        }

                        final GenericStack extracted = this.inventory.extractItems(
                                new GenericStack(fuzz.what(), 1), Actionable.SIMULATE, this.cluster.getActionSource());

                        if (extracted != null && extracted.amount() > 0) {
                            consumedCount.merge(fuzz.what(), 1, Integer::sum);
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
            for (GenericStack condensedInput : condensedInputs) {
                if (condensedInput == null || !(condensedInput.what() instanceof AEItemKey)) {
                    return false;
                }
                long needed = condensedInput.amount();
                boolean found = false;

                for (GenericStack fuzz : this.inventory.findFuzzyAny(condensedInput, FuzzyMode.IGNORE_ALL)) {
                    final GenericStack extracted = this.inventory.extractItems(
                            new GenericStack(fuzz.what(), needed), Actionable.SIMULATE, this.cluster.getActionSource());

                    if (extracted != null && extracted.amount() >= needed) {
                        found = true;
                        break;
                    } else if (extracted != null) {
                        needed -= extracted.amount();
                    }
                }

                if (!found) {
                    return false;
                }
            }
        }

        return true;
    }

    // ============================================================
    // Cancel / pause / track
    // ============================================================

    public void cancel() {
        if (this.myLastLink != null) {
            this.myLastLink.cancel();
        }

        final KeyCounter bridgeCancel = new KeyCounter();
        this.getGenericListOfItem(bridgeCancel, CraftingItemList.ALL);
        for (final var entry : bridgeCancel) {
            cluster.postChange(entry.getKey().toIAEStack(entry.getLongValue()), this.cluster.getActionSource());
        }

        this.isComplete = true;
        this.myLastLink = null;
        this.tasks.clear();

        final List<IAEStack<?>> items = new ArrayList<>(this.waitingFor.size());
        for (final var entry : this.waitingFor) {
            items.add(entry.getKey().toIAEStack(-entry.getLongValue()));
        }

        this.waitingFor.reset();

        for (final IAEStack<?> is : items) {
            cluster.postCraftingStatusChange(is);
        }

        notifyRequester(true);
        this.requestingPlayerUUID = null;
        this.finalOutput = null;
        cluster.updateOutput(null);

        this.storeItems();
    }

    public void switchCrafting() {
        this.pause = !pause;
    }

    public void trackCrafting() {
        EntityPlayer player = AppEng.proxy.getPlayerByUUID(this.requestingPlayerUUID);
        appeng.api.util.AETrack.trackCrafting(player, LatestMedium);
    }

    // ============================================================
    // Update crafting logic (main tick)
    // ============================================================

    public void updateCraftingLogic(final IGrid grid, final IEnergyGrid eg, final CraftingGridCache cc) {
        if (!cluster.isActive()) {
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
        if (this.waiting || this.tasks.isEmpty()) {
            return;
        }

        this.remainingOperations = cluster.getAccelerator() + 1 - (this.usedOps[0] + this.usedOps[1] + this.usedOps[2]);
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

    // ============================================================
    // Execute crafting
    // ============================================================

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

            MEInventoryCrafting ic = null;
            boolean found = false;

            for (int times = 0; times < BATCH_SIZE && e.getValue().value > 0; times++) {
                if (this.remainingOperations <= 0) {
                    break;
                }

                if (this.canCraft(details, details.getCondensedInputStacks())) {
                    ic = null;

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
                                final GenericStack[] input = details.getInputStacks();
                                double sum = 0;

                                for (final GenericStack anInput : input) {
                                    if (anInput != null) {
                                        sum += anInput.amount();
                                    }
                                }

                                if (eg.extractAEPower(sum, Actionable.MODULATE, PowerMultiplier.CONFIG) < sum - 0.01) {
                                    continue;
                                }
                                if (details.isCraftable()) {
                                    ic = new MEInventoryCrafting(new ContainerNull(), 3, 3);
                                } else {
                                    ic = new MEInventoryCrafting(new ContainerNull(),
                                            PatternHelper.PROCESSING_INPUT_WIDTH,
                                            PatternHelper.PROCESSING_INPUT_HEIGHT);
                                }

                                for (int x = 0; x < input.length; x++) {
                                    if (input[x] != null) {
                                        found = false;

                                        if (details.isCraftable()) {
                                            final List<GenericStack> itemList;

                                            if (details.canSubstitute()) {
                                                final List<GenericStack> substitutes = details.getSubstituteInputs(x);
                                                itemList = new ArrayList<>(substitutes.size());

                                                for (GenericStack stack : substitutes) {
                                                    itemList.addAll(
                                                            this.inventory.findFuzzyAny(stack, FuzzyMode.IGNORE_ALL));
                                                }
                                            } else {
                                                itemList = new ArrayList<>(1);

                                                long amount = this.inventory.getAvailableKeyCounter()
                                                        .get(input[x].what());
                                                if (amount > 0) {
                                                    itemList.add(new GenericStack(input[x].what(), amount));
                                                } else if (((AEItemKey) input[x].what()).toStack().getItem().isDamageable()
                                                        || Platform
                                                                .isGTDamageableItem(((AEItemKey) input[x].what()).toStack().getItem())) {
                                                    itemList.addAll(this.inventory.findFuzzyAny(
                                                            input[x], FuzzyMode.IGNORE_ALL));
                                                }
                                            }

                                            for (GenericStack fuzz : itemList) {
                                                if (details.isValidItemForSlot(x,
                                                        ((AEItemKey) fuzz.what()).toStack((int) input[x].amount()),
                                                        cluster.getWorld())) {
                                                    final GenericStack extracted = this.inventory.extractItems(
                                                            new GenericStack(fuzz.what(), input[x].amount()),
                                                            Actionable.MODULATE, this.cluster.getActionSource());

                                                    if (extracted != null && extracted.amount() > 0
                                                            && extracted.what() instanceof AEItemKey itemKey) {
                                                        final ItemStack is = itemKey
                                                                .toStack((int) Math.min(extracted.amount(), Integer.MAX_VALUE));

                                                        if (!is.isEmpty()) {
                                                            cluster.postChange(
                                                                    AEItemStack.fromItemStack(is),
                                                                    this.cluster.getActionSource());
                                                            ic.setInventorySlotContents(x, is);
                                                            found = true;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            final GenericStack ais = this.inventory.extractAny(input[x],
                                                    Actionable.MODULATE);

                                            if (ais != null && ais.amount() > 0) {
                                                cluster.postChange(ais, this.cluster.getActionSource());
                                                ic.setInventorySlotContents(x, input[x].toIAEStack());
                                                if (ais.amount() >= input[x].amount()) {
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
                                    this.returnItems(ic);
                                    ic = null;
                                    break;
                                }
                            }

                            if (m.pushPattern(details, ic)) {
                                if (m != LatestMedium)
                                    LatestMedium = m;
                                this.somethingChanged = true;
                                this.remainingOperations--;

                                for (final GenericStack out : details.getCondensedOutputStacks()) {
                                    cluster.postChange(out, this.cluster.getActionSource());
                                    this.waitingFor.add(out.what(), out.amount());
                                    cluster.postCraftingStatusChange(out);
                                }

                                if (details.isCraftable()) {
                                    for (int x = 0; x < ic.getSizeInventory(); x++) {
                                        final ItemStack output = Platform.getContainerItem(ic.getStackInSlot(x));
                                        if (!output.isEmpty()) {
                                            final IAEItemStack cItem = AEItemStack.fromItemStack(output);
                                            cluster.postChange(cItem, this.cluster.getActionSource());
                                            this.waitingFor.add(cItem.toAEKey(), cItem.getStackSize());
                                            cluster.postCraftingStatusChange(cItem);
                                        }
                                    }
                                }

                                ic = null;
                                this.markDirty();

                                e.getValue().value--;

                                if (m instanceof IMultiplePatternPushable && !details.isCraftable()
                                        && e.getValue().value > 0) {
                                    this.executeBatchPush(eg, (IMultiplePatternPushable) m, details, e);
                                }

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
                        this.returnItems(ic);
                    }
                }
            }
        }
    }

    // ============================================================
    // Batch push
    // ============================================================

    private int executeBatchPush(final IEnergyGrid eg, final IMultiplePatternPushable medium,
                                  final ICraftingPatternDetails details,
                                  final Entry<ICraftingPatternDetails, TaskProgress> e) {
        final GenericStack[] input = details.getInputStacks();

        long[] available = new long[input.length];
        GenericStack[] extracted = new GenericStack[input.length];
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null && input[x].amount() > 0) {
                GenericStack toExtract = new GenericStack(input[x].what(), Long.MAX_VALUE);
                final GenericStack ais = this.inventory.extractAny(toExtract, Actionable.MODULATE);
                if (ais != null) {
                    available[x] = ais.amount();
                    extracted[x] = ais;
                    cluster.postChange(ais, this.cluster.getActionSource());
                }
            }
        }

        long maxByInventory = Long.MAX_VALUE;
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null && input[x].amount() > 0) {
                long perRecipe = input[x].amount();
                long canDo = available[x] / perRecipe;
                if (canDo < maxByInventory) {
                    maxByInventory = canDo;
                }
            }
        }

        int maxTodo = (int) Math.min(maxByInventory, e.getValue().value);
        if (maxTodo <= 0) {
            for (int x = 0; x < input.length; x++) {
                if (extracted[x] != null) {
                    this.inventory.injectItems(extracted[x], Actionable.MODULATE);
                }
            }
            return 0;
        }

        MEInventoryCrafting ic = new MEInventoryCrafting(new appeng.container.ContainerNull(),
                appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH,
                appeng.helpers.PatternHelper.PROCESSING_INPUT_HEIGHT);
        for (int x = 0; x < input.length; x++) {
            if (input[x] != null) {
                ic.setInventorySlotContents(x, input[x].toIAEStack());
            }
        }

        int[] result = medium.pushPatternMulti(details, ic, maxTodo);
        int pushed = (result != null && result.length > 0) ? result[0] : 0;

        if (pushed > 0) {
            for (final GenericStack out : details.getCondensedOutputStacks()) {
                cluster.postChange(new GenericStack(out.what(), pushed * out.amount()), this.cluster.getActionSource());
                this.waitingFor.add(out.what(), pushed * out.amount());
                cluster.postCraftingStatusChange(new GenericStack(out.what(), pushed * out.amount()));
            }
            e.getValue().value -= pushed;
            this.somethingChanged = true;
            this.markDirty();
        }

        for (int x = 0; x < input.length; x++) {
            if (extracted[x] != null && input[x] != null) {
                long used = (long) pushed * input[x].amount();
                long remaining = available[x] - used;
                if (remaining > 0) {
                    this.inventory.injectItems(
                            new GenericStack(input[x].what(), remaining), Actionable.MODULATE);
                }
            }
        }

        return pushed;
    }

    // ============================================================
    // returnItems
    // ============================================================

    private void returnItems(MEInventoryCrafting ic) {
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            final IAEStack<?> aeStack = ic.getAEStackInSlot(x);
            if (aeStack != null) {
                this.inventory.injectItems(new GenericStack(aeStack.toAEKey(), aeStack.getStackSize()),
                        Actionable.MODULATE);
            } else {
                final ItemStack is = ic.getStackInSlot(x);
                if (!is.isEmpty()) {
                    this.inventory.injectItems(GenericStack.fromItemStack(is),
                            Actionable.MODULATE, this.cluster.getActionSource());
                }
            }
        }
    }

    // ============================================================
    // storeItems
    // ============================================================

    private void storeItems() {
        Preconditions.checkState(isComplete, "CPU should be complete to prevent re-insertion when dumping items");
        final IGrid g = cluster.getGrid();

        if (g == null) {
            return;
        }

        final IStorageGrid sg = g.getCache(IStorageGrid.class);

        KeyCounter kc = this.inventory.getAvailableKeyCounter();
        for (var entry : kc) {
            final AEKeyType keyType = entry.getKey().getType();
            final IMEMonitor monitor = sg.getInventory(keyType);
            if (monitor == null) continue;

            GenericStack extracted = this.inventory.extractItems(
                    new GenericStack(entry.getKey(), entry.getLongValue()), Actionable.MODULATE,
                    this.cluster.getActionSource());

            if (extracted != null) {
                cluster.postChange(extracted, this.cluster.getActionSource());
                GenericStack gs = monitor.injectItems(extracted, Actionable.MODULATE, this.cluster.getActionSource());
                if (gs != null) {
                    this.inventory.injectItems(gs, Actionable.MODULATE);
                }
            }
        }

        if (this.inventory.isEmpty()) {
            this.inventory = new MECraftingInventory();
        }

        this.markDirty();
    }

    // ============================================================
    // submitJob
    // ============================================================

    public ICraftingLink submitJob(final IGrid g, final ICraftingJob job, final IActionSource src,
                                    final ICraftingRequester requestingMachine) {
        if (!this.tasks.isEmpty() || !this.waitingFor.isEmpty()) {
            return null;
        }

        if (this.isBusy() || !cluster.isActive() || cluster.getAvailableStorage() < job.getByteTotal()) {
            return null;
        }

        final IStorageGrid sg = g.getCache(IStorageGrid.class);
        final MECraftingInventory ci = new MECraftingInventory(sg, true, false, false);

        this.waitingFor.reset();
        if (job instanceof CraftingJobV2) {
            ((CraftingJobV2) job).startCrafting(ci, this.cluster, src);
        } else {
            return null;
        }
        if (ci.commit(src)) {
            this.finalOutput = job.getOutput();
            this.waiting = false;
            this.isComplete = false;

            if (src instanceof PlayerSource playerSource && playerSource.player().isPresent()) {
                this.requestingPlayerUUID = playerSource.player().get().getUniqueID();
            } else {
                this.requestingPlayerUUID = null;
            }

            this.markDirty();

            cluster.updateOutput(this.finalOutput);
            final String craftID = this.generateCraftingID();

            this.myLastLink = new CraftingLink(this.generateLinkData(craftID, requestingMachine == null, false),
                    this.cluster);

            this.prepareElapsedTime();

            if (requestingMachine == null) {
                return this.myLastLink;
            }

            final ICraftingLink whatLink = new CraftingLink(this.generateLinkData(craftID, false, true),
                    requestingMachine);

            this.submitLink(this.myLastLink);
            this.submitLink(whatLink);

            final KeyCounter list = new KeyCounter();
            this.getGenericListOfItem(list, CraftingItemList.ALL);
            for (final var entry : list) {
                cluster.postChange(entry.getKey().toIAEStack(entry.getLongValue()), this.cluster.getActionSource());
            }

            return whatLink;
        } else {
            this.tasks.clear();
            this.inventory.resetStatus();
        }

        return null;
    }

    // ============================================================
    // getGenericListOfItem
    // ============================================================

    public void getGenericListOfItem(final IItemList<IAEStackBase> list, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE:
                for (final var entry : this.waitingFor) {
                    list.add(entry.getKey().toIAEStack(entry.getLongValue()));
                }
                break;
            case PENDING:
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (GenericStack ais : t.getKey().getCondensedOutputStacks()) {
                        var copy = ais.toIAEStack();
                        copy.setStackSize(copy.getStackSize() * t.getValue().value);
                        list.add(copy);
                    }
                }
                break;
            case STORAGE:
                for (var entry : this.inventory.getAvailableKeyCounter()) {
                    list.add(entry.getKey().toIAEStack(entry.getLongValue()));
                }
                break;
            default:
            case ALL:
                for (var entry : this.inventory.getAvailableKeyCounter()) {
                    list.add(entry.getKey().toIAEStack(entry.getLongValue()));
                }
                for (final var entry : this.waitingFor) {
                    list.add(entry.getKey().toIAEStack(entry.getLongValue()));
                }
                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (GenericStack ais : t.getKey().getCondensedOutputStacks()) {
                        var copy = ais.toIAEStack();
                        copy.setStackSize(copy.getStackSize() * t.getValue().value);
                        list.add(copy);
                    }
                }
                break;
        }
    }

    public void getGenericListOfItem(final KeyCounter out, final CraftingItemList whichList) {
        switch (whichList) {
            case ACTIVE:
                for (final var entry : this.waitingFor) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                break;
            case PENDING:
                for (final var t : this.tasks.entrySet()) {
                    for (GenericStack ais : t.getKey().getCondensedOutputStacks()) {
                        out.add(ais.what(), ais.amount() * t.getValue().value);
                    }
                }
                break;
            case STORAGE: {
                for (var entry : this.inventory.getAvailableKeyCounter()) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                break;
            }
            default:
            case ALL: {
                for (var entry : this.inventory.getAvailableKeyCounter()) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                for (final var entry : this.waitingFor) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
                for (final var t : this.tasks.entrySet()) {
                    for (GenericStack ais : t.getKey().getCondensedOutputStacks()) {
                        out.add(ais.what(), ais.amount() * t.getValue().value);
                    }
                }
                break;
            }
        }
    }

    // ============================================================
    // addStorage / addEmitable / addCrafting
    // ============================================================

    public void addStorage(final GenericStack stack) {
        this.inventory.injectItems(stack, Actionable.MODULATE);
    }

    public void addEmitable(final GenericStack stack) {
        this.waitingFor.add(stack.what(), stack.amount());
        cluster.postCraftingStatusChange(stack);
    }

    public void addCrafting(final ICraftingPatternDetails details, final long crafts) {
        TaskProgress i = this.tasks.get(details);
        if (i == null) {
            this.tasks.put(details, i = new TaskProgress());
        }
        i.value += crafts;
    }

    // ============================================================
    // getItemStack
    // ============================================================

    public IAEStack<?> getItemStack(final IAEStack<?> what, final CraftingItemList storage2) {
        IAEStack<?> is = null;

        switch (storage2) {
            case STORAGE: {
                long amount = this.inventory.getAvailableKeyCounter().get(what.toAEKey());
                if (amount > 0) {
                    is = what.toAEKey().toIAEStack(amount);
                }
                break;
            }
            case ACTIVE: {
                final long count = this.waitingFor.get(what.toAEKey());
                if (count > 0) {
                    is = what.copy();
                    is.setStackSize(count);
                }
                break;
            }
            case PENDING:
                is = what.copy();
                is.setStackSize(0);

                for (final Entry<ICraftingPatternDetails, TaskProgress> t : this.tasks.entrySet()) {
                    for (final GenericStack ais : t.getKey().getCondensedOutputStacks()) {
                        if (ais.what().equals(is.toAEKey())) {
                            is.setStackSize(is.getStackSize() + ais.amount() * t.getValue().value);
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

    // ============================================================
    // NBT serialization
    // ============================================================

    public void writeToNBT(final NBTTagCompound data) {
        data.setTag("finalOutput", GenericStack.writeTag(this.finalOutput));
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

    private NBTTagList writeList(final KeyCounter myList) {
        return appeng.util.AEStackSerialization.writeKeyCounterNBT(myList);
    }

    public void readFromNBT(final NBTTagCompound data) {
        final NBTTagCompound outputTag = (NBTTagCompound) data.getTag("finalOutput");
        if (outputTag != null) {
            var readOutput = GenericStack.readTag(outputTag);
            if (readOutput != null) {
                this.finalOutput = readOutput;
            }
        }

        final NBTTagList invTag = (NBTTagList) data.getTag("inventory");
        if (invTag != null) {
            this.inventory.readInventory(invTag);
        }

        this.waiting = data.getBoolean("waiting");
        this.isComplete = data.getBoolean("isComplete");
        this.pause = data.getBoolean("pause");

        if (data.hasKey("link")) {
            final NBTTagCompound link = data.getCompoundTag("link");
            this.myLastLink = new CraftingLink(link, this.cluster);
            this.submitLink(this.myLastLink);
        }

        final NBTTagList list = data.getTagList("tasks", 10);
        for (int x = 0; x < list.tagCount(); x++) {
            final NBTTagCompound item = list.getCompoundTagAt(x);
            final IAEItemStack pattern = AEItemStack.fromNBT(item);
            if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem cpi) {
                final ICraftingPatternDetails details = cpi.getPatternForItemWithNest(pattern.createItemStack(),
                        cluster.getWorld());
                if (details != null) {
                    final TaskProgress tp = new TaskProgress();
                    tp.value = item.getLong("craftingProgress");
                    this.tasks.put(details, tp);
                }
            }
        }

        this.waitingFor = this.readList((NBTTagList) data.getTag("waitingFor"));
        for (final var entry : this.waitingFor) {
            cluster.postCraftingStatusChange(entry.getKey().toIAEStack(entry.getLongValue()));
        }

        this.lastTime = System.nanoTime();
        this.elapsedTime = data.getLong("elapsedTime");
        this.startItemCount = data.getLong("startItemCount");
        this.remainingItemCount = data.getLong("remainingItemCount");

        if (Platform.isServer() && data.hasUniqueId("requestingPlayerUUID")) {
            this.requestingPlayerUUID = data.getUniqueId("requestingPlayerUUID");
        }
    }

    private KeyCounter readList(final NBTTagList tag) {
        final KeyCounter out = new KeyCounter();
        if (tag == null) {
            return out;
        }
        appeng.util.AEStackSerialization.readKeyCounterNBT(out, tag);
        return out;
    }

    // ============================================================
    // Elapsed time tracking
    // ============================================================

    private void prepareElapsedTime() {
        this.lastTime = System.nanoTime();
        this.elapsedTime = 0;

        final KeyCounter bridge = new KeyCounter();
        this.getGenericListOfItem(bridge, CraftingItemList.ACTIVE);
        this.getGenericListOfItem(bridge, CraftingItemList.PENDING);

        long itemCount = 0;
        for (final var entry : bridge) {
            itemCount += entry.getLongValue();
        }

        this.startItemCount = itemCount;
        this.remainingItemCount = itemCount;
    }

    private void updateRemainingItemCount(final AEKey what, final long amount) {
        this.remainingItemCount = this.getRemainingItemCount() - amount;
    }

    private void updateElapsedTime() {
        final long nextStartTime = System.nanoTime();
        this.elapsedTime = this.getElapsedTime() + nextStartTime - this.lastTime;
        this.lastTime = nextStartTime;
    }

    // ============================================================
    // Crafting ID / Link helpers
    // ============================================================

    private String generateCraftingID() {
        final long now = System.currentTimeMillis();
        final int hash = System.identityHashCode(this);
        final int hmm = this.finalOutput == null ? 0 : this.finalOutput.what().hashCode();

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
        final IGrid grid = cluster.getGrid();
        if (grid != null) {
            final CraftingGridCache cc = grid.getCache(ICraftingGrid.class);
            cc.addLink((CraftingLink) myLastLink2);
        }
    }

    // ============================================================
    // Inner class
    // ============================================================

    private static class TaskProgress {
        private long value;
    }
}

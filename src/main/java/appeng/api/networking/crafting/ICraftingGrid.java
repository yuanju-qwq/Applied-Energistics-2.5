/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking.crafting;

import java.util.concurrent.Future;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridCache;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;

public interface ICraftingGrid extends IGridCache {

    /**
     * Get the set of crafting patterns for the specified stack (item/fluid etc.).
     *
     * @param whatToCraft the requested crafting target
     * @param details     pattern details
     * @param slot        slot index
     * @param world       crafting world
     * @return the corresponding set of crafting patterns
     */
    ImmutableCollection<ICraftingPatternDetails> getCraftingFor(IAEStack<?> whatToCraft,
            ICraftingPatternDetails details, int slot, World world);

    /**
     * @deprecated Use {@link #getCraftingFor(IAEStack, ICraftingPatternDetails, int, World)} instead
     */
    @Deprecated
    default ImmutableCollection<ICraftingPatternDetails> getCraftingFor(IAEItemStack whatToCraft,
            ICraftingPatternDetails details, int slot, World world) {
        return getCraftingFor((IAEStack<?>) whatToCraft, details, slot, world);
    }

    /**
     * Get the multi-type pattern mapping of all craftable items/fluids.
     */
    ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns();

    /**
     * Begin calculating a crafting job.
     *
     * @param world     crafting world
     * @param grid      network
     * @param actionSrc source
     * @param craftWhat result
     * @param callback  callback -- optional
     *
     * @return a future which will at an undetermined point in the future get you the {@link ICraftingJob} do not wait
     *         on this, your be waiting forever.
     */
    Future<ICraftingJob> beginCraftingJob(World world, IGrid grid, IActionSource actionSrc, IAEStack<?> craftWhat,
            ICraftingCallback callback);

    /**
     * @deprecated Use {@link #beginCraftingJob(World, IGrid, IActionSource, IAEStack, ICraftingCallback)} instead
     */
    @Deprecated
    default Future<ICraftingJob> beginCraftingJob(World world, IGrid grid, IActionSource actionSrc, IAEItemStack craftWhat,
            ICraftingCallback callback) {
        return beginCraftingJob(world, grid, actionSrc, (IAEStack<?>) craftWhat, callback);
    }

    /**
     * Submit the job to the Crafting system for processing.
     *
     * @param job               - the crafting job from beginCraftingJob
     * @param requestingMachine - a machine if its being requested via automation, may be null.
     * @param target            - can be null
     * @param prioritizePower   - if cpu is null, this determine if the system should prioritize power, or if it should
     *                          find the lower end cpus, automatic processes generally should pick lower end cpus.
     * @param src               - the action source to use when starting the job, this will be used for extracting
     *                          items, should usually be the same as the one provided to beginCraftingJob.
     *
     * @return null ( if failed ) or an {@link ICraftingLink} other wise, if you send requestingMachine you need to
     *         properly keep track of this and handle the nbt saving and loading of the object as well as the
     *         {@link ICraftingRequester} methods. if you send null, this object should be discarded after verifying the
     *         return state.
     */
    @SuppressWarnings("unchecked")
    ICraftingLink submitJob(ICraftingJob job, ICraftingRequester requestingMachine, ICraftingCPU target,
            boolean prioritizePower, IActionSource src);

    /**
     * @return list of all the crafting cpus on the grid
     */
    ImmutableSet<ICraftingCPU> getCpus();

    /**
     * Check whether the specified stack (item/fluid etc.) can be requested via crafting emitter.
     *
     * @param what the stack to check
     * @return true if it can be emitted
     */
    boolean canEmitFor(IAEStack<?> what);

    /**
     * @deprecated Use {@link #canEmitFor(IAEStack)} instead
     */
    @Deprecated
    default boolean canEmitFor(IAEItemStack what) {
        return canEmitFor((IAEStack<?>) what);
    }

    /**
     * Check whether the specified stack (item/fluid etc.) is currently being crafted.
     *
     * @param what the stack to check
     * @return true if it is being crafted
     */
    boolean isRequesting(IAEStack<?> what);

    /**
     * @deprecated Use {@link #isRequesting(IAEStack)} instead
     */
    @Deprecated
    default boolean isRequesting(IAEItemStack what) {
        return isRequesting((IAEStack<?>) what);
    }

    /**
     * Get the total amount of the specified stack being requested by all Crafting CPUs on the grid.
     *
     * @param what the stack to query, stackSize is ignored
     * @return total amount being requested
     */
    long requesting(IAEStack<?> what);

    /**
     * @deprecated Use {@link #requesting(IAEStack)} instead
     */
    @Deprecated
    default long requesting(IAEItemStack what) {
        return requesting((IAEStack<?>) what);
    }
}

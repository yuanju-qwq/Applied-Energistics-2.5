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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;

public interface ICraftingGrid extends IGridCache {

    /**
     * Get the set of crafting patterns for the specified stack (item/fluid etc.).
     */
    ImmutableCollection<ICraftingPatternDetails> getCraftingFor(GenericStack whatToCraft,
            ICraftingPatternDetails details, int slot, World world);

    /**
     * @deprecated Use {@link #getCraftingFor(GenericStack, ICraftingPatternDetails, int, World)} instead.
     */
    @Deprecated
    default ImmutableCollection<ICraftingPatternDetails> getCraftingFor(IAEStack<?> whatToCraft,
            ICraftingPatternDetails details, int slot, World world) {
        var gs = GenericStack.fromIAEStack(whatToCraft);
        if (gs == null) return ImmutableList.of();
        return getCraftingFor(gs, details, slot, world);
    }

    /**
     * Get the multi-type pattern mapping of all craftable items/fluids.
     */
    ImmutableMap<GenericStack, ImmutableList<ICraftingPatternDetails>> getCraftingMultiPatterns();

    /**
     * @return the pattern mapping keyed by {@link IAEStack} (converted from {@link GenericStack} representations).
     * @deprecated Use {@link #getCraftingMultiPatterns()} instead.
     */
    @Deprecated
    default ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> getLegacyCraftingMultiPatterns() {
        var raw = getCraftingMultiPatterns();
        var builder = ImmutableMap.<IAEStack<?>, ImmutableList<ICraftingPatternDetails>>builder();
        for (var entry : raw.entrySet()) {
            var ae = entry.getKey().toIAEStack();
            if (ae != null) {
                builder.put(ae, entry.getValue());
            }
        }
        return builder.build();
    }

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
    Future<ICraftingJob> beginCraftingJob(World world, IGrid grid, IActionSource actionSrc, GenericStack craftWhat,
            ICraftingCallback callback);

    /**
     * @deprecated Use {@link #beginCraftingJob(World, IGrid, IActionSource, GenericStack, ICraftingCallback)} instead.
     */
    @Deprecated
    default Future<ICraftingJob> beginCraftingJob(World world, IGrid grid, IActionSource actionSrc, IAEStack<?> craftWhat,
            ICraftingCallback callback) {
        var gs = GenericStack.fromIAEStack(craftWhat);
        if (gs == null) return null;
        return beginCraftingJob(world, grid, actionSrc, gs, callback);
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
    boolean canEmitFor(AEKey what);

    /**
     * @deprecated Use {@link #canEmitFor(AEKey)} instead.
     */
    @Deprecated
    default boolean canEmitFor(IAEStack<?> what) {
        var key = what.toAEKey();
        return key != null && canEmitFor(key);
    }

    /**
     * Check whether the specified stack (item/fluid etc.) is currently being crafted.
     *
     * @param what the stack to check
     * @return true if it is being crafted
     */
    boolean isRequesting(AEKey what);

    /**
     * @deprecated Use {@link #isRequesting(AEKey)} instead.
     */
    @Deprecated
    default boolean isRequesting(IAEStack<?> what) {
        var key = what.toAEKey();
        return key != null && isRequesting(key);
    }

    /**
     * Get the total amount of the specified stack being requested by all Crafting CPUs on the grid.
     *
     * @param what the stack to query, stackSize is ignored
     * @return total amount being requested
     */
    long requesting(AEKey what);

    /**
     * @deprecated Use {@link #requesting(AEKey)} instead.
     */
    @Deprecated
    default long requesting(IAEStack<?> what) {
        var key = what.toAEKey();
        if (key == null) return 0;
        return requesting(key);
    }
}

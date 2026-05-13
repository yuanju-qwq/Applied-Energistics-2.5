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

import appeng.api.config.CraftingMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;

/**
 * Crafting job interface. v2 version supports generic stack types (item/fluid etc.).
 *
 * @param <StackType> the output stack type of this crafting job
 */
public interface ICraftingJob<StackType extends IAEStack> {

    /**
     * @return if this job is a simulation, simulations cannot be submitted and only represent 1 possible future
     *         crafting job with fake items.
     */
    boolean isSimulation();

    /**
     * @return total number of bytes to process this job.
     */
    long getByteTotal();

    /**
     * Populates the plan list with stack size, and requestable values that represent the stored, and crafting job
     * contents respectively.
     *
     * @param plan plan
     */
    void populatePlan(IItemList<IAEStackBase> plan);

    /**
     * @return the final output of the job.
     */
    StackType getOutput();

    /**
     * returns true if this needs more simulation.
     *
     * @param milli milliseconds of simulation
     * @return true if this needs more simulation
     */
    boolean simulateFor(final int milli);

    /**
     * Submit this job to the TickHandler for asynchronous computation.
     */
    Future<ICraftingJob<StackType>> schedule();

    /**
     * @return whether this job can run on the given cluster
     */
    default boolean supportsCPUCluster(final ICraftingCPU cluster) {
        return false;
    }

    /**
     * @return the crafting mode used by this job
     */
    default CraftingMode getCraftingMode() {
        return CraftingMode.STANDARD;
    }

    /**
     * Begin executing crafting on the CPU cluster.
     */
    default void startCrafting(final MECraftingInventory storage, final ICraftingCPU craftingCPUCluster,
            final IActionSource src) {}

    /**
     * Return the snapshot of the storage when crafting calculation begins, should be read-only, do not modify.
     */
    default MECraftingInventory getStorageAtBeginning() {
        return new MECraftingInventory();
    }

    /**
     * Get the total number of crafts for the specified output material (used for Crafting confirm GUI display).
     * Default implementation returns 0; overridden by concrete crafting job implementations (e.g. CraftingJob).
     *
     * @param material output material
     * @return number of crafts
     */
    default long getTotalCraftsForPrimaryOutput(IAEStack<?> material) {
        return 0;
    }
}

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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;

/**
 * Crafting job interface that supports generic stack types (item/fluid etc.).
 */
public interface ICraftingJob {

    boolean isSimulation();

    long getByteTotal();

    void populatePlan(IItemList<IAEStackBase> plan);

    GenericStack getOutput();

    boolean simulateFor(final int milli);

    Future<ICraftingJob> schedule();

    default boolean supportsCPUCluster(final ICraftingCPU cluster) {
        return false;
    }

    default CraftingMode getCraftingMode() {
        return CraftingMode.STANDARD;
    }

    default void startCrafting(final MECraftingInventory storage, final ICraftingCPU craftingCPUCluster,
            final IActionSource src) {}

    default MECraftingInventory getStorageAtBeginning() {
        return new MECraftingInventory();
    }

    /**
     * @deprecated Use {@link #getTotalCraftsForPrimaryOutput(AEKey)} instead.
     */
    @Deprecated
    default long getTotalCraftsForPrimaryOutput(IAEStack<?> material) {
        return 0;
    }

    default long getTotalCraftsForPrimaryOutput(AEKey material) {
        var output = getOutput();
        return output != null && output.what().equals(material) ? output.amount() : 0;
    }
}

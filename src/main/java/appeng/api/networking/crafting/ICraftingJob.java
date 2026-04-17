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
 * 合成任务接口。v2 版本支持泛型栈类型（物品/流体等）。
 *
 * @param <StackType> 该合成任务的输出栈类型
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
     * 将此任务提交到 TickHandler 进行异步计算。
     */
    Future<ICraftingJob<StackType>> schedule();

    /**
     * @return whether this job can run on the given cluster
     */
    default boolean supportsCPUCluster(final ICraftingCPU cluster) {
        return false;
    }

    /**
     * @return 此任务使用的合成模式
     */
    default CraftingMode getCraftingMode() {
        return CraftingMode.STANDARD;
    }

    /**
     * 在 CPU 集群上开始执行合成。
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
     * 获取指定产出物料的总合成次数（用于合成确认 GUI 显示）。
     * 默认实现返回 0；由具体合成任务实现（如 CraftingJob）覆盖。
     *
     * @param material 产出物料
     * @return 合成次数
     */
    default long getTotalCraftsForPrimaryOutput(IAEStack<?> material) {
        return 0;
    }
}

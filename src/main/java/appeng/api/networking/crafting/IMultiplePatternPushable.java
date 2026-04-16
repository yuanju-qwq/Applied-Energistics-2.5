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

import net.minecraft.inventory.InventoryCrafting;

/**
 * 扩展 {@link ICraftingMedium}，支持一次推送多份相同样板的材料。
 * 移植自 Programmable-Hatches-Mod 的 IMultiplePatternPushable 接口。
 *
 * <p>当合成 CPU 检测到 medium 实现了此接口时，会一次性从 AE 仓库中
 * 提取多份材料并调用 {@link #pushPatternMulti}，大幅减少交互次数。</p>
 */
public interface IMultiplePatternPushable extends ICraftingMedium {

    /**
     * 一次性推送多份相同样板的材料到 medium 中。
     *
     * @param patternDetails 合成样板详情
     * @param table          单份材料的 InventoryCrafting（与 pushPattern 相同）
     * @param maxTodo        最大允许推送的份数（由 AE 仓库库存和任务剩余数共同决定）
     * @return 实际推送的份数数组：
     *         length==1 时 [0] 为实际推送并消耗的份数；
     *         length==2 时 [0] 为推送份数，[1] 为消耗份数
     */
    int[] pushPatternMulti(ICraftingPatternDetails patternDetails, InventoryCrafting table, int maxTodo);
}

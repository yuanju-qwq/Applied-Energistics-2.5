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


import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;


public interface ICraftingPatternDetails {

    /**
     * @return 编码了此配方的模式物品
     */
    ItemStack getPattern();

    /**
     * 获取原始输入（物品类型），包含 null 占位以保持槽位位置一致。
     */
    IAEItemStack[] getInputs();

    /**
     * 获取精简后的输入（物品类型），合并相同物品，不含 null。
     */
    IAEItemStack[] getCondensedInputs();

    /**
     * 获取精简后的输出（物品类型），合并相同物品，不含 null。
     */
    IAEItemStack[] getCondensedOutputs();

    /**
     * 获取原始输出（物品类型）。
     */
    IAEItemStack[] getOutputs();

    /**
     * 获取主要输出（第一个输出）。
     * Pattern 只会被用来合成主要输出，其他是副产品。
     */
    default IAEItemStack getPrimaryOutput() {
        return getOutputs()[0];
    }

    /**
     * 获取泛型主要输出（支持物品+流体）。
     * 默认返回与 {@link #getPrimaryOutput()} 相同。
     */
    default IAEStack<?> getGenericPrimaryOutput() {
        return getPrimaryOutput();
    }

    /**
     * 获取泛型精简输入（支持物品+流体等多种类型）。
     * <p>
     * 默认实现返回与 {@link #getCondensedInputs()} 相同的内容。
     * 支持流体合成的 Pattern 应覆写此方法。
     */
    default IAEStack<?>[] getGenericCondensedInputs() {
        IAEItemStack[] items = getCondensedInputs();
        IAEStack<?>[] result = new IAEStack<?>[items.length];
        System.arraycopy(items, 0, result, 0, items.length);
        return result;
    }

    /**
     * 获取泛型精简输出（支持物品+流体等多种类型）。
     * <p>
     * 默认实现返回与 {@link #getCondensedOutputs()} 相同的内容。
     * 支持流体合成的 Pattern 应覆写此方法。
     */
    default IAEStack<?>[] getGenericCondensedOutputs() {
        IAEItemStack[] items = getCondensedOutputs();
        IAEStack<?>[] result = new IAEStack<?>[items.length];
        System.arraycopy(items, 0, result, 0, items.length);
        return result;
    }

    /**
     * 获取泛型原始输入（支持物品+流体等多种类型）。
     */
    default IAEStack<?>[] getGenericInputs() {
        IAEItemStack[] items = getInputs();
        IAEStack<?>[] result = new IAEStack<?>[items.length];
        System.arraycopy(items, 0, result, 0, items.length);
        return result;
    }

    /**
     * 获取泛型原始输出（支持物品+流体等多种类型）。
     */
    default IAEStack<?>[] getGenericOutputs() {
        IAEItemStack[] items = getOutputs();
        IAEStack<?>[] result = new IAEStack<?>[items.length];
        System.arraycopy(items, 0, result, 0, items.length);
        return result;
    }

    /**
     * @return 是否为合成台配方（true）还是处理配方（false）
     */
    boolean isCraftable();

    /**
     * 检查指定槽位的物品是否可以作为该配方的有效输入。
     * 只适用于合成台配方（isCraftable() == true）。
     */
    boolean isValidItemForSlot(int slotIndex, ItemStack i, World w);

    /**
     * 是否允许使用替代材料。
     */
    boolean canSubstitute();

    /**
     * @return 此配方的输出是否可以作为其他配方的替代输入
     */
    default boolean canBeSubstitute() {
        return canSubstitute();
    }

    /**
     * 获取指定槽位允许的替代输入列表。
     */
    List<IAEItemStack> getSubstituteInputs(int slot);

    /**
     * 获取合成台配方的输出结果。
     * 只适用于合成台配方（isCraftable() == true）。
     */
    ItemStack getOutput(InventoryCrafting craftingInv, World w);

    /**
     * @return 配方优先级
     */
    int getPriority();

    /**
     * 设置配方优先级。
     */
    void setPriority(int priority);
}

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
     * 检查指定槽位的物品是否可以作为该配方的有效输入。
     * 只适用于合成台配方（isCraftable() == true）。
     *
     * @param slotIndex 槽位索引
     * @param itemStack 物品栈
     * @param world     合成世界
     * @return 是否可用
     */
    boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world);

    /**
     * 泛型版本：检查指定槽位的栈（物品/流体等）是否可以作为该配方的有效输入。
     * 默认委托到 ItemStack 版本。
     */
    default boolean isValidItemForSlot(int slotIndex, IAEStack<?> aes, World world) {
        if (aes instanceof IAEItemStack) {
            return isValidItemForSlot(slotIndex, ((IAEItemStack) aes).getItemStack(), world);
        }
        return false;
    }

    /**
     * @return 是否为合成台配方（true）还是处理配方（false）
     */
    boolean isCraftable();

    // ========== 泛型主入口方法（支持物品+流体等多种类型） ==========

    /**
     * 获取原始输入（支持物品+流体等多种类型），包含 null 占位以保持槽位位置一致。
     * 默认委托到 {@link #getInputs()}。
     */
    default IAEStack<?>[] getAEInputs() {
        return getInputs();
    }

    /**
     * 获取精简后的输入（支持物品+流体等多种类型），合并相同栈，不含 null。
     * 默认委托到 {@link #getCondensedInputs()}。
     */
    default IAEStack<?>[] getCondensedAEInputs() {
        return getCondensedInputs();
    }

    /**
     * 获取精简后的输出（支持物品+流体等多种类型），合并相同栈，不含 null。
     * 默认委托到 {@link #getCondensedOutputs()}。
     */
    default IAEStack<?>[] getCondensedAEOutputs() {
        return getCondensedOutputs();
    }

    /**
     * 获取原始输出（支持物品+流体等多种类型）。
     * 默认委托到 {@link #getOutputs()}。
     */
    default IAEStack<?>[] getAEOutputs() {
        return getOutputs();
    }

    // ========== 旧版物品类型方法（已弃用，保留向后兼容） ==========

    /**
     * 获取原始输入（物品类型），包含 null 占位以保持槽位位置一致。
     *
     * @deprecated 使用 {@link #getAEInputs()} 替代
     */
    @Deprecated
    IAEItemStack[] getInputs();

    /**
     * 获取精简后的输入（物品类型），合并相同物品，不含 null。
     *
     * @deprecated 使用 {@link #getCondensedAEInputs()} 替代
     */
    @Deprecated
    IAEItemStack[] getCondensedInputs();

    /**
     * 获取精简后的输出（物品类型），合并相同物品，不含 null。
     *
     * @deprecated 使用 {@link #getCondensedAEOutputs()} 替代
     */
    @Deprecated
    IAEItemStack[] getCondensedOutputs();

    /**
     * 获取原始输出（物品类型）。
     *
     * @deprecated 使用 {@link #getAEOutputs()} 替代
     */
    @Deprecated
    IAEItemStack[] getOutputs();

    /**
     * 是否允许使用替代材料。
     */
    boolean canSubstitute();

    /**
     * @return 此配方的输出是否可以作为其他配方的替代输入
     */
    default boolean canBeSubstitute() {
        return true;
    }

    /**
     * 获取指定槽位允许的替代输入列表。
     */
    List<IAEItemStack> getSubstituteInputs(int slot);

    /**
     * 获取合成台配方的输出结果。
     * 只适用于合成台配方（isCraftable() == true）。
     *
     * @param craftingInv 合成台物品栏
     * @param world       合成世界
     * @return 合成输出物品
     */
    ItemStack getOutput(InventoryCrafting craftingInv, World world);

    /**
     * @return 配方优先级
     */
    int getPriority();

    /**
     * 设置配方优先级。
     *
     * @param priority 优先级值
     */
    void setPriority(int priority);

    /**
     * @return true 如果此 pattern 是仅输入型的，应在解析期间内联。
     */
    default boolean isInputOnly() {
        return false;
    }

    /**
     * @return 仅输入型 pattern 的唯一标识符，如果不适用则返回 null。
     */
    default java.util.UUID getInputOnlyUuid() {
        return null;
    }
}

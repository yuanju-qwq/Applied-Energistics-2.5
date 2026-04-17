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

package appeng.api.storage;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.items.contents.CellConfigLegacy;
import appeng.items.contents.CellConfigLegacyWrapper;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

public interface ICellWorkbenchItem {

    /**
     * if this return false, the item will not be treated as a cell, and cannot be inserted into the work bench.
     *
     * @param is item
     *
     * @return true if the item should be editable in the cell workbench.
     */
    boolean isEditable(ItemStack is);

    /**
     * used to edit the upgrade slots on your cell, should have a capacity of 0-24, you are also responsible for
     * implementing the valid checks, and any storage/usage of them.
     *
     * onInventoryChange will be called when saving is needed.
     */
    IItemHandler getUpgradesInventory(ItemStack is);

    /**
     * Used to extract, or mirror the contents of the work bench onto the cell.
     *
     * - This should have exactly 63 slots, any more, or less might cause issues.
     *
     * onInventoryChange will be called when saving is needed.
     *
     * @deprecated 请使用 {@link #getConfigAEInventory(ItemStack)} 代替。
     */
    @Deprecated
    IItemHandler getConfigInventory(ItemStack is);

    /**
     * 获取泛型版本的配置库存，可以存储物品、流体等任意 AE 栈类型。
     * <p>
     * 默认实现通过 {@link CellConfigLegacyWrapper} 包装旧版 {@link #getConfigInventory(ItemStack)}。
     * 推荐子类直接覆盖此方法以原生支持多类型栈。
     *
     * @param is 单元物品
     * @return 泛型配置库存
     */
    default IAEStackInventory getConfigAEInventory(ItemStack is) {
        return new CellConfigLegacyWrapper(this.getConfigInventory(is));
    }

    /**
     * 获取此单元支持的栈类型。
     * 默认返回物品栈类型。
     *
     * @return 栈类型
     */
    default appeng.api.storage.data.IAEStackType<?> getStackType() {
        return AEItemStackType.INSTANCE;
    }

    /**
     * @return the current fuzzy status.
     */
    FuzzyMode getFuzzyMode(ItemStack is);

    /**
     * sets the setting on the cell.
     */
    void setFuzzyMode(ItemStack is, FuzzyMode fzMode);
}

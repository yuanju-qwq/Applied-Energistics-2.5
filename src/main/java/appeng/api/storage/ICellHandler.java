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

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Registration record for {@link ICellRegistry}
 */
public interface ICellHandler {

    /**
     * return true if the provided item is handled by your cell handler. ( AE May choose to skip this method, and just
     * request a handler )
     *
     * @param is to be checked item
     *
     * @return return true, if getCellHandler will not return null.
     */
    boolean isCell(ItemStack is);

    /**
     * @deprecated 请使用 {@link #getCellInventory(ItemStack, ISaveProvider, IAEStackType)} 代替。
     */
    @Deprecated
    <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider host,
            IStorageChannel<T> channel);

    /**
     * 通过 {@link IAEStackType} 获取 cell 的 inventory handler。
     * <p>
     * 如果无法处理该物品，返回 null。
     *
     * @param is   存储 cell 物品
     * @param host 负责持久化 cell 内容的宿主，可为 null
     * @param type 请求的栈类型
     * @return 新的 IMEHandler，或 null
     */
    default <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider host,
            IAEStackType<T> type) {
        return this.getCellInventory(is, host, type.getStorageChannel());
    }

    /**
     * 0 - cell is missing.
     *
     * 1 - green, ( usually means available room for types or items. )
     *
     * 2 - orange, ( usually means available room for items, but not types. )
     *
     * 3 - red, ( usually means the cell is 100% full )
     *
     * @param is      the cell item. ( use the handler for any details you can )
     * @param handler the handler for the cell is provides for reference, you can cast this to your handler.
     *
     * @return get the status of the cell based on its contents.
     */
    default <T extends IAEStack<T>> int getStatusForCell(ItemStack is, ICellInventoryHandler<T> handler) {
        if (handler.getCellInv() != null) {
            int val = handler.getCellInv().getStatusForCell();

            if (val == 1 && handler.isPreformatted()) {
                val = 2;
            }

            return val;
        }
        return 0;
    }

    /**
     * @return the ae/t to drain for this storage cell inside a chest/drive.
     */
    default <T extends IAEStack<T>> double cellIdleDrain(ItemStack is, ICellInventoryHandler<T> handler) {
        if (handler.getCellInv() != null) {
            return handler.getCellInv().getIdleDrain();
        }
        return 1.0;
    }
}

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

import appeng.api.IAppEngApi;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Storage Cell Registry, used for specially implemented cells, if you just want to make a item act like a cell, or new
 * cell with different bytes, then you should probably consider IStorageCell instead its considerably simpler.
 *
 * Do not Implement, obtained via {@link IAppEngApi}.getCellRegistry()
 */
public interface ICellRegistry {

    /**
     * Register a new handler.
     *
     * Never be call before {@link FMLInitializationEvent} was handled by AE2. Will throw an exception otherwise.
     *
     * @param handler cell handler
     */
    void addCellHandler(@Nonnull ICellHandler handler);

    /**
     * Register a new handler
     * 
     * @param handler cell gui handler
     */
    void addCellGuiHandler(@Nonnull ICellGuiHandler handler);

    /**
     * return true, if you can get a InventoryHandler for the item passed.
     *
     * @param is to be checked item
     *
     * @return true if the provided item, can be handled by a handler in AE, ( AE May choose to skip this and just get
     *         the handler instead. )
     */
    boolean isCellHandled(ItemStack is);

    /**
     * get the handler, for the requested item.
     *
     * @param is to be checked item
     *
     * @return the handler registered for this item type.
     */
    @Nullable
    ICellHandler getHandler(ItemStack is);

    /**
     * @deprecated 请使用 {@link #getGuiHandler(IAEStackType, ItemStack)} 代替。
     */
    @Nullable
    @Deprecated
    <T extends IAEStack<T>> ICellGuiHandler getGuiHandler(IStorageChannel<T> channel, ItemStack is);

    /**
     * 通过 {@link IAEStackType} 获取 GUI handler。
     */
    @Nullable
    default <T extends IAEStack<T>> ICellGuiHandler getGuiHandler(IAEStackType<T> type, ItemStack is) {
        return this.getGuiHandler(type.getStorageChannel(), is);
    }

    /**
     * @deprecated 请使用 {@link #getCellInventory(ItemStack, ISaveProvider, IAEStackType)} 代替。
     */
    @Nullable
    @Deprecated
    <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider host,
            IStorageChannel<T> chan);

    /**
     * 通过 {@link IAEStackType} 获取 cell 的 inventory handler。
     */
    @Nullable
    default <T extends IAEStack<T>> ICellInventoryHandler<T> getCellInventory(ItemStack is, ISaveProvider host,
            IAEStackType<T> type) {
        return this.getCellInventory(is, host, type.getStorageChannel());
    }
}

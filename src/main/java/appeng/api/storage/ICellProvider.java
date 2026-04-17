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

import java.util.List;

import appeng.api.networking.IGridNodeService;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Allows you to provide cells via non IGridHosts directly to the storage system, drives, and similar features should go
 * though {@link ICellContainer} and be automatically handled by the storage system.
 */
public interface ICellProvider extends IGridNodeService {

    /**
     * @deprecated 请使用 {@link #getCellArray(IAEStackType)} 代替。
     */
    @Deprecated
    <T extends IAEStack<T>> List<IMEInventoryHandler<T>> getCellArray(IStorageChannel<T> channel);

    /**
     * 通过 {@link IAEStackType} 获取存储 cell 列表。
     * <p>
     * 必须返回对应类型的正确 handler，不能返回 null。
     *
     * @param type 栈类型
     * @return 有效的 handler 列表，不能为 null
     */
    default <T extends IAEStack<T>> List<IMEInventoryHandler<T>> getCellArray(IAEStackType<T> type) {
        return this.getCellArray(type.getStorageChannel());
    }

    /**
     * the storage's priority.
     *
     * Positive and negative are supported
     */
    int getPriority();
}

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

import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;

/**
 * Monitor view over typed ME storage.
 * <p>
 * New aggregation and result transport code should prefer {@link KeyCounter} and other AEKey-native
 * structures.
 */
public interface IMEMonitor<T extends IAEStackBase> extends IMEInventoryHandler<T>, IBaseMonitor<T> {

    /**
     * Get access to the full item list of the network.
     *
     * @return full storage list.
     */
    IItemList<T> getStorageList();

    /**
     * Get the AEKey-based content of this monitor.
     * <p>
     * Default implementation adapts from {@link #getStorageList()}.
     *
     * @return key counter with all available items (amount per key)
     */
    default KeyCounter getKeyCounter() {
        final KeyCounter result = new KeyCounter();
        final IItemList<T> list = getStorageList();
        for (final T stack : list) {
            final AEKey key = stack.toAEKey();
            if (key != null) {
                result.set(key, stack.getStackSize());
            }
        }
        return result;
    }
}

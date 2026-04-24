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

package appeng.api.storage.data;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

/**
 * Result of a container interaction (drain/fill) operation on an {@link IAEStackType}.
 * <p>
 * Wraps the amount of resource actually transferred and the modified container item
 * after the operation (e.g., a full bucket becomes an empty bucket after draining).
 *
 * @param <T> the concrete stack type
 */
public final class ContainerInteractionResult<T extends IAEStack<T>> {

    private static final ContainerInteractionResult<?> EMPTY =
            new ContainerInteractionResult<>(null, ItemStack.EMPTY);

    @Nullable
    private final T transferred;

    @Nonnull
    private final ItemStack resultContainer;

    private ContainerInteractionResult(@Nullable T transferred, @Nonnull ItemStack resultContainer) {
        this.transferred = transferred;
        this.resultContainer = resultContainer;
    }

    /**
     * Create a successful result.
     *
     * @param transferred     the amount actually transferred (must not be null, stackSize > 0)
     * @param resultContainer the container item after the operation
     */
    @Nonnull
    public static <T extends IAEStack<T>> ContainerInteractionResult<T> of(
            @Nonnull T transferred, @Nonnull ItemStack resultContainer) {
        return new ContainerInteractionResult<>(transferred, resultContainer);
    }

    /**
     * @return an empty/failed result indicating no interaction was possible
     */
    @Nonnull
    @SuppressWarnings("unchecked")
    public static <T extends IAEStack<T>> ContainerInteractionResult<T> empty() {
        return (ContainerInteractionResult<T>) EMPTY;
    }

    /**
     * @return true if the operation transferred any resource
     */
    public boolean isSuccess() {
        return transferred != null && transferred.getStackSize() > 0;
    }

    /**
     * @return the amount of resource transferred, or null if the operation failed
     */
    @Nullable
    public T getTransferred() {
        return transferred;
    }

    /**
     * @return the container item after the operation (e.g., empty bucket).
     *         Returns {@link ItemStack#EMPTY} if the operation failed or the container was consumed.
     */
    @Nonnull
    public ItemStack getResultContainer() {
        return resultContainer;
    }
}

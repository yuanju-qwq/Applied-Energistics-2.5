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

import java.util.Collection;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;

public interface IStorageHelper {

    /**
     * Returns all registered IAEStackType instances.
     */
    @Nonnull
    Collection<IAEStackType<? extends IAEStackBase>> getStackTypes();

    /**
     * load a crafting link from nbt data.
     *
     * @param data to be loaded data
     *
     * @return crafting link
     */
    ICraftingLink loadCraftingLink(NBTTagCompound data, ICraftingRequester req);

    /**
     * Extracts items from a {@link IMEInventory} respecting power requirements.
     * 
     * @param energy  Energy source.
     * @param inv     Inventory to extract from.
     * @param request Requested item and count.
     * @param src     Action source.
     * @param mode    Simulate or modulate
     * @return extracted items or {@code null} of nothing was extracted.
     * @deprecated Use {@link #poweredExtraction(IEnergySource, IMEInventory, GenericStack, IActionSource, Actionable)}
     */
    @Deprecated
    <T extends IAEStack<T>> T poweredExtraction(final IEnergySource energy, final IMEInventory<T> inv, final T request,
            final IActionSource src, final Actionable mode);

    /**
     * Inserts items into a {@link IMEInventory} respecting power requirements.
     * 
     * @param energy Energy source.
     * @param inv    Inventory to insert into.
     * @param input  Items to insert.
     * @param src    Action source.
     * @param mode   Simulate or modulate
     * @return items not inserted or {@code null} if everything was inserted.
     * @deprecated Use {@link #poweredInsert(IEnergySource, IMEInventory, GenericStack, IActionSource, Actionable)}
     */
    @Deprecated
    <T extends IAEStack<T>> T poweredInsert(final IEnergySource energy, final IMEInventory<T> inv, final T input,
            final IActionSource src, final Actionable mode);

    /**
     * GenericStack variant of {@link #poweredExtraction(IEnergySource, IMEInventory, IAEStack, IActionSource, Actionable)}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default GenericStack poweredExtraction(final IEnergySource energy, final IMEInventory<?> inv,
            final GenericStack request, final IActionSource src, final Actionable mode) {
        IMEInventory rawInv = (IMEInventory) inv;
        IAEStack<?> aeRequest = request.toIAEStack();
        if (aeRequest == null) return null;
        IAEStack<?> result = poweredExtraction(energy, rawInv, (IAEStack) aeRequest, src, mode);
        return result != null ? GenericStack.fromIAEStack(result) : null;
    }

    /**
     * GenericStack variant of {@link #poweredInsert(IEnergySource, IMEInventory, IAEStack, IActionSource, Actionable)}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    default GenericStack poweredInsert(final IEnergySource energy, final IMEInventory<?> inv,
            final GenericStack input, final IActionSource src, final Actionable mode) {
        IMEInventory rawInv = (IMEInventory) inv;
        IAEStack<?> aeInput = input.toIAEStack();
        if (aeInput == null) return null;
        IAEStack<?> result = poweredInsert(energy, rawInv, (IAEStack) aeInput, src, mode);
        return result != null ? GenericStack.fromIAEStack(result) : null;
    }

    /**
     * Posts alteration of stored items to the provided {@link IStorageGrid}. This can be used by cell containers to
     * notify the grid of storage cell changes.
     * 
     * @param gs          the storage grid.
     * @param removedCell the removed cell itemstack
     * @param addedCell   the added cell itemstack
     * @param src         the action source
     */
    void postChanges(@Nonnull final IStorageGrid gs, @Nonnull final ItemStack removedCell,
            @Nonnull final ItemStack addedCell, @Nonnull final IActionSource src);
}

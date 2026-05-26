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
import appeng.api.stacks.GenericStack;
import appeng.items.contents.CellConfigLegacy;
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
     * @deprecated Please use {@link #getConfigAEInventory(ItemStack)} instead.
     */
    @Deprecated
    IItemHandler getConfigInventory(ItemStack is);

    /**
     * Get the generic version of the config inventory, which can store any AE stack type such as items, fluids, etc.
     * <p>
     * Default implementation wraps the legacy {@link #getConfigInventory(ItemStack)} via an inline adapter.
     * Subclasses are recommended to override this method directly for native multi-type stack support.
     *
     * @param is cell item
     * @return generic config inventory
     */
    default IAEStackInventory getConfigAEInventory(ItemStack is) {
        var legacy = this.getConfigInventory(is);
        if (legacy instanceof IAEStackInventory) {
            return (IAEStackInventory) legacy;
        }
        return new IAEStackInventory(null, legacy.getSlots()) {
            @Override
            public net.minecraftforge.items.IItemHandler asItemHandler() {
                return legacy;
            }

            @Override
            public boolean isEmpty() {
                for (int i = 0; i < legacy.getSlots(); i++) {
                    if (!legacy.getStackInSlot(i).isEmpty()) return false;
                }
                return true;
            }

            @Override
            public GenericStack getGenericStack(int slot) {
                var stack = legacy.getStackInSlot(slot);
                return stack.isEmpty() ? null : GenericStack.fromItemStack(stack);
            }

            @Override
            public void setGenericStack(int slot, GenericStack stack) {
                if (legacy instanceof net.minecraftforge.items.IItemHandlerModifiable mod) {
                    if (stack != null && stack.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                        mod.setStackInSlot(slot, itemKey.toStack((int) Math.min(stack.amount(), Integer.MAX_VALUE)));
                    } else {
                        mod.setStackInSlot(slot, net.minecraft.item.ItemStack.EMPTY);
                    }
                }
                this.markDirty();
            }
        };
    }

    /**
     * Get the stack type supported by this cell.
     * Default returns item stack type.
     *
     * @return stack type
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

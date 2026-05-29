package appeng.tile.inventory;

import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.wrapper.RangedWrapper;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

public class AppEngNetworkInventory extends AppEngInternalOversizedInventory {

    private final Supplier<IStorageGrid> supplier;
    private final IActionSource source;

    public AppEngNetworkInventory(Supplier<IStorageGrid> networkSupplier, IActionSource source,
            IAEAppEngInventory inventory, int size, int maxStack) {
        super(inventory, size, maxStack);
        this.supplier = networkSupplier;
        this.source = source;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        IStorageGrid storage = supplier.get();
        if (storage != null) {
            int originAmt = stack.getCount();
            IMEInventory<IAEItemStack> dest = storage
                    .getInventory(AEItemStackType.INSTANCE);
            GenericStack overflow = dest.injectItems(GenericStack.fromItemStack(stack),
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, this.source);
            if (overflow != null && overflow.amount() == originAmt) {
                return super.insertItem(slot, stack, simulate);
            } else if (overflow != null) {
                if (!simulate) {
                    ItemStack added = stack.copy();
                    added.setCount((int) (stack.getCount() - overflow.amount()));
                    this.getTileEntity().onChangeInventory(this, slot, InvOperation.INSERT, ItemStack.EMPTY, added);
                }
                return ((AEItemKey) overflow.what()).toStack((int) overflow.amount());
            } else {
                if (!simulate) {
                    this.getTileEntity().onChangeInventory(this, slot, InvOperation.INSERT, ItemStack.EMPTY, stack);
                }
                return ItemStack.EMPTY;
            }
        } else {
            return super.insertItem(slot, stack, simulate);
        }
    }

    @Nonnull
    private ItemStack insertToBuffer(int slot, @Nonnull ItemStack stack, boolean simulate) {
        return super.insertItem(slot, stack, simulate);
    }

    public RangedWrapper getBufferWrapper(int selectSlot) {
        return new RangedWrapper(this, selectSlot, selectSlot + 1) {
            @Override
            @Nonnull
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if (slot == 0) {
                    return AppEngNetworkInventory.this.insertToBuffer(selectSlot, stack, simulate);
                }
                return stack;
            }
        };
    }

}

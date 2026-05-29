package appeng.fluids.util;

import java.util.function.Supplier;

import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEFluidStack;
import appeng.fluids.util.AEFluidStackType;

public class AENetworkFluidInventory extends AEFluidInventory {

    private final Supplier<IStorageGrid> supplier;
    private final IActionSource source;

    public AENetworkFluidInventory(Supplier<IStorageGrid> networkSupplier, IActionSource source,
            IAEFluidInventory handler, int slots, int capcity) {
        super(handler, slots, capcity);
        this.supplier = networkSupplier;
        this.source = source;
    }

    @Override
    public int fill(final FluidStack fluid, final boolean doFill) {
        if (fluid == null || fluid.amount <= 0) {
            return 0;
        }
        IStorageGrid storage = supplier.get();
        if (storage != null) {
            int originAmt = fluid.amount;
            IMEInventory<IAEFluidStack> dest = storage
                    .getInventory(AEFluidStackType.INSTANCE);
            GenericStack overflow = dest.injectItems(GenericStack.fromFluidStack(fluid),
                    doFill ? Actionable.MODULATE : Actionable.SIMULATE, this.source);
            if (overflow != null && overflow.amount() == originAmt) {
                return super.fill(fluid, doFill);
            } else if (overflow != null) {
                if (doFill) {
                    FluidStack added = fluid.copy();
                    added.amount = (int) (fluid.amount - overflow.amount());
                    this.handler.onFluidInventoryChanged(this, added, null);
                }
                return (int) (originAmt - overflow.amount());
            } else {
                if (doFill) {
                    this.handler.onFluidInventoryChanged(this, fluid, null);
                }
                return originAmt;
            }
        } else {
            return super.fill(fluid, doFill);
        }
    }

}

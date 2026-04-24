package appeng.fluids.util;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.storage.data.IAEFluidStack;

public interface IAEFluidTank extends IFluidHandler {
    void setFluidInSlot(final int slot, final IAEFluidStack fluid);

    IAEFluidStack getFluidInSlot(final int slot);

    int getSlots();

    /**
     * Fill a specific slot with the given fluid.
     */
    int fill(int slot, FluidStack resource, boolean doFill);

    /**
     * Drain from a specific slot matching the given fluid.
     */
    FluidStack drain(int slot, FluidStack resource, boolean doDrain);

}

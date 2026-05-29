package appeng.util.inv;

import java.util.Collections;
import java.util.Iterator;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.InventoryAdaptor;

/**
 * 纯Fluid adaptor：将 Forge IFluidHandler 暴露为 InventoryAdaptor。
 * <p>
 * 只有流体操作能力，物品操作全部返回空/失败。
 * 用于只有 IFluidHandler 能力但没有 IItemHandler 能力的 TileEntity。
 */
public class AdaptorFluidHandler extends InventoryAdaptor {

    protected final IFluidHandler fluidHandler;
    protected final EnumFacing facing;

    public AdaptorFluidHandler(IFluidHandler fluidHandler, EnumFacing facing) {
        this.fluidHandler = fluidHandler;
        this.facing = facing;
    }

    // ============================================================
    // 物品操作 — 不支持，全部返回空/失败
    // ============================================================

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return toBeAdded;
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return toBeSimulated;
    }

    @Override
    public boolean containsItems() {
        return containsFluid();
    }

    @Override
    public boolean hasSlots() {
        IFluidTankProperties[] props = this.fluidHandler.getTankProperties();
        return props != null && props.length > 0;
    }

    // ============================================================
    // 流体操作
    // ============================================================

    /**
     * @return 是否包含任何流体
     */
    protected boolean containsFluid() {
        IFluidTankProperties[] props = this.fluidHandler.getTankProperties();
        if (props != null) {
            for (IFluidTankProperties prop : props) {
                FluidStack contents = prop.getContents();
                if (contents != null && contents.amount > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    @Nullable
    public GenericStack addStack(GenericStack toBeAdded) {
        if (toBeAdded.what() instanceof AEFluidKey fluidKey) {
            FluidStack fs = fluidKey.toStack((int) toBeAdded.amount());
            int filled = this.fluidHandler.fill(fs, true);
            if (filled >= toBeAdded.amount()) {
                return null;
            }
            return new GenericStack(fluidKey, toBeAdded.amount() - filled);
        }
        return super.addStack(toBeAdded);
    }

    @Override
    @Nullable
    public GenericStack simulateAddStack(GenericStack toBeSimulated) {
        if (toBeSimulated.what() instanceof AEFluidKey fluidKey) {
            FluidStack fs = fluidKey.toStack((int) toBeSimulated.amount());
            int filled = this.fluidHandler.fill(fs, false);
            if (filled >= toBeSimulated.amount()) {
                return null;
            }
            return new GenericStack(fluidKey, toBeSimulated.amount() - filled);
        }
        return super.simulateAddStack(toBeSimulated);
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return Collections.emptyIterator();
    }
}

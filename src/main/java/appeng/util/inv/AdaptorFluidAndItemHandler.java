package appeng.util.inv;

import java.util.Iterator;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.InventoryAdaptor;
import appeng.util.item.AEItemStack;

/**
 * 复合适配器：同时包装 IItemHandler 和 IFluidHandler。
 * <p>
 * 物品操作委托给 AdaptorItemHandler，流体操作委托给内部的 IFluidHandler。
 * addStack/simulateAddStack 根据 IAEStack 类型自动分发。
 */
public class AdaptorFluidAndItemHandler extends InventoryAdaptor {

    private final AdaptorItemHandler itemAdaptor;
    private final IFluidHandler fluidHandler;
    private final EnumFacing facing;

    public AdaptorFluidAndItemHandler(IItemHandler itemHandler, IFluidHandler fluidHandler, EnumFacing facing) {
        this.itemAdaptor = new AdaptorItemHandler(itemHandler);
        this.fluidHandler = fluidHandler;
        this.facing = facing;
    }

    // ============================================================
    // 物品操作 — 委托给 AdaptorItemHandler
    // ============================================================

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        return this.itemAdaptor.removeItems(amount, filter, destination);
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return this.itemAdaptor.simulateRemove(amount, filter, destination);
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return this.itemAdaptor.removeSimilarItems(amount, filter, fuzzyMode, destination);
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return this.itemAdaptor.simulateSimilarRemove(amount, filter, fuzzyMode, destination);
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return this.itemAdaptor.addItems(toBeAdded);
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return this.itemAdaptor.simulateAdd(toBeSimulated);
    }

    @Override
    public boolean containsItems() {
        return this.itemAdaptor.containsItems() || containsFluid();
    }

    @Override
    public boolean hasSlots() {
        return this.itemAdaptor.hasSlots() || hasFluidSlots();
    }

    // ============================================================
    // 流体操作
    // ============================================================

    private boolean containsFluid() {
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

    private boolean hasFluidSlots() {
        IFluidTankProperties[] props = this.fluidHandler.getTankProperties();
        return props != null && props.length > 0;
    }

    // ============================================================
    // 泛型栈操作 — 根据类型分发
    // ============================================================

    @Override
    @Nullable
    public IAEStack<?> addStack(IAEStack<?> toBeAdded) {
        if (toBeAdded instanceof IAEFluidStack fluidStack) {
            FluidStack fs = fluidStack.getFluidStack();
            int filled = this.fluidHandler.fill(fs, true);
            if (filled >= toBeAdded.getStackSize()) {
                return null;
            }
            IAEFluidStack remainder = fluidStack.copy();
            remainder.setStackSize(toBeAdded.getStackSize() - filled);
            return remainder;
        }
        if (toBeAdded instanceof IAEItemStack itemStack) {
            ItemStack result = this.addItems(itemStack.createItemStack());
            return AEItemStack.fromItemStack(result);
        }
        return toBeAdded;
    }

    @Override
    @Nullable
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated) {
        if (toBeSimulated instanceof IAEFluidStack fluidStack) {
            FluidStack fs = fluidStack.getFluidStack();
            int filled = this.fluidHandler.fill(fs, false);
            if (filled >= toBeSimulated.getStackSize()) {
                return null;
            }
            IAEFluidStack remainder = fluidStack.copy();
            remainder.setStackSize(toBeSimulated.getStackSize() - filled);
            return remainder;
        }
        if (toBeSimulated instanceof IAEItemStack itemStack) {
            ItemStack result = this.simulateAdd(itemStack.createItemStack());
            return AEItemStack.fromItemStack(result);
        }
        return toBeSimulated;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return this.itemAdaptor.iterator();
    }
}

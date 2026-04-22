package appeng.helpers;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.tiles.ISegmentedInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.IConfigManager;
import appeng.fluids.helper.DualityFluidInterface;
import appeng.fluids.helper.IFluidInterfaceHost;
import appeng.fluids.util.AEFluidInventory;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.SettingsFrom;
import appeng.util.inv.InvOperation;

import com.google.common.collect.ImmutableSet;

/**
 * 二合一接口的核心组件，组合了物品接口（DualityInterface）和流体接口（DualityFluidInterface）。
 * 两个 Duality 共享同一个 AENetworkProxy，只占用一个频道。
 *
 * @param <H> 宿主类型，需要同时实现 IInterfaceHost 和 IFluidInterfaceHost
 * @deprecated 使用 {@link PatternProviderLogic} + {@link InterfaceLogic} 替代，二合一接口已被样板供应器和 ME 接口取代。
 */
@Deprecated
public class DualityDualInterface<H extends IInterfaceHost & IFluidInterfaceHost> {

    private final DualityInterface itemDuality;
    private final DualityFluidInterface fluidDuality;
    private final AENetworkProxy proxy;

    public DualityDualInterface(final AENetworkProxy proxy, final H host) {
        this.proxy = proxy;
        this.itemDuality = new DualityInterface(proxy, host);
        this.fluidDuality = new DualityFluidInterface(proxy, host);
    }

    // ---- 生命周期 ----

    public void initialize() {
        this.itemDuality.initialize();
    }

    public void onGridChanged() {
        this.itemDuality.gridChanged();
        this.fluidDuality.gridChanged();
    }

    public void onChannelStateChange(final MENetworkChannelsChanged c) {
        this.itemDuality.notifyNeighbors();
        this.fluidDuality.notifyNeighbors();
    }

    public void onPowerStateChange(final MENetworkPowerStatusChange c) {
        this.itemDuality.notifyNeighbors();
        this.fluidDuality.notifyNeighbors();
    }

    // ---- NBT ----

    public void readFromNBT(final NBTTagCompound data) {
        this.itemDuality.readFromNBT(data);
        // 流体接口数据存储在 "fluidIf" 子 Tag 下，避免与物品接口的 key 冲突
        NBTTagCompound fluidTag = data.getCompoundTag("fluidIf");
        this.fluidDuality.readFromNBT(fluidTag);
    }

    public void writeToNBT(final NBTTagCompound data) {
        this.itemDuality.writeToNBT(data);
        NBTTagCompound fluidTag = new NBTTagCompound();
        this.fluidDuality.writeToNBT(fluidTag);
        data.setTag("fluidIf", fluidTag);
    }

    // ---- Tick ----

    public TickingRequest getTickingRequest(final IGridNode node) {
        TickingRequest item = this.itemDuality.getTickingRequest(node);
        TickingRequest fluid = this.fluidDuality.getTickingRequest(node);
        return new TickingRequest(
                Math.min(item.minTickRate, fluid.minTickRate),
                Math.max(item.maxTickRate, fluid.maxTickRate),
                item.isSleeping && fluid.isSleeping,
                false);
    }

    public TickRateModulation onTick(final IGridNode node, final int ticksSinceLastCall) {
        TickRateModulation item = this.itemDuality.tickingRequest(node, ticksSinceLastCall);
        TickRateModulation fluid = this.fluidDuality.tickingRequest(node, ticksSinceLastCall);
        // 只要有一个需要紧急/普通 tick，就不休眠
        if (item == TickRateModulation.URGENT || fluid == TickRateModulation.URGENT) {
            return TickRateModulation.URGENT;
        }
        if (item == TickRateModulation.FASTER || fluid == TickRateModulation.FASTER) {
            return TickRateModulation.FASTER;
        }
        if (item == TickRateModulation.SAME || fluid == TickRateModulation.SAME) {
            return TickRateModulation.SAME;
        }
        if (item == TickRateModulation.SLOWER || fluid == TickRateModulation.SLOWER) {
            return TickRateModulation.SLOWER;
        }
        if (item == TickRateModulation.IDLE || fluid == TickRateModulation.IDLE) {
            return TickRateModulation.IDLE;
        }
        return TickRateModulation.SLEEP;
    }

    // ---- 掉落物 ----

    public void addDrops(final List<ItemStack> drops) {
        this.itemDuality.addDrops(drops);
        this.fluidDuality.addDrops(drops);
    }

    // ---- Capability ----

    public boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing) {
        // 物品能力走物品 Duality，流体能力走流体 Duality
        if (capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return this.fluidDuality.hasCapability(capabilityClass, facing);
        }
        return this.itemDuality.hasCapability(capabilityClass, facing);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capabilityClass, EnumFacing facing) {
        if (capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return this.fluidDuality.getCapability(capabilityClass, facing);
        }
        return this.itemDuality.getCapability(capabilityClass, facing);
    }

    // ---- 合成 ----

    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.itemDuality.pushPattern(patternDetails, table);
    }

    public boolean isCraftingBusy() {
        return this.itemDuality.isBusy();
    }

    public void provideCrafting(final appeng.api.networking.crafting.ICraftingProviderHelper craftingTracker) {
        this.itemDuality.provideCrafting(craftingTracker);
    }

    public ImmutableSet<appeng.api.networking.crafting.ICraftingLink> getRequestCraftingJobs() {
        return this.itemDuality.getRequestedJobs();
    }

    public appeng.api.storage.data.IAEItemStack injectCraftedItems(
            final appeng.api.networking.crafting.ICraftingLink link,
            final appeng.api.storage.data.IAEItemStack items,
            final appeng.api.config.Actionable mode) {
        return this.itemDuality.injectCraftedItems(link, items, mode);
    }

    public void onCraftingJobStateChange(final appeng.api.networking.crafting.ICraftingLink link) {
        this.itemDuality.jobStateChange(link);
    }

    // ---- 配置 ----

    public IConfigManager getConfigManager() {
        return this.itemDuality.getConfigManager();
    }

    // ---- 库存 ----

    public IItemHandler getItemInventoryByName(final String name) {
        return this.itemDuality.getInventoryByName(name);
    }

    public IFluidHandler getFluidInventoryByName(final String name) {
        return this.fluidDuality.getFluidInventoryByName(name);
    }

    public boolean canInsertItem(final ItemStack stack) {
        return this.itemDuality.canInsert(stack);
    }

    // ---- 物品变更回调 ----

    public void onItemInventoryChange(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        this.itemDuality.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    // ---- 优先级 ----

    public int getPriority() {
        return this.itemDuality.getPriority();
    }

    public void setPriority(final int newValue) {
        this.itemDuality.setPriority(newValue);
        this.fluidDuality.setPriority(newValue);
    }

    // ---- 访问器 ----

    public DualityInterface getItemInterface() {
        return this.itemDuality;
    }

    public DualityFluidInterface getFluidInterface() {
        return this.fluidDuality;
    }

    // ---- 记忆卡导入导出 ----

    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound tag = new NBTTagCompound();
        if (from == SettingsFrom.MEMORY_CARD) {
            final IItemHandler inv = this.getItemInventoryByName("patterns");
            if (inv instanceof AppEngInternalInventory) {
                ((AppEngInternalInventory) inv).writeToNBT(tag, "item_patterns");
            }
            final IFluidHandler fluidInv = this.fluidDuality.getFluidInventoryByName("config");
            if (fluidInv instanceof AEFluidInventory) {
                ((AEFluidInventory) fluidInv).writeToNBT(tag, "fluid_config");
            }
        }
        return tag;
    }

    public void uploadSettings(NBTTagCompound compound, net.minecraft.entity.player.EntityPlayer player) {
        final IItemHandler inv = this.getItemInventoryByName("patterns");
        if (inv instanceof AppEngInternalInventory) {
            final AppEngInternalInventory target = (AppEngInternalInventory) inv;
            AppEngInternalInventory tmp = new AppEngInternalInventory(null, target.getSlots());
            tmp.readFromNBT(compound, "item_patterns");
            net.minecraftforge.items.wrapper.PlayerMainInvWrapper playerInv =
                    new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(player.inventory);
            final appeng.api.definitions.IMaterials materials = appeng.api.AEApi.instance().definitions().materials();
            int missingPatternsToEncode = 0;

            for (int i = 0; i < inv.getSlots(); i++) {
                if (target.getStackInSlot(i).getItem() instanceof appeng.items.misc.ItemEncodedPattern) {
                    ItemStack blank = materials.blankPattern().maybeStack(target.getStackInSlot(i).getCount()).get();
                    if (!player.addItemStackToInventory(blank)) {
                        player.dropItem(blank, true);
                    }
                    target.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            for (int x = 0; x < tmp.getSlots(); x++) {
                if (!tmp.getStackInSlot(x).isEmpty()) {
                    boolean found = false;
                    for (int i = 0; i < playerInv.getSlots(); i++) {
                        if (materials.blankPattern().isSameAs(playerInv.getStackInSlot(i))) {
                            target.setStackInSlot(x, tmp.getStackInSlot(x));
                            playerInv.getStackInSlot(i).shrink(1);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        missingPatternsToEncode++;
                    }
                }
            }

            if (appeng.util.Platform.isServer() && missingPatternsToEncode > 0) {
                player.sendMessage(appeng.core.localization.PlayerMessages.MissingPatternsToEncode.get());
            }
        }

        final IFluidHandler fluidInv = this.fluidDuality.getFluidInventoryByName("config");
        if (fluidInv instanceof AEFluidInventory) {
            AEFluidInventory target = (AEFluidInventory) fluidInv;
            AEFluidInventory tmp = new AEFluidInventory(null, target.getSlots());
            tmp.readFromNBT(compound, "fluid_config");
            for (int x = 0; x < tmp.getSlots(); x++) {
                target.setFluidInSlot(x, tmp.getFluidInSlot(x));
            }
        }
    }
}

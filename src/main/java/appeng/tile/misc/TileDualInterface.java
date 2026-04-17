package appeng.tile.misc;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IMaterials;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.GuiBridge;
import appeng.fluids.helper.DualityFluidInterface;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.fluids.helper.IFluidInterfaceHost;
import appeng.helpers.DualityDualInterface;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.InvOperation;

/**
 * 二合一接口方块 TileEntity。
 * 同时拥有物品接口和流体接口的功能，共享一个频道。
 */
public class TileDualInterface extends AENetworkInvTile
        implements IGridTickable, IInventoryDestination, IInterfaceHost, IFluidInterfaceHost, IConfigurableFluidInventory, IPriorityHost {

    private final DualityDualInterface<TileDualInterface> duality =
            new DualityDualInterface<>(this.getProxy(), this);

    private boolean omniDirectional = true;

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        this.duality.onChannelStateChange(c);
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.duality.onPowerStateChange(c);
    }

    public void setSide(final EnumFacing facing) {
        if (Platform.isClient()) {
            return;
        }

        EnumFacing newForward = facing;

        if (!this.omniDirectional && this.getForward() == facing.getOpposite()) {
            newForward = facing;
        } else if (!this.omniDirectional
                && (this.getForward() == facing || this.getForward() == facing.getOpposite())) {
            this.omniDirectional = true;
        } else if (this.omniDirectional) {
            newForward = facing.getOpposite();
            this.omniDirectional = false;
        } else {
            newForward = Platform.rotateAround(this.getForward(), facing);
        }

        if (this.omniDirectional) {
            this.setOrientation(EnumFacing.NORTH, EnumFacing.UP);
        } else {
            EnumFacing newUp = EnumFacing.UP;
            if (newForward == EnumFacing.UP || newForward == EnumFacing.DOWN) {
                newUp = EnumFacing.NORTH;
            }
            this.setOrientation(newForward, newUp);
        }

        this.configureNodeSides();
        this.markForUpdate();
        this.saveChanges();
    }

    private void configureNodeSides() {
        if (this.omniDirectional) {
            this.getProxy().setValidSides(EnumSet.allOf(EnumFacing.class));
        } else {
            this.getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(this.getForward())));
        }
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        this.duality.addDrops(drops);
    }

    @Override
    public void gridChanged() {
        this.duality.onGridChanged();
    }

    @Override
    public void onReady() {
        this.configureNodeSides();
        super.onReady();
        this.duality.initialize();
        this.getProxy().setIdlePowerUsage(Math.pow(4, (this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION))));
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("omniDirectional", this.omniDirectional);
        this.duality.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.omniDirectional = data.getBoolean("omniDirectional");
        this.duality.readFromNBT(data);
    }

    @Override
    protected boolean readFromStream(final ByteBuf data) throws IOException {
        final boolean c = super.readFromStream(data);
        boolean oldOmniDirectional = this.omniDirectional;
        this.omniDirectional = data.readBoolean();
        return oldOmniDirectional != this.omniDirectional || c;
    }

    @Override
    protected void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.omniDirectional);
    }

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return this.duality.getItemInterface().getCableConnectionType(dir);
    }

    @Override
    public DimensionalCoord getLocation() {
        return this.duality.getItemInterface().getLocation();
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.duality.canInsertItem(stack);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return this.duality.getItemInventoryByName(name);
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.duality.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.duality.onTick(node, ticksSinceLastCall);
    }

    @Override
    public IItemHandler getInternalInventory() {
        return this.duality.getItemInterface().getInternalInventory();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        this.duality.onItemInventoryChange(inv, slot, mc, removed, added);
    }

    @Override
    public void onStackReturnNetwork(IAEStack<?> stack) {
        this.duality.getItemInterface().onStackReturnedToNetwork(stack);
    }

    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality.getItemInterface();
    }

    @Override
    public DualityFluidInterface getDualityFluidInterface() {
        return this.duality.getFluidInterface();
    }

    @Override
    public EnumSet<EnumFacing> getTargets() {
        if (this.omniDirectional) {
            return EnumSet.allOf(EnumFacing.class);
        }
        return EnumSet.of(this.getForward());
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.duality.getConfigManager();
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.duality.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.duality.isCraftingBusy();
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.duality.provideCrafting(craftingTracker);
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.duality.getItemInterface().getInstalledUpgrades(u);
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.duality.getRequestCraftingJobs();
    }

    @Override
    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack items,
            final Actionable mode) {
        return this.duality.injectCraftedItems(link, items, mode);
    }

    public void updateRedstoneState() {
        this.duality.getItemInterface().updateRedstoneState();
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.duality.onCraftingJobStateChange(link);
    }

    @Override
    public int getPriority() {
        return this.duality.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.duality.setPriority(newValue);
    }

    public boolean isOmniDirectional() {
        return this.omniDirectional;
    }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return this.duality.hasCapability(capability, facing) || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        T result = this.duality.getCapability(capability, facing);
        if (result != null) {
            return result;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().blocks().dualIface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return GuiBridge.GUI_DUAL_ITEM_INTERFACE;
    }

    public IFluidHandler getFluidInventoryByName(final String name) {
        return this.duality.getFluidInventoryByName(name);
    }

    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (from == SettingsFrom.MEMORY_CARD) {
            NBTTagCompound dualTag = this.duality.downloadSettings(from);
            if (output == null) {
                output = dualTag;
            } else {
                for (String key : dualTag.getKeySet()) {
                    output.setTag(key, dualTag.getTag(key));
                }
            }
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        this.duality.uploadSettings(compound, player);
    }
}

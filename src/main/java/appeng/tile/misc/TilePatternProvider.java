/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

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
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.EmptyHandler;
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
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPatternProviderHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.PatternProviderLogic;
import appeng.items.misc.ItemEncodedPattern;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.InvOperation;

/**
 * 样板供应器方块。
 *
 * 对应高版本 AE2 的 PatternProviderBlock。
 * 仅负责样板存储和合成推送，不含 Config/Storage 功能。
 */
public class TilePatternProvider extends AENetworkInvTile
        implements IGridTickable, IPatternProviderHost, IPriorityHost {

    private final PatternProviderLogic logic = new PatternProviderLogic(this.getProxy(), this);

    // 是否全向
    private boolean omniDirectional = true;

    // ========== 网络事件 ==========

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        this.logic.notifyNeighbors();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.logic.notifyNeighbors();
    }

    // ========== 方向控制 ==========

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
            newForward = appeng.util.OrientationHelper.rotateAround(this.getForward(), facing);
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

    public boolean isOmniDirectional() {
        return this.omniDirectional;
    }

    // ========== 掉落物 ==========

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        this.logic.addDrops(drops);
    }

    // ========== 网格 ==========

    @Override
    public void gridChanged() {
        this.logic.gridChanged();
    }

    @Override
    public void onReady() {
        this.configureNodeSides();
        super.onReady();
        this.logic.initialize();
        this.getProxy().setIdlePowerUsage(Math.pow(4, (this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION))));
    }

    // ========== NBT ==========

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("omniDirectional", this.omniDirectional);
        this.logic.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.omniDirectional = data.getBoolean("omniDirectional");
        this.logic.readFromNBT(data);
    }

    // ========== 网络同步 ==========

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

    // ========== 电缆连接 ==========

    @Override
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return this.logic.getCableConnectionType();
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    // ========== AENetworkInvTile ==========

    /**
     * 返回 EmptyHandler 避免 AEBaseInvTile 对样板槽进行额外的 NBT 读写和 Capability 暴露。
     * 样板和 returnBuffer 的 NBT/Capability 由 PatternProviderLogic 自行管理。
     */
    @Override
    public IItemHandler getInternalInventory() {
        return EmptyHandler.INSTANCE;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        this.logic.onChangeInventory(inv, slot, mc, removed, added);
    }

    // ========== IPatternProviderHost ==========

    @Override
    public PatternProviderLogic getPatternProviderLogic() {
        return this.logic;
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

    // ========== IGridTickable ==========

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.logic.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.logic.tickingRequest(node, ticksSinceLastCall);
    }

    // ========== ICraftingProvider ==========

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.logic.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.logic.isBusy();
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.logic.provideCrafting(craftingTracker);
    }

    // ========== ICraftingRequester ==========

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.logic.getRequestedJobs();
    }

    @Override
    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack items, final Actionable mode) {
        return this.logic.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.logic.jobStateChange(link);
    }

    // ========== IUpgradeableHost ==========

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.logic.getInstalledUpgrades(u);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return this.logic.getInventoryByName(name);
    }

    // ========== IConfigManagerHost / IConfigManager ==========

    @Override
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    // ========== IPriorityHost ==========

    @Override
    public int getPriority() {
        return this.logic.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.logic.setPriority(newValue);
    }

    // ========== Capability ==========

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return this.logic.hasCapability(capability, facing) || super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        T result = this.logic.getCapability(capability, facing);
        if (result != null) {
            return result;
        }
        return super.getCapability(capability, facing);
    }

    // ========== GUI ==========

    @Override
    public ItemStack getItemStackRepresentation() {
        // TODO: 等 B4（注册）完成后替换为正确的方块定义
        return AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        // TODO: 等 B4（注册）完成后替换为 GUI_PATTERN_PROVIDER
        return AEGuiKeys.INTERFACE.getLegacyBridge();
    }

    // ========== 红石 ==========

    public void updateRedstoneState() {
        this.logic.updateRedstoneState();
    }

    // ========== Memory Card ==========

    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from) {
        NBTTagCompound output = super.downloadSettings(from);
        if (from == SettingsFrom.MEMORY_CARD) {
            final IItemHandler inv = this.getInventoryByName("patterns");
            if (inv instanceof AppEngInternalInventory) {
                ((AppEngInternalInventory) inv).writeToNBT(output, "patterns");
            }
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        final IItemHandler inv = this.getInventoryByName("patterns");
        if (inv instanceof AppEngInternalInventory target) {
            AppEngInternalInventory tmp = new AppEngInternalInventory(null, target.getSlots());
            tmp.readFromNBT(compound, "patterns");
            PlayerMainInvWrapper playerInv = new PlayerMainInvWrapper(player.inventory);
            final IMaterials materials = AEApi.instance().definitions().materials();
            int missingPatternsToEncode = 0;
            int amountPatternSlots = PatternProviderLogic.NUMBER_OF_PATTERN_SLOTS;

            for (int i = 0; i < inv.getSlots(); i++) {
                if (target.getStackInSlot(i).getItem() instanceof ItemEncodedPattern) {
                    ItemStack blank = materials.blankPattern().maybeStack(target.getStackInSlot(i).getCount()).get();
                    if (!player.addItemStackToInventory(blank)) {
                        player.dropItem(blank, true);
                    }
                    target.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            for (int x = 0; x < amountPatternSlots; x++) {
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

            if (Platform.isServer() && missingPatternsToEncode > 0) {
                player.sendMessage(PlayerMessages.MissingPatternsToEncode.get());
            }
        }
    }
}

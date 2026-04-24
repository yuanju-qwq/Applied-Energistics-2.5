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

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IInterfaceLogicHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogic;
import appeng.tile.grid.AENetworkInvTile;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.InvOperation;

/**
 * ME 接口方块。
 *
 * 对应高版本 AE2 的 InterfaceBlock。
 * 使用统一的 Config（IAEStack<?> 泛型标记，支持物品+流体混合），
 * 不含样板功能（样板由 {@link TilePatternProvider} 承担）。
 */
public class TileMEInterface extends AENetworkInvTile
        implements IGridTickable, IInventoryDestination, IInterfaceLogicHost, IPriorityHost {

    private final InterfaceLogic logic = new InterfaceLogic(this.getProxy(), this);

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
        return this.logic.getLocation();
    }

    // ========== IInventoryDestination ==========

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.logic.canInsert(stack);
    }

    // ========== AENetworkInvTile ==========

    @Override
    public IItemHandler getInternalInventory() {
        return this.logic.getItemStorage();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        this.logic.onChangeInventory(inv, slot, mc, removed, added);
    }

    // ========== IInterfaceLogicHost ==========

    @Override
    public InterfaceLogic getInterfaceLogic() {
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

    @Override
    public void onStackReturnNetwork(IAEStack<?> stack) {
        // ME 接口不需要处理样板锁定
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
        // TODO: 等注册完成后替换为正确的方块定义
        return AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public AEGuiKey getGuiKey() {
        return AEGuiKeys.ME_INTERFACE;
    }

    @Override
    public GuiBridge getGuiBridge() {
        // TODO: 等 B4（Container/GUI）完成后替换为 GUI_ME_INTERFACE
        return getGuiKey().getLegacyBridge();
    }
}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.parts.misc;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
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
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.AppEng;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IInterfaceLogicHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.InvOperation;

/**
 * ME 接口部件。
 *
 * 对应高版本 AE2 的 InterfacePart。
 * 使用统一的 Config（IAEStack<?> 泛型标记，支持物品+流体混合），
 * 不含样板功能（样板由 {@link PartPatternProvider} 承担）。
 */
public class PartMEInterface extends PartBasicState implements IGridTickable, IStorageMonitorable,
        IInventoryDestination, IInterfaceLogicHost, IAEAppEngInventory, IPriorityHost {

    // --- 模型定义 ---
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID,
            "part/me_interface_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/me_interface_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/me_interface_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/me_interface_has_channel"));

    private final InterfaceLogic logic = new InterfaceLogic(this.getProxy(), this);

    @Reflected
    public PartMEInterface(final ItemStack is) {
        super(is);
    }

    // ========== 网络事件 ==========

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.logic.notifyNeighbors();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.logic.notifyNeighbors();
    }

    // ========== 碰撞箱 ==========

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    // ========== IUpgradeableHost ==========

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.logic.getInstalledUpgrades(u);
    }

    // ========== 网格 ==========

    @Override
    public void gridChanged() {
        this.logic.gridChanged();
    }

    // ========== NBT ==========

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
    }

    // ========== 生命周期 ==========

    @Override
    public void addToWorld() {
        super.addToWorld();
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        this.logic.addDrops(drops);
    }

    // ========== 电缆 ==========

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    // ========== IConfigManager ==========

    @Override
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return this.logic.getInventoryByName(name);
    }

    // ========== GUI ==========

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(p, this.getTileEntity(), this.getSide(), AEGuiKeys.ME_INTERFACE);
        }
        return true;
    }

    // ========== IInventoryDestination ==========

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.logic.canInsert(stack);
    }

    // ========== IStorageMonitorable ==========

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IAEStackType<T> type) {
        return this.logic.getInventory(type);
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

    // ========== IAEAppEngInventory ==========

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        this.logic.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    // ========== IInterfaceLogicHost ==========

    @Override
    public InterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    @Override
    public TileEntity getTileEntity() {
        return super.getHost().getTile();
    }

    @Override
    public void onStackReturnNetwork(IAEStack<?> stack) {
        // ME 接口不需要处理样板锁定
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

    // ========== 模型 ==========

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    // ========== Capability ==========

    @Override
    public boolean hasCapability(Capability<?> capabilityClass) {
        return this.logic.hasCapability(capabilityClass, this.getSide().getFacing());
    }

    @Override
    public <T> T getCapability(Capability<T> capabilityClass) {
        return this.logic.getCapability(capabilityClass, this.getSide().getFacing());
    }

    // ========== GUI 辅助 ==========

    @Override
    public ItemStack getItemStackRepresentation() {
        // TODO: 等注册完成后替换为正确的部件定义
        return AEApi.instance().definitions().parts().iface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return AEGuiKeys.ME_INTERFACE.getLegacyBridge();
    }
}

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

package appeng.helpers;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.google.common.primitives.Ints;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.*;
import appeng.api.storage.data.*;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.capabilities.Capabilities;
import appeng.core.settings.TickRates;
import appeng.fluids.util.AEFluidInventory;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.AEFluidStackType;
import appeng.fluids.util.AENetworkFluidInventory;
import appeng.fluids.util.IAEFluidInventory;
import appeng.fluids.util.IAEFluidTank;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.MEMonitorIFluidHandler;
import appeng.me.storage.MEMonitorPassThrough;
import appeng.me.storage.NullInventory;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.tile.inventory.AppEngNetworkInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.*;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 统一接口核心逻辑。
 *
 * 合并了旧的 {@link DualityInterface}（物品 Config + Storage）和
 * {@link appeng.fluids.helper.DualityFluidInterface}（流体 Config + Tank），
 * 使用统一的 {@link IAEStackInventory} 作为 Config，每个槽位可标记物品或流体。
 *
 * 对应高版本 AE2 的 {@code InterfaceLogic}。
 *
 * <h3>槽位布局</h3>
 * <ul>
 *   <li>Config: 18 槽 (IAEStack<?> 泛型标记，每槽可放 IAEItemStack 或 IAEFluidStack)</li>
 *   <li>物品 Storage: 18 槽 (对应 Config 中物品类型的实际存放)</li>
 *   <li>流体 Tank: 18 槽 (对应 Config 中流体类型的实际存放)</li>
 * </ul>
 *
 * 宿主方块/部件通过实现 {@link IInterfaceLogicHost} 并持有此对象来获得统一接口能力。
 */
public class InterfaceLogic
        implements IGridTickable, IStorageMonitorable, IInventoryDestination, IAEAppEngInventory,
        IConfigManagerHost, IUpgradeableHost, IConfigurableFluidInventory, IAEFluidInventory,
        IIAEStackInventory {

    // ========== 常量 ==========
    public static final int NUMBER_OF_CONFIG_SLOTS = 18;
    public static final int SLOTS_PER_ROW = 9;
    public static final int TANK_CAPACITY = Fluid.BUCKET_VOLUME * 4;

    // ========== 字段 ==========

    // --- 核心引用 ---
    private final AENetworkProxy gridProxy;
    private final IInterfaceLogicHost iHost;
    private final IActionSource mySource;
    private final IActionSource interfaceRequestSource;
    private final ConfigManager cm = new ConfigManager(this);

    // --- 统一 Config（36 槽泛型标记） ---
    private final IAEStackInventory config = new IAEStackInventory(this, NUMBER_OF_CONFIG_SLOTS, StorageName.CONFIG);

    // --- 物品 Storage（36 槽，与 Config 槽位一一对应） ---
    private final AppEngNetworkInventory itemStorage;

    // --- 流体 Tank（36 槽，与 Config 槽位一一对应） ---
    private final AEFluidInventory fluidTanks;

    // --- 统一 Plan（36 槽，IAEStack<?> 泛型工作计划） ---
    private final IAEStack<?>[] requireWork = new IAEStack<?>[NUMBER_OF_CONFIG_SLOTS];

    // --- ME 网络存储代理 ---
    private final MEMonitorPassThrough<IAEItemStack> items = new MEMonitorPassThrough<>(
            new NullInventory<IAEItemStack>(), AEItemStackType.INSTANCE);
    private final MEMonitorPassThrough<IAEFluidStack> fluids = new MEMonitorPassThrough<>(
            new NullInventory<IAEFluidStack>(), AEFluidStackType.INSTANCE);

    // --- Capability ---
    private final IStorageMonitorableAccessor accessor = this::getMonitorable;

    // --- 升级 ---
    private final UpgradeInventory upgrades;

    // --- 合成跟踪（用于 Config 中带 Crafting 升级的自动合成请求，仅物品） ---
    private final MultiCraftingTracker craftingTracker;

    // --- 状态 ---
    private boolean hasItemConfig = false;
    private boolean hasFluidConfig = false;
    private int isWorkingSlot = -1;
    private int priority;

    // --- ME 网络库存缓存 ---
    private IMEInventory<IAEItemStack> destination;
    private boolean resetItemConfigCache = true;
    private IMEMonitor<IAEItemStack> itemConfigCachedHandler;
    private boolean resetFluidConfigCache = true;
    private IMEMonitor<IAEFluidStack> fluidConfigCachedHandler;

    // ========== 构造 ==========

    public InterfaceLogic(final AENetworkProxy networkProxy, final IInterfaceLogicHost host) {
        this.gridProxy = networkProxy;
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);

        this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 3);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);

        this.iHost = host;
        this.craftingTracker = new MultiCraftingTracker(this.iHost, NUMBER_OF_CONFIG_SLOTS);

        final MachineSource actionSource = new MachineSource(this.iHost);
        this.mySource = actionSource;
        this.interfaceRequestSource = new InterfaceRequestSource(this.iHost);

        this.itemStorage = new AppEngNetworkInventory(this::getStorageGrid, this.mySource, this,
                NUMBER_OF_CONFIG_SLOTS, 512);
        this.fluidTanks = new AENetworkFluidInventory(this::getStorageGrid, this.mySource, this,
                NUMBER_OF_CONFIG_SLOTS, TANK_CAPACITY);

        this.fluids.setChangeSource(actionSource);
        this.items.setChangeSource(actionSource);
    }

    @Nullable
    private IStorageGrid getStorageGrid() {
        try {
            return this.gridProxy.getStorage();
        } catch (GridAccessException e) {
            return null;
        }
    }

    // ========== IIAEStackInventory — 泛型 Config 库存回调 ==========

    @Override
    public void saveAEStackInv() {
        boolean oldItemCfg = this.hasItemConfig;
        boolean oldFluidCfg = this.hasFluidConfig;

        this.readConfig();

        if (oldItemCfg != this.hasItemConfig) {
            resetItemConfigCache = true;
        }
        if (oldFluidCfg != this.hasFluidConfig) {
            resetFluidConfigCache = true;
        }
        if (oldItemCfg != this.hasItemConfig || oldFluidCfg != this.hasFluidConfig) {
            this.notifyNeighbors();
        }

        this.iHost.saveChanges();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }

    // ========== NBT 读写 ==========

    public void writeToNBT(final NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
        this.itemStorage.writeToNBT(data, "itemStorage");
        this.fluidTanks.writeToNBT(data, "fluidTanks");
        this.upgrades.writeToNBT(data, "upgrades");
        this.cm.writeToNBT(data);
        this.craftingTracker.writeToNBT(data);
        data.setInteger("priority", this.priority);
    }

    public void readFromNBT(final NBTTagCompound data) {
        this.upgrades.readFromNBT(data, "upgrades");
        this.config.readFromNBT(data, "config");
        this.itemStorage.readFromNBT(data, "itemStorage");
        this.fluidTanks.readFromNBT(data, "fluidTanks");
        this.priority = data.getInteger("priority");
        this.cm.readFromNBT(data);
        this.craftingTracker.readFromNBT(data);

        this.fluidTanks.setCapacity(getFluidTankCapacity());

        this.readConfig();
    }

    // ========== IAEAppEngInventory — 物品 Storage 变更回调 ==========

    @Override
    public void saveChanges() {
        this.iHost.saveChanges();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        if (inv == this.itemStorage && slot >= 0 && this.isWorkingSlot != slot) {
            if (!added.isEmpty()) {
                iHost.onStackReturnNetwork(AEItemStack.fromItemStack(added));
            }
            final boolean had = this.hasWorkToDo();
            this.updatePlan(slot);
            final boolean now = this.hasWorkToDo();
            if (had != now) {
                try {
                    if (now) {
                        this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                    } else {
                        this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                    }
                } catch (final GridAccessException e) {
                    // :P
                }
            }
        } else if (inv == this.upgrades) {
            this.fluidTanks.setCapacity(getFluidTankCapacity());
            try {
                this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
            } catch (GridAccessException ignored) {
            }
            // 容量升级变化时，重算所有流体槽的 Plan
            for (int x = 0; x < NUMBER_OF_CONFIG_SLOTS; x++) {
                IAEStack<?> cfg = this.config.getAEStackInSlot(x);
                if (cfg instanceof IAEFluidStack) {
                    this.updatePlan(x);
                }
            }
        }
    }

    // ========== IAEFluidInventory — 流体 Tank 变更回调 ==========

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inv, int slot) {
        onFluidInventoryChanged(inv, slot, null, null, null);
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inventory, FluidStack added, FluidStack removed) {
        if (inventory == this.fluidTanks) {
            if (added != null) {
                iHost.onStackReturnNetwork(AEFluidStack.fromFluidStack(added));
            }
            this.saveChanges();
        }
    }

    @Override
    public void onFluidInventoryChanged(final IAEFluidTank inventory, final int slot, InvOperation operation,
            FluidStack added, FluidStack removed) {
        if (this.isWorkingSlot == slot) {
            return;
        }

        if (inventory == this.fluidTanks) {
            if (added != null) {
                iHost.onStackReturnNetwork(AEFluidStack.fromFluidStack(added));
            }
            this.saveChanges();

            final boolean had = this.hasWorkToDo();
            this.updatePlan(slot);
            final boolean now = this.hasWorkToDo();
            if (had != now) {
                try {
                    if (now) {
                        this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                    } else {
                        this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                    }
                } catch (final GridAccessException e) {
                    // :P
                }
            }
        }
    }

    // ========== Config 读取 ==========

    private void readConfig() {
        this.hasItemConfig = false;
        this.hasFluidConfig = false;

        for (int i = 0; i < NUMBER_OF_CONFIG_SLOTS; i++) {
            IAEStack<?> cfg = this.config.getAEStackInSlot(i);
            if (cfg instanceof IAEItemStack) {
                this.hasItemConfig = true;
            } else if (cfg instanceof IAEFluidStack) {
                this.hasFluidConfig = true;
            }
        }

        final boolean had = this.hasWorkToDo();
        for (int x = 0; x < NUMBER_OF_CONFIG_SLOTS; x++) {
            this.updatePlan(x);
        }
        final boolean has = this.hasWorkToDo();
        if (had != has) {
            try {
                if (has) {
                    this.gridProxy.getTick().alertDevice(this.gridProxy.getNode());
                } else {
                    this.gridProxy.getTick().sleepDevice(this.gridProxy.getNode());
                }
            } catch (final GridAccessException e) {
                // :P
            }
        }
        this.notifyNeighbors();
    }

    // ========== Plan 计划（统一） ==========

    private void updatePlan(final int slot) {
        final IAEStack<?> cfg = this.config.getAEStackInSlot(slot);

        if (cfg instanceof IAEItemStack itemCfg) {
            updateItemPlan(slot, itemCfg);
        } else if (cfg instanceof IAEFluidStack fluidCfg) {
            updateFluidPlan(slot, fluidCfg);
        } else {
            // Config 为空：清理 Storage 中残留的物品和流体
            final ItemStack storedItem = this.itemStorage.getStackInSlot(slot);
            final IAEFluidStack storedFluid = this.fluidTanks.getFluidInSlot(slot);

            if (!storedItem.isEmpty()) {
                final IAEItemStack work = AEItemStackType.INSTANCE.createStack(storedItem);
                this.requireWork[slot] = work.setStackSize(-work.getStackSize());
            } else if (storedFluid != null && storedFluid.getStackSize() > 0) {
                final IAEFluidStack work = storedFluid.copy();
                this.requireWork[slot] = work.setStackSize(-work.getStackSize());
            } else {
                this.requireWork[slot] = null;
            }
        }
    }

    // --- 物品 Plan ---
    private void updateItemPlan(final int slot, IAEItemStack req) {
        if (req.getStackSize() <= 0) {
            this.config.putAEStackInSlot(slot, null);
            // 递归重算（此时 cfg 变为 null，走清理逻辑）
            this.updatePlan(slot);
            return;
        }

        final ItemStack stored = this.itemStorage.getStackInSlot(slot);

        if (stored.isEmpty()) {
            this.requireWork[slot] = req.copy();
        } else if (req.isSameType(stored)) {
            if (req.getStackSize() == stored.getCount()) {
                this.requireWork[slot] = null;
            } else {
                this.requireWork[slot] = req.copy();
                ((IAEItemStack) this.requireWork[slot]).setStackSize(req.getStackSize() - stored.getCount());
            }
        } else {
            // 类型不匹配：先退回旧物品
            final IAEItemStack work = AEItemStackType.INSTANCE.createStack(stored);
            this.requireWork[slot] = work.setStackSize(-work.getStackSize());
        }
    }

    // --- 流体 Plan ---
    private void updateFluidPlan(final int slot, IAEFluidStack req) {
        final IAEFluidStack stored = this.fluidTanks.getFluidInSlot(slot);
        final int tankSize = getFluidTankCapacity();

        if (stored == null || stored.getStackSize() == 0) {
            this.requireWork[slot] = req.copy();
            ((IAEFluidStack) this.requireWork[slot]).setStackSize(tankSize);
        } else if (req.equals(stored)) {
            if (stored.getStackSize() == tankSize) {
                this.requireWork[slot] = null;
            } else {
                this.requireWork[slot] = req.copy();
                ((IAEFluidStack) this.requireWork[slot]).setStackSize(tankSize - stored.getStackSize());
            }
        } else {
            // 类型不匹配：先退回旧流体
            final IAEFluidStack work = stored.copy();
            this.requireWork[slot] = work.setStackSize(-work.getStackSize());
        }
    }

    private int getFluidTankCapacity() {
        return (int) (Math.pow(4, this.getInstalledUpgrades(Upgrades.CAPACITY) + 1) * Fluid.BUCKET_VOLUME);
    }

    // ========== Storage 执行 ==========

    private boolean updateStorage() {
        boolean didSomething = false;

        for (int x = 0; x < NUMBER_OF_CONFIG_SLOTS; x++) {
            if (this.requireWork[x] != null) {
                if (this.requireWork[x] instanceof IAEItemStack itemWork) {
                    didSomething = this.useItemPlan(x, itemWork) || didSomething;
                } else if (this.requireWork[x] instanceof IAEFluidStack) {
                    didSomething = this.useFluidPlan(x) || didSomething;
                }
            }
        }

        return didSomething;
    }

    private boolean useItemPlan(final int x, final IAEItemStack itemStack) {
        final InventoryAdaptor adaptor = this.getItemAdaptor(x);
        this.isWorkingSlot = x;

        boolean changed = false;
        try {
            this.destination = this.gridProxy.getStorage().getInventory(AEItemStackType.INSTANCE);
            final IEnergySource src = this.gridProxy.getEnergy();

            if (itemStack.getStackSize() < 0) {
                IAEItemStack toStore = itemStack.copy();
                toStore.setStackSize(-toStore.getStackSize());
                long diff = toStore.getStackSize();

                final ItemStack canExtract = adaptor.simulateRemove((int) diff, toStore.getDefinition(), null);
                if (canExtract.isEmpty()) {
                    changed = true;
                    throw new GridAccessException();
                }

                toStore = appeng.util.StorageHelper.poweredInsert(src, this.destination, toStore,
                        this.interfaceRequestSource);
                if (toStore != null) {
                    diff -= toStore.getStackSize();
                }

                if (diff != 0) {
                    changed = true;
                    final ItemStack removed = adaptor.removeItems((int) diff, ItemStack.EMPTY, null);
                    if (removed.isEmpty()) {
                        throw new IllegalStateException("bad attempt at managing inventory. ( removeItems )");
                    }
                }
            }

            if (this.craftingTracker.isBusy(x)) {
                changed = this.handleCrafting(x, adaptor, itemStack) || changed;
            } else if (itemStack.getStackSize() > 0) {
                ItemStack inputStack = itemStack.getCachedItemStack(itemStack.getStackSize());
                ItemStack remaining = adaptor.simulateAdd(inputStack);

                if (!remaining.isEmpty()) {
                    itemStack.setCachedItemStack(remaining);
                    changed = true;
                    throw new GridAccessException();
                }

                IAEItemStack storedStack = this.gridProxy.getStorage()
                        .getInventory(AEItemStackType.INSTANCE)
                        .getStorageList().findPrecise(itemStack);
                if (storedStack != null) {
                    final IAEItemStack acquired = appeng.util.StorageHelper.poweredExtraction(src, this.destination,
                            itemStack, this.interfaceRequestSource);
                    if (acquired != null) {
                        changed = true;
                        inputStack.setCount(Ints.saturatedCast(acquired.getStackSize()));
                        final ItemStack issue = adaptor.addItems(inputStack);
                        if (!issue.isEmpty()) {
                            throw new IllegalStateException("bad attempt at managing inventory. ( addItems )");
                        }
                    } else if (storedStack.isCraftable()) {
                        itemStack.setCachedItemStack(inputStack);
                        changed = this.handleCrafting(x, adaptor, itemStack) || changed;
                    }
                    if (acquired == null) {
                        itemStack.setCachedItemStack(inputStack);
                    }
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }

        if (changed) {
            this.updatePlan(x);
        }

        this.isWorkingSlot = -1;
        return changed;
    }

    private boolean useFluidPlan(final int slot) {
        IAEFluidStack work = (IAEFluidStack) this.requireWork[slot];
        this.isWorkingSlot = slot;

        boolean changed = false;
        try {
            final IMEInventory<IAEFluidStack> dest = this.gridProxy.getStorage()
                    .getInventory(AEFluidStackType.INSTANCE);
            final IEnergySource src = this.gridProxy.getEnergy();

            if (work.getStackSize() > 0) {
                if (this.fluidTanks.fill(slot, work.getFluidStack(), false) != work.getStackSize()) {
                    changed = true;
                } else if (this.gridProxy.getStorage()
                        .getInventory(AEFluidStackType.INSTANCE)
                        .getStorageList().findPrecise(work) != null) {
                    final IAEFluidStack acquired = appeng.util.StorageHelper.poweredExtraction(src, dest, work,
                            this.interfaceRequestSource);
                    if (acquired != null) {
                        changed = true;
                        final int filled = this.fluidTanks.fill(slot, acquired.getFluidStack(), true);
                        if (filled != acquired.getStackSize()) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( fill )");
                        }
                    }
                }
            } else if (work.getStackSize() < 0) {
                IAEFluidStack toStore = work.copy();
                toStore.setStackSize(-toStore.getStackSize());

                final FluidStack canExtract = this.fluidTanks.drain(slot, toStore.getFluidStack(), false);
                if (canExtract == null || canExtract.amount != toStore.getStackSize()) {
                    changed = true;
                } else {
                    IAEFluidStack notStored = appeng.util.StorageHelper.poweredInsert(src, dest, toStore,
                            this.interfaceRequestSource);
                    toStore.setStackSize(
                            toStore.getStackSize() - (notStored == null ? 0 : notStored.getStackSize()));

                    if (toStore.getStackSize() > 0) {
                        changed = true;
                        final FluidStack removed = this.fluidTanks.drain(slot, toStore.getFluidStack(), true);
                        if (removed == null || toStore.getStackSize() != removed.amount) {
                            throw new IllegalStateException("bad attempt at managing tanks. ( drain )");
                        }
                    }
                }
            }
        } catch (final GridAccessException e) {
            // :P
        }

        if (changed) {
            this.updatePlan(slot);
        }

        this.isWorkingSlot = -1;
        return changed;
    }

    private InventoryAdaptor getItemAdaptor(final int slot) {
        return new AdaptorItemHandler(((AppEngNetworkInventory) this.itemStorage).getBufferWrapper(slot));
    }

    private boolean handleCrafting(final int x, final InventoryAdaptor d, final IAEItemStack itemStack) {
        try {
            if (this.getInstalledUpgrades(Upgrades.CRAFTING) > 0 && itemStack != null) {
                return this.craftingTracker.handleCrafting(x, itemStack.getStackSize(), itemStack, d,
                        this.iHost.getTileEntity().getWorld(), this.gridProxy.getGrid(), this.gridProxy.getCrafting(),
                        this.mySource);
            }
        } catch (final GridAccessException e) {
            // :P
        }
        return false;
    }

    // ========== IGridTickable ==========

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return new TickingRequest(TickRates.Interface.getMin(), TickRates.Interface.getMax(),
                !this.hasWorkToDo(), true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (!this.gridProxy.isActive()) {
            return TickRateModulation.SLEEP;
        }

        final boolean couldDoWork = this.updateStorage();
        return this.hasWorkToDo() ? (couldDoWork ? TickRateModulation.URGENT : TickRateModulation.SLOWER)
                : TickRateModulation.SLEEP;
    }

    private boolean hasWorkToDo() {
        for (final IAEStack<?> requiredWork : this.requireWork) {
            if (requiredWork != null) {
                return true;
            }
        }
        return false;
    }

    // ========== IStorageMonitorable ==========

    @Override
    public <T extends IAEStack<T>> IMEMonitor<T> getInventory(IAEStackType<T> type) {
        if (type == AEItemStackType.INSTANCE) {
            if (this.hasItemConfig()) {
                if (resetItemConfigCache) {
                    resetItemConfigCache = false;
                    itemConfigCachedHandler = new ItemInterfaceInventory(this);
                }
                return (IMEMonitor<T>) itemConfigCachedHandler;
            }
            return (IMEMonitor<T>) this.items;
        } else if (type == AEFluidStackType.INSTANCE) {
            if (this.hasFluidConfig()) {
                if (resetFluidConfigCache) {
                    resetFluidConfigCache = false;
                    fluidConfigCachedHandler = new FluidInterfaceInventory(this);
                }
                return (IMEMonitor<T>) fluidConfigCachedHandler;
            }
            return (IMEMonitor<T>) this.fluids;
        }
        return null;
    }

    public IStorageMonitorable getMonitorable(final IActionSource src) {
        if (Platform.canAccess(this.gridProxy, src)) {
            return this;
        }
        return null;
    }

    // ========== IInventoryDestination ==========

    @Override
    public boolean canInsert(final ItemStack stack) {
        final IAEItemStack out = this.destination.injectItems(
                AEItemStackType.INSTANCE.createStack(stack), Actionable.SIMULATE, null);
        if (out == null) {
            return true;
        }
        return out.getStackSize() != stack.getCount();
    }

    // ========== Capability ==========

    public boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing) {
        return capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                || capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capabilityClass, EnumFacing facing) {
        if (capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.fluidTanks;
        } else if (capabilityClass == Capabilities.STORAGE_MONITORABLE_ACCESSOR) {
            return (T) this.accessor;
        }
        return null;
    }

    // ========== 网格变更 ==========

    public void gridChanged() {
        try {
            this.items.setInternal(this.gridProxy.getStorage().getInventory(AEItemStackType.INSTANCE));
            this.fluids.setInternal(this.gridProxy.getStorage().getInventory(AEFluidStackType.INSTANCE));
        } catch (final GridAccessException gae) {
            this.items.setInternal(new NullInventory<>());
            this.fluids.setInternal(new NullInventory<>());
        }
        this.notifyNeighbors();
    }

    public void notifyNeighbors() {
        if (this.gridProxy.isActive()) {
            try {
                this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
            } catch (final GridAccessException e) {
                // :P
            }
        }

        final TileEntity te = this.iHost.getTileEntity();
        if (te != null && te.getWorld() != null) {
            appeng.util.WorldHelper.notifyBlocksOfNeighbors(te.getWorld(), te.getPos());
        }
    }

    // ========== 优先级 ==========

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(final int newValue) {
        this.priority = newValue;
    }

    // ========== 终端名称 ==========

    public String getTermName() {
        final TileEntity hostTile = this.iHost.getTileEntity();
        final net.minecraft.world.World hostWorld = hostTile.getWorld();

        if (((ICustomNameObject) this.iHost).hasCustomInventoryName()) {
            return ((ICustomNameObject) this.iHost).getCustomInventoryName();
        }

        final java.util.EnumSet<EnumFacing> possibleDirections = this.iHost.getTargets();
        for (final EnumFacing direction : possibleDirections) {
            final net.minecraft.util.math.BlockPos targ = hostTile.getPos().offset(direction);
            final TileEntity directedTile = hostWorld.getTileEntity(targ);
            if (directedTile == null) {
                continue;
            }

            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(directedTile, direction.getOpposite());
            if (adaptor != null) {
                if (!adaptor.hasSlots()) {
                    continue;
                }

                final net.minecraft.block.state.IBlockState directedBlockState = hostWorld.getBlockState(targ);
                final net.minecraft.block.Block directedBlock = directedBlockState.getBlock();
                ItemStack what = new ItemStack(directedBlock, 1,
                        directedBlock.getMetaFromState(directedBlockState));

                if (Platform.GTLoaded) {
                    final gregtech.api.bridge.IGTMachineHelper machineHelper = gregtech.api.bridge.GTBridge
                            .getMachineHelper();
                    if (machineHelper != null && machineHelper.isGTMachineBlock(directedBlock)) {
                        final gregtech.api.bridge.IGTMachineInfo machineInfo = machineHelper.getMachineInfo(
                                directedTile.getWorld(), directedTile.getPos());
                        if (machineInfo != null) {
                            final gregtech.api.bridge.IGTMachineInfo controller = machineInfo
                                    .getMultiblockController();
                            if (controller != null) {
                                return controller.getMetaFullName();
                            }
                            return machineInfo.getMetaFullName();
                        }
                    }
                }

                if (what.getItem() != net.minecraft.init.Items.AIR) {
                    return what.getItem().getTranslationKey(what);
                }
            }
        }
        return "Nothing";
    }

    public long getSortValue() {
        final TileEntity te = this.iHost.getTileEntity();
        return ((long) te.getPos().getZ() << 24) ^ ((long) te.getPos().getX() << 8) ^ te.getPos().getY();
    }

    // ========== 掉落物 ==========

    public void addDrops(final List<ItemStack> drops) {
        for (final ItemStack is : this.upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    // ========== IConfigManagerHost ==========

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.getInstalledUpgrades(Upgrades.CRAFTING) == 0) {
            this.craftingTracker.cancel();
        }
        this.iHost.saveChanges();
    }

    // ========== IUpgradeableHost ==========

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        if (this.upgrades == null) {
            return 0;
        }
        return this.upgrades.getInstalledUpgrades(u);
    }

    @Override
    public TileEntity getTile() {
        return (TileEntity) (this.iHost instanceof TileEntity ? this.iHost : null);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("itemStorage")) {
            return this.itemStorage;
        }
        if (name.equals("upgrades")) {
            return this.upgrades;
        }
        return null;
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        return null;
    }

    // ========== Getter ==========

    public AECableType getCableConnectionType() {
        return AECableType.SMART;
    }

    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this.iHost.getTileEntity());
    }

    public IAEStackInventory getConfig() {
        return this.config;
    }

    public IItemHandler getItemStorage() {
        return this.itemStorage;
    }

    public IAEFluidTank getFluidTanks() {
        return this.fluidTanks;
    }

    public AENetworkProxy getProxy() {
        return this.gridProxy;
    }

    public IUpgradeableHost getHost() {
        if (this.iHost instanceof IUpgradeableHost) {
            return (IUpgradeableHost) this.iHost;
        }
        return null;
    }

    public boolean hasItemConfig() {
        return this.hasItemConfig;
    }

    public boolean hasFluidConfig() {
        return this.hasFluidConfig;
    }

    public boolean hasConfig() {
        return this.hasItemConfig || this.hasFluidConfig;
    }

    public boolean sameGrid(final appeng.api.networking.IGrid grid) throws GridAccessException {
        return grid == this.gridProxy.getGrid();
    }

    // ========== 内部类：InterfaceRequestSource ==========

    private class InterfaceRequestSource extends MachineSource {
        private final InterfaceRequestContext context;

        InterfaceRequestSource(IActionHost v) {
            super(v);
            this.context = new InterfaceRequestContext();
        }

        @Override
        public <T> Optional<T> context(Class<T> key) {
            if (key == InterfaceRequestContext.class) {
                return (Optional<T>) Optional.of(this.context);
            }
            return super.context(key);
        }
    }

    private class InterfaceRequestContext implements Comparable<Integer> {
        @Override
        public int compareTo(Integer o) {
            return Integer.compare(InterfaceLogic.this.priority, o);
        }
    }

    // ========== 内部类：物品配置模式的 MEInventory 包装 ==========

    private static class ItemInterfaceInventory extends appeng.me.storage.MEMonitorIInventoryHandler {
        ItemInterfaceInventory(final InterfaceLogic logic) {
            super(logic.itemStorage);
        }

        @Override
        public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean isInterface = context.isPresent();
            if (isInterface) {
                return input;
            }
            return super.injectItems(input, type, src);
        }

        @Override
        public IAEItemStack extractItems(final IAEItemStack request, final Actionable type, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean hasLowerOrEqualPriority = context
                    .map(c -> c.compareTo(0) <= 0).orElse(false);
            if (hasLowerOrEqualPriority) {
                return null;
            }
            return super.extractItems(request, type, src);
        }
    }

    // ========== 内部类：流体配置模式的 MEInventory 包装 ==========

    private class FluidInterfaceInventory extends MEMonitorIFluidHandler {
        FluidInterfaceInventory(final InterfaceLogic logic) {
            super(logic.fluidTanks);
        }

        @Override
        public IAEFluidStack injectItems(final IAEFluidStack input, final Actionable type, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean isInterface = context.isPresent();
            if (isInterface) {
                return input;
            }
            return super.injectItems(input, type, src);
        }

        @Override
        public IAEFluidStack extractItems(final IAEFluidStack request, final Actionable type, final IActionSource src) {
            final Optional<InterfaceRequestContext> context = src.context(InterfaceRequestContext.class);
            final boolean hasLowerOrEqualPriority = context
                    .map(c -> c.compareTo(InterfaceLogic.this.priority) <= 0).orElse(false);
            if (hasLowerOrEqualPriority) {
                return null;
            }
            return super.extractItems(request, type, src);
        }
    }
}

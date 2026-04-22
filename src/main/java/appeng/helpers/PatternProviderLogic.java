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

import static appeng.api.config.LockCraftingMode.LOCK_UNTIL_PULSE;
import static appeng.api.config.LockCraftingMode.LOCK_UNTIL_RESULT;
import static appeng.helpers.ItemStackHelper.stackFromNBT;
import static appeng.helpers.ItemStackHelper.stackToNBT;

import java.util.*;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSet;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import de.ellpeck.actuallyadditions.api.tile.IPhantomTile;

import gregtech.api.bridge.GTBridge;
import gregtech.api.bridge.IGTMachineHelper;
import gregtech.api.bridge.IGTMachineInfo;

import appeng.api.config.*;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.tiles.ICraftingMachine;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkCraftingPatternChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartHost;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.capabilities.Capabilities;
import appeng.core.AELog;
import appeng.core.settings.TickRates;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.me.helpers.MachineSource;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.parts.automation.UpgradeInventory;
import appeng.parts.misc.PartInterface;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.networking.TileCableBus;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.*;
import appeng.util.item.AEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStackType;
import appeng.fluids.util.AEFluidStackType;
import appeng.api.storage.IMEMonitor;

/**
 * 样板供应器核心逻辑。
 *
 * 从 {@link DualityInterface} 中提取的所有样板存储、样板推送、合成锁定、
 * 合成跟踪、优先级和终端名称功能。
 *
 * 对应高版本 AE2 的 {@code PatternProviderLogic}。
 *
 * 宿主方块/部件通过实现 {@link IPatternProviderHost} 并持有此对象来获得样板供应能力。
 */
public class PatternProviderLogic
        implements IGridTickable, IConfigManagerHost, ICraftingProvider, IUpgradeableHost, IAEAppEngInventory {

    // ========== 常量 ==========
    public static final int NUMBER_OF_PATTERN_SLOTS = 36;

    private static final Collection<Block> BAD_BLOCKS = new HashSet<>(100);

    // ========== 字段 ==========

    // --- 核心引用 ---
    private final AENetworkProxy gridProxy;
    private final IPatternProviderHost iHost;
    private final IActionSource mySource;

    // --- 样板存储 ---
    private final AppEngInternalInventory patterns = new AppEngInternalInventory(this, NUMBER_OF_PATTERN_SLOTS, 1);
    private List<ICraftingPatternDetails> craftingList = null;

    // --- 网络直通包装器（外部机器返回的合成产物直接注入 ME 网络） ---
    private final ReturnItemHandler returnItemHandler = new ReturnItemHandler();
    private final ReturnFluidHandler returnFluidHandler = new ReturnFluidHandler();

    // --- 合成跟踪 ---
    private final MultiCraftingTracker craftingTracker;

    // --- 升级 ---
    private final UpgradeInventory upgrades;

    // --- 配置管理 ---
    private final ConfigManager cm = new ConfigManager(this);

    // --- 推送等待队列 ---
    private List<IAEStack<?>> waitingToSend = null;
    private EnumMap<EnumFacing, List<ItemStack>> waitingToSendFacing = null;
    private EnumSet<EnumFacing> visitedFaces = EnumSet.noneOf(EnumFacing.class);

    // --- 优先级 ---
    private int priority;

    // --- 合成锁定 ---
    private YesNo redstoneState = YesNo.UNDECIDED;
    @Nullable
    private UnlockCraftingEvent unlockEvent;
    @Nullable
    private IAEStack<?> unlockStack;

    // ========== 构造 ==========

    public PatternProviderLogic(final AENetworkProxy networkProxy, final IPatternProviderHost host) {
        this.gridProxy = networkProxy;
        this.gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);

        this.upgrades = new StackUpgradeInventory(this.gridProxy.getMachineRepresentation(), this, 4);
        this.cm.registerSetting(Settings.BLOCK, YesNo.NO);
        this.cm.registerSetting(Settings.INTERFACE_TERMINAL, YesNo.YES);
        this.cm.registerSetting(Settings.UNLOCK, LockCraftingMode.NONE);

        this.iHost = host;
        this.craftingTracker = new MultiCraftingTracker(this.iHost, 9);

        this.mySource = new MachineSource(this.iHost);
    }

    // ========== NBT 读写 ==========

    public void writeToNBT(final NBTTagCompound data) {
        this.patterns.writeToNBT(data, "patterns");
        this.upgrades.writeToNBT(data, "upgrades");
        this.cm.writeToNBT(data);
        this.craftingTracker.writeToNBT(data);
        data.setInteger("priority", this.priority);

        // 合成锁定状态
        if (unlockEvent == UnlockCraftingEvent.PULSE) {
            data.setByte("unlockEvent", (byte) 1);
        } else if (unlockEvent == UnlockCraftingEvent.RESULT) {
            if (unlockStack != null) {
                data.setByte("unlockEvent", (byte) 2);
                NBTTagCompound unlockStackTag = new NBTTagCompound();
                unlockStack.writeToNBTGeneric(unlockStackTag);
                data.setTag("unlockStack", unlockStackTag);
            } else {
                AELog.error("Saving PatternProvider {}, locked waiting for stack, but stack is null!", iHost);
            }
        }

        // 等待推送的物品
        final NBTTagList waitingToSendTag = new NBTTagList();
        if (this.waitingToSend != null) {
            for (final IAEStack<?> is : this.waitingToSend) {
                final NBTTagCompound itemNBT = is.toNBTGeneric();
                waitingToSendTag.appendTag(itemNBT);
            }
        }
        data.setTag("waitingToSend", waitingToSendTag);

        // 面向推送队列
        NBTTagCompound sidedWaitList = new NBTTagCompound();
        if (this.waitingToSendFacing != null) {
            for (EnumFacing s : this.iHost.getTargets()) {
                NBTTagList waitingListSided = new NBTTagList();
                if (this.waitingToSendFacing.containsKey(s)) {
                    for (final ItemStack is : this.waitingToSendFacing.get(s)) {
                        final NBTTagCompound itemNBT = stackToNBT(is);
                        waitingListSided.appendTag(itemNBT);
                    }
                    sidedWaitList.setTag(s.name(), waitingListSided);
                }
            }
        }
        data.setTag("sidedWaitList", sidedWaitList);
    }

    public void readFromNBT(final NBTTagCompound data) {
        // 等待推送队列
        this.waitingToSend = null;
        final NBTTagList waitingList = data.getTagList("waitingToSend", 10);
        if (waitingList != null) {
            for (int x = 0; x < waitingList.tagCount(); x++) {
                final NBTTagCompound c = waitingList.getCompoundTagAt(x);
                if (c != null) {
                    IAEStack<?> stack = c.hasKey("StackType") ? IAEStack.fromNBTGeneric(c) : null;
                    if (stack != null) {
                        this.addToSendList(stack);
                    } else {
                        final ItemStack is = stackFromNBT(c);
                        this.addToSendList(is);
                    }
                }
            }
        }

        // 面向推送队列
        this.waitingToSendFacing = null;
        final NBTTagCompound waitingListSided = data.getCompoundTag("sidedWaitList");
        for (EnumFacing s : EnumFacing.values()) {
            if (waitingListSided.hasKey(s.name())) {
                NBTTagList w = waitingListSided.getTagList(s.name(), 10);
                for (int x = 0; x < w.tagCount(); x++) {
                    final NBTTagCompound c = w.getCompoundTagAt(x);
                    if (c != null) {
                        final ItemStack is = stackFromNBT(c);
                        this.addToSendListFacing(is, EnumFacing.byIndex(s.getIndex()));
                    }
                }
            }
        }

        this.craftingTracker.readFromNBT(data);

        // 修复升级槽大小不匹配
        NBTTagCompound up = data.getCompoundTag("upgrades");
        if (up.hasKey("Size") && up.getInteger("Size") != this.upgrades.getSlots()) {
            up.setInteger("Size", this.upgrades.getSlots());
            this.upgrades.writeToNBT(up, "upgrades");
        }
        this.upgrades.readFromNBT(data, "upgrades");

        // 样板槽
        NBTTagCompound pa = data.getCompoundTag("patterns");
        if (pa.hasKey("Size") && pa.getInteger("Size") != this.patterns.getSlots()) {
            pa.setInteger("Size", this.patterns.getSlots());
            this.upgrades.writeToNBT(pa, "patterns");
        }
        this.patterns.readFromNBT(data, "patterns");

        this.priority = data.getInteger("priority");
        this.cm.readFromNBT(data);
        this.updateCraftingList();

        // 合成锁定状态
        var unlockEventType = data.getByte("unlockEvent");
        this.unlockEvent = switch (unlockEventType) {
            case 0 -> null;
            case 1 -> UnlockCraftingEvent.PULSE;
            case 2 -> UnlockCraftingEvent.RESULT;
            default -> {
                AELog.error("Unknown unlock event type {} in NBT for PatternProvider: {}", unlockEventType, data);
                yield null;
            }
        };

        if (this.unlockEvent == UnlockCraftingEvent.RESULT) {
            this.unlockStack = IAEStack.fromNBTGeneric(data.getCompoundTag("unlockStack"));
            if (this.unlockStack == null) {
                AELog.error("Could not load unlock stack for PatternProvider from NBT: {}", data);
            }
        } else {
            this.unlockStack = null;
        }
    }

    // ========== IAEAppEngInventory ==========

    @Override
    public void saveChanges() {
        this.iHost.saveChanges();
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removed, final ItemStack added) {
        if (inv == this.patterns && (!removed.isEmpty() || !added.isEmpty())) {
            this.updateCraftingList();
        }
    }

    // ========== 样板管理 ==========

    /**
     * 更新合成样板列表。扫描样板槽，与现有列表做增量比较。
     */
    private void updateCraftingList() {
        final Boolean[] accountedFor = new Boolean[this.patterns.getSlots()];
        Arrays.fill(accountedFor, false);

        if (!this.gridProxy.isReady()) {
            return;
        }

        if (this.craftingList != null) {
            final Iterator<ICraftingPatternDetails> i = this.craftingList.iterator();
            while (i.hasNext()) {
                final ICraftingPatternDetails details = i.next();
                boolean found = false;
                for (int x = 0; x < accountedFor.length; x++) {
                    final ItemStack is = this.patterns.getStackInSlot(x);
                    if (details.getPattern() == is) {
                        accountedFor[x] = found = true;
                    }
                }
                if (!found) {
                    i.remove();
                }
            }
        }

        for (int x = 0; x < accountedFor.length; x++) {
            if (!accountedFor[x]) {
                this.addToCraftingList(this.patterns.getStackInSlot(x));
            }
        }

        try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
        } catch (GridAccessException e) {
            e.printStackTrace();
        }
    }

    private void addToCraftingList(final ItemStack is) {
        if (is.isEmpty()) {
            return;
        }

        if (is.getItem() instanceof ICraftingPatternItem cpi) {
            final ICraftingPatternDetails details = cpi.getPatternForItemWithNest(is,
                    this.iHost.getTileEntity().getWorld());
            if (details != null) {
                if (this.craftingList == null) {
                    this.craftingList = new ArrayList<>();
                }
                this.craftingList.add(details);
            }
        }
    }

    /**
     * 掉落超出容量的样板（升级卡减少后）。
     */
    public void dropExcessPatterns() {
        IItemHandler patternsInv = getPatterns();

        List<ItemStack> dropList = new ArrayList<>();
        for (int invSlot = 0; invSlot < patternsInv.getSlots(); invSlot++) {
            if (invSlot > 8 + this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION) * 9) {
                ItemStack is = patternsInv.getStackInSlot(invSlot);
                if (is.isEmpty()) {
                    continue;
                }
                dropList.add(patternsInv.extractItem(invSlot, Integer.MAX_VALUE, false));
            }
        }
        if (dropList.size() > 0) {
            World world = this.getLocation().getWorld();
            BlockPos blockPos = this.getLocation().getPos();
            appeng.util.WorldHelper.spawnDrops(world, blockPos, dropList);
        }

        this.gridProxy.setIdlePowerUsage(Math.pow(4, (this.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION))));
    }

    public IItemHandler getPatterns() {
        return this.patterns;
    }

    // ========== 推送队列管理 ==========

    private void addToSendList(final IAEStack<?> is) {
        if (is == null || is.getStackSize() <= 0) {
            return;
        }
        if (this.waitingToSend == null) {
            this.waitingToSend = new ArrayList<>();
        }
        this.waitingToSend.add(is);

        try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void addToSendList(final ItemStack is) {
        if (is.isEmpty()) {
            return;
        }
        this.addToSendList(AEItemStack.fromItemStack(is));
    }

    private void addToSendListFacing(final ItemStack is, EnumFacing f) {
        if (is.isEmpty()) {
            return;
        }

        // 尝试将含流体的 ItemStack 转换为 IAEFluidStack
        final IAEStack<?> converted = tryConvertToFluidStack(is);

        if (this.waitingToSendFacing == null) {
            this.waitingToSendFacing = new EnumMap<>(EnumFacing.class);
        }
        this.waitingToSendFacing.computeIfAbsent(f, k -> new ArrayList<>());

        if (converted instanceof IAEFluidStack) {
            // 流体走统一的 waitingToSend 队列
            if (this.waitingToSend == null) {
                this.waitingToSend = new ArrayList<>();
            }
            this.waitingToSend.add(converted);
        } else {
            this.waitingToSendFacing.get(f).add(is);
        }

        try {
            this.gridProxy.getTick().wakeDevice(this.gridProxy.getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    /**
     * 尝试将 ItemStack 转换为 IAEFluidStack。
     * 支持 FluidDummyItem 和通用 IFluidHandlerItem。
     */
    @Nullable
    private static IAEStack<?> tryConvertToFluidStack(final ItemStack is) {
        if (is.isEmpty()) {
            return null;
        }

        // FluidDummyItem：流体占位物品（由 asItemStackRepresentation 产生）
        if (is.getItem() instanceof appeng.fluids.items.FluidDummyItem fluidDummy) {
            net.minecraftforge.fluids.FluidStack fs = fluidDummy.getFluidStack(is);
            if (fs != null) {
                return AEFluidStack.fromFluidStack(fs);
            }
        }

        net.minecraftforge.fluids.capability.IFluidHandlerItem fluidHandler =
                net.minecraftforge.fluids.FluidUtil.getFluidHandler(is.copy());
        if (fluidHandler != null) {
            net.minecraftforge.fluids.FluidStack drained = fluidHandler.drain(Integer.MAX_VALUE, false);
            if (drained != null && drained.amount > 0) {
                return AEFluidStack.fromFluidStack(drained);
            }
        }
        return null;
    }

    private boolean hasItemsToSend() {
        return this.waitingToSend != null && !this.waitingToSend.isEmpty();
    }

    private boolean hasItemsToSendFacing() {
        if (waitingToSendFacing != null) {
            for (EnumFacing enumFacing : waitingToSendFacing.keySet()) {
                if (!waitingToSendFacing.get(enumFacing).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ========== 推送物品到邻居 ==========

    /**
     * 推送 waitingToSend 队列中的物品到多个方向。
     */
    private void pushItemsOut(final EnumSet<EnumFacing> possibleDirections) {
        if (!this.hasItemsToSend()) {
            return;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        final Iterator<IAEStack<?>> i = this.waitingToSend.iterator();
        while (i.hasNext()) {
            IAEStack<?> whatToSend = i.next();

            for (final EnumFacing s : possibleDirections) {
                final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
                if (te == null) {
                    continue;
                }

                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                if (ad != null) {
                    final IAEStack<?> result = ad.addStack(whatToSend);
                    if (result == null || result.getStackSize() <= 0) {
                        whatToSend = null;
                    } else {
                        whatToSend = result;
                    }
                    if (whatToSend == null) {
                        break;
                    }
                }
            }

            if (whatToSend == null || whatToSend.getStackSize() <= 0) {
                i.remove();
            }
        }

        if (this.waitingToSend.isEmpty()) {
            this.waitingToSend = null;
        }
    }

    /**
     * 推送 waitingToSendFacing 队列中特定方向的物品。
     */
    private void pushItemsOut(final EnumFacing s) {
        if (this.waitingToSendFacing == null) {
            return;
        }

        final List<ItemStack> facingQueue = this.waitingToSendFacing.get(s);
        if (facingQueue == null || facingQueue.isEmpty()) {
            return;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
        if (te == null) {
            return;
        }

        // 如果目标是接口，尝试通过网络注入
        if (te instanceof IInterfaceHost || (te instanceof TileCableBus
                && ((TileCableBus) te).getPart(s.getOpposite()) instanceof PartInterface)) {
            try {
                IInterfaceHost targetTE;
                if (te instanceof IInterfaceHost) {
                    targetTE = (IInterfaceHost) te;
                } else {
                    targetTE = (IInterfaceHost) ((TileCableBus) te).getPart(s.getOpposite());
                }

                if (!targetTE.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                    IStorageMonitorableAccessor mon = te.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR,
                            s.getOpposite());
                    if (mon != null) {
                        IStorageMonitorable sm = mon.getInventory(this.mySource);
                        if (sm != null && Platform.canAccess(targetTE.getInterfaceDuality().gridProxy, this.mySource)) {
                            IMEMonitor<IAEItemStack> inv = sm.getInventory(AEItemStackType.INSTANCE);
                            if (inv != null) {
                                final Iterator<ItemStack> iter = facingQueue.iterator();
                                while (iter.hasNext()) {
                                    ItemStack whatToSend = iter.next();
                                    final IAEItemStack result = inv.injectItems(AEItemStack.fromItemStack(whatToSend),
                                            appeng.api.config.Actionable.MODULATE, this.mySource);
                                    if (result != null) {
                                        whatToSend.setCount((int) result.getStackSize());
                                        whatToSend.setTagCompound(result.getDefinition().getTagCompound());
                                    } else {
                                        iter.remove();
                                    }
                                }
                                if (facingQueue.isEmpty()) {
                                    this.waitingToSendFacing.remove(s);
                                }
                            }
                        }
                    }
                } else {
                    return;
                }
            } catch (GridAccessException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        // 否则通过 InventoryAdaptor 直接推送
        final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());

        final Iterator<ItemStack> iter = facingQueue.iterator();
        while (iter.hasNext()) {
            ItemStack whatToSend = iter.next();
            if (ad != null) {
                final ItemStack result = ad.addItems(whatToSend);
                if (!result.isEmpty()) {
                    whatToSend.setCount(result.getCount());
                    whatToSend.setTagCompound(result.getTagCompound());
                } else {
                    iter.remove();
                }
            }
        }

        if (facingQueue.isEmpty()) {
            this.waitingToSendFacing.remove(s);
        }
    }

    // ========== ICraftingProvider — 样板推送 ==========

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        if (this.hasItemsToSend() || this.hasItemsToSendFacing() || !this.gridProxy.isActive()
                || !this.craftingList.contains(patternDetails)) {
            return false;
        }

        final TileEntity tile = this.iHost.getTileEntity();
        final World w = tile.getWorld();

        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        if (this.visitedFaces.isEmpty()) {
            this.visitedFaces = this.iHost.getTargets();
        }

        for (final EnumFacing s : visitedFaces) {
            final TileEntity te = w.getTileEntity(tile.getPos().offset(s));
            if (te == null) {
                visitedFaces.remove(s);
                continue;
            }

            // --- 尝试通过 IStorageMonitorableAccessor 推送 ---
            var mon = te.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, s.getOpposite());
            if (mon != null) {
                visitedFaces.remove(s);

                try {
                    IGridProxyable proxyable;
                    if (te instanceof IGridProxyable) {
                        proxyable = (IGridProxyable) te;
                    } else if (te instanceof IPartHost partHost) {
                        proxyable = (IGridProxyable) partHost.getPart(s.getOpposite());
                    } else {
                        continue;
                    }

                    if (proxyable.getProxy().getGrid() == this.gridProxy.getGrid()) {
                        continue;
                    }

                    IStorageMonitorable sm = mon.getInventory(this.mySource);
                    if (sm != null && Platform.canAccess(proxyable.getProxy(), this.mySource)) {
                        if (this.isBlocking() && !sm
                                .getInventory(AEItemStackType.INSTANCE)
                                .getStorageList().isEmpty()) {
                            continue;
                        } else {
                            IMEMonitor<IAEItemStack> inv = sm.getInventory(AEItemStackType.INSTANCE);

                            var allItemsCanBeInserted = true;
                            for (int x = 0; x < table.getSizeInventory(); x++) {
                                final ItemStack is = table.getStackInSlot(x);
                                if (is.isEmpty()) {
                                    continue;
                                }
                                IAEItemStack result = inv.injectItems(AEItemStack.fromItemStack(is),
                                        appeng.api.config.Actionable.SIMULATE, this.mySource);
                                if (result != null) {
                                    allItemsCanBeInserted = false;
                                    break;
                                }
                            }

                            if (!allItemsCanBeInserted) {
                                continue;
                            }

                            this.visitedFaces.clear();

                            // 检查 Pattern 是否含流体输入
                            if (pushPatternContents(patternDetails, table, s)) {
                                return true;
                            }
                        }
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
                continue;
            }

            // --- 尝试通过 ICraftingMachine 推送 ---
            if (te instanceof ICraftingMachine craftMachine) {
                if (craftMachine.acceptsPlans()) {
                    visitedFaces.remove(s);
                    if (craftMachine.pushPattern(patternDetails, table, s.getOpposite())) {
                        onPushPatternSuccess(patternDetails);
                        return true;
                    }
                    continue;
                }
            }

            // --- 尝试通过 InventoryAdaptor 推送 ---
            InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
            if (ad != null) {
                if (this.isBlocking()) {
                    IPhantomTile phantomTE;
                    if (Platform.isModLoaded("actuallyadditions") && te instanceof IPhantomTile) {
                        phantomTE = ((IPhantomTile) te);
                        if (phantomTE.hasBoundPosition()) {
                            TileEntity phantom = w.getTileEntity(phantomTE.getBoundPosition());
                            if (NonBlockingItems.INSTANCE.getMap()
                                    .containsKey(w.getBlockState(phantomTE.getBoundPosition()).getBlock()
                                            .getRegistryName().getNamespace())) {
                                if (isCustomInvBlocking(phantom, s)) {
                                    visitedFaces.remove(s);
                                    continue;
                                }
                            }
                        }
                    } else if (NonBlockingItems.INSTANCE.getMap().containsKey(
                            w.getBlockState(tile.getPos().offset(s)).getBlock().getRegistryName().getNamespace())) {
                        if (isCustomInvBlocking(te, s)) {
                            visitedFaces.remove(s);
                            continue;
                        }
                    } else if (invIsBlocked(ad)) {
                        visitedFaces.remove(s);
                        continue;
                    }
                }

                if (this.acceptsItems(ad, table)) {
                    this.visitedFaces.clear();

                    // 检查 Pattern 是否含流体输入并推送
                    if (pushPatternContents(patternDetails, table, s)) {
                        return true;
                    }
                }
            }
            visitedFaces.remove(s);
        }
        return false;
    }

    /**
     * 推送样板内容（物品/流体）并处理锁定。
     * 从 {@link MEInventoryCrafting} 获取泛型栈信息，根据类型分流推送。
     */
    private boolean pushPatternContents(final ICraftingPatternDetails patternDetails,
            final InventoryCrafting table, final EnumFacing s) {
        // 从 MEInventoryCrafting 直接获取泛型栈，根据类型分流推送
        boolean hasNonItemInputs = false;
        if (table instanceof MEInventoryCrafting meTable) {
            for (int x = 0; x < meTable.getSizeInventory(); x++) {
                final IAEStack<?> aeStack = meTable.getAEStackInSlot(x);
                if (aeStack != null && !(aeStack instanceof IAEItemStack)) {
                    hasNonItemInputs = true;
                    break;
                }
            }
        }

        if (hasNonItemInputs && table instanceof MEInventoryCrafting meTable) {
            // 含流体/非物品输入：使用泛型栈直接发送
            for (int x = 0; x < meTable.getSizeInventory(); x++) {
                final IAEStack<?> aeStack = meTable.getAEStackInSlot(x);
                if (aeStack != null) {
                    this.addToSendList(aeStack.copy());
                }
            }
        } else {
            // 纯物品输入：使用 ItemStack 按面发送
            for (int x = 0; x < table.getSizeInventory(); x++) {
                final ItemStack is = table.getStackInSlot(x);
                if (!is.isEmpty()) {
                    addToSendListFacing(is, s);
                }
            }
        }

        onPushPatternSuccess(patternDetails);

        if (hasNonItemInputs) {
            pushItemsOut(EnumSet.of(s));
        } else {
            pushItemsOut(s);
        }
        return true;
    }

    @Override
    public boolean isBusy() {
        boolean busy = false;

        if (this.hasItemsToSend() || hasItemsToSendFacing()) {
            return true;
        }

        if (this.isBlocking()) {
            final EnumSet<EnumFacing> possibleDirections = this.iHost.getTargets();
            final TileEntity tile = this.iHost.getTileEntity();
            final World w = tile.getWorld();

            boolean allAreBusy = true;

            for (final EnumFacing s : possibleDirections) {
                final TileEntity te = w.getTileEntity(tile.getPos().offset(s));

                // 检查接口类邻居
                if (te instanceof IInterfaceHost || (te instanceof TileCableBus
                        && ((TileCableBus) te).getPart(s.getOpposite()) instanceof PartInterface)) {
                    try {
                        IInterfaceHost targetTE;
                        if (te instanceof IInterfaceHost) {
                            targetTE = (IInterfaceHost) te;
                        } else {
                            targetTE = (IInterfaceHost) ((TileCableBus) te).getPart(s.getOpposite());
                        }

                        if (targetTE.getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                            continue;
                        } else {
                            IStorageMonitorableAccessor monAccessor = te
                                    .getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, s.getOpposite());
                            if (monAccessor != null) {
                                IStorageMonitorable sm = monAccessor.getInventory(this.mySource);
                                if (sm != null && Platform.canAccess(targetTE.getInterfaceDuality().gridProxy,
                                        this.mySource)) {
                                    if (sm.getInventory(AEItemStackType.INSTANCE)
                                            .getStorageList().isEmpty()) {
                                        allAreBusy = false;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (final GridAccessException e) {
                        continue;
                    }
                    continue;
                }

                // 检查普通 InventoryAdaptor 邻居
                final InventoryAdaptor ad = InventoryAdaptor.getAdaptor(te, s.getOpposite());
                if (ad != null) {
                    if (Platform.isModLoaded("actuallyadditions") && Platform.GTLoaded
                            && te instanceof IPhantomTile phantomTE) {
                        if (phantomTE.hasBoundPosition()) {
                            TileEntity phantom = w.getTileEntity(phantomTE.getBoundPosition());
                            if (NonBlockingItems.INSTANCE.getMap()
                                    .containsKey(w.getBlockState(phantomTE.getBoundPosition()).getBlock()
                                            .getRegistryName().getNamespace())) {
                                if (!isCustomInvBlocking(phantom, s)) {
                                    allAreBusy = false;
                                    break;
                                }
                            }
                        }
                    } else if (NonBlockingItems.INSTANCE.getMap().containsKey(
                            w.getBlockState(tile.getPos().offset(s)).getBlock().getRegistryName().getNamespace())) {
                        if (!isCustomInvBlocking(te, s)) {
                            allAreBusy = false;
                            break;
                        }
                    } else {
                        if (!invIsBlocked(ad)) {
                            allAreBusy = false;
                            break;
                        }
                    }
                }
            }
            busy = allAreBusy;
        }
        return busy;
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        if (this.gridProxy.isActive() && this.craftingList != null) {
            for (final ICraftingPatternDetails details : this.craftingList) {
                details.setPriority(this.priority);
                craftingTracker.addCraftingOption(this, details);
            }
        }
    }

    // ========== 合成锁定 ==========

    public void resetCraftingLock() {
        if (unlockEvent != null) {
            unlockEvent = null;
            unlockStack = null;
            saveChanges();
        }
    }

    private void onPushPatternSuccess(ICraftingPatternDetails pattern) {
        resetCraftingLock();

        LockCraftingMode lockMode = (LockCraftingMode) cm.getSetting(Settings.UNLOCK);
        switch (lockMode) {
            case LOCK_UNTIL_PULSE -> {
                unlockEvent = UnlockCraftingEvent.PULSE;
                saveChanges();
            }
            case LOCK_UNTIL_RESULT -> {
                unlockEvent = UnlockCraftingEvent.RESULT;
                unlockStack = pattern.getAEOutputs()[0].copy();
                saveChanges();
            }
        }
    }

    /**
     * 获取合成锁定原因。
     *
     * @return {@link LockCraftingMode#NONE} 表示没有锁定
     */
    public LockCraftingMode getCraftingLockedReason() {
        var lockMode = cm.getSetting(Settings.UNLOCK);
        if (lockMode == LockCraftingMode.LOCK_WHILE_LOW && !getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_LOW;
        } else if (lockMode == LockCraftingMode.LOCK_WHILE_HIGH && getRedstoneState()) {
            return LockCraftingMode.LOCK_WHILE_HIGH;
        } else if (unlockEvent != null) {
            switch (unlockEvent) {
                case PULSE -> {
                    return LOCK_UNTIL_PULSE;
                }
                case RESULT -> {
                    return LOCK_UNTIL_RESULT;
                }
            }
        }
        return LockCraftingMode.NONE;
    }

    @Nullable
    public IAEStack<?> getUnlockStack() {
        return unlockStack;
    }

    public void onStackReturnedToNetwork(IAEStack<?> stack) {
        if (unlockEvent != UnlockCraftingEvent.RESULT) {
            return;
        }
        if (unlockStack == null) {
            AELog.error("PatternProvider was waiting for RESULT, but no result was set");
            unlockEvent = null;
        } else if (unlockStack.isSameType(stack)) {
            var remainingAmount = unlockStack.getStackSize() - stack.getStackSize();
            if (remainingAmount <= 0) {
                unlockEvent = null;
                unlockStack = null;
            } else {
                unlockStack.setStackSize(remainingAmount);
            }
        }
    }

    public void updateRedstoneState() {
        if (unlockEvent == UnlockCraftingEvent.PULSE && getRedstoneState()) {
            unlockEvent = null;
        } else {
            redstoneState = YesNo.UNDECIDED;
        }
        saveChanges();
    }

    private boolean getRedstoneState() {
        if (redstoneState == YesNo.UNDECIDED) {
            var entity = this.iHost.getTileEntity();
            redstoneState = entity.getWorld().getRedstonePowerFromNeighbors(entity.getPos()) > 0
                    ? YesNo.YES
                    : YesNo.NO;
        }
        return redstoneState == YesNo.YES;
    }

    // ========== 合成跟踪 ==========

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.craftingTracker.getRequestedJobs();
    }

    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack acquired,
            final appeng.api.config.Actionable mode) {
        // PatternProvider 没有 storage 槽，合成结果直接推入网络
        // 保留此方法以满足 ICraftingRequester 接口要求
        return acquired;
    }

    public void jobStateChange(final ICraftingLink link) {
        this.craftingTracker.jobStateChange(link);
    }

    // ========== 阻塞检查 ==========

    private boolean isBlocking() {
        return this.cm.getSetting(Settings.BLOCK) == YesNo.YES;
    }

    private boolean invIsBlocked(InventoryAdaptor inv) {
        return (inv.containsItems());
    }

    boolean isCustomInvBlocking(TileEntity te, EnumFacing s) {
        BlockingInventoryAdaptor blockingInventoryAdaptor = BlockingInventoryAdaptor.getAdaptor(te, s.getOpposite());
        return invIsCustomBlocking(blockingInventoryAdaptor);
    }

    private static boolean invIsCustomBlocking(BlockingInventoryAdaptor inv) {
        return (inv.containsBlockingItems());
    }

    private boolean acceptsItems(final InventoryAdaptor ad, final InventoryCrafting table) {
        for (int x = 0; x < table.getSizeInventory(); x++) {
            final ItemStack is = table.getStackInSlot(x);
            if (is.isEmpty()) {
                continue;
            }
            if (!ad.simulateAdd(is).isEmpty()) {
                return false;
            }
        }
        return true;
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

        // 推送残留物品
        if (this.hasItemsToSend()) {
            this.pushItemsOut(this.iHost.getTargets());
        }
        if (hasItemsToSendFacing()) {
            for (EnumFacing enumFacing : waitingToSendFacing.keySet()) {
                this.pushItemsOut(enumFacing);
            }
        }

        return this.hasWorkToDo() ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
    }

    private boolean hasWorkToDo() {
        return hasItemsToSend() || hasItemsToSendFacing();
    }

    // ========== 优先级 ==========

    public int getPriority() {
        return this.priority;
    }

    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.iHost.saveChanges();

        try {
            this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
        } catch (final GridAccessException e) {
            // :P
        }
    }

    // ========== 终端名称 ==========

    /**
     * 获取在接口终端中显示的名称。
     * 尝试根据面向的邻居方块来命名。
     */
    public String getTermName() {
        final TileEntity hostTile = this.iHost.getTileEntity();
        final World hostWorld = hostTile.getWorld();

        if (((ICustomNameObject) this.iHost).hasCustomInventoryName()) {
            return ((ICustomNameObject) this.iHost).getCustomInventoryName();
        }

        final EnumSet<EnumFacing> possibleDirections = this.iHost.getTargets();
        for (final EnumFacing direction : possibleDirections) {
            final BlockPos targ = hostTile.getPos().offset(direction);
            final TileEntity directedTile = hostWorld.getTileEntity(targ);

            if (directedTile == null) {
                continue;
            }

            if (directedTile instanceof IInterfaceHost) {
                try {
                    if (((IInterfaceHost) directedTile).getInterfaceDuality().sameGrid(this.gridProxy.getGrid())) {
                        continue;
                    }
                } catch (final GridAccessException e) {
                    continue;
                }
            }

            final InventoryAdaptor adaptor = InventoryAdaptor.getAdaptor(directedTile, direction.getOpposite());
            if (directedTile instanceof ICraftingMachine || adaptor != null) {
                if (adaptor != null && !adaptor.hasSlots()) {
                    continue;
                }

                final IBlockState directedBlockState = hostWorld.getBlockState(targ);
                final Block directedBlock = directedBlockState.getBlock();
                ItemStack what = new ItemStack(directedBlock, 1, directedBlock.getMetaFromState(directedBlockState));

                if (Platform.GTLoaded) {
                    final IGTMachineHelper machineHelper = GTBridge.getMachineHelper();
                    if (machineHelper != null && machineHelper.isGTMachineBlock(directedBlock)) {
                        final IGTMachineInfo machineInfo = machineHelper.getMachineInfo(
                                directedTile.getWorld(), directedTile.getPos());
                        if (machineInfo != null) {
                            final IGTMachineInfo controller = machineInfo.getMultiblockController();
                            if (controller != null) {
                                return controller.getMetaFullName();
                            }
                            return machineInfo.getMetaFullName();
                        }
                    }
                }

                try {
                    Vec3d from = new Vec3d(hostTile.getPos().getX() + 0.5, hostTile.getPos().getY() + 0.5,
                            hostTile.getPos().getZ() + 0.5);
                    from = from.add(direction.getXOffset() * 0.501, direction.getYOffset() * 0.501,
                            direction.getZOffset() * 0.501);
                    final Vec3d to = from.add(direction.getXOffset(), direction.getYOffset(),
                            direction.getZOffset());
                    final RayTraceResult mop = hostWorld.rayTraceBlocks(from, to, true);
                    if (mop != null && !BAD_BLOCKS.contains(directedBlock)) {
                        if (mop.getBlockPos().equals(directedTile.getPos())) {
                            final ItemStack g = directedBlock.getPickBlock(directedBlockState, mop, hostWorld,
                                    directedTile.getPos(), null);
                            if (!g.isEmpty()) {
                                what = g;
                            }
                        }
                    }
                } catch (final Throwable t) {
                    BAD_BLOCKS.add(directedBlock);
                }

                if (what.getItem() != Items.AIR) {
                    return what.getItem().getTranslationKey(what);
                }

                final Item item = Item.getItemFromBlock(directedBlock);
                if (item == Items.AIR) {
                    return directedBlock.getTranslationKey();
                }
            }
        }

        return "Nothing";
    }

    public long getSortValue() {
        final TileEntity te = this.iHost.getTileEntity();
        return ((long) te.getPos().getZ() << 24) ^ ((long) te.getPos().getX() << 8) ^ te.getPos().getY();
    }

    // ========== Capability — 直通 ME 网络的包装器 ==========

    public boolean hasCapability(Capability<?> capabilityClass, EnumFacing facing) {
        return capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY;
    }

    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capabilityClass, EnumFacing facing) {
        if (capabilityClass == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) this.returnItemHandler;
        } else if (capabilityClass == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return (T) this.returnFluidHandler;
        }
        return null;
    }

    // ========== 初始化 ==========

    public void initialize() {
        this.updateCraftingList();
    }

    public void gridChanged() {
        this.notifyNeighbors();
    }

    public void notifyNeighbors() {
        if (this.gridProxy.isActive()) {
            try {
                this.gridProxy.getGrid().postEvent(new MENetworkCraftingPatternChange(this, this.gridProxy.getNode()));
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

    // ========== 掉落物 ==========

    public void addDrops(final List<ItemStack> drops) {
        if (this.waitingToSend != null) {
            for (final IAEStack<?> aeStack : this.waitingToSend) {
                if (aeStack instanceof IAEItemStack) {
                    final ItemStack is = ((IAEItemStack) aeStack).createItemStack();
                    if (!is.isEmpty()) {
                        drops.add(is);
                    }
                }
            }
        }

        if (this.waitingToSendFacing != null) {
            for (List<ItemStack> itemList : waitingToSendFacing.values()) {
                for (final ItemStack is : itemList) {
                    if (!is.isEmpty()) {
                        drops.add(is);
                    }
                }
            }
        }

        for (final ItemStack is : this.upgrades) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }

        for (final ItemStack is : this.patterns) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    // ========== Getter/Setter ==========

    public AECableType getCableConnectionType() {
        return AECableType.SMART;
    }

    public appeng.api.util.DimensionalCoord getLocation() {
        return new appeng.api.util.DimensionalCoord(this.iHost.getTileEntity());
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.cm;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (newValue == LockCraftingMode.NONE) {
            resetCraftingLock();
        }
        this.iHost.saveChanges();
    }

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
        if (name.equals("patterns")) {
            return this.patterns;
        }
        if (name.equals("upgrades")) {
            return this.upgrades;
        }
        return null;
    }

    public IUpgradeableHost getHost() {
        if (this.iHost instanceof IUpgradeableHost) {
            return (IUpgradeableHost) this.iHost;
        }
        return null;
    }

    public AENetworkProxy getProxy() {
        return this.gridProxy;
    }

    public boolean sameGrid(final IGrid grid) throws GridAccessException {
        return grid == this.gridProxy.getGrid();
    }

    // ========== 内部类：物品直通 ME 网络的 IItemHandler ==========

    /**
     * 虚拟 IItemHandler，外部管道调用 insertItem() 时直接将物品注入 ME 网络。
     * 不存储任何物品，网络存不下则拒绝接收（返回原物品）。
     */
    private class ReturnItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            try {
                final IStorageGrid storage = gridProxy.getStorage();
                final appeng.api.networking.energy.IEnergySource src = gridProxy.getEnergy();
                final IAEItemStack aeStack = AEItemStack.fromItemStack(stack);

                final IAEItemStack remaining = appeng.util.StorageHelper.poweredInsert(
                        src, storage.getInventory(AEItemStackType.INSTANCE), aeStack,
                        mySource, simulate ? Actionable.SIMULATE : Actionable.MODULATE);

                if (remaining == null) {
                    if (!simulate) {
                        iHost.onStackReturnNetwork(aeStack);
                    }
                    return ItemStack.EMPTY;
                }
                if (!simulate && remaining.getStackSize() != stack.getCount()) {
                    IAEItemStack inserted = aeStack.copy();
                    inserted.setStackSize(aeStack.getStackSize() - remaining.getStackSize());
                    iHost.onStackReturnNetwork(inserted);
                }
                return remaining.createItemStack();
            } catch (GridAccessException e) {
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
    }

    // ========== 内部类：流体直通 ME 网络的 IFluidHandler ==========

    /**
     * 虚拟 IFluidHandler，外部管道调用 fill() 时直接将流体注入 ME 网络。
     * 不存储任何流体，网络存不下则拒绝接收（返回 0）。
     */
    private class ReturnFluidHandler implements IFluidHandler {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[0];
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) {
                return 0;
            }
            try {
                final IStorageGrid storage = gridProxy.getStorage();
                final appeng.api.networking.energy.IEnergySource src = gridProxy.getEnergy();
                final IAEFluidStack aeStack = AEFluidStack.fromFluidStack(resource);

                final IAEFluidStack remaining = appeng.util.StorageHelper.poweredInsert(
                        src, storage.getInventory(AEFluidStackType.INSTANCE), aeStack,
                        mySource, doFill ? Actionable.MODULATE : Actionable.SIMULATE);

                final long inserted;
                if (remaining == null) {
                    inserted = resource.amount;
                } else {
                    inserted = resource.amount - remaining.getStackSize();
                }

                if (doFill && inserted > 0) {
                    IAEFluidStack insertedStack = aeStack.copy();
                    insertedStack.setStackSize(inserted);
                    iHost.onStackReturnNetwork(insertedStack);
                }
                return (int) inserted;
            } catch (GridAccessException e) {
                return 0;
            }
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            return null;
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            return null;
        }
    }
}

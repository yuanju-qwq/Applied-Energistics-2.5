﻿/*
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

package appeng.container.implementations;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_HEIGHT;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.IContainerCraftingPacket;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.CellConfigLegacy;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * 无线二合一接口终端的容器。
 * 继承自 ContainerWirelessInterfaceTerminal，获得接口列表同步/无线管理能力。
 * 额外嵌入了样板编写功能（从 ContainerPatternEncoder 中移植）和 ME 网络物品监控功能。
 *
 * 布局说明：
 * - 接口终端数据同步：由父类 ContainerInterfaceTerminal 的 detectAndSendChanges 处理
 * - 无线终端管理：由父类 ContainerWirelessInterfaceTerminal 的 detectAndSendChanges 处理
 * - 样板编写：本类内嵌的 crafting/output/pattern 槽位（crafting/output 使用虚拟槽位同步）
 * - ME物品浏览：通过 IMEMonitorHandlerReceiver 监控 AE 网络库存变化
 */
@SuppressWarnings("unchecked")
public class ContainerWirelessDualInterfaceTerminal extends ContainerWirelessInterfaceTerminal
        implements IOptionalSlotHost, IContainerCraftingPacket, IMEMonitorHandlerReceiver,
        IConfigurableObject, IConfigManagerHost, IVirtualSlotHolder, IVirtualSlotSource, IIAEStackInventory {

    private static final int CRAFTING_INPUT_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;
    private static final int PROCESSING_INPUT_SLOTS = PROCESSING_INPUT_LIMIT;
    private static final String NBT_CRAFTING_GRID = "wirelessDualPatternCraftingGrid";
    private static final String NBT_OUTPUT = "wirelessDualPatternOutput";
    private static final String NBT_PATTERNS = "wirelessDualPatternSlots";
    private static final String LEGACY_NBT_PATTERNS = "patterns";

    // ========== 样板编写相关字段（从 ContainerPatternEncoder 移植） ==========
    // 泛型 AE 栈库存，支持物品、流体等任意类型
    private final IAEStackInventory crafting;
    private final IAEStackInventory patternOutput;
    private final AppEngInternalInventory patternSlots;
    private final WirelessTerminalGuiObject guiObject;

    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    // 服务端用于增量同步的客户端快照
    private IAEStack<?>[] craftingClientSlots;
    private IAEStack<?>[] outputClientSlots;

    @GuiSync(97)
    public boolean craftingMode = true;
    @GuiSync(96)
    public boolean substitute = false;
    @GuiSync(95)
    public boolean combine = false;
    @GuiSync(94)
    public boolean beSubstitute = false;
    @GuiSync(93)
    public boolean inverted = false;
    @GuiSync(92)
    public int activePage = 0;
    private static final int OUTPUT_SLOTS_PER_PAGE = 4;
    private static final int TOTAL_OUTPUT_SLOTS = 4;
    private IRecipe currentRecipe;
    private int bulkPatternUpdateDepth = 0;
    private boolean bulkPatternChanged = false;
    private boolean bulkCraftingChanged = false;

    // ========== ME 网络监控相关字段（从 ContainerMEMonitorable 移植） ==========

    /**
     * 多类型 Monitor 映射：每种已注册的 IAEStackType 对应一个 IMEMonitor。
     */
    private final Map<IAEStackType<?>, IMEMonitor<?>> meMonitors = new IdentityHashMap<>();

    /**
     * 多类型更新队列：服务端收到变化通知后，按类型暂存待发送的变更。
     */
    private final Map<IAEStackType<?>, Set<IAEStack<?>>> meUpdateQueue = new IdentityHashMap<>();

    /**
     * 当 onListUpdate 触发时标记为 true，下次 detectAndSendChanges 时发送全量。
     */
    private boolean meNeedListUpdate = false;

    /**
     * GUI 回调引用（客户端），用于将 postUpdate 转发到 GUI
     */
    private Object meGui;

    // ========== 排序/过滤设置（从 ContainerMEMonitorable 移植） ==========

    /**
     * 客户端配置管理器，用于同步排序/视图设置
     */
    private final IConfigManager clientCM;

    /**
     * 服务端配置管理器，从 WirelessTerminalGuiObject 获取
     */
    private IConfigManager serverCM;

    /**
     * AE 网络节点引用，用于 ME 物品交互的电力和存储访问
     */
    private IGridNode networkNode;

    public ContainerWirelessDualInterfaceTerminal(final InventoryPlayer ip, final WirelessTerminalGuiObject gui) {
        super(ip, gui);
        this.guiObject = gui;

        // 初始化排序/视图配置管理器
        this.clientCM = new ConfigManager(this);
        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        // 初始化样板编写的物品栈（泛型 AE 栈库存，支持虚拟槽位同步）
        this.crafting = new IAEStackInventory(this, PROCESSING_INPUT_SLOTS, StorageName.CRAFTING_INPUT);
        this.patternOutput = new IAEStackInventory(this, TOTAL_OUTPUT_SLOTS, StorageName.CRAFTING_OUTPUT);
        this.patternSlots = new AppEngInternalInventory(this, 2);

        this.craftingClientSlots = new IAEStack[PROCESSING_INPUT_SLOTS];
        this.outputClientSlots = new IAEStack[TOTAL_OUTPUT_SLOTS];

        this.loadPatternFromNBT();

        // 初始化 ME 网络监控（服务端）
        if (Platform.isServer()) {
            this.serverCM = gui.getConfigManager();

            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> mon = gui.getInventory(type);
                if (mon != null) {
                    mon.addListener(this, null);
                    this.meMonitors.put(type, mon);
                    this.meUpdateQueue.put(type, new HashSet<>());
                }
            }

            // 设置 ME 物品面板的电力来源和存储，使 SlotME 点击交互生效
            this.setPowerSource(gui);
            @SuppressWarnings("unchecked")
            IMEMonitor<IAEItemStack> itemMon = (IMEMonitor<IAEItemStack>) gui
                    .getInventory(AEStackTypeRegistry.getType("item"));
            if (itemMon != null) {
                this.setCellInventory(itemMon);
            }
            @SuppressWarnings("unchecked")
            IMEMonitor<IAEFluidStack> fluidMon = (IMEMonitor<IAEFluidStack>) gui
                    .getInventory(AEStackTypeRegistry.getType("fluid"));
            if (fluidMon != null) {
                this.setFluidCellInventory(fluidMon);
            }

            // 获取网络节点引用
            this.networkNode = gui.getActionableNode();
            if (this.networkNode != null) {
                final IGrid g = this.networkNode.getGrid();
                if (g != null) {
                    this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                }
            }
        }

        // crafting/output 槽位现在由 GUI 侧的 VirtualMEPatternSlot 管理，
        // 不再添加 SlotFakeCraftingMatrix / SlotPatternOutputs 到 Minecraft Container。

        // 添加样板编码槽
        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), gui,
                gui, new CellConfigLegacy(this.crafting, null), patternSlots, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // 添加空白样板输入槽和编码样板输出槽
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternSlots, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternSlots, 1, 147, -72 + 34, this.getInventoryPlayer()));
        this.patternSlotOUT.setStackLimit(1);
        this.restoreEncodedPatternContents();
    }

    // ========== IMEMonitorHandlerReceiver 接口实现（ME 网络监控） ==========

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void postChange(final IBaseMonitor monitor, final Iterable change,
            final IActionSource source) {
        for (final Object obj : change) {
            IAEStack<?> aes = (IAEStack<?>) obj;
            IAEStackType<?> type = aes.getStackType();
            Set<IAEStack<?>> queue = this.meUpdateQueue.get(type);
            if (queue != null) {
                queue.add(aes);
            }
        }
    }

    @Override
    public void onListUpdate() {
        this.meNeedListUpdate = true;
    }

    /**
     * 设置 GUI 回调对象（客户端），用于接收 postUpdate 转发。
     */
    public void setMeGui(final Object gui) {
        this.meGui = gui;
    }

    // ========== IConfigurableObject / IConfigManagerHost 接口实现 ==========

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        // 客户端接收到服务端同步的设置变更时，通知 GUI 刷新
    }

    /**
     * 客户端接收到 PacketMEInventoryUpdate 时调用。
     * 将更新转发到 GUI 的 postUpdate 方法。
     */
    @SuppressWarnings("unchecked")
    public void postUpdate(final List<IAEStack<?>> list) {
        if (this.meGui instanceof IMEInventoryUpdateReceiver receiver) {
            receiver.postUpdate(list);
        }
    }

    /**
     * GUI 实现此接口以接收 ME 库存更新
     */
    public interface IMEInventoryUpdateReceiver {
        void postUpdate(List<IAEStack<?>> list);
    }

    // ========== IContainerCraftingPacket 接口实现 ==========

    @Override
    public IGridNode getNetworkNode() {
        if (this.guiObject != null) {
            return this.guiObject.getActionableNode();
        }
        return null;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("crafting".equals(name)) {
            return new CellConfigLegacy(this.crafting, null);
        } else if ("output".equals(name)) {
            return new CellConfigLegacy(this.patternOutput, null);
        } else if ("player".equals(name)) {
            return new net.minecraftforge.items.wrapper.PlayerMainInvWrapper(
                    this.getPlayerInv());
        }
        return null;
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    @Override
    public ItemStack[] getViewCells() {
        return new ItemStack[0];
    }

    // ========== IOptionalSlotHost 接口实现 ==========

    @Override
    public boolean isSlotEnabled(final int idx) {
        boolean isCrafting = false;
        if (Platform.isServer()) {
            NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
            if (nbtTagCompound != null && nbtTagCompound.hasKey("isCraftingMode")) {
                isCrafting = nbtTagCompound.getBoolean("isCraftingMode");
            }
        }
        if (idx == 1) {
            return Platform.isServer() ? !isCrafting : !this.isCraftingMode();
        } else if (idx == 2) {
            return Platform.isServer() ? isCrafting : this.isCraftingMode();
        }
        return false;
    }

    // ========== IIAEStackInventory 接口实现 ==========

    @Override
    public void saveAEStackInv() {
        this.saveChanges();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CRAFTING_INPUT) {
            return this.crafting;
        }
        if (name == StorageName.CRAFTING_OUTPUT) {
            return this.patternOutput;
        }
        return null;
    }

    // ========== IVirtualSlotHolder 接口实现（客户端接收虚拟槽位数据） ==========

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        final IAEStackInventory inv;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.crafting;
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.patternOutput;
        } else {
            return;
        }
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            inv.putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }
    }

    // ========== IVirtualSlotSource 接口实现（服务端接收客户端虚拟槽位更新） ==========

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        final IAEStackInventory inv;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.crafting;
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.patternOutput;
        } else {
            return;
        }
        if (slotId >= 0 && slotId < inv.getSizeInventory()) {
            inv.putAEStackInSlot(slotId, aes);
        }
    }

    // ========== 样板编写核心方法（从 ContainerPatternEncoder 移植） ==========

    public boolean isCraftingMode() {
        return craftingMode;
    }

    private NBTTagCompound getOrCreateTerminalTag() {
        NBTTagCompound tag = this.guiObject.getItemStack().getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            this.guiObject.getItemStack().setTagCompound(tag);
        }
        return tag;
    }

    public void setCraftingMode(boolean craftingMode) {
        this.craftingMode = craftingMode;
        NBTTagCompound nbtTagCompound = this.getOrCreateTerminalTag();
        nbtTagCompound.setBoolean("isCraftingMode", craftingMode);
        this.updateOrderOfOutputSlots();
        if (craftingMode && !this.isBulkPatternUpdating()) {
            this.fixCraftingRecipes();
        }
        if (this.isBulkPatternUpdating()) {
            this.markBulkPatternChanged(true);
        } else {
            this.refreshPatternPreview();
        }
    }

    public boolean isSubstitute() {
        return substitute;
    }

    public void setSubstitute(boolean substitute) {
        this.substitute = substitute;
        NBTTagCompound nbtTagCompound = this.getOrCreateTerminalTag();
        nbtTagCompound.setBoolean("isSubstitute", substitute);
        if (this.isBulkPatternUpdating()) {
            this.markBulkPatternChanged(false);
        }
    }

    public boolean isBeSubstitute() {
        return this.beSubstitute;
    }

    public void setBeSubstitute(boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
        NBTTagCompound nbtTagCompound = this.getOrCreateTerminalTag();
        nbtTagCompound.setBoolean("beSubstitute", beSubstitute);
        if (this.isBulkPatternUpdating()) {
            this.markBulkPatternChanged(false);
        }
    }

    public boolean isInverted() {
        return this.inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        NBTTagCompound nbtTagCompound = this.getOrCreateTerminalTag();
        nbtTagCompound.setBoolean("isInverted", inverted);
        if (this.isBulkPatternUpdating()) {
            this.markBulkPatternChanged(false);
        }
    }

    public boolean isCombine() {
        return this.combine;
    }

    public void setCombine(boolean combine) {
        this.combine = combine;
        NBTTagCompound nbtTagCompound = this.getOrCreateTerminalTag();
        nbtTagCompound.setBoolean("isCombine", combine);
        if (this.isBulkPatternUpdating()) {
            this.markBulkPatternChanged(false);
        }
    }

    public int getActivePage() {
        return this.activePage;
    }

    public void setActivePage(int page) {
        final int maxPage = getTotalPages() - 1;
        this.activePage = Math.max(0, Math.min(page, maxPage));
        this.updateOrderOfOutputSlots();
    }

    private boolean isBulkPatternUpdating() {
        return this.bulkPatternUpdateDepth > 0;
    }

    private void beginBulkPatternUpdate() {
        this.bulkPatternUpdateDepth++;
    }

    private void markBulkPatternChanged(final boolean craftingChanged) {
        this.bulkPatternChanged = true;
        if (craftingChanged) {
            this.bulkCraftingChanged = true;
        }
    }

    private void endBulkPatternUpdate(final boolean syncNow) {
        if (this.bulkPatternUpdateDepth <= 0) {
            return;
        }
        this.bulkPatternUpdateDepth--;
        if (this.bulkPatternUpdateDepth > 0) {
            return;
        }

        if (this.bulkPatternChanged) {
            if (this.bulkCraftingChanged) {
                this.fixCraftingRecipes();
            }
            this.refreshPatternPreview();
            this.saveChanges();
            if (syncNow) {
                this.detectAndSendChanges();
            }
        }
        this.bulkPatternChanged = false;
        this.bulkCraftingChanged = false;
    }

    public int getTotalPages() {
        return (TOTAL_OUTPUT_SLOTS + OUTPUT_SLOTS_PER_PAGE - 1) / OUTPUT_SLOTS_PER_PAGE;
    }

    /**
     * 更新输出槽位的显示/隐藏状态：
     * - 合成模式下：显示 craftSlot（单个输出），隐藏所有 outputSlots
     * - 处理模式下：隐藏 craftSlot，输出由 GUI 的 VirtualMEPatternSlot 管理
     */
    private void updateOrderOfOutputSlots() {
        // output 槽位现在由 GUI 侧的 VirtualMEPatternSlot 管理。
        // 仅控制 craftSlot（合成模式输出）的可见性。
        if (!this.isCraftingMode()) {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
        } else {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
        }
    }

    /**
     * 合成模式下，确保所有输入物品数量为1
     */
    private void fixCraftingRecipes() {
        if (this.isCraftingMode()) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                final IAEStack<?> is = this.crafting.getAEStackInSlot(x);
                if (is != null) {
                    is.setStackSize(1);
                }
            }
        }
    }

    /**
     * 编码样板并移动到玩家背包
     */
    public void encodeAndMoveToInventory() {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (!output.isEmpty()) {
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.dropItem(output, false);
            }
            this.patternSlotOUT.putStack(ItemStack.EMPTY);
        }
    }

    /**
     * 编码样板：将合成网格中的输入和输出编码为样板物品
     */
    public void encode() {
        this.refreshPatternPreview();

        ItemStack output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();
        final boolean fluidPattern = containsFluid(in) || containsFluid(out);
        final ItemStack[] encodedIn = in;
        final ItemStack[] encodedOut = out;

        // 输入必须存在
        if (encodedIn == null) {
            return;
        }

        // 检查输出槽：若已有物品且既不是普通样板也不是特殊样板，则中止
        if (!output.isEmpty() && !this.isPattern(output) && !this.isSpecialPattern(output)) {
            return;
        }

        boolean hasValidOutput = false;
        if (encodedOut != null) {
            for (ItemStack stack : encodedOut) {
                if (!stack.isEmpty()) {
                    hasValidOutput = true;
                    break;
                }
            }
        }

        if (this.isCraftingMode() && !hasValidOutput) {
            return;
        }

        boolean requiresSpecialPattern = !hasValidOutput;

        boolean isCurrentSpecial = this.isSpecialPattern(output);
        boolean typeMatches = (requiresSpecialPattern == isCurrentSpecial);

        if (output.isEmpty() || !typeMatches) {
            ItemStack blankPattern = this.patternSlotIN.getStack();
            if (blankPattern.isEmpty() || !this.isPattern(blankPattern)) {
                return;
            }

            blankPattern.shrink(1);
            if (blankPattern.isEmpty()) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }

            Optional<ItemStack> newPatternOpt = requiresSpecialPattern
                    ? AEApi.instance().definitions().items().specialEncodedPattern().maybeStack(1)
                    : AEApi.instance().definitions().items().encodedPattern().maybeStack(1);

            if (!newPatternOpt.isPresent()) {
                return;
            }
            output = newPatternOpt.get();
        }

        final NBTTagCompound encodedValue = new NBTTagCompound();
        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final ItemStack i : encodedIn) {
            tagIn.appendTag(this.createItemTag(i));
        }

        if (encodedOut != null) {
            for (final ItemStack i : encodedOut) {
                tagOut.appendTag(this.createItemTag(i));
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());
        encodedValue.setBoolean("beSubstitute", this.isBeSubstitute());

        // 标记流体样板（当输入或输出中包含流体时）
        if (fluidPattern) {
            encodedValue.setBoolean("fluidPattern", true);
        }

        if (this.getPlayerInv().player != null) {
            encodedValue.setString("encoderName", this.getPlayerInv().player.getName());
        }

        output.setTagCompound(encodedValue);
        patternSlotOUT.putStack(output);
    }

    /**
     * 清除合成网格和输出槽
     */
    public void clear() {
        this.beginBulkPatternUpdate();
        try {
            this.clearPatternContents();
            this.markBulkPatternChanged(true);
        } finally {
            this.endBulkPatternUpdate(true);
        }
    }

    /**
     * 乘以倍数
     */
    public void multiply(int multiple) {
        boolean canMultiplyInputs = true;
        boolean canMultiplyOutputs = true;

        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() * multiple < 1) {
                canMultiplyInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() * multiple < 1) {
                canMultiplyOutputs = false;
            }
        }
        if (canMultiplyInputs && canMultiplyOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() * multiple);
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() * multiple);
                }
            }
        }
    }

    /**
     * 除以除数
     */
    public void divide(int divide) {
        boolean canDivideInputs = true;
        boolean canDivideOutputs = true;

        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() % divide != 0) {
                canDivideInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() % divide != 0) {
                canDivideOutputs = false;
            }
        }
        if (canDivideInputs && canDivideOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() / divide);
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() / divide);
                }
            }
        }
    }

    /**
     * 增加数量
     */
    public void increase(int increase) {
        boolean canIncreaseInputs = true;
        boolean canIncreaseOutputs = true;

        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() + increase < 1) {
                canIncreaseInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
            if (stack != null && stack.getStackSize() + increase < 1) {
                canIncreaseOutputs = false;
            }
        }
        if (canIncreaseInputs && canIncreaseOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                IAEStack<?> stack = this.crafting.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() + increase);
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                IAEStack<?> stack = this.patternOutput.getAEStackInSlot(x);
                if (stack != null) {
                    stack.setStackSize(stack.getStackSize() + increase);
                }
            }
        }
    }

    /**
     * 减少数量
     */
    public void decrease(int decrease) {
        increase(-decrease);
    }

    /**
     * 最大化数量（此操作在 IAEStack 上不适用，因为没有 maxStackSize 限制，保留空实现）
     */
    public void maximizeCount() {
        // IAEStack 没有 maxStackSize 的概念，此操作不再适用。
    }

    // ========== PlacePattern（将编码样板放入接口） ==========

    /**
     * 将编码输出槽中的样板放入指定接口的指定槽位。
     * 条件：目标槽为空、编码输出有样板、接口中不存在完全相同的样板。
     *
     * @param interfaceId 接口终端中的接口 ID
     * @param slot        目标接口的槽位索引
     */
    public void placePattern(long interfaceId, int slot) {
        final IItemHandler interfaceHandler = this.getInterfacePatternHandlerById(interfaceId);
        if (interfaceHandler == null) {
            return;
        }
        if (slot < 0 || slot >= interfaceHandler.getSlots()) {
            return;
        }
        if (!interfaceHandler.getStackInSlot(slot).isEmpty()) {
            return;
        }
        if (this.patternSlotOUT == null || !this.patternSlotOUT.getHasStack()) {
            return;
        }
        final ItemStack pattern = this.patternSlotOUT.getStack();
        // 检查接口中是否已有完全相同的样板
        for (int i = 0; i < interfaceHandler.getSlots(); i++) {
            final ItemStack existing = interfaceHandler.getStackInSlot(i);
            if (!existing.isEmpty() && Platform.itemComparisons().isSameItem(existing, pattern)) {
                return;
            }
        }
        // 放入样板并清空编码输出
        ItemHandlerUtil.setStackInSlot(interfaceHandler, slot, pattern.copy());
        this.patternSlotOUT.putStack(ItemStack.EMPTY);
        this.detectAndSendChanges();
    }

    /**
     * 获取编码输出槽
     */
    public SlotRestrictedInput getPatternSlotOUT() {
        return this.patternSlotOUT;
    }

    // ========== DoubleStacks（编码面板翻倍/减半） ==========

    /**
     * 对编码面板上的输入/输出进行翻倍或减半。
     * 位掩码参数：
     *   bit 0 = shift（快速模式：×8/÷8，否则 ×2/÷2）
     *   bit 1 = 右键（反向/除法）
     * 仅在处理模式下生效。
     *
     * @param val 位掩码参数
     */
    public void doubleStacks(int val) {
        if (this.isCraftingMode()) {
            return;
        }
        boolean fast = (val & 1) != 0;
        boolean backwards = (val & 2) != 0;
        int multi = fast ? 8 : 2;

        if (backwards) {
            if (canDivideAEInv(this.crafting, multi) && canDivideAEInv(this.patternOutput, multi)) {
                divideAEInv(this.crafting, multi);
                divideAEInv(this.patternOutput, multi);
            }
        } else {
            if (canMultiplyAEInv(this.crafting, multi) && canMultiplyAEInv(this.patternOutput, multi)) {
                multiplyAEInv(this.crafting, multi);
                multiplyAEInv(this.patternOutput, multi);
            }
        }
        this.detectAndSendChanges();
    }

    private boolean canMultiplyAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            IAEStack<?> stack = inv.getAEStackInSlot(i);
            if (stack != null && stack.getStackSize() * multi > Integer.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private boolean canDivideAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            IAEStack<?> stack = inv.getAEStackInSlot(i);
            if (stack != null && stack.getStackSize() / multi <= 0) {
                return false;
            }
        }
        return true;
    }

    private void multiplyAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            IAEStack<?> stack = inv.getAEStackInSlot(i);
            if (stack != null) {
                stack.setStackSize(stack.getStackSize() * multi);
            }
        }
    }

    private void divideAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            IAEStack<?> stack = inv.getAEStackInSlot(i);
            if (stack != null) {
                stack.setStackSize(stack.getStackSize() / multi);
            }
        }
    }

    // ========== InterfaceTerminal.Double（接口样板翻倍/减半） ==========

    /**
     * 对指定接口中所有已编码的处理样板（非合成模式）进行翻倍或减半。
     * 直接修改样板物品的 NBT 标签中 in/out 列表的 Count 字段。
     *
     * @param val         位掩码参数（bit 0=shift快速, bit 1=右键反向）
     * @param interfaceId 接口终端中的接口 ID
     */
    public void doubleInterfacePatterns(int val, long interfaceId) {
        final IItemHandler handler = this.getInterfacePatternHandlerById(interfaceId);
        if (handler == null) {
            return;
        }

        boolean fast = (val & 1) != 0;
        boolean backwards = (val & 2) != 0;
        int multi = fast ? 8 : 2;

        final World world = this.getPlayerInv().player.world;

        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem cpi)) {
                continue;
            }
            ICraftingPatternDetails details = cpi.getPatternForItem(stack, world);
            if (details == null || details.isCraftable()) {
                continue;
            }
            ItemStack copy = stack.copy();
            if (backwards) {
                if (!dividePatternNBT(copy, multi)) {
                    continue;
                }
            } else {
                if (!multiplyPatternNBT(copy, multi)) {
                    continue;
                }
            }
            ItemHandlerUtil.setStackInSlot(handler, i, copy);
        }
        this.detectAndSendChanges();
    }

    /**
     * 乘以指定倍数：修改样板 NBT 中所有 in/out 条目的 Count 字段
     * @return 是否所有条目都能安全乘以（不溢出 Integer.MAX_VALUE）
     */
    private boolean multiplyPatternNBT(ItemStack pattern, int multi) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) {
            return false;
        }
        if (!canMultiplyNBTList(tag.getTagList("in", 10), multi)
                || !canMultiplyNBTList(tag.getTagList("out", 10), multi)) {
            return false;
        }
        multiplyNBTList(tag.getTagList("in", 10), multi);
        multiplyNBTList(tag.getTagList("out", 10), multi);
        return true;
    }

    /**
     * 除以指定除数：修改样板 NBT 中所有 in/out 条目的 Count 字段
     * @return 是否所有条目都能安全除以（结果 >= 1）
     */
    private boolean dividePatternNBT(ItemStack pattern, int multi) {
        NBTTagCompound tag = pattern.getTagCompound();
        if (tag == null) {
            return false;
        }
        if (!canDivideNBTList(tag.getTagList("in", 10), multi)
                || !canDivideNBTList(tag.getTagList("out", 10), multi)) {
            return false;
        }
        divideNBTList(tag.getTagList("in", 10), multi);
        divideNBTList(tag.getTagList("out", 10), multi);
        return true;
    }

    /**
     * 从 NBT 条目中获取物品数量，兼容 stackSize 扩展字段
     */
    private int getCountFromNBT(NBTTagCompound entry) {
        if (entry.hasKey("stackSize")) {
            return entry.getInteger("stackSize");
        }
        return entry.getInteger("Count");
    }

    /**
     * 将物品数量写回 NBT 条目，大于 127 时同时写入 stackSize 扩展字段
     */
    private void setCountToNBT(NBTTagCompound entry, int count) {
        entry.setInteger("Count", count);
        if (count > Byte.MAX_VALUE) {
            entry.setInteger("stackSize", count);
        } else {
            entry.removeTag("stackSize");
        }
    }

    private boolean canMultiplyNBTList(NBTTagList list, int multi) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.isEmpty() && entry.hasKey("Count")) {
                long result = (long) getCountFromNBT(entry) * multi;
                if (result > Integer.MAX_VALUE || result <= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canDivideNBTList(NBTTagList list, int multi) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.isEmpty() && entry.hasKey("Count")) {
                int count = getCountFromNBT(entry);
                if (count > 0 && count / multi <= 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private void multiplyNBTList(NBTTagList list, int multi) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.isEmpty() && entry.hasKey("Count")) {
                int count = getCountFromNBT(entry);
                if (count > 0) {
                    setCountToNBT(entry, count * multi);
                }
            }
        }
    }

    private void divideNBTList(NBTTagList list, int multi) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!entry.isEmpty() && entry.hasKey("Count")) {
                int count = getCountFromNBT(entry);
                if (count > 0) {
                    setCountToNBT(entry, count / multi);
                }
            }
        }
    }

    // ========== 辅助方法 ==========

    /**
     * 获取编码时的输入物品列表。
     * 当 inverted=false 时，从 crafting（输入区）获取；
     * 当 inverted=true 时，从 patternOutput（输出区当作输入）获取。
     */
    private ItemStack[] getInputs() {
        ItemStack[] result;
        if (this.inverted && !this.isCraftingMode()) {
            result = getItemsFromOutputInv();
        } else {
            result = getItemsFromCraftingInv();
        }
        // 合并模式：处理模式下合并同类输入
        if (this.combine && !this.isCraftingMode() && result != null) {
            result = combineItems(result);
        }
        return result;
    }

    /**
     * 获取编码时的输出物品列表。
     * 当 inverted=false 时，从 patternOutput（输出区）获取；
     * 当 inverted=true 时，从 crafting（输入区当作输出）获取。
     */
    private ItemStack[] getOutputs() {
        if (this.isCraftingMode()) {
            final ItemStack out = this.getAndUpdateOutput();
            if (!out.isEmpty() && out.getCount() > 0) {
                return new ItemStack[] { out };
            }
        } else {
            ItemStack[] result;
            if (this.inverted) {
                result = getItemsFromCraftingInv();
            } else {
                result = getItemsFromOutputInv();
            }
            // 合并模式：处理模式下合并同类输出
            if (this.combine && result != null) {
                result = combineItems(result);
            }
            return result;
        }
        return null;
    }

    private ItemStack[] getItemsFromCraftingInv() {
        final int slotCount = this.isCraftingMode() ? CRAFTING_INPUT_SLOTS : this.crafting.getSizeInventory();
        final ItemStack[] input = new ItemStack[slotCount];
        boolean hasValue = false;
        for (int x = 0; x < slotCount; x++) {
            final IAEStack<?> aeStack = this.crafting.getAEStackInSlot(x);
            input[x] = aeStack != null ? this.toPatternTerminalStack(aeStack) : ItemStack.EMPTY;
            if (!input[x].isEmpty()) {
                hasValue = true;
            }
        }
        return hasValue ? input : null;
    }

    private ItemStack[] getItemsFromOutputInv() {
        final ItemStack[] result = new ItemStack[this.patternOutput.getSizeInventory()];
        boolean hasValue = false;
        for (int i = 0; i < this.patternOutput.getSizeInventory(); i++) {
            final IAEStack<?> aeStack = this.patternOutput.getAEStackInSlot(i);
            if (aeStack != null && aeStack.getStackSize() > 0) {
                result[i] = this.toPatternTerminalStack(aeStack);
                hasValue = true;
            } else {
                result[i] = ItemStack.EMPTY;
            }
        }
        return hasValue ? result : null;
    }

    /**
     * 合并相同物品：将 ItemStack 数组中 Item+NBT 相同的条目合并为一个，数量累加。
     * 用于 Combine（合并模式）下的编码。
     */
    private ItemStack[] combineItems(ItemStack[] items) {
        final List<ItemStack> merged = new ArrayList<>();
        for (ItemStack stack : items) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (ItemStack existing : merged) {
                if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
                    existing.grow(stack.getCount());
                    found = true;
                    break;
                }
            }
            if (!found) {
                merged.add(stack.copy());
            }
        }
        return merged.isEmpty() ? null : merged.toArray(new ItemStack[0]);
    }

    private ItemStack getAndUpdateOutput() {
        final World world = this.getPlayerInv().player.world;
        final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);

        for (int x = 0; x < ic.getSizeInventory(); x++) {
            final IAEStack<?> aeStack = this.crafting.getAEStackInSlot(x);
            ic.setInventorySlotContents(x, aeStack != null ? this.toPatternTerminalStack(aeStack) : ItemStack.EMPTY);
        }

        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, world);
        }

        final ItemStack is;
        if (this.currentRecipe == null) {
            is = ItemStack.EMPTY;
        } else {
            is = this.currentRecipe.getCraftingResult(ic);
        }

        this.cOut.setStackInSlot(0, is);
        return is;
    }

    private void refreshPatternPreview() {
        if (this.isCraftingMode()) {
            this.getAndUpdateOutput();
        } else {
            this.cOut.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    private boolean isPattern(final ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        return AEApi.instance().definitions().items().encodedPattern().isSameAs(output)
                || AEApi.instance().definitions().materials().blankPattern().isSameAs(output);
    }

    private boolean isSpecialPattern(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<ItemStack> specialPattern = AEApi.instance().definitions().items().specialEncodedPattern()
                .maybeStack(1);
        return specialPattern.isPresent() && stack.isItemEqual(specialPattern.get());
    }

    private NBTBase createItemTag(final ItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();
        if (!i.isEmpty()) {
            // FluidDummyItem（流体占位物品）：使用泛型格式序列化为流体
            if (i.getItem() instanceof FluidDummyItem fluidDummy) {
                FluidStack fs = fluidDummy.getFluidStack(i);
                if (fs != null) {
                    IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fs);
                    if (aeFluid != null) {
                        return aeFluid.toNBTGeneric();
                    }
                }
            }
            // 流体容器（桶等）：提取流体后使用泛型格式序列化
            FluidStack fluid = FluidUtil.getFluidContained(i);
            if (fluid != null && fluid.amount > 0) {
                IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
                if (aeFluid != null) {
                    aeFluid.setStackSize((long) fluid.amount * i.getCount());
                    return aeFluid.toNBTGeneric();
                }
            }
            // 普通物品：使用标准序列化
            i.writeToNBT(c);
        }
        return c;
    }

    /**
     * 检查输入/输出中是否包含流体条目（FluidDummyItem 或流体容器）。
     */
    private boolean containsFluid(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof FluidDummyItem) {
                return true;
            }
            FluidStack fluid = FluidUtil.getFluidContained(stack);
            if (fluid != null && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    private ItemStack[] compactPatternStacks(final ItemStack[] stacks) {
        if (stacks == null) {
            return null;
        }

        final List<ItemStack> compacted = new ArrayList<>();
        for (final ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                compacted.add(stack.copy());
            }
        }

        return compacted.isEmpty() ? null : compacted.toArray(new ItemStack[0]);
    }

    // ========== 公共访问器（供 GUI 层使用） ==========

    /**
     * 获取 crafting 输入 IAEStackInventory，供 GUI 层创建 VirtualMEPatternSlot。
     */
    public IAEStackInventory getCraftingAEInv() {
        return this.crafting;
    }

    /**
     * 获取 output IAEStackInventory，供 GUI 层创建 VirtualMEPatternSlot。
     */
    public IAEStackInventory getOutputAEInv() {
        return this.patternOutput;
    }

    // ========== 生命周期方法覆写 ==========

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = this.wirelessHelper.saveUpgradesToNBT();

            // 保存样板编写数据
            this.crafting.writeToNBT(tag, NBT_CRAFTING_GRID);
            this.patternOutput.writeToNBT(tag, NBT_OUTPUT);
            this.patternSlots.writeToNBT(tag, NBT_PATTERNS);
            final NBTTagCompound data = this.guiObject.getItemStack().getTagCompound();
            if (data != null) {
                data.removeTag(LEGACY_NBT_PATTERNS);
            }

            this.guiObject.saveChanges(tag);
        }
    }

    private void loadPatternFromNBT() {
        NBTTagCompound data = guiObject.getItemStack().getTagCompound();
        if (data != null) {
            this.crafting.readFromNBT(data, NBT_CRAFTING_GRID);
            this.patternOutput.readFromNBT(data, NBT_OUTPUT);
            if (data.hasKey(NBT_PATTERNS)) {
                this.loadValidPatternSlots(data, NBT_PATTERNS);
            } else {
                this.loadValidPatternSlots(data, LEGACY_NBT_PATTERNS);
            }
            if (data.hasKey("isCraftingMode")) {
                this.craftingMode = data.getBoolean("isCraftingMode");
            }
            if (data.hasKey("isSubstitute")) {
                this.substitute = data.getBoolean("isSubstitute");
            }
            if (data.hasKey("beSubstitute")) {
                this.beSubstitute = data.getBoolean("beSubstitute");
            }
            if (data.hasKey("isCombine")) {
                this.combine = data.getBoolean("isCombine");
            }
            if (data.hasKey("isInverted")) {
                this.inverted = data.getBoolean("isInverted");
            }
        }
        this.updateOrderOfOutputSlots();
    }

    /**
     * 校验并加载样板槽位：仅在 NBT 中数据有效时加载（空白样板/编码样板/无物品）
     */
    private void loadValidPatternSlots(NBTTagCompound data, String key) {
        AppEngInternalInventory tmpInv = new AppEngInternalInventory(null, 2);
        tmpInv.readFromNBT(data, key);
        for (int i = 0; i < 2; i++) {
            final ItemStack stack = tmpInv.getStackInSlot(i);
            if (!stack.isEmpty() && !isPattern(stack)) {
                // 非法物品：丢弃
                continue;
            }
            this.patternSlots.setStackInSlot(i, stack);
        }
    }

    /**
     * 将 IAEStack 转换为 ItemStack 表示形式（用于样板编码）。
     * IAEItemStack → ItemStack；
     * IAEFluidStack → FluidDummyItem ItemStack。
     */
    private ItemStack toPatternTerminalStack(final IAEStack<?> aeStack) {
        if (aeStack == null) {
            return ItemStack.EMPTY;
        }
        return aeStack.asItemStackRepresentation();
    }

    /**
     * 从编码样板中还原输入/输出到编码面板。
     * 如果 patternSlotOUT 中有编码样板，则读取 in/out NBT 并放入 crafting/patternOutput。
     */
    @SuppressWarnings("unchecked")
    private void restoreEncodedPatternContents() {
        final ItemStack encodedPattern = this.patternSlotOUT.getStack();
        if (encodedPattern.isEmpty()) {
            return;
        }
        if (!(encodedPattern.getItem() instanceof ICraftingPatternItem patternItem)) {
            return;
        }

        final World world = this.getPlayerInv().player.world;
        final ICraftingPatternDetails details = patternItem.getPatternForItem(encodedPattern, world);
        if (details == null) {
            return;
        }

        this.beginBulkPatternUpdate();
        try {
            this.setCraftingMode(details.isCraftable());
            this.setSubstitute(details.canSubstitute());
            if (details.canBeSubstitute() != null) {
                this.setBeSubstitute(details.canBeSubstitute());
            }

            // 还原输入
            final IAEStack<?>[] inputs = (IAEStack<?>[]) details.getInputs();
            for (int i = 0; i < this.crafting.getSizeInventory(); i++) {
                if (inputs != null && i < inputs.length && inputs[i] != null) {
                    this.crafting.putAEStackInSlot(i, inputs[i].copy());
                } else {
                    this.crafting.putAEStackInSlot(i, null);
                }
            }

            // 还原输出
            final IAEStack<?>[] outputs = (IAEStack<?>[]) details.getOutputs();
            for (int i = 0; i < this.patternOutput.getSizeInventory(); i++) {
                if (outputs != null && i < outputs.length && outputs[i] != null) {
                    this.patternOutput.putAEStackInSlot(i, outputs[i].copy());
                } else {
                    this.patternOutput.putAEStackInSlot(i, null);
                }
            }

            this.markBulkPatternChanged(true);
        } finally {
            this.endBulkPatternUpdate(false);
        }
    }

    /**
     * 清空 crafting 和 patternOutput 的内容。
     */
    private void clearPatternContents() {
        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            this.crafting.putAEStackInSlot(x, null);
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            this.patternOutput.putAEStackInSlot(x, null);
        }
    }

    // ========== detectAndSendChanges ==========

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            // 虚拟槽位同步：crafting 输入
            this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.crafting, this.craftingClientSlots);
            // 虚拟槽位同步：pattern 输出
            this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, this.patternOutput, this.outputClientSlots);

            // ME 网络库存同步
            if (this.meNeedListUpdate) {
                this.meNeedListUpdate = false;
                // 全量更新
                for (var entry : this.meMonitors.entrySet()) {
                    this.sendFullList(entry.getKey(), entry.getValue());
                }
            } else {
                // 增量更新
                for (var entry : this.meUpdateQueue.entrySet()) {
                    final Set<IAEStack<?>> set = entry.getValue();
                    if (!set.isEmpty()) {
                        this.sendIncrementalUpdate(entry.getKey(), set);
                        set.clear();
                    }
                }
            }

            // 同步配置（排序等设置）
            if (this.serverCM != null) {
                for (final Settings set : this.serverCM.getSettings()) {
                    final Enum<?> sideLocal = this.serverCM.getSetting(set);
                    final Enum<?> sideRemote = this.clientCM.getSetting(set);
                    if (sideLocal != sideRemote) {
                        this.clientCM.putSetting(set, sideLocal);
                        for (final IContainerListener crafter : this.listeners) {
                            if (crafter instanceof EntityPlayerMP) {
                                try {
                                    NetworkHandler.instance()
                                            .sendTo(
                                                    new PacketValueConfig(set.name(), sideLocal.name()),
                                                    (EntityPlayerMP) crafter);
                                } catch (final IOException e) {
                                    AELog.debug(e);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void sendFullList(IAEStackType<?> type, IMEMonitor<?> mon) {
        try {
            final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
            IItemList<T> list = (IItemList<T>) mon.getStorageList();
            for (final T stack : list) {
                piu.appendItem(stack);
            }

            for (final IContainerListener c : this.listeners) {
                if (c instanceof EntityPlayer ep && ep instanceof EntityPlayerMP mp) {
                    NetworkHandler.instance().sendTo(piu, mp);
                }
            }
        } catch (final IOException | BufferOverflowException e) {
            AELog.debug(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void sendIncrementalUpdate(IAEStackType<?> type, Set<IAEStack<?>> changes) {
        try {
            final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
            for (IAEStack<?> stack : changes) {
                piu.appendItem((T) stack);
            }

            for (final IContainerListener c : this.listeners) {
                if (c instanceof EntityPlayer ep && ep instanceof EntityPlayerMP mp) {
                    NetworkHandler.instance().sendTo(piu, mp);
                }
            }
        } catch (final IOException | BufferOverflowException e) {
            AELog.debug(e);
        }
    }

    // ========== onContainerClosed ==========

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);

        // 清理 ME monitor 监听
        for (var entry : this.meMonitors.entrySet()) {
            entry.getValue().removeListener(this);
        }
        this.meMonitors.clear();
        this.meUpdateQueue.clear();

        // 保存样板数据
        this.saveChanges();
    }

    // ========== onChangeInventory ==========

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removed, ItemStack added) {
        if (inv == this.patternSlots) {
            // 编码样板输出槽变化时，还原已编码内容
            if (slot == 1) {
                this.restoreEncodedPatternContents();
            }
        }
    }

    // ========== PacketValueConfig 处理 ==========

    @Override
    public void onUpdate(final String field, final String value) {
        super.onUpdate(field, value);
        try {
            switch (field) {
                case "PatternTerminal.CraftMode":
                    this.setCraftingMode("1".equals(value));
                    break;
                case "PatternTerminal.Substitute":
                    this.setSubstitute("1".equals(value));
                    break;
                case "PatternTerminal.BeSubstitute":
                    this.setBeSubstitute("1".equals(value));
                    break;
                case "PatternTerminal.Combine":
                    this.setCombine("1".equals(value));
                    break;
                case "PatternTerminal.Inverted":
                    this.setInverted("1".equals(value));
                    break;
                case "PatternTerminal.Encode":
                    this.encode();
                    break;
                case "PatternTerminal.Clear":
                    this.clear();
                    break;
                case "PatternTerminal.Multiply":
                    this.multiply(Integer.parseInt(value));
                    break;
                case "PatternTerminal.Divide":
                    this.divide(Integer.parseInt(value));
                    break;
                case "PatternTerminal.Increase":
                    this.increase(Integer.parseInt(value));
                    break;
                case "PatternTerminal.Decrease":
                    this.decrease(Integer.parseInt(value));
                    break;
                case "PatternTerminal.Double":
                    this.doubleStacks(Integer.parseInt(value));
                    break;
                case "PatternTerminal.EncodeAndMoveToInventory":
                    this.encodeAndMoveToInventory();
                    break;
                case "PatternTerminal.MaximizeCount":
                    this.maximizeCount();
                    break;
                case "PatternTerminal.ActivePage":
                    this.setActivePage(Integer.parseInt(value));
                    break;
                case "InterfaceTerminal.PlacePattern": {
                    String[] parts = value.split(",");
                    if (parts.length == 2) {
                        this.placePattern(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
                    }
                    break;
                }
                case "InterfaceTerminal.Double": {
                    String[] parts = value.split(",");
                    if (parts.length == 2) {
                        this.doubleInterfacePatterns(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
                    }
                    break;
                }
                default:
                    // 排序/视图配置更新
                    if (Platform.isServer()) {
                        for (final Settings set : this.serverCM.getSettings()) {
                            if (set.name().equals(field)) {
                                final Enum<?> enumVal = Platform.valueOf(set.getDefault().getDeclaringClass(), value);
                                this.serverCM.putSetting(set, enumVal);
                                break;
                            }
                        }
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            AELog.debug(e);
        }
    }
}
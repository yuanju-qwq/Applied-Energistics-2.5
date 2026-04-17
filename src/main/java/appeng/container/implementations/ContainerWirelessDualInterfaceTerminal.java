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

package appeng.container.implementations;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;

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
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternOutputs;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.IContainerCraftingPacket;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

/**
 * 无线二合一接口终端的容器
 * 继承自 ContainerWirelessInterfaceTerminal，获得接口列表同步+无线管理能力。
 * 额外嵌入了样板编写功能（从 ContainerPatternEncoder 中移植）和 ME 网络物品监控功能。
 *
 * 布局说明：
 * - 接口终端数据同步：由父类 ContainerInterfaceTerminal 的 detectAndSendChanges 处理
 * - 无线终端管理：由父类 ContainerWirelessInterfaceTerminal 的 detectAndSendChanges 处理
 * - 样板编写：本类内嵌的 crafting/output/pattern 槽位
 * - ME物品浏览：通过 IMEMonitorHandlerReceiver 监控 AE 网络库存变化
 */
@SuppressWarnings("unchecked")
public class ContainerWirelessDualInterfaceTerminal extends ContainerWirelessInterfaceTerminal
        implements IOptionalSlotHost, IContainerCraftingPacket, IMEMonitorHandlerReceiver,
        IConfigurableObject, IConfigManagerHost {

    // ========== 样板编写相关字段（从 ContainerPatternEncoder 移植） ==========
    private final AppEngInternalInventory crafting;
    private final AppEngInternalInventory patternOutput;
    private final AppEngInternalInventory patternSlots;
    private final WirelessTerminalGuiObject guiObject;

    private SlotFakeCraftingMatrix[] craftingSlots;
    private OptionalSlotFake[] outputSlots;
    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    private boolean craftingMode = true;
    private boolean substitute = false;
    private boolean beSubstitute = false;
    private boolean inverted = false;
    private boolean combine = false;
    private int activePage = 0;
    private static final int OUTPUT_SLOTS_PER_PAGE = 3;
    private static final int TOTAL_OUTPUT_SLOTS = 24;
    private IRecipe currentRecipe;

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

        // 初始化样板编写的物品栏
        this.crafting = new AppEngInternalInventory(this, CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION);
        this.patternOutput = new AppEngInternalInventory(this, TOTAL_OUTPUT_SLOTS);
        this.patternSlots = new AppEngInternalInventory(this, 2);

        this.craftingSlots = new SlotFakeCraftingMatrix[9];
        this.outputSlots = new OptionalSlotFake[TOTAL_OUTPUT_SLOTS];

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

            // 获取网络节点引用
            this.networkNode = gui.getActionableNode();
            if (this.networkNode != null) {
                final IGrid g = this.networkNode.getGrid();
                if (g != null) {
                    this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                }
            }
        }

        // 添加样板编写槽位（3x3合成网格）
        // 注意：这些坐标是"初始坐标"，最终位置由 GUI 的 repositionSlots() 方法决定
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                this.addSlotToContainer(this.craftingSlots[x + y * 3] = new SlotFakeCraftingMatrix(this.crafting,
                        x + y * 3, 18 + x * 18, -76 + y * 18));
            }
        }

        // 添加样板编码槽
        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), gui,
                gui, this.crafting, patternSlots, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // 添加输出槽
        for (int y = 0; y < this.outputSlots.length; y++) {
            this.addSlotToContainer(
                    this.outputSlots[y] = new SlotPatternOutputs(patternOutput, this, y, 110, -76 + y * 18, 0, 0, 1));
            this.outputSlots[y].setRenderDisabled(false);
            this.outputSlots[y].setIIcon(-1);
        }

        // 添加空白样板输入槽和编码样板输出槽
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternSlots, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternSlots, 1, 147, -72 + 34, this.getInventoryPlayer()));
        this.patternSlotOUT.setStackLimit(1);
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
    public void updateSetting(final IConfigManager manager, final Enum settingName, final Enum newValue) {
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
            return this.crafting;
        } else if ("output".equals(name)) {
            return this.patternOutput;
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

    // ========== 样板编写核心方法（从 ContainerPatternEncoder 移植） ==========

    public boolean isCraftingMode() {
        return craftingMode;
    }

    public void setCraftingMode(boolean craftingMode) {
        this.craftingMode = craftingMode;
        NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
        if (nbtTagCompound != null) {
            nbtTagCompound.setBoolean("isCraftingMode", craftingMode);
            this.updateOrderOfOutputSlots();
        }
        if (craftingMode) {
            this.fixCraftingRecipes();
        }
    }

    public boolean isSubstitute() {
        return substitute;
    }

    public void setSubstitute(boolean substitute) {
        this.substitute = substitute;
        NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
        if (nbtTagCompound != null) {
            nbtTagCompound.setBoolean("isSubstitute", substitute);
        }
    }

    public boolean isBeSubstitute() {
        return this.beSubstitute;
    }

    public void setBeSubstitute(boolean beSubstitute) {
        this.beSubstitute = beSubstitute;
        NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
        if (nbtTagCompound != null) {
            nbtTagCompound.setBoolean("beSubstitute", beSubstitute);
        }
    }

    public boolean isInverted() {
        return this.inverted;
    }

    public void setInverted(boolean inverted) {
        this.inverted = inverted;
        NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
        if (nbtTagCompound != null) {
            nbtTagCompound.setBoolean("isInverted", inverted);
        }
    }

    public boolean isCombine() {
        return this.combine;
    }

    public void setCombine(boolean combine) {
        this.combine = combine;
        NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
        if (nbtTagCompound != null) {
            nbtTagCompound.setBoolean("isCombine", combine);
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

    public int getTotalPages() {
        return (TOTAL_OUTPUT_SLOTS + OUTPUT_SLOTS_PER_PAGE - 1) / OUTPUT_SLOTS_PER_PAGE;
    }

    /**
     * 更新输出槽位的显示/隐藏状态：
     * - 合成模式下：显示 craftSlot（单个输出），隐藏所有 outputSlots
     * - 处理模式下：隐藏 craftSlot，仅显示当前 activePage 对应的 3 个 outputSlots
     *   并将它们的 yPos 重新定位到前 3 个槽的位置
     */
    private void updateOrderOfOutputSlots() {
        if (!this.isCraftingMode()) {
            if (craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
            final int pageStart = this.activePage * OUTPUT_SLOTS_PER_PAGE;
            for (int y = 0; y < TOTAL_OUTPUT_SLOTS; y++) {
                if (y >= pageStart && y < pageStart + OUTPUT_SLOTS_PER_PAGE) {
                    this.outputSlots[y].xPos = this.outputSlots[y].getX();
                    // 将当前页的槽位 yPos 映射到前 3 个槽位的位置
                    final int pageIndex = y - pageStart;
                    this.outputSlots[y].yPos = this.outputSlots[pageIndex].getY();
                } else {
                    this.outputSlots[y].xPos = -9000;
                }
            }
        } else {
            if (craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
            for (int y = 0; y < TOTAL_OUTPUT_SLOTS; y++) {
                this.outputSlots[y].xPos = -9000;
            }
        }
    }

    /**
     * 合成模式下，确保所有输入物品数量为1
     */
    private void fixCraftingRecipes() {
        if (this.isCraftingMode()) {
            for (int x = 0; x < this.crafting.getSlots(); x++) {
                final ItemStack is = this.crafting.getStackInSlot(x);
                if (!is.isEmpty()) {
                    is.setCount(1);
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
        ItemStack output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();

        // 输入必须存在
        if (in == null) {
            return;
        }

        // 检查输出槽：若已有物品且既不是普通样板也不是特殊样板，则中止
        if (!output.isEmpty() && !this.isPattern(output) && !this.isSpecialPattern(output)) {
            return;
        }

        boolean requiresSpecialPattern = (out == null);
        if (!requiresSpecialPattern) {
            requiresSpecialPattern = true;
            for (ItemStack stack : out) {
                if (!stack.isEmpty()) {
                    requiresSpecialPattern = false;
                    break;
                }
            }
        }

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

        for (final ItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }

        if (out != null) {
            for (final ItemStack i : out) {
                tagOut.appendTag(this.createItemTag(i));
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());
        encodedValue.setBoolean("beSubstitute", this.isBeSubstitute());

        // 标记流体样板（当输入或输出中包含流体时）
        if (containsFluid(in) || containsFluid(out)) {
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
        for (int x = 0; x < this.crafting.getSlots(); x++) {
            this.crafting.setStackInSlot(x, ItemStack.EMPTY);
        }
        for (int x = 0; x < this.patternOutput.getSlots(); x++) {
            this.patternOutput.setStackInSlot(x, ItemStack.EMPTY);
        }
        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    /**
     * 乘以倍数
     */
    public void multiply(int multiple) {
        boolean canMultiplyInputs = true;
        boolean canMultiplyOutputs = true;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() * multiple < 1) {
                canMultiplyInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() * multiple < 1) {
                canMultiplyOutputs = false;
            }
        }
        if (canMultiplyInputs && canMultiplyOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() * multiple);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() * multiple);
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

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() % divide != 0) {
                canDivideInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() % divide != 0) {
                canDivideOutputs = false;
            }
        }
        if (canDivideInputs && canDivideOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() / divide);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() / divide);
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

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getCount() + increase < 1) {
                canIncreaseInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() + increase < 1) {
                canIncreaseOutputs = false;
            }
        }
        if (canIncreaseInputs && canIncreaseOutputs) {
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() + increase);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() + increase);
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
     * 最大化数量
     */
    public void maximizeCount() {
        boolean canGrowInputs = true;
        boolean canGrowOutputs = true;
        int maxInputStackGrowth = 0;
        int maxOutputStackGrowth = 0;

        for (int x = 0; x < this.craftingSlots.length; x++) {
            ItemStack stack = this.craftingSlots[x].getStack();
            if (!stack.isEmpty() && stack.getMaxStackSize() - stack.getCount() > maxInputStackGrowth) {
                maxInputStackGrowth = stack.getMaxStackSize() - stack.getCount();
            }
            if (!stack.isEmpty() && stack.getCount() + maxInputStackGrowth > stack.getMaxStackSize()) {
                canGrowInputs = false;
            }
        }
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            maxOutputStackGrowth = out.getMaxStackSize() - out.getCount();
            if (!out.isEmpty() && out.getCount() + maxOutputStackGrowth > out.getMaxStackSize()) {
                canGrowOutputs = false;
            }
        }
        if (canGrowInputs && canGrowOutputs) {
            int maxStackGrowth = Math.min(maxInputStackGrowth, maxOutputStackGrowth);
            for (SlotFakeCraftingMatrix craftingSlot : this.craftingSlots) {
                ItemStack stack = craftingSlot.getStack();
                if (!stack.isEmpty()) {
                    craftingSlot.getStack().setCount(stack.getCount() + maxStackGrowth);
                }
            }
            for (OptionalSlotFake outputSlot : this.outputSlots) {
                ItemStack stack = outputSlot.getStack();
                if (!stack.isEmpty()) {
                    outputSlot.getStack().setCount(stack.getCount() + maxStackGrowth);
                }
            }
        }
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
            if (canDivide(this.craftingSlots, multi) && canDivide(this.outputSlots, multi)) {
                divideSlots(this.craftingSlots, multi);
                divideSlots(this.outputSlots, multi);
            }
        } else {
            if (canMultiply(this.craftingSlots, multi) && canMultiply(this.outputSlots, multi)) {
                multiplySlots(this.craftingSlots, multi);
                multiplySlots(this.outputSlots, multi);
            }
        }
        this.detectAndSendChanges();
    }

    private boolean canMultiply(net.minecraft.inventory.Slot[] slots, int multi) {
        for (net.minecraft.inventory.Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && (long) st.getCount() * multi > Integer.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private boolean canDivide(net.minecraft.inventory.Slot[] slots, int multi) {
        for (net.minecraft.inventory.Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty() && st.getCount() / multi <= 0) {
                return false;
            }
        }
        return true;
    }

    private void multiplySlots(net.minecraft.inventory.Slot[] slots, int multi) {
        for (net.minecraft.inventory.Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty()) {
                st.setCount(st.getCount() * multi);
            }
        }
    }

    private void divideSlots(net.minecraft.inventory.Slot[] slots, int multi) {
        for (net.minecraft.inventory.Slot s : slots) {
            ItemStack st = s.getStack();
            if (!st.isEmpty()) {
                st.setCount(st.getCount() / multi);
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
     * 当 inverted=false 时，从 craftingSlots（输入区）获取；
     * 当 inverted=true 时，从 outputSlots（输出区当作输入）获取。
     */
    private ItemStack[] getInputs() {
        ItemStack[] result;
        if (this.inverted && !this.isCraftingMode()) {
            result = getItemsFromOutputSlots();
        } else {
            result = getItemsFromCraftingSlots();
        }
        // 合并模式：处理模式下合并同类输入
        if (this.combine && !this.isCraftingMode() && result != null) {
            result = combineItems(result);
        }
        return result;
    }

    /**
     * 获取编码时的输出物品列表。
     * 当 inverted=false 时，从 outputSlots（输出区）获取；
     * 当 inverted=true 时，从 craftingSlots（输入区当作输出）获取。
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
                result = getItemsFromCraftingSlots();
            } else {
                result = getItemsFromOutputSlots();
            }
            // 合并模式：处理模式下合并同类输出
            if (this.combine && result != null) {
                result = combineItems(result);
            }
            return result;
        }
        return null;
    }

    private ItemStack[] getItemsFromCraftingSlots() {
        final ItemStack[] input = new ItemStack[craftingSlots.length];
        boolean hasValue = false;
        for (int x = 0; x < this.craftingSlots.length; x++) {
            input[x] = this.craftingSlots[x].getStack();
            if (!input[x].isEmpty()) {
                hasValue = true;
            }
        }
        return hasValue ? input : null;
    }

    private ItemStack[] getItemsFromOutputSlots() {
        final List<ItemStack> list = new ArrayList<>(outputSlots.length);
        boolean hasValue = false;
        for (final OptionalSlotFake outputSlot : this.outputSlots) {
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() > 0) {
                list.add(out);
                hasValue = true;
            }
        }
        return hasValue ? list.toArray(new ItemStack[0]) : null;
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
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        for (int x = 0; x < ic.getSizeInventory(); x++) {
            ic.setInventorySlotContents(x, this.crafting.getStackInSlot(x));
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
            // 流体伪物品（ItemFluidDrop）：使用泛型格式序列化为流体
            if (i.getItem() instanceof ItemFluidDrop) {
                IAEFluidStack fluidStack = ItemFluidDrop.getAeFluidStack(
                        AEItemStack.fromItemStack(i));
                if (fluidStack != null) {
                    return fluidStack.toNBTGeneric();
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
     * 检查输入/输出中是否包含流体条目（ItemFluidDrop 或流体容器）。
     */
    private boolean containsFluid(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ItemFluidDrop) {
                return true;
            }
            FluidStack fluid = FluidUtil.getFluidContained(stack);
            if (fluid != null && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    // ========== 生命周期方法覆写 ==========

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = this.wirelessHelper.saveUpgradesToNBT();

            // 保存样板编写数据
            this.crafting.writeToNBT(tag, "craftingGrid");
            this.patternOutput.writeToNBT(tag, "output");
            this.patternSlots.writeToNBT(tag, "patterns");

            this.guiObject.saveChanges(tag);
        }
    }

    private void loadPatternFromNBT() {
        NBTTagCompound data = guiObject.getItemStack().getTagCompound();
        if (data != null) {
            this.crafting.readFromNBT(data, "craftingGrid");
            this.patternOutput.readFromNBT(data, "output");
            this.patternSlots.readFromNBT(data, "patterns");
        }
    }

    @Override
    public void putStackInSlot(int slotID, ItemStack stack) {
        super.putStackInSlot(slotID, stack);
        this.getAndUpdateOutput();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            // ===== 同步排序/视图设置到客户端 =====
            if (this.serverCM != null) {
                for (final Settings set : this.serverCM.getSettings()) {
                    final Enum<?> sideLocal = this.serverCM.getSetting(set);
                    final Enum<?> sideRemote = this.clientCM.getSetting(set);

                    if (sideLocal != sideRemote) {
                        this.clientCM.putSetting(set, sideLocal);
                        for (final IContainerListener crafter : this.listeners) {
                            if (crafter instanceof EntityPlayerMP) {
                                try {
                                    NetworkHandler.instance().sendTo(
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

            NBTTagCompound nbtTagCompound = guiObject.getItemStack().getTagCompound();
            if (nbtTagCompound != null) {
                // 同步合成模式
                if (nbtTagCompound.hasKey("isCraftingMode")) {
                    boolean crafting = nbtTagCompound.getBoolean("isCraftingMode");
                    if (this.isCraftingMode() != crafting) {
                        this.craftingMode = crafting;
                        this.updateOrderOfOutputSlots();
                    }
                } else {
                    nbtTagCompound.setBoolean("isCraftingMode", false);
                }
                // 同步替代品模式
                if (nbtTagCompound.hasKey("isSubstitute")) {
                    boolean sub = nbtTagCompound.getBoolean("isSubstitute");
                    if (this.isSubstitute() != sub) {
                        this.substitute = sub;
                    }
                } else {
                    nbtTagCompound.setBoolean("isSubstitute", false);
                }
                // 同步绝对替换模式（beSubstitute）
                if (nbtTagCompound.hasKey("beSubstitute")) {
                    boolean beSub = nbtTagCompound.getBoolean("beSubstitute");
                    if (this.isBeSubstitute() != beSub) {
                        this.beSubstitute = beSub;
                    }
                } else {
                    nbtTagCompound.setBoolean("beSubstitute", false);
                }
                // 同步反转模式（inverted）
                if (nbtTagCompound.hasKey("isInverted")) {
                    boolean inv = nbtTagCompound.getBoolean("isInverted");
                    if (this.isInverted() != inv) {
                        this.inverted = inv;
                    }
                } else {
                    nbtTagCompound.setBoolean("isInverted", false);
                }
                // 同步合并模式（combine）
                if (nbtTagCompound.hasKey("isCombine")) {
                    boolean comb = nbtTagCompound.getBoolean("isCombine");
                    if (this.isCombine() != comb) {
                        this.combine = comb;
                    }
                } else {
                    nbtTagCompound.setBoolean("isCombine", false);
                }
            } else {
                nbtTagCompound = new NBTTagCompound();
                nbtTagCompound.setBoolean("isCraftingMode", false);
                nbtTagCompound.setBoolean("isSubstitute", false);
                nbtTagCompound.setBoolean("beSubstitute", false);
                nbtTagCompound.setBoolean("isInverted", false);
                nbtTagCompound.setBoolean("isCombine", false);
                guiObject.getItemStack().setTagCompound(nbtTagCompound);
            }

            // ===== ME 网络库存更新发送逻辑 =====
            if (this.meNeedListUpdate) {
                // 全量重发所有类型的库存
                this.meNeedListUpdate = false;
                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayerMP player) {
                        this.queueMEInventory(player);
                    }
                }
            } else {
                // 增量发送变更
                try {
                    final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (var entry : this.meUpdateQueue.entrySet()) {
                        IAEStackType type = entry.getKey();
                        IMEMonitor<?> mon = this.meMonitors.get(type);
                        if (mon == null) {
                            continue;
                        }
                        IItemList<?> storageList = mon.getStorageList();
                        for (IAEStack<?> aes : entry.getValue()) {
                            @SuppressWarnings("rawtypes")
                            final IAEStack<?> send = (IAEStack<?>) ((IItemList) storageList).findPrecise(aes);
                            if (send == null) {
                                aes.setStackSize(0);
                                piu.appendStack(aes);
                            } else {
                                piu.appendStack(send);
                            }
                        }
                    }

                    if (!piu.isEmpty()) {
                        for (var queue : this.meUpdateQueue.values()) {
                            queue.clear();
                        }
                        for (final Object c : this.listeners) {
                            if (c instanceof EntityPlayer) {
                                NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
                            }
                        }
                    }
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }
        }
    }

    /**
     * 向指定玩家发送完整的 ME 网络库存
     */
    @SuppressWarnings("unchecked")
    private void queueMEInventory(final EntityPlayerMP player) {
        try {
            PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

            for (var monitor : this.meMonitors.values()) {
                IItemList<?> storageList = monitor.getStorageList();
                for (final IAEStackBase stackBase : (IItemList<IAEStackBase>) storageList) {
                    final IAEStack<?> send = (IAEStack<?>) stackBase;
                    try {
                        piu.appendStack(send);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, player);
                        piu = new PacketMEInventoryUpdate();
                        piu.appendStack(send);
                    }
                }
            }

            NetworkHandler.instance().sendTo(piu, player);
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    public void addListener(final IContainerListener c) {
        super.addListener(c);

        // 新监听者加入时，发送完整的 ME 库存
        if (Platform.isServer() && c instanceof EntityPlayerMP player) {
            this.queueMEInventory(player);
        }
    }

    @Override
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);

        if (this.listeners.isEmpty()) {
            for (IMEMonitor<?> mon : this.meMonitors.values()) {
                mon.removeListener(this);
            }
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        for (IMEMonitor<?> mon : this.meMonitors.values()) {
            mon.removeListener(this);
        }
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        // 处理合成网格变更
        if (inv == this.crafting) {
            this.fixCraftingRecipes();
        }
        // 处理样板槽的变更：当放入已编码的样板时，自动加载其内容到编写网格
        if (inv == this.patternSlots && slot == 1) {
            final ItemStack is = this.patternSlots.getStackInSlot(1);
            if (!is.isEmpty() && is.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) is.getItem();
                final ICraftingPatternDetails details = pattern.getPatternForItem(is,
                        this.getPlayerInv().player.world);
                if (details != null) {
                    this.setCraftingMode(details.isCraftable());
                    this.setSubstitute(details.canSubstitute());

                    for (int x = 0; x < this.crafting.getSlots() && x < details.getInputs().length; x++) {
                        final IAEItemStack item = details.getInputs()[x];
                        ItemHandlerUtil.setStackInSlot(this.crafting, x,
                                item == null ? ItemStack.EMPTY : item.createItemStack());
                    }

                    for (int x = 0; x < this.patternOutput.getSlots(); x++) {
                        final IAEItemStack item;
                        if (x < details.getOutputs().length) {
                            item = details.getOutputs()[x];
                        } else {
                            item = null;
                        }
                        this.patternOutput.setStackInSlot(x, item == null ? ItemStack.EMPTY : item.createItemStack());
                    }
                }
            }
        }
    }
}

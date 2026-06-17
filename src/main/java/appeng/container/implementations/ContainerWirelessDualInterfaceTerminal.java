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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.StorageName;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * Wireless dual-interface terminal container.
 * Extends ContainerWirelessInterfaceTerminal, inheriting interface list and wireless management.
 * Embeds pattern encoding (ported from ContainerPatternEncoder) and ME network inventory monitoring.
 *
 * Layout description:
 * - Interface terminal data sync: handled by parent ContainerInterfaceTerminal detectAndSendChanges
 * - Wireless terminal management: handled by parent ContainerWirelessInterfaceTerminal detectAndSendChanges
 * - Pattern encoding: embedded crafting/output/pattern slots (crafting/output use Virtual slot sync)
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

    // ========== Pattern encoding fields (ported from ContainerPatternEncoder) ==========
    // 泛型 AE 栈库存，支持物品、流体等任意类型
    private final IAEStackInventory crafting;
    private final IAEStackInventory patternOutput;
    private final AppEngInternalInventory patternSlots;
    private final WirelessTerminalGuiObject guiObject;

    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    // Client-side snapshot for server incremental sync
    private GenericStack[] craftingClientSlots;

    private GenericStack[] outputClientSlots;
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

    // ========== ME network monitoring fields (ported from ContainerMEMonitorable) ==========

    /**
     * Multi-type Monitor mapping: each registered AEKeyType corresponds to one IMEMonitor.
     */
    private final Map<AEKeyType, IMEMonitor> meMonitors = new IdentityHashMap<>();

    /**
     * Multi-type update queue: server-side changes are queued by type before sending.
     */
    private final Map<AEKeyType, Set<GenericStack>> meUpdateQueue = new IdentityHashMap<>();

    /**
     * Flagged true when onListUpdate is triggered; sends full list on next detectAndSendChanges.
     */
    private boolean meNeedListUpdate = false;

    /**
     * GUI callback reference (client-side), used to forward postUpdate to GUI
     */
    private Object meGui;

    // ========== Sort/Filter settings (ported from ContainerMEMonitorable) ==========

    /**
     * Client-side config manager for syncing sort/view settings
     */
    private final IConfigManager clientCM;

    /**
     * 服务端配置管理器，从 WirelessTerminalGuiObject 获取
     */
    private IConfigManager serverCM;

    /**
     * AE network node reference, used for power and storage access for ME item interaction
     */
    private IGridNode networkNode;

    public ContainerWirelessDualInterfaceTerminal(final InventoryPlayer ip, final WirelessTerminalGuiObject gui) {
        super(ip, gui);
        this.guiObject = gui;

        // Initialize sort/view config manager
        this.clientCM = new ConfigManager(this);
        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        // Initialize pattern encoding item stacks (generic AE stack inventory, supports Virtual slot sync)
        this.crafting = new IAEStackInventory(this, PROCESSING_INPUT_SLOTS, StorageName.CRAFTING_INPUT);
        this.patternOutput = new IAEStackInventory(this, TOTAL_OUTPUT_SLOTS, StorageName.CRAFTING_OUTPUT);
        this.patternSlots = new AppEngInternalInventory(this, 2);
        this.craftingClientSlots = new GenericStack[PROCESSING_INPUT_SLOTS];

        this.outputClientSlots = new GenericStack[TOTAL_OUTPUT_SLOTS];

        this.loadPatternFromNBT();

        // Initialize ME network monitoring (server-side)
        if (Platform.isServer()) {
            this.serverCM = gui.getConfigManager();

            for (AEKeyType keyType : AEKeyType.getAllTypes()) {
                IMEMonitor mon = gui.getInventory(keyType);
                if (mon != null) {
                    mon.addListener(this, null);
                    this.meMonitors.put(keyType, mon);
                    this.meUpdateQueue.put(keyType, new HashSet<>());
                }
            }

            // 设置 ME 物品面板的电力来源和存储，使 SlotME 点击交互生效
            this.setPowerSource(gui);
            @SuppressWarnings("unchecked")
            IMEMonitor itemMon = (IMEMonitor) gui
                    .getInventory(AEKeyType.items());
            if (itemMon != null) {
                this.setCellInventory(itemMon);
            }
            @SuppressWarnings("unchecked")
            IMEMonitor fluidMon = (IMEMonitor) gui
                    .getInventory(AEKeyType.fluids());
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

        // Crafting/output slots are now managed by GUI-side VirtualMEPatternSlot
        // No longer add SlotFakeCraftingMatrix / SlotPatternOutputs to Minecraft Container

        // Add pattern encoder slot
        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), gui,
                gui, new CellConfigLegacy(this.crafting), patternSlots, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // Add blank pattern input slot and encoded pattern output slot
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternSlots, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternSlots, 1, 147, -72 + 34, this.getInventoryPlayer()));
        this.patternSlotOUT.setStackLimit(1);
        this.restoreEncodedPatternContents();
    }

    // ========== IMEMonitorHandlerReceiver interface implementation (ME network monitoring) ==========

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor monitor, final Iterable<GenericStack> change,
            final IActionSource source) {
        for (final GenericStack gs : change) {
            if (gs == null) continue;
            AEKeyType type = gs.what().getType();
            Set<GenericStack> queue = this.meUpdateQueue.get(type);
            if (queue != null) {
                queue.add(gs);
            }
        }
    }

    @Override
    public void onListUpdate() {
        this.meNeedListUpdate = true;
    }

    /**
     * Set GUI callback object (client-side) for receiving postUpdate forwarding.
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
     * Client-side handler for GenericStack format inventory updates.
     * Creates RepoEntry list and dispatches via postRepoEntryUpdate (preferred path),
     * with IAEStack fallback for legacy GUIs.
     */
    public void postGenericStackUpdate(final List<GenericStack> list) {
        if (this.meGui instanceof IMEInventoryUpdateReceiver receiver) {
            final List<appeng.client.me.ItemRepo.RepoEntry> entries = new java.util.ArrayList<>(list.size());
            for (GenericStack gs : list) {
                entries.add(new appeng.client.me.ItemRepo.RepoEntry(gs.what(), gs.amount(), false));
            }
            receiver.postRepoEntryUpdate(entries);
        }
    }

    /**
     * @deprecated Use {@link #postGenericStackUpdate(List)} instead.
     * Client-side handler for legacy IAEStack format inventory updates.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public void postUpdate(final List<IAEStack<?>> list) {
        if (this.meGui instanceof IMEInventoryUpdateReceiver receiver) {
            receiver.postUpdate(list);
        }
    }

    /**
     * GUI callback interface for receiving ME inventory updates.
     */
    public interface IMEInventoryUpdateReceiver {
        /**
         * Receive ME network inventory updates using RepoEntry (preferred path).
         */
        void postRepoEntryUpdate(List<appeng.client.me.ItemRepo.RepoEntry> entries);

        /**
         * @deprecated Use {@link #postRepoEntryUpdate(List)} instead.
         * Receive item/fluid list updates from the ME network.
         * Default implementation converts to RepoEntry and delegates.
         */
        @Deprecated
        default void postUpdate(List<IAEStack<?>> list) {
            List<appeng.client.me.ItemRepo.RepoEntry> entries = new java.util.ArrayList<>(list.size());
            for (IAEStack<?> stack : list) {
                var key = stack.toAEKey();
                if (key != null) {
                    entries.add(new appeng.client.me.ItemRepo.RepoEntry(key, stack.getStackSize(), stack.isCraftable()));
                }
            }
            postRepoEntryUpdate(entries);
        }
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
            return new CellConfigLegacy(this.crafting);
        } else if ("output".equals(name)) {
            return new CellConfigLegacy(this.patternOutput);
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

    // ========== IVirtualSlotHolder interface (client receives Virtual slot data) ==========

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<GenericStack> slotStacks) {
        final IAEStackInventory inv;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.crafting;
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.patternOutput;
        } else {
            return;
        }
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            inv.setGenericStack(entry.getIntKey(), entry.getValue());
        }
    }

    // ========== IVirtualSlotSource interface (server receives client Virtual slot updates) ==========

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, GenericStack gs) {
        final IAEStackInventory inv;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.crafting;
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.patternOutput;
        } else {
            return;
        }
        if (slotId >= 0 && slotId < inv.getSizeInventory()) {
            inv.setGenericStack(slotId, gs);
        }
    }

    // ========== Pattern encoding core methods (ported from ContainerPatternEncoder) ==========

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
     * Update output slot visibility:
     * - Crafting mode: show craftSlot (single output), hide all outputSlots
     * - Processing mode: hide craftSlot, output managed by GUI-side VirtualMEPatternSlot
     */
    private void updateOrderOfOutputSlots() {
        // Output slots are now managed by GUI-side VirtualMEPatternSlot
        // Only control craftSlot (crafting mode output) visibility
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
                final GenericStack gs = this.crafting.getGenericStack(x);
                if (gs != null && gs.amount() > 0) {
                    this.crafting.setGenericStack(x, new GenericStack(gs.what(), 1));
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
            GenericStack gs = this.crafting.getGenericStack(x);
            if (gs != null && gs.amount() * multiple < 1) {
                canMultiplyInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            GenericStack gs = this.patternOutput.getGenericStack(x);
            if (gs != null && gs.amount() * multiple < 1) {
                canMultiplyOutputs = false;
            }
        }
        if (canMultiplyInputs && canMultiplyOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                GenericStack gs = this.crafting.getGenericStack(x);
                if (gs != null) {
                    this.crafting.setGenericStack(x, new GenericStack(gs.what(), gs.amount() * multiple));
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                GenericStack gs = this.patternOutput.getGenericStack(x);
                if (gs != null) {
                    this.patternOutput.setGenericStack(x, new GenericStack(gs.what(), gs.amount() * multiple));
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
            GenericStack gs = this.crafting.getGenericStack(x);
            if (gs != null && gs.amount() % divide != 0) {
                canDivideInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            GenericStack gs = this.patternOutput.getGenericStack(x);
            if (gs != null && gs.amount() % divide != 0) {
                canDivideOutputs = false;
            }
        }
        if (canDivideInputs && canDivideOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                GenericStack gs = this.crafting.getGenericStack(x);
                if (gs != null) {
                    this.crafting.setGenericStack(x, new GenericStack(gs.what(), gs.amount() / divide));
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                GenericStack gs = this.patternOutput.getGenericStack(x);
                if (gs != null) {
                    this.patternOutput.setGenericStack(x, new GenericStack(gs.what(), gs.amount() / divide));
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
            GenericStack gs = this.crafting.getGenericStack(x);
            if (gs != null && gs.amount() + increase < 1) {
                canIncreaseInputs = false;
            }
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            GenericStack gs = this.patternOutput.getGenericStack(x);
            if (gs != null && gs.amount() + increase < 1) {
                canIncreaseOutputs = false;
            }
        }
        if (canIncreaseInputs && canIncreaseOutputs) {
            for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
                GenericStack gs = this.crafting.getGenericStack(x);
                if (gs != null) {
                    this.crafting.setGenericStack(x, new GenericStack(gs.what(), gs.amount() + increase));
                }
            }
            for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
                GenericStack gs = this.patternOutput.getGenericStack(x);
                if (gs != null) {
                    this.patternOutput.setGenericStack(x, new GenericStack(gs.what(), gs.amount() + increase));
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
     * Maximize count (not applicable on IAEStack since there is no maxStackSize limit, kept as no-op).
     */
    public void maximizeCount() {
        // IAEStack has no maxStackSize concept, this operation no longer applies.
    }

    // ========== PlacePattern (put encoded pattern into interface) ==========

    /**
     * Place the pattern from the encoded output slot into the specified interface slot.
     * Conditions: target slot is empty, encoded output has a pattern, interface does not have an identical pattern.
     *
     * @param interfaceId 接口终端中的接口 ID
     * @param slot        Target interface slot index
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
        // Check if the interface already has an identical pattern
        for (int i = 0; i < interfaceHandler.getSlots(); i++) {
            final ItemStack existing = interfaceHandler.getStackInSlot(i);
            if (!existing.isEmpty() && Platform.itemComparisons().isSameItem(existing, pattern)) {
                return;
            }
        }
        // Place pattern and clear encoded output
        ItemHandlerUtil.setStackInSlot(interfaceHandler, slot, pattern.copy());
        this.patternSlotOUT.putStack(ItemStack.EMPTY);
        this.detectAndSendChanges();
    }

    /**
     * Get encoded pattern output slot.
     */
    public SlotRestrictedInput getPatternSlotOUT() {
        return this.patternSlotOUT;
    }

    // ========== DoubleStacks (pattern panel double/halve) ==========

    /**
     * Double or halve the input/output on the encoding panel.
     * 位掩码参数：
     *   bit 0 = shift (fast mode: x8/x8, otherwise x2/x2)
     *   bit 1 = right-click (reverse/divide)
     * Only effective in processing mode.
     *
     * @param val Bitmask parameter
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
            GenericStack gs = inv.getGenericStack(i);
            if (gs != null && gs.amount() * multi > Integer.MAX_VALUE) {
                return false;
            }
        }
        return true;
    }

    private boolean canDivideAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            GenericStack gs = inv.getGenericStack(i);
            if (gs != null && gs.amount() / multi <= 0) {
                return false;
            }
        }
        return true;
    }

    private void multiplyAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            GenericStack gs = inv.getGenericStack(i);
            if (gs != null) {
                inv.setGenericStack(i, new GenericStack(gs.what(), gs.amount() * multi));
            }
        }
    }

    private void divideAEInv(IAEStackInventory inv, int multi) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            GenericStack gs = inv.getGenericStack(i);
            if (gs != null) {
                inv.setGenericStack(i, new GenericStack(gs.what(), gs.amount() / multi));
            }
        }
    }

    // ========== InterfaceTerminal.Double (interface pattern double/halve) ==========

    /**
     * Double or halve all encoded processing patterns (non-crafting) in the specified interface.
     * Directly modifies the Count field in the pattern item's NBT in/out lists.
     *
     * @param val         Bitmask parameter (bit 0=shift fast, bit 1=right-click reverse)
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
     * Multiply by multiplier: modifies Count field of all in/out entries in pattern NBT.
     * @return whether all entries can safely multiply (without exceeding Integer.MAX_VALUE)
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
     * Divide by divisor: modifies Count field of all in/out entries in pattern NBT.
     * @return whether all entries can safely divide (result >= 1)
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
     * Get item count from NBT entry, compatible with stackSize extended field.
     */
    private int getCountFromNBT(NBTTagCompound entry) {
        if (entry.hasKey("stackSize")) {
            return entry.getInteger("stackSize");
        }
        return entry.getInteger("Count");
    }

    /**
     * Write item count to NBT entry, writes stackSize extended field when count > 127.
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
     * Get input item list for encoding.
     * When inverted=false, gets from crafting (input area);
     * When inverted=true, gets from patternOutput (output area used as input).
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
     * Get output item list for encoding.
     * When inverted=false, gets from patternOutput (output area);
     * When inverted=true, gets from crafting (input area used as output).
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
            final GenericStack gs = this.crafting.getGenericStack(x);
            input[x] = gs != null ? this.toPatternTerminalStack(gs) : ItemStack.EMPTY;
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
            final GenericStack gs = this.patternOutput.getGenericStack(i);
            if (gs != null && gs.amount() > 0) {
                result[i] = this.toPatternTerminalStack(gs);
                hasValue = true;
            } else {
                result[i] = ItemStack.EMPTY;
            }
        }
        return hasValue ? result : null;
    }

    /**
     * Merge identical items: combines entries with same Item+NBT into one, summing counts.
     * Used under Combine mode for encoding.
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
            final GenericStack gs = this.crafting.getGenericStack(x);
            ic.setInventorySlotContents(x, gs != null ? this.toPatternTerminalStack(gs) : ItemStack.EMPTY);
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
            // FluidDummyItem (fluid placeholder item): serialize as fluid using generic format
            if (i.getItem() instanceof FluidDummyItem fluidDummy) {
                FluidStack fs = fluidDummy.getFluidStack(i);
                if (fs != null) {
                    GenericStack gs = GenericStack.fromFluidStack(fs);
                    if (gs != null) {
                        return GenericStack.writeTag(gs);
                    }
                }
            }
            // Fluid container (bucket etc.): extract fluid and serialize using generic format
            FluidStack fluid = FluidUtil.getFluidContained(i);
            if (fluid != null && fluid.amount > 0) {
                GenericStack gs = GenericStack.fromFluidStack(fluid);
                if (gs != null) {
                    return GenericStack.writeTag(new GenericStack(gs.what(), (long) fluid.amount * i.getCount()));
                }
            }
            // Normal item: use standard serialization
            i.writeToNBT(c);
        }
        return c;
    }

    /**
     * Check if input/output contains fluid entries (FluidDummyItem or fluid container).
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

    // ========== Public accessors (for GUI layer use) ==========

    /**
     * Get crafting input IAEStackInventory for GUI layer to create VirtualMEPatternSlot.
     */
    public IAEStackInventory getCraftingAEInv() {
        return this.crafting;
    }

    /**
     * Get output IAEStackInventory for GUI layer to create VirtualMEPatternSlot.
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
     * Validate and load pattern slots: only load when NBT data is valid (blank pattern/encoded pattern/no items).
     */
    private void loadValidPatternSlots(NBTTagCompound data, String key) {
        AppEngInternalInventory tmpInv = new AppEngInternalInventory(null, 2);
        tmpInv.readFromNBT(data, key);
        for (int i = 0; i < 2; i++) {
            final ItemStack stack = tmpInv.getStackInSlot(i);
            if (!stack.isEmpty() && !isPattern(stack)) {
                // Invalid item: skip
                continue;
            }
            this.patternSlots.setStackInSlot(i, stack);
        }
    }

    /**
     * Convert GenericStack to ItemStack representation (for pattern encoding).
     * AEItemKey -> ItemStack
     * AEFluidKey -> FluidDummyItem ItemStack
     */
    private ItemStack toPatternTerminalStack(final GenericStack gs) {
        if (gs == null) {
            return ItemStack.EMPTY;
        }
        return gs.what().asItemStackRepresentation();
    }

    /**
     * Restore input/output from encoded pattern onto the encoding panel.
     * If patternSlotOUT has an encoded pattern, read in/out NBT and place into crafting/patternOutput.
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
            this.setBeSubstitute(details.canBeSubstitute());

            // 还原输入
            final GenericStack[] inputs = details.getInputStacks();
            for (int i = 0; i < this.crafting.getSizeInventory(); i++) {
                if (inputs != null && i < inputs.length && inputs[i] != null) {
                    this.crafting.setGenericStack(i, inputs[i].copy());
                } else {
                    this.crafting.setGenericStack(i, null);
                }
            }

            // 还原输出
            final GenericStack[] outputs = details.getOutputStacks();
            for (int i = 0; i < this.patternOutput.getSizeInventory(); i++) {
                if (outputs != null && i < outputs.length && outputs[i] != null) {
                    this.patternOutput.setGenericStack(i, outputs[i].copy());
                } else {
                    this.patternOutput.setGenericStack(i, null);
                }
            }

            this.markBulkPatternChanged(true);
        } finally {
            this.endBulkPatternUpdate(false);
        }
    }

    /**
     * Clear crafting and patternOutput contents.
     */
    private void clearPatternContents() {
        for (int x = 0; x < this.crafting.getSizeInventory(); x++) {
            this.crafting.setGenericStack(x, null);
        }
        for (int x = 0; x < this.patternOutput.getSizeInventory(); x++) {
            this.patternOutput.setGenericStack(x, null);
        }
    }

    // ========== detectAndSendChanges ==========

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            // Virtual slot同步：crafting 输入
            this.updateVirtualSlots(StorageName.CRAFTING_INPUT, this.crafting, this.craftingClientSlots);
            // Virtual slot同步：pattern 输出
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
                    final Set<GenericStack> set = entry.getValue();
                    if (!set.isEmpty()) {
                        this.sendIncrementalUpdate(entry.getKey(), set);
                        set.clear();
                    }
                }
            }

            // Sync config (sort settings etc.)
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

    private void sendFullList(AEKeyType type, IMEMonitor mon) {
        try {
            final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
            for (final it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : mon.getAvailableKeyCounter()) {
                GenericStack stack = new GenericStack(entry.getKey(), entry.getLongValue());
                piu.appendStack(stack);
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

    private void sendIncrementalUpdate(AEKeyType type, Set<GenericStack> changes) {
        try {
            final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
            for (GenericStack stack : changes) {
                piu.appendStack(stack);
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

    public void onUpdate(final String field, final String value) {
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
                                @SuppressWarnings({"unchecked", "rawtypes"})
                                final Enum<?> enumVal = Enum.valueOf(
                                        (Class) set.getPossibleValues().iterator().next().getDeclaringClass(), value);
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
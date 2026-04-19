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
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
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
import appeng.util.inv.WrapperRangeItemHandler;
import appeng.util.inv.WrapperSupplierItemHandler;
import appeng.util.item.AEItemStack;

/**
 * 鏃犵嚎浜屽悎涓€鎺ュ彛缁堢鐨勫鍣?
 * 缁ф壙鑷?ContainerWirelessInterfaceTerminal锛岃幏寰楁帴鍙ｅ垪琛ㄥ悓姝?鏃犵嚎绠＄悊鑳藉姏銆?
 * 棰濆宓屽叆浜嗘牱鏉跨紪鍐欏姛鑳斤紙浠?ContainerPatternEncoder 涓Щ妞嶏級鍜?ME 缃戠粶鐗╁搧鐩戞帶鍔熻兘銆?
 *
 * 甯冨眬璇存槑锛?
 * - 鎺ュ彛缁堢鏁版嵁鍚屾锛氱敱鐖剁被 ContainerInterfaceTerminal 鐨?detectAndSendChanges 澶勭悊
 * - 鏃犵嚎缁堢绠＄悊锛氱敱鐖剁被 ContainerWirelessInterfaceTerminal 鐨?detectAndSendChanges 澶勭悊
 * - 鏍锋澘缂栧啓锛氭湰绫诲唴宓岀殑 crafting/output/pattern 妲戒綅
 * - ME鐗╁搧娴忚锛氶€氳繃 IMEMonitorHandlerReceiver 鐩戞帶 AE 缃戠粶搴撳瓨鍙樺寲
 */
@SuppressWarnings("unchecked")
public class ContainerWirelessDualInterfaceTerminal extends ContainerWirelessInterfaceTerminal
        implements IOptionalSlotHost, IContainerCraftingPacket, IMEMonitorHandlerReceiver,
        IConfigurableObject, IConfigManagerHost {

    private static final int CRAFTING_INPUT_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;
    private static final int PROCESSING_INPUT_SLOTS = PROCESSING_INPUT_LIMIT;
    private static final String NBT_CRAFTING_GRID = "wirelessDualPatternCraftingGrid";
    private static final String NBT_OUTPUT = "wirelessDualPatternOutput";
    private static final String NBT_PATTERNS = "wirelessDualPatternSlots";
    private static final String LEGACY_NBT_PATTERNS = "patterns";

    // ========== 鏍锋澘缂栧啓鐩稿叧瀛楁锛堜粠 ContainerPatternEncoder 绉绘锛?==========
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

    // ========== ME 缃戠粶鐩戞帶鐩稿叧瀛楁锛堜粠 ContainerMEMonitorable 绉绘锛?==========

    /**
     * 澶氱被鍨?Monitor 鏄犲皠锛氭瘡绉嶅凡娉ㄥ唽鐨?IAEStackType 瀵瑰簲涓€涓?IMEMonitor銆?
     */
    private final Map<IAEStackType<?>, IMEMonitor<?>> meMonitors = new IdentityHashMap<>();

    /**
     * 澶氱被鍨嬫洿鏂伴槦鍒楋細鏈嶅姟绔敹鍒板彉鍖栭€氱煡鍚庯紝鎸夌被鍨嬫殏瀛樺緟鍙戦€佺殑鍙樻洿銆?
     */
    private final Map<IAEStackType<?>, Set<IAEStack<?>>> meUpdateQueue = new IdentityHashMap<>();

    /**
     * 褰?onListUpdate 瑙﹀彂鏃舵爣璁颁负 true锛屼笅娆?detectAndSendChanges 鏃跺彂閫佸叏閲忋€?
     */
    private boolean meNeedListUpdate = false;

    /**
     * GUI 鍥炶皟寮曠敤锛堝鎴风锛夛紝鐢ㄤ簬灏?postUpdate 杞彂鍒?GUI
     */
    private Object meGui;

    // ========== 鎺掑簭/杩囨护璁剧疆锛堜粠 ContainerMEMonitorable 绉绘锛?==========

    /**
     * 瀹㈡埛绔厤缃鐞嗗櫒锛岀敤浜庡悓姝ユ帓搴?瑙嗗浘璁剧疆
     */
    private final IConfigManager clientCM;

    /**
     * 鏈嶅姟绔厤缃鐞嗗櫒锛屼粠 WirelessTerminalGuiObject 鑾峰彇
     */
    private IConfigManager serverCM;

    /**
     * AE 缃戠粶鑺傜偣寮曠敤锛岀敤浜?ME 鐗╁搧浜や簰鐨勭數鍔涘拰瀛樺偍璁块棶
     */
    private IGridNode networkNode;

    public ContainerWirelessDualInterfaceTerminal(final InventoryPlayer ip, final WirelessTerminalGuiObject gui) {
        super(ip, gui);
        this.guiObject = gui;

        // 鍒濆鍖栨帓搴?瑙嗗浘閰嶇疆绠＄悊鍣?
        this.clientCM = new ConfigManager(this);
        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        // 鍒濆鍖栨牱鏉跨紪鍐欑殑鐗╁搧鏍?
        this.crafting = new AppEngInternalInventory(this, PROCESSING_INPUT_SLOTS);
        this.patternOutput = new AppEngInternalInventory(this, TOTAL_OUTPUT_SLOTS);
        this.patternSlots = new AppEngInternalInventory(this, 2);

        this.craftingSlots = new SlotFakeCraftingMatrix[PROCESSING_INPUT_SLOTS];
        this.outputSlots = new OptionalSlotFake[OUTPUT_SLOTS_PER_PAGE];

        this.loadPatternFromNBT();

        // 鍒濆鍖?ME 缃戠粶鐩戞帶锛堟湇鍔＄锛?
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

            // 璁剧疆 ME 鐗╁搧闈㈡澘鐨勭數鍔涙潵婧愬拰瀛樺偍锛屼娇 SlotME 鐐瑰嚮浜や簰鐢熸晥
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

            // 鑾峰彇缃戠粶鑺傜偣寮曠敤
            this.networkNode = gui.getActionableNode();
            if (this.networkNode != null) {
                final IGrid g = this.networkNode.getGrid();
                if (g != null) {
                    this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                }
            }
        }

        // 娣诲姞鏍锋澘缂栧啓妲戒綅锛?x3鍚堟垚缃戞牸锛?
        // 娉ㄦ剰锛氳繖浜涘潗鏍囨槸"鍒濆鍧愭爣"锛屾渶缁堜綅缃敱 GUI 鐨?repositionSlots() 鏂规硶鍐冲畾
        for (int slotIndex = 0; slotIndex < PROCESSING_INPUT_SLOTS; slotIndex++) {
            final int x = slotIndex % PROCESSING_INPUT_WIDTH;
            final int y = slotIndex / PROCESSING_INPUT_WIDTH;
            this.addSlotToContainer(this.craftingSlots[slotIndex] = new SlotFakeCraftingMatrix(this.crafting,
                    slotIndex, 18 + x * 18, -76 + y * 18));
        }

        // 娣诲姞鏍锋澘缂栫爜妲?
        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), gui,
                gui, this.crafting, patternSlots, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // 娣诲姞杈撳嚭妲?
        for (int y = 0; y < this.outputSlots.length; y++) {
            final int outputX = 96;
            final int outputY = -75 + y * 18;
            this.addSlotToContainer(
                    this.outputSlots[y] = new SlotPatternOutputs(
                            new WrapperSupplierItemHandler(this::getVisiblePatternOutputs),
                            this,
                            y,
                            outputX,
                            outputY,
                            0,
                            0,
                            1));
            this.outputSlots[y].setRenderDisabled(false);
            this.outputSlots[y].setIIcon(-1);
        }

        // 娣诲姞绌虹櫧鏍锋澘杈撳叆妲藉拰缂栫爜鏍锋澘杈撳嚭妲?
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternSlots, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternSlots, 1, 147, -72 + 34, this.getInventoryPlayer()));
        this.patternSlotOUT.setStackLimit(1);
        this.restoreEncodedPatternContents();
    }

    // ========== IMEMonitorHandlerReceiver 鎺ュ彛瀹炵幇锛圡E 缃戠粶鐩戞帶锛?==========

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
     * 璁剧疆 GUI 鍥炶皟瀵硅薄锛堝鎴风锛夛紝鐢ㄤ簬鎺ユ敹 postUpdate 杞彂銆?
     */
    public void setMeGui(final Object gui) {
        this.meGui = gui;
    }

    // ========== IConfigurableObject / IConfigManagerHost 鎺ュ彛瀹炵幇 ==========

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        // 瀹㈡埛绔帴鏀跺埌鏈嶅姟绔悓姝ョ殑璁剧疆鍙樻洿鏃讹紝閫氱煡 GUI 鍒锋柊
    }

    /**
     * 瀹㈡埛绔帴鏀跺埌 PacketMEInventoryUpdate 鏃惰皟鐢ㄣ€?
     * 灏嗘洿鏂拌浆鍙戝埌 GUI 鐨?postUpdate 鏂规硶銆?
     */
    @SuppressWarnings("unchecked")
    public void postUpdate(final List<IAEStack<?>> list) {
        if (this.meGui instanceof IMEInventoryUpdateReceiver receiver) {
            receiver.postUpdate(list);
        }
    }

    /**
     * GUI 瀹炵幇姝ゆ帴鍙ｄ互鎺ユ敹 ME 搴撳瓨鏇存柊
     */
    public interface IMEInventoryUpdateReceiver {
        void postUpdate(List<IAEStack<?>> list);
    }

    // ========== IContainerCraftingPacket 鎺ュ彛瀹炵幇 ==========

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

    // ========== IOptionalSlotHost 鎺ュ彛瀹炵幇 ==========

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

    // ========== 鏍锋澘缂栧啓鏍稿績鏂规硶锛堜粠 ContainerPatternEncoder 绉绘锛?==========

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

    private IItemHandler getVisiblePatternOutputs() {
        final int pageStart = this.activePage * OUTPUT_SLOTS_PER_PAGE;
        final int pageEnd = Math.min(TOTAL_OUTPUT_SLOTS, pageStart + OUTPUT_SLOTS_PER_PAGE);
        return new WrapperRangeItemHandler(this.patternOutput, pageStart, pageEnd);
    }

    /**
     * 鏇存柊杈撳嚭妲戒綅鐨勬樉绀?闅愯棌鐘舵€侊細
     * - 鍚堟垚妯″紡涓嬶細鏄剧ず craftSlot锛堝崟涓緭鍑猴級锛岄殣钘忔墍鏈?outputSlots
     * - 澶勭悊妯″紡涓嬶細闅愯棌 craftSlot锛屼粎鏄剧ず褰撳墠 activePage 瀵瑰簲鐨?3 涓?outputSlots
     *   骞跺皢瀹冧滑鐨?yPos 閲嶆柊瀹氫綅鍒板墠 3 涓Ы鐨勪綅缃?
     */
    private void updateOrderOfOutputSlots() {
        if (this.outputSlots == null || this.outputSlots.length == 0) {
            return;
        }

        if (!this.isCraftingMode()) {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
            for (int y = 0; y < OUTPUT_SLOTS_PER_PAGE; y++) {
                final OptionalSlotFake outputSlot = this.outputSlots[y];
                if (outputSlot == null) {
                    continue;
                }
                outputSlot.xPos = outputSlot.getX();
                    // 灏嗗綋鍓嶉〉鐨勬Ы浣?yPos 鏄犲皠鍒板墠 3 涓Ы浣嶇殑浣嶇疆
                outputSlot.yPos = outputSlot.getY();
            }
        } else {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
            for (int y = 0; y < OUTPUT_SLOTS_PER_PAGE; y++) {
                final OptionalSlotFake outputSlot = this.outputSlots[y];
                if (outputSlot != null) {
                    outputSlot.xPos = -9000;
                }
            }
        }
    }

    /**
     * 鍚堟垚妯″紡涓嬶紝纭繚鎵€鏈夎緭鍏ョ墿鍝佹暟閲忎负1
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
     * 缂栫爜鏍锋澘骞剁Щ鍔ㄥ埌鐜╁鑳屽寘
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
     * 缂栫爜鏍锋澘锛氬皢鍚堟垚缃戞牸涓殑杈撳叆鍜岃緭鍑虹紪鐮佷负鏍锋澘鐗╁搧
     */
    public void encode() {
        this.refreshPatternPreview();

        ItemStack output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();
        final boolean fluidPattern = containsFluid(in) || containsFluid(out);
        final ItemStack[] encodedIn = in;
        final ItemStack[] encodedOut = out;

        // 杈撳叆蹇呴』瀛樺湪
        if (encodedIn == null) {
            return;
        }

        // 妫€鏌ヨ緭鍑烘Ы锛氳嫢宸叉湁鐗╁搧涓旀棦涓嶆槸鏅€氭牱鏉夸篃涓嶆槸鐗规畩鏍锋澘锛屽垯涓
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

        // 鏍囪娴佷綋鏍锋澘锛堝綋杈撳叆鎴栬緭鍑轰腑鍖呭惈娴佷綋鏃讹級
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
     * 娓呴櫎鍚堟垚缃戞牸鍜岃緭鍑烘Ы
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
     * 涔樹互鍊嶆暟
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
     * 闄や互闄ゆ暟
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
     * 澧炲姞鏁伴噺
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
     * 鍑忓皯鏁伴噺
     */
    public void decrease(int decrease) {
        increase(-decrease);
    }

    /**
     * 鏈€澶у寲鏁伴噺
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

    // ========== PlacePattern锛堝皢缂栫爜鏍锋澘鏀惧叆鎺ュ彛锛?==========

    /**
     * 灏嗙紪鐮佽緭鍑烘Ы涓殑鏍锋澘鏀惧叆鎸囧畾鎺ュ彛鐨勬寚瀹氭Ы浣嶃€?
     * 鏉′欢锛氱洰鏍囨Ы涓虹┖銆佺紪鐮佽緭鍑烘湁鏍锋澘銆佹帴鍙ｄ腑涓嶅瓨鍦ㄥ畬鍏ㄧ浉鍚岀殑鏍锋澘銆?
     *
     * @param interfaceId 鎺ュ彛缁堢涓殑鎺ュ彛 ID
     * @param slot        鐩爣鎺ュ彛鐨勬Ы浣嶇储寮?
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
        // 妫€鏌ユ帴鍙ｄ腑鏄惁宸叉湁瀹屽叏鐩稿悓鐨勬牱鏉?
        for (int i = 0; i < interfaceHandler.getSlots(); i++) {
            final ItemStack existing = interfaceHandler.getStackInSlot(i);
            if (!existing.isEmpty() && Platform.itemComparisons().isSameItem(existing, pattern)) {
                return;
            }
        }
        // 鏀惧叆鏍锋澘骞舵竻绌虹紪鐮佽緭鍑?
        ItemHandlerUtil.setStackInSlot(interfaceHandler, slot, pattern.copy());
        this.patternSlotOUT.putStack(ItemStack.EMPTY);
        this.detectAndSendChanges();
    }

    /**
     * 鑾峰彇缂栫爜杈撳嚭妲?
     */
    public SlotRestrictedInput getPatternSlotOUT() {
        return this.patternSlotOUT;
    }

    // ========== DoubleStacks锛堢紪鐮侀潰鏉跨炕鍊?鍑忓崐锛?==========

    /**
     * 瀵圭紪鐮侀潰鏉夸笂鐨勮緭鍏?杈撳嚭杩涜缈诲€嶆垨鍑忓崐銆?
     * 浣嶆帺鐮佸弬鏁帮細
     *   bit 0 = shift锛堝揩閫熸ā寮忥細脳8/梅8锛屽惁鍒?脳2/梅2锛?
     *   bit 1 = 鍙抽敭锛堝弽鍚?闄ゆ硶锛?
     * 浠呭湪澶勭悊妯″紡涓嬬敓鏁堛€?
     *
     * @param val 浣嶆帺鐮佸弬鏁?
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

    // ========== InterfaceTerminal.Double锛堟帴鍙ｆ牱鏉跨炕鍊?鍑忓崐锛?==========

    /**
     * 瀵规寚瀹氭帴鍙ｄ腑鎵€鏈夊凡缂栫爜鐨勫鐞嗘牱鏉匡紙闈炲悎鎴愭ā寮忥級杩涜缈诲€嶆垨鍑忓崐銆?
     * 鐩存帴淇敼鏍锋澘鐗╁搧鐨?NBT 鏍囩涓?in/out 鍒楄〃鐨?Count 瀛楁銆?
     *
     * @param val         浣嶆帺鐮佸弬鏁帮紙bit 0=shift蹇€? bit 1=鍙抽敭鍙嶅悜锛?
     * @param interfaceId 鎺ュ彛缁堢涓殑鎺ュ彛 ID
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
     * 涔樹互鎸囧畾鍊嶆暟锛氫慨鏀规牱鏉?NBT 涓墍鏈?in/out 鏉＄洰鐨?Count 瀛楁
     * @return 鏄惁鎵€鏈夋潯鐩兘鑳藉畨鍏ㄤ箻浠ワ紙涓嶆孩鍑?Integer.MAX_VALUE锛?
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
     * 闄や互鎸囧畾闄ゆ暟锛氫慨鏀规牱鏉?NBT 涓墍鏈?in/out 鏉＄洰鐨?Count 瀛楁
     * @return 鏄惁鎵€鏈夋潯鐩兘鑳藉畨鍏ㄩ櫎浠ワ紙缁撴灉 >= 1锛?
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
     * 浠?NBT 鏉＄洰涓幏鍙栫墿鍝佹暟閲忥紝鍏煎 stackSize 鎵╁睍瀛楁
     */
    private int getCountFromNBT(NBTTagCompound entry) {
        if (entry.hasKey("stackSize")) {
            return entry.getInteger("stackSize");
        }
        return entry.getInteger("Count");
    }

    /**
     * 灏嗙墿鍝佹暟閲忓啓鍥?NBT 鏉＄洰锛屽ぇ浜?127 鏃跺悓鏃跺啓鍏?stackSize 鎵╁睍瀛楁
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

    // ========== 杈呭姪鏂规硶 ==========

    /**
     * 鑾峰彇缂栫爜鏃剁殑杈撳叆鐗╁搧鍒楄〃銆?
     * 褰?inverted=false 鏃讹紝浠?craftingSlots锛堣緭鍏ュ尯锛夎幏鍙栵紱
     * 褰?inverted=true 鏃讹紝浠?outputSlots锛堣緭鍑哄尯褰撲綔杈撳叆锛夎幏鍙栥€?
     */
    private ItemStack[] getInputs() {
        ItemStack[] result;
        if (this.inverted && !this.isCraftingMode()) {
            result = getItemsFromOutputSlots();
        } else {
            result = getItemsFromCraftingSlots();
        }
        // 鍚堝苟妯″紡锛氬鐞嗘ā寮忎笅鍚堝苟鍚岀被杈撳叆
        if (this.combine && !this.isCraftingMode() && result != null) {
            result = combineItems(result);
        }
        return result;
    }

    /**
     * 鑾峰彇缂栫爜鏃剁殑杈撳嚭鐗╁搧鍒楄〃銆?
     * 褰?inverted=false 鏃讹紝浠?outputSlots锛堣緭鍑哄尯锛夎幏鍙栵紱
     * 褰?inverted=true 鏃讹紝浠?craftingSlots锛堣緭鍏ュ尯褰撲綔杈撳嚭锛夎幏鍙栥€?
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
            // 鍚堝苟妯″紡锛氬鐞嗘ā寮忎笅鍚堝苟鍚岀被杈撳嚭
            if (this.combine && result != null) {
                result = combineItems(result);
            }
            return result;
        }
        return null;
    }

    private ItemStack[] getItemsFromCraftingSlots() {
        final int slotCount = this.isCraftingMode() ? CRAFTING_INPUT_SLOTS : this.craftingSlots.length;
        final ItemStack[] input = new ItemStack[slotCount];
        boolean hasValue = false;
        for (int x = 0; x < slotCount; x++) {
            input[x] = this.craftingSlots[x].getStack();
            if (!input[x].isEmpty()) {
                hasValue = true;
            }
        }
        return hasValue ? input : null;
    }

    private ItemStack[] getItemsFromOutputSlots() {
        final ItemStack[] result = new ItemStack[this.outputSlots.length];
        boolean hasValue = false;
        for (int i = 0; i < this.outputSlots.length; i++) {
            final OptionalSlotFake outputSlot = this.outputSlots[i];
            final ItemStack out = outputSlot.getStack();
            if (!out.isEmpty() && out.getCount() > 0) {
                result[i] = out;
                hasValue = true;
            } else {
                result[i] = ItemStack.EMPTY;
            }
        }
        return hasValue ? result : null;
    }

    /**
     * 鍚堝苟鐩稿悓鐗╁搧锛氬皢 ItemStack 鏁扮粍涓?Item+NBT 鐩稿悓鐨勬潯鐩悎骞朵负涓€涓紝鏁伴噺绱姞銆?
     * 鐢ㄤ簬 Combine锛堝悎骞舵ā寮忥級涓嬬殑缂栫爜銆?
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
            // 娴佷綋浼墿鍝侊紙ItemFluidDrop锛夛細浣跨敤娉涘瀷鏍煎紡搴忓垪鍖栦负娴佷綋
            if (i.getItem() instanceof ItemFluidDrop) {
                IAEFluidStack fluidStack = ItemFluidDrop.getAeFluidStack(
                        AEItemStack.fromItemStack(i));
                if (fluidStack != null) {
                    return fluidStack.toNBTGeneric();
                }
            }
            // 娴佷綋瀹瑰櫒锛堟《绛夛級锛氭彁鍙栨祦浣撳悗浣跨敤娉涘瀷鏍煎紡搴忓垪鍖?
            FluidStack fluid = FluidUtil.getFluidContained(i);
            if (fluid != null && fluid.amount > 0) {
                IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
                if (aeFluid != null) {
                    aeFluid.setStackSize((long) fluid.amount * i.getCount());
                    return aeFluid.toNBTGeneric();
                }
            }
            // 鏅€氱墿鍝侊細浣跨敤鏍囧噯搴忓垪鍖?
            i.writeToNBT(c);
        }
        return c;
    }

    /**
     * 妫€鏌ヨ緭鍏?杈撳嚭涓槸鍚﹀寘鍚祦浣撴潯鐩紙ItemFluidDrop 鎴栨祦浣撳鍣級銆?
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

    // ========== 鐢熷懡鍛ㄦ湡鏂规硶瑕嗗啓 ==========

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = this.wirelessHelper.saveUpgradesToNBT();

            // 淇濆瓨鏍锋澘缂栧啓鏁版嵁
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
            if (data.hasKey("isInverted")) {
                this.inverted = data.getBoolean("isInverted");
            }
            if (data.hasKey("isCombine")) {
                this.combine = data.getBoolean("isCombine");
            }
            this.updateOrderOfOutputSlots();
            this.refreshPatternPreview();
        }
    }

    @Override
    public void putStackInSlot(int slotID, ItemStack stack) {
        super.putStackInSlot(slotID, stack);
        this.refreshPatternPreview();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            // ===== 鍚屾鎺掑簭/瑙嗗浘璁剧疆鍒板鎴风 =====
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
                // 鍚屾鍚堟垚妯″紡
                if (nbtTagCompound.hasKey("isCraftingMode")) {
                    boolean crafting = nbtTagCompound.getBoolean("isCraftingMode");
                    if (this.isCraftingMode() != crafting) {
                        this.setCraftingMode(crafting);
                    }
                } else {
                    nbtTagCompound.setBoolean("isCraftingMode", false);
                }
                // 鍚屾鏇夸唬鍝佹ā寮?
                if (nbtTagCompound.hasKey("isSubstitute")) {
                    boolean sub = nbtTagCompound.getBoolean("isSubstitute");
                    if (this.isSubstitute() != sub) {
                        this.setSubstitute(sub);
                    }
                } else {
                    nbtTagCompound.setBoolean("isSubstitute", false);
                }
                // 鍚屾缁濆鏇挎崲妯″紡锛坆eSubstitute锛?
                if (nbtTagCompound.hasKey("beSubstitute")) {
                    boolean beSub = nbtTagCompound.getBoolean("beSubstitute");
                    if (this.isBeSubstitute() != beSub) {
                        this.setBeSubstitute(beSub);
                    }
                } else {
                    nbtTagCompound.setBoolean("beSubstitute", false);
                }
                // 鍚屾鍙嶈浆妯″紡锛坕nverted锛?
                if (nbtTagCompound.hasKey("isInverted")) {
                    boolean inv = nbtTagCompound.getBoolean("isInverted");
                    if (this.isInverted() != inv) {
                        this.setInverted(inv);
                    }
                } else {
                    nbtTagCompound.setBoolean("isInverted", false);
                }
                // 鍚屾鍚堝苟妯″紡锛坈ombine锛?
                if (nbtTagCompound.hasKey("isCombine")) {
                    boolean comb = nbtTagCompound.getBoolean("isCombine");
                    if (this.isCombine() != comb) {
                        this.setCombine(comb);
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

            // ===== ME 缃戠粶搴撳瓨鏇存柊鍙戦€侀€昏緫 =====
            if (this.meNeedListUpdate) {
                // 鍏ㄩ噺閲嶅彂鎵€鏈夌被鍨嬬殑搴撳瓨
                this.meNeedListUpdate = false;
                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayerMP player) {
                        this.queueMEInventory(player);
                    }
                }
            } else {
                // 澧為噺鍙戦€佸彉鏇?
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
     * 鍚戞寚瀹氱帺瀹跺彂閫佸畬鏁寸殑 ME 缃戠粶搴撳瓨
     */
    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);

        if ("craftingMode".equals(field)) {
            this.refreshPatternPreview();
            this.updateOrderOfOutputSlots();
        } else if ("activePage".equals(field)) {
            this.updateOrderOfOutputSlots();
        }
    }

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

        // 鏂扮洃鍚€呭姞鍏ユ椂锛屽彂閫佸畬鏁寸殑 ME 搴撳瓨
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
        // 澶勭悊鍚堟垚缃戞牸鍙樻洿
        if (this.isBulkPatternUpdating()) {
            if (inv == this.crafting) {
                this.markBulkPatternChanged(true);
            } else if (inv == this.patternOutput || inv == this.patternSlots) {
                this.markBulkPatternChanged(false);
            }
            return;
        }

        if (inv == this.patternSlots && slot == 1) {
            this.restoreEncodedPatternContents();
            return;
        }

        if (inv == this.crafting) {
            this.fixCraftingRecipes();
            this.refreshPatternPreview();
            this.saveChanges();
        } else if (inv == this.patternOutput || inv == this.patternSlots) {
            this.saveChanges();
        }
        // 澶勭悊鏍锋澘妲界殑鍙樻洿锛氬綋鏀惧叆宸茬紪鐮佺殑鏍锋澘鏃讹紝鑷姩鍔犺浇鍏跺唴瀹瑰埌缂栧啓缃戞牸
    }

    private void restoreEncodedPatternContents() {
        this.beginBulkPatternUpdate();
        try {
            this.clearPatternContents();

            final ItemStack patternStack = this.patternSlots.getStackInSlot(1);
            if (patternStack.isEmpty() || !(patternStack.getItem() instanceof ICraftingPatternItem pattern)) {
                this.markBulkPatternChanged(true);
                return;
            }

            final ICraftingPatternDetails details = pattern.getPatternForItem(patternStack, this.getPlayerInv().player.world);
            if (details == null) {
                this.markBulkPatternChanged(true);
                return;
            }

            this.setCraftingMode(details.isCraftable());
            this.setSubstitute(details.canSubstitute());

            final IAEStack<?>[] inputs = details.getAEInputs();
            for (int x = 0; x < this.crafting.getSlots(); x++) {
                final IAEStack<?> item = inputs != null && x < inputs.length ? inputs[x] : null;
                this.setStackIfChanged(this.crafting, x, this.toPatternTerminalStack(item));
            }

            final IAEStack<?>[] outputs = details.getAEOutputs();
            for (int x = 0; x < this.patternOutput.getSlots(); x++) {
                final IAEStack<?> item = outputs != null && x < outputs.length ? outputs[x] : null;
                this.setStackIfChanged(this.patternOutput, x, this.toPatternTerminalStack(item));
            }
        } finally {
            this.endBulkPatternUpdate(false);
        }
    }

    private void clearPatternContents() {
        for (int x = 0; x < this.crafting.getSlots(); x++) {
            this.setStackIfChanged(this.crafting, x, ItemStack.EMPTY);
        }
        for (int x = 0; x < this.patternOutput.getSlots(); x++) {
            this.setStackIfChanged(this.patternOutput, x, ItemStack.EMPTY);
        }
    }

    private void setStackIfChanged(final IItemHandler inv, final int slot, final ItemStack desired) {
        final ItemStack current = inv.getStackInSlot(slot);
        if (ItemStack.areItemStacksEqual(current, desired)) {
            return;
        }
        ItemHandlerUtil.setStackInSlot(inv, slot, desired);
    }

    private void loadValidPatternSlots(final NBTTagCompound data, final String key) {
        final AppEngInternalInventory loadedPatternSlots = new AppEngInternalInventory(null, 2);
        loadedPatternSlots.readFromNBT(data, key);

        final ItemStack blankPattern = loadedPatternSlots.getStackInSlot(0);
        if (!blankPattern.isEmpty()
                && AEApi.instance().definitions().materials().blankPattern().isSameAs(blankPattern)) {
            this.patternSlots.setStackInSlot(0, blankPattern.copy());
        }

        final ItemStack encodedPattern = loadedPatternSlots.getStackInSlot(1);
        if (!encodedPattern.isEmpty() && encodedPattern.getItem() instanceof ICraftingPatternItem) {
            this.patternSlots.setStackInSlot(1, encodedPattern.copy());
        }
    }

    private ItemStack toPatternTerminalStack(final IAEStack<?> stack) {
        if (stack == null) {
            return ItemStack.EMPTY;
        }
        if (stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).createItemStack();
        }
        if (stack instanceof IAEFluidStack) {
            final ItemStack fluidDrop = ItemFluidDrop.newStack(((IAEFluidStack) stack).getFluidStack());
            if (!fluidDrop.isEmpty()) {
                return fluidDrop;
            }
        }
        return stack.asItemStackRepresentation();
    }
}

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

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.*;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.implementations.tiles.IMEChest;
import appeng.api.implementations.tiles.IViewCellStorage;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.config.SecurityPermissions;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IMEMonitorableGuiCallback;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketPinsUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.util.AEFluidStackType;
import appeng.helpers.IPinsHandler;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.items.contents.PinsHandler;
import appeng.items.contents.PinsHolder;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.me.helpers.PlayerSource;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.AEItemStackType;

@SuppressWarnings("unchecked")
public class ContainerMEMonitorable extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver<IAEStackBase> {

    protected final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];
    public final IItemList<IAEItemStack> items = AEItemStackType.INSTANCE.createList();

    /**
     * 多类型 Monitor 映射：每种已注册的 IAEStackType 对应一个 IMEMonitor。
     * 物品和流体（以及未来扩展的其他类型）都在同一个终端中监控。
     */
    private final Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();

    /**
     * 多类型更新队列：服务端收到变更通知后，按类型暂存待发送的变更。
     */
    private final Map<IAEStackType<?>, Set<IAEStack<?>>> updateQueue = new IdentityHashMap<>();

    private final IConfigManager clientCM;
    private final ITerminalHost host;

    /**
     * 获取终端宿主实例。
     * 用于 MUI 面板子类在不需要显式传入 host 的情况下获取 host 引用。
     */
    public ITerminalHost getHost() {
        return this.host;
    }
    @GuiSync(99)
    public boolean canAccessViewCells = false;
    @GuiSync(98)
    public boolean hasPower = false;
    private IConfigManagerHost gui;
    private IConfigManager serverCM;
    private IGridNode networkNode;
    protected int jeiOffset = Platform.isModLoaded("jei") ? 24 : 0;

    /**
     * 当 onListUpdate 触发时标记为 true，下次 detectAndSendChanges 时发送全量。
     */
    private boolean needListUpdate = false;

    // 服务端 Pins 处理器
    private PinsHandler serverPinsHandler;
    // 标记是否需要在下次 detectAndSendChanges 时发送初始 Pins 数据
    private boolean needsInitialPinsSync = true;

    public ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable) {
        this(ip, monitorable, true);
    }

    protected ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable,
            final boolean bindInventory) {
        this(ip, monitorable, null, bindInventory);
    }

    protected ContainerMEMonitorable(final InventoryPlayer ip, final ITerminalHost monitorable,
            final IGuiItemObject iGuiItemObject, final boolean bindInventory) {
        super(ip, monitorable instanceof TileEntity ? (TileEntity) monitorable : null,
                monitorable instanceof IPart ? (IPart) monitorable : null, iGuiItemObject);

        this.host = monitorable;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);

        if (Platform.isServer()) {
            this.serverCM = monitorable.getConfigManager();

            // 閬嶅巻鎵€鏈夊凡娉ㄥ唽鐨?IAEStackType锛岃幏鍙栧搴旂殑 IMEMonitor 骞舵敞鍐岀洃鍚?
            boolean hasAnyMonitor = false;
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> mon = monitorable.getInventory(type);
                if (mon != null) {
                    mon.addListener(this, null);
                    this.monitors.put(type, mon);
                    this.updateQueue.put(type, new HashSet<>());
                    hasAnyMonitor = true;
                }
            }

            if (hasAnyMonitor) {
                // 浣跨敤鐗╁搧 monitor 浣滀负 cell inventory锛堝悜鍚庡吋瀹癸級
                IMEMonitor<?> itemMon = this.monitors.get(
                        AEStackTypeRegistry.getType("item"));
                if (itemMon != null) {
                    this.setCellInventory((IMEInventoryHandler<IAEItemStack>) itemMon);
                }
                IMEMonitor<?> fluidMon = this.monitors.get(
                        AEStackTypeRegistry.getType("fluid"));
                if (fluidMon != null) {
                    this.setFluidCellInventory((IMEInventoryHandler<IAEFluidStack>) fluidMon);
                }

                if (monitorable instanceof IPortableCell) {
                    this.setPowerSource((IEnergySource) monitorable);
                    if (monitorable instanceof WirelessTerminalGuiObject) {
                        this.networkNode = ((WirelessTerminalGuiObject) monitorable).getActionableNode();
                    }
                } else if (monitorable instanceof IMEChest) {
                    this.setPowerSource((IEnergySource) monitorable);
                } else if (monitorable instanceof IGridHost || monitorable instanceof IActionHost) {
                    final IGridNode node;
                    if (monitorable instanceof IGridHost) {
                        node = ((IGridHost) monitorable).getGridNode(AEPartLocation.INTERNAL);
                    } else if (monitorable instanceof IActionHost) {
                        node = ((IActionHost) monitorable).getActionableNode();
                    } else {
                        node = null;
                    }

                    if (node != null) {
                        this.networkNode = node;
                        final IGrid g = node.getGrid();
                        if (g != null) {
                            this.setPowerSource(new ChannelPowerSrc(this.networkNode, g.getCache(IEnergyGrid.class)));
                        }
                    }
                }
            } else {
                this.setValidContainer(false);
            }
        }

        // 鍒濆鍖栨湇鍔＄ Pins 澶勭悊鍣?
        if (Platform.isServer() && ip.player != null) {
            final net.minecraft.world.storage.MapStorage storage = ip.player.getEntityWorld()
                    .getMapStorage();
            if (storage != null) {
                PinsHolder holder = PinsHolder.getForPlayer(storage, ip.player.getUniqueID());
                this.serverPinsHandler = new PinsHandler(holder);
            }
        }

        this.canAccessViewCells = false;
        if (monitorable instanceof IViewCellStorage) {
            for (int y = 0; y < 5; y++) {
                this.cellView[y] = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.VIEW_CELL,
                        ((IViewCellStorage) monitorable)
                                .getViewCellStorage(),
                        y, 206, y * 18 + 8 + jeiOffset, this.getInventoryPlayer());
                this.cellView[y].setAllowEdit(this.canAccessViewCells);
                this.addSlotToContainer(this.cellView[y]);
            }
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 0);
        }
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }

        // Below logic is all about handling shift click for view cells
        if (!(this.host instanceof IViewCellStorage)) {
            return super.transferStackInSlot(p, idx);
        }

        // Is it a view cell?
        final Slot clickSlot = this.inventorySlots.get(idx);
        ItemStack itemStack = clickSlot.getStack();
        if (!AEApi.instance().definitions().items().viewCell().isSameAs(itemStack)) {
            return super.transferStackInSlot(p, idx);
        }

        // Are we clicking from the player's inventory?
        final boolean isPlayerInventorySlot = this.inventorySlots.get(idx) instanceof SlotPlayerInv
                || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar;
        if (!isPlayerInventorySlot) {
            return super.transferStackInSlot(p, idx);
        }

        // Attempt to move the item into the view cell storage
        final IItemHandler viewCellInv = ((IViewCellStorage) this.host).getViewCellStorage();
        for (int slot = 0; slot < viewCellInv.getSlots(); slot++) {
            if (viewCellInv.isItemValid(slot, itemStack) && viewCellInv.getStackInSlot(slot).isEmpty()) {
                ItemStack remainder = viewCellInv.insertItem(slot, itemStack, true);
                if (!remainder.isEmpty()) { // That slot can't take the item
                    continue;
                }
                remainder = viewCellInv.insertItem(slot, itemStack, false);
                clickSlot.putStack(remainder);
                this.detectAndSendChanges();
                if (!remainder.isEmpty()) {
                    // How??
                    return super.transferStackInSlot(p, idx);
                }
                return ItemStack.EMPTY;
            }
        }
        return super.transferStackInSlot(p, idx);
    }

    public IGridNode getNetworkNode() {
        return this.networkNode;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            // 楠岃瘉鎵€鏈?monitor 浠嶇劧鏈夋晥
            for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                IMEMonitor<?> current = this.host.getInventory(type);
                IMEMonitor<?> stored = this.monitors.get(type);
                if (stored != null && stored != current) {
                    this.setValidContainer(false);
                    return;
                }
            }

            for (final Settings set : this.serverCM.getSettings()) {
                final Enum<?> sideLocal = this.serverCM.getSetting(set);
                final Enum<?> sideRemote = this.clientCM.getSetting(set);

                if (sideLocal != sideRemote) {
                    this.clientCM.putSetting(set, sideLocal);
                    for (final IContainerListener crafter : this.listeners) {
                        if (crafter instanceof EntityPlayerMP) {
                            try {
                                NetworkHandler.instance().sendTo(new PacketValueConfig(set.name(), sideLocal.name()),
                                        (EntityPlayerMP) crafter);
                            } catch (final IOException e) {
                                AELog.debug(e);
                            }
                        }
                    }
                }
            }

            if (this.needListUpdate) {
                // 鍏ㄩ噺閲嶅彂鎵€鏈夌被鍨嬬殑搴撳瓨
                this.needListUpdate = false;
                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayerMP player) {
                        this.queueInventory(player);
                    }
                }
            } else {
                // 澧為噺鍙戦€佸彉鏇?
                try {
                    final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (var entry : this.updateQueue.entrySet()) {
                        IAEStackType type = entry.getKey();
                        IMEMonitor<?> mon = this.monitors.get(type);
                        if (mon == null) {
                            continue;
                        }
                        IItemList<?> storageList = mon.getStorageList();
                        for (IAEStack<?> aes : entry.getValue()) {
                            final IAEStack<?> send = storageList.findPreciseGeneric(aes);
                            if (send == null) {
                                aes.setStackSize(0);
                                piu.appendStack(aes);
                            } else {
                                piu.appendStack(send);
                            }
                        }
                    }

                    if (!piu.isEmpty()) {
                        for (var queue : this.updateQueue.values()) {
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

            this.updatePowerStatus();

            // 鍚屾 Pins 鏁版嵁鍒板鎴风
            if (this.serverPinsHandler != null
                    && (this.needsInitialPinsSync || this.serverPinsHandler.isDirty())) {
                this.needsInitialPinsSync = false;
                this.serverPinsHandler.clearDirty();
                final PacketPinsUpdate pinsPacket = new PacketPinsUpdate(
                        this.serverPinsHandler.getMaxPlayerPinRows(),
                        this.serverPinsHandler.getMaxCraftingPinRows(),
                        this.serverPinsHandler.getSectionOrder(),
                        this.serverPinsHandler.getPins());
                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayerMP) {
                        NetworkHandler.instance().sendTo(pinsPacket, (EntityPlayerMP) c);
                    }
                }
            }

            final boolean oldAccessible = this.canAccessViewCells;
            this.canAccessViewCells = this.hasAccess(SecurityPermissions.BUILD, false);
            if (this.canAccessViewCells != oldAccessible) {
                for (int y = 0; y < 5; y++) {
                    if (this.cellView[y] != null) {
                        this.cellView[y].setAllowEdit(this.canAccessViewCells);
                    }
                }
            }

            super.detectAndSendChanges();
        }

    }

    protected void updatePowerStatus() {
        try {
            if (this.networkNode != null) {
                this.setPowered(this.networkNode.isActive());
            } else if (this.getPowerSource() instanceof IEnergyGrid) {
                this.setPowered(((IEnergyGrid) this.getPowerSource()).isNetworkPowered());
            } else {
                this.setPowered(
                        this.getPowerSource().extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.8);
            }
        } catch (final Throwable t) {
            // :P
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("canAccessViewCells")) {
            for (int y = 0; y < 5; y++) {
                if (this.cellView[y] != null) {
                    this.cellView[y].setAllowEdit(this.canAccessViewCells);
                }
            }
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public void addListener(final IContainerListener c) {
        super.addListener(c);

        if (Platform.isServer() && c instanceof EntityPlayerMP player) {
            this.queueInventory(player);
        }
    }

    @SuppressWarnings("unchecked")
    private void queueInventory(final EntityPlayerMP player) {
        try {
            PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

            for (var monitor : this.monitors.values()) {
                for (final IAEStackBase stackBase : (Iterable<IAEStackBase>) monitor.getStorageList()) {
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
    public void removeListener(final IContainerListener c) {
        super.removeListener(c);

        if (this.listeners.isEmpty()) {
            for (IMEMonitor<?> mon : this.monitors.values()) {
                mon.removeListener(this);
            }
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        for (IMEMonitor<?> mon : this.monitors.values()) {
            mon.removeListener(this);
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor<IAEStackBase> monitor, final Iterable<IAEStackBase> change,
            final IActionSource source) {
        for (final IAEStackBase obj : change) {
            IAEStack<?> aes = (IAEStack<?>) obj;
            IAEStackType<?> type = aes.getStackType();
            Set<IAEStack<?>> queue = this.updateQueue.get(type);
            if (queue != null) {
                queue.add(aes);
            }
        }
    }

    @Override
    public void onListUpdate() {
        this.needListUpdate = true;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    public ItemStack[] getViewCells() {
        final ItemStack[] list = new ItemStack[this.cellView.length];

        for (int x = 0; x < this.cellView.length; x++) {
            list[x] = this.cellView[x].getStack();
        }

        return list;
    }

    public SlotRestrictedInput getCellViewSlot(final int index) {
        return this.cellView[index];
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    private void setPowered(final boolean isPowered) {
        this.hasPower = isPowered;
    }

    public IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    public IItemList<IAEItemStack> getItems() {
        return this.items;
    }

    /**
     * 瀹㈡埛绔帴鏀跺埌澶氱被鍨?PacketMEInventoryUpdate 鏃惰皟鐢ㄣ€?
     * 灏嗘洿鏂板垎鍙戝埌 GUI 鐨?ItemRepo銆?
     */
    @SuppressWarnings("unchecked")
    public void postUpdate(final List<IAEStack<?>> list) {
        for (IAEStack<?> stack : list) {
            if (stack instanceof IAEItemStack) {
                final IAEItemStack itemStack = (IAEItemStack) stack;
                final IAEItemStack existing = this.items.findPrecise(itemStack);
                if (existing != null) {
                    existing.reset();
                    existing.add(itemStack);
                } else {
                    this.items.add(itemStack.copy());
                }
            }
        }
        if (this.gui instanceof IMEMonitorableGuiCallback guiMonitorable) {
            guiMonitorable.postUpdate(list);
        }
    }

    /**
     * @return 鐗╁搧绫诲瀷鐨?Monitor锛堝悜鍚庡吋瀹癸級
     */
    @SuppressWarnings("unchecked")
    public IMEMonitor<IAEItemStack> getItemMonitor() {
        IAEStackType<?> itemType = AEStackTypeRegistry.getType("item");
        if (itemType != null) {
            return (IMEMonitor<IAEItemStack>) this.monitors.get(itemType);
        }
        return null;
    }

    /**
     * @return 澶氱被鍨?Monitor 鏄犲皠
     */
    public Map<IAEStackType<?>, IMEMonitor<?>> getMonitors() {
        return this.monitors;
    }

    // region Pins System

    private PinList clientPinList = new PinList();
    private PinsRows clientMaxPlayerPinRows = PinsRows.TWO;
    private PinsRows clientMaxCraftingPinRows = PinsRows.ONE;
    private PinSectionOrder clientPinSectionOrder = PinSectionOrder.PLAYER_FIRST;

    /**
     * 瀹㈡埛绔帴鏀跺埌 Pins 鏇存柊鍖呮椂璋冪敤銆?
     */
    public void postPinsUpdate(PinList pinList, PinsRows maxPlayerPinRows,
            PinsRows maxCraftingPinRows, PinSectionOrder sectionOrder) {
        this.clientPinList = pinList;
        this.clientMaxPlayerPinRows = maxPlayerPinRows;
        this.clientMaxCraftingPinRows = maxCraftingPinRows;
        this.clientPinSectionOrder = sectionOrder;
        if (this.pinsUpdateCallback != null) {
            this.pinsUpdateCallback.run();
        }
    }

    private Runnable pinsUpdateCallback;

    /**
     * Register a callback to be invoked when pin data is updated from the server.
     */
    public void setPinsUpdateCallback(Runnable callback) {
        this.pinsUpdateCallback = callback;
    }

    public PinList getClientPinList() {
        return this.clientPinList;
    }

    public PinsRows getClientMaxPlayerPinRows() {
        return this.clientMaxPlayerPinRows;
    }

    public PinsRows getClientMaxCraftingPinRows() {
        return this.clientMaxCraftingPinRows;
    }

    public PinSectionOrder getClientPinSectionOrder() {
        return this.clientPinSectionOrder;
    }

    /**
     * @return 鏈嶅姟绔?Pins 澶勭悊鍣紙浠呮湇鍔＄鏈夊€硷級
     */
    public IPinsHandler getServerPinsHandler() {
        return this.serverPinsHandler;
    }

    /**
     * Send a full pin state sync to the specified player.
     * Called when pin rows are changed via client request.
     */
    public void sendPinsUpdate(EntityPlayerMP player) {
        if (this.serverPinsHandler == null) {
            return;
        }
        this.serverPinsHandler.clearDirty();
        final PacketPinsUpdate pinsPacket = new PacketPinsUpdate(
                this.serverPinsHandler.getMaxPlayerPinRows(),
                this.serverPinsHandler.getMaxCraftingPinRows(),
                this.serverPinsHandler.getSectionOrder(),
                this.serverPinsHandler.getPins());
        NetworkHandler.instance().sendTo(pinsPacket, player);
    }

    /**
     * 澶勭悊鏉ヨ嚜瀹㈡埛绔殑 Pin 鎿嶄綔璇锋眰銆?
     *
     * @param action Pin 鎿嶄綔绫诲瀷
     * @param stack  鐩稿叧鐨勬爤锛堝彲涓?null锛?
     */
    public void handlePinAction(appeng.helpers.InventoryAction action, IAEStack<?> stack) {
        if (this.serverPinsHandler == null || stack == null) {
            return;
        }

        switch (action) {
            case SET_ITEM_PIN:
                this.serverPinsHandler.addPlayerPin(stack);
                break;
            case UNSET_PIN:
                this.serverPinsHandler.removePin(stack);
                break;
            default:
                break;
        }
    }

    // endregion

    // ========== 流体桶交互逻辑 ==========

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot,
            final long id) {
        if (action == InventoryAction.FILL_ITEM || action == InventoryAction.EMPTY_ITEM) {
            doFluidBucketAction(player, action, slot, id);
            return;
        }
        super.doAction(player, action, slot, id);
    }

    /**
     * 处理流体桶的装/取操作。
     * <p>
     * FILL_ITEM：从网络提取流体，装入玩家手持的桶/容器。
     * EMPTY_ITEM：从玩家手持的桶/容器中提取流体，注入网络。
     */
    private void doFluidBucketAction(final EntityPlayerMP player, final InventoryAction action,
            final int slot, final long id) {
        @SuppressWarnings("unchecked")
        final IMEMonitor<IAEFluidStack> fluidMonitor =
                (IMEMonitor<IAEFluidStack>) this.monitors.get(AEFluidStackType.INSTANCE);
        if (fluidMonitor == null) {
            return;
        }

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        final IActionSource src = new PlayerSource(player, (IActionHost) this.host);
        final IAEFluidStack targetFluid = this.getTargetFluidStack();

        if (action == InventoryAction.FILL_ITEM) {
            if (targetFluid != null) {
                final IAEFluidStack extracted = fluidMonitor.extractItems(
                        targetFluid, Actionable.SIMULATE, src);
                if (extracted != null) {
                    final ContainerInteractionResult<IAEFluidStack> fillResult =
                            AEFluidStackType.INSTANCE.fillToContainer(held, extracted, false);
                    if (fillResult.isSuccess()) {
                        final IAEFluidStack toExtract = targetFluid.copy();
                        toExtract.setStackSize(fillResult.getTransferred().getStackSize());
                        fluidMonitor.extractItems(toExtract, Actionable.MODULATE, src);
                        player.inventory.setItemStack(fillResult.getResultContainer());
                        this.updateHeld(player);
                    }
                }
            }
        } else if (action == InventoryAction.EMPTY_ITEM) {
            final ContainerInteractionResult<IAEFluidStack> drainResult =
                    AEFluidStackType.INSTANCE.drainFromContainer(held, Integer.MAX_VALUE, true);
            if (drainResult.isSuccess()) {
                final IAEFluidStack notInserted = fluidMonitor.injectItems(
                        drainResult.getTransferred(), Actionable.SIMULATE, src);
                if (notInserted == null || notInserted.getStackSize() == 0) {
                    // Actually drain and insert
                    final ContainerInteractionResult<IAEFluidStack> actualDrain =
                            AEFluidStackType.INSTANCE.drainFromContainer(held,
                                    drainResult.getTransferred().getStackSize(), false);
                    if (actualDrain.isSuccess()) {
                        fluidMonitor.injectItems(actualDrain.getTransferred(), Actionable.MODULATE, src);
                        player.inventory.setItemStack(actualDrain.getResultContainer());
                        this.updateHeld(player);
                    }
                }
            }
        }
    }

    /**
     * Shift-click 传输时，如果手持流体容器（桶），自动执行 EMPTY_ITEM 操作。
     */
    @Override
    public ItemStack transferStackInSlot(final EntityPlayer player, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }

        if (player instanceof EntityPlayerMP playerMP) {
            final Slot clickedSlot = this.inventorySlots.get(idx);
            if (clickedSlot != null && clickedSlot.getHasStack()) {
                final ItemStack tis = clickedSlot.getStack();
                final ContainerInteractionResult<IAEFluidStack> drainResult =
                        AEFluidStackType.INSTANCE.drainFromContainer(tis.copy(), Integer.MAX_VALUE, true);
                if (drainResult.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    final IMEMonitor<IAEFluidStack> fluidMonitor =
                            (IMEMonitor<IAEFluidStack>) this.monitors.get(
                                    AEFluidStackType.INSTANCE);
                    if (fluidMonitor != null) {
                        final IActionSource src = new PlayerSource(playerMP, (IActionHost) this.host);
                        final IAEFluidStack notInserted = fluidMonitor.injectItems(
                                drainResult.getTransferred(), Actionable.SIMULATE, src);
                        if (notInserted == null || notInserted.getStackSize() == 0) {
                            final ContainerInteractionResult<IAEFluidStack> actualDrain =
                                    AEFluidStackType.INSTANCE.drainFromContainer(tis,
                                            drainResult.getTransferred().getStackSize(), false);
                            if (actualDrain.isSuccess()) {
                                fluidMonitor.injectItems(actualDrain.getTransferred(),
                                        Actionable.MODULATE, src);
                                clickedSlot.putStack(actualDrain.getResultContainer());
                                this.detectAndSendChanges();
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
            }
        }

        return super.transferStackInSlot(player, idx);
    }
}

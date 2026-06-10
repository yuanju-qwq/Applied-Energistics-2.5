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
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
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
import appeng.helpers.IPinsHandler;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.PinList;
import appeng.items.contents.PinsHandler;
import appeng.items.contents.PinsHolder;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.me.helpers.PlayerSource;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.item.ItemList;
import it.unimi.dsi.fastutil.objects.Object2LongMap;

@SuppressWarnings("unchecked")
public class ContainerMEMonitorable extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver {

    protected final SlotRestrictedInput[] cellView = new SlotRestrictedInput[5];
    public final IItemList<IAEItemStack> items = new ItemList();

    /**
     * Multi-type Monitor mapping: each registered AEKeyType corresponds to one IMEMonitor.
     * Items and fluids (and other types extended in the future) are all monitored in the same terminal.
     */
    private final Map<AEKeyType, IMEMonitor> monitors = new IdentityHashMap<>();

    /**
     * KeyCounter-based update buffer: AEKey change notifications are accumulated here,
     * then sent directly as GenericStack in detectAndSendChanges() without IAEStack conversion.
     */
    private final KeyCounter updateKeyCounter = new KeyCounter();

    /**
     * Tracks which keys had their craftable flag set in the most recent changes.
     * Parallels updateKeyCounter; entries are removed when craftable becomes false.
     * Reset together with updateKeyCounter after detectAndSendChanges sends the packet.
     */
    private final Set<AEKey> craftableChangeSet = new HashSet<>();

    private final IConfigManager clientCM;
    private final ITerminalHost host;

    /**
     * Get the terminal host instance.
     * Used by MUI panel subclasses to obtain the host reference without explicitly passing it.
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
     * Set to true when onListUpdate is triggered; a full resync is sent on the next detectAndSendChanges.
     */
    private boolean needListUpdate = false;

    // Server-side Pins handler
    private PinsHandler serverPinsHandler;
    // Whether initial Pins data needs to be sent on the next detectAndSendChanges
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

            // Iterate all registered AEKeyTypes and register as listeners on their IMEMonitors
            boolean hasAnyMonitor = false;
            for (AEKeyType keyType : AEKeyType.getAllTypes()) {
                IMEMonitor mon = monitorable.getInventory(keyType);
                if (mon != null) {
                    mon.addListener(this, null);
                    this.monitors.put(keyType, mon);
                    hasAnyMonitor = true;
                }
            }

            if (hasAnyMonitor) {
                // Use item monitor as cell inventory (backward compatibility)
                IMEMonitor itemMon = this.monitors.get(AEKeyType.items());
                if (itemMon != null) {
                    this.setCellInventory((IMEInventoryHandler) itemMon);
                }
                IMEMonitor fluidMon = this.monitors.get(AEKeyType.fluids());
                if (fluidMon != null) {
                    this.setFluidCellInventory((IMEInventoryHandler) fluidMon);
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

        // Initialize server-side Pins handler
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

        // Shift-click fluid container (bucket) to auto-empty into network
        if (p instanceof EntityPlayerMP playerMP) {
            final Slot clickedSlot = this.inventorySlots.get(idx);
            if (clickedSlot != null && clickedSlot.getHasStack()) {
                final ItemStack tis = clickedSlot.getStack();
                final ContainerInteractionResult<? extends IAEStack<?>> drainResult =
                        AEKeyType.fluids().drainFromContainer(tis.copy(), Integer.MAX_VALUE, true);
                if (drainResult.isSuccess()) {
                    @SuppressWarnings("unchecked")
                    final IMEMonitor fluidMonitor =
                            (IMEMonitor) this.monitors.get(AEKeyType.fluids());
                    if (fluidMonitor != null) {
                        final IActionSource src = new PlayerSource(playerMP, (IActionHost) this.host);
                        final GenericStack notInserted = fluidMonitor.injectItems(
                                new GenericStack(drainResult.getTransferred().toAEKey(), drainResult.getTransferredAmount()), Actionable.SIMULATE, src);
                        if (notInserted == null || notInserted.amount() == 0) {
                            final ContainerInteractionResult<? extends IAEStack<?>> actualDrain =
                                    AEKeyType.fluids().drainFromContainer(tis,
                                            drainResult.getTransferredAmount(), false);
                            if (actualDrain.isSuccess()) {
                                fluidMonitor.injectItems(actualDrain.getTransferredGenericStack(),
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
            // Verify all monitors are still valid
            for (AEKeyType keyType : AEKeyType.getAllTypes()) {
                IMEMonitor current = this.host.getInventory(keyType);
                IMEMonitor stored = this.monitors.get(keyType);
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
                // Full resync of all type inventories
                this.needListUpdate = false;
                this.updateKeyCounter.reset();
                this.craftableChangeSet.clear();
                for (final Object c : this.listeners) {
                    if (c instanceof EntityPlayerMP player) {
                        this.queueInventory(player);
                    }
                }
            } else {
                // Incremental: send changed entries directly from KeyCounter with craftable flags
                try {
                    PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (var entry : this.updateKeyCounter) {
                        AEKey key = entry.getKey();
                        long amount = entry.getLongValue();
                        boolean craftable = this.craftableChangeSet.contains(key);
                        try {
                            piu.appendStack(new GenericStack(key, amount), craftable);
                        } catch (final BufferOverflowException boe) {
                            for (final Object c : this.listeners) {
                                if (c instanceof EntityPlayerMP) {
                                    NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
                                }
                            }
                            piu = new PacketMEInventoryUpdate();
                            piu.appendStack(new GenericStack(key, amount), craftable);
                        }
                    }

                    if (!piu.isEmpty()) {
                        this.updateKeyCounter.reset();
                        this.craftableChangeSet.clear();
                        for (final Object c : this.listeners) {
                            if (c instanceof EntityPlayerMP) {
                                NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
                            }
                        }
                    }
                } catch (final IOException e) {
                    AELog.debug(e);
                }
            }

            this.updatePowerStatus();

            // Sync Pins data to client
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
                for (final Object2LongMap.Entry<AEKey> entry : monitor.getAvailableKeyCounter()) {
                    AEKey key = entry.getKey();
                    long amount = entry.getLongValue();
                    boolean craftable = false; // default: not craftable for full resync
                    try {
                        piu.appendStack(new GenericStack(key, amount), craftable);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, player);
                        piu = new PacketMEInventoryUpdate();
                        piu.appendStack(new GenericStack(key, amount), craftable);
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
            for (IMEMonitor mon : this.monitors.values()) {
                mon.removeListener(this);
            }
        }
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        for (IMEMonitor mon : this.monitors.values()) {
            mon.removeListener(this);
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(final IBaseMonitor monitor, final Iterable<GenericStack> change,
            final IActionSource source) {
        for (final GenericStack gs : change) {
            if (gs != null) {
                AEKey key = gs.what();
                long amount = gs.amount();
                this.updateKeyCounter.set(key, amount);
                // Craftable tracking: GenericStack no longer carries craftable flag,
                // so remove from set when amount changes (handled by caller if needed)
                this.craftableChangeSet.remove(key);
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
     * Called when the client receives a multi-type PacketMEInventoryUpdate.
     * Dispatches the updates to the GUI's ItemRepo.
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
     * Client-side handler for GenericStack format inventory updates
     * with explicit craftable flags.
     * <p>
     * Converts GenericStack list + craftable flags to RepoEntry and dispatches via
     * {@link IMEMonitorableGuiCallback#postRepoEntryUpdate(List)}.
     */
    public void postGenericStackUpdate(final List<GenericStack> list, final List<Boolean> craftableFlags) {
        if (this.gui instanceof IMEMonitorableGuiCallback guiMonitorable) {
            final int size = Math.min(list.size(), craftableFlags != null ? craftableFlags.size() : 0);
            final List<appeng.client.me.ItemRepo.RepoEntry> entries = new java.util.ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GenericStack gs = list.get(i);
                boolean craftable = craftableFlags.get(i);
                entries.add(new appeng.client.me.ItemRepo.RepoEntry(gs.what(), gs.amount(), craftable));
            }
            guiMonitorable.postRepoEntryUpdate(entries);
        }
    }

    /**
     * Client-side handler for GenericStack format inventory updates
     * without craftable flags (all entries default to not craftable).
     * <p>
     * Converts GenericStack list directly to RepoEntry and dispatches via
     * {@link IMEMonitorableGuiCallback#postRepoEntryUpdate(List)}.
     */
    public void postGenericStackUpdate(final List<GenericStack> list) {
        if (this.gui instanceof IMEMonitorableGuiCallback guiMonitorable) {
            final List<appeng.client.me.ItemRepo.RepoEntry> entries = new java.util.ArrayList<>(list.size());
            for (GenericStack gs : list) {
                entries.add(new appeng.client.me.ItemRepo.RepoEntry(gs.what(), gs.amount(), false));
            }
            guiMonitorable.postRepoEntryUpdate(entries);
        }
    }

    /**
     * @return item type Monitor (backward compatibility)
     */
    @SuppressWarnings("unchecked")
    public IMEMonitor getItemMonitor() {
        return (IMEMonitor) this.monitors.get(AEKeyType.items());
    }

    /**
     * @return multi-type Monitor mapping
     */
    public Map<AEKeyType, IMEMonitor> getMonitors() {
        return this.monitors;
    }

    // region Pins System

    private PinList clientPinList = new PinList();
    private PinsRows clientMaxPlayerPinRows = PinsRows.TWO;
    private PinsRows clientMaxCraftingPinRows = PinsRows.ONE;
    private PinSectionOrder clientPinSectionOrder = PinSectionOrder.PLAYER_FIRST;

    /**
     * Called when the client receives a Pins update packet.
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
     * @return server-side Pins handler (server-side only)
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
     * Process Pin action request from the client.
     *
     * @param action Pin action type
     * @param key    related AEKey (may be null)
     */
    public void handlePinAction(appeng.helpers.InventoryAction action, AEKey key) {
        if (this.serverPinsHandler == null || key == null) {
            return;
        }

        switch (action) {
            case SET_ITEM_PIN:
                this.serverPinsHandler.addPlayerPin(key);
                break;
            case UNSET_PIN:
                this.serverPinsHandler.removePin(key);
                break;
            default:
                break;
        }
    }

    // endregion

    // ========== Fluid bucket interaction logic ==========

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
     * Handle fluid bucket fill/empty operations.
     * <p>
     * FILL_ITEM: Extract fluid from the network and fill it into the player's held container.
     * EMPTY_ITEM: Drain fluid from the player's held bucket/container and inject it into the network.
     */
    private void doFluidBucketAction(final EntityPlayerMP player, final InventoryAction action,
            final int slot, final long id) {
        @SuppressWarnings("unchecked")
        final IMEMonitor fluidMonitor =
                (IMEMonitor) this.monitors.get(AEKeyType.fluids());
        if (fluidMonitor == null) {
            return;
        }

        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        final IActionSource src = new PlayerSource(player, (IActionHost) this.host);
        final GenericStack targetStack = this.getTargetStack();
        final AEFluidKey targetFluidKey = targetStack != null && targetStack.what() instanceof AEFluidKey afk ? afk : null;

        if (action == InventoryAction.FILL_ITEM) {
            if (targetFluidKey != null) {
                final GenericStack extracted = fluidMonitor.extractItems(
                        new GenericStack(targetFluidKey, Integer.MAX_VALUE), Actionable.SIMULATE, src);
                if (extracted != null && extracted.amount() > 0) {
                    final var fillResult =
                            AEKeyType.fluids().fillToContainer(held, extracted, false);
                    if (fillResult.isSuccess()) {
                        fluidMonitor.extractItems(
                                new GenericStack(targetFluidKey, fillResult.getTransferredAmount()),
                                Actionable.MODULATE, src);
                        player.inventory.setItemStack(fillResult.getResultContainer());
                        this.updateHeld(player);
                    }
                }
            }
        } else if (action == InventoryAction.EMPTY_ITEM) {
            final var drainResult =
                    AEKeyType.fluids().drainFromContainer(held, Integer.MAX_VALUE, true);
            if (drainResult.isSuccess()) {
                final GenericStack notInserted = fluidMonitor.injectItems(
                        drainResult.getTransferredGenericStack(), Actionable.SIMULATE, src);
                if (notInserted == null || notInserted.amount() == 0) {
                    final var actualDrain =
                            AEKeyType.fluids().drainFromContainer(held,
                                    drainResult.getTransferredAmount(), false);
                    if (actualDrain.isSuccess()) {
                        fluidMonitor.injectItems(actualDrain.getTransferredGenericStack(), Actionable.MODULATE, src);
                        player.inventory.setItemStack(actualDrain.getResultContainer());
                        this.updateHeld(player);
                    }
                }
            }
        }
    }

}

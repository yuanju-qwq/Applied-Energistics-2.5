package appeng.fluids.container;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import baubles.api.BaublesApi;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.helper.WirelessContainerHelper;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.interfaces.IPortableFluidCellGuiCallback;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.api.stacks.AEKeyType;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

/**
 * @deprecated 便携流体单元 Container 将在后续版本统一�?
 *             {@link appeng.container.implementations.ContainerMEMonitorable} 体系。此类保留向后兼容�?
 */
@Deprecated
public class ContainerMEPortableFluidCell extends AEBaseContainer implements IAEAppEngInventory, IConfigManagerHost,
        IConfigurableObject, IMEMonitorHandlerReceiver, IUpgradeableCellContainer, IInventorySlotAware {

    protected final WirelessTerminalGuiObject wirelessTerminalGUIObject;
    protected final WirelessContainerHelper wirelessHelper;

    private final IConfigManager clientCM;
    private final IMEMonitor monitor;
    private KeyCounter fluids = new KeyCounter();
    @GuiSync(99)
    public boolean hasPower = false;
    private final ITerminalHost terminal;
    private IConfigManager serverCM;
    private IConfigManagerHost gui;
    private IGridNode networkNode;

    public ContainerMEPortableFluidCell(final InventoryPlayer ip, final IPortableCell monitorable) {
        this(ip, monitorable, null, true);
    }

    public ContainerMEPortableFluidCell(final InventoryPlayer ip, final IPortableCell monitorable,
            WirelessTerminalGuiObject iGuiItemObject) {
        this(ip, monitorable, iGuiItemObject, true);
    }

    public ContainerMEPortableFluidCell(InventoryPlayer ip, IPortableCell monitorable,
            WirelessTerminalGuiObject iGuiItemObject, boolean bindInventory) {
        super(ip, monitorable);

        this.terminal = monitorable;
        this.wirelessTerminalGUIObject = (WirelessTerminalGuiObject) monitorable;
        this.wirelessHelper = new WirelessContainerHelper(this.wirelessTerminalGUIObject, ip, this);

        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        if (Platform.isServer()) {
            this.serverCM = terminal.getConfigManager();
            this.monitor = terminal
                    .getInventory(AEKeyType.fluids());

            if (this.monitor != null) {
                this.monitor.addListener(this, null);

                this.setPowerSource((IEnergySource) terminal);
                final IGridNode node;
                if (terminal instanceof IGridHost) {
                    node = ((IGridHost) terminal).getGridNode(AEPartLocation.INTERNAL);
                } else {
                    node = ((IActionHost) terminal).getActionableNode();
                }

                if (node != null) {
                    this.networkNode = node;
                }
            }
        } else {
            this.monitor = null;
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 140);
        }
        hasPower = this.wirelessTerminalGUIObject.extractAEPower(this.wirelessHelper.getPowerMultiplier(),
                Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.001;
        this.wirelessHelper.initUpgrades(this);
        this.loadFromNBT();

        this.setupUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.wirelessHelper.tickWirelessStatus(this);

            if (this.monitor != this.terminal
                    .getInventory(AEKeyType.fluids())) {
                this.setValidContainer(false);
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

            if (!this.fluids.isEmpty()) {
                try {
                    final KeyCounter monitorCache = this.monitor.getKeyCounter();

                    final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (var entry : this.fluids) {
                        AEKey searchKey = entry.getKey();
                        long sendAmount = monitorCache.get(searchKey);
                        IAEFluidStack is = (IAEFluidStack) searchKey.toIAEStack(sendAmount);
                        if (is != null) {
                            piu.appendStack(is);
                        }
                    }

                    if (!piu.isEmpty()) {
                        this.fluids = new KeyCounter();

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
            super.detectAndSendChanges();
        }
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }
        EntityPlayerMP player = (EntityPlayerMP) p;
        if (this.inventorySlots.get(idx) instanceof SlotPlayerInv
                || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar) {
            final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx);
            ItemStack itemStack = clickSlot.getStack();

            if (!AEKeyType.fluids().isContainerItemForType(itemStack)) {
                return ItemStack.EMPTY;
            }

            int heldAmount = itemStack.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copy = itemStack.copy();
                copy.setCount(1);

                final ContainerInteractionResult<?> simDrain =
                        AEKeyType.fluids().drainFromContainer(copy, Integer.MAX_VALUE, true);
                if (!simDrain.isSuccess()) {
                    return ItemStack.EMPTY;
                }

                final GenericStack notStorable = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        simDrain.getTransferredGenericStack(), this.getActionSource(), Actionable.SIMULATE);

                long toDrain = simDrain.getTransferredAmount();
                if (notStorable != null && notStorable.amount() > 0) {
                    toDrain -= notStorable.amount();
                    if (toDrain <= 0) {
                        return ItemStack.EMPTY;
                    }
                }

                final ContainerInteractionResult<?> actualDrain =
                        AEKeyType.fluids().drainFromContainer(copy, toDrain, false);
                if (!actualDrain.isSuccess()) {
                    return ItemStack.EMPTY;
                }

                final GenericStack notInserted = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        actualDrain.getTransferredGenericStack(), this.getActionSource());

                if (notInserted != null && notInserted.amount() > 0) {
                    GenericStack spill = this.monitor.injectItems(notInserted, Actionable.MODULATE,
                            this.getActionSource());
                    if (spill != null && spill.amount() > 0) {
                        AEKeyType.fluids().fillToContainer(
                                actualDrain.getResultContainer(), spill, false);
                    }
                }

                if (notInserted == null || notInserted.amount() == 0) {
                    if (!player.inventory.addItemStackToInventory(actualDrain.getResultContainer())) {
                        player.dropItem(actualDrain.getResultContainer(), false);
                    }
                    clickSlot.decrStackSize(1);
                }
            }
            this.detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        return super.transferStackInSlot(p, idx);
    }

    @Override
    public void doAction(EntityPlayerMP player, InventoryAction action, int slot, long id) {
        if (action != InventoryAction.FILL_ITEM && action != InventoryAction.EMPTY_ITEM) {
            super.doAction(player, action, slot, id);
            return;
        }

        final ItemStack held = player.inventory.getItemStack();
        if (!AEKeyType.fluids().isContainerItemForType(held)) {
            return;
        }

        if (action == InventoryAction.FILL_ITEM && this.getTargetFluidStack() != null) {
            final IAEFluidStack target = this.getTargetFluidStack().copy();

            int heldAmount = held.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copiedFluidContainer = held.copy();
                copiedFluidContainer.setCount(1);

                final IAEFluidStack fillRequest = target.copy();
                fillRequest.setStackSize(Integer.MAX_VALUE);
                final ContainerInteractionResult<?> simFill =
                        AEKeyType.fluids().fillToContainer(copiedFluidContainer, fillRequest, true);
                if (!simFill.isSuccess()) {
                    return;
                }

                final GenericStack canPull = appeng.util.StorageHelper.poweredExtraction(
                        this.getPowerSource(), this.monitor,
                        new GenericStack(target.toAEKey(), simFill.getTransferredAmount()),
                        this.getActionSource(), Actionable.SIMULATE);
                if (canPull == null || canPull.amount() < 1) {
                    return;
                }

                final ContainerInteractionResult<?> simFill2 =
                        AEKeyType.fluids().fillToContainer(copiedFluidContainer, canPull, true);
                if (!simFill2.isSuccess()) {
                    return;
                }

                final GenericStack pulled = appeng.util.StorageHelper.poweredExtraction(
                        this.getPowerSource(), this.monitor,
                        new GenericStack(target.toAEKey(), simFill2.getTransferredAmount()),
                        this.getActionSource());
                if (pulled == null || pulled.amount() < 1) {
                    AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
                    return;
                }

                final ContainerInteractionResult<?> actualFill =
                        AEKeyType.fluids().fillToContainer(copiedFluidContainer, pulled, false);
                if (!actualFill.isSuccess()) {
                    AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.",
                            held.getDisplayName());
                }

                if (held.getCount() == 1) {
                    player.inventory.setItemStack(actualFill.getResultContainer());
                } else {
                    player.inventory.getItemStack().shrink(1);
                    if (!player.inventory.addItemStackToInventory(actualFill.getResultContainer())) {
                        player.dropItem(actualFill.getResultContainer(), false);
                    }
                }
            }
            this.updateHeld(player);

        } else if (action == InventoryAction.EMPTY_ITEM) {
            int heldAmount = held.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copiedFluidContainer = held.copy();
                copiedFluidContainer.setCount(1);

                final ContainerInteractionResult<?> simDrain =
                        AEKeyType.fluids().drainFromContainer(copiedFluidContainer, Integer.MAX_VALUE, true);
                if (!simDrain.isSuccess()) {
                    return;
                }

                final GenericStack notStorable = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        simDrain.getTransferredGenericStack(), this.getActionSource(), Actionable.SIMULATE);

                long toDrain = simDrain.getTransferredAmount();
                if (notStorable != null && notStorable.amount() > 0) {
                    toDrain -= notStorable.amount();
                    if (toDrain <= 0) {
                        return;
                    }
                }

                final ContainerInteractionResult<?> actualDrain =
                        AEKeyType.fluids().drainFromContainer(copiedFluidContainer, toDrain, false);
                if (!actualDrain.isSuccess()) {
                    return;
                }

                final GenericStack notInserted = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        actualDrain.getTransferredGenericStack(), this.getActionSource());

                if (notInserted != null && notInserted.amount() > 0) {
                    GenericStack spill = this.monitor.injectItems(notInserted, Actionable.MODULATE,
                            this.getActionSource());
                    if (spill != null && spill.amount() > 0) {
                        AEKeyType.fluids().fillToContainer(
                                actualDrain.getResultContainer(), spill, false);
                    }
                }

                if (held.getCount() == 1) {
                    player.inventory.setItemStack(actualDrain.getResultContainer());
                } else {
                    player.inventory.getItemStack().shrink(1);
                    if (!player.inventory.addItemStackToInventory(actualDrain.getResultContainer())) {
                        player.dropItem(actualDrain.getResultContainer(), false);
                    }
                }
            }
            this.updateHeld(player);
        }
    }

    public boolean isValid(Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(IBaseMonitor monitor, Iterable<GenericStack> change,
            IActionSource actionSource) {
        for (final GenericStack is : change) {
            if (is.toIAEStack() instanceof IAEFluidStack fluidStack) {
                this.fluids.add(fluidStack.toAEKey(), fluidStack.getStackSize());
            }
        }
    }

    @Override
    public void onListUpdate() {
        for (final IContainerListener c : this.listeners) {
            this.queueInventory(c);
        }
    }

    @Override
    public void addListener(IContainerListener listener) {
        super.addListener(listener);

        this.queueInventory(listener);
    }

    @Override
    public void onContainerClosed(final EntityPlayer player) {
        super.onContainerClosed(player);
        if (this.monitor != null) {
            this.monitor.removeListener(this);
        }
    }

    private void queueInventory(final IContainerListener c) {
        if (Platform.isServer() && c instanceof EntityPlayer && this.monitor != null) {
            try {
                PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();
                final KeyCounter monitorCache = this.monitor.getKeyCounter();

                for (var entry : monitorCache) {
                    try {
                        IAEFluidStack send = (IAEFluidStack) entry.getKey().toIAEStack(entry.getLongValue());
                        if (send != null) {
                            piu.appendStack(send);
                        }
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);

                        piu = new PacketMEInventoryUpdate();
                        IAEFluidStack send = (IAEFluidStack) entry.getKey().toIAEStack(entry.getLongValue());
                        if (send != null) {
                            piu.appendStack(send);
                        }
                    }
                }

                NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        if (Platform.isServer()) {
            return this.serverCM;
        }
        return this.clientCM;
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
        if (this.getGui() != null) {
            this.getGui().updateSetting(manager, settingName, newValue);
        }
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    @Override
    public int availableUpgrades() {
        return 1;
    }

    @Override
    public void setupUpgrades() {
        SlotRestrictedInput slot = this.wirelessHelper.createMagnetSlot(
                this.getInventoryPlayer(), 183, 139);
        if (slot != null) {
            this.addSlotToContainer(slot);
        }
    }

    @Override
    public void saveChanges() {
        this.wirelessHelper.saveChanges();
    }

    private void loadFromNBT() {
        this.wirelessHelper.loadUpgradesFromNBT();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
    }

    @Override
    public int getInventorySlot() {
        return this.wirelessHelper.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return this.wirelessHelper.isBaubleSlot();
    }

    /**
     * 客户端接收流体库存更新包�?
     */
    public void postUpdate(final List<IAEStack<?>> list) {
        final IConfigManagerHost gui = this.getGui();
        if (gui instanceof IPortableFluidCellGuiCallback callback) {
            callback.postUpdate(list);
        }
    }

    /**
     * Client-side handler for GenericStack format inventory updates.
     * TODO: Migrate GUI to accept RepoEntry directly, then eliminate this IAEStack bridge.
     * Converts GenericStack list to IAEStack and delegates to the legacy method.
     */
    public void postGenericStackUpdate(final List<GenericStack> list) {
        final List<IAEStack<?>> converted = new java.util.ArrayList<>(list.size());
        for (GenericStack gs : list) {
            IAEStack<?> aeStack = gs.toIAEStack();
            if (aeStack != null) {
                converted.add(aeStack);
            }
        }
        this.postUpdate(converted);
    }
}

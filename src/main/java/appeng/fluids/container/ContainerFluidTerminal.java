/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

import appeng.api.config.*;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
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
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEPartLocation;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.container.interfaces.IPortableFluidCellGuiCallback;
import appeng.fluids.util.AEFluidStackType;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.ChannelPowerSrc;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.Platform;

/**
 * @author BrockWS
 * @version rv6 - 12/05/2018
 * @since rv6 12/05/2018
 * @deprecated 使用 {@link appeng.container.implementations.ContainerMEMonitorable} 替代，
 *             该终端已支持物品+流体的统一浏览和桶交互。
 */
@Deprecated
public class ContainerFluidTerminal extends AEBaseContainer
        implements IConfigManagerHost, IConfigurableObject, IMEMonitorHandlerReceiver<IAEFluidStack> {
    private final IConfigManager clientCM;
    private final IMEMonitor<IAEFluidStack> monitor;
    private final IItemList<IAEFluidStack> fluids = AEFluidStackType.INSTANCE.createList();
    @GuiSync(99)
    public boolean hasPower = false;
    private final ITerminalHost terminal;
    private IConfigManager serverCM;
    private IConfigManagerHost gui;
    private IGridNode networkNode;

    public ContainerFluidTerminal(InventoryPlayer ip, ITerminalHost terminal) {
        super(ip, terminal);
        this.terminal = terminal;
        this.clientCM = new ConfigManager(this);

        this.clientCM.registerSetting(Settings.SORT_BY, SortOrder.NAME);
        this.clientCM.registerSetting(Settings.SORT_DIRECTION, SortDir.ASCENDING);
        this.clientCM.registerSetting(Settings.VIEW_MODE, ViewItems.ALL);
        if (Platform.isServer()) {
            this.serverCM = terminal.getConfigManager();
            this.monitor = terminal
                    .getInventory(AEFluidStackType.INSTANCE);

            if (this.monitor != null) {
                this.monitor.addListener(this, null);

                if (terminal instanceof IEnergySource) {
                    this.setPowerSource((IEnergySource) terminal);
                } else if (terminal instanceof IGridHost || terminal instanceof IActionHost) {
                    final IGridNode node;
                    if (terminal instanceof IGridHost) {
                        node = ((IGridHost) terminal).getGridNode(AEPartLocation.INTERNAL);
                    } else if (terminal instanceof IActionHost) {
                        node = ((IActionHost) terminal).getActionableNode();
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
            }
        } else {
            this.monitor = null;
        }
        this.bindPlayerInventory(ip, 0, 222 - 82);
    }

    @Override
    public boolean isValid(Object verificationToken) {
        return true;
    }

    @Override
    public void postChange(IBaseMonitor<IAEFluidStack> monitor, Iterable<IAEFluidStack> change,
            IActionSource actionSource) {
        for (final IAEFluidStack is : change) {
            this.fluids.add(is);
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
                final IItemList<IAEFluidStack> monitorCache = this.monitor.getStorageList();

                for (final IAEFluidStack send : monitorCache) {
                    try {
                        piu.appendStack(send);
                    } catch (final BufferOverflowException boe) {
                        NetworkHandler.instance().sendTo(piu, (EntityPlayerMP) c);

                        piu = new PacketMEInventoryUpdate();
                        piu.appendStack(send);
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

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            if (this.monitor != this.terminal
                    .getInventory(AEFluidStackType.INSTANCE)) {
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
                    final IItemList<IAEFluidStack> monitorCache = this.monitor.getStorageList();

                    final PacketMEInventoryUpdate piu = new PacketMEInventoryUpdate();

                    for (final IAEFluidStack is : this.fluids) {
                        final IAEFluidStack send = monitorCache.findPrecise(is);
                        if (send == null) {
                            is.setStackSize(0);
                            piu.appendStack(is);
                        } else {
                            piu.appendStack(send);
                        }
                    }

                    if (!piu.isEmpty()) {
                        this.fluids.resetStatus();

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

            if (!AEFluidStackType.INSTANCE.isContainerItemForType(itemStack)) {
                return ItemStack.EMPTY;
            }

            int heldAmount = itemStack.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copy = itemStack.copy();
                copy.setCount(1);

                // Simulate drain
                final ContainerInteractionResult<IAEFluidStack> simDrain =
                        AEFluidStackType.INSTANCE.drainFromContainer(copy, Integer.MAX_VALUE, true);
                if (!simDrain.isSuccess()) {
                    return ItemStack.EMPTY;
                }

                // Simulate insert
                final IAEFluidStack notStorable = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        simDrain.getTransferred(), this.getActionSource(), Actionable.SIMULATE);

                long toDrain = simDrain.getTransferred().getStackSize();
                if (notStorable != null && notStorable.getStackSize() > 0) {
                    toDrain -= notStorable.getStackSize();
                    if (toDrain <= 0) {
                        return ItemStack.EMPTY;
                    }
                }

                // Actually drain
                final ContainerInteractionResult<IAEFluidStack> actualDrain =
                        AEFluidStackType.INSTANCE.drainFromContainer(copy, toDrain, false);
                if (!actualDrain.isSuccess()) {
                    return ItemStack.EMPTY;
                }

                // Insert into ME
                final IAEFluidStack notInserted = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        actualDrain.getTransferred(), this.getActionSource());

                if (notInserted != null && notInserted.getStackSize() > 0) {
                    IAEFluidStack spill = this.monitor.injectItems(notInserted, Actionable.MODULATE,
                            this.getActionSource());
                    if (spill != null && spill.getStackSize() > 0) {
                        AEFluidStackType.INSTANCE.fillToContainer(
                                actualDrain.getResultContainer(), spill, false);
                    }
                }

                if (notInserted == null || notInserted.getStackSize() == 0) {
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
        if (!AEFluidStackType.INSTANCE.isContainerItemForType(held)) {
            return;
        }

        if (action == InventoryAction.FILL_ITEM && this.getTargetFluidStack() != null) {
            final IAEFluidStack target = this.getTargetFluidStack().copy();

            int heldAmount = held.getCount();
            for (int i = 0; i < heldAmount; i++) {
                ItemStack copiedFluidContainer = held.copy();
                copiedFluidContainer.setCount(1);

                // Simulate fill
                final IAEFluidStack fillRequest = target.copy();
                fillRequest.setStackSize(Integer.MAX_VALUE);
                final ContainerInteractionResult<IAEFluidStack> simFill =
                        AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, fillRequest, true);
                if (!simFill.isSuccess()) {
                    return;
                }

                // Simulate pull from ME
                final IAEFluidStack canPull = appeng.util.StorageHelper.poweredExtraction(
                        this.getPowerSource(), this.monitor,
                        target.copy().setStackSize(simFill.getTransferred().getStackSize()),
                        this.getActionSource(), Actionable.SIMULATE);
                if (canPull == null || canPull.getStackSize() < 1) {
                    return;
                }

                // Re-simulate with actual available
                final ContainerInteractionResult<IAEFluidStack> simFill2 =
                        AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, canPull, true);
                if (!simFill2.isSuccess()) {
                    return;
                }

                // Actually pull
                final IAEFluidStack pulled = appeng.util.StorageHelper.poweredExtraction(
                        this.getPowerSource(), this.monitor,
                        target.copy().setStackSize(simFill2.getTransferred().getStackSize()),
                        this.getActionSource());
                if (pulled == null || pulled.getStackSize() < 1) {
                    AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
                    return;
                }

                // Actually fill
                final ContainerInteractionResult<IAEFluidStack> actualFill =
                        AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, pulled, false);
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

                // Simulate drain
                final ContainerInteractionResult<IAEFluidStack> simDrain =
                        AEFluidStackType.INSTANCE.drainFromContainer(copiedFluidContainer, Integer.MAX_VALUE, true);
                if (!simDrain.isSuccess()) {
                    return;
                }

                // Simulate insert into ME
                final IAEFluidStack notStorable = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        simDrain.getTransferred(), this.getActionSource(), Actionable.SIMULATE);

                long toDrain = simDrain.getTransferred().getStackSize();
                if (notStorable != null && notStorable.getStackSize() > 0) {
                    toDrain -= notStorable.getStackSize();
                    if (toDrain <= 0) {
                        return;
                    }
                }

                // Actually drain
                final ContainerInteractionResult<IAEFluidStack> actualDrain =
                        AEFluidStackType.INSTANCE.drainFromContainer(copiedFluidContainer, toDrain, false);
                if (!actualDrain.isSuccess()) {
                    return;
                }

                // Insert into ME
                final IAEFluidStack notInserted = appeng.util.StorageHelper.poweredInsert(
                        this.getPowerSource(), this.monitor,
                        actualDrain.getTransferred(), this.getActionSource());

                if (notInserted != null && notInserted.getStackSize() > 0) {
                    IAEFluidStack spill = this.monitor.injectItems(notInserted, Actionable.MODULATE,
                            this.getActionSource());
                    if (spill != null && spill.getStackSize() > 0) {
                        AEFluidStackType.INSTANCE.fillToContainer(
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
        } catch (final Exception ignore) {
            // :P
        }
    }

    private IConfigManagerHost getGui() {
        return this.gui;
    }

    public void setGui(@Nonnull final IConfigManagerHost gui) {
        this.gui = gui;
    }

    public boolean isPowered() {
        return this.hasPower;
    }

    private void setPowered(final boolean isPowered) {
        this.hasPower = isPowered;
    }

    /**
     * 客户端接收流体库存更新包。
     */
    public void postUpdate(final List<IAEStack<?>> list) {
        final IConfigManagerHost gui = this.getGui();
        if (gui instanceof IPortableFluidCellGuiCallback callback) {
            callback.postUpdate(list);
        }
    }
}

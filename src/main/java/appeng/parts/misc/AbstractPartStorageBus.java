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

package appeng.parts.misc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;

import appeng.api.config.*;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.ITickManager;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.storage.*;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.MachineSource;
import appeng.me.storage.ITickingMonitor;
import appeng.me.storage.MEInventoryHandler;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.ConfigManager;
import appeng.util.Platform;
import appeng.util.prioritylist.FuzzyPriorityList;
import appeng.util.prioritylist.PrecisePriorityList;

/**
 * Abstract base class for storage bus parts. Extracts the common logic shared by
 * item and fluid storage bus implementations, parameterized by the AE stack type.
 *
 * @param <T> the type of {@link IAEStack} this storage bus handles
 */
public abstract class AbstractPartStorageBus<T extends IAEStack<T>> extends PartUpgradeable
        implements IGridTickable, ICellContainer, IMEMonitorHandlerReceiver<T>, IPriorityHost {

    protected final IActionSource mySrc;
    protected int priority = 0;
    protected boolean cached = false;
    protected ITickingMonitor monitor = null;
    protected MEInventoryHandler<T> handler = null;
    protected int handlerHash = 0;
    private boolean wasActive = false;
    private byte resetCacheLogic = 0;
    private boolean accessChanged;
    private boolean readOncePass;

    public AbstractPartStorageBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.ACCESS, AccessRestriction.READ_WRITE);
        this.getConfigManager().registerSetting(Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL);
        this.getConfigManager().registerSetting(Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY);
        this.mySrc = new MachineSource(this);
    }

    // ---- Abstract methods for type-specific behavior ----

    /**
     * @return the {@link IAEStackType} this storage bus operates on
     */
    public abstract IAEStackType<T> getStackType();

    /**
     * @return the {@link TickRates} entry for this storage bus type
     */
    protected abstract TickRates getTickRates();

    /**
     * @return the GUI key to open when the player activates this part
     */
    protected abstract AEGuiKey getGuiKey();

    /**
     * Wraps the target tile entity's inventory into an ME inventory for this stack type.
     *
     * @param target the target tile entity
     * @return the wrapped ME inventory, or null if not applicable
     */
    protected abstract IMEInventory<T> getInventoryWrapper(TileEntity target);

    /**
     * Creates a hash for the target tile entity's handler to detect changes.
     *
     * @param target the target tile entity
     * @return a hash representing the handler state
     */
    protected abstract int createHandlerHash(TileEntity target);

    /**
     * Checks whether the given neighbor tile entity is an interface type relevant to this storage bus.
     * Used in {@link #onNeighborChanged} to decide whether to reset cache.
     *
     * @param te the tile entity to check
     * @return true if the tile entity is a relevant interface type
     */
    protected abstract boolean isRelevantInterfaceTile(TileEntity te);

    /**
     * Checks whether the given part (from a cable bus) is an interface type relevant to this storage bus.
     *
     * @param part the part to check
     * @return true if the part is a relevant interface part
     */
    protected abstract boolean isRelevantInterfacePart(appeng.api.parts.IPart part);

    /**
     * Populates the priority list from the config inventory.
     *
     * @param slotsToUse the number of config slots to read
     * @return the populated priority list
     */
    protected abstract IItemList<T> buildPriorityList(int slotsToUse);

    /**
     * @return the item stack representation for this part
     */
    @Override
    public abstract ItemStack getItemStackRepresentation();

    /**
     * Reads the config inventory from NBT.
     */
    protected abstract void readConfigFromNBT(NBTTagCompound data);

    /**
     * Writes the config inventory to NBT.
     */
    protected abstract void writeConfigToNBT(NBTTagCompound data);

    // ---- Common implementation ----

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.updateStatus();
    }

    private void updateStatus() {
        final boolean currentActive = this.getProxy().isActive();
        if (this.wasActive != currentActive) {
            this.wasActive = currentActive;
            try {
                this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
                this.getHost().markForUpdate();
            } catch (final GridAccessException e) {
                // :P
            }
        }
    }

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged changedChannels) {
        this.updateStatus();
    }

    @Override
    protected int getUpgradeSlots() {
        return 5;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        if (settingName.name().equals("ACCESS")) {
            this.accessChanged = true;
        }
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public void upgradesChanged() {
        super.upgradesChanged();
        this.resetCache(true);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.readConfigFromNBT(data);
        this.priority = data.getInteger("priority");
        this.accessChanged = false;
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.writeConfigToNBT(data);
        data.setInteger("priority", this.priority);
    }

    protected void resetCache(final boolean fullReset) {
        if (this.getHost() == null || this.getHost().getTile() == null || this.getHost().getTile().getWorld() == null
                || this.getHost().getTile().getWorld().isRemote) {
            return;
        }

        if (fullReset) {
            this.resetCacheLogic = 2;
        } else if (this.resetCacheLogic < 2) {
            this.resetCacheLogic = 1;
        }

        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    public boolean isValid(final Object verificationToken) {
        return this.handler == verificationToken;
    }

    @Override
    public void postChange(final IBaseMonitor<T> monitor, final Iterable<T> change,
            final IActionSource source) {
        if (this.getProxy().isActive()) {
            var filteredChanges = this.filterChanges(change);

            AccessRestriction currentAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager())
                    .getSetting(Settings.ACCESS);
            if (readOncePass) {
                readOncePass = false;
                try {
                    this.getProxy().getStorage().postAlterationOfStoredItems(
                            this.getStackType(), filteredChanges,
                            mySrc);
                } catch (final GridAccessException e) {
                    // :(
                }
                return;
            }
            if (!currentAccess.hasPermission(AccessRestriction.READ)) {
                return;
            }
            try {
                this.getProxy().getStorage().postAlterationOfStoredItems(
                        this.getStackType(), filteredChanges,
                        source);
            } catch (final GridAccessException e) {
                // :(
            }
        }
    }

    @Override
    public void onListUpdate() {
        // not used here.
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(3, 3, 15, 13, 13, 16);
        bch.addBox(2, 2, 14, 14, 14, 15);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (pos.offset(this.getSide().getFacing()).equals(neighbor)) {
            final TileEntity te = w.getTileEntity(neighbor);

            // In case the TE was destroyed, we have to do a full reset immediately.
            if (te instanceof appeng.tile.networking.TileCableBus) {
                appeng.api.parts.IPart iPart = ((appeng.tile.networking.TileCableBus) te)
                        .getPart(this.getSide().getOpposite());
                if (iPart == null) {
                    this.resetCache(true);
                    this.resetCache();
                } else if (this.isRelevantInterfacePart(iPart)) {
                    if (createHandlerHash(te) != handlerHash) {
                        this.resetCache(true);
                        this.resetCache();
                    }
                }
            } else if (te == null) {
                this.resetCache(true);
                this.resetCache();
            } else if (this.isRelevantInterfaceTile(te)) {
                if (createHandlerHash(te) != handlerHash) {
                    this.resetCache(true);
                    this.resetCache();
                }
            } else {
                this.resetCache(false);
            }
        }
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), this.getGuiKey());
        }
        return true;
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        TickRates rates = this.getTickRates();
        return new TickingRequest(rates.getMin(), rates.getMax(), this.monitor == null, true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        if (this.resetCacheLogic != 0) {
            this.resetCache();
        }

        if (this.monitor != null) {
            return this.monitor.onTick();
        }

        return TickRateModulation.SLEEP;
    }

    @SuppressWarnings("unchecked")
    protected void resetCache() {
        final boolean fullReset = this.resetCacheLogic == 2;
        this.resetCacheLogic = 0;

        final MEInventoryHandler<T> in = this.getInternalHandler();

        IItemList<T> before = this.getStackType().createList();
        if (in != null) {
            if (accessChanged) {
                AccessRestriction currentAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager())
                        .getSetting(Settings.ACCESS);
                AccessRestriction oldAccess = (AccessRestriction) ((ConfigManager) this.getConfigManager())
                        .getOldSetting(Settings.ACCESS);
                if (oldAccess.hasPermission(AccessRestriction.READ)
                        && !currentAccess.hasPermission(AccessRestriction.READ)) {
                    readOncePass = true;
                }
                in.setBaseAccess(oldAccess);
                before = in.getAvailableItems(before);
                in.setBaseAccess(currentAccess);
                accessChanged = false;
            } else {
                before = in.getAvailableItems(before);
            }
        }

        this.cached = false;
        if (fullReset) {
            this.handlerHash = 0;
        }

        final MEInventoryHandler<T> out = this.getInternalHandler();

        IItemList<T> after = this.getStackType().createList();

        if (in != out) {
            if (out != null) {
                after = out.getAvailableItems(after);
            }
            appeng.util.StorageHelper.postListChanges(before, after, this, this.mySrc);
        }
    }

    @SuppressWarnings("unchecked")
    public MEInventoryHandler<T> getInternalHandler() {
        if (this.cached) {
            return this.handler;
        }

        final boolean wasSleeping = this.monitor == null;

        this.cached = true;
        final TileEntity self = this.getHost().getTile();
        final TileEntity target = self.getWorld().getTileEntity(self.getPos().offset(this.getSide().getFacing()));
        final int newHandlerHash = this.createHandlerHash(target);

        if (newHandlerHash != 0 && newHandlerHash == this.handlerHash) {
            return this.handler;
        }

        this.handlerHash = newHandlerHash;
        this.handler = null;
        if (this.monitor != null) {
            ((IBaseMonitor<T>) monitor).removeListener(this);
        }
        this.monitor = null;
        if (target != null) {
            IMEInventory<T> inv = this.getInventoryWrapper(target);

            if (inv instanceof ITickingMonitor) {
                this.monitor = (ITickingMonitor) inv;
                this.monitor.setActionSource(mySrc);
                this.monitor.setMode((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));
            }

            if (inv != null) {
                this.handler = new MEInventoryHandler<>(inv, this.getStackType());

                this.handler.setBaseAccess((AccessRestriction) this.getConfigManager().getSetting(Settings.ACCESS));
                this.handler.setWhitelist(this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST
                        : IncludeExclude.WHITELIST);
                this.handler.setPriority(this.priority);
                this.handler
                        .setStorageFilter((StorageFilter) this.getConfigManager().getSetting(Settings.STORAGE_FILTER));

                final IItemList<T> priorityList = this.buildPriorityList(
                        18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9);

                if (this.getInstalledUpgrades(Upgrades.STICKY) > 0) {
                    this.handler.setSticky(true);
                }

                if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
                    this.handler.setPartitionList(new FuzzyPriorityList<>(priorityList,
                            (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)));
                } else {
                    this.handler.setPartitionList(new PrecisePriorityList<>(priorityList));
                }

                if (inv instanceof IBaseMonitor) {
                    if (((AccessRestriction) ((ConfigManager) this.getConfigManager()).getSetting(Settings.ACCESS))
                            .hasPermission(AccessRestriction.READ)) {
                        ((IBaseMonitor<T>) inv).addListener(this, this.handler);
                    }
                }
            }
        }

        // update sleep state...
        if (wasSleeping != (this.monitor == null)) {
            try {
                final ITickManager tm = this.getProxy().getTick();
                if (this.monitor == null) {
                    tm.sleepDevice(this.getProxy().getNode());
                } else {
                    tm.wakeDevice(this.getProxy().getNode());
                }
            } catch (final GridAccessException e) {
                // :(
            }
        }

        try {
            // force grid to update handlers...
            ((GridStorageCache) this.getProxy().getGrid().getCache(IStorageGrid.class)).cellUpdate(null);
        } catch (final GridAccessException e) {
            // :3
        }

        return this.handler;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends IAEStack<S>> List<IMEInventoryHandler<S>> getCellArray(final IAEStackType<S> type) {
        if (type == this.getStackType()) {
            final IMEInventoryHandler<T> out = this.getInternalHandler();
            if (out != null) {
                return Collections.singletonList((IMEInventoryHandler<S>) out);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int newValue) {
        this.priority = newValue;
        this.getHost().markForSave();
        this.resetCache(true);
    }

    @Override
    public void blinkCell(final int slot) {
    }

    @Override
    public void saveChanges(final ICellInventory<?> cellInventory) {
        // nope!
    }

    @Override
    public GuiBridge getGuiBridge() {
        return getGuiKey().getLegacyBridge();
    }

    /**
     * Filters the changes to only include items that pass the storage filter. Optimally, this should be handled by the
     * underlying monitor.
     */
    protected Iterable<T> filterChanges(Iterable<T> change) {
        var storageFilter = this.getConfigManager().getSetting(Settings.STORAGE_FILTER);
        if (storageFilter == StorageFilter.EXTRACTABLE_ONLY && handler != null) {
            var filteredList = new ArrayList<T>();
            for (final T stack : change) {
                if (this.handler.passesBlackOrWhitelist(stack)) {
                    filteredList.add(stack);
                }
            }

            return filteredList;
        }
        return change;
    }
}

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

package appeng.parts.automation;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.StorageName;
import appeng.api.util.IConfigManager;
import appeng.core.settings.TickRates;
import appeng.fluids.helper.IConfigurableAEStackInventory;
import appeng.me.GridAccessException;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;

/**
 * Abstract base class for all IO buses (Import Bus and Export Bus), both item and fluid variants.
 * <p>
 * Provides common functionality:
 * <ul>
 *   <li>9-slot {@link IAEStackInventory} config</li>
 *   <li>NBT read/write for config</li>
 *   <li>Redstone mode with pulse detection</li>
 *   <li>Grid tick scheduling</li>
 *   <li>Adjacent TileEntity lookup with chunk-loaded check</li>
 *   <li>Transfer amount calculation based on speed upgrades</li>
 *   <li>Available slots calculation based on capacity upgrades</li>
 * </ul>
 *
 * Subclasses must implement:
 * <ul>
 *   <li>{@link #doBusWork()} — actual transfer logic</li>
 *   <li>{@link #getTickRates()} — tick rate for the grid scheduler</li>
 * </ul>
 */
public abstract class AbstractPartIOBus extends PartUpgradeable
        implements IGridTickable, IIAEStackInventory, IConfigurableAEStackInventory {

    private final IAEStackInventory config = new IAEStackInventory(this, 9, StorageName.CONFIG);
    private boolean lastRedstone = false;

    /**
     * Indicates that an I/O bus in redstone pulse mode has observed a low to high redstone transition
     * and is waiting to act on this during its next tick.
     */
    private boolean pendingPulse = false;

    public AbstractPartIOBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE);
    }

    // ========== Tick Rates ==========

    /**
     * @return the tick rate configuration for this bus type (e.g. TickRates.ImportBus, TickRates.FluidExportBus)
     */
    protected abstract TickRates getTickRates();

    // ========== Bus Work ==========

    /**
     * Perform the actual bus transfer work. Called once per tick when the bus is awake.
     *
     * @return modulation indicating how busy the bus is
     */
    protected abstract TickRateModulation doBusWork();

    // ========== Redstone Mode ==========

    @Override
    public RedstoneMode getRSMode() {
        return (RedstoneMode) this.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED);
    }

    private boolean isInPulseMode() {
        return getRSMode() == RedstoneMode.SIGNAL_PULSE;
    }

    @Override
    public void upgradesChanged() {
        this.updateRedstoneState();
    }

    @Override
    public void updateSetting(IConfigManager manager, Enum<?> settingName, Enum<?> newValue) {
        super.updateSetting(manager, settingName, newValue);

        this.updateRedstoneState();

        // Ensure we have an up-to-date last redstone state when pulse mode is activated to
        // correctly detect subsequent pulses
        if (isInPulseMode()) {
            this.lastRedstone = getHost().hasRedstone(this.getSide());
        }
    }

    // ========== NBT ==========

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
        this.getConfig().readFromNBT(extra, "config");
        pendingPulse = isInPulseMode() && extra.getBoolean("pendingPulse");
    }

    @Override
    public void writeToNBT(final NBTTagCompound extra) {
        super.writeToNBT(extra);
        this.getConfig().writeToNBT(extra, "config");
        if (isInPulseMode() && pendingPulse) {
            extra.setBoolean("pendingPulse", true);
        }
    }

    // ========== Inventory Access ==========

    @Override
    public IItemHandler getInventoryByName(final String name) {
        // Config is now IAEStackInventory, not IItemHandler.
        // Use getAEStackInventoryByName("config") or getAEInventoryByName(StorageName.CONFIG) instead.
        return super.getInventoryByName(name);
    }

    @Override
    public IAEStackInventory getAEStackInventoryByName(final String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return null;
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }

    @Override
    public void saveAEStackInv() {
        this.getHost().markForSave();
    }

    IAEStackInventory getConfig() {
        return this.config;
    }

    // ========== Neighbor / Redstone ==========

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        if (isInPulseMode()) {
            var hostIsPowered = this.getHost().hasRedstone(this.getSide());
            if (this.lastRedstone != hostIsPowered) {
                this.lastRedstone = hostIsPowered;
                if (this.lastRedstone && !this.pendingPulse) {
                    // Perform the action based on the pulse on the next tick
                    this.pendingPulse = true;
                    alertDevice();
                }
            }
        } else {
            // This handles waking up the bus if the adjacent redstone has changed
            this.updateRedstoneState();
        }
    }

    private void alertDevice() {
        try {
            this.getProxy().getTick().alertDevice(this.getProxy().getNode());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    private void updateRedstoneState() {
        // Clear the pending pulse flag if the upgrade is removed or the config is toggled off
        if (!this.isInPulseMode()) {
            this.pendingPulse = false;
        }
        try {
            if (!this.isSleeping()) {
                this.getProxy().getTick().wakeDevice(this.getProxy().getNode());
            } else {
                this.getProxy().getTick().sleepDevice(this.getProxy().getNode());
            }
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    protected boolean isSleeping() {
        if (isInPulseMode() && this.pendingPulse) {
            return false;
        } else {
            return super.isSleeping();
        }
    }

    @Override
    public void addToWorld() {
        super.addToWorld();

        // To correctly detect pulses (changes from low to high), we need to know the current state when we
        // are added to the world.
        this.lastRedstone = this.getHost().hasRedstone(this.getSide());
        // We may have observed a redstone pulse before, but were unable to act on it due to being unloaded before
        // we ticked again. Ensure that we do act on this pulse as soon as possible.
        if (pendingPulse) {
            alertDevice();
        }
    }

    // ========== Ticking ==========

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        TickRates rates = this.getTickRates();
        return new TickingRequest(rates.getMin(), rates.getMax(), this.isSleeping(), true);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        // Sometimes between being woken up and actually doing work, the config/redstone mode may have changed
        // put us back to sleep if that was the case
        if (this.isSleeping()) {
            return TickRateModulation.SLEEP;
        }

        // Reset a potential redstone pulse trigger
        this.pendingPulse = false;

        TickRateModulation hasDoneWork = this.doBusWork();

        // We may be back to sleep (i.e. in pulse mode)
        if (isSleeping()) {
            return TickRateModulation.SLEEP;
        } else {
            return hasDoneWork;
        }
    }

    // ========== Adjacent Tile Entity ==========

    /**
     * Get the TileEntity adjacent to this bus (on the side it faces).
     *
     * @return the adjacent TileEntity, or null if the chunk is not loaded
     */
    protected TileEntity getConnectedTE() {
        final TileEntity self = this.getHost().getTile();
        return this.getTileEntity(self, self.getPos().offset(this.getSide().getFacing()));
    }

    private TileEntity getTileEntity(final TileEntity self, final BlockPos pos) {
        final World w = self.getWorld();

        if (w.getChunkProvider().getLoadedChunk(pos.getX() >> 4, pos.getZ() >> 4) != null) {
            return w.getTileEntity(pos);
        }

        return null;
    }

    /**
     * Checks if the bus can actually do something.
     * <p>
     * Currently this tests if the chunk for the target is actually loaded.
     *
     * @return true, if the the bus should do its work.
     */
    protected boolean canDoBusWork() {
        final TileEntity self = this.getHost().getTile();
        final BlockPos selfPos = self.getPos().offset(this.getSide().getFacing());
        final int xCoordinate = selfPos.getX();
        final int zCoordinate = selfPos.getZ();
        final World world = self.getWorld();

        return world != null && world.getChunkProvider().getLoadedChunk(xCoordinate >> 4, zCoordinate >> 4) != null;
    }

    // ========== Transfer Amount Calculation ==========

    /**
     * Calculate the number of available config slots based on installed Capacity upgrades.
     */
    protected int availableSlots() {
        return Math.min(1 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 4, this.getConfig().getSizeInventory());
    }

    /**
     * Calculate the amount to transfer per tick based on installed Speed upgrades.
     * Uses the base transfer factor (1 for items, e.g. 1000 for fluids in mB).
     * <p>
     * Speed upgrade scaling:
     * <ul>
     *   <li>0 upgrades: 1x base</li>
     *   <li>1 upgrade: 8x base</li>
     *   <li>2 upgrades: 32x base</li>
     *   <li>3 upgrades: 64x base</li>
     *   <li>4 upgrades: 96x base</li>
     * </ul>
     *
     * @param baseTransferFactor the base amount for this type (e.g. 1 for items, transferFactor() for fluids)
     * @return the amount to transfer this tick
     */
    protected int calculateAmountToSend(int baseTransferFactor) {
        double amount = baseTransferFactor;
        switch (this.getInstalledUpgrades(Upgrades.SPEED)) {
            case 4:
                amount = amount * 1.5;
            case 3:
                amount = amount * 2;
            case 2:
                amount = amount * 4;
            case 1:
                amount = amount * 8;
            case 0:
            default:
                return MathHelper.floor(amount);
        }
    }
}

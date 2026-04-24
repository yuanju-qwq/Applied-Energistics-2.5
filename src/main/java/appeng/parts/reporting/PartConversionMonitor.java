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

package appeng.parts.reporting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.parts.ConversionMonitorHandlerRegistry;
import appeng.api.parts.IConversionMonitorHandler;
import appeng.api.parts.IConversionMonitorHost;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AppEng;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.me.helpers.PlayerSource;
import appeng.parts.PartModel;
import appeng.util.Platform;

public class PartConversionMonitor extends AbstractPartMonitor implements IConversionMonitorHost {

    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_on");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_OFF = new ResourceLocation(AppEng.MOD_ID,
            "part/conversion_monitor_locked_off");
    @PartModels
    public static final ResourceLocation MODEL_LOCKED_ON = new ResourceLocation(AppEng.MOD_ID,
            "part/conversion_monitor_locked_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);
    public static final IPartModel MODELS_LOCKED_OFF = new PartModel(MODEL_BASE, MODEL_LOCKED_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_LOCKED_ON = new PartModel(MODEL_BASE, MODEL_LOCKED_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_LOCKED_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_LOCKED_ON,
            MODEL_STATUS_HAS_CHANNEL);

    @Reflected
    public PartConversionMonitor(final ItemStack is) {
        super(is);
    }

    // ==================== IConversionMonitorHost ====================

    @Override
    public TileEntity getTile() {
        return super.getTile();
    }

    @Override
    public EnumFacing getSideFacing() {
        return this.getSide().getFacing();
    }

    // ==================== Activation (Right-click) ====================

    @Override
    public boolean onPartActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        final ItemStack eq = player.getHeldItem(hand);

        // Find a handler whose container can interact with the held item (e.g., fluid container)
        final IConversionMonitorHandler<?> containerHandler = findContainerHandler(eq);

        if (this.isLocked()) {
            return handleLockedActivate(player, hand, pos, eq, containerHandler);
        }

        // Unlocked mode: try container handlers first, then item handler
        return handleUnlockedActivate(player, hand, pos, eq, containerHandler);
    }

    /**
     * Handle right-click when the monitor is locked.
     */
    private boolean handleLockedActivate(EntityPlayer player, EnumHand hand, Vec3d pos,
            ItemStack eq, IConversionMonitorHandler<?> containerHandler) {
        if (eq.isEmpty()) {
            // Empty hand + locked = insert all matching items from inventory
            insertAllMatching(player);
        } else if (Platform.isWrench(player, eq, this.getLocation().getPos())
                && (this.getDisplayed() == null || !this.getDisplayed().equals(eq))) {
            // Wrench interaction
            return super.onPartActivate(player, hand, pos);
        } else if (containerHandler != null) {
            // Held item is a container for some type — check if it matches the displayed stack
            final IAEStack<?> containerStack = containerHandler.getStackFromContainer(eq);
            if (this.getDisplayed() != null && containerStack != null
                    && this.getDisplayed().equals(containerStack)) {
                insertFromContainer(containerHandler, player, hand);
            }
        } else {
            // Regular item — insert single item
            insertSingle(player, hand);
        }

        // Also handle non-locked path below (original code fell through)
        return handleUnlockedFallthrough(player, hand, pos, eq, containerHandler);
    }

    /**
     * Handle the fallthrough logic that the original code had after the locked block.
     * The original onPartActivate continued execution after the locked block.
     */
    private boolean handleUnlockedFallthrough(EntityPlayer player, EnumHand hand, Vec3d pos,
            ItemStack eq, IConversionMonitorHandler<?> containerHandler) {
        if (containerHandler != null) {
            final IAEStack<?> containerStack = containerHandler.getStackFromContainer(eq);
            if (this.getDisplayed() == null || !isSameStackType(this.getDisplayed(), containerHandler)) {
                return super.onPartActivate(player, hand, pos);
            }
            if (containerStack != null && this.getDisplayed().equals(containerStack)) {
                insertFromContainer(containerHandler, player, hand);
            } else {
                return super.onPartActivate(player, hand, pos);
            }
        } else if (this.getDisplayed() != null && this.getDisplayed().equals(player.getHeldItem(hand))) {
            insertSingle(player, hand);
        }
        return super.onPartActivate(player, hand, pos);
    }

    /**
     * Handle right-click when the monitor is unlocked (non-locked path only, no locked block above).
     */
    private boolean handleUnlockedActivate(EntityPlayer player, EnumHand hand, Vec3d pos,
            ItemStack eq, IConversionMonitorHandler<?> containerHandler) {
        if (containerHandler != null) {
            final IAEStack<?> containerStack = containerHandler.getStackFromContainer(eq);
            if (this.getDisplayed() == null || !isSameStackType(this.getDisplayed(), containerHandler)) {
                return super.onPartActivate(player, hand, pos);
            }
            if (containerStack != null && this.getDisplayed().equals(containerStack)) {
                insertFromContainer(containerHandler, player, hand);
            } else {
                return super.onPartActivate(player, hand, pos);
            }
        } else if (this.getDisplayed() != null && this.getDisplayed().equals(player.getHeldItem(hand))) {
            insertSingle(player, hand);
        }
        return super.onPartActivate(player, hand, pos);
    }

    // ==================== Click (Left-click) ====================

    @Override
    public boolean onClicked(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        final IAEStack<?> displayed = this.getDisplayed();
        if (displayed != null) {
            extractDisplayed(player, hand, displayed, getDefaultExtractCount(displayed));
        }

        return true;
    }

    // ==================== Shift-Click ====================

    @Override
    public boolean onShiftClicked(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (this.getDisplayed() != null) {
            extractDisplayed(player, hand, this.getDisplayed(), 1);
        }

        return true;
    }

    // ==================== Generic dispatch methods ====================

    /**
     * Find a handler that considers the held item as a container (e.g., fluid container).
     */
    private IConversionMonitorHandler<?> findContainerHandler(ItemStack heldItem) {
        if (heldItem.isEmpty()) {
            return null;
        }
        for (IConversionMonitorHandler<?> handler : ConversionMonitorHandlerRegistry.getAllHandlers()) {
            if (handler.canInteractWithContainer(heldItem)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * Check if the displayed stack's type matches the handler's type.
     */
    private boolean isSameStackType(IAEStack<?> displayed, IConversionMonitorHandler<?> handler) {
        return displayed.getStackTypeBase() == handler.getStackType();
    }

    /**
     * Get the default extraction count for a left-click on the displayed stack.
     * For items: max stack size of the item. For other types: delegates to handler.
     */
    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> long getDefaultExtractCount(IAEStack<?> displayed) {
        final IAEStackType<T> stackType = (IAEStackType<T>) displayed.getStackTypeBase();
        // Use the amount per unit as a reasonable default batch size
        // For items (amountPerUnit=1): extracting 1 means extract one item,
        //   but we actually want maxStackSize — however that's item-specific.
        // We let the handler's extractToPlayer handle the actual count interpretation.
        // For now, use the displayed stack's info through createItemStack if available.
        if (displayed.asItemStackRepresentation() != ItemStack.EMPTY) {
            return displayed.asItemStackRepresentation().getMaxStackSize();
        }
        return stackType.getAmountPerUnit();
    }

    /**
     * Insert from a container (e.g., drain fluid from a bucket into the network).
     */
    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void insertFromContainer(
            IConversionMonitorHandler<?> rawHandler, EntityPlayer player, EnumHand hand) {
        try {
            final IConversionMonitorHandler<T> handler = (IConversionMonitorHandler<T>) rawHandler;
            final IEnergySource energy = this.getProxy().getEnergy();
            final IMEMonitor<T> monitor = this.getProxy().getStorage().getInventory(handler.getStackType());
            handler.insertFromPlayer(player, hand, energy, monitor, new PlayerSource(player, this));
        } catch (GridAccessException e) {
            // :P
        }
    }

    /**
     * Insert a single held item into the network (for item type).
     */
    private void insertSingle(EntityPlayer player, EnumHand hand) {
        final IAEStack<?> displayed = this.getDisplayed();
        if (displayed == null) {
            return;
        }
        doInsertSingle(displayed, player, hand);
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void doInsertSingle(IAEStack<?> displayed, EntityPlayer player, EnumHand hand) {
        try {
            final IAEStackType<T> stackType = (IAEStackType<T>) displayed.getStackTypeBase();
            final IConversionMonitorHandler<T> handler = ConversionMonitorHandlerRegistry.getHandler(stackType);
            if (handler == null) {
                return;
            }
            final IEnergySource energy = this.getProxy().getEnergy();
            final IMEMonitor<T> monitor = this.getProxy().getStorage().getInventory(stackType);
            handler.insertFromPlayer(player, hand, energy, monitor, new PlayerSource(player, this));
        } catch (GridAccessException e) {
            // :P
        }
    }

    /**
     * Insert all matching items from the player's inventory (locked + empty hand).
     */
    private void insertAllMatching(EntityPlayer player) {
        final IAEStack<?> displayed = this.getDisplayed();
        if (displayed == null) {
            return;
        }
        doInsertAll(displayed, player);
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void doInsertAll(IAEStack<?> displayed, EntityPlayer player) {
        try {
            final IAEStackType<T> stackType = (IAEStackType<T>) displayed.getStackTypeBase();
            final IConversionMonitorHandler<T> handler = ConversionMonitorHandlerRegistry.getHandler(stackType);
            if (handler == null) {
                return;
            }
            final IEnergySource energy = this.getProxy().getEnergy();
            final IMEMonitor<T> monitor = this.getProxy().getStorage().getInventory(stackType);
            handler.insertAllFromPlayer(player, (T) displayed, energy, monitor, new PlayerSource(player, this));
        } catch (GridAccessException e) {
            // :P
        }
    }

    /**
     * Extract displayed stack to the player (left-click / shift-click).
     */
    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void extractDisplayed(
            EntityPlayer player, EnumHand hand, IAEStack<?> displayed, long count) {
        try {
            final IAEStackType<T> stackType = (IAEStackType<T>) displayed.getStackTypeBase();
            final IConversionMonitorHandler<T> handler = ConversionMonitorHandlerRegistry.getHandler(stackType);
            if (handler == null) {
                return;
            }
            if (!this.getProxy().isActive()) {
                return;
            }
            final IEnergySource energy = this.getProxy().getEnergy();
            final IMEMonitor<T> monitor = this.getProxy().getStorage().getInventory(stackType);
            handler.extractToPlayer(
                    player, hand, (T) displayed, count, energy, monitor, new PlayerSource(player, this), this);
        } catch (GridAccessException e) {
            // :P
        }
    }

    // ==================== Model ====================

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL,
                MODELS_LOCKED_OFF, MODELS_LOCKED_ON, MODELS_LOCKED_HAS_CHANNEL);
    }

}

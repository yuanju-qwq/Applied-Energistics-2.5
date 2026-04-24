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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IConversionMonitorHandler;
import appeng.api.parts.IConversionMonitorHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AELog;
import appeng.fluids.util.AEFluidStackType;
import appeng.util.StorageHelper;

/**
 * Conversion Monitor handler for fluid stack type.
 * <p>
 * Handles fluid container interactions: draining fluid from held containers into the ME network,
 * and filling held containers from the ME network.
 * Uses {@link IAEStackType#drainFromContainer} and {@link IAEStackType#fillToContainer} APIs.
 */
public final class FluidConversionMonitorHandler implements IConversionMonitorHandler<IAEFluidStack> {

    public static final FluidConversionMonitorHandler INSTANCE = new FluidConversionMonitorHandler();

    private FluidConversionMonitorHandler() {}

    @Nonnull
    @Override
    public IAEStackType<IAEFluidStack> getStackType() {
        return AEFluidStackType.INSTANCE;
    }

    @Override
    public boolean canInteractWithContainer(@Nonnull ItemStack heldItem) {
        return AEFluidStackType.INSTANCE.isContainerItemForType(heldItem)
                && AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem) != null;
    }

    @Nullable
    @Override
    public IAEFluidStack getStackFromContainer(@Nonnull ItemStack heldItem) {
        return AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem);
    }

    // Drain fluid from held container into the ME network
    @Override
    public void insertFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEFluidStack> monitor,
            @Nonnull IActionSource src) {
        final ItemStack held = player.getHeldItem(hand);
        if (held.getCount() != 1) {
            return;
        }

        // Simulate: see how much we can drain
        final ContainerInteractionResult<IAEFluidStack> simDrain =
                AEFluidStackType.INSTANCE.drainFromContainer(held, Integer.MAX_VALUE, true);
        if (!simDrain.isSuccess()) {
            return;
        }

        // Simulate: check if ME network can accept
        final IAEFluidStack notStorable = StorageHelper.poweredInsert(
                energy, monitor, simDrain.getTransferred(), src, Actionable.SIMULATE);

        long toDrain = simDrain.getTransferred().getStackSize();
        if (notStorable != null && notStorable.getStackSize() > 0) {
            toDrain -= notStorable.getStackSize();
            if (toDrain <= 0) {
                return;
            }
        }

        // Actually drain from container
        final ContainerInteractionResult<IAEFluidStack> actualDrain =
                AEFluidStackType.INSTANCE.drainFromContainer(held, toDrain, false);
        if (!actualDrain.isSuccess()) {
            return;
        }

        // Insert into ME network
        final IAEFluidStack notInserted = StorageHelper.poweredInsert(
                energy, monitor, actualDrain.getTransferred(), src);

        if (notInserted != null && notInserted.getStackSize() > 0) {
            AELog.error("Fluid item [%s] reported a different possible amount to drain than it actually provided.",
                    held.getDisplayName());
        }

        player.setHeldItem(hand, actualDrain.getResultContainer());
    }

    // Fluids don't support "insert all from inventory" — containers are handled one at a time
    @Override
    public void insertAllFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull IAEFluidStack displayed,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEFluidStack> monitor,
            @Nonnull IActionSource src) {
        // No-op: fluid containers in the player's inventory cannot be bulk-drained
    }

    // Fill the player's held fluid container from the ME network
    @Override
    public void extractToPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IAEFluidStack displayed,
            long count,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<IAEFluidStack> monitor,
            @Nonnull IActionSource src,
            @Nonnull IConversionMonitorHost host) {
        final ItemStack held = player.getHeldItem(hand);
        if (held.getCount() != 1) {
            return;
        }

        // Simulate: see how much the container can accept
        final IAEFluidStack fillRequest = displayed.copy();
        fillRequest.setStackSize(Integer.MAX_VALUE);
        final ContainerInteractionResult<IAEFluidStack> simFill =
                AEFluidStackType.INSTANCE.fillToContainer(held, fillRequest, true);
        if (!simFill.isSuccess()) {
            return;
        }

        // Simulate: check if ME network has enough
        final IAEFluidStack request = displayed.copy();
        request.setStackSize(simFill.getTransferred().getStackSize());
        final IAEFluidStack canPull = StorageHelper.poweredExtraction(
                energy, monitor, request, src, Actionable.SIMULATE);
        if (canPull == null || canPull.getStackSize() < 1) {
            return;
        }

        // Re-simulate fill with what we can actually pull
        final ContainerInteractionResult<IAEFluidStack> simFill2 =
                AEFluidStackType.INSTANCE.fillToContainer(held, canPull, true);
        if (!simFill2.isSuccess()) {
            return;
        }

        // Actually pull from ME network
        final IAEFluidStack pullRequest = displayed.copy();
        pullRequest.setStackSize(simFill2.getTransferred().getStackSize());
        final IAEFluidStack pulled = StorageHelper.poweredExtraction(energy, monitor, pullRequest, src);
        if (pulled == null || pulled.getStackSize() < 1) {
            AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
            return;
        }

        // Actually fill container
        final ContainerInteractionResult<IAEFluidStack> actualFill =
                AEFluidStackType.INSTANCE.fillToContainer(held, pulled, false);

        if (!actualFill.isSuccess()
                || actualFill.getTransferred().getStackSize() != pulled.getStackSize()) {
            AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.",
                    held.getDisplayName());
        }

        player.setHeldItem(hand, actualFill.getResultContainer());
    }

    @Nullable
    @Override
    public IAEFluidStack resolveConfiguredStack(@Nonnull ItemStack heldItem) {
        final IAEFluidStack stack = AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem);
        return stack != null ? (IAEFluidStack) stack.setStackSize(0) : null;
    }
}

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
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IConversionMonitorHandler;
import appeng.api.parts.IConversionMonitorHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
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
public final class FluidConversionMonitorHandler implements IConversionMonitorHandler {

    public static final FluidConversionMonitorHandler INSTANCE = new FluidConversionMonitorHandler();

    private FluidConversionMonitorHandler() {}

    @Nonnull
    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.fluids();
    }

    @Override
    public boolean canInteractWithContainer(@Nonnull ItemStack heldItem) {
        return AEFluidStackType.INSTANCE.isContainerItemForType(heldItem)
                && AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem) != null;
    }

    @Nullable
    @Override
    public GenericStack getStackFromContainer(@Nonnull ItemStack heldItem) {
        IAEFluidStack stack = AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem);
        return stack != null ? GenericStack.fromIAEStack(stack) : null;
    }

    // Drain fluid from held container into the ME network
    @Override
    public void insertFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor monitor,
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
        final GenericStack notStorable = StorageHelper.poweredInsert(
                energy, monitor, simDrain.getTransferredGenericStack(), src, Actionable.SIMULATE);

        long toDrain = simDrain.getTransferredAmount();
        if (notStorable != null && notStorable.amount() > 0) {
            toDrain -= notStorable.amount();
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
        final GenericStack notInserted = StorageHelper.poweredInsert(
                energy, monitor, actualDrain.getTransferredGenericStack(), src);

        if (notInserted != null && notInserted.amount() > 0) {
            AELog.error("Fluid item [%s] reported a different possible amount to drain than it actually provided.",
                    held.getDisplayName());
        }

        player.setHeldItem(hand, actualDrain.getResultContainer());
    }

    // Fluids don't support "insert all from inventory" - containers are handled one at a time
    @Override
    public void insertAllFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull GenericStack displayed,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor monitor,
            @Nonnull IActionSource src) {
        // No-op: fluid containers in the player's inventory cannot be bulk-drained
    }

    // Fill the player's held fluid container from the ME network
    @Override
    public void extractToPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull GenericStack displayed,
            long count,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor monitor,
            @Nonnull IActionSource src,
            @Nonnull IConversionMonitorHost host) {
        final ItemStack held = player.getHeldItem(hand);
        if (held.getCount() != 1) {
            return;
        }

        final IAEFluidStack displayedFluid = (IAEFluidStack) displayed.toIAEStack();
        if (displayedFluid == null) {
            return;
        }

        // Simulate: see how much the container can accept
        final IAEFluidStack fillRequest = displayedFluid.copy();
        fillRequest.setStackSize(Integer.MAX_VALUE);
        final ContainerInteractionResult<IAEFluidStack> simFill =
                AEFluidStackType.INSTANCE.fillToContainer(held, fillRequest, true);
        if (!simFill.isSuccess()) {
            return;
        }

        // Simulate: check if ME network has enough
        final GenericStack canPull = StorageHelper.poweredExtraction(
                energy, monitor, new GenericStack(displayed.what(), simFill.getTransferredAmount()), src, Actionable.SIMULATE);
        if (canPull == null || canPull.amount() < 1) {
            return;
        }

        // Re-simulate fill with what we can actually pull
        final ContainerInteractionResult<IAEFluidStack> simFill2 =
                AEFluidStackType.INSTANCE.fillToContainer(held, canPull, true);
        if (!simFill2.isSuccess()) {
            return;
        }

        // Actually pull from ME network
        final GenericStack pulled = StorageHelper.poweredExtraction(energy, monitor,
                new GenericStack(displayed.what(), simFill2.getTransferredAmount()), src);
        if (pulled == null || pulled.amount() < 1) {
            AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes ");
            return;
        }

        // Actually fill container
        final ContainerInteractionResult<IAEFluidStack> actualFill =
                AEFluidStackType.INSTANCE.fillToContainer(held, pulled, false);

        if (!actualFill.isSuccess()
                || actualFill.getTransferredAmount() != pulled.amount()) {
            AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.",
                    held.getDisplayName());
        }

        player.setHeldItem(hand, actualFill.getResultContainer());
    }

    @Nullable
    @Override
    public GenericStack resolveConfiguredStack(@Nonnull ItemStack heldItem) {
        final IAEFluidStack stack = AEFluidStackType.INSTANCE.getStackFromContainerItem(heldItem);
        return stack != null ? GenericStack.fromIAEStack((IAEStack<?>) stack.setStackSize(0)) : null;
    }
}

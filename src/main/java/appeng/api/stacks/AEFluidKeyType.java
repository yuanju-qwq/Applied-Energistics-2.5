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

package appeng.api.stacks;

import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.fluids.util.AEFluidStack;
import appeng.util.ReadableNumberConverter;

/**
 * {@link AEKeyType} implementation for fluids.
 */
final class AEFluidKeyType extends AEKeyType {

    static final AEFluidKeyType INSTANCE = new AEFluidKeyType();

    private static final String[] FLUID_NUMBER_FORMATS = { "#.000", "#.00", "#.0", "#" };

    private AEFluidKeyType() {
        super("fluid", AEFluidKey.class, GuiText.Fluids.getLocal());
    }

    @Override
    public TextFormatting getColorDefinition() {
        return TextFormatting.GOLD;
    }

    @Override
    public int getAmountPerOperation() {
        return 1000;
    }

    @Override
    public int getAmountPerByte() {
        return 8000;
    }

    @Override
    public int getAmountPerUnit() {
        return 1000;
    }

    @Nullable
    @Override
    public String getUnitSymbol() {
        return "mB";
    }

    @Nullable
    @Override
    public ResourceLocation getButtonTexture() {
        return new ResourceLocation(AppEng.MOD_ID, "textures/guis/states.png");
    }

    @Override
    public int getButtonIconU() {
        return 128;
    }

    @Override
    public int getButtonIconV() {
        return 48;
    }

    @Override
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        if (container == null || container.isEmpty()) {
            return false;
        }
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(container);
        return handler != null;
    }

    @Nonnull
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ContainerInteractionResult<? extends IAEStack<?>> drainFromContainer(
            @Nonnull ItemStack container, long maxAmount, boolean simulate) {
        if (container.isEmpty()) {
            return ContainerInteractionResult.empty();
        }
        final IFluidHandlerItem fh = FluidUtil.getFluidHandler(container);
        if (fh == null) {
            return ContainerInteractionResult.empty();
        }

        final int drainAmount = (int) Math.min(maxAmount, Integer.MAX_VALUE);
        final FluidStack drained = fh.drain(drainAmount, !simulate);
        if (drained == null || drained.amount <= 0) {
            return ContainerInteractionResult.empty();
        }

        final var result = AEFluidStack.fromFluidStack(drained);
        if (result == null) {
            return ContainerInteractionResult.empty();
        }
        return ContainerInteractionResult.of(result, fh.getContainer());
    }

    @Nonnull
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ContainerInteractionResult<? extends IAEStack<?>> fillToContainer(
            @Nonnull ItemStack container, @Nonnull IAEStack<?> stack, boolean simulate) {
        if (container.isEmpty()) {
            return ContainerInteractionResult.empty();
        }
        final IFluidHandlerItem fh = FluidUtil.getFluidHandler(container);
        if (fh == null) {
            return ContainerInteractionResult.empty();
        }

        if (!(stack instanceof appeng.fluids.util.AEFluidStack)) {
            return ContainerInteractionResult.empty();
        }
        final var fluidStack = (appeng.fluids.util.AEFluidStack) stack;
        final FluidStack toFill = fluidStack.getFluidStack();
        final int filled = fh.fill(toFill, !simulate);
        if (filled <= 0) {
            return ContainerInteractionResult.empty();
        }

        final var result = fluidStack.copy();
        result.setStackSize(filled);
        return ContainerInteractionResult.of(result, fh.getContainer());
    }

    // ========== Amount formatting for fluids ==========

    @Override
    public String formatAmount(long amount, AmountFormat format) {
        switch (format) {
            case FULL:
                return NumberFormat.getNumberInstance(Locale.US).format(amount) + " mB";

            case PREVIEW_LARGE_FONT:
                if (amount < 1000L * 100) {
                    return formatFluidBuckets(amount, 1 + (int) Math.floor(Math.log10(amount)) / 2);
                }
                return ReadableNumberConverter.INSTANCE.toSlimReadableForm(amount / 1000);

            case PREVIEW_REGULAR:
            default:
                if (amount < 1000L * 1000) {
                    return formatFluidBuckets(amount, (int) Math.floor(Math.log10(amount)) / 2);
                }
                return ReadableNumberConverter.INSTANCE.toWideReadableForm(amount / 1000);
        }
    }

    private static String formatFluidBuckets(long amountMB, int logIndex) {
        final int index = Math.max(0, Math.min(3, logIndex));
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        final DecimalFormat df = new DecimalFormat(FLUID_NUMBER_FORMATS[index]);
        df.setDecimalFormatSymbols(symbols);
        df.setRoundingMode(RoundingMode.DOWN);
        return df.format(amountMB / 1000d);
    }

    @Nullable
    @Override
    public AEKey loadKeyFromTag(@Nonnull NBTTagCompound tag) {
        return AEFluidKey.fromTag(tag);
    }

    @Nullable
    @Override
    public AEKey readFromPacket(@Nonnull PacketBuffer input) throws IOException {
        return AEFluidKey.fromPacket(input);
    }
}
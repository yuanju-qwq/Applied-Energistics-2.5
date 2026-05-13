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

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import appeng.fluids.util.AEFluidStackType;
import appeng.util.ReadableNumberConverter;

/**
 * {@link AEKeyType} implementation for fluids.
 * Delegates metadata to {@link AEFluidStackType#INSTANCE}.
 */
final class AEFluidKeyType extends AEKeyType {

    static final AEFluidKeyType INSTANCE = new AEFluidKeyType();

    private AEFluidKeyType() {
        super(AEFluidStackType.INSTANCE, AEFluidKey.class);
    }

    // ========== Amount formatting for fluids ==========

    /**
     * Decimal format patterns for fluid display at different magnitudes.
     * Index 0 = most decimal places (small amounts), index 3 = no decimals (large amounts).
     */
    private static final String[] FLUID_NUMBER_FORMATS = { "#.000", "#.00", "#.0", "#" };

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

    /**
     * Format fluid amount as fractional buckets (e.g. "0.500", "1.23").
     *
     * @param amountMB the amount in mB
     * @param logIndex the precision index (0=most precise, 3=integer only)
     */
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

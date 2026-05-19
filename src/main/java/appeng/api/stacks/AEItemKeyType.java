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
import java.text.NumberFormat;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.core.AppEng;
import appeng.core.localization.GuiText;
import appeng.util.ReadableNumberConverter;

/**
 * {@link AEKeyType} implementation for items.
 */
final class AEItemKeyType extends AEKeyType {

    static final AEItemKeyType INSTANCE = new AEItemKeyType();

    private AEItemKeyType() {
        super("item", AEItemKey.class, GuiText.Items.getLocal());
    }

    @Override
    public TextFormatting getColorDefinition() {
        return TextFormatting.GREEN;
    }

    @Nullable
    @Override
    public ResourceLocation getButtonTexture() {
        return new ResourceLocation(AppEng.MOD_ID, "textures/guis/states.png");
    }

    @Override
    public int getButtonIconU() {
        return 112;
    }

    @Override
    public int getButtonIconV() {
        return 48;
    }

    @Override
    public boolean supportsFuzzyRangeSearch() {
        return true;
    }

    // ========== Amount formatting for items ==========

    @Override
    public String formatAmount(long amount, AmountFormat format) {
        switch (format) {
            case FULL:
                return NumberFormat.getNumberInstance(Locale.US).format(amount);
            case PREVIEW_LARGE_FONT:
                return ReadableNumberConverter.INSTANCE.toSlimReadableForm(amount);
            case PREVIEW_REGULAR:
            default:
                return ReadableNumberConverter.INSTANCE.toWideReadableForm(amount);
        }
    }

    @Nullable
    @Override
    public AEKey loadKeyFromTag(@Nonnull NBTTagCompound tag) {
        return AEItemKey.fromTag(tag);
    }

    @Nullable
    @Override
    public AEKey readFromPacket(@Nonnull PacketBuffer input) throws IOException {
        return AEItemKey.fromPacket(input);
    }
}
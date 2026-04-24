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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import appeng.util.item.AEItemStackType;

/**
 * {@link AEKeyType} implementation for items.
 * Delegates metadata to {@link AEItemStackType#INSTANCE}.
 */
final class AEItemKeyType extends AEKeyType {

    static final AEItemKeyType INSTANCE = new AEItemKeyType();

    private AEItemKeyType() {
        super(AEItemStackType.INSTANCE, AEItemKey.class);
    }

    @Override
    public boolean supportsFuzzyRangeSearch() {
        return true;
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

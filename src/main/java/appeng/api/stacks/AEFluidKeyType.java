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

import appeng.fluids.util.AEFluidStackType;

/**
 * {@link AEKeyType} implementation for fluids.
 * Delegates metadata to {@link AEFluidStackType#INSTANCE}.
 */
final class AEFluidKeyType extends AEKeyType {

    static final AEFluidKeyType INSTANCE = new AEFluidKeyType();

    private AEFluidKeyType() {
        super(AEFluidStackType.INSTANCE, AEFluidKey.class);
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

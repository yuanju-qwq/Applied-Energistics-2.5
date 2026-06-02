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

package appeng.core.api;

import java.util.Collection;

import javax.annotation.Nonnull;

import com.google.common.base.Preconditions;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageHelper;
import appeng.crafting.CraftingLink;

public class ApiStorage implements IStorageHelper {

    public ApiStorage() {
    }

    @Override
    public ICraftingLink loadCraftingLink(final NBTTagCompound data, final ICraftingRequester req) {
        Preconditions.checkNotNull(data);
        Preconditions.checkNotNull(req);

        return new CraftingLink(data, req);
    }

    @Override
    public GenericStack poweredInsert(IEnergySource energy, IMEInventory inv, GenericStack input,
            IActionSource src, Actionable mode) {
        return appeng.util.StorageHelper.poweredInsert(energy, inv, input, src, mode);
    }

    @Override
    public GenericStack poweredExtraction(IEnergySource energy, IMEInventory inv, GenericStack request,
            IActionSource src, Actionable mode) {
        return appeng.util.StorageHelper.poweredExtraction(energy, inv, request, src, mode);
    }

    @Override
    public void postChanges(IStorageGrid gs, ItemStack removedCell, ItemStack addedCell, IActionSource src) {
        Preconditions.checkNotNull(gs);
        Preconditions.checkNotNull(removedCell);
        Preconditions.checkNotNull(addedCell);
        Preconditions.checkNotNull(src);

        appeng.util.StorageHelper.postChanges(gs, removedCell, addedCell, src);
    }

    @Override
    @Nonnull
    public Collection<AEKeyType> getKeyTypes() {
        return AEKeyType.getAllTypes();
    }
}

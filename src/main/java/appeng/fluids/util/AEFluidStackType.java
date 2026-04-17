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

package appeng.fluids.util;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;

/**
 * 流体类型的 {@link IAEStackType} 实现。
 */
public class AEFluidStackType implements IAEStackType<IAEFluidStack> {

    public static final AEFluidStackType INSTANCE = new AEFluidStackType();
    public static final String ID = "fluid";

    private AEFluidStackType() {}

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return GuiText.Fluids.getLocal();
    }

    @Override
    public String getDisplayUnit() {
        return "mB";
    }

    @Nullable
    @Override
    public IAEFluidStack loadStackFromNBT(@Nonnull NBTTagCompound tag) {
        return AEFluidStack.fromNBT(tag);
    }

    @Nullable
    @Override
    public IAEFluidStack loadStackFromPacket(@Nonnull ByteBuf buffer) throws IOException {
        return AEFluidStack.fromPacket(buffer);
    }

    @Nonnull
    @Override
    public IItemList<IAEFluidStack> createList() {
        return new FluidList();
    }

    @Override
    public int transferFactor() {
        return 1000;
    }

    @Override
    public int getUnitsPerByte() {
        return 8000;
    }

    @Nullable
    @Override
    public IAEFluidStack createStack(@Nonnull Object input) {
        if (input instanceof FluidStack) {
            return AEFluidStack.fromFluidStack((FluidStack) input);
        }
        if (input instanceof ItemStack) {
            final ItemStack is = (ItemStack) input;
            if (is.getItem() instanceof appeng.fluids.items.FluidDummyItem) {
                return AEFluidStack.fromFluidStack(
                        ((appeng.fluids.items.FluidDummyItem) is.getItem()).getFluidStack(is));
            } else {
                return AEFluidStack.fromFluidStack(FluidUtil.getFluidContained(is));
            }
        }
        return null;
    }

    @Override
    public int getAmountPerUnit() {
        return 1000;
    }

    @Override
    public TextFormatting getColorDefinition() {
        return TextFormatting.GOLD;
    }

    @Override
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        if (container == null || container.isEmpty()) {
            return false;
        }
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(container);
        return handler != null;
    }

    @Nullable
    @Override
    public IAEFluidStack getStackFromContainerItem(@Nonnull ItemStack container) {
        if (container.isEmpty()) {
            return null;
        }
        IFluidHandlerItem handler = FluidUtil.getFluidHandler(container);
        if (handler == null) {
            return null;
        }
        FluidStack fluid = handler.drain(Integer.MAX_VALUE, false);
        if (fluid == null || fluid.amount <= 0) {
            return null;
        }
        return AEFluidStack.fromFluidStack(fluid);
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

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public IStorageChannel<IAEFluidStack> getStorageChannel() {
        for (IStorageChannel<?> ch : AEApi.instance().storage().storageChannels()) {
            if (ch.getStackType() == this) {
                return (IStorageChannel<IAEFluidStack>) ch;
            }
        }
        throw new IllegalStateException("No IStorageChannel registered for AEFluidStackType");
    }
}

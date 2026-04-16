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

package appeng.util.item;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AppEng;
import appeng.core.localization.GuiText;

/**
 * 物品类型的 {@link IAEStackType} 实现。
 */
public class AEItemStackType implements IAEStackType<IAEItemStack> {

    public static final AEItemStackType INSTANCE = new AEItemStackType();
    public static final String ID = "item";

    private AEItemStackType() {}

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return GuiText.Items.getLocal();
    }

    @Override
    public String getDisplayUnit() {
        return "";
    }

    @Nullable
    @Override
    public IAEItemStack loadStackFromNBT(@Nonnull NBTTagCompound tag) {
        return AEItemStack.fromNBT(tag);
    }

    @Nullable
    @Override
    public IAEItemStack loadStackFromPacket(@Nonnull ByteBuf buffer) throws IOException {
        return AEItemStack.fromPacket(buffer);
    }

    @Nonnull
    @Override
    public IItemList<IAEItemStack> createList() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
    }

    @Override
    public int getAmountPerUnit() {
        return 1;
    }

    @Override
    public TextFormatting getColorDefinition() {
        return TextFormatting.GREEN;
    }

    @Override
    public boolean isContainerItemForType(@Nullable ItemStack container) {
        // 物品类型本身没有"容器"概念
        return false;
    }

    @Nullable
    @Override
    public IAEItemStack getStackFromContainerItem(@Nonnull ItemStack container) {
        return null;
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

    @Nonnull
    @Override
    public IStorageChannel<IAEItemStack> getStorageChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }
}

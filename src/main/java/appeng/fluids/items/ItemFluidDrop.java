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

package appeng.fluids.items;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;

/**
 * 流体伪物品（Fluid Drop）。
 * <p>
 * 将流体表示为 ItemStack，使合成系统可以无缝处理流体。
 * ItemStack 的 count 对应流体的 mB 数量。
 *
 * @deprecated 请使用 {@link FluidDummyItem} 和 {@link appeng.api.storage.data.IAEFluidStack} 替代。
 *             此类仅为旧存档兼容保留物品注册。新代码不应使用此类。
 *             仅在 {@link appeng.util.Platform#convertLegacyStack} 和
 *             {@link appeng.util.Platform#stackConvert} 中作为旧格式迁移桥接被引用。
 */
@Deprecated
public class ItemFluidDrop extends Item {

    public static ItemFluidDrop INSTANCE;

    public ItemFluidDrop() {
        this.setMaxStackSize(Integer.MAX_VALUE);
        INSTANCE = this;
    }

    // ============================================================
    // Item 覆写
    // ============================================================

    @Override
    public void getSubItems(@Nonnull CreativeTabs tab, @Nonnull NonNullList<ItemStack> items) {
        // 不出现在创造模式标签页中
    }

    @SuppressWarnings("deprecation")
    @Override
    @Nonnull
    public String getItemStackDisplayName(@Nonnull ItemStack stack) {
        FluidStack fluid = getFluidStack(stack);
        return I18n.translateToLocalFormatted(getTranslationKey(stack) + ".name",
                fluid != null ? fluid.getLocalizedName() : "???");
    }

    @SuppressWarnings("deprecation")
    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
            @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flags) {
        FluidStack fluid = getFluidStack(stack);
        if (fluid != null) {
            tooltip.add(String.format(TextFormatting.GRAY + "%s, %,d mB",
                    fluid.getLocalizedName(), (long) stack.getCount()));
        } else {
            tooltip.add(TextFormatting.RED + "Invalid Fluid");
        }
    }

    // ============================================================
    // 静态工具方法：FluidStack <-> ItemStack 转换
    // ============================================================

    /**
     * 判断给定 ItemStack 是否为流体伪物品。
     */
    public static boolean isFluidDrop(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemFluidDrop;
    }

    /**
     * 从 ItemStack 中提取 FluidStack。
     *
     * @return FluidStack，如果不是流体伪物品则返回 null
     */
    @Nullable
    public static FluidStack getFluidStack(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemFluidDrop) || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("Fluid", Constants.NBT.TAG_STRING)) {
            return null;
        }
        Fluid fluid = FluidRegistry.getFluid(tag.getString("Fluid"));
        if (fluid == null) {
            return null;
        }
        FluidStack fluidStack = new FluidStack(fluid, stack.getCount());
        if (tag.hasKey("FluidTag", Constants.NBT.TAG_COMPOUND)) {
            fluidStack.tag = tag.getCompoundTag("FluidTag");
        }
        return fluidStack;
    }

    /**
     * 将 FluidStack 打包为 ItemStack（流体伪物品）。
     * count = mB 数量。
     */
    @Nonnull
    public static ItemStack newStack(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0 || INSTANCE == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(INSTANCE, fluid.amount);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Fluid", fluid.getFluid().getName());
        if (fluid.tag != null) {
            tag.setTag("FluidTag", fluid.tag);
        }
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 将 IAEFluidStack 转换为 IAEItemStack（流体伪物品的 AE 栈表示）。
     * stackSize = mB 数量。
     */
    @Nullable
    public static IAEItemStack newAEStack(@Nullable IAEFluidStack aeFluid) {
        if (aeFluid == null || aeFluid.getStackSize() <= 0) {
            return null;
        }
        ItemStack is = newStack(aeFluid.getFluidStack());
        if (is.isEmpty()) {
            return null;
        }
        IAEItemStack result = AEItemStack.fromItemStack(is);
        if (result != null) {
            result.setStackSize(aeFluid.getStackSize());
        }
        return result;
    }

    /**
     * 将 IAEItemStack（流体伪物品）转换回 IAEFluidStack。
     *
     * @return IAEFluidStack，如果不是流体伪物品则返回 null
     */
    @Nullable
    public static IAEFluidStack getAeFluidStack(@Nullable IAEItemStack aeItem) {
        if (aeItem == null) {
            return null;
        }
        FluidStack fs = getFluidStack(aeItem.createItemStack());
        if (fs == null) {
            return null;
        }
        IAEFluidStack result = AEFluidStack.fromFluidStack(fs);
        if (result != null) {
            result.setStackSize(aeItem.getStackSize());
        }
        return result;
    }
}

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

package appeng.helpers;

import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;
import static appeng.helpers.PatternHelper.PROCESSING_OUTPUT_LIMIT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 支持流体输入/输出的处理配方（非合成台配方）。
 * <p>
 * 与普通 {@link PatternHelper} 不同，此类的输入和输出可以包含 {@link appeng.api.storage.data.IAEFluidStack}。
 * 当 NBT 中含有 "fluidPattern" 标记时使用此类。
 * <p>
 * 物品栈存储在 "in"/"out" 标签中，流体栈存储在 "fluidIn"/"fluidOut" 标签中。
 * 也支持统一在 "in"/"out" 中使用泛型序列化格式（带 "aeTypeId" 字段）。
 */
public class FluidPatternHelper implements ICraftingPatternDetails, Comparable<FluidPatternHelper> {

    private final ItemStack patternItem;
    private final IAEItemStack pattern;
    private int priority = 0;

    // 物品类型的输入/输出（向后兼容旧接口）
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;

    // 泛型输入/输出（包含物品+流体）
    private final IAEStack<?>[] genericInputs;
    private final IAEStack<?>[] genericOutputs;
    private final IAEStack<?>[] genericCondensedInputs;
    private final IAEStack<?>[] genericCondensedOutputs;

    public FluidPatternHelper(final ItemStack is, final World w) {
        final NBTTagCompound encodedValue = is.getTagCompound();

        if (encodedValue == null) {
            throw new IllegalArgumentException("No pattern here!");
        }

        this.patternItem = is;
        this.pattern = AEItemStack.fromItemStack(is);

        final List<IAEItemStack> inItems = new ArrayList<>();
        final List<IAEItemStack> outItems = new ArrayList<>();
        final List<IAEStack<?>> inGeneric = new ArrayList<>();
        final List<IAEStack<?>> outGeneric = new ArrayList<>();

        // 读取输入
        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            if (tag.isEmpty()) {
                continue;
            }

            // 尝试泛型反序列化（带 aeTypeId）
            IAEStack<?> generic = IAEStack.fromNBTGeneric(tag);
            if (generic != null) {
                inGeneric.add(generic);
                if (generic instanceof IAEItemStack itemStack) {
                    inItems.add(itemStack);
                } else if (generic instanceof IAEFluidStack fluidStack) {
                    // 流体 → ItemFluidDrop 伪物品（供合成树使用）
                    IAEItemStack drop = ItemFluidDrop.newAEStack(fluidStack);
                    if (drop != null) {
                        inItems.add(drop);
                    }
                }
            } else {
                // 回退：当作普通物品
                final ItemStack gs = ItemStackHelper.stackFromNBT(tag);
                if (!gs.isEmpty()) {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.getStorageChannel().createStack(gs);
                    if (aeItem != null) {
                        inGeneric.add(aeItem);
                        inItems.add(aeItem);
                    }
                }
            }
        }

        // 读取流体输入（旧格式兼容：fluidIn 标签）
        if (encodedValue.hasKey("fluidIn")) {
            final NBTTagList fluidInTag = encodedValue.getTagList("fluidIn", 10);
            for (int x = 0; x < fluidInTag.tagCount(); x++) {
                final NBTTagCompound tag = fluidInTag.getCompoundTagAt(x);
                if (tag.isEmpty()) {
                    continue;
                }
                IAEFluidStack fluid = AEFluidStack.fromNBT(tag);
                if (fluid != null) {
                    inGeneric.add(fluid);
                    IAEItemStack drop = ItemFluidDrop.newAEStack(fluid);
                    if (drop != null) {
                        inItems.add(drop);
                    }
                }
            }
        }

        // 读取输出
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        for (int x = 0; x < outTag.tagCount(); x++) {
            final NBTTagCompound tag = outTag.getCompoundTagAt(x);
            if (tag.isEmpty()) {
                continue;
            }

            IAEStack<?> generic = IAEStack.fromNBTGeneric(tag);
            if (generic != null) {
                outGeneric.add(generic);
                if (generic instanceof IAEItemStack itemStack) {
                    outItems.add(itemStack);
                } else if (generic instanceof IAEFluidStack fluidStack) {
                    IAEItemStack drop = ItemFluidDrop.newAEStack(fluidStack);
                    if (drop != null) {
                        outItems.add(drop);
                    }
                }
            } else {
                final ItemStack gs = ItemStackHelper.stackFromNBT(tag);
                if (!gs.isEmpty()) {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.getStorageChannel().createStack(gs);
                    if (aeItem != null) {
                        outGeneric.add(aeItem);
                        outItems.add(aeItem);
                    }
                }
            }
        }

        // 读取流体输出（旧格式兼容：fluidOut 标签）
        if (encodedValue.hasKey("fluidOut")) {
            final NBTTagList fluidOutTag = encodedValue.getTagList("fluidOut", 10);
            for (int x = 0; x < fluidOutTag.tagCount(); x++) {
                final NBTTagCompound tag = fluidOutTag.getCompoundTagAt(x);
                if (tag.isEmpty()) {
                    continue;
                }
                IAEFluidStack fluid = AEFluidStack.fromNBT(tag);
                if (fluid != null) {
                    outGeneric.add(fluid);
                    IAEItemStack drop = ItemFluidDrop.newAEStack(fluid);
                    if (drop != null) {
                        outItems.add(drop);
                    }
                }
            }
        }

        this.inputs = inItems.toArray(new IAEItemStack[PROCESSING_INPUT_LIMIT]);
        this.outputs = outItems.toArray(new IAEItemStack[PROCESSING_OUTPUT_LIMIT]);
        this.condensedInputs = condenseItemList(this.inputs);
        this.condensedOutputs = condenseItemList(this.outputs);

        this.genericInputs = inGeneric.toArray(new IAEStack<?>[PROCESSING_INPUT_LIMIT]);
        this.genericOutputs = outGeneric.toArray(new IAEStack<?>[PROCESSING_OUTPUT_LIMIT]);
        this.genericCondensedInputs = condenseGenericList(this.genericInputs);
        this.genericCondensedOutputs = condenseGenericList(this.genericOutputs);

        if (this.genericCondensedInputs.length == 0 || this.genericCondensedOutputs.length == 0) {
            throw new IllegalStateException("No pattern here!");
        }
    }

    // ============================================================
    // ICraftingPatternDetails 实现
    // ============================================================

    @Override
    public ItemStack getPattern() {
        return this.patternItem;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.condensedInputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.condensedOutputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return this.genericCondensedInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return this.genericCondensedOutputs;
    }

    @Override
    public IAEStack<?>[] getAEInputs() {
        return this.genericInputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return this.genericOutputs;
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList();
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack i, World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World w) {
        throw new IllegalStateException("Not a crafting recipe!");
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ============================================================
    // Comparable / equals / hashCode
    // ============================================================

    @Override
    public int compareTo(FluidPatternHelper o) {
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        final FluidPatternHelper other = (FluidPatternHelper) obj;
        if (this.pattern != null && other.pattern != null) {
            return this.pattern.equals(other.pattern);
        }
        return false;
    }

    // ============================================================
    // 工具方法
    // ============================================================

    @SuppressWarnings("unchecked")
    private static IAEItemStack[] condenseItemList(IAEItemStack[] items) {
        final LinkedHashMap<IAEItemStack, IAEItemStack> tmp = new LinkedHashMap<>();
        for (IAEItemStack io : items) {
            if (io == null) {
                continue;
            }
            IAEItemStack g = tmp.get(io);
            if (g == null) {
                tmp.put(io, io.copy());
            } else {
                g.add(io);
            }
        }
        return tmp.values().toArray(new IAEItemStack[0]);
    }

    @SuppressWarnings("unchecked")
    private static IAEStack<?>[] condenseGenericList(IAEStack<?>[] items) {
        final LinkedHashMap<IAEStack<?>, IAEStack<?>> tmp = new LinkedHashMap<>();
        for (IAEStack<?> io : items) {
            if (io == null) {
                continue;
            }
            IAEStack g = tmp.get(io);
            if (g == null) {
                tmp.put(io, io.copy());
            } else {
                g.add(io);
            }
        }
        return tmp.values().toArray(new IAEStack<?>[0]);
    }

    /**
     * 检查给定的 Pattern ItemStack 是否为流体合成配方。
     * <p>
     * 条件：NBT 中包含 "fluidPattern" 标记为 true，
     * 或者 "in"/"out" 中包含带 "aeTypeId" 的泛型条目。
     */
    public static boolean isFluidPattern(final ItemStack is) {
        if (is.isEmpty() || !is.hasTagCompound()) {
            return false;
        }
        final NBTTagCompound nbt = is.getTagCompound();

        // 显式标记
        if (nbt.getBoolean("fluidPattern")) {
            return true;
        }

        // 检查 "fluidIn" 或 "fluidOut" 标签是否存在
        if (nbt.hasKey("fluidIn") || nbt.hasKey("fluidOut")) {
            return true;
        }

        // 检查 "in" 或 "out" 中是否包含泛型格式条目
        return hasGenericEntries(nbt.getTagList("in", 10))
                || hasGenericEntries(nbt.getTagList("out", 10));
    }

    private static boolean hasGenericEntries(NBTTagList tagList) {
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound tag = tagList.getCompoundTagAt(i);
            if (tag.hasKey("aeTypeId")) {
                return true;
            }
        }
        return false;
    }
}

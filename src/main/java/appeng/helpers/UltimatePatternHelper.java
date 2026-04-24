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

import static appeng.helpers.PatternHelper.convertToCondensedAEList;
import static appeng.helpers.PatternHelper.convertToCondensedList;
import static appeng.util.Platform.readStackNBT;
import static appeng.util.Platform.stackConvert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;

/**
 * 统一的加工配方（非合成台配方）解析器，原生支持泛型栈（物品 + 流体等）。
 * <p>
 * 取代 {@link FluidPatternHelper} 和 {@link SpecialPatternHelper}，
 * 通过 {@link appeng.util.Platform#readStackNBT} 读取 NBT 中的栈数据，
 * Automatically handles both new format (with "StackType" key) and legacy format (plain ItemStack / FluidDummyItem) migration.
 * <p>
 * 设计参考自 Applied-Energistics-2-Unofficial 的同名类。
 */
public class UltimatePatternHelper implements ICraftingPatternDetails, Comparable<UltimatePatternHelper> {

    private final ItemStack patternItem;
    private final IAEItemStack pattern;
    private final boolean canSubstitute;
    private final boolean canBeSubstitute;
    private int priority = 0;

    // 旧版物品类型数组（向后兼容旧接口）
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;

    // 泛型栈数组（主入口，支持物品 + 流体等所有类型）
    private final IAEStack<?>[] aeInputs;
    private final IAEStack<?>[] aeOutputs;
    private final IAEStack<?>[] condensedAEInputs;
    private final IAEStack<?>[] condensedAEOutputs;

    // 仅输入型配方支持（inputOnly / tunnel pattern）
    private final boolean inputOnly;
    private final UUID inputOnlyUuid;

    /**
     * 从编码了加工配方的 {@link ItemStack} 构造。
     *
     * @param is 编码了加工配方的模式物品（含 NBT）
     * @throws IllegalArgumentException 如果 NBT 缺失或已标记为无效
     * @throws IllegalStateException    如果无有效输入或无有效输出（非 inputOnly）
     */
    public UltimatePatternHelper(final ItemStack is) {
        final NBTTagCompound encodedValue = is.getTagCompound();

        if (encodedValue == null || encodedValue.getBoolean("InvalidPattern")) {
            throw new IllegalArgumentException("No pattern here!");
        }

        this.canSubstitute = encodedValue.getBoolean("substitute");
        this.canBeSubstitute = encodedValue.getBoolean("beSubstitute");
        this.patternItem = is;
        this.inputOnly = encodedValue.getBoolean("tunnel");
        this.inputOnlyUuid = readInputOnlyUuid(encodedValue, this.inputOnly);

        // 模式物品用于 equals/hashCode 比较时排除 "author" 标签
        if (encodedValue.hasKey("author")) {
            final ItemStack forComparison = this.patternItem.copy();
            forComparison.getTagCompound().removeTag("author");
            this.pattern = AEItemStack.fromItemStack(forComparison);
        } else {
            this.pattern = AEItemStack.fromItemStack(is);
        }

        final NBTTagList inTag = encodedValue.getTagList("in", NBT.TAG_COMPOUND);
        final NBTTagList outTag = encodedValue.getTagList("out", NBT.TAG_COMPOUND);

        // 旧版物品列表（兼容旧 API 的 getInputs/getOutputs）
        final List<IAEItemStack> inLegacy = new ArrayList<>();
        final List<IAEItemStack> outLegacy = new ArrayList<>();

        // 泛型列表（主入口）
        final List<IAEStack<?>> in = new ArrayList<>();
        final List<IAEStack<?>> out = new ArrayList<>();

        // ========== 解析输入 ==========
        for (int x = 0; x < inTag.tagCount(); x++) {
            final NBTTagCompound tag = inTag.getCompoundTagAt(x);
            // readStackNBT(tag, true): enable legacy FluidDummyItem auto-conversion
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.isEmpty()) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("No pattern here!");
            }

            // Legacy item list: convert fluids to FluidDummyItem items via stackConvert
            inLegacy.add(stackConvert(aeStack));
            in.add(aeStack);
        }

        // ========== 解析输出 ==========
        for (int x = 0; x < outTag.tagCount(); x++) {
            final NBTTagCompound tag = outTag.getCompoundTagAt(x);
            final IAEStack<?> aeStack = readStackNBT(tag, true);

            if (aeStack == null && !tag.isEmpty()) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("No pattern here!");
            }

            outLegacy.add(stackConvert(aeStack));
            out.add(aeStack);
        }

        // ========== 构建数组 ==========
        this.inputs = inLegacy.toArray(new IAEItemStack[0]);
        this.outputs = outLegacy.toArray(new IAEItemStack[0]);
        this.condensedInputs = convertToCondensedList(this.inputs);
        this.condensedOutputs = convertToCondensedList(this.outputs);

        this.aeInputs = in.toArray(new IAEStack<?>[0]);
        this.aeOutputs = out.toArray(new IAEStack<?>[0]);
        this.condensedAEInputs = convertToCondensedAEList(this.aeInputs);
        this.condensedAEOutputs = convertToCondensedAEList(this.aeOutputs);

        // ========== 有效性验证 ==========
        if (this.condensedAEInputs.length == 0) {
            encodedValue.setBoolean("InvalidPattern", true);
            throw new IllegalStateException("No pattern here!");
        }

        if (this.inputOnly) {
            if (this.condensedAEOutputs.length != 0) {
                encodedValue.setBoolean("InvalidPattern", true);
                throw new IllegalStateException("Input-only pattern has outputs");
            }
        } else if (this.condensedAEOutputs.length == 0) {
            encodedValue.setBoolean("InvalidPattern", true);
            throw new IllegalStateException("No pattern here!");
        }
    }

    // ========== ICraftingPatternDetails 实现 ==========

    @Override
    public ItemStack getPattern() {
        return this.patternItem;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    // --- 泛型主入口方法 ---

    @Override
    public IAEStack<?>[] getAEInputs() {
        return this.aeInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return this.condensedAEInputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return this.aeOutputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return this.condensedAEOutputs;
    }

    // --- 旧版物品类型方法（覆写 default 实现以避免重复过滤） ---

    @Override
    public IAEItemStack[] getInputs() {
        return this.inputs;
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return this.condensedInputs;
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return this.outputs;
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return this.condensedOutputs;
    }

    // --- 其他接口方法 ---

    @Override
    public boolean canSubstitute() {
        return this.canSubstitute;
    }

    @Override
    public boolean canBeSubstitute() {
        return this.canBeSubstitute;
    }

    @Override
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getOutput(final InventoryCrafting craftingInv, final World w) {
        throw new IllegalStateException("Not a crafting recipe!");
    }

    @Override
    public boolean isValidItemForSlot(final int slotIndex, final ItemStack i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public boolean isValidItemForSlot(final int slotIndex, final IAEStack<?> i, final World w) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    @Override
    public void setPriority(final int priority) {
        this.priority = priority;
    }

    @Override
    public boolean isInputOnly() {
        return this.inputOnly;
    }

    @Override
    public UUID getInputOnlyUuid() {
        return this.inputOnlyUuid;
    }

    // ========== Comparable / equals / hashCode ==========

    @Override
    public int compareTo(final UltimatePatternHelper o) {
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return this.pattern.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        final UltimatePatternHelper other = (UltimatePatternHelper) obj;
        if (this.pattern != null && other.pattern != null) {
            return this.pattern.equals(other.pattern);
        }
        return false;
    }

    // ========== 静态工具方法 ==========

    /**
     * 从 NBT 标签列表中加载泛型栈数组。
     *
     * @param tags        NBTTagList（每个条目为 NBTTagCompound）
     * @param saveOrder   是否保留 null 条目以维持槽位顺序
     * @param unknownItem 当条目无法解析时使用的回退物品（可为 null）
     * @return 泛型栈数组
     */
    public static IAEStack<?>[] loadIAEStackFromNBT(final NBTTagList tags, boolean saveOrder,
            final ItemStack unknownItem) {
        final List<IAEStack<?>> items = new ArrayList<>();
        for (int x = 0; x < tags.tagCount(); x++) {
            final NBTTagCompound tag = tags.getCompoundTagAt(x);
            if (tag.isEmpty()) {
                if (saveOrder) {
                    items.add(null);
                }
                continue;
            }

            IAEStack<?> gs = readStackNBT(tag, true);
            if (gs == null && unknownItem != null && !unknownItem.isEmpty()) {
                gs = AEItemStack.fromItemStack(unknownItem);
            }
            if (gs != null || saveOrder) {
                items.add(gs);
            }
        }
        return items.toArray(new IAEStack<?>[0]);
    }

    /**
     * 解析 inputOnly 类型配方的 UUID。
     */
    private static UUID readInputOnlyUuid(final NBTTagCompound encodedValue, boolean inputOnly) {
        if (!inputOnly) {
            return null;
        }
        final String rawUuid = encodedValue.getString("tunnelUuid");
        if (rawUuid == null || rawUuid.isEmpty()) {
            throw new IllegalStateException("No pattern here!");
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("No pattern here!");
        }
    }
}

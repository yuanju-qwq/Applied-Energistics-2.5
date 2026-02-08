package appeng.helpers;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import java.util.*;

import static appeng.helpers.ItemStackHelper.stackFromNBT;

/**
 * 支持空输出的特殊加工模板解析器
 * 仅适用于加工模式（isCrafting=false），合成模式必须有输出
 */
public class SpecialPatternHelper implements ICraftingPatternDetails, Comparable<SpecialPatternHelper> {

    // 常量定义（与原PatternHelper一致）
    public static final int PROCESSING_INPUT_HEIGHT = 4;
    public static final int PROCESSING_INPUT_WIDTH = 4;
    public static final int PROCESSING_INPUT_LIMIT = PROCESSING_INPUT_HEIGHT * PROCESSING_INPUT_WIDTH;
    public static final int PROCESSING_OUTPUT_LIMIT = 6;

    // 核心字段
    private final ItemStack patternItem;
    private final boolean isCrafting;
    private final boolean canSubstitute;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;
    private final Map<Integer, List<IAEItemStack>> substituteInputs = new HashMap<>();
    private final IAEItemStack pattern;
    private int priority = 0;

    /**
     * 构造函数：关键修改点 - 允许加工模式输出为空
     */
    public SpecialPatternHelper(final ItemStack is, final World w) {
        final NBTTagCompound encodedValue = is.getTagCompound();
        if (encodedValue == null) {
            throw new IllegalArgumentException("Invalid special pattern: missing NBT");
        }

        // 仅支持加工模式（合成模式必须有输出）
        this.isCrafting = encodedValue.getBoolean("crafting");
        if (this.isCrafting) {
            throw new IllegalArgumentException("Special patterns cannot be used for crafting recipes");
        }

        this.canSubstitute = false; // 加工模式不支持替代
        this.patternItem = is;
        this.pattern = AEItemStack.fromItemStack(is);

        // 解析输入
        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        final List<IAEItemStack> in = new ArrayList<>();
        for (int x = 0; x < inTag.tagCount() && x < PROCESSING_INPUT_LIMIT; x++) {
            final NBTTagCompound ingredient = inTag.getCompoundTagAt(x);
            final ItemStack gs = stackFromNBT(ingredient);

            if (!ingredient.isEmpty() && gs.isEmpty()) {
                throw new IllegalArgumentException("Invalid input at slot " + x);
            }
            in.add(gs.isEmpty() ? null : AEApi.instance().storage()
                    .getStorageChannel(IItemStorageChannel.class).createStack(gs));
        }

        // 解析输出 - 关键修改：允许空输出列表
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        final List<IAEItemStack> out = new ArrayList<>();
        for (int x = 0; x < outTag.tagCount() && x < PROCESSING_OUTPUT_LIMIT; x++) {
            final NBTTagCompound resultItemTag = outTag.getCompoundTagAt(x);
            final ItemStack gs = stackFromNBT(resultItemTag);

            if (!resultItemTag.isEmpty() && gs.isEmpty()) {
                throw new IllegalArgumentException("Invalid output at slot " + x);
            }
            if (!gs.isEmpty()) {
                out.add(AEApi.instance().storage()
                        .getStorageChannel(IItemStorageChannel.class).createStack(gs));
            }
        }

        // 输入不能为空（无输入的模板无意义）
        if (in.isEmpty() || in.stream().allMatch(Objects::isNull)) {
            throw new IllegalStateException("Special pattern requires at least one input");
        }

        // 输出可为空 - 关键修改点
        // 原逻辑: if (tmpOutputs.isEmpty() || tmpInputs.isEmpty()) throw ...
        // 新逻辑: 仅检查输入非空，输出允许为空

        // 初始化输入数组（固定大小）
        this.inputs = new IAEItemStack[PROCESSING_INPUT_LIMIT];
        for (int i = 0; i < Math.min(in.size(), PROCESSING_INPUT_LIMIT); i++) {
            this.inputs[i] = in.get(i);
        }

        // 初始化输出数组（动态大小）
        this.outputs = out.toArray(new IAEItemStack[0]);

        // 压缩输入（合并相同物品）
        final Map<IAEItemStack, IAEItemStack> tmpInputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.inputs) {
            if (io == null) continue;
            tmpInputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedInputs = tmpInputs.values().toArray(new IAEItemStack[0]);

        // 压缩输出（允许空）
        final Map<IAEItemStack, IAEItemStack> tmpOutputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.outputs) {
            if (io == null) continue;
            tmpOutputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedOutputs = tmpOutputs.values().toArray(new IAEItemStack[0]);
    }

    // ===== 接口实现 =====

    @Override
    public boolean isCraftable() {
        return false; // 特殊模板仅用于加工
    }

    @Override
    public IAEItemStack[] getInputs() {
        return inputs.clone(); // 防御性复制
    }

    @Override
    public IAEItemStack[] getCondensedInputs() {
        return condensedInputs.clone();
    }

    @Override
    public IAEItemStack[] getOutputs() {
        return outputs.clone();
    }

    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return condensedOutputs.clone();
    }

    @Override
    public boolean canSubstitute() {
        return false; // 加工模式不支持替代
    }

    @Override
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList(); // 无替代物品
    }

    @Override
    public ItemStack getPattern() {
        return patternItem;
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack i, World w) {
        // 加工模式不进行槽位验证（由设备处理）
        return slotIndex >= 0 && slotIndex < inputs.length;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World w) {
        // 空输出语义：返回 EMPTY 表示无物品产出
        return ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ===== 工具方法 =====

    /**
     * 检查是否为空输出模板（合法空输出）
     */
    public boolean hasEmptyOutput() {
        return outputs.length == 0 || Arrays.stream(outputs).allMatch(Objects::isNull);
    }

    // ===== 比较与哈希 =====

    @Override
    public int compareTo(SpecialPatternHelper o) {
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return pattern.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SpecialPatternHelper other)) return false;
        return Objects.equals(pattern, other.pattern);
    }
}
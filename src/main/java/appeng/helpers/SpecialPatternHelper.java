package appeng.helpers;

import static appeng.helpers.ItemStackHelper.stackFromNBT;

import java.util.*;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * 支持空输出的特殊加工模板解析器。仅适用于加工模式（isCrafting=false），合成模式必须有输出。
 * <p>
 * 支持多类型栈（物品+流体等），通过泛型 {@code aeTypeId} NBT 字段识别栈类型。
 * 旧格式（纯物品 NBT）亦可向后兼容。
 *
 * @deprecated 已被 {@link UltimatePatternHelper} 替代。此类仅为旧代码兼容保留。
 */
@Deprecated
public class SpecialPatternHelper implements ICraftingPatternDetails, Comparable<SpecialPatternHelper> {

    // 常量定义（与原PatternHelper一致）
    public static final int PROCESSING_INPUT_HEIGHT = 4;
    public static final int PROCESSING_INPUT_WIDTH = 4;
    public static final int PROCESSING_INPUT_LIMIT = PatternHelper.PROCESSING_INPUT_LIMIT;
    public static final int PROCESSING_OUTPUT_LIMIT = 6;

    // 核心字段
    private final ItemStack patternItem;
    private final boolean isCrafting;
    private final boolean canSubstitute;

    // 物品类型的输入/输出（向后兼容旧接口）
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;

    // 泛型输入/输出（包含物品+流体等所有类型）
    private final IAEStack<?>[] genericInputs;
    private final IAEStack<?>[] genericOutputs;
    private final IAEStack<?>[] genericCondensedInputs;
    private final IAEStack<?>[] genericCondensedOutputs;

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

        final List<IAEItemStack> inItems = new ArrayList<>();
        final List<IAEItemStack> outItems = new ArrayList<>();
        final List<IAEStack<?>> inGeneric = new ArrayList<>();
        final List<IAEStack<?>> outGeneric = new ArrayList<>();

        // ========== 解析输入 ==========
        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        for (int x = 0; x < inTag.tagCount() && x < PROCESSING_INPUT_LIMIT; x++) {
            final NBTTagCompound ingredient = inTag.getCompoundTagAt(x);
            if (ingredient.isEmpty()) {
                inItems.add(null);
                inGeneric.add(null);
                continue;
            }

            // Try generic deserialization (with StackType key)
            IAEStack<?> generic = ingredient.hasKey("StackType") ? IAEStack.fromNBTGeneric(ingredient) : null;
            if (generic != null) {
                inGeneric.add(generic);
                inItems.add(Platform.stackConvert(generic));
            } else {
                // 回退：当作普通物品
                final ItemStack gs = stackFromNBT(ingredient);
                if (!ingredient.isEmpty() && gs.isEmpty()) {
                    throw new IllegalArgumentException("Invalid input at slot " + x);
                }
                if (gs.isEmpty()) {
                    inItems.add(null);
                    inGeneric.add(null);
                } else {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.createStack(gs);
                    inItems.add(aeItem);
                    inGeneric.add(aeItem);
                }
            }
        }

        // ========== 解析输出 - 关键修改：允许空输出列表 ==========
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        for (int x = 0; x < outTag.tagCount() && x < PROCESSING_OUTPUT_LIMIT; x++) {
            final NBTTagCompound resultItemTag = outTag.getCompoundTagAt(x);
            if (resultItemTag.isEmpty()) {
                outItems.add(null);
                outGeneric.add(null);
                continue;
            }

            // Try generic deserialization (with StackType key)
            IAEStack<?> generic = resultItemTag.hasKey("StackType") ? IAEStack.fromNBTGeneric(resultItemTag) : null;
            if (generic != null) {
                outGeneric.add(generic);
                IAEItemStack converted = Platform.stackConvert(generic);
                if (converted != null) {
                    outItems.add(converted);
                }
            } else {
                // 回退：当作普通物品
                final ItemStack gs = stackFromNBT(resultItemTag);
                if (!resultItemTag.isEmpty() && gs.isEmpty()) {
                    throw new IllegalArgumentException("Invalid output at slot " + x);
                }
                if (!gs.isEmpty()) {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.createStack(gs);
                    if (aeItem != null) {
                        outItems.add(aeItem);
                        outGeneric.add(aeItem);
                    }
                }
            }
        }

        while (outItems.size() < PROCESSING_OUTPUT_LIMIT) {
            outItems.add(null);
            outGeneric.add(null);
        }

        // 输入不能为空（无输入的模板无意义）
        if (inItems.isEmpty() || inItems.stream().allMatch(Objects::isNull)) {
            throw new IllegalStateException("Special pattern requires at least one input");
        }

        // 输出可为空 - 关键修改点
        // 原逻辑: if (tmpOutputs.isEmpty() || tmpInputs.isEmpty()) throw ...
        // 新逻辑: 仅检查输入非空，输出允许为空

        // ========== 物品类型数组初始化 ==========

        // 初始化输入数组（固定大小）
        this.inputs = new IAEItemStack[Math.max(PROCESSING_INPUT_LIMIT, inItems.size())];
        for (int i = 0; i < Math.min(inItems.size(), this.inputs.length); i++) {
            this.inputs[i] = inItems.get(i);
        }

        // 初始化输出数组（动态大小）
        this.outputs = outItems.toArray(new IAEItemStack[PROCESSING_OUTPUT_LIMIT]);

        // 压缩物品输入（合并相同物品）
        final Map<IAEItemStack, IAEItemStack> tmpInputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.inputs) {
            if (io == null) {
                continue;
            }
            tmpInputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedInputs = tmpInputs.values().toArray(new IAEItemStack[0]);

        // 压缩物品输出（允许空）
        final Map<IAEItemStack, IAEItemStack> tmpOutputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.outputs) {
            if (io == null) {
                continue;
            }
            tmpOutputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedOutputs = tmpOutputs.values().toArray(new IAEItemStack[0]);

        // ========== 泛型数组初始化 ==========

        // 泛型输入数组（固定大小，保持槽位位置）
        this.genericInputs = new IAEStack<?>[Math.max(PROCESSING_INPUT_LIMIT, inGeneric.size())];
        for (int i = 0; i < Math.min(inGeneric.size(), this.genericInputs.length); i++) {
            this.genericInputs[i] = inGeneric.get(i);
        }

        // 泛型输出数组
        this.genericOutputs = outGeneric.toArray(new IAEStack<?>[PROCESSING_OUTPUT_LIMIT]);

        // 压缩泛型输入
        this.genericCondensedInputs = condenseGenericList(this.genericInputs);

        // 压缩泛型输出
        this.genericCondensedOutputs = condenseGenericList(this.genericOutputs);
    }

    // ===== 接口实现 =====

    @Override
    public boolean isCraftable() {
        return false; // 特殊模板仅用于加工
    }

    // ========== 泛型主入口方法（支持物品+流体等多种类型） ==========

    @Override
    public IAEStack<?>[] getAEInputs() {
        return this.genericInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return this.genericCondensedInputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return this.genericOutputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return this.genericCondensedOutputs;
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
        return genericOutputs.length == 0 || Arrays.stream(genericOutputs).allMatch(Objects::isNull);
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
        if (this == obj)
            return true;
        if (!(obj instanceof SpecialPatternHelper other))
            return false;
        return Objects.equals(pattern, other.pattern);
    }
}

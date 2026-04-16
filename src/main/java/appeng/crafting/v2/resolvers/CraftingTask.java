package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.UsedResolverEntry;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

/**
 * 用于解决 {@link CraftingRequest} 的单个操作。
 * 可以有多个输入和输出，在合成解析过程中运行时确定。
 */
public abstract class CraftingTask implements ITreeSerializable {

    public enum State {

        NEEDS_MORE_WORK(true),
        SUCCESS(false),
        /**
         * 中止整个合成操作，仅在绝对必要时使用
         */
        FAILURE(false);

        public final boolean needsMoreWork;

        State(boolean needsMoreWork) {
            this.needsMoreWork = needsMoreWork;
        }
    }

    public static final class StepOutput {

        @Nonnull
        public final List<CraftingRequest> extraInputsRequired;

        public StepOutput() {
            this(Collections.emptyList());
        }

        public StepOutput(@Nonnull List<CraftingRequest> extraInputsRequired) {
            this.extraInputsRequired = extraInputsRequired;
        }
    }

    public static final int PRIORITY_EXTRACT = Integer.MAX_VALUE - 100;
    public static final int PRIORITY_CRAFTING_EMITTER = PRIORITY_EXTRACT - 200;
    /** 加上配方优先级得到最终优先级 */
    public static final int PRIORITY_CRAFT_OFFSET = 0;

    public static final int PRIORITY_SIMULATE_CRAFT = Integer.MIN_VALUE + 200;
    public static final int PRIORITY_SIMULATE = Integer.MIN_VALUE + 100;

    public final CraftingRequest request;
    public final int priority;
    protected State state;

    /**
     * 执行一步计算。
     *
     * @return 描述此次调用进度的 {@link StepOutput} 实例
     */
    public abstract StepOutput calculateOneStep(CraftingContext context);

    /**
     * @return 实际退还的数量
     */
    public abstract long partialRefund(CraftingContext context, long amount);

    public abstract void fullRefund(CraftingContext context);

    @SuppressWarnings("rawtypes")
    public abstract void populatePlan(IItemList targetPlan);

    public abstract void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
            MECraftingInventory craftingInv);

    protected CraftingTask(CraftingRequest request, int priority) {
        this.request = request;
        this.priority = priority;
        this.state = State.NEEDS_MORE_WORK;
    }

    @SuppressWarnings({ "unused" })
    protected CraftingTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        this.request = ((UsedResolverEntry) parent).parent;
        this.priority = serializer.getBuffer().readInt();
        this.state = serializer.readEnum(State.class);
    }

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        serializer.getBuffer().writeInt(priority);
        serializer.writeEnum(state);
        return Collections.emptyList();
    }

    @Override
    public void loadChildren(List<ITreeSerializable> children) throws IOException {}

    public State getState() {
        return state;
    }

    /**
     * @return 合成树 GUI 的本地化 tooltip 文本
     */
    public String getTooltipText() {
        return toString();
    }

    /**
     * @return 此任务是否只在网络缺少材料时才有用
     */
    public boolean isSimulated() {
        return false;
    }

    /**
     * 按优先级比较 — 最高优先级在前
     */
    public static final Comparator<CraftingTask> PRIORITY_COMPARATOR = Comparator.comparing(ct -> -ct.priority);
}

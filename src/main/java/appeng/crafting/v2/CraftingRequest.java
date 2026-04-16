package appeng.crafting.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

import appeng.api.config.CraftingMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.crafting.v2.CraftingContext.RequestInProcessing;
import appeng.crafting.v2.resolvers.CraftingTask;
import io.netty.buffer.ByteBuf;

/**
 * 单个要合成的栈请求（物品或流体），例如 32x 火把
 */
public class CraftingRequest implements ITreeSerializable {

    public enum SubstitutionMode {
        /**
         * 不允许替代，不使用 AE 系统中的物品 — 用于用户发起的请求
         */
        PRECISE_FRESH,
        /**
         * 精确使用请求的物品
         */
        PRECISE,
        /**
         * 允许模糊匹配材料
         */
        ACCEPT_FUZZY
    }

    public static class UsedResolverEntry implements ITreeSerializable {

        public final CraftingRequest parent;
        public CraftingTask task;
        public final IAEStack<?> resolvedStack;

        public UsedResolverEntry(CraftingRequest parent, CraftingTask task, IAEStack<?> resolvedStack) {
            this.parent = parent;
            this.task = task;
            this.resolvedStack = resolvedStack;
        }

        public UsedResolverEntry(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            this.parent = (CraftingRequest) parent;
            this.resolvedStack = serializer.readStack();
            this.task = null;
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            serializer.writeStack(resolvedStack);
            return Collections.singletonList(task);
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            task = Objects.requireNonNull((CraftingTask) children.iterator().next());
        }
    }

    public final CraftingRequest parentRequest;
    public final Set<CraftingRequest> parentRequests;
    RequestInProcessing liveRequest;
    /**
     * 代表需要合成的物品/流体及其数量
     */
    public final IAEStack<?> stack;

    public final SubstitutionMode substitutionMode;
    public final Predicate<IAEStack<?>> acceptableSubstituteFn;

    public final CraftingMode craftingMode;

    public final List<UsedResolverEntry> usedResolvers = new ArrayList<>();
    /**
     * 此请求及其子请求是否可以用模拟来满足
     */
    public final boolean allowSimulation;
    /**
     * 尚未解决的元素数量（物品数/mB）
     */
    public volatile long remainingToProcess;

    private volatile long byteCost = 0;
    private volatile long untransformedByteCost = 0;
    /**
     * 如果物品不得不被模拟（系统中没有足够的材料以任何方式满足此请求）
     */
    public volatile boolean wasSimulated = false;
    public boolean incomplete = false;

    /**
     * 用于避免无限递归的所有祖先 pattern 集合
     */
    public final Set<ICraftingPatternDetails> patternParents = new HashSet<>();

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        serializer.writeStack(stack);
        serializer.writeEnum(substitutionMode);
        buffer.writeBoolean(allowSimulation);
        buffer.writeLong(remainingToProcess);
        buffer.writeLong(byteCost);
        buffer.writeLong(untransformedByteCost);
        buffer.writeBoolean(wasSimulated);
        buffer.writeBoolean(incomplete);
        buffer.writeInt(craftingMode.ordinal());
        return usedResolvers;
    }

    @Override
    public void loadChildren(List<ITreeSerializable> children) throws IOException {
        for (ITreeSerializable child : children) {
            usedResolvers.add((UsedResolverEntry) child);
        }
    }

    @SuppressWarnings({ "unused" })
    public CraftingRequest(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        stack = serializer.readStack();
        parentRequest = null;
        parentRequests = Collections.emptySet();
        substitutionMode = serializer.readEnum(SubstitutionMode.class);
        allowSimulation = buffer.readBoolean();
        remainingToProcess = buffer.readLong();
        byteCost = buffer.readLong();
        untransformedByteCost = buffer.readLong();
        wasSimulated = buffer.readBoolean();
        incomplete = buffer.readBoolean();
        int index = buffer.readInt();
        if (index < 0 || index >= CraftingMode.values().length || CraftingMode.values()[index] == CraftingMode.STANDARD)
            craftingMode = CraftingMode.STANDARD;
        else craftingMode = CraftingMode.IGNORE_MISSING;
        acceptableSubstituteFn = x -> true;
    }

    /**
     * @param parentRequest          发起此合成请求的父请求。null 如果是根请求
     * @param stack                  要请求的物品/流体及数量
     * @param substitutionMode       是否以及如何允许替代
     * @param acceptableSubstituteFn 在模糊模式下判断给定物品是否可以满足请求的谓词
     */
    public CraftingRequest(CraftingRequest parentRequest, @Nonnull IAEStack<?> stack, SubstitutionMode substitutionMode,
            boolean allowSimulation, CraftingMode craftingMode, Predicate<IAEStack<?>> acceptableSubstituteFn) {
        this.parentRequest = parentRequest;
        if (parentRequest == null) {
            this.parentRequests = Collections.emptySet();
        } else {
            Builder<CraftingRequest> builder = ImmutableSet.builder();
            builder.addAll(parentRequest.parentRequests);
            builder.add(parentRequest);
            this.parentRequests = builder.build();
        }
        this.stack = stack;
        this.substitutionMode = substitutionMode;
        this.acceptableSubstituteFn = acceptableSubstituteFn;
        this.remainingToProcess = stack.getStackSize();
        this.allowSimulation = allowSimulation;
        this.craftingMode = craftingMode;
    }

    public CraftingRequest(CraftingRequest parentRequest, @Nonnull IAEStack<?> stack, SubstitutionMode substitutionMode,
            boolean allowSimulation, CraftingMode craftingMode) {
        this(parentRequest, stack, substitutionMode, allowSimulation, craftingMode, x -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    public CraftingRequest(IAEStack<?> request, SubstitutionMode substitutionMode, boolean allowSimulation,
            CraftingMode craftingMode) {
        this(null, request, substitutionMode, allowSimulation, craftingMode, x -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    public long getByteCost() {
        return byteCost;
    }

    private String getReadableStackName() {
        try {
            return stack.getDisplayName();
        } catch (Exception e) {
            AELog.warn(e, "Trying to obtain display name for " + stack);
            return "<EXCEPTION>";
        }
    }

    @Override
    public String toString() {
        return "CraftingRequest{request=" + stack
                + "<"
                + getReadableStackName()
                + ">, substitutionMode="
                + substitutionMode
                + ", remainingToProcess="
                + remainingToProcess
                + ", byteCost="
                + byteCost
                + ", wasSimulated="
                + wasSimulated
                + ", incomplete="
                + incomplete
                + '}';
    }

    public String getTooltipText() {
        return "Requested: "
                + getReadableStackName()
                + "\n "
                + GuiText.Substitute.getLocal()
                + " "
                + ((substitutionMode == SubstitutionMode.ACCEPT_FUZZY) ? GuiText.Yes.getLocal() : GuiText.No.getLocal())
                + "\n "
                + GuiText.BytesUsed.getLocal()
                + ": "
                + byteCost
                + "\n "
                + GuiText.Simulation.getLocal()
                + ": "
                + (wasSimulated ? GuiText.Yes.getLocal() : GuiText.No.getLocal());
    }

    /**
     * 减少满足此请求所需的物品数量，并将多余的物品添加到上下文缓存中。
     */
    public void fulfill(CraftingTask origin, IAEStack<?> input, CraftingContext context) {
        if (input == null || input.getStackSize() == 0) {
            return;
        }
        if (input.getStackSize() < 0) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with a negative amount of " + input + " : " + this);
        }
        if (this.remainingToProcess < input.getStackSize()) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with too many of " + input + " : " + this);
        }
        this.untransformedByteCost += input.getStackSize();
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.remainingToProcess -= input.getStackSize();
        this.usedResolvers.add(new UsedResolverEntry(this, origin, input.copy()));
    }

    /**
     * 通过 resolver 合成任务传播所需的退还。
     */
    public void partialRefund(CraftingContext context, final long refundedAmount) {
        long remainingTaskAmount = refundedAmount;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (remainingTaskAmount <= 0) {
                break;
            }
            if (resolver.resolvedStack.getStackSize() <= 0) {
                continue;
            }
            final long taskRefunded = resolver.task
                    .partialRefund(context, Math.min(remainingTaskAmount, resolver.resolvedStack.getStackSize()));
            remainingTaskAmount -= taskRefunded;
            resolver.resolvedStack.setStackSize(resolver.resolvedStack.getStackSize() - taskRefunded);
        }
        if (remainingTaskAmount < 0) {
            throw new IllegalStateException("Refunds resulted in a negative amount of an item for request " + this);
        }
        if (remainingTaskAmount != 0) {
            throw new IllegalStateException("Partial refunds could not cover all resolved items for request " + this);
        }

        final long originallyRequested = this.stack.getStackSize();
        final long originallyRemainingToProcess = this.remainingToProcess;
        final long originallyProcessed = originallyRequested - originallyRemainingToProcess;

        final long newlyRequested = originallyRequested - refundedAmount;
        final long newlyProcessed = Math.min(originallyProcessed, newlyRequested);
        final long newlyRemainingToProcess = newlyRequested - newlyProcessed;

        this.stack.setStackSize(newlyRequested);
        this.remainingToProcess = newlyRemainingToProcess;
        this.untransformedByteCost -= refundedAmount;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        if (this.remainingToProcess < 0) {
            throw new IllegalArgumentException("Refunded more items than were resolved for request " + this);
        }
    }

    public void fullRefund(CraftingContext context) {
        for (UsedResolverEntry resolver : usedResolvers) {
            resolver.task.fullRefund(context);
        }
        this.remainingToProcess = 0;
        this.untransformedByteCost = 0;
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.stack.setStackSize(0);
        this.usedResolvers.clear();
    }

    /**
     * 获取已解析的物品栈。
     *
     * @throws IllegalStateException 如果使用了多种物品类型来解析此请求
     */
    public IAEStack<?> getOneResolvedType() {
        IAEStack<?> found = null;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (resolver.resolvedStack.getStackSize() <= 0) {
                continue;
            }
            if (found == null) {
                found = resolver.resolvedStack.copy();
            } else {
                throw new IllegalStateException("Found multiple item types resolving " + this);
            }
        }
        if (found == null) {
            throw new IllegalStateException("Found no resolution for " + this);
        }
        return found;
    }
}

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
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
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
        PRECISE_FRESH,
        PRECISE,
        ACCEPT_FUZZY
    }

    public static class UsedResolverEntry implements ITreeSerializable {

        public final CraftingRequest parent;
        public CraftingTask task;
        public GenericStack resolvedStack;

        public UsedResolverEntry(CraftingRequest parent, CraftingTask task, GenericStack resolvedStack) {
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

    public final AEKey what;
    /**
     * 总请求量（可能因退还而改变）
     */
    public long totalAmount;

    public final SubstitutionMode substitutionMode;
    public final Predicate<AEKey> acceptableSubstituteFn;

    public final CraftingMode craftingMode;

    public final List<UsedResolverEntry> usedResolvers = new ArrayList<>();
    public final boolean allowSimulation;
    public volatile long remainingToProcess;

    private volatile long byteCost = 0;
    private volatile long untransformedByteCost = 0;
    public volatile boolean wasSimulated = false;
    public boolean incomplete = false;

    public final Set<ICraftingPatternDetails> patternParents = new HashSet<>();

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        serializer.writeStack(new GenericStack(what, totalAmount));
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
        GenericStack stack = serializer.readStack();
        this.what = stack.what();
        this.totalAmount = stack.amount();
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

    public CraftingRequest(CraftingRequest parentRequest, @Nonnull AEKey what, long amount,
            SubstitutionMode substitutionMode, boolean allowSimulation, CraftingMode craftingMode,
            Predicate<AEKey> acceptableSubstituteFn) {
        this.parentRequest = parentRequest;
        if (parentRequest == null) {
            this.parentRequests = Collections.emptySet();
        } else {
            Builder<CraftingRequest> builder = ImmutableSet.builder();
            builder.addAll(parentRequest.parentRequests);
            builder.add(parentRequest);
            this.parentRequests = builder.build();
        }
        this.what = what;
        this.totalAmount = amount;
        this.substitutionMode = substitutionMode;
        this.acceptableSubstituteFn = acceptableSubstituteFn;
        this.remainingToProcess = amount;
        this.allowSimulation = allowSimulation;
        this.craftingMode = craftingMode;
    }

    public CraftingRequest(CraftingRequest parentRequest, @Nonnull AEKey what, long amount,
            SubstitutionMode substitutionMode, boolean allowSimulation, CraftingMode craftingMode) {
        this(parentRequest, what, amount, substitutionMode, allowSimulation, craftingMode, x -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    public CraftingRequest(AEKey what, long amount, SubstitutionMode substitutionMode, boolean allowSimulation,
            CraftingMode craftingMode) {
        this(null, what, amount, substitutionMode, allowSimulation, craftingMode, x -> true);
        if (substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            throw new IllegalArgumentException("Fuzzy requests must have a substitution-valid predicate");
        }
    }

    public long getByteCost() {
        return byteCost;
    }

    private String getReadableStackName() {
        try {
            return what.getDisplayName();
        } catch (Exception e) {
            AELog.warn(e, "Trying to obtain display name for " + what);
            return "<EXCEPTION>";
        }
    }

    @Override
    public String toString() {
        return "CraftingRequest{what=" + what
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

    public void fulfill(CraftingTask origin, GenericStack input, CraftingContext context) {
        if (input == null || input.amount() == 0) {
            return;
        }
        if (input.amount() < 0) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with a negative amount of " + input + " : " + this);
        }
        if (this.remainingToProcess < input.amount()) {
            throw new IllegalArgumentException(
                    "Can't fulfill crafting request with too many of " + input + " : " + this);
        }
        this.untransformedByteCost += input.amount();
        this.byteCost = CraftingCalculations.adjustByteCost(this, untransformedByteCost);
        this.remainingToProcess -= input.amount();
        this.usedResolvers.add(new UsedResolverEntry(this, origin, input));
    }

    public void partialRefund(CraftingContext context, final long refundedAmount) {
        long remainingTaskAmount = refundedAmount;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (remainingTaskAmount <= 0) {
                break;
            }
            if (resolver.resolvedStack.amount() <= 0) {
                continue;
            }
            final long taskRefunded = resolver.task
                    .partialRefund(context, Math.min(remainingTaskAmount, resolver.resolvedStack.amount()));
            remainingTaskAmount -= taskRefunded;
            resolver.resolvedStack = new GenericStack(resolver.resolvedStack.what(),
                    resolver.resolvedStack.amount() - taskRefunded);
        }
        if (remainingTaskAmount < 0) {
            throw new IllegalStateException("Refunds resulted in a negative amount of an item for request " + this);
        }
        if (remainingTaskAmount != 0) {
            throw new IllegalStateException("Partial refunds could not cover all resolved items for request " + this);
        }

        final long originallyRequested = this.totalAmount;
        final long originallyRemainingToProcess = this.remainingToProcess;
        final long originallyProcessed = originallyRequested - originallyRemainingToProcess;

        final long newlyRequested = originallyRequested - refundedAmount;
        final long newlyProcessed = Math.min(originallyProcessed, newlyRequested);
        final long newlyRemainingToProcess = newlyRequested - newlyProcessed;

        this.totalAmount = newlyRequested;
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
        this.totalAmount = 0;
        this.usedResolvers.clear();
    }

    public GenericStack getOneResolvedType() {
        GenericStack found = null;
        for (UsedResolverEntry resolver : usedResolvers) {
            if (resolver.resolvedStack.amount() <= 0) {
                continue;
            }
            if (found == null) {
                found = resolver.resolvedStack;
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

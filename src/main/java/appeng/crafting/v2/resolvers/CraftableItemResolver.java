package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;

/**
 * 通过合成配方来解决合成请求的解析器。
 */
public class CraftableItemResolver implements CraftingRequestResolver {

    /**
     * 子请求 + 每次合成消耗量的包装
     */
    public static class RequestAndPerCraftAmount implements ITreeSerializable {

        public final CraftingRequest request;
        public final long perCraftAmount;

        public RequestAndPerCraftAmount(CraftingRequest request, long perCraftAmount) {
            this.request = request;
            this.perCraftAmount = perCraftAmount;
        }

        @SuppressWarnings({ "unused" })
        public RequestAndPerCraftAmount(CraftingTreeSerializer serializer, ITreeSerializable parent)
                throws IOException {
            this.perCraftAmount = serializer.getBuffer().readLong();
            this.request = new CraftingRequest(serializer, parent);
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            serializer.getBuffer().writeLong(this.perCraftAmount);
            return request.serializeTree(serializer);
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            request.loadChildren(children);
        }

        @Override
        public ITreeSerializable getSerializationParent() {
            return request;
        }
    }

    /**
     * 使用特定配方进行合成的具体任务
     */
    public static class CraftFromPatternTask extends CraftingTask {

        public final ICraftingPatternDetails pattern;
        public final boolean allowSimulation;
        public final boolean isComplex;
        protected final GenericStack[] patternRecursionInputs;
        protected final GenericStack[] patternInputs;
        protected final GenericStack[] patternOutputs;
        protected final GenericStack matchingOutput;
        public IAEItemStack craftingMachine;
        protected final ArrayList<RequestAndPerCraftAmount> childRequests = new ArrayList<>();
        protected final ArrayList<CraftingRequest> complexRequestPerSlot = new ArrayList<>();
        protected final Map<GenericStack, CraftingRequest> childRecursionRequests = new HashMap<>();
        protected final IdentityHashMap<GenericStack, Long> byproducts = new IdentityHashMap<>();
        protected boolean requestedInputs = false;
        protected long totalCraftsDone = 0;
        protected long fulfilledAmount = 0;
        protected long matchingOutputRemainderItems = 0;

        public CraftFromPatternTask(CraftingRequest request, ICraftingPatternDetails pattern, int priority,
                boolean allowSimulation, boolean isComplex) {
            super(request, priority);
            this.pattern = pattern;
            this.allowSimulation = allowSimulation;
            this.isComplex = isComplex;

            GenericStack[] pInputs = pattern.getCondensedInputStacks();
            GenericStack[] pOutputs = pattern.getCondensedOutputStacks();

            if (!hasRecursiveInputs(pInputs, pOutputs)) {
                this.patternInputs = pInputs;
                this.patternOutputs = pOutputs;
                this.patternRecursionInputs = new GenericStack[0];
            } else {
                pInputs = copyStacks(pInputs);
                pOutputs = copyStacks(pOutputs);
                this.patternRecursionInputs = calculateRecursiveInputs(pInputs, pOutputs);
                this.patternInputs = filterMeaningfulStacks(pInputs);
                this.patternOutputs = filterMeaningfulStacks(pOutputs);
            }

            GenericStack mo = null;
            for (GenericStack patternOutput : this.patternOutputs) {
                if (isOutputAcceptable(patternOutput)) {
                    mo = patternOutput;
                    break;
                }
            }
            this.matchingOutput = mo;
            if (matchingOutput == null) {
                state = State.FAILURE;
            }
        }

        private static GenericStack[] copyStacks(GenericStack[] stacks) {
            return Arrays.stream(stacks).map(s -> new GenericStack(s.what(), s.amount())).toArray(GenericStack[]::new);
        }

        @SuppressWarnings("unused")
        public CraftFromPatternTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            final ByteBuf buffer = serializer.getBuffer();
            this.pattern = serializer.readPattern();
            this.allowSimulation = buffer.readBoolean();
            this.isComplex = buffer.readBoolean();
            this.matchingOutput = serializer.readStack();
            this.craftingMachine = serializer.readItemStack();
            this.totalCraftsDone = buffer.readLong();

            GenericStack[] pInputs = pattern.getCondensedInputStacks();
            GenericStack[] pOutputs = pattern.getCondensedOutputStacks();
            if (!hasRecursiveInputs(pInputs, pOutputs)) {
                this.patternInputs = pInputs;
                this.patternOutputs = pOutputs;
                this.patternRecursionInputs = new GenericStack[0];
            } else {
                pInputs = copyStacks(pInputs);
                pOutputs = copyStacks(pOutputs);
                this.patternRecursionInputs = calculateRecursiveInputs(pInputs, pOutputs);
                this.patternInputs = filterMeaningfulStacks(pInputs);
                this.patternOutputs = filterMeaningfulStacks(pOutputs);
            }
        }

        // ====================== 递归输入计算 ======================

        private static GenericStack[] calculateRecursiveInputs(GenericStack[] pInputs, GenericStack[] pOutputs) {
            GenericStack[] recInputs = null;
            for (int oi = 0; oi < pOutputs.length; oi++) {
                var output = pOutputs[oi];
                for (int ii = 0; ii < pInputs.length; ii++) {
                    var input = pInputs[ii];
                    if (!input.what().equals(output.what())) {
                        continue;
                    }
                    final long netProduced = output.amount() - input.amount();
                    GenericStack recInput;
                    if (netProduced > 0) {
                        recInput = new GenericStack(input.what(), input.amount());
                        pInputs[ii] = new GenericStack(input.what(), 0);
                        pOutputs[oi] = new GenericStack(output.what(), netProduced);
                    } else {
                        recInput = new GenericStack(input.what(), input.amount() + netProduced);
                        pInputs[ii] = new GenericStack(input.what(), -netProduced);
                        pOutputs[oi] = new GenericStack(output.what(), 0);
                    }
                    if (recInput.amount() <= 0) {
                        continue;
                    }
                    if (recInputs == null) {
                        recInputs = new GenericStack[] { recInput };
                    } else {
                        recInputs = Arrays.copyOf(recInputs, recInputs.length + 1);
                        recInputs[recInputs.length - 1] = recInput;
                    }
                }
            }
            return recInputs == null ? new GenericStack[0] : recInputs;
        }

        private static boolean hasRecursiveInputs(GenericStack[] pInputs, GenericStack[] pOutputs) {
            for (GenericStack output : pOutputs) {
                for (GenericStack input : pInputs) {
                    if (input.what().equals(output.what())) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static GenericStack[] filterMeaningfulStacks(GenericStack[] stacks) {
            int i = 0, j = 0;
            for (; i < stacks.length; i++) {
                if (stacks[i].amount() > 0) {
                    stacks[j] = stacks[i];
                    j++;
                }
            }
            return i == j ? stacks : Arrays.copyOf(stacks, j);
        }

        // ====================== 序列化 ======================

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            final ByteBuf buffer = serializer.getBuffer();
            serializer.writePattern(pattern);
            buffer.writeBoolean(allowSimulation);
            buffer.writeBoolean(isComplex);
            serializer.writeStack(matchingOutput);
            serializer.writeStack(GenericStack.fromIAEStack(craftingMachine));
            buffer.writeLong(totalCraftsDone);
            return this.childRequests;
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            for (ITreeSerializable child : children) {
                this.childRequests.add((RequestAndPerCraftAmount) child);
            }
        }

        // ====================== 查询方法 ======================

        public List<CraftingRequest> getChildRequests() {
            return childRequests.stream().map(r -> r.request).collect(Collectors.toList());
        }

        public long getTotalCraftsDone() {
            return totalCraftsDone;
        }

        public IAEItemStack getCraftingMachine() {
            return craftingMachine;
        }

        public boolean isOutputAcceptable(GenericStack otherStack) {
            if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
                if (!this.request.acceptableSubstituteFn.test(otherStack.what())) {
                    return false;
                }
                if (this.request.what.getType() != otherStack.what().getType()) return false;
                return this.request.what.fuzzyEquals(otherStack.what(), FuzzyMode.IGNORE_ALL);
            } else {
                return this.request.what.equals(otherStack.what());
            }
        }

        /**
         * Validate whether a substitute stack can be used in the given pattern slot.
         * <p>
         * MC limitation: isValidItemForSlot() calls MC's CraftingManager recipe matching,
         * which only works with ItemStack. For non-item types, we skip the validation and
         * return true (non-item inputs don't go through the MC crafting table).
         */
        public boolean isValidSubstitute(GenericStack reference, GenericStack stack, World world, int slot) {
            if (!pattern.isCraftable()) {
                return true;
            }
            // MC limitation: crafting table validation only works for ItemStack
            if (stack.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                return pattern.isValidItemForSlot(slot, itemKey.toStack(), world);
            }
            return true;
        }

        // ====================== 核心计算逻辑 ======================

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                state = State.SUCCESS;
                return new StepOutput();
            }
            final boolean canUseSubstitutes = pattern.canSubstitute();
            final SubstitutionMode childMode = canUseSubstitutes ? SubstitutionMode.ACCEPT_FUZZY
                    : SubstitutionMode.PRECISE;
            final long toCraft = Platform
                    .ceilDiv(isComplex ? 1 : request.remainingToProcess, matchingOutput.amount());

            if (requestedInputs) {
                return collectInputsAndCraft(context, toCraft);
            } else {
                return requestInputs(context, childMode, toCraft);
            }
        }

        /**
         * 第二步：所有子请求已完成，收集结果并实际"合成"
         */
        private StepOutput collectInputsAndCraft(CraftingContext context, long toCraft) {
            long maxCraftable = toCraft;

            // 检查递归输入是否满足
            for (CraftingRequest recInputChild : childRecursionRequests.values()) {
                if (recInputChild.remainingToProcess > 0) {
                    maxCraftable = 0;
                    break;
                }
            }
            // 检查常规输入是否满足
            for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                final CraftingRequest inputChild = inputChildPair.request;
                final long costPerRecipe = inputChild.totalAmount / toCraft;
                if (costPerRecipe <= 0) continue;
                final long available = inputChild.totalAmount - inputChild.remainingToProcess;
                final long fullRecipes = available / costPerRecipe;
                maxCraftable = Math.min(maxCraftable, fullRecipes);
            }

            final long producedMatchingOutput = Math.multiplyExact(maxCraftable, matchingOutput.amount());
            this.matchingOutputRemainderItems = Math.max(0, producedMatchingOutput - request.remainingToProcess);
            this.fulfilledAmount = producedMatchingOutput - matchingOutputRemainderItems;
            request.fulfill(this, new GenericStack(matchingOutput.what(), fulfilledAmount), context);

            // 余量放入副产品库存
            if (matchingOutputRemainderItems > 0) {
                context.byproductsInventory.injectItems(
                        new GenericStack(matchingOutput.what(), matchingOutputRemainderItems),
                        Actionable.MODULATE);
            }

            // 复杂配方处理（合成台容器物品等）
            if (isComplex && fulfilledAmount > 0) {
                processComplexCrafting(context, maxCraftable);
            }

            // 其他非匹配输出的副产品
            for (GenericStack output : patternOutputs) {
                if (output != matchingOutput) {
                    final long bpAmount = Math.multiplyExact(maxCraftable, output.amount());
                    context.byproductsInventory.injectItems(
                            new GenericStack(output.what(), bpAmount), Actionable.MODULATE);
                    this.byproducts.put(new GenericStack(output.what(), bpAmount), output.amount());
                }
            }

            this.totalCraftsDone = maxCraftable;

            // 退还多余的输入
            if (maxCraftable != toCraft) {
                refundExcessInputs(context, maxCraftable, toCraft);
            }

            // 传播模拟标记
            if (totalCraftsDone > 0) {
                for (RequestAndPerCraftAmount pair : childRequests) {
                    if (pair.request.wasSimulated) {
                        this.request.wasSimulated = true;
                        break;
                    }
                }
            }

            this.craftingMachine = context.getCrafterIconForPattern(this.pattern);
            state = State.SUCCESS;
            return new StepOutput();
        }

        private void processComplexCrafting(CraftingContext context, long maxCraftable) {
            if (maxCraftable > 1) {
                throw new IllegalStateException(
                        "Complex recipe got calculated with more than 1 set of inputs at a time");
            }
            final IAEItemStack[] inputs = new IAEItemStack[9];
            for (int slot = 0; slot < complexRequestPerSlot.size(); slot++) {
                final CraftingRequest slotRequest = complexRequestPerSlot.get(slot);
                if (slotRequest != null && slotRequest.remainingToProcess <= 0) {
                    GenericStack resolved = slotRequest.getOneResolvedType();
                    if (resolved != null && resolved.what() instanceof appeng.api.stacks.AEItemKey itemKey) {
                        inputs[slot] = AEItemStack.fromItemStack(
                                itemKey.toStack((int) Math.min(resolved.amount(), Integer.MAX_VALUE)));
                    }
                }
            }
            final IAEItemStack[] leftovers = context.simulateComplexCrafting(inputs, pattern);
            for (IAEItemStack leftover : leftovers) {
                if (leftover == null || leftover.getStackSize() <= 0) {
                    continue;
                }
                context.byproductsInventory.injectItems(
                        new GenericStack(leftover.toAEKey(), leftover.getStackSize()), Actionable.MODULATE);
                this.byproducts.put(new GenericStack(leftover.toAEKey(), leftover.getStackSize()), leftover.getStackSize());
            }
        }

        private void refundExcessInputs(CraftingContext context, long maxCraftable, long toCraft) {
            for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                final CraftingRequest inputChild = inputChildPair.request;
                final long costPerRecipe = inputChild.totalAmount / toCraft;
                if (costPerRecipe <= 0) continue;
                final long actuallyNeeded = Math.multiplyExact(costPerRecipe, maxCraftable);
                final long produced = inputChild.totalAmount
                        - Math.max(inputChild.remainingToProcess, 0);
                if (produced > actuallyNeeded) {
                    if (maxCraftable == 0) {
                        inputChild.fullRefund(context);
                    } else {
                        inputChild.partialRefund(context, produced - actuallyNeeded);
                    }
                }
            }
            if (maxCraftable == 0) {
                for (CraftingRequest recChild : childRecursionRequests.values()) {
                    recChild.fullRefund(context);
                }
            }
        }

        /**
         * 第一步：请求所有输入材料
         */
        private StepOutput requestInputs(CraftingContext context, SubstitutionMode childMode, long toCraft) {
            request.patternParents.add(this.pattern);
            ArrayList<CraftingRequest> newChildren = new ArrayList<>(
                    patternRecursionInputs.length + patternInputs.length);

            if (isComplex) {
                requestComplexInputs(context, childMode, toCraft, newChildren);
            } else {
                requestSimpleInputs(context, childMode, toCraft, newChildren);
            }

            // 递归输入
            for (GenericStack recInput : patternRecursionInputs) {
                final long amount = Math.multiplyExact(recInput.amount(), toCraft);
                CraftingRequest req = new CraftingRequest(
                        request, recInput.what(), amount,
                        SubstitutionMode.PRECISE, allowSimulation, request.craftingMode, x -> true);
                req.patternParents.addAll(request.patternParents);
                childRecursionRequests.put(recInput, req);
                newChildren.add(req);
            }

            this.requestedInputs = true;
            return new StepOutput(newChildren);
        }

        private void requestSimpleInputs(CraftingContext context, SubstitutionMode childMode, long toCraft,
                ArrayList<CraftingRequest> newChildren) {
            for (GenericStack input : patternInputs) {
                final long amount = Math.multiplyExact(input.amount(), toCraft);
                final AEKey inputKey = input.what();
                CraftingRequest req;
                if (childMode == SubstitutionMode.ACCEPT_FUZZY) {
                    final GenericStack inputRef = input;
                    req = new CraftingRequest(
                            request, inputKey, amount,
                            childMode, allowSimulation, request.craftingMode,
                            key -> {
                                if (!(inputKey instanceof appeng.api.stacks.AEItemKey)
                                        || !(inputRef.what() instanceof appeng.api.stacks.AEItemKey)) {
                                    return inputKey.equals(key);
                                }
                                return this.isValidSubstitute(inputRef,
                                        new GenericStack(key, 1), context.world, -1);
                            });
                } else {
                req = new CraftingRequest(
                        request, inputKey, amount,
                        childMode, allowSimulation, request.craftingMode, x -> true);
                }
                req.patternParents.addAll(request.patternParents);
                newChildren.add(req);
                childRequests.add(new RequestAndPerCraftAmount(req, input.amount()));
            }
        }

        private void requestComplexInputs(CraftingContext context, SubstitutionMode childMode, long toCraft,
                ArrayList<CraftingRequest> newChildren) {
            final GenericStack[] slotInputs = pattern.getInputStacks();
            for (int slot = 0; slot < slotInputs.length; slot++) {
                final GenericStack input = slotInputs[slot];
                if (input == null) {
                    complexRequestPerSlot.add(null);
                    continue;
                }
                final long amount = Math.multiplyExact(input.amount(), toCraft);
                final int finalSlot = slot;
                final AEKey inputKey = input.what();
                CraftingRequest req = new CraftingRequest(
                        request, inputKey, amount,
                        childMode, allowSimulation, request.craftingMode,
                        key -> this.isValidSubstitute(
                                new GenericStack(inputKey, amount),
                                new GenericStack(key, 1),
                                context.world, finalSlot));
                complexRequestPerSlot.add(req);
                newChildren.add(req);
                childRequests.add(new RequestAndPerCraftAmount(req, input.amount()));
            }
        }

        // ====================== 退还 ======================

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            if (fulfilledAmount <= 0) {
                return 0;
            }
            final long refundedCrafts = Math.min(
                    amount / matchingOutput.amount(),
                    totalCraftsDone);
            if (refundedCrafts <= 0) {
                return 0;
            }
            final long refundedOutputAmount = Math.multiplyExact(refundedCrafts, matchingOutput.amount());
            totalCraftsDone -= refundedCrafts;
            fulfilledAmount -= refundedOutputAmount;

            // 退还副产品
            for (Map.Entry<GenericStack, Long> bp : byproducts.entrySet()) {
                final long perCraft = bp.getValue();
                final GenericStack toExtract = new GenericStack(bp.getKey().what(),
                        Math.multiplyExact(refundedCrafts, perCraft));
                context.byproductsInventory.extractAny(toExtract, Actionable.MODULATE);
            }

            // 退还子请求的输入
            for (RequestAndPerCraftAmount childPair : childRequests) {
                final long childRefundAmount = Math.multiplyExact(refundedCrafts, childPair.perCraftAmount);
                childPair.request.partialRefund(context, childRefundAmount);
            }
            for (Map.Entry<GenericStack, CraftingRequest> recEntry : childRecursionRequests.entrySet()) {
                final long recRefundAmount = Math.multiplyExact(refundedCrafts, recEntry.getKey().amount());
                recEntry.getValue().partialRefund(context, recRefundAmount);
            }

            return refundedOutputAmount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            for (RequestAndPerCraftAmount childPair : childRequests) {
                childPair.request.fullRefund(context);
            }
            for (CraftingRequest recChild : childRecursionRequests.values()) {
                recChild.fullRefund(context);
            }
            for (Map.Entry<GenericStack, Long> bp : byproducts.entrySet()) {
                context.byproductsInventory.extractAny(
                        new GenericStack(bp.getKey().what(),
                                Math.multiplyExact(totalCraftsDone, bp.getValue())),
                        Actionable.MODULATE);
            }
            if (matchingOutputRemainderItems > 0) {
                context.byproductsInventory.extractItems(
                        new GenericStack(matchingOutput.what(), matchingOutputRemainderItems), Actionable.MODULATE);
            }
            totalCraftsDone = 0;
            fulfilledAmount = 0;
            matchingOutputRemainderItems = 0;
        }

        // ====================== 计划 & CPU 启动 ======================

        @Override
        @SuppressWarnings("unchecked")
        public void populatePlan(IItemList<IAEStackBase> targetPlan) {
            if (totalCraftsDone > 0) {
                for (RequestAndPerCraftAmount childPair : childRequests) {
                    childPair.request.usedResolvers.forEach(re -> re.task.populatePlan(targetPlan));
                }
                for (CraftingRequest recChild : childRecursionRequests.values()) {
                    recChild.usedResolvers.forEach(re -> re.task.populatePlan(targetPlan));
                }
                for (GenericStack output : patternOutputs) {
                    final long amount = Math.multiplyExact(totalCraftsDone, output.amount());
                    targetPlan.addRequestable(new GenericStack(output.what(), amount).toIAEStack());
                }
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            if (totalCraftsDone > 0) {
                for (RequestAndPerCraftAmount childPair : childRequests) {
                    childPair.request.usedResolvers.forEach(re -> re.task.startOnCpu(context, cpuCluster, craftingInv));
                }
                for (CraftingRequest recChild : childRecursionRequests.values()) {
                    recChild.usedResolvers.forEach(re -> re.task.startOnCpu(context, cpuCluster, craftingInv));
                }
                cpuCluster.addCrafting(pattern, totalCraftsDone);
            }
        }

        @Override
        public String toString() {
            return "CraftFromPatternTask{" + "pattern="
                    + pattern
                    + ", totalCraftsDone="
                    + totalCraftsDone
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }

    // ====================== Resolver 接口实现 ======================

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest request,
            @Nonnull CraftingContext context) {
        final boolean allowSimulation = request.allowSimulation;

        List<ICraftingPatternDetails> patterns = context.getPrecisePatternsFor(
                new GenericStack(request.what, 1).toIAEStack());

        if (patterns.isEmpty() && request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            patterns = context.getFuzzyPatternsFor(
                    new GenericStack(request.what, 1).toIAEStack());
        }

        if (patterns.isEmpty()) {
            return Collections.emptyList();
        }

        final ArrayList<CraftingTask> tasks = new ArrayList<>(patterns.size());
        for (ICraftingPatternDetails pattern : patterns) {
            if (request.patternParents.contains(pattern)) {
                continue;
            }
            final boolean isComplex = context.isPatternComplex(pattern);
            final int priority = CraftingTask.PRIORITY_CRAFT_OFFSET + pattern.getPriority();
            tasks.add(new CraftFromPatternTask(request, pattern, priority, allowSimulation, isComplex));
        }
        return tasks;
    }
}

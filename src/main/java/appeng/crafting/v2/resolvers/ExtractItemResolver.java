package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.CraftingPlan;
import appeng.api.storage.data.IAEStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class ExtractItemResolver implements CraftingRequestResolver {

    public static class ExtractItemTask extends CraftingTask {

        public final ArrayList<GenericStack> removedFromSystem = new ArrayList<>();
        public final ArrayList<GenericStack> removedFromByproducts = new ArrayList<>();

        public ExtractItemTask(CraftingRequest request) {
            super(request, CraftingTask.PRIORITY_EXTRACT);
        }

        @SuppressWarnings("unused")
        public ExtractItemTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            serializer.readList(removedFromSystem, serializer::readStack);
            serializer.readList(removedFromByproducts, serializer::readStack);
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            serializer.writeList(removedFromSystem, serializer::writeStack);
            serializer.writeList(removedFromByproducts, serializer::writeStack);
            return Collections.emptyList();
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {}

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            state = State.SUCCESS;
            if (request.remainingToProcess <= 0) {
                return new StepOutput(Collections.emptyList());
            }
            extractExact(context, context.byproductsInventory, removedFromByproducts);
            if (request.remainingToProcess > 0) {
                extractExact(context, context.itemModel, removedFromSystem);
            }
            if (request.remainingToProcess > 0
                    && request.substitutionMode == CraftingRequest.SubstitutionMode.ACCEPT_FUZZY) {
                extractFuzzy(context, context.byproductsInventory, removedFromByproducts);
                if (request.remainingToProcess > 0) {
                    extractFuzzy(context, context.itemModel, removedFromSystem);
                }
            }
            removedFromSystem.trimToSize();
            removedFromByproducts.trimToSize();
            return new StepOutput(Collections.emptyList());
        }

        private void extractExact(CraftingContext context, MECraftingInventory source, List<GenericStack> removedList) {
            GenericStack hint = new GenericStack(request.what, request.remainingToProcess);
            GenericStack exactMatching = source.extractAny(hint, Actionable.SIMULATE);
            if (exactMatching != null) {
                final long requestSize = Math.min(request.remainingToProcess, exactMatching.amount());
                GenericStack extracted = source.extractAny(
                        new GenericStack(exactMatching.what(), requestSize), Actionable.MODULATE);
                if (extracted != null && extracted.amount() > 0) {
                    request.fulfill(this, extracted, context);
                    removedList.add(extracted);
                }
            }
        }

        private void extractFuzzy(CraftingContext context, MECraftingInventory source, List<GenericStack> removedList) {
            var fuzzyMatching = source.findFuzzyAny(
                    new GenericStack(request.what, request.remainingToProcess), FuzzyMode.IGNORE_ALL);
            for (final var candidate : fuzzyMatching) {
                if (candidate == null) continue;
                if (request.acceptableSubstituteFn.test(candidate.what())) {
                    final long requestSize = Math.min(request.remainingToProcess, candidate.amount());
                    var extracted = source.extractAny(
                            new GenericStack(candidate.what(), requestSize), Actionable.MODULATE);
                    if (extracted == null || extracted.amount() <= 0) continue;
                    request.fulfill(this, extracted, context);
                    removedList.add(extracted);
                }
            }
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            final long originalAmount = amount;
            Collections.reverse(removedFromSystem);
            Collections.reverse(removedFromByproducts);
            amount = partialRefundFrom(context, amount, removedFromSystem, context.itemModel);
            amount = partialRefundFrom(context, amount, removedFromByproducts, context.byproductsInventory);
            Collections.reverse(removedFromSystem);
            Collections.reverse(removedFromByproducts);
            return originalAmount - amount;
        }

        private long partialRefundFrom(CraftingContext context, long amount, List<GenericStack> source,
                MECraftingInventory target) {
            final java.util.ListIterator<GenericStack> removedIt = source.listIterator();
            while (removedIt.hasNext() && amount > 0) {
                final GenericStack available = removedIt.next();
                final long availAmount = available.amount();
                if (availAmount > amount) {
                    target.injectItems(new GenericStack(available.what(), amount), Actionable.MODULATE);
                    removedIt.set(new GenericStack(available.what(), availAmount - amount));
                    amount = 0;
                } else {
                    target.injectItems(new GenericStack(available.what(), availAmount), Actionable.MODULATE);
                    amount -= availAmount;
                    removedIt.remove();
                }
            }
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            for (GenericStack removed : new ArrayList<>(removedFromByproducts)) {
                context.byproductsInventory.injectItems(removed, Actionable.MODULATE);
            }
            for (GenericStack removed : new ArrayList<>(removedFromSystem)) {
                context.itemModel.injectItems(removed, Actionable.MODULATE);
            }
            removedFromSystem.clear();
            removedFromByproducts.clear();
        }

        @Override
        public void contributePlan(CraftingPlan.Builder plan) {
            for (GenericStack removed : removedFromSystem) {
                plan.addAvailable(removed.what(), removed.amount());
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            for (GenericStack stack : removedFromSystem) {
                if (stack.amount() > 0) {
                    GenericStack extracted = craftingInv.extractAny(stack, Actionable.MODULATE);
                    if (extracted == null || extracted.amount() != stack.amount()) {
                        throw new IllegalStateException(new CraftBranchFailure(stack));
                    }
                    cpuCluster.addStorage(extracted);
                }
            }
        }

        @Override
        public String toString() {
            return "ExtractItemTask{" + "request="
                    + request
                    + ", removedFromSystem="
                    + removedFromSystem
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest request,
            @Nonnull CraftingContext context) {
        if (request.substitutionMode == CraftingRequest.SubstitutionMode.PRECISE_FRESH) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new ExtractItemTask(request));
        }
    }
}

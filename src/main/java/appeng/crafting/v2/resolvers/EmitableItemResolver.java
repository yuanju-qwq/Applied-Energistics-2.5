package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.me.cluster.implementations.CraftingCPUCluster;

public class EmitableItemResolver implements CraftingRequestResolver {

    public static class EmitItemTask extends CraftingTask {

        private long fulfilled = 0;

        public EmitItemTask(CraftingRequest request) {
            super(request, CraftingTask.PRIORITY_CRAFTING_EMITTER);
        }

        @SuppressWarnings("unused")
        public EmitItemTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            fulfilled = serializer.getBuffer().readLong();
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            serializer.getBuffer().writeLong(fulfilled);
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
            fulfilled = request.remainingToProcess;
            request.fulfill(this, new GenericStack(request.what, request.remainingToProcess), context);
            return new StepOutput(Collections.emptyList());
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            if (amount > fulfilled) {
                amount = fulfilled;
            }
            fulfilled -= amount;
            return amount;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            fulfilled = 0;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void populatePlan(IItemList<IAEStackBase> targetPlan) {
            if (fulfilled > 0) targetPlan.addRequestable(new GenericStack(request.what, fulfilled).toIAEStack());
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            cpuCluster.addEmitable(new GenericStack(this.request.what, fulfilled).toIAEStack());
        }

        @Override
        public String toString() {
            return "EmitItemTask{" + "fulfilled="
                    + fulfilled
                    + ", request="
                    + request
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
        if (context.craftingGrid.canEmitFor(request.what)) {
            return Collections.singletonList(new EmitItemTask(request));
        } else {
            return Collections.emptyList();
        }
    }
}

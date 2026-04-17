package appeng.crafting.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

import appeng.api.config.CraftingMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.core.AELog;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.hooks.TickHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import io.netty.buffer.ByteBuf;

/**
 * v2 合成任务——使用基于请求和解析器（resolver）的架构，原生支持 IAEStack<?> 泛型。
 *
 * @param <StackType> 顶层请求的栈类型
 */
public class CraftingJobV2<StackType extends IAEStack> implements ICraftingJob<StackType>,
        ITreeSerializable {

    private final StackType output;
    private CraftingContext context;
    private CraftingRequest topRequest;
    private CraftingMode craftingMode = CraftingMode.STANDARD;
    private boolean done = false;
    private boolean started = false;
    private ICraftingCallback callback;
    private CraftingStepLimitExceeded limitExceeded = null;

    /**
     * 从 GUI 创建新的合成任务
     *
     * @param output    要合成的目标物品/流体
     * @param grid      ME 网格
     * @param source    请求源
     * @param callback  完成回调
     */
    public CraftingJobV2(@Nonnull StackType output, @Nonnull IGrid grid, @Nonnull IActionSource source,
            ICraftingCallback callback, net.minecraft.world.World world, CraftingMode craftingMode) {
        this.output = output;
        this.callback = callback;
        this.craftingMode = craftingMode;
        this.context = new CraftingContext(world, grid, source);
        this.topRequest = new CraftingRequest(
                output.copy(),
                CraftingRequest.SubstitutionMode.PRECISE_FRESH,
                true,
                craftingMode);
    }

    /**
     * 反序列化构造
     */
    @SuppressWarnings("unchecked")
    public CraftingJobV2(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        this.output = (StackType) serializer.readStack();
        this.done = buffer.readBoolean();
        this.started = buffer.readBoolean();
        int modeOrd = buffer.readInt();
        if (modeOrd >= 0 && modeOrd < CraftingMode.values().length) {
            this.craftingMode = CraftingMode.values()[modeOrd];
        } else {
            this.craftingMode = CraftingMode.STANDARD;
        }
    }

    @Override
    public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
        final ByteBuf buffer = serializer.getBuffer();
        serializer.writeStack(output);
        buffer.writeBoolean(done);
        buffer.writeBoolean(started);
        buffer.writeInt(craftingMode.ordinal());
        if (topRequest != null) {
            return Collections.singletonList(topRequest);
        }
        return Collections.emptyList();
    }

    @Override
    public void loadChildren(List<ITreeSerializable> children) throws IOException {
        if (!children.isEmpty()) {
            topRequest = (CraftingRequest) children.get(0);
        }
    }

    // ==================== ICraftingJob 接口实现 ====================

    @Override
    public boolean isSimulation() {
        return context != null && context.wasSimulated;
    }

    @Override
    public long getByteTotal() {
        if (topRequest == null) {
            return 0;
        }
        return topRequest.getByteCost();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void populatePlan(IItemList<IAEStackBase> plan) {
        if (topRequest == null) {
            return;
        }
        for (CraftingRequest.UsedResolverEntry resolver : topRequest.usedResolvers) {
            resolver.task.populatePlan(plan);
        }
    }

    @Override
    public StackType getOutput() {
        return output;
    }

    @Override
    public boolean simulateFor(int milli) {
        if (done || context == null) {
            return false;
        }
        if (!started) {
            started = true;
            try {
                context.addRequest(topRequest);
            } catch (CraftingStepLimitExceeded e) {
                this.limitExceeded = e;
                this.done = true;
                return false;
            } catch (Exception e) {
                AELog.error(e, "Error starting crafting calculation for " + output);
                this.done = true;
                return false;
            }
        }
        final long deadline = System.currentTimeMillis() + milli;
        try {
            while (System.currentTimeMillis() < deadline) {
                CraftingTask.State state = context.doWork();
                if (state != CraftingTask.State.NEEDS_MORE_WORK) {
                    this.done = true;
                    return false;
                }
            }
        } catch (CraftingStepLimitExceeded e) {
            this.limitExceeded = e;
            this.done = true;
            return false;
        } catch (Exception e) {
            AELog.error(e, "Error during crafting calculation for " + output);
            this.done = true;
            return false;
        }
        return true;
    }

    @Override
    public Future<ICraftingJob<StackType>> schedule() {
        return TickHandler.instance().registerCraftingSimulation(
                context != null ? context.world : null,
                this);
    }

    @Override
    public boolean supportsCPUCluster(ICraftingCPU cluster) {
        return !this.isSimulation() || craftingMode == CraftingMode.IGNORE_MISSING;
    }

    @Override
    public CraftingMode getCraftingMode() {
        return this.craftingMode;
    }

    @Override
    public void startCrafting(MECraftingInventory storage, ICraftingCPU craftingCPU, IActionSource src) {
        if (topRequest == null || context == null) {
            return;
        }
        CraftingCPUCluster cpuCluster = (CraftingCPUCluster) craftingCPU;
        for (CraftingRequest.UsedResolverEntry resolver : topRequest.usedResolvers) {
            resolver.task.startOnCpu(context, cpuCluster, storage);
        }
    }

    @Override
    public MECraftingInventory getStorageAtBeginning() {
        if (context == null) {
            return new MECraftingInventory();
        }
        return context.availableCache;
    }

    // ==================== 查询方法 ====================

    public CraftingRequest getTopRequest() {
        return topRequest;
    }

    public CraftingContext getContext() {
        return context;
    }

    public CraftingStepLimitExceeded getLimitExceeded() {
        return limitExceeded;
    }

    public boolean isDone() {
        return done;
    }

    public ICraftingCallback getCallback() {
        return callback;
    }

    /**
     * 用于序列化后的网络传输
     */
    public ByteBuf serializeToNetwork() {
        if (context == null) {
            return null;
        }
        CraftingTreeSerializer serializer = new CraftingTreeSerializer(context.world);
        try {
            serializer.writeSerializableAndQueueChildren(this);
            while (serializer.hasWork()) {
                serializer.doWork();
            }
        } catch (Exception e) {
            AELog.error(e, "Error serializing crafting tree for " + output);
            serializer.doBestEffortWork();
        }
        return serializer.getBuffer();
    }

    @Override
    public String toString() {
        return "CraftingJobV2{" + "output="
                + output
                + ", done="
                + done
                + ", started="
                + started
                + ", craftingMode="
                + craftingMode
                + ", isSimulation="
                + isSimulation()
                + ", byteCost="
                + getByteTotal()
                + '}';
    }
}

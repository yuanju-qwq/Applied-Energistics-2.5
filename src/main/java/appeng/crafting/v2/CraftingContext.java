package appeng.crafting.v2;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MutableClassToInstanceMap;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.core.AEConfig;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.CraftingTask.State;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.OreListMultiMap;

/**
 * 鍚堟垚鎿嶄綔鐨勪笂涓嬫枃鐘舵€佸寘锛歁E 缃戞牸銆佽姹傝€呫€佸簱瀛樻ā鍨嬬瓑銆?
 */
public final class CraftingContext {

    public final World world;
    public final IGrid meGrid;
    public final ICraftingGrid craftingGrid;
    public IActionSource actionSource;

    /**
     * AE 绯荤粺鐗╁搧鍒楄〃鐨勫伐浣滃壇鏈紝鐢ㄤ簬妯℃嫙鍚堟垚璇锋眰瑙ｆ瀽鏃剁殑鍙樺寲銆?
     * 鍙兘鎻愬彇锛屾敞鍏ヨ浣跨敤 {@link CraftingContext#byproductsInventory}銆?
     */
    public final MECraftingInventory itemModel;
    /**
     * 涓€涓垵濮嬩负绌虹殑搴撳瓨锛岀敤浜庝繚瀛樻墍鏈夊悎鎴愬壇浜у搧杈撳嚭銆?
     * 鍦ㄤ粠 {@link CraftingContext#itemModel} 鎻愬彇涔嬪墠鍏堜粠杩欓噷鎻愬彇銆?
     */
    public final MECraftingInventory byproductsInventory;
    /**
     * 鍚堟垚璇锋眰寮€濮嬫椂鐗╁搧瀛樺湪鐘舵€佺殑缂撳瓨锛屼笉瑕佷慨鏀?
     */
    public final MECraftingInventory availableCache;

    public boolean wasSimulated = false;

    public static final class RequestInProcessing {

        public final CraftingRequest request;
        /**
         * 鎸変紭鍏堢骇鎺掑簭
         */
        public final ArrayList<CraftingTask> resolvers = new ArrayList<>(4);
        private boolean isRemainingResolversAllSimulated = true;

        public RequestInProcessing(CraftingRequest request) {
            this.request = request;
        }

        void refresh() {
            isRemainingResolversAllSimulated = isRemainingResolversAllSimulatedSlow();
        }

        public boolean isRemainingResolversAllSimulated() {
            return isRemainingResolversAllSimulated;
        }

        private boolean isRemainingResolversAllSimulatedSlow() {
            for (CraftingTask resolver : resolvers) {
                if (!resolver.isSimulated()) return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return "RequestInProcessing{" + "request=" + request + ", resolvers=" + resolvers + '}';
        }
    }

    private final List<RequestInProcessing> liveRequests = new ArrayList<>(32);
    private final List<CraftingTask> resolvedTasks = new ArrayList<>();
    private final ArrayDeque<CraftingTask> tasksToProcess = new ArrayDeque<>(64);
    private boolean doingWork = false;
    private CraftingTask.State finishedState = CraftingTask.State.FAILURE;
    private final ImmutableMap<IAEStack<?>, ImmutableList<ICraftingPatternDetails>> availablePatterns;
    private final Map<IAEStack<?>, List<ICraftingPatternDetails>> precisePatternCache = new HashMap<>();
    private final Map<ICraftingPatternDetails, IAEItemStack> crafterIconCache = new HashMap<>();
    private final OreListMultiMap<ICraftingPatternDetails> fuzzyPatternCache = new OreListMultiMap<>();
    private final IdentityHashMap<ICraftingPatternDetails, Boolean> isPatternComplexCache = new IdentityHashMap<>();
    private final ClassToInstanceMap<Object> userCaches = MutableClassToInstanceMap.create();

    public CraftingContext(@Nonnull World world, @Nonnull IGrid meGrid, @Nonnull IActionSource actionSource) {
        this.world = world;
        this.meGrid = meGrid;
        this.craftingGrid = meGrid.getCache(ICraftingGrid.class);
        this.actionSource = actionSource;
        final IStorageGrid sg = meGrid.getCache(IStorageGrid.class);
        // 浣跨敤 IStorageMonitorable 鏋勯€犲嚱鏁帮紝璇诲彇鎵€鏈夌被鍨嬶紙鐗╁搧+娴佷綋锛夌殑搴撳瓨
        this.itemModel = new MECraftingInventory(sg, true, false, true);
        this.byproductsInventory = new MECraftingInventory();
        this.availableCache = new MECraftingInventory(sg, false, false, false);
        this.availablePatterns = craftingGrid.getCraftingMultiPatterns();
    }

    /**
     * 鍙敤浜庢彃浠剁殑鑷畾涔夌紦瀛?
     */
    public <T> T getUserCache(Class<T> cacheType, Supplier<T> constructor) {
        T instance = userCaches.getInstance(cacheType);
        if (instance == null) {
            instance = constructor.get();
            userCaches.putInstance(cacheType, instance);
        }
        return instance;
    }

    public void addRequest(@Nonnull CraftingRequest request) {
        if (doingWork) {
            throw new IllegalStateException(
                    "Trying to add requests while inside a CraftingTask handler, return requests in the StepOutput instead");
        }
        final RequestInProcessing processing = new RequestInProcessing(request);
        processing.resolvers.addAll(CraftingCalculations.tryResolveCraftingRequest(request, this));
        processing.refresh();
        Collections.reverse(processing.resolvers);
        liveRequests.add(processing);
        request.liveRequest = processing;
        if (processing.resolvers.isEmpty()) {
            throw new IllegalStateException("No resolvers available for request " + request.toString());
        }
        queueNextTaskOf(processing, true);
    }

    public IAEItemStack getCrafterIconForPattern(@Nonnull ICraftingPatternDetails pattern) {
        return crafterIconCache.computeIfAbsent(pattern, ignored -> AEItemStack.fromItemStack(
                AEApi.instance().definitions().blocks().iface().maybeStack(1).orElse(ItemStack.EMPTY)));
    }

    public List<ICraftingPatternDetails> getPrecisePatternsFor(@Nonnull IAEStack<?> stack) {
        return precisePatternCache.compute(stack, (key, value) -> {
            if (value == null) {
                return availablePatterns.getOrDefault(stack, ImmutableList.of());
            } else {
                return value;
            }
        });
    }

    public List<ICraftingPatternDetails> getFuzzyPatternsFor(@Nonnull IAEStack<?> stack) {
        if (stack instanceof IAEItemStack) {
            IAEItemStack aiStack = (IAEItemStack) stack;
            if (!fuzzyPatternCache.isPopulated()) {
                for (final ImmutableList<ICraftingPatternDetails> patternSet : availablePatterns.values()) {
                    for (final ICraftingPatternDetails pattern : patternSet) {
                        if (pattern.canBeSubstitute()) {
                            for (final IAEStack<?> output : pattern.getAEOutputs()) {
                                if (output instanceof IAEItemStack) {
                                    fuzzyPatternCache.put(((IAEItemStack) output).copy(), pattern);
                                }
                            }
                        }
                    }
                }
                fuzzyPatternCache.freeze();
            }
            return fuzzyPatternCache.get(aiStack);
        } else {
            return getPrecisePatternsFor(stack);
        }
    }

    /**
     * @return 閰嶆柟鏄惁鏈夊鏉傝涓猴紙鍦ㄥ悎鎴愮綉鏍间腑鐣欎笅鐗╁搧锛夛紝闇€瑕侀€愪竴妯℃嫙
     */
    public boolean isPatternComplex(@Nonnull ICraftingPatternDetails pattern) {
        if (!pattern.isCraftable()) {
            return false;
        }
        final Boolean cached = isPatternComplexCache.get(pattern);
        if (cached != null) {
            return cached;
        }

        final IAEStack<?>[] inputs = pattern.getAEInputs();
        // 鍙湁鐗╁搧绫诲瀷鐨勫悎鎴愬彴閰嶆柟鎵嶅彲鑳芥湁澶嶆潅琛屼负
        boolean allItems = true;
        for (IAEStack<?> s : inputs) {
            if (s != null && !(s instanceof IAEItemStack)) {
                allItems = false;
                break;
            }
        }
        if (!allItems) {
            isPatternComplexCache.put(pattern, false);
            return false;
        }

        final IAEItemStack[] itemInputs = new IAEItemStack[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            itemInputs[i] = (IAEItemStack) inputs[i];
        }
        final IAEItemStack[] mcOutputs = simulateComplexCrafting(itemInputs, pattern);

        final boolean isComplex = Arrays.stream(mcOutputs).anyMatch(Objects::nonNull);
        isPatternComplexCache.put(pattern, isComplex);
        return isComplex;
    }

    /**
     * 妯℃嫙鐢ㄥ悎鎴愬彴鍋?1 娆″悎鎴愩€?
     *
     * @param inputSlots 3x3 鍚堟垚鐭╅樀鍐呭
     * @return 鍚堟垚鍚?3x3 鐭╅樀涓墿浣欑殑鐗╁搧
     */
    public IAEItemStack[] simulateComplexCrafting(IAEItemStack[] inputSlots, ICraftingPatternDetails pattern) {
        if (inputSlots.length > 9) {
            throw new IllegalArgumentException(inputSlots.length + " slots supplied to a simulated crafting task");
        }
        final InventoryCrafting simulatedWorkbench = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < inputSlots.length; i++) {
            simulatedWorkbench.setInventorySlotContents(i,
                    inputSlots[i] == null ? ItemStack.EMPTY : inputSlots[i].createItemStack());
        }
        if (world instanceof WorldServer) {
            FMLCommonHandler.instance().firePlayerCraftingEvent(
                    appeng.util.WorldHelper.getPlayer((WorldServer) world),
                    pattern.getOutput(simulatedWorkbench, world),
                    simulatedWorkbench);
        }
        IAEItemStack[] output = new IAEItemStack[9];
        for (int i = 0; i < output.length; i++) {
            ItemStack mcOut = simulatedWorkbench.getStackInSlot(i);
            if (mcOut.isEmpty()) {
                output[i] = null;
                continue;
            }
            ItemStack container = Platform.getContainerItem(mcOut);
            if (!container.isEmpty()) {
                if (container.getCount() <= 0) {
                    output[i] = null;
                } else {
                    output[i] = AEItemStack.fromItemStack(container);
                }
            } else {
                mcOut.shrink(1);
                if (mcOut.isEmpty()) {
                    output[i] = null;
                } else {
                    output[i] = AEItemStack.fromItemStack(mcOut);
                }
            }
        }
        return output;
    }

    /**
     * 鎵ц涓€涓伐浣滃崟鍏冦€?
     *
     * @return 鏄惁闇€瑕佹洿澶氬伐浣?
     */
    public CraftingTask.State doWork() {
        if (tasksToProcess.isEmpty()) {
            return finishedState;
        }
        if (FMLCommonHandler.instance().getSide() == Side.SERVER
                && resolvedTasks.size() > AEConfig.instance().getMaxCraftingSteps()) {
            this.finishedState = State.FAILURE;
            for (CraftingTask task : tasksToProcess) {
                if (task.request != null) {
                    task.request.incomplete = true;
                }
            }
            throw new CraftingStepLimitExceeded();
        }
        final CraftingTask frontTask = tasksToProcess.getFirst();
        if (frontTask.getState() == CraftingTask.State.SUCCESS || frontTask.getState() == CraftingTask.State.FAILURE) {
            resolvedTasks.add(frontTask);
            tasksToProcess.removeFirst();
            return CraftingTask.State.NEEDS_MORE_WORK;
        }
        doingWork = true;
        CraftingTask.StepOutput out = frontTask.calculateOneStep(this);
        CraftingTask.State newState = frontTask.getState();
        doingWork = false;
        if (!out.extraInputsRequired.isEmpty()) {
            final Set<ICraftingPatternDetails> parentPatterns = frontTask.request.patternParents;
            for (int ri = out.extraInputsRequired.size() - 1; ri >= 0; ri--) {
                final CraftingRequest request = out.extraInputsRequired.get(ri);
                request.patternParents.addAll(parentPatterns);
                this.addRequest(request);
            }
        } else if (newState == CraftingTask.State.SUCCESS) {
            if (tasksToProcess.getFirst() != frontTask) {
                throw new IllegalStateException("A crafting task got added to the queue without requesting more work.");
            }
            resolvedTasks.add(frontTask);
            tasksToProcess.removeFirst();
            finishedState = CraftingTask.State.SUCCESS;
        } else if (newState == CraftingTask.State.FAILURE) {
            tasksToProcess.clear();
            finishedState = CraftingTask.State.FAILURE;
            return CraftingTask.State.FAILURE;
        }
        return tasksToProcess.isEmpty() ? CraftingTask.State.SUCCESS : CraftingTask.State.NEEDS_MORE_WORK;
    }

    public List<CraftingTask> getResolvedTasks() {
        return Collections.unmodifiableList(resolvedTasks);
    }

    public List<RequestInProcessing> getLiveRequests() {
        return Collections.unmodifiableList(liveRequests);
    }

    public RequestInProcessing getLiveRequest(CraftingRequest request) {
        return request.liveRequest;
    }

    @Override
    public String toString() {
        final Set<CraftingTask> processed = Collections.newSetFromMap(new IdentityHashMap<>());
        return getResolvedTasks().stream().map(rt -> {
            boolean isNew = processed.add(rt);
            return (isNew ? "  " : "  [duplicate] ") + rt.toString();
        }).collect(Collectors.joining("\n"));
    }

    /**
     * @return 鏄惁鏈変换鍔¤娣诲姞
     */
    private boolean queueNextTaskOf(RequestInProcessing request, boolean addResolverTask) {
        if (request.request.remainingToProcess <= 0 || request.resolvers.isEmpty()) {
            return false;
        }
        CraftingTask nextResolver = request.resolvers.remove(request.resolvers.size() - 1);
        request.refresh();
        if (addResolverTask && !request.resolvers.isEmpty()) {
            tasksToProcess.addFirst(new CheckOtherResolversTask(request));
        }
        if (request.resolvers.isEmpty()) {
            request.resolvers.trimToSize();
        }
        tasksToProcess.addFirst(nextResolver);
        return true;
    }

    /**
     * 鍦ㄦ煇涓?resolver 璁＄畻瀹屾垚鍚庢鏌ユ槸鍚﹂渶瑕佷负鍚屼竴涓姹傜户缁В鏋愮殑鍐呴儴浠诲姟銆?
     */
    private final class CheckOtherResolversTask extends CraftingTask {

        private final RequestInProcessing myRequest;

        public CheckOtherResolversTask(RequestInProcessing myRequest) {
            super(myRequest.request, 0);
            this.myRequest = myRequest;
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            final boolean needsMoreWork = queueNextTaskOf(myRequest, false);
            if (needsMoreWork) {
                this.state = State.NEEDS_MORE_WORK;
            } else if (myRequest.request.remainingToProcess <= 0) {
                this.state = State.SUCCESS;
            } else {
                if (hasConcreteResolversLeft()) {
                    this.state = State.SUCCESS;
                } else {
                    this.state = State.FAILURE;
                }
            }
            return new StepOutput();
        }

        private boolean hasConcreteResolversLeft() {
            for (RequestInProcessing maybeParentRequest : liveRequests) {
                if (request.parentRequests.contains(maybeParentRequest.request)) {
                    if (!maybeParentRequest.isRemainingResolversAllSimulated()) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            return 0;
        }

        @Override
        public void fullRefund(CraftingContext context) {
        }

        @Override
        public void populatePlan(IItemList<IAEStackBase> targetPlan) {
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
        }

        @Override
        public boolean isSimulated() {
            return myRequest.resolvers.stream().allMatch(CraftingTask::isSimulated);
        }

        @Override
        public String toString() {
            return "CheckOtherResolversTask{" + "myRequest="
                    + myRequest
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }
    }
}

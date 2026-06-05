# Applied Energistics 2.5 ModularUI 与 AEKey 体系迁移方案

## 1. 文档目标
两条迁移主线相互关联但可独立推进：

- **ModularUI 迁移**：将客户端 GUI 从旧式大类写法迁移为 MUI 结构化面板体系
- **AEKey 体系迁移**：将数据模型从旧 `IAEStack<T>` / `IItemList<T>` 泛型体系迁移为不可变 `AEKey` + `KeyCounter` 体系

合成系统（CraftingCPUCluster, MECraftingInventory, CraftingV2 resolvers）	~15个	极高
任务1: MECraftingInventory.inventoryMap 迁移
当前内部存储是 Map<IAEStackType<?>, IItemList<?>>，每一层都需要 @SuppressWarnings("unchecked") 原始类型转换。

方案A：KeyCounter 替换（推荐）
将 inventoryMap 改为单个 KeyCounter，参考上游 ListCraftingInventory 的设计。

优点：

消除所有 iterateTyped/addToTypedList/castTypedList 原始类型转换辅助方法
KeyCounter 原生支持异类型（items + fluids 混合存储），不需要按类型分桶
注入/提取变为 kc.add(key, amount) / kc.add(key, -amount)，无需可变 IAEStack 对象
getAvailableKeyCounter() 直接返回副本（无转换开销）
构造函数3（v2）已经是从 IStorageMonitorable.getKeyCounter() 读取，原代码先转成 IItemList 再转回 KeyCounter，删除这个来回转换
commit() 方法不需要改动（它已经使用 KeyCounter 缓存）
上游已证明此方案可行
缺点：

原有"原地修改"模式（extractAny 中对找到的 IAEStack 调用 decStackSize/reset）需要改为 KeyCounter 操作方法
findFuzzyAny 虽然 KeyCounter 有对应方法，但返回类型不同，调用者需要适配
需要为遗留的 IAEStack 调用者添加桥接方法（如 findPreciseInternal 返回 IAEStack 的变体）
6个构造函数每个都需要改动
writeInventory/readInventory 的 NBT 序列化需要新的实现路径
getInventoryMap() 公共访问器需要移除或替换
改动范围： 约30个方法，5个不同的调用文件（CraftingCPUCluster, CraftableItemResolver, ExtractItemResolver, etc.）

方案B：保留 IItemList 但去类型分桶
保留 IItemList 但去掉 Map<IAEStackType<?>, IItemList<?>> 的分桶，改用单个 IItemList<IAEStackBase> 或 IMixedStackList。

优点： 改动较小，findPrecise/findFuzzy 等 IItemList 方法保持不变

缺点：

仍然依赖 IItemList/IAEStack 体系，只是换了容器
原始类型转换仍然需要
没有解决根本问题——只是换了个同样过时的容器
IMixedStackList 本身就内部维护多个类型分桶
推荐：方案A（KeyCounter），因为它与上游一致，是唯一真正消除 IAE 依赖的方案。

任务2: CraftingCPUCluster.finalOutput + injectItems 迁移
当前 finalOutput 是 IAEStack<?>（第107行），injectItems 接受和返回 IAEStack<?>。

方案A：字段改 GenericStack + injectItems 内部逐段迁移（推荐）
将 finalOutput 改为 GenericStack，injectItems 接受/返回 GenericStack。内部逻辑逐段用 GenericStack 重写：

private GenericStack finalOutput;  // 替代 IAEStack<?>

public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
    // 当前 251-361 行的逻辑，但用 GenericStack.amount() / new GenericStack(...) 替代
    // IAEStack.getStackSize() / copy() / setStackSize() / decStackSize() / isSameType()
}
优点：

getFinalMultiOutput() 直接返回 finalOutput 的副本（无 toAEKey() 桥接）
updateCPU() 使用 GenericStack 传递给 TileCraftingMonitorTile.setJob（该项也需要适配）
消除 submitJob 中的 job.getOutput().toIAEStack() 转换
与上游 CraftingCpuLogic.updateOutput(GenericStack) 一致
缺点：

injectItems 方法极其复杂（111行），内部大量使用 IAEStack.copy()/setStackSize()/isSameType()/decStackSize() 等操作，全部改为 GenericStack + AEKey 需要非常仔细
CraftingLink.injectItems() 仍返回 IAEStack<?>，需要适配
postCraftingStatusChange 使用 InterestManager 中的 IAEStack 作为键，需要改为 AEKey
writeToNBT/readFromNBT 的 finalOutput 序列化需要适配 GenericStack
notifyRequester() 中 PacketCraftingToast 需要 IAEItemStack（可桥接）
改动范围： 约15个方法，CraftingGridCache 调用者，CraftingWatcher/InterestManager 键类型

方案B：拆分为 CraftingCPUCluster + CraftingCpuLogic（参考上游）
像上游一样将 1564 行的单体类拆分为：

CraftingCPUCluster（集群管理、边界、协处理器、存储）
CraftingCpuLogic（作业执行、库存、任务处理）
优点：

关注点分离，每个类职责单一
上游已验证的设计
新类从一开始就用 GenericStack/AEKey
可逐步迁移，不破坏旧代码
缺点：

重构范围极大（1564行拆分 + 新类编写）
需要修改所有引用 CraftingCPUCluster 的原有方法
风险高（作业执行是核心逻辑）
推荐：方案A（字段类型迁移）——在当前架构内改类型，风险可控。

任务3: populatePlan 迁移
当前 populatePlan(IItemList<IAEStackBase>) 用 IAEStack 的可变标志（craftable/requestable）在各解析器间传递计划数据。调用链涉及：

ContainerCraftConfirm → ICraftingJob.populatePlan(IItemList)
  → CraftingJobV2.populatePlan
    → 每个 CraftingTask.populatePlan (5个实现)
        → targetPlan.addRequestable(craftedIAEStack)  // 设置 setCraftable(false), setCountRequestable(...)
方案A：CraftingPlan 记录（推荐，参考上游）
public record CraftingPlan(
    GenericStack finalOutput,
    long bytes,
    boolean simulation,
    KeyCounter availableItems,    // 可用存货
    KeyCounter missingItems,      // 缺失
    KeyCounter emittedItems,      // 发射器提供
    Map<ICraftingPatternDetails, Long> patternTimes
) {
    public CraftingPlan merge(CraftingPlan other) { ... }
}
修改接口：

// ICraftingJob
CraftingPlan getPlan();
GenericStack getOutput();

// 移除 populatePlan(IItemList<IAEStackBase>)
优点：

不可变，类型安全
KeyCounter 内部存储，无需 IAEStack 标志位
上游已证明可行（CraftingPlan record）
合并逻辑简单（KeyCounter.addAll）
一次性消除所有解析器中 populatePlan 的 IAEStack 引用
缺点：

需要修改所有5个解析器的 populatePlan 实现 → 改为填充 CraftingPlan
ContainerCraftConfirm 的显示逻辑需要适配（从读 IItemList 改为读 CraftingPlan）
CraftingJobV2.populatePlan 需要完全重写
TickHandler 的 populatePlan 委托也需要更新
改动涉及约15个文件
方案B：简单 KeyCounter 替换
保持 populatePlan 方法，但改为接受 KeyCounter 参数。plan 分三个 KeyCounter：

KeyCounter available — add() 方法添加
KeyCounter craftable — addRequestable() 变为添加到 craftable
KeyCounter requestable — 保持
优点：

接口改动最小（只改参数类型）
解析器改动较简单
不引入新类型
缺点：

三个独立的 KeyCounter 参数，不够内聚
不如 CraftingPlan 记录优雅
KeyCounter 无法表达 setCraftable(false)/setCountRequestable() 等标志
推荐：方案A（CraftingPlan record）——上游已有成熟设计，类型安全，内聚性好。

实施顺序建议
先做 MECraftingInventory.inventoryMap → KeyCounter（基础层，解析器依赖此层）
再做 CraftingCPUCluster.finalOutput + injectItems（依赖 MECraftingInventory）
最后做 populatePlan → CraftingPlan（依赖解析器，解析器依赖前两项）
是否从 MECraftingInventory.inventoryMap 的 KeyCounter 迁移开始？

流体容器交互（drainFromContainer/fillToContainer, 46处调用）	~7个	高
网络序列化（PacketMEInventoryUpdate, PacketTargetStack等）	~6个	高
显示/渲染（AEBasePanel, MUIStackRenderer, ItemRepo等）	~10个	中
getStorageList()（NetworkMonitor, MEMonitorPassThrough, ContainerPatternEncoder）	3个	中
AEStackTypeRegistry（byte ID分配，被AEKeyType桥接调用）	1个	中
IItemContainer/IItemList（ItemModList唯一实现者）	2个	低
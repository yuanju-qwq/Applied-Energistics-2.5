# AEKey 体系迁移完整计划

## 总览

将当前项目的 `IAEStack<T>` 递归泛型体系，迁移到不可变的 `AEKey` 体系。
采用**渐进式并行迁移**策略：先引入 AEKey 作为并行 API，通过桥接层共存，再逐模块迁移。

GuiBridge移除计划

### 设计决策

- **AEKeyType 复用 IAEStackType**：AEKeyType 内部持有 `IAEStackType<?>` 引用做委托，避免重复代码
- **不使用 Forge Registry**：沿用现有 `AEStackTypeRegistry` 的注册机制，AEKeyType 只是去泛型的包装
- **AEKey 新代码使用 PacketBuffer**：`PacketBuffer extends ByteBuf`，提供 `writeVarInt/writeString/writeCompoundTag` 等便利方法，AE2UEL-Extra 也是这样做的；旧代码的 ByteBuf 签名仍然兼容
- **使用 jabel 支持的 record/var/instanceof pattern**：项目已启用 jabel
- 当前项目的AEKEY体系对新增其他类型的支持已经完美了吗？不会还是很麻烦吧，要考虑到原版mc的限制

***

## 阶段 1：引入 AEKey 核心类型（底层）

**目标**：创建 AEKey 体系的核心类，与现有代码并行存在，零修改旧代码
**风险**：低（纯新增文件）
**包路径**：`appeng.api.stacks`

### 任务 1.1 — 创建 `AEKey` 抽象类

- 文件：`src/main/java/appeng/api/stacks/AEKey.java`
- 不可变类型标识基类
- 包含：`getType()`, `toTag()`, `writeToPacket()`, `getPrimaryKey()`, `dropSecondary()`
- 包含：`getFuzzySearchValue()`, `getFuzzySearchMaxValue()`, `fuzzyEquals()`
- 包含：静态方法 `fromTagGeneric()`, `writeKey()`, `readKey()`, `writeOptionalKey()`, `readOptionalKey()`
- 包含：便捷委托方法 `getAmountPerUnit()`, `supportsFuzzyRangeSearch()`
- 包含：`getDisplayName()`, `getModId()`
- **不包含** stackSize、craftable 等可变状态

### 任务 1.2 — 创建 `AEKeyType` 抽象类

- 文件：`src/main/java/appeng/api/stacks/AEKeyType.java`
- 内部持有 `IAEStackType<?>` 引用做委托（方案 2）
- 委托方法：`getId()`, `getAmountPerUnit()`, `transferFactor()`, `getUnitsPerByte()`, `getDisplayName()`, `getDisplayUnit()`, `getColorDefinition()`, `getButtonTexture/U/V()`, `isContainerItemForType()`, `getStackFromContainerItem()`, `drainFromContainer()`, `fillToContainer()`
- 新增方法：`loadKeyFromTag()`, `readFromPacket()`, `getKeyClass()`, `supportsFuzzyRangeSearch()`, `contains()`, `tryCast()`, `filter()`
- 包含：静态方法 `items()`, `fluids()`, `fromRawId()`
- 网络 ID 通过 `AEStackTypeRegistry.getNetworkId()` 获取

### 任务 1.3 — 创建 `AEItemKey` 不可变物品标识

- 文件：`src/main/java/appeng/api/stacks/AEItemKey.java`
- final 类，持有 `Item` + `@Nullable NBTTagCompound`（防御性复制）
- 构造时缓存 hashCode
- 静态工厂：`of(ItemStack)`, `of(Item)`, `of(Item, NBTTagCompound)`
- 转换方法：`toStack()`, `toStack(int count)`, `matches(ItemStack)`
- 序列化：`fromTag()`, `toTag()`, `fromPacket()`, `writeToPacket()`
- Fuzzy：`getFuzzySearchValue()` = damage, `getFuzzySearchMaxValue()` = maxDamage
- 桥接：`toIAEItemStack(long amount)`, `static fromIAEItemStack(IAEItemStack)`

### 任务 1.4 — 创建 `AEFluidKey` 不可变流体标识

- 文件：`src/main/java/appeng/api/stacks/AEFluidKey.java`
- final 类，持有 `Fluid` + `@Nullable NBTTagCompound`（防御性复制）
- 构造时缓存 hashCode
- 静态工厂：`of(Fluid)`, `of(Fluid, NBTTagCompound)`, `of(FluidStack)`
- 转换方法：`toStack(int amount)`, `matches(FluidStack)`
- 序列化：`fromTag()`, `toTag()`, `fromPacket()`, `writeToPacket()`
- 桥接：`toIAEFluidStack(long amount)`, `static fromIAEFluidStack(IAEFluidStack)`

### 任务 1.5 — 创建 `AEItemKeyType` 物品类型描述符

- 文件：`src/main/java/appeng/api/stacks/AEItemKeyType.java`（包可见）
- 继承 `AEKeyType`，委托给 `AEItemStackType.INSTANCE`
- 实现 `loadKeyFromTag()`, `readFromPacket()`, `supportsFuzzyRangeSearch() = true`

### 任务 1.6 — 创建 `AEFluidKeyType` 流体类型描述符

- 文件：`src/main/java/appeng/api/stacks/AEFluidKeyType.java`（包可见）
- 继承 `AEKeyType`，委托给 `AEFluidStackType.INSTANCE`
- 实现 `loadKeyFromTag()`, `readFromPacket()`

### 任务 1.7 — 创建 `GenericStack` record

- 文件：`src/main/java/appeng/api/stacks/GenericStack.java`
- `@Desugar record GenericStack(AEKey what, long amount)`
- 静态方法：`fromItemStack()`, `fromFluidStack()`, `readTag()`, `writeTag()`, `readBuffer()`, `writeBuffer()`
- 桥接：`toIAEStack()`, `static fromIAEStack(IAEStack<?>)`

### 任务 1.8 — 创建 `AEKeyFilter` 函数式接口

- 文件：`src/main/java/appeng/api/storage/AEKeyFilter.java`
- `@FunctionalInterface`, 方法 `boolean matches(AEKey what)`
- 静态方法 `none()` 返回全匹配实例

### 任务 1.9 — 创建 `NoOpKeyFilter` 实现

- 文件：`src/main/java/appeng/api/storage/NoOpKeyFilter.java`
- 包可见，单例 `INSTANCE`，`matches()` 始终返回 `true`

### 任务 1.10 — 验证编译

- 执行 `gradlew compileJava` 确保所有新文件编译通过
- 确认零旧文件被修改

***

## 阶段 2：引入 KeyCounter 存储结构（底层）

**目标**：创建替代 `IItemList` 的非泛型数据结构
**风险**：低（纯新增文件）
**前置**：阶段 1 完成

### 任务 2.1 — 创建 `FuzzySearch` 工具类

- 文件：`src/main/java/appeng/api/stacks/FuzzySearch.java`
- 包可见
- 基于 fastutil 的 AVL Tree Map，支持耐久度范围查询
- `createMap()`, `createMap2Long()`, `findFuzzy()`
- `FuzzyBound` record 用于范围查询

### 任务 2.2 — 创建 `VariantCounter` 抽象类

- 文件：`src/main/java/appeng/api/stacks/VariantCounter.java`
- 包可见
- 按 primaryKey 分桶，每个桶用 Hash 或 AVL Tree（取决于是否支持 fuzzy）
- `UnorderedVariantMap` / `FuzzyVariantMap` 内部类
- 方法：`add()`, `set()`, `get()`, `findFuzzy()`, `removeZeros()`, `copy()`, `invert()`

### 任务 2.3 — 创建 `KeyCounter` 计数器

- 文件：`src/main/java/appeng/api/stacks/KeyCounter.java`
- public，替代 `IItemList` 的非泛型异构计数器
- 按 `AEKey.getPrimaryKey()` 分桶到 `VariantCounter`
- 方法：`add()`, `remove()`, `set()`, `get()`, `findFuzzy()`, `removeZeros()`, `addAll()`, `removeAll()`, `getFirstKey()`, `keySet()`, `size()`, `isEmpty()`, `clear()`, `reset()`
- 实现 `Iterable<Object2LongMap.Entry<AEKey>>`

### 任务 2.4 — 创建 `AmountFormat` 枚举

- 文件：`src/main/java/appeng/api/stacks/AmountFormat.java`
- `FULL`, `PREVIEW_REGULAR`, `PREVIEW_LARGE_FONT`
- 在 `AEKeyType` 上添加 `formatAmount(long, AmountFormat)` 方法

### 任务 2.5 — 验证编译

- 执行 `gradlew compileJava` 确保所有新文件编译通过
- 确认零旧文件被修改

***

## 阶段 3：编写 AEKey ↔ IAEStack 桥接层（底层/功能层）

**目标**：让新旧 API 可以互相转换，为后续迁移铺路
**风险**：中（需要修改少量旧文件添加桥接方法）
**前置**：阶段 1、2 完成

### 任务 3.1 — 在 `IAEStack` 上添加 `toAEKey()` 桥接方法

- 在 `IAEStack` 接口添加 `default AEKey toAEKey()`
- 在 `IAEStackBase` 上添加 `default AEKey toAEKeyBase()` (返回 null，子类重写)

### 任务 3.2 — 在 `AEItemStack` 上实现 `toAEKey()`

- 返回 `AEItemKey.of(getDefinition())`

### 任务 3.3 — 在 `AEFluidStack` 上实现 `toAEKey()`

- 返回 `AEFluidKey.of(getFluid(), getTagCompound())`

### 任务 3.4 — 在 `IAEStack` 上添加 `toGenericStack()` 桥接方法

- `default GenericStack toGenericStack()` { return new GenericStack(toAEKey(), getStackSize()); }

### 任务 3.5 — 在 `GenericStack` 上添加 `toIAEStack()` 静态方法

- `static IAEStack<?> toIAEStack(GenericStack)` — 根据 key 类型转换

### 任务 3.6 — 在 `AEKey` 上添加 `toIAEStack(long amount)` 桥接方法

- `abstract IAEStack<?> toIAEStack(long amount)` — 由子类实现

### 任务 3.7 — 创建 `KeyCounterAdapter` 工具类

- `static KeyCounter fromIItemList(IItemList<?>)` — 将旧列表转换为 KeyCounter
- `static void toIItemList(KeyCounter, IItemList<IAEStackBase>)` — 将 KeyCounter 倒入旧列表

### 任务 3.8 — 验证编译 + 简单单元测试

- 确保 `AEItemKey.of(stack).toIAEItemStack(64)` 往返转换正确
- 确保 `AEFluidKey.of(fluidStack).toIAEFluidStack(1000)` 往返转换正确
- 确保 `KeyCounter.add(key, 64)` → `get(key) == 64`

***

## 阶段 4：逐模块迁移（功能层）— 从叶子节点开始

**目标**：将功能代码从 IAEStack 迁移到 AEKey
**风险**：中到高（每个子任务涉及 5\~15 个文件）
**前置**：阶段 3 完成
**原则**：每完成一个子任务就编译验证，确保不破坏现有功能

### 任务 4.1 — GUI 渲染层

- `StackSizeRenderer` — 使用 `AEKey` + `long amount` 替代 `IAEStackBase`
- `FluidStackSizeRenderer` — 同上
- `AEItemStackRenderer` — 接受 `AEKey`
- `AEFluidStackRenderer` — 接受 `AEKey`
- `FallbackStackRenderer` — 接受 `AEKey`
- `IAEStackTypeRenderer` + `AEStackTypeRendererRegistry` — 注册 AEKey 渲染器
- `TesrRenderHelper` — 使用 `AEKey`

### 任务 4.2 — 终端/容器 GUI

- `ItemRepo` / `FluidRepo` — 内部使用 `KeyCounter` 替代 `IItemList`
- `VirtualMESlot` — 持有 `GenericStack` 而非 `IAEStackBase`
- 终端容器类 — 使用 `AEKey` 做过滤/搜索

### 任务 4.3 — Pattern 系统

- `PatternHelper` — 输入/输出使用 `GenericStack[]`
- `FluidPatternHelper` — 同上
- `SpecialPatternHelper`, `UltimatePatternHelper` — 同上
- `ICraftingPatternDetails` 接口 — 考虑添加 `GenericStack[]` 方法

### 任务 4.4 — IO 总线

- `PartExportBus`, `PartImportBus` — 使用 `AEKey` 做配置
- `PartFluidExportBus`, `PartFluidImportBus` — 同上
- `AbstractPartExportBus`, `AbstractPartImportBus` — 基类迁移

### 任务 4.5 — Storage Bus

- `AbstractPartStorageBus` — 使用 `AEKey` 做过滤
- `PartStorageBus`, `PartFluidStorageBus` — 具体实现

### 任务 4.6 — Level/Rate Emitter

- `PartLevelEmitter` — 使用 `AEKey` 做监控目标
- 相关面板类

### 任务 4.7 — Formation/Annihilation Plane

- `PartFormationPlane`, `PartAnnihilationPlane` — 使用 `AEKey`
- 流体版本

### 任务 4.8 — Interface 系统

- `InterfaceLogic` — 内部使用 `GenericStack[]` 做配置
- `IInterfaceSlotHandler<T>` — 去泛型，改用 `AEKey` 参数
- `InterfaceSlotHandlerRegistry` — 按 `AEKeyType` 注册

### 任务 4.9 — Cell 存储

- `AbstractCellInventory` — 内部使用 `KeyCounter` 替代 `IItemList`
- `BasicCellInventory`, `BasicFluidCellInventory` — 具体实现
- `BasicCellInventoryHandler` — 适配层
- `StorageCellHandler` — 注册表

### 任务 4.10 — Crafting 系统（最复杂）

- `CraftingCPUCluster` — 内部使用 `KeyCounter`
- `MECraftingInventory` — 使用 `KeyCounter`
- `CraftingContext` — 使用 `AEKey` + `GenericStack`
- `CraftingTreeNode`, `CraftingTreeProcess` — 使用 `AEKey`
- v2 合成系统全量迁移

***

## 阶段 5：迁移 ME 网络核心（功能层）

**目标**：将网络层从泛型 IMEInventory 迁移到非泛型
**风险**：高（核心逻辑，影响所有存储交互）
**前置**：阶段 4 大部分完成

### 任务 5.1 — 非泛型化 `IMEInventory`

- `IMEInventory` 去掉泛型参数（或创建新接口 `IMEInventoryV2`）
- 使用 `AEKey` + `long` 替代 `IAEStack<T>`
- `extractItems(AEKey what, long amount, ...)` / `injectItems(AEKey what, long amount, ...)`

### 任务 5.2 — 非泛型化 `IMEMonitor`

- 同上

### 任务 5.3 — 迁移 `NetworkMonitor`, `MEMonitorPassThrough`, `GridStorageCache`

- 统一为非泛型版本

### 任务 5.4 — 迁移 `IStorageMonitorable`

- 从 per-channel `getInventory(IStorageChannel)` 改为统一 `getInventory()`

***

## 阶段 6：清理旧 API（底层）

**目标**：删除过渡期遗留，统一为 AEKey 体系
**风险**：低（此时所有引用已迁移完毕）
**前置**：阶段 5 完成

### 任务 6.1 — 将 `AEKeyType` 的委托内联

- 将 `legacyType` 引用的方法直接实现在 `AEKeyType` 上
- 把 `IAEStackType` 的功能（按钮纹理、容器交互等）移入 `AEKeyType`

### 任务 6.2 — 删除 `IAEStackBase` 接口

### 任务 6.3 — 简化或删除 `IAEStack<T>`

- 如果仍需要可变栈对象（高频合成计算），保留为仅内部使用
- 否则用 `GenericStack` 完全替代

### 任务 6.4 — 删除 `IMixedStackList`, `IAEStackList`

### 任务 6.5 — 删除 `IStorageChannel<T>`

### 任务 6.6 — 合并 `IAEStackType<T>` 到 `AEKeyType`

- 删除 `IAEStackType` 接口
- 删除 `AEStackTypeRegistry`，统一到 `AEKeyType` 的注册机制

### 任务 6.7 — 删除桥接方法

- 删除 `toAEKey()`, `toIAEStack()`, `KeyCounterAdapter` 等过渡代码

***

## 依赖关系图

```
阶段 1 (AEKey 核心)
    ↓
阶段 2 (KeyCounter)
    ↓
阶段 3 (桥接层)
    ↓
阶段 4.1 ~ 4.10 (功能模块，可并行) ← 每个子任务独立编译验证
    ↓
阶段 5 (ME 网络核心)
    ↓
阶段 6 (清理旧 API)
```

## 注意事项

1. **每个阶段完成后都要** **`gradlew compileJava`** **编译验证**
2. **阶段 1\~3 不修改旧文件**（除了阶段 3 添加少量桥接方法）
3. **阶段 4 按子任务分批提交 git**，方便回滚
4. **阶段 4.10（Crafting）和阶段 5（ME Core）是最危险的**，建议单独 git 分支
5. **fastutil 依赖**：Minecraft 1.12.2 自带 fastutil，可直接使用 `Object2LongMap` 等
6. **jabel 支持**：可以使用 `record`（需 `@Desugar`）、`var`、`instanceof pattern matching`
7. **网络兼容性**：AEKey 的网络序列化格式与旧 IAEStack 不同，需要确保客户端服务端同步更新


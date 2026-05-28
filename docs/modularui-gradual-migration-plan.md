# Applied Energistics 2.5 ModularUI 与 AEKey 体系迁移方案

## 1. 文档目标

本文档基于当前仓库代码现状，给出一份可执行的 ModularUI GUI 迁移 + AEKey 数据模型迁移联合方案。

两条迁移主线相互关联但可独立推进：

- **ModularUI 迁移**：将客户端 GUI 从旧式大类写法迁移为 MUI 结构化面板体系
- **AEKey 体系迁移**：将数据模型从旧 `IAEStack<T>` / `IItemList<T>` 泛型体系迁移为不可变 `AEKey` + `KeyCounter` 体系

两条主线的交汇点在于：

- 终端 GUI（`MUIMEMonitorablePanel` 等）既需要 MUI 结构化，也需要将渲染和交互逻辑从 `IAEStack` 切换为 `AEKey` / `GenericStack`
- Container 层的数据传输协议需要逐步从 `IAEStack` 增量更新切换为 `GenericStack` / `KeyCounter` 结构
- Cell 配置库存等逻辑已摆脱 `IStorageChannel<T>`，下一步需要进一步切换到 `AEKeyType` + `AEKeyFilter`

本文档聚焦以下目标：

- 保持现有 AE2 GUI 的功能兼容和交互手感
- 继续沿用当前已经存在的 MUI 迁移基础设施和 AEKey 骨架
- 避免一次性全量重写带来的高风险回归
- 将当前"已迁目录但未完全结构化"的页面收敛为可持续维护的架构
- 将当前"核心骨架已建但业务未全面切换"的 AEKey 体系推向实际接管
- 为后续新增功能提供统一的开发入口和迁移模板

## 2. 当前代码现状

### 2.1 ModularUI 现状

#### 2.1.1 依赖与基础设施

当前项目已经引入 ModularUI：

- `build.gradle`
  - `api("com.cleanroommc:modularui:3.0.7") { transitive = false }`

仓库中已经存在完整的 MUI 目录结构：

- `src/main/java/appeng/client/mui/` — 基础层（AEBasePanel, AEBaseMEPanel, AEMUITheme, AEMUIRegistration）
- `src/main/java/appeng/client/mui/screen/` — 页面层（70+ MUI Panel 类）
- `src/main/java/appeng/client/mui/widgets/` — 控件层（MUITextFieldWidget, MUIScrollBar, MUIButtonWidget 等 15+ 控件）
- `src/main/java/appeng/client/mui/module/` — 模块层（InterfaceListModule, MEItemBrowserModule, PatternEncodingModule）

说明该项目已处于"迁移进行中，需要收敛和规范化"的阶段。

#### 2.1.2 MUI 架构特征

##### 底层

- 服务端容器与同步层仍保留原有 AE2 体系
- 客户端新增了以 `AEBasePanel` 为核心的 MUI 基础面板层
- 页面层已经存在大量 `MUI...Panel` 类（覆盖全部 GUI）
- 控件层同时存在旧 GUI 控件和新的 MUI widgets
- 模块层已经开始出现，说明项目已具备进一步拆分复杂页面的基础

##### 功能层

当前复杂 GUI 虽已迁移到 MUI 目录，但多数页面仍带有旧式实现痕迹：

- `initGui()` 中仍直接创建旧控件
- `setupWidgets()` 还未在所有页面真正承担组件注册职责
- `drawBG()` / `drawFG()` 仍承担较重的业务绘制和 UI 组织逻辑
- 页面类内部仍直接管理搜索、滚动、筛选、动态槽位、按钮映射等复杂状态

### 2.2 AEKey 体系现状

#### 2.2.1 已建立的核心骨架

##### 底层（`appeng.api.stacks` 包）

| 类 / 接口 | 职责 | 状态 |
| --- | --- | --- |
| `AEKey` | 不可变资源标识基类，分离 identity 与 quantity | ✅ 已完成 |
| `AEItemKey` | 物品标识（Item + NBT），cached hashCode | ✅ 已完成 |
| `AEFluidKey` | 流体标识（Fluid + NBT） | ✅ 已完成 |
| `AEKeyType` | 类型描述符，桥接 `IAEStackType` 元数据 | ✅ 已完成 |
| `AEItemKeyType` / `AEFluidKeyType` | 具体类型实例 | ✅ 已完成 |
| `GenericStack` | 不可变 key+amount record，替代 `IAEStack` 的量携带职责 | ✅ 已完成 |
| `KeyCounter` | 异构计数器，替代 `IItemList<T>` | ✅ 已完成 |
| `VariantCounter` | KeyCounter 内部分区索引 | ✅ 已完成 |
| `FuzzySearch` | 模糊匹配工具 | ✅ 已完成 |
| `AmountFormat` | 数量格式化枚举 | ✅ 已完成 |
| `KeyCounterAdapter` | `IItemList ↔ KeyCounter` 双向适配 | ✅ 已完成 |

##### 功能层（桥接与兼容）

| 桥接能力 | 实现方式 | 状态 |
| --- | --- | --- |
| `IAEStack.toAEKey()` | 旧栈转新 Key | ✅ 已在接口中定义 |
| `IAEStack.toGenericStack()` | 旧栈转 GenericStack | ✅ 已在接口中定义 |
| `AEKey.toIAEStack(long)` | 新 Key 反向生成旧栈 | ✅ abstract 已实现 |
| `GenericStack.fromIAEStack()` / `.toIAEStack()` | 双向转换 | ✅ 已完成 |
| `KeyCounterAdapter.fromIItemList()` | IItemList → KeyCounter | ✅ 已完成 |
| `KeyCounterAdapter.toIItemList()` | KeyCounter → IItemList | ✅ 已完成 |
| `AEKeyType.fromLegacyType()` | IAEStackType → AEKeyType | ✅ 已完成 |
| `IStorageChannel<T>` | **已彻底移除** | ✅ 已删除 |

#### 2.2.2 阶段 9.3 已完成成果（IStorageChannel 清理）

以下工作已在上一个开发周期完成：

| 成果 | 详情 |
| --- | --- |
| `IStorageChannel<T>` 接口 | 已删除，所有方法迁移至 `IAEStackType` |
| `IItemStorageChannel` / `IFluidStorageChannel` | 已删除 |
| `CellConfigLegacyWrapper` | 已删除 |
| `IAEStackType.getStorageChannel()` | 已移除（解除与 IStorageChannel 的循环依赖） |
| `IAEStack.getChannel()` | 已移除（由 `getStackType()` 替代） |
| `IStorageCell.getChannel()` | 已移除（`getStackType()` 改为 abstract） |
| `IMEInventory.getChannel()` | 已移除 |
| `MENetworkStorageEvent.channel` | 已移除（由 `stackType` 替代） |
| 6 个 API 接口的 `@Deprecated` default 方法 | 已移除（`IStorageMonitorable`, `ICellRegistry`, `ICellHandler`, `ICellProvider`, `ICellGuiHandler`, `IMEInventory`） |
| 5 个 ME storage 类的 `@Deprecated` 构造函数 | 已移除（`MEMonitorHandler`, `MEPassThrough`, `MEMonitorPassThrough`, `MEInventoryHandler`, `BasicCellInventoryHandler`） |
| `IStorageHelper` 的 channel 注册/查询 API | 已替换为 `getStackTypes()` 方法 |
| `ApiStorage` 内部 channel 实现类 | 已删除 |
| `CondenserVoidInventory` | 已从 `IStorageChannel<T>` 迁移为 `IAEStackType<T>` |
| `StorageHelper.postChanges()` | 已从 `IStorageChannel` 迭代迁移为 `AEStackTypeRegistry.getAllTypes()` 迭代 |
| `ContainerCellWorkbench` / `BasicCellInventory` / `TileChest` / `TileCondenser` | 已从 channel 参数迁移为 `IAEStackType` 参数 |

#### 2.2.3 当前双轨并行状态

##### 底层双轨

- **存储核心**：`IMEInventory<T>`, `IMEMonitor<T>`, `NetworkInventoryHandler<T>` 仍然以 `IAEStack<T>` 为数据载体
- **Cell 系统**：`BasicCellInventory`, `BasicCellInventoryHandler`, `DriveWatcher` 仍然围绕 `IAEStack<T>` 运转
- **网络缓存**：`GridStorageCache`, `NetworkMonitor`, `CraftingGridCache` 内部 list 仍为 `IItemList<T>`
- **类型注册**：`AEStackTypeRegistry` 仍然是枢纽，`AEKeyType` 通过 `fromLegacyType()` 方式桥接

##### 功能层双轨

- **Container 数据传输**：`ContainerMEMonitorable` 使用 `PacketMEInventoryUpdate` 发送 `IAEStack` 列表；客户端 `ItemRepo` 接收 `IAEStack`
- **客户端渲染**：`ItemRepo`, `SlotME`, `VirtualMEMonitorableSlot` 以 `IAEItemStack` / `IAEFluidStack` 为渲染输入
- **配置库存**：`IAEStackInventory` 已切换为 `GenericStack[]` 原生存储，`getAEStackInSlot()` / `putAEStackInSlot()` 已移除
- **Pattern 系统**：`ICraftingPatternDetails` 已全面使用 `GenericStack[]`，8 个 `@Deprecated` 旧方法已移除
- **Pin 系统**：`PinList` / `PinsHandler` 仍使用 `IAEStack<?>` 泛型（待迁移）
- **多类型终端**：`ContainerMEMonitorable.monitors` 已支持多 `IAEStackType` 并行监控

#### 2.2.4 `@Deprecated` 清理状态

✅ **`appeng.api` 包下已无任何 `@Deprecated` 标注**（任务 9.4-9.7 已完成）。

已清理的废弃 API：

| 来源 | 清理内容 | 任务 |
| --- | --- | --- |
| `ICraftingGrid`, `ICraftingCPU`, `ICraftingRequester`, `ICraftingProviderHelper`, `ICraftingWatcherHost` | 移除 9 个 `IAEItemStack` 特定重载 | 9.4 |
| `ICraftingPatternDetails` | 移除 8 个 `@Deprecated` 默认方法（`getAEInputs`/`getInputs` 等） | 9.4 + 9.7 |
| `ICellInventory`, `ICellWorkbenchItem` | 移除 `getConfigInventory()` 废弃委托 | 9.5 |
| `IAEStackInventory` | 移除 `getAEStackInSlot()` / `putAEStackInSlot()` | 9.5 |
| `IMEMonitor` | 移除 `getAvailableItems(IItemList)` 废弃重声明；新增 `getKeyCounter()` 默认方法 | 9.6 |
| `TunnelType` | 移除无参构造函数 | 9.7 |
| `IRecipeLoader`, `IRecipeHandler`, `IIngredient`, `ICraftHandler` | 删除 4 个废弃接口 | 9.7 |
| `IRecipeHandlerRegistry` | 移除 3 个废弃方法 | 9.7 |
| `IInscriberRecipe`, `IInscriberRecipeBuilder` | 移除 4 个废弃默认方法 | 9.7 |

#### 2.2.5 量化数据（更新后）

- `IStorageChannel<T>` 在代码中出现：**0 处**（已完全删除）
- `IAEStack` 在代码中出现约 **820 处**（跨 80+ 文件，较之前减少约 12%）
- `AEKey` / `GenericStack` / `KeyCounter` 在代码中出现约 **290 处**（集中在 28 文件）
- `appeng.api` 包下 `@Deprecated` 标注：**0 处**（已全部清理）
- 比例约为 2.8:1，新体系骨架稳定且旧 API 标记已清理

## 3. 迁移策略

### 3.1 总体策略

采用 **双线渐进、交汇推进** 策略：

- ModularUI 迁移和 AEKey 迁移各自保持独立推进节奏
- 在终端页面、Container 数据层、配置库存三个交汇点协同推进
- 每个阶段都保持完整的向后兼容和功能不回归

不推荐以下方式：

- 一次性全量替换所有 `IAEStack` 引用
- 先追求"纯 AEKey 化"再考虑功能兼容
- 将 AEKey 迁移与 MUI 迁移耦合为一条串行路径

### 3.2 推荐理由

#### 底层

- AEKey 核心骨架已完整，桥接层已就位，可以逐步侵入
- MUI 基础层已稳定运行，页面可以独立于数据层进行结构化
- 双线并行可以让团队根据优先级灵活排期
- `IStorageChannel<T>` 清理工作已完成，消除了一个主要的循环依赖源

#### 功能层

- 终端页面的 MUI 结构化改造天然会触及数据渲染层，此时同步引入 AEKey 是最低成本时机
- Container 层的数据传输协议切换可以独立于 GUI 改造，作为纯后端任务
- 渐进方式可以保持功能稳定，同时逐步降低技术债

## 4. 迁移总目标

### 4.1 ModularUI 目标

#### 底层目标

- 让 `client/mui` 成为新 GUI 的默认入口
- 统一 GUI 生命周期和基础绘制管线
- 将旧控件从"页面直接依赖"收敛为"兼容实现细节"
- 建立可复用的主题、布局和 widget 体系
- 保持对当前 container / sync / slot 体系的兼容

#### 功能层目标

- 保持现有 AE2 GUI 的交互手感和用户习惯
- 优先降低复杂页面的维护成本
- 将搜索、滚动、过滤、动态槽位等复杂行为模块化
- 让未来新增 GUI 不再复制旧式大类写法

### 4.2 AEKey 体系目标

#### 底层目标（更新后）

- ✅ ~~将 `IStorageChannel<T>` 彻底淘汰~~ **已完成**
- ✅ ~~移除 `CellConfigLegacyWrapper`~~ **已完成**
- ✅ ~~清理所有 API 层剩余 `@Deprecated` 标注~~ **已完成**
- 将 `AEKey` / `GenericStack` / `KeyCounter` 确立为数据模型的主线
- 逐步让存储核心（`IMEInventory`, Cell 系统, NetworkMonitor）原生输出 AEKey 结构
- 将 `IAEStack<T>` / `IItemList<T>` 收敛为纯兼容桥接层

#### 功能层目标

- 终端 GUI 的数据输入从 `IAEStack` 切换为 `GenericStack` / `KeyCounter`
- Container 数据传输协议支持 `GenericStack` 序列化
- 配置库存原生支持 `GenericStack`，去除 `IAEStackInventory` 中的 deprecated 存取方法
- Pattern 系统的输入输出原生使用 `GenericStack[]`
- ItemRepo / VirtualSlot 渲染层切换到 AEKey 输入

## 5. 迁移原则

### 5.1 ModularUI 原则

#### 底层原则

- 保留 container、packet、slot、sync 主干，不轻易重写底层协议
- ModularUI 先接管 view 层、生命周期和 widget 注册流程
- 老控件优先通过包装适配，而不是一次性全部删除
- 公共行为统一下沉到基础类、widgets 或 modules 中

#### 功能层原则

- 先整理已经迁入 MUI 的页面，再考虑扩张范围
- 先收敛结构，再优化表现形式
- 对终端类页面优先保持行为一致，不先追求"代码最纯"
- 每迁完一类页面，必须沉淀通用能力，避免下一页重复造轮子

### 5.2 AEKey 体系原则（更新后）

#### 底层原则

- ✅ ~~旧 API（`IStorageChannel`）~~ **已删除**，无需保留兼容
- ✅ ~~API 层全部 `@Deprecated` 标记~~ **已清理**
- `IAEStack` / `IItemList` 仍作为存储核心接口存在（无法完全移除，详见下文）
- 新代码应优先使用 `AEKey` / `GenericStack` / `KeyCounter`，避免引入新的 `IAEStack` 依赖
- 桥接转换必须通过集中的适配器类（`KeyCounterAdapter`, `GenericStack.fromIAEStack`），禁止散落转换
- 每次迁移一个子系统时，旧入口保留为委托，新入口直接操作 AEKey 结构

#### 功能层原则

- 优先迁移数据消费端（GUI 渲染、ItemRepo），再迁移数据生产端（NetworkMonitor、Cell）
- 数据传输层（Packet）作为两端的粘合剂，可独立提供双格式支持
- 配置库存和 Pattern 系统的迁移独立于终端 GUI，可以并行推进
- 每个子系统迁移后必须验证模糊匹配、排序、过滤、craftable 标记等行为不回归

## 6. 页面分类与迁移优先级

### 6.1 第一梯队：已半迁移页面（P0）

优先处理：

- `MUIInterfaceConfigurationTerminalPanel`
- `MUIInterfaceTerminalPanel`
- `MUIMEMonitorablePanel`

原因：

- 已进入 MUI 体系，继续改造的边际成本低
- 具备代表性，适合作为未来页面的模板样板
- 改造后沉淀出的模块可复用范围大
- `MUIMEMonitorablePanel` 是 AEKey 数据消费端的核心汇聚点

### 6.2 第二梯队：标准化配置类页面（P1）

优先特征：

- 布局固定
- 输入和按钮为主
- 动态列表较少
- 风险较低

代表页面：

- `MUIRenamerPanel`, `MUIPriorityPanel`, `MUICraftAmountPanel`
- `MUIOreDictStorageBusPanel`, `MUILevelEmitterPanel`
- `MUIStorageBusPanel`, `MUIFormationPlanePanel`

原因：

- 易于完成模块化模板建设
- 其中 StorageBus、LevelEmitter 等涉及 AEKey 类型过滤，是 AEKey 体系的功能层触点

### 6.3 第三梯队：复杂终端家族页面（P2）

包括但不限于：

- `MUICraftingTermPanel`, `MUIPatternTermPanel`, `MUIExpandedProcessingPatternTermPanel`
- `MUICraftingStatusPanel`, `MUICraftConfirmPanel`, `MUICraftingCPUPanel`
- Wireless 终端变体、PortableCell 变体
- `MUIMACPanel`, `MUISecurityStationPanel`

原因：

- 功能复杂，用户习惯强
- 依赖搜索、排序、虚拟槽位、滚动和快捷交互
- 应在基础设施稳定后再进一步统一

## 7. 可执行迁移任务清单

### 7.1 阶段 0：建立基线 ✅ 已完成大部分

#### 任务 0.1：输出 GUI 页面状态清单

##### 当前状态清单（P0 基线）

| 页面类 | 继承基类 | setupWidgets 状态 | initGui 中旧控件 | 旧 widgets 直接依赖 | 风险 | 优先级 | 当前分类 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MUIInterfaceConfigurationTerminalPanel | AEBasePanel | 已承担搜索框注册 | 否 | GuiScrollbar, GuiImgButton | 中 | P0 | 已完成首轮结构化 |
| MUIInterfaceTerminalPanel | AEBasePanel | 已承担三搜索框注册 | 否 | GuiScrollbar, GuiImgButton | 中 | P0 | 已完成首轮结构化 |
| MUIMEMonitorablePanel | AEBaseMEPanel | 空实现 | 是 | MEGuiTextField, GuiScrollbar, GuiImgButton, GuiTabButton | 高 | P0 | 已迁目录但未完成结构化 |
| MUIRenamerPanel | AEBasePanel | 已承担输入框注册 | 否 | 无 | 低 | P1 | 已完成首轮结构化 |
| MUIOreDictStorageBusPanel | MUIUpgradeablePanel | 已承担输入框注册 | 否（legacy 按钮保留） | GuiImgButton, GuiTabButton | 中 | P1 | 已完成首轮结构化 |
| MEItemBrowserModule | 独立模块 | 不适用 | 否 | GuiScrollbar, GuiImgButton | 低 | P1 | 已完成首轮结构化 |
| InterfaceListModule | 独立模块 | 不适用 | 否 | GuiScrollbar, GuiImgButton | 中 | P0 | 已完成首轮结构化 |
| MUIWirelessPanel | AEBasePanel | 已实现 | 否 | 无 | 低 | P2 | 已完成迁移 |
| MUICondenserPanel | AEBasePanel | 已实现 | 否 | 无 | 低 | P2 | 已完成迁移 |
| MUIChestPanel | AEBasePanel | 已实现 | 否 | 无 | 低 | P2 | 已完成迁移 |

#### 任务 0.2：输出 AEKey 体系接入状态清单

##### AEKey 接入状态清单（更新后）

| 子系统 | 层级 | 当前状态 | 旧 API 残留 | AEKey 接入度 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| `AEKey` / `GenericStack` / `KeyCounter` 核心 | 底层 | 完整可用 | 无 | 100% | — |
| 桥接适配器（`KeyCounterAdapter` 等） | 底层 | 完整可用 | 无 | 100% | — |
| `IStorageChannel<T>` 体系 | 底层 | **已删除** | 无 | 100% | ✅ 完成 |
| `CellConfigLegacyWrapper` | 底层 | **已删除** | 无 | 100% | ✅ 完成 |
| `IAEStackType` 层 | 底层 | 已清理 `getStorageChannel()` | 无 channel 依赖 | 90% | ✅ 完成 |
| `IAEStackInventory`（泛型配置库存） | 底层 | 已支持泛型 `IAEStack<?>` | deprecated get/putAEStackInSlot | 50% | P1 |
| Container 数据传输（`PacketMEInventoryUpdate`） | 功能层 | 仍发送 `IAEStack` | 全量旧 API | 0% | P1 |
| `ItemRepo` 客户端缓存 | 功能层 | 接收 `IAEStack` 并渲染 | 全量旧 API | 0% | P0 |
| `ContainerMEMonitorable.monitors` | 功能层 | 多类型 Monitor 已支持 | `IMEMonitor<T>` 接口 | 30% | P1 |
| Cell 系统（`BasicCellInventory` 等） | 底层 | 纯 `IAEStack<T>` | 全量旧 API | 0% | P2 |
| NetworkMonitor / GridStorageCache | 底层 | 纯 `IItemList<T>` | 全量旧 API | 0% | P2 |
| Pattern 系统 | 功能层 | 已全面使用 `GenericStack[]` | 旧 API 已清理 | 40% | P1 |
| Pin 系统 | 功能层 | 使用 `IAEStack<?>` | 大量旧 API | 10% | P1 |
| Storage Bus / Level Emitter 过滤 | 功能层 | 已使用 `IAEStackInventory` | 无 AEKeyFilter 原生支持 | 20% | P1 |
| Crafting 体系（`ICraftingGrid` 等） | 功能层 | 接口废弃方法已移除 | 实现仍使用 IAEItemStack | 30% | P2 |

#### 任务 0.3：定义迁移完成标准 ✅ 标准已定义

### 7.2 阶段 1：ModularUI 基础层收口

（内容与前版一致，状态：进行中）

### 7.3 阶段 2：AEKey 数据消费端迁移

> 此阶段专注于让客户端（GUI 渲染层）可以原生消费 AEKey 数据，无需等待底层存储引擎全面切换。

（内容与前版一致，状态：待启动）

### 7.4 阶段 3：旧控件兼容层正式化

（内容与前版一致，状态：进行中）

### 7.5 阶段 4：先改已半迁移页面

（内容与前版一致，状态：进行中）

### 7.6 阶段 5：模块化复杂页面能力

（内容与前版一致，状态：待启动）

### 7.7 阶段 6：AEKey 数据生产端迁移

> 此阶段专注于将服务端存储核心从旧体系切换为 AEKey 原生输出。

#### 任务 6.1：Container 层数据传输全面切换

目标：

- `ContainerMEMonitorable` 的数据传输全面使用 `GenericStack` 格式
- 移除旧 `IAEStack` 传输路径

验收标准：

- 旧 `PacketMEInventoryUpdate` 的 `IAEStack` 路径可标记为 `@Deprecated`

#### 任务 6.2：NetworkMonitor 原生输出 KeyCounter

目标：

- `NetworkMonitor` / `GridStorageCache` 内部缓存切换为 `KeyCounter`
- 对外提供 `KeyCounter` 视图作为主接口

验收标准：

- `ContainerMEMonitorable` 可直接从 `NetworkMonitor.getKeyCounter()` 获取数据
- 旧 `getStorageList()` 仍可用但标记为废弃

#### 任务 6.3：Cell 系统原生输出 GenericStack

目标：

- `BasicCellInventory` 内部数据结构从 `IAEStack` 切换为 `GenericStack` / `KeyCounter`
- Cell 的 `getAvailableItems()` 原生返回 `KeyCounter`

验收标准：

- Cell 系统可以原生存储和查询 AEKey
- 旧 API 路径保持功能正确但标记为废弃

### 7.8 阶段 7：配置库存与 Pattern 系统迁移

#### 任务 7.1：配置库存切换为 GenericStack 原生 ✅ 已完成（任务 9.5）

- ✅ `IAEStackInventory` 内部存储已为 `GenericStack[]`；`getAEStackInSlot()` / `putAEStackInSlot()` 已移除
- ✅ `ICellInventory.getConfigInventory()` 废弃委托已收敛（改为 `getConfigAEInventory()`）

#### 任务 7.2：Pattern 系统切换为 GenericStack ✅ 已完成（任务 9.4 + 9.7）

- ✅ `ICraftingPatternDetails` 的输入输出已切换为 `GenericStack[]`，8 个 `@Deprecated` 方法已移除
- ✅ Crafting API 接口（`ICraftingGrid`、`ICraftingCPU`、`ICraftingRequester`、`ICraftingProviderHelper`、`ICraftingWatcherHost`）已清理 9 个废弃方法
- ✅ `FluidPatternHelper` 已删除

#### 任务 7.3：Pin 系统切换为 AEKey

目标：

- `PinList` / `PinsHandler` 内部从 `IAEStack<?>` 切换为 `AEKey`

验收标准：

- Pin 系统可以 pin 物品和流体
- Pin 的匹配逻辑使用 `AEKey.equals()` 而非 `IAEStack.equals()`

### 7.9 阶段 8：新增 GUI 停止走旧路

（内容与前版一致）

### 7.10 阶段 9：按 ROI 回收旧页面与旧 API

#### 任务 9.1：建立迁移评分表

（内容与前版一致）

#### 任务 9.2：优先迁标准化页面

（内容与前版一致）

#### ~~任务 9.3：清理废弃 API~~ ✅ 已完成 IStorageChannel 清理

已完成内容：

- ✅ 移除 `IStorageChannel<T>` 接口（包括 `IItemStorageChannel` / `IFluidStorageChannel`）
- ✅ 移除 `CellConfigLegacyWrapper`
- ✅ 移除 `IAEStackType.getStorageChannel()` / `IAEStack.getChannel()` / `IStorageCell.getChannel()` 等桥接方法
- ✅ 清理 `IStorageHelper` 中的 channel 注册/查询 API，替换为 `getStackTypes()`
- ✅ 清理 `ApiStorage` 中的 channel 内部实现类
- ✅ 清理 6 个 API 接口中的 `@Deprecated` default 方法
- ✅ 清理 5 个 ME storage 类的 `@Deprecated` 构造函数
- ✅ 迁移所有 call site 从 `IStorageChannel` 到 `IAEStackType`

#### ~~任务 9.4：清理 Pattern / Crafting 废弃 API~~ ✅ 已完成

- ✅ 移除 `ICraftingGrid`, `ICraftingCPU`, `ICraftingRequester`, `ICraftingProviderHelper`, `ICraftingWatcherHost` 中的 9 个 `@Deprecated` 方法
- ✅ `ICraftingPatternDetails` 输入输出统一为 `GenericStack[]`
- ✅ `FluidPatternHelper` 已删除；`SpecialPatternHelper` 因含空输出逻辑暂保留
- ✅ `getInputStacks()` / `getOutputStacks()` 成为唯一入口

#### ~~任务 9.5：清理配置库存废弃 API~~ ✅ 已完成

- ✅ `IAEStackInventory` 内部存储已为 `GenericStack[]`，`getAEStackInSlot()` / `putAEStackInSlot()` 已移除
- ✅ `ICellInventory` / `ICellWorkbenchItem` 的 `getConfigInventory()` 废弃委托已收敛

#### ~~任务 9.6：清理 IMEMonitor 废弃 API~~ ✅ 已完成

- ✅ `IMEMonitor` 中 `getAvailableItems(IItemList)` 废弃重声明已移除
- ✅ 新增 `getKeyCounter()` 默认方法

#### ~~任务 9.7：清理剩余 @Deprecated 标注~~ ✅ 已完成

- ✅ `appeng.api` 包下已无任何 `@Deprecated` 标注
- ✅ 移除 4 个废弃 Recipe 接口、TunnelType 无参构造、IRecipeHandlerRegistry 3 个方法、IInscriberRecipe/IInscriberRecipeBuilder 4 个方法

## 8. 双线交汇点详解

### 8.1 交汇点 A：终端 GUI 数据渲染

| 维度 | MUI 侧 | AEKey 侧 | 联合效果 |
| --- | --- | --- | --- |
| 数据输入 | `MUIMEMonitorablePanel` 结构化 | `ItemRepo` 切换为 `KeyCounter` | 终端可以统一渲染物品+流体 |
| 虚拟槽位 | 动态槽位模块化 | `VirtualSlot` 接受 `GenericStack` | 一套 slot 管理代码支持所有类型 |
| 类型切换 | 工具栏模块化 | `AEKeyType.filter()` | 按钮切换逻辑统一（已无 `IStorageChannel` 依赖） |
| 搜索过滤 | 搜索栏模块化 | `AEKey.getDisplayName()` | 搜索不再区分栈类型 |

### 8.2 交汇点 B：Container 数据传输

| 维度 | MUI 侧 | AEKey 侧 | 联合效果 |
| --- | --- | --- | --- |
| 数据格式 | GUI 接收层解耦 | `GenericStack` 序列化 | 传输协议与 GUI 实现解耦 |
| 多类型支持 | 页面无需区分频道 | `ContainerMEMonitorable` 统一发送 | 一个终端看到所有类型 |

### 8.3 交汇点 C：配置库存

| 维度 | MUI 侧 | AEKey 侧 | 联合效果 |
| --- | --- | --- | --- |
| GUI 交互 | 配置 slot 用 MUI widget 渲染 | slot 持有 `GenericStack` | 配置界面原生支持多类型 |
| 序列化 | 无影响 | `GenericStack.writeTag()` | NBT 格式统一 |

## 9. 风险与缓解

| 风险 | 影响 | 缓解措施 |
| --- | --- | --- |
| AEKey 迁移导致存储数据格式不兼容 | 玩家丢失存储数据 | Cell 内部序列化保持旧格式读取能力；新格式写入后旧版无法读取需版本号保护 |
| Container 协议变更导致网络不兼容 | 客户端/服务端混版崩溃 | Packet 添加版本标记；客户端兼容双格式；服务端按客户端版本选择格式 |
| MUI 结构化改造引入 GUI 回归 | 用户体验退化 | 逐页面改造+手动验证；保留旧 GUI 代码直到新页面验证通过 |
| 双线并行推进导致冲突 | 代码合并困难 | 明确文件边界：MUI 改 `client/mui/**`，AEKey 改 `api/stacks/**` + `container/**`；交汇点统一协调 |
| 桥接层性能开销 | 大型网络性能退化 | `KeyCounterAdapter` 转换只在必要时执行；优先迁移热路径 |

## 10. 依赖关系与推荐执行顺序

```
阶段 0: 建立基线 ✅
    ├── 任务 9.3: IStorageChannel 清理 ✅ 已完成
    │
    ├── 阶段 1: MUI 基础层收口
    │       ↓
    │   阶段 3: 旧控件兼容层
    │       ↓
    │   阶段 4: 半迁移页面改造 ──────────────┐
    │       ↓                                │
    │   阶段 5: 模块化复杂页面能力            │
    │                                        │
    ├── 阶段 2: AEKey 数据消费端 ─────────────┘
    │       ↓                     (交汇: MUIMEMonitorablePanel)
    │   阶段 6: AEKey 数据生产端
    │       ↓
    │   阶段 7: 配置库存与 Pattern
    │
    ├── 阶段 8: 新增 GUI 停止走旧路（双线共用）
    │
    ├── ~~任务 9.4: Pattern / Crafting 废弃 API 清理~~ ✅ 已完成
    ├── ~~任务 9.5: 配置库存废弃 API 清理~~ ✅ 已完成
    ├── ~~任务 9.6: IMEMonitor 废弃 API 清理~~ ✅ 已完成
    │
    └── ~~任务 9.7: 最终 @Deprecated 清理~~ ✅ 已完成
```

关键依赖：

- 阶段 2 可以与阶段 1/3 并行
- 阶段 4.3（MUIMEMonitorablePanel）依赖阶段 2 的 ItemRepo 切换
- 阶段 6 依赖阶段 2 完成（客户端能接收新格式后才能切换服务端发送格式）
- 阶段 7 独立于 GUI 迁移，可以在任意时间点推进
- ✅ ~~任务 9.3-9.7 全部已完成~~ **API 层 @Deprecated 已全部清理**

## 11. 附录：IStorageChannel 清理变更记录

以下为任务 9.3 完成的完整变更记录，供后续迁移参考。

### 已删除文件

| 文件 | 说明 |
| --- | --- |
| `src/main/java/appeng/api/storage/IStorageChannel.java` | 旧 channel 接口 |
| `src/main/java/appeng/api/storage/channels/IItemStorageChannel.java` | 物品 channel 子接口 |
| `src/main/java/appeng/api/storage/channels/IFluidStorageChannel.java` | 流体 channel 子接口 |
| `src/main/java/appeng/items/contents/CellConfigLegacyWrapper.java` | Cell 配置 Legacy 包装器 |

### 已修改的 API 接口

| 文件 | 变更 |
| --- | --- |
| `IAEStackType.java` | 移除 `getStorageChannel()` 方法及 IStorageChannel import |
| `IAEStack.java` | 移除 `getChannel()` 方法及 IStorageChannel import |
| `IStorageCell.java` | 移除 deprecated `getChannel()`；`getStackType()` 改为 abstract |
| `IMEInventory.java` | 移除 deprecated `getChannel()` default 方法 |
| `IStorageMonitorable.java` | 移除 deprecated `getInventory(IStorageChannel)` |
| `ICellRegistry.java` | 移除 2 个 deprecated default 方法 |
| `ICellHandler.java` | 移除 deprecated `getCellInventory(... IStorageChannel)` |
| `ICellProvider.java` | 移除 deprecated `getCellArray(IStorageChannel)` |
| `ICellGuiHandler.java` | 移除 2 个 deprecated default 方法 |
| `IStorageHelper.java` | 移除 channel 注册/查询方法；新增 `getStackTypes()` |
| `MENetworkStorageEvent.java` | 移除 deprecated `channel` 字段 |
| `IItemList.java` | 移除 IStorageChannel import 和旧 Javadoc |
| `IAEItemStack.java` | 更新 Javadoc（移除 channel 引用） |
| `IAEFluidStack.java` | 更新 Javadoc（移除 channel 引用） |

### 已修改的实现类

| 文件 | 变更 |
| --- | --- |
| `ApiStorage.java` | 移除 channel 注册表及内部实现类；实现 `getStackTypes()` |
| `AEItemStackType.java` | 移除 `getStorageChannel()` 实现 |
| `AEFluidStackType.java` | 移除 `getStorageChannel()` 实现 |
| `AEItemStack.java` | 移除 `getChannel()` 实现 |
| `AEFluidStack.java` | 移除 `getChannel()` 实现 |
| `MEMonitorHandler.java` | 移除 deprecated IStorageChannel 构造函数 |
| `MEPassThrough.java` | 移除 deprecated IStorageChannel 构造函数 |
| `MEMonitorPassThrough.java` | 移除 deprecated IStorageChannel 构造函数 |
| `MEInventoryHandler.java` | 移除 deprecated IStorageChannel 构造函数 |
| `BasicCellInventoryHandler.java` | 移除 deprecated IStorageChannel 构造函数 |
| `CondenserVoidInventory.java` | 从 IStorageChannel 迁移为 IAEStackType |
| `StorageHelper.java` | `postChanges()` 迭代 `AEStackTypeRegistry.getAllTypes()` |
| `ContainerCellWorkbench.java` | 从 `getStorageChannel()` 调用改为直接使用 `IAEStackType` |
| `BasicCellInventory.java` | `isCellOfType()` 参数从 IStorageChannel 改为 IAEStackType |
| `TileChest.java` | 迭代从 `storageChannels()` 改为 `getStackTypes()` |
| `TileCondenser.java` | 移除 `.getStorageChannel()` 调用 |
| `AbstractStorageCell.java` | 新增 abstract `getStackType()` |
| `ToolColorApplicator.java` | 所有 `getChannel()` 调用改为 `getStackType()` |

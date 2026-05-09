# Applied Energistics 2.5 ModularUI 与 AEKey 体系迁移方案

## 1. 文档目标

本文档基于当前仓库代码现状，给出一份可执行的 ModularUI GUI 迁移 + AEKey 数据模型迁移联合方案。

两条迁移主线相互关联但可独立推进：

- **ModularUI 迁移**：将客户端 GUI 从旧式大类写法迁移为 MUI 结构化面板体系
- **AEKey 体系迁移**：将数据模型从旧 `IAEStack<T>` / `IItemList<T>` 泛型体系迁移为不可变 `AEKey` + `KeyCounter` 体系

两条主线的交汇点在于：

- 终端 GUI（`MUIMEMonitorablePanel` 等）既需要 MUI 结构化，也需要将渲染和交互逻辑从 `IAEStack` 切换为 `AEKey` / `GenericStack`
- Container 层的数据传输协议需要逐步从 `IAEStack` 增量更新切换为 `GenericStack` / `KeyCounter` 结构
- 存储单元过滤、配置库存等逻辑需要从 `IStorageChannel<T>` 切换为 `AEKeyType` + `AEKeyFilter`

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
| `IStorageChannel` | 已标记 `@Deprecated`，委托到 `IAEStackType` | ✅ 兼容保留 |

#### 2.2.2 当前双轨并行状态

##### 底层双轨

- **存储核心**：`IMEInventory<T>`, `IMEMonitor<T>`, `NetworkInventoryHandler<T>` 仍然以 `IAEStack<T>` 为数据载体
- **Cell 系统**：`BasicCellInventory`, `BasicCellInventoryHandler`, `DriveWatcher` 仍然围绕 `IAEStack<T>` 运转
- **网络缓存**：`GridStorageCache`, `NetworkMonitor`, `CraftingGridCache` 内部 list 仍为 `IItemList<T>`
- **类型注册**：`AEStackTypeRegistry` 仍然是枢纽，`AEKeyType` 通过委托方式桥接

##### 功能层双轨

- **Container 数据传输**：`ContainerMEMonitorable` 使用 `PacketMEInventoryUpdate` 发送 `IAEStack` 列表；客户端 `ItemRepo` 接收 `IAEStack`
- **客户端渲染**：`ItemRepo`, `SlotME`, `VirtualMEMonitorableSlot` 以 `IAEItemStack` / `IAEFluidStack` 为渲染输入
- **配置库存**：`IAEStackInventory` 已支持泛型栈存储，但 Cell Config 多数通过 `CellConfigLegacyWrapper` 兼容
- **Pattern 系统**：`ICraftingPatternDetails.getAEOutputs()` 返回 `IAEStack<?>[]`，已提供 `@Deprecated` 旧方法
- **Pin 系统**：`PinList` / `PinsHandler` 已使用 `IAEStack<?>` 泛型
- **多类型终端**：`ContainerMEMonitorable.monitors` 已支持多 `IAEStackType` 并行监控

#### 2.2.3 量化数据

- `IAEStack` 在代码中出现约 **922 处**（跨 100+ 文件）
- `AEKey` / `GenericStack` / `KeyCounter` 在代码中出现约 **236 处**（集中在 22 文件）
- 比例约为 4:1，说明新体系已建立骨架但业务层远未全面切换

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

#### 底层目标

- 将 `AEKey` / `GenericStack` / `KeyCounter` 确立为数据模型的主线
- 逐步让存储核心（`IMEInventory`, Cell 系统, NetworkMonitor）原生输出 AEKey 结构
- 将 `IAEStack<T>` / `IItemList<T>` 收敛为纯兼容桥接层
- 将 `IStorageChannel<T>` 彻底淘汰为废弃 API

#### 功能层目标

- 终端 GUI 的数据输入从 `IAEStack` 切换为 `GenericStack` / `KeyCounter`
- Container 数据传输协议支持 `GenericStack` 序列化
- 配置库存原生支持 `GenericStack`，去除 `CellConfigLegacyWrapper`
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

### 5.2 AEKey 体系原则

#### 底层原则

- 旧 API（`IAEStack`, `IItemList`, `IStorageChannel`）保持编译兼容但标记 `@Deprecated`
- 新代码只允许使用 `AEKey` / `GenericStack` / `KeyCounter`，不允许引入新的 `IAEStack` 依赖
- 桥接转换必须通过集中的适配器类（`KeyCounterAdapter`, `GenericStack.fromIAEStack`），禁止散落转换
- 每次迁移一个子系统时，旧入口保留为 `@Deprecated` 委托，新入口直接操作 AEKey 结构

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

### 7.1 阶段 0：建立基线

#### 任务 0.1：输出 GUI 页面状态清单

目标：

- 标记当前 GUI 页面属于以下哪一类：
  - 已完成迁移
  - 已迁目录但未完成结构化
  - 未迁移

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

目标：

- 标记当前各子系统对 AEKey 体系的接入程度

##### AEKey 接入状态清单

| 子系统 | 层级 | 当前状态 | 旧 API 残留 | AEKey 接入度 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| `AEKey` / `GenericStack` / `KeyCounter` 核心 | 底层 | 完整可用 | 无 | 100% | — |
| 桥接适配器（`KeyCounterAdapter` 等） | 底层 | 完整可用 | 无 | 100% | — |
| `IAEStackInventory`（泛型配置库存） | 底层 | 已支持泛型 `IAEStack<?>` | 无直接 AEKey 存储 | 50% | P1 |
| Container 数据传输（`PacketMEInventoryUpdate`） | 功能层 | 仍发送 `IAEStack` | 全量旧 API | 0% | P1 |
| `ItemRepo` 客户端缓存 | 功能层 | 接收 `IAEStack` 并渲染 | 全量旧 API | 0% | P0 |
| `ContainerMEMonitorable.monitors` | 功能层 | 多类型 Monitor 已支持 | `IMEMonitor<T>` 接口 | 30% | P1 |
| Cell 系统（`BasicCellInventory` 等） | 底层 | 纯 `IAEStack<T>` | 全量旧 API | 0% | P2 |
| NetworkMonitor / GridStorageCache | 底层 | 纯 `IItemList<T>` | 全量旧 API | 0% | P2 |
| Pattern 系统 | 功能层 | `getAEOutputs()` 返回 `IAEStack<?>[]` | 大量旧 API | 10% | P1 |
| Pin 系统 | 功能层 | 使用 `IAEStack<?>` | 大量旧 API | 10% | P1 |
| Storage Bus / Level Emitter 过滤 | 功能层 | 已使用 `IAEStackInventory` | 无 AEKeyFilter 原生支持 | 20% | P1 |

#### 任务 0.3：定义迁移完成标准

##### ModularUI 完成标准

###### 底层标准

- 页面以 `AEBasePanel` / `AEBaseMEPanel` 作为唯一 GUI 生命周期入口
- `initGui()` 只负责尺寸计算、坐标刷新、滚动范围和 slot 位移等布局刷新职责
- `setupWidgets()` 负责创建并注册输入控件、按钮包装控件和其他 MUI widgets
- 页面不再直接 `new MEGuiTextField(...)`
- 旧控件若仍保留，必须通过 MUI widgets 或 module 统一封装后接入

###### 功能标准

- 搜索、tooltip、清空、焦点等输入行为由统一文本输入包装层承载
- 滚动条、按钮组、动态列表、动态槽位中至少一个复杂区域被结构化收口
- `drawBG()` 仅负责底板和区域背景绘制，`drawFG()` 仅负责标题、tooltip 和轻量前景叠加
- 页面保留原有交互手感，不引入搜索、定位、快捷键、ghost 拖放等行为回归
- 页面内新增的实现可以被同类页面复用，而不是只服务单页临时代码

##### AEKey 完成标准

###### 底层标准

- 新代码中不出现 `IAEStack` / `IItemList` 的新引入
- 被迁移子系统的主入口接受 / 返回 `AEKey` / `GenericStack` / `KeyCounter`
- 旧入口保留为 `@Deprecated` 并委托到新入口
- 序列化格式支持 `GenericStack` 的 NBT / Packet 编解码

###### 功能标准

- 所有资源类型（物品、流体）在已迁移子系统中享有等同待遇
- 模糊匹配、排序、过滤、craftable 标记行为不回归
- 已迁移子系统不再需要 `IStorageChannel<T>` 类型参数

验收标准：

- 所有后续页面和子系统都按同一标准评估是否"迁移完成"
- 同时满足底层标准和功能标准，才可以标记为"已完成迁移"

### 7.2 阶段 1：ModularUI 基础层收口

#### 任务 1.1：规范 AEBasePanel 生命周期

目标：

- 明确 `AEBasePanel` 及其子类的方法职责边界

##### 生命周期规范

###### 底层职责

- `initGui()`：完成 MC 原生初始化、重建 `SlotME`、清理旧 widgets、执行 `setupWidgets()`，随后由子类补充布局刷新
- `setupWidgets()`：只负责 widget 创建与注册，不承担依赖实时屏幕尺寸的布局重算
- `drawGuiContainerBackgroundLayer()`：统一执行背景贴图、`drawBG()`、slot 背景和 MUI widget 背景层
- `drawGuiContainerForegroundLayer()`：统一执行滚动条、标题、`drawFG()` 和 MUI widget 前景层

###### 功能职责

- `drawBG()`：绘制底板、分区背景、列表底图、输入框背景承载区等稳定背景内容
- `drawFG()`：绘制标题、轻量 overlay、局部提示文本，不再负责组件注册
- `mouseClicked()` / `keyTyped()`：优先分发给已注册 widget，再处理页面特有输入
- 子类若需要重复布局能力，应优先抽到基础类或 module，而不是在多个页面复制实现

验收标准：

- 至少两个复杂页面按照该规范完成改造
- `MUIInterfaceConfigurationTerminalPanel` 已作为第一份样板开始执行该规范

#### 任务 1.2：下沉重复布局逻辑

目标：

- 将页面中重复出现的布局和定位逻辑抽出为基础方法

重点提炼内容：

- 滚动条位置与范围设置
- 搜索框定位
- 玩家背包区域位移
- AppEngSlot 的重定位模板

验收标准：

- 多个页面不再重复实现同类布局代码

#### 任务 1.3：统一主题入口

目标：

- 将背景纹理、颜色状态、基础皮肤入口统一收口

重点：

- 页面不直接分散定义风格常量
- 主题逻辑统一从基础层注入

验收标准：

- 终端类和配置类页面可以共享主题入口

### 7.3 阶段 2：AEKey 数据消费端迁移

> 此阶段专注于让客户端（GUI 渲染层）可以原生消费 AEKey 数据，无需等待底层存储引擎全面切换。

#### 任务 2.1：引入 GenericStack 传输协议

目标：

- 在 `PacketMEInventoryUpdate` 中新增 `GenericStack` 序列化格式支持

策略：

- 新增 packet 版本标记或并行 packet 类
- 服务端优先尝试发送 `GenericStack` 格式
- 客户端兼容接收两种格式（渐进期）
- 旧 packet 格式保留为 fallback 直到完全切换

验收标准：

- 客户端可以接收并正确解析 `GenericStack` 列表
- 旧客户端不会因新 packet 格式崩溃

#### 任务 2.2：ItemRepo 切换为 AEKey 输入

目标：

- 将 `ItemRepo`（客户端物品缓存和排序引擎）的内部数据模型从 `IAEItemStack` 切换为 `AEKey` + amount

改造点：

- 内部缓存从 `IItemList<IAEItemStack>` 切换为 `KeyCounter` 或 `List<GenericStack>`
- 排序比较器接受 `AEKey` 而非 `IAEItemStack`
- 模糊过滤使用 `AEKey.fuzzyEquals()` 和 `KeyCounter.findFuzzy()`
- 对外提供 `GenericStack` 视图供 VirtualSlot 渲染使用

验收标准：

- 终端中物品和流体可以共存于同一 ItemRepo
- 排序、搜索、类型过滤行为不回归

#### 任务 2.3：VirtualSlot 渲染层适配

目标：

- 将 `VirtualMEMonitorableSlot` / `SlotME` 的渲染输入从 `IAEStack` 切换为 `GenericStack`

改造点：

- Slot 持有 `GenericStack` 引用而非 `IAEItemStack`
- 渲染逻辑通过 `AEKey.asItemStackRepresentation()` 获取显示 ItemStack
- 数量格式化通过 `AEKeyType.formatAmount()` 获取显示文本

验收标准：

- 物品和流体在终端中正确渲染
- 数量显示格式保持一致

### 7.4 阶段 3：旧控件兼容层正式化

#### 任务 3.1：包装文本输入控件

目标：

- 将旧 `MEGuiTextField` 封装为 MUI 侧统一可复用组件

##### 当前落地策略

###### 底层策略

- 继续沿用现有 `MUITextFieldWidget` 作为唯一 MUI 文本输入入口
- 在该控件上补齐文本变化监听、tooltip、右键清空、位置更新和绝对/相对坐标兼容能力
- 页面逐步从直接持有 `MEGuiTextField` 迁移为持有 `MUITextFieldWidget`

###### 功能策略

- 先覆盖搜索框这类高频场景
- 保持原有空格输入、右键清空、焦点切换和 tooltip 行为
- 后续再逐步吸收 validator、selection、placeholder 等剩余能力

验收标准：

- 页面不再直接 `new MEGuiTextField(...)`
- `MUIInterfaceConfigurationTerminalPanel` 已切换到 `MUITextFieldWidget`

#### 任务 3.2：包装按钮控件

目标：

- 将旧按钮控件统一包装为 MUI 侧按钮抽象

重点对象：

- `GuiImgButton`
- `GuiToggleButton`
- `GuiTabButton`

验收标准：

- 页面层对旧按钮类型的直接依赖显著减少

#### 任务 3.3：包装滚动条控件

目标：

- 统一滚动条的创建、范围设置、页高设置和滚轮分发逻辑

重点对象：

- `GuiScrollbar`

验收标准：

- 页面不再散写重复的滚动条初始化代码

### 7.5 阶段 4：先改已半迁移页面

#### 任务 4.1：重构 MUIInterfaceConfigurationTerminalPanel

##### 当前落地结果

###### 底层改造

- 搜索框创建已迁入 `setupWidgets()`
- `initGui()` 仅保留键盘重复输入开关、滚动条布局和 slot 重定位
- 页面字段已从 `MEGuiTextField` 切换为 `MUITextFieldWidget`
- 搜索框构造已切换到统一工厂 `MUITextFieldWidget.addSearchField(...)`

###### 功能改造

- 搜索框保留文本变化刷新列表行为
- tooltip 保留为 `Inputs OR names`
- 右键清空行为由统一文本输入包装层承载
- 搜索逻辑与 JEI ghost 拖放逻辑保持原状

验收标准：

- 该页面成为"半迁移页面收口"的第一份样板
- 已满足 P0 阶段的页面收口目标

#### 任务 4.2：重构 MUIInterfaceTerminalPanel

##### 当前落地结果

###### 底层改造

- 三个搜索框已迁入 `setupWidgets()`
- `initGui()` 不再创建 legacy 搜索框，仅保留行数计算、布局刷新、滚动条和 slot 重定位
- 页面字段已从 `MEGuiTooltipTextField` 切换为 `MUITextFieldWidget`
- 三联搜索框构造已切换到 `MUITextFieldWidget.SearchFieldGroup`
- 搜索框注册已切换到 `MUITextFieldWidget.addSearchFieldGroup(...)`

###### 功能改造

- 输入 / 输出 / 名称 三搜索框保留原有筛选行为
- tooltip、右键清空和文本变化刷新已收口到统一文本输入包装层
- Tab 焦点轮转、空格拦截和筛选刷新行为保持原状
- 过滤按钮、terminal style 切换和高亮定位逻辑保持兼容

验收标准：

- 终端按钮组和搜索区进入可复用结构

#### 任务 4.3：重构 MUIMEMonitorablePanel（MUI + AEKey 联合改造）

当前问题：

- 职责过重：搜索、排序、类型切换、pin 系统、终端按钮等均集中在一个类中
- 数据输入仍为 `IAEItemStack`（通过 ItemRepo 和 SlotME）
- `setupWidgets()` 为空实现

目标（MUI 侧）：

- 将大型终端基类拆分为多个可组合模块
- 保留终端滚动、搜索、虚拟槽位和快捷键行为

目标（AEKey 侧）：

- 切换 ItemRepo 输入为 `GenericStack`
- 切换 VirtualSlot 渲染为 AEKey 驱动
- 类型过滤按钮使用 `AEKeyType.filter()` 而非 `IStorageChannel` 判断

验收标准：

- `MUIMEMonitorablePanel` 成为稳定的终端基类，不再继续膨胀
- 终端可以同时显示物品和流体（如果 monitor 中包含两种类型）

### 7.6 阶段 5：模块化复杂页面能力

#### 任务 5.1：建立搜索栏模块

##### 当前落地结果

###### 底层改造

- 已建立 `MUITextFieldWidget.SearchFieldSpec` 作为单搜索框构建参数
- 已建立 `MUITextFieldWidget.SearchFieldGroup` 作为三联搜索框建模
- 已建立 `MUITextFieldWidget.addSearchField(...)` 与 `addSearchFieldGroup(...)` 两级注册入口
- Interface Terminal 家族页面和模块已经开始复用同一套搜索框构造模型

###### 功能改造

- `MUIInterfaceConfigurationTerminalPanel` 已切换到单搜索框统一工厂
- `MUIInterfaceTerminalPanel` 已切换到三联搜索框统一组装
- `InterfaceListModule` 已切换到三联搜索框统一组装
- 三联搜索框的输入 / 输出 / 名称职责划分已统一

验收标准：

- 页面不再直接维护搜索框行为细节

#### 任务 5.2：建立终端工具栏模块

能力范围：

- 排序按钮
- 显示模式按钮
- 搜索模式按钮
- 终端样式按钮
- 过滤开关按钮
- **类型切换按钮**（物品/流体/全部 — 基于 `AEKeyType`）

验收标准：

- 同类页面共享统一工具栏实现
- 类型切换按钮使用 `AEKeyType.filter()` 而非硬编码 channel 判断

#### 任务 5.3：建立动态列表 / 动态槽位模块

能力范围：

- 可见区计算
- 动态槽位刷新
- 滚动同步
- 匹配高亮
- hover / disabled overlay
- **支持 GenericStack 输入**（物品和流体槽位统一处理）

验收标准：

- 页面不再混合书写列表遍历、槽位创建和绘制细节
- 模块同时支持物品和流体的槽位渲染

#### 任务 5.4：建立高亮定位模块

能力范围：

- 方块高亮
- 跨维度判断
- 定位提示
- 高亮按钮行为

验收标准：

- 定位相关逻辑不再散落在多个终端页面内

### 7.7 阶段 6：AEKey 数据生产端迁移

> 此阶段专注于将服务端存储核心从旧体系切换为 AEKey 原生输出。

#### 任务 6.1：Container 层数据传输全面切换

目标：

- `ContainerMEMonitorable` 的数据传输全面使用 `GenericStack` 格式
- 移除旧 `IAEStack` 传输路径

改造点：

- `detectAndSendChanges()` 中增量更新改为发送 `GenericStack` 列表
- `queueInventory()` 全量发送改为 `GenericStack` 批量序列化
- 客户端 `handleUpdateQueue()` 接收 `GenericStack` 并更新 `KeyCounter` / ItemRepo

验收标准：

- 旧 `PacketMEInventoryUpdate` 的 `IAEStack` 路径可标记为 `@Deprecated`

#### 任务 6.2：NetworkMonitor 原生输出 KeyCounter

目标：

- `NetworkMonitor` / `GridStorageCache` 内部缓存切换为 `KeyCounter`
- 对外提供 `KeyCounter` 视图作为主接口

策略：

- `NetworkMonitor` 内部维护 `KeyCounter` 作为主缓存
- 旧 `getStorageList()` 返回的 `IItemList` 通过 `KeyCounterAdapter.toIItemList()` 按需生成（`@Deprecated`）
- 新方法 `getKeyCounter()` 直接返回内部 `KeyCounter`

验收标准：

- `ContainerMEMonitorable` 可直接从 `NetworkMonitor.getKeyCounter()` 获取数据
- 旧 `getStorageList()` 仍可用但标记为废弃

#### 任务 6.3：Cell 系统原生输出 GenericStack

目标：

- `BasicCellInventory` 内部数据结构从 `IAEStack` 切换为 `GenericStack` / `KeyCounter`
- Cell 的 `getAvailableItems()` 原生返回 `KeyCounter`

策略：

- Cell 内部 stored items 从 `IItemList<T>` 切换为 `KeyCounter`
- Cell filter (白名单) 从 `IItemList<T>` 切换为 `Set<AEKey>` + `AEKeyFilter`
- 旧 `getAvailableItems(IItemList)` 保留为兼容委托

验收标准：

- Cell 系统可以原生存储和查询 AEKey
- 旧 API 路径保持功能正确但标记为废弃

### 7.8 阶段 7：配置库存与 Pattern 系统迁移

#### 任务 7.1：配置库存切换为 GenericStack 原生

目标：

- `IAEStackInventory` 内部从 `IAEStack<?>[]` 切换为 `GenericStack[]`
- 去除 `CellConfigLegacyWrapper`

改造点：

- `getAEStackInSlot()` → `getGenericStack(int slot)`
- `putAEStackInSlot()` → `setGenericStack(int slot, GenericStack)`
- NBT 序列化使用 `GenericStack.writeTag()` / `readTag()`
- `asItemHandler()` 视图保持兼容

验收标准：

- StorageBus、LevelEmitter、CellWorkbench 等配置界面原生操作 `GenericStack`
- 旧 `IAEStack` 存取方法保留为 `@Deprecated` 委托

#### 任务 7.2：Pattern 系统切换为 GenericStack

目标：

- `ICraftingPatternDetails` 的输入输出切换为 `GenericStack[]`
- `ItemEncodedPattern` 的序列化使用 `GenericStack.writeTag()`

改造点：

- 新增 `getInputStacks()` / `getOutputStacks()` 返回 `GenericStack[]`
- 旧 `getInputs()` / `getOutputs()` 标记为 `@Deprecated` 并委托
- `SpecialPatternHelper` 等辅助类适配 `GenericStack`

验收标准：

- Pattern 编码界面可以原生编码流体输入/输出
- Crafting CPU 可以正确处理 `GenericStack` 格式的 Pattern

#### 任务 7.3：Pin 系统切换为 AEKey

目标：

- `PinList` / `PinsHandler` 内部从 `IAEStack<?>` 切换为 `AEKey`

改造点：

- Pin 的 identity 从 `IAEStack` 切换为 `AEKey`（因为 Pin 只需要 identity 不需要 amount）
- 序列化使用 `AEKey.toTagGeneric()` / `AEKey.fromTagGeneric()`

验收标准：

- Pin 系统可以 pin 物品和流体
- Pin 的匹配逻辑使用 `AEKey.equals()` 而非 `IAEStack.equals()`

### 7.9 阶段 8：新增 GUI 停止走旧路

#### 任务 8.1：建立新 GUI 模板

模板要求：

- 默认继承 `AEBasePanel` 或 `AEBaseMEPanel`
- 统一通过 `setupWidgets()` 注册控件
- 公共能力优先复用 `widgets` 和 `module`
- 页面类仅保留必要业务编排逻辑
- 数据输入使用 `GenericStack` / `KeyCounter`，不引入 `IAEStack`

验收标准：

- 新 GUI 默认按模板实现，不再复制旧页面代码结构

#### 任务 8.2：建立评审规则

建议规则：

- 不允许新增页面继续复制旧式 `initGui()` 大块初始化逻辑
- 不允许页面直接管理多个旧 GUI 控件细节
- 同类交互优先复用已存在的模块和封装控件
- 不允许新代码引入 `IAEStack` / `IItemList` / `IStorageChannel` 依赖
- 类型判断必须使用 `AEKeyType` 而非 `instanceof IAEItemStack`

验收标准：

- 新增页面不再扩散技术债

### 7.10 阶段 9：按 ROI 回收旧页面与旧 API

#### 任务 9.1：建立迁移评分表

##### 底层维度

- 布局复杂度
- 控件复杂度
- 旧控件依赖程度
- 容器耦合程度
- `IAEStack` 依赖深度

##### 功能层维度

- 用户敏感度
- 功能复杂度
- 维护频率
- 可复用价值
- 多类型支持需求

验收标准：

- 可以对旧页面和旧 API 消费者进行排序，作为下一阶段迁移输入

#### 任务 9.2：优先迁标准化页面

优先对象：

- 配置类页面
- 输入型面板
- 命名 / 数值 / 优先级类小窗口

暂缓对象：

- 高度复杂且用户习惯强的核心终端页面

验收标准：

- 每批迁移都能沉淀通用能力，不做一次性孤立重写

#### 任务 9.3：清理废弃 API

最终目标（长期）：

- 移除 `IStorageChannel<T>` 接口
- 移除 `IItemList<T>` 接口（以 `KeyCounter` 完全替代）
- `IAEStack<T>` 收敛为纯内部实现细节（Cell 序列化兼容）
- 移除 `CellConfigLegacyWrapper`
- `AEKeyType` 不再委托到 `IAEStackType`，而是直接持有元数据

验收标准：

- 公共 API 表面不再暴露旧泛型体系
- 所有 `@Deprecated` 标记已清理

## 8. 双线交汇点详解

### 8.1 交汇点 A：终端 GUI 数据渲染

| 维度 | MUI 侧 | AEKey 侧 | 联合效果 |
| --- | --- | --- | --- |
| 数据输入 | `MUIMEMonitorablePanel` 结构化 | `ItemRepo` 切换为 `KeyCounter` | 终端可以统一渲染物品+流体 |
| 虚拟槽位 | 动态槽位模块化 | `VirtualSlot` 接受 `GenericStack` | 一套 slot 管理代码支持所有类型 |
| 类型切换 | 工具栏模块化 | `AEKeyType.filter()` | 按钮切换逻辑统一 |
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
阶段 0: 建立基线（双线共用）
    ↓
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
    └── 阶段 8: 新增 GUI 停止走旧路（双线共用）
            ↓
        阶段 9: 按 ROI 回收旧页面与旧 API
```

关键依赖：

- 阶段 2 可以与阶段 1/3 并行
- 阶段 4.3（MUIMEMonitorablePanel）依赖阶段 2 的 ItemRepo 切换
- 阶段 6 依赖阶段 2 完成（客户端能接收新格式后才能切换服务端发送格式）
- 阶段 7 独立于 GUI 迁移，可以在任意时间点推进
- 阶段 8/9 依赖前面阶段的基础设施稳定

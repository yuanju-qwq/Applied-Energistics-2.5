# MUI 迁移评分表

## 评分方法

每个维度评分 1-5：

| 分数 | 含义 |
|------|------|
| 1 | 极低 / 无影响 |
| 2 | 低 |
| 3 | 中等 |
| 4 | 高 |
| 5 | 极高 / 核心阻塞 |

**迁移优先级 = 底层总分 × 0.4 + 功能层总分 × 0.6**

权重说明：功能层权重更高，因为用户可见度和维护收益是驱动迁移的核心动力。

---

## 底层维度评分

### L1. 布局复杂度
评估屏幕布局的复杂程度：单区域网格 ↔ 多面板 + 拖拽 + 嵌套

| 分数 | 标准 | 示例 |
|------|------|------|
| 1 | 单张背景贴图 + 标题 | `MUISkyChestPanel` |
| 2 | 静态网格 + 少量按钮 | `MUICraftAmountPanel` |
| 3 | 动态网格 + 工具栏 + 滚动条 | `MUIMEMonitorablePanel`（基础终端） |
| 4 | 多面板 + 侧栏 + 搜索 | `MUIWirelessDualInterfaceTerminalPanel` |
| 5 | 多区域 + 拖拽 + 嵌套模块 | `MUIExpandedProcessingPatternTermPanel` |

### L2. 控件复杂度
评估屏幕内部控件的数量与多样性

| 分数 | 标准 |
|------|------|
| 1 | 无自定义控件 |
| 2 | 1-2 个简单控件（按钮、文本） |
| 3 | 3-5 个控件（按钮组、输入框、标签） |
| 4 | 5-10 个控件 + 自定义槽 |
| 5 | >10 个控件 + 多种自定义槽 + 虚拟网格 |

### L3. 旧控件依赖程度
评估对 `gui/widgets/` 下旧控件的依赖

| 分数 | 标准 |
|------|------|
| 1 | 完全使用 MUI 控件 |
| 2 | 仅有 `VirtualMEMonitorableSlot` 等旧槽位 |
| 3 | 使用旧式按钮或标签 |
| 4 | 混合使用新旧控件 |
| 5 | 完全依赖旧控件系统 |

### L4. 容器耦合程度
评估屏幕与容器的交互复杂度

| 分数 | 标准 |
|------|------|
| 1 | 纯展示，无容器回调 |
| 2 | 少量 `actionPerformed` / 按键事件 |
| 3 | 容器回调 + 数据更新 + 槽点击 |
| 4 | 多容器接口 + 自定义网络包 |
| 5 | 深度耦合：直接操作 `IAEStackInventory` + `SlotME` |

### L5. IAEStack 依赖深度
评估对 `IAEStack` / `IAEStackType` 的数据依赖

| 分数 | 标准 |
|------|------|
| 1 | 无引用（已完全使用 `AEKey`/`GenericStack`） |
| 2 | 仅 `IAEStackType` 类型过滤（`acceptType`） |
| 3 | 少量读操作（`getStackSize`、`isCraftable`） |
| 4 | 读写操作 + 列表管理（`findPrecise`、`copy`） |
| 5 | 全面生命周期：列表创建 + 查询 + 修改 + 序列化 |

---

## 功能层维度评分

### F1. 用户敏感度
评估屏幕的日常使用频率和用户可见度

| 分数 | 标准 |
|------|------|
| 1 | 极少使用（如 QNB、SpatialIO） |
| 2 | 偶尔使用（如 Grinder、Condenser） |
| 3 | 中等频率（如 PatternProvider、Interface） |
| 4 | 频繁使用（如 CraftingTerminal、Drive） |
| 5 | 核心体验（如 ME Terminal、CraftConfirm、CPU 管理） |

### F2. 功能复杂度
评估屏幕承载的业务逻辑复杂程度

| 分数 | 标准 |
|------|------|
| 1 | 纯展示，无交互 |
| 2 | 简单输入/修改 |
| 3 | 列表浏览 + 选择 |
| 4 | 多步骤流程（如 CraftAmount → CraftConfirm） |
| 5 | 多系统联动（合成 + IO + 流体 + 模板编码） |

### F3. 维护频率
评估代码变更的活跃程度（基于 git history 推断）

| 分数 | 标准 |
|------|------|
| 1 | 几乎不变（如 SkyChest） |
| 2 | 偶尔调整（如 Bus、LevelEmitter） |
| 3 | 常规维护（如 ME Terminal、CraftingTerm） |
| 4 | 频繁改动（如 PatternTerminal、InterfaceTerminal） |
| 5 | 持续演进（如 ExpandedProcessingPatternTerm） |

### F4. 可复用价值
评估重构为模板后能被其他屏幕复用的程度

| 分数 | 标准 |
|------|------|
| 1 | 完全特化，无法复用 |
| 2 | 少量通用参数可提取 |
| 3 | 部分逻辑可提取为 module |
| 4 | 核心交互可复用为 module 或模板 |
| 5 | 高度通用，可作为新模板基类 |

### F5. 多类型支持需求
评估屏幕是否需要同时支持 item/fluid 等多种资源类型

| 分数 | 标准 |
|------|------|
| 1 | 仅 item |
| 2 | 以 item 为主，少量 fluid |
| 3 | 同时支持 item + fluid |
| 4 | 支持多种 AEKeyType |
| 5 | 完全类型无关，可扩展 |

---

## 评分明细

### 屏幕面板（Screen Panels）

| # | 文件 | L1 布局 | L2 控件 | L3 旧控件 | L4 耦合 | L5 栈依赖 | 底层∑ | F1 敏感度 | F2 功能 | F3 维护 | F4 复用 | F5 多类型 | 功能∑ | **优先级分** | 已迁移 |
|---|------|---------|---------|-----------|---------|-----------|-------|-----------|---------|---------|---------|-----------|-------|------------|--------|
| 1 | `MUIMEMonitorablePanel` | 3 | 4 | 3 | 4 | 2 | 16 | 5 | 4 | 3 | 4 | 4 | 20 | **18.4** | 基础-MUI |
| 2 | `MUICraftConfirmPanel` | 3 | 3 | 2 | 3 | **5** | 16 | 4 | **5** | 2 | 3 | 3 | 17 | **16.6** | 基础-MUI |
| 3 | `MUICraftingCPUPanel` | 3 | 3 | 2 | 3 | **5** | 16 | 4 | 4 | 2 | 3 | 3 | 16 | **16.0** | 基础-MUI |
| 4 | `MUIPatternTermPanel` | 4 | 4 | 3 | 4 | 3 | 18 | 4 | **5** | **5** | 4 | 3 | 21 | **19.8** | 基础-MUI |
| 5 | `MUIExpandedProcessingPatternTermPanel` | **5** | **5** | 3 | 4 | 3 | 20 | 4 | **5** | **5** | **5** | 4 | 23 | **21.8** | 基础-MUI |
| 6 | `MUICraftingTermPanel` | 3 | 3 | 2 | 3 | 1 | 12 | **5** | 4 | 4 | 4 | 3 | 20 | **16.8** | ✅ |
| 7 | `MUINetworkStatusPanel` | 2 | 2 | 1 | 2 | 3 | 10 | 2 | 2 | 1 | 2 | 2 | 9 | **9.4** | 基础-MUI |
| 8 | `MUIMEInterfacePanel` | 3 | 3 | 2 | 3 | 2 | 13 | 3 | 3 | 3 | 3 | 3 | 15 | **14.2** | 基础-MUI |
| 9 | `MUIWirelessDualInterfaceTerminalPanel` | 4 | 4 | 3 | 3 | 1 | 15 | 3 | 4 | 3 | 4 | 3 | 17 | **16.2** | 基础-MUI |
| 10 | `MUIInterfaceTerminalPanel` | 4 | 4 | 3 | 3 | 1 | 15 | 3 | 4 | 3 | 4 | 3 | 17 | **16.2** | 基础-MUI |
| 11 | `MUIInterfaceConfigurationTerminalPanel` | 4 | 4 | 3 | 3 | 1 | 15 | 3 | 4 | 3 | 4 | 3 | 17 | **16.2** | 基础-MUI |
| 12 | `MUICraftingStatusPanel` | 2 | 2 | 1 | 2 | 2 | 9 | 3 | 2 | 1 | 2 | 2 | 10 | **9.6** | 基础-MUI |
| 13 | `MUICraftAmountPanel` | 2 | 3 | 3 | 2 | 1 | 11 | 4 | 3 | 2 | 2 | 1 | 12 | **11.6** | ✅ |
| 14 | `MUIWirelessTermPanelImpl` | 3 | 4 | 3 | 3 | 1 | 14 | 4 | 3 | 3 | 3 | 3 | 16 | **15.2** | 基础-MUI |
| 15 | `MUIWirelessCraftingTermPanelImpl` | 3 | 4 | 3 | 3 | 1 | 14 | 4 | 3 | 3 | 3 | 3 | 16 | **15.2** | 基础-MUI |
| 16 | `MUIWirelessPatternTermPanelImpl` | 3 | 4 | 3 | 3 | 1 | 14 | 4 | 3 | 3 | 3 | 3 | 16 | **15.2** | 基础-MUI |
| 17 | `MUIWirelessInterfaceTermPanelImpl` | 3 | 4 | 3 | 3 | 1 | 14 | 4 | 3 | 3 | 3 | 3 | 16 | **15.2** | 基础-MUI |
| 18 | `MUIPortableCellPanel` | 3 | 3 | 2 | 3 | 1 | 12 | 3 | 3 | 2 | 3 | 3 | 14 | **13.2** | 基础-MUI |
| 19 | `MUIMEPortableCellPanelImpl` | 3 | 3 | 2 | 3 | 1 | 12 | 3 | 3 | 2 | 3 | 3 | 14 | **13.2** | 基础-MUI |
| 20 | `MUISecurityStationPanelImpl` | 3 | 3 | 2 | 3 | 1 | 12 | 3 | 3 | 2 | 3 | 3 | 14 | **13.2** | 基础-MUI |
| 21 | `MUIChestPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 3 | 2 | 2 | 1 | 1 | 9 | **9.0** | ✅ |
| 22 | `MUIDrivePanel` | 2 | 2 | 2 | 2 | 1 | 9 | 3 | 2 | 2 | 1 | 1 | 9 | **9.0** | ✅ |
| 23 | `MUICellWorkbenchPanel` | 3 | 3 | 2 | 3 | 1 | 12 | 2 | 3 | 2 | 2 | 2 | 11 | **11.4** | 基础-MUI |
| 24 | `MUIUpgradeablePanel` | 2 | 3 | 3 | 2 | 1 | 11 | 3 | 2 | 2 | 3 | 3 | 13 | **12.2** | 基础-MUI |
| 25 | `MUIStorageBusPanel` | 2 | 3 | 3 | 2 | 1 | 11 | 3 | 2 | 2 | 3 | 2 | 12 | **11.6** | 基础-MUI |
| 26 | `MUIFluidIOPanel` | 2 | 3 | 3 | 2 | 1 | 11 | 2 | 2 | 2 | 3 | 2 | 11 | **11.0** | 基础-MUI |
| 27 | `MUIFormationPlanePanel` | 2 | 3 | 3 | 2 | 1 | 11 | 2 | 2 | 2 | 3 | 2 | 11 | **11.0** | 基础-MUI |
| 28 | `MUIFluidFormationPlanePanel` | 2 | 3 | 3 | 2 | 1 | 11 | 2 | 2 | 2 | 3 | 2 | 11 | **11.0** | 基础-MUI |
| 29 | `MUILevelEmitterPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 1 | 2 | 2 | 9 | **9.0** | 基础-MUI |
| 30 | `MUIFluidLevelEmitterPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 1 | 2 | 2 | 9 | **9.0** | 基础-MUI |
| 31 | `MUIIOPortPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 1 | 2 | 2 | 9 | **9.0** | ✅ |
| 32 | `MUISkyChestPanel` | 1 | 1 | 1 | 1 | 1 | 5 | 2 | 1 | 1 | 1 | 1 | 6 | **5.6** | ✅ |
| 33 | `MUIVibrationChamberPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 1 | 1 | 1 | 1 | 1 | 5 | **6.6** | ✅ |
| 34 | `MUIMACPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 3 | 2 | 2 | 2 | 1 | 10 | **9.6** | ✅ |
| 35 | `MUIInscriberPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 3 | 2 | 2 | 2 | 1 | 10 | **9.6** | ✅ |
| 36 | `MUIGrinderPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 1 | 1 | 1 | 1 | 1 | 5 | **6.6** | ✅ |
| 37 | `MUIPriorityPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 2 | 2 | 1 | 9 | **9.0** | ✅ |
| 38 | `MUINetworkToolPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 2 | 2 | 1 | 9 | **9.0** | ✅ |
| 39 | `MUIQNBPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 1 | 1 | 1 | 1 | 1 | 5 | **6.6** | ✅ |
| 40 | `MUISpatialIOPortPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 1 | 2 | 1 | 1 | 1 | 6 | **7.2** | ✅ |
| 41 | `MUIQuartzKnifePanel` | 1 | 1 | 1 | 1 | 1 | 5 | 1 | 1 | 1 | 1 | 1 | 5 | **5.0** | ✅ |
| 42 | `MUIRenamerPanel` | 1 | 1 | 1 | 1 | 1 | 5 | 2 | 1 | 1 | 1 | 1 | 6 | **5.6** | ✅ |
| 43 | `MUICondenserPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 1 | 1 | 1 | 7 | **7.8** | ✅ |
| 44 | `MUIPatternProviderPanel` | 3 | 3 | 2 | 3 | 1 | 12 | 3 | 3 | 3 | 3 | 2 | 14 | **13.2** | 基础-MUI |
| 45 | `MUIPatternValueAmountPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 2 | 2 | 2 | 10 | **9.6** | 基础-MUI |
| 46 | `MUIPatternValueNamePanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 2 | 2 | 2 | 10 | **9.6** | 基础-MUI |
| 47 | `MUIOreDictStorageBusPanel` | 2 | 3 | 3 | 2 | 1 | 11 | 2 | 2 | 2 | 3 | 2 | 11 | **11.0** | 基础-MUI |
| 48 | `MUISecurityStationPanel` | 2 | 2 | 2 | 2 | 1 | 9 | 2 | 2 | 1 | 2 | 1 | 8 | **8.4** | 基础-MUI |
| 49 | `WirelessTerminalHelper` | — | — | — | — | — | — | — | — | — | — | — | — | — | 非面板 |

已迁移 = ✅，基础-MUI = 已迁移至 MUI 框架但仍有旧 API 依赖

### 基础设施（Infrastructure）

| # | 文件 | L1 布局 | L2 控件 | L3 旧控件 | L4 耦合 | L5 栈依赖 | 底层∑ | F1 敏感度 | F2 功能 | F3 维护 | F4 复用 | F5 多类型 | 功能∑ | **优先级分** | 说明 |
|---|------|---------|---------|-----------|---------|-----------|-------|-----------|---------|---------|---------|-----------|-------|------------|------|
| I1 | `ItemRepo` | — | — | — | — | 3 | 3 | **5** | 4 | 4 | **5** | **5** | 23 | **15.0** | 核心桥接层，需最后迁移 |
| I2 | `VirtualMESlot` | — | — | — | — | 4 | 4 | 4 | 3 | 3 | 3 | 4 | 17 | **11.8** | 抽象桥接方法 |
| I3 | `DynamicListModule` | — | — | — | — | 2 | 2 | 3 | 2 | 2 | **5** | 4 | 16 | **10.4** | 弃用方法清理 |
| I4 | `MEItemBrowserModule` | — | — | — | — | 2 | 2 | 4 | 3 | 3 | 4 | 4 | 18 | **11.6** | 弃用方法清理 |
| I5 | `MUIStackRenderer` | — | — | — | — | 2 | 2 | 3 | 2 | 2 | 4 | 4 | 15 | **9.8** | 桥接方法清理 |
| I6 | `MUITypeFilter` | — | — | — | — | 1 | 1 | 3 | 2 | 2 | 3 | 4 | 14 | **8.8** | 旧枚举引用 |

---

## 迁移优先级排序

### 第一梯队（优先级 ≥ 16，高收益 + 中等难度）

| 排名 | 文件 | 优先级分 | 迁移要点 |
|------|------|---------|---------|
| **1** | `MUIExpandedProcessingPatternTermPanel` | **21.8** | 布局最复杂；深度耦合；可复用价值最高。先提取 `PatternEncodingModule`，再逐步清洗 IAEStack |
| **2** | `MUIPatternTermPanel` | **19.8** | 与排版终端共享 `PatternEncodingModule`；`.copy()` + 网络包的 IAEStack 链路需替换为 `GenericStack` |
| **3** | `MUIMEMonitorablePanel` | **18.4** | 用户敏感度最高（核心终端）；改 `postUpdate(List<IAEStack>)` 为 `postRepoEntryUpdate(List<RepoEntry>)` |
| **4** | `MUICraftingTermPanel` | **16.8** | 用户频繁使用；继承 MUIMEMonitorablePanel，可随其一起迁移 |
| **5** | `MUICraftConfirmPanel` | **16.6** | IAEStack 引用最多（29处）；`IAEStackList` → `KeyCounter` 重构；需要配套改 Container |

### 第二梯队（优先级 14–16，中等收益 + 中等难度）

| 排名 | 文件 | 优先级分 | 迁移要点 |
|------|------|---------|---------|
| 6 | `MUICraftingCPUPanel` | 16.0 | 与 CraftConfirm 几乎对称；一起迁移 |
| 7 | `MUIInterfaceTerminalPanel` | 16.2 | 含 `InterfaceListModule`；提取通用模块 |
| 8 | `MUIWirelessDualInterfaceTerminalPanel` | 16.2 | 多终端混合；IAEStack 极少（1处），但布局复杂 |
| 9 | `MUIInterfaceConfigurationTerminalPanel` | 16.2 | 与 InterfaceTerminal 共享模块 |
| 10 | `MUIWirelessTermPanelImpl` | 15.2 | 无线终端组；依赖 MUIMEMonitorablePanel |
| 11 | `MUIWirelessCraftingTermPanelImpl` | 15.2 | 同上 |
| 12 | `MUIWirelessPatternTermPanelImpl` | 15.2 | 同上 |
| 13 | `MUIWirelessInterfaceTermPanelImpl` | 15.2 | 同上 |

### 第三梯队（优先级 10–14，可优化但优先级不高）

| 排名 | 文件 | 优先级分 |
|------|------|---------|
| 14 | `MUIMEInterfacePanel` | 14.2 |
| 15 | `MUIPortableCellPanel` + `MUIMEPortableCellPanelImpl` | 13.2 |
| 16 | `MUIPatternProviderPanel` | 13.2 |
| 17 | `MUISecurityStationPanelImpl` | 13.2 |
| 18 | `MUIUpgradeablePanel` | 12.2 |
| 19 | `MUIStorageBusPanel` | 11.6 |
| 20 | `MUICraftAmountPanel` | 11.6 |
| 21 | `MUICellWorkbenchPanel` | 11.4 |
| 22 | `MUIFluidIOPanel` / `MUIFormationPlanePanel` / `MUIFluidFormationPlanePanel` | 11.0 |
| 23 | `MUIOreDictStorageBusPanel` | 11.0 |

### 第四梯队（优先级 < 10，建议自动化重构即可）

| 范围 | 文件 |
|------|------|
| 纯 IAEStackType 文件（8个） | `MUILevelEmitterPanel`, `MUIFluidLevelEmitterPanel`, `MUIFluidIOPanel`, `MUIFluidFormationPlanePanel`, `MUICellWorkbenchPanel`, `MUIStorageBusPanel`, `MUILevelEmitterPanel`, `MUISecurityStationPanel` |
| 已完全干净（10个 ✅） | `MUICraftingTermPanel`, `MUICraftAmountPanel`, `MUIChestPanel`, `MUIDrivePanel`, `MUIIOPortPanel`, `MUISkyChestPanel`, `MUIVibrationChamberPanel`, `MUIMACPanel`, `MUIInscriberPanel`, `MUIGrinderPanel`, `MUIPriorityPanel`, `MUINetworkToolPanel`, `MUIQNBPanel`, `MUISpatialIOPortPanel`, `MUIQuartzKnifePanel`, `MUIRenamerPanel`, `MUICondenserPanel` |
| 基础设施 | `DynamicListModule`（弃用方法清理）, `MUIStackRenderer`（桥接清理）, `MUITypeFilter`（旧枚举引用） |

---

## 迁移路线图建议

```
Phase 1 (第一梯队): PatternTerminal + ExpandedPatternTerminal → 提取 PatternEncodingModule
Phase 2 (第一梯队): MEMonitorablePanel → postUpdate 迁移 + 模板化
Phase 3 (第二梯队): CraftConfirm + CraftingCPU → IAEStackList → KeyCounter
Phase 4 (第二梯队): InterfaceTerminal 系列 → InterfaceListModule 抽取
Phase 5 (第三梯队): 零散面板逐个清理 IAEStackType
Phase 6 (基础设施): ItemRepo / VirtualMESlot 桥接方法清理（最后，依赖方先清理完）
```

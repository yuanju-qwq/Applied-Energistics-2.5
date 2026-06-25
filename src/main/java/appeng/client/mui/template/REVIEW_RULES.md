# MUI 新 GUI 代码评审规则

## 目标

新增页面不再扩散技术债，所有新 GUI 面板遵循统一的模板架构。

---

## 规则

### R1. 新页面必须继承模板基类

| 用途 | 基类 |
|------|------|
| 标准列表面板（含滚动 + 虚拟槽网格） | `MUITemplatePanel` |
| 纯展示/无列表面板 | `AEBaseMEPanel`（仅当模板不适用时） |
| 其他 | 禁止直接继承 `AEBasePanel`（除非评审确认不适用） |

**禁止**：直接继承 `AEBasePanel` / `AEBaseMEPanel` 并复制 `initGui()` 大块初始化逻辑。

---

### R2. initGui() 中禁止创建控件

新页面允许覆写 `initGui()` 仅限于：
- 计算布局参数（行数、`ySize`、`guiTop`） — 在 `super.initGui()` 之前
- 调用 `initModules()` 完成模块初始化 — 在 `super.initGui()` 之后
- 设置 scrollbar 范围、槽位重定位

**禁止** 在 `initGui()` 中：
- 创建 `GuiButton`、`GuiTextField`、`MUIButtonWidget` 等控件
- 注册 `IMUIWidget`
- 手动管理 `buttonList`/`widgets` 列表

这些操作应统一放在 `setupWidgets()` 中。

---

### R3. 同类交互优先复用模块和封装控件

| 场景 | 应使用 | 不应使用 |
|------|--------|----------|
| 可滚动资源网格 | `DynamicListModule` | 自行创建 `MUIVirtualSlot` 列表 + 手动 scroll 逻辑 |
| 搜索栏 | `SearchBarModule` | 自行创建 `GuiTextField` |
| 工具栏（排序/过滤/终端样式） | `TerminalToolbar` | 自行创建 `MUIButtonWidget` 组合 |
| 设置按钮 | `MUIButtonWidget`（Settings 模式） | 旧式 `GuiImgButton` |
| 虚拟槽渲染 | `MUIVirtualSlot` | 旧式 `VirtualMEMonitorableSlot` |

**禁止** 手动重复实现上述模块已封装的功能。

---

### R4. 新代码禁止引入 IAEStack / IAEStackType / IItemList / IStorageChannel

数据输入和传递必须使用以下类型：

| ✅ 允许 | ❌ 禁止 |
|---------|---------|
| `GenericStack` | `IAEStack` / `IAEItemStack` / `IAEFluidStack` |
| `KeyCounter` | `IItemList` |
| `AEKey` / `AEItemKey` / `AEFluidKey` | `IAEStackType` / `IStorageChannel` |
| `ItemRepo.RepoEntry` | 所有 `IAE*` 前缀的 API 类型 |

**豁免**：仅在以下场景可保留 `IAEStack` 引用：
- 旧页面迁移中的历史 import（但应在重构中逐步消除）
- `AEBasePanel`/`AEBaseMEPanel` 基类内部的兼容层
- `appeng.client.mui.legacy` 包内的桥接类（如 `LegacyStackBridge`），用于集中 IAEStack↔AEKey/GenericStack/RepoEntry 的转换逻辑，作为单一修改点隔离旧体系依赖。新代码不得在 legacy 包外调用这些桥接方法。

---

### R5. 类型判断必须使用 AEKeyType

```java
// ✅ 正确
if (key.getType() == AEKeyType.items()) { ... }
if (key instanceof AEItemKey) { ... }

// ❌ 禁止
if (stack instanceof IAEItemStack) { ... }
```

`AEKeyType` 是统一的类型标识系统，覆盖 item、fluid 等所有资源类型。

---

### R6. 页面类职责边界

页面（Panel）仅应包含：
1. 构造函数：传入容器、设置初始布局参数
2. `setupWidgets()`：注册业务特定控件（如有）
3. 容器回调：处理网络包、更新数据
4. `drawBG()`/`drawFG()`：绘制面板特有内容

不应包含：
- 自行创建 slot 管理逻辑
- 自行实现滚动条管理
- 自行处理搜索/排序/过滤状态
- 类型判断的 if-else 链（优先用 `AEKeyType` 或模块接口）

---

### R7. 新页面大小限制参考

| 类型 | 推荐行数上限 | 说明 |
|------|-------------|------|
| 纯展示面板 | ≤ 80 行 | 参考 `MUISkyChestPanel` |
| 单一功能面板 | ≤ 200 行 | 参考 `MUICraftAmountPanel` |
| 模块化复杂面板 | ≤ 400 行 | 组合多个 module，参考 `MUIMEMonitorablePanel` |
| 超长面板 | 不允许新增 | 需拆分为子模块 |

---

## 评审检查清单

```
□ R1  继承自 MUITemplatePanel 或 AEBaseMEPanel（而不是 AEBasePanel）
□ R2  不存在 initGui() 中创建控件的代码
□ R3  不存在手动重造模块功能的代码（搜 MUIVirtualSlot 列表管理、手动 scroll 等）
□ R4  不存在 IAEStack / IAEStackType / IItemList / IStorageChannel 的 import（legacy 包除外）
□ R5  不存在 instanceof IAEItemStack / instanceof IAEFluidStack 判断
□ R6  setupWidgets() 是唯一的控件注册入口
□ R7  总行数未超出对应类型的合理上限
```

---

*模板文件位于 `appeng/client/mui/template/MUITemplatePanel.java`*

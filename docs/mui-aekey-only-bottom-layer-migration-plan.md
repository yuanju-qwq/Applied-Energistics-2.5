# MUI 底层 AEKey-only 迁移计划

## 1. 目标

将新的 MUI 底层从当前 `GuiContainer` 兼容壳中逐步拆出，并保证新底层不再依赖旧 `IAEStack` 体系。

本计划中的“新 MUI 底层”指新建的屏幕基类、面板基类、虚拟槽、列表模块、渲染器、JEI/HEI 适配接口和输入事件管线。它们的公开 API 和内部数据模型必须只使用 AEKey 体系。

允许使用的数据类型：

- `AEKey`
- `AEKeyType`
- `AEItemKey`
- `AEFluidKey`
- `GenericStack`
- `KeyCounter`
- AEKey-only 的 UI entry 类型，例如 `ItemRepo.RepoEntry` 或后续新增的等价类型

禁止进入新底层的数据类型：

- `IAEStack`
- `IAEItemStack`
- `IAEFluidStack`
- `IItemList`
- `IItemContainer`
- `IAEStackInventory`
- `AEItemStack`
- `AEFluidStack`
- `IAEStackTypeRenderer`
- `FluidDummyItem`
- `dummyFluidItem`

新底层还禁止把流体渲染、tooltip、JEI ingredient 转换为流体伪物品。`AEFluidKey` 只能通过 `FluidStack`、流体 atlas 贴图和流体本身的显示名参与 UI。

## 2. 核心原则

### 2.1 新底层不能包一层旧体系

新 MUI 底层不能采用“外部暴露 AEKey，内部继续 `toIAEStack()`”的方式。这样只是隐藏旧依赖，后续仍会被渲染、tooltip、slot、JEI 继续绑住。

正确边界：

```text
旧逻辑 / 旧容器 / 旧网络包
        |
        | 仅在 legacy bridge 中转换
        v
GenericStack / KeyCounter / AEKey
        |
        v
新 MUI 底层
```

### 2.2 旧体系只能留在隔离桥接层

如果短期内确实需要从旧数据转换到新数据，转换代码必须集中放在独立包或独立类中，例如：

```text
appeng.client.mui.legacy
appeng.client.mui.bridge
```

新底层包不得 import 这些旧类型。桥接层可以依赖旧体系，但新底层不能反向依赖桥接层。

### 2.3 不同时做大规模业务重构

本迁移只处理 GUI 底层和 UI 数据模型。不要在同一阶段混入：

- 合成 CPU 逻辑重写
- 样板编码规则重写
- 存储网络算法重写
- 物品/流体业务行为调整

旧业务逻辑可以先保留，但进入新 UI 前必须转换为 AEKey 体系。

## 3. 包边界建议

建议新建或整理以下包结构：

```text
appeng.client.mui.core       新 MUI 基类、生命周期、坐标、输入管线
appeng.client.mui.key        AEKey-only UI 数据模型、entry、列表工具
appeng.client.mui.key.slot   AEKey-only 虚拟槽和 slot 交互
appeng.client.mui.render     AEKey-only 图标、数量、tooltip 渲染
appeng.client.mui.jei        AEKey-only JEI/HEI 适配
appeng.client.mui.legacy     旧 IAE 体系到 AEKey 体系的临时桥接
```

硬性约束：

- `core`、`key`、`key.slot`、`render`、`jei` 不允许 import `appeng.api.storage.data.*`
- `core`、`key`、`key.slot`、`render`、`jei` 不允许 import `appeng.tile.inventory.IAEStackInventory`
- `legacy` 可以 import 旧类型，但不能被新底层反向依赖

## 4. 阶段 0：建立审查规则

先将规则写入代码审查和 grep 检查，避免新代码继续把旧体系带进去。

建议检查命令：

```powershell
rg -n "IAE[A-Za-z]*Stack|IItemList|IItemContainer|IAEStackInventory|AEItemStack|AEFluidStack|IAEStackTypeRenderer" src/main/java/appeng/client/mui
```

迁移初期允许已有旧文件命中，但新建底层包不得命中。后续每迁移一个模块，都要减少命中范围。

## 5. 阶段 1：替换渲染核心

当前 `MUIStackRenderer` 仍存在 `IAEStack` 输入和 `entry.toIAEStack()` 桥接。新底层不能继续依赖它。

新增 AEKey-only 渲染器，例如：

```java
public final class AEKeyStackRenderer {
    public static void renderIcon(Minecraft mc, AEKey key, int x, int y) {
    }

    public static void renderOverlay(Minecraft mc, AEKey key, long amount, boolean craftable, int x, int y) {
    }

    public static List<String> buildTooltip(AEKey key, long amount, boolean craftable) {
    }
}
```

渲染分发规则：

- 类型判断使用 `AEKeyType`
- 物品图标通过 `AEItemKey` 获取 `ItemStack` 表示
- 流体图标通过 `AEFluidKey` 获取 `FluidStack` 表示
- 流体图标只允许使用 fluid still texture，不能 fallback 到 dummy fluid item
- 数量格式化使用 `AEKeyType#formatAmount`
- tooltip 由 `AEKey` / `GenericStack` 构造，不调用 `IAEStackTypeRenderer`
- 流体 tooltip 由 `FluidStack` 构造，不调用 `AEKey#asItemStackRepresentation()`

完成标准：

- 新渲染器不 import `IAEStack`
- 新渲染器不调用 `toIAEStack()`
- 新虚拟槽可以只依赖 `AEKeyStackRenderer` 显示 item/fluid

## 6. 阶段 2：替换 UI 数据 entry

建立统一的 AEKey-only UI entry。可以继续使用现有 `ItemRepo.RepoEntry`，也可以新建更明确的类型：

```java
public record AEKeyDisplayEntry(
        AEKey key,
        long amount,
        boolean craftable
) {
}
```

该 entry 必须满足：

- 不提供 `toIAEStack()`
- 不保存 `IAEStack`
- 不暴露旧 storage channel
- craftable 状态独立保存

推荐先让列表、虚拟槽、tooltip、JEI 都依赖这个 entry，再逐步清理旧 `ItemRepo.RepoEntry#toIAEStack()` 调用。

本项目第一阶段新增的干净边界：

- `AEKeyDisplayEntry`：单条 AEKey-only UI 条目
- `AEKeyListData`：只接收 `AEKey` / `GenericStack` / `KeyCounter` / `AEKeyDisplayEntry` 的列表数据入口
- `AEKeyModularPanel`：新 MUI 底层面板基类，实现 `IAEKeyGuiPanel`
- `AEKeyMUIScreenFactory`：将 `AEKeyModularPanel` 包装为 ModularUI screen，并记录 wrapper 到 panel 的弱引用映射
- `AEKeyGuiHandler`：新 MUI screen 的 AEKey-only JEI ghost ingredient handler

新面板应优先依赖这些类型，不直接依赖仍带 legacy bridge 的旧 `ItemRepo.RepoEntry`。

## 7. 阶段 3：替换虚拟槽模型

新虚拟槽只接受 AEKey 体系：

```java
public abstract class AEKeyVirtualSlot {
    public abstract GenericStack getStack();
    public abstract void setStack(GenericStack stack);
    public abstract boolean accepts(AEKeyType type, int mouseButton);
}
```

禁止继续提供：

```java
getAEStack()
toIAEStack()
fromIAEStack()
```

需要重点迁移的现有类：

- `VirtualMESlot`
- `VirtualMEPhantomSlot`
- `VirtualMEMonitorableSlot`
- `VirtualMEPatternSlot`
- `MUIVirtualSlot`

完成标准：

- 虚拟槽渲染只调用 AEKey-only renderer
- 虚拟槽点击逻辑以 `GenericStack` 作为同步数据
- 接受类型判断只使用 `AEKeyType`
- 旧 `IAEStackInventory` 只允许出现在 legacy bridge 或旧面板中

## 8. 阶段 4：替换列表更新入口

新 MUI 底层只允许以下更新入口：

```java
void postUpdate(GenericStack stack, boolean craftable);
void postGenericStackUpdate(List<GenericStack> stacks);
void postKeyCounterUpdate(KeyCounter counter, boolean craftable);
void postEntryUpdate(List<AEKeyDisplayEntry> entries);
```

禁止以下入口进入新底层：

```java
void postUpdate(IAEStack<?> stack);
void postUpdate(List<IAEStack<?>> stacks);
void postGenericUpdate(List<IAEStack<?>> stacks, byte ref);
```

需要重点处理：

- `DynamicListModule`
- `MEItemBrowserModule`
- `MUIItemRepo`
- `MUICraftConfirmPanel`
- `MUICraftingCPUPanel`

短期兼容方式：

- 旧 container 可以继续产生 `IAEStack`
- 但必须在 `legacy` 桥接层转换成 `GenericStack` 或 `KeyCounter`
- 新列表模块只接收转换后的数据

## 9. 阶段 5：网络包和 Container 边界转换

新 MUI 底层收到的数据必须已经是 AEKey 体系。

推荐映射：

| 场景 | 新数据类型 |
|---|---|
| 单个虚拟槽同步 | `GenericStack` |
| 鼠标目标资源 | `GenericStack` |
| 终端资源批量更新 | `KeyCounter` |
| 合成状态分类列表 | `Map<AEKey, Long>` 或 `KeyCounter` |
| 类型过滤 | `AEKeyType` |

已有 `PacketVirtualSlot` 使用 `GenericStack` 的方向是正确的，应继续扩大这种边界。

完成标准：

- 新 MUI 面板不接收 `List<IAEStack<?>>`
- 新 MUI 面板不调用 `GenericStack.fromIAEStack()`
- `GenericStack.fromIAEStack()` 只在旧兼容桥接层调用

## 10. 阶段 6：新增 MUI 底层基类

不要直接修改当前 `AEBasePanel extends GuiContainer`。先新增 AEKey-only 试点基类，例如：

```java
public abstract class AEKeyModularPanel {
    public abstract List<AEKeyVirtualSlot> getVirtualSlots();
    public abstract GenericStack getStackUnderMouse(int mouseX, int mouseY);
    public abstract List<Rectangle> getJEIExclusionArea();
}
```

或根据 MUI 实际 API 继承：

```java
public abstract class AEKeyModularPanel extends ModularPanel {
}
```

公开 API 只能使用 AEKey 体系：

- `GenericStack`
- `AEKey`
- `AEKeyType`
- `KeyCounter`
- `AEKeyVirtualSlot`

不得暴露：

- `SlotME#getAEStack()`
- `IAEStackInventory`
- `IAEStackTypeRenderer`

## 11. 阶段 7：双轨 GUI 工厂

`AEMUIGuiFactory` 需要支持旧面板和新面板双轨运行。

建议逻辑：

```text
旧面板：返回 AEBasePanel，走现有 GuiContainer 管线
新面板：返回 AEKeyModularPanel，走 MUI wrapper 管线
```

第一阶段已在 `AEMUIGuiFactory#createGui` 中加入识别逻辑：旧 GUI 原样返回；未来注册项返回 `AEKeyModularPanel` 时，会通过 `AEKeyMUIScreenFactory` 包装为 ModularUI screen。

不要让新面板继承旧 `AEBasePanel`。否则旧 IAE 依赖会继续通过 protected 方法和 slot 管线渗透进来。

完成标准：

- 新面板可以独立打开
- 旧面板保持不变
- GUI 注册表能明确区分 legacy GUI 和 AEKey MUI GUI

## 12. 阶段 8：JEI/HEI 适配

当前 JEI handler 绑定 `AEBasePanel`。新底层应抽 AEKey-only 接口：

```java
public interface IAEKeyGuiPanel {
    int getGuiLeft();
    int getGuiTop();
    List<AEKeyVirtualSlot> getVirtualSlots();
    GenericStack getStackUnderMouse(int mouseX, int mouseY);
    List<Rectangle> getJEIExclusionArea();
}
```

JEI ingredient 转换规则：

- `ItemStack` 转 `GenericStack`
- `FluidStack` 转 `GenericStack`
- 不生成 `IAEStack`
- 不生成流体伪物品
- ghost ingredient 写入虚拟槽时只发送 `GenericStack`

完成标准：

- 新 JEI handler 不 import `IAEStack`
- 新 JEI handler 不依赖 `AEBasePanel`
- 拖拽 ghost ingredient 到虚拟槽仍可同步到服务端

第一阶段已新增 `AEKeyGuiHandler` 并注册到 JEI。由于 JEI 1.12 的 `IAdvancedGuiHandler` 只能绑定 `GuiContainer`，当前只接入 `GuiScreenWrapper` 的 ghost ingredient 路径；额外区域和鼠标 ingredient 查询留到新底座切入 `GuiContainerWrapper` 或 ModularUI 自带 JEI handler 后继续。

## 13. 阶段 9：试点迁移顺序

推荐迁移顺序：

1. `MUIRenamerPanel`
   - 无 AEKey 数据压力
   - 用于验证新 MUI 底座打开、关闭、输入、按钮
   - 第一阶段新增 `AEKeyRenamerPanel` 作为试点类，并已临时接入 `AEGuiKeys.RENAMER` 注册；验证完成前保留旧 `MUIRenamerPanel` 便于回退

2. `MUICondenserPanel`
   - 普通 slot 和背景绘制
   - 用于验证 slot hover、shift click、tooltip

3. `MUILevelEmitterPanel` / `MUIFluidLevelEmitterPanel`
   - 验证 `AEKeyType` 过滤
   - 验证虚拟槽接受类型

4. `MUIStorageBusPanel`
   - 验证配置虚拟槽
   - 验证 item/fluid 过滤边界

5. `MUIMEMonitorablePanel`
   - 验证终端列表、搜索、排序、滚动
   - 列表更新必须改为 `GenericStack` / `KeyCounter`

6. `MUIPatternTermPanel` / `MUIExpandedProcessingPatternTermPanel`
   - 验证样板编码虚拟槽
   - 所有 slot 同步使用 `GenericStack`

7. `MUICraftConfirmPanel` / `MUICraftingCPUPanel`
   - 验证合成状态批量数据
   - `IAEStackList` 替换为 `KeyCounter`

## 14. 日志策略

遇到无法确定的兼容问题，优先加日志让实际游戏输出事件链。

建议日志点：

- GUI 创建路径：legacy 还是 AEKey MUI
- panel 初始化尺寸：`guiLeft`、`guiTop`、`xSize`、`ySize`
- 鼠标点击：坐标、button、命中的 slot 类型
- 虚拟槽写入：slot index、`GenericStack`、`AEKeyType`
- JEI ghost 拖拽：ingredient 类型、转换结果、目标 slot
- 网络同步：packet 类型、slot index、stack key、amount
- 列表更新：entry 数量、KeyCounter size、是否 craftable

日志应尽量放在迁移开关或 debug 条件下，避免正式运行刷屏。

## 15. 完成标准

第一阶段完成标准：

- 存在 AEKey-only 的渲染器
- 存在 AEKey-only 的虚拟槽基类
- 存在 AEKey-only 的新 MUI 面板接口
- 至少一个简单 GUI 能通过新底座打开
- 新底层包 grep 不命中旧 IAE 类型

中期完成标准：

- 终端列表更新不再接收 `List<IAEStack<?>>`
- 虚拟槽不再暴露 `getAEStack()`
- JEI 新 handler 不依赖 `AEBasePanel`
- `GenericStack.fromIAEStack()` 只出现在 legacy bridge 中

最终完成标准：

- 所有新 MUI 面板只使用 AEKey 体系
- `AEBasePanel extends GuiContainer` 仅作为旧兼容层存在，或被完全移除
- `client/mui/core`、`client/mui/key`、`client/mui/render`、`client/mui/jei` 不再命中旧 IAE 类型
- 新底层可以独立于旧 `IAEStack` 渲染、输入、tooltip、JEI 和网络同步运行

## 16. 风险点

| 风险 | 说明 | 应对 |
|---|---|---|
| 图标渲染不完整 | 旧渲染器依赖 `IAEStackTypeRenderer` | 先补齐 AEKey item/fluid renderer |
| tooltip 信息减少 | 旧 tooltip 由 `IAEStack` renderer 构造 | 为 `AEKey` 建立等价 tooltip builder |
| 虚拟槽点击行为变化 | 旧逻辑使用 `IAEStack` 判断 item/fluid | 改为 `AEKeyType` 和 `GenericStack` |
| JEI ghost 拖拽断裂 | 当前 handler 绑定 `AEBasePanel` | 新增 `IAEKeyGuiPanel` handler |
| Container 仍推旧数据 | 服务端旧逻辑短期难全改 | 在 legacy bridge 边界集中转换 |
| 一次性改动过大 | 终端和样板界面复杂 | 先迁移简单面板，再迁移终端 |

## 17. 建议的下一步

优先做以下三个基础设施，不直接迁移所有面板：

1. 新增 `AEKeyStackRenderer`
2. 新增 `AEKeyVirtualSlot`
3. 新增 `IAEKeyGuiPanel`

这三者稳定后，再选择 `MUIRenamerPanel` 和 `MUICondenserPanel` 做新 MUI 底座试点。

## 18. 进度记录

### 2026-06-25 — Phase A/B/C/D 完成

本轮在已有 AEKey 体系基础上完成了测试基础设施搭建、IAEStack 残留清理、桥接层抽取与旧入口删除。

#### Phase A：测试基础设施 + 单元测试 + 集成测试

新增 5 个测试类，共 75 个测试用例，全部通过：

| 测试类 | 测试数 | 覆盖范围 |
|---|---|---|
| `AEKeyDisplayEntryTest` | 10 | 构造、`of` 工厂、`toGenericStack`、null 安全 |
| `AEKeyListDataTest` | 17 | `postUpdate` 各重载、snapshot 缓存、`KeyCounter` 入口、`clear`、顺序稳定性 |
| `AEKeyVirtualSlotTest` | 20 | `containsLocal`/`containsScreen`、位置/尺寸、可见性、`accepts`、`getDisplayEntry` |
| `AEKeyModularPanelTest` | 19 | virtualSlots 管理、JEI 排除区、`getStackUnderMouse` 命中测试、listData 集成 |
| `AEKeyDataFlowIntegrationTest` | 9 | `KeyCounter -> AEKeyListData -> List<AEKeyDisplayEntry>` 全链路 |

关键决策：

- `AEItemKey`/`AEFluidKey` 依赖 Minecraft `Item`/`Fluid` 运行时，无法在纯单测中实例化。
- 新增测试专用 `TestKey`/`TestKeyType` 作为 `AEKey`/`AEKeyType` 的轻量替身，注册 id 为 `ae2_test`，避开生产类型冲突。
- `TestKey.getPrimaryKey()` 返回 `Integer.valueOf(id)`，确保 `KeyCounter` 的 `IdentityHashMap` 能正确命中 Integer 缓存范围内的 key。
- `KeyCounter` 内部使用 `IdentityHashMap`，迭代顺序未定义；集成测试通过 `findByTestId` 按 key 查找断言，不依赖顺序。

#### Phase B：清理 dead code + 抽取 `LegacyStackBridge`

勘察发现 `AEBaseMEPanel`/`VirtualMESlot` 中的 IAEStack 残留调用大多是 dead code：`ItemRepo` 不存储 requestable 数量，`RepoEntry.toIAEStack()` 创建的新栈 `countRequestable` 永远为 0，因此所有 `getCountRequestable() > 0` 分支永不执行。

采取「清理 + 桥接抽取」策略：

- 删除 `AEBaseMEPanel.drawTooltip()` 中 `aeStack.getCountRequestable()` dead code 分支。
- 删除 `AEBaseMEPanel.renderToolTip()` 中 `myStack.getCountRequestable()` dead code 分支。
- 移除 `AEBaseMEPanel` 中不再使用的 `IAEStack` import。
- 新建 `appeng.client.mui.legacy.LegacyStackBridge`，集中所有 IAEStack↔AEKey/GenericStack/RepoEntry 转换逻辑，作为单一修改点。
- `RepoEntry.toIAEStack()`/`fromIAEStack()` 改为委托 `LegacyStackBridge`，保留 API 兼容性。
- 保留 `VirtualMESlot.getAEStack()`（`@Deprecated`），因为 `VirtualMEPhantomSlot.handleMouseClicked()` 仍合法使用它做 IAEStack 修改操作（`decStackSize` 等）。

#### Phase C：8 个纯 IAEStackType 面板已验证完成迁移

MIGRATION_SCORING.md 中列出的 8 个面板（`MUILevelEmitterPanel`、`MUIFluidLevelEmitterPanel`、`MUIFluidIOPanel`、`MUIFluidFormationPlanePanel`、`MUICellWorkbenchPanel`、`MUIStorageBusPanel`、`MUISecurityStationPanel`、`MUISecurityStationPanelImpl`）经核查**已无任何 `IAEStack`/`IAEStackType`/`IAEItemStack`/`IAEFluidStack` 依赖**，仅使用 `IAEStackInventory`（尽管名字含 IAE，但该类已是纯 AEKey 体系，内部存储 `GenericStack[]`）。MIGRATION_SCORING.md 的分类已过时，需在 Phase F2 中修正。

#### Phase D：删除 `DynamicListModule` 的 `@Deprecated` 旧入口

- `DynamicListModule.postUpdate(List<IAEStack<?>> stacks)` 已 `@Deprecated` 且无外部调用者，直接删除。
- 移除 `DynamicListModule` 中不再使用的 `IAEStack` import。

#### 验证

- `gradlew compileJava test` 编译通过，75 个测试用例全部通过，0 失败 0 跳过。


# MUI Panel 基类迁移计划书

## 1. 目标

将 `AEBasePanel extends GuiContainer` 改为基于 MUI `ModularPanel` / `GuiContainerWrapper` 架构，消除对 Minecraft `GuiContainer` 的直接继承依赖。

## 2. 当前架构

```
GuiContainer (net.minecraft.client.gui.inventory)
  └── AEBasePanel (abstract)
        ├── AEBaseMEPanel (abstract)
        │     ├── MUITemplatePanel (abstract)
        │     └── MUIMEMonitorablePanel + 7个子类
        ├── MUIUpgradeablePanel (abstract) + 12个子类
        └── 17个独立面板 (CUIChestPanel, CUIRenamerPanel, ...)
```

**AEBasePanel 自身实现的能力：**
| 功能 | 实现方式 |
|---|---|
| 屏幕生命周期 | `initGui()` → `setupWidgets()` |
| 背景绘制 | `drawGuiContainerBackgroundLayer()` → `drawBG()` |
| 前景绘制 | `drawGuiContainerForegroundLayer()` → `drawFG()` |
| Widget 管理 | `addWidget(IMUIWidget)` + 遍历 `drawBackground/drawForeground` |
| Slot 绘制 | `drawSlot()` 覆盖，处理 `IMEFluidSlot`/`SlotFake`/`AppEngSlot` |
| 输入事件 | 覆盖 `mouseClicked()`, `keyTyped()`, `handleMouseClick()` |
| Slot 交互 | 覆盖 `handleMouseClick()` 将点击分发给 VirtualMESlot |
| Container 访问 | `this.inventorySlots` (GuiContainer 的 protected 字段) |
| 工具提示 | `drawTooltip()` + `drawHoveringText()` |
| 网络同步 | `detectAndSendChanges()` → container 同步 |

## 3. MUI 3.0.7 提供的基础设施

```
com.cleanroommc.modularui.screen
  ├── ModularScreen          -- 主屏幕类 (extends GuiScreen)
  ├── ModularPanel           -- 面板 (Flex 布局, FontRenderer, 主题)
  ├── GuiScreenWrapper       -- 包装 ModularScreen 为 GuiScreen
  ├── GuiContainerWrapper    -- 包装 ModularScreen 为 GuiContainer
  ├── ModularContainer       -- 对应 Container 父类
  ├── PanelManager           -- 多面板管理
  ├── DraggablePanelWrapper  -- 可拖拽面板
  └── CustomModularScreen    -- 自定义屏幕

com.cleanroommc.modularui.factory
  ├── GuiFactories           -- GUI 工厂方法
  ├── TileEntityGuiFactory   -- TileEntity GUI
  ├── SidedTileEntityGuiFactory -- 有面 TileEntity GUI
  ├── ClientGUI              -- 客户端 GUI
  └── GuiManager             -- GUI 注册管理
```

**MUI 已有的核心能力（可直接替代 AEBasePanel 的功能）：**
| AEBasePanel 功能 | MUI 对应 |
|---|---|
| 屏幕创建 | `GuiContainerWrapper.create()` |
| Widget 树 | `ModularPanel` + `widget()` 构建 |
| 背景/前景 | `drawBackground()` + `drawForeground()` 内覆写 |
| Slot 集成 | `ModularContainer` + 自动 slot 管理 |
| 输入处理 | `GuiContainerWrapper` 内部事件分发 |
| 工具提示 | `RichTooltip` 系统 |
| 主题/颜色 | `UISettings` + `GuiDraw` |
| 坐标计算 | Flex 布局 + padding/margin |
| 多面板 | `PanelManager` + `SecondaryPanel` |

## 4. 可行性分析

### 4.1 可无缝替代的功能

| 功能 | 迁移难度 | 说明 |
|---|---|---|
| `drawBG()` → MUI `drawBackground()` | **低** | `bindTexture` + `drawTexturedModalRect` 不变 |
| `drawFG()` → MUI `drawForeground()` | **低** | `fontRenderer.drawString` 不变 |
| Widget 渲染 | **低** | `IMUIWidget.drawBackground/drawForeground` 可继续使用 |
| `fontRenderer` | **低** | MUI 也使用相同 `FontRenderer` |

### 4.2 需要适配的功能

| 功能 | 迁移难度 | 说明 |
|---|---|---|
| `addWidget(IMUIWidget)` | **中** | 需改为 MUI 的 widget 系统或保留自定义 |
| `drawSlot()` | **高** | 当前覆盖 `GuiContainer.drawSlot()` 处理 `IMEFluidSlot`/`AppEngSlot` 渲染 |
| `handleMouseClick()` | **高** | 当前覆盖 `GuiContainer.handleMouseClick()` 分发到 `VirtualMESlot` |
| `mouseClicked()/keyTyped()` | **中** | 需通过 MUI 的事件系统或 `GuiContainerWrapper` 转发 |
| `inventorySlots` 访问 | **中** | 改为通过 `ModularContainer` 访问 |
| Slot 与 widget 的 z-order | **中** | 当前手动控制，MUI 自动分层 |

### 4.3 重大风险

| 风险 | 严重度 | 说明 |
|---|---|---|
| 坐标系统冲突 | **高** | 所有面板使用绝对坐标，MUI 用 Flex+相对坐标。两套系统并存可能导致双重偏移 |
| Slot 渲染断裂 | **高** | `drawSlot()` 有 120+ 行自定义逻辑（流体slot、SlotFake、AppEngSlot、icon），迁移后可能丢失功能 |
| 输入事件丢失 | **高** | 当前有大量自定义点击逻辑（`IMEFluidSlot`、VirtualME slot、drag-splitting），MUI 事件系统可能不兼容 |
| Container 桥接 | **中** | `AEBaseContainer` → `ModularContainer` 的适配需要双向修改 |
| 50+ 面板逐个测试 | **高** | 即使编译通过，每个面板都需要手动验证显示正常 |

## 5. 迁移方案（分阶段）

### 阶段 0：保持现状（推荐）

**当前架构已是功能完整的混合方案：**
- Widget 层：完全使用 MUI widget（收益最大）
- 屏幕层：保留 `GuiContainer` 的成熟绘制/输入管道

**不做迁移的理由：**
1. 50+面板功能稳定，重写风险远大于收益
2. `GuiContainer` 不是 AE2 旧系统，是 Minecraft Forge 标准 API
3. MUI 的 ModularPanel 底层也依赖 `GuiContainer`
4. Widget 层迁移已完成 ≈90% 收益，屏幕层迁移只贡献 ≈10%

### 阶段 1：渐进式试点（低风险，可选）

选择 1-2 个简单面板作试点，最小改动验证可行性：

**候选面板：**
- `MUIRenamerPanel` (116 行，最简单：1个文本框 + 1个按钮)
- `MUICondenserPanel` (简单进度条面板)
- `MUIPriorityPanel` (数字输入 + 按钮)

**试点步骤：**
1. 让试点面板继承 `ModularPanel` 而非 `AEBasePanel`
2. 面板通过 `GuiContainerWrapper.create(panel)` 构建
3. 保留 `AEBasePanel` 所有其他面板不动
4. 验证：如果试点面板在 MUI 框架下显示正常 → 考虑扩大；如果不正常 → 回退

### 阶段 2：全面迁移（高风险，仅当阶段 1 成功）

1. 重构 `AEBasePanel` → `AbstractMuiPanel extends ModularPanel`
2. 将 `AEBasePanel` 的核心逻辑（`drawBG/drawFG`、slot 渲染、输入处理）逐步搬到 `AbstractMuiPanel`
3. 所有子类改为继承 `AbstractMuiPanel`
4. 测试每个面板的视觉效果和交互

## 6. 影响文件清单

### 核心文件（必须改）

| 文件 | 行数 | 改动类型 |
|---|---|---|
| `AEBasePanel.java` | 1464 | 重写基类 |
| `AEBaseMEPanel.java` | 208 | 适配新基类 |
| `MUITemplatePanel.java` | 336 | 适配新基类 |
| `AEBasePanelGuiHandler.java` | ~200 | slot 处理适配 |
| `AEGuiHandler.java` | 447 | GUI 创建入口修改 |
| `AEMUIRegistration.java` | ~200 | 注册方式修改 |

### 所有面板（50+ 个，必须适配）

| 抽象类/子类 | 数量 |
|---|---|
| `AEBaseMEPanel` 子类 (MUIMEMonitorablePanel 等) | 10 |
| `MUIUpgradeablePanel` 子类 | 13 |
| 独立面板 (CUIChestPanel, CUIRenamerPanel, ...) | 17+ |
| `MUITemplatePanel` 子类 | 2 |
| Wireless 面板 | 6+ |

### Widget 层（可能需要适配）

| 文件 | 改动类型 |
|---|---|
| `IMUIWidget.java` | 可能需适配 MUI widget 接口 |
| 所有 ~30 个 widget 类 | slot/screen 坐标计算可能需要调整 |

## 7. 建议

**不做迁移。**

理由总结：
- 当前混合架构（MUI widget + `GuiContainer` 屏幕）功能完整、已验证
- `GuiContainer` 是 Forge 标准 API，不是可替代的"旧GUI系统"
- MUI 本身就在 `GuiContainer` 基础上运行
- 迁移收益（≈10%）远小于风险（坐标混乱、slot 渲染断裂、500+ 文件改动、全面回归测试）

若坚持要做，先从阶段 1 试点（1个简单面板）开始，验证 MUI 框架下的坐标/渲染/输入是否正常，再决定是否推进。

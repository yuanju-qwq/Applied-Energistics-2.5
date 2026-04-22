/*
 * ==================================================================================
 *  AE2.5 UIFactory 注册体系替换 GuiBridge 架构设计
 * ==================================================================================
 *
 *  文档版本: 1.0
 *  编写日期: 2026-04-21
 *  适用范围: Applied Energistics 2.5 MUI 框架重构
 *
 * ==================================================================================
 */

// ====================================================================================
//  1. 背景与现状
// ====================================================================================

/**
 * 【当前体系 — GuiBridge 枚举】
 *
 * GuiBridge 是一个同时扮演以下 4 个角色的巨型枚举：
 *
 *   1) GUI 标识符：每个枚举值代表一个唯一的 GUI 类型
 *      - 40 个枚举值对应 40 种 GUI
 *
 *   2) IGuiHandler 实现：getServerGuiElement / getClientGuiElement
 *      - 负责解析 ordinal 编码，提取 side / usingItemOnTile 信息
 *      - 根据 GuiHostType (WORLD / ITEM / ITEM_OR_WORLD) 决定宿主获取方式
 *
 *   3) 反射工厂：ConstructContainer / ConstructGui
 *      - 通过 Container 类名推导 GUI 类名（.container.Container → .client.gui.Gui）
 *      - 缓存构造函数到 ConcurrentHashMap
 *
 *   4) 安全检查：hasPermissions / securityCheck / CorrectTileOrPart
 *
 * 【问题】
 *   - 反射创建 GUI/Container：启动时通过类名字符串替换推导 GUI 类，运行时反射调用构造函数
 *   - 单文件过于庞大：567 行，职责混杂，难以维护
 *   - 无法扩展：第三方 Addon 无法注册自定义 GUI（只能修改枚举）
 *   - ordinal 编码紧耦合：GUI ID = (enumOrdinal << 4) | (usingItemOnTile << 3) | side
 *   - 所有 Block/Part/Item 直接引用 GuiBridge.GUI_XXX (53 个文件, ~150 处引用)
 */

// ====================================================================================
//  2. 目标架构 — UIFactory 注册体系
// ====================================================================================

/**
 * 【设计原则】
 *   - 渐进式替换：不一步删除 GuiBridge，而是分 4 阶段逐步迁移
 *   - 双轨运行：新旧体系并存过渡期，所有旧 GUI 保持可用
 *   - 零反射：所有 GUI/Container 通过 Lambda 工厂创建
 *   - 可扩展：第三方 Addon 可通过 API 注册自定义 GUI
 *
 * 【核心类】
 *
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  AEGuiKey (新)                                          │
 *   │  - 替代 GuiBridge 枚举的标识符角色                        │
 *   │  - ResourceLocation 作为唯一键 (ae2:me_terminal 等)       │
 *   │  - 包含元数据: GuiHostType, SecurityPermissions, tileClass│
 *   │  - 所有键在 AEGuiKeys 常量类中定义                        │
 *   └─────────────────────────────────────────────────────────┘
 *                          │
 *                          ▼
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  AEMUIGuiFactory (已有, 需升级)                          │
 *   │  - 注册表: Map<AEGuiKey, Registration>                   │
 *   │  - Registration = ContainerFactory + GuiFactory           │
 *   │  - register(AEGuiKey, ContainerFactory, GuiFactory)      │
 *   │  - createContainer(AEGuiKey, player, world, pos, side)   │
 *   │  - createGui(AEGuiKey, player, world, pos, side)         │
 *   └─────────────────────────────────────────────────────────┘
 *                          │
 *                          ▼
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  AEGuiHandler (新, 替代 GuiBridge 的 IGuiHandler 角色)   │
 *   │  - 实现 IGuiHandler                                      │
 *   │  - getServerGuiElement: 解析宿主 → 调用 AEMUIGuiFactory │
 *   │  - getClientGuiElement: 解析宿主 → 调用 AEMUIGuiFactory │
 *   │  - 宿主解析逻辑从 GuiBridge 中提取并优化                  │
 *   └─────────────────────────────────────────────────────────┘
 *                          │
 *                          ▼
 *   ┌─────────────────────────────────────────────────────────┐
 *   │  AEMUIRegistration (已有, 需扩展)                        │
 *   │  - 在客户端 init 时注册所有 MUI 面板                      │
 *   │  - 当前使用 GuiBridge.setMuiGuiFactory()                 │
 *   │  - 升级后使用 AEMUIGuiFactory.register(AEGuiKey, ...)    │
 *   └─────────────────────────────────────────────────────────┘
 */

// ====================================================================================
//  3. 分阶段实施计划
// ====================================================================================

/**
 * 【阶段 1 — 完成所有 MUI 面板注册】（当前进度: 70%）
 *
 * 目标: 让所有 40 个 GuiBridge 枚举值都有对应的 MUI 面板注册
 *
 * 已注册 (25 个):
 *   - 存储设备: GUI_CHEST, GUI_DRIVE, GUI_CELL_WORKBENCH, GUI_PORTABLE_CELL
 *   - 合成设备: GUI_MAC, GUI_INSCRIBER
 *   - 工具/杂项: GUI_PRIORITY, GUI_SECURITY, GUI_NETWORK_STATUS, GUI_NETWORK_TOOL,
 *     GUI_WIRELESS, GUI_SPATIAL_IO_PORT, GUI_CONDENSER, GUI_VIBRATION_CHAMBER,
 *     GUI_GRINDER, GUI_QNB, GUI_SKYCHEST, GUI_QUARTZ_KNIFE, GUI_RENAMER, GUI_OREDICTSTORAGEBUS
 *   - 无线终端: GUI_WIRELESS_TERM, GUI_WIRELESS_CRAFTING_TERMINAL,
 *     GUI_WIRELESS_PATTERN_TERMINAL, GUI_WIRELESS_INTERFACE_TERMINAL,
 *     GUI_WIRELESS_DUAL_INTERFACE_TERMINAL
 *
 * 待注册 (15 个):
 *   - 通用终端: GUI_ME
 *   - 合成终端: GUI_CRAFTING_TERMINAL
 *   - 样板终端: GUI_PATTERN_TERMINAL, GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL
 *   - 接口: GUI_INTERFACE, GUI_FLUID_INTERFACE, GUI_DUAL_ITEM_INTERFACE, GUI_DUAL_FLUID_INTERFACE
 *   - 总线: GUI_BUS, GUI_BUS_FLUID, GUI_STORAGEBUS, GUI_STORAGEBUS_FLUID
 *   - 其他: GUI_FORMATION_PLANE, GUI_FLUID_FORMATION_PLANE
 *   - Level Emitter: GUI_LEVEL_EMITTER, GUI_FLUID_LEVEL_EMITTER
 *   - IO Port: GUI_IOPORT
 *   - 合成子系统: GUI_CRAFTING_CPU, GUI_CRAFTING_AMOUNT, GUI_CRAFTING_CONFIRM, GUI_CRAFTING_STATUS
 *   - 接口终端: GUI_INTERFACE_TERMINAL, GUI_INTERFACE_CONFIGURATION_TERMINAL,
 *     GUI_FLUID_INTERFACE_CONFIGURATION_TERMINAL
 *   - 样板值: GUI_PATTERN_VALUE_AMOUNT, GUI_PATTERN_VALUE_NAME
 *   - 流体终端: GUI_FLUID_TERMINAL (Deprecated)
 *
 * 操作:
 *   1. 在 AEMUIRegistration.registerAll() 中逐一添加 setMuiGuiFactory() 调用
 *   2. 对于有编译错误的 MUI 面板，先修复编译问题
 *   3. 每批注册后编译验证
 *
 * 涉及文件: ~5 个
 * 风险: 低
 */

/**
 * 【阶段 2 — 创建 AEGuiKey 标识符体系 + 升级 AEMUIGuiFactory】
 *
 * 目标: 建立新的 GUI 标识符体系，不依赖 GuiBridge 枚举
 *
 * 操作:
 *   1. 创建 AEGuiKey 类:
 *      - ResourceLocation id (如 "ae2:me_terminal")
 *      - GuiHostType hostType (WORLD / ITEM / ITEM_OR_WORLD)
 *      - SecurityPermissions requiredPermission (nullable)
 *      - Class<?> tileClass (用于 CorrectTileOrPart 检查)
 *      - 构建器模式: AEGuiKey.builder("ae2:me_terminal").hostType(WORLD).permission(null).build()
 *
 *   2. 创建 AEGuiKeys 常量类:
 *      - 为每个 GuiBridge 枚举值创建对应的 AEGuiKey 常量
 *      - 例: public static final AEGuiKey ME_TERMINAL = AEGuiKey.builder("ae2:me_terminal")...build()
 *
 *   3. 升级 AEMUIGuiFactory:
 *      - 注册表键从 GuiBridge → AEGuiKey
 *      - 工厂接口签名升级: (EntityPlayer, World, int, int, int) → (EntityPlayer, Object hostObject)
 *      - 新增: IContainerFactory<T> = (InventoryPlayer, T host) → Container
 *      - 新增: IGuiFactory<T> = (InventoryPlayer, T host) → Object
 *      - 保留与 GuiBridge 的兼容桥接方法
 *
 *   4. 在每个 AEGuiKey 上添加与 GuiBridge 枚举的映射:
 *      - AEGuiKey.fromGuiBridge(GuiBridge gb) 查询映射
 *      - GuiBridge.getGuiKey() 返回对应的 AEGuiKey
 *
 * 涉及文件: ~5 个新建 + ~3 个修改
 * 风险: 低（纯新增，不修改现有逻辑）
 */

/**
 * 【阶段 3 — 创建 AEGuiHandler + 替换 GuiBridge 的 IGuiHandler 角色】
 *
 * 目标: 将 GuiBridge 的 IGuiHandler 职责移到新的 AEGuiHandler
 *
 * 操作:
 *   1. 创建 AEGuiHandler implements IGuiHandler:
 *      - 从 GuiBridge 中提取宿主解析逻辑 (TileEntity/Part/Item 获取)
 *      - getServerGuiElement: 解析宿主 → 安全检查 → AEMUIGuiFactory.createContainer()
 *      - getClientGuiElement: 解析宿主 → AEMUIGuiFactory.createGui()
 *      - 重用 ordinal 编码格式以保持网络兼容
 *
 *   2. 在 AppEng.init() 中注册 AEGuiHandler:
 *      - NetworkRegistry.INSTANCE.registerGuiHandler(AppEng.instance(), new AEGuiHandler())
 *      - 替换原来的 GuiBridge.GUI_Handler
 *
 *   3. 更新 GuiBridge:
 *      - getServerGuiElement / getClientGuiElement 委托给 AEGuiHandler
 *      - 或直接替换注册的 IGuiHandler
 *
 * 涉及文件: ~3 个新建 + ~2 个修改
 * 风险: 中（修改 GUI 打开路径的核心代码）
 */

/**
 * 【阶段 4 — 全局替换 GuiBridge.GUI_XXX 引用 → AEGuiKeys.XXX】
 *
 * 目标: 消除所有 Block/Part/Item 对 GuiBridge 枚举值的直接引用
 *
 * 操作:
 *   1. 在所有 Block/Part/Item 中替换:
 *
 *      旧代码:
 *        Platform.openGUI(player, te, AEPartLocation.INTERNAL, GuiBridge.GUI_CHEST);
 *
 *      新代码:
 *        Platform.openGUI(player, te, AEPartLocation.INTERNAL, AEGuiKeys.CHEST);
 *
 *   2. 更新 Platform.openGUI() 方法:
 *      - 接受 AEGuiKey 参数
 *      - 通过 AEGuiKey 查找 ordinal（过渡期）或直接用 ResourceLocation 编码
 *
 *   3. 更新 PacketSwitchGuis:
 *      - 网络包中的 GUI 标识符从 GuiBridge ordinal → AEGuiKey ResourceLocation
 *      - 保留 ordinal 格式的兼容解码器
 *
 *   4. 更新 WirelessTerminalMode:
 *      - GuiBridge 引用替换为 AEGuiKey
 *
 * 涉及文件: 53 个 (所有引用 GuiBridge.GUI_* 的文件)
 *
 * 具体文件清单:
 *   Block 层 (14 个):
 *     - BlockGrinder, BlockQuantumLinkChamber, BlockSkyChest, BlockChest,
 *       BlockDrive, BlockIOPort, BlockWireless, BlockSpatialIOPort,
 *       BlockCellWorkbench, BlockCondenser, BlockInscriber, BlockSecurityStation,
 *       BlockVibrationChamber, BlockMolecularAssembler, BlockCraftingUnit,
 *       BlockInterface, BlockDualInterface, BlockFluidInterface
 *
 *   Part 层 (15 个):
 *     - PartStorageBus, PartOreDicStorageBus, PartFluidStorageBus,
 *       PartFormationPlane, PartFluidFormationPlane, PartLevelEmitter,
 *       PartFluidLevelEmitter, PartCraftingTerminal, PartPatternTerminal,
 *       PartExpandedProcessingPatternTerminal, PartInterfaceTerminal,
 *       PartInterfaceConfigurationTerminal, PartFluidInterfaceConfigurationTerminal,
 *       PartFluidTerminal, AEBasePart, PartSharedFluidBus,
 *       PartInterface, PartDualInterface, PartFluidInterface
 *
 *   Item 层 (8 个):
 *     - ToolPortableCell, ToolWirelessTerminal, ToolWirelessCraftingTerminal,
 *       ToolWirelessPatternTerminal, ToolWirelessFluidTerminal,
 *       ToolWirelessInterfaceTerminal, ToolWirelessDualInterfaceTerminal,
 *       ToolQuartzCuttingKnife, ToolNetworkTool, Terminal
 *
 *   核心/网络层 (5 个):
 *     - AppEng, PacketSwitchGuis, PacketInventoryAction, PacketCraftRequest,
 *       WirelessTerminalMode
 *
 *   旧 GUI 层 (11 个, 阶段 5 删除时处理):
 *     - GuiMEMonitorable, GuiCraftConfirm, GuiCraftAmount, GuiChest, GuiDrive,
 *       GuiFormationPlane, GuiOreDictStorageBus, GuiStorageBus,
 *       GuiCraftingStatus, GuiDualItemInterface, 等
 *
 * 风险: 高（大批量文件修改，需要全量测试）
 */

/**
 * 【阶段 5 — 删除旧基类和旧 GUI】
 *
 * 目标: 清除所有遗留的旧 GUI 代码
 *
 * 前提: 所有 40 个 GUI 都通过 MUI 面板渲染，GuiBridge 不再做反射调用
 *
 * 操作:
 *   1. 删除旧 GUI 基类:
 *      - AEBaseGui (1206 行)
 *      - AEBaseMEGui (170 行)
 *
 *   2. 删除所有旧 GUI 实现类 (26 + 4 + 7 = 37 个):
 *      - 直接继承 AEBaseGui 的 26 个类
 *      - 直接继承 AEBaseMEGui 的 4 个类
 *      - 继承 GuiMEMonitorable 的 7 个类
 *      - 继承 GuiUpgradeable 的若干子类
 *      - 继承 GuiFluidTerminal 的子类
 *      - 继承 GuiPatternTerm 的子类
 *
 *   3. 删除 GuiBridge 中的反射相关代码:
 *      - getGui() 方法
 *      - guiClass 字段
 *      - guiConstructors 缓存
 *      - containerConstructors 缓存
 *      - ConstructGui() 中的反射分支
 *      - ConstructContainer() 中的反射分支
 *
 *   4. 清理 GuiBridge 为纯数据枚举:
 *      - 只保留: containerClass, tileClass, type, requiredPermission
 *      - 或完全删除 GuiBridge，改用 AEGuiKeys
 *
 * 涉及文件: ~40 个删除 + ~5 个修改
 * 风险: 高（需要完整的游戏内测试验证所有 GUI）
 */

// ====================================================================================
//  4. AEGuiKey 类设计
// ====================================================================================

/**
 * <pre>
 * public final class AEGuiKey {
 *
 *     private final ResourceLocation id;
 *     private final GuiHostType hostType;
 *     @Nullable private final SecurityPermissions requiredPermission;
 *     private final Class<?> hostClass;
 *
 *     // 与 GuiBridge 的兼容映射（过渡期使用）
 *     @Nullable private GuiBridge legacyBridge;
 *
 *     private AEGuiKey(Builder builder) { ... }
 *
 *     public ResourceLocation getId() { return id; }
 *     public GuiHostType getHostType() { return hostType; }
 *     public SecurityPermissions getRequiredPermission() { return requiredPermission; }
 *     public Class<?> getHostClass() { return hostClass; }
 *
 *     public boolean isValidHost(Object host) {
 *         return hostClass.isInstance(host);
 *     }
 *
 *     public static Builder builder(String namespace, String path) {
 *         return new Builder(new ResourceLocation(namespace, path));
 *     }
 *
 *     public static class Builder {
 *         private ResourceLocation id;
 *         private GuiHostType hostType = GuiHostType.WORLD;
 *         private SecurityPermissions permission;
 *         private Class<?> hostClass = Object.class;
 *         private GuiBridge legacyBridge;
 *
 *         public Builder hostType(GuiHostType type) { ... }
 *         public Builder permission(SecurityPermissions perm) { ... }
 *         public Builder hostClass(Class<?> cls) { ... }
 *         public Builder legacyBridge(GuiBridge bridge) { ... }
 *         public AEGuiKey build() { ... }
 *     }
 * }
 * </pre>
 */

// ====================================================================================
//  5. AEGuiKeys 常量类设计
// ====================================================================================

/**
 * <pre>
 * public final class AEGuiKeys {
 *
 *     // ===== 存储设备 =====
 *     public static final AEGuiKey CHEST = AEGuiKey.builder("ae2", "chest")
 *             .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
 *             .hostClass(TileChest.class).legacyBridge(GuiBridge.GUI_CHEST).build();
 *
 *     public static final AEGuiKey DRIVE = AEGuiKey.builder("ae2", "drive")
 *             .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
 *             .hostClass(TileDrive.class).legacyBridge(GuiBridge.GUI_DRIVE).build();
 *
 *     // ... 其他所有 40 个键
 *
 *     // ===== 查询方法 =====
 *     private static final Map<GuiBridge, AEGuiKey> LEGACY_MAP = new IdentityHashMap<>();
 *
 *     public static AEGuiKey fromLegacy(GuiBridge bridge) {
 *         return LEGACY_MAP.get(bridge);
 *     }
 *
 *     static {
 *         // 批量注册映射
 *         for (Field f : AEGuiKeys.class.getDeclaredFields()) {
 *             if (f.getType() == AEGuiKey.class) {
 *                 AEGuiKey key = (AEGuiKey) f.get(null);
 *                 if (key.getLegacyBridge() != null) {
 *                     LEGACY_MAP.put(key.getLegacyBridge(), key);
 *                 }
 *             }
 *         }
 *     }
 * }
 * </pre>
 */

// ====================================================================================
//  6. AEGuiHandler 设计
// ====================================================================================

/**
 * <pre>
 * public class AEGuiHandler implements IGuiHandler {
 *
 *     // ordinal 编码格式保持兼容:
 *     // bits [7:4] = guiKeyIndex, [3] = usingItemOnTile, [2:0] = side
 *
 *     @Override
 *     public Object getServerGuiElement(int ordinal, EntityPlayer player, World w, int x, int y, int z) {
 *         AEPartLocation side = AEPartLocation.fromOrdinal(ordinal & 0x07);
 *         boolean usingItemOnTile = ((ordinal >> 3) & 1) == 1;
 *         AEGuiKey key = resolveKey(ordinal >> 4);
 *
 *         Object host = resolveHost(key, player, w, x, y, z, side, usingItemOnTile);
 *         if (host == null) return new ContainerNull();
 *
 *         if (!checkSecurity(key, host, player)) return new ContainerNull();
 *
 *         Container container = AEMUIGuiFactory.createContainer(key, player.inventory, host);
 *         return updateContext(container, w, x, y, z, side, host);
 *     }
 *
 *     @Override
 *     public Object getClientGuiElement(int ordinal, EntityPlayer player, World w, int x, int y, int z) {
 *         AEPartLocation side = AEPartLocation.fromOrdinal(ordinal & 0x07);
 *         boolean usingItemOnTile = ((ordinal >> 3) & 1) == 1;
 *         AEGuiKey key = resolveKey(ordinal >> 4);
 *
 *         Object host = resolveHost(key, player, w, x, y, z, side, usingItemOnTile);
 *         if (host == null) return new GuiNull(new ContainerNull());
 *
 *         return AEMUIGuiFactory.createGui(key, player.inventory, host);
 *     }
 *
 *     private Object resolveHost(AEGuiKey key, ...) {
 *         // 从 GuiBridge 提取的宿主解析逻辑
 *         if (key.getHostType().isItem()) { ... }
 *         if (key.getHostType().isTile()) { ... }
 *     }
 * }
 * </pre>
 */

// ====================================================================================
//  7. 升级后的 AEMUIGuiFactory 设计
// ====================================================================================

/**
 * <pre>
 * public final class AEMUIGuiFactory {
 *
 *     // 工厂接口升级: 接受 (InventoryPlayer, hostObject) 而非 (EntityPlayer, World, int, int, int)
 *
 *     @FunctionalInterface
 *     public interface IContainerFactory {
 *         Container createContainer(InventoryPlayer inventory, Object host);
 *     }
 *
 *     @FunctionalInterface
 *     public interface IGuiFactory {
 *         @SideOnly(Side.CLIENT)
 *         Object createGui(InventoryPlayer inventory, Object host);
 *     }
 *
 *     // 注册表
 *     private static final Map<AEGuiKey, Registration> registry = new IdentityHashMap<>();
 *
 *     // 与 GuiBridge 的兼容注册
 *     private static final Map<GuiBridge, Registration> legacyRegistry = new IdentityHashMap<>();
 *
 *     public static void register(AEGuiKey key, IContainerFactory containerFactory, IGuiFactory guiFactory) {
 *         registry.put(key, new Registration(containerFactory, guiFactory));
 *         // 自动添加兼容映射
 *         if (key.getLegacyBridge() != null) {
 *             legacyRegistry.put(key.getLegacyBridge(), registry.get(key));
 *         }
 *     }
 *
 *     // 新接口
 *     public static Container createContainer(AEGuiKey key, InventoryPlayer ip, Object host) { ... }
 *     public static Object createGui(AEGuiKey key, InventoryPlayer ip, Object host) { ... }
 *
 *     // 兼容接口（过渡期使用，阶段 4 完成后可删除）
 *     public static Container createContainer(GuiBridge key, EntityPlayer player, World w, int x, int y, int z) { ... }
 *     public static Object createGui(GuiBridge key, EntityPlayer player, World w, int x, int y, int z) { ... }
 * }
 * </pre>
 */

// ====================================================================================
//  8. Platform.openGUI() 升级设计
// ====================================================================================

/**
 * <pre>
 * public class Platform {
 *
 *     // 新的 openGUI 方法
 *     public static void openGUI(EntityPlayer p, TileEntity te, AEPartLocation side, AEGuiKey key) {
 *         // 使用 AEGuiKey 的内部索引编码 ordinal
 *         int guiId = (key.getIndex() << 4) | side.ordinal();
 *         p.openGui(AppEng.instance(), guiId, te.getWorld(),
 *                   te.getPos().getX(), te.getPos().getY(), te.getPos().getZ());
 *     }
 *
 *     // 兼容旧调用（过渡期保留，标记 @Deprecated）
 *     @Deprecated
 *     public static void openGUI(EntityPlayer p, TileEntity te, AEPartLocation side, GuiBridge gui) {
 *         openGUI(p, te, side, AEGuiKeys.fromLegacy(gui));
 *     }
 * }
 * </pre>
 */

// ====================================================================================
//  9. 迁移检查表
// ====================================================================================

/**
 * 阶段 1 检查表:
 *   □ 所有 40 个 GuiBridge 枚举值都有 setMuiGuiFactory() 注册
 *   □ 所有 MUI 面板编译无错误
 *   □ 游戏内测试所有 GUI 正常打开
 *
 * 阶段 2 检查表:
 *   □ AEGuiKey 类创建完成
 *   □ AEGuiKeys 常量类包含所有 40 个键
 *   □ AEMUIGuiFactory 升级完成，支持双键查询
 *   □ 所有 MUI 面板通过新接口注册
 *   □ 兼容桥接方法工作正常
 *
 * 阶段 3 检查表:
 *   □ AEGuiHandler 创建完成
 *   □ AppEng.init() 注册 AEGuiHandler
 *   □ GuiBridge 的 IGuiHandler 职责已委托
 *   □ 所有 GUI 打开路径测试通过
 *
 * 阶段 4 检查表:
 *   □ 14 个 Block 文件中的 GuiBridge.GUI_XXX 替换为 AEGuiKeys.XXX
 *   □ 15 个 Part 文件中的引用替换
 *   □ 8 个 Item 文件中的引用替换
 *   □ 5 个核心/网络文件中的引用替换
 *   □ Platform.openGUI() 旧签名标记 @Deprecated
 *   □ PacketSwitchGuis 更新为使用 AEGuiKey
 *   □ 全量游戏内测试
 *
 * 阶段 5 检查表:
 *   □ 37 个旧 GUI 实现类删除
 *   □ AEBaseGui、AEBaseMEGui 删除
 *   □ GuiBridge 中反射代码删除
 *   □ 兼容桥接方法删除
 *   □ 全量回归测试
 */

// ====================================================================================
//  10. 风险评估
// ====================================================================================

/**
 * 【风险 1 — 第三方 Addon 兼容性】
 *   - 使用 GuiBridge 枚举 ordinal 的 Addon 会受阶段 4 影响
 *   - 缓解: 保留 GuiBridge 枚举在阶段 5 之后的一个版本周期
 *   - 缓解: 提供 AEGuiKeys.fromLegacy() 适配器
 *
 * 【风险 2 — 网络协议兼容性】
 *   - ordinal 编码格式变更会导致跨版本连接问题
 *   - 缓解: 阶段 3 保持 ordinal 编码格式不变（(index << 4) | side）
 *   - 缓解: AEGuiKey.getIndex() 使用与 GuiBridge.ordinal() 相同的值
 *
 * 【风险 3 — 合成确认/状态 GUI 链路】
 *   - CraftAmount → CraftConfirm → CraftingCPU 之间的 GUI 跳转
 *   - PacketSwitchGuis 用 GuiBridge 枚举传递目标 GUI
 *   - 缓解: 阶段 4 中同步更新所有跳转逻辑
 *
 * 【风险 4 — 旧 GUI 中的特殊逻辑】
 *   - GuiMEMonitorable 的 TypeToggle (Items/Fluids/Both 切换)
 *   - GuiPatternTerm 的 4×4 扩展处理模式
 *   - GuiCraftingCPU 的 CPUID 追踪
 *   - 缓解: 确认所有特殊逻辑在 MUI 面板中已复制
 */

// ====================================================================================
//  11. 文件变更统计
// ====================================================================================

/**
 * 阶段 1: ~5 个文件修改
 * 阶段 2: ~5 个新建 + ~3 个修改
 * 阶段 3: ~3 个新建 + ~2 个修改
 * 阶段 4: ~53 个修改
 * 阶段 5: ~40 个删除 + ~5 个修改
 *
 * 总计: ~8 个新建, ~68 个修改, ~40 个删除
 */

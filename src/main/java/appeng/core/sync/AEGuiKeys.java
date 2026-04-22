/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.sync;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.api.config.SecurityPermissions;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.storage.ITerminalHost;
import appeng.core.AppEng;
import appeng.fluids.helper.IFluidInterfaceHost;
import appeng.fluids.parts.PartFluidFormationPlane;
import appeng.fluids.parts.PartFluidLevelEmitter;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.parts.PartSharedFluidBus;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.QuartzKnifeObj;
import appeng.parts.automation.PartFormationPlane;
import appeng.parts.automation.PartLevelEmitter;
import appeng.parts.misc.PartOreDicStorageBus;
import appeng.parts.misc.PartStorageBus;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartFluidInterfaceConfigurationTerminal;
import appeng.parts.reporting.PartInterfaceConfigurationTerminal;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.crafting.TileCraftingTile;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.grindstone.TileGrinder;
import appeng.tile.misc.TileCellWorkbench;
import appeng.tile.misc.TileCondenser;
import appeng.tile.misc.TileInscriber;
import appeng.tile.misc.TileSecurityStation;
import appeng.tile.misc.TileVibrationChamber;
import appeng.tile.networking.TileWireless;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.tile.spatial.TileSpatialIOPort;
import appeng.tile.storage.TileChest;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileIOPort;
import appeng.tile.storage.TileSkyChest;

/**
 * 所有预定义 {@link AEGuiKey} 常量。
 * <p>
 * 每个常量与一个 {@link GuiBridge} 枚举值一一对应，用于渐进式替换。
 * 通过 {@link #fromLegacy(GuiBridge)} 可以从旧枚举值查询到对应的 {@code AEGuiKey}。
 *
 * <h3>命名规范</h3>
 * 常量名去除 {@code GUI_} 前缀，使用 UPPER_SNAKE_CASE。
 * ResourceLocation 路径使用 lower_snake_case。
 *
 * @see AEGuiKey
 * @see GuiBridge
 */
public final class AEGuiKeys {

    private AEGuiKeys() {
    }

    // ========== 存储设备 ==========

    public static final AEGuiKey CHEST = key("chest")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileChest.class).legacyBridge(GuiBridge.GUI_CHEST).build();

    public static final AEGuiKey DRIVE = key("drive")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileDrive.class).legacyBridge(GuiBridge.GUI_DRIVE).build();

    public static final AEGuiKey CELL_WORKBENCH = key("cell_workbench")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileCellWorkbench.class).legacyBridge(GuiBridge.GUI_CELL_WORKBENCH).build();

    public static final AEGuiKey PORTABLE_CELL = key("portable_cell")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(IPortableCell.class).legacyBridge(GuiBridge.GUI_PORTABLE_CELL).build();

    // ========== 合成设备 ==========

    public static final AEGuiKey MAC = key("mac")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileMolecularAssembler.class).legacyBridge(GuiBridge.GUI_MAC).build();

    public static final AEGuiKey INSCRIBER = key("inscriber")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileInscriber.class).legacyBridge(GuiBridge.GUI_INSCRIBER).build();

    // ========== 工具/杂项 ==========

    public static final AEGuiKey GRINDER = key("grinder")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileGrinder.class).legacyBridge(GuiBridge.GUI_GRINDER).build();

    public static final AEGuiKey QNB = key("qnb")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileQuantumBridge.class).legacyBridge(GuiBridge.GUI_QNB).build();

    public static final AEGuiKey SKY_CHEST = key("sky_chest")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileSkyChest.class).legacyBridge(GuiBridge.GUI_SKYCHEST).build();

    public static final AEGuiKey PRIORITY = key("priority")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IPriorityHost.class).legacyBridge(GuiBridge.GUI_PRIORITY).build();

    public static final AEGuiKey SECURITY = key("security")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.SECURITY)
            .hostClass(TileSecurityStation.class).legacyBridge(GuiBridge.GUI_SECURITY).build();

    public static final AEGuiKey NETWORK_STATUS = key("network_status")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(INetworkTool.class).legacyBridge(GuiBridge.GUI_NETWORK_STATUS).build();

    public static final AEGuiKey NETWORK_TOOL = key("network_tool")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(INetworkTool.class).legacyBridge(GuiBridge.GUI_NETWORK_TOOL).build();

    public static final AEGuiKey WIRELESS = key("wireless")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileWireless.class).legacyBridge(GuiBridge.GUI_WIRELESS).build();

    public static final AEGuiKey SPATIAL_IO_PORT = key("spatial_io_port")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileSpatialIOPort.class).legacyBridge(GuiBridge.GUI_SPATIAL_IO_PORT).build();

    public static final AEGuiKey CONDENSER = key("condenser")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileCondenser.class).legacyBridge(GuiBridge.GUI_CONDENSER).build();

    public static final AEGuiKey VIBRATION_CHAMBER = key("vibration_chamber")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(TileVibrationChamber.class).legacyBridge(GuiBridge.GUI_VIBRATION_CHAMBER).build();

    public static final AEGuiKey QUARTZ_KNIFE = key("quartz_knife")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(QuartzKnifeObj.class).legacyBridge(GuiBridge.GUI_QUARTZ_KNIFE).build();

    public static final AEGuiKey RENAMER = key("renamer")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(ICustomNameObject.class).legacyBridge(GuiBridge.GUI_RENAMER).build();

    public static final AEGuiKey ORE_DICT_STORAGE_BUS = key("ore_dict_storage_bus")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartOreDicStorageBus.class).legacyBridge(GuiBridge.GUI_OREDICTSTORAGEBUS).build();

    // ========== 通用/合成/样板终端 ==========

    public static final AEGuiKey ME_TERMINAL = key("me_terminal")
            .hostType(GuiHostType.WORLD).permission(null)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_ME).build();

    public static final AEGuiKey CRAFTING_TERMINAL = key("crafting_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(PartCraftingTerminal.class).legacyBridge(GuiBridge.GUI_CRAFTING_TERMINAL).build();

    public static final AEGuiKey PATTERN_TERMINAL = key("pattern_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(PartPatternTerminal.class).legacyBridge(GuiBridge.GUI_PATTERN_TERMINAL).build();

    public static final AEGuiKey EXPANDED_PROCESSING_PATTERN_TERMINAL = key("expanded_processing_pattern_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(PartExpandedProcessingPatternTerminal.class)
            .legacyBridge(GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL).build();

    @Deprecated
    public static final AEGuiKey FLUID_TERMINAL = key("fluid_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_FLUID_TERMINAL).build();

    // ========== 无线终端 ==========

    public static final AEGuiKey WIRELESS_TERM = key("wireless_term")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class).legacyBridge(GuiBridge.GUI_WIRELESS_TERM).build();

    public static final AEGuiKey WIRELESS_CRAFTING_TERMINAL = key("wireless_crafting_terminal")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class).legacyBridge(GuiBridge.GUI_WIRELESS_CRAFTING_TERMINAL).build();

    public static final AEGuiKey WIRELESS_PATTERN_TERMINAL = key("wireless_pattern_terminal")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class).legacyBridge(GuiBridge.GUI_WIRELESS_PATTERN_TERMINAL).build();

    @Deprecated
    public static final AEGuiKey WIRELESS_FLUID_TERMINAL = key("wireless_fluid_terminal")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class).legacyBridge(GuiBridge.GUI_WIRELESS_FLUID_TERMINAL).build();

    public static final AEGuiKey WIRELESS_INTERFACE_TERMINAL = key("wireless_interface_terminal")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class).legacyBridge(GuiBridge.GUI_WIRELESS_INTERFACE_TERMINAL).build();

    public static final AEGuiKey WIRELESS_DUAL_INTERFACE_TERMINAL = key("wireless_dual_interface_terminal")
            .hostType(GuiHostType.ITEM).permission(null)
            .hostClass(WirelessTerminalGuiObject.class)
            .legacyBridge(GuiBridge.GUI_WIRELESS_DUAL_INTERFACE_TERMINAL).build();

    // ========== 接口设置 ==========

    public static final AEGuiKey INTERFACE = key("interface")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IInterfaceHost.class).legacyBridge(GuiBridge.GUI_INTERFACE).build();

    public static final AEGuiKey FLUID_INTERFACE = key("fluid_interface")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IFluidInterfaceHost.class).legacyBridge(GuiBridge.GUI_FLUID_INTERFACE).build();

    public static final AEGuiKey DUAL_ITEM_INTERFACE = key("dual_item_interface")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IInterfaceHost.class).legacyBridge(GuiBridge.GUI_DUAL_ITEM_INTERFACE).build();

    public static final AEGuiKey DUAL_FLUID_INTERFACE = key("dual_fluid_interface")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IFluidInterfaceHost.class).legacyBridge(GuiBridge.GUI_DUAL_FLUID_INTERFACE).build();

    // ========== 总线/面板 ==========

    public static final AEGuiKey BUS = key("bus")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(IUpgradeableHost.class).legacyBridge(GuiBridge.GUI_BUS).build();

    public static final AEGuiKey BUS_FLUID = key("bus_fluid")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartSharedFluidBus.class).legacyBridge(GuiBridge.GUI_BUS_FLUID).build();

    public static final AEGuiKey STORAGE_BUS = key("storage_bus")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartStorageBus.class).legacyBridge(GuiBridge.GUI_STORAGEBUS).build();

    public static final AEGuiKey STORAGE_BUS_FLUID = key("storage_bus_fluid")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartFluidStorageBus.class).legacyBridge(GuiBridge.GUI_STORAGEBUS_FLUID).build();

    public static final AEGuiKey FORMATION_PLANE = key("formation_plane")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartFormationPlane.class).legacyBridge(GuiBridge.GUI_FORMATION_PLANE).build();

    public static final AEGuiKey FLUID_FORMATION_PLANE = key("fluid_formation_plane")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartFluidFormationPlane.class).legacyBridge(GuiBridge.GUI_FLUID_FORMATION_PLANE).build();

    public static final AEGuiKey LEVEL_EMITTER = key("level_emitter")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartLevelEmitter.class).legacyBridge(GuiBridge.GUI_LEVEL_EMITTER).build();

    public static final AEGuiKey FLUID_LEVEL_EMITTER = key("fluid_level_emitter")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartFluidLevelEmitter.class).legacyBridge(GuiBridge.GUI_FLUID_LEVEL_EMITTER).build();

    // ========== IO/合成子系统 ==========

    public static final AEGuiKey IO_PORT = key("io_port")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(TileIOPort.class).legacyBridge(GuiBridge.GUI_IOPORT).build();

    public static final AEGuiKey CRAFTING_CPU = key("crafting_cpu")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(TileCraftingTile.class).legacyBridge(GuiBridge.GUI_CRAFTING_CPU).build();

    public static final AEGuiKey CRAFTING_AMOUNT = key("crafting_amount")
            .hostType(GuiHostType.ITEM_OR_WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_CRAFTING_AMOUNT).build();

    public static final AEGuiKey CRAFTING_CONFIRM = key("crafting_confirm")
            .hostType(GuiHostType.ITEM_OR_WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_CRAFTING_CONFIRM).build();

    public static final AEGuiKey CRAFTING_STATUS = key("crafting_status")
            .hostType(GuiHostType.ITEM_OR_WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_CRAFTING_STATUS).build();

    // ========== 接口终端 ==========

    public static final AEGuiKey INTERFACE_TERMINAL = key("interface_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartInterfaceTerminal.class).legacyBridge(GuiBridge.GUI_INTERFACE_TERMINAL).build();

    public static final AEGuiKey INTERFACE_CONFIGURATION_TERMINAL = key("interface_configuration_terminal")
            .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
            .hostClass(PartInterfaceConfigurationTerminal.class)
            .legacyBridge(GuiBridge.GUI_INTERFACE_CONFIGURATION_TERMINAL).build();

    public static final AEGuiKey FLUID_INTERFACE_CONFIGURATION_TERMINAL =
            key("fluid_interface_configuration_terminal")
                    .hostType(GuiHostType.WORLD).permission(SecurityPermissions.BUILD)
                    .hostClass(PartFluidInterfaceConfigurationTerminal.class)
                    .legacyBridge(GuiBridge.GUI_FLUID_INTERFACE_CONFIGURATION_TERMINAL).build();

    // ========== 样板值设置 ==========

    public static final AEGuiKey PATTERN_VALUE_AMOUNT = key("pattern_value_amount")
            .hostType(GuiHostType.ITEM_OR_WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_PATTERN_VALUE_AMOUNT).build();

    public static final AEGuiKey PATTERN_VALUE_NAME = key("pattern_value_name")
            .hostType(GuiHostType.ITEM_OR_WORLD).permission(SecurityPermissions.CRAFT)
            .hostClass(ITerminalHost.class).legacyBridge(GuiBridge.GUI_PATTERN_VALUE_NAME).build();

    // ========== 映射表 ==========

    /**
     * GuiBridge → AEGuiKey 的反向映射表。
     * 在类加载时自动填充。
     */
    private static final Map<GuiBridge, AEGuiKey> LEGACY_MAP = new IdentityHashMap<>();

    /**
     * ResourceLocation → AEGuiKey 的映射表，用于网络包解码。
     * 在类加载时自动填充。
     */
    private static final Map<ResourceLocation, AEGuiKey> ID_MAP = new HashMap<>();

    static {
        // 反射遍历所有 static final AEGuiKey 字段，自动填充映射表
        try {
            for (java.lang.reflect.Field f : AEGuiKeys.class.getDeclaredFields()) {
                if (f.getType() == AEGuiKey.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    AEGuiKey guiKey = (AEGuiKey) f.get(null);
                    ID_MAP.put(guiKey.getId(), guiKey);
                    if (guiKey.getLegacyBridge() != null) {
                        LEGACY_MAP.put(guiKey.getLegacyBridge(), guiKey);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to initialize AEGuiKeys legacy map", e);
        }
    }

    // ========== 查询方法 ==========

    /**
     * 从 {@link ResourceLocation} 查询对应的 {@link AEGuiKey}。
     * 用于网络包解码。
     *
     * @param id GUI 标识的 ResourceLocation
     * @return 对应的 AEGuiKey，如果没有注册则返回 {@code null}
     */
    @Nullable
    public static AEGuiKey fromId(ResourceLocation id) {
        return ID_MAP.get(id);
    }

    /**
     * 从旧的 {@link GuiBridge} 枚举值查询对应的 {@link AEGuiKey}。
     *
     * @param bridge 旧枚举值
     * @return 对应的 AEGuiKey，如果没有映射则返回 {@code null}
     */
    @Nullable
    public static AEGuiKey fromLegacy(GuiBridge bridge) {
        return LEGACY_MAP.get(bridge);
    }

    /**
     * 获取已注册的 legacy 映射数量（主要用于日志/调试）。
     */
    public static int getLegacyMapSize() {
        return LEGACY_MAP.size();
    }

    // ========== 内部辅助 ==========

    /**
     * 创建使用 AE2 命名空间的 Builder 快捷方法。
     */
    private static AEGuiKey.Builder key(String path) {
        return AEGuiKey.builder(AppEng.MOD_ID, path);
    }
}

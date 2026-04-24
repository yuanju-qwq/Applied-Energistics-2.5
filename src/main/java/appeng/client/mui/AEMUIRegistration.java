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

package appeng.client.mui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.storage.ITerminalHost;
import appeng.client.mui.screen.*;
import appeng.container.implementations.*;
import appeng.fluids.container.*;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceLogicHost;
import appeng.helpers.IPatternProviderHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.QuartzKnifeObj;
import appeng.fluids.parts.PartFluidFormationPlane;
import appeng.fluids.parts.PartFluidLevelEmitter;
import appeng.fluids.util.AEFluidStackType;
import appeng.parts.automation.PartFormationPlane;
import appeng.parts.automation.PartLevelEmitter;
import appeng.parts.misc.PartOreDicStorageBus;
import appeng.parts.misc.AbstractPartStorageBus;
import appeng.parts.misc.PartStorageBus;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.tile.grindstone.TileGrinder;
import appeng.tile.misc.TileCellWorkbench;
import appeng.tile.misc.TileCondenser;
import appeng.tile.storage.TileIOPort;
import appeng.tile.misc.TileInscriber;
import appeng.tile.misc.TileVibrationChamber;
import appeng.tile.networking.TileWireless;
import appeng.tile.qnb.TileQuantumBridge;
import appeng.tile.spatial.TileSpatialIOPort;
import appeng.tile.storage.TileChest;
import appeng.util.item.AEItemStackType;
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileSkyChest;
import appeng.api.implementations.IUpgradeableHost;
import appeng.fluids.parts.PartSharedFluidBus;
import appeng.parts.reporting.PartInterfaceConfigurationTerminal;

/**
 * MUI GUI 统一注册入口。
 * <p>
 * 在客户端初始化阶段调用 {@link #registerAll()}，
 * 将所有 MUI 面板通过 {@link AEMUIGuiFactory#register(appeng.core.sync.AEGuiKey,
 * AEMUIGuiFactory.IHostContainerFactory, AEMUIGuiFactory.IHostGuiFactory)}
 * 注册到以 {@link appeng.core.sync.AEGuiKey} 为主键的注册表中。
 * <p>
 * 注册时同时提供服务端 Container 工厂和客户端 GUI 工厂，
 * {@link AEMUIGuiFactory} 内部会自动同步到旧体系的 {@code legacyRegistry}
 * 以保持兼容。
 *
 * <h3>注册范围</h3>
 * <ul>
 *   <li>存储设备：Chest、Drive、CellWorkbench、MEPortableCell</li>
 *   <li>合成设备：MAC、Inscriber</li>
 *   <li>工具/杂项：Priority、SecurityStation、NetworkStatus、NetworkTool、Wireless、
 *       SpatialIOPort、Condenser、VibrationChamber、Grinder、QNB、SkyChest、
 *       QuartzKnife、Renamer、OreDictStorageBus</li>
 *   <li>无线终端：WirelessTerm、WirelessCraftingTerminal、WirelessPatternTerminal、
 *       WirelessInterfaceTerminal、WirelessDualInterfaceTerminal</li>
 *   <li>通用/合成/样板终端：ME、CraftingTerminal、PatternTerminal、
 *       ExpandedProcessingPatternTerminal、FluidTerminal</li>
 *   <li>接口设置：Interface、FluidInterface、DualItemInterface、DualFluidInterface</li>
 *   <li>总线/面板：Bus、BusFluid、StorageBus、StorageBusFluid、FormationPlane、
 *       FluidFormationPlane、LevelEmitter、FluidLevelEmitter</li>
 *   <li>IO/合成子系统：IOPort、CraftingCPU、CraftAmount、CraftConfirm、CraftingStatus</li>
 *   <li>接口终端：InterfaceTerminal、InterfaceConfigurationTerminal、
 *       FluidInterfaceConfigurationTerminal</li>
 *   <li>样板值设置：PatternValueAmount、PatternValueName</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public final class AEMUIRegistration {

    private AEMUIRegistration() {
    }

    /**
     * 注册所有 MUI 面板到 {@link AEMUIGuiFactory} 的 AEGuiKey 注册表。
     * 应在客户端 init 阶段调用。
     */
    public static void registerAll() {
        AELog.info("MUI: Registering device/tool GUI panels...");

        // ========== 存储设备 ==========

        AEMUIGuiFactory.register(AEGuiKeys.CHEST,
                (ip, host) -> new ContainerChest(ip, (TileChest) host),
                (ip, host) -> new MUIChestPanel(ip, (TileChest) host));

        AEMUIGuiFactory.register(AEGuiKeys.DRIVE,
                (ip, host) -> new ContainerDrive(ip, (TileDrive) host),
                (ip, host) -> new MUIDrivePanel(ip, (TileDrive) host));

        AEMUIGuiFactory.register(AEGuiKeys.CELL_WORKBENCH,
                (ip, host) -> new ContainerCellWorkbench(ip, (TileCellWorkbench) host),
                (ip, host) -> new MUICellWorkbenchPanel(
                        new ContainerCellWorkbench(ip, (TileCellWorkbench) host)));

        AEMUIGuiFactory.register(AEGuiKeys.PORTABLE_CELL,
                (ip, host) -> new ContainerMEPortableCell(ip, (IPortableCell) host),
                (ip, host) -> new MUIMEPortableCellPanelImpl(
                        new ContainerMEPortableCell(ip, (IPortableCell) host)));

        // ========== 合成设备 ==========

        AEMUIGuiFactory.register(AEGuiKeys.MAC,
                (ip, host) -> new ContainerMAC(ip, (TileMolecularAssembler) host),
                (ip, host) -> new MUIMACPanel(ip, (TileMolecularAssembler) host));

        AEMUIGuiFactory.register(AEGuiKeys.INSCRIBER,
                (ip, host) -> new ContainerInscriber(ip, (TileInscriber) host),
                (ip, host) -> new MUIInscriberPanel(ip, (TileInscriber) host));

        // ========== 工具/杂项 ==========

        AEMUIGuiFactory.register(AEGuiKeys.PRIORITY,
                (ip, host) -> new ContainerPriority(ip, (IPriorityHost) host),
                (ip, host) -> new MUIPriorityPanel(ip, (IPriorityHost) host));

        AEMUIGuiFactory.register(AEGuiKeys.SECURITY,
                (ip, host) -> new ContainerSecurityStation(ip, (ITerminalHost) host),
                (ip, host) -> new MUISecurityStationPanelImpl(
                        new ContainerSecurityStation(ip, (ITerminalHost) host)));

        AEMUIGuiFactory.register(AEGuiKeys.NETWORK_STATUS,
                (ip, host) -> new ContainerNetworkStatus(ip, (INetworkTool) host),
                (ip, host) -> new MUINetworkStatusPanel(
                        new ContainerNetworkStatus(ip, (INetworkTool) host)));

        AEMUIGuiFactory.register(AEGuiKeys.NETWORK_TOOL,
                (ip, host) -> new ContainerNetworkTool(ip, (INetworkTool) host),
                (ip, host) -> new MUINetworkToolPanel(ip, (INetworkTool) host));

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS,
                (ip, host) -> new ContainerWireless(ip, (TileWireless) host),
                (ip, host) -> new MUIWirelessPanel(ip, (TileWireless) host));

        AEMUIGuiFactory.register(AEGuiKeys.SPATIAL_IO_PORT,
                (ip, host) -> new ContainerSpatialIOPort(ip, (TileSpatialIOPort) host),
                (ip, host) -> new MUISpatialIOPortPanel(ip, (TileSpatialIOPort) host));

        AEMUIGuiFactory.register(AEGuiKeys.CONDENSER,
                (ip, host) -> new ContainerCondenser(ip, (TileCondenser) host),
                (ip, host) -> new MUICondenserPanel(ip, (TileCondenser) host));

        AEMUIGuiFactory.register(AEGuiKeys.VIBRATION_CHAMBER,
                (ip, host) -> new ContainerVibrationChamber(ip, (TileVibrationChamber) host),
                (ip, host) -> new MUIVibrationChamberPanel(ip, (TileVibrationChamber) host));

        AEMUIGuiFactory.register(AEGuiKeys.GRINDER,
                (ip, host) -> new ContainerGrinder(ip, (TileGrinder) host),
                (ip, host) -> new MUIGrinderPanel(ip, (TileGrinder) host));

        AEMUIGuiFactory.register(AEGuiKeys.QNB,
                (ip, host) -> new ContainerQNB(ip, (TileQuantumBridge) host),
                (ip, host) -> new MUIQNBPanel(ip, (TileQuantumBridge) host));

        AEMUIGuiFactory.register(AEGuiKeys.SKY_CHEST,
                (ip, host) -> new ContainerSkyChest(ip, (TileSkyChest) host),
                (ip, host) -> new MUISkyChestPanel(ip, (TileSkyChest) host));

        AEMUIGuiFactory.register(AEGuiKeys.QUARTZ_KNIFE,
                (ip, host) -> new ContainerQuartzKnife(ip, (QuartzKnifeObj) host),
                (ip, host) -> new MUIQuartzKnifePanel(ip, (QuartzKnifeObj) host));

        AEMUIGuiFactory.register(AEGuiKeys.RENAMER,
                (ip, host) -> new ContainerRenamer(ip, (ICustomNameObject) host),
                (ip, host) -> new MUIRenamerPanel(ip, (ICustomNameObject) host));

        AEMUIGuiFactory.register(AEGuiKeys.ORE_DICT_STORAGE_BUS,
                (ip, host) -> new ContainerOreDictStorageBus(ip, (PartOreDicStorageBus) host),
                (ip, host) -> new MUIOreDictStorageBusPanel(
                        new ContainerOreDictStorageBus(ip, (PartOreDicStorageBus) host)));

        // ========== 无线终端 ==========

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_TERM,
                (ip, host) -> new ContainerWirelessTerm(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessTermPanelImpl(ip, (WirelessTerminalGuiObject) host));

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_CRAFTING_TERMINAL,
                (ip, host) -> new ContainerWirelessCraftingTerminal(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessCraftingTermPanelImpl(ip, (WirelessTerminalGuiObject) host));

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_PATTERN_TERMINAL,
                (ip, host) -> new ContainerWirelessPatternTerminal(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessPatternTermPanelImpl(ip, (WirelessTerminalGuiObject) host));

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_INTERFACE_TERMINAL,
                (ip, host) -> new ContainerWirelessInterfaceTerminal(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessInterfaceTermPanelImpl(ip, (WirelessTerminalGuiObject) host));

        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_DUAL_INTERFACE_TERMINAL,
                (ip, host) -> new ContainerWirelessDualInterfaceTerminal(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessDualInterfaceTerminalPanel(
                        new ContainerWirelessDualInterfaceTerminal(ip, (WirelessTerminalGuiObject) host)));

        // WIRELESS_FLUID_TERMINAL: the wireless terminal already supports unified
        // item+fluid display, so we reuse the same panel as WIRELESS_TERM.
        AEMUIGuiFactory.register(AEGuiKeys.WIRELESS_FLUID_TERMINAL,
                (ip, host) -> new ContainerWirelessTerm(ip, (WirelessTerminalGuiObject) host),
                (ip, host) -> new MUIWirelessTermPanelImpl(ip, (WirelessTerminalGuiObject) host));

        // ========== 通用/合成/样板终端 ==========

        AEMUIGuiFactory.register(AEGuiKeys.ME_TERMINAL,
                (ip, host) -> new ContainerMEMonitorable(ip, (ITerminalHost) host),
                (ip, host) -> new MUIMEMonitorablePanel(ip, (ITerminalHost) host));

        AEMUIGuiFactory.register(AEGuiKeys.CRAFTING_TERMINAL,
                (ip, host) -> new ContainerCraftingTerm(ip, (PartCraftingTerminal) host),
                (ip, host) -> new MUICraftingTermPanel(ip, (PartCraftingTerminal) host));

        AEMUIGuiFactory.register(AEGuiKeys.PATTERN_TERMINAL,
                (ip, host) -> new ContainerPatternTerm(ip, (PartPatternTerminal) host),
                (ip, host) -> new MUIPatternTermPanel(ip, (PartPatternTerminal) host));

        AEMUIGuiFactory.register(AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL,
                (ip, host) -> new ContainerExpandedProcessingPatternTerm(ip,
                        (PartExpandedProcessingPatternTerminal) host),
                (ip, host) -> new MUIExpandedProcessingPatternTermPanel(ip,
                        (PartExpandedProcessingPatternTerminal) host));

        AEMUIGuiFactory.register(AEGuiKeys.FLUID_TERMINAL,
                (ip, host) -> new ContainerMEMonitorable(ip, (ITerminalHost) host),
                (ip, host) -> new MUIMEMonitorablePanel(ip, (ITerminalHost) host));

        // ========== 接口设置 ==========

        AEMUIGuiFactory.register(AEGuiKeys.ME_INTERFACE,
                (ip, host) -> new ContainerMEInterface(ip, (IInterfaceLogicHost) host),
                (ip, host) -> new MUIMEInterfacePanel(
                        new ContainerMEInterface(ip, (IInterfaceLogicHost) host)));

        AEMUIGuiFactory.register(AEGuiKeys.PATTERN_PROVIDER,
                (ip, host) -> new ContainerPatternProvider(ip, (IPatternProviderHost) host),
                (ip, host) -> new MUIPatternProviderPanel(
                        new ContainerPatternProvider(ip, (IPatternProviderHost) host)));

        // ========== 总线/面板 ==========

        AEMUIGuiFactory.register(AEGuiKeys.BUS,
                (ip, host) -> new ContainerUpgradeable(ip, (IUpgradeableHost) host),
                (ip, host) -> new MUIUpgradeablePanel(
                        new ContainerUpgradeable(ip, (IUpgradeableHost) host)));

        AEMUIGuiFactory.register(AEGuiKeys.BUS_FLUID,
                (ip, host) -> new ContainerFluidIO(ip, (PartSharedFluidBus) host),
                (ip, host) -> new MUIFluidIOPanel(
                        new ContainerFluidIO(ip, (PartSharedFluidBus) host)));

        AEMUIGuiFactory.register(AEGuiKeys.STORAGE_BUS,
                (ip, host) -> new ContainerStorageBus(ip, (AbstractPartStorageBus<?>) host),
                (ip, host) -> new MUIStorageBusPanel(
                        new ContainerStorageBus(ip, (AbstractPartStorageBus<?>) host),
                        AEItemStackType.INSTANCE, GuiText.StorageBus));

        AEMUIGuiFactory.register(AEGuiKeys.STORAGE_BUS_FLUID,
                (ip, host) -> new ContainerStorageBus(ip, (AbstractPartStorageBus<?>) host),
                (ip, host) -> new MUIStorageBusPanel(
                        new ContainerStorageBus(ip, (AbstractPartStorageBus<?>) host),
                        AEFluidStackType.INSTANCE, GuiText.StorageBusFluids));

        AEMUIGuiFactory.register(AEGuiKeys.FORMATION_PLANE,
                (ip, host) -> new ContainerFormationPlane(ip, (PartFormationPlane) host),
                (ip, host) -> new MUIFormationPlanePanel(
                        new ContainerFormationPlane(ip, (PartFormationPlane) host)));

        AEMUIGuiFactory.register(AEGuiKeys.FLUID_FORMATION_PLANE,
                (ip, host) -> new ContainerFluidFormationPlane(ip, (PartFluidFormationPlane) host),
                (ip, host) -> new MUIFluidFormationPlanePanel(
                        new ContainerFluidFormationPlane(ip, (PartFluidFormationPlane) host)));

        AEMUIGuiFactory.register(AEGuiKeys.LEVEL_EMITTER,
                (ip, host) -> new ContainerLevelEmitter(ip, (PartLevelEmitter) host),
                (ip, host) -> new MUILevelEmitterPanel(
                        new ContainerLevelEmitter(ip, (PartLevelEmitter) host)));

        AEMUIGuiFactory.register(AEGuiKeys.FLUID_LEVEL_EMITTER,
                (ip, host) -> new ContainerFluidLevelEmitter(ip, (PartFluidLevelEmitter) host),
                (ip, host) -> new MUIFluidLevelEmitterPanel(
                        new ContainerFluidLevelEmitter(ip, (PartFluidLevelEmitter) host)));

        // ========== IO/合成子系统 ==========

        AEMUIGuiFactory.register(AEGuiKeys.IO_PORT,
                (ip, host) -> new ContainerIOPort(ip, (TileIOPort) host),
                (ip, host) -> new MUIIOPortPanel(
                        new ContainerIOPort(ip, (TileIOPort) host)));

        AEMUIGuiFactory.register(AEGuiKeys.CRAFTING_CPU,
                (ip, host) -> new ContainerCraftingCPU(ip, host),
                (ip, host) -> new MUICraftingCPUPanel(ip, host));

        AEMUIGuiFactory.register(AEGuiKeys.CRAFTING_AMOUNT,
                (ip, host) -> new ContainerCraftAmount(ip, (ITerminalHost) host),
                (ip, host) -> new MUICraftAmountPanel(ip, (ITerminalHost) host));

        AEMUIGuiFactory.register(AEGuiKeys.CRAFTING_CONFIRM,
                (ip, host) -> new ContainerCraftConfirm(ip, (ITerminalHost) host),
                (ip, host) -> new MUICraftConfirmPanel(ip, (ITerminalHost) host));

        AEMUIGuiFactory.register(AEGuiKeys.CRAFTING_STATUS,
                (ip, host) -> new ContainerCraftingStatus(ip, (ITerminalHost) host),
                (ip, host) -> new MUICraftingStatusPanel(ip, (ITerminalHost) host));

        // ========== 接口终端 ==========

        AEMUIGuiFactory.register(AEGuiKeys.INTERFACE_TERMINAL,
                (ip, host) -> new ContainerInterfaceTerminal(ip, (PartInterfaceTerminal) host),
                (ip, host) -> new MUIInterfaceTerminalPanel(
                        new ContainerInterfaceTerminal(ip, (PartInterfaceTerminal) host)));

        AEMUIGuiFactory.register(AEGuiKeys.INTERFACE_CONFIGURATION_TERMINAL,
                (ip, host) -> new ContainerInterfaceConfigurationTerminal(ip,
                        (PartInterfaceConfigurationTerminal) host),
                (ip, host) -> new MUIInterfaceConfigurationTerminalPanel(
                        new ContainerInterfaceConfigurationTerminal(ip,
                                (PartInterfaceConfigurationTerminal) host)));

        // ========== 样板值设置 ==========

        AEMUIGuiFactory.register(AEGuiKeys.PATTERN_VALUE_AMOUNT,
                (ip, host) -> new ContainerPatternValueAmount(ip, (ITerminalHost) host),
                (ip, host) -> new MUIPatternValueAmountPanel(ip, (ITerminalHost) host));

        AEMUIGuiFactory.register(AEGuiKeys.PATTERN_VALUE_NAME,
                (ip, host) -> new ContainerPatternValueName(ip, (ITerminalHost) host),
                (ip, host) -> new MUIPatternValueNamePanel(ip, (ITerminalHost) host));

        AELog.info("MUI: Registered all %d GUI panels.", 52);
    }
}

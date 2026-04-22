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
import appeng.fluids.helper.IFluidInterfaceHost;
import appeng.api.storage.ITerminalHost;
import appeng.client.mui.screen.*;
import appeng.container.implementations.*;
import appeng.fluids.container.*;
import appeng.core.AELog;
import appeng.core.sync.GuiBridge;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.QuartzKnifeObj;
import appeng.fluids.parts.PartFluidFormationPlane;
import appeng.fluids.parts.PartFluidLevelEmitter;
import appeng.parts.automation.PartFormationPlane;
import appeng.parts.automation.PartLevelEmitter;
import appeng.parts.misc.PartOreDicStorageBus;
import appeng.parts.misc.PartStorageBus;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.tile.crafting.TileCraftingTile;
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
import appeng.tile.storage.TileDrive;
import appeng.tile.storage.TileSkyChest;
import appeng.api.implementations.IUpgradeableHost;
import appeng.fluids.parts.PartFluidStorageBus;
import appeng.fluids.parts.PartSharedFluidBus;
import appeng.parts.reporting.PartInterfaceConfigurationTerminal;
import appeng.parts.reporting.PartFluidInterfaceConfigurationTerminal;

/**
 * MUI GUI 统一注册入口。
 * <p>
 * 在客户端初始化阶段调用 {@link #registerAll()}，
 * 将所有 MUI 面板通过 {@link GuiBridge#setMuiGuiFactory} 注册到 GuiBridge 枚举上。
 * 注册后，{@link GuiBridge#ConstructGui} 将优先使用 MUI 面板，跳过旧 GUI 的反射创建。
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
     * 注册所有 MUI 面板到 GuiBridge。
     * 应在客户端 init 阶段调用。
     */
    public static void registerAll() {
        AELog.info("MUI: Registering device/tool GUI panels...");

        // ========== 存储设备 ==========

        GuiBridge.GUI_CHEST.setMuiGuiFactory(
                (ip, te) -> new MUIChestPanel(ip, (TileChest) te));

        GuiBridge.GUI_DRIVE.setMuiGuiFactory(
                (ip, te) -> new MUIDrivePanel(ip, (TileDrive) te));

        GuiBridge.GUI_CELL_WORKBENCH.setMuiGuiFactory(
                (ip, te) -> new MUICellWorkbenchPanel(new ContainerCellWorkbench(ip, (TileCellWorkbench) te)));

        GuiBridge.GUI_PORTABLE_CELL.setMuiGuiFactory(
                (ip, te) -> new MUIMEPortableCellPanelImpl(
                        new ContainerMEPortableCell(ip, (IPortableCell) te)));

        // ========== 合成设备 ==========

        GuiBridge.GUI_MAC.setMuiGuiFactory(
                (ip, te) -> new MUIMACPanel(ip, (TileMolecularAssembler) te));

        GuiBridge.GUI_INSCRIBER.setMuiGuiFactory(
                (ip, te) -> new MUIInscriberPanel(ip, (TileInscriber) te));

        // ========== 工具/杂项 ==========

        GuiBridge.GUI_PRIORITY.setMuiGuiFactory(
                (ip, te) -> new MUIPriorityPanel(ip, (IPriorityHost) te));

        GuiBridge.GUI_SECURITY.setMuiGuiFactory(
                (ip, te) -> new MUISecurityStationPanelImpl(
                        new ContainerSecurityStation(ip, (ITerminalHost) te)));

        GuiBridge.GUI_NETWORK_STATUS.setMuiGuiFactory(
                (ip, te) -> new MUINetworkStatusPanel(
                        new ContainerNetworkStatus(ip, (INetworkTool) te)));

        GuiBridge.GUI_NETWORK_TOOL.setMuiGuiFactory(
                (ip, te) -> new MUINetworkToolPanel(ip, (INetworkTool) te));

        GuiBridge.GUI_WIRELESS.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessPanel(ip, (TileWireless) te));

        GuiBridge.GUI_SPATIAL_IO_PORT.setMuiGuiFactory(
                (ip, te) -> new MUISpatialIOPortPanel(ip, (TileSpatialIOPort) te));

        GuiBridge.GUI_CONDENSER.setMuiGuiFactory(
                (ip, te) -> new MUICondenserPanel(ip, (TileCondenser) te));

        GuiBridge.GUI_VIBRATION_CHAMBER.setMuiGuiFactory(
                (ip, te) -> new MUIVibrationChamberPanel(ip, (TileVibrationChamber) te));

        GuiBridge.GUI_GRINDER.setMuiGuiFactory(
                (ip, te) -> new MUIGrinderPanel(ip, (TileGrinder) te));

        GuiBridge.GUI_QNB.setMuiGuiFactory(
                (ip, te) -> new MUIQNBPanel(ip, (TileQuantumBridge) te));

        GuiBridge.GUI_SKYCHEST.setMuiGuiFactory(
                (ip, te) -> new MUISkyChestPanel(ip, (TileSkyChest) te));

        GuiBridge.GUI_QUARTZ_KNIFE.setMuiGuiFactory(
                (ip, te) -> new MUIQuartzKnifePanel(ip, (QuartzKnifeObj) te));

        GuiBridge.GUI_RENAMER.setMuiGuiFactory(
                (ip, te) -> new MUIRenamerPanel(ip, (ICustomNameObject) te));

        GuiBridge.GUI_OREDICTSTORAGEBUS.setMuiGuiFactory(
                (ip, te) -> new MUIOreDictStorageBusPanel(
                        new ContainerOreDictStorageBus(ip, (PartOreDicStorageBus) te)));

        // ========== 无线终端 ==========

        GuiBridge.GUI_WIRELESS_TERM.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessTermPanelImpl(ip, (WirelessTerminalGuiObject) te));

        GuiBridge.GUI_WIRELESS_CRAFTING_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessCraftingTermPanelImpl(ip, (WirelessTerminalGuiObject) te));

        GuiBridge.GUI_WIRELESS_PATTERN_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessPatternTermPanelImpl(ip, (WirelessTerminalGuiObject) te));

        GuiBridge.GUI_WIRELESS_INTERFACE_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessInterfaceTermPanelImpl(ip, (WirelessTerminalGuiObject) te));

        GuiBridge.GUI_WIRELESS_DUAL_INTERFACE_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIWirelessDualInterfaceTerminalPanel(
                        new ContainerWirelessDualInterfaceTerminal(ip, (WirelessTerminalGuiObject) te)));

        // GUI_WIRELESS_FLUID_TERMINAL 暂不注册：
        // 该终端已标记 @Deprecated，且其 Container 不继承 ContainerMEMonitorable，
        // 需要完整的 MUI 流体终端面板支持。保留使用旧 GUI。

        // ========== 通用/合成/样板终端 ==========

        GuiBridge.GUI_ME.setMuiGuiFactory(
                (ip, te) -> new MUIMEMonitorablePanel(ip, (ITerminalHost) te));

        GuiBridge.GUI_CRAFTING_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUICraftingTermPanel(ip, (PartCraftingTerminal) te));

        GuiBridge.GUI_PATTERN_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIPatternTermPanel(ip, (PartPatternTerminal) te));

        GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIExpandedProcessingPatternTermPanel(ip,
                        (PartExpandedProcessingPatternTerminal) te));

        GuiBridge.GUI_FLUID_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIMEMonitorablePanel(ip, (ITerminalHost) te));

        // ========== 接口设置 ==========

        GuiBridge.GUI_INTERFACE.setMuiGuiFactory(
                (ip, te) -> new MUIInterfacePanel(
                        new ContainerInterface(ip, (IInterfaceHost) te)));

        GuiBridge.GUI_FLUID_INTERFACE.setMuiGuiFactory(
                (ip, te) -> new MUIFluidInterfacePanel(
                        new ContainerFluidInterface(ip, (IFluidInterfaceHost) te),
                        (IFluidInterfaceHost) te));

        GuiBridge.GUI_DUAL_ITEM_INTERFACE.setMuiGuiFactory(
                (ip, te) -> new MUIDualItemInterfacePanel(
                        new ContainerDualItemInterface(ip, (IInterfaceHost) te)));

        GuiBridge.GUI_DUAL_FLUID_INTERFACE.setMuiGuiFactory(
                (ip, te) -> new MUIDualFluidInterfacePanel(
                        new ContainerDualFluidInterface(ip, (IFluidInterfaceHost) te),
                        (IFluidInterfaceHost) te));

        // ========== 总线/面板 ==========

        GuiBridge.GUI_BUS.setMuiGuiFactory(
                (ip, te) -> new MUIUpgradeablePanel(
                        new ContainerUpgradeable(ip, (IUpgradeableHost) te)));

        GuiBridge.GUI_BUS_FLUID.setMuiGuiFactory(
                (ip, te) -> new MUIFluidIOPanel(
                        new ContainerFluidIO(ip, (PartSharedFluidBus) te), (PartSharedFluidBus) te));

        GuiBridge.GUI_STORAGEBUS.setMuiGuiFactory(
                (ip, te) -> new MUIStorageBusPanel(
                        new ContainerStorageBus(ip, (PartStorageBus) te)));

        GuiBridge.GUI_STORAGEBUS_FLUID.setMuiGuiFactory(
                (ip, te) -> new MUIFluidStorageBusPanel(
                        new ContainerFluidStorageBus(ip, (PartFluidStorageBus) te)));

        GuiBridge.GUI_FORMATION_PLANE.setMuiGuiFactory(
                (ip, te) -> new MUIFormationPlanePanel(
                        new ContainerFormationPlane(ip, (PartFormationPlane) te)));

        GuiBridge.GUI_FLUID_FORMATION_PLANE.setMuiGuiFactory(
                (ip, te) -> new MUIFluidFormationPlanePanel(
                        new ContainerFluidFormationPlane(ip, (PartFluidFormationPlane) te)));

        GuiBridge.GUI_LEVEL_EMITTER.setMuiGuiFactory(
                (ip, te) -> new MUILevelEmitterPanel(
                        new ContainerLevelEmitter(ip, (PartLevelEmitter) te)));

        GuiBridge.GUI_FLUID_LEVEL_EMITTER.setMuiGuiFactory(
                (ip, te) -> new MUIFluidLevelEmitterPanel(
                        new ContainerFluidLevelEmitter(ip, (PartFluidLevelEmitter) te)));

        // ========== IO/合成子系统 ==========

        GuiBridge.GUI_IOPORT.setMuiGuiFactory(
                (ip, te) -> new MUIIOPortPanel(
                        new ContainerIOPort(ip, (TileIOPort) te)));

        GuiBridge.GUI_CRAFTING_CPU.setMuiGuiFactory(
                (ip, te) -> new MUICraftingCPUPanel(ip, te));

        GuiBridge.GUI_CRAFTING_AMOUNT.setMuiGuiFactory(
                (ip, te) -> new MUICraftAmountPanel(ip, (ITerminalHost) te));

        GuiBridge.GUI_CRAFTING_CONFIRM.setMuiGuiFactory(
                (ip, te) -> new MUICraftConfirmPanel(ip, (ITerminalHost) te));

        GuiBridge.GUI_CRAFTING_STATUS.setMuiGuiFactory(
                (ip, te) -> new MUICraftingStatusPanel(ip, (ITerminalHost) te));

        // ========== 接口终端 ==========

        GuiBridge.GUI_INTERFACE_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIInterfaceTerminalPanel(
                        new ContainerInterfaceTerminal(ip, (PartInterfaceTerminal) te)));

        GuiBridge.GUI_INTERFACE_CONFIGURATION_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIInterfaceConfigurationTerminalPanel(
                        new ContainerInterfaceConfigurationTerminal(ip,
                                (PartInterfaceConfigurationTerminal) te)));

        GuiBridge.GUI_FLUID_INTERFACE_CONFIGURATION_TERMINAL.setMuiGuiFactory(
                (ip, te) -> new MUIFluidInterfaceConfigurationTerminalPanel(
                        new ContainerFluidInterfaceConfigurationTerminal(ip,
                                (PartFluidInterfaceConfigurationTerminal) te)));

        // ========== 样板值设置 ==========

        GuiBridge.GUI_PATTERN_VALUE_AMOUNT.setMuiGuiFactory(
                (ip, te) -> new MUIPatternValueAmountPanel(ip, (ITerminalHost) te));

        GuiBridge.GUI_PATTERN_VALUE_NAME.setMuiGuiFactory(
                (ip, te) -> new MUIPatternValueNamePanel(ip, (ITerminalHost) te));

        AELog.info("MUI: Registered all %d GUI panels.", 47);
    }
}

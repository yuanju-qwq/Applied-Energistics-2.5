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

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.config.SecurityPermissions;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.storage.ITerminalHost;
import appeng.api.util.AEPartLocation;
import appeng.container.implementations.*;
import appeng.fluids.container.*;
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

public enum GuiBridge implements IGuiHandler {
    GUI_Handler(),

    GUI_GRINDER(ContainerGrinder.class, TileGrinder.class, GuiHostType.WORLD, null),

    GUI_QNB(ContainerQNB.class, TileQuantumBridge.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_SKYCHEST(ContainerSkyChest.class, TileSkyChest.class, GuiHostType.WORLD, null),

    GUI_CHEST(ContainerChest.class, TileChest.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_WIRELESS(ContainerWireless.class, TileWireless.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_ME(ContainerMEMonitorable.class, ITerminalHost.class, GuiHostType.WORLD, null),

    GUI_PORTABLE_CELL(ContainerMEPortableCell.class, IPortableCell.class, GuiHostType.ITEM, null),

    GUI_WIRELESS_TERM(ContainerWirelessTerm.class, WirelessTerminalGuiObject.class, GuiHostType.ITEM, null),
    GUI_WIRELESS_CRAFTING_TERMINAL(ContainerWirelessCraftingTerminal.class, WirelessTerminalGuiObject.class,
            GuiHostType.ITEM, null),
    GUI_WIRELESS_PATTERN_TERMINAL(ContainerWirelessPatternTerminal.class, WirelessTerminalGuiObject.class,
            GuiHostType.ITEM, null),
    GUI_WIRELESS_FLUID_TERMINAL(ContainerWirelessFluidTerminal.class, WirelessTerminalGuiObject.class, GuiHostType.ITEM,
            null),
    GUI_WIRELESS_INTERFACE_TERMINAL(ContainerWirelessInterfaceTerminal.class, WirelessTerminalGuiObject.class,
            GuiHostType.ITEM,
            null),
    GUI_WIRELESS_DUAL_INTERFACE_TERMINAL(ContainerWirelessDualInterfaceTerminal.class,
            WirelessTerminalGuiObject.class, GuiHostType.ITEM, null),

    GUI_NETWORK_STATUS(ContainerNetworkStatus.class, INetworkTool.class, GuiHostType.ITEM, null),

    GUI_CRAFTING_CPU(ContainerCraftingCPU.class, TileCraftingTile.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),

    GUI_NETWORK_TOOL(ContainerNetworkTool.class, INetworkTool.class, GuiHostType.ITEM, null),

    GUI_QUARTZ_KNIFE(ContainerQuartzKnife.class, QuartzKnifeObj.class, GuiHostType.ITEM, null),

    GUI_DRIVE(ContainerDrive.class, TileDrive.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_VIBRATION_CHAMBER(ContainerVibrationChamber.class, TileVibrationChamber.class, GuiHostType.WORLD, null),

    GUI_CONDENSER(ContainerCondenser.class, TileCondenser.class, GuiHostType.WORLD, null),

    GUI_INTERFACE(ContainerInterface.class, IInterfaceHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_FLUID_INTERFACE(ContainerFluidInterface.class, IFluidInterfaceHost.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    // 二合一接口（物品面 + 流体面）
    GUI_DUAL_ITEM_INTERFACE(ContainerDualItemInterface.class, IInterfaceHost.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_DUAL_FLUID_INTERFACE(ContainerDualFluidInterface.class, IFluidInterfaceHost.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_BUS(ContainerUpgradeable.class, IUpgradeableHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_BUS_FLUID(ContainerFluidIO.class, PartSharedFluidBus.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_IOPORT(ContainerIOPort.class, TileIOPort.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_STORAGEBUS(ContainerStorageBus.class, PartStorageBus.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_OREDICTSTORAGEBUS(ContainerOreDictStorageBus.class, PartOreDicStorageBus.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_STORAGEBUS_FLUID(ContainerFluidStorageBus.class, PartFluidStorageBus.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_FORMATION_PLANE(ContainerFormationPlane.class, PartFormationPlane.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_FLUID_FORMATION_PLANE(ContainerFluidFormationPlane.class, PartFluidFormationPlane.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_PRIORITY(ContainerPriority.class, IPriorityHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_SECURITY(ContainerSecurityStation.class, TileSecurityStation.class, GuiHostType.WORLD,
            SecurityPermissions.SECURITY),

    GUI_CRAFTING_TERMINAL(ContainerCraftingTerm.class, PartCraftingTerminal.class, GuiHostType.WORLD,
            SecurityPermissions.CRAFT),

    GUI_PATTERN_TERMINAL(ContainerPatternTerm.class, PartPatternTerminal.class, GuiHostType.WORLD,
            SecurityPermissions.CRAFT),

    GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL(ContainerExpandedProcessingPatternTerm.class,
            PartExpandedProcessingPatternTerminal.class, GuiHostType.WORLD, SecurityPermissions.CRAFT),

    @Deprecated // 流体终端已集成到通用终端，此枚举保留向后兼容
    GUI_FLUID_TERMINAL(ContainerMEMonitorable.class, ITerminalHost.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    // extends (Container/Gui) + Bus
    GUI_LEVEL_EMITTER(ContainerLevelEmitter.class, PartLevelEmitter.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_FLUID_LEVEL_EMITTER(ContainerFluidLevelEmitter.class, PartFluidLevelEmitter.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_SPATIAL_IO_PORT(ContainerSpatialIOPort.class, TileSpatialIOPort.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_INSCRIBER(ContainerInscriber.class, TileInscriber.class, GuiHostType.WORLD, null),

    GUI_CELL_WORKBENCH(ContainerCellWorkbench.class, TileCellWorkbench.class, GuiHostType.WORLD, null),

    GUI_MAC(ContainerMAC.class, TileMolecularAssembler.class, GuiHostType.WORLD, null),

    GUI_CRAFTING_AMOUNT(ContainerCraftAmount.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD,
            SecurityPermissions.CRAFT),

    GUI_PATTERN_VALUE_AMOUNT(ContainerPatternValueAmount.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD,
            SecurityPermissions.CRAFT),

    GUI_PATTERN_VALUE_NAME(ContainerPatternValueName.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD,
            SecurityPermissions.CRAFT),

    GUI_CRAFTING_CONFIRM(ContainerCraftConfirm.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD,
            SecurityPermissions.CRAFT),

    GUI_INTERFACE_TERMINAL(ContainerInterfaceTerminal.class, PartInterfaceTerminal.class, GuiHostType.WORLD,
            SecurityPermissions.BUILD),

    GUI_CRAFTING_STATUS(ContainerCraftingStatus.class, ITerminalHost.class, GuiHostType.ITEM_OR_WORLD,
            SecurityPermissions.CRAFT),

    GUI_INTERFACE_CONFIGURATION_TERMINAL(ContainerInterfaceConfigurationTerminal.class,
            PartInterfaceConfigurationTerminal.class, GuiHostType.WORLD, SecurityPermissions.BUILD),
    GUI_FLUID_INTERFACE_CONFIGURATION_TERMINAL(ContainerFluidInterfaceConfigurationTerminal.class,
            PartFluidInterfaceConfigurationTerminal.class, GuiHostType.WORLD, SecurityPermissions.BUILD),

    GUI_RENAMER(ContainerRenamer.class, ICustomNameObject.class, GuiHostType.WORLD, SecurityPermissions.BUILD);

    private final Class tileClass;
    private final Class containerClass;
    private GuiHostType type;
    private SecurityPermissions requiredPermission;
    private GuiWrapper.IExternalGui externalGui = null;
    private final Map<Class<?>, Constructor<?>> containerConstructors = new ConcurrentHashMap<>();

    /**
     * MUI GUI 工厂 Lambda，设置后 {@link #ConstructGui} 将使用此工厂直接创建 GUI。
     * 参数：(InventoryPlayer, tileEntity/part/guiObject) → GUI 对象
     */
    private BiFunction<InventoryPlayer, Object, Object> muiGuiFactory;

    GuiBridge() {
        this.tileClass = null;
        this.containerClass = null;
    }

    GuiBridge(GuiWrapper.IExternalGui obj) {
        this.tileClass = null;
        this.containerClass = null;
        this.externalGui = obj;
    }

    public GuiWrapper.IExternalGui getExternalGui() {
        return this.externalGui;
    }

    /**
     * 注册 MUI GUI 工厂 Lambda。注册后 {@link #ConstructGui} 将优先使用此工厂直接创建 GUI，不走反射。
     *
     * @param factory (InventoryPlayer, tileEntity/part/guiObject) → MUI Panel 实例
     */
    public void setMuiGuiFactory(final BiFunction<InventoryPlayer, Object, Object> factory) {
        this.muiGuiFactory = factory;
    }

    GuiBridge(final Class containerClass, final SecurityPermissions requiredPermission) {
        this.requiredPermission = requiredPermission;
        this.containerClass = containerClass;
        this.tileClass = null;
    }

    GuiBridge(final Class containerClass, final Class tileClass, final GuiHostType type,
            final SecurityPermissions requiredPermission) {
        this.requiredPermission = requiredPermission;
        this.containerClass = containerClass;
        this.type = type;
        this.tileClass = tileClass;
    }

    @Override
    public Object getServerGuiElement(final int ordinal, final EntityPlayer player, final World w, final int x,
            final int y, final int z) {
        return AEGuiHandler.INSTANCE.getServerGuiElement(ordinal, player, w, x, y, z);
    }

    public boolean CorrectTileOrPart(final Object tE) {
        if (this.tileClass == null) {
            throw new IllegalArgumentException("This Gui Cannot use the standard Handler.");
        }

        return this.tileClass.isInstance(tE);
    }

    public Object ConstructContainer(final InventoryPlayer inventory, final AEPartLocation side, final Object tE) {
        try {
            final Constructor target = this.resolveConstructor(this.containerClass, this.containerConstructors, inventory,
                    tE);
            return target.newInstance(inventory, tE);
        } catch (final Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private Constructor findConstructor(final Constructor[] c, final InventoryPlayer inventory, final Object tE) {
        for (final Constructor con : c) {
            final Class[] types = con.getParameterTypes();
            if (types.length == 2) {
                if (types[0].isAssignableFrom(inventory.getClass()) && types[1].isAssignableFrom(tE.getClass())) {
                    return con;
                }
            }
        }
        return null;
    }

    private String typeName(final Object inventory) {
        if (inventory == null) {
            return "NULL";
        }

        return inventory.getClass().getName();
    }

    @Override
    public Object getClientGuiElement(final int ordinal, final EntityPlayer player, final World w, final int x,
            final int y, final int z) {
        return AEGuiHandler.INSTANCE.getClientGuiElement(ordinal, player, w, x, y, z);
    }

    public Object ConstructGui(final InventoryPlayer inventory, final AEPartLocation side, final Object tE) {
        if (this.muiGuiFactory != null) {
            return this.muiGuiFactory.apply(inventory, tE);
        }
        throw new IllegalStateException("No MUI GUI factory registered for " + this.name()
                + ". All GUIs must be registered via setMuiGuiFactory().");
    }

    private Constructor<?> resolveConstructor(final Class targetClass, final Map<Class<?>, Constructor<?>> cache,
            final InventoryPlayer inventory, final Object targetObject) throws AppEngException {
        final Constructor[] constructors = targetClass.getConstructors();
        if (constructors.length == 0) {
            throw new AppEngException("Invalid Gui Class");
        }

        return cache.computeIfAbsent(targetObject.getClass(), ignored -> {
            final Constructor constructor = this.findConstructor(constructors, inventory, targetObject);
            if (constructor == null) {
                throw new IllegalStateException(
                        "Cannot find " + targetClass.getName() + "( " + this.typeName(inventory) + ", "
                                + this.typeName(targetObject) + " )");
            }
            return constructor;
        });
    }

    public boolean hasPermissions(final TileEntity te, final int x, final int y, final int z, final AEPartLocation side,
            final EntityPlayer player) {
        return AEGuiHandler.hasPermissions(this, te, x, y, z, side, player);
    }

    public GuiHostType getType() {
        return this.type;
    }

    /**
     * 获取打开此 GUI 所需的安全权限。
     *
     * @return 权限要求，如果不需要权限检查则返回 {@code null}
     */
    public SecurityPermissions getRequiredPermission() {
        return this.requiredPermission;
    }

}

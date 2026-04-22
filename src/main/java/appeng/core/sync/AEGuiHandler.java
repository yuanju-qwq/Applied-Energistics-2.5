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

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import baubles.api.BaublesApi;

import appeng.api.AEApi;
import appeng.api.config.SecurityPermissions;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.GuiNull;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerNull;
import appeng.container.ContainerOpenContext;
import appeng.core.AELog;
import appeng.helpers.WirelessTerminalGuiObject;

/**
 * 新的 {@link IGuiHandler} 实现，替代 {@link GuiBridge#GUI_Handler} 的 IGuiHandler 角色。
 * <p>
 * 从 {@link GuiBridge} 中提取了以下职责：
 * <ul>
 *   <li>ordinal 编码解析：{@code bits [N:4] = GuiBridge.ordinal, [3] = usingItemOnTile, [2:0] = side}</li>
 *   <li>宿主解析：根据 {@link GuiHostType} 获取 TileEntity / Part / Item 宿主</li>
 *   <li>安全检查：根据 {@link SecurityPermissions} 校验玩家权限</li>
 *   <li>Container / GUI 创建：优先走 MUI 工厂（{@link GuiBridge#muiGuiFactory}），
 *       回退走反射（{@link GuiBridge#ConstructContainer} / {@link GuiBridge#ConstructGui}）</li>
 *   <li>ContainerOpenContext 填充</li>
 * </ul>
 *
 * <h3>ordinal 编码格式（保持网络兼容）</h3>
 * <pre>
 *   ordinal = (guiBridgeOrdinal << 4) | (usingItemOnTile << 3) | side
 * </pre>
 *
 * @see GuiBridge
 * @see AEGuiKey
 */
public final class AEGuiHandler implements IGuiHandler {

    public static final AEGuiHandler INSTANCE = new AEGuiHandler();

    private AEGuiHandler() {
    }

    // ========== IGuiHandler 实现 ==========

    @Override
    public Object getServerGuiElement(final int ordinal, final EntityPlayer player, final World w,
            final int x, final int y, final int z) {
        final AEPartLocation side = AEPartLocation.fromOrdinal(ordinal & 0x07);
        final boolean usingItemOnTile = ((ordinal >> 3) & 1) == 1;
        final GuiBridge guiBridge = resolveGuiBridge(ordinal >> 4);
        if (guiBridge == null) {
            return new ContainerNull();
        }

        // 解析宿主
        final Object host = resolveHost(guiBridge, player, w, x, y, z, side, usingItemOnTile);
        if (host == null) {
            return new ContainerNull();
        }

        // 创建 Container
        final Object container = guiBridge.ConstructContainer(player.inventory, side, host);
        return updateOpenContext(container, w, x, y, z, side, host);
    }

    @Override
    public Object getClientGuiElement(final int ordinal, final EntityPlayer player, final World w,
            final int x, final int y, final int z) {
        final AEPartLocation side = AEPartLocation.fromOrdinal(ordinal & 0x07);
        final boolean usingItemOnTile = ((ordinal >> 3) & 1) == 1;
        final GuiBridge guiBridge = resolveGuiBridge(ordinal >> 4);
        if (guiBridge == null) {
            return new GuiNull(new ContainerNull());
        }

        // 解析宿主
        final Object host = resolveHost(guiBridge, player, w, x, y, z, side, usingItemOnTile);
        if (host == null) {
            return new GuiNull(new ContainerNull());
        }

        // 创建 GUI
        return guiBridge.ConstructGui(player.inventory, side, host);
    }

    // ========== ordinal 解析 ==========

    /**
     * 从 ordinal 高位解析 {@link GuiBridge} 枚举值。
     */
    @Nullable
    private static GuiBridge resolveGuiBridge(final int guiBridgeIndex) {
        final GuiBridge[] values = GuiBridge.values();
        if (guiBridgeIndex < 0 || guiBridgeIndex >= values.length) {
            AELog.warn("Invalid GuiBridge index: %d (max: %d)", guiBridgeIndex, values.length - 1);
            return null;
        }
        return values[guiBridgeIndex];
    }

    // ========== 宿主解析 ==========

    /**
     * 根据 {@link GuiHostType} 解析 GUI 的宿主对象。
     * <p>
     * 对应原 {@link GuiBridge#getServerGuiElement} 和 {@link GuiBridge#getClientGuiElement}
     * 中的 Item / Tile / Part 解析逻辑。
     *
     * @return 解析到的宿主对象，如果不匹配则返回 null
     */
    @Nullable
    public static Object resolveHost(final GuiBridge guiBridge, final EntityPlayer player, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side, final boolean usingItemOnTile) {
        final GuiHostType type = guiBridge.getType();

        // Item 类型宿主
        if (type.isItem()) {
            final Object itemHost = resolveItemHost(player, w, x, y, z, usingItemOnTile);
            if (itemHost != null && guiBridge.CorrectTileOrPart(itemHost)) {
                return itemHost;
            }
        }

        // Tile / Part 类型宿主
        if (type.isTile()) {
            final Object tileHost = resolveTileHost(guiBridge, w, x, y, z, side);
            if (tileHost != null) {
                return tileHost;
            }
        }

        return null;
    }

    /**
     * 解析 Item 类型的宿主对象（从玩家背包、Baubles 饰品栏获取）。
     */
    @Nullable
    private static Object resolveItemHost(final EntityPlayer player, final World w,
            final int x, final int y, final int z,
            final boolean usingItemOnTile) {
        ItemStack it = ItemStack.EMPTY;

        if (usingItemOnTile) {
            it = player.inventory.getCurrentItem();
        } else if (y == 0) {
            // 普通背包槽位
            if (x >= 0 && x < player.inventory.mainInventory.size()) {
                it = player.inventory.getStackInSlot(x);
            }
        } else if (y == 1 && z == Integer.MIN_VALUE) {
            // Baubles 饰品栏槽位
            it = BaublesApi.getBaublesHandler(player).getStackInSlot(x);
        }

        return getGuiObject(it, player, w, x, y, z);
    }

    /**
     * 解析 Tile / Part 类型的宿主对象（从 TileEntity 或 IPartHost 获取）。
     */
    @Nullable
    private static Object resolveTileHost(final GuiBridge guiBridge, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side) {
        final TileEntity te = w.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof IPartHost) {
            final IPart part = ((IPartHost) te).getPart(side);
            if (part != null && guiBridge.CorrectTileOrPart(part)) {
                return part;
            }
        } else {
            if (te != null && guiBridge.CorrectTileOrPart(te)) {
                return te;
            }
        }
        return null;
    }

    /**
     * 从 ItemStack 获取 GUI 对象（IGuiItem 或 WirelessTerminalGuiObject）。
     * <p>
     * 对应原 {@link GuiBridge#getGuiObject} 方法。
     */
    @Nullable
    private static Object getGuiObject(final ItemStack it, final EntityPlayer player, final World w,
            final int x, final int y, final int z) {
        if (!it.isEmpty()) {
            if (it.getItem() instanceof IGuiItem) {
                return ((IGuiItem) it.getItem()).getGuiObject(it, w, new BlockPos(x, y, z));
            }

            final IWirelessTermHandler wh = AEApi.instance().registries().wireless().getWirelessTerminalHandler(it);
            if (wh != null) {
                return new WirelessTerminalGuiObject(wh, it, player, w, x, y, z);
            }
        }
        return null;
    }

    // ========== ContainerOpenContext 填充 ==========

    /**
     * 为 Container 填充 {@link ContainerOpenContext}，记录打开位置和方向。
     * <p>
     * 对应原 {@link GuiBridge#updateGui} 方法。
     */
    public static Object updateOpenContext(final Object container, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side, @Nullable final Object host) {
        if (container instanceof AEBaseContainer) {
            final AEBaseContainer bc = (AEBaseContainer) container;
            bc.setOpenContext(new ContainerOpenContext(host));
            bc.getOpenContext().setWorld(w);
            bc.getOpenContext().setX(x);
            bc.getOpenContext().setY(y);
            bc.getOpenContext().setZ(z);
            bc.getOpenContext().setSide(side);
        }
        return container;
    }

    // ========== 安全检查 ==========

    /**
     * 检查玩家是否有权限打开指定的 GUI。
     * <p>
     * 对应原 {@link GuiBridge#hasPermissions} 方法。
     *
     * @param guiBridge GUI 标识
     * @param te        TileEntity（可为 null）
     * @param x         x 坐标或槽位索引
     * @param y         y 坐标
     * @param z         z 坐标
     * @param side      方向
     * @param player    玩家
     * @return 如果有权限则返回 true
     */
    public static boolean hasPermissions(final GuiBridge guiBridge, @Nullable final TileEntity te,
            final int x, final int y, final int z,
            final AEPartLocation side, final EntityPlayer player) {
        final World w = player.getEntityWorld();
        final BlockPos pos = new BlockPos(x, y, z);

        if (!appeng.util.WorldHelper.hasPermissions(
                te != null ? new DimensionalCoord(te) : new DimensionalCoord(player.world, pos),
                player)) {
            return false;
        }

        final GuiHostType type = guiBridge.getType();

        if (type.isItem()) {
            final ItemStack it = player.inventory.getCurrentItem();
            if (!it.isEmpty() && it.getItem() instanceof IGuiItem) {
                final Object myItem = ((IGuiItem) it.getItem()).getGuiObject(it, w, pos);
                if (guiBridge.CorrectTileOrPart(myItem)) {
                    return true;
                }
            }
        }

        if (type.isTile()) {
            final TileEntity tileEntity = w.getTileEntity(pos);
            if (tileEntity instanceof IPartHost) {
                final IPart part = ((IPartHost) tileEntity).getPart(side);
                if (guiBridge.CorrectTileOrPart(part)) {
                    return securityCheck(guiBridge, part, player);
                }
            } else {
                if (guiBridge.CorrectTileOrPart(tileEntity)) {
                    return securityCheck(guiBridge, tileEntity, player);
                }
            }
        }

        return false;
    }

    /**
     * 安全检查：校验玩家对网络的权限。
     * <p>
     * 对应原 {@link GuiBridge#securityCheck} 方法。
     */
    public static boolean securityCheck(final GuiBridge guiBridge, final Object host,
            final EntityPlayer player) {
        final SecurityPermissions requiredPermission = guiBridge.getRequiredPermission();
        if (host instanceof IActionHost && requiredPermission != null) {
            final IGridNode gn = ((IActionHost) host).getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                    return sg.hasPermission(player, requiredPermission);
                }
            }
            return false;
        }
        return true;
    }
}

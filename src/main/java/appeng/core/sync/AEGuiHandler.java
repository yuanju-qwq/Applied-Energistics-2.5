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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
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
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.GuiNull;
import appeng.client.mui.AEMUIGuiFactory;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerNull;
import appeng.container.ContainerOpenContext;
import appeng.core.AELog;
import appeng.helpers.WirelessTerminalGuiObject;

/**
 * AE2 的 {@link IGuiHandler} 主实现。
 * <p>
 * 基于 {@link AEGuiKey} 进行宿主解析、权限检查和 Container/GUI 创建。
 * 通过 {@link AEMUIGuiFactory} 的注册表查找对应的工厂方法。
 *
 * <h3>Token Map 编码方案</h3>
 * <p>
 * Forge {@link IGuiHandler} 只支持 {@code int ordinal} 参数。为了彻底去除对
 * {@link GuiBridge#ordinal()} 的依赖，采用 Token Map 方案：
 * <ol>
 *   <li>{@link #allocateToken(AEGuiKey, AEPartLocation, boolean)} 分配自增 token，
 *       将 {@link AEGuiKey} + side + usingItemOnTile 存入 {@link #pendingOpens}</li>
 *   <li>token 作为 {@code ordinal} 传给 {@code player.openGui(mod, token, w, x, y, z)}</li>
 *   <li>{@link #getServerGuiElement}/{@link #getClientGuiElement} 从 map 取出解码</li>
 *   <li>token 使用后立即从 map 移除（一次性使用）</li>
 * </ol>
 *
 * @see AEGuiKey
 * @see AEGuiKeys
 * @see AEMUIGuiFactory
 */
public final class AEGuiHandler implements IGuiHandler {

    public static final AEGuiHandler INSTANCE = new AEGuiHandler();

    // ========== Token Map ==========

    /**
     * 待处理的 GUI 打开请求。
     */
    public static final class PendingGuiOpen {

        private final AEGuiKey guiKey;
        private final AEPartLocation side;
        private final boolean usingItemOnTile;

        PendingGuiOpen(AEGuiKey guiKey, AEPartLocation side, boolean usingItemOnTile) {
            this.guiKey = guiKey;
            this.side = side;
            this.usingItemOnTile = usingItemOnTile;
        }

        public AEGuiKey getGuiKey() {
            return this.guiKey;
        }

        public AEPartLocation getSide() {
            return this.side;
        }

        public boolean isUsingItemOnTile() {
            return this.usingItemOnTile;
        }
    }

    /**
     * 自增 token 计数器。
     * 使用 int 范围足够（约 21 亿次打开操作后回绕，实际不会碰撞）。
     */
    private static final AtomicInteger TOKEN_COUNTER = new AtomicInteger(0);

    /**
     * Token → PendingGuiOpen 映射表。
     * 服务端和客户端各自维护，因为 {@code player.openGui} 会在同一端
     * 先调用 {@code allocateToken}，然后 Forge 框架立即回调 {@code getServer/ClientGuiElement}。
     */
    private static final ConcurrentHashMap<Integer, PendingGuiOpen> pendingOpens = new ConcurrentHashMap<>();

    /**
     * 分配一个一次性 token，将 GUI 打开上下文存入映射表。
     *
     * @return token（用作 {@code player.openGui} 的 ordinal 参数）
     */
    public static int allocateToken(final AEGuiKey guiKey, final AEPartLocation side,
            final boolean usingItemOnTile) {
        final int token = TOKEN_COUNTER.incrementAndGet();
        pendingOpens.put(token, new PendingGuiOpen(guiKey, side, usingItemOnTile));
        return token;
    }

    /**
     * 从映射表中取出并移除 token 对应的 GUI 打开上下文。
     *
     * @return 如果 token 有效则返回 PendingGuiOpen，否则返回 null
     */
    @Nullable
    private static PendingGuiOpen consumeToken(final int token) {
        return pendingOpens.remove(token);
    }

    private AEGuiHandler() {
    }

    // ========== IGuiHandler 实现 ==========

    @Override
    public Object getServerGuiElement(final int ordinal, final EntityPlayer player, final World w,
            final int x, final int y, final int z) {
        final PendingGuiOpen pending = consumeToken(ordinal);
        if (pending == null) {
            AELog.warn("AEGuiHandler: unknown token %d for server GUI element", ordinal);
            return new ContainerNull();
        }

        final AEGuiKey guiKey = pending.getGuiKey();
        final AEPartLocation side = pending.getSide();
        final boolean usingItemOnTile = pending.isUsingItemOnTile();

        // 解析宿主
        final Object host = resolveHost(guiKey, player, w, x, y, z, side, usingItemOnTile);
        if (host == null) {
            return new ContainerNull();
        }

        // Through AEMUIGuiFactory create Container
        final Container container = AEMUIGuiFactory.createContainer(guiKey, player.inventory, host);
        if (container != null) {
            return updateOpenContext(container, w, x, y, z, side, host);
        }

        AELog.warn("No container factory registered for GUI key: %s", guiKey.getId());
        return new ContainerNull();
    }

    @Override
    public Object getClientGuiElement(final int ordinal, final EntityPlayer player, final World w,
            final int x, final int y, final int z) {
        final PendingGuiOpen pending = consumeToken(ordinal);
        if (pending == null) {
            AELog.warn("AEGuiHandler: unknown token %d for client GUI element", ordinal);
            return new GuiNull(new ContainerNull());
        }

        final AEGuiKey guiKey = pending.getGuiKey();
        final AEPartLocation side = pending.getSide();
        final boolean usingItemOnTile = pending.isUsingItemOnTile();

        // 解析宿主
        final Object host = resolveHost(guiKey, player, w, x, y, z, side, usingItemOnTile);
        if (host == null) {
            return new GuiNull(new ContainerNull());
        }

        // Through AEMUIGuiFactory create GUI
        final Object gui = AEMUIGuiFactory.createGui(guiKey, player.inventory, host);
        if (gui != null) {
            return gui;
        }

        AELog.warn("No GUI factory registered for GUI key: %s", guiKey.getId());
        return new GuiNull(new ContainerNull());
    }

    // ========== 宿主解析 ==========

    /**
     * 根据 {@link AEGuiKey} 的 {@link GuiHostType} 解析 GUI 的宿主对象。
     *
     * @return 解析到的宿主对象，如果不匹配则返回 null
     */
    @Nullable
    public static Object resolveHost(final AEGuiKey guiKey, final EntityPlayer player, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side, final boolean usingItemOnTile) {
        final GuiHostType type = guiKey.getHostType();

        // Item 类型宿主
        if (type.isItem()) {
            final Object itemHost = resolveItemHost(player, w, x, y, z, usingItemOnTile);
            if (itemHost != null && guiKey.isValidHost(itemHost)) {
                return itemHost;
            }
        }

        // Tile / Part 类型宿主
        if (type.isTile()) {
            final Object tileHost = resolveTileHost(guiKey, w, x, y, z, side);
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
    private static Object resolveTileHost(final AEGuiKey guiKey, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side) {
        final TileEntity te = w.getTileEntity(new BlockPos(x, y, z));
        if (te instanceof IPartHost) {
            final IPart part = ((IPartHost) te).getPart(side);
            if (part != null && guiKey.isValidHost(part)) {
                return part;
            }
        } else {
            if (te != null && guiKey.isValidHost(te)) {
                return te;
            }
        }
        return null;
    }

    /**
     * 从 ItemStack 获取 GUI 对象（IGuiItem 或 WirelessTerminalGuiObject）。
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

    // ========== 安全检查（基于 AEGuiKey） ==========

    /**
     * 检查玩家是否有权限打开指定 {@link AEGuiKey} 的 GUI。
     *
     * @param guiKey GUI 标识键
     * @param te     TileEntity（可为 null）
     * @param x      x 坐标或槽位索引
     * @param y      y 坐标
     * @param z      z 坐标
     * @param side   方向
     * @param player 玩家
     * @return 如果有权限则返回 true
     */
    public static boolean hasPermissions(final AEGuiKey guiKey, @Nullable final TileEntity te,
            final int x, final int y, final int z,
            final AEPartLocation side, final EntityPlayer player) {
        final World w = player.getEntityWorld();
        final BlockPos pos = new BlockPos(x, y, z);

        if (!appeng.util.WorldHelper.hasPermissions(
                te != null ? new DimensionalCoord(te) : new DimensionalCoord(player.world, pos),
                player)) {
            return false;
        }

        final GuiHostType type = guiKey.getHostType();

        if (type.isItem()) {
            final ItemStack it = player.inventory.getCurrentItem();
            if (!it.isEmpty() && it.getItem() instanceof IGuiItem) {
                final Object myItem = ((IGuiItem) it.getItem()).getGuiObject(it, w, pos);
                if (guiKey.isValidHost(myItem)) {
                    return true;
                }
            }
        }

        if (type.isTile()) {
            final TileEntity tileEntity = w.getTileEntity(pos);
            if (tileEntity instanceof IPartHost) {
                final IPart part = ((IPartHost) tileEntity).getPart(side);
                if (guiKey.isValidHost(part)) {
                    return securityCheck(guiKey, part, player);
                }
            } else {
                if (guiKey.isValidHost(tileEntity)) {
                    return securityCheck(guiKey, tileEntity, player);
                }
            }
        }

        return false;
    }

    /**
     * 安全检查：校验玩家对网络的权限。
     */
    public static boolean securityCheck(final AEGuiKey guiKey, final Object host,
            final EntityPlayer player) {
        final SecurityPermissions requiredPermission = guiKey.getRequiredPermission();
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

    // ========== 旧体系兼容方法 ==========

    /**
     * @deprecated 使用 {@link #hasPermissions(AEGuiKey, TileEntity, int, int, int, AEPartLocation, EntityPlayer)} 代替。
     */
    @Deprecated
    public static boolean hasPermissions(final GuiBridge guiBridge, @Nullable final TileEntity te,
            final int x, final int y, final int z,
            final AEPartLocation side, final EntityPlayer player) {
        final AEGuiKey key = AEGuiKeys.fromLegacy(guiBridge);
        if (key == null) {
            AELog.warn("GuiBridge %s has no AEGuiKey mapping for permission check", guiBridge.name());
            return false;
        }
        return hasPermissions(key, te, x, y, z, side, player);
    }

    /**
     * @deprecated 使用 {@link #securityCheck(AEGuiKey, Object, EntityPlayer)} 代替。
     */
    @Deprecated
    public static boolean securityCheck(final GuiBridge guiBridge, final Object host,
            final EntityPlayer player) {
        final AEGuiKey key = AEGuiKeys.fromLegacy(guiBridge);
        if (key == null) {
            AELog.warn("GuiBridge %s has no AEGuiKey mapping for security check", guiBridge.name());
            return false;
        }
        return securityCheck(key, host, player);
    }

    /**
     * @deprecated 使用 {@link #resolveHost(AEGuiKey, EntityPlayer, World, int, int, int, AEPartLocation, boolean)} 代替。
     */
    @Deprecated
    @Nullable
    public static Object resolveHost(final GuiBridge guiBridge, final EntityPlayer player, final World w,
            final int x, final int y, final int z,
            final AEPartLocation side, final boolean usingItemOnTile) {
        final AEGuiKey key = AEGuiKeys.fromLegacy(guiBridge);
        if (key == null) {
            AELog.warn("GuiBridge %s has no AEGuiKey mapping for host resolution", guiBridge.name());
            return null;
        }
        return resolveHost(key, player, w, x, y, z, side, usingItemOnTile);
    }
}

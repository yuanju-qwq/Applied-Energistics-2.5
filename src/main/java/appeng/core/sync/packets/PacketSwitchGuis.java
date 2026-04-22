/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.core.sync.packets;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import appeng.api.networking.security.IActionHost;
import appeng.container.AEBaseContainer;
import appeng.container.ContainerOpenContext;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AELog;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;

/**
 * GUI 切换网络包。
 * <p>
 * 客户端发送此包告知服务端切换到另一个 GUI。
 * 网络编码使用 {@link ResourceLocation} 字符串标识目标 GUI。
 *
 * @see AEGuiKey
 */
public class PacketSwitchGuis extends AppEngPacket {

    private final AEGuiKey newGui;

    // ========== 网络解码构造 ==========

    public PacketSwitchGuis(final ByteBuf stream) {
        final int len = stream.readInt();
        final byte[] bytes = new byte[len];
        stream.readBytes(bytes);
        final ResourceLocation id = new ResourceLocation(new String(bytes, StandardCharsets.UTF_8));
        final AEGuiKey resolved = AEGuiKeys.fromId(id);
        if (resolved == null) {
            AELog.warn("PacketSwitchGuis: unknown GUI id '%s', ignoring", id);
        }
        this.newGui = resolved;
    }

    // ========== 新体系构造（AEGuiKey） ==========

    /**
     * 使用 {@link AEGuiKey} 构造 GUI 切换包（推荐）。
     */
    public PacketSwitchGuis(final AEGuiKey newGui) {
        this.newGui = newGui;

        final byte[] idBytes = newGui.getId().toString().getBytes(StandardCharsets.UTF_8);
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(idBytes.length);
        data.writeBytes(idBytes);
        this.configureWrite(data);
    }

    // ========== 旧体系兼容构造（GuiBridge） ==========

    /**
     * @deprecated 使用 {@link #PacketSwitchGuis(AEGuiKey)} 代替。
     */
    @Deprecated
    public PacketSwitchGuis(final GuiBridge newGui) {
        this(requireGuiKey(newGui));
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (this.newGui == null) {
            return;
        }
        final Container c = player.openContainer;
        if (c instanceof AEBaseContainer) {
            final AEBaseContainer bc = (AEBaseContainer) c;
            final ContainerOpenContext context = bc.getOpenContext();
            if (context != null) {
                final Object target = bc.getTarget();
                if (target instanceof IActionHost) {
                    final IActionHost ah = (IActionHost) target;

                    final TileEntity te = context.getTile();

                    if (te != null) {
                        Platform.openGUI(player, te, bc.getOpenContext().getSide(), this.newGui);
                    } else {
                        if (ah instanceof IInventorySlotAware) {
                            IInventorySlotAware i = ((IInventorySlotAware) ah);
                            Platform.openGUI(player, i.getInventorySlot(), this.newGui, i.isBaubleSlot());
                        }
                    }
                }
            }
        }
    }

    // ========== 内部辅助 ==========

    private static AEGuiKey requireGuiKey(GuiBridge bridge) {
        final AEGuiKey key = AEGuiKeys.fromLegacy(bridge);
        if (key == null) {
            throw new IllegalArgumentException("GuiBridge " + bridge + " has no AEGuiKey mapping");
        }
        return key;
    }
}

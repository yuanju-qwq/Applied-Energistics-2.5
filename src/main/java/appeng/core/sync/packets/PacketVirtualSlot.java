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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;

/**
 * 用于在客户端和服务端之间同步虚拟槽位（IAEStack<?> 泛型栈）的网络包。
 * <p>
 * 服务端通过此包将 IAEStackInventory 的变更批量推送到客户端；
 * 客户端通过此包将 VirtualMEPhantomSlot 的用户操作发送到服务端。
 */
public class PacketVirtualSlot extends AppEngPacket {

    private final StorageName invName;
    private final Int2ObjectMap<IAEStack<?>> slotStacks;

    /**
     * 从 ByteBuf 反序列化（接收端构造）。
     */
    public PacketVirtualSlot(final ByteBuf buf) {
        this.invName = StorageName.values()[buf.readInt()];
        this.slotStacks = new Int2ObjectOpenHashMap<>();

        final int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            final int slot = buf.readInt();
            if (buf.readBoolean()) {
                this.slotStacks.put(slot, Platform.readStackByte(buf));
            } else {
                this.slotStacks.put(slot, null);
            }
        }
    }

    /**
     * 批量发送多个槽位的变更（服务端 → 客户端同步）。
     */
    public PacketVirtualSlot(final StorageName invName, final Int2ObjectMap<IAEStack<?>> slotStacks) {
        this.invName = invName;
        this.slotStacks = null;

        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());

        buf.writeInt(invName.ordinal());
        buf.writeInt(slotStacks.size());
        for (Int2ObjectMap.Entry<IAEStack<?>> entry : slotStacks.int2ObjectEntrySet()) {
            buf.writeInt(entry.getIntKey());
            IAEStack<?> stack = entry.getValue();
            buf.writeBoolean(stack != null);
            if (stack != null) {
                Platform.writeStackByte(stack, buf);
            }
        }

        this.configureWrite(buf);
    }

    /**
     * 发送单个槽位的变更（客户端 → 服务端，或服务端 → 客户端）。
     */
    public PacketVirtualSlot(final StorageName invName, final int slotIndex, final IAEStack<?> stack) {
        this.invName = invName;
        this.slotStacks = null;

        final ByteBuf buf = Unpooled.buffer();
        buf.writeInt(this.getPacketID());

        buf.writeInt(invName.ordinal());
        buf.writeInt(1);
        buf.writeInt(slotIndex);
        buf.writeBoolean(stack != null);
        if (stack != null) {
            Platform.writeStackByte(stack, buf);
        }

        this.configureWrite(buf);
    }

    @Override
    public void clientPacketData(INetworkInfo network, AppEngPacket packet, EntityPlayer player) {
        final Container c = player.openContainer;
        if (c instanceof IVirtualSlotHolder) {
            ((IVirtualSlotHolder) c).receiveSlotStacks(this.invName, this.slotStacks);
        }
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        final Container c = player.openContainer;
        if (c instanceof IVirtualSlotSource) {
            // 客户端发送的单槽位更新
            for (Int2ObjectMap.Entry<IAEStack<?>> entry : this.slotStacks.int2ObjectEntrySet()) {
                ((IVirtualSlotSource) c).updateVirtualSlot(this.invName, entry.getIntKey(), entry.getValue());
            }
        }
    }
}

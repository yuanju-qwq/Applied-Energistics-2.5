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

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.items.contents.PinList;

/**
 * 服务端 → 客户端：同步 Pins 数据和配置。
 * <p>
 * 数据格式：
 * <ul>
 * <li>1 byte: maxPlayerPinRows ordinal</li>
 * <li>1 byte: maxCraftingPinRows ordinal</li>
 * <li>1 byte: sectionOrder ordinal</li>
 * <li>对每个槽位 [0, TOTAL_SLOTS)：先 1 byte 是否非空，如果非空则跟随泛型栈数据</li>
 * </ul>
 */
public class PacketPinsUpdate extends AppEngPacket {

    // 客户端读取的结果
    private PinsRows maxPlayerPinRows;
    private PinsRows maxCraftingPinRows;
    private PinSectionOrder sectionOrder;
    private final PinList pinList;

    // 反序列化（客户端接收）
    public PacketPinsUpdate(final ByteBuf stream) throws IOException {
        this.pinList = new PinList();

        this.maxPlayerPinRows = PinsRows.fromOrdinal(stream.readByte());
        this.maxCraftingPinRows = PinsRows.fromOrdinal(stream.readByte());
        this.sectionOrder = PinSectionOrder.values()[stream.readByte()];

        int count = stream.readShort();
        for (int i = 0; i < count; i++) {
            int slotIndex = stream.readShort();
            boolean hasStack = stream.readBoolean();
            if (hasStack) {
                IAEStack<?> stack = IAEStack.fromPacketGeneric(stream);
                this.pinList.setPin(slotIndex, stack);
            }
        }
    }

    // 序列化（服务端发送）
    public PacketPinsUpdate(PinsRows maxPlayerPinRows, PinsRows maxCraftingPinRows,
            PinSectionOrder sectionOrder, PinList pinList) {
        this.maxPlayerPinRows = maxPlayerPinRows;
        this.maxCraftingPinRows = maxCraftingPinRows;
        this.sectionOrder = sectionOrder;
        this.pinList = pinList;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        data.writeByte(maxPlayerPinRows.ordinal());
        data.writeByte(maxCraftingPinRows.ordinal());
        data.writeByte(sectionOrder.ordinal());

        // 统计非空槽位数量
        int nonNullCount = 0;
        for (int i = 0; i < PinList.TOTAL_SLOTS; i++) {
            if (pinList.getPin(i) != null) {
                nonNullCount++;
            }
        }

        data.writeShort(nonNullCount);
        for (int i = 0; i < PinList.TOTAL_SLOTS; i++) {
            IAEStack<?> stack = pinList.getPin(i);
            if (stack != null) {
                data.writeShort(i);
                data.writeBoolean(true);
                try {
                    IAEStack.writeToPacketGeneric(data, stack);
                } catch (IOException e) {
                    AELog.warn(e, "Failed to write pin stack to packet at slot %d", i);
                    data.writeBoolean(false);
                }
            }
        }

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet,
            final EntityPlayer player) {
        final Container c = player.openContainer;

        if (c instanceof ContainerMEMonitorable) {
            ((ContainerMEMonitorable) c).postPinsUpdate(
                    this.pinList, this.maxPlayerPinRows, this.maxCraftingPinRows, this.sectionOrder);
        }
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet,
            final EntityPlayer player) {
        // 此包仅服务端 → 客户端
    }
}

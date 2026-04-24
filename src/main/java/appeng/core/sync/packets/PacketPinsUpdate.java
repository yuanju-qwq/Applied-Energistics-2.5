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
import net.minecraft.entity.player.EntityPlayerMP;
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
import appeng.helpers.IPinsHandler;
import appeng.items.contents.PinList;

/**
 * Bidirectional pin data packet.
 * <p>
 * <b>Server → Client (full sync):</b> contains pin rows config + all pinned stacks.
 * <p>
 * <b>Client → Server (rows update):</b> contains only the requested pin rows counts.
 * The server will process the request and send back a full sync.
 * <p>
 * Discriminated by a leading mode byte:
 * <ul>
 *   <li>MODE_FULL_SYNC (0) — server→client full state</li>
 *   <li>MODE_ROWS_UPDATE (1) — client→server row count change request</li>
 * </ul>
 */
public class PacketPinsUpdate extends AppEngPacket {

    private static final byte MODE_FULL_SYNC = 0;
    private static final byte MODE_ROWS_UPDATE = 1;

    private byte mode;
    private PinsRows maxPlayerPinRows;
    private PinsRows maxCraftingPinRows;
    private PinSectionOrder sectionOrder;
    private final PinList pinList;

    // ========== Deserialization ==========

    public PacketPinsUpdate(final ByteBuf stream) throws IOException {
        this.pinList = new PinList();
        this.mode = stream.readByte();

        this.maxPlayerPinRows = PinsRows.fromOrdinal(stream.readByte());
        this.maxCraftingPinRows = PinsRows.fromOrdinal(stream.readByte());

        if (this.mode == MODE_FULL_SYNC) {
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
        } else {
            this.sectionOrder = PinSectionOrder.PLAYER_FIRST;
        }
    }

    // ========== Server → Client: full sync ==========

    public PacketPinsUpdate(PinsRows maxPlayerPinRows, PinsRows maxCraftingPinRows,
            PinSectionOrder sectionOrder, PinList pinList) {
        this.mode = MODE_FULL_SYNC;
        this.maxPlayerPinRows = maxPlayerPinRows;
        this.maxCraftingPinRows = maxCraftingPinRows;
        this.sectionOrder = sectionOrder;
        this.pinList = pinList;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        data.writeByte(MODE_FULL_SYNC);
        data.writeByte(maxPlayerPinRows.ordinal());
        data.writeByte(maxCraftingPinRows.ordinal());
        data.writeByte(sectionOrder.ordinal());

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
                    AELog.warn(e, String.format("Failed to write pin stack to packet at slot %d", i));
                    data.writeBoolean(false);
                }
            }
        }

        this.configureWrite(data);
    }

    // ========== Client → Server: rows update request ==========

    public PacketPinsUpdate(PinsRows craftingRows, PinsRows playerRows) {
        this.mode = MODE_ROWS_UPDATE;
        this.maxCraftingPinRows = craftingRows;
        this.maxPlayerPinRows = playerRows;
        this.sectionOrder = PinSectionOrder.PLAYER_FIRST;
        this.pinList = new PinList();

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        data.writeByte(MODE_ROWS_UPDATE);
        data.writeByte(playerRows.ordinal());
        data.writeByte(craftingRows.ordinal());

        this.configureWrite(data);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet,
            final EntityPlayer player) {
        if (this.mode != MODE_FULL_SYNC) {
            return;
        }
        final Container c = player.openContainer;

        if (c instanceof ContainerMEMonitorable) {
            ((ContainerMEMonitorable) c).postPinsUpdate(
                    this.pinList, this.maxPlayerPinRows, this.maxCraftingPinRows, this.sectionOrder);
        }
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet,
            final EntityPlayer player) {
        if (this.mode != MODE_ROWS_UPDATE) {
            return;
        }
        final Container c = player.openContainer;
        if (c instanceof ContainerMEMonitorable container) {
            final IPinsHandler handler = container.getServerPinsHandler();
            if (handler != null) {
                handler.setMaxPlayerPinRows(this.maxPlayerPinRows);
                handler.setMaxCraftingPinRows(this.maxCraftingPinRows);
                // Trigger a full sync back to client
                container.sendPinsUpdate((EntityPlayerMP) player);
            }
        }
    }
}

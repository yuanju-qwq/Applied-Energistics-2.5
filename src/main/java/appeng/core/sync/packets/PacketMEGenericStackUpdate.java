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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.stacks.GenericStack;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.implementations.ContainerNetworkStatus;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.fluids.container.ContainerFluidTerminal;
import appeng.fluids.container.ContainerMEPortableFluidCell;

/**
 * New-generation inventory update packet based on {@link GenericStack}.
 * <p>
 * Replaces {@link PacketMEInventoryUpdate} for transmitting ME network inventory snapshots and incremental updates.
 * Uses {@link GenericStack#writeBuffer} / {@link GenericStack#readBuffer} for serialization,
 * which natively supports all resource types (items, fluids, etc.) without relying on IAEStack.
 * <p>
 * The packet payload is GZIP-compressed. A leading {@code ref} byte distinguishes
 * full-sync vs incremental update contexts (same convention as the legacy packet).
 */
public class PacketMEGenericStackUpdate extends AppEngPacket {

    private static final int UNCOMPRESSED_PACKET_BYTE_LIMIT = 16 * 1024 * 1024;
    private static final int OPERATION_BYTE_LIMIT = 2 * 1024;
    private static final int TEMP_BUFFER_SIZE = 1024;
    private static final int STREAM_MASK = 0xff;

    // ==================== Read side (client) ====================

    @Nullable
    private final List<GenericStack> list;

    private final byte ref;

    // ==================== Write side (server) ====================

    @Nullable
    private final ByteBuf data;

    @Nullable
    private final GZIPOutputStream compressFrame;

    private int writtenBytes = 0;
    private boolean empty = true;

    // ==================== Deserialization constructor (client) ====================

    /**
     * Packet deserialization constructor. Called by the packet handler on the client side.
     */
    public PacketMEGenericStackUpdate(final ByteBuf stream) throws IOException {
        this.data = null;
        this.compressFrame = null;
        this.list = new ArrayList<>();
        this.ref = stream.readByte();

        final PacketBuffer wrappedStream = new PacketBuffer(stream);

        try (GZIPInputStream gzReader = new GZIPInputStream(new InputStream() {
            @Override
            public int read() throws IOException {
                if (wrappedStream.readableBytes() <= 0) {
                    return -1;
                }
                return wrappedStream.readByte() & STREAM_MASK;
            }
        })) {
            final ByteBuf uncompressedBuf = Unpooled.buffer(stream.readableBytes());
            final byte[] tmp = new byte[TEMP_BUFFER_SIZE];

            while (gzReader.available() != 0) {
                final int bytes = gzReader.read(tmp);
                if (bytes > 0) {
                    uncompressedBuf.writeBytes(tmp, 0, bytes);
                }
            }

            final PacketBuffer uncompressed = new PacketBuffer(uncompressedBuf);
            while (uncompressed.readableBytes() > 0) {
                GenericStack stack = GenericStack.readBuffer(uncompressed);
                if (stack != null) {
                    this.list.add(stack);
                }
            }
        }

        this.empty = this.list.isEmpty();
    }

    // ==================== Serialization constructors (server) ====================

    /**
     * Creates a new packet for writing with ref=0.
     */
    public PacketMEGenericStackUpdate() throws IOException {
        this((byte) 0);
    }

    /**
     * Creates a new packet for writing with the given ref byte.
     *
     * @param ref context identifier (0 = default, used to distinguish full-sync vs incremental)
     */
    public PacketMEGenericStackUpdate(final byte ref) throws IOException {
        this.ref = ref;
        this.data = Unpooled.buffer(OPERATION_BYTE_LIMIT);
        this.data.writeInt(this.getPacketID());
        this.data.writeByte(this.ref);

        this.compressFrame = new GZIPOutputStream(new OutputStream() {
            @Override
            public void write(final int value) throws IOException {
                PacketMEGenericStackUpdate.this.data.writeByte(value);
            }
        });

        this.list = null;
    }

    // ==================== Client-side packet handling ====================

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (c instanceof ContainerCraftConfirm craftConfirm) {
            craftConfirm.postGenericStackUpdate(this.list, this.ref);
        }

        if (c instanceof ContainerCraftingCPU craftingCPU) {
            craftingCPU.postGenericStackUpdate(this.list, this.ref);
        }

        if (c instanceof ContainerMEMonitorable meMonitorable) {
            meMonitorable.postGenericStackUpdate(this.list);
        }

        if (c instanceof ContainerWirelessDualInterfaceTerminal wirelessDualInterface) {
            wirelessDualInterface.postGenericStackUpdate(this.list);
        }

        if (c instanceof ContainerNetworkStatus networkStatus) {
            networkStatus.postGenericStackUpdate(this.list);
        }

        if (c instanceof ContainerFluidTerminal fluidTerminal) {
            fluidTerminal.postGenericStackUpdate(this.list);
        }

        if (c instanceof ContainerMEPortableFluidCell portableFluidCell) {
            portableFluidCell.postGenericStackUpdate(this.list);
        }
    }

    // ==================== Server-side packet writing ====================

    @Nullable
    @Override
    public FMLProxyPacket getProxy() {
        try {
            this.compressFrame.close();
            this.configureWrite(this.data);
            return super.getProxy();
        } catch (final IOException e) {
            AELog.debug(e);
        }
        return null;
    }

    /**
     * Appends a GenericStack to this packet's payload.
     *
     * @param stack the stack to append (must not be null)
     * @throws BufferOverflowException if the packet payload exceeds the size limit
     */
    public void appendStack(final GenericStack stack) throws IOException, BufferOverflowException {
        final PacketBuffer tmp = new PacketBuffer(Unpooled.buffer(OPERATION_BYTE_LIMIT));
        GenericStack.writeBuffer(stack, tmp);

        this.compressFrame.flush();
        if (this.writtenBytes + tmp.readableBytes() > UNCOMPRESSED_PACKET_BYTE_LIMIT) {
            throw new BufferOverflowException();
        } else {
            this.writtenBytes += tmp.readableBytes();
            this.compressFrame.write(tmp.array(), 0, tmp.readableBytes());
            this.empty = false;
        }
    }

    // ==================== Accessors ====================

    public int getLength() {
        return this.data.readableBytes();
    }

    public boolean isEmpty() {
        return this.empty;
    }

    /**
     * @return all deserialized GenericStacks (items, fluids, etc.)
     */
    @Nullable
    public List<GenericStack> getList() {
        return this.list;
    }

    public byte getRef() {
        return this.ref;
    }
}

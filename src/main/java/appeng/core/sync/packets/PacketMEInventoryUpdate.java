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
import appeng.api.storage.data.IAEStack;
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

public class PacketMEInventoryUpdate extends AppEngPacket {
    private static final int UNCOMPRESSED_PACKET_BYTE_LIMIT = 16 * 1024 * 1024;
    private static final int OPERATION_BYTE_LIMIT = 2 * 1024;
    private static final int TEMP_BUFFER_SIZE = 1024;
    private static final int STREAM_MASK = 0xff;

    // GenericStack format marker byte placed at the beginning of compressed payload.
    // 0x00-0xFE are reserved for legacy IAEStack type network IDs,
    // so 0xFF serves as a non-colliding discriminator.
    private static final byte FORMAT_GENERIC_STACK = (byte) 0xFF;

    // Read side — legacy IAEStack format.
    @Nullable
    private final List<IAEStack<?>> list;
    // Read side — new GenericStack format (mutually exclusive with list).
    @Nullable
    private final List<GenericStack> genericList;

    private final byte ref;

    // Write side.
    @Nullable
    private final ByteBuf data;
    @Nullable
    private final GZIPOutputStream compressFrame;

    private int writtenBytes = 0;
    private boolean empty = true;

    // Tracks whether this packet is being written in GenericStack format.
    // Set to true on first appendStack(GenericStack) call.
    private transient boolean usesGenericFormat = false;

    // Deserialization constructor — detects format byte and dispatches accordingly.
    public PacketMEInventoryUpdate(final ByteBuf stream) throws IOException {
        this.data = null;
        this.compressFrame = null;
        this.list = new ArrayList<>();
        this.genericList = null;
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

            if (uncompressedBuf.readableBytes() > 0) {
                // Peek first byte to detect format
                byte firstByte = uncompressedBuf.getByte(uncompressedBuf.readerIndex());
                if (firstByte == FORMAT_GENERIC_STACK) {
                    // Skip the format marker
                    uncompressedBuf.readByte();
                    this.genericList = new ArrayList<>();
                    this.list.clear();
                    final PacketBuffer uncompressed = new PacketBuffer(uncompressedBuf);
                    while (uncompressed.readableBytes() > 0) {
                        GenericStack stack = GenericStack.readBuffer(uncompressed);
                        if (stack != null) {
                            this.genericList.add(stack);
                        }
                    }
                } else {
                    // Legacy IAEStack format — first byte is a type network ID
                    while (uncompressedBuf.readableBytes() > 0) {
                        IAEStack<?> stack = IAEStack.fromPacketGeneric(uncompressedBuf);
                        if (stack != null) {
                            this.list.add(stack);
                        }
                    }
                }
            }
        }

        this.empty = this.list.isEmpty() && (this.genericList == null || this.genericList.isEmpty());
    }

    // api
    public PacketMEInventoryUpdate() throws IOException {
        this((byte) 0);
    }

    // api
    public PacketMEInventoryUpdate(final byte ref) throws IOException {
        this.ref = ref;
        this.data = Unpooled.buffer(OPERATION_BYTE_LIMIT);
        this.data.writeInt(this.getPacketID());
        this.data.writeByte(this.ref);

        this.compressFrame = new GZIPOutputStream(new OutputStream() {
            @Override
            public void write(final int value) throws IOException {
                PacketMEInventoryUpdate.this.data.writeByte(value);
            }
        });

        this.list = null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (this.genericList != null) {
            dispatchGenericStack(c);
        } else {
            dispatchIAEStack(c);
        }
    }

    private void dispatchGenericStack(final Container c) {
        if (c instanceof ContainerCraftConfirm) {
            ((ContainerCraftConfirm) c).postGenericStackUpdate(this.genericList, this.ref);
        }
        if (c instanceof ContainerCraftingCPU) {
            ((ContainerCraftingCPU) c).postGenericStackUpdate(this.genericList, this.ref);
        }
        if (c instanceof ContainerMEMonitorable) {
            ((ContainerMEMonitorable) c).postGenericStackUpdate(this.genericList);
        }
        if (c instanceof ContainerWirelessDualInterfaceTerminal) {
            ((ContainerWirelessDualInterfaceTerminal) c).postGenericStackUpdate(this.genericList);
        }
        if (c instanceof ContainerNetworkStatus) {
            ((ContainerNetworkStatus) c).postGenericStackUpdate(this.genericList);
        }
        if (c instanceof ContainerFluidTerminal) {
            ((ContainerFluidTerminal) c).postGenericStackUpdate(this.genericList);
        }
        if (c instanceof ContainerMEPortableFluidCell) {
            ((ContainerMEPortableFluidCell) c).postGenericStackUpdate(this.genericList);
        }
    }

    private void dispatchIAEStack(final Container c) {
        if (c instanceof ContainerCraftConfirm) {
            ((ContainerCraftConfirm) c).postGenericUpdate(this.list, this.ref);
        }
        if (c instanceof ContainerCraftingCPU) {
            ((ContainerCraftingCPU) c).postGenericUpdate(this.list, this.ref);
        }
        if (c instanceof ContainerMEMonitorable) {
            ((ContainerMEMonitorable) c).postUpdate(this.list);
        }
        if (c instanceof ContainerWirelessDualInterfaceTerminal) {
            ((ContainerWirelessDualInterfaceTerminal) c).postUpdate(this.list);
        }
        if (c instanceof ContainerNetworkStatus) {
            ((ContainerNetworkStatus) c).postUpdate(this.list);
        }
        if (c instanceof ContainerFluidTerminal) {
            ((ContainerFluidTerminal) c).postUpdate(this.list);
        }
        if (c instanceof ContainerMEPortableFluidCell) {
            ((ContainerMEPortableFluidCell) c).postUpdate(this.list);
        }
    }

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
     * Append an IAEStack (legacy format).
     * Cannot be mixed with {@link #appendStack(GenericStack)} in the same packet.
     */
    public void appendStack(final IAEStack<?> is) throws IOException, BufferOverflowException {
        if (this.usesGenericFormat) {
            throw new IllegalStateException("Cannot mix IAEStack and GenericStack in the same packet");
        }

        final ByteBuf tmp = Unpooled.buffer(OPERATION_BYTE_LIMIT);
        IAEStack.writeToPacketGeneric(tmp, is);

        this.compressFrame.flush();
        if (this.writtenBytes + tmp.readableBytes() > UNCOMPRESSED_PACKET_BYTE_LIMIT) {
            throw new BufferOverflowException();
        } else {
            this.writtenBytes += tmp.readableBytes();
            this.compressFrame.write(tmp.array(), 0, tmp.readableBytes());
            this.empty = false;
        }
    }

    /**
     * Append a GenericStack (AEKey-based format).
     * Writes a {@value #FORMAT_GENERIC_STACK} marker byte before the first entry.
     * Cannot be mixed with {@link #appendStack(IAEStack)} in the same packet.
     */
    public void appendStack(final GenericStack stack) throws IOException, BufferOverflowException {
        if (!this.usesGenericFormat && this.writtenBytes > 0) {
            throw new IllegalStateException("Cannot mix IAEStack and GenericStack in the same packet");
        }

        final PacketBuffer tmp = new PacketBuffer(Unpooled.buffer(OPERATION_BYTE_LIMIT));
        GenericStack.writeBuffer(stack, tmp);

        this.compressFrame.flush();
        if (!this.usesGenericFormat) {
            // Write format marker byte before first GenericStack entry
            this.compressFrame.write(FORMAT_GENERIC_STACK);
            this.writtenBytes += 1;
            this.usesGenericFormat = true;
        }
        if (this.writtenBytes + tmp.readableBytes() > UNCOMPRESSED_PACKET_BYTE_LIMIT) {
            throw new BufferOverflowException();
        } else {
            this.writtenBytes += tmp.readableBytes();
            this.compressFrame.write(tmp.array(), 0, tmp.readableBytes());
            this.empty = false;
        }
    }

    public int getLength() {
        return this.data.readableBytes();
    }

    public boolean isEmpty() {
        return this.empty;
    }

    /**
     * @return deserialized IAEStacks in legacy format, or null if this packet carries GenericStack format
     */
    @Nullable
    public List<IAEStack<?>> getList() {
        return this.list;
    }

    /**
     * @return deserialized GenericStacks in AEKey-based format,
     *         or null if this packet carries legacy IAEStack format
     */
    @Nullable
    public List<GenericStack> getGenericList() {
        return this.genericList;
    }
}

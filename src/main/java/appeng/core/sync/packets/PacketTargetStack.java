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

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.storage.data.IAEStack;
import appeng.container.AEBaseContainer;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;

/**
 * Unified packet for sending any type of target stack (item, fluid, or future types)
 * from client to server. Replaces the separate {@link PacketTargetItemStack} and
 * {@link PacketTargetFluidStack} packets.
 * <p>
 * Uses {@link IAEStack#writeToPacketGeneric} / {@link IAEStack#fromPacketGeneric}
 * for type-agnostic serialization.
 */
public class PacketTargetStack extends AppEngPacket {

    private IAEStack<?> stack;

    // Deserialization constructor (called by packet handler)
    public PacketTargetStack(final ByteBuf stream) {
        try {
            if (stream.readableBytes() > 0) {
                this.stack = IAEStack.fromPacketGeneric(stream);
            } else {
                this.stack = null;
            }
        } catch (Exception ex) {
            AELog.debug(ex);
            this.stack = null;
        }
    }

    // API constructor (called by client code)
    public PacketTargetStack(IAEStack<?> stack) {
        this.stack = stack;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        if (stack != null) {
            try {
                IAEStack.writeToPacketGeneric(data, stack);
            } catch (Exception ex) {
                AELog.debug(ex);
            }
        }
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (player.openContainer instanceof AEBaseContainer baseContainer) {
            baseContainer.setTargetStack(this.stack);
        }
    }
}

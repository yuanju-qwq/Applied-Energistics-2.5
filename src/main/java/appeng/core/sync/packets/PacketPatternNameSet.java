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
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternValueName;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotFake;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;

/**
 * 样板名称设置包：客户端输入自定义名称后发送到服务端，
 * 服务端修改对应样板槽位中物品的显示名称，然后切换回原始 GUI。
 */
public class PacketPatternNameSet extends AppEngPacket {

    private final GuiBridge originGui;
    private final String name;
    private final int valueIndex;

    // 从网络流中读取
    public PacketPatternNameSet(final ByteBuf stream) {
        this.originGui = GuiBridge.values()[stream.readInt()];
        final int nameLen = stream.readInt();
        final byte[] nameBytes = new byte[nameLen];
        stream.readBytes(nameBytes);
        this.name = new String(nameBytes, StandardCharsets.UTF_8);
        this.valueIndex = stream.readInt();
    }

    // 客户端构造
    public PacketPatternNameSet(final GuiBridge originGui, final String name, final int valueIndex) {
        this.originGui = originGui;
        this.name = name;
        this.valueIndex = valueIndex;

        final byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(originGui.ordinal());
        data.writeInt(nameBytes.length);
        data.writeBytes(nameBytes);
        data.writeInt(valueIndex);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerPatternValueName)) {
            return;
        }
        final ContainerPatternValueName cpn = (ContainerPatternValueName) player.openContainer;
        final ContainerOpenContext context = cpn.getOpenContext();
        if (context == null) {
            return;
        }

        // 切换回原始 GUI
        final Object target = cpn.getTarget();
        final TileEntity te = context.getTile();
        if (te != null) {
            Platform.openGUI(player, te, context.getSide(), this.originGui);
        } else if (target instanceof IInventorySlotAware) {
            final IInventorySlotAware i = (IInventorySlotAware) target;
            Platform.openGUI(player, i.getInventorySlot(), this.originGui, i.isBaubleSlot());
        }

        // 在新打开的容器中修改对应槽位的物品名称
        if (player.openContainer != null) {
            if (this.valueIndex >= 0 && this.valueIndex < player.openContainer.inventorySlots.size()) {
                final Slot slot = player.openContainer.inventorySlots.get(this.valueIndex);
                if (slot instanceof SlotFake && slot.getHasStack()) {
                    final ItemStack stack = slot.getStack().copy();
                    if (this.name.isEmpty()) {
                        // 清除自定义名称
                        stack.clearCustomName();
                    } else {
                        stack.setStackDisplayName(this.name);
                    }
                    slot.putStack(stack);
                }
            }
        }
    }
}

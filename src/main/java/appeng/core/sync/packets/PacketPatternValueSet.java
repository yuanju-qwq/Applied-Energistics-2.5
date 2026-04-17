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
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotFake;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;

/**
 * 样板数值设置包：客户端输入精确数量后发送到服务端，
 * 服务端修改对应样板槽位中物品的数量，然后切换回原始 GUI。
 */
public class PacketPatternValueSet extends AppEngPacket {

    private final GuiBridge originGui;
    private final int amount;
    private final int valueIndex;

    // 从网络流中读取
    public PacketPatternValueSet(final ByteBuf stream) {
        this.originGui = GuiBridge.values()[stream.readInt()];
        this.amount = stream.readInt();
        this.valueIndex = stream.readInt();
    }

    // 客户端构造
    public PacketPatternValueSet(final GuiBridge originGui, final int amount, final int valueIndex) {
        this.originGui = originGui;
        this.amount = amount;
        this.valueIndex = valueIndex;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(originGui.ordinal());
        data.writeInt(amount);
        data.writeInt(valueIndex);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (!(player.openContainer instanceof ContainerPatternValueAmount)) {
            return;
        }
        final ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) player.openContainer;
        final ContainerOpenContext context = cpv.getOpenContext();
        if (context == null) {
            return;
        }

        // 切换回原始 GUI
        final Object target = cpv.getTarget();
        final TileEntity te = context.getTile();
        if (te != null) {
            Platform.openGUI(player, te, context.getSide(), this.originGui);
        } else if (target instanceof IInventorySlotAware) {
            final IInventorySlotAware i = (IInventorySlotAware) target;
            Platform.openGUI(player, i.getInventorySlot(), this.originGui, i.isBaubleSlot());
        }

        // 在新打开的容器中修改对应槽位的物品数量
        if (player.openContainer != null) {
            if (this.valueIndex >= 0 && this.valueIndex < player.openContainer.inventorySlots.size()) {
                final Slot slot = player.openContainer.inventorySlots.get(this.valueIndex);
                if (slot instanceof SlotFake && slot.getHasStack()) {
                    final ItemStack stack = slot.getStack().copy();
                    stack.setCount(Math.max(1, this.amount));
                    slot.putStack(stack);
                }
            }
        }
    }
}

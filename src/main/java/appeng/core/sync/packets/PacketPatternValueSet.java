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
import net.minecraft.util.ResourceLocation;

import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotFake;
import appeng.core.AELog;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;

/**
 * 样板数值设置包：客户端输入精确数量后发送到服务端，
 * 服务端修改对应样板槽位中物品的数量，然后切换回原始 GUI。
 * <p>
 * 网络编码使用 {@link ResourceLocation} 字符串标识原始 GUI。
 *
 * @see AEGuiKey
 */
public class PacketPatternValueSet extends AppEngPacket {

    private final AEGuiKey originGui;
    private final int amount;
    private final int valueIndex;

    // ========== 网络解码构造 ==========

    public PacketPatternValueSet(final ByteBuf stream) {
        final int idLen = stream.readInt();
        final byte[] idBytes = new byte[idLen];
        stream.readBytes(idBytes);
        final ResourceLocation id = new ResourceLocation(new String(idBytes, StandardCharsets.UTF_8));
        final AEGuiKey resolved = AEGuiKeys.fromId(id);
        if (resolved == null) {
            AELog.warn("PacketPatternValueSet: unknown GUI id '%s', ignoring", id);
        }
        this.originGui = resolved;
        this.amount = stream.readInt();
        this.valueIndex = stream.readInt();
    }

    // ========== 新体系构造（AEGuiKey） ==========

    /**
     * 使用 {@link AEGuiKey} 构造样板数值设置包（推荐）。
     */
    public PacketPatternValueSet(final AEGuiKey originGui, final int amount, final int valueIndex) {
        this.originGui = originGui;
        this.amount = amount;
        this.valueIndex = valueIndex;

        final byte[] idBytes = originGui.getId().toString().getBytes(StandardCharsets.UTF_8);
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(idBytes.length);
        data.writeBytes(idBytes);
        data.writeInt(amount);
        data.writeInt(valueIndex);
        this.configureWrite(data);
    }

    // ========== 旧体系兼容构造（GuiBridge） ==========

    /**
     * @deprecated 使用 {@link #PacketPatternValueSet(AEGuiKey, int, int)} 代替。
     */
    @Deprecated
    public PacketPatternValueSet(final GuiBridge originGui, final int amount, final int valueIndex) {
        this(requireGuiKey(originGui), amount, valueIndex);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        if (this.originGui == null) {
            return;
        }
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

    // ========== 内部辅助 ==========

    private static AEGuiKey requireGuiKey(GuiBridge bridge) {
        final AEGuiKey key = AEGuiKeys.fromLegacy(bridge);
        if (key == null) {
            throw new IllegalArgumentException("GuiBridge " + bridge + " has no AEGuiKey mapping");
        }
        return key;
    }
}

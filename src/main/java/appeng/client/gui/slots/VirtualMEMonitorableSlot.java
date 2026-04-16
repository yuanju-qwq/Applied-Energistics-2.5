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

package appeng.client.gui.slots;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo;
import appeng.container.AEBaseContainer;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

/**
 * 通用终端（ME Monitorable）使用的虚拟槽位。
 * <p>
 * 从 {@link ItemRepo} 中按索引获取当前应该显示的 AE 栈。
 * 默认配置为显示数量、合成文字和合成图标。
 */
public class VirtualMEMonitorableSlot extends VirtualMESlot {

    private final ItemRepo repo;

    public VirtualMEMonitorableSlot(int id, int x, int y, ItemRepo repo, int slotIndex) {
        super(id, x, y, slotIndex);
        this.repo = repo;
        this.showAmountAlways = true;
        this.showCraftableText = true;
        this.showCraftableIcon = true;
    }

    @Override
    @Nullable
    public IAEStack<?> getAEStack() {
        return this.repo.getReferenceItem(this.slotIndex);
    }

    @Override
    public boolean isVisible() {
        return this.repo.hasPower();
    }

    @Override
    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
        final IAEStack<?> aeStack = this.getAEStack();
        if (aeStack == null) {
            return;
        }

        final EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) {
            return;
        }

        InventoryAction action = null;
        IAEItemStack itemStack = (aeStack instanceof IAEItemStack) ? (IAEItemStack) aeStack : null;

        if (GuiScreen.isShiftKeyDown()) {
            // Shift+点击 = 快速移动
            action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
        } else if (mouseButton == 1) {
            // 右键 = 取出一半/放置单个
            action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
        } else {
            // 左键 = 拾取/放下
            action = InventoryAction.PICKUP_OR_SET_DOWN;

            // 如果栈数量为 0 或按住 Alt 且手上没有物品，触发自动合成
            if (itemStack != null
                    && (itemStack.getStackSize() == 0 || GuiScreen.isAltKeyDown())
                    && player.inventory.getItemStack().isEmpty()) {
                action = InventoryAction.AUTO_CRAFT;
            }
        }

        if (GuiScreen.isCtrlKeyDown() && mouseButton == 2) {
            // Ctrl+中键 = 创造模式复制 或 自动合成
            if (itemStack != null && itemStack.isCraftable()) {
                action = InventoryAction.AUTO_CRAFT;
            } else if (player.capabilities.isCreativeMode) {
                action = InventoryAction.CREATIVE_DUPLICATE;
            }
        }

        if (action != null && itemStack != null) {
            if (player.openContainer instanceof AEBaseContainer container) {
                container.setTargetStack(itemStack);
                final int inventorySize = container.inventorySlots.size();
                final PacketInventoryAction p = new PacketInventoryAction(action, inventorySize, 0);
                NetworkHandler.instance().sendToServer(p);
            }
        }
    }
}

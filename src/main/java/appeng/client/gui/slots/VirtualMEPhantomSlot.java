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

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStack;

/**
 * 虚拟 ME 幽灵槽位，用于在 GUI 中显示和交互泛型 {@link IAEStack}（物品、流体等）。
 * <p>
 * 与普通的 phantom slot 不同，此槽位不依赖 Minecraft 的 {@link net.minecraft.inventory.Slot}，
 * 而是直接操作 {@link IAEStackInventory}。用户点击操作通过
 * {@link PacketVirtualSlot} 网络包同步到服务端。
 * </p>
 */
public class VirtualMEPhantomSlot extends VirtualMESlot {

    /**
     * 类型接受判断函数，决定此槽位是否接受指定类型的栈。
     */
    @FunctionalInterface
    public interface TypeAcceptPredicate {

        boolean test(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton);
    }

    private final IAEStackInventory inventory;
    private final TypeAcceptPredicate acceptType;
    private boolean hidden = false;

    public VirtualMEPhantomSlot(int id, int x, int y, IAEStackInventory inventory, int slotIndex,
            TypeAcceptPredicate acceptType) {
        super(id, x, y, slotIndex);
        this.inventory = inventory;
        this.showAmount = false;
        this.acceptType = acceptType;
    }

    @Nullable
    @Override
    public IAEStack<?> getAEStack() {
        return this.inventory.getAEStackInSlot(this.getSlotIndex());
    }

    public StorageName getStorageName() {
        return this.inventory.getStorageName();
    }

    @Override
    public boolean isSlotEnabled() {
        return !this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    /**
     * 由 {@link appeng.client.gui.AEBaseGui} 的 mouseClicked 调用，桥接到 {@link #handleMouseClicked}。
     */
    @Override
    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
        this.handleMouseClicked(clickStack, false, mouseButton);
    }

    /**
     * 处理鼠标点击事件。
     *
     * @param itemStack     玩家手持的物品栈（客户端）
     * @param isExtraAction 是否为扩展操作（如按住特殊键）
     * @param mouseButton   鼠标按键（0=左键，1=右键）
     */
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        IAEStack<?> currentStack = this.getAEStack();
        final ItemStack hand = itemStack != null ? itemStack.copy() : null;

        if (hand != null && !this.showAmount) {
            hand.setCount(1);
        }

        // 收集当前槽位接受的所有栈类型
        final List<IAEStackType<?>> acceptTypes = new ArrayList<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            if (this.acceptType.test(this, type, mouseButton)) {
                acceptTypes.add(type);
            }
        }

        // 先尝试将手持物品转换为非物品类型（如流体容器 → 流体栈）
        if (hand != null) {
            for (IAEStackType<?> type : acceptTypes) {
                IAEStack<?> converted = type.convertStackFromItem(hand);
                if (converted != null) {
                    currentStack = converted;
                    acceptTypes.clear();
                    isExtraAction = false;
                    break;
                }
            }
        }

        final boolean acceptItem = acceptTypes.contains(ITEM_STACK_TYPE);
        boolean acceptExtra = false;
        for (IAEStackType<?> type : acceptTypes) {
            if (type != ITEM_STACK_TYPE) {
                acceptExtra = true;
                break;
            }
        }

        switch (mouseButton) {
            case 0: { // 左键
                if (hand != null) {
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        // 优先尝试从容器物品中提取非物品栈
                        for (IAEStackType<?> type : acceptTypes) {
                            IAEStack<?> stackFromContainer = type.getStackFromContainerItem(hand);
                            if (stackFromContainer != null) {
                                currentStack = stackFromContainer;
                                break;
                            }
                        }
                    } else if (acceptItem) {
                        currentStack = AEItemStack.fromItemStack(hand);
                    }
                } else {
                    currentStack = null;
                }
                break;
            }
            case 1: { // 右键
                if (hand != null) {
                    hand.setCount(1);

                    IAEStack<?> stackFromContainer = null;
                    for (IAEStackType<?> type : acceptTypes) {
                        stackFromContainer = type.getStackFromContainerItem(hand);
                        if (stackFromContainer != null) {
                            break;
                        }
                    }

                    IAEStack<?> stackForHand = null;
                    if (acceptExtra && (!acceptItem || isExtraAction)) {
                        if (stackFromContainer != null) {
                            stackForHand = stackFromContainer;
                        }
                    } else if (acceptItem) {
                        stackForHand = AEItemStack.fromItemStack(hand);
                    }

                    if (stackForHand != null && this.showAmount
                            && acceptTypes.contains(stackForHand.getStackType())
                            && stackForHand.equals(currentStack)) {
                        currentStack.decStackSize(-1);
                    } else {
                        currentStack = stackForHand;
                    }
                } else if (currentStack != null) {
                    currentStack.decStackSize(1);
                    if (currentStack.getStackSize() <= 0) currentStack = null;
                }
                break;
            }
        }

        // 在客户端立即设置，避免慢网络时的延迟
        inventory.putAEStackInSlot(this.getSlotIndex(), currentStack);

        // 发送到服务端
        NetworkHandler.instance()
                .sendToServer(new PacketVirtualSlot(this.getStorageName(), this.getSlotIndex(), currentStack));
    }
}

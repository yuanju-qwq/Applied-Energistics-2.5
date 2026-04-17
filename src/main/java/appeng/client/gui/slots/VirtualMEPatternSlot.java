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

import net.minecraft.item.ItemStack;

import appeng.tile.inventory.IAEStackInventory;

/**
 * 样板终端使用的虚拟 ME 幽灵槽位，与 {@link VirtualMEPhantomSlot} 的区别在于默认显示数量。
 * <p>
 * 用于样板终端的合成输入/输出槽位，允许用户设置带数量的泛型栈。
 * </p>
 */
public class VirtualMEPatternSlot extends VirtualMEPhantomSlot {

    public VirtualMEPatternSlot(int id, int x, int y, IAEStackInventory inventory, int slotIndex,
            TypeAcceptPredicate acceptType) {
        super(id, x, y, inventory, slotIndex, acceptType);
        this.showAmount = true;
    }

    @Override
    public void handleMouseClicked(@Nullable ItemStack itemStack, boolean isExtraAction, int mouseButton) {
        super.handleMouseClicked(itemStack, isExtraAction, mouseButton);
    }
}

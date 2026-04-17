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

package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;

import appeng.api.config.SecurityPermissions;
import appeng.api.storage.ITerminalHost;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotInaccessible;
import appeng.helpers.Reflected;
import appeng.tile.inventory.AppEngInternalInventory;

/**
 * 样板名称设置容器（Ctrl+中键点击样板槽位时打开，用于自定义物品名称）。
 * 参照 {@link ContainerPatternValueAmount} 的结构。
 */
public class ContainerPatternValueName extends AEBaseContainer {

    private final Slot patternValue;

    /** 要修改的槽位索引，通过 @GuiSync 同步到客户端 */
    @GuiSync(11)
    public int valueIndex;

    @Reflected
    public ContainerPatternValueName(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
        this.patternValue = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 34, 53);
        this.addSlotToContainer(this.patternValue);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    public Slot getPatternValue() {
        return this.patternValue;
    }

    public int getValueIndex() {
        return this.valueIndex;
    }

    public void setValueIndex(int valueIndex) {
        this.valueIndex = valueIndex;
    }
}

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
import net.minecraft.client.renderer.GlStateManager;

import appeng.api.storage.data.IAEStack;
import appeng.items.contents.PinList;

/**
 * Pins 区域使用的虚拟槽位。
 * <p>
 * 从 {@link PinList} 中按索引获取被钉选的栈。
 * Pins 槽位始终显示数量，且标记有特殊背景。
 */
public class VirtualMEPinSlot extends VirtualMESlot {

    private final PinList pinList;
    private final boolean isCraftingPin;

    /**
     * @param id        槽位 ID
     * @param x         屏幕 X 坐标
     * @param y         屏幕 Y 坐标
     * @param pinList   Pins 数据来源
     * @param slotIndex 在 PinList 中的索引
     */
    public VirtualMEPinSlot(int id, int x, int y, PinList pinList, int slotIndex) {
        super(id, x, y, slotIndex);
        this.pinList = pinList;
        this.isCraftingPin = slotIndex < PinList.PLAYER_OFFSET;
        this.showAmountAlways = true;
        this.showCraftableText = true;
    }

    @Override
    @Nullable
    public IAEStack<?> getAEStack() {
        return this.pinList.getPin(this.slotIndex);
    }

    /**
     * @return 是否为合成 Pin（false = 玩家手动 Pin）
     */
    public boolean isCraftingPin() {
        return this.isCraftingPin;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        IAEStack<?> stack = this.getAEStack();
        if (stack == null) {
            return;
        }

        // 绘制特殊背景色以区分 Pin 区域
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();

        if (this.isCraftingPin) {
            // 合成 Pin：淡蓝色背景
            drawRect(this.xPos(), this.yPos(), this.xPos() + 16, this.yPos() + 16, 0x1800AAFF);
        } else {
            // 玩家 Pin：淡绿色背景
            drawRect(this.xPos(), this.yPos(), this.xPos() + 16, this.yPos() + 16, 0x1800FF00);
        }

        GlStateManager.enableDepth();
        GlStateManager.enableLighting();

        // 调用父类绘制图标和数量
        this.drawStackIcon(mc, stack, this.xPos(), this.yPos());
        this.drawStackOverlay(mc, stack, this.xPos(), this.yPos());
    }
}

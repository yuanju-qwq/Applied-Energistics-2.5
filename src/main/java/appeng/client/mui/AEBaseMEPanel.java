/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
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

package appeng.client.mui;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlot;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.me.SlotME;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;

/**
 * ME 终端专用的 MUI 基础面板。
 * <p>
 * 在 {@link AEBasePanel} 基础上，增加了 ME 终端特有的功能：
 * <ul>
 *   <li>SlotME 的精确数量 tooltip（包括库存数量、可请求数量、可合成标记）</li>
 *   <li>VirtualMESlot 的富 tooltip（AE 栈的完整信息）</li>
 *   <li>AppEngSlot 的数量显示 tooltip</li>
 * </ul>
 * <p>
 * 对应旧 GUI 体系中已移除的 AEBaseMEGui（已删除）。
 */
@SideOnly(Side.CLIENT)
public abstract class AEBaseMEPanel extends AEBasePanel {

    public AEBaseMEPanel(final Container container) {
        super(container);
    }

    public AEBaseMEPanel(final Container container, int xSize, int ySize) {
        super(container, xSize, ySize);
    }

    // ========== ME 终端增强 Tooltip ==========

    /**
     * 覆写 MC 的物品悬停 tooltip，为 SlotME 和 AppEngSlot 追加精确数量信息。
     */
    @Override
    protected void renderToolTip(final ItemStack stack, final int x, final int y) {
        final Slot s = this.getSlot(x, y);

        final int bigNumber = AEConfig.instance().useTerminalUseLargeFont() ? 999 : 9999;
        final List<String> currentToolTip = this.getItemToolTip(stack);

        if (s instanceof SlotME && !stack.isEmpty()) {

            IAEItemStack myStack = null;

            try {
                final SlotME theSlotField = (SlotME) s;
                myStack = theSlotField.getAEStack();
            } catch (final Throwable ignore) {
            }

            if (myStack != null) {
                // 库存数量
                if (myStack.getStackSize() > 1) {
                    final String local = ButtonToolTips.ItemsStored.getLocal();
                    final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                            .format(myStack.getStackSize());
                    final String format = String.format(local, formattedAmount);

                    currentToolTip.add(TextFormatting.GRAY + format);
                }

                // 可请求数量
                if (myStack.getCountRequestable() > 0) {
                    final String local = ButtonToolTips.ItemsRequestable.getLocal();
                    final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                            .format(myStack.getCountRequestable());
                    final String format = String.format(local, formattedAmount);

                    currentToolTip.add(format);
                }

                // 可合成标记
                if (myStack.isCraftable() && AEConfig.instance().isShowCraftableTooltip()) {
                    final String local = ButtonToolTips.ItemsCraftable.getLocal();
                    currentToolTip.add(TextFormatting.GRAY + local);
                }

                this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);

                return;
            } else if (stack.getCount() > bigNumber) {
                final String local = ButtonToolTips.ItemsStored.getLocal();
                final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(stack.getCount());
                final String format = String.format(local, formattedAmount);

                currentToolTip.add(TextFormatting.GRAY + format);

                this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);

                return;
            }
        } else if (s instanceof AppEngSlot) {
            // 非玩家背包的 AppEngSlot：显示精确数量
            if (!(s instanceof SlotPlayerInv) && !(s instanceof SlotPlayerHotBar)) {
                if (!s.getStack().isEmpty()) {
                    final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                            .format(s.getStack().getCount());
                    currentToolTip.add(TextFormatting.GRAY + formattedAmount);
                    this.drawHoveringText(currentToolTip, x, y, this.fontRenderer);
                    return;
                }
            }
        }

        super.renderToolTip(stack, x, y);
    }

    // ========== VirtualMESlot Tooltip ==========

    /**
     * 覆写 tooltip 绘制，为 VirtualMESlot 提供富信息 tooltip。
     * <p>
     * 包含：物品原始 tooltip + 库存数量 + 可请求数量 + 可合成标记 + 子类追加信息。
     */
    @Override
    public void drawTooltip(ITooltip tooltip, int mouseX, int mouseY) {
        if (tooltip instanceof VirtualMESlot virtualSlot && tooltip.isVisible()) {
            final int tx = tooltip.xPos();
            int ty = tooltip.yPos();

            if (tx < mouseX && tx + tooltip.getWidth() > mouseX
                    && ty < mouseY && ty + tooltip.getHeight() > mouseY) {

                IAEStack<?> aeStack = virtualSlot.getAEStack();
                if (aeStack != null) {
                    // 获取物品的原始 tooltip
                    ItemStack displayStack = aeStack.asItemStackRepresentation();
                    if (!displayStack.isEmpty()) {
                        final List<String> lines = this.getItemToolTip(displayStack);

                        // 添加大数字格式化的精确数量
                        if (aeStack.getStackSize() > 1) {
                            final String local = ButtonToolTips.ItemsStored.getLocal();
                            final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                                    .format(aeStack.getStackSize());
                            lines.add(TextFormatting.GRAY + String.format(local, formattedAmount));
                        }

                        // 添加可请求数量
                        if (aeStack.getCountRequestable() > 0) {
                            final String local = ButtonToolTips.ItemsRequestable.getLocal();
                            final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                                    .format(aeStack.getCountRequestable());
                            lines.add(String.format(local, formattedAmount));
                        }

                        // 添加可合成标记
                        if (aeStack.isCraftable() && AEConfig.instance().isShowCraftableTooltip()) {
                            lines.add(TextFormatting.GRAY + ButtonToolTips.ItemsCraftable.getLocal());
                        }

                        // 调用子类追加 tooltip
                        virtualSlot.addTooltip(lines);

                        if (ty < 15) {
                            ty = 15;
                        }
                        this.drawHoveringText(lines, mouseX, mouseY, this.fontRenderer);
                        return;
                    }
                }
            }
        }

        super.drawTooltip(tooltip, mouseX, mouseY);
    }
}

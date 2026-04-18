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

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;
import appeng.util.ISlimReadableNumberConverter;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.ReadableNumberConverter;

/**
 * ME 终端的虚拟槽位抽象基类。
 * <p>
 * 用于在终端 GUI 中显示 ME 网络中的栈（物品、流体等），不像 Minecraft 的 {@link net.minecraft.inventory.Slot}
 * 那样直接关联物品栏。渲染和交互完全由客户端 GUI 控制。
 */
public abstract class VirtualMESlot extends GuiCustomSlot {

    private static final ISlimReadableNumberConverter SLIM_CONVERTER = ReadableNumberConverter.INSTANCE;
    private static final IWideReadableNumberConverter WIDE_CONVERTER = ReadableNumberConverter.INSTANCE;

    protected final int slotIndex;

    protected boolean showAmount = true;
    protected boolean showAmountAlways = false;
    protected boolean showCraftableText = false;
    protected boolean showCraftableIcon = false;

    public VirtualMESlot(int id, int x, int y, int slotIndex) {
        super(id, x, y);
        this.slotIndex = slotIndex;
    }

    /**
     * @return 此槽位当前显示的 AE 栈，可能为 null
     */
    @Nullable
    public abstract IAEStack<?> getAEStack();

    /**
     * @return 此槽位在 Repo 中的索引
     */
    public int getSlotIndex() {
        return this.slotIndex;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        IAEStack<?> stack = this.getAEStack();
        if (stack == null) {
            return;
        }

        // 渲染物品/流体图标
        this.drawStackIcon(mc, stack, this.xPos(), this.yPos());

        // 渲染数量叠加层
        this.drawStackOverlay(mc, stack, this.xPos(), this.yPos());
    }

    /**
     * 渲染栈的图标（物品的 16x16 图标）。
     */
    protected void drawStackIcon(Minecraft mc, IAEStack<?> stack, int x, int y) {
        ItemStack displayStack = stack.asItemStackRepresentation();
        if (!displayStack.isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.enableDepth();
            RenderHelper.enableGUIStandardItemLighting();

            mc.getRenderItem().zLevel = 100.0F;
            mc.getRenderItem().renderItemAndEffectIntoGUI(displayStack, x, y);
            mc.getRenderItem().zLevel = 0.0F;

            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }

    /**
     * 渲染栈的数量叠加层（数字文本或合成标记）。
     */
    protected void drawStackOverlay(Minecraft mc, IAEStack<?> stack, int x, int y) {
        FontRenderer fontRenderer = mc.fontRenderer;

        final float scaleFactor = AEConfig.instance().useTerminalUseLargeFont() ? 0.85f : 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = AEConfig.instance().useTerminalUseLargeFont() ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        if ((stack.getStackSize() == 0 || GuiScreen.isAltKeyDown()) && stack.isCraftable()
                && this.showCraftableText) {
            final String craftLabelText = AEConfig.instance().useTerminalUseLargeFont()
                    ? GuiText.LargeFontCraft.getLocal()
                    : GuiText.SmallFontCraft.getLocal();

            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
            final int X = (int) (((float) x + offset + 16.0f
                    - fontRenderer.getStringWidth(craftLabelText) * scaleFactor) * inverseScaleFactor);
            final int Y = (int) (((float) y + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
            fontRenderer.drawStringWithShadow(craftLabelText, X, Y, 16777215);
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
        } else if (stack.getStackSize() > 0 && this.showAmount) {
            final String stackSize = this.getToBeRenderedStackSize(stack.getStackSize());

            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.disableBlend();
            GlStateManager.pushMatrix();
            GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
            final int X = (int) (((float) x + offset + 16.0f
                    - fontRenderer.getStringWidth(stackSize) * scaleFactor) * inverseScaleFactor);
            final int Y = (int) (((float) y + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
            fontRenderer.drawStringWithShadow(stackSize, X, Y, 16777215);
            GlStateManager.popMatrix();
            GlStateManager.enableLighting();
            GlStateManager.enableDepth();
            GlStateManager.enableBlend();
        }

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    private String getToBeRenderedStackSize(final long originalSize) {
        if (AEConfig.instance().useTerminalUseLargeFont()) {
            return SLIM_CONVERTER.toSlimReadableForm(originalSize);
        } else {
            return WIDE_CONVERTER.toWideReadableForm(originalSize);
        }
    }

    @Override
    @Nullable
    public String getMessage() {
        return null;
    }

    @Override
    @Nullable
    public Object getIngredient() {
        IAEStack<?> stack = this.getAEStack();
        if (stack instanceof IAEItemStack) {
            return ((IAEItemStack) stack).createItemStack();
        } else if (stack instanceof IAEFluidStack) {
            return ((IAEFluidStack) stack).getFluidStack();
        }
        return null;
    }

    /**
     * 向 tooltip 列表中添加额外信息（如大数字格式化的精确数量）。子类可覆盖。
     */
    public void addTooltip(List<String> lines) {
        IAEStack<?> stack = this.getAEStack();
        if (stack != null && stack.getStackSize() > 999) {
            final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(stack.getStackSize());
            lines.add("\u00a77" + formattedAmount);
        }
    }

    // region Getters and Setters

    public boolean isShowAmount() {
        return this.showAmount;
    }

    public void setShowAmount(boolean showAmount) {
        this.showAmount = showAmount;
    }

    public boolean isShowAmountAlways() {
        return this.showAmountAlways;
    }

    public void setShowAmountAlways(boolean showAmountAlways) {
        this.showAmountAlways = showAmountAlways;
    }

    public boolean isShowCraftableText() {
        return this.showCraftableText;
    }

    public void setShowCraftableText(boolean showCraftableText) {
        this.showCraftableText = showCraftableText;
    }

    public boolean isShowCraftableIcon() {
        return this.showCraftableIcon;
    }

    public void setShowCraftableIcon(boolean showCraftableIcon) {
        this.showCraftableIcon = showCraftableIcon;
    }

    // endregion
}

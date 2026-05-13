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
import net.minecraft.item.ItemStack;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.me.ItemRepo.RepoEntry;
import appeng.client.render.stack.AEStackTypeRendererRegistry;
import appeng.core.AEConfig;
import appeng.core.localization.GuiText;

/**
 * Abstract base class for virtual ME terminal slots.
 * <p>
 * Used to display ME network stacks (items, fluids, etc.) in the terminal GUI.
 * Unlike Minecraft's {@link net.minecraft.inventory.Slot}, these are not backed
 * by an inventory. Rendering and interaction are fully controlled by the client GUI.
 * <p>
 * The rendering core uses the AEKey system: subclasses provide data via either
 * {@link #getRepoEntry()} (preferred) or {@link #getAEStack()} (legacy bridge).
 * The {@link #drawContent} method extracts {@link AEKey}, amount, and craftable flag
 * from the data source and delegates to AEKey-based rendering helpers.
 */
public abstract class VirtualMESlot extends GuiCustomSlot {

    protected final int slotIndex;

    protected boolean showAmount = true;
    protected boolean showAmountAlways = false;
    protected boolean showCraftableText = false;
    protected boolean showCraftableIcon = false;

    public VirtualMESlot(int id, int x, int y, int slotIndex) {
        super(id, x, y);
        this.slotIndex = slotIndex;
    }

    // ========== Data access (AEKey-based primary, IAEStack legacy bridge) ==========

    /**
     * Returns the current display data as a {@link RepoEntry}.
     * <p>
     * Subclasses backed by {@link appeng.client.me.ItemRepo} should override this
     * to return the entry directly. The default implementation bridges from
     * {@link #getAEStack()} for backward compatibility.
     *
     * @return the entry, or null if this slot has nothing to display
     */
    @Nullable
    public RepoEntry getRepoEntry() {
        IAEStack<?> stack = this.getAEStack();
        return RepoEntry.fromIAEStack(stack);
    }

    /**
     * @deprecated Use {@link #getRepoEntry()} instead.
     * @return the current AE stack for this slot, may be null
     */
    @Deprecated
    @Nullable
    public abstract IAEStack<?> getAEStack();

    /**
     * @return the slot index (in Repo or inventory)
     */
    public int getSlotIndex() {
        return this.slotIndex;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    // ========== AEKey-based rendering core ==========

    @Override
    public void drawContent(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        RepoEntry entry = this.getRepoEntry();
        if (entry == null) {
            return;
        }

        // Render stack icon (16x16)
        this.drawStackIcon(mc, entry.what(), this.xPos(), this.yPos());

        // Render amount/craftable overlay
        this.drawStackOverlay(mc, entry.what(), entry.amount(), entry.craftable(), this.xPos(), this.yPos());
    }

    /**
     * Render the stack icon using AEKey.
     * Uses {@link AEKey#asItemStackRepresentation()} to obtain a renderable ItemStack.
     */
    protected void drawStackIcon(Minecraft mc, AEKey what, int x, int y) {
        ItemStack displayStack = what.asItemStackRepresentation();
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
     * Render the amount overlay (numeric text or craft label) using AEKey data.
     */
    protected void drawStackOverlay(Minecraft mc, AEKey what, long amount, boolean craftable, int x, int y) {
        FontRenderer fontRenderer = mc.fontRenderer;

        final float scaleFactor = AEConfig.instance().useTerminalUseLargeFont() ? 0.85f : 0.5f;
        final float inverseScaleFactor = 1.0f / scaleFactor;
        final int offset = AEConfig.instance().useTerminalUseLargeFont() ? 0 : -1;

        final boolean unicodeFlag = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(false);

        if ((amount == 0 || GuiScreen.isAltKeyDown()) && craftable && this.showCraftableText) {
            // Craftable label
            final String craftLabelText = AEConfig.instance().useTerminalUseLargeFont()
                    ? GuiText.LargeFontCraft.getLocal()
                    : GuiText.SmallFontCraft.getLocal();

            renderOverlayText(fontRenderer, craftLabelText, x, y, scaleFactor, inverseScaleFactor, offset);
        } else if (amount > 0 && this.showAmount) {
            // Amount text: delegate to type-aware formatting via the legacy renderer
            final String stackSize = this.formatAmount(what, amount);

            renderOverlayText(fontRenderer, stackSize, x, y, scaleFactor, inverseScaleFactor, offset);
        }

        fontRenderer.setUnicodeFlag(unicodeFlag);
    }

    /**
     * Format the amount for overlay display, using {@link AEKeyType#formatAmount}.
     * <p>
     * Delegates to the AEKey type system for type-specific formatting
     * (items use SI suffixes, fluids use mB→Bucket conversion, etc.).
     */
    protected String formatAmount(AEKey what, long amount) {
        boolean largeFont = AEConfig.instance().useTerminalUseLargeFont();
        return what.getType().formatAmount(amount,
                largeFont ? AmountFormat.PREVIEW_LARGE_FONT : AmountFormat.PREVIEW_REGULAR);
    }

    /**
     * Common helper to render overlay text at the bottom-right of a slot.
     */
    private static void renderOverlayText(FontRenderer fontRenderer, String text, int x, int y,
            float scaleFactor, float inverseScaleFactor, int offset) {
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.disableBlend();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scaleFactor, scaleFactor, scaleFactor);
        final int X = (int) (((float) x + offset + 16.0f
                - fontRenderer.getStringWidth(text) * scaleFactor) * inverseScaleFactor);
        final int Y = (int) (((float) y + offset + 16.0f - 7.0f * scaleFactor) * inverseScaleFactor);
        fontRenderer.drawStringWithShadow(text, X, Y, 16777215);
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableBlend();
    }

    // ========== Legacy rendering bridge (for subclasses that override old methods) ==========

    /**
     * @deprecated Use {@link #drawStackIcon(Minecraft, AEKey, int, int)} instead.
     *             Legacy bridge: renders using IAEStack.
     */
    @Deprecated
    protected void drawStackIcon(Minecraft mc, IAEStack<?> stack, int x, int y) {
        AEKey key = stack.toAEKey();
        if (key != null) {
            drawStackIcon(mc, key, x, y);
        }
    }

    /**
     * @deprecated Use {@link #drawStackOverlay(Minecraft, AEKey, long, boolean, int, int)} instead.
     *             Legacy bridge: renders using IAEStack.
     */
    @Deprecated
    protected void drawStackOverlay(Minecraft mc, IAEStack<?> stack, int x, int y) {
        AEKey key = stack.toAEKey();
        if (key != null) {
            drawStackOverlay(mc, key, stack.getStackSize(), stack.isCraftable(), x, y);
        }
    }

    // ========== Tooltip ==========

    @Override
    @Nullable
    public String getMessage() {
        return null;
    }

    @Override
    @Nullable
    public Object getIngredient() {
        // Bridge to legacy renderer for JEI ingredient extraction
        IAEStack<?> stack = this.getAEStack();
        if (stack != null) {
            return AEStackTypeRendererRegistry.getRenderer(stack).getIngredient(stack);
        }
        return null;
    }

    /**
     * Append extra information to the tooltip lines.
     * <p>
     * The default implementation appends a formatted exact amount for large numbers.
     * Subclasses may override to add additional info.
     */
    public void addTooltip(List<String> lines) {
        RepoEntry entry = this.getRepoEntry();
        if (entry != null && entry.amount() > 999) {
            final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(entry.amount());
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

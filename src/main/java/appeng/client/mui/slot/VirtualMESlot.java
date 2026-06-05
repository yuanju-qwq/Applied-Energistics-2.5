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

package appeng.client.mui.slot;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import appeng.api.storage.data.IAEStack;
import appeng.client.me.ItemRepo.RepoEntry;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUICustomSlot;
import appeng.client.mui.widgets.MUIStackRenderer;

/**
 * Abstract base class for virtual ME terminal slots.
 * <p>
 * Used to display ME network stacks (items, fluids, etc.) in the terminal GUI.
 * Unlike Minecraft's {@link net.minecraft.inventory.Slot}, these are not backed
 * by an inventory. Rendering and interaction are fully controlled by the client GUI.
 * <p>
 * The rendering core delegates to {@link MUIStackRenderer} for type-aware icon and
 * overlay rendering (items use Forge item renderer, fluids use texture sprite).
 */
public abstract class VirtualMESlot extends MUICustomSlot {

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
     *
     * @return the entry, or null if this slot has nothing to display
     */
    @Nullable
    public abstract RepoEntry getRepoEntry();

    /**
     * @deprecated Use {@link #getRepoEntry()} instead.
     * @return the current AE stack for this slot, may be null
     */
    @Deprecated
    @Nullable
    public IAEStack<?> getAEStack() {
        RepoEntry entry = this.getRepoEntry();
        return entry != null ? entry.toIAEStack() : null;
    }

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
    public void drawContent(AEBasePanel panel, Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        RepoEntry entry = this.getRepoEntry();
        if (entry == null) {
            return;
        }

        MUIStackRenderer.renderEntryIcon(mc, entry, this.xPos(), this.yPos());
        MUIStackRenderer.renderEntryOverlay(mc, entry, this.xPos(), this.yPos(),
                this.showAmount, this.showCraftableText);
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
        RepoEntry entry = this.getRepoEntry();
        if (entry != null) {
            return entry.what().asItemStackRepresentation();
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

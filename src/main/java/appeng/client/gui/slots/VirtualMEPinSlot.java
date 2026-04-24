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
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import appeng.api.storage.data.IAEStack;
import appeng.core.AppEng;
import appeng.items.contents.PinList;

/**
 * Virtual slot for the terminal Pin (pin/favorite) area.
 * <p>
 * Displays pinned items from {@link PinList} with a tinted background and
 * a semi-transparent pin icon overlay.
 */
public class VirtualMEPinSlot extends VirtualMESlot {

    // Pin icon location in states.png: row 5 (0-indexed), column 14
    private static final int PIN_ICON_INDEX = 5 * 16 + 14;
    private static final int UV_Y = PIN_ICON_INDEX / 16;
    private static final int UV_X = PIN_ICON_INDEX - UV_Y * 16;
    private static final int ICON_SIZE = 16;
    private static final int SLOT_SIZE = 18;
    private static final float PIN_ICON_OPACITY = 0.4f;
    private static final ResourceLocation TEXTURE = new ResourceLocation(AppEng.MOD_ID, "textures/guis/states.png");

    // Background colors (ARGB)
    private static final int CRAFTING_PIN_BG = 0x30FF6600;
    private static final int PLAYER_PIN_BG = 0x300066FF;

    private final PinList pinList;
    private final boolean isCraftingSlot;

    public VirtualMEPinSlot(int x, int y, PinList pinList, int slotIndex, boolean isCraftingSlot) {
        super(-1, x, y, slotIndex);
        this.pinList = pinList;
        this.isCraftingSlot = isCraftingSlot;
        this.showAmountAlways = true;
        this.showCraftableText = true;
        this.showCraftableIcon = true;
    }

    public boolean isCraftingSlot() {
        return this.isCraftingSlot;
    }

    @Override
    @Nullable
    public IAEStack<?> getAEStack() {
        return this.pinList.getPin(this.slotIndex);
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void slotClicked(final ItemStack clickStack, final int mouseButton) {
        // Pin slots do not handle normal clicks; pin/unpin is handled by the panel
    }

    /**
     * Draw tinted backgrounds and pin icon overlays for all given pin slots.
     */
    public static void drawSlotsBackground(VirtualMEPinSlot[] slots, Minecraft mc, float z) {
        if (slots == null || slots.length == 0) {
            return;
        }

        // Draw colored backgrounds
        for (VirtualMEPinSlot slot : slots) {
            int color = slot.isCraftingSlot() ? CRAFTING_PIN_BG : PLAYER_PIN_BG;
            if (color != 0) {
                Gui.drawRect(
                        slot.xPos() - 1,
                        slot.yPos() - 1,
                        slot.xPos() - 1 + SLOT_SIZE,
                        slot.yPos() - 1 + SLOT_SIZE,
                        color);
            }
        }

        // Draw semi-transparent pin icons
        GlStateManager.pushAttrib();
        GlStateManager.enableBlend();
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(TEXTURE);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        for (VirtualMEPinSlot slot : slots) {
            final double x = slot.xPos();
            final double y = slot.yPos();
            final double uvX = UV_X * 16;
            final double uvY = UV_Y * 16;
            final double f = 1.0 / 256.0;
            int alpha = (int) (PIN_ICON_OPACITY * 255);

            buffer.pos(x, y + ICON_SIZE, z).tex((uvX) * f, (uvY + ICON_SIZE) * f)
                    .color(255, 255, 255, alpha).endVertex();
            buffer.pos(x + ICON_SIZE, y + ICON_SIZE, z).tex((uvX + ICON_SIZE) * f, (uvY + ICON_SIZE) * f)
                    .color(255, 255, 255, alpha).endVertex();
            buffer.pos(x + ICON_SIZE, y, z).tex((uvX + ICON_SIZE) * f, (uvY) * f)
                    .color(255, 255, 255, alpha).endVertex();
            buffer.pos(x, y, z).tex((uvX) * f, (uvY) * f)
                    .color(255, 255, 255, alpha).endVertex();
        }

        tessellator.draw();
        GlStateManager.popAttrib();
    }
}

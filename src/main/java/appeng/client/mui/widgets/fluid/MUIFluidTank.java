/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.client.mui.widgets.fluid;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.IMUITooltip;
import appeng.client.mui.widgets.MUICustomSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.InventoryAction;

/**
 * MUI namespace fluid tank slot rendering widget.
 * <p>
 * Equivalent to the legacy {@code appeng.client.gui.widgets.GuiFluidTank}, but
 * migrated to the {@link MUICustomSlot} + {@link IMUITooltip} system and renamed
 * to follow the {@code MUI*} naming convention used by all MUI widgets.
 * <p>
 * The widget no longer inherits {@code net.minecraft.client.gui.Gui}; the
 * required drawing methods (e.g. {@code drawTexturedModalRect}) are obtained
 * indirectly through {@link AEBasePanel}, which extends
 * {@code GuiContainer} → {@code Gui}.
 */
@SideOnly(Side.CLIENT)
public class MUIFluidTank extends MUICustomSlot {
    private final IAEFluidTank tank;
    private final int slot;
    private final int width;
    private final int height;
    private boolean darkened = false;

    public MUIFluidTank(IAEFluidTank tank, int slot, int id, int x, int y, int w, int h) {
        super(id, x, y);
        this.tank = tank;
        this.slot = slot;
        this.width = w;
        this.height = h;
    }

    public MUIFluidTank(IAEFluidTank tank, int slot, int id, int x, int y, int w, int h, boolean darkened) {
        super(id, x, y);
        this.tank = tank;
        this.slot = slot;
        this.width = w;
        this.height = h;
        this.darkened = darkened;
    }

    @Override
    public void drawContent(AEBasePanel panel, Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        final IAEFluidStack fs = this.getFluidStack();
        if (fs != null) {
            GlStateManager.disableBlend();
            GlStateManager.disableLighting();

            final IAEFluidStack fluid = this.tank.getFluidInSlot(this.slot);
            if (fluid != null && fluid.getStackSize() > 0) {
                mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

                float red = (fluid.getFluid().getColor() >> 16 & 255) / 255.0F;
                float green = (fluid.getFluid().getColor() >> 8 & 255) / 255.0F;
                float blue = (fluid.getFluid().getColor() & 255) / 255.0F;
                if (darkened) {
                    red = red * 0.4F;
                    green = green * 0.4F;
                    blue = blue * 0.4F;
                }
                GlStateManager.color(red, green, blue);

                TextureAtlasSprite sprite = mc.getTextureMapBlocks()
                        .getAtlasSprite(fluid.getFluid().getStill().toString());
                int scaledHeight = (int) (this.height
                        * ((float) fluid.getStackSize() / this.tank.getTankProperties()[this.slot].getCapacity()));
                scaledHeight = Math.min(this.height, scaledHeight);

                int iconHeightRemainder = scaledHeight % 16;
                if (iconHeightRemainder > 0) {
                    // Delegates to the panel's GuiScreen-inherited drawTexturedModalRect.
                    panel.drawTexturedModalRect(this.xPos(), this.yPos() + this.getHeight() - iconHeightRemainder,
                            sprite, 16, iconHeightRemainder);
                }
                for (int i = 0; i < scaledHeight / 16; i++) {
                    panel.drawTexturedModalRect(this.xPos(),
                            this.yPos() + this.getHeight() - iconHeightRemainder - (i + 1) * 16, sprite, 16, 16);
                }
            }
        }
    }

    @Override
    public String getMessage() {
        final IAEFluidStack fluid = this.tank.getFluidInSlot(this.slot);
        if (fluid != null && fluid.getStackSize() > 0) {
            String desc = fluid.getFluid().getLocalizedName(fluid.getFluidStack());

            return desc + "\n" + fluid.getStackSize() + "/" + this.tank.getTankProperties()[this.slot].getCapacity()
                    + "mB";
        }
        return null;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    public IAEFluidStack getFluidStack() {
        return this.tank.getFluidInSlot(this.slot);
    }

    @Override
    public void slotClicked(ItemStack clickStack, final int mouseButton) {
        if (getFluidStack() != null) {
            NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.FILL_ITEM, slot, id));
        } else {
            NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.EMPTY_ITEM, slot, id));
        }
    }

    @Override
    public Object getIngredient() {
        return this.getFluidStack() == null ? null : this.getFluidStack().getFluidStack();
    }

}

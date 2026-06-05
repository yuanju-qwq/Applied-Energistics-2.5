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

package appeng.client.mui.screen;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.container.implementations.ContainerCraftingTerm;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;

/**
 * MUI version of the Crafting Terminal panel
 * <p>
 * Extends {@link MUIMEMonitorablePanel}, 3x3 crafting grid + clear button.
 * The clear button is implemented as a {@link MUIButtonWidget} with an
 * onClick callback that asks the server to MOVE_REGION the crafting matrix slot.
 */
@SideOnly(Side.CLIENT)
public class MUICraftingTermPanel extends MUIMEMonitorablePanel {

    private MUIButtonWidget clearBtn;

    public MUICraftingTermPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerCraftingTerm(inventoryPlayer, te));
        this.setReservedSpace(73);
    }

    @Override
    public void initGui() {
        super.initGui();

        // Clear button (3x3 crafting grid stash)
        this.clearBtn = new MUIButtonWidget(this.guiLeft + 92, this.guiTop + this.ySize - 156,
                Settings.ACTIONS, ActionItems.STASH);
        this.clearBtn.setHalfSize(true);
        this.clearBtn.setOnClick(btn -> sendClearCraftingGridPacket());
        this.addWidget(this.clearBtn);
    }

    /**
     * Find the crafting matrix slot and ask the server to clear the entire 3x3 grid.
     * Triggered by the MUI clear button's onClick callback.
     */
    private void sendClearCraftingGridPacket() {
        Slot s = null;
        final Container c = this.inventorySlots;
        for (final Object j : c.inventorySlots) {
            if (j instanceof SlotCraftingMatrix) {
                s = (Slot) j;
            }
        }

        if (s != null) {
            final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.MOVE_REGION, s.slotNumber, 0);
            NetworkHandler.instance().sendToServer(p);
        }
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.CraftingTerminal.getLocal(), 8,
                this.ySize - 96 + 1 - this.getReservedSpace(), AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected String getBackground() {
        return "guis/crafting.png";
    }
}

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

package appeng.client.mui.screen;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerWirelessCraftingTerminal;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;

/**
 * MUI 版无线合成终端面板。
 * <p>
 * 继承 {@link MUIMEMonitorablePanel}，添加 3×3 合成网格 + 清除按钮 +
 * 无线升级图标 + 终端模式切换按钮。
 */
@SideOnly(Side.CLIENT)
public class MUIWirelessCraftingTermPanelImpl extends MUIMEMonitorablePanel implements MUIWirelessTermPanel {

    private final WirelessTerminalHelper wirelessHelper = new WirelessTerminalHelper();
    private GuiImgButton clearBtn;

    public MUIWirelessCraftingTermPanelImpl(final InventoryPlayer inventoryPlayer,
            final WirelessTerminalGuiObject te) {
        super(inventoryPlayer, te, new ContainerWirelessCraftingTerminal(inventoryPlayer, te));
        this.setReservedSpace(73);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.add(this.clearBtn = new GuiImgButton(this.guiLeft + 92, this.guiTop + this.ySize - 156,
                Settings.ACTIONS, ActionItems.STASH));
        this.clearBtn.setHalfSize(true);
        this.wirelessHelper.initButtons(
                ((AEBaseContainer) this.inventorySlots).getPlayerInv(),
                this.guiLeft, this.guiTop, this.buttonList, 200, this.itemRender);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (this.wirelessHelper.handleButtonClick(btn)) {
            return;
        }
        super.actionPerformed(btn);

        if (this.clearBtn == btn) {
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
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.CraftingTerminal.getLocal(), 8,
                this.ySize - 96 + 1 - this.getReservedSpace(), 4210752);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.wirelessHelper.drawWirelessIcon(offsetX, offsetY, 198, 127);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }

    @Override
    protected String getBackground() {
        return "guis/crafting.png";
    }
}

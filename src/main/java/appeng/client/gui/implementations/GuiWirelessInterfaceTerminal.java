package appeng.client.gui.implementations;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.helpers.WirelessTerminalGuiObject;

public class GuiWirelessInterfaceTerminal extends GuiInterfaceTerminal {

    private UniversalTerminalButtons universalButtons;

    public GuiWirelessInterfaceTerminal(InventoryPlayer inventoryPlayer, final WirelessTerminalGuiObject te) {
        super(inventoryPlayer, te);
    }

    @Override
    public void initGui() {
        super.initGui();
        this.universalButtons = new UniversalTerminalButtons(
                ((appeng.container.AEBaseContainer) this.inventorySlots).getPlayerInv());
        this.universalButtons.initButtons(this.guiLeft, this.guiTop, this.buttonList, 200, this.itemRender);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        if (this.universalButtons != null && this.universalButtons.handleButtonClick(btn)) {
            return;
        }
        super.actionPerformed(btn);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/wirelessupgrades.png");
        Gui.drawModalRectWithCustomSizedTexture(offsetX + 189, offsetY + 165, 0, 0, 32, 32, 32, 32);
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
    }
}

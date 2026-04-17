package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerDualItemInterface;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.IInterfaceHost;

/**
 * 二合一接口的物品面 GUI。
 * 继承原版接口 GUI，添加切换到流体面的按钮。
 */
public class GuiDualItemInterface extends GuiInterface {

    private GuiTabButton switchToFluid;

    public GuiDualItemInterface(final InventoryPlayer inventoryPlayer, final IInterfaceHost te) {
        super(inventoryPlayer, te);
        ContainerDualItemInterface container = new ContainerDualItemInterface(inventoryPlayer, te);
        this.inventorySlots = container;
    }

    @Override
    protected void addButtons() {
        super.addButtons();
        // 切换到流体面的按钮
        ItemStack fluidIcon = AEApi.instance().definitions().blocks().fluidIface().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        this.switchToFluid = new GuiTabButton(this.guiLeft + 133, this.guiTop, fluidIcon,
                fluidIcon.getDisplayName(), this.itemRender);
        this.buttonList.add(this.switchToFluid);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.switchToFluid) {
            // 切换到流体面
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_DUAL_FLUID_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

package appeng.fluids.client.gui;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.container.ContainerDualFluidInterface;
import appeng.fluids.helper.IFluidInterfaceHost;

/**
 * 二合一接口的流体面 GUI。
 * 继承原版流体接口 GUI，添加切换到物品面的按钮。
 */
public class GuiDualFluidInterface extends GuiFluidInterface {

    private GuiTabButton switchToItem;

    public GuiDualFluidInterface(final InventoryPlayer inventoryPlayer, final IFluidInterfaceHost te) {
        super(inventoryPlayer, te);
        ContainerDualFluidInterface container = new ContainerDualFluidInterface(inventoryPlayer, te);
        this.inventorySlots = container;
    }

    @Override
    public void initGui() {
        super.initGui();
        // 切换到物品面的按钮
        ItemStack itemIcon = AEApi.instance().definitions().blocks().iface().maybeStack(1)
                .orElse(ItemStack.EMPTY);
        this.switchToItem = new GuiTabButton(this.guiLeft + 133, this.guiTop, itemIcon,
                itemIcon.getDisplayName(), this.itemRender);
        this.buttonList.add(this.switchToItem);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (btn == this.switchToItem) {
            // 切换到物品面
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(GuiBridge.GUI_DUAL_ITEM_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

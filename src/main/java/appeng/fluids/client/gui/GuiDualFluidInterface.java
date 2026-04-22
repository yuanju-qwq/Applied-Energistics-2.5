package appeng.fluids.client.gui;

// ========================================================================
// [MUI Migration] 此旧 GUI 类已被 MUI 面板完全替代，运行时不再被实例化。
// 全部 GUI 创建已通过 AEMUIRegistration 中注册的 MUI 工厂完成。
// 如需恢复，取消下方块注释即可。
// ========================================================================
/*


import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.fluids.container.ContainerDualFluidInterface;
import appeng.fluids.helper.IFluidInterfaceHost;

/**
 * 二合一接口的流体面 GUI。
 * 继承原版流体接口 GUI，添加切换到物品面的按钮。
 * @deprecated 使用 {@link appeng.client.mui.screen.MUIMEInterfacePanel} 替代，二合一接口已被取代。
 */
@Deprecated
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
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.DUAL_ITEM_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

*/
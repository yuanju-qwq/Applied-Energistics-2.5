package appeng.client.gui.implementations;

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
import appeng.container.implementations.ContainerDualItemInterface;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.IInterfaceHost;

/**
 * 二合一接口的物品面 GUI。
 * 继承原版接口 GUI，添加切换到流体面的按钮。
 * @deprecated 使用 {@link appeng.client.mui.screen.MUIMEInterfacePanel} 替代，二合一接口已被取代。
 */
@Deprecated
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
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.DUAL_FLUID_INTERFACE));
        } else {
            super.actionPerformed(btn);
        }
    }
}

*/
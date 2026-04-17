package appeng.fluids.container;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.fluids.helper.IFluidInterfaceHost;

/**
 * 二合一接口的流体面 Container。
 * 复用 ContainerFluidInterface 的所有逻辑。
 */
public class ContainerDualFluidInterface extends ContainerFluidInterface {

    public ContainerDualFluidInterface(final InventoryPlayer ip, final IFluidInterfaceHost te) {
        super(ip, te);
    }
}

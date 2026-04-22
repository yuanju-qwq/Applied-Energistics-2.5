package appeng.fluids.container;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.fluids.helper.IFluidInterfaceHost;

/**
 * 二合一接口的流体面 Container。
 * 复用 ContainerFluidInterface 的所有逻辑。
 * @deprecated 使用 {@link appeng.container.implementations.ContainerMEInterface} 替代。
 */
@Deprecated
public class ContainerDualFluidInterface extends ContainerFluidInterface {

    public ContainerDualFluidInterface(final InventoryPlayer ip, final IFluidInterfaceHost te) {
        super(ip, te);
    }
}

package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.helpers.IInterfaceHost;

/**
 * 二合一接口的物品面 Container。
 * 复用 ContainerInterface 的所有逻辑。
 * @deprecated 使用 {@link ContainerPatternProvider} 或 {@link ContainerMEInterface} 替代，二合一接口已被取代。
 */
@Deprecated
public class ContainerDualItemInterface extends ContainerInterface {

    public ContainerDualItemInterface(final InventoryPlayer ip, final IInterfaceHost te) {
        super(ip, te);
    }
}

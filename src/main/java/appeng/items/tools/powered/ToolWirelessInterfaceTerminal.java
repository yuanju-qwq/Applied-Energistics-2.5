package appeng.items.tools.powered;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.IGuiHandler;

import appeng.api.AEApi;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;

public class ToolWirelessInterfaceTerminal extends ToolWirelessTerminal {
    @Override
    public boolean canHandle(ItemStack is) {
        return AEApi.instance().definitions().items().wirelessInterfaceTerminal().isSameAs(is);
    }

    @Override
    public Object getGuiHandler(ItemStack is) {
        return AEGuiKeys.WIRELESS_INTERFACE_TERMINAL;
    }
}

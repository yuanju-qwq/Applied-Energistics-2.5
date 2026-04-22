package appeng.items.tools.powered;

import appeng.api.AEApi;
import appeng.api.definitions.IItemDefinition;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;

public enum Terminal {
    WIRELESS_TERMINAL(AEApi.instance().definitions().items().wirelessTerminal(), AEGuiKeys.WIRELESS_TERM),
    WIRELESS_CRAFTING_TERMINAL(AEApi.instance().definitions().items().wirelessCraftingTerminal(),
            AEGuiKeys.WIRELESS_CRAFTING_TERMINAL),
    WIRELESS_PATTERN_TERMINAL(AEApi.instance().definitions().items().wirelessPatternTerminal(),
            AEGuiKeys.WIRELESS_PATTERN_TERMINAL),
    WIRELESS_FLUID_TERMINAL(AEApi.instance().definitions().items().wirelessFluidTerminal(),
            AEGuiKeys.WIRELESS_FLUID_TERMINAL),
    WIRELESS_INTERFACE_TERMINAL(AEApi.instance().definitions().items().wirelessInterfaceTerminal(),
            AEGuiKeys.WIRELESS_INTERFACE_TERMINAL),
    WIRELESS_DUAL_INTERFACE_TERMINAL(AEApi.instance().definitions().items().wirelessDualInterfaceTerminal(),
            AEGuiKeys.WIRELESS_DUAL_INTERFACE_TERMINAL),
    WIRELESS_UNIVERSAL_TERMINAL(AEApi.instance().definitions().items().wirelessUniversalTerminal(),
            AEGuiKeys.WIRELESS_TERM);

    final GuiBridge bridge;
    final IItemDefinition itemDefinition;

    Terminal(IItemDefinition itemDefinition, GuiBridge guiBridge) {
        this.itemDefinition = itemDefinition;
        this.bridge = guiBridge;
    }

    public GuiBridge getBridge() {
        return bridge;
    }

    public IItemDefinition getItemDefinition() {
        return itemDefinition;
    }
}

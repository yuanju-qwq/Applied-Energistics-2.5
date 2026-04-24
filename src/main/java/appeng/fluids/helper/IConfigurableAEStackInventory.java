package appeng.fluids.helper;

import javax.annotation.Nullable;

import appeng.tile.inventory.IAEStackInventory;

/**
 * Interface for parts/tiles that expose configurable generic stack inventories.
 * Replaces {@link IConfigurableFluidInventory} for config-only inventories
 * where IFluidHandler is not needed.
 */
public interface IConfigurableAEStackInventory {

    /**
     * Gets the generic stack inventory by name.
     *
     * @param name inventory name (e.g. "config")
     * @return the named inventory, or null
     */
    @Nullable
    IAEStackInventory getAEStackInventoryByName(String name);
}

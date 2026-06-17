package appeng.api.networking.crafting;

import appeng.api.networking.IGridNodeService;
import appeng.api.stacks.AEKey;
import appeng.api.storage.data.IAEStack;

public interface ICraftingWatcherHost extends IGridNodeService {

    void updateWatcher(ICraftingWatcher newWatcher);

    void onRequestChange(ICraftingGrid craftingGrid, AEKey what);

    /**
     * @deprecated Use {@link #onRequestChange(ICraftingGrid, AEKey)} instead.
     */
    @Deprecated
    default void onRequestChange(ICraftingGrid craftingGrid, IAEStack<?> what) {
        AEKey key = what != null ? what.toAEKey() : null;
        if (key != null) {
            onRequestChange(craftingGrid, key);
        }
    }
}

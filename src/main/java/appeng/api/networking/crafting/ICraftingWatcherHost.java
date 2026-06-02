package appeng.api.networking.crafting;

import appeng.api.networking.IGridNodeService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;

public interface ICraftingWatcherHost extends IGridNodeService {

    void updateWatcher(ICraftingWatcher newWatcher);

    /**
     * @deprecated Use {@link #onRequestChange(ICraftingGrid, AEKey)} instead.
     */
    @Deprecated
    void onRequestChange(ICraftingGrid craftingGrid, IAEStack<?> what);

    default void onRequestChange(ICraftingGrid craftingGrid, AEKey what) {
        onRequestChange(craftingGrid, new GenericStack(what, 1).toIAEStack());
    }
}
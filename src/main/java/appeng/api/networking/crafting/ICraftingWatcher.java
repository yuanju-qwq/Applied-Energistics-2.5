package appeng.api.networking.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.data.IAEStack;

/**
 * DO NOT IMPLEMENT.
 *
 * Will be injected when adding an {@link ICraftingWatcherHost} to a grid.
 */
public interface ICraftingWatcher {
    /**
     * @deprecated Use {@link #add(AEKey)} instead.
     */
    @Deprecated
    boolean add(IAEStack<?> stack);

    /**
     * @deprecated Use {@link #remove(AEKey)} instead.
     */
    @Deprecated
    boolean remove(IAEStack<?> stack);

    default boolean add(AEKey key) {
        return add(new GenericStack(key, 0).toIAEStack());
    }

    default boolean remove(AEKey key) {
        return remove(new GenericStack(key, 0).toIAEStack());
    }

    void reset();
}
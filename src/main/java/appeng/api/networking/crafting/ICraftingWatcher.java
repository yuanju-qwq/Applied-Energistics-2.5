package appeng.api.networking.crafting;

import appeng.api.stacks.AEKey;
import appeng.api.storage.data.IAEStack;

/**
 * DO NOT IMPLEMENT.
 *
 * Will be injected when adding an {@link ICraftingWatcherHost} to a grid.
 */
public interface ICraftingWatcher {
    boolean add(AEKey key);

    boolean remove(AEKey key);

    /**
     * @deprecated Use {@link #add(AEKey)} instead.
     */
    @Deprecated
    default boolean add(IAEStack<?> stack) {
        AEKey key = stack != null ? stack.toAEKey() : null;
        return key != null && add(key);
    }

    /**
     * @deprecated Use {@link #remove(AEKey)} instead.
     */
    @Deprecated
    default boolean remove(IAEStack<?> stack) {
        AEKey key = stack != null ? stack.toAEKey() : null;
        return key != null && remove(key);
    }

    void reset();
}

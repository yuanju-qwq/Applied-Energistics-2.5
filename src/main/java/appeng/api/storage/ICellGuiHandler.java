
package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.stacks.AEKeyType;

public interface ICellGuiHandler {
    /**
     * Check if this handler can handle the given key type.
     *
     * @param type the key type
     * @return true if this handler can handle the type
     */
    boolean isHandlerFor(AEKeyType type);

    /**
     * Return true to prioritize this handler for the provided {@link ItemStack}.
     * 
     * @param is Cell ItemStack
     * @return True, if specialized else false.
     */
    default boolean isSpecializedFor(ItemStack is) {
        return false;
    }

    /**
     * Open the ME Chest GUI for the given key type.
     */
    void openChestGui(EntityPlayer player, IChestOrDrive chest,
            ICellHandler cellHandler, IMEInventoryHandler inv, ItemStack is, AEKeyType type);

    /**
     * AEKeyType-based variant of {@link #openChestGui(EntityPlayer, IChestOrDrive, ICellHandler, IMEInventoryHandler, ItemStack, IAEStackType)}.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    default void openChestGui(EntityPlayer player, IChestOrDrive chest,
            ICellHandler cellHandler, IMEInventoryHandler<?> inv, ItemStack is, AEKeyType type) {
        var legacyType = appeng.api.storage.data.AEStackTypeRegistry.getType(type.getId());
        if (legacyType != null) {
            openChestGui(player, chest, cellHandler, (IMEInventoryHandler) inv, is, legacyType);
        }
    }
}

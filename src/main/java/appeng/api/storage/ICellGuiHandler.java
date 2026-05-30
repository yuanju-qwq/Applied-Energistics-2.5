
package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

public interface ICellGuiHandler {
    /**
     * 判断此 handler 是否可以处理指定的栈类型。
     *
     * @param type 栈类型
     * @return 如果可以处理返回 true
     * @deprecated Use {@link #isHandlerFor(AEKeyType)} instead.
     */
    @Deprecated
    <T extends IAEStack<T>> boolean isHandlerFor(IAEStackType<T> type);

    /**
     * AEKeyType-based variant of {@link #isHandlerFor(IAEStackType)}.
     */
    default boolean isHandlerFor(AEKeyType type) {
        var legacyType = appeng.api.storage.data.AEStackTypeRegistry.getType(type.getId());
        return legacyType != null && isHandlerFor(legacyType);
    }

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
     * 通过 {@link IAEStackType} 打开 ME Chest 的 GUI。
     * @deprecated Use {@link #openChestGui(EntityPlayer, IChestOrDrive, ICellHandler, IMEInventoryHandler, ItemStack, AEKeyType)}
     */
    @Deprecated
    <T extends IAEStack<T>> void openChestGui(EntityPlayer player, IChestOrDrive chest,
            ICellHandler cellHandler, IMEInventoryHandler<T> inv, ItemStack is, IAEStackType<T> type);

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

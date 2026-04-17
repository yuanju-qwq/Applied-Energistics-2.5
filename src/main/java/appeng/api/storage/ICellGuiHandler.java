
package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

public interface ICellGuiHandler {
    /**
     * @deprecated 请使用 {@link #isHandlerFor(IAEStackType)} 代替。
     */
    @Deprecated
    <T extends IAEStack<T>> boolean isHandlerFor(IStorageChannel<T> channel);

    /**
     * 判断此 handler 是否可以处理指定的栈类型。
     *
     * @param type 栈类型
     * @return 如果可以处理返回 true
     */
    default <T extends IAEStack<T>> boolean isHandlerFor(IAEStackType<T> type) {
        return this.isHandlerFor(type.getStorageChannel());
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
     * @deprecated 请使用 {@link #openChestGui(EntityPlayer, IChestOrDrive, ICellHandler, IMEInventoryHandler, ItemStack, IAEStackType)} 代替。
     */
    @Deprecated
    <T extends IAEStack<T>> void openChestGui(EntityPlayer player, IChestOrDrive chest, ICellHandler cellHandler,
            IMEInventoryHandler<T> inv, ItemStack is, IStorageChannel<T> chan);

    /**
     * 通过 {@link IAEStackType} 打开 ME Chest 的 GUI。
     */
    default <T extends IAEStack<T>> void openChestGui(EntityPlayer player, IChestOrDrive chest,
            ICellHandler cellHandler, IMEInventoryHandler<T> inv, ItemStack is, IAEStackType<T> type) {
        this.openChestGui(player, chest, cellHandler, inv, is, type.getStorageChannel());
    }

}

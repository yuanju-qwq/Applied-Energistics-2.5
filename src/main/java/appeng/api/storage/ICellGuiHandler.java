
package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

public interface ICellGuiHandler {
    /**
     * 判断此 handler 是否可以处理指定的栈类型。
     *
     * @param type 栈类型
     * @return 如果可以处理返回 true
     */
    <T extends IAEStack<T>> boolean isHandlerFor(IAEStackType<T> type);

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
     */
    <T extends IAEStack<T>> void openChestGui(EntityPlayer player, IChestOrDrive chest,
            ICellHandler cellHandler, IMEInventoryHandler<T> inv, ItemStack is, IAEStackType<T> type);

}

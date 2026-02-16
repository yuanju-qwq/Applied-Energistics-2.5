package appeng.api.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.features.IWirelessTermRegistry;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.misc.TileSecurityStation;
import appeng.util.Platform;

/**
 * 辅助类，用于处理玩家无线终端与AE2网格的连接。
 */
public final class PlayerWirelessGridHelper {
    private PlayerWirelessGridHelper() {
    }

    /**
     * 创建无线终端GUI对象（内部辅助方法）。
     *
     * @param item      终端物品
     * @param player    玩家
     * @param slotIndex 物品所在槽位
     * @return 无线终端GUI对象，若条件不满足则返回null
     */
    private static WirelessTerminalGuiObject getTerminalGuiObject(ItemStack item, EntityPlayer player, int slotIndex) {
        if (Platform.isClient())
            return null;

        IWirelessTermRegistry registry = AEApi.instance().registries().wireless();
        if (!registry.isWirelessTerminal(item)) {
            return null;
        }

        IWirelessTermHandler handler = registry.getWirelessTerminalHandler(item);
        String encryptionKey = handler.getEncryptionKey(item);
        if (encryptionKey.isEmpty()) {
            return null;
        }

        try {
            long key = Long.parseLong(encryptionKey);
            ILocatable securityStation = AEApi.instance().registries().locatable().getLocatableBy(key);
            if (!(securityStation instanceof TileSecurityStation)) {
                return null;
            }

            if (!handler.hasPower(player, 1000F, item)) {
                return null;
            }

            return new WirelessTerminalGuiObject(handler, item, player, player.world, slotIndex, 0, 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 在玩家背包中查找无线终端所在的槽位。
     *
     * @param player 玩家
     * @return 槽位索引，若未找到则返回-1
     */
    public static int findWirelessTerminalSlot(EntityPlayer player) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (AEApi.instance().registries().wireless().isWirelessTerminal(stack)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取玩家无线终端连接的网格节点（自动查找终端槽位）。 若终端超出范围或无效，会向玩家发送“超出范围”消息。
     *
     * @param player 玩家
     * @return 网格节点，若无法获取则返回null
     */
    public static IGridNode getGridNode(EntityPlayer player) {
        return getGridNode(player, findWirelessTerminalSlot(player));
    }

    /**
     * 获取玩家指定槽位无线终端连接的网格节点。 若终端超出范围或无效，会向玩家发送“超出范围”消息。
     *
     * @param player    玩家
     * @param slotIndex 终端所在槽位
     * @return 网格节点，若无法获取则返回null
     */
    public static IGridNode getGridNode(EntityPlayer player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= player.inventory.getSizeInventory()) {
            return null;
        }
        ItemStack terminalStack = player.inventory.getStackInSlot(slotIndex);
        if (terminalStack.isEmpty()) {
            return null;
        }

        WirelessTerminalGuiObject guiObject = getTerminalGuiObject(terminalStack, player, slotIndex);
        if (guiObject == null || !guiObject.rangeCheck()) {
            player.sendMessage(PlayerMessages.OutOfRange.get());
            return null;
        }
        return guiObject.getActionableNode();
    }

    /**
     * 获取玩家无线终端连接的存储网格（自动查找终端槽位）。
     *
     * @param player 玩家
     * @return 存储网格，若无法获取则返回null
     */
    public static IStorageGrid getStorageGrid(EntityPlayer player) {
        return getStorageGrid(player, findWirelessTerminalSlot(player));
    }

    /**
     * 获取玩家指定槽位无线终端连接的存储网格。
     *
     * @param player    玩家
     * @param slotIndex 终端所在槽位
     * @return 存储网格，若无法获取则返回null
     */
    public static IStorageGrid getStorageGrid(EntityPlayer player, int slotIndex) {
        IGridNode gridNode = getGridNode(player, slotIndex);
        if (gridNode == null) {
            return null;
        }
        IGrid grid = gridNode.getGrid();
        return grid != null ? grid.getCache(IStorageGrid.class) : null;
    }
}

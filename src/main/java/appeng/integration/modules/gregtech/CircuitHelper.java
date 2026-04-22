package appeng.integration.modules.gregtech;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;

/**
 * GT 可编程电路集成辅助类。
 * 基础实现全部返回默认值/空值，由 GregTech 在运行时通过 setInstance() 注册实际实现。
 */
public class CircuitHelper {

    private static CircuitHelper instance = new CircuitHelper();

    public static CircuitHelper getInstance() {
        return instance;
    }

    public static void setInstance(CircuitHelper helper) {
        instance = helper;
    }

    /**
     * 检查物品是否是可编程电路。
     */
    public boolean isProgrammableCircuit(ItemStack stack) {
        return false;
    }

    /**
     * 检查物品是否是集成电路。
     */
    public boolean isIntegratedCircuit(ItemStack stack) {
        return false;
    }

    /**
     * 将物品包装为可编程电路形式。
     *
     * @return 包装后的 AE 堆叠，如果无法包装则返回 null
     */
    @Nullable
    public IAEStack<?> wrapItemAsProgrammable(ItemStack sourceItem) {
        return null;
    }

    /**
     * 检查玩家背包中是否有编程工具包。
     */
    public boolean hasToolkitInInventory(@Nullable EntityPlayer player) {
        return false;
    }

    /**
     * 获取可编程电路物品堆叠。
     *
     * @return 可编程电路 ItemStack，如果不可用则返回 null
     */
    @Nullable
    public ItemStack getProgrammableCircuitStack() {
        return null;
    }

    /**
     * 可编程电路功能是否可用。
     */
    public boolean isProgrammableCircuitAvailable() {
        return false;
    }
}

package appeng.integration.modules.betterquesting;

import java.util.Collections;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;

import betterquesting.handlers.BQFluidInventoryUpdateEvent;
import betterquesting.handlers.BQInventoryUpdateEvent;

import appeng.fluids.items.ItemFluidDrop;

public class BQEventHelper {
    public static void sendMessage(ItemStack itemStack, EntityPlayer player) {
        {
            // 检查是否为流体伪物品（ItemFluidDrop），如果是则发送流体事件
            if (ItemFluidDrop.isFluidDrop(itemStack)) {
                FluidStack fluid = ItemFluidDrop.getFluidStack(itemStack);
                if (fluid != null) {
                    MinecraftForge.EVENT_BUS
                            .post(new BQFluidInventoryUpdateEvent(player, Collections.singletonList(fluid)));
                    return;
                }
            }
            MinecraftForge.EVENT_BUS.post(new BQInventoryUpdateEvent(player, Collections.singletonList(itemStack)));
        }
    }
}

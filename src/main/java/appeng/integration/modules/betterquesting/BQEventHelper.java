package appeng.integration.modules.betterquesting;

import java.util.Collections;

import com.glodblock.github.common.item.fake.FakeFluids;
import com.glodblock.github.common.item.fake.FakeItemRegister;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;

import betterquesting.handlers.BQFluidInventoryUpdateEvent;
import betterquesting.handlers.BQInventoryUpdateEvent;

import appeng.util.Platform;

public class BQEventHelper {
    public static void sendMessage(ItemStack itemStack, EntityPlayer player) {
        {
            if (Platform.isModLoaded("ae2fc")) {
                if (FakeFluids.isFluidFakeItem(itemStack)) {
                    FluidStack fluid = FakeItemRegister.getStack(itemStack);
                    MinecraftForge.EVENT_BUS
                            .post(new BQFluidInventoryUpdateEvent(player, Collections.singletonList(fluid)));
                    return;
                }
            }
            MinecraftForge.EVENT_BUS.post(new BQInventoryUpdateEvent(player, Collections.singletonList(itemStack)));
        }
    }
}

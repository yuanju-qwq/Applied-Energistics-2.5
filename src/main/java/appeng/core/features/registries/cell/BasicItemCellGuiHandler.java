package appeng.core.features.registries.cell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.storage.ICellGuiHandler;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEPartLocation;
import appeng.core.sync.GuiBridge;
import appeng.util.Platform;
import appeng.util.item.AEItemStackType;

public class BasicItemCellGuiHandler implements ICellGuiHandler {
    @Override
    public <T extends IAEStack<T>> boolean isHandlerFor(final IStorageChannel<T> channel) {
        return channel == AEItemStackType.INSTANCE.getStorageChannel();
    }

    @Override
    public <T extends IAEStack<T>> void openChestGui(final EntityPlayer player, final IChestOrDrive chest, final ICellHandler cellHandler,
            final IMEInventoryHandler<T> inv, final ItemStack is, final IStorageChannel<T> chan) {
        Platform.openGUI(player, (TileEntity) chest, AEPartLocation.fromFacing(chest.getUp()), GuiBridge.GUI_ME);
    }
}

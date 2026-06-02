package appeng.core.features.registries.cell;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import appeng.api.implementations.tiles.IChestOrDrive;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ICellGuiHandler;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.util.AEPartLocation;
import appeng.core.sync.AEGuiKeys;
import appeng.util.Platform;

public class BasicItemCellGuiHandler implements ICellGuiHandler {
    @Override
    public boolean isHandlerFor(final AEKeyType type) {
        return type == AEKeyType.items();
    }

    @Override
    public void openChestGui(final EntityPlayer player, final IChestOrDrive chest, final ICellHandler cellHandler,
            final IMEInventoryHandler inv, final ItemStack is, final AEKeyType type) {
        Platform.openGUI(player, (TileEntity) chest, AEPartLocation.fromFacing(chest.getUp()), AEGuiKeys.ME_TERMINAL);
    }
}

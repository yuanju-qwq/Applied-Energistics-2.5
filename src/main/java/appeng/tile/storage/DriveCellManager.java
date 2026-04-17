package appeng.tile.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.storage.ICellHandler;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.me.storage.DriveWatcher;
import appeng.tile.inventory.AppEngCellInventory;

public class DriveCellManager {
    private final TileDrive drive;
    private final AppEngCellInventory inv;
    private final ICellHandler[] handlersBySlot;
    private final DriveWatcher<IAEItemStack>[] invBySlot;
    private final Map<IAEStackType<?>, List<IMEInventoryHandler<?>>> inventoryHandlers;

    public DriveCellManager(TileDrive drive, AppEngCellInventory inv,
            ICellHandler[] handlersBySlot, DriveWatcher<IAEItemStack>[] invBySlot,
            Map<IAEStackType<?>, List<IMEInventoryHandler<?>>> inventoryHandlers) {
        this.drive = drive;
        this.inv = inv;
        this.handlersBySlot = handlersBySlot;
        this.invBySlot = invBySlot;
        this.inventoryHandlers = inventoryHandlers;
    }

    public void updateState(boolean isCached) {
        if (!isCached) {
            AEStackTypeRegistry.getAllTypes()
                    .forEach(type -> this.inventoryHandlers.put(type, new ArrayList<>(10)));

            double power = 2.0;

            for (int x = 0; x < this.inv.getSlots(); x++) {
                processCellSlot(x, power);
            }

            drive.getProxy().setIdlePowerUsage(power);
        }
    }

    @SuppressWarnings("unchecked")
    private void processCellSlot(int slot, double power) {
        final ItemStack is = this.inv.getStackInSlot(slot);
        this.invBySlot[slot] = null;
        this.handlersBySlot[slot] = null;

        if (!is.isEmpty()) {
            this.handlersBySlot[slot] = AEApi.instance().registries().cell().getHandler(is);

            if (this.handlersBySlot[slot] != null) {
                for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                    ICellInventoryHandler<?> cell = this.handlersBySlot[slot].getCellInventory(is, drive, type);

                    if (cell != null) {
                        this.inv.setHandler(slot, cell);
                        power += this.handlersBySlot[slot].cellIdleDrain(is, cell);

                        final DriveWatcher ih = new DriveWatcher(cell, is,
                                this.handlersBySlot[slot], drive);
                        ih.setPriority(drive.getPriority());
                        this.invBySlot[slot] = ih;
                        this.inventoryHandlers.get(type).add(ih);
                        break;
                    }
                }
            }
        }
    }
}

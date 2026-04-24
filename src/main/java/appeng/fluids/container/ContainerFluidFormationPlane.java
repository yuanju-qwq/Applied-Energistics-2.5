package appeng.fluids.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Upgrades;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.SlotRestrictedInput;
import appeng.fluids.parts.PartFluidFormationPlane;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;

public class ContainerFluidFormationPlane extends ContainerUpgradeable {
    private final PartFluidFormationPlane plane;

    public ContainerFluidFormationPlane(final InventoryPlayer ip, final PartFluidFormationPlane te) {
        super(ip, te);
        this.plane = te;
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        final IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer((new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0,
                187, 8, this.getInventoryPlayer()))
                .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 8 + 18,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 8 + 18 * 2,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 8 + 18 * 3,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
        this.addSlotToContainer(
                (new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 4, 187, 8 + 18 * 4,
                        this.getInventoryPlayer()))
                        .setNotDraggable());
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            // Clear config slots that exceed current capacity (e.g. capacity upgrade removed)
            final IAEStackInventory cfg = this.getConfig();
            if (cfg != null) {
                final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
                final int maxSlots = 18 + (9 * upgrades);
                for (int i = maxSlots; i < cfg.getSizeInventory(); i++) {
                    if (cfg.getAEStackInSlot(i) != null) {
                        cfg.putAEStackInSlot(i, null);
                    }
                }
            }
        }

        this.checkToolbox();
        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        final int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);

        return upgrades > idx;
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }
}

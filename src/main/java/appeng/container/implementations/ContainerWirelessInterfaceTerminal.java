package appeng.container.implementations;

import org.jetbrains.annotations.NotNull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.api.networking.security.IActionHost;
import appeng.container.helper.WirelessContainerHelper;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

public class ContainerWirelessInterfaceTerminal extends ContainerInterfaceTerminal
        implements IInventorySlotAware, IUpgradeableCellContainer, IAEAppEngInventory {
    protected final WirelessTerminalGuiObject wirelessTerminalGUIObject;
    protected final WirelessContainerHelper wirelessHelper;

    public ContainerWirelessInterfaceTerminal(InventoryPlayer ip, WirelessTerminalGuiObject guiObject) {
        super(ip, guiObject, false);

        this.wirelessTerminalGUIObject = guiObject;
        this.wirelessHelper = new WirelessContainerHelper(guiObject, ip, this);
        this.wirelessHelper.initUpgrades(this);
        this.loadFromNBT();

        this.bindPlayerInventory(ip, 0, 0);
        setupUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.wirelessHelper.tickWirelessStatus(this);
            super.detectAndSendChanges();
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @NotNull EntityPlayer player) {
        ItemStack result = this.wirelessHelper.handleMagnetSlotClick(slotId, dragType, clickTypeIn, this);
        if (result != null) {
            return result;
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    protected IActionHost getActionHost() {
        return wirelessTerminalGUIObject;
    }

    @Override
    public int getInventorySlot() {
        return this.wirelessHelper.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return this.wirelessHelper.isBaubleSlot();
    }

    @Override
    public int availableUpgrades() {
        return 1;
    }

    @Override
    public void setupUpgrades() {
        SlotRestrictedInput slot = this.wirelessHelper.createMagnetSlot(
                this.getInventoryPlayer(), 183, -1);
        if (slot != null) {
            this.addSlotToContainer(slot);
        }
    }

    private void loadFromNBT() {
        this.wirelessHelper.loadUpgradesFromNBT();
    }

    @Override
    public void saveChanges() {
        this.wirelessHelper.saveChanges();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {

    }
}

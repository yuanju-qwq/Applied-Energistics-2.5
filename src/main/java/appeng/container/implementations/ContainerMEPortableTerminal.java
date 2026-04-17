/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.api.implementations.guiobjects.IPortableCell;
import appeng.api.networking.security.IActionHost;
import appeng.container.helper.WirelessContainerHelper;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

public class ContainerMEPortableTerminal extends ContainerMEMonitorable
        implements IUpgradeableCellContainer, IAEAppEngInventory, IInventorySlotAware {

    protected final WirelessTerminalGuiObject wirelessTerminalGUIObject;
    protected final WirelessContainerHelper wirelessHelper;

    public ContainerMEPortableTerminal(InventoryPlayer ip, WirelessTerminalGuiObject guiObject, boolean bindInventory) {
        super(ip, guiObject, guiObject, bindInventory);
        this.wirelessTerminalGUIObject = guiObject;
        this.wirelessHelper = new WirelessContainerHelper(guiObject, ip, this);
        this.wirelessHelper.initUpgrades(this);
        this.loadFromNBT();

        this.bindPlayerInventory(ip, 0, 0);
        this.setupUpgrades();
    }

    public ContainerMEPortableTerminal(InventoryPlayer ip, IPortableCell guiObject) {
        super(ip, guiObject, guiObject, true);
        this.wirelessTerminalGUIObject = (WirelessTerminalGuiObject) guiObject;
        this.wirelessHelper = new WirelessContainerHelper(this.wirelessTerminalGUIObject, ip, this);
        this.wirelessHelper.initUpgrades(this);
        this.loadFromNBT();
        this.setupUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            this.wirelessHelper.tickWirelessStatus(this);
            super.detectAndSendChanges();
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        ItemStack result = this.wirelessHelper.handleMagnetSlotClick(slotId, dragType, clickTypeIn, this);
        if (result != null) {
            return result;
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    protected IActionHost getActionHost() {
        return this.wirelessTerminalGUIObject;
    }

    @Override
    public int availableUpgrades() {
        return 1;
    }

    @Override
    public void setupUpgrades() {
        SlotRestrictedInput slot = this.wirelessHelper.createMagnetSlot(
                this.getInventoryPlayer(), 206, 135);
        if (slot != null) {
            this.addSlotToContainer(slot);
        }
    }

    @Override
    public void saveChanges() {
        this.wirelessHelper.saveChanges();
    }

    protected void loadFromNBT() {
        this.wirelessHelper.loadUpgradesFromNBT();
    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {

    }

    @Override
    public int getInventorySlot() {
        return this.wirelessHelper.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return this.wirelessHelper.isBaubleSlot();
    }
}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.implementations.IUpgradeableCellContainer;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.container.helper.WirelessContainerHelper;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.contents.CellConfigLegacy;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.Platform;
import appeng.util.inv.InvOperation;

/**
 * 无线样板终端容器。
 * 处理模式使用 16 输入 + 6 输出的泛型栈库存（与扩展处理样板终端一致）。
 * 合成模式仍然使用 3x3 = 9 输入 + 1 输出。
 */
public class ContainerWirelessPatternTerminal extends ContainerPatternEncoder
        implements IUpgradeableCellContainer, IInventorySlotAware, IIAEStackInventory {

    // 处理模式：16 输入 + 6 输出（与扩展处理样板终端一致）
    private static final int PROCESSING_INPUT_SLOTS = PROCESSING_INPUT_LIMIT;
    private static final int PROCESSING_OUTPUT_SLOTS = 6;
    private static final String NBT_CRAFTING_GRID = "wirelessPatternCraftingGrid";
    private static final String NBT_OUTPUT = "wirelessPatternOutput";
    private static final String NBT_PATTERNS = "wirelessPatternSlots";
    private static final String LEGACY_NBT_PATTERNS = "patterns";

    private final WirelessTerminalGuiObject wirelessTerminalGUIObject;
    private final WirelessContainerHelper wirelessHelper;

    // 泛型栈库存（存储在 ItemStack NBT 中）
    private final IAEStackInventory craftingInv;
    private final IAEStackInventory outputInv;
    protected AppEngInternalInventory pattern;

    public ContainerWirelessPatternTerminal(final InventoryPlayer ip, final WirelessTerminalGuiObject gui) {
        super(ip, gui, gui, false);

        this.wirelessTerminalGUIObject = gui;
        this.wirelessHelper = new WirelessContainerHelper(gui, ip, this);
        this.wirelessHelper.initUpgrades(this);

        // 创建泛型栈库存（处理模式容量）
        this.craftingInv = new IAEStackInventory(this, PROCESSING_INPUT_SLOTS, StorageName.CRAFTING_INPUT);
        this.outputInv = new IAEStackInventory(this, PROCESSING_OUTPUT_SLOTS, StorageName.CRAFTING_OUTPUT);
        this.pattern = new AppEngInternalInventory(this, 2);

        this.loadFromNBT();
        final IItemHandler craftingHandler = new CellConfigLegacy(this.craftingInv, null);
        this.addSlotToContainer(this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(),
                this.getPowerSource(), gui, craftingHandler, this.pattern, this.cOut, 110, -76 + 18, this, 2, this));
        this.craftSlot.setIIcon(-1);

        // 样板输入/输出槽
        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        pattern, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        pattern, 1, 147, -72 + 34, this.getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);
        this.restoreEncodedPatternContents();

        this.updateOrderOfOutputSlots();

        this.bindPlayerInventory(ip, 0, 0);

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
    public boolean isSlotEnabled(final int idx) {
        boolean crafting = false;
        if (Platform.isServer()) {
            NBTTagCompound nbtTagCompound = iGuiItemObject.getItemStack().getTagCompound();
            if (nbtTagCompound != null) {
                if (nbtTagCompound.hasKey("isCraftingMode")) {
                    crafting = nbtTagCompound.getBoolean("isCraftingMode");
                }
            }
        }
        if (idx == 1) {
            return Platform.isServer() ? !crafting : !this.isCraftingMode();
        } else if (idx == 2) {
            return Platform.isServer() ? crafting : this.isCraftingMode();
        } else {
            return false;
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
    public int getInventorySlot() {
        return this.wirelessHelper.getInventorySlot();
    }

    @Override
    public boolean isBaubleSlot() {
        return this.wirelessHelper.isBaubleSlot();
    }

    // ---- NBT 保存/加载 ----

    @Override
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = this.wirelessHelper.saveUpgradesToNBT();
            this.craftingInv.writeToNBT(tag, NBT_CRAFTING_GRID);
            this.outputInv.writeToNBT(tag, NBT_OUTPUT);
            this.pattern.writeToNBT(tag, NBT_PATTERNS);
            final NBTTagCompound data = this.wirelessTerminalGUIObject.getItemStack().getTagCompound();
            if (data != null) {
                data.removeTag(LEGACY_NBT_PATTERNS);
            }
            this.wirelessTerminalGUIObject.saveChanges(tag);
        }
    }

    private void loadFromNBT() {
        this.wirelessHelper.loadUpgradesFromNBT();
        NBTTagCompound data = wirelessTerminalGUIObject.getItemStack().getTagCompound();
        if (data != null) {
            this.craftingInv.readFromNBT(data, NBT_CRAFTING_GRID);
            this.outputInv.readFromNBT(data, NBT_OUTPUT);
            if (data.hasKey(NBT_PATTERNS)) {
                this.loadValidPatternSlots(data, NBT_PATTERNS);
            } else {
                this.loadValidPatternSlots(data, LEGACY_NBT_PATTERNS);
            }
        }
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);

        if (inv == this.pattern && slot == 1) {
            this.restoreEncodedPatternContents();
        }
    }

    private void restoreEncodedPatternContents() {
        this.clearPatternContents();

        final ItemStack encodedPattern = this.pattern.getStackInSlot(1);
        if (encodedPattern.isEmpty() || !(encodedPattern.getItem() instanceof ICraftingPatternItem)) {
            this.getAndUpdateOutput();
            this.saveChanges();
            return;
        }

        final ICraftingPatternItem patternItem = (ICraftingPatternItem) encodedPattern.getItem();
        final ICraftingPatternDetails details = patternItem.getPatternForItem(encodedPattern,
                this.getPlayerInv().player.world);
        if (details == null) {
            this.getAndUpdateOutput();
            this.saveChanges();
            return;
        }

        this.setCraftingMode(details.isCraftable());
        this.setSubstitute(details.canSubstitute());

        final IAEStack<?>[] inputs = details.getAEInputs();
        for (int i = 0; i < this.craftingInv.getSizeInventory(); i++) {
            final IAEStack<?> input = inputs != null && i < inputs.length && inputs[i] != null ? inputs[i].copy() : null;
            this.craftingInv.putAEStackInSlot(i, input);
        }

        final IAEStack<?>[] outputs = details.getAEOutputs();
        for (int i = 0; i < this.outputInv.getSizeInventory(); i++) {
            final IAEStack<?> output = outputs != null && i < outputs.length && outputs[i] != null
                    ? outputs[i].copy()
                    : null;
            this.outputInv.putAEStackInSlot(i, output);
        }

        this.getAndUpdateOutput();
        this.saveChanges();
    }

    private void clearPatternContents() {
        for (int i = 0; i < this.craftingInv.getSizeInventory(); i++) {
            this.craftingInv.putAEStackInSlot(i, null);
        }
        for (int i = 0; i < this.outputInv.getSizeInventory(); i++) {
            this.outputInv.putAEStackInSlot(i, null);
        }
    }

    private void loadValidPatternSlots(final NBTTagCompound data, final String key) {
        final AppEngInternalInventory loadedPattern = new AppEngInternalInventory(null, 2);
        loadedPattern.readFromNBT(data, key);

        final ItemStack blankPattern = loadedPattern.getStackInSlot(0);
        if (!blankPattern.isEmpty()
                && AEApi.instance().definitions().materials().blankPattern().isSameAs(blankPattern)) {
            this.pattern.setStackInSlot(0, blankPattern.copy());
        }

        final ItemStack encodedPattern = loadedPattern.getStackInSlot(1);
        if (!encodedPattern.isEmpty() && encodedPattern.getItem() instanceof ICraftingPatternItem) {
            this.pattern.setStackInSlot(1, encodedPattern.copy());
        }
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        return super.getInventoryByName(name);
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    // ---- IIAEStackInventory 实现 ----

    @Override
    public void saveAEStackInv() {
        this.saveChanges();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CRAFTING_INPUT) {
            return this.craftingInv;
        }
        if (name == StorageName.CRAFTING_OUTPUT) {
            return this.outputInv;
        }
        return null;
    }

    // ---- 覆盖父类的 IAEStackInventory 访问器（无线终端数据存储在 Container 自身而非 Part）----

    @Override
    public IAEStackInventory getCraftingAEInv() {
        return this.craftingInv;
    }

    @Override
    public IAEStackInventory getOutputAEInv() {
        return this.outputInv;
    }
}

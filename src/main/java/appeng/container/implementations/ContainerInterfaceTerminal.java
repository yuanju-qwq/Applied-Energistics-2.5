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

import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.client.me.SlotDisconnected;
import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketCompressedNBT;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.InventoryAction;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.items.misc.ItemEncodedPattern;
import appeng.parts.misc.PartInterface;
import appeng.parts.reporting.PartInterfaceTerminal;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TileInterface;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.inv.WrapperFilteredItemHandler;
import appeng.util.inv.WrapperRangeItemHandler;
import appeng.util.inv.filter.IAEItemFilter;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;

public class ContainerInterfaceTerminal extends AEBaseContainer {

    /**
     * this stuff is all server side..
     */

    private static long autoBase = Long.MIN_VALUE;
    private final Map<IInterfaceHost, InvTracker> diList = new HashMap<>();
    private final List<ProviderTracker> provider = new ArrayList<>();
    private final Map<Long, InvTracker> byId = new HashMap<>();
    private final Map<Long, ProviderTracker> providerId = new HashMap<>();
    private IGrid grid;
    private NBTTagCompound data = new NBTTagCompound();

    public ContainerInterfaceTerminal(final InventoryPlayer ip, final PartInterfaceTerminal anchor) {
        super(ip, anchor);

        if (Platform.isServer()) {
            this.grid = anchor.getActionableNode().getGrid();
        }

        this.bindPlayerInventory(ip, 0, 0);
    }

    public ContainerInterfaceTerminal(final InventoryPlayer ip, final WirelessTerminalGuiObject guiObject,
                                      boolean bindInventory) {
        super(ip, guiObject);

        if (Platform.isServer()) {
            IGridNode node = guiObject.getActionableNode();
            if (node != null && node.isActive()) {
                this.grid = node.getGrid();
            }
        }

        if (bindInventory) {
            this.bindPlayerInventory(ip, 0, 0);
        }
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isClient()) {
            return;
        }

        super.detectAndSendChanges();

        if (this.grid == null) {
            return;
        }

        int total = 0;
        boolean missing = false;

        final IActionHost host = this.getActionHost();
        if (host != null) {
            final IGridNode agn = host.getActionableNode();
            if (agn != null && agn.isActive()) {
                for (final IGridNode gn : this.grid.getMachines(TileInterface.class)) {
                    if (gn.isActive()) {
                        final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
                        if (ih.getInterfaceDuality().getConfigManager()
                                .getSetting(Settings.INTERFACE_TERMINAL) == YesNo.NO) {
                            continue;
                        }

                        final InvTracker t = this.diList.get(ih);
                        if (t == null) {
                            missing = true;
                        } else {
                            final DualityInterface dual = ih.getInterfaceDuality();
                            if (!t.unlocalizedName.equals(dual.getTermName())) {
                                missing = true;
                            }
                        }
                        total++;
                    }
                }

                for (final IGridNode gn : this.grid.getMachines(PartInterface.class)) {
                    if (gn.isActive()) {
                        final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
                        if (ih.getInterfaceDuality().getConfigManager()
                                .getSetting(Settings.INTERFACE_TERMINAL) == YesNo.NO) {
                            continue;
                        }

                        final InvTracker t = this.diList.get(ih);
                        if (t == null) {
                            missing = true;
                        } else {
                            final DualityInterface dual = ih.getInterfaceDuality();
                            if (!t.unlocalizedName.equals(dual.getTermName())) {
                                missing = true;
                            }
                        }
                        total++;
                    }
                }
                if (Platform.GTLoaded) {
                    // GT pattern provider detection — injected by GregTech via Mixin
                    int[] gtResult = checkGTProviderChanges();
                    total += gtResult[0];
                    if (gtResult[1] != 0) {
                        missing = true;
                    }
                }
            }
        }

        if (total != this.diList.size() + (Platform.GTLoaded ? this.provider.size() : 0) || missing) {
            this.regenList(this.data);
        } else {

            for (final Entry<IInterfaceHost, InvTracker> en : this.diList.entrySet()) {
                final InvTracker inv = en.getValue();
                for (int x = 0; x < inv.server.getSlots(); x++) {
                    if (this.isDifferent(inv.server.getStackInSlot(x), inv.client.getStackInSlot(x))) {
                        this.addItems(this.data, inv, x, 1);
                    }
                }
            }
            if (Platform.GTLoaded) {
                for (final ProviderTracker en : this.provider) {
                    for (int x = 0; x < en.server.getSlots(); x++) {
                        if (this.isDifferent(en.server.getStackInSlot(x), en.client.getStackInSlot(x))) {
                            this.addProvider(this.data, en, x, 1);
                        }
                    }
                }
            }
        }

        if (!this.data.isEmpty()) {
            try {
                NetworkHandler.instance().sendTo(new PacketCompressedNBT(this.data),
                        (EntityPlayerMP) this.getPlayerInv().player);
            } catch (final IOException e) {
            }
            this.data = new NBTTagCompound();
        }
    }

    @Override
    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        final InvTracker inv = this.byId.get(id);
        if (inv != null) {
            // The code below this block assumes "slot" is an interface slot id. Not in this case.
            if (action == InventoryAction.PLACE_SINGLE) {
                final AppEngSlot playerSlot;
                try {
                    playerSlot = (AppEngSlot) this.inventorySlots.get(slot);
                } catch (IndexOutOfBoundsException ignored) {
                    return;
                }

                if (!playerSlot.isPlayerSide() || !playerSlot.getHasStack())
                    return;

                var itemStack = playerSlot.getStack();
                if (!itemStack.isEmpty()) {
                    var handler = new WrapperFilteredItemHandler(
                            new WrapperRangeItemHandler(inv.server, 0, 9 * (inv.numUpgrades + 1)),
                            new PatternSlotFilter());
                    playerSlot.putStack(ItemHandlerHelper.insertItem(handler, itemStack, false));
                    detectAndSendChanges();
                }

                return;
            }

            final ItemStack is = inv.server.getStackInSlot(slot);
            final boolean hasItemInHand = !player.inventory.getItemStack().isEmpty();

            final InventoryAdaptor playerHand = new AdaptorItemHandler(new WrapperCursorItemHandler(player.inventory));

            final IItemHandler theSlot = new WrapperFilteredItemHandler(
                    new WrapperRangeItemHandler(inv.server, slot, slot + 1), new PatternSlotFilter());
            final InventoryAdaptor interfaceSlot = new AdaptorItemHandler(theSlot);

            IItemHandler interfaceHandler = inv.server;
            boolean canInsert = true;

            switch (action) {
                case PICKUP_OR_SET_DOWN:
                    if (hasItemInHand) {
                        for (int s = 0; s < interfaceHandler.getSlots(); s++) {
                            if (Platform.itemComparisons().isSameItem(interfaceHandler.getStackInSlot(s),
                                    player.inventory.getItemStack())) {
                                canInsert = false;
                                break;
                            }
                        }
                        if (canInsert) {
                            ItemStack inSlot = theSlot.getStackInSlot(0);
                            if (inSlot.isEmpty()) {
                                player.inventory.setItemStack(interfaceSlot.addItems(player.inventory.getItemStack()));
                            } else {
                                inSlot = inSlot.copy();
                                final ItemStack inHand = player.inventory.getItemStack().copy();

                                ItemHandlerUtil.setStackInSlot(theSlot, 0, ItemStack.EMPTY);
                                player.inventory.setItemStack(ItemStack.EMPTY);

                                player.inventory.setItemStack(interfaceSlot.addItems(inHand.copy()));

                                if (player.inventory.getItemStack().isEmpty()) {
                                    player.inventory.setItemStack(inSlot);
                                } else {
                                    player.inventory.setItemStack(inHand);
                                    ItemHandlerUtil.setStackInSlot(theSlot, 0, inSlot);
                                }
                            }
                        }
                    } else {
                        ItemHandlerUtil.setStackInSlot(theSlot, 0, playerHand.addItems(theSlot.getStackInSlot(0)));
                    }

                    break;
                case SPLIT_OR_PLACE_SINGLE:
                    if (hasItemInHand) {
                        for (int s = 0; s < interfaceHandler.getSlots(); s++) {
                            if (Platform.itemComparisons().isSameItem(interfaceHandler.getStackInSlot(s),
                                    player.inventory.getItemStack())) {
                                canInsert = false;
                                break;
                            }
                        }
                        if (canInsert) {
                            ItemStack extra = playerHand.removeItems(1, ItemStack.EMPTY, null);
                            if (!extra.isEmpty() && !interfaceSlot.containsItems()) {
                                extra = interfaceSlot.addItems(extra);
                            }
                            if (!extra.isEmpty()) {
                                playerHand.addItems(extra);
                            }
                        }
                    } else if (!is.isEmpty()) {
                        ItemStack extra = interfaceSlot.removeItems((is.getCount() + 1) / 2, ItemStack.EMPTY, null);
                        if (!extra.isEmpty()) {
                            extra = playerHand.addItems(extra);
                        }
                        if (!extra.isEmpty()) {
                            interfaceSlot.addItems(extra);
                        }
                    }

                    break;
                case SHIFT_CLICK:

                    final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(player);

                    ItemHandlerUtil.setStackInSlot(theSlot, 0, playerInv.addItems(theSlot.getStackInSlot(0)));

                    break;
                case MOVE_REGION:

                    final InventoryAdaptor playerInvAd = InventoryAdaptor.getAdaptor(player);
                    for (int x = 0; x < inv.server.getSlots(); x++) {
                        ItemHandlerUtil.setStackInSlot(inv.server, x,
                                playerInvAd.addItems(inv.server.getStackInSlot(x)));
                    }

                    break;
                case CREATIVE_DUPLICATE:

                    if (player.capabilities.isCreativeMode && !hasItemInHand) {
                        player.inventory.setItemStack(is.isEmpty() ? ItemStack.EMPTY : is.copy());
                    }

                    break;
            }

            this.updateHeld(player);
        }
        if (Platform.GTLoaded) {
            final ProviderTracker providerTracker = this.providerId.get(id);
            if (providerTracker != null) {
                if (action == InventoryAction.PLACE_SINGLE) {
                    final AppEngSlot playerSlot;
                    try {
                        playerSlot = (AppEngSlot) this.inventorySlots.get(slot);
                    } catch (IndexOutOfBoundsException ignored) {
                        return;
                    }

                    if (!playerSlot.isPlayerSide() || !playerSlot.getHasStack())
                        return;

                    var itemStack = playerSlot.getStack();
                    if (!itemStack.isEmpty()) {
                        int slotCount = (int) Math.pow(2, 1 + Math.min(9, providerTracker.tier));
                        int patternSlotLimit = Math.min(
                                slotCount,
                                providerTracker.server.getSlots()
                        );

                        var handler = new WrapperFilteredItemHandler(
                                new WrapperRangeItemHandler(providerTracker.server, 0, patternSlotLimit),
                                new PatternSlotFilter());
                        playerSlot.putStack(ItemHandlerHelper.insertItem(handler, itemStack, false));
                        detectAndSendChanges();
                    }
                    return;
                }

                final ItemStack is = providerTracker.server.getStackInSlot(slot);
                final boolean hasItemInHand = !player.inventory.getItemStack().isEmpty();

                final InventoryAdaptor playerHand = new AdaptorItemHandler(
                        new WrapperCursorItemHandler(player.inventory));

                final IItemHandler theSlot = new WrapperFilteredItemHandler(
                        new WrapperRangeItemHandler(providerTracker.server, slot, slot + 1),
                        new PatternSlotFilter());
                final InventoryAdaptor interfaceSlot = new AdaptorItemHandler(theSlot);

                IItemHandler interfaceHandler = providerTracker.server;
                boolean canInsert = true;

                switch (action) {
                    case PICKUP_OR_SET_DOWN:
                        if (hasItemInHand) {
                            for (int s = 0; s < interfaceHandler.getSlots(); s++) {
                                if (Platform.itemComparisons().isSameItem(
                                        interfaceHandler.getStackInSlot(s),
                                        player.inventory.getItemStack())) {
                                    canInsert = false;
                                    break;
                                }
                            }
                            if (canInsert) {
                                ItemStack inSlot = theSlot.getStackInSlot(0);
                                if (inSlot.isEmpty()) {
                                    player.inventory.setItemStack(
                                            interfaceSlot.addItems(player.inventory.getItemStack()));
                                } else {
                                    inSlot = inSlot.copy();
                                    final ItemStack inHand = player.inventory.getItemStack().copy();

                                    ItemHandlerUtil.setStackInSlot(theSlot, 0, ItemStack.EMPTY);
                                    player.inventory.setItemStack(ItemStack.EMPTY);

                                    player.inventory.setItemStack(interfaceSlot.addItems(inHand.copy()));

                                    if (player.inventory.getItemStack().isEmpty()) {
                                        player.inventory.setItemStack(inSlot);
                                    } else {
                                        player.inventory.setItemStack(inHand);
                                        ItemHandlerUtil.setStackInSlot(theSlot, 0, inSlot);
                                    }
                                }
                            }
                        } else {
                            ItemHandlerUtil.setStackInSlot(theSlot, 0,
                                    playerHand.addItems(theSlot.getStackInSlot(0)));
                        }
                        break;

                    case SPLIT_OR_PLACE_SINGLE:
                        if (hasItemInHand) {
                            for (int s = 0; s < interfaceHandler.getSlots(); s++) {
                                if (Platform.itemComparisons().isSameItem(
                                        interfaceHandler.getStackInSlot(s),
                                        player.inventory.getItemStack())) {
                                    canInsert = false;
                                    break;
                                }
                            }
                            if (canInsert) {
                                ItemStack extra = playerHand.removeItems(1, ItemStack.EMPTY, null);
                                if (!extra.isEmpty() && !interfaceSlot.containsItems()) {
                                    extra = interfaceSlot.addItems(extra);
                                }
                                if (!extra.isEmpty()) {
                                    playerHand.addItems(extra);
                                }
                            }
                        } else if (!is.isEmpty()) {
                            ItemStack extra = interfaceSlot.removeItems(
                                    (is.getCount() + 1) / 2, ItemStack.EMPTY, null);
                            if (!extra.isEmpty()) {
                                extra = playerHand.addItems(extra);
                            }
                            if (!extra.isEmpty()) {
                                interfaceSlot.addItems(extra);
                            }
                        }
                        break;

                    case SHIFT_CLICK:
                        final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(player);
                        ItemHandlerUtil.setStackInSlot(theSlot, 0,
                                playerInv.addItems(theSlot.getStackInSlot(0)));
                        break;

                    case MOVE_REGION:
                        final InventoryAdaptor playerInvAd = InventoryAdaptor.getAdaptor(player);
                        for (int x = 0; x < providerTracker.server.getSlots(); x++) {
                            ItemHandlerUtil.setStackInSlot(providerTracker.server, x,
                                    playerInvAd.addItems(providerTracker.server.getStackInSlot(x)));
                        }
                        break;

                    case CREATIVE_DUPLICATE:
                        if (player.capabilities.isCreativeMode && !hasItemInHand) {
                            player.inventory.setItemStack(
                                    is.isEmpty() ? ItemStack.EMPTY : is.copy());
                        }
                        break;
                }

                this.updateHeld(player);
            }
        }
    }

    private void regenList(final NBTTagCompound data) {
        this.byId.clear();
        this.providerId.clear();
        this.diList.clear();
        if (Platform.GTLoaded)
            this.provider.clear();

        final IActionHost host = this.getActionHost();
        if (host != null) {
            final IGridNode agn = host.getActionableNode();
            if (agn != null && agn.isActive()) {
                for (final IGridNode gn : this.grid.getMachines(TileInterface.class)) {
                    final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
                    final DualityInterface dual = ih.getInterfaceDuality();
                    if (gn.isActive() && dual.getConfigManager().getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES) {
                        this.diList.put(ih, new InvTracker(dual, dual.getPatterns(), dual.getTermName()));
                    }
                }

                for (final IGridNode gn : this.grid.getMachines(PartInterface.class)) {
                    final IInterfaceHost ih = (IInterfaceHost) gn.getMachine();
                    final DualityInterface dual = ih.getInterfaceDuality();
                    if (gn.isActive() && dual.getConfigManager().getSetting(Settings.INTERFACE_TERMINAL) == YesNo.YES) {
                        this.diList.put(ih, new InvTracker(dual, dual.getPatterns(), dual.getTermName()));
                    }
                }
                if (Platform.GTLoaded) {
                    // GT pattern provider collection — injected by GregTech via Mixin
                    collectGTProviders();
                }
            }
        }

        data.setBoolean("clear", true);

        for (final Entry<IInterfaceHost, InvTracker> en : this.diList.entrySet()) {
            final InvTracker inv = en.getValue();
            this.byId.put(inv.which, inv);
            this.addItems(data, inv, 0, inv.server.getSlots());
        }
        if (Platform.GTLoaded) {
            for (final ProviderTracker en : provider) {
                this.providerId.put(en.which, en);
                this.addProvider(data, en, 0, en.server.getSlots());
            }
        }
    }

    /**
     * Check GT pattern provider changes.
     * Base implementation returns {0, 0}, actual logic injected by GregTech via Mixin.
     *
     * @return int array where [0] = total count, [1] = 1 if missing else 0
     */
    protected int[] checkGTProviderChanges() {
        return new int[] { 0, 0 };
    }

    /**
     * Collect GT pattern providers.
     * Base implementation is empty, actual logic injected by GregTech via Mixin.
     */
    protected void collectGTProviders() {
    }

    private boolean isDifferent(final ItemStack a, final ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) {
            return false;
        }

        if (a.isEmpty() || b.isEmpty()) {
            return true;
        }

        return !ItemStack.areItemStacksEqual(a, b);
    }

    private void addProvider(final NBTTagCompound data, final ProviderTracker inv, final int offset, final int length) {
        final String name = '=' + Long.toString(inv.which, Character.MAX_RADIX);
        final NBTTagCompound tag = data.getCompoundTag(name);

        if (tag.isEmpty()) {
            tag.setBoolean("provider", true);
            tag.setString("un", inv.unlocalizedName);
            tag.setTag("pos", NBTUtil.createPosTag(inv.pos));
            tag.setInteger("dim", inv.dim);
            tag.setLong("sortBy", inv.sortBy);
            tag.setInteger("tier", inv.tier);
            tag.setInteger("slots", inv.server.getSlots());
        }

        for (int x = 0; x < length; x++) {
            final NBTTagCompound itemNBT = new NBTTagCompound();
            final ItemStack is = inv.server.getStackInSlot(x + offset);

            // 同步更新客户端缓存
            ItemHandlerUtil.setStackInSlot(inv.client, x + offset,
                    is.isEmpty() ? ItemStack.EMPTY : is.copy());

            if (!is.isEmpty()) {
                stackWriteToNBT(is, itemNBT);
            }
            tag.setTag(Integer.toString(x + offset), itemNBT);
        }

        data.setTag(name, tag);
    }


    private void addItems(final NBTTagCompound data, final InvTracker inv, final int offset, final int length) {
        final String name = '=' + Long.toString(inv.which, Character.MAX_RADIX);
        final NBTTagCompound tag = data.getCompoundTag(name);

        if (tag.isEmpty()) {
            tag.setBoolean("provider", false);
            tag.setLong("sortBy", inv.sortBy);
            tag.setString("un", inv.unlocalizedName);
            tag.setTag("pos", NBTUtil.createPosTag(inv.pos));
            tag.setInteger("dim", inv.dim);
            tag.setInteger("numUpgrades", inv.numUpgrades);
        }

        for (int x = 0; x < length; x++) {
            final NBTTagCompound itemNBT = new NBTTagCompound();

            final ItemStack is = inv.server.getStackInSlot(x + offset);

            // "update" client side.
            ItemHandlerUtil.setStackInSlot(inv.client, x + offset, is.isEmpty() ? ItemStack.EMPTY : is.copy());

            if (!is.isEmpty()) {
                stackWriteToNBT(is, itemNBT);
            }

            tag.setTag(Integer.toString(x + offset), itemNBT);
        }

        data.setTag(name, tag);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer p, int idx) {
        if (Platform.isClient()) {
            var playerSlot = this.inventorySlots.get(idx);
            if (playerSlot instanceof AppEngSlot playerAppEngSlot && playerAppEngSlot.isPlayerSide()) {
                for (var slot : this.inventorySlots) {
                    if (slot instanceof SlotDisconnected slotDisconnected && !slot.getHasStack()) {
                        // Signal the server to move the pattern.
                        var packet = new PacketInventoryAction(InventoryAction.PLACE_SINGLE,
                                playerAppEngSlot.slotNumber, slotDisconnected.getSlot().getId());

                        NetworkHandler.instance().sendToServer(packet);

                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return super.transferStackInSlot(p, idx);
    }

    public static class ProviderTracker {
        public final BlockPos pos;
        public final int dim;
        public final int tier;
        public final long which = autoBase++;
        public final IItemHandler client;
        public final IItemHandler server;
        public final String unlocalizedName;
        public final long sortBy;

        /**
         * General constructor for GT pattern provider tracking.
         * Actual instantiation injected by GregTech via Mixin.
         */
        public ProviderTracker(BlockPos pos, int dim, int tier, IItemHandler server, String unlocalizedName) {
            this.pos = pos;
            this.dim = dim;
            this.tier = tier;
            this.server = server;
            this.client = new AppEngInternalInventory(null, this.server.getSlots());
            this.unlocalizedName = unlocalizedName;
            this.sortBy = getSortValue(pos);
        }

        private long getSortValue(BlockPos pos) {
            return ((long) pos.getZ() << 24) ^ ((long) pos.getX() << 8) ^ pos.getY();
        }
    }


    private static class InvTracker {

        private final long sortBy;
        private final long which = autoBase++;
        private final String unlocalizedName;
        private final IItemHandler client;
        private final IItemHandler server;
        private final BlockPos pos;
        private final int dim;
        private final int numUpgrades;

        public InvTracker(final DualityInterface dual, final IItemHandler patterns, final String unlocalizedName) {
            this.server = patterns;
            this.client = new AppEngInternalInventory(null, this.server.getSlots());
            this.unlocalizedName = unlocalizedName;
            this.sortBy = dual.getSortValue();
            this.pos = dual.getLocation().getPos();
            this.dim = dual.getLocation().getWorld().provider.getDimension();
            this.numUpgrades = dual.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION);
        }
    }

    private static class PatternSlotFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(IItemHandler inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(IItemHandler inv, int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof ItemEncodedPattern;
        }
    }
}

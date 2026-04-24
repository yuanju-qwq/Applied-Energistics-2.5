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

package appeng.container;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.NotNull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.SecurityPermissions;
import appeng.api.definitions.IItemDefinition;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.parts.IPart;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.ContainerInteractionResult;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.me.SlotME;
import appeng.container.guisync.GuiSync;
import appeng.container.guisync.SyncData;
import appeng.container.implementations.ContainerPatternProvider;
import appeng.container.slot.*;
import appeng.container.slot.SlotRestrictedInput.PlacableItemType;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.items.FluidDummyItem;
import appeng.fluids.util.AEFluidStack;
import appeng.fluids.util.AEFluidStackType;
import appeng.helpers.ICustomNameObject;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

public abstract class AEBaseContainer extends Container {
    private static final Map<Class<?>, List<SyncBinding>> SYNC_BINDINGS = new ConcurrentHashMap<>();

    private final InventoryPlayer invPlayer;
    private final IActionSource mySrc;
    private final HashSet<Integer> locked = new HashSet<>();
    private final TileEntity tileEntity;
    private final IPart part;
    protected final IGuiItemObject obj;
    private final HashMap<Integer, SyncData> syncData = new HashMap<>();
    private boolean isContainerValid = true;
    private String customName;
    private ContainerOpenContext openContext;
    private IMEInventoryHandler<IAEItemStack> cellInv;
    private IMEInventoryHandler<IAEFluidStack> fluidCellInv;
    private IEnergySource powerSrc;
    private boolean sentCustomName;
    private int ticksSinceCheck = 900;
    /**
     * Unified target stack for all types. Replaces the old separate item/fluid fields.
     */
    private IAEStack<?> clientRequestedTargetStack = null;

    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart) {
        this(ip, myTile, myPart, null);
    }

    public AEBaseContainer(final InventoryPlayer ip, final TileEntity myTile, final IPart myPart,
            final IGuiItemObject gio) {
        this.invPlayer = ip;
        this.tileEntity = myTile;
        this.part = myPart;
        this.obj = gio;
        this.mySrc = new PlayerSource(ip.player, this.getActionHost());
        this.prepareSync();
    }

    protected IActionHost getActionHost() {
        if (this.obj instanceof IActionHost) {
            return (IActionHost) this.obj;
        }

        if (this.tileEntity instanceof IActionHost) {
            return (IActionHost) this.tileEntity;
        }

        if (this.part instanceof IActionHost) {
            return (IActionHost) this.part;
        }

        return null;
    }

    private void prepareSync() {
        for (final SyncBinding binding : getSyncBindings(this.getClass())) {
            this.syncData.put(binding.annotation.value(), new SyncData(this, binding.field, binding.annotation));
        }
    }

    private static List<SyncBinding> getSyncBindings(final Class<?> containerClass) {
        return SYNC_BINDINGS.computeIfAbsent(containerClass, clazz -> {
            final HashSet<Integer> usedChannels = new HashSet<>();
            final List<SyncBinding> bindings = new ArrayList<>();

            for (final Field field : clazz.getFields()) {
                final GuiSync annotation = field.getAnnotation(GuiSync.class);
                if (annotation == null) {
                    continue;
                }

                if (!usedChannels.add(annotation.value())) {
                    AELog.warn("Channel already in use: " + annotation.value() + " for " + field.getName());
                    continue;
                }

                bindings.add(new SyncBinding(field, annotation));
            }

            return bindings;
        });
    }

    private static final class SyncBinding {

        private final Field field;
        private final GuiSync annotation;

        private SyncBinding(final Field field, final GuiSync annotation) {
            this.field = field;
            this.annotation = annotation;
        }
    }

    public AEBaseContainer(final InventoryPlayer ip, final Object anchor) {
        this.invPlayer = ip;
        this.tileEntity = anchor instanceof TileEntity ? (TileEntity) anchor : null;
        this.part = anchor instanceof IPart ? (IPart) anchor : null;
        this.obj = anchor instanceof IGuiItemObject ? (IGuiItemObject) anchor : null;

        if (this.tileEntity == null && this.part == null && this.obj == null) {
            throw new IllegalArgumentException("Must have a valid anchor, instead " + anchor + " in " + ip);
        }

        this.mySrc = new PlayerSource(ip.player, this.getActionHost());

        this.prepareSync();
    }

    /**
     * @deprecated Use {@link #getTargetGenericStack()} for type-agnostic access.
     */
    @Deprecated
    public IAEItemStack getTargetStack() {
        return this.clientRequestedTargetStack instanceof IAEItemStack item ? item : null;
    }

    /**
     * @deprecated Use {@link #setTargetStack(IAEStack)} instead.
     */
    @Deprecated
    public void setTargetStack(final IAEItemStack stack) {
        this.setTargetStack((IAEStack<?>) stack);
    }

    /**
     * @deprecated Use {@link #getTargetGenericStack()} for type-agnostic access.
     */
    @Deprecated
    public IAEFluidStack getTargetFluidStack() {
        return this.clientRequestedTargetStack instanceof IAEFluidStack fluid ? fluid : null;
    }

    /**
     * @deprecated Use {@link #setTargetStack(IAEStack)} instead.
     */
    @Deprecated
    public void setTargetStack(final IAEFluidStack stack) {
        this.setTargetStack((IAEStack<?>) stack);
    }

    /**
     * Get the current target stack of any type.
     */
    public IAEStack<?> getTargetGenericStack() {
        return this.clientRequestedTargetStack;
    }

    /**
     * Set the target stack (any type). On client side, sends {@link PacketTargetStack} to server.
     * Skips sending if the stack is unchanged.
     */
    public void setTargetStack(final IAEStack<?> stack) {
        if (Platform.isClient()) {
            if (stack == null && this.clientRequestedTargetStack == null) {
                return;
            }
            if (stack != null && this.clientRequestedTargetStack != null
                    && stack.isSameType(this.clientRequestedTargetStack)) {
                return;
            }

            NetworkHandler.instance()
                    .sendToServer(new appeng.core.sync.packets.PacketTargetStack(stack));
        }

        this.clientRequestedTargetStack = stack == null ? null : stack.copy();
    }

    public IActionSource getActionSource() {
        return this.mySrc;
    }

    public void verifyPermissions(final SecurityPermissions security, final boolean requirePower) {
        if (Platform.isClient()) {
            return;
        }

        this.ticksSinceCheck++;
        if (this.ticksSinceCheck < 20) {
            return;
        }

        this.ticksSinceCheck = 0;
        this.setValidContainer(this.isValidContainer() && this.hasAccess(security, requirePower));
    }

    protected boolean hasAccess(final SecurityPermissions perm, final boolean requirePower) {
        final IActionHost host = this.getActionHost();

        if (host != null) {
            final IGridNode gn = host.getActionableNode();
            if (gn != null) {
                final IGrid g = gn.getGrid();
                if (g != null) {
                    if (requirePower) {
                        final IEnergyGrid eg = g.getCache(IEnergyGrid.class);
                        if (!eg.isNetworkPowered()) {
                            return false;
                        }
                    }

                    final ISecurityGrid sg = g.getCache(ISecurityGrid.class);
                    return sg.hasPermission(this.getInventoryPlayer().player, perm);
                }
            }
        }

        return false;
    }

    public void lockPlayerInventorySlot(final int idx) {
        this.locked.add(idx);
    }

    public Object getTarget() {
        if (this.tileEntity != null) {
            return this.tileEntity;
        }
        if (this.part != null) {
            return this.part;
        }
        return this.obj;
    }

    public InventoryPlayer getPlayerInv() {
        return this.getInventoryPlayer();
    }

    public TileEntity getTileEntity() {
        return this.tileEntity;
    }

    public final void updateFullProgressBar(final int idx, final long value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
            return;
        }

        this.updateProgressBar(idx, (int) value);
    }

    public void stringSync(final int idx, final String value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update(value);
        }
    }

    protected void bindPlayerInventory(final InventoryPlayer inventoryPlayer, final int offsetX, final int offsetY) {
        IItemHandler ih = new PlayerInvWrapper(inventoryPlayer);

        // bind player inventory
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                if (this.locked.contains(j + i * 9 + 9)) {
                    this.addSlotToContainer(
                            new SlotDisabled(ih, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                } else {
                    this.addSlotToContainer(
                            new SlotPlayerInv(ih, j + i * 9 + 9, 8 + j * 18 + offsetX, offsetY + i * 18));
                }
            }
        }

        // bind player hotbar
        for (int i = 0; i < 9; i++) {
            if (this.locked.contains(i)) {
                this.addSlotToContainer(new SlotDisabled(ih, i, 8 + i * 18 + offsetX, 58 + offsetY));
            } else {
                this.addSlotToContainer(new SlotPlayerHotBar(ih, i, 8 + i * 18 + offsetX, 58 + offsetY));
            }
        }
    }

    @Override
    protected Slot addSlotToContainer(final Slot newSlot) {
        if (newSlot instanceof AppEngSlot) {
            final AppEngSlot s = (AppEngSlot) newSlot;
            s.setContainer(this);
            return super.addSlotToContainer(newSlot);
        } else {
            throw new IllegalArgumentException(
                    "Invalid Slot [" + newSlot + "] for AE Container instead of AppEngSlot.");
        }
    }

    @Override
    public void detectAndSendChanges() {
        this.sendCustomName();

        if (Platform.isServer()) {
            if (this.tileEntity != null
                    && this.tileEntity.getWorld().getTileEntity(this.tileEntity.getPos()) != this.tileEntity) {
                this.setValidContainer(false);
            }

            for (final IContainerListener listener : this.listeners) {
                for (final SyncData sd : this.syncData.values()) {
                    sd.tick(listener);
                }
            }
        }

        super.detectAndSendChanges();
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }

        final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!

        if (clickSlot instanceof SlotDisabled || clickSlot instanceof SlotInaccessible) {
            return ItemStack.EMPTY;
        }
        if (clickSlot != null && clickSlot.getHasStack()) {
            ItemStack tis = clickSlot.getStack();

            if (tis.isEmpty()) {
                return ItemStack.EMPTY;
            }

            IItemDefinition expansionCard = AEApi.instance().definitions().materials().cardPatternExpansion();
            ContainerPatternProvider casted;

            final List<Slot> selectedSlots = new ArrayList<>();

            /**
             * Gather a list of valid destinations.
             */
            if (clickSlot.isPlayerSide()) {
                tis = this.transferStackToContainer(tis);

                if (!tis.isEmpty()) {
                    if (this instanceof ContainerPatternProvider && expansionCard.isSameAs(tis)
                            && (casted = (ContainerPatternProvider) this).getPatternUpgrades() == casted.availableUpgrades()
                                    - 1) {
                        return ItemStack.EMPTY; // Don't insert more pattern expansions than maximum useful
                    }

                    // target slots in the container...
                    for (final Object inventorySlot : this.inventorySlots) {
                        final AppEngSlot cs = (AppEngSlot) inventorySlot;

                        if (!(cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                            if (cs.isItemValid(tis)) {
                                selectedSlots.add(cs);
                            }
                        }
                    }
                }
            } else {
                tis = tis.copy();

                // target slots in the container...
                for (final Object inventorySlot : this.inventorySlots) {
                    final AppEngSlot cs = (AppEngSlot) inventorySlot;

                    if ((cs.isPlayerSide()) && !(cs instanceof SlotFake) && !(cs instanceof SlotCraftingMatrix)) {
                        if (cs.isItemValid(tis)) {
                            selectedSlots.add(cs);
                        }
                    }
                }
            }

            /**
             * Handle Fake Slot Shift clicking.
             */
            if (selectedSlots.isEmpty() && clickSlot.isPlayerSide()) {
                if (!tis.isEmpty()) {
                    // target slots in the container...
                    for (final Object inventorySlot : this.inventorySlots) {
                        final AppEngSlot cs = (AppEngSlot) inventorySlot;
                        final ItemStack destination = cs.getStack();

                        if (!(cs.isPlayerSide()) && cs instanceof SlotFake) {
                            if (Platform.itemComparisons().isSameItem(destination, tis)) {
                                break;
                            } else if (destination.isEmpty()) {
                                cs.putStack(tis.copy());
                                this.updateSlot(cs);
                                break;
                            }
                        }
                    }
                }
            }

            if (!tis.isEmpty()) {
                // find partials..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled || d instanceof SlotME) {
                        continue;
                    }

                    if (d.isItemValid(tis)) {
                        if (d.getHasStack()) {
                            final ItemStack t = d.getStack().copy();

                            if (Platform.itemComparisons().isSameItem(tis, t)) // t.isItemEqual(tis))
                            {
                                if (d instanceof SlotRestrictedInput && ((SlotRestrictedInput) d)
                                        .getPlaceableItemType() == PlacableItemType.ENCODED_PATTERN) {
                                    return ItemStack.EMPTY; // don't insert duplicate encoded patterns to interfaces
                                }

                                int maxSize = Math.min(tis.getMaxStackSize(), d.getSlotStackLimit());

                                int placeAble = maxSize - t.getCount();

                                if (tis.getCount() < placeAble) {
                                    placeAble = tis.getCount();
                                }

                                t.setCount(t.getCount() + placeAble);
                                tis.setCount(tis.getCount() - placeAble);

                                d.putStack(t);

                                if (tis.getCount() <= 0) {
                                    clickSlot.putStack(ItemStack.EMPTY);
                                    d.onSlotChanged();

                                    this.updateSlot(clickSlot);
                                    this.updateSlot(d);
                                    return ItemStack.EMPTY;
                                } else {
                                    this.updateSlot(d);
                                }
                            }
                        }
                    }
                }

                // any match..
                for (final Slot d : selectedSlots) {
                    if (d instanceof SlotDisabled || d instanceof SlotME) {
                        continue;
                    }

                    if (d.isItemValid(tis)) {
                        if (!d.getHasStack()) {
                            int maxSize = Math.min(tis.getMaxStackSize(), d.getSlotStackLimit());

                            final ItemStack tmp = tis.copy();
                            if (tmp.getCount() > maxSize) {
                                tmp.setCount(maxSize);
                            }

                            tis.setCount(tis.getCount() - tmp.getCount());
                            d.putStack(tmp);

                            if (tis.getCount() <= 0) {
                                clickSlot.putStack(ItemStack.EMPTY);
                                d.onSlotChanged();

                                this.updateSlot(clickSlot);
                                this.updateSlot(d);
                                return ItemStack.EMPTY;
                            } else {
                                this.updateSlot(d);

                                if ((d instanceof SlotRestrictedInput && ((SlotRestrictedInput) d)
                                        .getPlaceableItemType() == PlacableItemType.ENCODED_PATTERN) ||
                                        (this instanceof ContainerPatternProvider && expansionCard.isSameAs(tis)
                                                && (casted = (ContainerPatternProvider) this)
                                                        .getPatternUpgrades() == casted.availableUpgrades() - 1)) {
                                    break; // Only insert one pattern when shift-clicking into interfaces, and don't
                                           // insert more pattern expansions than maximum useful
                                }
                            }
                        }
                    }
                }
            }

            clickSlot.putStack(!tis.isEmpty() ? tis : ItemStack.EMPTY);
        }

        this.updateSlot(clickSlot);
        return ItemStack.EMPTY;
    }

    @Override
    public final void updateProgressBar(final int idx, final int value) {
        if (this.syncData.containsKey(idx)) {
            this.syncData.get(idx).update((long) value);
        }
    }

    @Override
    public boolean canInteractWith(final EntityPlayer entityplayer) {
        if (this.isValidContainer()) {
            if (this.tileEntity instanceof IInventory) {
                return ((IInventory) this.tileEntity).isUsableByPlayer(entityplayer);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean canDragIntoSlot(final Slot s) {
        return ((AppEngSlot) s).isDraggable();
    }

    public void doAction(final EntityPlayerMP player, final InventoryAction action, final int slot, final long id) {
        if (slot >= 0 && slot < this.inventorySlots.size()) {
            final Slot s = this.getSlot(slot);

            if (s instanceof SlotCraftingTerm) {
                switch (action) {
                    case CRAFT_SHIFT:
                    case CRAFT_ITEM:
                    case CRAFT_STACK:
                        ((SlotCraftingTerm) s).doClick(action, player);
                        this.updateHeld(player);
                    default:
                }
            }

            if (s instanceof SlotFake) {
                final ItemStack hand = player.inventory.getItemStack();

                switch (action) {
                    case PICKUP_OR_SET_DOWN:
                        // 左键：直接放入物品本身（保持原有行为）
                        if (hand.isEmpty()) {
                            s.putStack(ItemStack.EMPTY);
                        } else {
                            s.putStack(hand.copy());
                        }
                        break;
                    case PLACE_SINGLE:
                        if (!hand.isEmpty()) {
                            final ItemStack is = hand.copy();
                            is.setCount(1);
                            s.putStack(is);
                        } else {
                            final ItemStack is = s.getStack().copy();
                            if (is.getCount() < is.getMaxStackSize() * 8)
                                is.grow(1);
                            s.putStack(is);
                        }
                        break;
                    case PICKUP_SINGLE:
                        if (hand.isEmpty()) {
                            final ItemStack is = s.getStack().copy();
                            if (is.getCount() > 1)
                                is.shrink(1);
                            s.putStack(is);
                        }
                        break;
                    case SPLIT_OR_PLACE_SINGLE:
                        ItemStack is = s.getStack();
                        if (!is.isEmpty()) {
                            if (hand.isEmpty()) {
                                is.setCount(Math.max(1, is.getCount() - 1));
                            } else if (hand.isItemEqual(is)) {
                                is.setCount(Math.min(is.getMaxStackSize(), is.getCount() + 1));
                            } else {
                                is = hand.copy();
                                is.setCount(1);
                            }
                            s.putStack(is);
                        } else if (!hand.isEmpty()) {
                            is = hand.copy();
                            is.setCount(1);
                            s.putStack(is);
                        }
                        break;
                    case PICKUP_FLUID_FROM_CONTAINER:
                        // Ctrl+左键：从流体容器提取流体放入 SlotFake
                        if (hand.isEmpty()) {
                            s.putStack(ItemStack.EMPTY);
                        } else {
                            ItemStack fluidDrop = tryConvertToFluidDrop(hand);
                            s.putStack(fluidDrop);
                        }
                        break;
                    case PLACE_SINGLE_FLUID_FROM_CONTAINER:
                        // Ctrl+右键：从流体容器提取流体，以 1000 mB 为单位放入/增减
                        if (!hand.isEmpty()) {
                            ItemStack fluidItem = tryConvertToFluidDrop(hand);
                            ItemStack existing = s.getStack();
                            if (!existing.isEmpty() && existing.isItemEqual(fluidItem)) {
                                existing.grow(1000);
                                s.putStack(existing);
                            } else {
                                fluidItem.setCount(1000);
                                s.putStack(fluidItem);
                            }
                        } else {
                            ItemStack existing = s.getStack();
                            if (!existing.isEmpty() && existing.getItem() instanceof FluidDummyItem) {
                                int newCount = existing.getCount() - 1000;
                                if (newCount <= 0) {
                                    s.putStack(ItemStack.EMPTY);
                                } else {
                                    existing.setCount(newCount);
                                    s.putStack(existing);
                                }
                            }
                        }
                        break;
                    case PICKUP_ALL_FLUID_FROM_CONTAINER:
                        // Ctrl+Shift+左键：提取流体，数量设为 Integer.MAX_VALUE
                        if (hand.isEmpty()) {
                            s.putStack(ItemStack.EMPTY);
                        } else {
                            ItemStack maxFluid = tryConvertToFluidDrop(hand);
                            if (maxFluid.getItem() instanceof FluidDummyItem) {
                                maxFluid.setCount(Integer.MAX_VALUE);
                            }
                            s.putStack(maxFluid);
                        }
                        break;
                    case HALVE:
                        if (s.getStack().getCount() > 1) {
                            ItemStack halved = s.getStack().copy();
                            halved.setCount(s.getStack().getCount() / 2);
                            s.putStack(halved);
                        }
                        break;
                    case DOUBLE:
                        ItemStack doubled = s.getStack().copy();
                        if (s.getStack().getCount() * 2 > 0) {
                            doubled.setCount(Math.min(s.getSlotStackLimit(), s.getStack().getCount() * 2));
                            s.putStack(doubled);
                        }
                        break;
                    case CREATIVE_DUPLICATE:
                    case MOVE_REGION:
                    case SHIFT_CLICK:
                    default:
                        break;
                }
            }

            if (action == InventoryAction.MOVE_REGION) {
                final List<Slot> from = new ArrayList<>();

                for (final Slot j : this.inventorySlots) {
                    if (j != null && j.getClass() == s.getClass() && !(j instanceof SlotCraftingTerm)) {
                        from.add(j);
                    }
                }

                for (final Slot fr : from) {
                    this.transferStackInSlot(player, fr.slotNumber);
                }
            }

            return;
        }

        // get target stack.
        final IAEItemStack slotItem = this.getTargetStack();
        final IAEFluidStack slotFluid = this.getTargetFluidStack();

        switch (action) {
            case SHIFT_CLICK:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItem != null) {
                    IAEItemStack ais = slotItem.copy();
                    ItemStack myItem = ais.createItemStack();

                    ais.setStackSize(myItem.getMaxStackSize());

                    final InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                    myItem.setCount((int) ais.getStackSize());
                    myItem = adp.simulateAdd(myItem);

                    if (!myItem.isEmpty()) {
                        ais.setStackSize(ais.getStackSize() - myItem.getCount());
                    }

                    ais = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais,
                            this.getActionSource());
                    if (ais != null) {
                        adp.addItems(ais.createItemStack());
                    }
                }
                break;
            case ROLL_DOWN:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                final int releaseQty = 1;
                final ItemStack isg = player.inventory.getItemStack();

                if (!isg.isEmpty() && releaseQty > 0) {
                    IAEItemStack ais = AEItemStackType.INSTANCE
                            .createStack(isg);
                    ais.setStackSize(1);
                    final IAEItemStack extracted = ais.copy();

                    ais = appeng.util.StorageHelper.poweredInsert(this.getPowerSource(), this.getCellInventory(), ais,
                            this.getActionSource());
                    if (ais == null) {
                        final InventoryAdaptor ia = new AdaptorItemHandler(
                                new WrapperCursorItemHandler(player.inventory));

                        final ItemStack fail = ia.removeItems(1, extracted.getDefinition(), null);
                        if (fail.isEmpty()) {
                            this.getCellInventory().extractItems(extracted, Actionable.MODULATE,
                                    this.getActionSource());
                        }

                        this.updateHeld(player);
                    }
                }

                break;
            case ROLL_UP:
            case PICKUP_SINGLE:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItem != null) {
                    int liftQty = 1;
                    final ItemStack item = player.inventory.getItemStack();

                    if (!item.isEmpty()) {
                        if (item.getCount() >= item.getMaxStackSize()) {
                            liftQty = 0;
                        }
                        if (!Platform.itemComparisons().isSameItem(slotItem.getDefinition(), item)) {
                            liftQty = 0;
                        }
                    }

                    if (liftQty > 0) {
                        IAEItemStack ais = slotItem.copy();
                        ais.setStackSize(1);
                        ais = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais,
                                this.getActionSource());
                        if (ais != null) {
                            final InventoryAdaptor ia = new AdaptorItemHandler(
                                    new WrapperCursorItemHandler(player.inventory));

                            final ItemStack fail = ia.addItems(ais.createItemStack());
                            if (!fail.isEmpty()) {
                                this.getCellInventory().injectItems(ais, Actionable.MODULATE, this.getActionSource());
                            }

                            this.updateHeld(player);
                        }
                    }
                }
                break;
            case PICKUP_OR_SET_DOWN:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (player.inventory.getItemStack().isEmpty()) {
                    if (slotItem != null) {
                        IAEItemStack ais = slotItem.copy();
                        ais.setStackSize(ais.getDefinition().getMaxStackSize());
                        ais = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais,
                                this.getActionSource());
                        if (ais != null) {
                            player.inventory.setItemStack(ais.createItemStack());
                        } else {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                } else {
                    IAEItemStack ais = AEItemStackType.INSTANCE
                            .createStack(player.inventory.getItemStack());
                    ais = appeng.util.StorageHelper.poweredInsert(this.getPowerSource(), this.getCellInventory(), ais,
                            this.getActionSource());
                    if (ais != null) {
                        player.inventory.setItemStack(ais.createItemStack());
                    } else {
                        player.inventory.setItemStack(ItemStack.EMPTY);
                    }
                    this.updateHeld(player);
                }

                break;
            case SPLIT_OR_PLACE_SINGLE:
                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (player.inventory.getItemStack().isEmpty()) {
                    if (slotItem != null) {
                        IAEItemStack ais = slotItem.copy();
                        final long maxSize = ais.getDefinition().getMaxStackSize();
                        ais.setStackSize(maxSize);
                        ais = this.getCellInventory().extractItems(ais, Actionable.SIMULATE, this.getActionSource());

                        if (ais != null) {
                            final long stackSize = Math.min(maxSize, ais.getStackSize());
                            ais.setStackSize((stackSize + 1) >> 1);
                            ais = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais,
                                    this.getActionSource());
                        }

                        if (ais != null) {
                            player.inventory.setItemStack(ais.createItemStack());
                        } else {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                } else {
                    IAEItemStack ais = AEItemStackType.INSTANCE
                            .createStack(player.inventory.getItemStack());
                    ais.setStackSize(1);
                    ais = appeng.util.StorageHelper.poweredInsert(this.getPowerSource(), this.getCellInventory(), ais,
                            this.getActionSource());
                    if (ais == null) {
                        final ItemStack is = player.inventory.getItemStack();
                        is.setCount(is.getCount() - 1);
                        if (is.getCount() <= 0) {
                            player.inventory.setItemStack(ItemStack.EMPTY);
                        }
                        this.updateHeld(player);
                    }
                }

                break;
            case CREATIVE_DUPLICATE:
                if (player.capabilities.isCreativeMode && slotItem != null) {
                    final ItemStack is = slotItem.createItemStack();
                    is.setCount(is.getMaxStackSize());
                    player.inventory.setItemStack(is);
                    this.updateHeld(player);
                }
                break;
            case MOVE_REGION:

                if (this.getPowerSource() == null || this.getCellInventory() == null) {
                    return;
                }

                if (slotItem != null) {
                    final int playerInv = 9 * 4;
                    for (int slotNum = 0; slotNum < playerInv; slotNum++) {
                        IAEItemStack ais = slotItem.copy();
                        ItemStack myItem = ais.createItemStack();

                        ais.setStackSize(myItem.getMaxStackSize());

                        final InventoryAdaptor adp = InventoryAdaptor.getAdaptor(player);
                        myItem.setCount((int) ais.getStackSize());
                        myItem = adp.simulateAdd(myItem);

                        if (!myItem.isEmpty()) {
                            ais.setStackSize(ais.getStackSize() - myItem.getCount());
                        }

                        ais = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(), ais,
                                this.getActionSource());
                        if (ais != null) {
                            adp.addItems(ais.createItemStack());
                        } else {
                            return;
                        }
                    }
                }

                break;
            case FILL_SINGLE_CONTAINER:
            case FILL_CONTAINERS:
            case DRAIN_SINGLE_CONTAINER:
            case DRAIN_CONTAINERS:
            case CONTAINER_QUICK_TRANSFER:
                // 流体容器交互操作（预留，需在 ContainerMEMonitorable 等子类中实现）
                break;
            case FILL_ITEM:
                if (this.getPowerSource() == null || this.getFluidCellInventory() == null || slotFluid == null) {
                    return;
                }

                this.handleFillFluidContainer(player, slotFluid);
                break;
            case EMPTY_ITEM:
                if (this.getPowerSource() == null || this.getFluidCellInventory() == null) {
                    return;
                }

                this.handleEmptyFluidContainer(player);
                break;
            case SET_ITEM_PIN:
            case SET_CONTAINER_PIN:
            case UNSET_PIN:
                if (this instanceof appeng.container.implementations.ContainerMEMonitorable monContainer) {
                    monContainer.handlePinAction(action, this.getTargetGenericStack());
                }
                break;
            default:
                break;
        }
    }

    private void handleFillFluidContainer(final EntityPlayerMP player, final IAEFluidStack slotFluid) {
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        final int heldAmount = held.getCount();

        for (int i = 0; i < heldAmount; i++) {
            final ItemStack copiedFluidContainer = held.copy();
            copiedFluidContainer.setCount(1);

            // Simulate: see how much the container can accept
            final IAEFluidStack fillRequest = slotFluid.copy();
            fillRequest.setStackSize(Integer.MAX_VALUE);
            final ContainerInteractionResult<IAEFluidStack> simFill =
                    AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, fillRequest, true);
            if (!simFill.isSuccess()) {
                return;
            }

            final IAEFluidStack canPull = appeng.util.StorageHelper.poweredExtraction(
                    this.getPowerSource(),
                    this.getFluidCellInventory(),
                    slotFluid.copy().setStackSize(simFill.getTransferred().getStackSize()),
                    this.getActionSource(),
                    Actionable.SIMULATE);
            if (canPull == null || canPull.getStackSize() < 1) {
                return;
            }

            // Re-simulate with the actual available amount
            final ContainerInteractionResult<IAEFluidStack> simFill2 =
                    AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, canPull, true);
            if (!simFill2.isSuccess()) {
                return;
            }

            final IAEFluidStack pulled = appeng.util.StorageHelper.poweredExtraction(
                    this.getPowerSource(),
                    this.getFluidCellInventory(),
                    slotFluid.copy().setStackSize(simFill2.getTransferred().getStackSize()),
                    this.getActionSource());
            if (pulled == null || pulled.getStackSize() < 1) {
                AELog.error("Unable to pull fluid out of the ME system even though the simulation said yes");
                return;
            }

            // Actually fill
            final ContainerInteractionResult<IAEFluidStack> actualFill =
                    AEFluidStackType.INSTANCE.fillToContainer(copiedFluidContainer, pulled, false);
            if (!actualFill.isSuccess()) {
                AELog.error("Fluid item [%s] reported a different possible amount than it actually accepted.",
                        held.getDisplayName());
            }

            if (held.getCount() == 1) {
                player.inventory.setItemStack(actualFill.getResultContainer());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(actualFill.getResultContainer())) {
                    player.dropItem(actualFill.getResultContainer(), false);
                }
            }
        }

        this.updateHeld(player);
    }

    private void handleEmptyFluidContainer(final EntityPlayerMP player) {
        final ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        final int heldAmount = held.getCount();

        for (int i = 0; i < heldAmount; i++) {
            final ItemStack copiedFluidContainer = held.copy();
            copiedFluidContainer.setCount(1);

            // Simulate drain
            final ContainerInteractionResult<IAEFluidStack> simDrain =
                    AEFluidStackType.INSTANCE.drainFromContainer(copiedFluidContainer, Integer.MAX_VALUE, true);
            if (!simDrain.isSuccess()) {
                return;
            }

            // Simulate insert into ME
            final IAEFluidStack notStorable = appeng.util.StorageHelper.poweredInsert(
                    this.getPowerSource(),
                    this.getFluidCellInventory(),
                    simDrain.getTransferred(),
                    this.getActionSource(),
                    Actionable.SIMULATE);

            long toDrain = simDrain.getTransferred().getStackSize();
            if (notStorable != null && notStorable.getStackSize() > 0) {
                toDrain -= notStorable.getStackSize();
                if (toDrain <= 0) {
                    return;
                }
            }

            // Actually drain
            final ContainerInteractionResult<IAEFluidStack> actualDrain =
                    AEFluidStackType.INSTANCE.drainFromContainer(copiedFluidContainer, toDrain, false);
            if (!actualDrain.isSuccess()) {
                return;
            }

            // Insert into ME
            final IAEFluidStack notInserted = appeng.util.StorageHelper.poweredInsert(
                    this.getPowerSource(),
                    this.getFluidCellInventory(),
                    actualDrain.getTransferred(),
                    this.getActionSource());

            if (notInserted != null && notInserted.getStackSize() > 0) {
                final IAEFluidStack spill = this.getFluidCellInventory()
                        .injectItems(notInserted, Actionable.MODULATE, this.getActionSource());
                if (spill != null && spill.getStackSize() > 0) {
                    // Attempt to put spilled fluid back into the container
                    AEFluidStackType.INSTANCE.fillToContainer(
                            actualDrain.getResultContainer(), spill, false);
                }
            }

            if (held.getCount() == 1) {
                player.inventory.setItemStack(actualDrain.getResultContainer());
            } else {
                player.inventory.getItemStack().shrink(1);
                if (!player.inventory.addItemStackToInventory(actualDrain.getResultContainer())) {
                    player.dropItem(actualDrain.getResultContainer(), false);
                }
            }
        }

        this.updateHeld(player);
    }

    protected void updateHeld(final EntityPlayerMP p) {
        if (Platform.isServer()) {
            try {
                NetworkHandler.instance()
                        .sendTo(
                                new PacketInventoryAction(InventoryAction.UPDATE_HAND, 0,
                                        AEItemStack.fromItemStack(p.inventory.getItemStack())),
                                p);
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    protected ItemStack transferStackToContainer(final ItemStack input) {
        return this.shiftStoreItem(input);
    }

    private ItemStack shiftStoreItem(final ItemStack input) {
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            return input;
        }
        final IAEItemStack ais = appeng.util.StorageHelper.poweredInsert(this.getPowerSource(), this.getCellInventory(),
                AEItemStackType.INSTANCE.createStack(input),
                this.getActionSource());
        if (ais == null) {
            return ItemStack.EMPTY;
        }
        return ais.createItemStack();
    }

    private void updateSlot(final Slot clickSlot) {
        // ???
        this.detectAndSendChanges();
    }

    private void sendCustomName() {
        if (!this.sentCustomName) {
            this.sentCustomName = true;
            if (Platform.isServer()) {
                ICustomNameObject name = null;

                if (this.part instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.part;
                }

                if (this.tileEntity instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.tileEntity;
                }

                if (this.obj instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this.obj;
                }

                if (this instanceof ICustomNameObject) {
                    name = (ICustomNameObject) this;
                }

                if (name != null) {
                    if (name.hasCustomInventoryName()) {
                        this.setCustomName(name.getCustomInventoryName());
                    }

                    if (this.getCustomName() != null) {
                        try {
                            NetworkHandler.instance()
                                    .sendTo(new PacketValueConfig("CustomName", this.getCustomName()),
                                            (EntityPlayerMP) this.getInventoryPlayer().player);
                        } catch (final IOException e) {
                            AELog.debug(e);
                        }
                    }
                }
            }
        }
    }

    public void swapSlotContents(final int slotA, final int slotB) {
        final Slot a = this.getSlot(slotA);
        final Slot b = this.getSlot(slotB);

        // NPE protection...
        if (a == null || b == null) {
            return;
        }

        final ItemStack isA = a.getStack();
        final ItemStack isB = b.getStack();

        // something to do?
        if (isA.isEmpty() && isB.isEmpty()) {
            return;
        }

        // can take?

        if (!isA.isEmpty() && !a.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        if (!isB.isEmpty() && !b.canTakeStack(this.getInventoryPlayer().player)) {
            return;
        }

        // swap valid?

        if (!isB.isEmpty() && !a.isItemValid(isB)) {
            return;
        }

        if (!isA.isEmpty() && !b.isItemValid(isA)) {
            return;
        }

        ItemStack testA = isB.isEmpty() ? ItemStack.EMPTY : isB.copy();
        ItemStack testB = isA.isEmpty() ? ItemStack.EMPTY : isA.copy();

        // can put some back?
        if (!testA.isEmpty() && testA.getCount() > a.getSlotStackLimit()) {
            if (!testB.isEmpty()) {
                return;
            }

            final int totalA = testA.getCount();
            testA.setCount(a.getSlotStackLimit());
            testB = testA.copy();

            testB.setCount(totalA - testA.getCount());
        }

        if (!testB.isEmpty() && testB.getCount() > b.getSlotStackLimit()) {
            if (!testA.isEmpty()) {
                return;
            }

            final int totalB = testB.getCount();
            testB.setCount(b.getSlotStackLimit());
            testA = testB.copy();

            testA.setCount(totalB - testA.getCount());
        }

        a.putStack(testA);
        b.putStack(testB);
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @NotNull EntityPlayer player) {
        if (slotId >= 0 && clickTypeIn == ClickType.PICKUP) {
            final var slot = this.getSlot(slotId);
            if (slot instanceof AppEngSlot appEngSlot) {
                var slotStack = slot.getStack();
                var draggedStack = this.invPlayer.getItemStack();

                // The default vanilla behavior assumes that slots can't hold more items than the default stack size.
                // Thus, it's possible to underflow the vanilla code when clicking non-empty slots with an item stack.
                if (!draggedStack.isEmpty()) {
                    if (appEngSlot.isItemValid(draggedStack)) {
                        if (slotStack.getItem() == draggedStack.getItem()
                                && slotStack.getMetadata() == draggedStack.getMetadata()
                                && ItemStack.areItemStackTagsEqual(slotStack, draggedStack)) {
                            // Slot size or stack size, whichever is smaller.
                            var maxSize = Math.min(appEngSlot.getSlotStackLimit(), draggedStack.getMaxStackSize());

                            // The maximum number of items that can be inserted into the slot, non-negative.
                            var maxInsertable = Math.min(draggedStack.getCount(),
                                    Math.max(0, maxSize - appEngSlot.getStack().getCount()));

                            if (maxInsertable != 0) {
                                var toInsert = Math.min(maxInsertable, dragType == 0 ? maxInsertable : 1);

                                draggedStack.shrink(toInsert);
                                slotStack.grow(toInsert);

                                slot.putStack(slot.getStack());
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
                // Fixes taking and halving issues from oversized slots.
                else if (dragType == 0 || dragType == 1) {
                    if (slot.canTakeStack(player) && !slotStack.isEmpty()) {
                        var result = slotStack.copy();
                        var toTake = Math.min(slotStack.getCount(), slotStack.getMaxStackSize());
                        this.invPlayer.setItemStack(slot.decrStackSize(dragType == 0 ? toTake : (toTake + 1) / 2));

                        slot.putStack(slot.getStack());
                        return result;
                    }
                }

            }
        }
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    public void onUpdate(final String field, final Object oldValue, final Object newValue) {

    }

    public void onSlotChange(final Slot s) {

    }

    public boolean isValidForSlot(final Slot s, final ItemStack i) {
        return true;
    }

    public IMEInventoryHandler<IAEItemStack> getCellInventory() {
        return this.cellInv;
    }

    public void setCellInventory(final IMEInventoryHandler<IAEItemStack> cellInv) {
        this.cellInv = cellInv;
    }

    public IMEInventoryHandler<IAEFluidStack> getFluidCellInventory() {
        return this.fluidCellInv;
    }

    public void setFluidCellInventory(final IMEInventoryHandler<IAEFluidStack> fluidCellInv) {
        this.fluidCellInv = fluidCellInv;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(final String customName) {
        this.customName = customName;
    }

    public InventoryPlayer getInventoryPlayer() {
        return this.invPlayer;
    }

    public boolean isValidContainer() {
        return this.isContainerValid;
    }

    public void setValidContainer(final boolean isContainerValid) {
        this.isContainerValid = isContainerValid;
    }

    public ContainerOpenContext getOpenContext() {
        return this.openContext;
    }

    public void setOpenContext(final ContainerOpenContext openContext) {
        this.openContext = openContext;
    }

    public IEnergySource getPowerSource() {
        return this.powerSrc;
    }

    public void setPowerSource(final IEnergySource powerSrc) {
        this.powerSrc = powerSrc;
    }

    /**
     * 尝试将流体容器（桶等）转换为 FluidDummyItem 占位物品。
     * 如果物品不是流体容器，则返回原物品的副本。
     */
    protected static ItemStack tryConvertToFluidDrop(ItemStack hand) {
        if (hand.isEmpty()) {
            return ItemStack.EMPTY;
        }
        FluidStack fluid = FluidUtil.getFluidContained(hand);
        if (fluid != null && fluid.amount > 0) {
            AEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
            if (aeFluid != null) {
                return aeFluid.asItemStackRepresentation();
            }
        }
        return hand.copy();
    }

    // ---- 虚拟槽位同步机制（服务端 → 客户端）----

    private final java.util.EnumSet<StorageName> fullSyncPending =
            java.util.EnumSet.allOf(StorageName.class);

    /**
     * 在服务端 detectAndSendChanges 中调用，将 {@link appeng.tile.inventory.IAEStackInventory}
     * 的变更增量推送到所有已连接的客户端。
     * <p>
     * 首次调用时会进行全量同步；后续只同步发生变更的槽位。
     *
     * @param invName           库存名称标识
     * @param inventory         服务端的 IAEStackInventory
     * @param clientSlotsStacks 与客户端同步的快照数组（用于增量比较）
     */
    protected void updateVirtualSlots(StorageName invName,
            appeng.tile.inventory.IAEStackInventory inventory,
            IAEStack<?>[] clientSlotsStacks) {
        final boolean needsFull = fullSyncPending.remove(invName);
        var list = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<IAEStack<?>>();
        for (int i = 0; i < inventory.getSizeInventory(); ++i) {
            IAEStack<?> aes = inventory.getAEStackInSlot(i);
            IAEStack<?> aesClient = clientSlotsStacks[i];

            if (needsFull || !appeng.util.AEStackSerialization.isStacksIdentical(aes, aesClient)) {
                list.put(i, aes);
                clientSlotsStacks[i] = aes != null ? aes.copy() : null;
            }
        }

        if (!list.isEmpty()) {
            for (final IContainerListener listener : this.listeners) {
                if (listener instanceof EntityPlayerMP) {
                    final EntityPlayerMP emp = (EntityPlayerMP) listener;
                    try {
                        NetworkHandler.instance()
                                .sendTo(new appeng.core.sync.packets.PacketVirtualSlot(invName, list), emp);
                    } catch (final Exception e) {
                        AELog.debug(e);
                    }
                }
            }
        }
    }
}

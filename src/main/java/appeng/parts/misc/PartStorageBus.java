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

package appeng.parts.misc;

import java.util.Objects;

import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import appeng.api.AEApi;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.capabilities.Capabilities;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.tile.misc.TileMEInterface;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStackType;

public class PartStorageBus extends AbstractPartStorageBus<IAEItemStack>
        implements IIAEStackInventory {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/storage_bus_has_channel"));

    @CapabilityInject(IItemRepository.class)
    public static Capability<IItemRepository> ITEM_REPOSITORY_CAPABILITY = null;

    protected final IAEStackInventory Config = new IAEStackInventory(this, 63, StorageName.CONFIG);

    @Reflected
    public PartStorageBus(final ItemStack is) {
        super(is);
        this.getConfigManager().registerSetting(Settings.STICKY_MODE, YesNo.NO);
    }

    // ---- AbstractPartStorageBus abstract method implementations ----

    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return AEItemStackType.INSTANCE;
    }

    @Override
    protected TickRates getTickRates() {
        return TickRates.StorageBus;
    }

    @Override
    public AEGuiKey getGuiKey() {
        return AEGuiKeys.STORAGE_BUS;
    }

    @Override
    protected IMEInventory<IAEItemStack> getInventoryWrapper(TileEntity target) {
        EnumFacing targetSide = this.getSide().getFacing().getOpposite();

        // Prioritize a handler to directly link to another ME network
        IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR,
                targetSide);

        if (accessor != null) {
            IStorageMonitorable inventory = accessor.getInventory(this.mySrc);
            if (inventory != null) {
                return inventory.getInventory(AEItemStackType.INSTANCE);
            }

            // So this could / can be a design decision. If the tile does support our custom capability,
            // but it does not return an inventory for the action source, we do NOT fall back to using
            // IItemHandler's, as that might circumvent the security setings, and might also cause
            // performance issues.
            return null;
        }

        // Check via cap for IItemRepository
        if (ITEM_REPOSITORY_CAPABILITY != null && target.hasCapability(ITEM_REPOSITORY_CAPABILITY, targetSide)) {
            IItemRepository handlerRepo = target.getCapability(ITEM_REPOSITORY_CAPABILITY, targetSide);
            if (handlerRepo != null) {
                return new ItemRepositoryAdapter(handlerRepo, this);
            }
        }
        // Check via cap for IItemHandler
        IItemHandler handlerExt = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, targetSide);
        if (handlerExt != null) {
            return new ItemHandlerAdapter(handlerExt, this);
        }

        return null;
    }

    @Override
    protected int createHandlerHash(TileEntity target) {
        if (target == null) {
            return 0;
        }

        final EnumFacing targetSide = this.getSide().getFacing().getOpposite();

        if (target.hasCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide)) {
            IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR,
                    targetSide);
            if (accessor != null) {
                IStorageMonitorable inventory = accessor.getInventory(this.mySrc);
                if (inventory != null) {
                    return Objects.hash(target, inventory
                            .getInventory(AEItemStackType.INSTANCE));
                }
            }
            return Objects.hash(target, target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide));
        }

        final IItemHandler itemHandler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY,
                targetSide);

        if (itemHandler != null) {
            return Objects.hash(target, itemHandler, itemHandler.getSlots());
        }

        return 0;
    }

    @Override
    protected boolean isRelevantInterfaceTile(TileEntity te) {
        return te instanceof TileMEInterface;
    }

    @Override
    protected boolean isRelevantInterfacePart(appeng.api.parts.IPart part) {
        return part instanceof PartMEInterface;
    }

    @Override
    protected IItemList<IAEItemStack> buildPriorityList(int slotsToUse) {
        final IItemList<IAEItemStack> priorityList = AEItemStackType.INSTANCE.createList();
        for (int x = 0; x < this.Config.getSizeInventory() && x < slotsToUse; x++) {
            final IAEStack<?> stack = this.Config.getAEStackInSlot(x);
            if (stack instanceof IAEItemStack) {
                priorityList.add((IAEItemStack) stack);
            }
        }
        return priorityList;
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().storageBus().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void readConfigFromNBT(NBTTagCompound data) {
        this.Config.readFromNBT(data, "config");
    }

    @Override
    protected void writeConfigToNBT(NBTTagCompound data) {
        this.Config.writeToNBT(data, "config");
    }

    // ---- Item-specific behavior ----

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        super.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return super.getInventoryByName(name);
    }

    // ---- IIAEStackInventory implementation ----

    @Override
    public void saveAEStackInv() {
        this.resetCache(true);
        this.getHost().markForSave();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.Config;
        }
        return null;
    }

    // ---- Model ----

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }
}

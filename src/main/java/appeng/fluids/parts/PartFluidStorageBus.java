/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2018, AlgorithmX2, All rights reserved.
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

package appeng.fluids.parts;

import java.util.Objects;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

import appeng.api.AEApi;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.IStorageMonitorableAccessor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.capabilities.Capabilities;
import appeng.core.AppEng;
import appeng.core.settings.TickRates;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.fluids.helper.IConfigurableAEStackInventory;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.tile.misc.TileMEInterface;
import appeng.fluids.util.AEFluidStackType;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.misc.PartMEInterface;
import appeng.api.storage.StorageName;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.parts.misc.AbstractPartStorageBus;

/**
 * @author BrockWS
 * @version rv6 - 22/05/2018
 * @since rv6 22/05/2018
 */
public class PartFluidStorageBus extends AbstractPartStorageBus<IAEFluidStack>
        implements IIAEStackInventory, IConfigurableFluidInventory, IConfigurableAEStackInventory {

    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID,
            "part/fluid_storage_bus_base");
    @PartModels
    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_off"));
    @PartModels
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_on"));
    @PartModels
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/fluid_storage_bus_has_channel"));

    private final IAEStackInventory config = new IAEStackInventory(this, 63, StorageName.CONFIG);

    public PartFluidStorageBus(ItemStack is) {
        super(is);
    }

    // ---- AbstractPartStorageBus abstract method implementations ----

    @Override
    protected IAEStackType<IAEFluidStack> getStackType() {
        return AEFluidStackType.INSTANCE;
    }

    @Override
    protected TickRates getTickRates() {
        return TickRates.FluidStorageBus;
    }

    @Override
    public AEGuiKey getGuiKey() {
        return AEGuiKeys.STORAGE_BUS_FLUID;
    }

    @Override
    protected IMEInventory<IAEFluidStack> getInventoryWrapper(TileEntity target) {
        EnumFacing targetSide = this.getSide().getFacing().getOpposite();
        // Prioritize a handler to directly link to another ME network
        IStorageMonitorableAccessor accessor = target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR,
                targetSide);
        if (accessor != null) {
            IStorageMonitorable inventory = accessor.getInventory(this.mySrc);
            if (inventory != null) {
                return inventory.getInventory(AEFluidStackType.INSTANCE);
            }

            // So this could / can be a design decision. If the tile does support our custom capability,
            // but it does not return an inventory for the action source, we do NOT fall back to using
            // IItemHandler's, as that might circumvent the security setings, and might also cause
            // performance issues.
            return null;
        }

        // Check via cap for IFluidHandler
        IFluidHandler handlerExt = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, targetSide);
        if (handlerExt != null) {
            return new FluidHandlerAdapter(handlerExt, this);
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
            return Objects.hash(target, target.getCapability(Capabilities.STORAGE_MONITORABLE_ACCESSOR, targetSide));
        }

        final IFluidHandler fluidHandler = target.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY,
                targetSide);

        if (fluidHandler != null) {
            return Objects.hash(target, fluidHandler, fluidHandler.getTankProperties().length);
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
    protected IItemList<IAEFluidStack> buildPriorityList(int slotsToUse) {
        final IItemList<IAEFluidStack> priorityList = AEFluidStackType.INSTANCE.createList();
        for (int x = 0; x < this.config.size() && x < slotsToUse; x++) {
            final IAEStack<?> is = this.config.getAEStackInSlot(x);
            if (is instanceof IAEFluidStack fluidStack) {
                priorityList.add(fluidStack);
            }
        }
        return priorityList;
    }

    @Override
    public ItemStack getItemStackRepresentation() {
        return AEApi.instance().definitions().parts().fluidStorageBus().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void readConfigFromNBT(NBTTagCompound data) {
        this.config.readFromNBT(data, "config");
    }

    @Override
    protected void writeConfigToNBT(NBTTagCompound data) {
        this.config.writeToNBT(data, "config");
    }

    // ---- Fluid-specific behavior ----

    @Override
    public void saveAEStackInv() {
        this.resetCache(true);
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        return null;
    }

    @Override
    public IAEStackInventory getAEStackInventoryByName(final String name) {
        if ("config".equals(name)) {
            return this.config;
        }
        return null;
    }

    public IAEStackInventory getConfig() {
        return this.config;
    }

    // ---- Model ----

    @Nonnull
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

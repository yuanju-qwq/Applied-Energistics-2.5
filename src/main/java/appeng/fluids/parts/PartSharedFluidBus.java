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

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.Vec3d;

import appeng.api.parts.IPartCollisionHelper;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.core.sync.AEGuiKeys;
import appeng.fluids.helper.IConfigurableFluidInventory;
import appeng.fluids.util.AEFluidStackType;
import appeng.parts.automation.AbstractPartIOBus;
import appeng.util.Platform;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Shared base class for fluid I/O buses (Fluid Import Bus and Fluid Export Bus).
 * <p>
 * Extends {@link AbstractPartIOBus} with fluid-specific functionality:
 * <ul>
 *   <li>Fluid handler access via {@link #getConnectedTE()}</li>
 *   <li>Fluid-based transfer amount using {@link AEFluidStackType#transferFactor()}</li>
 * </ul>
 * <p>
 * Now inherits full redstone pulse mode support from {@link AbstractPartIOBus}.
 *
 * @author BrockWS
 * @version rv6 - 30/04/2018
 * @since rv6 30/04/2018
 */
public abstract class PartSharedFluidBus extends AbstractPartIOBus
        implements IConfigurableFluidInventory {

    public PartSharedFluidBus(ItemStack is) {
        super(is);
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(player, this.getHost().getTile(), this.getSide(), AEGuiKeys.BUS_FLUID);
        }

        return true;
    }

    @Override
    public void getBoxes(IPartCollisionHelper bch) {
        bch.addBox(6, 6, 11, 10, 10, 13);
        bch.addBox(5, 5, 13, 11, 11, 14);
        bch.addBox(4, 4, 14, 12, 12, 16);
    }

    @Override
    public IFluidHandler getFluidInventoryByName(final String name) {
        // Config is now IAEStackInventory, not IFluidHandler
        return null;
    }

    protected IAEStackType<IAEFluidStack> getStackType() {
        return AEFluidStackType.INSTANCE;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    /**
     * Calculate the amount of fluid (in mB) to transfer based on Speed upgrades.
     */
    protected int calculateFluidAmountToSend() {
        return this.calculateAmountToSend((int) this.getStackType().transferFactor());
    }
}

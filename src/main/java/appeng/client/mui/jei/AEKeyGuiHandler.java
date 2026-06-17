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

package appeng.client.mui.jei;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.api.stacks.GenericStack;
import appeng.client.mui.core.AEKeyMUIScreenFactory;
import appeng.client.mui.key.slot.AEKeyVirtualSlot;

import com.cleanroommc.modularui.screen.GuiScreenWrapper;

/**
 * AEKey-only JEI ghost ingredient handler for new MUI screens.
 */
@SideOnly(Side.CLIENT)
public final class AEKeyGuiHandler implements IGhostIngredientHandler<GuiScreenWrapper> {

    public Class<GuiScreenWrapper> getGuiScreenClass() {
        return GuiScreenWrapper.class;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I> List<Target<I>> getTargets(@NotNull final GuiScreenWrapper gui, @NotNull final I ingredient,
            final boolean doStart) {
        final IAEKeyGuiPanel panel = AEKeyMUIScreenFactory.getPanel(gui);
        final GenericStack stack = toGenericStack(ingredient);
        if (panel == null || stack == null) {
            return Collections.emptyList();
        }

        final List<Target<I>> targets = new ArrayList<>();
        for (final AEKeyVirtualSlot slot : panel.getVirtualSlots()) {
            if (!slot.isVisible()
                    || !slot.isEnabled()
                    || !slot.accepts(stack.what().getType(), -1)) {
                continue;
            }

            targets.add((Target<I>) new Target<GenericStack>() {
                @Override
                public @NotNull Rectangle getArea() {
                    return new Rectangle(
                            panel.getGuiLeft() + slot.getX(),
                            panel.getGuiTop() + slot.getY(),
                            slot.getWidth(),
                            slot.getHeight());
                }

                @Override
                public void accept(@NotNull final GenericStack ignored) {
                    slot.setStack(stack);
                }
            });
        }

        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }

    @Nullable
    private static GenericStack toGenericStack(final Object ingredient) {
        if (ingredient instanceof ItemStack itemStack) {
            return GenericStack.fromItemStack(itemStack);
        }

        if (ingredient instanceof FluidStack fluidStack) {
            return GenericStack.fromFluidStack(fluidStack);
        }

        return null;
    }
}

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
import java.util.List;

import javax.annotation.Nullable;

import appeng.api.stacks.GenericStack;
import appeng.client.mui.key.slot.AEKeyVirtualSlot;

/**
 * AEKey-only GUI surface for new MUI panels and JEI/HEI integration.
 */
public interface IAEKeyGuiPanel {

    int getGuiLeft();

    int getGuiTop();

    List<AEKeyVirtualSlot> getVirtualSlots();

    @Nullable
    GenericStack getStackUnderMouse(int mouseX, int mouseY);

    List<Rectangle> getJEIExclusionArea();
}

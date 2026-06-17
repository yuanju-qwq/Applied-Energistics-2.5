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

package appeng.client.mui.core;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularScreen;

import appeng.client.mui.jei.IAEKeyGuiPanel;

/**
 * Client-side wrapper for AEKey-only MUI panels.
 */
@SideOnly(Side.CLIENT)
public final class AEKeyMUIScreenFactory {

    private static final Map<GuiScreenWrapper, IAEKeyGuiPanel> PANELS = new WeakHashMap<>();

    private AEKeyMUIScreenFactory() {
    }

    public static GuiScreenWrapper createScreen(final AEKeyModularPanel panel) {
        final GuiScreenWrapper wrapper = new GuiScreenWrapper(new ModularScreen(panel));
        PANELS.put(wrapper, panel);
        return wrapper;
    }

    public static IAEKeyGuiPanel getPanel(final GuiScreenWrapper wrapper) {
        return PANELS.get(wrapper);
    }
}

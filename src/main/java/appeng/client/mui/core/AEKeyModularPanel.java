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

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.cleanroommc.modularui.screen.ModularPanel;

import appeng.api.stacks.GenericStack;
import appeng.client.mui.jei.IAEKeyGuiPanel;
import appeng.client.mui.key.AEKeyListData;
import appeng.client.mui.key.slot.AEKeyVirtualSlot;

/**
 * AEKey-only base panel for the new MUI bottom layer.
 */
public class AEKeyModularPanel extends ModularPanel implements IAEKeyGuiPanel {

    private final List<AEKeyVirtualSlot> virtualSlots = new ArrayList<>();
    private final List<Rectangle> jeiExclusionAreas = new ArrayList<>();
    private final AEKeyListData listData = new AEKeyListData();

    private int guiLeft;
    private int guiTop;

    public AEKeyModularPanel(final String name) {
        super(name);
    }

    public AEKeyModularPanel addVirtualSlot(final AEKeyVirtualSlot slot) {
        this.virtualSlots.add(slot);
        return this;
    }

    public AEKeyModularPanel clearVirtualSlots() {
        this.virtualSlots.clear();
        return this;
    }

    public AEKeyListData getListData() {
        return this.listData;
    }

    public AEKeyModularPanel setGuiOrigin(final int guiLeft, final int guiTop) {
        this.guiLeft = guiLeft;
        this.guiTop = guiTop;
        return this;
    }

    public AEKeyModularPanel addJEIExclusionArea(final Rectangle area) {
        this.jeiExclusionAreas.add(area);
        return this;
    }

    public AEKeyModularPanel clearJEIExclusionAreas() {
        this.jeiExclusionAreas.clear();
        return this;
    }

    @Override
    public int getGuiLeft() {
        return this.guiLeft;
    }

    @Override
    public int getGuiTop() {
        return this.guiTop;
    }

    @Override
    public List<AEKeyVirtualSlot> getVirtualSlots() {
        return Collections.unmodifiableList(this.virtualSlots);
    }

    @Nullable
    @Override
    public GenericStack getStackUnderMouse(final int mouseX, final int mouseY) {
        for (final AEKeyVirtualSlot slot : this.virtualSlots) {
            if (slot.containsScreen(mouseX, mouseY, this.guiLeft, this.guiTop)) {
                return slot.getStack();
            }
        }
        return null;
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        return Collections.unmodifiableList(this.jeiExclusionAreas);
    }
}

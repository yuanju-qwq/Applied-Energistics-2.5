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

package appeng.client.mui.key.slot;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.client.mui.key.AEKeyDisplayEntry;
import appeng.client.mui.render.AEKeyStackRenderer;

/**
 * AEKey-only virtual slot base for the new MUI bottom layer.
 */
public abstract class AEKeyVirtualSlot {

    public static final int DEFAULT_SIZE = 16;

    private final int id;
    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;

    protected AEKeyVirtualSlot(final int id, final int x, final int y) {
        this(id, x, y, DEFAULT_SIZE, DEFAULT_SIZE);
    }

    protected AEKeyVirtualSlot(final int id, final int x, final int y, final int width, final int height) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract @Nullable GenericStack getStack();

    public abstract void setStack(@Nullable GenericStack stack);

    public void setStack(@Nullable final AEKey key, final long amount) {
        this.setStack(key != null ? new GenericStack(key, amount) : null);
    }

    public boolean accepts(final AEKeyType type, final int mouseButton) {
        return true;
    }

    public void render(final Minecraft mc, final int guiLeft, final int guiTop, final boolean craftable) {
        if (!this.visible) {
            return;
        }

        final int screenX = guiLeft + this.x;
        final int screenY = guiTop + this.y;
        final GenericStack stack = this.getStack();
        AEKeyStackRenderer.renderIcon(mc, stack, screenX, screenY);
        AEKeyStackRenderer.renderOverlay(mc, stack, craftable, screenX, screenY);
    }

    @Nullable
    public AEKeyDisplayEntry getDisplayEntry(final boolean craftable) {
        final GenericStack stack = this.getStack();
        return stack != null ? AEKeyDisplayEntry.of(stack, craftable) : null;
    }

    public boolean containsLocal(final int mouseX, final int mouseY) {
        return this.visible
                && mouseX >= this.x
                && mouseX < this.x + this.width
                && mouseY >= this.y
                && mouseY < this.y + this.height;
    }

    public boolean containsScreen(final int mouseX, final int mouseY, final int guiLeft, final int guiTop) {
        return this.containsLocal(mouseX - guiLeft, mouseY - guiTop);
    }

    public int getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setPosition(final int x, final int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(final int width, final int height) {
        this.width = width;
        this.height = height;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}

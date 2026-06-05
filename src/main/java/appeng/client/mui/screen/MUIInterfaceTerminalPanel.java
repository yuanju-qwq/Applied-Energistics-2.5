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

package appeng.client.mui.screen;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.module.InterfaceListModule;
import appeng.container.implementations.ContainerInterfaceTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.util.Platform;

/**
 * MUI interface terminal GUI panel.
 *
 * <p>Delegates all interface list logic to {@link InterfaceListModule}:
 * <ul>
 *   <li>Data management (byId / providerById, server sync via postUpdate)</li>
 *   <li>Three search fields (inputs / outputs / names) and filter buttons</li>
 *   <li>List refresh, scrollbar management</li>
 *   <li>Rendering (drawBG / drawFG / drawScreen dynamic slots)</li>
 *   <li>Keyboard / mouse event forwarding</li>
 *   <li>Block highlight positioning</li>
 * </ul>
 *
 * <p>The panel retains only panel-specific concerns:
 * <ul>
 *   <li>Slot repositioning</li>
 *   <li>JEI exclusion area</li>
 *   <li>Panel size / layout</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUIInterfaceTerminalPanel extends AEBasePanel
        implements IInterfaceTerminalGuiCallback, InterfaceListModule.Host {

    // ========== Constants ==========

    private static final int MAIN_GUI_WIDTH = 208;
    private static final int MAGIC_HEIGHT_NUMBER = 52 + 99;

    // JEI offset to avoid button overlap with JEI area
    private final int jeiOffset = Platform.isJEIEnabled() ? 24 : 0;

    // ========== Module ==========

    private InterfaceListModule interfaceListModule;

    // ========== Construction ==========

    public MUIInterfaceTerminalPanel(final ContainerInterfaceTerminal container) {
        super(container);

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);

        this.xSize = MAIN_GUI_WIDTH;
        this.ySize = 255;
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        // Create module
        this.interfaceListModule = new InterfaceListModule(this);

        // Calculate rows
        this.interfaceListModule.calculateRows();
        final int rows = this.interfaceListModule.getRows();

        super.initGui();

        // Set panel size
        this.ySize = MAGIC_HEIGHT_NUMBER + rows * 18;
        this.centerVertically();

        // Initialize search fields and filter buttons
        this.interfaceListModule.initSearchFieldsAndButtons();

        // Reposition slots
        this.repositionAllSlots();
    }

    @Override
    protected void setupWidgets() {
        // All widget initialization is handled by InterfaceListModule.initSearchFieldsAndButtons()
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = this.ySize + s.getY() - 78 - 7;
        s.xPos = s.getX() + 14;
    }

    // ========== IInterfaceTerminalGuiCallback ==========

    @Override
    public void postUpdate(final NBTTagCompound in) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.postUpdate(in);
        }
    }

    // ========== Rendering ==========

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawBG(offsetX, offsetY);
        }
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawFG(offsetX, offsetY,
                    this.getGuiDisplayName(GuiText.InterfaceTerminal.getLocal()));
        }
    }

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.populateDynamicSlots();
        }

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Search field tooltips are handled by the unified widget lifecycle
        if (this.interfaceListModule != null) {
            this.interfaceListModule.drawSearchFieldTooltips(this, mouseX, mouseY);
        }
    }

    // ========== Input events ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.interfaceListModule != null) {
            this.interfaceListModule.mouseClicked(xCoord, yCoord, btn);
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.interfaceListModule != null && this.interfaceListModule.keyTyped(character, key)) {
            return;
        }

        if (!this.checkHotbarKeys(key)) {
            super.keyTyped(character, key);
        }
    }

    // ========== JEI compatibility ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        final List<Rectangle> exclusionArea = new ArrayList<>();

        // Left-side button area
        int visibleButtons = (int) this.buttonList.stream().filter(v -> v.enabled && v.x < guiLeft).count();
        if (visibleButtons > 0) {
            exclusionArea.add(new Rectangle(guiLeft - 18, guiTop + 8 + jeiOffset, 20,
                    visibleButtons * 20 + visibleButtons - 2));
        }

        return exclusionArea;
    }

    // ========== InterfaceListModule.Host implementation ==========

    @Override
    public int getScreenWidth() {
        return this.width;
    }

    @Override
    public int getScreenHeight() {
        return this.height;
    }

    @Override
    public FontRenderer getFontRenderer() {
        return this.fontRenderer;
    }

    @Override
    public MUIScrollBar getInterfaceScrollBar() {
        return this.getScrollBar();
    }

    @Override
    public void addModuleWidget(IMUIWidget widget) {
        this.addWidget(widget);
    }

    @Override
    public AEBasePanel getPanel() {
        return this;
    }

    @Override
    public void requestReinitialize() {
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void bindTexture(String file) {
        super.bindTexture(file);
    }

    @Override
    public void drawTexturedModalRect(int x, int y, int textureX, int textureY, int w, int h) {
        super.drawTexturedModalRect(x, y, textureX, textureY, w, h);
    }

    @Override
    public int getJeiOffset() {
        return this.jeiOffset;
    }

    // ========== Module accessor (for subclasses) ==========

    /**
     * Get the interface list module.
     * Subclasses (e.g. wireless terminal) may need access for customization.
     */
    protected InterfaceListModule getInterfaceListModule() {
        return this.interfaceListModule;
    }
}

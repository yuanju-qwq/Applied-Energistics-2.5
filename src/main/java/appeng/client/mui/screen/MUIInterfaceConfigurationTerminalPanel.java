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

import com.google.common.collect.HashMultimap;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.module.InterfaceListModule;
import appeng.client.mui.module.HighlightModule;
import appeng.container.implementations.ContainerInterfaceConfigurationTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InventoryAction;
import appeng.util.item.AEItemStack;

/**
 * MUI interface Config configuration terminal panel.
 * Displays the Config list of all legacy item interfaces in the ME network.
 * Supports search filtering (item name/interface name), scrollable list,
 * block highlight positioning, SlotDisconnected Config operations,
 * JEI ghost drag-and-drop.
 *
 * <p>Delegates all data management to {@link InterfaceListModule} in SINGLE_INPUTS_NAMES mode.
 * Retains its own rendering customizations (dim overlay on non-matching interfaces,
 * different texture coordinates, JEI ghost drag support).
 */
public class MUIInterfaceConfigurationTerminalPanel extends AEBasePanel
        implements IInterfaceTerminalGuiCallback, IJEIGhostIngredients,
        InterfaceListModule.Host {

    private static final int LINES_ON_PAGE = 6;
    private static final int OFFSET_X = 21;
    private static final int SCROLL_BAR_LEFT = 189;
    private static final int SCROLL_BAR_TOP = 31;
    private static final int SCROLL_BAR_HEIGHT = 106;

    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    /** Interface list module (data management in SINGLE_INPUTS_NAMES mode). */
    private InterfaceListModule module;

    public MUIInterfaceConfigurationTerminalPanel(final ContainerInterfaceConfigurationTerminal container) {
        super(container);

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 235;

        this.module = new InterfaceListModule(this,
                InterfaceListModule.SearchMode.SINGLE_INPUTS_NAMES,
                InterfaceLogic.NUMBER_OF_CONFIG_SLOTS, 512);
    }

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        // All widget initialization is handled by InterfaceListModule.initSearchFieldsAndButtons()
    }

    @Override
    public void initGui() {
        this.module.initSearchFieldsAndButtons();

        super.initGui();

        this.getScrollBar().setLeft(SCROLL_BAR_LEFT);
        this.getScrollBar().setHeight(SCROLL_BAR_HEIGHT);
        this.getScrollBar().setTop(SCROLL_BAR_TOP);

        this.repositionAllSlots();
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = this.ySize + s.getY() - 78 - 7;
        s.xPos = s.getX() + 14;
    }

    // ========== Rendering ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.module.getHighlightButtonPool().reset();
        this.inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        final int currentScroll = this.getScrollBar().getCurrentScroll();
        final HighlightModule highlightModule = this.module.getHighlightModule();
        final Map<ClientDCInternalInv, Integer> numUpgradesMap = this.module.getNumUpgradesMap();

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE
                && currentScroll + x < this.module.getLines().size(); x++) {
            final Object lineObj = this.module.getLines().get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                highlightModule.createHighlightButton(
                        this.module.getHighlightButtonPool(), 4, offset, inv);

                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    for (int z = 0; z < 9; z++) {
                        this.inventorySlots.inventorySlots
                                .add(new SlotDisconnected(inv, z + (row * 9), (z * 18 + 22), offset));
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String) {
                linesDraw++;
                offset += 18;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(
                this.getGuiDisplayName(GuiText.InterfaceConfigurationTerminal.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        final int currentScroll = this.getScrollBar().getCurrentScroll();
        final ArrayList<Object> lines = this.module.getLines();
        final Map<ClientDCInternalInv, Integer> numUpgradesMap = this.module.getNumUpgradesMap();
        final Set<Object> matchedStacks = this.module.getMatchedStacks();
        final Set<ClientDCInternalInv> matchedInterfaces = this.module.getMatchedInterfaces();
        final HashMultimap<String, ClientDCInternalInv> byName = this.module.getByName();

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && currentScroll + x < lines.size(); x++) {
            final Object lineObj = lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    for (int z = 0; z < 9; z++) {
                        if (matchedStacks.contains(inv.getInventory().getStackInSlot(z + (row * 9)))) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x8A00FF00);
                        } else if (!matchedInterfaces.contains(inv)) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x6A000000);
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String name) {
                final int rows = byName.get(name).size();
                if (rows > 1) {
                    name = name + " (" + rows + ')';
                }
                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 155) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, OFFSET_X + 2, 5 + offset, AEMUITheme.COLOR_TITLE);
                linesDraw++;
                offset += 18;
            }
        }
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/interfaceconfigurationterminal.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        int offset = 29;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        final ArrayList<Object> lines = this.module.getLines();
        final Map<ClientDCInternalInv, Integer> numUpgradesMap = this.module.getNumUpgradesMap();
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && ex + x < lines.size(); x++) {
            final Object lineObj = lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv) {
                GlStateManager.color(1, 1, 1, 1);
                final int width = 9 * 18;
                int extraLines = numUpgradesMap.get(lineObj);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 170, width, 18);
                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }
    }

    // ========== Input handling ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.module != null) {
            this.module.mouseClicked(xCoord, yCoord, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (this.module != null && this.module.keyTyped(character, key)) {
            return;
        }

        if (!this.checkHotbarKeys(key)) {
            super.keyTyped(character, key);
        }
    }

    // ========== Data update ==========

    @Override
    public void postUpdate(final NBTTagCompound in) {
        if (this.module != null) {
            this.module.postUpdate(in);
        }
    }

    // ========== JEI Ghost drag and drop ==========

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) {
            return Collections.emptyList();
        }
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotDisconnected) {
                ItemStack itemStack = (ItemStack) ingredient;
                IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                    }

                    @Override
                    public void accept(Object ingredient) {
                        try {
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    InventoryAction.PLACE_JEI_GHOST_ITEM, (SlotDisconnected) slot,
                                    GenericStack.fromItemStack(itemStack));
                            NetworkHandler.instance().sendToServer(p);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                targets.add(target);
                mapTargetSlot.putIfAbsent(target, slot);
            }
        }
        return targets;
    }

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
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
    public <T extends IMUIWidget> T addModuleWidget(T widget) {
        return this.addWidget(widget);
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
        return 0;
    }
}

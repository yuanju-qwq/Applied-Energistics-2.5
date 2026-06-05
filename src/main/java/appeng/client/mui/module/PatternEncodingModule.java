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

package appeng.client.mui.module;

import static appeng.helpers.PatternHelper.CRAFTING_GRID_DIMENSION;
import static appeng.helpers.PatternHelper.PROCESSING_INPUT_WIDTH;

import java.io.IOException;

import org.lwjgl.input.Mouse;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.config.ActionItems;
import appeng.api.config.CombineMode;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.client.mui.slot.VirtualMEPatternSlot;
import appeng.client.mui.slot.VirtualMEPhantomSlot.KeyTypeAcceptPredicate;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.mui.widgets.MUITabButton;
import appeng.container.implementations.ContainerWirelessDualInterfaceTerminal;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.PatternHelper;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Pattern encoding module - a reusable component extracted from GuiWirelessDualInterfaceTerminal.
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Creation, layout, and visibility management of pattern encoding buttons (MUI widgets)</li>
 *   <li>Drawing pattern.png / pattern3.png panel background</li>
 *   <li>Button click events sent via PacketValueConfig (callback-based)</li>
 *   <li>repositionSlots: dynamic slot positioning based on crafting/processing mode</li>
 *   <li>Processing mode input/output Scrollbar management</li>
 *   <li>PlacePattern auto-insert functionality</li>
 *   <li>Panel drag support</li>
 * </ul>
 */
public class PatternEncodingModule {

    // ========== Textures ==========

    private static final ResourceLocation PATTERN_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern.png");
    private static final ResourceLocation PATTERN3_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/gui/widget/pattern3.png");

    // ========== Layout constants ==========

    private static final int CRAFTING_INPUT_SLOTS = CRAFTING_GRID_DIMENSION * CRAFTING_GRID_DIMENSION;

    private static final int PATTERN_PANEL_X_OFFSET = 209;
    private static final int PATTERN_PANEL_WIDTH = 133;
    private static final int PATTERN_PANEL_UPPER_HEIGHT = 93;
    private static final int PATTERN_PANEL_LOWER_WIDTH = 40;
    private static final int PATTERN_PANEL_LOWER_HEIGHT = 77;
    private static final int PATTERN_PANEL_FOOTER_WIDTH = 32;
    private static final int PATTERN_PANEL_FOOTER_HEIGHT = 32;
    private static final int PATTERN_PANEL_HEIGHT = PATTERN_PANEL_UPPER_HEIGHT + PATTERN_PANEL_LOWER_HEIGHT;
    static final int PATTERN_PANEL_TOTAL_HEIGHT = PATTERN_PANEL_HEIGHT + PATTERN_PANEL_FOOTER_HEIGHT;

    private static final int CRAFTING_GRID_OFFSET_X = 15;
    private static final int CRAFTING_GRID_OFFSET_Y = 18;
    private static final int PROCESSING_GRID_OFFSET_X = 15;
    private static final int PROCESSING_GRID_OFFSET_Y = 9;
    private static final int PROCESSING_INPUT_ROWS = 4;

    private static final int CRAFTING_OUTPUT_OFFSET_X = 108;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = 9;
    private static final int PROCESSING_OUTPUT_COLUMNS = 1;
    private static final int PROCESSING_OUTPUT_ROWS = 4;
    private static final int PROCESSING_OUTPUT_NORMAL_OFFSET_X = 112;
    private static final int PROCESSING_OUTPUT_INVERTED_OFFSET_X = 15;
    private static final int PROCESSING_INVERTED_GRID_OFFSET_X = 58;

    private static final int PATTERN_IN_OFFSET_X = 10;
    private static final int PATTERN_IN_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 6;
    private static final int PATTERN_OUT_OFFSET_X = 11;
    private static final int PATTERN_OUT_OFFSET_Y = PATTERN_PANEL_UPPER_HEIGHT + 49;

    // ========== Host interface ==========

    /**
     * The host GUI must implement this interface to provide the context needed by the module.
     */
    public interface Host {
        int getGuiLeft();

        int getGuiTop();

        int getYSize();

        AEBasePanel getPanel();

        RenderItem getItemRenderer();

        ContainerWirelessDualInterfaceTerminal getDualContainer();

        /**
         * Returns the host's widget container. The module adds all of its MUI widgets here
         * exactly once during {@link #initButtons()}.
         */
        void addModuleWidget(IMUIWidget widget);

        /**
         * Requests the host to reinitialize the GUI.
         */
        void requestReinitialize();

        /**
         * Gets the interface list module (used for PlacePattern).
         */
        InterfaceListModule getInterfaceListModule();
    }

    // ========== Data ==========

    private final Host host;

    // Tab buttons (crafting / processing mode toggle)
    private MUITabButton tabCraftButton;
    private MUITabButton tabProcessButton;

    // Img buttons
    private MUIButtonWidget substitutionsEnabledBtn;
    private MUIButtonWidget substitutionsDisabledBtn;
    private MUIButtonWidget beSubstitutionsEnabledBtn;
    private MUIButtonWidget beSubstitutionsDisabledBtn;
    private MUIButtonWidget invertBtn;
    private MUIButtonWidget combineEnabledBtn;
    private MUIButtonWidget combineDisabledBtn;
    private MUIButtonWidget encodeBtn;
    private MUIButtonWidget clearBtn;

    // Quantity adjustment buttons
    private MUIButtonWidget x2Btn;
    private MUIButtonWidget x3Btn;
    private MUIButtonWidget plusOneBtn;
    private MUIButtonWidget divTwoBtn;
    private MUIButtonWidget divThreeBtn;
    private MUIButtonWidget minusOneBtn;
    private MUIButtonWidget doubleBtn;

    // Scrollbars
    private final MUIScrollBar processingInputScrollbar;
    private int processingInputPage = 0;
    private final MUIScrollBar processingScrollBar;

    // Virtual slots: crafting input and pattern output
    private VirtualMEPatternSlot[] craftingVirtualSlots;
    private VirtualMEPatternSlot[] outputVirtualSlots;

    // PlacePattern
    private boolean pendingPlacePattern = false;

    // Panel drag
    private PanelDragState dragState;

    // ========== Constructor ==========

    public PatternEncodingModule(Host host) {
        this.host = host;
        this.processingInputScrollbar = new MUIScrollBar();
        this.processingScrollBar = new MUIScrollBar();
    }

    // ========== Accessors ==========

    public int getPanelX() {
        return PATTERN_PANEL_X_OFFSET + (dragState != null ? dragState.getDragOffsetX() : 0);
    }

    public int getPanelY() {
        return (dragState != null ? dragState.getDragOffsetY() : 0);
    }

    public int getPanelWidth() {
        return PATTERN_PANEL_WIDTH;
    }

    public int getPanelTotalHeight() {
        return PATTERN_PANEL_TOTAL_HEIGHT;
    }

    public MUIScrollBar getProcessingInputScrollbar() {
        return processingInputScrollbar;
    }

    public MUIScrollBar getProcessingScrollBar() {
        return processingScrollBar;
    }

    public PanelDragState getDragState() {
        return dragState;
    }

    // ========== Initialization ==========

    /**
     * Initializes drag state. Must be called at the start of initGui.
     */
    public void initDragState() {
        this.dragState = new PanelDragState((mouseX, mouseY) -> {
            final int absX = host.getGuiLeft() + getPanelX();
            final int absY = host.getGuiTop() + getPanelY();
            return mouseX >= absX && mouseX < absX + PATTERN_PANEL_WIDTH
                    && mouseY >= absY + PATTERN_PANEL_HEIGHT
                    && mouseY < absY + PATTERN_PANEL_HEIGHT + 16;
        });
    }

    /**
     * Creates all buttons and registers them as MUI widgets on the host.
     * <p>
     * Each button has its own onClick callback so the host no longer needs a central
     * {@code actionPerformed} dispatcher. Called once during initGui.
     */
    public void initButtons() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();

        // Encode button
        this.encodeBtn = new MUIButtonWidget(panelScreenX + 11, panelScreenY + 118,
                Settings.ACTIONS, ActionItems.ENCODE);
        this.encodeBtn.setOnClick(btn -> onEncodeClicked());
        host.addModuleWidget(this.encodeBtn);

        // Clear button
        this.clearBtn = new MUIButtonWidget(panelScreenX + 87, panelScreenY + 10,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.clearBtn.setOnClick(btn -> sendPacket("PatternTerminal.Clear", "1"));
        host.addModuleWidget(this.clearBtn);

        // Crafting/Processing mode toggle buttons (item-icon tabs)
        this.tabCraftButton = new MUITabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.CRAFTING_TABLE),
                GuiText.CraftingPattern.getLocal(), host.getItemRenderer());
        this.tabCraftButton.setOnClick(tab -> sendPacket("PatternTerminal.CraftMode", "0"));
        host.addModuleWidget(this.tabCraftButton);

        this.tabProcessButton = new MUITabButton(panelScreenX + 39, panelScreenY + 93,
                new ItemStack(Blocks.FURNACE),
                GuiText.ProcessingPattern.getLocal(), host.getItemRenderer());
        this.tabProcessButton.setOnClick(tab -> sendPacket("PatternTerminal.CraftMode", "1"));
        host.addModuleWidget(this.tabProcessButton);

        // Substitution buttons
        this.substitutionsEnabledBtn = new MUIButtonWidget(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.substitutionsEnabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Substitute", "0"));
        host.addModuleWidget(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new MUIButtonWidget(panelScreenX + 97, panelScreenY + 10,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.substitutionsDisabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Substitute", "1"));
        host.addModuleWidget(this.substitutionsDisabledBtn);

        // beSubstitute buttons
        this.beSubstitutionsEnabledBtn = new MUIButtonWidget(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.beSubstitutionsEnabledBtn.setHalfSize(true);
        this.beSubstitutionsEnabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.beSubstitute", "0"));
        host.addModuleWidget(this.beSubstitutionsEnabledBtn);

        this.beSubstitutionsDisabledBtn = new MUIButtonWidget(panelScreenX + 87, panelScreenY + 20,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.beSubstitutionsDisabledBtn.setHalfSize(true);
        this.beSubstitutionsDisabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.beSubstitute", "1"));
        host.addModuleWidget(this.beSubstitutionsDisabledBtn);

        // Invert button
        this.invertBtn = new MUIButtonWidget(panelScreenX + 97, panelScreenY + 20,
                Settings.ACTIONS, ActionItems.CLOSE);
        this.invertBtn.setHalfSize(true);
        this.invertBtn.setOnClick(btn -> {
            final boolean newInverted = !host.getDualContainer().isInverted();
            sendPacket("PatternTerminal.Invert", newInverted ? "1" : "0");
        });
        host.addModuleWidget(this.invertBtn);

        // Combine buttons
        this.combineEnabledBtn = new MUIButtonWidget(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.ENABLED);
        this.combineEnabledBtn.setHalfSize(true);
        this.combineEnabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Combine", "0"));
        host.addModuleWidget(this.combineEnabledBtn);

        this.combineDisabledBtn = new MUIButtonWidget(panelScreenX + 87, panelScreenY + 30,
                Settings.ACTIONS, CombineMode.DISABLED);
        this.combineDisabledBtn.setHalfSize(true);
        this.combineDisabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Combine", "1"));
        host.addModuleWidget(this.combineDisabledBtn);

        // Quantity adjustment buttons
        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28;

        this.x3Btn = new MUIButtonWidget(adjBtnX1, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.x3Btn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByThree" : "PatternTerminal.MultiplyByThree",
                "1"));
        host.addModuleWidget(this.x3Btn);

        this.x2Btn = new MUIButtonWidget(adjBtnX1, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.x2Btn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByTwo" : "PatternTerminal.MultiplyByTwo",
                "1"));
        host.addModuleWidget(this.x2Btn);

        this.plusOneBtn = new MUIButtonWidget(adjBtnX1, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.plusOneBtn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DecreaseByOne" : "PatternTerminal.IncreaseByOne",
                "1"));
        host.addModuleWidget(this.plusOneBtn);

        this.divThreeBtn = new MUIButtonWidget(adjBtnX2, panelScreenY + 6,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.divThreeBtn.setOnClick(btn -> sendPacket("PatternTerminal.DivideByThree", "1"));
        host.addModuleWidget(this.divThreeBtn);

        this.divTwoBtn = new MUIButtonWidget(adjBtnX2, panelScreenY + 16,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.divTwoBtn.setOnClick(btn -> sendPacket("PatternTerminal.DivideByTwo", "1"));
        host.addModuleWidget(this.divTwoBtn);

        this.minusOneBtn = new MUIButtonWidget(adjBtnX2, panelScreenY + 26,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.minusOneBtn.setOnClick(btn -> sendPacket("PatternTerminal.DecreaseByOne", "1"));
        host.addModuleWidget(this.minusOneBtn);

        this.doubleBtn = new MUIButtonWidget(adjBtnX2, panelScreenY + 36,
                Settings.ACTIONS, ActionItems.DOUBLE_STACKS);
        this.doubleBtn.setHalfSize(true);
        this.doubleBtn.setOnClick(btn -> onDoubleClicked());
        host.addModuleWidget(this.doubleBtn);

        // Initialize scrollbars
        this.updateProcessingScrollbar();
        this.updateProcessingInputScrollbar();
    }

    // ========== Button click callbacks ==========

    /**
     * Encode button click: dispatches Encode packet. Alt+Click (no shift/ctrl) sets the
     * PlacePattern flag for {@link #updateScreen} to pick up.
     */
    private void onEncodeClicked() {
        final int value = (AEBasePanel.isCtrlKeyDown() ? 1 : 0) << 1
                | (AEBasePanel.isShiftKeyDown() ? 1 : 0);
        sendPacket("PatternTerminal.Encode", String.valueOf(value));
        if (value == 0 && AEBasePanel.isAltKeyDown()) {
            this.pendingPlacePattern = true;
        }
    }

    /**
     * Double button click: encodes a value bitfield with shift and right-mouse-button flags.
     */
    private void onDoubleClicked() {
        final boolean backwards = Mouse.isButtonDown(1);
        int val = AEBasePanel.isShiftKeyDown() ? 1 : 0;
        if (backwards) {
            val |= 0b10;
        }
        sendPacket("PatternTerminal.Double", String.valueOf(val));
    }

    /**
     * Helper that swallows IOExceptions. All onClick callbacks funnel through here.
     */
    private void sendPacket(String key, String value) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(key, value));
        } catch (IOException e) {
            // ignore
        }
    }

    // ========== Slot positioning ==========

    /**
     * Initializes Virtual slots: crafting input and pattern output.
     * Must be called during initGui.
     */
    public void initVirtualSlots() {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        final IAEStackInventory craftingInv = ct.getCraftingAEInv();
        final IAEStackInventory outputInv = ct.getOutputAEInv();

        this.craftingVirtualSlots = new VirtualMEPatternSlot[craftingInv.getSizeInventory()];
        for (int i = 0; i < craftingInv.getSizeInventory(); i++) {
            VirtualMEPatternSlot slot = new VirtualMEPatternSlot(i, -9000, -9000, craftingInv, i,
                    (KeyTypeAcceptPredicate) (s, type, btn) -> true);
            this.craftingVirtualSlots[i] = slot;
            host.getPanel().getGuiSlots().add(slot);
        }

        this.outputVirtualSlots = new VirtualMEPatternSlot[outputInv.getSizeInventory()];
        for (int i = 0; i < outputInv.getSizeInventory(); i++) {
            VirtualMEPatternSlot slot = new VirtualMEPatternSlot(i, -9000, -9000, outputInv, i,
                    (KeyTypeAcceptPredicate) (s, type, btn) -> true);
            this.outputVirtualSlots[i] = slot;
            host.getPanel().getGuiSlots().add(slot);
        }
    }

    /**
     * Repositions all pattern-related slots (crafting/processing/pattern IN/OUT).
     * Virtual slots (crafting/output) have their coordinates managed directly by this module.
     * Container-registered Slots (craftSlot/patternIN/patternOUT) are still iterated via inventorySlots.
     * Must be called during initGui and updateScreen.
     */
    public void repositionSlots() {
        final int panelX = getPanelX();
        final int panelY = getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();

        // Position crafting Virtual slots
        if (this.craftingVirtualSlots != null) {
            for (int craftIdx = 0; craftIdx < this.craftingVirtualSlots.length; craftIdx++) {
                final VirtualMEPatternSlot slot = this.craftingVirtualSlots[craftIdx];
                if (ct.isCraftingMode()) {
                    if (craftIdx >= CRAFTING_INPUT_SLOTS) {
                        slot.setPosition(-9000, -9000);
                    } else {
                        final int gridX = craftIdx % CRAFTING_GRID_DIMENSION;
                        final int gridY = craftIdx / CRAFTING_GRID_DIMENSION;
                        slot.setPosition(
                                panelX + CRAFTING_GRID_OFFSET_X + gridX * 18,
                                panelY + CRAFTING_GRID_OFFSET_Y + gridY * 18);
                    }
                } else {
                    final boolean inverted = ct.isInverted();
                    final int processingGridOffsetX = inverted
                            ? PROCESSING_INVERTED_GRID_OFFSET_X
                            : PROCESSING_GRID_OFFSET_X;
                    final int pageStart = this.processingInputPage
                            * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
                    final int pageEnd = Math.min(pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS,
                            PatternHelper.PROCESSING_INPUT_LIMIT);
                    if (craftIdx < pageStart || craftIdx >= pageEnd) {
                        slot.setPosition(-9000, -9000);
                    } else {
                        final int visibleIndex = craftIdx - pageStart;
                        final int gridX = visibleIndex % PROCESSING_INPUT_WIDTH;
                        final int gridY = visibleIndex / PROCESSING_INPUT_WIDTH;
                        slot.setPosition(
                                panelX + processingGridOffsetX + gridX * 18,
                                panelY + PROCESSING_GRID_OFFSET_Y + gridY * 18);
                    }
                }
            }
        }

        // Position output Virtual slots
        if (this.outputVirtualSlots != null) {
            for (int outIdx = 0; outIdx < this.outputVirtualSlots.length; outIdx++) {
                final VirtualMEPatternSlot slot = this.outputVirtualSlots[outIdx];
                if (ct.isCraftingMode()) {
                    slot.setPosition(-9000, -9000);
                } else {
                    final int processingOutputOffsetX = ct.isInverted()
                            ? PROCESSING_OUTPUT_INVERTED_OFFSET_X
                            : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
                    final int outX = outIdx % PROCESSING_OUTPUT_COLUMNS;
                    final int outY = outIdx / PROCESSING_OUTPUT_COLUMNS;
                    slot.setPosition(
                            panelX + processingOutputOffsetX + outX * 18,
                            panelY + PROCESSING_OUTPUT_OFFSET_Y + outY * 18);
                }
            }
        }

        // Position Container-registered slots (craftSlot/patternIN/patternOUT/player inventory)
        for (final Object obj : host.getPanel().inventorySlots.inventorySlots) {
            if (obj instanceof AppEngSlot slot) {
                if (slot instanceof SlotPatternTerm) {
                    if (ct.isCraftingMode()) {
                        slot.xPos = panelX + CRAFTING_OUTPUT_OFFSET_X;
                        slot.yPos = panelY + 37;
                    } else {
                        slot.xPos = -9000;
                        slot.yPos = -9000;
                    }
                } else if (slot instanceof SlotRestrictedInput restrictedSlot) {
                    if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.BLANK_PATTERN) {
                        slot.xPos = panelX + PATTERN_IN_OFFSET_X;
                        slot.yPos = panelY + PATTERN_IN_OFFSET_Y;
                    } else if (restrictedSlot.getPlaceableItemType()
                            == SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN) {
                        slot.xPos = panelX + PATTERN_OUT_OFFSET_X;
                        slot.yPos = panelY + PATTERN_OUT_OFFSET_Y;
                    } else {
                        // Player inventory slots: handled by host
                        slot.yPos = host.getYSize() + slot.getY() - 78 - 7;
                        slot.xPos = slot.getX() + 14;
                    }
                } else {
                    // Other normal slots
                    slot.yPos = host.getYSize() + slot.getY() - 78 - 7;
                    slot.xPos = slot.getX() + 14;
                }
            }
        }
    }

    // ========== Rendering: drawBG ==========

    /**
     * Draws the pattern encoding panel background.
     */
    public void drawBG(int offsetX, int offsetY) {
        final int panelX = offsetX + getPanelX();
        final int panelY = offsetY + getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        final AEBasePanel panel = host.getPanel();

        GlStateManager.color(1, 1, 1, 1);

        // Upper section: crafting mode = pattern3.png, processing mode = pattern.png (inverted)
        if (ct.isCraftingMode()) {
            panel.mc.getTextureManager().bindTexture(PATTERN3_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else if (ct.isInverted()) {
            panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, 0,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        } else {
            panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
            panel.drawTexturedModalRect(panelX, panelY, 0, PATTERN_PANEL_UPPER_HEIGHT,
                    PATTERN_PANEL_WIDTH, PATTERN_PANEL_UPPER_HEIGHT);
        }

        // Lower section: IN/OUT slot backgrounds
        panel.mc.getTextureManager().bindTexture(PATTERN_TEXTURE);
        panel.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_UPPER_HEIGHT,
                133, 0, PATTERN_PANEL_LOWER_WIDTH, PATTERN_PANEL_LOWER_HEIGHT);
        panel.drawTexturedModalRect(panelX, panelY + PATTERN_PANEL_HEIGHT,
                173, 0, PATTERN_PANEL_FOOTER_WIDTH, PATTERN_PANEL_FOOTER_HEIGHT);

        // Processing mode scrollbar
        GlStateManager.pushMatrix();
        GlStateManager.translate(offsetX, offsetY, 0);
        if (!ct.isCraftingMode()) {
            if (this.getTotalProcessingInputPages() > 1) {
                this.processingInputScrollbar.draw(panel);
            }
            if (ct.getTotalPages() > 1) {
                this.processingScrollBar.draw(panel);
            }
        }
        GlStateManager.popMatrix();
    }

    // ========== Rendering: drawFG ==========

    /**
     * Draws the pattern encoding panel foreground (title text and button visibility management).
     */
    public void drawFG() {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        final int panelX = getPanelX();
        final int panelY = getPanelY();

        host.getPanel().mc.fontRenderer.drawString(GuiText.PatternEncoding.getLocal(),
                panelX + 4, panelY + 4, AEMUITheme.COLOR_TITLE);

        // Button visibility
        if (ct.isCraftingMode()) {
            this.tabCraftButton.setVisible(true);
            this.tabProcessButton.setVisible(false);
            this.substitutionsEnabledBtn.setVisible(ct.isSubstitute());
            this.substitutionsDisabledBtn.setVisible(!ct.isSubstitute());
            this.beSubstitutionsEnabledBtn.setVisible(ct.isBeSubstitute());
            this.beSubstitutionsDisabledBtn.setVisible(!ct.isBeSubstitute());
            this.invertBtn.setVisible(false);
            this.combineEnabledBtn.setVisible(false);
            this.combineDisabledBtn.setVisible(false);
            this.x2Btn.setVisible(false);
            this.x3Btn.setVisible(false);
            this.divTwoBtn.setVisible(false);
            this.divThreeBtn.setVisible(false);
            this.plusOneBtn.setVisible(false);
            this.minusOneBtn.setVisible(false);
            this.doubleBtn.setVisible(false);
        } else {
            this.tabCraftButton.setVisible(false);
            this.tabProcessButton.setVisible(true);
            this.substitutionsEnabledBtn.setVisible(false);
            this.substitutionsDisabledBtn.setVisible(false);
            this.beSubstitutionsEnabledBtn.setVisible(false);
            this.beSubstitutionsDisabledBtn.setVisible(false);
            this.invertBtn.setVisible(true);
            this.combineEnabledBtn.setVisible(ct.isCombine());
            this.combineDisabledBtn.setVisible(!ct.isCombine());
            this.x2Btn.setVisible(true);
            this.x3Btn.setVisible(true);
            this.x2Btn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.divTwoBtn.setVisible(false);
            this.divThreeBtn.setVisible(false);
            this.plusOneBtn.setVisible(true);
            this.plusOneBtn.set(AEBasePanel.isShiftKeyDown()
                    ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
            this.minusOneBtn.setVisible(false);
            this.doubleBtn.setVisible(true);
        }
    }

    // ========== Button position update ==========
    //
    // Position is now updated once during initButtons() AND on drag. The legacy
    // populateButtons() / addIfNotNull dance is gone - widgets are owned by the host
    // and rendered as part of the standard MUI pass each frame.

    /**
     * Updates button positions to follow the panel after a drag. Idempotent; safe to call
     * every frame from the drag update hook.
     */
    public void updateButtonPositions() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();

        setPos(this.encodeBtn, panelScreenX + 11, panelScreenY + 118);
        setPos(this.tabCraftButton, panelScreenX + 39, panelScreenY + 93);
        setPos(this.tabProcessButton, panelScreenX + 39, panelScreenY + 93);

        if (ct.isCraftingMode()) {
            setPos(this.clearBtn, panelScreenX + 72, panelScreenY + 14);
            setPos(this.substitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 14);
            setPos(this.substitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 14);
            setPos(this.beSubstitutionsEnabledBtn, panelScreenX + 82, panelScreenY + 24);
            setPos(this.beSubstitutionsDisabledBtn, panelScreenX + 82, panelScreenY + 24);
            return;
        }

        final int offset = ct.isInverted() ? -3 * 18 : 0;
        setPos(this.clearBtn, panelScreenX + 87 + offset, panelScreenY + 10);
        setPos(this.substitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        setPos(this.substitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 10);
        setPos(this.beSubstitutionsEnabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        setPos(this.beSubstitutionsDisabledBtn, panelScreenX + 97 + offset, panelScreenY + 69);
        setPos(this.invertBtn, panelScreenX + 87 + offset, panelScreenY + 20);
        setPos(this.combineEnabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);
        setPos(this.combineDisabledBtn, panelScreenX + 87 + offset, panelScreenY + 59);

        final int adjBtnX1 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 38 + offset;
        final int adjBtnX2 = panelScreenX + PROCESSING_OUTPUT_OFFSET_X + 28 + offset;
        setPos(this.x3Btn, adjBtnX1, panelScreenY + 6);
        setPos(this.x2Btn, adjBtnX1, panelScreenY + 16);
        setPos(this.plusOneBtn, adjBtnX1, panelScreenY + 26);
        setPos(this.divThreeBtn, adjBtnX2, panelScreenY + 6);
        setPos(this.divTwoBtn, adjBtnX2, panelScreenY + 16);
        setPos(this.minusOneBtn, adjBtnX2, panelScreenY + 26);
        setPos(this.doubleBtn, adjBtnX2, panelScreenY + 36);
    }

    private static void setPos(MUIButtonWidget btn, int x, int y) {
        if (btn != null) {
            btn.setPosition(x, y);
        }
    }

    private static void setPos(MUITabButton btn, int x, int y) {
        if (btn != null) {
            btn.setPosition(x, y);
        }
    }

    // ========== Input handling: mouseWheel ==========

    /**
     * Handles mouse wheel (processing mode input/output area).
     *
     * @return true if the event was consumed
     */
    public boolean mouseWheelEvent(int x, int y, int wheel) {
        final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
        if (ct.isCraftingMode()) {
            return false;
        }

        final int patPanelAbsX = host.getGuiLeft() + getPanelX();
        final int patPanelAbsY = host.getGuiTop() + getPanelY();
        if (x >= patPanelAbsX && x < patPanelAbsX + PATTERN_PANEL_WIDTH
                && y >= patPanelAbsY && y < patPanelAbsY + PATTERN_PANEL_UPPER_HEIGHT) {
            if (this.isMouseOverProcessingInputArea(x, y) && this.getTotalProcessingInputPages() > 1) {
                this.updateProcessingInputScrollbar();
                final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
                this.processingInputScrollbar.wheel(wheel);
                if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
                    this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
                    return true;
                }
            }

            if (this.isMouseOverProcessingOutputArea(x, y) && ct.getTotalPages() > 1) {
                this.processingScrollBar.wheel(wheel);
                this.sendActivePageUpdate();
                return true;
            }
        }
        return false;
    }

    // ========== Input handling: mouse click (Scrollbar drag) ==========

    /**
     * Attempts to handle processing mode Scrollbar mouse drag.
     *
     * @return true if the event was consumed
     */
    public boolean handleScrollbarClick(int mouseX, int mouseY) {
        return this.updatePatternInputScrollFromMouse(mouseX, mouseY)
                || this.updatePatternOutputScrollFromMouse(mouseX, mouseY);
    }

    // ========== updateScreen ==========

    /**
     * Tick update. Called during updateScreen.
     */
    public void updateScreen() {
        this.updateProcessingInputScrollbar();
        this.repositionSlots();
        this.updateProcessingScrollbar();
        this.updateButtonPositions();

        // PlacePattern: auto-insert into empty interface slots after encoding completes
        if (this.pendingPlacePattern) {
            this.pendingPlacePattern = false;
            final ContainerWirelessDualInterfaceTerminal ct = host.getDualContainer();
            if (ct.getPatternSlotOUT() != null && ct.getPatternSlotOUT().getHasStack()) {
                this.tryPlacePatternToHighlightedInterface();
            }
        }
    }

    // ========== PlacePattern ==========

    private void tryPlacePatternToHighlightedInterface() {
        final InterfaceListModule listModule = host.getInterfaceListModule();
        for (final ClientDCInternalInv inv : listModule.getById().values()) {
            final int slotLimit = inv.getInventory().getSlots();
            final int extraLines = listModule.getNumUpgradesMap().getOrDefault(inv, 0);
            final int maxSlots = Math.min(slotLimit, 9 * (1 + extraLines));

            for (int i = 0; i < maxSlots; i++) {
                if (inv.getInventory().getStackInSlot(i).isEmpty()) {
                    try {
                        NetworkHandler.instance().sendToServer(new PacketValueConfig(
                                "PatternTerminal.PlacePattern",
                                inv.getId() + "," + i));
                    } catch (IOException e) {
                        // ignore
                    }
                    return;
                }
            }
        }
    }

    // ========== Scrollbar management ==========

    private void sendActivePageUpdate() {
        final int newPage = this.processingScrollBar.getCurrentScroll();
        host.getDualContainer().setActivePage(newPage);
        try {
            NetworkHandler.instance().sendToServer(
                    new PacketValueConfig("PatternTerminal.ActivePage", String.valueOf(newPage)));
        } catch (IOException e) {
            // ignore
        }
    }

    private int getProcessingGridOffsetX() {
        return host.getDualContainer().isInverted()
                ? PROCESSING_INVERTED_GRID_OFFSET_X : PROCESSING_GRID_OFFSET_X;
    }

    private int getProcessingOutputOffsetX() {
        return host.getDualContainer().isInverted()
                ? PROCESSING_OUTPUT_INVERTED_OFFSET_X : PROCESSING_OUTPUT_NORMAL_OFFSET_X;
    }

    public void updateProcessingScrollbar() {
        final int panelRelX = getPanelX();
        final int panelRelY = getPanelY();
        this.processingScrollBar.setLeft(panelRelX + this.getProcessingOutputOffsetX()
                + PROCESSING_OUTPUT_COLUMNS * 18)
                .setTop(panelRelY + PROCESSING_OUTPUT_OFFSET_Y)
                .setHeight(PROCESSING_OUTPUT_ROWS * 18 - 2);

        final ContainerWirelessDualInterfaceTerminal container = host.getDualContainer();
        final int totalPages = container.getTotalPages();
        this.processingScrollBar.setRange(0, Math.max(0, totalPages - 1), 1);
        this.processingScrollBar.setCurrentScroll(container.getActivePage());
    }

    private int getTotalProcessingInputPages() {
        return Math.max(1, (PatternHelper.PROCESSING_INPUT_LIMIT
                + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS - 1)
                / PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
    }

    public void updateProcessingInputScrollbar() {
        final int panelRelX = getPanelX();
        final int panelRelY = getPanelY();
        this.processingInputPage = Math.min(this.processingInputPage,
                this.getTotalProcessingInputPages() - 1);
        this.processingInputScrollbar.setLeft(panelRelX + this.getProcessingGridOffsetX()
                + PROCESSING_INPUT_WIDTH * 18 + 4)
                .setTop(panelRelY + PROCESSING_GRID_OFFSET_Y)
                .setHeight(PROCESSING_INPUT_ROWS * 18 - 2);
        this.processingInputScrollbar.setRange(0,
                Math.max(0, this.getTotalProcessingInputPages() - 1), 1);
        this.processingInputScrollbar.setCurrentScroll(this.processingInputPage);
    }

    private boolean updatePatternInputScrollFromMouse(final int mouseX, final int mouseY) {
        if (host.getDualContainer().isCraftingMode() || this.getTotalProcessingInputPages() <= 1) {
            return false;
        }

        this.updateProcessingInputScrollbar();
        final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
        this.processingInputScrollbar.click(host.getPanel(), mouseX - host.getGuiLeft(),
                mouseY - host.getGuiTop());
        if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
            this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
            return true;
        }
        return false;
    }

    private boolean updatePatternOutputScrollFromMouse(final int mouseX, final int mouseY) {
        if (host.getDualContainer().isCraftingMode() || host.getDualContainer().getTotalPages() <= 1) {
            return false;
        }

        final int oldScroll = this.processingScrollBar.getCurrentScroll();
        this.processingScrollBar.click(host.getPanel(), mouseX - host.getGuiLeft(),
                mouseY - host.getGuiTop());
        if (oldScroll != this.processingScrollBar.getCurrentScroll()) {
            this.sendActivePageUpdate();
            return true;
        }
        return false;
    }

    private void setProcessingInputPage(final int page) {
        this.processingInputPage = Math.max(0,
                Math.min(page, this.getTotalProcessingInputPages() - 1));
        this.repositionSlots();
    }

    // ========== Area detection ==========

    private boolean isMouseOverProcessingInputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = host.getGuiLeft() + getPanelX();
        final int panelAbsY = host.getGuiTop() + getPanelY();
        final int left = panelAbsX + this.getProcessingGridOffsetX();
        final int top = panelAbsY + PROCESSING_GRID_OFFSET_Y;
        final int right = left + PROCESSING_INPUT_WIDTH * 18 + 4 + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private boolean isMouseOverProcessingOutputArea(final int mouseX, final int mouseY) {
        final int panelAbsX = host.getGuiLeft() + getPanelX();
        final int panelAbsY = host.getGuiTop() + getPanelY();
        final int left = panelAbsX + this.getProcessingOutputOffsetX();
        final int top = panelAbsY + PROCESSING_OUTPUT_OFFSET_Y;
        final int right = left + PROCESSING_OUTPUT_COLUMNS * 18;
        final int bottom = top + PROCESSING_OUTPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    /**
     * Gets the panel's absolute screen coordinates (for JEI exclusion area).
     */
    public java.awt.Rectangle getJEIExclusionRect() {
        final int panelScreenX = host.getGuiLeft() + getPanelX();
        final int panelScreenY = host.getGuiTop() + getPanelY();
        return new java.awt.Rectangle(panelScreenX, panelScreenY,
                PATTERN_PANEL_WIDTH, PATTERN_PANEL_TOTAL_HEIGHT);
    }
}

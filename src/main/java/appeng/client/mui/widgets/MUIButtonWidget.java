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

package appeng.client.mui.widgets;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.translation.I18n;

import appeng.api.config.*;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.core.localization.ButtonToolTips;

/**
 * MUI settings-aware button widget. Full replacement for legacy {@code GuiImgButton}.
 * <p>
 * Supports two modes:
 * <ol>
 *   <li><b>Settings mode</b>: constructed with {@code (x, y, Settings, Enum)} — uses the
 *       static appearance registry to resolve icon index and tooltip text, exactly like
 *       the legacy {@code GuiImgButton}.</li>
 *   <li><b>Custom mode</b>: constructed with {@code (x, y)} or {@code (x, y, w, h)} — uses
 *       a user-supplied {@link IIconRenderer} for icon drawing and a manual tooltip string.</li>
 * </ol>
 *
 * @see ITooltip for the tooltip contract used by {@link AEBasePanel#drawTooltip(ITooltip, int, int)}
 */
public class MUIButtonWidget implements IMUIWidget, ITooltip {

    // ========== Icon renderer interface (custom mode) ==========

    /**
     * Custom icon renderer for non-settings buttons.
     */
    @FunctionalInterface
    public interface IIconRenderer {
        /**
         * Draw the icon within the button area.
         *
         * @param mc      Minecraft instance
         * @param screenX button screen-left X
         * @param screenY button screen-top Y
         * @param width   button width
         * @param height  button height
         * @param hovered whether the mouse is hovering
         */
        void render(Minecraft mc, int screenX, int screenY, int width, int height, boolean hovered);
    }

    // ========== Appearance registry (settings mode) ==========

    private static final Pattern COMPILE = Pattern.compile("%s");
    private static final Pattern PATTERN_NEW_LINE = Pattern.compile("\\n", Pattern.LITERAL);

    @Nullable
    private static Map<EnumPair, ButtonAppearance> appearances;

    /**
     * Settings + value pair key for the appearance registry.
     */
    public static final class EnumPair {
        final Enum<?> setting;
        final Enum<?> value;

        EnumPair(final Enum<?> a, final Enum<?> b) {
            this.setting = a;
            this.value = b;
        }

        @Override
        public int hashCode() {
            return this.setting.hashCode() ^ this.value.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == null) {
                return false;
            }
            if (this.getClass() != obj.getClass()) {
                return false;
            }
            final EnumPair other = (EnumPair) obj;
            return other.setting == this.setting && other.value == this.value;
        }
    }

    /**
     * Visual appearance entry: icon atlas index + localizable tooltip strings.
     */
    private static class ButtonAppearance {
        int index;
        String displayName;
        String displayValue;
    }

    /**
     * Initialize the static appearance registry (called lazily on first settings-mode button creation).
     * <p>
     * Mirrors the legacy {@code GuiImgButton} registration table exactly.
     */
    private static void ensureAppearancesInitialized() {
        if (appearances != null) {
            return;
        }
        appearances = new HashMap<>();

        // Condenser
        reg(16 * 7, Settings.CONDENSER_OUTPUT, CondenserOutput.TRASH,
                ButtonToolTips.CondenserOutput, ButtonToolTips.Trash);
        reg(16 * 7 + 1, Settings.CONDENSER_OUTPUT, CondenserOutput.MATTER_BALLS,
                ButtonToolTips.CondenserOutput, ButtonToolTips.MatterBalls);
        reg(16 * 7 + 2, Settings.CONDENSER_OUTPUT, CondenserOutput.SINGULARITY,
                ButtonToolTips.CondenserOutput, ButtonToolTips.Singularity);

        // Access
        reg(16 * 9 + 1, Settings.ACCESS, AccessRestriction.READ, ButtonToolTips.IOMode, ButtonToolTips.Read);
        reg(16 * 9, Settings.ACCESS, AccessRestriction.WRITE, ButtonToolTips.IOMode, ButtonToolTips.Write);
        reg(16 * 9 + 2, Settings.ACCESS, AccessRestriction.READ_WRITE, ButtonToolTips.IOMode,
                ButtonToolTips.ReadWrite);

        // Power units
        reg(16 * 10, Settings.POWER_UNITS, PowerUnits.AE, ButtonToolTips.PowerUnits,
                PowerUnits.AE.unlocalizedName);
        reg(16 * 10 + 1, Settings.POWER_UNITS, PowerUnits.EU, ButtonToolTips.PowerUnits,
                PowerUnits.EU.unlocalizedName);
        reg(16 * 10 + 4, Settings.POWER_UNITS, PowerUnits.RF, ButtonToolTips.PowerUnits,
                PowerUnits.RF.unlocalizedName);
        reg(16 * 10 + 1, Settings.POWER_UNITS, PowerUnits.GTEU, ButtonToolTips.PowerUnits,
                PowerUnits.EU.unlocalizedName);

        // Redstone controlled
        reg(3, Settings.REDSTONE_CONTROLLED, RedstoneMode.IGNORE, ButtonToolTips.RedstoneMode,
                ButtonToolTips.AlwaysActive);
        reg(0, Settings.REDSTONE_CONTROLLED, RedstoneMode.LOW_SIGNAL, ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveWithoutSignal);
        reg(1, Settings.REDSTONE_CONTROLLED, RedstoneMode.HIGH_SIGNAL, ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveWithSignal);
        reg(2, Settings.REDSTONE_CONTROLLED, RedstoneMode.SIGNAL_PULSE, ButtonToolTips.RedstoneMode,
                ButtonToolTips.ActiveOnPulse);

        // Redstone emitter
        reg(0, Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL, ButtonToolTips.RedstoneMode,
                ButtonToolTips.EmitLevelsBelow);
        reg(1, Settings.REDSTONE_EMITTER, RedstoneMode.HIGH_SIGNAL, ButtonToolTips.RedstoneMode,
                ButtonToolTips.EmitLevelAbove);

        // Operation / IO direction
        reg(51, Settings.OPERATION_MODE, OperationMode.FILL, ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToStorageCell);
        reg(50, Settings.OPERATION_MODE, OperationMode.EMPTY, ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToNetwork);
        reg(51, Settings.IO_DIRECTION, RelativeDirection.LEFT, ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToStorageCell);
        reg(50, Settings.IO_DIRECTION, RelativeDirection.RIGHT, ButtonToolTips.TransferDirection,
                ButtonToolTips.TransferToNetwork);

        // Sort direction
        reg(48, Settings.SORT_DIRECTION, SortDir.ASCENDING, ButtonToolTips.SortOrder,
                ButtonToolTips.ToggleSortDirection);
        reg(49, Settings.SORT_DIRECTION, SortDir.DESCENDING, ButtonToolTips.SortOrder,
                ButtonToolTips.ToggleSortDirection);

        // Search mode
        reg(16 * 2 + 3, Settings.SEARCH_MODE, SearchBoxMode.AUTOSEARCH,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_Auto);
        reg(16 * 2 + 4, Settings.SEARCH_MODE, SearchBoxMode.MANUAL_SEARCH,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_Standard);
        reg(16 * 2 + 5, Settings.SEARCH_MODE, SearchBoxMode.JEI_AUTOSEARCH,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_JEIAuto);
        reg(16 * 2 + 6, Settings.SEARCH_MODE, SearchBoxMode.JEI_MANUAL_SEARCH,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_JEIStandard);
        reg(16 * 2 + 7, Settings.SEARCH_MODE, SearchBoxMode.AUTOSEARCH_KEEP,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_AutoKeep);
        reg(16 * 2 + 8, Settings.SEARCH_MODE, SearchBoxMode.MANUAL_SEARCH_KEEP,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_StandardKeep);
        reg(16 * 2 + 9, Settings.SEARCH_MODE, SearchBoxMode.JEI_AUTOSEARCH_KEEP,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_JEIAutoKeep);
        reg(16 * 2 + 10, Settings.SEARCH_MODE, SearchBoxMode.JEI_MANUAL_SEARCH_KEEP,
                ButtonToolTips.SearchMode, ButtonToolTips.SearchMode_JEIStandardKeep);

        // Level type
        reg(16 * 5 + 3, Settings.LEVEL_TYPE, LevelType.ENERGY_LEVEL, ButtonToolTips.LevelType,
                ButtonToolTips.LevelType_Energy);
        reg(16 * 4 + 3, Settings.LEVEL_TYPE, LevelType.ITEM_LEVEL, ButtonToolTips.LevelType,
                ButtonToolTips.LevelType_Item);

        // Terminal style
        reg(16 * 13, Settings.TERMINAL_STYLE, TerminalStyle.TALL, ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Tall);
        reg(16 * 13 + 1, Settings.TERMINAL_STYLE, TerminalStyle.SMALL, ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Small);
        reg(16 * 13 + 2, Settings.TERMINAL_STYLE, TerminalStyle.FULL, ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Full);
        reg(16 * 13 + 3, Settings.TERMINAL_STYLE, TerminalStyle.MEDIUM, ButtonToolTips.TerminalStyle,
                ButtonToolTips.TerminalStyle_Medium);

        // Sort by
        reg(64, Settings.SORT_BY, SortOrder.NAME, ButtonToolTips.SortBy, ButtonToolTips.ItemName);
        reg(65, Settings.SORT_BY, SortOrder.AMOUNT, ButtonToolTips.SortBy, ButtonToolTips.NumberOfItems);
        reg(68, Settings.SORT_BY, SortOrder.INVTWEAKS, ButtonToolTips.SortBy, ButtonToolTips.InventoryTweaks);
        reg(69, Settings.SORT_BY, SortOrder.MOD, ButtonToolTips.SortBy, ButtonToolTips.Mod);

        // Actions
        reg(66, Settings.ACTIONS, ActionItems.WRENCH, ButtonToolTips.PartitionStorage,
                ButtonToolTips.PartitionStorageHint);
        reg(6, Settings.ACTIONS, ActionItems.CLOSE, ButtonToolTips.Clear, ButtonToolTips.ClearSettings);
        reg(6, Settings.ACTIONS, ActionItems.STASH, ButtonToolTips.Stash, ButtonToolTips.StashDesc);

        reg(6 + 4 * 16, Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO,
                ButtonToolTips.MultiplyByTwo, ButtonToolTips.MultiplyByTwoDesc);
        reg(7 + 4 * 16, Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE,
                ButtonToolTips.MultiplyByThree, ButtonToolTips.MultiplyByThreeDesc);
        reg(8 + 4 * 16, Settings.ACTIONS, ActionItems.INCREASE_BY_ONE,
                ButtonToolTips.IncreaseByOne, ButtonToolTips.IncreaseByOneDesc);
        reg(9 + 4 * 16, Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO,
                ButtonToolTips.DivideByTwo, ButtonToolTips.DivideByTwoDesc);
        reg(10 + 4 * 16, Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE,
                ButtonToolTips.DivideByThree, ButtonToolTips.DivideByThreeDesc);
        reg(11 + 4 * 16, Settings.ACTIONS, ActionItems.DECREASE_BY_ONE,
                ButtonToolTips.DecreaseByOne, ButtonToolTips.DecreaseByOneDesc);
        reg(12 + 4 * 16, Settings.ACTIONS, ActionItems.MAX_COUNT,
                ButtonToolTips.MaxCount, ButtonToolTips.MaxCountDesc);

        reg(6 + 5 * 16, Settings.ACTIONS, ActionItems.MOLECULAR_ASSEMBLERS_ON,
                ButtonToolTips.ToggleMolecularAssemblers, ButtonToolTips.ToggleMolecularAssemblersOnDesc);
        reg(7 + 5 * 16, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_FULL_INTERFACES_ON,
                ButtonToolTips.ToggleShowFullInterfaces, ButtonToolTips.ToggleShowFullInterfacesOnDesc);
        reg(8 + 5 * 16, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_FULL_INTERFACES_OFF,
                ButtonToolTips.ToggleShowFullInterfaces, ButtonToolTips.ToggleShowFullInterfacesOffDesc);
        reg(9 + 5 * 16, Settings.ACTIONS, ActionItems.MOLECULAR_ASSEMBLERS_OFF,
                ButtonToolTips.ToggleMolecularAssemblers, ButtonToolTips.ToggleMolecularAssemblersOffDesc);
        reg(6 + 6 * 16, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE,
                ButtonToolTips.HighlightInterface, "");
        reg(6 + 4 * 16, Settings.ACTIONS, ActionItems.DOUBLE_STACKS,
                ButtonToolTips.DoubleStacks, ButtonToolTips.DoubleStacksDesc);
        reg(4 + 5 * 16, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_OFF,
                ButtonToolTips.ToggleShowOnlyInvalidInterface,
                ButtonToolTips.ToggleShowOnlyInvalidInterfaceOffDesc);
        reg(5 + 5 * 16, Settings.ACTIONS, ActionItems.TOGGLE_SHOW_ONLY_INVALID_PATTERNS_ON,
                ButtonToolTips.ToggleShowOnlyInvalidInterface, ButtonToolTips.ToggleShowOnlyInvalidInterfaceOnDesc);

        reg(8, Settings.ACTIONS, ActionItems.ENCODE, ButtonToolTips.Encode, ButtonToolTips.EncodeDescription);
        reg(14 + 5 * 16, Settings.ACTIONS, ActionItems.PINS, ButtonToolTips.Pins, ButtonToolTips.PinsDesc);
        reg(4 + 3 * 16, Settings.ACTIONS, ItemSubstitution.ENABLED, ButtonToolTips.Substitutions,
                ButtonToolTips.SubstitutionsDescEnabled);
        reg(7 + 3 * 16, Settings.ACTIONS, ItemSubstitution.DISABLED, ButtonToolTips.Substitutions,
                ButtonToolTips.SubstitutionsDescDisabled);

        // Combine mode
        reg(5 + 3 * 16, Settings.ACTIONS, CombineMode.ENABLED, ButtonToolTips.CombineMode,
                ButtonToolTips.CombineModeYes);
        reg(8 + 3 * 16, Settings.ACTIONS, CombineMode.DISABLED, ButtonToolTips.CombineMode,
                ButtonToolTips.CombineModeNo);

        // View mode
        reg(16, Settings.VIEW_MODE, ViewItems.STORED, ButtonToolTips.View, ButtonToolTips.StoredItems);
        reg(18, Settings.VIEW_MODE, ViewItems.ALL, ButtonToolTips.View, ButtonToolTips.StoredCraftable);
        reg(19, Settings.VIEW_MODE, ViewItems.CRAFTABLE, ButtonToolTips.View, ButtonToolTips.Craftable);

        // Fuzzy mode
        reg(16 * 6, Settings.FUZZY_MODE, FuzzyMode.PERCENT_25, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_25);
        reg(16 * 6 + 1, Settings.FUZZY_MODE, FuzzyMode.PERCENT_50, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_50);
        reg(16 * 6 + 2, Settings.FUZZY_MODE, FuzzyMode.PERCENT_75, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_75);
        reg(16 * 6 + 3, Settings.FUZZY_MODE, FuzzyMode.PERCENT_99, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZPercent_99);
        reg(16 * 6 + 4, Settings.FUZZY_MODE, FuzzyMode.IGNORE_ALL, ButtonToolTips.FuzzyMode,
                ButtonToolTips.FZIgnoreAll);

        // Fullness mode
        reg(80, Settings.FULLNESS_MODE, FullnessMode.EMPTY, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenEmpty);
        reg(81, Settings.FULLNESS_MODE, FullnessMode.HALF, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenWorkIsDone);
        reg(82, Settings.FULLNESS_MODE, FullnessMode.FULL, ButtonToolTips.OperationMode,
                ButtonToolTips.MoveWhenFull);

        // Block / craft only
        reg(16 + 5, Settings.BLOCK, YesNo.YES, ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.Blocking);
        reg(16 + 4, Settings.BLOCK, YesNo.NO, ButtonToolTips.InterfaceBlockingMode, ButtonToolTips.NonBlocking);
        reg(16 + 3, Settings.CRAFT_ONLY, YesNo.YES, ButtonToolTips.Craft, ButtonToolTips.CraftOnly);
        reg(16 + 2, Settings.CRAFT_ONLY, YesNo.NO, ButtonToolTips.Craft, ButtonToolTips.CraftEither);

        // Craft via redstone
        reg(16 * 11 + 2, Settings.CRAFT_VIA_REDSTONE, YesNo.YES, ButtonToolTips.EmitterMode,
                ButtonToolTips.CraftViaRedstone);
        reg(16 * 11 + 1, Settings.CRAFT_VIA_REDSTONE, YesNo.NO, ButtonToolTips.EmitterMode,
                ButtonToolTips.EmitWhenCrafting);

        // Storage filter
        reg(16 * 3 + 5, Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY,
                ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsNo);
        reg(16 * 3 + 6, Settings.STORAGE_FILTER, StorageFilter.NONE,
                ButtonToolTips.ReportInaccessibleItems, ButtonToolTips.ReportInaccessibleItemsYes);

        // Block placement
        reg(16 * 14, Settings.PLACE_BLOCK, YesNo.YES, ButtonToolTips.BlockPlacement,
                ButtonToolTips.BlockPlacementYes);
        reg(16 * 14 + 1, Settings.PLACE_BLOCK, YesNo.NO, ButtonToolTips.BlockPlacement,
                ButtonToolTips.BlockPlacementNo);

        // Scheduling mode
        reg(16 * 15, Settings.SCHEDULING_MODE, SchedulingMode.DEFAULT, ButtonToolTips.SchedulingMode,
                ButtonToolTips.SchedulingModeDefault);
        reg(16 * 15 + 1, Settings.SCHEDULING_MODE, SchedulingMode.ROUNDROBIN,
                ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRoundRobin);
        reg(16 * 15 + 2, Settings.SCHEDULING_MODE, SchedulingMode.RANDOM,
                ButtonToolTips.SchedulingMode, ButtonToolTips.SchedulingModeRandom);

        // Lock crafting mode
        reg(10, Settings.UNLOCK, LockCraftingMode.NONE, ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingModeNone);
        reg(7, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_RESULT, ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingUntilResultReturned);
        reg(0, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_LOW, ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingWhileRedstoneLow);
        reg(1, Settings.UNLOCK, LockCraftingMode.LOCK_WHILE_HIGH, ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingWhileRedstoneHigh);
        reg(2, Settings.UNLOCK, LockCraftingMode.LOCK_UNTIL_PULSE, ButtonToolTips.LockCraftingMode,
                ButtonToolTips.LockCraftingUntilRedstonePulse);
    }

    /**
     * Register a button appearance entry with ButtonToolTips hint.
     */
    private static void reg(int iconIndex, Settings setting, Enum<?> val,
            ButtonToolTips title, ButtonToolTips hint) {
        reg(iconIndex, setting, val, title, hint.getUnlocalized());
    }

    /**
     * Register a button appearance entry with raw string hint.
     */
    private static void reg(int iconIndex, Settings setting, Enum<?> val,
            ButtonToolTips title, String hint) {
        final ButtonAppearance a = new ButtonAppearance();
        a.displayName = title.getUnlocalized();
        a.displayValue = hint;
        a.index = iconIndex;
        appearances.put(new EnumPair(setting, val), a);
    }

    // ========== Constants ==========

    private static final ResourceLocation STATES_TEXTURE = new ResourceLocation("appliedenergistics2",
            "textures/guis/states.png");

    // ========== Instance fields ==========

    private int x;
    private int y;
    private int width;
    private int height;
    private boolean visible = true;
    private boolean enabled = true;
    private boolean halfSize = false;
    private boolean hovered = false;

    // Custom mode fields
    @Nullable
    private IIconRenderer iconRenderer;
    @Nullable
    private Consumer<MUIButtonWidget> onClick;
    @Nullable
    private String tooltip;

    // Settings mode fields
    @Nullable
    private Enum<?> buttonSetting;
    @Nullable
    private Enum<?> currentValue;
    @Nullable
    private String fillVar;

    // ========== Constructors ==========

    /**
     * Custom mode constructor (no Settings association).
     */
    public MUIButtonWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.buttonSetting = null;
        this.currentValue = null;
    }

    /**
     * Custom mode constructor with default 16x16 size.
     */
    public MUIButtonWidget(int x, int y) {
        this(x, y, 16, 16);
    }

    /**
     * Settings mode constructor. Equivalent to {@code new GuiImgButton(x, y, setting, val)}.
     * <p>
     * Uses the static appearance registry to resolve icon and tooltip.
     *
     * @param x       panel-relative X coordinate
     * @param y       panel-relative Y coordinate
     * @param setting the Settings enum (e.g. {@code Settings.SORT_BY})
     * @param val     the initial value enum (e.g. {@code SortOrder.NAME})
     */
    public MUIButtonWidget(int x, int y, Enum<?> setting, Enum<?> val) {
        ensureAppearancesInitialized();
        this.x = x;
        this.y = y;
        this.width = 16;
        this.height = 16;
        this.buttonSetting = setting;
        this.currentValue = val;
    }

    // ========== Reinitialization (for button pool reuse) ==========

    /**
     * Reinitialize this button as a settings-mode button. Used by {@link MUIButtonPool}
     * to reuse button instances across frames without allocation.
     *
     * @param x       panel-relative X coordinate
     * @param y       panel-relative Y coordinate
     * @param setting the Settings enum
     * @param val     the initial value enum
     */
    public void reinitAsSettings(int x, int y, Enum<?> setting, Enum<?> val) {
        ensureAppearancesInitialized();
        this.x = x;
        this.y = y;
        this.width = 16;
        this.height = 16;
        this.buttonSetting = setting;
        this.currentValue = val;
        this.iconRenderer = null;
    }

    /**
     * Reinitialize this button as a custom-mode button. Used by {@link MUIButtonPool}
     * to reuse button instances across frames without allocation.
     *
     * @param x      panel-relative X coordinate
     * @param y      panel-relative Y coordinate
     * @param width  button width
     * @param height button height
     */
    public void reinitAsCustom(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.buttonSetting = null;
        this.currentValue = null;
        this.iconRenderer = null;
    }

    // ========== Drawing ==========

    @Override
    public void drawBackground(AEBasePanel panel, int guiLeft, int guiTop,
            int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }

        final int screenX = guiLeft + this.x;
        final int screenY = guiTop + this.y;
        final int finalW = this.halfSize ? this.width / 2 : this.width;
        final int finalH = this.halfSize ? this.height / 2 : this.height;

        this.hovered = mouseX >= screenX && mouseY >= screenY
                && mouseX < screenX + finalW && mouseY < screenY + finalH;

        if (!this.enabled) {
            GlStateManager.color(0.5f, 0.5f, 0.5f, 1.0f);
        } else {
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }

        final Minecraft mc = Minecraft.getMinecraft();

        if (this.halfSize) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(screenX, screenY, 0);
            GlStateManager.scale(0.5f, 0.5f, 1.0f);

            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            Gui.drawModalRectWithCustomSizedTexture(0, 0, 256 - this.width, 256 - this.height,
                    this.width, this.height, 256, 256);

            if (this.buttonSetting != null) {
                drawSettingsIcon(mc, 0, 0);
            } else if (this.iconRenderer != null) {
                this.iconRenderer.render(mc, 0, 0, this.width, this.height, this.hovered);
            }

            GlStateManager.popMatrix();
        } else {
            mc.getTextureManager().bindTexture(STATES_TEXTURE);
            Gui.drawModalRectWithCustomSizedTexture(screenX, screenY,
                    256 - this.width, 256 - this.height, this.width, this.height, 256, 256);

            if (this.buttonSetting != null) {
                drawSettingsIcon(mc, screenX, screenY);
            } else if (this.iconRenderer != null) {
                this.iconRenderer.render(mc, screenX, screenY, this.width, this.height, this.hovered);
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Draw the icon from the appearance registry at the given screen coordinates.
     */
    private void drawSettingsIcon(Minecraft mc, int drawX, int drawY) {
        final int iconIndex = this.getIconIndex();
        final int uvY = iconIndex / 16;
        final int uvX = iconIndex - uvY * 16;

        mc.getTextureManager().bindTexture(STATES_TEXTURE);
        Gui.drawModalRectWithCustomSizedTexture(drawX, drawY, uvX * 16, uvY * 16, 16, 16, 256, 256);
    }

    /**
     * Resolve the icon atlas index from the appearance registry.
     */
    private int getIconIndex() {
        if (this.buttonSetting != null && this.currentValue != null && appearances != null) {
            final ButtonAppearance app = appearances.get(new EnumPair(this.buttonSetting, this.currentValue));
            if (app != null) {
                return app.index;
            }
        }
        return 256 - 1;
    }

    @Override
    public void drawForeground(AEBasePanel panel, int localX, int localY) {
        // Tooltip rendering is handled by AEBasePanel.drawTooltip(ITooltip, ...) via the ITooltip interface.
    }

    // ========== Input events ==========

    @Override
    public boolean mouseClicked(int localX, int localY, int mouseButton) {
        if (!this.visible || !this.enabled) {
            return false;
        }

        final int finalW = this.halfSize ? this.width / 2 : this.width;
        final int finalH = this.halfSize ? this.height / 2 : this.height;

        if (localX >= this.x && localY >= this.y
                && localX < this.x + finalW && localY < this.y + finalH) {
            if (this.onClick != null) {
                this.onClick.accept(this);
            }
            return true;
        }
        return false;
    }

    // ========== ITooltip implementation ==========

    @Override
    public String getMessage() {
        // Settings mode: generate tooltip from appearance registry
        if (this.buttonSetting != null && this.currentValue != null && appearances != null) {
            final ButtonAppearance app = appearances.get(new EnumPair(this.buttonSetting, this.currentValue));
            if (app == null) {
                return "No Such Message";
            }

            String name = I18n.translateToLocal(app.displayName);
            String value = I18n.translateToLocal(app.displayValue);

            if (name == null || name.isEmpty()) {
                name = app.displayName;
            }
            if (value == null || value.isEmpty()) {
                value = app.displayValue;
            }

            if (this.fillVar != null) {
                value = COMPILE.matcher(value).replaceFirst(this.fillVar);
            }

            value = PATTERN_NEW_LINE.matcher(value).replaceAll("\n");
            final StringBuilder sb = new StringBuilder(value);

            int i = sb.lastIndexOf("\n");
            if (i <= 0) {
                i = 0;
            }
            while (i + 30 < sb.length() && (i = sb.lastIndexOf(" ", i + 30)) != -1) {
                sb.replace(i, i + 1, "\n");
            }

            return name + '\n' + sb;
        }

        // Custom mode: return manual tooltip
        return this.tooltip;
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    // ========== Settings mode accessors ==========

    /**
     * Get the Settings enum this button is associated with (settings mode only).
     *
     * @return the Settings enum, or null if in custom mode
     */
    @Nullable
    public Settings getSetting() {
        return this.buttonSetting instanceof Settings ? (Settings) this.buttonSetting : null;
    }

    /**
     * Get the current value enum.
     */
    @Nullable
    public Enum<?> getCurrentValue() {
        return this.currentValue;
    }

    /**
     * Set the current value enum (updates icon and tooltip automatically).
     *
     * @param e the new value
     */
    public void set(final Enum<?> e) {
        if (this.currentValue != e) {
            this.currentValue = e;
        }
    }

    /**
     * Set the fill variable for tooltip text substitution (replaces %s in the value string).
     */
    public void setFillVar(@Nullable String fillVar) {
        this.fillVar = fillVar;
    }

    @Nullable
    public String getFillVar() {
        return this.fillVar;
    }

    /**
     * Convenience: set visibility and enabled state together (mirrors legacy GuiImgButton.setVisibility).
     */
    public void setVisibility(boolean vis) {
        this.visible = vis;
        this.enabled = vis;
    }

    // ========== Common property accessors ==========

    public MUIButtonWidget setIconRenderer(@Nullable IIconRenderer renderer) {
        this.iconRenderer = renderer;
        return this;
    }

    public MUIButtonWidget setOnClick(@Nullable Consumer<MUIButtonWidget> onClick) {
        this.onClick = onClick;
        return this;
    }

    public MUIButtonWidget setTooltip(@Nullable String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    public MUIButtonWidget setVisible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public MUIButtonWidget setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public MUIButtonWidget setHalfSize(boolean halfSize) {
        this.halfSize = halfSize;
        return this;
    }

    public boolean isHalfSize() {
        return this.halfSize;
    }

    public MUIButtonWidget setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public boolean isHovered() {
        return this.hovered;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    @Nullable
    public String getTooltip() {
        return this.tooltip;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.halfSize ? this.width / 2 : this.width;
    }

    @Override
    public int getHeight() {
        return this.halfSize ? this.height / 2 : this.height;
    }
}

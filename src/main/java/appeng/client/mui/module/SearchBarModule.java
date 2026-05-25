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

package appeng.client.mui.module;

import java.util.function.Consumer;

import javax.annotation.Nullable;

import appeng.api.config.SearchBoxFocusPriority;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.core.AEConfig;
import appeng.integration.Integrations;
import appeng.util.Platform;

/**
 * Search bar module — reusable component for all search-bar-related behavior.
 *
 * <p>Supports two search modes via {@link SearchMode}:
 * <ul>
 *   <li>{@link SearchMode#SINGLE} — single search field (ME terminal, interface configuration terminal)</li>
 *   <li>{@link SearchMode#TRIPLE} — three search fields: inputs / outputs / names (interface terminal)</li>
 * </ul>
 *
 * <p>Responsible for:
 * <ul>
 *   <li>Search field creation and registration (via {@link SearchFieldSpec} / {@link SearchFieldGroup})</li>
 *   <li>Terminal search mode configuration ({@link TerminalSearchConfig})</li>
 *   <li>Keyboard event handling (auto-focus, space suppression, focus priority, Tab cycling)</li>
 *   <li>Mouse click handling (focus, right-click clear)</li>
 *   <li>Memory text persistence and JEI synchronization</li>
 *   <li>Search text change notification</li>
 * </ul>
 *
 * <p>Pages should delegate all search-bar behavior to this module instead of
 * directly managing search field widgets.
 */
public class SearchBarModule {

    // ========== Search mode ==========

    /**
     * Search mode: determines how many search fields the module manages.
     */
    public enum SearchMode {
        /** Single search field (ME terminal, interface configuration terminal). */
        SINGLE,
        /** Three search fields: inputs / outputs / names (interface terminal). */
        TRIPLE
    }

    // ========== Host interface ==========

    /**
     * The host GUI must implement this interface to provide context for the search bar module.
     */
    public interface Host {
        /**
         * Get the host panel for widget registration.
         */
        AEBasePanel getPanel();

        /**
         * Get the absolute left coordinate of the GUI.
         */
        int getGuiLeft();

        /**
         * Get the absolute top coordinate of the GUI.
         */
        int getGuiTop();

        /**
         * Request the host to reinitialize the GUI.
         */
        void requestReinitialize();
    }

    // ========== Search field style constants ==========

    /**
     * Search field default style constants, shared across all terminal pages.
     */
    public static final class SearchFieldStyle {
        public static final int DEFAULT_HEIGHT = 12;
        public static final int DEFAULT_MAX_LENGTH = 25;
        public static final int DEFAULT_TEXT_COLOR = 0xFFFFFF;

        private SearchFieldStyle() {
        }
    }

    // ========== Search field spec ==========

    /**
     * Search field construction parameters.
     */
    public static class SearchFieldSpec {
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        @Nullable
        private final String tooltip;
        @Nullable
        private final Consumer<String> textChangeListener;
        private final boolean focused;

        protected SearchFieldSpec(Builder builder) {
            this.x = builder.x;
            this.y = builder.y;
            this.width = builder.width;
            this.height = builder.height;
            this.tooltip = builder.tooltip;
            this.textChangeListener = builder.textChangeListener;
            this.focused = builder.focused;
        }

        /**
         * Copy constructor for subclass delegation.
         */
        protected SearchFieldSpec(SearchFieldSpec other) {
            this.x = other.x;
            this.y = other.y;
            this.width = other.width;
            this.height = other.height;
            this.tooltip = other.tooltip;
            this.textChangeListener = other.textChangeListener;
            this.focused = other.focused;
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

        @Nullable
        public String getTooltip() {
            return this.tooltip;
        }

        @Nullable
        public Consumer<String> getTextChangeListener() {
            return this.textChangeListener;
        }

        public boolean isFocused() {
            return this.focused;
        }

        public static Builder builder(int x, int y, int width) {
            return new Builder(x, y, width);
        }

        public static final class Builder {
            private final int x;
            private final int y;
            private final int width;
            private int height = SearchFieldStyle.DEFAULT_HEIGHT;
            @Nullable
            private String tooltip;
            @Nullable
            private Consumer<String> textChangeListener;
            private boolean focused;

            private Builder(int x, int y, int width) {
                this.x = x;
                this.y = y;
                this.width = width;
            }

            public Builder tooltip(@Nullable String tooltip) {
                this.tooltip = tooltip;
                return this;
            }

            public Builder onTextChange(@Nullable Consumer<String> textChangeListener) {
                this.textChangeListener = textChangeListener;
                return this;
            }

            public Builder focused(boolean focused) {
                this.focused = focused;
                return this;
            }

            public Builder height(int height) {
                this.height = height;
                return this;
            }

            public SearchFieldSpec build() {
                return new SearchFieldSpec(this);
            }
        }
    }

    // ========== Search field group (triple mode) ==========

    /**
     * Search field group for the inputs / outputs / names triple-search scenario.
     */
    public static class SearchFieldGroup {
        @Nullable
        private final SearchFieldSpec inputs;
        @Nullable
        private final SearchFieldSpec outputs;
        @Nullable
        private final SearchFieldSpec names;

        protected SearchFieldGroup(Builder builder) {
            this.inputs = builder.inputs;
            this.outputs = builder.outputs;
            this.names = builder.names;
        }

        /**
         * Constructor for subclass delegation with explicit field values.
         */
        protected SearchFieldGroup(@Nullable SearchFieldSpec inputs,
                @Nullable SearchFieldSpec outputs,
                @Nullable SearchFieldSpec names) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.names = names;
        }

        @Nullable
        public SearchFieldSpec getInputs() {
            return this.inputs;
        }

        @Nullable
        public SearchFieldSpec getOutputs() {
            return this.outputs;
        }

        @Nullable
        public SearchFieldSpec getNames() {
            return this.names;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            @Nullable
            private SearchFieldSpec inputs;
            @Nullable
            private SearchFieldSpec outputs;
            @Nullable
            private SearchFieldSpec names;

            private Builder() {
            }

            public Builder inputs(@Nullable SearchFieldSpec inputs) {
                this.inputs = inputs;
                return this;
            }

            public Builder outputs(@Nullable SearchFieldSpec outputs) {
                this.outputs = outputs;
                return this;
            }

            public Builder names(@Nullable SearchFieldSpec names) {
                this.names = names;
                return this;
            }

            public SearchFieldGroup build() {
                return new SearchFieldGroup(this);
            }
        }
    }

    // ========== Registered search field widgets (triple mode) ==========

    /**
     * Registered search field widget instances (triple mode).
     */
    public static class SearchFieldWidgets {
        @Nullable
        private final MUITextFieldWidget inputs;
        @Nullable
        private final MUITextFieldWidget outputs;
        @Nullable
        private final MUITextFieldWidget names;

        public SearchFieldWidgets(@Nullable MUITextFieldWidget inputs,
                @Nullable MUITextFieldWidget outputs,
                @Nullable MUITextFieldWidget names) {
            this.inputs = inputs;
            this.outputs = outputs;
            this.names = names;
        }

        @Nullable
        public MUITextFieldWidget getInputs() {
            return this.inputs;
        }

        @Nullable
        public MUITextFieldWidget getOutputs() {
            return this.outputs;
        }

        @Nullable
        public MUITextFieldWidget getNames() {
            return this.names;
        }
    }

    // ========== Terminal search config ==========

    /**
     * Terminal search mode configuration, derived from {@link SearchBoxMode} settings.
     *
     * <p>Encapsulates the three boolean flags that control terminal search behavior:
     * auto-focus, keep-filter (memory text), and JEI synchronization.
     * All terminal subclasses can share a single derivation path.
     */
    public static class TerminalSearchConfig {
        private final boolean autoFocus;
        private final boolean keepFilter;
        private final boolean jeiEnabled;

        public TerminalSearchConfig(boolean autoFocus, boolean keepFilter, boolean jeiEnabled) {
            this.autoFocus = autoFocus;
            this.keepFilter = keepFilter;
            this.jeiEnabled = jeiEnabled;
        }

        /**
         * Derive the configuration from the current {@link SearchBoxMode} setting.
         */
        public static TerminalSearchConfig fromCurrentSetting() {
            final Enum<?> mode = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);

            boolean autoFocus = mode == SearchBoxMode.AUTOSEARCH
                    || mode == SearchBoxMode.JEI_AUTOSEARCH
                    || mode == SearchBoxMode.AUTOSEARCH_KEEP
                    || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP;

            boolean keepFilter = mode == SearchBoxMode.AUTOSEARCH_KEEP
                    || mode == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                    || mode == SearchBoxMode.MANUAL_SEARCH_KEEP
                    || mode == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;

            boolean jeiEnabled = mode == SearchBoxMode.JEI_AUTOSEARCH
                    || mode == SearchBoxMode.JEI_MANUAL_SEARCH;

            return new TerminalSearchConfig(autoFocus, keepFilter, jeiEnabled);
        }

        public boolean isAutoFocus() {
            return this.autoFocus;
        }

        public boolean isKeepFilter() {
            return this.keepFilter;
        }

        public boolean isJEIEnabled() {
            return this.jeiEnabled;
        }
    }

    // ========== Terminal key result ==========

    /**
     * Result of terminal key event handling.
     */
    public enum TerminalKeyResult {
        /** The key event was consumed by the search field. */
        HANDLED,
        /** The key event was not consumed; caller should fall through to super. */
        NOT_HANDLED,
        /** The key event was suppressed (e.g. leading space in empty field). */
        SUPPRESSED
    }

    // ========== Instance fields ==========

    private final Host host;
    private final SearchMode mode;

    // SINGLE mode
    @Nullable
    private MUITextFieldWidget searchField;

    // TRIPLE mode
    @Nullable
    private SearchFieldWidgets tripleFields;

    // Terminal search state
    private String memoryText = "";
    private boolean autoFocus = false;

    // ========== Constructor ==========

    /**
     * Create a search bar module with the specified mode.
     *
     * @param host the host GUI providing context
     * @param mode the search mode (SINGLE or TRIPLE)
     */
    public SearchBarModule(Host host, SearchMode mode) {
        this.host = host;
        this.mode = mode;
    }

    // ========== Accessors ==========

    /**
     * Get the search mode.
     */
    public SearchMode getMode() {
        return this.mode;
    }

    /**
     * Get the single search field (SINGLE mode only).
     */
    @Nullable
    public MUITextFieldWidget getSearchField() {
        return this.searchField;
    }

    /**
     * Get the triple search field widgets (TRIPLE mode only).
     */
    @Nullable
    public SearchFieldWidgets getTripleFields() {
        return this.tripleFields;
    }

    /**
     * Get the memory text (persisted across GUI reopen).
     */
    public String getMemoryText() {
        return this.memoryText;
    }

    /**
     * Set the memory text externally (e.g. from JEI sync).
     */
    public void setMemoryText(String memoryText) {
        this.memoryText = memoryText;
    }

    /**
     * Check if auto-focus mode is enabled.
     */
    public boolean isAutoFocus() {
        return this.autoFocus;
    }

    // ========== SINGLE mode: field creation ==========

    /**
     * Create and register a single search field on the host panel.
     *
     * @param spec the search field specification
     * @return the created search field widget
     */
    public MUITextFieldWidget initSingleField(SearchFieldSpec spec) {
        this.searchField = addSearchField(this.host.getPanel(), spec);
        return this.searchField;
    }

    // ========== TRIPLE mode: field creation ==========

    /**
     * Create and register a triple search field group on the host panel.
     *
     * @param group the search field group specification
     * @return the created search field widgets
     */
    public SearchFieldWidgets initTripleFields(SearchFieldGroup group) {
        this.tripleFields = addSearchFieldGroup(this.host.getPanel(), group);
        return this.tripleFields;
    }

    // ========== Terminal search config (SINGLE mode) ==========

    /**
     * Apply terminal search mode configuration to the search field.
     *
     * <p>Handles auto-focus, JEI text sync, and memory text restoration.
     * Typically called from {@code initGui()} after {@code super.initGui()}.
     *
     * @param config         the terminal search config derived from the current setting
     * @param searchCallback callback to apply the restored search text to the repo
     */
    public void applyTerminalSearchConfig(TerminalSearchConfig config,
            @Nullable Consumer<String> searchCallback) {
        this.autoFocus = config.isAutoFocus();

        if (this.searchField == null) {
            return;
        }

        this.searchField.setFocused(config.isAutoFocus());

        String effectiveText = this.memoryText;
        if (config.isJEIEnabled()) {
            effectiveText = Integrations.jei().getSearchText();
        }

        if (config.isKeepFilter() && effectiveText != null && !effectiveText.isEmpty()) {
            this.searchField.setText(effectiveText);
            this.searchField.selectAll();
            if (searchCallback != null) {
                searchCallback.accept(effectiveText);
            }
        }
    }

    // ========== Keyboard handling (SINGLE mode) ==========

    /**
     * Handle a key event with terminal-specific behavior:
     * empty-space suppression, auto-focus-on-type, and focus priority logic.
     *
     * <p>This method should be called from the panel's {@code keyTyped()} after
     * hotbar key check and toggle-focus handling, but before {@code super.keyTyped()}.
     *
     * @param character  the typed character
     * @param key        the key code
     * @param mouseInGui whether the mouse cursor is inside the GUI area
     * @return {@link TerminalKeyResult} indicating how the key was handled
     */
    public TerminalKeyResult handleTerminalKeyTyped(char character, int key, boolean mouseInGui) {
        if (this.searchField == null) {
            return TerminalKeyResult.NOT_HANDLED;
        }

        // Suppress leading space in empty search field
        if (character == ' ' && this.searchField.getText().isEmpty()) {
            return TerminalKeyResult.SUPPRESSED;
        }

        boolean wasFocused = this.searchField.isFocused();

        // Auto-focus: when autoFocus is enabled and mouse is in GUI, focus on key press
        if (this.autoFocus && !this.searchField.isFocused() && mouseInGui) {
            final SearchBoxFocusPriority focusPriority = (SearchBoxFocusPriority) AEConfig.instance()
                    .getConfigManager().getSetting(Settings.SEARCH_BOX_FOCUS_PRIORITY);
            if (focusPriority != SearchBoxFocusPriority.NEVER) {
                this.searchField.setFocused(true);
            }
        }

        if (this.searchField.textboxKeyTyped(character, key)) {
            return TerminalKeyResult.HANDLED;
        }

        // If focus was acquired only for this key attempt but the key was not consumed,
        // revert focus so that unrelated keys (e.g. shift) do not stick the cursor in.
        if (!wasFocused) {
            this.searchField.setFocused(false);
        }

        return TerminalKeyResult.NOT_HANDLED;
    }

    // ========== Keyboard handling (TRIPLE mode) ==========

    /**
     * Handle keyboard events for triple search fields.
     *
     * <p>Handles:
     * <ul>
     *   <li>Space suppression in empty fields</li>
     *   <li>Tab key cycling between fields</li>
     *   <li>Text input forwarding</li>
     * </ul>
     *
     * @param character the typed character
     * @param key       the key code
     * @return true if the event was consumed
     */
    public boolean handleTripleKeyTyped(char character, int key) {
        if (this.tripleFields == null) {
            return false;
        }

        MUITextFieldWidget inputs = this.tripleFields.getInputs();
        MUITextFieldWidget outputs = this.tripleFields.getOutputs();
        MUITextFieldWidget names = this.tripleFields.getNames();

        // Suppress leading space in empty focused field
        if (character == ' ') {
            if ((inputs != null && inputs.getText().isEmpty() && inputs.isFocused())
                    || (outputs != null && outputs.getText().isEmpty() && outputs.isFocused())
                    || (names != null && names.getText().isEmpty() && names.isFocused())) {
                return true;
            }
        } else if (character == '\t') {
            if (handleTab()) {
                return true;
            }
        }

        // Forward to focused field
        if ((inputs != null && inputs.textboxKeyTyped(character, key))
                || (outputs != null && outputs.textboxKeyTyped(character, key))
                || (names != null && names.textboxKeyTyped(character, key))) {
            return true;
        }

        return false;
    }

    // ========== Mouse handling ==========

    /**
     * Handle mouse clicks for the search field(s).
     *
     * @param xCoord absolute screen X
     * @param yCoord absolute screen Y
     * @param btn    mouse button (0=left, 1=right, 2=middle)
     */
    public void handleMouseClicked(int xCoord, int yCoord, int btn) {
        int localX = xCoord - this.host.getGuiLeft();
        int localY = yCoord - this.host.getGuiTop();

        if (this.mode == SearchMode.SINGLE && this.searchField != null) {
            this.searchField.mouseClicked(localX, localY, btn);
        } else if (this.mode == SearchMode.TRIPLE && this.tripleFields != null) {
            if (this.tripleFields.getInputs() != null) {
                this.tripleFields.getInputs().mouseClicked(localX, localY, btn);
            }
            if (this.tripleFields.getOutputs() != null) {
                this.tripleFields.getOutputs().mouseClicked(localX, localY, btn);
            }
            if (this.tripleFields.getNames() != null) {
                this.tripleFields.getNames().mouseClicked(localX, localY, btn);
            }
        }
    }

    // ========== GUI close ==========

    /**
     * Save search field text to memoryText and sync to JEI if applicable.
     * Call from the host's {@code onGuiClosed()}.
     */
    public void onGuiClosed() {
        if (this.mode == SearchMode.SINGLE && this.searchField != null) {
            this.memoryText = this.searchField.getText();
            syncToJEI();
        } else if (this.mode == SearchMode.TRIPLE && this.tripleFields != null) {
            // Triple mode: combine all field texts for memory
            StringBuilder sb = new StringBuilder();
            if (this.tripleFields.getInputs() != null && !this.tripleFields.getInputs().getText().isEmpty()) {
                sb.append(this.tripleFields.getInputs().getText());
            }
            this.memoryText = sb.toString();
            syncToJEI();
        }
    }

    // ========== Focus management ==========

    /**
     * Check if any search field has focus.
     */
    public boolean isAnyFieldFocused() {
        if (this.mode == SearchMode.SINGLE) {
            return this.searchField != null && this.searchField.isFocused();
        } else if (this.mode == SearchMode.TRIPLE && this.tripleFields != null) {
            return (this.tripleFields.getInputs() != null && this.tripleFields.getInputs().isFocused())
                    || (this.tripleFields.getOutputs() != null && this.tripleFields.getOutputs().isFocused())
                    || (this.tripleFields.getNames() != null && this.tripleFields.getNames().isFocused());
        }
        return false;
    }

    /**
     * Set focus on the search field(s).
     *
     * @param focused true to focus, false to unfocus
     */
    public void setFocused(boolean focused) {
        if (this.mode == SearchMode.SINGLE && this.searchField != null) {
            this.searchField.setFocused(focused);
        } else if (this.mode == SearchMode.TRIPLE && this.tripleFields != null) {
            if (this.tripleFields.getInputs() != null) {
                this.tripleFields.getInputs().setFocused(focused);
            }
            if (this.tripleFields.getOutputs() != null) {
                this.tripleFields.getOutputs().setFocused(focused);
            }
            if (this.tripleFields.getNames() != null) {
                this.tripleFields.getNames().setFocused(focused);
            }
        }
    }

    /**
     * Get the index of the currently focused search field in TRIPLE mode.
     *
     * @return 0=Inputs, 1=Outputs, 2=Names, -1=none
     */
    public int getFocusedFieldIndex() {
        if (this.mode != SearchMode.TRIPLE || this.tripleFields == null) {
            return -1;
        }
        if (this.tripleFields.getInputs() != null && this.tripleFields.getInputs().isFocused()) {
            return 0;
        }
        if (this.tripleFields.getOutputs() != null && this.tripleFields.getOutputs().isFocused()) {
            return 1;
        }
        if (this.tripleFields.getNames() != null && this.tripleFields.getNames().isFocused()) {
            return 2;
        }
        return -1;
    }

    /**
     * Set focus on a specific search field in TRIPLE mode.
     *
     * @param index 0=Inputs, 1=Outputs, 2=Names
     */
    public void setFocusedField(int index) {
        if (this.mode != SearchMode.TRIPLE || this.tripleFields == null) {
            return;
        }
        if (this.tripleFields.getInputs() != null) {
            this.tripleFields.getInputs().setFocused(index == 0);
        }
        if (this.tripleFields.getOutputs() != null) {
            this.tripleFields.getOutputs().setFocused(index == 1);
        }
        if (this.tripleFields.getNames() != null) {
            this.tripleFields.getNames().setFocused(index == 2);
        }
    }

    // ========== Search text access ==========

    /**
     * Get the search text from the single search field.
     */
    public String getSearchText() {
        if (this.searchField != null) {
            return this.searchField.getText();
        }
        return "";
    }

    /**
     * Set the search text on the single search field.
     */
    public void setSearchText(String text) {
        if (this.searchField != null) {
            this.searchField.setText(text);
        }
    }

    // ========== Factory methods ==========

    /**
     * Create and register a single search field on the given panel.
     */
    public static MUITextFieldWidget addSearchField(AEBasePanel panel, SearchFieldSpec spec) {
        return panel.addWidget(new MUITextFieldWidget(spec.getX(), spec.getY(), spec.getWidth(), spec.getHeight())
                .setEnableBackground(false)
                .setMaxStringLength(SearchFieldStyle.DEFAULT_MAX_LENGTH)
                .setTextColor(SearchFieldStyle.DEFAULT_TEXT_COLOR)
                .setTooltip(spec.getTooltip())
                .setTextChangeListener(spec.getTextChangeListener())
                .setFocused(spec.isFocused()));
    }

    /**
     * Create and register a triple search field group on the given panel.
     */
    public static SearchFieldWidgets addSearchFieldGroup(AEBasePanel panel, SearchFieldGroup group) {
        MUITextFieldWidget inputs = group.getInputs() == null ? null : addSearchField(panel, group.getInputs());
        MUITextFieldWidget outputs = group.getOutputs() == null ? null : addSearchField(panel, group.getOutputs());
        MUITextFieldWidget names = group.getNames() == null ? null : addSearchField(panel, group.getNames());
        return new SearchFieldWidgets(inputs, outputs, names);
    }

    // ========== Internal helpers ==========

    /**
     * Cycle search field focus with Tab key (TRIPLE mode).
     * Cycle order: Inputs → Outputs → Names → Inputs (Shift reverses).
     *
     * @return true if focus was cycled successfully
     */
    private boolean handleTab() {
        if (this.tripleFields == null) {
            return false;
        }

        MUITextFieldWidget inputs = this.tripleFields.getInputs();
        MUITextFieldWidget outputs = this.tripleFields.getOutputs();
        MUITextFieldWidget names = this.tripleFields.getNames();

        boolean shiftDown = AEBasePanel.isShiftKeyDown();

        if (inputs != null && inputs.isFocused()) {
            inputs.setFocused(false);
            if (shiftDown) {
                if (names != null) {
                    names.setFocused(true);
                }
            } else {
                if (outputs != null) {
                    outputs.setFocused(true);
                }
            }
            return true;
        } else if (outputs != null && outputs.isFocused()) {
            outputs.setFocused(false);
            if (shiftDown) {
                if (inputs != null) {
                    inputs.setFocused(true);
                }
            } else {
                if (names != null) {
                    names.setFocused(true);
                }
            }
            return true;
        } else if (names != null && names.isFocused()) {
            names.setFocused(false);
            if (shiftDown) {
                if (outputs != null) {
                    outputs.setFocused(true);
                }
            } else {
                if (inputs != null) {
                    inputs.setFocused(true);
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Sync search text to JEI if the current mode requires it.
     */
    private void syncToJEI() {
        final Enum searchModeSetting = AEConfig.instance().getConfigManager().getSetting(Settings.SEARCH_MODE);
        final boolean isJEISync = SearchBoxMode.JEI_AUTOSEARCH == searchModeSetting
                || SearchBoxMode.JEI_MANUAL_SEARCH == searchModeSetting;
        if (isJEISync && Platform.isJEIEnabled()) {
            Integrations.jei().setSearchText(this.memoryText);
        }
    }
}

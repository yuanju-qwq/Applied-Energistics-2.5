package appeng.api.config;

/**
 * Crafting request mode.
 */
public enum CraftingMode {
    /**
     * Standard mode: when materials are insufficient, the crafting tree calculation fails.
     */
    STANDARD,
    /**
     * Ignore missing mode: when materials are insufficient, mark as missing and continue calculation.
     */
    IGNORE_MISSING
}

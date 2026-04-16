package appeng.api.config;

/**
 * 合成请求的模式。
 */
public enum CraftingMode {
    /**
     * 标准模式：当材料不足时，合成树计算失败。
     */
    STANDARD,
    /**
     * 忽略缺失模式：当材料不足时，标记为缺失并继续计算。
     */
    IGNORE_MISSING
}

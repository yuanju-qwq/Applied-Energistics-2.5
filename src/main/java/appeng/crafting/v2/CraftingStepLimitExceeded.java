package appeng.crafting.v2;

/**
 * 合成步数超出上限时抛出的异常。
 */
public class CraftingStepLimitExceeded extends RuntimeException {

    public CraftingStepLimitExceeded() {
        super("Crafting step limit exceeded");
    }
}

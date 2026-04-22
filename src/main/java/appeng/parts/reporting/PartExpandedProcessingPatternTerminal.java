package appeng.parts.reporting;

import static appeng.helpers.PatternHelper.PROCESSING_INPUT_LIMIT;
import static appeng.helpers.PatternHelper.PROCESSING_OUTPUT_LIMIT;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import appeng.api.parts.IPartModel;
import appeng.api.storage.StorageName;
import appeng.core.AppEng;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;

public class PartExpandedProcessingPatternTerminal extends AbstractPartEncoder {
    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AppEng.MOD_ID,
            "part/expanded_processing_pattern_terminal_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AppEng.MOD_ID,
            "part/expanded_processing_pattern_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    @Reflected
    public PartExpandedProcessingPatternTerminal(final ItemStack is) {
        super(is);
        this.craftingAE = new IAEStackInventory(this, PROCESSING_INPUT_LIMIT,
                StorageName.CRAFTING_INPUT);
        this.outputAE = new IAEStackInventory(this, PROCESSING_OUTPUT_LIMIT,
                StorageName.CRAFTING_OUTPUT);
        this.pattern = new AppEngInternalInventory(this, 2);
        this.craftingMode = false;
    }

    @Override
    public boolean isCraftingRecipe() {
        return false;
    }

    @Override
    public void setCraftingRecipe(final boolean craftingMode) {
        // NO-OP
    }

    @Override
    public GuiBridge getGuiBridge() {
        return AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL.getLegacyBridge();
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }
}

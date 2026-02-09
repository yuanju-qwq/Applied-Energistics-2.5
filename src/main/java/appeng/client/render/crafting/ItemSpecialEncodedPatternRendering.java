package appeng.client.render.crafting;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.bootstrap.IItemRendering;
import appeng.bootstrap.ItemRenderingCustomizer;
import appeng.core.AppEng;

public class ItemSpecialEncodedPatternRendering extends ItemRenderingCustomizer {

    private static final ResourceLocation MODEL = new ResourceLocation(AppEng.MOD_ID,
            "builtin/special_encoded_pattern");

    @Override
    @SideOnly(Side.CLIENT)
    public void customize(IItemRendering rendering) {
        rendering.builtInModel("models/item/builtin/special_encoded_pattern", new ItemSpecialEncodedPatternModel());
        rendering.model(new ModelResourceLocation(MODEL, "inventory")).variants(MODEL);
    }

}

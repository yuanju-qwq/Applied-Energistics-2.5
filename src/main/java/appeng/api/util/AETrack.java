package appeng.api.util;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.client.render.BlockPosHighlighter;
import appeng.helpers.DualityInterface;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;

public class AETrack {
    public static void trackCrafting(EntityPlayer player, ICraftingMedium latestMedium) {
        if (latestMedium instanceof ICraftingProvider provider) {
            if (provider instanceof DualityInterface patternInterface) {
                BlockPos blockPos = patternInterface.getLocation().getPos();
                player.sendMessage(new TextComponentTranslation("[合成追踪]正在追踪位于 X:" + blockPos.getX() + " Y:"
                        + blockPos.getY() + " Z:" + blockPos.getZ() + " 的接口"));
                showPos(blockPos, player);
            } else if (Platform.GTLoaded) {
                // GT pattern provider tracking — injected by GregTech via Mixin
                trackGTProvider(provider, player);
            }
        }
    }

    /**
     * Track GT pattern provider location.
     * Base implementation is empty, actual logic injected by GregTech via Mixin.
     */
    protected static void trackGTProvider(ICraftingProvider provider, EntityPlayer player) {
    }

    public static void showPos(BlockPos pos, EntityPlayer player) {
        BlockPos blockPos2 = player.getPosition();
        int playerDim = player.world.provider.getDimension();

        long currentTime = System.currentTimeMillis();
        double distance = BlockPosUtils.getDistance(pos, blockPos2);
        long highlightTime = (long) (currentTime + 500 * distance);

        BlockPosHighlighter.hilightBlock(pos, highlightTime, playerDim);
    }
}

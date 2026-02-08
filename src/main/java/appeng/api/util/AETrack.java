package appeng.api.util;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.client.render.BlockPosHighlighter;
import appeng.core.AppEng;
import appeng.helpers.DualityInterface;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import gregtech.api.metatileentity.MetaTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

public class AETrack {
    public static void trackCrafting(EntityPlayer player, ICraftingMedium latestMedium) {
        if(latestMedium instanceof ICraftingProvider provider){
            if(provider instanceof DualityInterface patternInterface)
            {
                BlockPos blockPos = patternInterface.getLocation().getPos();
                player.sendMessage(new TextComponentTranslation("[合成追踪]正在追踪位于 X:"+blockPos.getX()+" Y:"+blockPos.getY()+" Z:"+blockPos.getZ()+" 的接口"));
                showPos(blockPos,player);
            }
            else if(Platform.isModLoaded("gregtech")){
                if(provider instanceof MetaTileEntity metaTileEntity)
                {
                    BlockPos blockPos = metaTileEntity.getPos();
                    player.sendMessage(new TextComponentTranslation("[合成追踪]正在追踪位于 X:"+blockPos.getX()+" Y:"+blockPos.getY()+" Z:"+blockPos.getZ()+" 的样板总成"));
                    showPos(blockPos,player);
                }
            }
        }
    }

    private static void showPos(BlockPos pos,EntityPlayer  player){
        BlockPos blockPos2 = player.getPosition();
        int playerDim = player.world.provider.getDimension();

        long currentTime = System.currentTimeMillis();
        double distance = BlockPosUtils.getDistance(pos, blockPos2);
        long highlightTime = (long) (currentTime + 500 * distance);

        BlockPosHighlighter.hilightBlock(pos, highlightTime, playerDim);
    }
}

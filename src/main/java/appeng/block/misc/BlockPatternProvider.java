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

package appeng.block.misc;

import javax.annotation.Nullable;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import appeng.api.util.AEPartLocation;
import appeng.api.util.IOrientable;
import appeng.block.AEBaseTileBlock;
import appeng.core.sync.AEGuiKeys;
import appeng.tile.misc.TilePatternProvider;
import appeng.util.Platform;

/**
 * Block for the Pattern Provider (crafting pattern storage and push).
 */
public class BlockPatternProvider extends AEBaseTileBlock {

    public BlockPatternProvider() {
        super(Material.IRON);
    }

    @Override
    public boolean onActivated(final World w, final BlockPos pos, final EntityPlayer player, final EnumHand hand,
            final @Nullable ItemStack heldItem, final EnumFacing side, final float hitX, final float hitY,
            final float hitZ) {
        if (player.isSneaking()) {
            return false;
        }

        if (Platform.isServer()) {
            final TilePatternProvider tile = this.getTileEntity(w, pos);
            if (tile != null) {
                Platform.openGUI(player, tile, AEPartLocation.fromFacing(side), AEGuiKeys.PATTERN_PROVIDER);
                return true;
            }
        }

        return true;
    }

    // Notify the pattern provider when a neighbor block changes (for redstone state updates)
    @Override
    public void neighborChanged(final IBlockState state, final World w, final BlockPos pos, final Block neighborBlock,
            final BlockPos neighborPos) {
        final TilePatternProvider tile = this.getTileEntity(w, pos);
        if (tile != null) {
            tile.updateRedstoneState();
        }
    }

    @Override
    protected boolean hasCustomRotation() {
        return true;
    }

    @Override
    protected void customRotateBlock(final IOrientable rotatable, final EnumFacing axis) {
        if (rotatable instanceof TilePatternProvider) {
            ((TilePatternProvider) rotatable).setSide(axis);
        }
    }
}

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

package appeng.util;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.WeakHashMap;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayerFactory;

import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.core.AELog;
import appeng.hooks.TickHandler;

public final class WorldHelper {
    private static final Random RANDOM_GENERATOR = new Random();
    private static final WeakHashMap<World, EntityPlayer> FAKE_PLAYERS = new WeakHashMap<>();

    private WorldHelper() {
    }

    public static boolean hasPermissions(final DimensionalCoord dc, final EntityPlayer player) {
        return dc.getWorld().canMineBlockBody(player, dc.getPos());
    }

    public static boolean hasPermissions(final World world, final BlockPos pos, final EntityPlayer player) {
        return world.canMineBlockBody(player, pos);
    }

    public static boolean isBlockAir(final World w, final BlockPos pos) {
        try {
            return w.getBlockState(pos).getBlock().isAir(w.getBlockState(pos), w, pos);
        } catch (final Throwable e) {
            return false;
        }
    }

    public static ItemStack[] getBlockDrops(final World w, final BlockPos pos) {
        return getBlockDrops(w, pos, 0);
    }

    public static ItemStack[] getBlockDrops(final World w, final BlockPos pos, int fortune) {
        List<ItemStack> out = new ArrayList<>();
        final IBlockState state = w.getBlockState(pos);

        if (state != null) {
            out = state.getBlock().getDrops(w, pos, state, fortune);
        }

        if (out == null || out.isEmpty()) {
            return new ItemStack[0];
        }
        return out.toArray(new ItemStack[out.size()]);
    }

    public static void spawnDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        if (!w.isRemote) {
            for (final ItemStack i : drops) {
                if (!i.isEmpty()) {
                    if (i.getCount() > 0) {
                        final double offset_x = (getRandomInt() % 32 - 16) / 82;
                        final double offset_y = (getRandomInt() % 32 - 16) / 82;
                        final double offset_z = (getRandomInt() % 32 - 16) / 82;
                        final EntityItem ei = new EntityItem(w, 0.5 + offset_x + pos.getX(),
                                0.5 + offset_y + pos.getY(), 0.2 + offset_z + pos.getZ(), i.copy());
                        w.spawnEntity(ei);
                    }
                }
            }
        }
    }

    public static EntityPlayer getPlayer(final WorldServer w) {
        if (w == null) {
            throw new InvalidParameterException("World is null.");
        }

        final EntityPlayer wrp = FAKE_PLAYERS.get(w);
        if (wrp != null) {
            return wrp;
        }

        final EntityPlayer p = FakePlayerFactory.getMinecraft(w);
        FAKE_PLAYERS.put(w, p);
        return p;
    }

    public static void configurePlayer(final EntityPlayer player, final AEPartLocation side, final TileEntity tile) {
        float pitch = 0.0f;
        float yaw = 0.0f;

        switch (side) {
            case DOWN:
                pitch = 90.0f;
                break;
            case EAST:
                yaw = -90.0f;
                break;
            case NORTH:
                yaw = 180.0f;
                break;
            case SOUTH:
                yaw = 0.0f;
                break;
            case INTERNAL:
                break;
            case UP:
                pitch = 90.0f;
                break;
            case WEST:
                yaw = 90.0f;
                break;
        }

        player.posX = tile.getPos().getX() + 0.5;
        player.posY = tile.getPos().getY() + 0.5;
        player.posZ = tile.getPos().getZ() + 0.5;

        player.rotationPitch = player.prevCameraPitch = player.cameraPitch = pitch;
        player.rotationYaw = player.prevCameraYaw = player.cameraYaw = yaw;
    }

    public static void notifyBlocksOfNeighbors(final World world, final BlockPos pos) {
        if (!world.isRemote) {
            TickHandler.instance().addCallable(world, new BlockUpdate(pos));
        }
    }

    public static void sendChunk(final Chunk c, final int verticalBits) {
        try {
            final WorldServer ws = (WorldServer) c.getWorld();
            final PlayerChunkMap pm = ws.getPlayerChunkMap();
            final PlayerChunkMapEntry playerInstance = pm.getEntry(c.x, c.z);

            if (playerInstance != null) {
                playerInstance.sendPacket(new SPacketChunkData(c, verticalBits));
            }
        } catch (final Throwable t) {
            AELog.debug(t);
        }
    }

    public static float getEyeOffset(final EntityPlayer player) {
        assert player.world.isRemote : "Valid only on client";
        return (float) (player.posY + player.getEyeHeight() - player.getDefaultEyeHeight());
    }

    /**
     * Get the GT MetaTileEntity at the given position.
     * Base implementation returns null, actual logic injected by GregTech via Mixin.
     */
    public static Object getMetaTileEntity(IBlockAccess world, BlockPos pos) {
        return null;
    }

    private static int getRandomInt() {
        return Math.abs(RANDOM_GENERATOR.nextInt());
    }
}

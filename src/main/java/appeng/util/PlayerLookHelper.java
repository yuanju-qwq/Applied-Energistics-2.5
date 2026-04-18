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

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class PlayerLookHelper {
    private PlayerLookHelper() {
    }

    public static LookDirection getPlayerRay(final EntityPlayer playerIn, final float eyeOffset) {
        double reachDistance = 5.0d;

        final double x = playerIn.prevPosX + (playerIn.posX - playerIn.prevPosX);
        final double y = playerIn.prevPosY + (playerIn.posY - playerIn.prevPosY) + playerIn.getEyeHeight();
        final double z = playerIn.prevPosZ + (playerIn.posZ - playerIn.prevPosZ);

        final float playerPitch = playerIn.prevRotationPitch + (playerIn.rotationPitch - playerIn.prevRotationPitch);
        final float playerYaw = playerIn.prevRotationYaw + (playerIn.rotationYaw - playerIn.prevRotationYaw);

        final float yawRayX = MathHelper.sin(-playerYaw * 0.017453292f - (float) Math.PI);
        final float yawRayZ = MathHelper.cos(-playerYaw * 0.017453292f - (float) Math.PI);

        final float pitchMultiplier = -MathHelper.cos(-playerPitch * 0.017453292F);
        final float eyeRayY = MathHelper.sin(-playerPitch * 0.017453292F);
        final float eyeRayX = yawRayX * pitchMultiplier;
        final float eyeRayZ = yawRayZ * pitchMultiplier;

        if (playerIn instanceof EntityPlayerMP) {
            reachDistance = ((EntityPlayerMP) playerIn).interactionManager.getBlockReachDistance();
        }

        final Vec3d from = new Vec3d(x, y, z);
        final Vec3d to = from.add(eyeRayX * reachDistance, eyeRayY * reachDistance, eyeRayZ * reachDistance);

        return new LookDirection(from, to);
    }

    public static RayTraceResult rayTrace(final EntityPlayer p, final boolean hitBlocks, final boolean hitEntities) {
        final World w = p.getEntityWorld();

        final float f = 1.0F;
        float f1 = p.prevRotationPitch + (p.rotationPitch - p.prevRotationPitch) * f;
        final float f2 = p.prevRotationYaw + (p.rotationYaw - p.prevRotationYaw) * f;
        final double d0 = p.prevPosX + (p.posX - p.prevPosX) * f;
        final double d1 = p.prevPosY + (p.posY - p.prevPosY) * f + 1.62D - p.getYOffset();
        final double d2 = p.prevPosZ + (p.posZ - p.prevPosZ) * f;
        final Vec3d vec3 = new Vec3d(d0, d1, d2);
        final float f3 = MathHelper.cos(-f2 * 0.017453292F - (float) Math.PI);
        final float f4 = MathHelper.sin(-f2 * 0.017453292F - (float) Math.PI);
        final float f5 = -MathHelper.cos(-f1 * 0.017453292F);
        final float f6 = MathHelper.sin(-f1 * 0.017453292F);
        final float f7 = f4 * f5;
        final float f8 = f3 * f5;
        final double d3 = 32.0D;

        final Vec3d vec31 = vec3.add(f7 * d3, f6 * d3, f8 * d3);

        final AxisAlignedBB bb = new AxisAlignedBB(Math.min(vec3.x, vec31.x), Math.min(vec3.y, vec31.y),
                Math.min(vec3.z, vec31.z), Math.max(vec3.x, vec31.x), Math.max(vec3.y, vec31.y),
                Math.max(vec3.z, vec31.z)).grow(16, 16, 16);

        Entity entity = null;
        double closest = 9999999.0D;
        if (hitEntities) {
            final List list = w.getEntitiesWithinAABBExcludingEntity(p, bb);

            for (int l = 0; l < list.size(); ++l) {
                final Entity entity1 = (Entity) list.get(l);

                if (!entity1.isDead && entity1 != p && !(entity1 instanceof EntityItem)) {
                    if (entity1.isEntityAlive()) {
                        // prevent killing / flying of mounts.
                        if (entity1.isRidingOrBeingRiddenBy(p)) {
                            continue;
                        }

                        f1 = 0.3F;
                        final AxisAlignedBB boundingBox = entity1.getEntityBoundingBox().grow(f1, f1, f1);
                        final RayTraceResult rayTraceResult = boundingBox.calculateIntercept(vec3, vec31);

                        if (rayTraceResult != null) {
                            final double nd = vec3.squareDistanceTo(rayTraceResult.hitVec);

                            if (nd < closest) {
                                entity = entity1;
                                closest = nd;
                            }
                        }
                    }
                }
            }
        }

        RayTraceResult pos = null;
        Vec3d vec = null;

        if (hitBlocks) {
            vec = new Vec3d(d0, d1, d2);
            pos = w.rayTraceBlocks(vec3, vec31, true);
        }

        if (entity != null && pos != null && pos.hitVec.squareDistanceTo(vec) > closest) {
            pos = new RayTraceResult(entity);
        } else if (entity != null && pos == null) {
            pos = new RayTraceResult(entity);
        }

        return pos;
    }
}

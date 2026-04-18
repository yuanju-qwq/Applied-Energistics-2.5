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

import net.minecraft.util.EnumFacing;

import appeng.api.util.AEPartLocation;

public final class OrientationHelper {
    private OrientationHelper() {
    }

    public static AEPartLocation crossProduct(final AEPartLocation forward, final AEPartLocation up) {
        final int west_x = forward.yOffset * up.zOffset - forward.zOffset * up.yOffset;
        final int west_y = forward.zOffset * up.xOffset - forward.xOffset * up.zOffset;
        final int west_z = forward.xOffset * up.yOffset - forward.yOffset * up.xOffset;

        switch (west_x + west_y * 2 + west_z * 3) {
            case 1:
                return AEPartLocation.EAST;
            case -1:
                return AEPartLocation.WEST;

            case 2:
                return AEPartLocation.UP;
            case -2:
                return AEPartLocation.DOWN;

            case 3:
                return AEPartLocation.SOUTH;
            case -3:
                return AEPartLocation.NORTH;
        }

        return AEPartLocation.INTERNAL;
    }

    public static EnumFacing crossProduct(final EnumFacing forward, final EnumFacing up) {
        final int west_x = forward.getYOffset() * up.getZOffset() - forward.getZOffset() * up.getYOffset();
        final int west_y = forward.getZOffset() * up.getXOffset() - forward.getXOffset() * up.getZOffset();
        final int west_z = forward.getXOffset() * up.getYOffset() - forward.getYOffset() * up.getXOffset();

        switch (west_x + west_y * 2 + west_z * 3) {
            case 1:
                return EnumFacing.EAST;
            case -1:
                return EnumFacing.WEST;

            case 2:
                return EnumFacing.UP;
            case -2:
                return EnumFacing.DOWN;

            case 3:
                return EnumFacing.SOUTH;
            case -3:
                return EnumFacing.NORTH;
        }

        // something is better then nothing?
        return EnumFacing.NORTH;
    }

    public static AEPartLocation cycleOrientations(final AEPartLocation dir, final boolean upAndDown) {
        if (upAndDown) {
            switch (dir) {
                case NORTH:
                    return AEPartLocation.SOUTH;
                case SOUTH:
                    return AEPartLocation.EAST;
                case EAST:
                    return AEPartLocation.WEST;
                case WEST:
                    return AEPartLocation.NORTH;
                case UP:
                    return AEPartLocation.UP;
                case DOWN:
                    return AEPartLocation.DOWN;
                case INTERNAL:
                    return AEPartLocation.INTERNAL;
            }
        } else {
            switch (dir) {
                case UP:
                    return AEPartLocation.DOWN;
                case DOWN:
                    return AEPartLocation.NORTH;
                case NORTH:
                    return AEPartLocation.SOUTH;
                case SOUTH:
                    return AEPartLocation.EAST;
                case EAST:
                    return AEPartLocation.WEST;
                case WEST:
                    return AEPartLocation.UP;
                case INTERNAL:
                    return AEPartLocation.INTERNAL;
            }
        }

        return AEPartLocation.INTERNAL;
    }

    public static AEPartLocation rotateAround(final AEPartLocation forward, final AEPartLocation axis) {
        if (axis == AEPartLocation.INTERNAL || forward == AEPartLocation.INTERNAL) {
            return forward;
        }

        switch (forward) {
            case DOWN:
                switch (axis) {
                    case DOWN:
                        return forward;
                    case UP:
                        return forward;
                    case NORTH:
                        return AEPartLocation.EAST;
                    case SOUTH:
                        return AEPartLocation.WEST;
                    case EAST:
                        return AEPartLocation.NORTH;
                    case WEST:
                        return AEPartLocation.SOUTH;
                    default:
                        break;
                }
                break;
            case UP:
                switch (axis) {
                    case NORTH:
                        return AEPartLocation.WEST;
                    case SOUTH:
                        return AEPartLocation.EAST;
                    case EAST:
                        return AEPartLocation.SOUTH;
                    case WEST:
                        return AEPartLocation.NORTH;
                    default:
                        break;
                }
                break;
            case NORTH:
                switch (axis) {
                    case UP:
                        return AEPartLocation.WEST;
                    case DOWN:
                        return AEPartLocation.EAST;
                    case EAST:
                        return AEPartLocation.UP;
                    case WEST:
                        return AEPartLocation.DOWN;
                    default:
                        break;
                }
                break;
            case SOUTH:
                switch (axis) {
                    case UP:
                        return AEPartLocation.EAST;
                    case DOWN:
                        return AEPartLocation.WEST;
                    case EAST:
                        return AEPartLocation.DOWN;
                    case WEST:
                        return AEPartLocation.UP;
                    default:
                        break;
                }
                break;
            case EAST:
                switch (axis) {
                    case UP:
                        return AEPartLocation.NORTH;
                    case DOWN:
                        return AEPartLocation.SOUTH;
                    case NORTH:
                        return AEPartLocation.UP;
                    case SOUTH:
                        return AEPartLocation.DOWN;
                    default:
                        break;
                }
            case WEST:
                switch (axis) {
                    case UP:
                        return AEPartLocation.SOUTH;
                    case DOWN:
                        return AEPartLocation.NORTH;
                    case NORTH:
                        return AEPartLocation.DOWN;
                    case SOUTH:
                        return AEPartLocation.UP;
                    default:
                        break;
                }
            default:
                break;
        }
        return forward;
    }

    public static EnumFacing rotateAround(final EnumFacing forward, final EnumFacing axis) {
        switch (forward) {
            case DOWN:
                switch (axis) {
                    case DOWN:
                        return forward;
                    case UP:
                        return forward;
                    case NORTH:
                        return EnumFacing.EAST;
                    case SOUTH:
                        return EnumFacing.WEST;
                    case EAST:
                        return EnumFacing.NORTH;
                    case WEST:
                        return EnumFacing.SOUTH;
                    default:
                        break;
                }
                break;
            case UP:
                switch (axis) {
                    case NORTH:
                        return EnumFacing.WEST;
                    case SOUTH:
                        return EnumFacing.EAST;
                    case EAST:
                        return EnumFacing.SOUTH;
                    case WEST:
                        return EnumFacing.NORTH;
                    default:
                        break;
                }
                break;
            case NORTH:
                switch (axis) {
                    case UP:
                        return EnumFacing.WEST;
                    case DOWN:
                        return EnumFacing.EAST;
                    case EAST:
                        return EnumFacing.UP;
                    case WEST:
                        return EnumFacing.DOWN;
                    default:
                        break;
                }
                break;
            case SOUTH:
                switch (axis) {
                    case UP:
                        return EnumFacing.EAST;
                    case DOWN:
                        return EnumFacing.WEST;
                    case EAST:
                        return EnumFacing.DOWN;
                    case WEST:
                        return EnumFacing.UP;
                    default:
                        break;
                }
                break;
            case EAST:
                switch (axis) {
                    case UP:
                        return EnumFacing.NORTH;
                    case DOWN:
                        return EnumFacing.SOUTH;
                    case NORTH:
                        return EnumFacing.UP;
                    case SOUTH:
                        return EnumFacing.DOWN;
                    default:
                        break;
                }
            case WEST:
                switch (axis) {
                    case UP:
                        return EnumFacing.SOUTH;
                    case DOWN:
                        return EnumFacing.NORTH;
                    case NORTH:
                        return EnumFacing.DOWN;
                    case SOUTH:
                        return EnumFacing.UP;
                    default:
                        break;
                }
            default:
                break;
        }
        return forward;
    }
}

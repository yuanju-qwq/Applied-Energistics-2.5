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

import java.io.IOException;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.helpers.ItemStackHelper;
import appeng.util.item.AEItemStackType;
import appeng.util.item.IMixedStackList;

public final class AEStackSerialization {

    private AEStackSerialization() {}

    public static boolean isStacksIdentical(final IAEStack<?> a, final IAEStack<?> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.equals(b)) {
            return a.getStackSize() == b.getStackSize();
        }
        return false;
    }

    public static void writeStackByte(final IAEStack<?> stack, final ByteBuf buffer) {
        try {
            IAEStack.writeToPacketGeneric(buffer, stack);
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static IAEStack<?> readStackByte(final ByteBuf buffer) {
        try {
            return IAEStack.fromPacketGeneric(buffer);
        } catch (RuntimeException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static NBTTagCompound writeStackNBT(final IAEStack<?> stack, final NBTTagCompound tag) {
        if (stack != null) {
            stack.writeToNBTGeneric(tag);
        }
        return tag;
    }

    public static NBTTagList writeAEStackListNBT(final IItemList<?> list) {
        final NBTTagList out = new NBTTagList();
        for (final Object stackObj : list) {
            final IAEStack<?> stack = (IAEStack<?>) stackObj;
            final NBTTagCompound tag = new NBTTagCompound();
            writeStackNBT(stack, tag);
            out.appendTag(tag);
        }
        return out;
    }

    public static NBTTagList writeAEStackListNBT(final IMixedStackList list) {
        final NBTTagList out = new NBTTagList();
        for (final IAEStack<?> stack : list.typedView()) {
            final NBTTagCompound tag = new NBTTagCompound();
            writeStackNBT(stack, tag);
            out.appendTag(tag);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public static void readAEStackListNBT(final IItemList<IAEStackBase> list, final NBTTagList tagList) {
        for (int i = 0; i < tagList.tagCount(); i++) {
            final NBTTagCompound tag = tagList.getCompoundTagAt(i);
            IAEStack<?> stack = tag.hasKey("StackType") ? IAEStack.fromNBTGeneric(tag) : null;
            if (stack == null) {
                final ItemStack legacyStack = ItemStackHelper.stackFromNBT(tag);
                if (!legacyStack.isEmpty()) {
                    stack = AEItemStackType.INSTANCE.createStack(legacyStack);
                }
            }
            if (stack != null) {
                list.add(stack);
            }
        }
    }

    public static void readAEStackListNBT(final IMixedStackList list, final NBTTagList tagList) {
        for (int i = 0; i < tagList.tagCount(); i++) {
            final NBTTagCompound tag = tagList.getCompoundTagAt(i);
            IAEStack<?> stack = tag.hasKey("StackType") ? IAEStack.fromNBTGeneric(tag) : null;
            if (stack == null) {
                final ItemStack legacyStack = ItemStackHelper.stackFromNBT(tag);
                if (!legacyStack.isEmpty()) {
                    stack = AEItemStackType.INSTANCE.createStack(legacyStack);
                }
            }
            if (stack != null) {
                list.add(stack);
            }
        }
    }
}

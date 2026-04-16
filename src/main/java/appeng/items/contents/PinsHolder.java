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

package appeng.items.contents;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.core.AppEng;

/**
 * 持久化存储某个玩家的 Pins 数据（WorldSavedData）。
 * <p>
 * 每个玩家一个实例，通过 {@link #getForPlayer(MapStorage, UUID)} 获取。
 */
public class PinsHolder extends WorldSavedData {

    private static final String NAME_PREFIX = AppEng.MOD_ID + "_pins_";

    private static final String TAG_PLAYER_PINS = "playerPins";
    private static final String TAG_CRAFTING_PINS = "craftingPins";
    private static final String TAG_MAX_PLAYER_ROWS = "maxPlayerRows";
    private static final String TAG_MAX_CRAFTING_ROWS = "maxCraftingRows";
    private static final String TAG_SECTION_ORDER = "sectionOrder";

    private PinList pinList = new PinList();
    private PinsRows maxPlayerPinRows = PinsRows.TWO;
    private PinsRows maxCraftingPinRows = PinsRows.ONE;
    private PinSectionOrder sectionOrder = PinSectionOrder.PLAYER_FIRST;

    public PinsHolder(String name) {
        super(name);
    }

    /**
     * 为指定玩家获取或创建 PinsHolder。
     */
    @Nonnull
    public static PinsHolder getForPlayer(MapStorage storage, UUID playerUUID) {
        String name = NAME_PREFIX + playerUUID.toString().replace("-", "");
        PinsHolder holder = (PinsHolder) storage.getOrLoadData(PinsHolder.class, name);
        if (holder == null) {
            holder = new PinsHolder(name);
            storage.setData(name, holder);
        }
        return holder;
    }

    public PinList getPinList() {
        return this.pinList;
    }

    public PinsRows getMaxPlayerPinRows() {
        return this.maxPlayerPinRows;
    }

    public void setMaxPlayerPinRows(PinsRows rows) {
        this.maxPlayerPinRows = rows;
        this.markDirty();
    }

    public PinsRows getMaxCraftingPinRows() {
        return this.maxCraftingPinRows;
    }

    public void setMaxCraftingPinRows(PinsRows rows) {
        this.maxCraftingPinRows = rows;
        this.markDirty();
    }

    public PinSectionOrder getSectionOrder() {
        return this.sectionOrder;
    }

    public void setSectionOrder(PinSectionOrder order) {
        this.sectionOrder = order;
        this.markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        this.pinList = new PinList();

        // 读取玩家 Pins
        readPinsFromNBT(nbt, TAG_PLAYER_PINS, PinList.PLAYER_OFFSET, PinList.PLAYER_SLOTS);
        // 读取合成 Pins
        readPinsFromNBT(nbt, TAG_CRAFTING_PINS, 0, PinList.CRAFTING_SLOTS);

        // 读取配置
        if (nbt.hasKey(TAG_MAX_PLAYER_ROWS)) {
            try {
                this.maxPlayerPinRows = PinsRows.fromOrdinal(nbt.getInteger(TAG_MAX_PLAYER_ROWS));
            } catch (Exception e) {
                AELog.warn("Invalid maxPlayerPinRows in NBT, using default.");
                this.maxPlayerPinRows = PinsRows.TWO;
            }
        }

        if (nbt.hasKey(TAG_MAX_CRAFTING_ROWS)) {
            try {
                this.maxCraftingPinRows = PinsRows.fromOrdinal(nbt.getInteger(TAG_MAX_CRAFTING_ROWS));
            } catch (Exception e) {
                AELog.warn("Invalid maxCraftingPinRows in NBT, using default.");
                this.maxCraftingPinRows = PinsRows.ONE;
            }
        }

        if (nbt.hasKey(TAG_SECTION_ORDER)) {
            try {
                this.sectionOrder = PinSectionOrder.values()[nbt.getInteger(TAG_SECTION_ORDER)];
            } catch (Exception e) {
                AELog.warn("Invalid sectionOrder in NBT, using default.");
                this.sectionOrder = PinSectionOrder.PLAYER_FIRST;
            }
        }
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(@Nonnull NBTTagCompound nbt) {
        // 写入玩家 Pins
        writePinsToNBT(nbt, TAG_PLAYER_PINS, PinList.PLAYER_OFFSET, PinList.PLAYER_SLOTS);
        // 写入合成 Pins
        writePinsToNBT(nbt, TAG_CRAFTING_PINS, 0, PinList.CRAFTING_SLOTS);

        // 写入配置
        nbt.setInteger(TAG_MAX_PLAYER_ROWS, this.maxPlayerPinRows.ordinal());
        nbt.setInteger(TAG_MAX_CRAFTING_ROWS, this.maxCraftingPinRows.ordinal());
        nbt.setInteger(TAG_SECTION_ORDER, this.sectionOrder.ordinal());

        return nbt;
    }

    private void readPinsFromNBT(NBTTagCompound nbt, String tagKey, int offset, int maxSlots) {
        if (!nbt.hasKey(tagKey, Constants.NBT.TAG_LIST)) {
            return;
        }
        NBTTagList list = nbt.getTagList(tagKey, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount() && i < maxSlots; i++) {
            NBTTagCompound stackTag = list.getCompoundTagAt(i);
            if (!stackTag.isEmpty()) {
                IAEStack<?> stack = IAEStack.fromNBTGeneric(stackTag);
                this.pinList.setPin(offset + i, stack);
            }
        }
    }

    private void writePinsToNBT(NBTTagCompound nbt, String tagKey, int offset, int maxSlots) {
        NBTTagList list = new NBTTagList();
        int lastNonNull = -1;

        // 找到最后一个非 null 的索引
        for (int i = 0; i < maxSlots; i++) {
            if (this.pinList.getPin(offset + i) != null) {
                lastNonNull = i;
            }
        }

        // 写入到最后一个非 null 为止
        for (int i = 0; i <= lastNonNull; i++) {
            IAEStack<?> stack = this.pinList.getPin(offset + i);
            if (stack != null) {
                list.appendTag(stack.toNBTGeneric());
            } else {
                list.appendTag(new NBTTagCompound());
            }
        }

        nbt.setTag(tagKey, list);
    }
}

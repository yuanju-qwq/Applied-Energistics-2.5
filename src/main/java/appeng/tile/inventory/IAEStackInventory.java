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

package appeng.tile.inventory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants.NBT;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.util.Platform;

/**
 * 泛型 AE 栈库存，可以存储任意类型的 {@link IAEStack}（物品、流体等）。
 * <p>
 * 与 {@link AppEngInternalAEInventory} 不同，此类不限制为 {@link appeng.api.storage.data.IAEItemStack}，
 * 而是可以在同一库存中混合存储不同类型的 {@link IAEStack}。
 * </p>
 */
public class IAEStackInventory {

    private final IIAEStackInventory owner;
    private final IAEStack<?>[] inv;
    private final int size;
    private final StorageName storageName;

    /**
     * @param owner       持有此库存的对象，变更时会回调 {@link IIAEStackInventory#saveAEStackInv()}
     * @param size        槽位数量
     * @param storageName 库存名称标识
     */
    public IAEStackInventory(final IIAEStackInventory owner, final int size, StorageName storageName) {
        this.owner = owner;
        this.size = size;
        this.inv = new IAEStack<?>[size];
        this.storageName = storageName;
    }

    /**
     * @param owner 持有此库存的对象
     * @param size  槽位数量
     */
    public IAEStackInventory(final IIAEStackInventory owner, final int size) {
        this(owner, size, StorageName.NONE);
    }

    /**
     * @return 库存是否为空（所有槽位均为 null）
     */
    public boolean isEmpty() {
        for (int x = 0; x < this.size; x++) {
            if (this.inv[x] != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取指定槽位的泛型 AE 栈。
     *
     * @param slot 槽位索引
     * @return 该槽位的 IAEStack，可能为 null
     */
    @Nullable
    public IAEStack<?> getAEStackInSlot(final int slot) {
        return this.inv[slot];
    }

    /**
     * 设置指定槽位的泛型 AE 栈，并触发 {@link #markDirty()}。
     *
     * @param slot  槽位索引
     * @param stack 要放入的栈，可以为 null 表示清空
     */
    public void putAEStackInSlot(final int slot, @Nullable IAEStack<?> stack) {
        this.inv[slot] = stack;
        this.markDirty();
    }

    // ---- NBT 序列化/反序列化 ----

    /**
     * 将库存数据写入 ItemStack 的 NBT 中。
     *
     * @param stack ItemStack 目标
     * @param name  NBT 键名
     */
    public void writeToNBT(@Nonnull ItemStack stack, String name) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        this.writeToNBT(stack.getTagCompound(), name);
        if (stack.getTagCompound().isEmpty()) {
            stack.setTagCompound(null);
        }
    }

    /**
     * 将库存数据写入指定 NBT 复合标签中。
     *
     * @param data 写入目标
     * @param name NBT 键名
     */
    public void writeToNBT(final NBTTagCompound data, final String name) {
        final NBTTagCompound c = new NBTTagCompound();
        this.writeToNBTInternal(c);
        if (c.isEmpty()) {
            data.removeTag(name);
        } else {
            data.setTag(name, c);
        }
    }

    private void writeToNBTInternal(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                if (this.inv[x] != null) {
                    final NBTTagCompound c = new NBTTagCompound();
                    appeng.util.AEStackSerialization.writeStackNBT(this.inv[x], c);
                    target.setTag("#" + x, c);
                }
            } catch (final Exception ignored) {
            }
        }
    }

    /**
     * 从 NBT 复合标签中读取库存数据。
     *
     * @param data 包含库存数据的外层 NBT（可以为 null）
     * @param name NBT 键名
     */
    public void readFromNBT(@Nullable final NBTTagCompound data, final String name) {
        if (data != null && data.hasKey(name, NBT.TAG_COMPOUND)) {
            this.readFromNBTInternal(data.getCompoundTag(name));
        }
    }

    private void readFromNBTInternal(final NBTTagCompound target) {
        for (int x = 0; x < this.size; x++) {
            try {
                final String key = "#" + x;
                if (target.hasKey(key, NBT.TAG_COMPOUND)) {
                    final NBTTagCompound c = target.getCompoundTag(key);
                    // 优先尝试新的泛型格式（含 "StackType" 键）
                    IAEStack<?> stack = c.hasKey("StackType") ? IAEStack.fromNBTGeneric(c) : null;
                    if (stack == null && !c.isEmpty()) {
                        // 兼容旧版 AppEngInternalAEInventory 格式（无 "StackType" 键）
                        stack = appeng.util.item.AEItemStack.fromNBT(c);
                    }
                    this.inv[x] = stack;
                }
            } catch (final Exception e) {
                AELog.debug(e);
            }
        }
    }

    /**
     * @return 库存槽位总数
     */
    public int getSizeInventory() {
        return this.size;
    }

    /**
     * 标记库存已修改，通知持有者保存。
     */
    public void markDirty() {
        if (this.owner != null && Platform.isServer()) {
            this.owner.saveAEStackInv();
        }
    }

    /**
     * @return 此库存的名称标识
     */
    public StorageName getStorageName() {
        return this.storageName;
    }
}

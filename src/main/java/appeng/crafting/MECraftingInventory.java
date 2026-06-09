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

package appeng.crafting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.core.AELog;

/**
 * 合成模拟库存 — 使用 KeyCounter 统一存储所有类型。
 * <p>
 * 内部使用单个 {@link KeyCounter}，不再依赖任何 IAEStack 类型。
 */
public class MECraftingInventory implements IMEInventory {

    private final MECraftingInventory par;
    private final IStorageMonitorable monitorableTarget;
    private final IMEInventory legacyTarget;

    private final KeyCounter inventory = new KeyCounter();

    private final boolean logExtracted;
    private final KeyCounter extractedCache;

    private final boolean logInjections;
    private final KeyCounter injectedCache;

    private final boolean logMissing;
    private final KeyCounter missingCache;

    // ========== 构造函数 ==========

    public MECraftingInventory() {
        this.par = null;
        this.monitorableTarget = null;
        this.legacyTarget = null;
        this.extractedCache = null;
        this.injectedCache = null;
        this.missingCache = null;
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
    }

    public MECraftingInventory(final MECraftingInventory parent) {
        this.par = parent;
        this.monitorableTarget = parent.monitorableTarget;
        this.legacyTarget = parent.legacyTarget;
        this.logExtracted = parent.logExtracted;
        this.logInjections = parent.logInjections;
        this.logMissing = parent.logMissing;

        if (this.logMissing) {
            this.missingCache = new KeyCounter();
        } else {
            this.missingCache = null;
        }

        if (this.logExtracted) {
            this.extractedCache = new KeyCounter();
        } else {
            this.extractedCache = null;
        }

        if (this.logInjections) {
            this.injectedCache = new KeyCounter();
        } else {
            this.injectedCache = null;
        }

        this.inventory.addAll(parent.inventory);
    }

    public MECraftingInventory(final IStorageMonitorable target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.par = null;
        this.monitorableTarget = target;
        this.legacyTarget = null;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = new KeyCounter();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new KeyCounter();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new KeyCounter();
        } else {
            this.injectedCache = null;
        }

        for (AEKeyType keyType : AEKeyType.getAllTypes()) {
            IMEMonitor monitor = target.getInventory(keyType);
            if (monitor != null) {
                KeyCounter kc = monitor.getKeyCounter();
                for (var entry : kc) {
                    this.inventory.add(entry.getKey(), entry.getLongValue());
                }
            }
        }
    }

    public MECraftingInventory(final IMEMonitor target, final IActionSource src,
            final boolean logExtracted, final boolean logInjections, final boolean logMissing) {
        this.par = null;
        this.monitorableTarget = null;
        this.legacyTarget = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = new KeyCounter();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new KeyCounter();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new KeyCounter();
        } else {
            this.injectedCache = null;
        }

        KeyCounter kc = target.getKeyCounter();
        for (var entry : kc) {
            GenericStack extracted = target.extractItems(
                    new GenericStack(entry.getKey(), entry.getLongValue()), Actionable.SIMULATE, src);
            if (extracted != null) {
                this.inventory.add(extracted.what(), extracted.amount());
            }
        }
    }

    public MECraftingInventory(final IMEInventory target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.par = null;
        this.monitorableTarget = null;
        this.legacyTarget = target;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = new KeyCounter();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new KeyCounter();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new KeyCounter();
        } else {
            this.injectedCache = null;
        }

        KeyCounter kc = target.getAvailableKeyCounter();
        for (var entry : kc) {
            this.inventory.add(entry.getKey(), entry.getLongValue());
        }
    }

    // ========== 泛型操作方法（v2 合成树使用） ==========

    /**
     * 注入 GenericStack，无 action source。
     */
    public void injectItems(final GenericStack input, final Actionable mode) {
        injectItems(input, mode, null);
    }

    /**
     * 提取任意类型的栈（GenericStack 版本）。
     */
    public GenericStack extractAny(final GenericStack request, final Actionable mode) {
        if (request == null) return null;
        long available = this.inventory.get(request.what());
        if (available <= 0) return null;
        long extracted = Math.min(available, request.amount());
        if (mode == Actionable.MODULATE) {
            this.inventory.add(request.what(), -extracted);
            if (this.logExtracted) {
                this.extractedCache.add(request.what(), extracted);
            }
        }
        return new GenericStack(request.what(), extracted);
    }

    /**
     * 获取指定栈的模糊匹配（返回 GenericStack 列表）。
     */
    public List<GenericStack> findFuzzyAny(final GenericStack filter, final FuzzyMode fuzzy) {
        if (filter == null) return Collections.emptyList();
        var entries = this.inventory.findFuzzy(filter.what(), fuzzy);
        List<GenericStack> result = new ArrayList<>();
        for (var entry : entries) {
            result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
        }
        return result;
    }

    /**
     * 所有列表是否为空。
     */
    public boolean isEmpty() {
        return this.inventory.isEmpty();
    }

    /**
     * 重置状态（KeyCounter 无状态标志，保留为空操作）。
     */
    public void resetStatus() {
    }

    /**
     * 将全部库存写入 NBT。
     */
    public NBTTagList writeInventory() {
        NBTTagList tag = new NBTTagList();
        for (var entry : this.inventory) {
            NBTTagCompound entryTag = GenericStack.writeTag(
                    new GenericStack(entry.getKey(), entry.getLongValue()));
            tag.appendTag(entryTag);
        }
        return tag;
    }

    /**
     * 从 NBT 读取库存。
     */
    public void readInventory(NBTTagList tag) {
        this.inventory.clear();
        for (int i = 0; i < tag.tagCount(); i++) {
            NBTTagCompound entryTag = tag.getCompoundTagAt(i);
            GenericStack gs = GenericStack.readTag(entryTag);
            if (gs != null && gs.amount() > 0) {
                this.inventory.add(gs.what(), gs.amount());
            }
        }
    }

    // ========== IMEInventory 接口实现 ==========

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable mode, final IActionSource src) {
        if (input == null) return null;
        if (mode == Actionable.MODULATE) {
            this.inventory.add(input.what(), input.amount());
            if (this.logInjections) {
                this.injectedCache.add(input.what(), input.amount());
            }
        }
        return null;
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        return extractAny(request, mode);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        out.addAll(this.inventory);
        return out;
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    // ========== 提交 ==========

    /**
     * 提交更改到源库存。
     * <p>
     * 对于 IStorageMonitorable 来源，按类型分发到对应的 IMEMonitor。
     * 对于 IMEInventory 来源，只处理物品类型。
     */
    public boolean commit(final IActionSource src) {
        final KeyCounter added = new KeyCounter();
        final KeyCounter pulled = new KeyCounter();
        boolean failed = false;

        if (this.logInjections) {
            for (final var entry : this.injectedCache) {
                final GenericStack inject = new GenericStack(entry.getKey(), entry.getLongValue());
                GenericStack result = doInject(inject, Actionable.MODULATE, src);
                if (result != null) {
                    added.add(result.what(), result.amount());
                }

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                doExtract(new GenericStack(entry.getKey(), entry.getLongValue()), Actionable.MODULATE, src);
            }
            return false;
        }

        if (this.logExtracted) {
            for (final var entry : this.extractedCache) {
                final GenericStack extra = new GenericStack(entry.getKey(), entry.getLongValue());
                GenericStack result = doExtract(extra, Actionable.MODULATE, src);
                if (result != null) {
                    pulled.add(result.what(), result.amount());
                }

                if (result == null || result.amount() != extra.amount()) {
                    AELog.warn("Failed to extract %s x%d from crafting inventory", extra.what(), extra.amount());
                    failed = true;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                doExtract(new GenericStack(entry.getKey(), entry.getLongValue()), Actionable.MODULATE, src);
            }

            for (final var entry : pulled) {
                doInject(new GenericStack(entry.getKey(), entry.getLongValue()), Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logMissing && this.par != null) {
            for (final var entry : this.missingCache) {
                this.par.addMissing(entry.getKey(), entry.getLongValue());
            }
        }

        return true;
    }

    /**
     * 向目标源注入一个栈。
     * 根据来源类型（IStorageMonitorable 或 IMEInventory）分发。
     */
    private GenericStack doInject(final GenericStack stack, final Actionable mode, final IActionSource src) {
        if (stack == null) return null;

        if (this.monitorableTarget != null) {
            AEKeyType keyType = stack.what().getType();
            if (keyType == null) return stack;
            IMEMonitor monitor = this.monitorableTarget.getInventory(keyType);
            if (monitor != null) {
                return monitor.injectItems(stack, mode, src);
            }
            return stack;
        } else if (this.legacyTarget != null) {
            return this.legacyTarget.injectItems(stack, mode, src);
        }
        return stack;
    }

    /**
     * 从目标源提取一个栈。
     * 根据来源类型（IStorageMonitorable 或 IMEInventory）分发。
     */
    private GenericStack doExtract(final GenericStack stack, final Actionable mode, final IActionSource src) {
        if (stack == null) return null;

        if (this.monitorableTarget != null) {
            AEKeyType keyType = stack.what().getType();
            if (keyType == null) return null;
            IMEMonitor monitor = this.monitorableTarget.getInventory(keyType);
            if (monitor != null) {
                return monitor.extractItems(stack, mode, src);
            }
            return null;
        } else if (this.legacyTarget != null) {
            return this.legacyTarget.extractItems(stack, mode, src);
        }
        return null;
    }

    private void addMissing(final AEKey key, final long amount) {
        this.missingCache.add(key, amount);
    }

    /**
     * 将指定栈的数量设为 0（用于合成计算中忽略已使用的栈）。
     */
    public void ignore(final GenericStack what) {
        if (what == null) return;
        this.inventory.add(what.what(), -this.inventory.get(what.what()));
    }
}

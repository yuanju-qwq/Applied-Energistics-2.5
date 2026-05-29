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

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagList;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInformPlayer;
import appeng.util.Platform;
import appeng.util.item.AEItemStackType;
import appeng.util.item.IAEStackList;

/**
 * 合成模拟库存 — 多类型泛型版本。
 * <p>
 * 内部按 {@link IAEStackType} 分类存储，支持物品和流体等所有已注册的栈类型。
 * 同时保留 {@link IMEInventory}{@code <IAEItemStack>} 接口以兼容 v1 合成树。
 */
public class MECraftingInventory implements IMEInventory<IAEItemStack> {

    private final MECraftingInventory par;

    // 多类型来源（v2 合成树使用）
    private final IStorageMonitorable monitorableTarget;
    // 单类型来源（v1 合成树向后兼容）
    private final IMEInventory<IAEItemStack> legacyTarget;

    // 按类型分类的内部缓存
    private final Map<IAEStackType<?>, IItemList<?>> inventoryMap = new IdentityHashMap<>();

    private final boolean logExtracted;
    private final KeyCounter extractedCache;

    private final boolean logInjections;
    private final KeyCounter injectedCache;

    private final boolean logMissing;
    private final KeyCounter missingCache;

    // ========== 构造函数 ==========

    /**
     * 空构造函数 — 创建一个空的合成模拟库存。
     */
    public MECraftingInventory() {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        this.extractedCache = null;
        this.injectedCache = null;
        this.missingCache = null;
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
        this.monitorableTarget = null;
        this.legacyTarget = null;
        this.par = null;
    }

    private static KeyCounter newMixedList() {
        return new KeyCounter();
    }

    /**
     * Iterate any {@link IItemList}{@code <?>} as {@code IAEStack<?>} without going through {@code IAEStackBase}.
     */
    @SuppressWarnings("unchecked")
    private static Iterable<IAEStack<?>> iterateTyped(final IItemList<?> list) {
        return (Iterable<IAEStack<?>>) (Iterable<?>) list;
    }

    @SuppressWarnings("unchecked")
    private static <T extends IAEStack<T>> IItemList<T> castTypedList(final IItemList<?> list) {
        return (IItemList<T>) list;
    }

    /**
     * Add a stack to a typed list with raw casting to handle wildcard types.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void addToTypedList(final IItemList<?> list, final IAEStack<?> stack) {
        ((IItemList) list).add(stack);
    }

    /**
     * Find fuzzy matches in a typed list with raw casting to handle wildcard types.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Collection<? extends IAEStackBase> findFuzzyInTypedList(final IItemList<?> list,
            final IAEStack<?> filter, final FuzzyMode fuzzy) {
        return (Collection<? extends IAEStackBase>) (Collection<?>) ((IItemList) list).findFuzzy(filter, fuzzy);
    }

    private IItemList<?> getList(final IAEStackType<?> type) {
        return this.inventoryMap.get(type);
    }

    private IItemList<IAEItemStack> getItemListInternal() {
        return castTypedList(this.getList(AEItemStackType.INSTANCE));
    }

    private static IAEStack<?> findPreciseStack(final IItemList<?> list, final IAEStack<?> stack) {
        return list.findPreciseGeneric(stack);
    }

    private IAEStack<?> findPreciseInternal(final IAEStack<?> stack) {
        return stack == null ? null : findPreciseStack(this.getList(stack.getStackType()), stack);
    }

    private static IAEStack<?> injectToMonitor(final IMEMonitor<?> monitor, final IAEStack<?> stack,
            final Actionable mode, final IActionSource src) {
        return monitor.injectItemsGeneric(stack, mode, src);
    }

    private static IAEStack<?> extractFromMonitor(final IMEMonitor<?> monitor, final IAEStack<?> stack,
            final Actionable mode, final IActionSource src) {
        return monitor.extractItemsGeneric(stack, mode, src);
    }

    /**
     * 从父 MECraftingInventory 复制构造。
     */
    public MECraftingInventory(final MECraftingInventory parent) {
        this.monitorableTarget = parent.monitorableTarget;
        this.legacyTarget = parent.legacyTarget;
        this.logExtracted = parent.logExtracted;
        this.logInjections = parent.logInjections;
        this.logMissing = parent.logMissing;

        if (this.logMissing) {
            this.missingCache = newMixedList();
        } else {
            this.missingCache = null;
        }

        if (this.logExtracted) {
            this.extractedCache = newMixedList();
        } else {
            this.extractedCache = null;
        }

        if (this.logInjections) {
            this.injectedCache = newMixedList();
        } else {
            this.injectedCache = null;
        }

        // 复制父库存的所有内容
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IItemList<?> list = type.createList();
            this.inventoryMap.put(type, list);
            IItemList<?> parentList = parent.inventoryMap.get(type);
            if (parentList != null) {
                for (IAEStack<?> stack : iterateTyped(parentList)) {
                    list.addGeneric(stack);
                }
            }
        }

        this.par = parent;
    }

    /**
     * v2 合成树使用的构造函数 — 从 IStorageMonitorable 读取所有类型的库存。
     */
    public MECraftingInventory(final IStorageMonitorable target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.monitorableTarget = target;
        this.legacyTarget = null;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = newMixedList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = newMixedList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = newMixedList();
        } else {
            this.injectedCache = null;
        }

        // 从所有类型的 monitor 中读取库存快照
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IItemList<?> list = type.createList();
            this.inventoryMap.put(type, list);
            IMEMonitor<?> monitor = target.getInventory(type);
            if (monitor != null) {
                IItemList<?> storageList = monitor.getStorageList();
                for (IAEStack<?> stack : iterateTyped(storageList)) {
                    list.addGeneric(stack.copy());
                }
            }
        }

        this.par = null;
    }

    /**
     * v1 合成树兼容构造函数 — 从 IMEMonitor&lt;IAEItemStack&gt; 读取物品库存。
     */
    public MECraftingInventory(final IMEMonitor<IAEItemStack> target, final IActionSource src,
            final boolean logExtracted, final boolean logInjections, final boolean logMissing) {
        this.legacyTarget = target;
        this.monitorableTarget = null;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = newMixedList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = newMixedList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = newMixedList();
        } else {
            this.injectedCache = null;
        }

        // 初始化所有类型的列表，但只填充物品类型
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        IItemList<?> itemList = this.getList(AEItemStackType.INSTANCE);
        for (final IAEItemStack is : target.getStorageList()) {
            GenericStack extracted = target.extractItems(new GenericStack(is.toAEKey(), is.getStackSize()), Actionable.SIMULATE, src);
            if (extracted != null) {
                itemList.addGeneric(extracted.toIAEStack());
            }
        }

        this.par = null;
    }

    /**
     * v1 合成树兼容构造函数 — 从 IMEInventory&lt;IAEItemStack&gt; 读取物品库存。
     */
    public MECraftingInventory(final IMEInventory<IAEItemStack> target, final boolean logExtracted,
            final boolean logInjections, final boolean logMissing) {
        this.legacyTarget = target;
        this.monitorableTarget = null;
        this.logExtracted = logExtracted;
        this.logInjections = logInjections;
        this.logMissing = logMissing;

        if (logMissing) {
            this.missingCache = newMixedList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = newMixedList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = newMixedList();
        } else {
            this.injectedCache = null;
        }

        // 初始化所有类型的列表，物品类型从 target 填充
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        IItemList<IAEItemStack> itemList = this.getItemListInternal();
        KeyCounter kc = target.getAvailableKeyCounter();
        for (var entry : kc) {
            if (entry.getKey() instanceof AEItemKey itemKey) {
                itemList.add((IAEItemStack) itemKey.toIAEStack(entry.getLongValue()));
            }
        }

        this.par = null;
    }

    /**
     * 从已有的物品列表创建（v1 向后兼容）。
     */
    public MECraftingInventory(final IItemList<IAEItemStack> itemList) {
        this.legacyTarget = null;
        this.monitorableTarget = null;
        this.logExtracted = false;
        this.logInjections = false;
        this.logMissing = false;
        this.missingCache = null;
        this.extractedCache = null;
        this.injectedCache = null;

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        IItemList<?> targetList = this.getList(AEItemStackType.INSTANCE);
        for (IAEItemStack iaeItemStack : itemList) {
            targetList.addGeneric(iaeItemStack);
        }

        this.par = null;
    }

    // ========== 泛型操作方法（v2 合成树使用） ==========

    /**
     * 注入任意类型的栈。
     */
    @Deprecated
    public void injectItems(final IAEStack<?> input, final Actionable mode) {
        if (input != null && mode == Actionable.MODULATE) {
            addToTypedList(this.getList(input.getStackType()), input);
            if (this.logInjections) {
                this.injectedCache.add(input.toAEKey(), input.getStackSize());
            }
        }
    }

    /**
     * 注入 GenericStack，无 action source。
     */
    public void injectItems(final GenericStack input, final Actionable mode) {
        injectItems(input, mode, null);
    }

    public IAEStack<?> extractAny(final IAEStack<?> request, final Actionable mode) {
        if (request == null) return null;

        IAEStack<?> stack = this.findPreciseInternal(request);
        if (stack == null || stack.getStackSize() <= 0) return null;

        if (stack.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                stack.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request.toAEKey(), request.getStackSize());
                }
            }
            return request;
        }

        final IAEStack<?> ret = request.copy();
        ret.setStackSize(stack.getStackSize());
        if (mode == Actionable.MODULATE) {
            stack.reset();
            if (this.logExtracted) {
                this.extractedCache.add(request.toAEKey(), ret.getStackSize());
            }
        }
        return ret;
    }

    public GenericStack extractAny(final GenericStack request, final Actionable mode) {
        if (request == null) return null;
        IAEStack<?> aeReq = request.toIAEStack();
        if (aeReq == null) return null;
        IAEStack<?> result = extractAny(aeReq, mode);
        return result != null ? new GenericStack(result.toAEKey(), result.getStackSize()) : null;
    }

    /**
     * 提取任意类型的栈。
     */
    @SuppressWarnings("unchecked")
    public <StackType extends IAEStack> StackType extractItems(final StackType request,
            final Actionable mode) {
        return (StackType) this.extractAny(request, mode);
    }

    /**
     * 获取可用的栈列表（按类型分发或全部）。
     */
    public IItemList<IAEStackBase> getAvailableStacks(final IItemList<IAEStackBase> out) {
        IAEStackType<?> listType = out.getStackType();

        if (listType != null) {
            IItemList<?> sourceList = this.inventoryMap.get(listType);
            if (sourceList != null) {
                for (IAEStack<?> stack : iterateTyped(sourceList)) {
                    out.add(stack);
                }
            }
        } else {
            for (IItemList<?> list : this.inventoryMap.values()) {
                for (IAEStack<?> stack : iterateTyped(list)) {
                    out.add(stack);
                }
            }
        }

        return out;
    }

    /**
     * 获取指定栈的精确匹配。
     */
    public IAEStack<?> findPreciseAny(final IAEStack<?> is) {
        if (is == null) return null;
        return this.findPreciseInternal(is);
    }

    @SuppressWarnings("unchecked")
    public <StackType extends IAEStack> StackType findPrecise(final StackType is) {
        return (StackType) this.findPreciseAny(is);
    }

    public IAEItemStack findPreciseItem(final IAEItemStack item) {
        if (item == null) {
            return null;
        }
        return this.getItemListInternal().findPrecise(item);
    }

    public IAEItemStack findPreciseItem(final GenericStack item) {
        if (item == null || !(item.what() instanceof appeng.api.stacks.AEItemKey)) {
            return null;
        }
        return findPreciseItem((IAEItemStack) item.toIAEStack());
    }

    /**
     * 获取指定栈的模糊匹配。
     */
    public Collection<? extends IAEStackBase> findFuzzyAny(final IAEStack<?> filter, final FuzzyMode fuzzy) {
        if (filter == null) return null;
        return findFuzzyInTypedList(this.getList(filter.getStackType()), filter, fuzzy);
    }

    public Collection<IAEItemStack> findFuzzyItems(final IAEItemStack filter, final FuzzyMode fuzzy) {
        if (filter == null) {
            return Collections.emptyList();
        }
        return this.getItemListInternal().findFuzzy(filter, fuzzy);
    }

    public Collection<IAEItemStack> findFuzzyItems(final GenericStack filter, final FuzzyMode fuzzy) {
        if (filter == null || !(filter.what() instanceof appeng.api.stacks.AEItemKey)) {
            return Collections.emptyList();
        }
        return findFuzzyItems((IAEItemStack) filter.toIAEStack(), fuzzy);
    }

    @SuppressWarnings("unchecked")
    public <StackType extends IAEStack> Collection<StackType> findFuzzy(final StackType filter,
            final FuzzyMode fuzzy) {
        return (Collection<StackType>) (Collection<?>) this.findFuzzyAny(filter, fuzzy);
    }

    /**
     * 获取指定栈的可用量（返回副本）。
     */
    @Nonnull
    public IAEStack<?> getAvailableItem(@Nonnull IAEStack<?> request) {
        IAEStack<?> stack = this.findPreciseInternal(request);
        return stack != null ? stack.copy() : null;
    }

    /**
     * 返回内部的类型-列表映射。
     */
    public Map<IAEStackType<?>, IItemList<?>> getInventoryMap() {
        return this.inventoryMap;
    }

    /**
     * 泛型版本的 getAvailableItems，将所有类型的栈填充到输出列表中。
     */
    public void fillAvailableItems(final IItemList<IAEStackBase> out) {
        for (IItemList<?> list : this.inventoryMap.values()) {
            for (IAEStack<?> stack : iterateTyped(list)) {
                out.add(stack);
            }
        }
    }

    /**
     * 所有列表是否为空。
     */
    public boolean isEmpty() {
        for (IItemList<?> list : this.inventoryMap.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 重置所有列表的状态。
     */
    public void resetStatus() {
        for (IItemList<?> list : this.inventoryMap.values()) {
            list.resetStatus();
        }
    }

    /**
     * 将全部库存写入 NBT。
     */
    public NBTTagList writeInventory() {
        NBTTagList tag = new NBTTagList();
        for (IItemList<?> list : this.inventoryMap.values()) {
            NBTTagList subList = appeng.util.AEStackSerialization.writeAEStackListNBT(list);
            for (int i = 0; i < subList.tagCount(); i++) {
                tag.appendTag(subList.getCompoundTagAt(i));
            }
        }
        return tag;
    }

    /**
     * 从 NBT 读取库存。
     */
    public void readInventory(NBTTagList tag) {
        final IAEStackList bridge = new IAEStackList();
        appeng.util.AEStackSerialization.readAEStackListNBT((appeng.util.item.IMixedStackList) bridge, tag);
        for (IAEStack<?> stack : bridge.typedView()) {
            injectItems(stack, Actionable.MODULATE);
        }
    }

    // ========== IMEInventory<IAEItemStack> 接口方法（v1 向后兼容） ==========

    @Override
    @Deprecated
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final IActionSource src) {
        if (input == null) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            this.getItemListInternal().add(input);
            if (this.logInjections) {
                this.injectedCache.add(input.toAEKey(), input.getStackSize());
            }
        }

        return null;
    }

    @Override
    @Deprecated
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final IActionSource src) {
        if (request == null) {
            return null;
        }

        final IAEItemStack list = this.getItemListInternal().findPrecise(request);
        if (list == null || list.getStackSize() == 0) {
            return null;
        }

        if (list.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                list.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request.toAEKey(), request.getStackSize());
                }
            }

            return request;
        }

        final IAEItemStack ret = request.copy();
        ret.setStackSize(list.getStackSize());

        if (mode == Actionable.MODULATE) {
            list.reset();
            if (this.logExtracted) {
                this.extractedCache.add(ret.toAEKey(), ret.getStackSize());
            }
        }

        return ret;
    }

    @Override
    @Deprecated
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        IItemList<?> itemList = this.getList(AEItemStackType.INSTANCE);
        if (itemList != null) {
            for (IAEStack<?> stack : iterateTyped(itemList)) {
                out.add((IAEItemStack) stack);
            }
        }
        return out;
    }

    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return AEItemStackType.INSTANCE;
    }

    // ========== GenericStack / KeyCounter override ==========

    @Override
    public GenericStack injectItems(final GenericStack input, final Actionable mode, final IActionSource src) {
        if (input == null) return null;
        IAEStack<?> aeStack = input.toIAEStack();
        if (aeStack == null) return input;
        if (mode == Actionable.MODULATE) {
            addToTypedList(this.getList(aeStack.getStackType()), aeStack);
            if (this.logInjections) {
                this.injectedCache.add(input.what(), input.amount());
            }
        }
        return null;
    }

    @Override
    public GenericStack extractItems(final GenericStack request, final Actionable mode, final IActionSource src) {
        if (request == null) return null;
        IAEStack<?> aeRequest = request.toIAEStack();
        if (aeRequest == null) return null;
        IAEStack<?> list = this.findPreciseInternal(aeRequest);
        if (list == null || list.getStackSize() <= 0) return null;
        long extracted = Math.min(list.getStackSize(), request.amount());
        if (mode == Actionable.MODULATE) {
            list.decStackSize(extracted);
            if (this.logExtracted) {
                this.extractedCache.add(request.what(), extracted);
            }
        }
        return new GenericStack(request.what(), extracted);
    }

    public GenericStack extractItems(final GenericStack request, final Actionable mode) {
        return extractItems(request, mode, null);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        KeyCounter out = new KeyCounter();
        for (IItemList<?> list : this.inventoryMap.values()) {
            for (IAEStack<?> stack : iterateTyped(list)) {
                out.add(stack.toAEKey(), stack.getStackSize());
            }
        }
        return out;
    }

    // ========== 旧 API 兼容 ==========

    /**
     * 获取物品类型的列表（v1 向后兼容）。
     */
    public IItemList<IAEItemStack> getItemList() {
        return this.getItemListInternal();
    }

    /**
     * 提交更改到源库存。
     * <p>
     * 对于 IStorageMonitorable 来源，按类型分发到对应的 IMEMonitor。
     * 对于 IMEInventory&lt;IAEItemStack&gt; 来源，只处理物品类型。
     */
    public boolean commit(final IActionSource src) {
        final KeyCounter added = newMixedList();
        final KeyCounter pulled = newMixedList();
        boolean failed = false;

        if (this.logInjections) {
            for (final var entry : this.injectedCache) {
                final IAEStack<?> inject = entry.getKey().toIAEStack(entry.getLongValue());
                IAEStack<?> result = doInject(inject, Actionable.MODULATE, src);
                if (result != null) {
                    added.add(result.toAEKey(), result.getStackSize());
                }

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                doExtract(entry.getKey().toIAEStack(entry.getLongValue()), Actionable.MODULATE, src);
            }
            return false;
        }

        if (this.logExtracted) {
            for (final var entry : this.extractedCache) {
                final IAEStack<?> extra = entry.getKey().toIAEStack(entry.getLongValue());
                IAEStack<?> result = doExtract(extra, Actionable.MODULATE, src);
                if (result != null) {
                    pulled.add(result.toAEKey(), result.getStackSize());
                }

                if (result == null || result.getStackSize() != extra.getStackSize()) {
                    if (src.player().isPresent() && extra instanceof IAEItemStack) {
                        try {
                            if (result == null) {
                                NetworkHandler.instance()
                                        .sendTo(new PacketInformPlayer((IAEItemStack) extra, null,
                                                PacketInformPlayer.InfoType.NO_ITEMS_EXTRACTED),
                                                (EntityPlayerMP) src.player().get());
                            } else {
                                NetworkHandler.instance()
                                        .sendTo(new PacketInformPlayer((IAEItemStack) extra,
                                                (IAEItemStack) result,
                                                PacketInformPlayer.InfoType.PARTIAL_ITEM_EXTRACTION),
                                                (EntityPlayerMP) src.player().get());
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    failed = true;
                }
            }
        }

        if (failed) {
            for (final var entry : added) {
                doExtract(entry.getKey().toIAEStack(entry.getLongValue()), Actionable.MODULATE, src);
            }

            for (final var entry : pulled) {
                doInject(entry.getKey().toIAEStack(entry.getLongValue()), Actionable.MODULATE, src);
            }

            return false;
        }

        if (this.logMissing && this.par != null) {
            for (final var entry : this.missingCache) {
                this.par.addMissing(entry.getKey().toIAEStack(entry.getLongValue()));
            }
        }

        return true;
    }

    /**
     * 向目标源注入一个栈。
     * 根据来源类型（IStorageMonitorable 或 IMEInventory）分发。
     */
    private IAEStack<?> doInject(final IAEStack<?> stack, final Actionable mode, final IActionSource src) {
        if (stack == null) return null;

        if (this.monitorableTarget != null) {
            // 多类型路径
            IMEMonitor<?> monitor = this.monitorableTarget.getInventory(stack.getStackType());
            if (monitor != null) {
                return injectToMonitor(monitor, stack, mode, src);
            }
            return stack; // 没有对应 monitor，返回原栈表示注入失败
        } else if (this.legacyTarget != null && stack instanceof IAEItemStack) {
            // v1 向后兼容路径 — 只处理物品
            GenericStack result = this.legacyTarget.injectItems(new GenericStack(stack.toAEKey(), stack.getStackSize()), mode, src);
            return result != null ? result.toIAEStack() : null;
        }
        return stack;
    }

    /**
     * 从目标源提取一个栈。
     * 根据来源类型（IStorageMonitorable 或 IMEInventory）分发。
     */
    private IAEStack<?> doExtract(final IAEStack<?> stack, final Actionable mode, final IActionSource src) {
        if (stack == null) return null;

        if (this.monitorableTarget != null) {
            // 多类型路径
            IMEMonitor<?> monitor = this.monitorableTarget.getInventory(stack.getStackType());
            if (monitor != null) {
                return extractFromMonitor(monitor, stack, mode, src);
            }
            return null;
        } else if (this.legacyTarget != null && stack instanceof IAEItemStack) {
            // v1 向后兼容路径 — 只处理物品
            GenericStack result = this.legacyTarget.extractItems(new GenericStack(stack.toAEKey(), stack.getStackSize()), mode, src);
            return result != null ? result.toIAEStack() : null;
        }
        return null;
    }

    private void addMissing(final IAEStack<?> extra) {
        this.missingCache.add(extra.toAEKey(), extra.getStackSize());
    }

    /**
     * 将指定栈的数量设为 0（用于合成计算中忽略已使用的栈）。
     */
    public void ignore(final IAEStack<?> what) {
        if (what == null) return;
        IAEStack<?> stack = this.findPreciseInternal(what);
        if (stack != null) {
            stack.setStackSize(0);
        }
    }

    /**
     * v1 向后兼容版本。
     */
    void ignore(final IAEItemStack what) {
        ignore(what);
    }
}

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
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagList;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
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
@SuppressWarnings({"rawtypes", "unchecked"})
public class MECraftingInventory implements IMEInventory<IAEItemStack> {

    private final MECraftingInventory par;

    // 多类型来源（v2 合成树使用）
    private final IStorageMonitorable monitorableTarget;
    // 单类型来源（v1 合成树向后兼容）
    private final IMEInventory<IAEItemStack> legacyTarget;

    // 按类型分类的内部缓存
    private final Map<IAEStackType<?>, IItemList> inventoryMap = new IdentityHashMap<>();

    private final boolean logExtracted;
    private final IItemList extractedCache;

    private final boolean logInjections;
    private final IItemList injectedCache;

    private final boolean logMissing;
    private final IItemList missingCache;

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
            this.missingCache = new IAEStackList();
        } else {
            this.missingCache = null;
        }

        if (this.logExtracted) {
            this.extractedCache = new IAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (this.logInjections) {
            this.injectedCache = new IAEStackList();
        } else {
            this.injectedCache = null;
        }

        // 复制父库存的所有内容
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IItemList list = type.createList();
            this.inventoryMap.put(type, list);
            IItemList parentList = parent.inventoryMap.get(type);
            if (parentList != null) {
                for (Object stack : parentList) {
                    list.add((IAEStack) stack);
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
            this.missingCache = new IAEStackList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new IAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new IAEStackList();
        } else {
            this.injectedCache = null;
        }

        // 从所有类型的 monitor 中读取库存快照
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            IItemList list = type.createList();
            this.inventoryMap.put(type, list);
            IMEMonitor monitor = target.getMEMonitor(type);
            if (monitor != null) {
                for (Object is : monitor.getStorageList()) {
                    if (is instanceof IAEStack<?> stack) {
                        list.add(stack.copy());
                    }
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
            this.missingCache = new IAEStackList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new IAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new IAEStackList();
        } else {
            this.injectedCache = null;
        }

        // 初始化所有类型的列表，但只填充物品类型
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        IItemList itemList = this.inventoryMap.get(
                AEItemStackType.INSTANCE);
        for (final IAEItemStack is : target.getStorageList()) {
            itemList.add(target.extractItems(is, Actionable.SIMULATE, src));
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
            this.missingCache = new IAEStackList();
        } else {
            this.missingCache = null;
        }

        if (logExtracted) {
            this.extractedCache = new IAEStackList();
        } else {
            this.extractedCache = null;
        }

        if (logInjections) {
            this.injectedCache = new IAEStackList();
        } else {
            this.injectedCache = null;
        }

        // 初始化所有类型的列表，物品类型从 target 填充
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.inventoryMap.put(type, type.createList());
        }
        target.getAvailableItems(this.inventoryMap.get(
                AEItemStackType.INSTANCE));

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
        IItemList targetList = this.inventoryMap.get(
                AEItemStackType.INSTANCE);
        for (IAEItemStack iaeItemStack : itemList) {
            targetList.add(iaeItemStack);
        }

        this.par = null;
    }

    // ========== 泛型操作方法（v2 合成树使用） ==========

    /**
     * 注入任意类型的栈。
     */
    public void injectItems(final IAEStack<?> input, final Actionable mode) {
        if (input != null && mode == Actionable.MODULATE) {
            this.inventoryMap.get(input.getStackType()).add(input);
            if (this.logInjections) {
                this.injectedCache.add(input);
            }
        }
    }

    /**
     * 提取任意类型的栈。
     */
    public <StackType extends IAEStack<StackType>> StackType extractItems(final StackType request,
            final Actionable mode) {
        if (request == null) return null;

        IAEStack<?> stack = (IAEStack<?>) this.inventoryMap.get(request.getStackType()).findPrecise(request);
        if (stack == null || stack.getStackSize() <= 0) return null;

        if (stack.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                stack.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request);
                }
            }
            return request;
        }

        final StackType ret = request.copy();
        ret.setStackSize(stack.getStackSize());

        if (mode == Actionable.MODULATE) {
            stack.reset();
            if (this.logExtracted) {
                this.extractedCache.add(ret);
            }
        }

        return ret;
    }

    /**
     * 获取可用的栈列表（按类型分发或全部）。
     */
    public IItemList getAvailableStacks(final IItemList out) {
        IAEStackType<?> listType = out.getStackType();

        if (listType != null) {
            // 单类型列表：只填充对应类型
            IItemList sourceList = this.inventoryMap.get(listType);
            if (sourceList != null) {
                for (Object stack : sourceList) {
                    out.add((IAEStack) stack);
                }
            }
        } else {
            // 多类型列表：填充所有类型
            for (IItemList list : this.inventoryMap.values()) {
                for (Object stack : list) {
                    out.add((IAEStack) stack);
                }
            }
        }

        return out;
    }

    /**
     * 获取指定栈的精确匹配。
     */
    public <StackType extends IAEStack<StackType>> StackType findPrecise(final StackType is) {
        if (is == null) return null;
        return (StackType) this.inventoryMap.get(is.getStackType()).findPrecise(is);
    }

    /**
     * 获取指定栈的模糊匹配。
     */
    public <StackType extends IAEStack<StackType>> Collection<StackType> findFuzzy(final StackType filter,
            final FuzzyMode fuzzy) {
        if (filter == null) return null;
        return (Collection) this.inventoryMap.get(filter.getStackType()).findFuzzy(filter, fuzzy);
    }

    /**
     * 获取指定栈的可用量（返回副本）。
     */
    @Nonnull
    public IAEStack<?> getAvailableItem(@Nonnull IAEStack<?> request) {
        IAEStack<?> stack = (IAEStack<?>) this.inventoryMap.get(request.getStackType()).findPrecise(request);
        return stack != null ? stack.copy() : null;
    }

    /**
     * 返回内部的类型-列表映射。
     */
    public Map<IAEStackType<?>, IItemList> getInventoryMap() {
        return this.inventoryMap;
    }

    /**
     * 所有列表是否为空。
     */
    public boolean isEmpty() {
        for (IItemList list : this.inventoryMap.values()) {
            if (!list.isEmpty()) return false;
        }
        return true;
    }

    /**
     * 重置所有列表的状态。
     */
    public void resetStatus() {
        for (IItemList list : this.inventoryMap.values()) {
            list.resetStatus();
        }
    }

    /**
     * 将全部库存写入 NBT。
     */
    public NBTTagList writeInventory() {
        NBTTagList tag = new NBTTagList();
        for (IItemList list : this.inventoryMap.values()) {
            NBTTagList subList = Platform.writeAEStackListNBT(list);
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
        IItemList tempList = new IAEStackList();
        Platform.readAEStackListNBT(tempList, tag);
        for (Object stack : tempList) {
            if (stack instanceof IAEStack<?> aeStack) {
                injectItems(aeStack, Actionable.MODULATE);
            }
        }
    }

    // ========== IMEInventory<IAEItemStack> 接口方法（v1 向后兼容） ==========

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable mode, final IActionSource src) {
        if (input == null) {
            return null;
        }

        if (mode == Actionable.MODULATE) {
            this.inventoryMap.get(input.getStackType()).add(input);
            if (this.logInjections) {
                this.injectedCache.add(input);
            }
        }

        return null;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final IActionSource src) {
        if (request == null) {
            return null;
        }

        final IAEItemStack list = (IAEItemStack) this.inventoryMap.get(request.getStackType()).findPrecise(request);
        if (list == null || list.getStackSize() == 0) {
            return null;
        }

        if (list.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) {
                list.decStackSize(request.getStackSize());
                if (this.logExtracted) {
                    this.extractedCache.add(request);
                }
            }

            return request;
        }

        final IAEItemStack ret = request.copy();
        ret.setStackSize(list.getStackSize());

        if (mode == Actionable.MODULATE) {
            list.reset();
            if (this.logExtracted) {
                this.extractedCache.add(ret);
            }
        }

        return ret;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out) {
        IItemList itemList = this.inventoryMap.get(
                AEItemStackType.INSTANCE);
        if (itemList != null) {
            for (Object is : itemList) {
                out.add((IAEItemStack) is);
            }
        }
        return out;
    }

    @Override
    public IStorageChannel getChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    // ========== 旧 API 兼容 ==========

    /**
     * 获取物品类型的列表（v1 向后兼容）。
     */
    public IItemList<IAEItemStack> getItemList() {
        return this.inventoryMap.get(
                AEItemStackType.INSTANCE);
    }

    /**
     * 提交更改到源库存。
     * <p>
     * 对于 IStorageMonitorable 来源，按类型分发到对应的 IMEMonitor。
     * 对于 IMEInventory&lt;IAEItemStack&gt; 来源，只处理物品类型。
     */
    public boolean commit(final IActionSource src) {
        final IItemList added = new IAEStackList();
        final IItemList pulled = new IAEStackList();
        boolean failed = false;

        // 注入阶段
        if (this.logInjections) {
            for (final Object obj : this.injectedCache) {
                final IAEStack<?> inject = (IAEStack<?>) obj;
                IAEStack<?> result = doInject(inject, Actionable.MODULATE, src);
                added.add(result);

                if (result != null) {
                    failed = true;
                    break;
                }
            }
        }

        // 注入失败回滚
        if (failed) {
            for (final Object obj : added) {
                final IAEStack<?> is = (IAEStack<?>) obj;
                doExtract(is, Actionable.MODULATE, src);
            }
            return false;
        }

        // 提取阶段
        if (this.logExtracted) {
            for (final Object obj : this.extractedCache) {
                final IAEStack<?> extra = (IAEStack<?>) obj;
                IAEStack<?> result = doExtract(extra, Actionable.MODULATE, src);
                pulled.add(result);

                if (result == null || result.getStackSize() != extra.getStackSize()) {
                    // 只对物品类型发送通知包（流体类型暂不支持通知）
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

        // 提取失败回滚
        if (failed) {
            for (final Object obj : added) {
                final IAEStack<?> is = (IAEStack<?>) obj;
                doExtract(is, Actionable.MODULATE, src);
            }

            for (final Object obj : pulled) {
                final IAEStack<?> is = (IAEStack<?>) obj;
                doInject(is, Actionable.MODULATE, src);
            }

            return false;
        }

        // 传播 missing 到父级
        if (this.logMissing && this.par != null) {
            for (final Object obj : this.missingCache) {
                final IAEStack<?> extra = (IAEStack<?>) obj;
                this.par.addMissing(extra);
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
            IMEMonitor monitor = this.monitorableTarget.getMEMonitor(stack.getStackType());
            if (monitor != null) {
                return (IAEStack<?>) monitor.injectItems(stack, mode, src);
            }
            return stack; // 没有对应 monitor，返回原栈表示注入失败
        } else if (this.legacyTarget != null && stack instanceof IAEItemStack) {
            // v1 向后兼容路径 — 只处理物品
            return this.legacyTarget.injectItems((IAEItemStack) stack, mode, src);
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
            IMEMonitor monitor = this.monitorableTarget.getMEMonitor(stack.getStackType());
            if (monitor != null) {
                return (IAEStack<?>) monitor.extractItems(stack, mode, src);
            }
            return null;
        } else if (this.legacyTarget != null && stack instanceof IAEItemStack) {
            // v1 向后兼容路径 — 只处理物品
            return this.legacyTarget.extractItems((IAEItemStack) stack, mode, src);
        }
        return null;
    }

    private void addMissing(final IAEStack<?> extra) {
        this.missingCache.add(extra);
    }

    /**
     * 将指定栈的数量设为 0（用于合成计算中忽略已使用的栈）。
     */
    public void ignore(final IAEStack<?> what) {
        if (what == null) return;
        IAEStack<?> stack = (IAEStack<?>) this.inventoryMap.get(what.getStackType()).findPrecise(what);
        if (stack != null) {
            stack.setStackSize(0);
        }
    }

    /**
     * v1 向后兼容版本。
     */
    void ignore(final IAEItemStack what) {
        ignore((IAEStack<?>) what);
    }
}

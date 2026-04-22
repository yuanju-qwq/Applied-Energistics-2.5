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

package appeng.client.mui;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.core.AELog;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;

/**
 * MUI GUI 工厂注册表。
 * <p>
 * 替代 {@link GuiBridge} 中硬编码的枚举→GUI 映射，提供可插拔的注册机制。
 * 第三方 Addon 和内部模块都可以通过此工厂注册自定义 GUI。
 *
 * <h3>双键查询</h3>
 * 支持两种注册键：
 * <ul>
 *   <li>{@link AEGuiKey} — 新体系，推荐使用</li>
 *   <li>{@link GuiBridge} — 旧体系兼容，查询时自动转换为 {@link AEGuiKey}</li>
 * </ul>
 *
 * <h3>工厂接口</h3>
 * 提供两组工厂接口：
 * <ul>
 *   <li>新接口 {@link IHostContainerFactory} / {@link IHostGuiFactory}：
 *       接收 (InventoryPlayer, hostObject)，零反射直接创建</li>
 *   <li>旧接口 {@link IContainerFactory} / {@link IGuiFactory}：
 *       接收 (EntityPlayer, World, x, y, z)，保留向后兼容</li>
 * </ul>
 */
public final class AEMUIGuiFactory {

    private AEMUIGuiFactory() {
    }

    // ========== 新工厂接口（推荐） ==========

    /**
     * 服务端 Container 工厂（新接口）。
     * <p>
     * 接收已解析好的宿主对象，无需再做 TileEntity/Part 查找。
     */
    @FunctionalInterface
    public interface IHostContainerFactory {
        @Nullable
        Container createContainer(InventoryPlayer inventory, Object host);
    }

    /**
     * 客户端 GUI 工厂（新接口）。
     * <p>
     * 接收已解析好的宿主对象，无需再做 TileEntity/Part 查找。
     */
    @FunctionalInterface
    public interface IHostGuiFactory {
        @SideOnly(Side.CLIENT)
        @Nullable
        Object createGui(InventoryPlayer inventory, Object host);
    }

    // ========== 旧工厂接口（兼容） ==========

    /**
     * 服务端 Container 工厂（旧接口，保留向后兼容）。
     */
    @FunctionalInterface
    public interface IContainerFactory {
        @Nullable
        Container createContainer(EntityPlayer player, World world, int x, int y, int z);
    }

    /**
     * 客户端 GUI 工厂（旧接口，保留向后兼容）。
     */
    @FunctionalInterface
    public interface IGuiFactory {
        @SideOnly(Side.CLIENT)
        @Nullable
        Object createGui(EntityPlayer player, World world, int x, int y, int z);
    }

    // ========== 注册项 ==========

    /**
     * 注册项：一组 Container 工厂 + GUI 工厂。
     * <p>
     * 可以同时持有新旧两组工厂接口，优先使用新接口。
     */
    public static final class Registration {
        @Nullable
        private final IContainerFactory containerFactory;
        @Nullable
        private final IGuiFactory guiFactory;
        @Nullable
        private final IHostContainerFactory hostContainerFactory;
        @Nullable
        private final IHostGuiFactory hostGuiFactory;

        /**
         * 旧接口构造。
         */
        public Registration(IContainerFactory containerFactory, IGuiFactory guiFactory) {
            this.containerFactory = containerFactory;
            this.guiFactory = guiFactory;
            this.hostContainerFactory = null;
            this.hostGuiFactory = null;
        }

        /**
         * 新接口构造。
         */
        public Registration(IHostContainerFactory hostContainerFactory, IHostGuiFactory hostGuiFactory) {
            this.containerFactory = null;
            this.guiFactory = null;
            this.hostContainerFactory = hostContainerFactory;
            this.hostGuiFactory = hostGuiFactory;
        }

        @Nullable
        public IContainerFactory getContainerFactory() {
            return containerFactory;
        }

        @Nullable
        public IGuiFactory getGuiFactory() {
            return guiFactory;
        }

        @Nullable
        public IHostContainerFactory getHostContainerFactory() {
            return hostContainerFactory;
        }

        @Nullable
        public IHostGuiFactory getHostGuiFactory() {
            return hostGuiFactory;
        }
    }

    // ========== 注册表 ==========

    /** 以 AEGuiKey 为键的注册表（新体系） */
    private static final Map<AEGuiKey, Registration> keyRegistry = new HashMap<>();

    /** 以 GuiBridge 枚举值为键的注册表（旧体系兼容） */
    private static final Map<GuiBridge, Registration> legacyRegistry = new IdentityHashMap<>();

    // ========== 新体系注册方法 ==========

    /**
     * 使用新接口注册一个 MUI GUI（推荐）。
     *
     * @param key                  GUI 标识键
     * @param hostContainerFactory 服务端 Container 创建工厂
     * @param hostGuiFactory       客户端 GUI 创建工厂
     */
    public static void register(AEGuiKey key, IHostContainerFactory hostContainerFactory,
            IHostGuiFactory hostGuiFactory) {
        Registration reg = new Registration(hostContainerFactory, hostGuiFactory);
        Registration prev = keyRegistry.put(key, reg);
        if (prev != null) {
            AELog.warn("MUI GUI registration overwritten for key: %s", key.getId());
        }
        // 自动添加旧体系兼容映射
        if (key.getLegacyBridge() != null) {
            legacyRegistry.put(key.getLegacyBridge(), reg);
        }
    }

    // ========== 旧体系注册方法（兼容） ==========

    /**
     * 使用旧接口注册一个 MUI GUI（保留向后兼容）。
     *
     * @param key              GuiBridge 枚举值
     * @param containerFactory 服务端 Container 创建工厂
     * @param guiFactory       客户端 GUI 创建工厂
     */
    public static void register(GuiBridge key, IContainerFactory containerFactory, IGuiFactory guiFactory) {
        Registration reg = new Registration(containerFactory, guiFactory);
        legacyRegistry.put(key, reg);
        // 尝试同步到新体系
        AEGuiKey guiKey = AEGuiKeys.fromLegacy(key);
        if (guiKey != null) {
            keyRegistry.put(guiKey, reg);
        }
    }

    // ========== 新体系查询方法 ==========

    /**
     * 查询指定 key 是否有 MUI GUI 注册。
     */
    public static boolean hasMUIGui(AEGuiKey key) {
        return keyRegistry.containsKey(key);
    }

    /**
     * 获取指定 key 的注册项。
     */
    @Nullable
    public static Registration getRegistration(AEGuiKey key) {
        return keyRegistry.get(key);
    }

    /**
     * 通过 MUI 工厂创建服务端 Container（新接口）。
     *
     * @param key  GUI 标识键
     * @param ip   玩家背包
     * @param host 已解析的宿主对象
     * @return Container 实例，如果没有注册或创建失败则返回 null
     */
    @Nullable
    public static Container createContainer(AEGuiKey key, InventoryPlayer ip, Object host) {
        Registration reg = keyRegistry.get(key);
        if (reg == null || reg.hostContainerFactory == null) {
            return null;
        }
        try {
            return reg.hostContainerFactory.createContainer(ip, host);
        } catch (Exception e) {
            AELog.warn(e, "Failed to create MUI container for key: %s", key.getId());
            return null;
        }
    }

    /**
     * 通过 MUI 工厂创建客户端 GUI（新接口）。
     *
     * @param key  GUI 标识键
     * @param ip   玩家背包
     * @param host 已解析的宿主对象
     * @return GUI 实例，如果没有注册或创建失败则返回 null
     */
    @SideOnly(Side.CLIENT)
    @Nullable
    public static Object createGui(AEGuiKey key, InventoryPlayer ip, Object host) {
        Registration reg = keyRegistry.get(key);
        if (reg == null || reg.hostGuiFactory == null) {
            return null;
        }
        try {
            return reg.hostGuiFactory.createGui(ip, host);
        } catch (Exception e) {
            AELog.warn(e, "Failed to create MUI GUI for key: %s", key.getId());
            return null;
        }
    }

    // ========== 旧体系查询方法（兼容） ==========

    /**
     * 查询指定 key 是否有 MUI GUI 注册（旧接口）。
     */
    public static boolean hasMUIGui(GuiBridge key) {
        return legacyRegistry.containsKey(key);
    }

    /**
     * 获取指定 key 的注册项（旧接口）。
     */
    @Nullable
    public static Registration getRegistration(GuiBridge key) {
        return legacyRegistry.get(key);
    }

    /**
     * 通过 MUI 工厂创建服务端 Container（旧接口）。
     *
     * @return Container 实例，如果没有注册或创建失败则返回 null
     */
    @Nullable
    public static Container createContainer(GuiBridge key, EntityPlayer player, World world,
            int x, int y, int z) {
        Registration reg = legacyRegistry.get(key);
        if (reg == null) {
            return null;
        }
        try {
            if (reg.containerFactory != null) {
                return reg.containerFactory.createContainer(player, world, x, y, z);
            }
            return null;
        } catch (Exception e) {
            AELog.warn(e, "Failed to create MUI container for key: %s", key.name());
            return null;
        }
    }

    /**
     * 通过 MUI 工厂创建客户端 GUI（旧接口）。
     *
     * @return GUI 实例，如果没有注册或创建失败则返回 null
     */
    @SideOnly(Side.CLIENT)
    @Nullable
    public static Object createGui(GuiBridge key, EntityPlayer player, World world,
            int x, int y, int z) {
        Registration reg = legacyRegistry.get(key);
        if (reg == null) {
            return null;
        }
        try {
            if (reg.guiFactory != null) {
                return reg.guiFactory.createGui(player, world, x, y, z);
            }
            return null;
        } catch (Exception e) {
            AELog.warn(e, "Failed to create MUI GUI for key: %s", key.name());
            return null;
        }
    }

    /**
     * 清空所有注册项（用于测试）。
     */
    public static void clearAll() {
        keyRegistry.clear();
        legacyRegistry.clear();
    }
}

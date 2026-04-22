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

package appeng.core.sync;

import javax.annotation.Nullable;

import net.minecraft.util.ResourceLocation;

import appeng.api.config.SecurityPermissions;

/**
 * GUI 标识键，替代 {@link GuiBridge} 枚举的标识符角色。
 * <p>
 * 每个 {@code AEGuiKey} 实例代表一种唯一的 GUI 类型，使用
 * {@link ResourceLocation} 作为唯一键（如 {@code appliedenergistics2:me_terminal}）。
 * 包含 GUI 的元数据：宿主类型、权限要求、宿主类匹配。
 * <p>
 * 所有预定义键在 {@link AEGuiKeys} 常量类中声明。
 *
 * <h3>与 GuiBridge 的兼容</h3>
 * 每个 {@code AEGuiKey} 可通过 {@link #getLegacyBridge()} 关联到对应的
 * {@link GuiBridge} 枚举值，用于过渡期的双向查询。
 *
 * @see AEGuiKeys
 * @see GuiBridge
 */
public final class AEGuiKey {

    private final ResourceLocation id;
    private final GuiHostType hostType;
    @Nullable
    private final SecurityPermissions requiredPermission;
    private final Class<?> hostClass;

    // 与 GuiBridge 的兼容映射（过渡期使用）
    @Nullable
    private final GuiBridge legacyBridge;

    private AEGuiKey(Builder builder) {
        this.id = builder.id;
        this.hostType = builder.hostType;
        this.requiredPermission = builder.permission;
        this.hostClass = builder.hostClass;
        this.legacyBridge = builder.legacyBridge;
    }

    /**
     * 获取此 GUI 的唯一标识。
     */
    public ResourceLocation getId() {
        return this.id;
    }

    /**
     * 获取此 GUI 的宿主类型（WORLD / ITEM / ITEM_OR_WORLD）。
     */
    public GuiHostType getHostType() {
        return this.hostType;
    }

    /**
     * 获取打开此 GUI 所需的安全权限。
     *
     * @return 权限要求，如果不需要权限检查则返回 {@code null}
     */
    @Nullable
    public SecurityPermissions getRequiredPermission() {
        return this.requiredPermission;
    }

    /**
     * 获取此 GUI 的宿主类（用于 {@link GuiBridge#CorrectTileOrPart} 检查）。
     */
    public Class<?> getHostClass() {
        return this.hostClass;
    }

    /**
     * 获取对应的旧 {@link GuiBridge} 枚举值。
     *
     * @return 对应的 GuiBridge，如果是纯新增的 GUI 则返回 {@code null}
     */
    @Nullable
    public GuiBridge getLegacyBridge() {
        return this.legacyBridge;
    }

    /**
     * 检查给定的宿主对象是否与此 GUI 的 hostClass 匹配。
     */
    public boolean isValidHost(Object host) {
        return this.hostClass.isInstance(host);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AEGuiKey)) {
            return false;
        }
        return this.id.equals(((AEGuiKey) o).id);
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public String toString() {
        return "AEGuiKey{" + this.id + '}';
    }

    /**
     * 创建一个新的构建器。
     *
     * @param namespace 资源命名空间（如 {@code "appliedenergistics2"}）
     * @param path      资源路径（如 {@code "me_terminal"}）
     */
    public static Builder builder(String namespace, String path) {
        return new Builder(new ResourceLocation(namespace, path));
    }

    /**
     * {@link AEGuiKey} 的构建器。
     */
    public static final class Builder {

        private final ResourceLocation id;
        private GuiHostType hostType = GuiHostType.WORLD;
        @Nullable
        private SecurityPermissions permission;
        private Class<?> hostClass = Object.class;
        @Nullable
        private GuiBridge legacyBridge;

        private Builder(ResourceLocation id) {
            this.id = id;
        }

        /**
         * 设置 GUI 的宿主类型。
         */
        public Builder hostType(GuiHostType type) {
            this.hostType = type;
            return this;
        }

        /**
         * 设置打开 GUI 所需的安全权限。
         */
        public Builder permission(@Nullable SecurityPermissions perm) {
            this.permission = perm;
            return this;
        }

        /**
         * 设置 GUI 宿主类（用于类型校验）。
         */
        public Builder hostClass(Class<?> cls) {
            this.hostClass = cls;
            return this;
        }

        /**
         * 设置对应的旧 {@link GuiBridge} 枚举值（过渡期兼容用）。
         */
        public Builder legacyBridge(GuiBridge bridge) {
            this.legacyBridge = bridge;
            return this;
        }

        /**
         * 构建 {@link AEGuiKey} 实例。
         */
        public AEGuiKey build() {
            return new AEGuiKey(this);
        }
    }
}

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage.data;

import java.io.IOException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.storage.IStorageChannel;

/**
 * 表示一种 AE 存储栈的类型（如物品、流体等）。
 * <p>
 * 每种类型都提供序列化/反序列化能力、容器交互能力以及 GUI 按钮资源。
 *
 * @param <T> 该类型对应的 {@link IAEStack} 子类型
 */
public interface IAEStackType<T extends IAEStack<T>> {

    /**
     * @return 该类型的唯一字符串 ID，如 "item"、"fluid"
     */
    String getId();

    /**
     * @return 本地化的显示名称
     */
    String getDisplayName();

    /**
     * @return 单位名称，如 "" 或 "mB"
     */
    String getDisplayUnit();

    /**
     * 从 NBT 加载一个栈实例。
     */
    @Nullable
    T loadStackFromNBT(@Nonnull NBTTagCompound tag);

    /**
     * 从网络包加载一个栈实例。
     */
    @Nullable
    T loadStackFromPacket(@Nonnull ByteBuf buffer) throws IOException;

    /**
     * 创建此类型对应的 {@link IItemList}。
     */
    @Nonnull
    IItemList<T> createList();

    /**
     * @return 每个单位的数量，如物品为 1，流体为 1000 (mB)
     */
    int getAmountPerUnit();

    /**
     * @return 在聊天/Tooltip 中用于该类型的颜色
     */
    default TextFormatting getColorDefinition() {
        return TextFormatting.AQUA;
    }

    /**
     * 判断给定的 ItemStack 是否为本类型的容器（如水桶是流体的容器）。
     */
    boolean isContainerItemForType(@Nullable ItemStack container);

    /**
     * 从容器物品中提取本类型的栈。
     *
     * @param container 容器物品
     * @return 提取出的栈，如不支持返回 null
     */
    @Nullable
    T getStackFromContainerItem(@Nonnull ItemStack container);

    /**
     * @return 该类型 GUI 按钮使用的纹理资源位置
     */
    @Nullable
    ResourceLocation getButtonTexture();

    /**
     * @return 按钮图标在纹理中的 U 坐标起始位置 (0~256)
     */
    int getButtonIconU();

    /**
     * @return 按钮图标在纹理中的 V 坐标起始位置 (0~256)
     */
    int getButtonIconV();

    /**
     * @return 该类型对应的 {@link IStorageChannel} 实例，用于向后兼容现有的 channel 体系
     */
    @Nonnull
    IStorageChannel<T> getStorageChannel();
}

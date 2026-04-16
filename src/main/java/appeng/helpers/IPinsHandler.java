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

package appeng.helpers;

import javax.annotation.Nullable;

import appeng.api.config.PinSectionOrder;
import appeng.api.config.PinsRows;
import appeng.api.storage.data.IAEStack;
import appeng.items.contents.PinList;

/**
 * 终端 Pins 系统的处理器接口。
 * <p>
 * 管理 Pins 的增删、配置和同步。
 */
public interface IPinsHandler {

    /**
     * @return Pins 数据列表
     */
    PinList getPins();

    /**
     * @return 合成 Pins 最大行数配置
     */
    PinsRows getMaxCraftingPinRows();

    /**
     * @return 玩家 Pins 最大行数配置
     */
    PinsRows getMaxPlayerPinRows();

    /**
     * 设置合成 Pins 最大行数。
     */
    void setMaxCraftingPinRows(PinsRows rows);

    /**
     * 设置玩家 Pins 最大行数。
     */
    void setMaxPlayerPinRows(PinsRows rows);

    /**
     * @return 两个 Pin 分区的显示顺序
     */
    PinSectionOrder getSectionOrder();

    /**
     * 设置两个 Pin 分区的显示顺序。
     */
    void setSectionOrder(PinSectionOrder order);

    /**
     * 在玩家 Pins 区域中添加一个栈。
     *
     * @param stack 要钉选的栈
     * @return 是否添加成功（可能已满或已存在）
     */
    boolean addPlayerPin(@Nullable IAEStack<?> stack);

    /**
     * 从所有区域中移除指定栈的 Pin。
     *
     * @param stack 要取消钉选的栈
     * @return 是否有移除操作发生
     */
    boolean removePin(@Nullable IAEStack<?> stack);

    /**
     * 判断指定栈是否已被钉选。
     */
    boolean isPinned(@Nullable IAEStack<?> stack);

    /**
     * 标记 Pins 数据已变更，需要同步到客户端。
     */
    void markDirty();
}

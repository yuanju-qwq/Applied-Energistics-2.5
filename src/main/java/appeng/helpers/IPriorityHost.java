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

import net.minecraft.item.ItemStack;

import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;

public interface IPriorityHost {

    /**
     * get current priority.
     */
    int getPriority();

    /**
     * set new priority
     */
    void setPriority(int newValue);

    ItemStack getItemStackRepresentation();

    /**
     * @deprecated 使用 {@link #getGuiKey()} 代替。
     */
    @Deprecated
    GuiBridge getGuiBridge();

    /**
     * 返回此宿主对应的 {@link AEGuiKey}（新体系）。
     * <p>
     * 默认实现通过 {@link #getGuiBridge()} 做旧体系兼容转换，
     * 子类应优先覆写此方法以直接返回 {@link AEGuiKeys} 常量。
     */
    default AEGuiKey getGuiKey() {
        return AEGuiKeys.fromLegacy(getGuiBridge());
    }
}

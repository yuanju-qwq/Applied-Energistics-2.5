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

package appeng.items.tools.powered;

import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;

/**
 * 通用无线终端的模式枚举。
 * 每个模式对应一种无线终端类型，关联其 {@link AEGuiKey}。
 */
public enum WirelessTerminalMode {

    TERMINAL((byte) 0, "terminal", AEGuiKeys.WIRELESS_TERM),
    CRAFTING((byte) 1, "crafting", AEGuiKeys.WIRELESS_CRAFTING_TERMINAL),
    FLUID((byte) 2, "fluid", AEGuiKeys.WIRELESS_FLUID_TERMINAL),
    PATTERN((byte) 3, "pattern", AEGuiKeys.WIRELESS_PATTERN_TERMINAL),
    INTERFACE((byte) 4, "interface", AEGuiKeys.WIRELESS_INTERFACE_TERMINAL),
    DUAL_INTERFACE((byte) 5, "dual_interface", AEGuiKeys.WIRELESS_DUAL_INTERFACE_TERMINAL);

    private final byte id;
    private final String name;
    private final AEGuiKey guiKey;

    WirelessTerminalMode(byte id, String name, AEGuiKey guiKey) {
        this.id = id;
        this.name = name;
        this.guiKey = guiKey;
    }

    public byte getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * 获取此模式关联的 {@link AEGuiKey}。
     */
    public AEGuiKey getGuiKey() {
        return guiKey;
    }

    /**
     * 获取此模式关联的旧 {@link GuiBridge}（兼容用）。
     */
    public GuiBridge getGuiBridge() {
        return guiKey.getLegacyBridge();
    }

    /**
     * 根据 mode id 查找对应的枚举值。
     *
     * @param id mode id
     * @return 对应的 WirelessTerminalMode，找不到时返回 TERMINAL
     */
    public static WirelessTerminalMode fromId(byte id) {
        for (WirelessTerminalMode mode : values()) {
            if (mode.id == id) {
                return mode;
            }
        }
        return TERMINAL;
    }
}

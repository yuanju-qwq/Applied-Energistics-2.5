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
 * Mode enumeration for the universal wireless terminal.
 * Each mode corresponds to a wireless terminal type, associated with its {@link AEGuiKey}.
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
     * Get the {@link AEGuiKey} associated with this mode.
     */
    public AEGuiKey getGuiKey() {
        return guiKey;
    }

    /**
     * Get the legacy {@link GuiBridge} associated with this mode (for compatibility).
     */
    public GuiBridge getGuiBridge() {
        return guiKey.getLegacyBridge();
    }

    /**
     * Find the corresponding enum value by mode id.
     *
     * @param id mode id
     * @return the corresponding WirelessTerminalMode, or TERMINAL if not found
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

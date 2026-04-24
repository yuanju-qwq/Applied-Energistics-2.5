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
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.core.AELog;
import appeng.core.sync.AEGuiKey;

/**
 * MUI GUI factory registry.
 * <p>
 * Replaces the hardcoded enum-to-GUI mapping in GuiBridge with a pluggable
 * registration mechanism. Both internal modules and third-party addons can
 * register custom GUIs through this factory.
 *
 * <h3>Factory Interfaces</h3>
 * <ul>
 *   <li>{@link IHostContainerFactory} — server-side Container creation</li>
 *   <li>{@link IHostGuiFactory} — client-side GUI creation</li>
 * </ul>
 * Both receive the pre-resolved host object, avoiding any reflection.
 */
public final class AEMUIGuiFactory {

    private AEMUIGuiFactory() {
    }

    // ========== Factory Interfaces ==========

    /**
     * Server-side Container factory.
     * <p>
     * Receives the already-resolved host object; no TileEntity/Part lookup needed.
     */
    @FunctionalInterface
    public interface IHostContainerFactory {
        @Nullable
        Container createContainer(InventoryPlayer inventory, Object host);
    }

    /**
     * Client-side GUI factory.
     * <p>
     * Receives the already-resolved host object; no TileEntity/Part lookup needed.
     */
    @FunctionalInterface
    public interface IHostGuiFactory {
        @SideOnly(Side.CLIENT)
        @Nullable
        Object createGui(InventoryPlayer inventory, Object host);
    }

    // ========== Registration Entry ==========

    /**
     * A registration entry holding a Container factory and a GUI factory pair.
     */
    public static final class Registration {
        private final IHostContainerFactory hostContainerFactory;
        private final IHostGuiFactory hostGuiFactory;

        public Registration(IHostContainerFactory hostContainerFactory, IHostGuiFactory hostGuiFactory) {
            this.hostContainerFactory = hostContainerFactory;
            this.hostGuiFactory = hostGuiFactory;
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

    // ========== Registry ==========

    /** AEGuiKey-based registry */
    private static final Map<AEGuiKey, Registration> keyRegistry = new HashMap<>();

    // ========== Registration ==========

    /**
     * Register a MUI GUI.
     *
     * @param key                  the GUI identifier key
     * @param hostContainerFactory server-side Container creation factory
     * @param hostGuiFactory       client-side GUI creation factory
     */
    public static void register(AEGuiKey key, IHostContainerFactory hostContainerFactory,
            IHostGuiFactory hostGuiFactory) {
        Registration reg = new Registration(hostContainerFactory, hostGuiFactory);
        Registration prev = keyRegistry.put(key, reg);
        if (prev != null) {
            AELog.warn("MUI GUI registration overwritten for key: %s", key.getId());
        }
    }

    // ========== Query ==========

    /**
     * Check whether the specified key has a MUI GUI registered.
     */
    public static boolean hasMUIGui(AEGuiKey key) {
        return keyRegistry.containsKey(key);
    }

    /**
     * Get the registration entry for the specified key.
     */
    @Nullable
    public static Registration getRegistration(AEGuiKey key) {
        return keyRegistry.get(key);
    }

    /**
     * Create a server-side Container via the MUI factory.
     *
     * @param key  the GUI identifier key
     * @param ip   player inventory
     * @param host the resolved host object
     * @return Container instance, or null if not registered or creation failed
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
     * Create a client-side GUI via the MUI factory.
     *
     * @param key  the GUI identifier key
     * @param ip   player inventory
     * @param host the resolved host object
     * @return GUI instance, or null if not registered or creation failed
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

    /**
     * Clear all registrations (for testing).
     */
    public static void clearAll() {
        keyRegistry.clear();
    }
}

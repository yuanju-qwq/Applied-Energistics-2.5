/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 - 2015 AlgorithmX2
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

package appeng.api.features;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;

import appeng.api.config.TunnelType;

/**
 * Registry for P2P tunnel attunements.
 * <p>
 * An "attunement" defines what item, mod, or Forge Capability triggers the conversion
 * of a generic P2P tunnel into a specific tunnel type. When a player right-clicks a
 * P2P tunnel with a trigger item, AE2 looks up the tunnel type in this registry and
 * converts the tunnel accordingly.
 * <p>
 * Access via {@code AEApi.instance().registries().p2pTunnel()}.
 * <p>
 * <b>Attunement types:</b>
 * <ul>
 *   <li><b>ItemStack</b>: right-clicking with a specific item converts to this tunnel type.
 *       Example: right-click with a Bucket → Fluid tunnel.</li>
 *   <li><b>ModId</b>: any item from the given mod triggers the attunement.
 *       Example: any Thermal Expansion item → FE Power tunnel.</li>
 *   <li><b>Capability</b>: any item exposing the given Forge Capability triggers the attunement.
 *       Example: any item with {@code IFluidHandlerItem} → Fluid tunnel.</li>
 * </ul>
 * <p>
 * <b>Priority</b>: ItemStack matching is checked first, then Capability, then ModId.
 * <p>
 * <b>Third-party mods</b> should register attunements during mod initialization
 * (e.g., FMLInitializationEvent or InterModEnqueueEvent).
 *
 * <h3>Example:</h3>
 * <pre>{@code
 * IP2PTunnelRegistry reg = AEApi.instance().registries().p2pTunnel();
 *
 * // Register a new tunnel type (if not already done)
 * TunnelType MANA = TunnelType.registerTunnelType("MANA", manaP2PPartStack);
 *
 * // Attune by specific item
 * reg.addNewAttunement(new ItemStack(ModItems.MANA_POOL), MANA);
 *
 * // Attune by Forge Capability (any item with this capability triggers attunement)
 * reg.addNewAttunement(ModCapabilities.MANA_HANDLER, MANA);
 *
 * // Attune by mod id (any item from this mod triggers attunement)
 * reg.addNewAttunement("botania", MANA);
 * }</pre>
 *
 * @see TunnelType
 * @see TunnelType#registerTunnelType(String, ItemStack)
 * @see appeng.parts.p2p.PartP2PTunnel
 */
public interface IP2PTunnelRegistry {

    /**
     * Register an item-based attunement trigger.
     * <p>
     * When a player right-clicks a P2P tunnel with this item, the tunnel converts
     * to the specified type.
     *
     * @param trigger the item which triggers attunement (must not be empty)
     * @param type    the tunnel type to attune to (null to remove)
     */
    void addNewAttunement(@Nonnull ItemStack trigger, @Nullable TunnelType type);

    /**
     * Register a mod-based attunement trigger.
     * <p>
     * Any item from the specified mod will trigger attunement to the given type.
     * This has the lowest priority (checked after ItemStack and Capability).
     *
     * @param ModId the mod identifier (e.g., "botania", "thermalexpansion")
     * @param type  the tunnel type to attune to (null to remove)
     */
    void addNewAttunement(@Nonnull String ModId, @Nullable TunnelType type);

    /**
     * Register a Capability-based attunement trigger.
     * <p>
     * Any item that exposes the given Forge Capability will trigger attunement
     * to the specified type. This is the recommended approach for third-party mods,
     * as it automatically covers all items that implement the capability.
     *
     * @param cap  the Forge Capability to match (e.g., {@code CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY})
     * @param type the tunnel type to attune to (null to remove)
     */
    void addNewAttunement(@Nonnull Capability<?> cap, @Nullable TunnelType type);

    /**
     * Look up the tunnel type for the given trigger item.
     * <p>
     * Checks in order: ItemStack match → Capability match → ModId match.
     *
     * @param trigger attunement trigger item
     * @return the matching tunnel type, or null if no attunement is found
     */
    @Nullable
    TunnelType getTunnelTypeByItem(ItemStack trigger);

    /**
     * Convenience method to register a new {@link TunnelType} enum constant and return it.
     * <p>
     * Equivalent to calling {@link TunnelType#registerTunnelType(String, ItemStack)}.
     *
     * @param enumName  the unique enum constant name
     * @param partStack the P2P tunnel part ItemStack
     * @return the newly registered TunnelType
     */
    TunnelType registerTunnelType(@Nonnull String enumName, @Nonnull ItemStack partStack);
}

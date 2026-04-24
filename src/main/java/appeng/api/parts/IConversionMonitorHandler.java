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

package appeng.api.parts;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;

/**
 * Handles type-specific player interactions for the Conversion Monitor part.
 * <p>
 * Each {@link IAEStackType} registers an implementation that knows how to:
 * <ul>
 *   <li>Determine if a held item can interact with this type (e.g., fluid containers for fluids)</li>
 *   <li>Convert a held item into the corresponding {@link IAEStack} for matching</li>
 *   <li>Insert resources from the player into the ME network</li>
 *   <li>Insert all matching resources from the player's inventory into the ME network</li>
 *   <li>Extract resources from the ME network to the player</li>
 * </ul>
 * <p>
 * This abstraction replaces all {@code instanceof IAEItemStack / IAEFluidStack}
 * dispatching in the Conversion Monitor.
 *
 * @param <T> the concrete stack type
 */
public interface IConversionMonitorHandler<T extends IAEStack<T>> {

    /**
     * @return the stack type this handler manages
     */
    @Nonnull
    IAEStackType<T> getStackType();

    /**
     * Determine if the player's held item can interact with this stack type
     * as a container (e.g., a bucket can interact with fluid type).
     * <p>
     * Note: this is for "container" interactions only (like drain/fill).
     * Direct item insertion (held item IS the resource) is handled separately.
     *
     * @param heldItem the item the player is holding
     * @return true if this handler can interact with the held item as a container
     */
    boolean canInteractWithContainer(@Nonnull ItemStack heldItem);

    /**
     * Extract the AE stack representation from a container item held by the player.
     * <p>
     * For example, for fluid type, this would return the fluid contained in a bucket.
     *
     * @param heldItem the item the player is holding
     * @return the AE stack extracted from the container, or null if not applicable
     */
    @Nullable
    T getStackFromContainer(@Nonnull ItemStack heldItem);

    /**
     * Insert a single held item/container into the ME network.
     * <p>
     * For items: inserts the held ItemStack directly.
     * For fluids: drains the fluid from the held container into the network.
     *
     * @param player  the interacting player
     * @param hand    the hand holding the item
     * @param energy  the energy source for the operation
     * @param monitor the ME monitor for this stack type
     * @param src     the action source
     */
    void insertFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<T> monitor,
            @Nonnull IActionSource src);

    /**
     * Insert all matching items from the player's inventory into the ME network.
     * <p>
     * This is used in "locked" mode when the player right-clicks with an empty hand.
     * Only resources matching the displayed stack should be inserted.
     *
     * @param player    the interacting player
     * @param displayed the currently displayed stack on the monitor (guaranteed to be of this handler's type)
     * @param energy    the energy source for the operation
     * @param monitor   the ME monitor for this stack type
     * @param src       the action source
     */
    void insertAllFromPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull T displayed,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<T> monitor,
            @Nonnull IActionSource src);

    /**
     * Extract resources from the ME network and give them to the player.
     * <p>
     * For items: extracts ItemStacks and adds them to the player's inventory
     *            (or drops them in the world if inventory is full).
     * For fluids: fills the player's held fluid container from the network.
     *
     * @param player    the interacting player
     * @param hand      the hand holding the item (may be needed for container interactions)
     * @param displayed the currently displayed stack on the monitor
     * @param count     the amount to extract (for items: stack count; implementation may interpret differently)
     * @param energy    the energy source for the operation
     * @param monitor   the ME monitor for this stack type
     * @param src       the action source
     * @param host      the conversion monitor part, used for getting tile/side info for item drops
     */
    void extractToPlayer(
            @Nonnull EntityPlayer player,
            @Nonnull EnumHand hand,
            @Nonnull T displayed,
            long count,
            @Nonnull IEnergySource energy,
            @Nonnull IMEMonitor<T> monitor,
            @Nonnull IActionSource src,
            @Nonnull IConversionMonitorHost host);

    /**
     * Resolve the configured stack from a player's held item for the storage monitor.
     * <p>
     * When the monitor is unlocked and the player right-clicks with an item,
     * this method determines what stack to configure the monitor to display.
     * <p>
     * For items: returns AEItemStack from the held ItemStack.
     * For fluids: returns AEFluidStack from a fluid container.
     * <p>
     * The returned stack should have stackSize set to 0 (it's a type reference, not a quantity).
     *
     * @param heldItem the item the player is holding
     * @return the configured stack with stackSize=0, or null if this handler cannot resolve the held item
     */
    @Nullable
    T resolveConfiguredStack(@Nonnull ItemStack heldItem);
}

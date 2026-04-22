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

package appeng.util;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.InvalidParameterException;
import java.text.DecimalFormat;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.RegistryNamespaced;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.oredict.OreDictionary;

import ic2.api.item.ICustomDamageItem;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import mezz.jei.config.Config;

import io.netty.buffer.ByteBuf;

import appeng.api.AEApi;
import appeng.api.config.*;
import appeng.api.definitions.IItemDefinition;
import appeng.api.definitions.IParts;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.implementations.items.IAEWrench;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.security.ISecurityGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackBase;
import appeng.api.storage.data.IItemList;
import appeng.api.util.AEColor;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.features.AEFeature;
import appeng.core.stats.Stats;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.GuiHostType;
import appeng.core.sync.GuiWrapper;
import appeng.fluids.util.AEFluidStack;
import appeng.hooks.TickHandler;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;
import appeng.integration.modules.gregtech.ToolClass;
import appeng.me.GridAccessException;
import appeng.me.GridNode;
import appeng.me.helpers.AENetworkProxy;
import appeng.util.helpers.ItemComparisonHelper;
import appeng.util.helpers.P2PHelper;
import appeng.util.item.AEItemStack;
import appeng.util.prioritylist.IPartitionList;

/**
 * @author AlgorithmX2
 * @author thatsIch
 * @version rv2
 * @since rv0
 */
@Optional.Interface(iface = "ic2.api.item.ICustomDamageItem", modid = "IC2")
public class Platform {
    private static final Object2BooleanMap<String> CACHED_MODS = new Object2BooleanOpenHashMap<>();

    public static final Block AIR_BLOCK = Blocks.AIR;

    public static final int DEF_OFFSET = 16;

    private static final boolean CLIENT_INSTALL = FMLCommonHandler.instance().getSide().isClient();

    /*
     * random source, use it for item drop locations...
     */
    private static final Random RANDOM_GENERATOR = new Random();
    // private static Method getEntry;

    private static final ItemComparisonHelper ITEM_COMPARISON_HELPER = new ItemComparisonHelper();
    private static final P2PHelper P2P_HELPER = new P2PHelper();
    public static final boolean GTLoaded = isModLoaded("gregtech");

    public static ItemComparisonHelper itemComparisons() {
        return ITEM_COMPARISON_HELPER;
    }

    public static P2PHelper p2p() {
        return P2P_HELPER;
    }

    public static Random getRandom() {
        return RANDOM_GENERATOR;
    }

    public static float getRandomFloat() {
        return RANDOM_GENERATOR.nextFloat();
    }

    /**
     * This displays the value for encoded longs ( double *100 )
     *
     * @param n      to be formatted long value
     * @param isRate if true it adds a /t to the formatted string
     * @return formatted long value
     */
    public static String formatPowerLong(final long n, final boolean isRate) {
        double p = ((double) n) / 100;

        final PowerUnits displayUnits = AEConfig.instance().selectedPowerUnit();
        p = PowerUnits.AE.convertTo(displayUnits, p);

        final String[] preFixes = {
                "k", "M", "G", "T", "P", "T", "P", "E", "Z", "Y"
        };
        String unitName = displayUnits.name();

        String level = "";
        int offset = 0;
        while (p > 1000 && offset < preFixes.length) {
            p /= 1000;
            level = preFixes[offset];
            offset++;
        }

        final DecimalFormat df = new DecimalFormat("#.##");
        return df.format(p) + ' ' + level + unitName + (isRate ? "/t" : "");
    }

    public static void openGUI(@Nonnull final EntityPlayer p, @Nullable final TileEntity tile,
            @Nullable final AEPartLocation side, @Nonnull final GuiBridge type) {
        if (isClient()) {
            return;
        }

        if (type.getExternalGui() != null) {
            GuiWrapper.IExternalGui obj = type.getExternalGui();
            GuiWrapper.Opener opener = GuiWrapper.INSTANCE.getOpener(obj.getID());
            if (opener == null) {
                AELog.warn("External Gui with ID: %s is missing a opener.", obj.getID());
            } else {
                World world = tile == null ? p.world : tile.getWorld();
                BlockPos pos = tile == null ? null : tile.getPos();
                EnumFacing face = side == null ? null : side.getFacing();
                opener.open(obj, new GuiWrapper.GuiContext(world, p, pos, face, null));
            }
            return;
        }

        int x = 0;
        int y = 0;
        int z = Integer.MIN_VALUE;

        if (tile != null) {
            x = tile.getPos().getX();
            y = tile.getPos().getY();
            z = tile.getPos().getZ();
        } else {
            if (p.openContainer instanceof IInventorySlotAware) {
                x = ((IInventorySlotAware) p.openContainer).getInventorySlot();
                y = ((IInventorySlotAware) p.openContainer).isBaubleSlot() ? 1 : 0;
            } else {
                x = p.inventory.currentItem;
            }
        }

        if ((type.getType().isItem() && tile == null) || type.hasPermissions(tile, x, y, z, side, p)) {
            if (tile == null && type.getType() == GuiHostType.ITEM) {
                p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), x, 0, 0);
            } else if (tile == null || type.getType() == GuiHostType.ITEM) {
                if (tile != null) {
                    p.openGui(AppEng.instance(), type.ordinal() << 4 | (1 << 3), p.getEntityWorld(), x, y, z);
                } else {
                    p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), x, y, z);
                }
            } else {
                p.openGui(AppEng.instance(), type.ordinal() << 4 | (side.ordinal()), tile.getWorld(), x, y, z);
            }
        }
    }

    /**
     * 使用 {@link AEGuiKey} 打开 GUI（新接口）。
     * <p>
     * 通过 {@link AEGuiKey#getLegacyBridge()} 转换为 {@link GuiBridge}，
     * 委托给旧的 {@link #openGUI(EntityPlayer, TileEntity, AEPartLocation, GuiBridge)}。
     */
    public static void openGUI(@Nonnull final EntityPlayer p, @Nullable final TileEntity tile,
            @Nullable final AEPartLocation side, @Nonnull final AEGuiKey guiKey) {
        final GuiBridge bridge = guiKey.getLegacyBridge();
        if (bridge == null) {
            AELog.warn("AEGuiKey %s has no legacy GuiBridge mapping, cannot open GUI", guiKey.getId());
            return;
        }
        openGUI(p, tile, side, bridge);
    }

    /**
     * 使用 {@link AEGuiKey} 在指定槽位打开 GUI（新接口）。
     */
    public static void openGUI(@Nonnull final EntityPlayer p, int slot, @Nonnull final AEGuiKey guiKey,
            boolean isBauble) {
        final GuiBridge bridge = guiKey.getLegacyBridge();
        if (bridge == null) {
            AELog.warn("AEGuiKey %s has no legacy GuiBridge mapping, cannot open GUI", guiKey.getId());
            return;
        }
        openGUI(p, slot, bridge, isBauble);
    }

    public static void openGUI(@Nonnull final EntityPlayer p, int slot, @Nonnull final GuiBridge type,
            boolean isBauble) {
        if (isClient()) {
            return;
        }

        if (type.getExternalGui() != null) {
            GuiWrapper.IExternalGui obj = type.getExternalGui();
            GuiWrapper.Opener opener = GuiWrapper.INSTANCE.getOpener(obj.getID());
            if (opener == null) {
                AELog.warn("External Gui with ID: %s is missing a opener.", obj.getID());
            } else {
                NBTTagCompound extra = new NBTTagCompound();
                extra.setInteger("slot", slot);
                extra.setBoolean("isBauble", isBauble);
                opener.open(obj, new GuiWrapper.GuiContext(p.world, p, null, null, extra));
            }
            return;
        }

        if (type.getType().isItem()) {
            p.openGui(AppEng.instance(), type.ordinal() << 4, p.getEntityWorld(), slot, isBauble ? 1 : 0,
                    Integer.MIN_VALUE);
        }
    }

    /*
     * returns true if the code is on the client.
     */
    public static boolean isClient() {
        return FMLCommonHandler.instance().getEffectiveSide().isClient();
    }

    /*
     * returns true if client classes are available.
     */
    public static boolean isClientInstall() {
        return CLIENT_INSTALL;
    }

    /*
     * returns true if the code is on the server.
     */
    public static boolean isServer() {
        return FMLCommonHandler.instance().getEffectiveSide().isServer();
    }

    public static int getRandomInt() {
        return Math.abs(RANDOM_GENERATOR.nextInt());
    }

    public static boolean isModLoaded(final String modid) {
        return CACHED_MODS.computeIfAbsent(modid, (k) -> {
            try {
                // if this fails for some reason, try the other method.
                return Loader.isModLoaded(k);
            } catch (final Throwable ignored) {
            }

            return Loader.instance().getActiveModList()
                    .stream().anyMatch(mod -> mod.getModId().equals(k));
        });
    }

    public static boolean isJEIEnabled() {
        if (isModLoaded("jei")) {
            return Config.isOverlayEnabled();
        } else {
            return false;
        }
    }

    public static boolean isJEICenterSearchBarEnabled() {
        if (isModLoaded("jei")) {
            return Config.isCenterSearchBarEnabled() && Config.isOverlayEnabled();
        } else {
            return false;
        }
    }

    public static ItemStack findMatchingRecipeOutput(final InventoryCrafting ic, final World world) {
        return CraftingManager.findMatchingResult(ic, world);
    }

    @SideOnly(Side.CLIENT)
    public static List<String> getTooltip(final Object o) {
        if (o == null) {
            return new ArrayList<>();
        }

        ItemStack itemStack = ItemStack.EMPTY;
        if (o instanceof AEItemStack ais) {
            return ais.getToolTip();
        } else if (o instanceof ItemStack) {
            itemStack = (ItemStack) o;
        } else {
            return new ArrayList<>();
        }

        try {
            ITooltipFlag.TooltipFlags tooltipFlag = Minecraft.getMinecraft().gameSettings.advancedItemTooltips
                    ? ITooltipFlag.TooltipFlags.ADVANCED
                    : ITooltipFlag.TooltipFlags.NORMAL;
            return itemStack.getTooltip(Minecraft.getMinecraft().player, tooltipFlag);
        } catch (final Exception errB) {
            return new ArrayList<>();
        }
    }

    public static String getModId(final IAEItemStack is) {
        if (is == null) {
            return "** Null";
        }

        final String n = ((AEItemStack) is).getModID();
        return n == null ? "** Null" : n;
    }

    public static String getModId(final IAEFluidStack fs) {
        if (fs == null || fs.getFluidStack() == null) {
            return "** Null";
        }

        final String n = FluidRegistry.getModId(fs.getFluidStack());
        return n == null ? "** Null" : n;
    }

    public static String getItemDisplayName(final Object o) {
        if (o == null) {
            return "** Null";
        }

        ItemStack itemStack = ItemStack.EMPTY;
        if (o instanceof AEItemStack) {
            final String n = ((AEItemStack) o).getDisplayName();
            return n == null ? "** Null" : n;
        } else if (o instanceof IAEStack) {
            // 泛型 IAEStack 类型（包括 AEFluidStack 等），使用通用的 getDisplayName()
            final String n = ((IAEStack<?>) o).getDisplayName();
            return n == null ? "** Null" : n;
        } else if (o instanceof ItemStack) {
            itemStack = (ItemStack) o;
        } else {
            return "**Invalid Object";
        }

        try {
            String name = itemStack.getDisplayName();
            if (name == null || name.isEmpty()) {
                name = itemStack.getItem().getTranslationKey(itemStack);
            }
            return name == null ? "** Null" : name;
        } catch (final Exception errA) {
            try {
                final String n = itemStack.getTranslationKey();
                return n == null ? "** Null" : n;
            } catch (final Exception errB) {
                return "** Exception";
            }
        }
    }

    public static String getFluidDisplayName(Object o) {
        if (o == null) {
            return "** Null";
        }
        FluidStack fluidStack = null;
        if (o instanceof AEFluidStack) {
            fluidStack = ((AEFluidStack) o).getFluidStack();
        } else if (o instanceof FluidStack) {
            fluidStack = (FluidStack) o;
        } else {
            return "**Invalid Object";
        }
        String n = fluidStack.getLocalizedName();
        if (n == null || "".equalsIgnoreCase(n)) {
            n = fluidStack.getUnlocalizedName();
        }
        return n == null ? "** Null" : n;
    }

    public static boolean isWrench(final EntityPlayer player, final ItemStack eq, final BlockPos pos) {
        if (!eq.isEmpty()) {
            try {
                // TODO: Build Craft Wrench?
                /*
                 * if( eq.getItem() instanceof IToolWrench ) { IToolWrench wrench = (IToolWrench) eq.getItem(); return
                 * wrench.canWrench( player, x, y, z ); }
                 */

                if (eq.getItem() instanceof cofh.api.item.IToolHammer) {
                    return ((cofh.api.item.IToolHammer) eq.getItem()).isUsable(eq, player, pos);
                }
            } catch (final Throwable ignore) { // explodes without BC

            }

            if (eq.getItem() instanceof IAEWrench wrench) {
                return wrench.canWrench(eq, player, pos);
            }
        }
        return false;
    }

    public static boolean isChargeable(final ItemStack i) {
        if (i.isEmpty()) {
            return false;
        }
        final Item it = i.getItem();
        if (it instanceof IAEItemPowerStorage) {
            return ((IAEItemPowerStorage) it).getPowerFlow(i) != AccessRestriction.READ;
        }
        return false;
    }

    public static int MC2MEColor(final int color) {
        switch (color) {
            case 4: // "blue"
                return 0;
            case 0: // "black"
                return 1;
            case 15: // "white"
                return 2;
            case 3: // "brown"
                return 3;
            case 1: // "red"
                return 4;
            case 11: // "yellow"
                return 5;
            case 2: // "green"
                return 6;

            case 5: // "purple"
            case 6: // "cyan"
            case 7: // "silver"
            case 8: // "gray"
            case 9: // "pink"
            case 10: // "lime"
            case 12: // "lightBlue"
            case 13: // "magenta"
            case 14: // "orange"
        }
        return -1;
    }

    public static int findEmpty(final RegistryNamespaced registry, final int minId, final int maxId) {
        for (int x = minId; x < maxId; x++) {
            if (registry.getObjectById(x) == null) {
                return x;
            }
        }
        return -1;
    }

    public static int findEmpty(final Object[] l) {
        for (int x = 0; x < l.length; x++) {
            if (l[x] == null) {
                return x;
            }
        }
        return -1;
    }

    /**
     * Returns a random element from the given collection.
     *
     * @return null if the collection is empty
     */
    @Nullable
    public static <T> T pickRandom(final Collection<T> outs) {
        if (outs.isEmpty()) {
            return null;
        }

        int index = RANDOM_GENERATOR.nextInt(outs.size());
        return Iterables.get(outs, index, null);
    }

    @SideOnly(Side.CLIENT)
    public static String gui_localize(final String string) {
        return I18n.translateToLocal(string);
    }

    private static boolean bothUsingDefaultSecurity(GridNode a, GridNode b) {
        return a.getLastSecurityKey() == -1 && b.getLastSecurityKey() == -1;
    }

    private static boolean securityKeysMatch(GridNode a, GridNode b) {
        return a.getLastSecurityKey() == b.getLastSecurityKey();
    }

    public static boolean securityCheck(final GridNode a, final GridNode b) {
        if (bothUsingDefaultSecurity(a, b) || securityKeysMatch(a, b)) {
            return true;
        }

        final boolean a_isSecure = isPowered(a.getGrid()) && a.getLastSecurityKey() != -1;
        final boolean b_isSecure = isPowered(b.getGrid()) && b.getLastSecurityKey() != -1;

        if (AEConfig.instance().isFeatureEnabled(AEFeature.LOG_SECURITY_AUDITS)) {
            final String locationA = a.getGridBlock().isWorldAccessible() ? a.getGridBlock().getLocation().toString()
                    : "notInWorld";
            final String locationB = b.getGridBlock().isWorldAccessible() ? b.getGridBlock().getLocation().toString()
                    : "notInWorld";

            AELog.info(
                    "Audit: Node A [isSecure=%b, key=%d, playerID=%d, location={%s}] vs Node B[isSecure=%b, key=%d, playerID=%d, location={%s}]",
                    a_isSecure, a.getLastSecurityKey(), a.getPlayerID(), locationA, b_isSecure, b.getLastSecurityKey(),
                    b.getPlayerID(), locationB);
        }

        // can't do that son...
        if (a_isSecure && b_isSecure) {
            return false;
        }

        if (!a_isSecure && b_isSecure) {
            return checkPlayerPermissions(b.getGrid(), a.getPlayerID());
        }

        if (a_isSecure && !b_isSecure) {
            return checkPlayerPermissions(a.getGrid(), b.getPlayerID());
        }

        return true;
    }

    private static boolean isPowered(final IGrid grid) {
        if (grid == null) {
            return false;
        }

        final IEnergyGrid eg = grid.getCache(IEnergyGrid.class);
        return eg.isNetworkPowered();
    }

    private static boolean checkPlayerPermissions(final IGrid grid, final int playerID) {
        if (grid == null) {
            return true;
        }

        final ISecurityGrid gs = grid.getCache(ISecurityGrid.class);

        if (gs == null) {
            return true;
        }

        if (!gs.isAvailable()) {
            return true;
        }

        return gs.hasPermission(playerID, SecurityPermissions.BUILD);
    }

    public static boolean canAccess(final AENetworkProxy gridProxy, final IActionSource src) {
        try {
            if (src.player().isPresent()) {
                return gridProxy.getSecurity().hasPermission(src.player().get(), SecurityPermissions.BUILD);
            } else if (src.machine().isPresent()) {
                final IActionHost te = src.machine().get();
                final IGridNode n = te.getActionableNode();
                if (n == null) {
                    return false;
                }

                final int playerID = n.getPlayerID();
                return gridProxy.getSecurity().hasPermission(playerID, SecurityPermissions.BUILD);
            } else {
                return false;
            }
        } catch (final GridAccessException gae) {
            return false;
        }
    }

    public static ItemStack extractItemsByRecipe(final IEnergySource energySrc, final IActionSource mySrc,
            final IMEMonitor<IAEItemStack> src, final World w, final IRecipe r, final ItemStack output,
            final InventoryCrafting ci, final ItemStack providedTemplate, final int slot,
            final IItemList<IAEItemStack> items, final Actionable realForFake,
            final IPartitionList<IAEItemStack> filter) {
        if (energySrc.extractAEPower(1, Actionable.SIMULATE, PowerMultiplier.CONFIG) > 0.9) {
            if (providedTemplate == null) {
                return ItemStack.EMPTY;
            }

            final AEItemStack ae_req = AEItemStack.fromItemStack(providedTemplate);
            ae_req.setStackSize(1);

            if (filter == null || filter.isListed(ae_req)) {
                final IAEItemStack ae_ext = src.extractItems(ae_req, realForFake, mySrc);
                if (ae_ext != null) {
                    final ItemStack extracted = ae_ext.createItemStack();
                    if (!extracted.isEmpty()) {
                        energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                        return extracted;
                    }
                }
            }

            final boolean checkFuzzy = ae_req.getOre().isPresent()
                    || providedTemplate.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || providedTemplate.hasTagCompound() || providedTemplate.isItemStackDamageable();

            if (items != null && checkFuzzy) {
                for (final IAEItemStack x : items) {
                    final ItemStack sh = x.getDefinition();
                    if ((Platform.itemComparisons().isEqualItemType(providedTemplate, sh) || ae_req.sameOre(x))
                            && !ItemStack.areItemsEqual(sh, output)) { // Platform.isSameItemType( sh, providedTemplate
                                                                       // )
                        final ItemStack cp = sh.copy();
                        cp.setCount(1);
                        ci.setInventorySlotContents(slot, cp);
                        if (r.matches(ci, w) && ItemStack.areItemsEqual(r.getCraftingResult(ci), output)) {
                            final IAEItemStack ax = x.copy();
                            ax.setStackSize(1);
                            if (filter == null || filter.isListed(ax)) {
                                final IAEItemStack ex = src.extractItems(ax, realForFake, mySrc);
                                if (ex != null) {
                                    energySrc.extractAEPower(1, realForFake, PowerMultiplier.CONFIG);
                                    return ex.createItemStack();
                                }
                            }
                        }
                        ci.setInventorySlotContents(slot, providedTemplate);
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // TODO wtf is this?
    public static ItemStack getContainerItem(final ItemStack stackInSlot) {
        if (stackInSlot == null) {
            return ItemStack.EMPTY;
        }

        final Item i = stackInSlot.getItem();
        if (i == null || !i.hasContainerItem(stackInSlot)) {
            if (stackInSlot.getCount() > 1) {
                stackInSlot.setCount(stackInSlot.getCount() - 1);
                return stackInSlot;
            }
            return ItemStack.EMPTY;
        }

        ItemStack ci = i.getContainerItem(stackInSlot.copy());
        if (!ci.isEmpty() && ci.isItemStackDamageable() && ci.getItemDamage() == ci.getMaxDamage()) {
            ci = ItemStack.EMPTY;
        }

        return ci;
    }

    public static boolean canRepair(final AEFeature type, final ItemStack a, final ItemStack b) {
        if (b.isEmpty() || a.isEmpty()) {
            return false;
        }

        if (type == AEFeature.CERTUS_QUARTZ_TOOLS) {
            final IItemDefinition certusQuartzCrystal = AEApi.instance().definitions().materials()
                    .certusQuartzCrystal();

            return certusQuartzCrystal.isSameAs(b);
        }

        if (type == AEFeature.NETHER_QUARTZ_TOOLS) {
            return Items.QUARTZ == b.getItem();
        }

        return false;
    }

    public static List<ItemStack> findPreferred(final ItemStack[] is) {
        final IParts parts = AEApi.instance().definitions().parts();

        for (final ItemStack stack : is) {
            if (parts.cableGlass().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableCovered().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableSmart().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }

            if (parts.cableDenseSmart().sameAs(AEColor.TRANSPARENT, stack)) {
                return Collections.singletonList(stack);
            }
        }

        return Lists.newArrayList(is);
    }

    // public static void addStat( final int playerID, final Achievement achievement )
    // {
    // final EntityPlayer p = AEApi.instance().registries().players().findPlayer( playerID );
    // if( p != null )
    // {
    // p.addStat( achievement, 1 );
    // }
    // }

    public static boolean isRecipePrioritized(final ItemStack what) {
        return isPurifiedCertus(what) ||
                isPurifiedFluix(what) ||
                isPurifiedNetherQuartz(what);
    }

    private static boolean isPurifiedCertus(ItemStack stack) {
        return AEApi.instance().definitions().materials()
                .purifiedCertusQuartzCrystal().isSameAs(stack);
    }

    private static boolean isPurifiedFluix(ItemStack stack) {
        return AEApi.instance().definitions().materials()
                .purifiedFluixCrystal().isSameAs(stack);
    }

    private static boolean isPurifiedNetherQuartz(ItemStack stack) {
        return AEApi.instance().definitions().materials()
                .purifiedNetherQuartzCrystal().isSameAs(stack);
    }

    // consider methods below moving to a compability class
    public static boolean isGTDamageableItem(Item item) {
        return ((GTLoaded) && ToolClass.getGTToolClass().isAssignableFrom(item.getClass()));
    }

    public static boolean isIC2DamageableItem(Item item) {
        return (isModLoaded("IC2") && item instanceof ICustomDamageItem);
    }

    public static String formatModName(String modId) {
        ModContainer modContainer = Loader.instance().getModList().stream()
                .filter(mod -> mod.getModId().equals(modId))
                .findFirst()
                .orElse(null);

        if (modContainer != null) {
            return TextFormatting.BLUE.toString() + TextFormatting.ITALIC + modContainer.getName();
        }
        return null;
    }

    /**
     * 向上整除（ceiling division），等价于 Math.ceil((double)a / b) 但使用整数运算。
     */
    public static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    /**
     * 比较两个泛型 IAEStack 是否完全相同（包括类型、内容和数量）。
     */
}

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

package appeng.parts.misc;

import java.util.EnumSet;
import java.util.List;

import com.google.common.collect.ImmutableSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.definitions.IMaterials;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartModel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.IConfigManager;
import appeng.core.AppEng;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.helpers.IPatternProviderHost;
import appeng.helpers.IPriorityHost;
import appeng.helpers.PatternProviderLogic;
import appeng.helpers.Reflected;
import appeng.items.misc.ItemEncodedPattern;
import appeng.items.parts.PartModels;
import appeng.parts.PartBasicState;
import appeng.parts.PartModel;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TilePatternProvider;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

/**
 * 样板供应器部件。
 *
 * 对应高版本 AE2 的 PatternProviderPart。
 * 仅负责样板存储和合成推送，不含 Config/Storage 功能。
 */
public class PartPatternProvider extends PartBasicState implements IGridTickable, IPatternProviderHost,
        IAEAppEngInventory, IPriorityHost {

    // --- 模型定义 ---
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(AppEng.MOD_ID,
            "part/pattern_provider_base");

    @PartModels
    public static final PartModel MODELS_OFF = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/pattern_provider_off"));

    @PartModels
    public static final PartModel MODELS_ON = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/pattern_provider_on"));

    @PartModels
    public static final PartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE,
            new ResourceLocation(AppEng.MOD_ID, "part/pattern_provider_has_channel"));

    private final PatternProviderLogic logic = new PatternProviderLogic(this.getProxy(), this);

    @Reflected
    public PartPatternProvider(final ItemStack is) {
        super(is);
    }

    // ========== 网络事件 ==========

    @Override
    @MENetworkEventSubscribe
    public void chanRender(final MENetworkChannelsChanged c) {
        this.logic.notifyNeighbors();
    }

    @Override
    @MENetworkEventSubscribe
    public void powerRender(final MENetworkPowerStatusChange c) {
        this.logic.notifyNeighbors();
    }

    // ========== 碰撞箱 ==========

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        bch.addBox(2, 2, 14, 14, 14, 16);
        bch.addBox(5, 5, 12, 11, 11, 14);
    }

    // ========== IUpgradeableHost ==========

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.logic.getInstalledUpgrades(u);
    }

    // ========== 网格 ==========

    @Override
    public void gridChanged() {
        this.logic.gridChanged();
    }

    // ========== NBT ==========

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.logic.readFromNBT(data);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.logic.writeToNBT(data);
    }

    // ========== 生命周期 ==========

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.logic.initialize();
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        this.logic.addDrops(drops);
    }

    // ========== 电缆 ==========

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 4;
    }

    // ========== IConfigManager ==========

    @Override
    public IConfigManager getConfigManager() {
        return this.logic.getConfigManager();
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        return this.logic.getInventoryByName(name);
    }

    // ========== GUI ==========

    @Override
    public boolean onPartActivate(final EntityPlayer p, final EnumHand hand, final Vec3d pos) {
        if (Platform.isServer()) {
            Platform.openGUI(p, this.getTileEntity(), this.getSide(), AEGuiKeys.PATTERN_PROVIDER);
        }
        return true;
    }

    // ========== IGridTickable ==========

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.logic.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.logic.tickingRequest(node, ticksSinceLastCall);
    }

    // ========== IAEAppEngInventory ==========

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        this.logic.onChangeInventory(inv, slot, mc, removedStack, newStack);
    }

    // ========== IPatternProviderHost ==========

    @Override
    public PatternProviderLogic getPatternProviderLogic() {
        return this.logic;
    }

    @Override
    public EnumSet<EnumFacing> getTargets() {
        return EnumSet.of(this.getSide().getFacing());
    }

    @Override
    public TileEntity getTileEntity() {
        return super.getHost().getTile();
    }

    // ========== ICraftingProvider ==========

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.logic.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.logic.isBusy();
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.logic.provideCrafting(craftingTracker);
    }

    // ========== ICraftingRequester ==========

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.logic.getRequestedJobs();
    }

    @Override
    public IAEItemStack injectCraftedItems(final ICraftingLink link, final IAEItemStack items, final Actionable mode) {
        return this.logic.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.logic.jobStateChange(link);
    }

    // ========== IPriorityHost ==========

    @Override
    public int getPriority() {
        return this.logic.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.logic.setPriority(newValue);
    }

    // ========== 模型 ==========

    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) {
            return MODELS_HAS_CHANNEL;
        } else if (this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_OFF;
        }
    }

    // ========== 邻居变化 ==========

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        TileEntity tileEntity = getTileEntity();
        if (tileEntity instanceof TilePatternProvider) {
            ((TilePatternProvider) tileEntity).updateRedstoneState();
        }
    }

    // ========== GUI 辅助 ==========

    @Override
    public ItemStack getItemStackRepresentation() {
        // TODO: 等注册完成后替换为正确的部件定义
        return AEApi.instance().definitions().parts().iface().maybeStack(1).orElse(ItemStack.EMPTY);
    }

    @Override
    public GuiBridge getGuiBridge() {
        return AEGuiKeys.PATTERN_PROVIDER.getLegacyBridge();
    }

    // ========== Memory Card ==========

    @Override
    public NBTTagCompound downloadSettings(SettingsFrom from, NBTTagCompound compound) {
        NBTTagCompound output = super.downloadSettings(from, compound);
        if (from == SettingsFrom.MEMORY_CARD) {
            final IItemHandler inv = this.getInventoryByName("patterns");
            if (inv instanceof AppEngInternalInventory) {
                ((AppEngInternalInventory) inv).writeToNBT(output, "patterns");
            }
        }
        return output;
    }

    @Override
    public void uploadSettings(SettingsFrom from, NBTTagCompound compound, EntityPlayer player) {
        super.uploadSettings(from, compound, player);
        final IItemHandler inv = this.getInventoryByName("patterns");
        if (inv instanceof AppEngInternalInventory target) {
            AppEngInternalInventory tmp = new AppEngInternalInventory(null, target.getSlots());
            tmp.readFromNBT(compound, "patterns");
            PlayerMainInvWrapper playerInv = new PlayerMainInvWrapper(player.inventory);
            final IMaterials materials = AEApi.instance().definitions().materials();
            int missingPatternsToEncode = 0;
            int amountPatternSlots = PatternProviderLogic.NUMBER_OF_PATTERN_SLOTS;

            for (int i = 0; i < inv.getSlots(); i++) {
                if (target.getStackInSlot(i).getItem() instanceof ItemEncodedPattern) {
                    ItemStack blank = materials.blankPattern().maybeStack(target.getStackInSlot(i).getCount()).get();
                    if (!player.addItemStackToInventory(blank)) {
                        player.dropItem(blank, true);
                    }
                    target.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            for (int x = 0; x < amountPatternSlots; x++) {
                if (!tmp.getStackInSlot(x).isEmpty()) {
                    boolean found = false;
                    for (int i = 0; i < playerInv.getSlots(); i++) {
                        if (materials.blankPattern().isSameAs(playerInv.getStackInSlot(i))) {
                            target.setStackInSlot(x, tmp.getStackInSlot(x));
                            playerInv.getStackInSlot(i).shrink(1);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        missingPatternsToEncode++;
                    }
                }
            }
            if (Platform.isServer() && missingPatternsToEncode > 0) {
                player.sendMessage(PlayerMessages.MissingPatternsToEncode.get());
            }
        }
    }
}

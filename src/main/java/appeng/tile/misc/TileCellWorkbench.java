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

package appeng.tile.misc;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import appeng.api.config.CopyMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IUpgradeableHost;
import appeng.api.storage.ICellWorkbenchItem;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.tile.AEBaseTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.ConfigManager;
import appeng.util.IConfigManagerHost;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;

public class TileCellWorkbench extends AEBaseTile
        implements IUpgradeableHost, IAEAppEngInventory, IConfigManagerHost, IIAEStackInventory {

    private final AppEngInternalInventory cell = new AppEngInternalInventory(this, 1);
    // 泛型 AE 栈库存，支持物品、流体等任意类型的过滤配置
    private final IAEStackInventory config = new IAEStackInventory(this, 63, StorageName.CONFIG);
    private final ConfigManager manager = new ConfigManager(this);

    private IItemHandler cacheUpgrades = null;
    private IAEStackInventory cacheConfig = null;
    private boolean locked = false;

    public TileCellWorkbench() {
        this.manager.registerSetting(Settings.COPY_MODE, CopyMode.CLEAR_ON_REMOVE);
        this.cell.setEnableClientEvents(true);
    }

    public IItemHandler getCellUpgradeInventory() {
        if (this.cacheUpgrades == null) {
            final ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            final ItemStack is = this.cell.getStackInSlot(0);
            if (is.isEmpty()) {
                return null;
            }

            final IItemHandler inv = cell.getUpgradesInventory(is);
            if (inv == null) {
                return null;
            }

            return this.cacheUpgrades = inv;
        }
        return this.cacheUpgrades;
    }

    public ICellWorkbenchItem getCell() {
        if (this.cell.getStackInSlot(0).isEmpty()) {
            return null;
        }

        if (this.cell.getStackInSlot(0).getItem() instanceof ICellWorkbenchItem) {
            return ((ICellWorkbenchItem) this.cell.getStackInSlot(0).getItem());
        }

        return null;
    }

    @Override
    public NBTTagCompound writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.cell.writeToNBT(data, "cell");
        this.config.writeToNBT(data, "config");
        this.manager.writeToNBT(data);
        return data;
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.cell.readFromNBT(data, "cell");
        this.config.readFromNBT(data, "config");
        this.manager.readFromNBT(data);
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("cell")) {
            return this.cell;
        }

        return null;
    }

    // ---- IIAEStackInventory 实现 ----

    @Override
    public void saveAEStackInv() {
        this.saveChanges();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) {
            return this.config;
        }
        return null;
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return 0;
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.cell && !this.locked) {
            this.locked = true;

            this.cacheUpgrades = null;
            this.cacheConfig = null;

            final IAEStackInventory configInventory = this.getCellConfigAEInventory();
            if (configInventory != null) {
                boolean cellHasConfig = false;
                for (int x = 0; x < configInventory.getSizeInventory(); x++) {
                    if (configInventory.getAEStackInSlot(x) != null) {
                        cellHasConfig = true;
                        break;
                    }
                }

                if (cellHasConfig) {
                    // 单元自带配置 → 复制到工作台
                    for (int x = 0; x < this.config.getSizeInventory(); x++) {
                        if (x < configInventory.getSizeInventory()) {
                            this.config.putAEStackInSlot(x, configInventory.getAEStackInSlot(x));
                        } else {
                            this.config.putAEStackInSlot(x, null);
                        }
                    }
                } else {
                    // 单元无配置 → 复制工作台到单元
                    copyAEInv(this.config, configInventory);
                }
            } else if (this.manager.getSetting(Settings.COPY_MODE) == CopyMode.CLEAR_ON_REMOVE) {
                for (int x = 0; x < this.config.getSizeInventory(); x++) {
                    this.config.putAEStackInSlot(x, null);
                }

                this.saveChanges();
            }

            this.locked = false;
        }
    }

    /**
     * 当 config IAEStackInventory 内容被外部修改（如虚拟槽位交互）时，
     * 将变更同步回单元物品的 config。
     */
    public void syncConfigToCell() {
        if (!this.locked) {
            this.locked = true;
            final IAEStackInventory c = this.getCellConfigAEInventory();
            if (c != null) {
                copyAEInv(this.config, c);
                // 回读：单元可能修改了某些槽位（如不接受的类型）
                copyAEInv(c, this.config);
            }
            this.locked = false;
        }
    }

    private IAEStackInventory getCellConfigAEInventory() {
        if (this.cacheConfig == null) {
            final ICellWorkbenchItem cell = this.getCell();
            if (cell == null) {
                return null;
            }

            final ItemStack is = this.cell.getStackInSlot(0);
            if (is.isEmpty()) {
                return null;
            }

            final IAEStackInventory inv = cell.getConfigAEInventory(is);
            if (inv == null) {
                return null;
            }

            this.cacheConfig = inv;
        }
        return this.cacheConfig;
    }

    /**
     * 将源 IAEStackInventory 的内容复制到目标 IAEStackInventory。
     */
    private static void copyAEInv(IAEStackInventory src, IAEStackInventory dst) {
        final int size = Math.min(src.getSizeInventory(), dst.getSizeInventory());
        for (int x = 0; x < size; x++) {
            final IAEStack<?> stack = src.getAEStackInSlot(x);
            dst.putAEStackInSlot(x, stack != null ? stack.copy() : null);
        }
    }

    @Override
    public void getDrops(final World w, final BlockPos pos, final List<ItemStack> drops) {
        super.getDrops(w, pos, drops);

        if (this.cell.getStackInSlot(0) != null) {
            drops.add(this.cell.getStackInSlot(0));
        }
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.manager;
    }

    @Override
    public void updateSetting(final IConfigManager manager, final Enum<?> settingName, final Enum<?> newValue) {
        // nothing here..
    }
}

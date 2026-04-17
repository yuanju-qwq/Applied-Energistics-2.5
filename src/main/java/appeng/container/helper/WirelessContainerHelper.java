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

package appeng.container.helper;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import baubles.api.BaublesApi;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.container.AEBaseContainer;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.automation.StackUpgradeInventory;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;

/**
 * 无线终端容器的公共逻辑委托类。
 * 提取了所有无线终端 Container 中重复的以下逻辑：
 * <ul>
 *   <li>构造函数中的槽位锁定</li>
 *   <li>detectAndSendChanges() 中的物品验证、电力消耗、范围检查</li>
 *   <li>slotClick() 中的磁铁右键切换</li>
 *   <li>升级槽位的初始化 (setupUpgrades)</li>
 *   <li>NBT 读写 (saveChanges / loadFromNBT)</li>
 *   <li>IInventorySlotAware 的委托实现</li>
 * </ul>
 *
 * 使用方式：在各无线终端 Container 中创建此类的实例，将重复逻辑委托给它。
 */
public class WirelessContainerHelper {

    private final WirelessTerminalGuiObject guiObject;
    private final int slot;
    private double powerMultiplier = 0.5;
    private int ticks = 0;

    private AppEngInternalInventory upgrades;
    private SlotRestrictedInput magnetSlot;

    public WirelessContainerHelper(WirelessTerminalGuiObject guiObject, InventoryPlayer ip,
            AEBaseContainer container) {
        this.guiObject = guiObject;

        if (guiObject != null) {
            final int slotIndex = ((IInventorySlotAware) guiObject).getInventorySlot();
            if (!((IInventorySlotAware) guiObject).isBaubleSlot()) {
                container.lockPlayerInventorySlot(slotIndex);
            }
            this.slot = slotIndex;
        } else {
            this.slot = -1;
            container.lockPlayerInventorySlot(ip.currentItem);
        }
    }

    // ========== detectAndSendChanges 中的无线终端管理逻辑 ==========

    /**
     * 执行无线终端的物品验证、电力消耗和范围检查。
     * 应在各 Container 的 detectAndSendChanges() 中调用（仅服务端）。
     *
     * @param container 所属的容器
     * @return true 表示容器仍然有效，false 表示容器已失效
     */
    public boolean tickWirelessStatus(AEBaseContainer container) {
        if (!Platform.isServer()) {
            return true;
        }

        // ---- 物品验证 ----
        final ItemStack currentItem;
        if (guiObject.isBaubleSlot()) {
            currentItem = BaublesApi.getBaublesHandler(container.getPlayerInv().player).getStackInSlot(this.slot);
        } else {
            currentItem = this.slot < 0 ? container.getPlayerInv().getCurrentItem()
                    : container.getPlayerInv().getStackInSlot(this.slot);
        }

        if (currentItem.isEmpty()) {
            container.setValidContainer(false);
            return false;
        } else if (!this.guiObject.getItemStack().isEmpty()
                && currentItem != this.guiObject.getItemStack()) {
            if (ItemStack.areItemsEqual(this.guiObject.getItemStack(), currentItem)) {
                if (guiObject.isBaubleSlot()) {
                    BaublesApi.getBaublesHandler(container.getPlayerInv().player).setStackInSlot(this.slot,
                            this.guiObject.getItemStack());
                } else {
                    container.getPlayerInv().setInventorySlotContents(this.slot,
                            this.guiObject.getItemStack());
                }
            } else {
                container.setValidContainer(false);
                return false;
            }
        }

        // ---- 电力消耗 ----
        this.ticks++;
        if (this.ticks > 10) {
            double ext = this.guiObject.extractAEPower(this.powerMultiplier * this.ticks,
                    Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (ext < this.powerMultiplier * this.ticks) {
                if (container.isValidContainer()) {
                    container.getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                }
                container.setValidContainer(false);
                return false;
            }
            this.ticks = 0;
        }

        // ---- 范围检查 ----
        if (!this.guiObject.rangeCheck()) {
            if (container.isValidContainer()) {
                container.getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get());
            }
            container.setValidContainer(false);
            return false;
        } else {
            this.powerMultiplier = AEConfig.instance().wireless_getDrainRate(this.guiObject.getRange());
        }

        return true;
    }

    // ========== 磁铁右键切换逻辑 ==========

    /**
     * 处理磁铁卡右键切换启用/禁用。
     * 应在各 Container 的 slotClick() 中调用。
     *
     * @param slotId     被点击的槽位ID
     * @param dragType   拖拽类型
     * @param clickType  点击类型
     * @param container  所属的容器
     * @return 如果拦截了点击事件返回 ItemStack.EMPTY，否则返回 null 表示应交给 super 处理
     */
    public ItemStack handleMagnetSlotClick(int slotId, int dragType, ClickType clickType,
            AEBaseContainer container) {
        if (magnetSlot == null) {
            return null;
        }
        if (slotId >= 0 && slotId < container.inventorySlots.size()) {
            if (clickType == ClickType.PICKUP && dragType == 1) {
                if (container.inventorySlots.get(slotId) == magnetSlot) {
                    ItemStack itemStack = magnetSlot.getStack();
                    if (!itemStack.isEmpty()) {
                        NBTTagCompound tag = itemStack.getTagCompound();
                        if (tag == null) {
                            tag = new NBTTagCompound();
                        }
                        if (tag.hasKey("enabled")) {
                            boolean e = tag.getBoolean("enabled");
                            tag.setBoolean("enabled", !e);
                        } else {
                            tag.setBoolean("enabled", false);
                        }
                        magnetSlot.getStack().setTagCompound(tag);
                        magnetSlot.onSlotChanged();
                        return ItemStack.EMPTY;
                    }
                }
            }
        }
        return null;
    }

    // ========== 升级槽位管理 ==========

    /**
     * 初始化升级物品栏。应在 Container 构造函数中调用。
     *
     * @param invHost 物品栏变更监听宿主
     */
    public void initUpgrades(IAEAppEngInventory invHost) {
        this.upgrades = new StackUpgradeInventory(guiObject.getItemStack(), invHost, 2);
    }

    /**
     * 创建磁铁升级槽位。调用方应通过 addSlotToContainer() 将返回的槽位添加到容器中。
     *
     * @param ip 玩家物品栏
     * @param x  升级槽位的 x 坐标
     * @param y  升级槽位的 y 坐标
     * @return 创建好的磁铁升级槽位，如果 guiObject 或 upgrades 为 null 则返回 null
     */
    public SlotRestrictedInput createMagnetSlot(InventoryPlayer ip, int x, int y) {
        if (guiObject != null && upgrades != null) {
            this.magnetSlot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES,
                    upgrades, 0, x, y, ip);
            this.magnetSlot.setNotDraggable();
            return this.magnetSlot;
        }
        return null;
    }

    // ========== NBT 读写 ==========

    /**
     * 从物品 NBT 中加载升级数据。
     */
    public void loadUpgradesFromNBT() {
        NBTTagCompound data = guiObject.getItemStack().getTagCompound();
        if (data != null && upgrades != null) {
            upgrades.readFromNBT(data.getCompoundTag("upgrades"));
        }
    }

    /**
     * 将升级数据保存到物品 NBT 中。
     * 返回写入了升级数据的 tag，调用方可继续追加其他数据后调用
     * {@link WirelessTerminalGuiObject#saveChanges(NBTTagCompound)}。
     *
     * @return 包含升级数据的 NBTTagCompound
     */
    public NBTTagCompound saveUpgradesToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        if (upgrades != null) {
            upgrades.writeToNBT(tag, "upgrades");
        }
        return tag;
    }

    /**
     * 执行完整的保存流程：保存升级数据并写入 guiObject。
     * 适用于只需保存升级数据的简单 Container。
     */
    public void saveChanges() {
        if (Platform.isServer()) {
            NBTTagCompound tag = saveUpgradesToNBT();
            guiObject.saveChanges(tag);
        }
    }

    // ========== Getter / Setter ==========

    public WirelessTerminalGuiObject getGuiObject() {
        return guiObject;
    }

    public int getSlot() {
        return slot;
    }

    public double getPowerMultiplier() {
        return powerMultiplier;
    }

    public void setPowerMultiplier(double powerMultiplier) {
        this.powerMultiplier = powerMultiplier;
    }

    public AppEngInternalInventory getUpgrades() {
        return upgrades;
    }

    public SlotRestrictedInput getMagnetSlot() {
        return magnetSlot;
    }

    public int getInventorySlot() {
        return guiObject.getInventorySlot();
    }

    public boolean isBaubleSlot() {
        return guiObject.isBaubleSlot();
    }
}

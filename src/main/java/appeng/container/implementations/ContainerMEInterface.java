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

package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.config.*;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.*;
import appeng.fluids.util.IAEFluidTank;
import appeng.helpers.IInterfaceLogicHost;
import appeng.helpers.InterfaceLogic;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

/**
 * ME 接口 Container。
 *
 * Config 槽位使用 {@link IAEStackInventory}（泛型虚拟槽位，每槽可标记物品或流体），
 * 通过 {@link appeng.core.sync.packets.PacketVirtualSlot} 同步。
 *
 * <h3>槽位布局（Config 和 Storage 上下排列）</h3>
 * <pre>
 * 行0: [Config×9]  ← VirtualMEPhantomSlot（GUI 层，泛型 IAEStack）
 * 行1: [Storage×9] ← 物品用 SlotOversized / 流体用 VirtualMESlot（GUI 层根据 Config 类型动态切换）
 * 行2: [Config×9]  ← VirtualMEPhantomSlot
 * 行3: [Storage×9] ← 物品用 SlotOversized / 流体用 VirtualMESlot
 * </pre>
 *
 * Container 层注册物品 Storage 的 {@link SlotOversized}（18个）。
 * 流体 Storage 通过 {@link StorageName#STORAGE} VirtualSlot 同步到客户端 {@link #fluidStorageClientInv}。
 */
public class ContainerMEInterface extends ContainerUpgradeable
        implements IVirtualSlotHolder, IVirtualSlotSource {

    private final InterfaceLogic logic;

    // --- Config 行和 Storage 行的 Y 坐标 ---
    public static final int CONFIG_ROW_0_Y = 30;
    public static final int STORAGE_ROW_0_Y = CONFIG_ROW_0_Y + 18;
    public static final int CONFIG_ROW_1_Y = STORAGE_ROW_0_Y + 22;
    public static final int STORAGE_ROW_1_Y = CONFIG_ROW_1_Y + 18;
    public static final int SLOT_X_OFFSET = 8;

    @GuiSync(3)
    public YesNo iTermMode = YesNo.YES;

    // --- Config VirtualSlot sync (server → client) ---
    private final IAEStack<?>[] configClientSlot = new IAEStack[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];

    // --- Fluid Storage VirtualSlot sync (server → client) ---
    // Server-side mirror: populated from fluidTanks each tick for diff comparison
    private final IAEStackInventory fluidStorageServerMirror =
            new IAEStackInventory(null, InterfaceLogic.NUMBER_OF_CONFIG_SLOTS, StorageName.STORAGE);
    // Server-side diff snapshot
    private final IAEStack<?>[] fluidStorageClientSlot = new IAEStack[InterfaceLogic.NUMBER_OF_CONFIG_SLOTS];
    // Client-side inventory: GUI reads from this
    private final IAEStackInventory fluidStorageClientInv =
            new IAEStackInventory(null, InterfaceLogic.NUMBER_OF_CONFIG_SLOTS, StorageName.STORAGE);

    public ContainerMEInterface(final InventoryPlayer ip, final IInterfaceLogicHost host) {
        super(ip, host);

        this.logic = host.getInterfaceLogic();

        // 物品 Storage 槽位：18 个 SlotOversized
        // 行1（紧跟 Config 行0 下方）
        for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new SlotOversized(this.logic.getItemStorage(),
                    x, SLOT_X_OFFSET + 18 * x, STORAGE_ROW_0_Y));
        }
        // 行3（紧跟 Config 行2 下方）
        for (int x = 0; x < 9; x++) {
            this.addSlotToContainer(new SlotOversized(this.logic.getItemStorage(),
                    9 + x, SLOT_X_OFFSET + 18 * x, STORAGE_ROW_1_Y));
        }
    }

    @Override
    protected int getHeight() {
        return 222;
    }

    @Override
    protected void setupConfig() {
        // Config 槽位由 GUI 层的 VirtualMEPhantomSlot 处理，不在 Container 中注册 Slot。
        // 这里只添加升级槽位。
        this.setupUpgrades();
    }

    @Override
    protected boolean supportCapacity() {
        return false;
    }

    @Override
    public int availableUpgrades() {
        return 3;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            final IConfigManager cm = this.logic.getConfigManager();
            this.setInterfaceTerminalMode((YesNo) cm.getSetting(Settings.INTERFACE_TERMINAL));

            // Sync config via VirtualSlot
            final IAEStackInventory config = this.logic.getConfig();
            this.updateVirtualSlots(StorageName.CONFIG, config, this.configClientSlot);

            // Sync fluid storage via VirtualSlot
            this.syncFluidStorageToMirror();
            this.updateVirtualSlots(StorageName.STORAGE, this.fluidStorageServerMirror, this.fluidStorageClientSlot);
        }

        this.standardDetectAndSendChanges();
    }

    /**
     * Copies fluid tank contents into the server-side mirror IAEStackInventory
     * so that updateVirtualSlots can perform diff-based sync.
     */
    private void syncFluidStorageToMirror() {
        final IAEFluidTank tanks = this.logic.getFluidTanks();
        for (int i = 0; i < InterfaceLogic.NUMBER_OF_CONFIG_SLOTS; i++) {
            final IAEFluidStack fs = tanks.getFluidInSlot(i);
            this.fluidStorageServerMirror.putAEStackInSlot(i, fs);
        }
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        this.setInterfaceTerminalMode((YesNo) cm.getSetting(Settings.INTERFACE_TERMINAL));
    }

    // ---- IVirtualSlotHolder (client receives server sync) ----

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        if (invName == StorageName.CONFIG) {
            final IAEStackInventory config = this.logic.getConfig();
            for (var entry : slotStacks.int2ObjectEntrySet()) {
                config.putAEStackInSlot(entry.getIntKey(), entry.getValue());
            }
        } else if (invName == StorageName.STORAGE) {
            for (var entry : slotStacks.int2ObjectEntrySet()) {
                this.fluidStorageClientInv.putAEStackInSlot(entry.getIntKey(), entry.getValue());
            }
        }
    }

    // ---- IVirtualSlotSource (server receives client update) ----

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        // Only config slots are client-writable
        if (invName == StorageName.CONFIG) {
            final IAEStackInventory config = this.logic.getConfig();
            if (config != null && slotId >= 0 && slotId < config.getSizeInventory()) {
                config.putAEStackInSlot(slotId, aes);
            }
        }
        // STORAGE is read-only from client, ignore
    }

    // ---- Getter ----

    public InterfaceLogic getLogic() {
        return this.logic;
    }

    public IAEStackInventory getConfig() {
        return this.logic.getConfig();
    }

    /**
     * Returns the client-side fluid storage inventory, synced from server via VirtualSlot.
     * GUI panels should read from this for fluid storage display.
     */
    public IAEStackInventory getFluidStorageClientInv() {
        return this.fluidStorageClientInv;
    }

    public YesNo getInterfaceTerminalMode() {
        return this.iTermMode;
    }

    private void setInterfaceTerminalMode(final YesNo iTermMode) {
        this.iTermMode = iTermMode;
    }
}

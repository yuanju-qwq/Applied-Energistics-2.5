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
import appeng.api.util.IConfigManager;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.*;
import appeng.helpers.IPatternProviderHost;
import appeng.helpers.PatternProviderLogic;
import appeng.util.Platform;

/**
 * 样板供应器 Container。
 *
 * 管理样板槽位（36个，分4行，可通过 PATTERN_EXPANSION 升级扩展）、
 * 升级槽位、以及服务端→客户端的设置同步。
 */
public class ContainerPatternProvider extends ContainerUpgradeable implements IOptionalSlotHost {

    private final PatternProviderLogic logic;

    @GuiSync(3)
    public YesNo bMode = YesNo.NO;

    @GuiSync(4)
    public LockCraftingMode lMode = LockCraftingMode.NONE;

    @GuiSync(7)
    public int patternExpansions = 0;

    @GuiSync(8)
    public YesNo iTermMode = YesNo.YES;

    @GuiSync(9)
    public LockCraftingMode lockReason = LockCraftingMode.NONE;

    public ContainerPatternProvider(final InventoryPlayer ip, final IPatternProviderHost host) {
        super(ip, host);

        this.logic = host.getPatternProviderLogic();

        // 样板槽位：4 行 × 9 列 = 36 槽，前1行始终可见，后3行通过 PATTERN_EXPANSION 升级解锁
        for (int row = 0; row < 4; ++row) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(new OptionalSlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, this.logic
                                .getPatterns(),
                        this, x + row * 9, 8 + 18 * x, 36 + (18 * row), row, this.getInventoryPlayer())
                        .setStackLimit(1));
            }
        }
    }

    @Override
    protected int getHeight() {
        return 204;
    }

    @Override
    protected void setupConfig() {
        this.setupUpgrades();
    }

    @Override
    public int availableUpgrades() {
        return 4;
    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        return logic.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION) >= idx;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (patternExpansions != getPatternUpgrades()) {
            patternExpansions = getPatternUpgrades();
            this.logic.dropExcessPatterns();
        }

        if (Platform.isServer()) {
            lockReason = logic.getCraftingLockedReason();
        }
        super.detectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if (Platform.isClient() && field.equals("patternExpansions")) {
            this.logic.dropExcessPatterns();
        }
    }

    @Override
    protected void loadSettingsFromHost(final IConfigManager cm) {
        this.setBlockingMode((YesNo) cm.getSetting(Settings.BLOCK));
        this.setUnlockMode((LockCraftingMode) cm.getSetting(Settings.UNLOCK));
        this.setInterfaceTerminalMode((YesNo) cm.getSetting(Settings.INTERFACE_TERMINAL));
    }

    public LockCraftingMode getUnlockMode() {
        return this.lMode;
    }

    public void setUnlockMode(final LockCraftingMode mode) {
        this.lMode = mode;
    }

    public YesNo getBlockingMode() {
        return this.bMode;
    }

    private void setBlockingMode(final YesNo bMode) {
        this.bMode = bMode;
    }

    public YesNo getInterfaceTerminalMode() {
        return this.iTermMode;
    }

    private void setInterfaceTerminalMode(final YesNo iTermMode) {
        this.iTermMode = iTermMode;
    }

    public int getPatternUpgrades() {
        return this.logic.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION);
    }

    public LockCraftingMode getCraftingLockedReason() {
        return lockReason;
    }

    public PatternProviderLogic getLogic() {
        return this.logic;
    }
}

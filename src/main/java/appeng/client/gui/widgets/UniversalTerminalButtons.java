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

package appeng.client.gui.widgets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.items.tools.powered.ToolWirelessUniversalTerminal;
import appeng.items.tools.powered.WirelessTerminalMode;

/**
 * 通用无线终端的 GUI 模式切换按钮管理器。
 * 用于在各无线终端 GUI 中添加终端切换按钮。
 * 当玩家持有通用终端时，会在 GUI 左侧显示其他已安装模式的切换按钮。
 */
public class UniversalTerminalButtons {

    private final List<ModeButton> modeButtons = new ArrayList<>();
    private final ItemStack terminalStack;
    private boolean isUniversalTerminal;

    /**
     * @param ip 玩家物品栏
     */
    public UniversalTerminalButtons(InventoryPlayer ip) {
        this.terminalStack = findUniversalTerminal(ip);
        this.isUniversalTerminal = !this.terminalStack.isEmpty();
    }

    /**
     * 在 GUI 的 initGui() 中调用。创建并添加模式切换按钮。
     *
     * @param guiLeft     GUI 左边界 x
     * @param guiTop      GUI 上边界 y
     * @param buttonList  GUI 的按钮列表
     * @param nextButtonId 下一个可用的按钮 ID
     * @param itemRender  物品渲染器
     * @return 使用的按钮数量（用于后续 ID 分配）
     */
    public int initButtons(int guiLeft, int guiTop, List<GuiButton> buttonList, int nextButtonId,
            RenderItem itemRender) {
        this.modeButtons.clear();
        if (!isUniversalTerminal) {
            return 0;
        }

        WirelessTerminalMode currentMode = ToolWirelessUniversalTerminal.getMode(terminalStack);
        int[] installedModes = ToolWirelessUniversalTerminal.getInstalledModes(terminalStack);
        int count = 0;

        for (int modeId : installedModes) {
            WirelessTerminalMode mode = WirelessTerminalMode.fromId((byte) modeId);
            if (mode == currentMode) {
                continue;
            }

            ItemStack iconStack = getIconForMode(mode);
            String tooltip = mode.getName();

            GuiTabButton btn = new GuiTabButton(guiLeft - 22, guiTop + 4 + count * 24, iconStack, tooltip,
                    itemRender);
            btn.id = nextButtonId + count;

            ModeButton modeButton = new ModeButton(btn, mode);
            this.modeButtons.add(modeButton);
            buttonList.add(btn);
            count++;
        }

        return count;
    }

    /**
     * 处理按钮点击事件。如果是模式切换按钮，发送网络包并返回 true。
     *
     * @param button 被点击的按钮
     * @return true 如果已处理，false 如果不是模式切换按钮
     */
    public boolean handleButtonClick(GuiButton button) {
        for (ModeButton mb : modeButtons) {
            if (mb.button == button) {
                try {
                    NetworkHandler.instance().sendToServer(
                            new PacketValueConfig("UniversalTerminal.SwitchMode",
                                    String.valueOf(mb.mode.getId())));
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 查看是否是通用终端。
     */
    public boolean isUniversalTerminal() {
        return isUniversalTerminal;
    }

    /**
     * 获取指定模式的图标物品。
     */
    private ItemStack getIconForMode(WirelessTerminalMode mode) {
        switch (mode) {
            case TERMINAL:
                return AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            case CRAFTING:
                return AEApi.instance().definitions().items().wirelessCraftingTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case FLUID:
                return AEApi.instance().definitions().items().wirelessFluidTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case PATTERN:
                return AEApi.instance().definitions().items().wirelessPatternTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case INTERFACE:
                return AEApi.instance().definitions().items().wirelessInterfaceTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            case DUAL_INTERFACE:
                return AEApi.instance().definitions().items().wirelessDualInterfaceTerminal().maybeStack(1)
                        .orElse(ItemStack.EMPTY);
            default:
                return ItemStack.EMPTY;
        }
    }

    /**
     * 在玩家物品栏中查找通用终端物品。
     */
    private static ItemStack findUniversalTerminal(InventoryPlayer ip) {
        ItemStack mainHand = ip.player.getHeldItemMainhand();
        if (mainHand.getItem() instanceof ToolWirelessUniversalTerminal) {
            return mainHand;
        }
        ItemStack offHand = ip.player.getHeldItemOffhand();
        if (offHand.getItem() instanceof ToolWirelessUniversalTerminal) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 模式按钮的内部数据结构。
     */
    private static class ModeButton {
        final GuiTabButton button;
        final WirelessTerminalMode mode;

        ModeButton(GuiTabButton button, WirelessTerminalMode mode) {
            this.button = button;
            this.mode = mode;
        }
    }
}

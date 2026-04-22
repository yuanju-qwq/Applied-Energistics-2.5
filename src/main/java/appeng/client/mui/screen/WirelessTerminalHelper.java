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

package appeng.client.mui.screen;

import java.util.List;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.widgets.UniversalTerminalButtons;
import appeng.core.AppEng;

/**
 * 无线终端 GUI 共通功能帮助类。
 * <p>
 * 封装所有无线终端 MUI 面板共有的：
 * <ul>
 *   <li>无线升级图标绘制（右上角 32×32 的 wirelessupgrades.png）</li>
 *   <li>{@link UniversalTerminalButtons} 终端模式切换按钮管理</li>
 * </ul>
 * <p>
 * 由各 {@link MUIWirelessTermPanel} 实现类组合使用（组合优于继承）。
 */
final class WirelessTerminalHelper {

    private static final ResourceLocation WIRELESS_ICON_TEX =
            new ResourceLocation(AppEng.MOD_ID, "textures/guis/wirelessupgrades.png");

    private UniversalTerminalButtons universalButtons;

    WirelessTerminalHelper() {
    }

    /**
     * 在 initGui() 中调用，初始化终端模式切换按钮。
     */
    void initButtons(InventoryPlayer ip, int guiLeft, int guiTop,
            List<GuiButton> buttonList, int nextButtonId, RenderItem itemRender) {
        this.universalButtons = new UniversalTerminalButtons(ip);
        this.universalButtons.initButtons(guiLeft, guiTop, buttonList, nextButtonId, itemRender);
    }

    /**
     * 在 actionPerformed() 中调用，优先处理终端切换按钮。
     *
     * @return true 如果按钮已被处理
     */
    boolean handleButtonClick(GuiButton btn) {
        return this.universalButtons != null && this.universalButtons.handleButtonClick(btn);
    }

    /**
     * 在 drawBG() 中调用，绘制无线升级图标。
     *
     * @param offsetX GUI 绝对 X
     * @param offsetY GUI 绝对 Y
     * @param iconX   图标相对于 offsetX 的偏移（物品终端默认 198，流体终端默认 175）
     * @param iconY   图标相对于 offsetY 的偏移（默认 127，流体终端为 131）
     */
    void drawWirelessIcon(int offsetX, int offsetY, int iconX, int iconY) {
        net.minecraft.client.Minecraft.getMinecraft().getTextureManager().bindTexture(WIRELESS_ICON_TEX);
        Gui.drawModalRectWithCustomSizedTexture(offsetX + iconX, offsetY + iconY, 0, 0, 32, 32, 32, 32);
    }
}

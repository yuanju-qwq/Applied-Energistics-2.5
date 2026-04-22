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

package appeng.client.gui.implementations;

// ========================================================================
// [MUI Migration] 此旧 GUI 类已被 MUI 面板完全替代，运行时不再被实例化。
// 全部 GUI 创建已通过 AEMUIRegistration 中注册的 MUI 工厂完成。
// 如需恢复，取消下方块注释即可。
// ========================================================================
/*


import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.ContainerPatternValueName;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketPatternNameSet;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.helpers.Reflected;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;

/**
 * 样板名称设置 GUI：Ctrl+中键点击样板槽位后打开，允许用户自定义物品名称。
 * 参照 {@link GuiCraftAmount} 和 {@link GuiPatternValueAmount} 的结构。
 */
public class GuiPatternValueName extends AEBaseGui {

    private GuiTextField nameBox;
    private GuiTabButton originalGuiBtn;
    private GuiButton submit;
    private GuiButton clearName;
    private GuiBridge originalGui;

    @Reflected
    public GuiPatternValueName(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerPatternValueName(inventoryPlayer, te));
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(
                this.submit = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20,
                        GuiText.SetAmount.getLocal()));
        this.buttonList.add(
                this.clearName = new GuiButton(1, this.guiLeft + 20, this.guiTop + 26, 100, 20,
                        GuiText.Cancel.getLocal()));

        // 检测原始 GUI 类型
        ItemStack myIcon = ItemStack.EMPTY;
        final Object target = ((AEBaseContainer) this.inventorySlots).getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = (GuiBridge) AEApi.instance().registries().wireless()
                    .getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon);
        }

        if (target instanceof PartPatternTerminal) {
            myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }

        if (this.originalGui != null && !myIcon.isEmpty()) {
            this.buttonList.add(this.originalGuiBtn = new GuiTabButton(this.guiLeft + 154, this.guiTop, myIcon,
                    myIcon.getDisplayName(), this.itemRender));
        }

        this.nameBox = new GuiTextField(0, this.fontRenderer, this.guiLeft + 62, this.guiTop + 57, 59,
                this.fontRenderer.FONT_HEIGHT);
        this.nameBox.setEnableBackgroundDrawing(false);
        this.nameBox.setMaxStringLength(32);
        this.nameBox.setTextColor(0xFFFFFF);
        this.nameBox.setVisible(true);
        this.nameBox.setFocused(true);

        // 从 Container 的展示槽获取当前名称作为默认值
        final ContainerPatternValueName cpn = (ContainerPatternValueName) this.inventorySlots;
        if (cpn.getPatternValue().getHasStack()) {
            final ItemStack stack = cpn.getPatternValue().getStack();
            if (stack.hasDisplayName()) {
                this.nameBox.setText(stack.getDisplayName());
                this.nameBox.setSelectionPos(0);
            }
        }
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(GuiText.PatternRename.getLocal(), 8, 6, 4210752);
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/craft_amt.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.nameBox.drawTextBox();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.submit);
            }
            if (!this.nameBox.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }

        if (btn == this.clearName) {
            this.nameBox.setText("");
        }

        if (btn == this.submit && btn.enabled) {
            final ContainerPatternValueName cpn = (ContainerPatternValueName) this.inventorySlots;
            NetworkHandler.instance().sendToServer(
                    new PacketPatternNameSet(this.originalGui, this.nameBox.getText(), cpn.getValueIndex()));
        }
    }
}

*/
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

package appeng.client.mui.screen;

import java.io.IOException;
import java.util.regex.Pattern;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.implementations.ContainerOreDictStorageBus;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.item.OreDictFilterMatcher;

/**
 * MUI 版矿辞存储总线 GUI 面板。
 *
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiOreDictStorageBus}。
 * 继承 {@link MUIUpgradeablePanel}，包含矿辞正则表达式输入框、
 * 优先级/分区/读写模式/存储过滤按钮。
 */
public class MUIOreDictStorageBusPanel extends MUIUpgradeablePanel {

    private final ContainerOreDictStorageBus container;
    private static final Pattern ORE_DICTIONARY_FILTER = Pattern.compile("[0-9a-zA-Z* &|^!()]*");

    // ========== 控件 ==========
    private GuiTabButton priority;
    private GuiImgButton partition;
    private GuiImgButton storageFilter;
    private GuiImgButton rwMode;
    private MEGuiTextField searchFieldInputs;

    public MUIOreDictStorageBusPanel(final ContainerOreDictStorageBus container) {
        super(container);
        this.container = container;
        this.ySize = 170;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        super.initGui();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        this.searchFieldInputs = new MEGuiTextField(this.fontRenderer, this.guiLeft + 3, this.guiTop + 22, 170, 12);
        this.searchFieldInputs.setEnableBackgroundDrawing(false);
        this.searchFieldInputs.setMaxStringLength(512);
        this.searchFieldInputs.setTextColor(0xFFFFFF);
        this.searchFieldInputs.setVisible(true);
        this.searchFieldInputs.setFocused(false);
        this.searchFieldInputs.setValidator(str -> ORE_DICTIONARY_FILTER.matcher(str).matches());

        this.buttonList.add(this.priority = new GuiTabButton(this.guiLeft + 154, this.guiTop, 2 + 4 * 16,
                GuiText.Priority.getLocal(), this.itemRender));
        this.buttonList.add(this.partition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS,
                ActionItems.WRENCH));
        this.buttonList.add(this.rwMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + 48, Settings.ACCESS,
                AccessRestriction.READ_WRITE));
        this.buttonList.add(this.storageFilter = new GuiImgButton(this.guiLeft - 18, this.guiTop + 68,
                Settings.STORAGE_FILTER, StorageFilter.EXTRACTABLE_ONLY));

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("OreDictStorageBus.getRegex", "1"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void fillRegex(String regex) {
        this.searchFieldInputs.setText(regex);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.OreDictStorageBus.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(
                this.searchFieldInputs.getText().length() + " / " + this.searchFieldInputs.getMaxStringLength(), 120,
                36, 4210752);
        this.fontRenderer.drawString("& = AND    " + "| = OR", 8, 36, 4210752);
        this.fontRenderer.drawString("^ = XOR    " + "! = NOT", 8, 48, 4210752);
        this.fontRenderer.drawString("() for priority    " + "* for wildcard", 8, 60, 4210752);
        this.fontRenderer.drawString("Ex.: *Redstone*&!dustRedstone", 8, 72, 4210752);

        if (this.storageFilter != null) {
            this.storageFilter.set(container.getStorageFilter());
        }

        if (this.rwMode != null) {
            this.rwMode.set(container.getReadWriteMode());
        }
    }

    @Override
    protected String getBackground() {
        return "guis/oredictstoragebus.png";
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);
        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.drawTextBox();
        }
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        try {
            if (btn == this.priority) {
                NetworkHandler.instance().sendToServer(new PacketSwitchGuis(AEGuiKeys.PRIORITY));
            } else if (btn == this.partition) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("StorageBus.Action", "Partition"));
            } else if (btn == this.rwMode) {
                NetworkHandler.instance().sendToServer(new PacketConfigButton(this.rwMode.getSetting(), backwards));
            } else if (btn == this.storageFilter) {
                NetworkHandler.instance()
                        .sendToServer(new PacketConfigButton(this.storageFilter.getSetting(), backwards));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    // ========== 输入事件 ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        boolean wasFocused = this.searchFieldInputs.isFocused();
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchFieldInputs.isMouseIn(xCoord, yCoord)) {
            this.searchFieldInputs.setText("");
        }

        if (!searchFieldInputs.isFocused() && wasFocused) {
            searchFieldInputs.setText(OreDictFilterMatcher.validateExp(searchFieldInputs.getText()));
            NetworkHandler.instance()
                    .sendToServer(new PacketValueConfig("OreDictStorageBus.save", searchFieldInputs.getText()));
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                searchFieldInputs.setText(OreDictFilterMatcher.validateExp(searchFieldInputs.getText()));
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("OreDictStorageBus.save", searchFieldInputs.getText()));
            }
            if (!this.searchFieldInputs.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }
}

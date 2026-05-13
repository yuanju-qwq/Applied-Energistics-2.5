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
import appeng.client.mui.AEMUITheme;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.implementations.ContainerOreDictStorageBus;
import appeng.container.interfaces.IOreDictStorageBusGuiCallback;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.item.OreDictFilterMatcher;

/**
 * MUI 版矿辞存储总线 GUI 面板�? *
 * 继承 {@link MUIUpgradeablePanel}，包含矿辞正则表达式输入框�? * 优先�?分区/读写模式/存储过滤按钮�? */
public class MUIOreDictStorageBusPanel extends MUIUpgradeablePanel implements IOreDictStorageBusGuiCallback {

    private static final int SEARCH_FIELD_X = 3;
    private static final int SEARCH_FIELD_Y = 22;
    private static final int SEARCH_FIELD_WIDTH = 170;
    private static final int SEARCH_FIELD_HEIGHT = 12;

    private final ContainerOreDictStorageBus container;
    private static final Pattern ORE_DICTIONARY_FILTER = Pattern.compile("[0-9a-zA-Z* &|^!()]*");

    // ========== 控件 ==========
    private GuiTabButton priority;
    private GuiImgButton partition;
    private GuiImgButton storageFilter;
    private GuiImgButton rwMode;
    private MUITextFieldWidget searchFieldInputs;

    public MUIOreDictStorageBusPanel(final ContainerOreDictStorageBus container) {
        super(container);
        this.container = container;
        this.ySize = 170;
    }

    // ========== 初始�?==========

    @Override
    public void initGui() {
        super.initGui();
    }

    // ========== 按钮管理 ==========

    @Override
    protected void addButtons() {
        // Search input is wrapped as a MUI widget while the surrounding action buttons stay on legacy controls.
        this.searchFieldInputs = this.addWidget(new MUITextFieldWidget(
                SEARCH_FIELD_X,
                SEARCH_FIELD_Y,
                SEARCH_FIELD_WIDTH,
                SEARCH_FIELD_HEIGHT)
                        .setEnableBackground(false)
                        .setMaxStringLength(512)
                        .setTextColor(AEMUITheme.COLOR_TEXT_FIELD)
                        .setVisible(true)
                        .setFocused(false)
                        .setValidator(str -> ORE_DICTIONARY_FILTER.matcher(str).matches())
                        .setFocusLostListener(this::saveSearchField));

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

    @Override
    public void fillRegex(String regex) {
        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.setText(regex);
        }
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.fontRenderer.drawString(this.getGuiDisplayName(GuiText.OreDictStorageBus.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(
                this.getSearchFieldLength() + " / " + this.getSearchFieldMaxLength(), 120,
                36, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString("& = AND    " + "| = OR", 8, 36, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString("^ = XOR    " + "! = NOT", 8, 48, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString("() for priority    " + "* for wildcard", 8, 60, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString("Ex.: *Redstone*&!dustRedstone", 8, 72, AEMUITheme.COLOR_TITLE);

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
        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.mouseClicked(xCoord - this.guiLeft, yCoord - this.guiTop, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.saveSearchField(this.searchFieldInputs == null ? "" : this.searchFieldInputs.getText());
            }
            if (this.searchFieldInputs == null || !this.searchFieldInputs.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    private int getSearchFieldLength() {
        return this.searchFieldInputs == null ? 0 : this.searchFieldInputs.getText().length();
    }

    private int getSearchFieldMaxLength() {
        return this.searchFieldInputs == null ? 0 : this.searchFieldInputs.getMaxStringLength();
    }

    private void saveSearchField(String text) {
        if (this.searchFieldInputs == null) {
            return;
        }

        String validatedText = OreDictFilterMatcher.validateExp(text);
        this.searchFieldInputs.setText(validatedText);

        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig("OreDictStorageBus.save", validatedText));
        } catch (IOException e) {
            AELog.debug(e);
        }
    }
}

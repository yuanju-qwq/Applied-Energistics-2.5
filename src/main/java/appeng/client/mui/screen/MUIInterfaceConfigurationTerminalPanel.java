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

import static appeng.client.render.BlockPosHighlighter.hilightBlock;
import static appeng.helpers.ItemStackHelper.stackFromNBT;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import com.google.common.collect.HashMultimap;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;

import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.me.ClientDCInternalInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.widgets.MUIButtonPool;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITextFieldWidget;
import appeng.container.implementations.ContainerInterfaceConfigurationTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InventoryAction;
import appeng.util.BlockPosUtils;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * MUI interface Config configuration terminal panel.
 * Displays the Config list of all legacy item interfaces in the ME network. Supports search filtering (item name/interface name), scrollable list, block highlight positioning. SlotDisconnected Config operations, JEI ghost drag-and-drop. */
public class MUIInterfaceConfigurationTerminalPanel extends AEBasePanel
        implements IInterfaceTerminalGuiCallback, IJEIGhostIngredients {

    private static final int LINES_ON_PAGE = 6;
    private static final int OFFSET_X = 21;
    private static final int SEARCH_FIELD_X = Math.max(32, OFFSET_X);
    private static final int SEARCH_FIELD_Y = 17;
    private static final int SEARCH_FIELD_WIDTH = 65;
    private static final int SEARCH_FIELD_HEIGHT = 12;
    private static final int SCROLL_BAR_LEFT = 189;
    private static final int SCROLL_BAR_TOP = 31;
    private static final int SCROLL_BAR_HEIGHT = 106;

    private final HashMap<Long, ClientDCInternalInv> byId = new HashMap<>();
    private final HashMultimap<String, ClientDCInternalInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<MUIButtonWidget, ClientDCInternalInv> highlightButtonMap = new HashMap<>();
    private final Map<ClientDCInternalInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Set<ClientDCInternalInv> matchedInterfaces = new HashSet<>();
    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();
    private final Map<ClientDCInternalInv, Integer> dimHashMap = new HashMap<>();
    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    private boolean refreshList = false;
    private MUITextFieldWidget searchFieldInputs;

    /** Dynamic highlight button pool (replaces per-frame GuiImgButton creation) */
    private MUIButtonPool highlightButtonPool;

    public MUIInterfaceConfigurationTerminalPanel(final ContainerInterfaceConfigurationTerminal container) {
        super(container);

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 235;
    }

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        // Search widget registration is centralized here to keep initGui focused on layout refresh.
        this.searchFieldInputs = MUITextFieldWidget.addSearchField(this,
                MUITextFieldWidget.SearchFieldSpec.builder(
                        SEARCH_FIELD_X,
                        SEARCH_FIELD_Y,
                        SEARCH_FIELD_WIDTH)
                        .height(SEARCH_FIELD_HEIGHT)
                        .tooltip("Inputs OR names")
                        .onTextChange(text -> this.refreshList())
                        .build());

        // Dynamic highlight button pool
        this.highlightButtonPool = new MUIButtonPool()
                .setDefaultOnClick(btn -> this.onHighlightClicked(btn));
        this.addWidget(this.highlightButtonPool);
    }

    @Override
    public void initGui() {
        super.initGui();

        this.getScrollBar().setLeft(SCROLL_BAR_LEFT);
        this.getScrollBar().setHeight(SCROLL_BAR_HEIGHT);
        this.getScrollBar().setTop(SCROLL_BAR_TOP);

        this.repositionAllSlots();
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = this.ySize + s.getY() - 78 - 7;
        s.xPos = s.getX() + 14;
    }

    // ========== Rendering ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        this.highlightButtonPool.reset();
        this.highlightButtonMap.clear();
        this.inventorySlots.inventorySlots.removeIf(slot -> slot instanceof SlotDisconnected);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {

                MUIButtonWidget hlBtn = this.highlightButtonPool.acquireSettings(
                        4, offset, Settings.ACTIONS, ActionItems.HIGHLIGHT_INTERFACE);
                highlightButtonMap.put(hlBtn, inv);

                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    for (int z = 0; z < 9; z++) {
                        this.inventorySlots.inventorySlots
                                .add(new SlotDisconnected(inv, z + (row * 9), (z * 18 + 22), offset));
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String) {
                linesDraw++;
                offset += 18;
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRenderer.drawString(
                this.getGuiDisplayName(GuiText.InterfaceConfigurationTerminal.getLocal()), 8, 6, AEMUITheme.COLOR_TITLE);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96 + 3, AEMUITheme.COLOR_TITLE);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalInv inv) {
                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    for (int z = 0; z < 9; z++) {
                        if (this.matchedStacks.contains(inv.getInventory().getStackInSlot(z + (row * 9)))) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x8A00FF00);
                        } else if (!matchedInterfaces.contains(inv)) {
                            drawRect(z * 18 + 22, offset, z * 18 + 22 + 16, offset + 16, 0x6A000000);
                        }
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String name) {
                final int rows = this.byName.get(name).size();
                if (rows > 1) {
                    name = name + " (" + rows + ')';
                }
                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 155) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, OFFSET_X + 2, 5 + offset, AEMUITheme.COLOR_TITLE);
                linesDraw++;
                offset += 18;
            }
        }
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/interfaceconfigurationterminal.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        int offset = 29;
        final int ex = this.getScrollBar().getCurrentScroll();
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && ex + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(ex + x);
            if (lineObj instanceof ClientDCInternalInv) {
                GlStateManager.color(1, 1, 1, 1);
                final int width = 9 * 18;
                int extraLines = numUpgradesMap.get(lineObj);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    this.drawTexturedModalRect(offsetX + 20, offsetY + offset, 20, 170, width, 18);
                    offset += 18;
                    linesDraw++;
                }
            } else {
                offset += 18;
                linesDraw++;
            }
        }
    }

    // ========== 输入处理 ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.mouseClicked(xCoord - this.guiLeft, yCoord - this.guiTop, btn);
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    /**
     * Handle highlight button click (from MUIButtonPool onClick callback).
     * Locates the interface in the world and highlights its block position.
     */
    private void onHighlightClicked(MUIButtonWidget btn) {
        ClientDCInternalInv inv = highlightButtonMap.get(btn);
        if (inv == null) {
            return;
        }
        BlockPos blockPos = blockPosHashMap.get(inv);
        BlockPos blockPos2 = mc.player.getPosition();
        int playerDim = mc.world.provider.getDimension();
        int interfaceDim = dimHashMap.get(inv);
        if (playerDim != interfaceDim) {
            try {
                mc.player.sendStatusMessage(
                        PlayerMessages.InterfaceInOtherDimParam.get(interfaceDim,
                                DimensionManager.getWorld(interfaceDim).provider.getDimensionType().getName()),
                        false);
            } catch (Exception e) {
                mc.player.sendStatusMessage(PlayerMessages.InterfaceInOtherDim.get(), false);
            }
        } else {
            hilightBlock(blockPos,
                    System.currentTimeMillis() + 500 * BlockPosUtils.getDistance(blockPos, blockPos2), playerDim);
            mc.player.sendStatusMessage(
                    PlayerMessages.InterfaceHighlighted.get(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                    false);
        }
        mc.player.closeScreen();
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (character == ' ' && this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused()) {
                return;
            }

            if (!this.searchFieldInputs.textboxKeyTyped(character, key)) {
                super.keyTyped(character, key);
            }
        }
    }

    // ========== 数据更新 ==========

    @Override
    public void postUpdate(final NBTTagCompound in) {
        if (in.getBoolean("clear")) {
            this.byId.clear();
            this.refreshList = true;
        }

        for (final Object oKey : in.getKeySet()) {
            final String key = (String) oKey;
            if (key.startsWith("=")) {
                try {
                    final long id = Long.parseLong(key.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(key);
                    final ClientDCInternalInv current = this.getById(id, invData.getLong("sortBy"),
                            invData.getString("un"));
                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));
                    numUpgradesMap.put(current, invData.getInteger("numUpgrades"));

                    for (int x = 0; x < current.getInventory().getSlots(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setStackInSlot(x, stackFromNBT(invData.getCompoundTag(which)));
                        }
                    }
                } catch (final NumberFormatException ignored) {
                }
            }
        }

        if (this.refreshList) {
            this.refreshList = false;
            this.cachedSearches.clear();
            this.refreshList();
        }
    }

    // ========== 列表管理 ==========

    private void refreshList() {
        this.byName.clear();
        this.matchedStacks.clear();
        this.matchedInterfaces.clear();

        final String searchTerm = this.searchFieldInputs == null ? "" : this.searchFieldInputs.getText().toLowerCase();
        final Set<Object> cachedSearch = this.getCacheForSearchTerm(searchTerm);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalInv entry : this.byId.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchTerm.isEmpty();

            if (!found) {
                int slot = 0;
                for (final ItemStack itemStack : entry.getInventory()) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }
                    if (this.itemStackMatchesSearchTerm(itemStack, searchTerm)) {
                        found = true;
                        matchedStacks.add(itemStack);
                    }
                    slot++;
                }
            }
            if (searchTerm.isEmpty() || entry.getName().toLowerCase().contains(searchTerm)) {
                this.matchedInterfaces.add(entry);
                found = true;
            }
            if (found) {
                this.byName.put(entry.getName(), entry);
                cachedSearch.add(entry);
            } else {
                cachedSearch.remove(entry);
            }
        }

        this.names.clear();
        this.names.addAll(this.byName.keySet());
        Collections.sort(this.names);

        this.lines.clear();
        this.lines.ensureCapacity(this.names.size() + this.byId.size());

        for (final String n : this.names) {
            this.lines.add(n);
            final ArrayList<ClientDCInternalInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    private boolean itemStackMatchesSearchTerm(final ItemStack itemStack, final String searchTerm) {
        if (itemStack.isEmpty()) {
            return false;
        }

        boolean foundMatchingItemStack = false;
        final String displayName = appeng.util.Platform
                .getItemDisplayName(AEItemStackType.INSTANCE.createStack(itemStack)).toLowerCase();

        for (String term : searchTerm.split(" ")) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                term = term.substring(1);
                if (displayName.contains(term)) {
                    return false;
                }
            } else if (displayName.contains(term)) {
                foundMatchingItemStack = true;
            } else {
                return false;
            }
        }
        return foundMatchingItemStack;
    }

    private Set<Object> getCacheForSearchTerm(final String searchTerm) {
        if (!this.cachedSearches.containsKey(searchTerm)) {
            this.cachedSearches.put(searchTerm, new HashSet<>());
        }
        final Set<Object> cache = this.cachedSearches.get(searchTerm);
        if (cache.isEmpty() && searchTerm.length() > 1) {
            cache.addAll(this.getCacheForSearchTerm(searchTerm.substring(0, searchTerm.length() - 1)));
            return cache;
        }
        return cache;
    }

    private ClientDCInternalInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalInv o = this.byId.get(id);
        if (o == null) {
            this.byId.put(id,
                    o = new ClientDCInternalInv(InterfaceLogic.NUMBER_OF_CONFIG_SLOTS, id, sortBy, string, 512));
            this.refreshList = true;
        }
        return o;
    }

    // ========== JEI Ghost 拖放 ==========

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!(ingredient instanceof ItemStack)) {
            return Collections.emptyList();
        }
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotDisconnected) {
                ItemStack itemStack = (ItemStack) ingredient;
                IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                    }

                    @Override
                    public void accept(Object ingredient) {
                        try {
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    InventoryAction.PLACE_JEI_GHOST_ITEM, (SlotDisconnected) slot,
                                    AEItemStack.fromItemStack(itemStack));
                            NetworkHandler.instance().sendToServer(p);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                };
                targets.add(target);
                mapTargetSlot.putIfAbsent(target, slot);
            }
        }
        return targets;
    }

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}

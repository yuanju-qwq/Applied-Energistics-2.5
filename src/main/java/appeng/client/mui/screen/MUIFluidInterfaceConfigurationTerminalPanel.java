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

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import com.google.common.collect.HashMultimap;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fluids.FluidStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.api.config.ActionItems;
import appeng.api.config.Settings;
import appeng.api.storage.data.IAEFluidStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ClientDCInternalFluidInv;
import appeng.client.me.SlotDisconnected;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerFluidInterfaceConfigurationTerminal;
import appeng.container.interfaces.IInterfaceTerminalGuiCallback;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.fluids.client.gui.widgets.GuiFluidTank;
import appeng.fluids.helper.DualityFluidInterface;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.util.BlockPosUtils;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;

/**
 * MUI 版流体接口 Config 配置终端面板。
 * <p>
 * 对应旧 GUI：{@link appeng.client.gui.implementations.GuiFluidInterfaceConfigurationTerminal}。
 * 显示 ME 网络中所有流体接口的 Config 配置列表，
 * 支持搜索过滤（流体名/接口名）、滚动列表、方块高亮定位、
 * GuiFluidTank Config 操作、JEI ghost 拖放。
 */
public class MUIFluidInterfaceConfigurationTerminalPanel extends AEBasePanel
        implements IInterfaceTerminalGuiCallback, IJEIGhostIngredients {

    private static final int LINES_ON_PAGE = 6;
    private static final int OFFSET_X = 21;

    // ========== 数据存储 ==========

    private final HashMap<Long, ClientDCInternalFluidInv> byId = new HashMap<>();
    private final HashMultimap<String, ClientDCInternalFluidInv> byName = HashMultimap.create();
    private final HashMap<ClientDCInternalFluidInv, BlockPos> blockPosHashMap = new HashMap<>();
    private final HashMap<GuiButton, ClientDCInternalFluidInv> guiButtonHashMap = new HashMap<>();
    private final Map<GuiFluidTank, ClientDCInternalFluidInv> guiFluidTankMap = new Object2ObjectOpenHashMap<>();
    private final Map<ClientDCInternalFluidInv, Integer> numUpgradesMap = new HashMap<>();
    private final ArrayList<String> names = new ArrayList<>();
    private final ArrayList<Object> lines = new ArrayList<>();
    private final Set<Object> matchedStacks = new HashSet<>();
    private final Set<ClientDCInternalFluidInv> matchedInterfaces = new HashSet<>();
    private final Map<String, Set<Object>> cachedSearches = new WeakHashMap<>();
    private final Map<ClientDCInternalFluidInv, Integer> dimHashMap = new HashMap<>();
    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    private boolean refreshList = false;
    private MEGuiTextField searchFieldInputs;

    // ========== 构造 ==========

    public MUIFluidInterfaceConfigurationTerminalPanel(
            final ContainerFluidInterfaceConfigurationTerminal container) {
        super(container);

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
        this.xSize = 208;
        this.ySize = 235;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        super.initGui();

        this.getScrollBar().setLeft(189);
        this.getScrollBar().setHeight(106);
        this.getScrollBar().setTop(31);

        this.searchFieldInputs = new MEGuiTextField(this.fontRenderer, this.guiLeft + Math.max(32, OFFSET_X),
                this.guiTop + 17, 65, 12);
        this.searchFieldInputs.setEnableBackgroundDrawing(false);
        this.searchFieldInputs.setMaxStringLength(25);
        this.searchFieldInputs.setTextColor(0xFFFFFF);
        this.searchFieldInputs.setVisible(true);
        this.searchFieldInputs.setFocused(false);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    // ========== 渲染 ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.buttonList.clear();
        this.guiButtonHashMap.clear();

        this.fontRenderer.drawString(
                this.getGuiDisplayName(GuiText.FluidInterfaceConfigurationTerminal.getLocal()), 8, 6, 4210752);
        this.fontRenderer.drawString(GuiText.inventory.getLocal(), OFFSET_X + 2, this.ySize - 96 + 3, 4210752);

        final int currentScroll = this.getScrollBar().getCurrentScroll();

        this.guiSlots.removeIf(slot -> slot instanceof GuiFluidTank);

        int offset = 30;
        int linesDraw = 0;
        for (int x = 0; x < LINES_ON_PAGE && linesDraw < LINES_ON_PAGE && currentScroll + x < this.lines.size(); x++) {
            final Object lineObj = this.lines.get(currentScroll + x);
            if (lineObj instanceof ClientDCInternalFluidInv) {
                final ClientDCInternalFluidInv inv = (ClientDCInternalFluidInv) lineObj;

                GuiButton guiButton = new GuiImgButton(guiLeft + 4, guiTop + offset, Settings.ACTIONS,
                        ActionItems.HIGHLIGHT_INTERFACE);
                guiButtonHashMap.put(guiButton, inv);
                this.buttonList.add(guiButton);
                int extraLines = numUpgradesMap.get(inv);

                for (int row = 0; row < 1 + extraLines && linesDraw < LINES_ON_PAGE; ++row) {
                    for (int z = 0; z < DualityFluidInterface.NUMBER_OF_TANKS; z++) {
                        GuiFluidTank tankSlot;
                        if (!matchedInterfaces.contains(inv)
                                && !this.matchedStacks.contains(inv.getInventory().getFluidInSlot(z + (row * 5)))) {
                            tankSlot = new GuiFluidTank(inv.getInventory(), z + (row * 5), z + (row * 5),
                                    (z * 18 + 22), offset, 16, 16, true);
                        } else {
                            tankSlot = new GuiFluidTank(inv.getInventory(), z + (row * 5), z + (row * 5),
                                    (z * 18 + 22), offset, 16, 16);
                        }
                        this.guiSlots.add(tankSlot);
                        guiFluidTankMap.put(tankSlot, inv);
                    }
                    linesDraw++;
                    offset += 18;
                }
            } else if (lineObj instanceof String) {
                String name = (String) lineObj;
                final int rows = this.byName.get(name).size();
                if (rows > 1) {
                    name = name + " (" + rows + ')';
                }
                while (name.length() > 2 && this.fontRenderer.getStringWidth(name) > 155) {
                    name = name.substring(0, name.length() - 1);
                }
                this.fontRenderer.drawString(name, OFFSET_X + 2, 5 + offset, 4210752);
                linesDraw++;
                offset += 18;
            }
        }

        if (searchFieldInputs != null && searchFieldInputs.isMouseIn(mouseX, mouseY)) {
            drawTooltip(Mouse.getEventX() * this.width / this.mc.displayWidth - offsetX, mouseY - guiTop,
                    "Inputs OR names");
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
            if (lineObj instanceof ClientDCInternalFluidInv) {
                GlStateManager.color(1, 1, 1, 1);
                final int width = DualityFluidInterface.NUMBER_OF_TANKS * 18;

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

        if (this.searchFieldInputs != null) {
            this.searchFieldInputs.drawTextBox();
        }
    }

    // ========== 输入处理 ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        this.searchFieldInputs.mouseClicked(xCoord, yCoord, btn);

        if (btn == 1 && this.searchFieldInputs.isMouseIn(xCoord, yCoord)) {
            this.searchFieldInputs.setText("");
            this.refreshList();
        }

        for (GuiCustomSlot slot : this.guiSlots) {
            if (slot instanceof GuiFluidTank) {
                if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), xCoord, yCoord)
                        && slot.canClick(this.mc.player)) {
                    NetworkHandler.instance().sendToServer(new PacketInventoryAction(InventoryAction.PICKUP_OR_SET_DOWN,
                            slot.getId(), guiFluidTankMap.get(slot).getId()));
                    return;
                }
            }
        }

        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        if (guiButtonHashMap.containsKey(btn)) {
            BlockPos blockPos = blockPosHashMap.get(guiButtonHashMap.get(this.selectedButton));
            BlockPos blockPos2 = mc.player.getPosition();
            int playerDim = mc.world.provider.getDimension();
            int interfaceDim = dimHashMap.get(guiButtonHashMap.get(this.selectedButton));
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
    }

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (character == ' ' && this.searchFieldInputs.getText().isEmpty() && this.searchFieldInputs.isFocused()) {
                return;
            }
            if (this.searchFieldInputs.textboxKeyTyped(character, key)) {
                this.refreshList();
            } else {
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

        for (final String oKey : in.getKeySet()) {
            if (oKey.startsWith("=")) {
                try {
                    final long id = Long.parseLong(oKey.substring(1), Character.MAX_RADIX);
                    final NBTTagCompound invData = in.getCompoundTag(oKey);
                    final ClientDCInternalFluidInv current = this.getById(id, invData.getLong("sortBy"),
                            invData.getString("un"));
                    blockPosHashMap.put(current, NBTUtil.getPosFromTag(invData.getCompoundTag("pos")));
                    dimHashMap.put(current, invData.getInteger("dim"));
                    numUpgradesMap.put(current, invData.getInteger("numUpgrades"));

                    for (int x = 0; x < current.getInventory().getSlots(); x++) {
                        final String which = Integer.toString(x);
                        if (invData.hasKey(which)) {
                            current.getInventory().setFluidInSlot(x,
                                    AEFluidStack.fromNBT(invData.getCompoundTag(which)));
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
        this.buttonList.clear();
        this.matchedStacks.clear();
        this.matchedInterfaces.clear();

        final String searchTerm = this.searchFieldInputs.getText().toLowerCase();
        final Set<Object> cachedSearch = this.getCacheForSearchTerm(searchTerm);
        final boolean rebuild = cachedSearch.isEmpty();

        for (final ClientDCInternalFluidInv entry : this.byId.values()) {
            if (!rebuild && !cachedSearch.contains(entry)) {
                continue;
            }

            boolean found = searchTerm.isEmpty();

            if (!found) {
                int slot = 0;
                for (int i = 0; i < entry.getInventory().getSlots(); i++) {
                    if (slot > 8 + numUpgradesMap.get(entry) * 9) {
                        break;
                    }
                    IAEFluidStack fs = entry.getInventory().getFluidInSlot(i);
                    if (this.fluidStackMatchesSearchTerm(fs, searchTerm)) {
                        found = true;
                        matchedStacks.add(fs);
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
            final ArrayList<ClientDCInternalFluidInv> clientInventories = new ArrayList<>(this.byName.get(n));
            Collections.sort(clientInventories);
            this.lines.addAll(clientInventories);
        }

        this.getScrollBar().setRange(0, this.lines.size() - 1, 1);
    }

    private boolean fluidStackMatchesSearchTerm(final IAEFluidStack iaeFluidStack, final String searchTerm) {
        if (iaeFluidStack == null) {
            return false;
        }

        boolean foundMatchingFluidStack = false;
        final String displayName = Platform.getFluidDisplayName(iaeFluidStack).toLowerCase();

        for (String term : searchTerm.split(" ")) {
            if (term.length() > 1 && (term.startsWith("-") || term.startsWith("!"))) {
                term = term.substring(1);
                if (displayName.contains(term)) {
                    return false;
                }
            } else if (displayName.contains(term)) {
                foundMatchingFluidStack = true;
            } else {
                return false;
            }
        }
        return foundMatchingFluidStack;
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

    private ClientDCInternalFluidInv getById(final long id, final long sortBy, final String string) {
        ClientDCInternalFluidInv o = this.byId.get(id);
        if (o == null) {
            this.byId.put(id,
                    o = new ClientDCInternalFluidInv(DualityFluidInterface.NUMBER_OF_TANKS, id, sortBy, string, 1000));
            this.refreshList = true;
        }
        return o;
    }

    // ========== JEI Ghost 拖放 ==========

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        if (!(ingredient instanceof FluidStack)) {
            return Collections.emptyList();
        }
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        for (Slot slot : this.inventorySlots.inventorySlots) {
            if (slot instanceof SlotDisconnected) {
                FluidStack fluidStack = (FluidStack) ingredient;
                IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                    @Override
                    public Rectangle getArea() {
                        return new Rectangle(getGuiLeft() + slot.xPos, getGuiTop() + slot.yPos, 16, 16);
                    }

                    @Override
                    public void accept(Object ingredient) {
                        try {
                            final PacketInventoryAction p = new PacketInventoryAction(
                                    InventoryAction.PLACE_JEI_GHOST_ITEM, (SlotDisconnected) slot,
                                    AEItemStack.fromItemStack(
                                            AEFluidStack.fromFluidStack(fluidStack).asItemStackRepresentation()));
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
}

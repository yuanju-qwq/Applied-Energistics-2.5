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

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Joiner;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEBasePanelGuiHandler;
import appeng.container.implementations.ContainerCraftConfirm;
import appeng.container.interfaces.ICraftConfirmGuiCallback;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.AEGuiKey;
import appeng.core.sync.AEGuiKeys;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;
import appeng.util.Platform;
import appeng.util.item.IMixedStackList;
import appeng.util.item.IAEStackList;

/**
 * MUI 版合成确认面板。
 * <p>
 * 功能：显示合成计划（从仓库/待合成/缺失的物品列表），选择 CPU，确认开始合成。
 * <p>
 * 特性：
 * <ul>
 *   <li>3 列 × 5 行网格显示物品</li>
 *   <li>从仓库 / 待合成 / 缺失 三种状态指示</li>
 *   <li>缺失物品红色高亮</li>
 *   <li>CPU 选择按钮（左右键切换）</li>
 *   <li>字节使用量和协处理器信息</li>
 *   <li>模拟模式标识</li>
 *   <li>合成执行次数（样板）显示</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUICraftConfirmPanel extends AEBasePanel
        implements ICraftConfirmGuiCallback, AEBasePanelGuiHandler.IMUIVisualListPanel {

    // ========== 常量 ==========

    private static final int ROWS = 5;

    // ========== 数据 ==========

    private final ContainerCraftConfirm ccc;

    private final IMixedStackList storage = new IAEStackList();
    private final IMixedStackList pending = new IAEStackList();
    private final IMixedStackList missing = new IAEStackList();

    private final List<IAEStack<?>> visual = new ArrayList<>();

    // ========== UI 控件 ==========

    private AEGuiKey originalGui;
    private GuiButton cancel;
    private GuiButton start;
    private GuiButton selectCPU;
    private int tooltip = -1;

    // ========== 构造 ==========

    public MUICraftConfirmPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftConfirm(inventoryPlayer, te));
        this.xSize = 238;
        this.ySize = 206;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);

        this.ccc = (ContainerCraftConfirm) this.inventorySlots;
        this.ccc.setGui((ICraftConfirmGuiCallback) this);

        // 确定来源终端
        if (te instanceof WirelessTerminalGuiObject) {
            ItemStack itemStack = ((WirelessTerminalGuiObject) te).getItemStack();
            this.originalGui = AEGuiKeys.fromLegacy(
                    (appeng.core.sync.GuiBridge) AEApi.instance().registries().wireless()
                            .getWirelessTerminalHandler(itemStack).getGuiHandler(itemStack));
        }

        if (te instanceof PartTerminal) {
            this.originalGui = AEGuiKeys.ME_TERMINAL;
        }

        if (te instanceof PartCraftingTerminal) {
            this.originalGui = AEGuiKeys.CRAFTING_TERMINAL;
        }

        if (te instanceof PartPatternTerminal) {
            this.originalGui = AEGuiKeys.PATTERN_TERMINAL;
        }

        if (te instanceof PartExpandedProcessingPatternTerminal) {
            this.originalGui = AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }
    }

    boolean isAutoStart() {
        return ((ContainerCraftConfirm) this.inventorySlots).isAutoStart();
    }

    // ========== ICraftConfirmGuiCallback ==========

    @Override
    public void postGenericUpdate(final List<IAEStack<?>> list, final byte ref) {
        switch (ref) {
            case 0:
                for (final IAEStack<?> l : list) {
                    this.handleInput(this.storage, l);
                }
                break;

            case 1:
                for (final IAEStack<?> l : list) {
                    this.handleInput(this.pending, l);
                }
                break;

            case 2:
                for (final IAEStack<?> l : list) {
                    this.handleInput(this.missing, l);
                }
                break;
        }

        for (final IAEStack<?> l : list) {
            final long amt = this.getTotal(l);

            if (amt <= 0) {
                this.deleteVisualStack(l);
            } else {
                final IAEStack<?> is = this.findVisualStack(l);
                is.setStackSize(amt);
            }
        }

        this.updateScrollBar();
    }

    // ========== 初始化 ==========

    @Override
    protected void setupWidgets() {
        // Start 按钮
        this.start = new GuiButton(0, this.guiLeft + 162, this.guiTop + this.ySize - 25, 50, 20,
                GuiText.Start.getLocal());
        this.start.enabled = false;
        this.buttonList.add(this.start);

        // CPU 选择按钮
        this.selectCPU = new GuiButton(0, this.guiLeft + (219 - 180) / 2, this.guiTop + this.ySize - 68, 180, 20,
                GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic);
        this.selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

        // Cancel 按钮（仅当有来源终端时显示）
        if (this.originalGui != null) {
            this.cancel = new GuiButton(0, this.guiLeft + 6, this.guiTop + this.ySize - 25, 50, 20,
                    GuiText.Cancel.getLocal());
        }

        this.buttonList.add(this.cancel);
    }

    private void updateScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(19).setLeft(218).setHeight(114);
        this.getScrollBar().setRange(0, (size + 2) / 3 - ROWS, 1);
    }

    // ========== 绘制 ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.updateCPUButtonText();

        this.start.enabled = !(this.ccc.hasNoCPU() || this.isSimulation());
        this.selectCPU.enabled = !this.isSimulation();

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

        // 计算鼠标悬停的物品索引
        final int offY = 23;
        int y = 0;
        int x = 0;
        for (int z = 0; z <= 4 * 5; z++) {
            final int minX = gx + 9 + x * 67;
            final int minY = gy + 22 + y * offY;

            if (minX < mouseX && minX + 67 > mouseX) {
                if (minY < mouseY && minY + offY - 2 > mouseY) {
                    this.tooltip = z;
                    break;
                }
            }

            x++;

            if (x > 2) {
                y++;
                x = 0;
            }
        }

        super.drawScreen(mouseX, mouseY, btn);
    }

    private void updateCPUButtonText() {
        String btnTextText = GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic.getLocal();
        if (this.ccc.getSelectedCpu() >= 0) {
            if (this.ccc.getName().length() > 0) {
                final String name = this.ccc.getName().substring(0, Math.min(20, this.ccc.getName().length()));
                btnTextText = GuiText.CraftingCPU.getLocal() + ": " + name;
            } else {
                btnTextText = GuiText.CraftingCPU.getLocal() + ": #" + this.ccc.getSelectedCpu();
            }
        }

        if (this.ccc.hasNoCPU()) {
            btnTextText = GuiText.NoCraftingCPUs.getLocal();
        }

        this.selectCPU.displayString = btnTextText;
    }

    private boolean isSimulation() {
        return ((ContainerCraftConfirm) this.inventorySlots).isSimulation();
    }

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 标题行
        final long BytesUsed = this.ccc.getUsedBytes();
        final String byteUsed = NumberFormat.getInstance().format(BytesUsed);
        final String Add = BytesUsed > 0 ? (byteUsed + ' ' + GuiText.BytesUsed.getLocal())
                : GuiText.CalculatingWait.getLocal();
        this.fontRenderer.drawString(GuiText.CraftingPlan.getLocal() + " - " + Add, 8, 7, 4210752);

        // CPU 信息行
        String dsp = null;

        if (this.isSimulation()) {
            dsp = GuiText.Simulation.getLocal();
        } else {
            dsp = this.ccc.getCpuAvailableBytes() > 0
                    ? (GuiText.Bytes.getLocal() + ": " + this.ccc.getCpuAvailableBytes() + " : " + GuiText.CoProcessors
                            .getLocal() + ": " + this.ccc.getCpuCoProcessors())
                    : GuiText.Bytes.getLocal() + ": N/A : " + GuiText.CoProcessors.getLocal() + ": N/A";
        }

        final int offset = (219 - this.fontRenderer.getStringWidth(dsp)) / 2;
        this.fontRenderer.drawString(dsp, offset, 165, 4210752);

        // 物品列表
        final int sectionLength = 67;

        int x = 0;
        int y = 0;
        final int xo = 9;
        final int yo = 22;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * ROWS;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final IAEStack<?> refStack = this.visual.get(z);
            if (refStack != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final IAEStack<?> stored = this.storage.findPrecise(refStack);
                final IAEStack<?> pendingStack = this.pending.findPrecise(refStack);
                final IAEStack<?> missingStack = this.missing.findPrecise(refStack);

                int lines = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    lines++;
                }
                if (missingStack != null && missingStack.getStackSize() > 0) {
                    lines++;
                }
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                    if (pendingStack.getCountRequestableCrafts() > 0) {
                        lines++;
                    }
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                // 从仓库
                if (stored != null && stored.getStackSize() > 0) {
                    String str = Long.toString(stored.getStackSize());
                    if (stored.getStackSize() >= 10000) {
                        str = Long.toString(stored.getStackSize() / 1000) + 'k';
                    }
                    if (stored.getStackSize() >= 10000000) {
                        str = Long.toString(stored.getStackSize() / 1000000) + 'm';
                    }

                    str = GuiText.FromStorage.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.FromStorage.getLocal() + ": " + stored.getStackSize());
                    }

                    downY += 5;
                }

                // 缺失
                boolean red = false;
                if (missingStack != null && missingStack.getStackSize() > 0) {
                    String str = Long.toString(missingStack.getStackSize());
                    if (missingStack.getStackSize() >= 10000) {
                        str = Long.toString(missingStack.getStackSize() / 1000) + 'k';
                    }
                    if (missingStack.getStackSize() >= 10000000) {
                        str = Long.toString(missingStack.getStackSize() / 1000000) + 'm';
                    }

                    str = GuiText.Missing.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Missing.getLocal() + ": " + missingStack.getStackSize());
                    }

                    red = true;
                    downY += 5;
                }

                // 待合成
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    String str = Long.toString(pendingStack.getStackSize());
                    if (pendingStack.getStackSize() >= 10000) {
                        str = Long.toString(pendingStack.getStackSize() / 1000) + 'k';
                    }
                    if (pendingStack.getStackSize() >= 10000000) {
                        str = Long.toString(pendingStack.getStackSize() / 1000000) + 'm';
                    }

                    str = GuiText.ToCraft.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, 4210752);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.ToCraft.getLocal() + ": " + pendingStack.getStackSize());
                    }

                    downY += 5;

                    // 样板执行次数
                    long craftRounds = pendingStack.getCountRequestableCrafts();
                    if (craftRounds > 0) {
                        String roundsStr = String.format("%s: %d", GuiText.PatternExecutionCount.getLocal(),
                                craftRounds);
                        final int wRounds = 4 + this.fontRenderer.getStringWidth(roundsStr);
                        this.fontRenderer.drawString(roundsStr,
                                (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (wRounds * 0.5)) * 2),
                                (y * offY + yo + 6 - negY + downY) * 2, 4210752);
                        downY += 5;
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
                final int posY = y * offY + yo;

                final ItemStack is = refStack.asItemStackRepresentation();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(refStack);

                    if (pendingStack != null) {
                        long rounds = pendingStack.getCountRequestableCrafts();
                        if (rounds > 0) {
                            lineList.add(String.format("%s: %d",
                                    GuiText.PatternExecutionCount.getLocal(),
                                    rounds));
                        }
                    }

                    if (lineList.size() > 0) {
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                    toolPosY = y * offY + yo;
                }

                this.drawItem(posX, posY, is);

                // 缺失物品红色高亮
                if (red) {
                    final int startX = x * (1 + sectionLength) + xo;
                    final int startY = posY - 4;
                    drawRect(startX, startY, startX + sectionLength, startY + offY, 0x1AFF0000);
                }

                x++;

                if (x > 2) {
                    y++;
                    x = 0;
                }
            }
        }

        if (this.tooltip >= 0 && !dspToolTip.isEmpty()) {
            this.drawTooltip(toolPosX, toolPosY + 10, dspToolTip);
        }
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.updateScrollBar();
        this.bindTexture("guis/craftingreport.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

    // ========== 输入事件 ==========

    @Override
    protected void keyTyped(final char character, final int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                this.actionPerformed(this.start);
            }
            super.keyTyped(character, key);
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.selectCPU) {
            try {
                NetworkHandler.instance()
                        .sendToServer(new PacketValueConfig("Terminal.Cpu", backwards ? "Prev" : "Next"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }

        if (btn == this.cancel) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }

        if (btn == this.start) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("Terminal.Start", "Start"));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }
    }

    // ========== 数据处理 ==========

    private void handleInput(final IMixedStackList s, final IAEStack<?> l) {
        IAEStack<?> a = s.findPrecise(l);

        if (l.getStackSize() <= 0) {
            if (a != null) {
                a.reset();
            }
        } else {
            if (a == null) {
                s.add(l.copy());
                a = s.findPrecise(l);
            }

            if (a != null) {
                a.setStackSize(l.getStackSize());
                a.setCountRequestableCrafts(l.getCountRequestableCrafts());
            }
        }
    }

    private long getTotal(final IAEStack<?> is) {
        final IAEStack<?> a = this.storage.findPrecise(is);
        final IAEStack<?> c = this.pending.findPrecise(is);
        final IAEStack<?> m = this.missing.findPrecise(is);

        long total = 0;

        if (a != null) {
            total += a.getStackSize();
        }

        if (c != null) {
            total += c.getStackSize();
        }

        if (m != null) {
            total += m.getStackSize();
        }

        return total;
    }

    private void deleteVisualStack(final IAEStack<?> l) {
        final Iterator<IAEStack<?>> i = this.visual.iterator();
        while (i.hasNext()) {
            final IAEStack<?> o = i.next();
            if (o.equals(l)) {
                i.remove();
                return;
            }
        }
    }

    private IAEStack<?> findVisualStack(final IAEStack<?> l) {
        for (final IAEStack<?> o : this.visual) {
            if (o.equals(l)) {
                return o;
            }
        }

        final IAEStack<?> stack = l.copy();
        this.visual.add(stack);
        return stack;
    }

    // ========== 公共访问器 ==========

    public List<IAEStack<?>> getVisual() {
        return visual;
    }

    public int getDisplayedRows() {
        return ROWS;
    }
}

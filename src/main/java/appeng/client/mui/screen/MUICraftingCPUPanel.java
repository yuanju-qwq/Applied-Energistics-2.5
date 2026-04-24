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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Joiner;

import org.apache.commons.lang3.time.DurationFormatUtils;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEColor;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ISortSource;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEBasePanelGuiHandler;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.interfaces.ICraftingCPUGuiCallback;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;
import appeng.util.item.IMixedStackList;
import appeng.util.item.IAEStackList;

/**
 * MUI 版合成 CPU 状态面板。
 * <p>
 * 功能：显示当前合成 CPU 的运行状态（已存储/正在合成/待处理的物品和流体列表）。
 * <p>
 * 特性：
 * <ul>
 *   <li>3 列 × 6 行网格显示物品</li>
 *   <li>颜色状态指示（绿色=正在合成，黄色=已排程）</li>
 *   <li>ETA 预计剩余时间显示</li>
 *   <li>追踪/暂停恢复/取消按钮</li>
 *   <li>滚动条翻页</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class MUICraftingCPUPanel extends AEBasePanel
        implements ISortSource, ICraftingCPUGuiCallback, AEBasePanelGuiHandler.IMUIVisualListPanel {

    // ========== 常量 ==========

    private static final int GUI_HEIGHT = 210;
    private static final int GUI_WIDTH = 238;

    private static final int DISPLAYED_ROWS = 6;

    private static final int TEXT_COLOR = 0x404040;
    private static final int BACKGROUND_ALPHA = 0x5A000000;

    private static final int SECTION_LENGTH = 67;

    private static final int SCROLLBAR_TOP = 19;
    private static final int SCROLLBAR_LEFT = 218;
    private static final int SCROLLBAR_HEIGHT = 137;

    private static final int CANCEL_LEFT_OFFSET = 8 + 50 + 8 + 50 + 8;
    private static final int CANCEL_TOP_OFFSET = 50;
    private static final int CANCEL_HEIGHT = 20;
    private static final int CANCEL_WIDTH = 50;

    private static final int SWITCH_LEFT_OFFSET = 8 + 50 + 8;
    private static final int SWITCH_WIDTH = 50;

    private static final int TRACK_LEFT_OFFSET = 8;
    private static final int TRACK_WIDTH = 50;

    private static final int TITLE_TOP_OFFSET = 7;
    private static final int TITLE_LEFT_OFFSET = 8;

    private static final int ITEMSTACK_LEFT_OFFSET = 9;
    private static final int ITEMSTACK_TOP_OFFSET = 22;

    // ========== 数据 ==========

    private final ContainerCraftingCPU craftingCpu;

    private IMixedStackList storage = new IAEStackList();
    private IMixedStackList active = new IAEStackList();
    private IMixedStackList pending = new IAEStackList();

    private List<IAEStack<?>> visual = new ArrayList<>();

    // ========== UI 控件 ==========

    private GuiButton cancel;
    private GuiButton switchButton;
    private GuiButton trackButton;
    private int tooltip = -1;

    // ========== 构造 ==========

    public MUICraftingCPUPanel(final InventoryPlayer inventoryPlayer, final Object te) {
        this(new ContainerCraftingCPU(inventoryPlayer, te));
    }

    protected MUICraftingCPUPanel(final ContainerCraftingCPU container) {
        super(container);
        this.craftingCpu = container;
        this.craftingCpu.setGui((ICraftingCPUGuiCallback) this);
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;

        final GuiScrollbar scrollbar = new GuiScrollbar();
        this.setScrollBar(scrollbar);
    }

    // ========== ICraftingCPUGuiCallback ==========

    @Override
    public void clearItems() {
        this.storage = new IAEStackList();
        this.active = new IAEStackList();
        this.pending = new IAEStackList();
        this.visual = new ArrayList<>();
    }

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
                    this.handleInput(this.active, l);
                }
                break;

            case 2:
                for (final IAEStack<?> l : list) {
                    this.handleInput(this.pending, l);
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
        this.updateScrollBar();

        this.trackButton = new GuiButton(2,
                this.guiLeft + TRACK_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                TRACK_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Track.getLocal());
        this.buttonList.add(this.trackButton);

        this.switchButton = new GuiButton(1,
                this.guiLeft + SWITCH_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                SWITCH_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Resume.getLocal() + "/" + GuiText.Pause.getLocal());
        this.buttonList.add(this.switchButton);

        this.cancel = new GuiButton(0,
                this.guiLeft + CANCEL_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                CANCEL_WIDTH,
                CANCEL_HEIGHT,
                GuiText.Cancel.getLocal());
        this.buttonList.add(this.cancel);
    }

    private void updateScrollBar() {
        final int size = this.visual.size();

        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(SCROLLBAR_HEIGHT);
        this.getScrollBar().setRange(0, (size + 2) / 3 - DISPLAYED_ROWS, 1);
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (this.cancel == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Cancel", "Cancel"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (this.switchButton == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Switch", "Switch"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (this.trackButton == btn) {
            try {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("TileCrafting.Track", "Track"));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    // ========== 绘制 ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();
        this.trackButton.enabled = !this.visual.isEmpty();
        this.switchButton.enabled = true;

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

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        // 标题 + ETA
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());

        if (this.craftingCpu.getEstimatedTime() > 0 && !this.visual.isEmpty()) {
            final long etaInMilliseconds = TimeUnit.MILLISECONDS.convert(this.craftingCpu.getEstimatedTime(),
                    TimeUnit.NANOSECONDS);
            final String etaTimeText = DurationFormatUtils.formatDuration(etaInMilliseconds,
                    GuiText.ETAFormat.getLocal());
            title += " - " + etaTimeText;
        }

        this.fontRenderer.drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, TEXT_COLOR);

        // 物品列表
        int x = 0;
        int y = 0;
        final int viewStart = this.getScrollBar().getCurrentScroll() * 3;
        final int viewEnd = viewStart + 3 * 6;

        String dspToolTip = "";
        final List<String> lineList = new ArrayList<>();
        int toolPosX = 0;
        int toolPosY = 0;

        final int offY = 23;

        final ReadableNumberConverter converter = ReadableNumberConverter.INSTANCE;
        for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
            final IAEStack<?> refStack = this.visual.get(z);
            if (refStack != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final IAEStack<?> stored = this.storage.findPrecise(refStack);
                final IAEStack<?> activeStack = this.active.findPrecise(refStack);
                final IAEStack<?> pendingStack = this.pending.findPrecise(refStack);

                int lines = 0;

                if (stored != null && stored.getStackSize() > 0) {
                    lines++;
                }
                boolean isActive = false;
                if (activeStack != null && activeStack.getStackSize() > 0) {
                    lines++;
                    isActive = true;
                }
                boolean scheduled = false;
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    lines++;
                    scheduled = true;
                }

                // 颜色状态背景
                if (AEConfig.instance().isUseColoredCraftingStatus() && (isActive || scheduled)) {
                    final int bgColor = (isActive ? AEColor.GREEN.blackVariant : AEColor.YELLOW.blackVariant)
                            | BACKGROUND_ALPHA;
                    final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
                    final int startY = ((y * offY + ITEMSTACK_TOP_OFFSET) - 3) * 2;
                    drawRect(startX, startY, startX + (SECTION_LENGTH * 2), startY + (offY * 2) - 2, bgColor);
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                // 已存储
                if (stored != null && stored.getStackSize() > 0) {
                    final String str = GuiText.Stored.getLocal() + ": "
                            + converter.toWideReadableForm(stored.getStackSize());
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Stored.getLocal() + ": " + stored.getStackSize());
                    }

                    downY += 5;
                }

                // 正在合成
                if (activeStack != null && activeStack.getStackSize() > 0) {
                    final String str = GuiText.Crafting.getLocal() + ": "
                            + converter.toWideReadableForm(activeStack.getStackSize());
                    final int w = 4 + this.fontRenderer.getStringWidth(str);

                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Crafting.getLocal() + ": " + activeStack.getStackSize());
                    }

                    downY += 5;
                }

                // 待处理
                if (pendingStack != null && pendingStack.getStackSize() > 0) {
                    final String str = GuiText.Scheduled.getLocal() + ": "
                            + converter.toWideReadableForm(pendingStack.getStackSize());
                    final int w = 4 + this.fontRenderer.getStringWidth(str);

                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Scheduled.getLocal() + ": " + pendingStack.getStackSize());
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
                final int posY = y * offY + ITEMSTACK_TOP_OFFSET;

                final ItemStack is = refStack.asItemStackRepresentation();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = Platform.getItemDisplayName(refStack);

                    if (lineList.size() > 0) {
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 8;
                    toolPosY = y * offY + ITEMSTACK_TOP_OFFSET;
                }

                this.drawItem(posX, posY, is);

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
        this.bindTexture("guis/craftingcpu.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
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
            }
        }
    }

    private long getTotal(final IAEStack<?> is) {
        final IAEStack<?> a = this.storage.findPrecise(is);
        final IAEStack<?> b = this.active.findPrecise(is);
        final IAEStack<?> c = this.pending.findPrecise(is);

        long total = 0;

        if (a != null) {
            total += a.getStackSize();
        }

        if (b != null) {
            total += b.getStackSize();
        }

        if (c != null) {
            total += c.getStackSize();
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

    // ========== ISortSource ==========

    @Override
    public Enum getSortBy() {
        return SortOrder.NAME;
    }

    @Override
    public Enum getSortDir() {
        return SortDir.ASCENDING;
    }

    @Override
    public Enum getSortDisplay() {
        return ViewItems.ALL;
    }

    // ========== 公共访问器 ==========

    public List<IAEStack<?>> getVisual() {
        return visual;
    }

    public int getDisplayedRows() {
        return DISPLAYED_ROWS;
    }
}

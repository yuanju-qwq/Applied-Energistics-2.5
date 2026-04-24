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

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.container.interfaces.ICraftingStatusGuiCallback;
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

/**
 * MUI 版合成状态面板。
 * <p>
 * 继承 {@link MUICraftingCPUPanel}，在左侧增加 CPU 选择器列表，
 * 右上角增加返回原始终端的 Tab 按钮。
 */
@SideOnly(Side.CLIENT)
public class MUICraftingStatusPanel extends MUICraftingCPUPanel implements ICraftingStatusGuiCallback {

    // ========== CPU 选择器表格尺寸常量 ==========

    private static final int CPU_TABLE_WIDTH = 94;
    private static final int CPU_TABLE_HEIGHT = 164;
    private static final int CPU_TABLE_SLOT_XOFF = 100;
    private static final int CPU_TABLE_SLOT_YOFF = 0;
    private static final int CPU_TABLE_SLOT_WIDTH = 67;
    private static final int CPU_TABLE_SLOT_HEIGHT = 23;

    // ========== 数据 ==========

    private final ContainerCraftingStatus status;
    private GuiButton selectCPU;
    private GuiScrollbar cpuScrollbar;

    private GuiTabButton originalGuiBtn;
    private AEGuiKey originalGui;
    private ItemStack myIcon = ItemStack.EMPTY;
    private String selectedCPUName = "";

    // ========== 构造 ==========

    public MUICraftingStatusPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        final Object target = this.status.getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = AEGuiKeys.fromLegacy(
                    (appeng.core.sync.GuiBridge) AEApi.instance().registries().wireless()
                            .getWirelessTerminalHandler(myIcon).getGuiHandler(myIcon));
        }

        if (target instanceof PartTerminal) {
            this.myIcon = parts.terminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.ME_TERMINAL;
        }

        if (target instanceof PartCraftingTerminal) {
            this.myIcon = parts.craftingTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            this.myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = AEGuiKeys.EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        this.selectCPU = new GuiButton(0, this.guiLeft + 8, this.guiTop + this.ySize - 25,
                166, 20, GuiText.CraftingCPU.getLocal() + ": " + GuiText.NoCraftingCPUs);
        this.selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

        this.cpuScrollbar = new GuiScrollbar();
        this.cpuScrollbar.setLeft(-16);
        this.cpuScrollbar.setTop(19);
        this.cpuScrollbar.setWidth(12);
        this.cpuScrollbar.setHeight(137);

        if (!this.myIcon.isEmpty()) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(this.guiLeft + 213, this.guiTop - 4,
                            this.myIcon, this.myIcon.getDisplayName(), this.itemRender));
            this.originalGuiBtn.setHideEdge(13);
        }
    }

    // ========== 按钮事件 ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }
    }

    // ========== 渲染 ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        this.selectedCPUName = null;
        this.cpuScrollbar.setRange(0, Integer.max(0, cpus.size() - 6), 1);
        for (CraftingCPUStatus cpu : cpus) {
            if (cpu.getSerial() == this.status.selectedCpuSerial) {
                this.selectedCPUName = cpu.getName();
            }
        }
        this.updateCPUButtonText();
        super.drawScreen(mouseX, mouseY, btn);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        final int firstCpu = this.cpuScrollbar.getCurrentScroll();
        CraftingCPUStatus hoveredCpu = hitCpu(mouseX, mouseY);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        final int textColor = 0x202020;
        final int pausedColor = 0xFFA500;

        // 绘制 CPU 槽位列表
        for (int i = firstCpu; i < firstCpu + 6 && i < cpus.size(); i++) {
            if (i < 0) {
                continue;
            }
            CraftingCPUStatus cpu = cpus.get(i);
            if (cpu == null) {
                continue;
            }

            int x = -CPU_TABLE_WIDTH + 9;
            int y = 19 + (i - firstCpu) * CPU_TABLE_SLOT_HEIGHT;

            // 槽位背景颜色
            if (cpu.getSerial() == this.status.selectedCpuSerial) {
                GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
            } else if (hoveredCpu != null && hoveredCpu.getSerial() == cpu.getSerial()) {
                GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
            } else {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
            this.bindTexture("guis/cpu_selector.png");
            this.drawTexturedModalRect(x, y, CPU_TABLE_SLOT_XOFF, CPU_TABLE_SLOT_YOFF,
                    CPU_TABLE_SLOT_WIDTH, CPU_TABLE_SLOT_HEIGHT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // CPU 名称
            String name = cpu.getName();
            if (name == null || name.isEmpty()) {
                name = GuiText.CPUs.getLocal() + " #" + cpu.getSerial();
            }
            if (cpu.isPause()) {
                name += " [P]";
            }
            if (name.length() > 12) {
                name = name.substring(0, 11) + "..";
            }
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 3, y + 3, 0);
            GL11.glScalef(0.8f, 0.8f, 1.0f);
            font.drawString(name, 0, 0, cpu.isPause() ? pausedColor : textColor);
            GL11.glPopMatrix();

            // CPU 状态区域
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 3, y + 11, 0);
            IAEItemStack craftingStack = cpu.getCrafting();
            if (cpu.isPause()) {
                drawStatusIcon(font, 16 * 12, GuiText.Pause.getLocal(), pausedColor);
            } else if (craftingStack != null) {
                String amount = Long.toString(craftingStack.getStackSize());
                if (amount.length() > 5) {
                    amount = amount.substring(0, 5) + "..";
                }
                drawStatusIcon(font, 16 * 11 + 2, amount, 0x009000);
            } else {
                drawStatusIcon(font, 16 * 4 + 3, cpu.formatStorage(), textColor);
            }
            GL11.glPopMatrix();

            // 合成物品图标
            if (craftingStack != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x + CPU_TABLE_SLOT_WIDTH - 19, y + 3, 0);
                this.drawItem(0, 0, craftingStack.createItemStack());
                GL11.glPopMatrix();
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        // CPU 悬停提示
        StringBuilder tooltip = new StringBuilder();
        if (hoveredCpu != null) {
            buildCpuTooltip(hoveredCpu, tooltip);
        }

        if (this.cpuScrollbar != null) {
            this.cpuScrollbar.draw(this);
        }
        super.drawFG(offsetX, offsetY, mouseX, mouseY);

        if (tooltip.length() > 0) {
            this.drawTooltip(mouseX - offsetX, mouseY - offsetY, tooltip.toString());
        }
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.bindTexture("guis/craftingcpu1.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        this.bindTexture("guis/cpu_selector.png");
        this.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY, 0, 0,
                CPU_TABLE_WIDTH, CPU_TABLE_HEIGHT);
    }

    // ========== JEI 排除区域 ==========

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> area = new ArrayList<>();
        area.add(new Rectangle(this.guiLeft - CPU_TABLE_WIDTH, this.guiTop,
                CPU_TABLE_WIDTH, CPU_TABLE_HEIGHT));
        return area;
    }

    // ========== 鼠标事件 ==========

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        super.mouseClicked(xCoord, yCoord, btn);
        if (this.cpuScrollbar != null) {
            this.cpuScrollbar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }
        CraftingCPUStatus hit = hitCpu(xCoord, yCoord);
        if (hit != null) {
            try {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("Terminal.Cpu.Set", Integer.toString(hit.getSerial())));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        if (this.cpuScrollbar != null) {
            this.cpuScrollbar.click(this, x - this.guiLeft, y - this.guiTop);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        x -= this.guiLeft - CPU_TABLE_WIDTH;
        y -= this.guiTop;
        int dwheel = Mouse.getEventDWheel();
        if (x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9
                && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT) {
            if (this.cpuScrollbar != null && dwheel != 0) {
                this.cpuScrollbar.wheel(dwheel);
                return;
            }
        }
        super.handleMouseInput();
    }

    // ========== 内部方法 ==========

    private void drawStatusIcon(FontRenderer font, int iconIndex, String text, int color) {
        this.bindTexture("guis/states.png");
        int uvY = iconIndex / 16;
        int uvX = iconIndex - uvY * 16;
        GL11.glScalef(0.5f, 0.5f, 1.0f);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.drawTexturedModalRect(0, 0, uvX * 16, uvY * 16, 16, 16);
        GL11.glTranslatef(18.0f, 2.0f, 0.0f);
        GL11.glScalef(1.5f, 1.5f, 1.0f);
        font.drawString(text, 0, 0, color);
    }

    private void buildCpuTooltip(CraftingCPUStatus cpu, StringBuilder tooltip) {
        String name = cpu.getName();
        if (name != null && !name.isEmpty()) {
            tooltip.append(name);
        } else {
            tooltip.append(GuiText.CPUs.getLocal()).append(" #").append(cpu.getSerial());
        }
        if (cpu.isPause()) {
            tooltip.append(" [").append(GuiText.Pause.getLocal()).append("]");
        }
        tooltip.append('\n');

        IAEItemStack crafting = cpu.getCrafting();
        if (crafting != null && crafting.getStackSize() > 0) {
            tooltip.append(GuiText.Crafting.getLocal()).append(": ")
                    .append(crafting.getStackSize()).append(' ')
                    .append(crafting.createItemStack().getDisplayName()).append('\n')
                    .append(cpu.getRemainingItems()).append(" / ")
                    .append(cpu.getTotalItems()).append('\n');
        }
        if (cpu.getStorage() > 0) {
            tooltip.append(GuiText.Bytes.getLocal()).append(": ")
                    .append(cpu.formatStorage()).append('\n');
        }
        if (cpu.getCoprocessors() > 0) {
            tooltip.append(GuiText.CoProcessors.getLocal()).append(": ")
                    .append(cpu.getCoprocessors()).append('\n');
        }
    }

    private CraftingCPUStatus hitCpu(int x, int y) {
        x -= this.guiLeft - CPU_TABLE_WIDTH;
        y -= this.guiTop;
        if (!(x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9
                && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT)) {
            return null;
        }
        int scrollOffset = this.cpuScrollbar != null ? this.cpuScrollbar.getCurrentScroll() : 0;
        int cpuId = scrollOffset + (y - 19) / CPU_TABLE_SLOT_HEIGHT;
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        return (cpuId >= 0 && cpuId < cpus.size()) ? cpus.get(cpuId) : null;
    }

    private void updateCPUButtonText() {
        String btnText = GuiText.NoCraftingJobs.getLocal();
        if (this.status.selectedCpuSerial >= 0) {
            if (this.selectedCPUName != null && this.selectedCPUName.length() > 0) {
                final String name = this.selectedCPUName.substring(0,
                        Math.min(20, this.selectedCPUName.length()));
                btnText = GuiText.CPUs.getLocal() + ": " + name;
            } else {
                btnText = GuiText.CPUs.getLocal() + ": #" + this.status.selectedCpuSerial;
            }
        }
        if (this.status.getCPUs().isEmpty()) {
            btnText = GuiText.NoCraftingJobs.getLocal();
        }
        this.selectCPU.displayString = btnText;
    }

    @Override
    protected String getGuiDisplayName(final String in) {
        return in;
    }

    @Override
    public void postCPUUpdate(CraftingCPUStatus[] cpus) {
        this.status.postCPUUpdate(cpus);
    }
}

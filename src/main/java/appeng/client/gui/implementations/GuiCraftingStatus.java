package appeng.client.gui.implementations;

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

import appeng.api.AEApi;
import appeng.api.definitions.IDefinitions;
import appeng.api.definitions.IParts;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.parts.reporting.PartCraftingTerminal;
import appeng.parts.reporting.PartExpandedProcessingPatternTerminal;
import appeng.parts.reporting.PartPatternTerminal;
import appeng.parts.reporting.PartTerminal;

public class GuiCraftingStatus extends GuiCraftingCPU {

    private static final int CPU_TABLE_WIDTH = 94;
    private static final int CPU_TABLE_HEIGHT = 164;
    private static final int CPU_TABLE_SLOT_XOFF = 100;
    private static final int CPU_TABLE_SLOT_YOFF = 0;
    private static final int CPU_TABLE_SLOT_WIDTH = 67;
    private static final int CPU_TABLE_SLOT_HEIGHT = 23;

    private final ContainerCraftingStatus status;
    private GuiButton selectCPU;
    private GuiScrollbar cpuScrollbar;

    private GuiTabButton originalGuiBtn;
    private GuiBridge originalGui;
    private ItemStack myIcon = ItemStack.EMPTY;
    private String selectedCPUName = "";

    public GuiCraftingStatus(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftingStatus(inventoryPlayer, te));

        this.status = (ContainerCraftingStatus) this.inventorySlots;
        final Object target = this.status.getTarget();
        final IDefinitions definitions = AEApi.instance().definitions();
        final IParts parts = definitions.parts();

        if (target instanceof WirelessTerminalGuiObject) {
            myIcon = ((WirelessTerminalGuiObject) target).getItemStack();
            this.originalGui = (GuiBridge) AEApi.instance().registries().wireless().getWirelessTerminalHandler(myIcon)
                    .getGuiHandler(myIcon);
        }

        if (target instanceof PartTerminal) {
            this.myIcon = parts.terminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_ME;
        }

        if (target instanceof PartCraftingTerminal) {
            this.myIcon = parts.craftingTerminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_CRAFTING_TERMINAL;
        }

        if (target instanceof PartPatternTerminal) {
            this.myIcon = parts.patternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);

            this.originalGui = GuiBridge.GUI_PATTERN_TERMINAL;
        }

        if (target instanceof PartExpandedProcessingPatternTerminal) {
            myIcon = parts.expandedProcessingPatternTerminal().maybeStack(1).orElse(ItemStack.EMPTY);
            this.originalGui = GuiBridge.GUI_EXPANDED_PROCESSING_PATTERN_TERMINAL;
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance().sendToServer(new PacketSwitchGuis(this.originalGui));
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.selectCPU = new GuiButton(0, this.guiLeft + 8, this.guiTop + this.ySize - 25, 166, 20, GuiText.CraftingCPU
                .getLocal() + ": " + GuiText.NoCraftingCPUs);
        selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

        this.cpuScrollbar = new GuiScrollbar();
        this.cpuScrollbar.setLeft(-16);
        this.cpuScrollbar.setTop(19);
        this.cpuScrollbar.setWidth(12);
        this.cpuScrollbar.setHeight(137);

        if (!this.myIcon.isEmpty()) {
            this.buttonList.add(
                    this.originalGuiBtn = new GuiTabButton(this.guiLeft + 213, this.guiTop - 4, this.myIcon,
                            this.myIcon.getDisplayName(), this.itemRender));
            this.originalGuiBtn.setHideEdge(13);
        }
    }

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
        final int TEXT_COLOR = 0x202020;
        final int PAUSED_COLOR = 0xFFA500;

        // 绘制CPU槽位
        for (int i = firstCpu; i < firstCpu + 6 && i < cpus.size(); i++) {
            if (i < 0)
                continue;
            CraftingCPUStatus cpu = cpus.get(i);
            if (cpu == null)
                continue;

            int x = -CPU_TABLE_WIDTH + 9;
            int y = 19 + (i - firstCpu) * CPU_TABLE_SLOT_HEIGHT;

            // 绘制槽位背景
            if (cpu.getSerial() == this.status.selectedCpuSerial) {
                GL11.glColor4f(0.0F, 0.8352F, 1.0F, 1.0F);
            } else if (hoveredCpu != null && hoveredCpu.getSerial() == cpu.getSerial()) {
                GL11.glColor4f(0.65F, 0.9F, 1.0F, 1.0F);
            } else {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            }
            this.bindTexture("guis/cpu_selector.png");
            this.drawTexturedModalRect(x, y, CPU_TABLE_SLOT_XOFF, CPU_TABLE_SLOT_YOFF, CPU_TABLE_SLOT_WIDTH,
                    CPU_TABLE_SLOT_HEIGHT);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            // 绘制CPU名称
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
            font.drawString(name, 0, 0, cpu.isPause() ? PAUSED_COLOR : TEXT_COLOR);
            GL11.glPopMatrix();

            // 绘制状态区域
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 3, y + 11, 0);

            IAEItemStack craftingStack = cpu.getCrafting();
            if (cpu.isPause()) {
                // 暂停状态
                int pauseIconIndex = 16 * 12 + 0;
                this.bindTexture("guis/states.png");
                int pause_uv_y = pauseIconIndex / 16;
                int pause_uv_x = pauseIconIndex - pause_uv_y * 16;
                GL11.glScalef(0.5f, 0.5f, 1.0f);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.drawTexturedModalRect(0, 0, pause_uv_x * 16, pause_uv_y * 16, 16, 16);
                GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                GL11.glScalef(1.5f, 1.5f, 1.0f);
                font.drawString(GuiText.Pause.getLocal(), 0, 0, PAUSED_COLOR);
            } else if (craftingStack != null) {
                // 合成中状态
                int iconIndex = 16 * 11 + 2;
                this.bindTexture("guis/states.png");
                int uv_y = iconIndex / 16;
                int uv_x = iconIndex - uv_y * 16;
                GL11.glScalef(0.5f, 0.5f, 1.0f);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
                GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                GL11.glScalef(1.5f, 1.5f, 1.0f);
                String amount = Long.toString(craftingStack.getStackSize());
                if (amount.length() > 5) {
                    amount = amount.substring(0, 5) + "..";
                }
                font.drawString(amount, 0, 0, 0x009000);
            } else {
                // 空闲状态
                int iconIndex = 16 * 4 + 3;
                this.bindTexture("guis/states.png");
                int uv_y = iconIndex / 16;
                int uv_x = iconIndex - uv_y * 16;
                GL11.glScalef(0.5f, 0.5f, 1.0f);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                this.drawTexturedModalRect(0, 0, uv_x * 16, uv_y * 16, 16, 16);
                GL11.glTranslatef(18.0f, 2.0f, 0.0f);
                GL11.glScalef(1.5f, 1.5f, 1.0f);
                font.drawString(cpu.formatStorage(), 0, 0, TEXT_COLOR);
            }
            GL11.glPopMatrix();

            // 绘制合成物品图标（暂停/合成中均显示）
            if (craftingStack != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(x + CPU_TABLE_SLOT_WIDTH - 19, y + 3, 0);
                this.drawItem(0, 0, craftingStack.createItemStack());
                GL11.glPopMatrix();
            }
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        StringBuilder tooltip = new StringBuilder();
        if (hoveredCpu != null) {
            String name = hoveredCpu.getName();
            if (name != null && !name.isEmpty()) {
                tooltip.append(name);
            } else {
                tooltip.append(GuiText.CPUs.getLocal()).append(" #").append(hoveredCpu.getSerial());
            }
            if (hoveredCpu.isPause()) {
                tooltip.append(" [").append(GuiText.Pause.getLocal()).append("]");
            }
            tooltip.append('\n');

            IAEItemStack crafting = hoveredCpu.getCrafting();
            if (crafting != null && crafting.getStackSize() > 0) {
                tooltip.append(GuiText.Crafting.getLocal()).append(": ")
                        .append(crafting.getStackSize()).append(' ')
                        .append(crafting.createItemStack().getDisplayName()).append('\n')
                        .append(hoveredCpu.getRemainingItems()).append(" / ")
                        .append(hoveredCpu.getTotalItems()).append('\n');
            }
            if (hoveredCpu.getStorage() > 0) {
                tooltip.append(GuiText.Bytes.getLocal()).append(": ")
                        .append(hoveredCpu.formatStorage()).append('\n');
            }
            if (hoveredCpu.getCoprocessors() > 0) {
                tooltip.append(GuiText.CoProcessors.getLocal()).append(": ")
                        .append(hoveredCpu.getCoprocessors()).append('\n');
            }
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
        this.drawTexturedModalRect(offsetX - CPU_TABLE_WIDTH, offsetY, 0, 0, CPU_TABLE_WIDTH, CPU_TABLE_HEIGHT);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        Rectangle craftingCPUArea = new Rectangle(this.guiLeft - CPU_TABLE_WIDTH, this.guiTop, CPU_TABLE_WIDTH,
                CPU_TABLE_HEIGHT);
        List<Rectangle> area = new ArrayList<Rectangle>();
        area.add(craftingCPUArea);
        return area;
    }

    @Override
    protected void mouseClicked(int xCoord, int yCoord, int btn) throws IOException {
        super.mouseClicked(xCoord, yCoord, btn);

        if (cpuScrollbar != null) {
            cpuScrollbar.click(this, xCoord - this.guiLeft, yCoord - this.guiTop);
        }
        CraftingCPUStatus hit = hitCpu(xCoord, yCoord);
        if (hit != null) {
            try {
                NetworkHandler.instance
                        .sendToServer(new PacketValueConfig("Terminal.Cpu.Set", Integer.toString(hit.getSerial())));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int c, long d) {
        super.mouseClickMove(x, y, c, d);
        if (cpuScrollbar != null) {
            cpuScrollbar.click(this, x - this.guiLeft, y - this.guiTop);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        int dwheel = Mouse.getEventDWheel();
        if (x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT) {
            if (this.cpuScrollbar != null && dwheel != 0) {
                this.cpuScrollbar.wheel(dwheel);
                return;
            }
        }
        super.handleMouseInput();
    }

    private CraftingCPUStatus hitCpu(int x, int y) {
        x -= guiLeft - CPU_TABLE_WIDTH;
        y -= guiTop;
        if (!(x >= 9 && x < CPU_TABLE_SLOT_WIDTH + 9 && y >= 19 && y < 19 + 6 * CPU_TABLE_SLOT_HEIGHT)) {
            return null;
        }
        int scrollOffset = this.cpuScrollbar != null ? this.cpuScrollbar.getCurrentScroll() : 0;
        int cpuId = scrollOffset + (y - 19) / CPU_TABLE_SLOT_HEIGHT;
        List<CraftingCPUStatus> cpus = this.status.getCPUs();
        return (cpuId >= 0 && cpuId < cpus.size()) ? cpus.get(cpuId) : null;
    }

    private void updateCPUButtonText() {
        String btnTextText = GuiText.NoCraftingJobs.getLocal();

        if (this.status.selectedCpuSerial >= 0)// && status.selectedCpu < status.cpus.size() )
        {
            if (this.selectedCPUName != null && this.selectedCPUName.length() > 0) {
                final String name = this.selectedCPUName.substring(0, Math.min(20, this.selectedCPUName.length()));
                btnTextText = GuiText.CPUs.getLocal() + ": " + name;
            } else {
                btnTextText = GuiText.CPUs.getLocal() + ": #" + this.status.selectedCpuSerial;
            }
        }

        if (this.status.getCPUs().isEmpty()) {
            btnTextText = GuiText.NoCraftingJobs.getLocal();
        }

        this.selectCPU.displayString = btnTextText;
    }

    @Override
    protected String getGuiDisplayName(final String in) {
        return in; // the cup name is on the button
    }

    public void postCPUUpdate(CraftingCPUStatus[] cpus) {
        this.status.postCPUUpdate(cpus);
    }
}

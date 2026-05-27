package appeng.client.mui.screen;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import appeng.api.stacks.AEKey;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEStack;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.widgets.MUIScrollBar;
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

/**
 * MUI craft confirm panel.
 * <p>
 * Internal data storage uses {@link AEKey} + {@code long} amounts instead of {@link IAEStack}.
 */
@SideOnly(Side.CLIENT)
public class MUICraftConfirmPanel extends AEBasePanel
        implements ICraftConfirmGuiCallback, AEBasePanelGuiHandler.IMUIVisualListPanel {

    private static final int ROWS = 5;

    private final ContainerCraftConfirm ccc;

    private final Map<AEKey, Long> storage = new HashMap<>();
    private final Map<AEKey, Long> pending = new HashMap<>();
    private final Map<AEKey, Long> missing = new HashMap<>();
    private final Map<AEKey, Long> craftCountsByKey = new HashMap<>();

    private final List<AEKey> visual = new ArrayList<>();

    private AEGuiKey originalGui;
    private GuiButton cancel;
    private GuiButton start;
    private GuiButton selectCPU;
    private int tooltip = -1;

    public MUICraftConfirmPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(new ContainerCraftConfirm(inventoryPlayer, te));
        this.xSize = 238;
        this.ySize = 206;

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);

        this.ccc = (ContainerCraftConfirm) this.inventorySlots;
        this.ccc.setGui((ICraftConfirmGuiCallback) this);

        if (te instanceof WirelessTerminalGuiObject) {
            ItemStack itemStack = ((WirelessTerminalGuiObject) te).getItemStack();
            Object guiHandler = AEApi.instance().registries().wireless()
                    .getWirelessTerminalHandler(itemStack).getGuiHandler(itemStack);
            if (guiHandler instanceof appeng.core.sync.AEGuiKey key) {
                this.originalGui = key;
            } else if (guiHandler instanceof appeng.core.sync.GuiBridge gb) {
                this.originalGui = AEGuiKeys.fromLegacy(gb);
            }
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
        for (final IAEStack<?> l : list) {
            final AEKey key = l.toAEKey();
            if (key == null) continue;

            final long amount = l.getStackSize();
            final long craftRounds = ref == 1 ? l.getCountRequestableCrafts() : 0;

            final Map<AEKey, Long> target = switch (ref) {
                case 0 -> this.storage;
                case 1 -> this.pending;
                case 2 -> this.missing;
                default -> null;
            };

            if (target != null) {
                this.handleInput(target, key, amount, craftRounds);
            }
        }

        final Set<AEKey> seen = new HashSet<>();
        for (final IAEStack<?> l : list) {
            final AEKey key = l.toAEKey();
            if (key == null || !seen.add(key)) continue;

            final long amt = this.getTotal(key);
            if (amt <= 0) {
                this.deleteVisualStack(key);
            } else {
                final int idx = this.findVisualIndex(key);
                final AEKey entry = this.visual.get(idx);
                // entry is already the right key; amounts are computed at render time
            }
        }

        this.updateScrollBar();
    }

    // ========== Initialization ==========

    @Override
    protected void setupWidgets() {
        this.start = new GuiButton(0, this.guiLeft + 162, this.guiTop + this.ySize - 25, 50, 20,
                GuiText.Start.getLocal());
        this.start.enabled = false;
        this.buttonList.add(this.start);

        this.selectCPU = new GuiButton(0, this.guiLeft + (219 - 180) / 2, this.guiTop + this.ySize - 68, 180, 20,
                GuiText.CraftingCPU.getLocal() + ": " + GuiText.Automatic);
        this.selectCPU.enabled = false;
        this.buttonList.add(this.selectCPU);

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

    // ========== Rendering ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.updateCPUButtonText();

        this.start.enabled = !(this.ccc.hasNoCPU() || this.isSimulation());
        this.selectCPU.enabled = !this.isSimulation();

        final int gx = (this.width - this.xSize) / 2;
        final int gy = (this.height - this.ySize) / 2;

        this.tooltip = -1;

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
        final long BytesUsed = this.ccc.getUsedBytes();
        final String byteUsed = NumberFormat.getInstance().format(BytesUsed);
        final String Add = BytesUsed > 0 ? (byteUsed + ' ' + GuiText.BytesUsed.getLocal())
                : GuiText.CalculatingWait.getLocal();
        this.fontRenderer.drawString(GuiText.CraftingPlan.getLocal() + " - " + Add, 8, 7, AEMUITheme.COLOR_TITLE);

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
        this.fontRenderer.drawString(dsp, offset, 165, AEMUITheme.COLOR_TITLE);

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
            final AEKey key = this.visual.get(z);
            if (key != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final long stored = this.storage.getOrDefault(key, 0L);
                final long pendingAmt = this.pending.getOrDefault(key, 0L);
                final long missingAmt = this.missing.getOrDefault(key, 0L);

                int lines = 0;
                if (stored > 0) lines++;
                if (missingAmt > 0) lines++;
                if (pendingAmt > 0) {
                    lines++;
                    if (this.craftCountsByKey.getOrDefault(key, 0L) > 0) {
                        lines++;
                    }
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored > 0) {
                    String str = Long.toString(stored);
                    if (stored >= 10000) str = Long.toString(stored / 1000) + 'k';
                    if (stored >= 10000000) str = Long.toString(stored / 1000000) + 'm';

                    str = GuiText.FromStorage.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, AEMUITheme.COLOR_TITLE);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.FromStorage.getLocal() + ": " + stored);
                    }

                    downY += 5;
                }

                boolean red = false;
                if (missingAmt > 0) {
                    String str = Long.toString(missingAmt);
                    if (missingAmt >= 10000) str = Long.toString(missingAmt / 1000) + 'k';
                    if (missingAmt >= 10000000) str = Long.toString(missingAmt / 1000000) + 'm';

                    str = GuiText.Missing.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, AEMUITheme.COLOR_TITLE);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Missing.getLocal() + ": " + missingAmt);
                    }

                    red = true;
                    downY += 5;
                }

                if (pendingAmt > 0) {
                    String str = Long.toString(pendingAmt);
                    if (pendingAmt >= 10000) str = Long.toString(pendingAmt / 1000) + 'k';
                    if (pendingAmt >= 10000000) str = Long.toString(pendingAmt / 1000000) + 'm';

                    str = GuiText.ToCraft.getLocal() + ": " + str;
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (w * 0.5)) * 2),
                            (y * offY + yo + 6 - negY + downY) * 2, AEMUITheme.COLOR_TITLE);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.ToCraft.getLocal() + ": " + pendingAmt);
                    }

                    downY += 5;

                    long craftRounds = this.craftCountsByKey.getOrDefault(key, 0L);
                    if (craftRounds > 0) {
                        String roundsStr = String.format("%s: %d", GuiText.PatternExecutionCount.getLocal(),
                                craftRounds);
                        final int wRounds = 4 + this.fontRenderer.getStringWidth(roundsStr);
                        this.fontRenderer.drawString(roundsStr,
                                (int) ((x * (1 + sectionLength) + xo + sectionLength - 19 - (wRounds * 0.5)) * 2),
                                (y * offY + yo + 6 - negY + downY) * 2, AEMUITheme.COLOR_TITLE);
                        downY += 5;
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + sectionLength) + xo + sectionLength - 19;
                final int posY = y * offY + yo;

                final ItemStack is = key.asItemStackRepresentation();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = key.getDisplayName();

                    long rounds = this.craftCountsByKey.getOrDefault(key, 0L);
                    if (rounds > 0) {
                        lineList.add(String.format("%s: %d",
                                GuiText.PatternExecutionCount.getLocal(),
                                rounds));
                    }

                    if (lineList.size() > 0) {
                        dspToolTip = dspToolTip + '\n' + Joiner.on("\n").join(lineList);
                    }

                    toolPosX = x * (1 + sectionLength) + xo + sectionLength - 8;
                    toolPosY = y * offY + yo;
                }

                this.drawItem(posX, posY, is);

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

    // ========== Input events ==========

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

    // ========== Data processing ==========

    private void handleInput(final Map<AEKey, Long> counter, final AEKey key, final long amount, final long craftRounds) {
        if (amount <= 0) {
            counter.remove(key);
            this.craftCountsByKey.remove(key);
        } else {
            counter.put(key, amount);
            if (craftRounds > 0) {
                this.craftCountsByKey.put(key, craftRounds);
            }
        }
    }

    private long getTotal(final AEKey key) {
        long total = 0;
        total += this.storage.getOrDefault(key, 0L);
        total += this.pending.getOrDefault(key, 0L);
        total += this.missing.getOrDefault(key, 0L);
        return total;
    }

    private void deleteVisualStack(final AEKey key) {
        this.visual.removeIf(k -> k.equals(key));
    }

    private int findVisualIndex(final AEKey key) {
        for (int i = 0; i < this.visual.size(); i++) {
            if (this.visual.get(i).equals(key)) {
                return i;
            }
        }
        this.visual.add(key);
        return this.visual.size() - 1;
    }

    // ========== Public accessors ==========

    @Override
    public List<AEKey> getVisual() {
        return visual;
    }

    @Override
    public int getDisplayedRows() {
        return ROWS;
    }
}

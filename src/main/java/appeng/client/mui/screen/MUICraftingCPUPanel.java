package appeng.client.mui.screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import appeng.api.stacks.AEKey;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEColor;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEBasePanelGuiHandler;
import appeng.client.mui.widgets.IMUISortSource;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.interfaces.ICraftingCPUGuiCallback;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

/**
 * MUI crafting CPU status panel.
 * <p>
 * Internal data uses {@link AEKey} + {@code long} amounts instead of {@link IAEStack}.
 */
@SideOnly(Side.CLIENT)
public class MUICraftingCPUPanel extends AEBasePanel
        implements IMUISortSource, ICraftingCPUGuiCallback, AEBasePanelGuiHandler.IMUIVisualListPanel {

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

    private final ContainerCraftingCPU craftingCpu;

    private final Map<AEKey, Long> storage = new HashMap<>();
    private final Map<AEKey, Long> active = new HashMap<>();
    private final Map<AEKey, Long> pending = new HashMap<>();

    private final List<AEKey> visual = new ArrayList<>();

    private GuiButton cancel;
    private GuiButton switchButton;
    private GuiButton trackButton;
    private int tooltip = -1;

    public MUICraftingCPUPanel(final InventoryPlayer inventoryPlayer, final Object te) {
        this(new ContainerCraftingCPU(inventoryPlayer, te));
    }

    protected MUICraftingCPUPanel(final ContainerCraftingCPU container) {
        super(container);
        this.craftingCpu = container;
        this.craftingCpu.setGui((ICraftingCPUGuiCallback) this);
        this.ySize = GUI_HEIGHT;
        this.xSize = GUI_WIDTH;

        final MUIScrollBar scrollbar = new MUIScrollBar();
        this.setScrollBar(scrollbar);
    }

    @Override
    public void clearItems() {
        this.storage.clear();
        this.active.clear();
        this.pending.clear();
        this.visual.clear();
    }

    @Override
    public void postGenericUpdate(final List<IAEStack<?>> list, final byte ref) {
        for (final IAEStack<?> l : list) {
            final AEKey key = l.toAEKey();
            if (key == null) continue;

            final long amount = l.getStackSize();

            final Map<AEKey, Long> target = switch (ref) {
                case 0 -> this.storage;
                case 1 -> this.active;
                case 2 -> this.pending;
                default -> null;
            };

            if (target != null) {
                this.handleInput(target, key, amount);
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
                this.findVisualIndex(key);
            }
        }

        this.updateScrollBar();
    }

    @Override
    protected void setupWidgets() {
        this.updateScrollBar();

        this.trackButton = new GuiButton(2,
                this.guiLeft + TRACK_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                TRACK_WIDTH, CANCEL_HEIGHT,
                GuiText.Track.getLocal());
        this.buttonList.add(this.trackButton);

        this.switchButton = new GuiButton(1,
                this.guiLeft + SWITCH_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                SWITCH_WIDTH, CANCEL_HEIGHT,
                GuiText.Resume.getLocal() + "/" + GuiText.Pause.getLocal());
        this.buttonList.add(this.switchButton);

        this.cancel = new GuiButton(0,
                this.guiLeft + CANCEL_LEFT_OFFSET,
                this.guiTop + this.ySize - CANCEL_TOP_OFFSET,
                CANCEL_WIDTH, CANCEL_HEIGHT,
                GuiText.Cancel.getLocal());
        this.buttonList.add(this.cancel);
    }

    private void updateScrollBar() {
        final int size = this.visual.size();
        this.getScrollBar().setTop(SCROLLBAR_TOP).setLeft(SCROLLBAR_LEFT).setHeight(SCROLLBAR_HEIGHT);
        this.getScrollBar().setRange(0, (size + 2) / 3 - DISPLAYED_ROWS, 1);
    }

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

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float btn) {
        this.cancel.enabled = !this.visual.isEmpty();
        this.trackButton.enabled = !this.visual.isEmpty();
        this.switchButton.enabled = true;

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

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        String title = this.getGuiDisplayName(GuiText.CraftingStatus.getLocal());

        if (this.craftingCpu.getEstimatedTime() > 0 && !this.visual.isEmpty()) {
            final long etaInMilliseconds = TimeUnit.MILLISECONDS.convert(this.craftingCpu.getEstimatedTime(),
                    TimeUnit.NANOSECONDS);
            final String etaTimeText = DurationFormatUtils.formatDuration(etaInMilliseconds,
                    GuiText.ETAFormat.getLocal());
            title += " - " + etaTimeText;
        }

        this.fontRenderer.drawString(title, TITLE_LEFT_OFFSET, TITLE_TOP_OFFSET, TEXT_COLOR);

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
            final AEKey key = this.visual.get(z);
            if (key != null) {
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.5, 0.5, 0.5);

                final long stored = this.storage.getOrDefault(key, 0L);
                final long activeAmt = this.active.getOrDefault(key, 0L);
                final long pendingAmt = this.pending.getOrDefault(key, 0L);

                int lines = 0;
                if (stored > 0) lines++;
                boolean isActive = false;
                if (activeAmt > 0) {
                    lines++;
                    isActive = true;
                }
                boolean scheduled = false;
                if (pendingAmt > 0) {
                    lines++;
                    scheduled = true;
                }

                if (AEConfig.instance().isUseColoredCraftingStatus() && (isActive || scheduled)) {
                    final int bgColor = (isActive ? AEColor.GREEN.blackVariant : AEColor.YELLOW.blackVariant)
                            | BACKGROUND_ALPHA;
                    final int startX = (x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET) * 2;
                    final int startY = ((y * offY + ITEMSTACK_TOP_OFFSET) - 3) * 2;
                    drawRect(startX, startY, startX + (SECTION_LENGTH * 2), startY + (offY * 2) - 2, bgColor);
                }

                final int negY = ((lines - 1) * 5) / 2;
                int downY = 0;

                if (stored > 0) {
                    final String str = GuiText.Stored.getLocal() + ": "
                            + converter.toWideReadableForm(stored);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Stored.getLocal() + ": " + stored);
                    }

                    downY += 5;
                }

                if (activeAmt > 0) {
                    final String str = GuiText.Crafting.getLocal() + ": "
                            + converter.toWideReadableForm(activeAmt);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Crafting.getLocal() + ": " + activeAmt);
                    }

                    downY += 5;
                }

                if (pendingAmt > 0) {
                    final String str = GuiText.Scheduled.getLocal() + ": "
                            + converter.toWideReadableForm(pendingAmt);
                    final int w = 4 + this.fontRenderer.getStringWidth(str);
                    this.fontRenderer.drawString(str,
                            (int) ((x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19 - (w * 0.5))
                                    * 2),
                            (y * offY + ITEMSTACK_TOP_OFFSET + 6 - negY + downY) * 2, TEXT_COLOR);

                    if (this.tooltip == z - viewStart) {
                        lineList.add(GuiText.Scheduled.getLocal() + ": " + pendingAmt);
                    }
                }

                GlStateManager.popMatrix();
                final int posX = x * (1 + SECTION_LENGTH) + ITEMSTACK_LEFT_OFFSET + SECTION_LENGTH - 19;
                final int posY = y * offY + ITEMSTACK_TOP_OFFSET;

                final ItemStack is = key.asItemStackRepresentation();

                if (this.tooltip == z - viewStart) {
                    dspToolTip = key.getDisplayName();

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

    // ========== Data processing ==========

    private void handleInput(final Map<AEKey, Long> counter, final AEKey key, final long amount) {
        if (amount <= 0) {
            counter.remove(key);
        } else {
            counter.put(key, amount);
        }
    }

    private long getTotal(final AEKey key) {
        long total = 0;
        total += this.storage.getOrDefault(key, 0L);
        total += this.active.getOrDefault(key, 0L);
        total += this.pending.getOrDefault(key, 0L);
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

    // ========== Public accessors ==========

    @Override
    public List<AEKey> getVisual() {
        return visual;
    }

    @Override
    public int getDisplayedRows() {
        return DISPLAYED_ROWS;
    }
}

package appeng.client.mui.screen;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.stacks.AEKeyType;
import appeng.api.storage.ITerminalHost;
import appeng.client.mui.slot.VirtualMEPatternSlot;
import appeng.client.mui.slot.VirtualMEPhantomSlot;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.module.PatternTerminalModule;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.container.implementations.ContainerExpandedProcessingPatternTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.helpers.PatternHelper;
import appeng.tile.inventory.IAEStackInventory;

/**
 * MUI expanded processing pattern terminal - processing-only with 4-column input grid.
 * <p>
 * Button/JEI ghost management is delegated to {@link PatternTerminalModule}.
 */
@SideOnly(Side.CLIENT)
public class MUIExpandedProcessingPatternTermPanel extends MUIMEMonitorablePanel implements IJEIGhostIngredients, PatternTerminalModule.Host {

    // ========== Constants ==========

    private static final String BACKGROUND_EXPANDED_PROCESSING_MODE = "guis/pattern_processing_expanded.png";
    private static final int PROCESSING_INPUT_OFFSET_X = 5;
    private static final int PROCESSING_INPUT_OFFSET_Y = -88;
    private static final int PROCESSING_INPUT_SCROLLBAR_OFFSET_X = PROCESSING_INPUT_OFFSET_X + 4 * 18 + 4;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = -76;
    private static final int PROCESSING_INPUT_ROWS = 4;

    // ========== Data ==========

    private VirtualMEPatternSlot[] craftingVSlots;
    private VirtualMEPatternSlot[] outputVSlots;
    private final ContainerPatternEncoder container;
    private final MUIScrollBar processingInputScrollbar = new MUIScrollBar();
    private int processingInputPage = 0;

    private final PatternTerminalModule module = new PatternTerminalModule(this);

    // ========== Constructors ==========

    public MUIExpandedProcessingPatternTermPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerExpandedProcessingPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternEncoder) this.inventorySlots;
        this.setReservedSpace(81);
    }

    // ========== PatternTerminalModule.Host ==========

    @Override
    public ContainerPatternEncoder getEncoder() {
        return container;
    }

    @Override
    public int getGuiLeft() {
        return guiLeft;
    }

    @Override
    public int getGuiTop() {
        return guiTop;
    }

    @Override
    public int getYSize() {
        return ySize;
    }

    @Override
    public AEBasePanel getPanel() {
        return this;
    }

    @Override
    public RenderItem getItemRenderer() {
        return itemRender;
    }

    @Override
    public void addModuleWidget(IMUIWidget widget) {
        this.addWidget(widget);
    }

    @Override
    public int getMultiplyButtonX() {
        return 131;
    }

    @Override
    protected void actionPerformed(final net.minecraft.client.gui.GuiButton btn) throws IOException {
        super.actionPerformed(btn);
    }

    // ========== Initialization ==========

    @Override
    public void initGui() {
        super.initGui();
        this.module.initButtons();
        this.initVirtualSlots();
    }

    // ========== Rendering ==========

    @Override
    protected void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.module.drawFG(false, false, isShiftKeyDown());
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8,
                this.ySize - 96 + 2 - this.getReservedSpace(), AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected String getBackground() {
        return BACKGROUND_EXPANDED_PROCESSING_MODE;
    }

    @Override
    protected void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        if (this.getTotalProcessingInputPages() > 1) {
            this.updateProcessingInputScrollbar();
            GlStateManager.pushMatrix();
            GlStateManager.translate(offsetX, offsetY, 0);
            this.processingInputScrollbar.draw(this);
            GlStateManager.popMatrix();
        }
    }

    @Override
    protected void repositionSlot(final AppEngSlot s) {
        final int offsetPlayerSide = s.isPlayerSide() ? 5 : 3;
        s.yPos = s.getY() + this.ySize - 78 - offsetPlayerSide;
    }

    // ========== JEI ghost drag ==========

    @Override
    public List<Target<?>> getPhantomTargets(final Object ingredient) {
        return this.module.createPhantomTargets(ingredient, this.craftingVSlots, this.outputVSlots);
    }

    @Override
    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return this.module.getFakeSlotTargetMap();
    }

    // ========== Virtual slot management ==========

    private void initVirtualSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPatternSlot);
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();

        if (craftInv != null) {
            final int pageStart = this.processingInputPage * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
            final int pageEnd = Math.min(craftInv.getSizeInventory(),
                    pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
            this.craftingVSlots = new VirtualMEPatternSlot[Math.max(0, pageEnd - pageStart)];
            for (int i = pageStart; i < pageEnd; i++) {
                final int visibleIndex = i - pageStart;
                final int x = (visibleIndex % 4) * 18;
                final int y = (visibleIndex / 4) * 18;
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        i, PROCESSING_INPUT_OFFSET_X + x, this.patternGuiY(PROCESSING_INPUT_OFFSET_Y + y),
                        craftInv, i, this::acceptType);
                this.craftingVSlots[visibleIndex] = slot;
                this.guiSlots.add(slot);
            }
        }

        if (outInv != null) {
            this.outputVSlots = new VirtualMEPatternSlot[outInv.getSizeInventory()];
            for (int i = 0; i < outInv.getSizeInventory(); i++) {
                final int x = (i % 2) * 18;
                final int y = (i / 2) * 18;
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        i, PROCESSING_OUTPUT_OFFSET_X + x, this.patternGuiY(PROCESSING_OUTPUT_OFFSET_Y + y),
                        outInv, i, this::acceptType);
                this.outputVSlots[i] = slot;
                this.guiSlots.add(slot);
            }
        }
    }

    private boolean acceptType(final VirtualMEPhantomSlot slot, final AEKeyType type, final int mouseButton) {
        return true;
    }

    private int patternGuiY(final int y) {
        return y + this.ySize - 81;
    }

    // ========== Input events ==========

    @Override
    protected void mouseClicked(final int xCoord, final int yCoord, final int btn) throws IOException {
        if (btn == 0 && this.updateProcessingInputScrollFromMouse(xCoord, yCoord)) {
            return;
        }
        super.mouseClicked(xCoord, yCoord, btn);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && this.updateProcessingInputScrollFromMouse(mouseX, mouseY)) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        if (this.isMouseOverProcessingInputArea(x, y) && this.getTotalProcessingInputPages() > 1) {
            final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
            this.processingInputScrollbar.wheel(wheel);
            if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
                this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
                return;
            }
        }
        super.mouseWheelEvent(x, y, wheel);
    }

    // ========== Input scrollbar ==========

    private int getTotalProcessingInputPages() {
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        if (craftInv == null) {
            return 1;
        }
        return Math.max(1, (craftInv.getSizeInventory() + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS - 1)
                / PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
    }

    private void updateProcessingInputScrollbar() {
        this.processingInputPage = Math.min(this.processingInputPage, this.getTotalProcessingInputPages() - 1);
        this.processingInputScrollbar
                .setLeft(PROCESSING_INPUT_SCROLLBAR_OFFSET_X)
                .setTop(this.patternGuiY(PROCESSING_INPUT_OFFSET_Y))
                .setHeight(PROCESSING_INPUT_ROWS * 18 - 2);
        this.processingInputScrollbar.setRange(0, Math.max(0, this.getTotalProcessingInputPages() - 1), 1);
        this.processingInputScrollbar.setCurrentScroll(this.processingInputPage);
    }

    private boolean updateProcessingInputScrollFromMouse(final int mouseX, final int mouseY) {
        if (this.getTotalProcessingInputPages() <= 1) {
            return false;
        }

        final int oldScroll = this.processingInputScrollbar.getCurrentScroll();
        this.processingInputScrollbar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        if (oldScroll != this.processingInputScrollbar.getCurrentScroll()) {
            this.setProcessingInputPage(this.processingInputScrollbar.getCurrentScroll());
            return true;
        }
        return false;
    }

    private void setProcessingInputPage(final int page) {
        final int clampedPage = Math.max(0, Math.min(page, this.getTotalProcessingInputPages() - 1));
        if (this.processingInputPage != clampedPage) {
            this.processingInputPage = clampedPage;
            this.initVirtualSlots();
        } else {
            this.processingInputPage = clampedPage;
        }
    }

    private boolean isMouseOverProcessingInputArea(final int mouseX, final int mouseY) {
        final int left = this.guiLeft + PROCESSING_INPUT_OFFSET_X;
        final int top = this.guiTop + this.patternGuiY(PROCESSING_INPUT_OFFSET_Y);
        final int right = this.guiLeft + PROCESSING_INPUT_SCROLLBAR_OFFSET_X
                + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }
}

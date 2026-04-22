package appeng.client.gui.implementations;

// ========================================================================
// [MUI Migration] 此旧 GUI 类已被 MUI 面板完全替代，运行时不再被实例化。
// 全部 GUI 创建已通过 AEMUIRegistration 中注册的 MUI 工厂完成。
// 如需恢复，取消下方块注释即可。
// ========================================================================
/*


import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerExpandedProcessingPatternTerm;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.PatternHelper;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

public class GuiExpandedProcessingPatternTerm extends GuiMEMonitorable implements IJEIGhostIngredients {
    private static final String BACKGROUND_EXPANDED_PROCESSING_MODE = "guis/pattern_processing_expanded.png";
    private static final int PROCESSING_INPUT_OFFSET_X = 5;
    private static final int PROCESSING_INPUT_OFFSET_Y = -88;
    private static final int PROCESSING_INPUT_SCROLLBAR_OFFSET_X = PROCESSING_INPUT_OFFSET_X + 4 * 18 + 4;
    private static final int PROCESSING_OUTPUT_OFFSET_X = 96;
    private static final int PROCESSING_OUTPUT_OFFSET_Y = -76;
    private static final int PROCESSING_INPUT_ROWS = 4;

    private static final String SUBSITUTION_DISABLE = "0";
    private static final String SUBSITUTION_ENABLE = "1";

    private static final String CRAFTMODE_CRFTING = "1";
    private static final String CRAFTMODE_PROCESSING = "0";

    private GuiTabButton tabCraftButton;
    private GuiTabButton tabProcessButton;
    private GuiImgButton substitutionsEnabledBtn;
    private GuiImgButton substitutionsDisabledBtn;
    private GuiImgButton encodeBtn;
    private GuiImgButton clearBtn;
    private GuiImgButton x2Btn;
    private GuiImgButton x3Btn;
    private GuiImgButton plusOneBtn;
    private GuiImgButton divTwoBtn;
    private GuiImgButton divThreeBtn;
    private GuiImgButton minusOneBtn;
    private GuiImgButton maxCountBtn;
    public Map<IGhostIngredientHandler.Target<?>, Object> mapTargetSlot = new HashMap<>();

    private VirtualMEPatternSlot[] craftingVSlots;
    private VirtualMEPatternSlot[] outputVSlots;
    private final ContainerPatternEncoder container;
    private final GuiScrollbar processingInputScrollbar = new GuiScrollbar();
    private int processingInputPage = 0;

    public GuiExpandedProcessingPatternTerm(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerExpandedProcessingPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternEncoder) this.inventorySlots;
        this.setReservedSpace(81);
    }

    @Override
    protected void actionPerformed(final GuiButton btn) throws java.io.IOException {
        super.actionPerformed(btn);

        try {

            if (this.tabCraftButton == btn || this.tabProcessButton == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.CraftMode",
                        this.tabProcessButton == btn ? CRAFTMODE_CRFTING : CRAFTMODE_PROCESSING));
            }

            if (this.encodeBtn == btn) {
                if (isShiftKeyDown()) {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "2"));
                } else {
                    NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", "1"));
                }
            }

            if (this.clearBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
            }

            if (this.x2Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DivideByTwo" : "PatternTerminal.MultiplyByTwo", "1"));
            }

            if (this.x3Btn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DivideByThree" : "PatternTerminal.MultiplyByThree", "1"));
            }

            if (this.plusOneBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig(
                        isShiftKeyDown() ? "PatternTerminal.DecreaseByOne" : "PatternTerminal.IncreaseByOne", "1"));
            }

            if (this.maxCountBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.MaximizeCount", "1"));
            }

            if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Substitute",
                        this.substitutionsEnabledBtn == btn ? SUBSITUTION_DISABLE : SUBSITUTION_ENABLE));
            }
        } catch (final IOException e) {
            AELog.error(e);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.tabCraftButton = new GuiTabButton(this.guiLeft + 173, this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.CRAFTING_TABLE), GuiText.CraftingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(this.guiLeft + 173, this.guiTop + this.ySize - 177,
                new ItemStack(Blocks.FURNACE), GuiText.ProcessingPattern.getLocal(), this.itemRender);
        this.buttonList.add(this.tabProcessButton);

        this.substitutionsEnabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163,
                Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(this.guiLeft + 84, this.guiTop + this.ySize - 163,
                Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(this.guiLeft + 74, this.guiTop + this.ySize - 163, Settings.ACTIONS,
                ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.buttonList.add(this.clearBtn);

        this.x3Btn = new GuiImgButton(this.guiLeft + 131, this.guiTop + this.ySize - 158, Settings.ACTIONS,
                ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(this.guiLeft + 131, this.guiTop + this.ySize - 148, Settings.ACTIONS,
                ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(this.guiLeft + 131, this.guiTop + this.ySize - 138, Settings.ACTIONS,
                ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(this.guiLeft + 87, this.guiTop + this.ySize - 158, Settings.ACTIONS,
                ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.divThreeBtn.visible = false;
        this.divThreeBtn.enabled = false;
        this.buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(this.guiLeft + 87, this.guiTop + this.ySize - 148, Settings.ACTIONS,
                ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.divTwoBtn.visible = false;
        this.divTwoBtn.enabled = false;
        this.buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(this.guiLeft + 87, this.guiTop + this.ySize - 138, Settings.ACTIONS,
                ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.minusOneBtn.visible = false;
        this.minusOneBtn.enabled = false;
        this.buttonList.add(this.minusOneBtn);

        // this.maxCountBtn = new GuiImgButton( this.guiLeft + 128, this.guiTop + this.ySize - 108, Settings.ACTIONS,
        // ActionItems.MAX_COUNT );
        // this.maxCountBtn.setHalfSize( true );
        // this.buttonList.add( this.maxCountBtn );

        this.encodeBtn = new GuiImgButton(this.guiLeft + 147, this.guiTop + this.ySize - 142, Settings.ACTIONS,
                ActionItems.ENCODE);
        this.buttonList.add(this.encodeBtn);

        this.initVirtualSlots();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.tabCraftButton.visible = false;
        this.tabProcessButton.visible = true;
        this.substitutionsEnabledBtn.visible = false;
        this.substitutionsDisabledBtn.visible = false;
        this.x2Btn.visible = true;
        this.x3Btn.visible = true;
        this.x2Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
        this.x3Btn.set(isShiftKeyDown() ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
        this.divTwoBtn.visible = false;
        this.divThreeBtn.visible = false;
        this.plusOneBtn.visible = true;
        this.plusOneBtn.set(isShiftKeyDown() ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
        this.minusOneBtn.visible = false;
        // this.maxCountBtn.visible = true;

        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8,
                this.ySize - 96 + 2 - this.getReservedSpace(), 4210752);
    }

    @Override
    protected String getBackground() {
        return BACKGROUND_EXPANDED_PROCESSING_MODE;
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
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

    @Override
    public List<IGhostIngredientHandler.Target<?>> getPhantomTargets(Object ingredient) {
        final ItemStack itemIngredient = ingredient instanceof ItemStack ? (ItemStack) ingredient : ItemStack.EMPTY;
        final IAEStack<?> aeIngredient = ingredient instanceof FluidStack fluidStack
                ? AEFluidStack.fromFluidStack(fluidStack.copy())
                : null;

        if (itemIngredient.isEmpty() && aeIngredient == null) {
            return Collections.emptyList();
        }
        this.mapTargetSlot.clear();
        List<IGhostIngredientHandler.Target<?>> targets = new ArrayList<>();
        this.addVirtualTargets(targets, this.craftingVSlots, itemIngredient, aeIngredient);
        this.addVirtualTargets(targets, this.outputVSlots, itemIngredient, aeIngredient);
        return targets;
    }

    private void addVirtualTargets(List<IGhostIngredientHandler.Target<?>> targets, VirtualMEPatternSlot[] slots,
            ItemStack itemIngredient, IAEStack<?> aeIngredient) {
        if (slots == null) {
            return;
        }

        for (VirtualMEPatternSlot slot : slots) {
            if (slot == null || !slot.isVisible() || !slot.isSlotEnabled()) {
                continue;
            }

            final IGhostIngredientHandler.Target<Object> target = new IGhostIngredientHandler.Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(getGuiLeft() + slot.xPos(), getGuiTop() + slot.yPos(), 16, 16);
                }

                @Override
                public void accept(Object ignored) {
                    if (aeIngredient != null) {
                        final IAEStackInventory targetInv = slot.getStorageName() == StorageName.CRAFTING_OUTPUT
                                ? container.getOutputAEInv()
                                : container.getCraftingAEInv();
                        if (targetInv != null) {
                            targetInv.putAEStackInSlot(slot.getSlotIndex(), aeIngredient.copy());
                        }
                        NetworkHandler.instance().sendToServer(
                                new PacketVirtualSlot(slot.getStorageName(), slot.getSlotIndex(), aeIngredient));
                    } else {
                        slot.handleMouseClicked(itemIngredient, false, 0);
                    }
                }
            };

            targets.add(target);
            this.mapTargetSlot.putIfAbsent(target, slot);
        }
    }

    @Override
    public Map<IGhostIngredientHandler.Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }

    // ---- 虚拟槽位初始化 ----

    private void initVirtualSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPatternSlot);
        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();

        if (craftInv != null) {
            final int pageStart = this.processingInputPage * PatternHelper.PROCESSING_INPUT_PAGE_SLOTS;
            final int pageEnd = Math.min(craftInv.getSizeInventory(), pageStart + PatternHelper.PROCESSING_INPUT_PAGE_SLOTS);
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

    private boolean acceptType(VirtualMEPhantomSlot slot, IAEStackType<?> type, int mouseButton) {
        // 扩展处理样板终端始终为处理模式，接受所有类型
        return true;
    }

    private int patternGuiY(final int y) {
        return y + this.ySize - 81;
    }

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
        final int right = this.guiLeft + PROCESSING_INPUT_SCROLLBAR_OFFSET_X + this.processingInputScrollbar.getWidth();
        final int bottom = top + PROCESSING_INPUT_ROWS * 18;
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }
}

*/
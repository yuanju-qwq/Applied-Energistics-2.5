package appeng.client.mui.screen;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.storage.ITerminalHost;
import appeng.api.storage.data.IAEStackType;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.module.PatternTerminalModule;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerWirelessPatternTerminal;
import appeng.container.interfaces.IJEIGhostIngredients;
import appeng.container.slot.AppEngSlot;
import appeng.core.localization.GuiText;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEItemStackType;

/**
 * MUI pattern terminal with crafting/processing dual mode.
 * <p>
 * Button/JEI ghost management is delegated to {@link PatternTerminalModule}.
 */
@SideOnly(Side.CLIENT)
public class MUIPatternTermPanel extends MUIMEMonitorablePanel implements IJEIGhostIngredients, PatternTerminalModule.Host {

    // ========== Constants ==========

    private static final String BACKGROUND_CRAFTING_MODE = "guis/pattern.png";
    private static final String BACKGROUND_PROCESSING_MODE = "guis/pattern2.png";

    // ========== Data ==========

    protected final ContainerPatternEncoder container;

    private final PatternTerminalModule module = new PatternTerminalModule(this);

    protected VirtualMEPatternSlot[] craftingVSlots;
    protected VirtualMEPatternSlot[] outputVSlots;
    protected Boolean lastCraftingMode;

    // ========== Constructors ==========

    public MUIPatternTermPanel(final InventoryPlayer inventoryPlayer, final ITerminalHost te) {
        super(inventoryPlayer, te, new ContainerPatternTerm(inventoryPlayer, te));
        this.container = (ContainerPatternTerm) this.inventorySlots;
        this.setReservedSpace(81);
    }

    public MUIPatternTermPanel(final InventoryPlayer inventoryPlayer, WirelessTerminalGuiObject te,
            final ContainerWirelessPatternTerminal wpt) {
        super(inventoryPlayer, te, wpt);
        this.container = (ContainerWirelessPatternTerminal) this.inventorySlots;
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
    public List<GuiButton> getButtonList() {
        return buttonList;
    }

    @Override
    public int getMultiplyButtonX() {
        return 128;
    }

    @Override
    public int getDivideButtonX() {
        return 100;
    }

    // ========== Button events ==========

    @Override
    protected void actionPerformed(final GuiButton btn) throws IOException {
        super.actionPerformed(btn);
        this.module.actionPerformed(btn);
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
        this.refreshVirtualSlots();
        this.module.drawFG(this.container.isCraftingMode(), true, isShiftKeyDown());
        super.drawFG(offsetX, offsetY, mouseX, mouseY);
        this.fontRenderer.drawString(GuiText.PatternTerminal.getLocal(), 8,
                this.ySize - 96 + 2 - this.getReservedSpace(), AEMUITheme.COLOR_TITLE);
    }

    @Override
    protected String getBackground() {
        if (this.container.isCraftingMode()) {
            return BACKGROUND_CRAFTING_MODE;
        }
        return BACKGROUND_PROCESSING_MODE;
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

    protected void initVirtualSlots() {
        this.guiSlots.removeIf(slot -> slot instanceof VirtualMEPatternSlot);

        final IAEStackInventory craftInv = this.container.getCraftingAEInv();
        final IAEStackInventory outInv = this.container.getOutputAEInv();

        if (craftInv != null) {
            this.craftingVSlots = new VirtualMEPatternSlot[craftInv.getSizeInventory()];
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    final int slotIdx = x + y * 3;
                    VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                            slotIdx, 18 + x * 18, this.patternGuiY(-76 + y * 18),
                            craftInv, slotIdx, this::acceptType);
                    this.craftingVSlots[slotIdx] = slot;
                    this.guiSlots.add(slot);
                }
            }
        }

        if (outInv != null) {
            this.outputVSlots = new VirtualMEPatternSlot[outInv.getSizeInventory()];
            for (int y = 0; y < outInv.getSizeInventory(); y++) {
                VirtualMEPatternSlot slot = new VirtualMEPatternSlot(
                        y, 110, this.patternGuiY(-76 + y * 18),
                        outInv, y, this::acceptType);
                this.outputVSlots[y] = slot;
                this.guiSlots.add(slot);
            }
        }

        this.updateVirtualSlotVisibility();
        this.lastCraftingMode = this.container.isCraftingMode();
    }

    protected boolean acceptType(final VirtualMEPhantomSlot slot, final IAEStackType<?> type, final int mouseButton) {
        if (type == AEItemStackType.INSTANCE) {
            return true;
        }
        return !this.container.isCraftingMode();
    }

    protected void refreshVirtualSlots() {
        final boolean craftingMode = this.container.isCraftingMode();
        if (this.lastCraftingMode == null || this.lastCraftingMode.booleanValue() != craftingMode) {
            this.initVirtualSlots();
            return;
        }
        this.updateVirtualSlotVisibility();
    }

    protected void updateVirtualSlotVisibility() {
        final boolean craftingMode = this.container.isCraftingMode();

        if (this.craftingVSlots != null) {
            for (final VirtualMEPatternSlot slot : this.craftingVSlots) {
                if (slot != null) {
                    slot.setHidden(false);
                }
            }
        }

        if (this.outputVSlots != null) {
            for (final VirtualMEPatternSlot slot : this.outputVSlots) {
                if (slot != null) {
                    slot.setHidden(craftingMode);
                }
            }
        }
    }

    protected int patternGuiY(final int y) {
        return y + this.ySize - 81;
    }
}

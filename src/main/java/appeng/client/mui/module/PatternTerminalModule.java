package appeng.client.mui.module;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMEPatternSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.mui.AEBasePanel;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.AELog;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.util.AEFluidStack;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Shared pattern terminal module for {@link MUIPatternTermPanel} and {@link MUIExpandedProcessingPatternTermPanel}.
 *
 * <p>Extracts duplicated button creation, visibility management, click handling,
 * and JEI ghost drag logic into a single reusable component.
 */
public class PatternTerminalModule {

    /**
     * Implemented by the host panel to provide container access, GUI context, and button layout overrides.
     */
    public interface Host {
        ContainerPatternEncoder getEncoder();

        int getGuiLeft();

        int getGuiTop();

        int getYSize();

        AEBasePanel getPanel();

        RenderItem getItemRenderer();

        List<GuiButton> getButtonList();

        /**
         * X position (relative to guiLeft) for the right column of quantity adjustment buttons.
         */
        int getMultiplyButtonX();

        /**
         * X position (relative to guiLeft) for the left column of quantity adjustment buttons.
         */
        int getDivideButtonX();

        default int getSubstituteX() {
            return 84;
        }

        default int getSubstituteY() {
            return -163;
        }

        default int getClearX() {
            return 74;
        }

        default int getTabX() {
            return 173;
        }

        default int getTabY() {
            return -177;
        }

        default int getEncodeX() {
            return 147;
        }

        default int getEncodeY() {
            return -142;
        }
    }

    private final Host host;

    // buttons
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

    // JEI ghost drag target & slot mapping
    public Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public PatternTerminalModule(Host host) {
        this.host = host;
    }

    /**
     * Creates and registers all pattern terminal buttons. Call once from the panel's {@code initGui()}
     * after {@code super.initGui()}.
     */
    public void initButtons() {
        final List<GuiButton> buttonList = host.getButtonList();
        final int guiLeft = host.getGuiLeft();
        final int guiTop = host.getGuiTop();
        final int ySize = host.getYSize();

        this.tabCraftButton = new GuiTabButton(guiLeft + host.getTabX(), guiTop + ySize + host.getTabY(),
                new ItemStack(Blocks.CRAFTING_TABLE), GuiText.CraftingPattern.getLocal(),
                host.getItemRenderer());
        buttonList.add(this.tabCraftButton);

        this.tabProcessButton = new GuiTabButton(guiLeft + host.getTabX(), guiTop + ySize + host.getTabY(),
                new ItemStack(Blocks.FURNACE), GuiText.ProcessingPattern.getLocal(),
                host.getItemRenderer());
        buttonList.add(this.tabProcessButton);

        this.substitutionsEnabledBtn = new GuiImgButton(guiLeft + host.getSubstituteX(),
                guiTop + ySize + host.getSubstituteY(), Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        buttonList.add(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new GuiImgButton(guiLeft + host.getSubstituteX(),
                guiTop + ySize + host.getSubstituteY(), Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        buttonList.add(this.substitutionsDisabledBtn);

        this.clearBtn = new GuiImgButton(guiLeft + host.getClearX(), guiTop + ySize + host.getSubstituteY(),
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        buttonList.add(this.clearBtn);

        this.x3Btn = new GuiImgButton(guiLeft + host.getMultiplyButtonX(), guiTop + ySize - 158,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        buttonList.add(this.x3Btn);

        this.x2Btn = new GuiImgButton(guiLeft + host.getMultiplyButtonX(), guiTop + ySize - 148,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        buttonList.add(this.x2Btn);

        this.plusOneBtn = new GuiImgButton(guiLeft + host.getMultiplyButtonX(), guiTop + ySize - 138,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        buttonList.add(this.plusOneBtn);

        this.divThreeBtn = new GuiImgButton(guiLeft + host.getDivideButtonX(), guiTop + ySize - 158,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_THREE);
        this.divThreeBtn.setHalfSize(true);
        this.divThreeBtn.visible = false;
        this.divThreeBtn.enabled = false;
        buttonList.add(this.divThreeBtn);

        this.divTwoBtn = new GuiImgButton(guiLeft + host.getDivideButtonX(), guiTop + ySize - 148,
                Settings.ACTIONS, ActionItems.DIVIDE_BY_TWO);
        this.divTwoBtn.setHalfSize(true);
        this.divTwoBtn.visible = false;
        this.divTwoBtn.enabled = false;
        buttonList.add(this.divTwoBtn);

        this.minusOneBtn = new GuiImgButton(guiLeft + host.getDivideButtonX(), guiTop + ySize - 138,
                Settings.ACTIONS, ActionItems.DECREASE_BY_ONE);
        this.minusOneBtn.setHalfSize(true);
        this.minusOneBtn.visible = false;
        this.minusOneBtn.enabled = false;
        buttonList.add(this.minusOneBtn);

        this.encodeBtn = new GuiImgButton(guiLeft + host.getEncodeX(), guiTop + ySize + host.getEncodeY(),
                Settings.ACTIONS, ActionItems.ENCODE);
        buttonList.add(this.encodeBtn);
    }

    /**
     * Updates button visibility and labels each frame. Call from the panel's {@code drawFG()}.
     *
     * @param craftingActive whether the container is currently in crafting mode
     * @param showDualTabs   {@code true} if the panel supports both crafting and processing tabs
     *                       ({@link MUIPatternTermPanel}); {@code false} for processing-only
     *                       ({@link MUIExpandedProcessingPatternTermPanel})
     * @param shiftDown      whether Shift is held (passed because the module cannot resolve
     *                       {@link GuiScreen#isShiftKeyDown()} from this package)
     */
    public void drawFG(boolean craftingActive, boolean showDualTabs, boolean shiftDown) {
        if (showDualTabs && craftingActive) {
            this.tabCraftButton.visible = true;
            this.tabProcessButton.visible = false;
            this.x2Btn.visible = false;
            this.x3Btn.visible = false;
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = false;
            this.minusOneBtn.visible = false;

            if (host.getEncoder().substitute) {
                this.substitutionsEnabledBtn.visible = true;
                this.substitutionsDisabledBtn.visible = false;
            } else {
                this.substitutionsEnabledBtn.visible = false;
                this.substitutionsDisabledBtn.visible = true;
            }
        } else {
            this.tabCraftButton.visible = false;
            this.tabProcessButton.visible = true;
            this.substitutionsEnabledBtn.visible = false;
            this.substitutionsDisabledBtn.visible = false;
            this.x2Btn.visible = true;
            this.x3Btn.visible = true;
            this.x2Btn.set(shiftDown ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(shiftDown ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.divTwoBtn.visible = false;
            this.divThreeBtn.visible = false;
            this.plusOneBtn.visible = true;
            this.plusOneBtn.set(shiftDown ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
            this.minusOneBtn.visible = false;
        }
    }

    /**
     * Handles all pattern-terminal button clicks. Call from the panel's {@code actionPerformed()}
     * after {@code super.actionPerformed(btn)}.
     *
     * @return {@code true} if the button was handled by this module
     */
    public boolean actionPerformed(GuiButton btn) {
        try {
            if (this.tabCraftButton == btn) {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.CraftMode", "0"));
                return true;
            }

            if (this.tabProcessButton == btn) {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.CraftMode", "1"));
                return true;
            }

            if (this.encodeBtn == btn) {
                final String value = AEBasePanel.isShiftKeyDown() ? "2" : "1";
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Encode", value));
                return true;
            }

            if (this.clearBtn == btn) {
                NetworkHandler.instance().sendToServer(new PacketValueConfig("PatternTerminal.Clear", "1"));
                return true;
            }

            if (this.x2Btn == btn) {
                final String action = AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByTwo"
                        : "PatternTerminal.MultiplyByTwo";
                NetworkHandler.instance().sendToServer(new PacketValueConfig(action, "1"));
                return true;
            }

            if (this.x3Btn == btn) {
                final String action = AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByThree"
                        : "PatternTerminal.MultiplyByThree";
                NetworkHandler.instance().sendToServer(new PacketValueConfig(action, "1"));
                return true;
            }

            if (this.plusOneBtn == btn) {
                final String action = AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DecreaseByOne"
                        : "PatternTerminal.IncreaseByOne";
                NetworkHandler.instance().sendToServer(new PacketValueConfig(action, "1"));
                return true;
            }

            if (this.substitutionsEnabledBtn == btn || this.substitutionsDisabledBtn == btn) {
                NetworkHandler.instance().sendToServer(
                        new PacketValueConfig("PatternTerminal.Substitute",
                                this.substitutionsEnabledBtn == btn ? "0" : "1"));
                return true;
            }
        } catch (final IOException e) {
            AELog.error(e);
        }
        return false;
    }

    /**
     * Builds JEI ghost ingredient targets for the given ingredient over the provided slot arrays.
     * Call from the panel's {@code getPhantomTargets()}.
     */
    public List<Target<?>> createPhantomTargets(final Object ingredient,
            final VirtualMEPatternSlot[] craftingSlots, final VirtualMEPatternSlot[] outputSlots) {
        final ItemStack itemIngredient = ingredient instanceof ItemStack ? (ItemStack) ingredient : ItemStack.EMPTY;
        final IAEStack<?> aeIngredient = ingredient instanceof FluidStack fluidStack
                ? AEFluidStack.fromFluidStack(fluidStack.copy())
                : null;

        if (itemIngredient.isEmpty() && aeIngredient == null) {
            return Collections.emptyList();
        }

        if (aeIngredient != null && host.getEncoder().isCraftingMode()) {
            return Collections.emptyList();
        }

        this.mapTargetSlot.clear();
        final List<Target<?>> targets = new ArrayList<>();
        this.addVirtualTargets(targets, craftingSlots, itemIngredient, aeIngredient);
        this.addVirtualTargets(targets, outputSlots, itemIngredient, aeIngredient);
        return targets;
    }

    private void addVirtualTargets(final List<Target<?>> targets, final VirtualMEPatternSlot[] slots,
            final ItemStack itemIngredient, final IAEStack<?> aeIngredient) {
        if (slots == null) {
            return;
        }

        for (final VirtualMEPatternSlot slot : slots) {
            if (slot == null || !slot.isVisible() || !slot.isSlotEnabled()) {
                continue;
            }

            final Target<Object> target = new Target<Object>() {
                @Override
                public Rectangle getArea() {
                    return new Rectangle(host.getGuiLeft() + slot.xPos(), host.getGuiTop() + slot.yPos(), 16, 16);
                }

                @Override
                public void accept(final Object ignored) {
                    if (aeIngredient != null) {
                        final IAEStackInventory targetInv = slot.getStorageName() == StorageName.CRAFTING_OUTPUT
                                ? host.getEncoder().getOutputAEInv()
                                : host.getEncoder().getCraftingAEInv();
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

    public Map<Target<?>, Object> getFakeSlotTargetMap() {
        return mapTargetSlot;
    }
}

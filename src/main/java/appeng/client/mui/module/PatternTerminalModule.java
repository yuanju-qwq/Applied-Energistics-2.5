package appeng.client.mui.module;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.gui.IGhostIngredientHandler.Target;

import appeng.api.config.ActionItems;
import appeng.api.config.ItemSubstitution;
import appeng.api.config.Settings;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.mui.slot.VirtualMEPatternSlot;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.IMUIWidget;
import appeng.client.mui.widgets.MUIButtonWidget;
import appeng.client.mui.widgets.MUITabButton;
import appeng.container.implementations.ContainerPatternEncoder;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.fluids.util.AEFluidStack;
import appeng.tile.inventory.IAEStackInventory;

/**
 * Shared pattern terminal module for {@link MUIPatternTermPanel} and {@link MUIExpandedProcessingPatternTermPanel}.
 *
 * <p>Manages pattern terminal buttons as MUI widgets (MUIButtonWidget/MUITabButton)
 * with onClick callbacks, replacing legacy GuiImgButton/GuiTabButton.
 * JEI ghost drag logic is also handled here.
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

        void addModuleWidget(IMUIWidget widget);

        int getMultiplyButtonX();

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

    private MUITabButton tabCraftButton;
    private MUITabButton tabProcessButton;
    private MUIButtonWidget substitutionsEnabledBtn;
    private MUIButtonWidget substitutionsDisabledBtn;
    private MUIButtonWidget encodeBtn;
    private MUIButtonWidget clearBtn;
    private MUIButtonWidget x2Btn;
    private MUIButtonWidget x3Btn;
    private MUIButtonWidget plusOneBtn;

    public Map<Target<?>, Object> mapTargetSlot = new HashMap<>();

    public PatternTerminalModule(Host host) {
        this.host = host;
    }

    /**
     * Creates and registers all pattern terminal buttons as MUI widgets.
     * Call once from the panel's {@code initGui()} after {@code super.initGui()}.
     */
    public void initButtons() {
        final int ySize = host.getYSize();

        this.tabCraftButton = new MUITabButton(host.getTabX(), ySize + host.getTabY(),
                new ItemStack(Blocks.CRAFTING_TABLE), GuiText.CraftingPattern.getLocal(),
                host.getItemRenderer());
        this.tabCraftButton.setOnClick(tab -> sendPacket("PatternTerminal.CraftMode", "0"));
        host.addModuleWidget(this.tabCraftButton);

        this.tabProcessButton = new MUITabButton(host.getTabX(), ySize + host.getTabY(),
                new ItemStack(Blocks.FURNACE), GuiText.ProcessingPattern.getLocal(),
                host.getItemRenderer());
        this.tabProcessButton.setOnClick(tab -> sendPacket("PatternTerminal.CraftMode", "1"));
        host.addModuleWidget(this.tabProcessButton);

        this.substitutionsEnabledBtn = new MUIButtonWidget(host.getSubstituteX(),
                ySize + host.getSubstituteY(), Settings.ACTIONS, ItemSubstitution.ENABLED);
        this.substitutionsEnabledBtn.setHalfSize(true);
        this.substitutionsEnabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Substitute", "0"));
        host.addModuleWidget(this.substitutionsEnabledBtn);

        this.substitutionsDisabledBtn = new MUIButtonWidget(host.getSubstituteX(),
                ySize + host.getSubstituteY(), Settings.ACTIONS, ItemSubstitution.DISABLED);
        this.substitutionsDisabledBtn.setHalfSize(true);
        this.substitutionsDisabledBtn.setOnClick(btn -> sendPacket("PatternTerminal.Substitute", "1"));
        host.addModuleWidget(this.substitutionsDisabledBtn);

        this.clearBtn = new MUIButtonWidget(host.getClearX(), ySize + host.getSubstituteY(),
                Settings.ACTIONS, ActionItems.CLOSE);
        this.clearBtn.setHalfSize(true);
        this.clearBtn.setOnClick(btn -> sendPacket("PatternTerminal.Clear", "1"));
        host.addModuleWidget(this.clearBtn);

        this.x3Btn = new MUIButtonWidget(host.getMultiplyButtonX(), ySize - 158,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_THREE);
        this.x3Btn.setHalfSize(true);
        this.x3Btn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByThree" : "PatternTerminal.MultiplyByThree",
                "1"));
        host.addModuleWidget(this.x3Btn);

        this.x2Btn = new MUIButtonWidget(host.getMultiplyButtonX(), ySize - 148,
                Settings.ACTIONS, ActionItems.MULTIPLY_BY_TWO);
        this.x2Btn.setHalfSize(true);
        this.x2Btn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DivideByTwo" : "PatternTerminal.MultiplyByTwo",
                "1"));
        host.addModuleWidget(this.x2Btn);

        this.plusOneBtn = new MUIButtonWidget(host.getMultiplyButtonX(), ySize - 138,
                Settings.ACTIONS, ActionItems.INCREASE_BY_ONE);
        this.plusOneBtn.setHalfSize(true);
        this.plusOneBtn.setOnClick(btn -> sendPacket(
                AEBasePanel.isShiftKeyDown() ? "PatternTerminal.DecreaseByOne" : "PatternTerminal.IncreaseByOne",
                "1"));
        host.addModuleWidget(this.plusOneBtn);

        this.encodeBtn = new MUIButtonWidget(host.getEncodeX(), ySize + host.getEncodeY(),
                Settings.ACTIONS, ActionItems.ENCODE);
        this.encodeBtn.setOnClick(btn -> {
            final String value = AEBasePanel.isShiftKeyDown() ? "2" : "1";
            sendPacket("PatternTerminal.Encode", value);
        });
        host.addModuleWidget(this.encodeBtn);
    }

    /**
     * Updates button visibility and labels each frame. Call from the panel's {@code drawFG()}.
     */
    public void drawFG(boolean craftingActive, boolean showDualTabs, boolean shiftDown) {
        if (showDualTabs && craftingActive) {
            this.tabCraftButton.setVisible(true);
            this.tabProcessButton.setVisible(false);
            this.x2Btn.setVisible(false);
            this.x3Btn.setVisible(false);
            this.plusOneBtn.setVisible(false);

            this.substitutionsEnabledBtn.setVisible(host.getEncoder().substitute);
            this.substitutionsDisabledBtn.setVisible(!host.getEncoder().substitute);
        } else {
            this.tabCraftButton.setVisible(false);
            this.tabProcessButton.setVisible(true);
            this.substitutionsEnabledBtn.setVisible(false);
            this.substitutionsDisabledBtn.setVisible(false);
            this.x2Btn.setVisible(true);
            this.x3Btn.setVisible(true);
            this.x2Btn.set(shiftDown ? ActionItems.DIVIDE_BY_TWO : ActionItems.MULTIPLY_BY_TWO);
            this.x3Btn.set(shiftDown ? ActionItems.DIVIDE_BY_THREE : ActionItems.MULTIPLY_BY_THREE);
            this.plusOneBtn.setVisible(true);
            this.plusOneBtn.set(shiftDown ? ActionItems.DECREASE_BY_ONE : ActionItems.INCREASE_BY_ONE);
        }
    }

    /** Helper that swallows IOExceptions. */
    private void sendPacket(String key, String value) {
        try {
            NetworkHandler.instance().sendToServer(new PacketValueConfig(key, value));
        } catch (IOException e) {
            // ignore
        }
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
                            targetInv.setGenericStack(slot.getSlotIndex(), GenericStack.fromIAEStack(aeIngredient.copy()));
                        }
                        NetworkHandler.instance().sendToServer(
                                new PacketVirtualSlot(slot.getStorageName(), slot.getSlotIndex(), GenericStack.fromIAEStack(aeIngredient)));
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

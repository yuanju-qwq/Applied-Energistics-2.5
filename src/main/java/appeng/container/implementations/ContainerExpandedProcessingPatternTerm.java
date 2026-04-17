package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

import appeng.api.storage.ITerminalHost;
import appeng.container.slot.SlotRestrictedInput;

public class ContainerExpandedProcessingPatternTerm extends ContainerPatternEncoder {

    public ContainerExpandedProcessingPatternTerm(InventoryPlayer ip, ITerminalHost monitorable) {
        super(ip, monitorable, false);

        // crafting/output 槽位现在由 GUI 侧的 VirtualMEPatternSlot 管理，
        // 不再添加 SlotFakeCraftingMatrix / OptionalSlotFake 到 Minecraft Container。

        final IItemHandler patternInv = this.getPart().getInventoryByName("pattern");

        this.addSlotToContainer(
                this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                        patternInv, 0, 147, -72 - 9, this.getInventoryPlayer()));
        this.addSlotToContainer(
                this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patternInv, 1, 147, -72 + 34, this.getInventoryPlayer()));

        this.patternSlotOUT.setStackLimit(1);

        this.bindPlayerInventory(ip, 0, 0);

    }

    @Override
    public boolean isSlotEnabled(final int idx) {
        return true;
    }

    @Override
    public boolean isCraftingMode() {
        return false;
    }
}

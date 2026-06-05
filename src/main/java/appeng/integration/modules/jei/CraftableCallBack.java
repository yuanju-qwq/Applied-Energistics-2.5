package appeng.integration.modules.jei;

import java.util.Collection;
import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import com.google.common.base.Stopwatch;

import mezz.jei.api.gui.ITooltipCallback;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.helpers.IContainerCraftingPacket;
import appeng.util.Platform;

public class CraftableCallBack implements ITooltipCallback<ItemStack> {
    private final KeyCounter list;
    private final Container container;
    private final Stopwatch lastClicked = Stopwatch.createStarted();

    public CraftableCallBack(Container container, KeyCounter ir) {
        this.list = ir;
        this.container = container;
    }

    @Override
    public void onTooltip(int slotIndex, boolean input, ItemStack ingredient, List<String> tooltip) {
        if (!input)
            return;
        if (list != null) {

            KeyCounter available = mergeInventories(list, (ContainerMEMonitorable) container);

            AEItemKey search = AEItemKey.of(ingredient);
            if (search == null) return;
            if (ingredient.getItem().isDamageable() || Platform.isGTDamageableItem(ingredient.getItem())) {
                Collection<Object2LongMap.Entry<AEKey>> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
                if (!fuzzy.isEmpty()) {
                    for (Object2LongMap.Entry<AEKey> entry : fuzzy) {
                        if (entry.getLongValue() > 0) {
                            if (Platform.isGTDamageableItem(ingredient.getItem())) {
                                if (!(ingredient.getMetadata() == ((AEItemKey) entry.getKey()).getItemDamage())) {
                                    continue;
                                }
                            }

                            break;
                        } else {
                            String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing")
                                    + "]";
                            tooltip.add(line);
                        }
                    }
                } else {
                    String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                    tooltip.add(line);
                }
            } else {
                long amount = available.get(search);
                if (amount == 0) {
                    String line = "§c[" + I18n.translateToLocalFormatted("gui.appliedenergistics2.Missing") + "]";
                    tooltip.add(line);
                }
            }
        }
    }

    KeyCounter mergeInventories(KeyCounter repo,
            ContainerMEMonitorable containerCraftingTerm) {
        KeyCounter itemList = new KeyCounter();
        for (Object2LongMap.Entry<AEKey> entry : repo) {
            itemList.add(entry.getKey(), entry.getLongValue());
        }

        PlayerMainInvWrapper invWrapper = new PlayerMainInvWrapper(containerCraftingTerm.getPlayerInv());
        for (int i = 0; i < invWrapper.getSlots(); i++) {
            ItemStack stack = invWrapper.getStackInSlot(i);
            if (!stack.isEmpty()) {
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    itemList.add(key, stack.getCount());
                }
            }
        }

        if (containerCraftingTerm instanceof IContainerCraftingPacket) {
            IItemHandler itemHandler = ((IContainerCraftingPacket) containerCraftingTerm)
                    .getInventoryByName("crafting");
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    AEItemKey key = AEItemKey.of(stack);
                    if (key != null) {
                        itemList.add(key, stack.getCount());
                    }
                }
            }
        }
        return itemList;
    }
}

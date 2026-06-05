package appeng.integration.modules.jei;

import static mezz.jei.api.recipe.transfer.IRecipeTransferError.Type.USER_FACING;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.gui.TooltipRenderer;
import mezz.jei.gui.recipes.RecipeLayout;
import mezz.jei.gui.recipes.RecipeTransferButton;

import it.unimi.dsi.fastutil.objects.Object2LongMap;

import appeng.api.config.FuzzyMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.helpers.IContainerCraftingPacket;
import appeng.util.Platform;

public class JEIMissingItem implements IRecipeTransferError {

    private boolean errored;
    public long lastUpdate;
    private final List<Integer> craftableSlots = new ArrayList<>();
    private final List<Integer> foundSlots = new ArrayList<>();

    KeyCounter available = new KeyCounter();

    KeyCounter used = new KeyCounter();

    JEIMissingItem(Container container, @Nonnull IRecipeLayout recipeLayout) {
        if (container instanceof ContainerMEMonitorable) {
            KeyCounter ir = new KeyCounter();
            for (IAEItemStack stack : ((ContainerMEMonitorable) container).items) {
                AEKey key = stack.toAEKey();
                if (key != null) {
                    ir.add(key, stack.getStackSize());
                }
            }

            KeyCounter available = mergeInventories(ir, (ContainerMEMonitorable) container);

            boolean found;
            this.errored = false;
            recipeLayout.getItemStacks().addTooltipCallback(new CraftableCallBack(container, available));

            KeyCounter used = new KeyCounter();
            for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                found = false;
                if (i.isInput() && !i.getAllIngredients().isEmpty()) {
                    List<?> allIngredients = i.getAllIngredients();
                    for (Object allIngredient : allIngredients) {
                        if (allIngredient instanceof ItemStack) {
                            ItemStack stack = (ItemStack) allIngredient;
                            if (!stack.isEmpty()) {
                                AEItemKey search = AEItemKey.of(stack);
                                if (search == null) continue;
                                if (stack.getItem().isDamageable() || Platform.isGTDamageableItem(stack.getItem())) {
                                    Collection<Object2LongMap.Entry<AEKey>> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
                                    if (!fuzzy.isEmpty()) {
                                        for (Object2LongMap.Entry<AEKey> entry : fuzzy) {
                                            if (entry.getLongValue() > 0) {
                                                if (Platform.isGTDamageableItem(stack.getItem())) {
                                                    if (!(stack.getMetadata() == ((AEItemKey) entry.getKey()).getItemDamage())) {
                                                        continue;
                                                    }
                                                }
                                                found = true;
                                                used.add(entry.getKey(), 1);
                                            }
                                        }
                                    }
                                } else {
                                    long ext = available.get(search);
                                    if (ext > 0) {
                                        long usedCount = used.get(search);
                                        if (usedCount < ext) {
                                            used.add(search, 1);
                                            found = true;
                                        }
                                    }
                                }
                            } else {
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        this.errored = true;
                        break;
                    }
                }
            }
        }
    }

    @Nonnull
    @Override
    public Type getType() {
        return USER_FACING;
    }

    @Override
    public void showError(Minecraft minecraft, int mouseX, int mouseY, @Nonnull IRecipeLayout recipeLayout, int recipeX,
            int recipeY) {
        Container c = minecraft.player.openContainer;
        if (c instanceof ContainerMEMonitorable container) {
            KeyCounter ir = new KeyCounter();
            for (IAEItemStack stack : ((ContainerMEMonitorable) c).items) {
                AEKey key = stack.toAEKey();
                if (key != null) {
                    ir.add(key, stack.getStackSize());
                }
            }
            boolean found = false;
            boolean foundAny = false;
            boolean craftable = false;
            boolean foundAnyCraftable = false;
            int currentSlot = 0;
            this.errored = false;

            if (System.currentTimeMillis() - lastUpdate > 1000) {
                lastUpdate = System.currentTimeMillis();
                available = mergeInventories(ir, container);
                this.foundSlots.clear();
                this.craftableSlots.clear();
            } else {
                for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                    if (i.isInput()) {
                        if (!foundSlots.contains(currentSlot)) {
                            if (craftableSlots.contains(currentSlot)) {
                                i.drawHighlight(minecraft, new Color(0.0f, 0.0f, 1.0f, 0.4f), recipeX, recipeY);
                            } else {
                                i.drawHighlight(minecraft, new Color(1.0f, 0.0f, 0.0f, 0.4f), recipeX, recipeY);
                            }
                        }
                    }
                    currentSlot++;
                }
                return;
            }
            this.used.reset();

            for (IGuiIngredient<?> i : recipeLayout.getItemStacks().getGuiIngredients().values()) {
                found = false;
                craftable = false;
                KeyCounter valid = new KeyCounter();
                if (i.isInput()) {
                    List<?> allIngredients = i.getAllIngredients();
                    for (Object allIngredient : allIngredients) {
                        if (allIngredient instanceof ItemStack stack) {
                            if (!stack.isEmpty()) {
                                AEItemKey search = AEItemKey.of(stack);
                                if (search == null) continue;
                                if (stack.getItem().isDamageable() || Platform.isGTDamageableItem(stack.getItem())) {
                                    Collection<Object2LongMap.Entry<AEKey>> fuzzy = available.findFuzzy(search, FuzzyMode.IGNORE_ALL);
                                    if (!fuzzy.isEmpty()) {
                                        for (Object2LongMap.Entry<AEKey> entry : fuzzy) {
                                            if (entry.getLongValue() > 0) {
                                                if (Platform.isGTDamageableItem(stack.getItem())) {
                                                    if (!(stack.getMetadata() == ((AEItemKey) entry.getKey()).getItemDamage())) {
                                                        continue;
                                                    }
                                                }
                                                found = true;
                                                used.add(entry.getKey(), 1);
                                                valid.add(entry.getKey(), 1);
                                            }
                                        }
                                    }
                                } else {
                                    long ext = available.get(search);
                                    if (ext > 0) {
                                        long usedCount = used.get(search);
                                        if (usedCount < ext) {
                                            used.add(search, 1);
                                            if (craftable) {
                                                valid = new KeyCounter();
                                            }
                                            valid.add(search, 1);
                                            found = true;
                                        }
                                    }
                                }
                            } else {
                                found = true;
                            }
                        }
                    }
                    if (i.getAllIngredients().isEmpty()) {
                        currentSlot++;
                        continue;
                    }
                    ArrayList<ItemStack> validStacks = new ArrayList<>();
                    for (Object2LongMap.Entry<AEKey> entry : valid) {
                        if (entry.getLongValue() > 0) {
                            AEItemKey key = (AEItemKey) entry.getKey();
                            ItemStack validStack = key.toStack(1);
                            validStacks.add(validStack);
                        }
                    }
                    if (!found) {
                        if (craftable) {
                            i.drawHighlight(minecraft, new Color(0.0f, 0.0f, 1.0f, 0.4f), recipeX, recipeY);
                            this.craftableSlots.add(currentSlot);
                            recipeLayout.getItemStacks().set(currentSlot, validStacks);
                            foundAnyCraftable = true;
                        } else {
                            i.drawHighlight(minecraft, new Color(1.0f, 0.0f, 0.0f, 0.4f), recipeX, recipeY);
                        }
                        this.errored = true;
                    } else {
                        foundAny = true;
                        this.foundSlots.add(currentSlot);
                        recipeLayout.getItemStacks().set(currentSlot, validStacks);
                    }
                }
                currentSlot++;
            }
            RecipeTransferButton b = ((RecipeLayout) recipeLayout).getRecipeTransferButton();
            if (b != null) {
                List<String> tooltipLines = new ArrayList<>();
                b.init(c, minecraft.player);
                if (errored && foundAny) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.PartialTransfer"));
                    b.enabled = true;
                    b.visible = true;
                }
                if (errored) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.MissingItem"));
                }
                if (foundAnyCraftable) {
                    tooltipLines.add(I18n.translateToLocal("gui.tooltips.appliedenergistics2.CraftableItem"));
                }
                TooltipRenderer.drawHoveringText(minecraft, tooltipLines, mouseX, mouseY);
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

    public boolean errored() {
        return errored;
    }
}

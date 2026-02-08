package appeng.items.misc;

import java.util.List;
import java.util.Map;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.PatternHelper;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.core.localization.GuiText;
import appeng.helpers.SpecialPatternHelper;
import appeng.items.AEBaseItem;
import appeng.util.Platform;
import appeng.util.item.ItemStackHashStrategy;

public class ItemSpecialEncodedPattern extends AEBaseItem implements ICraftingPatternItem {

    private static final ItemStackHashStrategy HASH_STRATEGY = ItemStackHashStrategy.comparingAllButCount();
    private static final Map<ItemStack, ItemStack> OUTPUT_CACHE =
            new Object2ObjectOpenCustomHashMap<>(HASH_STRATEGY);

    public ItemSpecialEncodedPattern() {
        this.setMaxStackSize(16); // 降低堆叠限制以区分普通模板
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World w, EntityPlayer player, EnumHand hand) {
        this.clearPattern(player.getHeldItem(hand), player);
        return ActionResult.newResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private boolean clearPattern(ItemStack stack, EntityPlayer player) {
        if (player.isSneaking()) {
            OUTPUT_CACHE.remove(stack);
            if (Platform.isClient()) return false;

            // 替换为空白模板（数量保持不变）
            ItemStack blank = AEApi.instance().definitions()
                    .materials().blankPattern().maybeStack(stack.getCount()).orElse(ItemStack.EMPTY);

            if (!blank.isEmpty()) {
                for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
                    if (player.inventory.getStackInSlot(i) == stack) {
                        player.inventory.setInventorySlotContents(i, blank);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addCheckedInformation(final ItemStack stack, final World world, final List<String> lines,
                                      final ITooltipFlag advancedTooltips) {
        final ICraftingPatternDetails details;
        try {
            details = new SpecialPatternHelper(stack, world);
        } catch (Exception e) {
            lines.add(TextFormatting.RED + GuiText.InvalidPattern.getLocal());
            lines.add(TextFormatting.DARK_GRAY + "Purpose: " + getPatternPurpose());
            return;
        }

        // 移除自定义名称以显示系统名称
        if (stack.hasDisplayName()) {
            stack.clearCustomName();
        }

        // 显示输入
        lines.add(GuiText.With.getLocal() + ":");
        boolean first = true;
        for (IAEItemStack in : details.getCondensedInputs()) {
            if (in != null) {
                lines.add((first ? "  " : "  " + GuiText.And.getLocal() + " ")
                        + in.getStackSize() + " " + Platform.getItemDisplayName(in));
                first = false;
            }
        }
        // 显示用途说明
        lines.add(TextFormatting.DARK_GRAY + "Purpose: " + getPatternPurpose());

        // 显示编码器信息
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey("encoderName")) {
            lines.add("Encoder Name: "+tag.getString("encoderName"));
        }
    }

    /**
     * 获取模板用途描述（用于UI显示）
     */
    public String getPatternPurpose() {
        return "允许空输出的样板，用于产线下单时的封装（用法：使用本样板编写一套多方块，将本样板作为输入编写到其他样板中，即可一键下单多套多方块，且多方块相互封装便于替换）";
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(final ItemStack is, final World w) {
        try {
            return new PatternHelper(is, w);
        } catch (final Throwable t) {
            return null;
        }
    }

    /**
     * 获取输出物品（空输出返回 EMPTY）
     */
    public ItemStack getOutput(ItemStack item) {
        ItemStack cached = OUTPUT_CACHE.get(item);
        if (cached != null) return cached;

        World w = appeng.core.AppEng.proxy.getWorld();
        if (w == null) return ItemStack.EMPTY;

        ICraftingPatternDetails details = getPatternForItem(item, w);
        ItemStack output = (details != null && details.getOutputs().length > 0)
                ? details.getOutputs()[0].createItemStack()
                : ItemStack.EMPTY;

        OUTPUT_CACHE.put(item, output);
        return output;
    }
}
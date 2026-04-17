package appeng.parts.reporting;

import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.items.IItemHandler;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.parts.IPartModel;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.sync.GuiBridge;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;
import appeng.util.inv.InvOperation;
import appeng.util.item.AEItemStack;

public abstract class AbstractPartEncoder extends AbstractPartTerminal implements IIAEStackInventory {

    // 原始 Minecraft 格式库存（pattern 槽位仍然使用此格式）
    protected AppEngInternalInventory pattern;

    // 泛型栈库存（crafting 和 output 使用此格式，支持物品/流体等）
    protected IAEStackInventory craftingAE;
    protected IAEStackInventory outputAE;

    // 兼容旧代码的 AppEngInternalInventory 引用（保留供旧容器代码访问，但不再作为主要存储）
    protected AppEngInternalInventory crafting;
    protected AppEngInternalInventory output;

    protected boolean craftingMode = true;
    protected boolean substitute = false;

    public AbstractPartEncoder(ItemStack is) {
        super(is);
    }

    @Override
    public void getDrops(final List<ItemStack> drops, final boolean wrenched) {
        for (final ItemStack is : this.pattern) {
            if (!is.isEmpty()) {
                drops.add(is);
            }
        }
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        this.pattern.readFromNBT(data, "pattern");
        this.craftingAE.readFromNBT(data, "crafting");
        this.outputAE.readFromNBT(data, "outputList");
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);
        this.pattern.writeToNBT(data, "pattern");
        this.craftingAE.writeToNBT(data, "crafting");
        this.outputAE.writeToNBT(data, "outputList");
    }

    @Override
    public void onChangeInventory(final IItemHandler inv, final int slot, final InvOperation mc,
            final ItemStack removedStack, final ItemStack newStack) {
        if (inv == this.pattern && slot == 1) {
            final ItemStack is = this.pattern.getStackInSlot(1);
            if (!is.isEmpty() && is.getItem() instanceof ICraftingPatternItem) {
                final ICraftingPatternItem pattern = (ICraftingPatternItem) is.getItem();
                final ICraftingPatternDetails details = pattern.getPatternForItem(is,
                        this.getHost().getTile().getWorld());
                if (details != null) {
                    this.setCraftingRecipe(details.isCraftable());
                    this.setSubstitution(details.canSubstitute());

                    for (int x = 0; x < this.craftingAE.getSizeInventory() && x < details.getInputs().length; x++) {
                        final IAEItemStack item = details.getInputs()[x];
                        this.craftingAE.putAEStackInSlot(x, item);
                    }

                    for (int x = 0; x < this.outputAE.getSizeInventory(); x++) {
                        final IAEItemStack item;
                        if (x < details.getOutputs().length) {
                            item = details.getOutputs()[x];
                        } else {
                            item = null;
                        }
                        this.outputAE.putAEStackInSlot(x, item);
                    }
                }
            }
        }

        this.getHost().markForSave();
    }

    private void fixCraftingRecipes() {
        if (this.isCraftingRecipe()) {
            for (int x = 0; x < this.craftingAE.getSizeInventory(); x++) {
                final IAEStack<?> is = this.craftingAE.getAEStackInSlot(x);
                if (is != null) {
                    is.setStackSize(1);
                }
            }
        }
    }

    public boolean isCraftingRecipe() {
        return this.craftingMode;
    }

    public void setCraftingRecipe(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        this.fixCraftingRecipes();
    }

    public boolean isSubstitution() {
        return this.substitute;
    }

    public void setSubstitution(final boolean canSubstitute) {
        this.substitute = canSubstitute;
    }

    @Override
    public IItemHandler getInventoryByName(final String name) {
        if (name.equals("pattern")) {
            return this.pattern;
        }

        return super.getInventoryByName(name);
    }

    // ---- IIAEStackInventory 实现 ----

    @Override
    public void saveAEStackInv() {
        this.fixCraftingRecipes();
        this.getHost().markForSave();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CRAFTING_INPUT) {
            return this.craftingAE;
        }
        if (name == StorageName.CRAFTING_OUTPUT) {
            return this.outputAE;
        }
        return null;
    }

    @Override
    public GuiBridge getGui(final EntityPlayer p) {
        int x = (int) p.posX;
        int y = (int) p.posY;
        int z = (int) p.posZ;
        if (this.getHost().getTile() != null) {
            x = this.getTile().getPos().getX();
            y = this.getTile().getPos().getY();
            z = this.getTile().getPos().getZ();
        }

        if (getGuiBridge().hasPermissions(this.getHost().getTile(), x, y, z, this.getSide(), p)) {
            return getGuiBridge();
        }
        return GuiBridge.GUI_ME;
    }

    abstract public GuiBridge getGuiBridge();

    @Nonnull
    @Override
    abstract public IPartModel getStaticModels();
}

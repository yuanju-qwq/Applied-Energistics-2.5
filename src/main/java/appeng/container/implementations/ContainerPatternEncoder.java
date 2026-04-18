package appeng.container.implementations;

import static appeng.helpers.ItemStackHelper.stackWriteToNBT;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IDefinitions;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.container.ContainerNull;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.interfaces.IVirtualSlotSource;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.sync.packets.PacketPatternSlot;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.IContainerCraftingPacket;
import appeng.items.storage.ItemViewCell;
import appeng.me.helpers.MachineSource;
import appeng.parts.reporting.AbstractPartEncoder;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.util.inv.AdaptorItemHandler;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.inv.WrapperCursorItemHandler;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public abstract class ContainerPatternEncoder extends ContainerMEMonitorable
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket,
        IVirtualSlotHolder, IVirtualSlotSource {

    protected AbstractPartEncoder patternTerminal = null;

    protected IGuiItemObject iGuiItemObject = null;

    final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    protected IItemHandler crafting;
    protected SlotPatternTerm craftSlot;
    protected SlotRestrictedInput patternSlotIN;
    protected SlotRestrictedInput patternSlotOUT;
    protected IRecipe currentRecipe;

    protected SlotFakeCraftingMatrix[] craftingSlots;
    protected OptionalSlotFake[] outputSlots;

    // 服务端用于增量同步的客户端快照
    private IAEStack<?>[] craftingClientSlots;
    private IAEStack<?>[] outputClientSlots;

    @GuiSync(97)
    public boolean craftingMode = true;
    @GuiSync(96)
    public boolean substitute = false;

    protected ContainerPatternEncoder(InventoryPlayer ip, ITerminalHost monitorable, boolean bindInventory) {
        super(ip, monitorable, bindInventory);
        patternTerminal = (AbstractPartEncoder) monitorable;
    }

    protected ContainerPatternEncoder(InventoryPlayer ip, ITerminalHost monitorable, IGuiItemObject iGuiItemObject,
            boolean bindInventory) {
        super(ip, monitorable, iGuiItemObject, bindInventory);
        if (monitorable instanceof AbstractPartEncoder) {
            patternTerminal = (AbstractPartEncoder) monitorable;
        }
        this.iGuiItemObject = iGuiItemObject;
    }

    @Override
    public ItemStack transferStackInSlot(final EntityPlayer p, final int idx) {
        if (Platform.isClient()) {
            return ItemStack.EMPTY;
        }
        if (this.inventorySlots.get(idx) instanceof SlotPlayerInv
                || this.inventorySlots.get(idx) instanceof SlotPlayerHotBar) {
            final AppEngSlot clickSlot = (AppEngSlot) this.inventorySlots.get(idx); // require AE SLots!
            ItemStack itemStack = clickSlot.getStack();
            if (AEApi.instance().definitions().materials().blankPattern().isSameAs(itemStack)) {
                IItemHandler patternInv = this.getPart().getInventoryByName("pattern");
                ItemStack remainder = patternInv.insertItem(0, itemStack, false);
                clickSlot.putStack(remainder);
            }
        }
        return super.transferStackInSlot(p, idx);
    }

    public AbstractPartEncoder getPart() {
        return this.patternTerminal;
    }

    @Override
    public abstract boolean isSlotEnabled(int idx);

    @Override
    public IItemHandler getInventoryByName(String name) {
        if (name.equals("player")) {
            return new PlayerInvWrapper(this.getInventoryPlayer());
        }
        if (this.getPart() != null) {
            return this.getPart().getInventoryByName(name);
        }
        return null;
    }

    @Override
    public boolean useRealItems() {
        return false;
    }

    @Override
    public void saveChanges() {

    }

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation mc, ItemStack removedStack,
            ItemStack newStack) {
    }

    void fixCraftingRecipes() {
        if (this.isCraftingMode()) {
            final IAEStackInventory inv = this.getCraftingAEInv();
            if (inv == null) return;
            for (int x = 0; x < inv.getSizeInventory(); x++) {
                final IAEStack<?> is = inv.getAEStackInSlot(x);
                if (is != null) {
                    is.setStackSize(1);
                }
            }
        }
    }

    @Override
    public void putStackInSlot(int slotID, ItemStack stack) {
        super.putStackInSlot(slotID, stack);
        this.getAndUpdateOutput();
    }

    protected void updateOrderOfOutputSlots() {
        if (!this.isCraftingMode()) {
            if (craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }

            if (this.outputSlots != null) {
                for (int y = 0; y < Math.min(3, this.outputSlots.length); y++) {
                    if (this.outputSlots[y] != null) {
                        this.outputSlots[y].xPos = this.outputSlots[y].getX();
                    }
                }
            }
        } else {
            if (craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
            if (this.outputSlots != null) {
                for (int y = 0; y < Math.min(3, this.outputSlots.length); y++) {
                    if (this.outputSlots[y] != null) {
                        this.outputSlots[y].xPos = -9000;
                    }
                }
            }
        }
    }

    @Override
    public void onSlotChange(final Slot s) {
        if (s == this.patternSlotOUT && Platform.isServer()) {
            for (final IContainerListener listener : this.listeners) {
                for (final Slot slot : this.inventorySlots) {
                    if (slot instanceof OptionalSlotFake || slot instanceof SlotFakeCraftingMatrix) {
                        listener.sendSlotContents(this, slot.slotNumber, slot.getStack());
                    }
                }
                if (listener instanceof EntityPlayerMP) {
                    ((EntityPlayerMP) listener).isChangingQuantityOnly = false;
                }
            }
            this.detectAndSendChanges();
        }
        if (s == this.craftSlot && Platform.isClient()) {
            this.getAndUpdateOutput();
        }
    }

    public void encodeAndMoveToInventory() {
        encode();
        ItemStack output = this.patternSlotOUT.getStack();
        if (!output.isEmpty()) {
            if (!getPlayerInv().addItemStackToInventory(output)) {
                getPlayerInv().player.dropItem(output, false);
            }
            this.patternSlotOUT.putStack(ItemStack.EMPTY);
        }
    }

    public void encode() {
        ItemStack output = this.patternSlotOUT.getStack();
        final ItemStack[] in = this.getInputs();
        final ItemStack[] out = this.getOutputs();

        // 输入必须存在，输出可以为 null（特殊样板场景）
        if (in == null) {
            return;
        }

        // 检查输出槽：若已有物品且既不是普通样板也不是特殊样板，则中止（保护用户物品）
        if (!output.isEmpty() && !this.isPattern(output) && !this.isSpecialPattern(output)) {
            return;
        }

        boolean requiresSpecialPattern = (out == null);
        if (!requiresSpecialPattern) {
            requiresSpecialPattern = true; // 假设需要特殊样板
            for (ItemStack stack : out) {
                if (!stack.isEmpty()) {
                    requiresSpecialPattern = false; // 找到有效输出，不需要特殊样板
                    break;
                }
            }
        }

        // 检查当前输出槽样板类型是否匹配需求
        boolean isCurrentSpecial = this.isSpecialPattern(output);
        boolean typeMatches = (requiresSpecialPattern == isCurrentSpecial);

        // 仅当类型不匹配或输出槽为空时，才消耗新空白样板
        if (output.isEmpty() || !typeMatches) {
            // 从输入槽获取普通空白样板（输入槽只接受普通空白样板）
            ItemStack blankPattern = this.patternSlotIN.getStack();
            if (blankPattern.isEmpty() || !this.isPattern(blankPattern)) {
                return; // 无可用空白样板
            }

            // 消耗一个空白样板
            blankPattern.shrink(1);
            if (blankPattern.isEmpty()) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }

            // 根据输出状态创建对应类型的新样板
            Optional<ItemStack> newPatternOpt = requiresSpecialPattern
                    ? AEApi.instance().definitions().items().specialEncodedPattern().maybeStack(1)
                    : AEApi.instance().definitions().items().encodedPattern().maybeStack(1);

            if (!newPatternOpt.isPresent()) {
                return; // 无法创建目标样板
            }
            output = newPatternOpt.get();
        }

        // 编码NBT数据
        final NBTTagCompound encodedValue = new NBTTagCompound();
        final NBTTagList tagIn = new NBTTagList();
        final NBTTagList tagOut = new NBTTagList();

        for (final ItemStack i : in) {
            tagIn.appendTag(this.createItemTag(i));
        }

        // 即使 out 为 null，也写入空列表（保持NBT结构完整）
        if (out != null) {
            for (final ItemStack i : out) {
                tagOut.appendTag(this.createItemTag(i));
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.isCraftingMode());
        encodedValue.setBoolean("substitute", this.isSubstitute());

        // 标记流体样板（当输入或输出中包含流体时）
        if (containsFluid(in) || containsFluid(out)) {
            encodedValue.setBoolean("fluidPattern", true);
        }

        if (this.getPlayerInv().player != null) {
            encodedValue.setString("encoderName", this.getPlayerInv().player.getName());
        }

        output.setTagCompound(encodedValue);
        patternSlotOUT.putStack(output);
    }

    /**
     * 判断物品是否为特殊样板（specialEncodedPattern）
     */
    private boolean isSpecialPattern(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        Optional<ItemStack> specialPattern = AEApi.instance().definitions().items().specialEncodedPattern()
                .maybeStack(1);
        return specialPattern.isPresent() && stack.isItemEqual(specialPattern.get());
    }

    public void multiply(int multiple) {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv == null || outInv == null) return;

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() * multiple < 1) return;
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() * multiple < 1) return;
        }

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() * multiple);
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() * multiple);
        }
    }

    public void divide(int divide) {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv == null || outInv == null) return;

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() % divide != 0) return;
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() % divide != 0) return;
        }

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() / divide);
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() / divide);
        }
    }

    public void increase(int increase) {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv == null || outInv == null) return;

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() + increase < 1) return;
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() + increase < 1) return;
        }

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() + increase);
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() + increase);
        }
    }

    public void decrease(int decrease) {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv == null || outInv == null) return;

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() - decrease < 1) return;
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null && is.getStackSize() - decrease < 1) return;
        }

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() - decrease);
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(is.getStackSize() - decrease);
        }
    }

    public void maximizeCount() {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv == null || outInv == null) return;

        long maxCount = Long.MAX_VALUE;
        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null && is instanceof IAEItemStack) {
                long maxPerStack = ((IAEItemStack) is).getDefinition().getMaxStackSize();
                maxCount = Math.min(maxCount, maxPerStack);
            }
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null && is instanceof IAEItemStack) {
                long maxPerStack = ((IAEItemStack) is).getDefinition().getMaxStackSize();
                maxCount = Math.min(maxCount, maxPerStack);
            }
        }
        if (maxCount == Long.MAX_VALUE || maxCount < 1) return;

        for (int x = 0; x < craftInv.getSizeInventory(); x++) {
            final IAEStack<?> is = craftInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(maxCount);
        }
        for (int x = 0; x < outInv.getSizeInventory(); x++) {
            final IAEStack<?> is = outInv.getAEStackInSlot(x);
            if (is != null) is.setStackSize(maxCount);
        }
    }

    protected ItemStack[] getInputs() {
        final IAEStackInventory inv = this.getCraftingAEInv();
        if (inv == null) return null;

        final ItemStack[] input = new ItemStack[inv.getSizeInventory()];
        boolean hasValue = false;

        for (int x = 0; x < inv.getSizeInventory(); x++) {
            final IAEStack<?> stack = inv.getAEStackInSlot(x);
            if (stack instanceof IAEItemStack) {
                input[x] = ((IAEItemStack) stack).createItemStack();
                hasValue = true;
            } else if (stack instanceof IAEFluidStack) {
                // 流体栈转换为 ItemFluidDrop
                input[x] = ItemFluidDrop.newStack(((IAEFluidStack) stack).getFluidStack());
                if (!input[x].isEmpty()) {
                    hasValue = true;
                } else {
                    input[x] = ItemStack.EMPTY;
                }
            } else {
                input[x] = ItemStack.EMPTY;
            }
        }

        if (hasValue) {
            return input;
        }

        return null;
    }

    protected ItemStack[] getOutputs() {
        if (this.isCraftingMode()) {
            final ItemStack out = this.getAndUpdateOutput();

            if (!out.isEmpty() && out.getCount() > 0) {
                return new ItemStack[] { out };
            }
        } else {
            final IAEStackInventory inv = this.getOutputAEInv();
            if (inv == null) return null;

            final List<ItemStack> list = new ArrayList<>(inv.getSizeInventory());
            boolean hasValue = false;

            for (int x = 0; x < inv.getSizeInventory(); x++) {
                final IAEStack<?> stack = inv.getAEStackInSlot(x);
                if (stack instanceof IAEItemStack) {
                    list.add(((IAEItemStack) stack).createItemStack());
                    hasValue = true;
                } else if (stack instanceof IAEFluidStack) {
                    ItemStack fluidDrop = ItemFluidDrop.newStack(((IAEFluidStack) stack).getFluidStack());
                    if (!fluidDrop.isEmpty()) {
                        list.add(fluidDrop);
                        hasValue = true;
                    }
                }
            }

            if (hasValue) {
                return list.toArray(new ItemStack[0]);
            }
        }

        return null;
    }

    protected ItemStack getAndUpdateOutput() {
        final World world = this.getPlayerInv().player.world;
        final InventoryCrafting ic = new InventoryCrafting(this, 3, 3);

        final IAEStackInventory inv = this.getCraftingAEInv();
        for (int x = 0; x < ic.getSizeInventory(); x++) {
            if (inv != null) {
                final IAEStack<?> stack = inv.getAEStackInSlot(x);
                if (stack instanceof IAEItemStack) {
                    ic.setInventorySlotContents(x, ((IAEItemStack) stack).createItemStack());
                } else {
                    ic.setInventorySlotContents(x, ItemStack.EMPTY);
                }
            } else {
                ic.setInventorySlotContents(x, ItemStack.EMPTY);
            }
        }

        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, world);
        }

        final ItemStack is;

        if (this.currentRecipe == null) {
            is = ItemStack.EMPTY;
        } else {
            is = this.currentRecipe.getCraftingResult(ic);
        }

        this.cOut.setStackInSlot(0, is);
        return is;
    }

    public boolean isCraftingMode() {
        return this.craftingMode;
    }

    public void setCraftingMode(final boolean craftingMode) {
        this.craftingMode = craftingMode;
        if (getPart() != null) {
            getPart().setCraftingRecipe(craftingMode);
        } else if (iGuiItemObject != null) {
            NBTTagCompound nbtTagCompound = iGuiItemObject.getItemStack().getTagCompound();
            if (nbtTagCompound != null) {
                nbtTagCompound.setBoolean("isCraftingMode", craftingMode);
                this.updateOrderOfOutputSlots();
            }
        }
        if (craftingMode) {
            this.fixCraftingRecipes();
        }
    }

    boolean isSubstitute() {
        return this.substitute;
    }

    public void setSubstitute(final boolean substitute) {
        this.substitute = substitute;
        if (getPart() != null) {
            getPart().setSubstitution(substitute);
        } else if (iGuiItemObject != null) {
            NBTTagCompound nbtTagCompound = iGuiItemObject.getItemStack().getTagCompound();
            if (nbtTagCompound != null) {
                nbtTagCompound.setBoolean("isSubstitute", substitute);
            }
        }
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (Platform.isServer()) {
            if (getPart() != null) {
                if (this.isCraftingMode() != this.getPart().isCraftingRecipe()) {
                    this.setCraftingMode(this.getPart().isCraftingRecipe());
                    this.updateOrderOfOutputSlots();
                }
                this.substitute = this.getPart().isSubstitution();
            } else if (iGuiItemObject != null) {
                NBTTagCompound nbtTagCompound = iGuiItemObject.getItemStack().getTagCompound();
                if (nbtTagCompound != null) {
                    if (nbtTagCompound.hasKey("isCraftingMode")) {
                        boolean crafting = nbtTagCompound.getBoolean("isCraftingMode");
                        if (this.isCraftingMode() != crafting) {
                            this.setCraftingMode(crafting);
                            this.updateOrderOfOutputSlots();
                        }
                    } else {
                        nbtTagCompound.setBoolean("isCraftingMode", false);
                    }
                } else {
                    nbtTagCompound = new NBTTagCompound();
                    nbtTagCompound.setBoolean("isCraftingMode", false);
                    iGuiItemObject.getItemStack().setTagCompound(nbtTagCompound);
                }
                nbtTagCompound = iGuiItemObject.getItemStack().getTagCompound();
                if (nbtTagCompound != null) {
                    if (nbtTagCompound.hasKey("isSubstitute")) {
                        boolean substitute = nbtTagCompound.getBoolean("isSubstitute");
                        if (this.isSubstitute() != substitute) {
                            this.setSubstitute(substitute);
                        }
                    } else {
                        nbtTagCompound.setBoolean("isSubstitute", false);
                    }
                } else {
                    nbtTagCompound = new NBTTagCompound();
                    nbtTagCompound.setBoolean("isSubstitute", false);
                    iGuiItemObject.getItemStack().setTagCompound(nbtTagCompound);
                }
            }

            // 使用虚拟槽位同步 crafting 和 output IAEStackInventory
            final IAEStackInventory craftInv = this.getCraftingAEInv();
            final IAEStackInventory outInv = this.getOutputAEInv();
            if (craftInv != null) {
                this.initClientSlotsIfNeeded(craftInv, outInv);
                this.updateVirtualSlots(StorageName.CRAFTING_INPUT, craftInv, this.craftingClientSlots);
            }
            if (outInv != null) {
                this.updateVirtualSlots(StorageName.CRAFTING_OUTPUT, outInv, this.outputClientSlots);
            }
        }
    }

    private void initClientSlotsIfNeeded(IAEStackInventory craftInv, IAEStackInventory outInv) {
        if (this.craftingClientSlots == null) {
            this.craftingClientSlots = new IAEStack<?>[craftInv.getSizeInventory()];
        }
        if (this.outputClientSlots == null && outInv != null) {
            this.outputClientSlots = new IAEStack<?>[outInv.getSizeInventory()];
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        super.onUpdate(field, oldValue, newValue);

        if (field.equals("craftingMode")) {
            this.getAndUpdateOutput();
            this.updateOrderOfOutputSlots();
        }
    }

    boolean isPattern(final ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }

        final IDefinitions definitions = AEApi.instance().definitions();

        boolean isPattern = definitions.items().encodedPattern().isSameAs(output);
        isPattern |= definitions.materials().blankPattern().isSameAs(output);

        return isPattern;
    }

    NBTBase createItemTag(final ItemStack i) {
        final NBTTagCompound c = new NBTTagCompound();

        if (!i.isEmpty()) {
            // 流体伪物品（ItemFluidDrop）：使用泛型格式序列化为流体
            if (i.getItem() instanceof ItemFluidDrop) {
                IAEFluidStack fluidStack = ItemFluidDrop.getAeFluidStack(
                        AEItemStack.fromItemStack(i));
                if (fluidStack != null) {
                    return fluidStack.toNBTGeneric();
                }
            }
            // 流体容器（桶等）：提取流体后使用泛型格式序列化
            FluidStack fluid = FluidUtil.getFluidContained(i);
            if (fluid != null && fluid.amount > 0) {
                IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluid);
                if (aeFluid != null) {
                    aeFluid.setStackSize((long) fluid.amount * i.getCount());
                    return aeFluid.toNBTGeneric();
                }
            }
            // 普通物品：使用标准序列化
            stackWriteToNBT(i, c);
        }

        return c;
    }

    /**
     * 检查输入/输出中是否包含流体条目（ItemFluidDrop 或流体容器）。
     */
    protected boolean containsFluid(ItemStack[] stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ItemFluidDrop) {
                return true;
            }
            FluidStack fluid = FluidUtil.getFluidContained(stack);
            if (fluid != null && fluid.amount > 0) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        final IAEStackInventory craftInv = this.getCraftingAEInv();
        final IAEStackInventory outInv = this.getOutputAEInv();
        if (craftInv != null) {
            for (int x = 0; x < craftInv.getSizeInventory(); x++) {
                craftInv.putAEStackInSlot(x, null);
            }
        }
        if (outInv != null) {
            for (int x = 0; x < outInv.getSizeInventory(); x++) {
                outInv.putAEStackInSlot(x, null);
            }
        }

        this.detectAndSendChanges();
        this.getAndUpdateOutput();
    }

    public void craftOrGetItem(final PacketPatternSlot packetPatternSlot) {
        if (packetPatternSlot.slotItem != null && this.getCellInventory() != null) {
            final IAEItemStack out = packetPatternSlot.slotItem.copy();
            InventoryAdaptor inv = new AdaptorItemHandler(
                    new WrapperCursorItemHandler(this.getPlayerInv().player.inventory));
            final InventoryAdaptor playerInv = InventoryAdaptor.getAdaptor(this.getPlayerInv().player);

            if (packetPatternSlot.shift) {
                inv = playerInv;
            }

            if (!inv.simulateAdd(out.createItemStack()).isEmpty()) {
                return;
            }

            final IAEItemStack extracted = appeng.util.StorageHelper.poweredExtraction(this.getPowerSource(), this.getCellInventory(),
                    out, this.getActionSource());
            final EntityPlayer p = this.getPlayerInv().player;

            if (extracted != null) {
                inv.addItems(extracted.createItemStack());
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
                return;
            }

            final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
            final InventoryCrafting real = new InventoryCrafting(new ContainerNull(), 3, 3);

            for (int x = 0; x < 9; x++) {
                ic.setInventorySlotContents(x, packetPatternSlot.pattern[x] == null ? ItemStack.EMPTY
                        : packetPatternSlot.pattern[x].createItemStack());
            }

            final IRecipe r = CraftingManager.findMatchingRecipe(ic, p.world);

            if (r == null) {
                return;
            }

            IMEMonitor<IAEItemStack> storage = null;
            if (getPart() != null) {
                storage = this.getPart()
                        .getInventory(AEItemStackType.INSTANCE.getStorageChannel());
            } else if (iGuiItemObject != null) {
                storage = ((ITerminalHost) iGuiItemObject)
                        .getInventory(AEItemStackType.INSTANCE.getStorageChannel());
            }

            final IItemList<IAEItemStack> all = storage.getStorageList();

            final ItemStack is = r.getCraftingResult(ic);

            for (int x = 0; x < ic.getSizeInventory(); x++) {
                if (!ic.getStackInSlot(x).isEmpty()) {
                    final ItemStack pulled = Platform.extractItemsByRecipe(this.getPowerSource(),
                            this.getActionSource(), storage, p.world, r, is, ic,
                            ic.getStackInSlot(x), x, all, Actionable.MODULATE,
                            ItemViewCell.createFilter(this.getViewCells()));
                    real.setInventorySlotContents(x, pulled);
                }
            }

            final IRecipe rr = CraftingManager.findMatchingRecipe(real, p.world);

            if (rr == r && Platform.itemComparisons().isSameItem(rr.getCraftingResult(real), is)) {
                final InventoryCraftResult craftingResult = new InventoryCraftResult();
                craftingResult.setRecipeUsed(rr);

                final SlotCrafting sc = new SlotCrafting(p, real, craftingResult, 0, 0, 0);
                sc.onTake(p, is);

                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = playerInv.addItems(real.getStackInSlot(x));

                    if (!failed.isEmpty()) {
                        p.dropItem(failed, false);
                    }
                }

                inv.addItems(is);
                if (p instanceof EntityPlayerMP) {
                    this.updateHeld((EntityPlayerMP) p);
                }
                this.detectAndSendChanges();
            } else {
                for (int x = 0; x < real.getSizeInventory(); x++) {
                    final ItemStack failed = real.getStackInSlot(x);
                    if (!failed.isEmpty()) {
                        this.getCellInventory()
                                .injectItems(AEItemStack.fromItemStack(failed), Actionable.MODULATE,
                                        new MachineSource(this.getPart() != null ? this.getPart()
                                                : (IActionHost) iGuiItemObject));
                    }
                }
            }
        }
    }

    // ---- IVirtualSlotHolder 实现（客户端接收服务端推送的虚拟槽位数据）----

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        IAEStackInventory inv = null;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.getCraftingAEInv();
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.getOutputAEInv();
        }
        if (inv != null) {
            for (var entry : slotStacks.int2ObjectEntrySet()) {
                inv.putAEStackInSlot(entry.getIntKey(), entry.getValue());
            }
        }
    }

    // ---- IVirtualSlotSource 实现（服务端接收客户端发来的虚拟槽位更新）----

    @Override
    public void updateVirtualSlot(StorageName invName, int slotId, IAEStack<?> aes) {
        IAEStackInventory inv = null;
        if (invName == StorageName.CRAFTING_INPUT) {
            inv = this.getCraftingAEInv();
        } else if (invName == StorageName.CRAFTING_OUTPUT) {
            inv = this.getOutputAEInv();
        }
        if (inv != null && slotId >= 0 && slotId < inv.getSizeInventory()) {
            inv.putAEStackInSlot(slotId, aes);
        }
    }

    // ---- IAEStackInventory 访问器 ----

    public IAEStackInventory getCraftingAEInv() {
        if (this.patternTerminal != null) {
            return this.patternTerminal.getAEInventoryByName(StorageName.CRAFTING_INPUT);
        }
        return null;
    }

    public IAEStackInventory getOutputAEInv() {
        if (this.patternTerminal != null) {
            return this.patternTerminal.getAEInventoryByName(StorageName.CRAFTING_OUTPUT);
        }
        return null;
    }
}

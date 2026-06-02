package appeng.me.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.Actionable;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IStorageCell;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.ISaveProvider;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.util.item.AEStack;

@SuppressWarnings("rawtypes")
public class BasicCellInventory extends AbstractCellInventory {
    private final IAEStackType stackType;

    private BasicCellInventory(final IStorageCell cellType, final ItemStack o, final ISaveProvider container) {
        super(cellType, o, container);
        this.stackType = cellType.getStackType();
    }

    public static ICellInventory createInventory(final ItemStack o,
            final ISaveProvider container) {
        try {
            if (o == null) {
                throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
            }

            final Item type = o.getItem();
            final IStorageCell cellType;
            if (type instanceof IStorageCell) {
                cellType = (IStorageCell) type;
            } else {
                throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
            }

            if (!cellType.isStorageCell(o)) {
                throw new AppEngException("ItemStack was used as a cell, but was not a cell!");
            }

            return new BasicCellInventory(cellType, o, container);
        } catch (final AppEngException e) {
            AELog.error(e);
            return null;
        }
    }

    public static boolean isCellOfType(final ItemStack input, IAEStackType<?> channel) {
        final IStorageCell<?> type = getStorageCell(input);

        return type != null && type.getStackType() == channel;
    }

    public static boolean isCell(final ItemStack input) {
        return getStorageCell(input) != null;
    }

    /**
     * Check if the given stack represents a non-empty storage cell.
     * <p>
     * MC limitation: Only items can be storage cells (physical items that go into ME drives).
     * Fluids and other non-item types cannot be storage cells, so this check only applies
     * to IAEItemStack. This prevents storing a non-empty cell inside another cell.
     */
    private boolean isStorageCell(final GenericStack input) {
        if (input != null && input.what() instanceof AEItemKey itemKey) {
            final IStorageCell<?> type = getStorageCell(itemKey.toStack());
            return type != null && !type.storableInStorageCell();
        }
        return false;
    }

    private static IStorageCell<?> getStorageCell(final ItemStack input) {
        if (input != null) {
            final Item type = input.getItem();

            if (type instanceof IStorageCell) {
                return (IStorageCell<?>) type;
            }
        }

        return null;
    }

    private static boolean isCellEmpty(ICellInventory inv) {
        if (inv != null) {
            return inv.getAvailableKeyCounter().isEmpty();
        }
        return true;
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable mode, IActionSource src) {
        if (input == null) {
            return null;
        }
        if (input.amount() == 0) {
            return null;
        }

        if (this.isStorageCell(input)) {
            final ICellInventory meInventory = createInventory(
                    input.what() instanceof AEItemKey itemKey ? itemKey.toStack() : ItemStack.EMPTY, null);
            if (meInventory != null && !meInventory.getAvailableKeyCounter().isEmpty()) {
                return input;
            }
        }

        final AEKey key = input.what();
        final KeyCounter kc = this.getKeyCounter();
        final long existing = kc.get(key);

        if (existing > 0) {
            final long remainingItemCount = this.getRemainingItemCount();
            if (remainingItemCount <= 0) {
                return input;
            }

            if (input.amount() > remainingItemCount) {
                if (mode == Actionable.MODULATE) {
                    kc.set(key, existing + remainingItemCount);
                    this.saveChanges();
                }
                return new GenericStack(key, input.amount() - remainingItemCount);
            } else {
                if (mode == Actionable.MODULATE) {
                    kc.set(key, existing + input.amount());
                    this.saveChanges();
                }
                return null;
            }
        }

        if (this.canHoldNewItem()) // room for new type, and for at least one item!
        {
            final long remainingItemCount = this.getRemainingItemCount()
                    - (long) this.getBytesPerType() * this.itemsPerByte;
            if (remainingItemCount > 0) {
                if (input.amount() > remainingItemCount) {
                    if (mode == Actionable.MODULATE) {
                        kc.set(key, remainingItemCount);
                        this.saveChanges();
                    }
                    return new GenericStack(key, input.amount() - remainingItemCount);
                }

                if (mode == Actionable.MODULATE) {
                    kc.set(key, input.amount());
                    this.saveChanges();
                }

                return null;
            }
        }

        return input;
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        if (request == null) {
            return null;
        }

        final long size = Math.min(Integer.MAX_VALUE, request.amount());

        final AEKey key = request.what();
        final KeyCounter kc = this.getKeyCounter();
        final long existing = kc.get(key);

        if (existing <= 0) {
            return null;
        }

        if (existing <= size) {
            if (mode == Actionable.MODULATE) {
                kc.set(key, 0);
                this.saveChanges();
            }
            return new GenericStack(key, existing);
        } else {
            if (mode == Actionable.MODULATE) {
                kc.set(key, existing - size);
                this.saveChanges();
            }
            return new GenericStack(key, size);
        }
    }

    @Override
    protected boolean loadCellItem(NBTTagCompound compoundTag, long stackSize) {
        // Now load the item stack
        final IAEStack t;
        try {
            t = this.getStackType().createFromNBT(compoundTag);
            if (t == null) {
                AELog.warn("Removing item " + compoundTag
                        + " from storage cell because the associated item type couldn't be found.");
                return false;
            }
        } catch (Throwable ex) {
            if (AEConfig.instance().isRemoveCrashingItemsOnLoad()) {
                AELog.warn(ex,
                        "Removing item " + compoundTag + " from storage cell because loading the ItemStack crashed.");
                return false;
            }
            throw ex;
        }

        t.setStackSize(stackSize);
        t.setCraftable(false);

        if (stackSize > 0) {
            this.cellItems.add(t);
            AEKey k = t.toAEKey();
            if (k != null) {
                this.getKeyCounter().set(k, stackSize);
            }
        }

        return true;
    }
}

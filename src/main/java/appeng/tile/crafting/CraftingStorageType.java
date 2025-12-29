package appeng.tile.crafting;

import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.block.crafting.BlockCraftingUnit;

public enum CraftingStorageType {
    STORAGE_1K(1, 1024,
            () -> AEApi.instance().definitions().blocks().craftingStorage1k().maybeStack(1),
            BlockCraftingUnit.CraftingUnitType.STORAGE_1K),
    STORAGE_4K(4, 4096,
            () -> AEApi.instance().definitions().blocks().craftingStorage4k().maybeStack(1),
            BlockCraftingUnit.CraftingUnitType.STORAGE_4K),
    STORAGE_16K(16, 16384,
            () -> AEApi.instance().definitions().blocks().craftingStorage16k().maybeStack(1),
            BlockCraftingUnit.CraftingUnitType.STORAGE_16K),
    STORAGE_64K(64, 65536,
            () -> AEApi.instance().definitions().blocks().craftingStorage64k().maybeStack(1),
            BlockCraftingUnit.CraftingUnitType.STORAGE_64K);

    private final int kiloBytes;
    private final int storageBytes;
    private final Supplier<Optional<ItemStack>> itemStackSupplier;
    private final BlockCraftingUnit.CraftingUnitType blockType;

    CraftingStorageType(int kiloBytes, int storageBytes,
            Supplier<Optional<ItemStack>> itemStackSupplier,
            BlockCraftingUnit.CraftingUnitType blockType) {
        this.kiloBytes = kiloBytes;
        this.storageBytes = storageBytes;
        this.itemStackSupplier = itemStackSupplier;
        this.blockType = blockType;
    }

    public static Optional<CraftingStorageType> fromKiloBytes(int kiloBytes) {
        return Arrays.stream(values())
                .filter(type -> type.kiloBytes == kiloBytes)
                .findFirst();
    }

    public static Optional<CraftingStorageType> fromBlockType(BlockCraftingUnit.CraftingUnitType blockType) {
        return Arrays.stream(values())
                .filter(type -> type.blockType == blockType)
                .findFirst();
    }

    public static Optional<CraftingStorageType> fromItemStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        return Arrays.stream(values())
                .filter(type -> {
                    Optional<ItemStack> typeStack = type.getItemStack();
                    return typeStack.isPresent() &&
                            ItemStack.areItemsEqual(typeStack.get(), stack);
                })
                .findFirst();
    }

    // 从存储字节数获取类型
    public static Optional<CraftingStorageType> fromStorageBytes(int bytes) {
        return Arrays.stream(values())
                .filter(type -> type.storageBytes == bytes)
                .findFirst();
    }

    public Optional<ItemStack> getItemStack() {
        return itemStackSupplier.get();
    }

    public int getStorageBytes() {
        return storageBytes;
    }

    public int getKiloBytes() {
        return kiloBytes;
    }

    public BlockCraftingUnit.CraftingUnitType getBlockType() {
        return blockType;
    }
}

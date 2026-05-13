package appeng.helpers;

import static appeng.helpers.ItemStackHelper.stackFromNBT;

import java.util.*;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;

/**
 * Special processing pattern parser that supports empty output. Only applicable to processing mode (isCrafting=false); crafting mode must have output.
 * <p>
 * Supports multi-type stacks (items + fluids, etc.) via the generic {@code aeTypeId} NBT field for stack type identification.
 * Old format (plain item NBT) is also backward compatible.
 *
 * @deprecated Replaced by {@link UltimatePatternHelper}. This class is retained only for legacy code compatibility.
 */
@Deprecated
public class SpecialPatternHelper implements ICraftingPatternDetails, Comparable<SpecialPatternHelper> {

    // Constant definitions (consistent with original PatternHelper)
    public static final int PROCESSING_INPUT_HEIGHT = 4;
    public static final int PROCESSING_INPUT_WIDTH = 4;
    public static final int PROCESSING_INPUT_LIMIT = PatternHelper.PROCESSING_INPUT_LIMIT;
    public static final int PROCESSING_OUTPUT_LIMIT = 6;

    // Core fields
    private final ItemStack patternItem;
    private final boolean isCrafting;
    private final boolean canSubstitute;

    // Item-type inputs/outputs (backward compatible with legacy interface)
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] outputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] condensedOutputs;

    // Generic inputs/outputs (contains items + fluids and all other types)
    private final IAEStack<?>[] genericInputs;
    private final IAEStack<?>[] genericOutputs;
    private final IAEStack<?>[] genericCondensedInputs;
    private final IAEStack<?>[] genericCondensedOutputs;

    private final Map<Integer, List<IAEItemStack>> substituteInputs = new HashMap<>();
    private final IAEItemStack pattern;
    private int priority = 0;

    /**
     * Constructor: key modification - allows empty output in processing mode
     */
    public SpecialPatternHelper(final ItemStack is, final World w) {
        final NBTTagCompound encodedValue = is.getTagCompound();
        if (encodedValue == null) {
            throw new IllegalArgumentException("Invalid special pattern: missing NBT");
        }

        // Only supports processing mode (crafting mode must have output)
        this.isCrafting = encodedValue.getBoolean("crafting");
        if (this.isCrafting) {
            throw new IllegalArgumentException("Special patterns cannot be used for crafting recipes");
        }

        this.canSubstitute = false; // Processing mode does not support substitution
        this.patternItem = is;
        this.pattern = AEItemStack.fromItemStack(is);

        final List<IAEItemStack> inItems = new ArrayList<>();
        final List<IAEItemStack> outItems = new ArrayList<>();
        final List<IAEStack<?>> inGeneric = new ArrayList<>();
        final List<IAEStack<?>> outGeneric = new ArrayList<>();

        // ========== Parse inputs ==========
        final NBTTagList inTag = encodedValue.getTagList("in", 10);
        for (int x = 0; x < inTag.tagCount() && x < PROCESSING_INPUT_LIMIT; x++) {
            final NBTTagCompound ingredient = inTag.getCompoundTagAt(x);
            if (ingredient.isEmpty()) {
                inItems.add(null);
                inGeneric.add(null);
                continue;
            }

            // Try generic deserialization (with StackType key)
            IAEStack<?> generic = ingredient.hasKey("StackType") ? IAEStack.fromNBTGeneric(ingredient) : null;
            if (generic != null) {
                inGeneric.add(generic);
                inItems.add(Platform.stackConvert(generic));
            } else {
                // Fallback: treat as plain item
                final ItemStack gs = stackFromNBT(ingredient);
                if (!ingredient.isEmpty() && gs.isEmpty()) {
                    throw new IllegalArgumentException("Invalid input at slot " + x);
                }
                if (gs.isEmpty()) {
                    inItems.add(null);
                    inGeneric.add(null);
                } else {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.createStack(gs);
                    inItems.add(aeItem);
                    inGeneric.add(aeItem);
                }
            }
        }

        // ========== Parse outputs - key modification: allow empty output list ==========
        final NBTTagList outTag = encodedValue.getTagList("out", 10);
        for (int x = 0; x < outTag.tagCount() && x < PROCESSING_OUTPUT_LIMIT; x++) {
            final NBTTagCompound resultItemTag = outTag.getCompoundTagAt(x);
            if (resultItemTag.isEmpty()) {
                outItems.add(null);
                outGeneric.add(null);
                continue;
            }

            // Try generic deserialization (with StackType key)
            IAEStack<?> generic = resultItemTag.hasKey("StackType") ? IAEStack.fromNBTGeneric(resultItemTag) : null;
            if (generic != null) {
                outGeneric.add(generic);
                IAEItemStack converted = Platform.stackConvert(generic);
                if (converted != null) {
                    outItems.add(converted);
                }
            } else {
                // Fallback: treat as plain item
                final ItemStack gs = stackFromNBT(resultItemTag);
                if (!resultItemTag.isEmpty() && gs.isEmpty()) {
                    throw new IllegalArgumentException("Invalid output at slot " + x);
                }
                if (!gs.isEmpty()) {
                    IAEItemStack aeItem = AEItemStackType.INSTANCE.createStack(gs);
                    if (aeItem != null) {
                        outItems.add(aeItem);
                        outGeneric.add(aeItem);
                    }
                }
            }
        }

        while (outItems.size() < PROCESSING_OUTPUT_LIMIT) {
            outItems.add(null);
            outGeneric.add(null);
        }

        // Inputs cannot be empty (a pattern with no inputs is meaningless)
        if (inItems.isEmpty() || inItems.stream().allMatch(Objects::isNull)) {
            throw new IllegalStateException("Special pattern requires at least one input");
        }

        // Outputs can be empty - key modification
        // Original logic: if (tmpOutputs.isEmpty() || tmpInputs.isEmpty()) throw ...
        // New logic: only check inputs are non-empty, outputs are allowed to be empty

        // ========== Item-type array initialization ==========

        // Initialize input array (fixed size)
        this.inputs = new IAEItemStack[Math.max(PROCESSING_INPUT_LIMIT, inItems.size())];
        for (int i = 0; i < Math.min(inItems.size(), this.inputs.length); i++) {
            this.inputs[i] = inItems.get(i);
        }

        // Initialize output array (dynamic size)
        this.outputs = outItems.toArray(new IAEItemStack[PROCESSING_OUTPUT_LIMIT]);

        // Condense item inputs (merge same items)
        final Map<IAEItemStack, IAEItemStack> tmpInputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.inputs) {
            if (io == null) {
                continue;
            }
            tmpInputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedInputs = tmpInputs.values().toArray(new IAEItemStack[0]);

        // Condense item outputs (allow empty)
        final Map<IAEItemStack, IAEItemStack> tmpOutputs = new Object2ObjectOpenHashMap<>();
        for (final IAEItemStack io : this.outputs) {
            if (io == null) {
                continue;
            }
            tmpOutputs.merge(io, io.copy(), (a, b) -> {
                a.add(b);
                return a;
            });
        }
        this.condensedOutputs = tmpOutputs.values().toArray(new IAEItemStack[0]);

        // ========== Generic array initialization ==========

        // Generic input array (fixed size, preserving slot positions)
        this.genericInputs = new IAEStack<?>[Math.max(PROCESSING_INPUT_LIMIT, inGeneric.size())];
        for (int i = 0; i < Math.min(inGeneric.size(), this.genericInputs.length); i++) {
            this.genericInputs[i] = inGeneric.get(i);
        }

        // Generic output array
        this.genericOutputs = outGeneric.toArray(new IAEStack<?>[PROCESSING_OUTPUT_LIMIT]);

        // Condense generic inputs
        this.genericCondensedInputs = condenseGenericList(this.genericInputs);

        // Condense generic outputs
        this.genericCondensedOutputs = condenseGenericList(this.genericOutputs);
    }

    // ===== Interface implementation =====

    @Override
    public boolean isCraftable() {
        return false; // Special patterns are for processing only
    }

    // ========== Generic entry point methods (supports items + fluids and other types) ==========

    @Override
    public IAEStack<?>[] getAEInputs() {
        return this.genericInputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return this.genericCondensedInputs;
    }

    @Override
    public IAEStack<?>[] getAEOutputs() {
        return this.genericOutputs;
    }

    @Override
    public IAEStack<?>[] getCondensedAEOutputs() {
        return this.genericCondensedOutputs;
    }

    @Override
    public boolean canSubstitute() {
        return false; // Processing mode does not support substitution
    }

    @Override
    public List<IAEItemStack> getSubstituteInputs(int slot) {
        return Collections.emptyList(); // No substitute items
    }

    @Override
    public ItemStack getPattern() {
        return patternItem;
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack i, World w) {
        // Processing mode does not validate slots (handled by the machine)
        return slotIndex >= 0 && slotIndex < inputs.length;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World w) {
        // Empty output semantics: return EMPTY indicating no item output
        return ItemStack.EMPTY;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    // ===== Utility methods =====

    /**
     * Check whether this is an empty-output pattern (legitimate empty output)
     */
    public boolean hasEmptyOutput() {
        return genericOutputs.length == 0 || Arrays.stream(genericOutputs).allMatch(Objects::isNull);
    }

    @SuppressWarnings("unchecked")
    private static IAEStack<?>[] condenseGenericList(IAEStack<?>[] items) {
        final LinkedHashMap<IAEStack<?>, IAEStack<?>> tmp = new LinkedHashMap<>();
        for (IAEStack<?> io : items) {
            if (io == null) {
                continue;
            }
            IAEStack g = tmp.get(io);
            if (g == null) {
                tmp.put(io, io.copy());
            } else {
                g.add(io);
            }
        }
        return tmp.values().toArray(new IAEStack<?>[0]);
    }

    // ===== Comparison and hashing =====

    @Override
    public int compareTo(SpecialPatternHelper o) {
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public int hashCode() {
        return pattern.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof SpecialPatternHelper other))
            return false;
        return Objects.equals(pattern, other.pattern);
    }
}

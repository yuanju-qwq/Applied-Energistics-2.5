package appeng.api.networking.crafting;

import java.util.HashMap;
import java.util.Map;

import com.github.bsideup.jabel.Desugar;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

@Desugar
public record CraftingPlan(
        GenericStack finalOutput,
        long bytes,
        boolean simulation,
        KeyCounter availableItems,
        KeyCounter missingItems,
        KeyCounter emittedItems,
        Map<ICraftingPatternDetails, Long> patternTimes) {

    public CraftingPlan merge(CraftingPlan other) {
        var mergedAvailable = new KeyCounter();
        mergedAvailable.addAll(this.availableItems);
        mergedAvailable.addAll(other.availableItems);

        var mergedMissing = new KeyCounter();
        mergedMissing.addAll(this.missingItems);
        mergedMissing.addAll(other.missingItems);

        var mergedEmitted = new KeyCounter();
        mergedEmitted.addAll(this.emittedItems);
        mergedEmitted.addAll(other.emittedItems);

        var mergedPatternTimes = new HashMap<>(this.patternTimes);
        for (var entry : other.patternTimes.entrySet()) {
            mergedPatternTimes.merge(entry.getKey(), entry.getValue(), Long::sum);
        }

        return new CraftingPlan(
                this.finalOutput,
                this.bytes,
                this.simulation,
                mergedAvailable,
                mergedMissing,
                mergedEmitted,
                mergedPatternTimes);
    }

    public static class Builder {
        private final KeyCounter availableItems = new KeyCounter();
        private final KeyCounter missingItems = new KeyCounter();
        private final KeyCounter emittedItems = new KeyCounter();
        private final Map<ICraftingPatternDetails, Long> patternTimes = new HashMap<>();

        public void addAvailable(AEKey key, long amount) {
            availableItems.add(key, amount);
        }

        public void addMissing(AEKey key, long amount) {
            missingItems.add(key, amount);
        }

        public void addEmitted(AEKey key, long amount) {
            emittedItems.add(key, amount);
        }

        public void addPatternTime(ICraftingPatternDetails pattern, long times) {
            patternTimes.merge(pattern, times, Long::sum);
        }

        public void addAll(Builder other) {
            availableItems.addAll(other.availableItems);
            missingItems.addAll(other.missingItems);
            emittedItems.addAll(other.emittedItems);
            for (var entry : other.patternTimes.entrySet()) {
                patternTimes.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }

        public CraftingPlan build(GenericStack finalOutput, long bytes, boolean simulation) {
            return new CraftingPlan(finalOutput, bytes, simulation,
                    availableItems, missingItems, emittedItems, patternTimes);
        }
    }
}

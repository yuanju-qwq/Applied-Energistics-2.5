package appeng.helpers;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.util.Platform;

public class NonBlockingItems {
    public static Map<String, Object2ObjectOpenHashMap<Item, IntSet>> NON_BLOCKING_MAP = new HashMap<>();
    public static NonBlockingItems INSTANCE = new NonBlockingItems();

    private NonBlockingItems() {
        String[] strings = AEConfig.instance().getNonBlockingItems();
        String[] modids = new String[0];
        for (String s : strings) {
            if (s.startsWith("[") && s.endsWith("]")) {
                modids = s.substring(1, s.length() - 1).split("\\|");
            } else {
                for (String modid : modids) {
                    if (!Platform.isModLoaded(modid)) {
                        continue;
                    }
                    NON_BLOCKING_MAP.putIfAbsent(modid, new Object2ObjectOpenHashMap<>());

                    String[] ModItemMeta = s.split(":");

                    if (ModItemMeta.length < 2 || ModItemMeta.length > 3) {
                        AELog.error("Invalid non blocking item entry: " + s);
                        continue;
                    }

                    if (ModItemMeta[0].equals("gregtech") && Platform.GTLoaded) {
                        // GT MetaItem lookup — injected by GregTech via Mixin
                        boolean found = lookupGTMetaItem(modid, ModItemMeta, s);
                        if (!found) {
                            AELog.error("Item not found on nonBlocking config: " + s);
                        }
                    } else if (ModItemMeta[0].equals("ore")) {
                        OreDictionary.getOres(ModItemMeta[1]).forEach(itemStack -> {
                            NON_BLOCKING_MAP.get(modid).putIfAbsent(itemStack.getItem(), new IntOpenHashSet());
                            NON_BLOCKING_MAP.get(modid).computeIfPresent(itemStack.getItem(), (item, intSet) -> {
                                intSet.add(itemStack.getItemDamage());
                                return intSet;
                            });
                        });
                    } else {
                        ItemStack itemStack = GameRegistry.makeItemStack(ModItemMeta[0] + ":" + ModItemMeta[1],
                                ModItemMeta.length == 3 ? Integer.parseInt(ModItemMeta[2]) : 0, 1, null);
                        if (!itemStack.isEmpty()) {
                            NON_BLOCKING_MAP.get(modid).putIfAbsent(itemStack.getItem(), new IntOpenHashSet());
                            NON_BLOCKING_MAP.get(modid).computeIfPresent(itemStack.getItem(), (item, intSet) -> {
                                intSet.add(itemStack.getItemDamage());
                                return intSet;
                            });
                        } else {
                            AELog.error("Item not found on nonBlocking config: " + s);
                        }
                    }
                }
            }
        }
    }

    public Map<String, Object2ObjectOpenHashMap<Item, IntSet>> getMap() {
        return NON_BLOCKING_MAP;
    }

    /**
     * Lookup GT MetaItem and register it into NON_BLOCKING_MAP.
     * Base implementation returns false, actual logic injected by GregTech via Mixin.
     *
     * @param modid the mod ID
     * @param modItemMeta the split mod:item:meta array
     * @param rawEntry the raw config string entry
     * @return true if the item was found
     */
    protected boolean lookupGTMetaItem(String modid, String[] modItemMeta, String rawEntry) {
        return false;
    }

    public void init() {
    }
}

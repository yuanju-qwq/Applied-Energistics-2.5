package appeng.util.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

/**
 * 基于矿物词典的多值映射，用于 v2 合成树中的模糊 pattern 匹配。
 *
 * @param <T> 值类型（通常是 ICraftingPatternDetails）
 */
public class OreListMultiMap<T> {

    private ImmutableListMultimap<Integer, T> map;
    private ImmutableListMultimap.Builder<Integer, T> builder = new ImmutableListMultimap.Builder<>();
    private boolean populated = false;

    private static Collection<Integer> getOreIds(IAEItemStack stack) {
        if (stack == null) {
            return Collections.emptyList();
        }
        ItemStack is = stack.createItemStack();
        if (is.isEmpty()) {
            return Collections.emptyList();
        }
        int[] ids = OreDictionary.getOreIDs(is);
        if (ids.length == 0) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<>(ids.length);
        for (int id : ids) {
            result.add(id);
        }
        return result;
    }

    public boolean isPopulated() {
        return populated;
    }

    public void put(IAEItemStack key, T val) {
        for (Integer oreId : getOreIds(key)) {
            builder.put(oreId, val);
        }
    }

    public void freeze() {
        map = builder.build();
        builder = new ImmutableListMultimap.Builder<>();
        populated = true;
    }

    public ImmutableList<T> get(IAEItemStack key) {
        if (map == null) {
            return ImmutableList.of();
        }
        Collection<Integer> ids = getOreIds(key);
        if (ids.isEmpty()) {
            return ImmutableList.of();
        }
        if (ids.size() == 1) {
            return map.get(ids.iterator().next());
        }
        ImmutableList.Builder<T> b = ImmutableList.builder();
        for (Integer id : ids) {
            b.addAll(map.get(id));
        }
        return b.build();
    }

    public void clear() {
        map = null;
        populated = false;
        builder = new ImmutableListMultimap.Builder<>();
    }
}

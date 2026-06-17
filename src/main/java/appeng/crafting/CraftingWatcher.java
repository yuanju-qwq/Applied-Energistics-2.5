package appeng.crafting;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.stacks.AEKey;
import appeng.me.cache.CraftingGridCache;

public class CraftingWatcher implements ICraftingWatcher {

    private final CraftingGridCache gsc;
    private final ICraftingWatcherHost host;
    private final Set<AEKey> myInterests = new HashSet<>();

    public CraftingWatcher(final CraftingGridCache cache, final ICraftingWatcherHost host) {
        this.gsc = cache;
        this.host = host;
    }

    public ICraftingWatcherHost getHost() {
        return this.host;
    }

    @Override
    public boolean add(final AEKey key) {
        if (this.myInterests.contains(key)) {
            return false;
        }

        return this.myInterests.add(key) && this.gsc.getInterestManager().put(key, this);
    }

    @Override
    public boolean remove(final AEKey key) {
        return this.myInterests.remove(key) && this.gsc.getInterestManager().remove(key, this);
    }

    @Override
    public void reset() {
        final Iterator<AEKey> i = this.myInterests.iterator();

        while (i.hasNext()) {
            this.gsc.getInterestManager().remove(i.next(), this);
            i.remove();
        }
    }
}

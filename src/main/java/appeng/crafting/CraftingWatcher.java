package appeng.crafting;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingWatcher;
import appeng.api.networking.crafting.ICraftingWatcherHost;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.CraftingGridCache;

public class CraftingWatcher implements ICraftingWatcher {

    private final CraftingGridCache gsc;
    private final ICraftingWatcherHost host;
    private final Set<IAEStack> myInterests = new HashSet<>();

    public CraftingWatcher(final CraftingGridCache cache, final ICraftingWatcherHost host) {
        this.gsc = cache;
        this.host = host;
    }

    public ICraftingWatcherHost getHost() {
        return this.host;
    }

    @Override
    public boolean add(final IAEStack e) {
        if (this.myInterests.contains(e)) {
            return false;
        }

        return this.myInterests.add(e.copy()) && this.gsc.getInterestManager().put(e, this);
    }

    @Override
    public boolean remove(final IAEStack o) {
        return this.myInterests.remove(o) && this.gsc.getInterestManager().remove(o, this);
    }

    @Override
    public void reset() {
        final Iterator<IAEStack> i = this.myInterests.iterator();

        while (i.hasNext()) {
            this.gsc.getInterestManager().remove(i.next(), this);
            i.remove();
        }
    }
}
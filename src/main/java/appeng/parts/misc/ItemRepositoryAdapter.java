package appeng.parts.misc;

import java.util.*;

import com.google.common.primitives.Ints;
import com.jaquadro.minecraft.storagedrawers.api.capabilities.IItemRepository;

import net.minecraft.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.core.AELog;
import appeng.me.GridAccessException;
import appeng.me.helpers.IGridProxyable;
import appeng.me.storage.ITickingMonitor;
import appeng.util.item.AEItemStack;

/**
 * Wraps an Item Repository in such a way that it can be used as an IMEInventory for items. Used by the Storage Bus
 */

class ItemRepositoryAdapter implements IMEInventory, IBaseMonitor, ITickingMonitor {
    private final Object2ObjectMap<IMEMonitorHandlerReceiver, Object> listeners = new Object2ObjectOpenHashMap<>();
    private IActionSource mySource;
    private final IItemRepository itemRepository;
    private final IGridProxyable proxyable;
    private final InventoryCache cache;
    private AccessRestriction access;

    ItemRepositoryAdapter(IItemRepository itemRepository, IGridProxyable proxy) {
        this.itemRepository = itemRepository;
        this.proxyable = proxy;
        this.cache = new InventoryCache(this.itemRepository);
        if (this.proxyable instanceof AbstractPartStorageBus) {
            AbstractPartStorageBus partStorageBus = (AbstractPartStorageBus) this.proxyable;
            this.access = ((AccessRestriction) partStorageBus.getConfigManager().getSetting(Settings.ACCESS));
        }
        this.cache.update();
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable type, IActionSource src) {
        if (input == null) return null;
        if (!(input.what() instanceof AEItemKey itemKey)) return input;

        long amount = input.amount();
        ItemStack stack = itemKey.toStack(Ints.saturatedCast(amount));
        ItemStack remaining = this.itemRepository.insertItem(stack, type == Actionable.SIMULATE);

        if (remaining == stack) {
            return input;
        }

        if (type == Actionable.MODULATE) {
            long added = amount - remaining.getCount();
            this.cache.currentlyCached.add(itemKey, added);
            this.postDifference(Collections.singletonList(new GenericStack(itemKey, added)));
            try {
                this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
            } catch (GridAccessException ex) {
                // meh
            }
        }

        return remaining.isEmpty() ? null : new GenericStack(itemKey, remaining.getCount());
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        if (request == null) return null;
        if (!(request.what() instanceof AEItemKey itemKey)) return null;

        int remainingSize = Ints.saturatedCast(request.amount());
        final boolean simulate = (mode == Actionable.SIMULATE);

        ItemStack extracted = this.itemRepository.extractItem(
                itemKey.toStack(),
                remainingSize,
                simulate);

        if (extracted.getCount() > remainingSize) {
            AELog.warn("Mod that provided item handler %s is broken. Returned %s items while only requesting %d.",
                    this.itemRepository.getClass().getName(), extracted.toString(), remainingSize);
            extracted.setCount(remainingSize);
        }

        if (!extracted.isEmpty()) {
            if (mode == Actionable.MODULATE) {
                long cachedAmount = this.cache.currentlyCached.get(itemKey);
                if (cachedAmount > 0) {
                    this.cache.currentlyCached.add(itemKey, -extracted.getCount());
                    this.postDifference(Collections.singletonList(new GenericStack(itemKey, -extracted.getCount())));
                }
                try {
                    this.proxyable.getProxy().getTick().alertDevice(this.proxyable.getProxy().getNode());
                } catch (GridAccessException ex) {
                    // meh
                }
            }
            return new GenericStack(itemKey, extracted.getCount());
        }
        return null;
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.cache.getAvailableKeyCounter();
    }

    @Override
    public AEKeyType getKeyType() {
        return AEKeyType.items();
    }

    @Override
    public void addListener(IMEMonitorHandlerReceiver l, Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    private void postDifference(Iterable<GenericStack> a) {
        final Iterator<Map.Entry<IMEMonitorHandlerReceiver, Object>> i = this.listeners.entrySet()
                .iterator();
        while (i.hasNext()) {
            final Map.Entry<IMEMonitorHandlerReceiver, Object> l = i.next();
            final IMEMonitorHandlerReceiver key = l.getKey();
            if (key.isValid(l.getValue())) {
                key.postChange(this, a, this.mySource);
            } else {
                i.remove();
            }
        }
    }

    @Override
    public TickRateModulation onTick() {
        List<GenericStack> changes = this.cache.update();
        if (!changes.isEmpty() && access.hasPermission(AccessRestriction.READ)) {
            this.postDifference(changes);
            return TickRateModulation.URGENT;
        } else {
            return TickRateModulation.SLOWER;
        }
    }

    @Override
    public void setActionSource(final IActionSource mySource) {
        this.mySource = mySource;
    }

    private static class InventoryCache {
        private KeyCounter currentlyCached = new KeyCounter();
        private final IItemRepository iItemRepository;

        public InventoryCache(IItemRepository iItemRepository) {
            this.iItemRepository = iItemRepository;
        }

        public KeyCounter getAvailableKeyCounter() {
            KeyCounter out = new KeyCounter();
            for (var entry : currentlyCached) {
                out.add(entry.getKey(), entry.getLongValue());
            }
            return out;
        }

        public List<GenericStack> update() {
            final List<GenericStack> changes = new ArrayList<>();

            KeyCounter currentlyOnStorage = new KeyCounter();
            this.iItemRepository.getAllItems().stream()
                    .map(s -> AEItemStack.fromItemStack(s.itemPrototype).setStackSize(s.count))
                    .forEach(aeStack -> {
                        if (aeStack != null) {
                            currentlyOnStorage.add(aeStack.toAEKey(), aeStack.getStackSize());
                        }
                    });

            // Items removed or changed
            for (var entry : currentlyCached) {
                AEKey key = entry.getKey();
                long oldAmount = entry.getLongValue();
                long newAmount = currentlyOnStorage.get(key);
                long diff = newAmount - oldAmount;
                if (diff != 0) {
                    changes.add(new GenericStack(key, diff));
                }
            }

            // New items
            for (var entry : currentlyOnStorage) {
                AEKey key = entry.getKey();
                if (currentlyCached.get(key) == 0) {
                    changes.add(new GenericStack(key, entry.getLongValue()));
                }
            }

            currentlyCached = currentlyOnStorage;

            return changes;
        }

    }
}

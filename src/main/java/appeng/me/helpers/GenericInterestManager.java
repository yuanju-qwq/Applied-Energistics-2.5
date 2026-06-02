package appeng.me.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Multimap;

import appeng.api.storage.data.IAEStack;

public class GenericInterestManager<K, T> {

    private final Multimap<K, T> container;
    private List<SavedTransactions> transactions = null;
    private int transDepth = 0;

    public GenericInterestManager(final Multimap<K, T> interests) {
        this.container = interests;
    }

    public void enableTransactions() {
        if (this.transDepth == 0) {
            this.transactions = new ArrayList<>();
        }

        this.transDepth++;
    }

    public void disableTransactions() {
        this.transDepth--;

        if (this.transDepth == 0) {
            final List<SavedTransactions> myActions = this.transactions;
            this.transactions = null;

            for (final SavedTransactions t : myActions) {
                if (t.put) {
                    this.put(t.key, t.iw);
                } else {
                    this.remove(t.key, t.iw);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public boolean put(final K stack, final T iw) {
        if (this.transactions != null) {
            this.transactions.add(new SavedTransactions(true, stack, iw));
            return true;
        } else {
            return this.container.put(stack, iw);
        }
    }

    @SuppressWarnings("unchecked")
    public boolean remove(final K stack, final T iw) {
        if (this.transactions != null) {
            this.transactions.add(new SavedTransactions(false, stack, iw));
            return true;
        } else {
            return this.container.remove(stack, iw);
        }
    }

    public boolean containsKey(final K stack) {
        return this.container.containsKey(stack);
    }

    public Collection<T> get(final K stack) {
        return this.container.get(stack);
    }

    private class SavedTransactions {

        private final boolean put;
        private final K key;
        private final T iw;

        public SavedTransactions(final boolean putOperation, final K myStack, final T watcher) {
            this.put = putOperation;
            this.key = myStack;
            this.iw = watcher;
        }
    }
}
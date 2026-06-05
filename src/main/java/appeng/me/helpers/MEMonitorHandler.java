/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.me.helpers;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;

/**
 * Common implementation of a simple class that monitors injection/extraction of a inventory to send events to a list of
 * listeners.
 */
@SuppressWarnings("rawtypes")
public class MEMonitorHandler implements IMEMonitor {

    private final IMEInventoryHandler internalHandler;
    private final KeyCounter cachedKeyCounter;
    private final HashMap<IMEMonitorHandlerReceiver, Object> listeners = new HashMap<>();

    protected boolean hasChanged = true;

    public MEMonitorHandler(final IMEInventoryHandler t) {
        this.internalHandler = t;
        this.cachedKeyCounter = new KeyCounter();
    }

    @Override
    public void addListener(final IMEMonitorHandlerReceiver l, final Object verificationToken) {
        this.listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(final IMEMonitorHandlerReceiver l) {
        this.listeners.remove(l);
    }

    @Override
    public GenericStack injectItems(GenericStack input, Actionable mode, IActionSource src) {
        return this.getHandler().injectItems(input, mode, src);
    }

    protected IMEInventoryHandler getHandler() {
        return this.internalHandler;
    }

    public void postChangesToListeners(final Iterable<GenericStack> changes, final IActionSource src) {
        this.notifyListenersOfChange(changes, src);
    }

    protected void notifyListenersOfChange(final Iterable<GenericStack> diff, final IActionSource src) {
        this.hasChanged = true;
        final Iterator<Entry<IMEMonitorHandlerReceiver, Object>> i = this.getListeners();
        while (i.hasNext()) {
            final Entry<IMEMonitorHandlerReceiver, Object> o = i.next();
            final IMEMonitorHandlerReceiver receiver = o.getKey();
            if (receiver.isValid(o.getValue())) {
                receiver.postChange(this, diff, src);
            } else {
                i.remove();
            }
        }
    }

    protected Iterator<Entry<IMEMonitorHandlerReceiver, Object>> getListeners() {
        return this.listeners.entrySet().iterator();
    }

    @Override
    public GenericStack extractItems(GenericStack request, Actionable mode, IActionSource src) {
        return this.getHandler().extractItems(request, mode, src);
    }

    @Override
    public AEKeyType getKeyType() {
        return this.getHandler().getKeyType();
    }

    @Override
    public AccessRestriction getAccess() {
        return this.getHandler().getAccess();
    }

    @Override
    public boolean isPrioritized(final AEKey input) {
        return this.getHandler().isPrioritized(input);
    }

    @Override
    public boolean canAccept(final AEKey input) {
        return this.getHandler().canAccept(input);
    }

    @Override
    public KeyCounter getAvailableKeyCounter() {
        return this.getHandler().getAvailableKeyCounter();
    }

    @Override
    public KeyCounter getKeyCounter() {
        return this.getAvailableKeyCounter();
    }

    @Override
    public int getPriority() {
        return this.getHandler().getPriority();
    }

    @Override
    public int getSlot() {
        return this.getHandler().getSlot();
    }

    @Override
    public boolean validForPass(final int i) {
        return this.getHandler().validForPass(i);
    }

    @Override
    public boolean isSticky() {
        return this.internalHandler.isSticky();
    }
}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util;

import java.util.EnumSet;

import appeng.api.config.SearchBoxMode;
import appeng.api.config.SortOrder;
import appeng.integration.Integrations;
import appeng.integration.modules.bogosorter.InventoryBogoSortModule;

public final class EnumCycler {
    private EnumCycler() {
    }

    public static <E extends Enum<E>> E rotateEnum(final E ce, final boolean backwards,
            final EnumSet<? extends Enum<?>> validOptions) {
        return rotateEnumTyped(ce, backwards, validOptions);
    }

    public static Enum<?> rotateEnumWildcard(final Enum<?> ce, final boolean backwards,
            final EnumSet<? extends Enum<?>> validOptions) {
        return rotateEnumTyped(ce, backwards, validOptions);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> E rotateEnumTyped(final Enum<?> ce, final boolean backwards,
            final EnumSet<? extends Enum<?>> validOptions) {
        final E typedValue = (E) ce;
        final EnumSet<E> typedOptions = (EnumSet<E>) validOptions;
        E current = typedValue;
        do {
            if (backwards) {
                current = prevEnum(current);
            } else {
                current = nextEnum(current);
            }
        } while (!typedOptions.contains(current) || isNotValidSetting(current));

        return current;
    }

    /*
     * Simple way to cycle an enum...
     */
    private static <E extends Enum<E>> E prevEnum(final E ce) {
        final EnumSet<E> valList = EnumSet.allOf(ce.getDeclaringClass());

        int pLoc = ce.ordinal() - 1;
        if (pLoc < 0) {
            pLoc = valList.size() - 1;
        }

        if (pLoc < 0 || pLoc >= valList.size()) {
            pLoc = 0;
        }

        int pos = 0;
        for (final E g : valList) {
            if (pos == pLoc) {
                return g;
            }
            pos++;
        }

        return null;
    }

    /*
     * Simple way to cycle an enum...
     */
    public static <E extends Enum<E>> E nextEnum(final E ce) {
        final EnumSet<E> valList = EnumSet.allOf(ce.getDeclaringClass());

        int pLoc = ce.ordinal() + 1;
        if (pLoc >= valList.size()) {
            pLoc = 0;
        }

        if (pLoc < 0 || pLoc >= valList.size()) {
            pLoc = 0;
        }

        int pos = 0;
        for (final E g : valList) {
            if (pos == pLoc) {
                return g;
            }
            pos++;
        }

        return null;
    }

    private static boolean isNotValidSetting(final Enum<?> e) {
        if (e == SortOrder.INVTWEAKS && !Integrations.invTweaks().isEnabled() && !InventoryBogoSortModule.isLoaded()) {
            return true;
        }

        final boolean isJEI = e == SearchBoxMode.JEI_AUTOSEARCH || e == SearchBoxMode.JEI_AUTOSEARCH_KEEP
                || e == SearchBoxMode.JEI_MANUAL_SEARCH || e == SearchBoxMode.JEI_MANUAL_SEARCH_KEEP;
        return isJEI && !Integrations.jei().isEnabled();
    }
}

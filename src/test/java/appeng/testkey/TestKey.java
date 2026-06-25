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

package appeng.testkey;

import java.util.Objects;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStack;

/**
 * Minimal {@link AEKey} test double used by AEKey-only infrastructure unit tests.
 *
 * <p>Each instance carries a monotonically increasing {@code id} that serves as both
 * the equality key and the {@link #getPrimaryKey() primary key} (boxed via
 * {@link Integer#valueOf(int)} so the {@link appeng.api.stacks.KeyCounter}'s
 * {@link java.util.IdentityHashMap} can locate entries for ids in the JVM's
 * integer cache range).
 *
 * <p>Minecraft-runtime-dependent methods ({@link #asItemStackRepresentation()} and
 * {@link #toIAEStack(long)}) throw {@link UnsupportedOperationException} because
 * the test infrastructure never calls them.
 */
public final class TestKey extends AEKey {

    private final int id;
    private final Integer primaryKey;

    public TestKey(int id) {
        this.id = id;
        // Integer.valueOf caches values in [-128, 127], sufficient for test sizes.
        this.primaryKey = Integer.valueOf(id);
    }

    public int getId() {
        return id;
    }

    @Override
    public AEKeyType getType() {
        return TestKeyType.INSTANCE;
    }

    @Override
    public AEKey dropSecondary() {
        return this;
    }

    @Override
    public NBTTagCompound toTag() {
        return new NBTTagCompound();
    }

    @Override
    public Object getPrimaryKey() {
        return primaryKey;
    }

    @Override
    public String getModId() {
        return "ae2_test";
    }

    @Override
    public String getDisplayName() {
        return "TestKey#" + id;
    }

    @Override
    public void writeToPacket(PacketBuffer data) {
        // No-op; tests do not exercise packet serialization.
    }

    @Override
    public ItemStack asItemStackRepresentation() {
        throw new UnsupportedOperationException(
                "TestKey does not support ItemStack representation; tests must not call this.");
    }

    @Override
    public IAEStack<?> toIAEStack(long amount) {
        throw new UnsupportedOperationException(
                "TestKey does not support legacy IAEStack bridge; tests must not call this.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestKey)) return false;
        return id == ((TestKey) o).id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TestKey#" + id;
    }
}

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.data.IAEStack;

/**
 * Minimal {@link AEKeyType} test double registered under the unique id {@code "ae2_test"}.
 *
 * <p>The type self-registers into the {@link AEKeyType} registry on first class-load, which
 * is acceptable for unit tests since the id does not collide with production types
 * ({@code "item"}, {@code "fluid"}). All legacy bridge methods return {@code null} as the
 * test infrastructure does not exercise legacy IAEStack interop.
 */
public final class TestKeyType extends AEKeyType {

    /** Singleton instance, registered once per JVM. */
    public static final TestKeyType INSTANCE = new TestKeyType();

    /** Unique registry id; intentionally distinct from production ids. */
    public static final String ID = "ae2_test";

    private TestKeyType() {
        super(ID, TestKey.class, "AE2 Test Key");
    }

    @Override
    public TextFormatting getColorDefinition() {
        return TextFormatting.WHITE;
    }

    @Nullable
    @Override
    public ResourceLocation getButtonTexture() {
        return null;
    }

    @Override
    public int getButtonIconU() {
        return 0;
    }

    @Override
    public int getButtonIconV() {
        return 0;
    }

    @Nullable
    @Override
    public AEKey loadKeyFromTag(@Nonnull NBTTagCompound tag) {
        return null;
    }

    @Nullable
    @Override
    public AEKey readFromPacket(@Nonnull PacketBuffer input) {
        return null;
    }

    // ========== Legacy IAEStack bridge methods (no-ops for tests) ==========

    @Nullable
    @Override
    public IAEStack<?> loadStackFromNBT(@Nonnull NBTTagCompound tag) {
        return null;
    }

    @Nullable
    @Override
    public IAEStack<?> loadStackFromPacket(@Nonnull ByteBuf buffer) {
        return null;
    }

    @Nullable
    @Override
    public IAEStack<?> createStack(@Nonnull Object input) {
        return null;
    }

    @Nullable
    @Override
    public IAEStack<?> getStackFromContainerItem(@Nonnull ItemStack container) {
        return null;
    }
}

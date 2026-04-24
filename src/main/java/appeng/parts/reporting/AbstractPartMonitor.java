/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
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

package appeng.parts.reporting;

import java.io.IOException;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.implementations.parts.IPartStorageMonitor;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.render.TesrRenderHelper;
import appeng.api.parts.ConversionMonitorHandlerRegistry;
import appeng.api.parts.IConversionMonitorHandler;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.Reflected;
import appeng.me.GridAccessException;
import appeng.util.IWideReadableNumberConverter;
import appeng.util.Platform;
import appeng.util.ReadableNumberConverter;

/**
 * A basic subclass for any item monitor like display with an item icon and an amount.
 * <p>
 * It can also be used to extract items from somewhere and spawned into the world.
 *
 * @author AlgorithmX2
 * @author thatsIch
 * @author yueh
 * @version rv3
 * @since rv3
 */
public abstract class AbstractPartMonitor extends AbstractPartDisplay
        implements IPartStorageMonitor, IStackWatcherHost {
    private static final IWideReadableNumberConverter NUMBER_CONVERTER = ReadableNumberConverter.INSTANCE;

    // Unified configured stack (supports item, fluid, or any registered IAEStackType)
    @Nullable
    private IAEStack<?> configured;
    private String lastHumanReadableText;
    private boolean isLocked;
    private IStackWatcher myWatcher;

    @Reflected
    public AbstractPartMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);

        this.isLocked = data.getBoolean("isLocked");

        final NBTTagCompound stackTag = data.getCompoundTag("configured");
        this.configured = IAEStack.fromNBTGeneric(stackTag);
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("isLocked", this.isLocked);

        final NBTTagCompound stackTag = this.configured != null ? this.configured.toNBTGeneric() : new NBTTagCompound();
        data.setTag("configured", stackTag);
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);

        data.writeBoolean(this.isLocked);
        IAEStack.writeToPacketGeneric(data, this.configured);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);

        final boolean isLocked = data.readBoolean();
        needRedraw = this.isLocked != isLocked;

        this.isLocked = isLocked;

        final IAEStack<?> old = this.configured;
        this.configured = IAEStack.fromPacketGeneric(data);
        if (!java.util.Objects.equals(old, this.configured)) {
            needRedraw = true;
        }

        return needRedraw;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (!this.isLocked) {
            final ItemStack eq = player.getHeldItem(hand);

            // Try each registered handler to resolve the held item into a configured stack
            IAEStack<?> resolved = null;
            if (!eq.isEmpty()) {
                for (IConversionMonitorHandler<?> handler : ConversionMonitorHandlerRegistry.getAllHandlers()) {
                    resolved = handler.resolveConfiguredStack(eq);
                    if (resolved != null) {
                        break;
                    }
                }
            }

            this.configured = resolved;

            this.configureWatchers();
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        } else {
            return super.onPartActivate(player, hand, pos);
        }

        return true;
    }

    @Override
    public boolean onPartShiftActivate(EntityPlayer player, EnumHand hand, Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive()) {
            return false;
        }

        if (!appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        if (player.getHeldItem(hand).isEmpty()) {
            this.isLocked = !this.isLocked;
            player.sendMessage((this.isLocked ? PlayerMessages.isNowLocked : PlayerMessages.isNowUnlocked).get());
            this.getHost().markForSave();
            this.getHost().markForUpdate();
        }

        return true;
    }

    // update the system...
    private void configureWatchers() {
        if (this.myWatcher != null) {
            this.myWatcher.reset();
        }

        try {
            if (this.configured != null) {
                if (this.myWatcher != null) {
                    this.myWatcher.add(this.configured);
                }

                final IAEStackType<?> stackType = this.configured.getStackTypeBase();
                this.updateReportingValue(
                        this.getProxy().getStorage().getInventory(stackType));
            }
        } catch (final GridAccessException e) {
            // >.>
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends IAEStack<T>> void updateReportingValue(final IMEMonitor<T> monitor) {
        if (this.configured != null) {
            final T result = monitor.getStorageList().findPrecise((T) this.configured);
            final long amount = result != null ? result.getStackSize() : 0;
            this.configured.setStackSize(amount);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {

        if ((this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG)) != (PartPanel.POWERED_FLAG
                | PartPanel.CHANNEL_FLAG)) {
            return;
        }

        IAEStack<?> ais = this.getDisplayed();

        if (ais == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        EnumFacing facing = this.getSide().getFacing();

        TesrRenderHelper.moveToFace(facing);
        TesrRenderHelper.rotateToFace(facing, this.getSpin());
        TesrRenderHelper.renderStack2dWithAmount(ais, 0.8f, 0.17f);
        GlStateManager.popMatrix();

    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Override
    public IAEStack<?> getDisplayed() {
        return this.configured;
    }

    @Override
    public boolean isLocked() {
        return this.isLocked;
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.myWatcher = newWatcher;
        this.configureWatchers();
    }

    @MENetworkEventSubscribe
    public void powerStatusChange(final MENetworkPowerStatusChange ev) {
        if (this.getProxy().isPowered()) {
            this.configureWatchers();
        }
    }

    @MENetworkEventSubscribe
    public void channelChanged(final MENetworkChannelsChanged c) {
        if (this.getProxy().isPowered()) {
            this.configureWatchers();
        }
    }

    @Override
    public void onStackChange(IItemList<?> o, IAEStack<?> fullStack, IAEStack<?> diffStack, IActionSource src,
            IAEStackType<?> type) {
        if (this.configured != null && fullStack != null) {
            this.configured.setStackSize(fullStack.getStackSize());
        } else if (this.configured != null) {
            this.configured.setStackSize(0);
        }
        this.getHost().markForUpdate();
    }

    @Override
    public boolean showNetworkInfo(final RayTraceResult where) {
        return false;
    }

    protected IPartModel selectModel(IPartModel off, IPartModel on, IPartModel hasChannel, IPartModel lockedOff,
            IPartModel lockedOn, IPartModel lockedHasChannel) {
        if (this.isActive()) {
            if (this.isLocked()) {
                return lockedHasChannel;
            } else {
                return hasChannel;
            }
        } else if (this.isPowered()) {
            if (this.isLocked()) {
                return lockedOn;
            } else {
                return on;
            }
        } else {
            if (this.isLocked()) {
                return lockedOff;
            } else {
                return off;
            }
        }
    }

}

/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2024, AlgorithmX2, All rights reserved.
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
import java.text.DecimalFormat;

import appeng.api.util.AEUtils;
import io.netty.buffer.ByteBuf;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.networking.storage.IStackWatcherHost;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.render.TesrRenderHelper;
import appeng.core.AppEng;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.Reflected;
import appeng.items.parts.PartModels;
import appeng.me.GridAccessException;
import appeng.parts.PartModel;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AEItemStackType;
import appeng.fluids.util.AEFluidStackType;

public class PartRateMonitor extends AbstractPartDisplay implements IStackWatcherHost {
    private static final int TICKS_PER_SECOND = 20;
    private static final int TICKS_PER_MINUTE = TICKS_PER_SECOND * 60;
    private static final int TICKS_PER_HOUR = TICKS_PER_MINUTE * 60;

    @PartModels
    public static final ResourceLocation MODEL_OFF = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_off");
    @PartModels
    public static final ResourceLocation MODEL_ON = new ResourceLocation(AppEng.MOD_ID, "part/conversion_monitor_on");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_ON, MODEL_STATUS_HAS_CHANNEL);

    private IAEItemStack configuredItem;
    private IAEFluidStack configuredFluid;
    private long currentAmount = 0;
    private long lastSecondAmount = 0;
    private long lastSecondTime = 0;

    private long lastMinuteAmount = 0;
    private long lastMinuteTime = 0;

    private long lastHourAmount = 0;
    private long lastHourTime = 0;

    private TimeUnit timeUnit = TimeUnit.SECOND;

    private IStackWatcher watcher;

    @Reflected
    public PartRateMonitor(final ItemStack is) {
        super(is);
    }

    @Override
    public void readFromNBT(final NBTTagCompound data) {
        super.readFromNBT(data);
        final NBTTagCompound itemTag = data.getCompoundTag("configuredItem");
        this.configuredItem = AEItemStack.fromNBT(itemTag);

        final NBTTagCompound fluidTag = data.getCompoundTag("configuredFluid");
        this.configuredFluid = AEFluidStack.fromNBT(fluidTag);

        this.lastSecondAmount = data.getLong("lastSecondAmount");
        this.lastSecondTime = data.getLong("lastSecondTime");
        this.lastMinuteAmount = data.getLong("lastMinuteAmount");
        this.lastMinuteTime = data.getLong("lastMinuteTime");
        this.lastHourAmount = data.getLong("lastHourAmount");
        this.lastHourTime = data.getLong("lastHourTime");

        if (data.hasKey("timeUnit")) {
            this.timeUnit = TimeUnit.values()[data.getByte("timeUnit")];
        }
    }

    @Override
    public void writeToNBT(final NBTTagCompound data) {
        super.writeToNBT(data);

        final NBTTagCompound itemTag = new NBTTagCompound();
        if (this.configuredItem != null) this.configuredItem.writeToNBT(itemTag);
        data.setTag("configuredItem", itemTag);

        final NBTTagCompound fluidTag = new NBTTagCompound();
        if (this.configuredFluid != null) this.configuredFluid.writeToNBT(fluidTag);
        data.setTag("configuredFluid", fluidTag);

        data.setLong("lastSecondAmount", this.lastSecondAmount);
        data.setLong("lastSecondTime", this.lastSecondTime);
        data.setLong("lastMinuteAmount", this.lastMinuteAmount);
        data.setLong("lastMinuteTime", this.lastMinuteTime);
        data.setLong("lastHourAmount", this.lastHourAmount);
        data.setLong("lastHourTime", this.lastHourTime);

        data.setByte("timeUnit", (byte) this.timeUnit.ordinal());
    }

    @Override
    public void writeToStream(final ByteBuf data) throws IOException {
        super.writeToStream(data);
        data.writeBoolean(this.configuredItem != null);
        data.writeBoolean(this.configuredFluid != null);

        if (this.configuredItem != null) {
            this.configuredItem.writeToPacket(data);
        } else if (this.configuredFluid != null) {
            this.configuredFluid.writeToPacket(data);
        }

        data.writeLong(this.currentAmount);

        data.writeLong(this.lastSecondAmount);
        data.writeLong(this.lastMinuteAmount);
        data.writeLong(this.lastHourAmount);

        data.writeByte((byte) this.timeUnit.ordinal());
    }

    @Override
    public boolean readFromStream(final ByteBuf data) throws IOException {
        boolean needRedraw = super.readFromStream(data);

        final boolean isItem = data.readBoolean();
        final boolean isFluid = data.readBoolean();

        if (isItem) {
            this.configuredItem = AEItemStack.fromPacket(data);
            this.configuredFluid = null;
        } else if (isFluid) {
            this.configuredFluid = AEFluidStack.fromPacket(data);
            this.configuredItem = null;
        } else {
            this.configuredItem = null;
            this.configuredFluid = null;
        }

        final long newAmount = data.readLong();
        if (this.currentAmount != newAmount) {
            this.currentAmount = newAmount;
            needRedraw = true;
        }

        this.lastSecondAmount = data.readLong();
        this.lastMinuteAmount = data.readLong();
        this.lastHourAmount = data.readLong();

        final TimeUnit newUnit = TimeUnit.values()[data.readByte()];
        if (this.timeUnit != newUnit) {
            this.timeUnit = newUnit;
            needRedraw = true;
        }

        return needRedraw;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive() || !appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        // 鏇存柊蹇収锛堝叧閿細鍒╃敤鐜╁浜や簰瑙﹀彂鏃堕棿妫€鏌ワ級
        this.updateSnapshots();

        final ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) {
            this.clearConfiguration();
        } else {
            FluidStack fluidInTank = null;

            if (held.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                IFluidHandlerItem fluidHandlerItem = (held.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null));
                fluidInTank = fluidHandlerItem.drain(Integer.MAX_VALUE, false);
            }
            final AEFluidStack fluidStack = AEFluidStack.fromFluidStack(fluidInTank);

            if (fluidStack != null && fluidStack.getStackSize() > 0) {
                this.configuredFluid = fluidStack.copy().setStackSize(0);
                this.configuredItem = null;
            } else {
                this.configuredItem = AEItemStack.fromItemStack(held).setStackSize(0);
                this.configuredFluid = null;
            }
            this.resetSnapshots();
        }

        this.configureWatcher();
        this.getHost().markForSave();
        this.getHost().markForUpdate();
        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        if (Platform.isClient()) {
            return true;
        }

        if (!this.getProxy().isActive() || !appeng.util.WorldHelper.hasPermissions(this.getLocation(), player)) {
            return false;
        }

        // 鏇存柊蹇収锛堢‘淇濆垏鎹㈡椂鏁版嵁鏈€鏂帮級
        this.updateSnapshots();

        // 鍒囨崲鏃堕棿鍗曚綅锛堢Щ闄ら攣瀹氶€昏緫锛?
        this.timeUnit = this.timeUnit.next();

        this.getHost().markForUpdate();
        return true;
    }

    /**
     * 娓呯┖閰嶇疆骞堕噸缃揩鐓?
     */
    private void clearConfiguration() {
        this.configuredItem = null;
        this.configuredFluid = null;
        this.currentAmount = 0;
        this.resetSnapshots();
    }

    /**
     * 閲嶇疆鎵€鏈夊揩鐓т负褰撳墠鏁伴噺鍜屾椂闂?
     */
    private void resetSnapshots() {
        final long now = this.getWorldTime();
        this.lastSecondAmount = this.currentAmount;
        this.lastSecondTime = now;
        this.lastMinuteAmount = this.currentAmount;
        this.lastMinuteTime = now;
        this.lastHourAmount = this.currentAmount;
        this.lastHourTime = now;
    }

    /**
     * 鏍规嵁涓栫晫鏃堕棿鏇存柊蹇収锛堟牳蹇冮€昏緫锛氭棤 tick 渚濊禆锛?
     */
    private void updateSnapshots() {
        final long now = this.getWorldTime();
        final boolean secondElapsed = now - this.lastSecondTime >= TICKS_PER_SECOND;
        final boolean minuteElapsed = now - this.lastMinuteTime >= TICKS_PER_MINUTE;
        final boolean hourElapsed = now - this.lastHourTime >= TICKS_PER_HOUR;

        // 浠呭綋鏃堕棿闃堝€艰揪鍒版椂鏇存柊瀵瑰簲蹇収
        if (secondElapsed) {
            this.lastSecondAmount = this.currentAmount;
            this.lastSecondTime = now;
        }
        if (minuteElapsed) {
            this.lastMinuteAmount = this.currentAmount;
            this.lastMinuteTime = now;
        }
        if (hourElapsed) {
            this.lastHourAmount = this.currentAmount;
            this.lastHourTime = now;
        }
    }

    private long getWorldTime() {
        final net.minecraft.tileentity.TileEntity tile = this.getTile();
        return tile != null ? tile.getWorld().getTotalWorldTime() : 0;
    }

    private void configureWatcher() {
        if (this.watcher != null) {
            this.watcher.reset();
            if (this.configuredItem != null) {
                this.watcher.add(this.configuredItem);
            } else if (this.configuredFluid != null) {
                this.watcher.add(this.configuredFluid);
            }
        }
        this.updateCurrentAmount();
        this.updateSnapshots();
    }

    /**
     * 鏇存柊褰撳墠搴撳瓨鏁伴噺
     */
    private void updateCurrentAmount() {
        try {
            if (this.configuredItem != null) {
                final IMEMonitor<IAEItemStack> inv = this.getProxy().getStorage()
                        .getInventory(AEItemStackType.INSTANCE.getStorageChannel());
                final IAEItemStack found = inv.getStorageList().findPrecise(this.configuredItem);
                this.currentAmount = found != null ? found.getStackSize() : 0;
            } else if (this.configuredFluid != null) {
                final IMEMonitor<IAEFluidStack> inv = this.getProxy().getStorage()
                        .getInventory(AEFluidStackType.INSTANCE.getStorageChannel());
                final IAEFluidStack found = inv.getStorageList().findPrecise(this.configuredFluid);
                this.currentAmount = found != null ? found.getStackSize() : 0;
            }
        } catch (final GridAccessException e) {
            this.currentAmount = 0;
        }
    }

    @Override
    public void onStackChange(IItemList<?> o, IAEStack<?> fullStack, IAEStack<?> diffStack, IActionSource src,
                              IAEStackType<?> type) {
        if (this.configuredItem != null && fullStack instanceof IAEItemStack) {
            this.currentAmount = fullStack.getStackSize();
        } else if (this.configuredFluid != null && fullStack instanceof IAEFluidStack) {
            this.currentAmount = fullStack.getStackSize();
        }

        this.updateSnapshots();
        this.getHost().markForUpdate();
    }

    @Override
    public void updateWatcher(final IStackWatcher newWatcher) {
        this.watcher = newWatcher;
        this.configureWatcher();
    }

    @MENetworkEventSubscribe
    public void powerStatusChange(final MENetworkPowerStatusChange ev) {
        if (this.getProxy().isPowered()) {
            this.configureWatcher();
        }
    }

    @MENetworkEventSubscribe
    public void channelChanged(final MENetworkChannelsChanged c) {
        if (this.getProxy().isPowered()) {
            this.configureWatcher();
        }
    }

    private long calculateChange() {
        switch (this.timeUnit) {
            case SECOND:
                return this.currentAmount - this.lastSecondAmount;
            case MINUTE:
                return this.currentAmount - this.lastMinuteAmount;
            case HOUR:
                return this.currentAmount - this.lastHourAmount;
            default:
                return 0;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void renderDynamic(double x, double y, double z, float partialTicks, int destroyStage) {
        if ((this.getClientFlags() & (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG))
                != (PartPanel.POWERED_FLAG | PartPanel.CHANNEL_FLAG)) {
            return;
        }

        final IAEStack<?> displayed = this.getDisplayed();
        if (displayed == null) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5);

        final EnumFacing facing = this.getSide().getFacing();
        TesrRenderHelper.moveToFace(facing);
        TesrRenderHelper.rotateToFace(facing, this.getSpin());

        final long change = this.calculateChange();
        final String suffix = this.timeUnit.getSuffix();
        final String rateText = AEUtils.formatNumber(change) + suffix;
        final int color = change > 0 ? 0xFF00FF00 : (change < 0 ? 0xFFFF5555 : 0xFFFFFFFF);

        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);

        if (displayed instanceof IAEItemStack) {
            TesrRenderHelper.renderItem2dWithRate(
                    (IAEItemStack) displayed,
                    0.6f,
                    0.17f,
                    rateText,
                    color
            );
        } else if (displayed instanceof IAEFluidStack) {
            TesrRenderHelper.renderFluid2dWithRate(
                    (IAEFluidStack) displayed,
                    0.6f,
                    0.17f,
                    rateText,
                    color
            );
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
    }

    private IAEStack<?> getDisplayed() {
        if (this.configuredItem != null) {
            return this.configuredItem.copy().setStackSize(this.currentAmount);
        } else if (this.configuredFluid != null) {
            return this.configuredFluid.copy().setStackSize(this.currentAmount);
        }
        return null;
    }

    @Override
    public boolean requireDynamicRender() {
        return true;
    }

    @Override
    public IPartModel getStaticModels() {
        // 缁熶竴妯″瀷锛屼笉鍖哄垎鏃堕棿鍗曚綅
        if (!this.isActive()) {
            return MODELS_OFF;
        } else if (!this.isPowered()) {
            return MODELS_ON;
        } else {
            return MODELS_HAS_CHANNEL;
        }
    }

    private enum TimeUnit {
        SECOND("/s"),
        MINUTE("/min"),
        HOUR("/h");

        private final String suffix;

        TimeUnit(String suffix) {

            this.suffix = suffix;
        }

        public TimeUnit next() {
            final TimeUnit[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }



        public String getSuffix() {
            return this.suffix;
        }
    }
}

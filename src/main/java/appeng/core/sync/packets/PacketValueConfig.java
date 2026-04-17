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

package appeng.core.sync.packets;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiOreDictStorageBus;
import appeng.container.AEBaseContainer;
import appeng.container.implementations.*;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.fluids.container.ContainerFluidLevelEmitter;
import appeng.fluids.container.ContainerFluidStorageBus;
import appeng.helpers.IMouseWheelItem;
import appeng.items.tools.powered.ToolWirelessUniversalTerminal;
import appeng.items.tools.powered.WirelessTerminalMode;
import appeng.util.Platform;

public class PacketValueConfig extends AppEngPacket {

    private final String Name;
    private final String Value;

    // automatic.
    public PacketValueConfig(final ByteBuf stream) throws IOException {
        final DataInputStream dis = new DataInputStream(
                this.getPacketByteArray(stream, stream.readerIndex(), stream.readableBytes()));
        this.Name = dis.readUTF();
        this.Value = dis.readUTF();
        // dis.close();
    }

    // api
    public PacketValueConfig(final String name, final String value) throws IOException {
        this.Name = name;
        this.Value = value;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);
        dos.writeUTF(name);
        dos.writeUTF(value);
        // dos.close();

        data.writeBytes(bos.toByteArray());

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (this.Name.equals("Item")
                && ((!player.getHeldItem(EnumHand.MAIN_HAND).isEmpty() && player.getHeldItem(EnumHand.MAIN_HAND)
                        .getItem() instanceof IMouseWheelItem)
                        || (!player.getHeldItem(EnumHand.OFF_HAND)
                                .isEmpty()
                                && player.getHeldItem(EnumHand.OFF_HAND).getItem() instanceof IMouseWheelItem))) {
            final EnumHand hand;
            if (!player.getHeldItem(EnumHand.MAIN_HAND).isEmpty()
                    && player.getHeldItem(EnumHand.MAIN_HAND).getItem() instanceof IMouseWheelItem) {
                hand = EnumHand.MAIN_HAND;
            } else if (!player.getHeldItem(EnumHand.OFF_HAND).isEmpty()
                    && player.getHeldItem(EnumHand.OFF_HAND).getItem() instanceof IMouseWheelItem) {
                hand = EnumHand.OFF_HAND;
            } else {
                return;
            }

            final ItemStack is = player.getHeldItem(hand);
            final IMouseWheelItem si = (IMouseWheelItem) is.getItem();
            si.onWheel(is, this.Value.equals("WheelUp"));
        } else if (this.Name.equals("Terminal.Cpu.Set") && c instanceof ContainerCraftingStatus) {
            final ContainerCraftingStatus qk = (ContainerCraftingStatus) c;
            qk.selectCPU(Integer.parseInt(this.Value));
        } else if (this.Name.equals("Terminal.Cpu") && c instanceof ContainerCraftConfirm) {
            final ContainerCraftConfirm qk = (ContainerCraftConfirm) c;
            qk.cycleCpu(this.Value.equals("Next"));
        } else if (this.Name.equals("Terminal.Start") && c instanceof ContainerCraftConfirm) {
            final ContainerCraftConfirm qk = (ContainerCraftConfirm) c;
            qk.startJob();
        } else if (this.Name.equals("TileCrafting.Cancel") && c instanceof ContainerCraftingCPU) {
            final ContainerCraftingCPU qk = (ContainerCraftingCPU) c;
            qk.cancelCrafting();
        } else if (this.Name.equals("TileCrafting.Switch") && c instanceof ContainerCraftingCPU) {
            final ContainerCraftingCPU qk = (ContainerCraftingCPU) c;
            qk.switchCrafting();
        } else if (this.Name.equals("TileCrafting.Track") && c instanceof ContainerCraftingCPU) {
            final ContainerCraftingCPU qk = (ContainerCraftingCPU) c;
            qk.trackCrafting();
        } else if (this.Name.equals("QuartzKnife.Name") && c instanceof ContainerQuartzKnife) {
            final ContainerQuartzKnife qk = (ContainerQuartzKnife) c;
            qk.setName(this.Value);
        } else if (this.Name.equals("QuartzKnife.ReName") && c instanceof ContainerRenamer) {
            final ContainerRenamer qk = (ContainerRenamer) c;
            qk.setNewName(this.Value);
        } else if (this.Name.equals("TileSecurityStation.ToggleOption") && c instanceof ContainerSecurityStation) {
            final ContainerSecurityStation sc = (ContainerSecurityStation) c;
            sc.toggleSetting(this.Value, player);
        } else if (this.Name.equals("PriorityHost.Priority") && c instanceof ContainerPriority) {
            final ContainerPriority pc = (ContainerPriority) c;
            pc.setPriority(Integer.parseInt(this.Value), player);
        } else if (this.Name.equals("LevelEmitter.Value") && c instanceof ContainerLevelEmitter) {
            final ContainerLevelEmitter lvc = (ContainerLevelEmitter) c;
            lvc.setLevel(Long.parseLong(this.Value), player);
        } else if (this.Name.equals("FluidLevelEmitter.Value") && c instanceof ContainerFluidLevelEmitter) {
            final ContainerFluidLevelEmitter lvc = (ContainerFluidLevelEmitter) c;
            lvc.setLevel(Long.parseLong(this.Value), player);
        } else if (this.Name.startsWith("PatternTerminal.")) {
            if (c instanceof ContainerPatternEncoder) {
                final ContainerPatternEncoder cpt = (ContainerPatternEncoder) c;
                if (this.Name.equals("PatternTerminal.CraftMode")) {
                    cpt.setCraftingMode(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.Encode")) {
                    if (this.Value.equals("2")) {
                        cpt.encodeAndMoveToInventory();
                    } else {
                        cpt.encode();
                    }
                } else if (this.Name.equals("PatternTerminal.Clear")) {
                    cpt.clear();
                } else if (this.Name.equals("PatternTerminal.MultiplyByTwo")) {
                    cpt.multiply(2);
                } else if (this.Name.equals("PatternTerminal.MultiplyByThree")) {
                    cpt.multiply(3);
                } else if (this.Name.equals("PatternTerminal.DivideByTwo")) {
                    cpt.divide(2);
                } else if (this.Name.equals("PatternTerminal.DivideByThree")) {
                    cpt.divide(3);
                } else if (this.Name.equals("PatternTerminal.IncreaseByOne")) {
                    cpt.increase(1);
                } else if (this.Name.equals("PatternTerminal.DecreaseByOne")) {
                    cpt.decrease(1);
                } else if (this.Name.equals("PatternTerminal.MaximizeCount")) {
                    cpt.maximizeCount();
                } else if (this.Name.equals("PatternTerminal.Substitute")) {
                    cpt.setSubstitute(this.Value.equals("1"));
                }
            } else if (c instanceof ContainerWirelessDualInterfaceTerminal) {
                // 二合一接口终端中的样板编写功能
                final ContainerWirelessDualInterfaceTerminal cdt = (ContainerWirelessDualInterfaceTerminal) c;
                if (this.Name.equals("PatternTerminal.CraftMode")) {
                    cdt.setCraftingMode(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.Encode")) {
                    // 编码值：(ctrl?1:0)<<1|(shift?1:0) => 0=普通, 1=shift(移到背包), 2=ctrl, 3=ctrl+shift
                    final int val = Integer.parseInt(this.Value);
                    final boolean shift = (val & 1) != 0;
                    if (shift) {
                        cdt.encodeAndMoveToInventory();
                    } else {
                        cdt.encode();
                    }
                } else if (this.Name.equals("PatternTerminal.Clear")) {
                    cdt.clear();
                } else if (this.Name.equals("PatternTerminal.MultiplyByTwo")) {
                    cdt.multiply(2);
                } else if (this.Name.equals("PatternTerminal.MultiplyByThree")) {
                    cdt.multiply(3);
                } else if (this.Name.equals("PatternTerminal.DivideByTwo")) {
                    cdt.divide(2);
                } else if (this.Name.equals("PatternTerminal.DivideByThree")) {
                    cdt.divide(3);
                } else if (this.Name.equals("PatternTerminal.IncreaseByOne")) {
                    cdt.increase(1);
                } else if (this.Name.equals("PatternTerminal.DecreaseByOne")) {
                    cdt.decrease(1);
                } else if (this.Name.equals("PatternTerminal.MaximizeCount")) {
                    cdt.maximizeCount();
                } else if (this.Name.equals("PatternTerminal.Substitute")) {
                    cdt.setSubstitute(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.beSubstitute")) {
                    cdt.setBeSubstitute(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.Invert")) {
                    cdt.setInverted(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.Combine")) {
                    cdt.setCombine(this.Value.equals("1"));
                } else if (this.Name.equals("PatternTerminal.ActivePage")) {
                    cdt.setActivePage(Integer.parseInt(this.Value));
                } else if (this.Name.equals("PatternTerminal.PlacePattern")) {
                    // Value 格式: "interfaceId,slot"
                    final String[] parts = this.Value.split(",");
                    if (parts.length == 2) {
                        final long interfaceId = Long.parseLong(parts[0]);
                        final int slot = Integer.parseInt(parts[1]);
                        cdt.placePattern(interfaceId, slot);
                    }
                } else if (this.Name.equals("PatternTerminal.Double")) {
                    cdt.doubleStacks(Integer.parseInt(this.Value));
                } else if (this.Name.equals("InterfaceTerminal.Double")) {
                    // Value 格式: "val,interfaceId"
                    final String[] parts = this.Value.split(",");
                    if (parts.length == 2) {
                        final int val = Integer.parseInt(parts[0]);
                        final long interfaceId = Long.parseLong(parts[1]);
                        cdt.doubleInterfacePatterns(val, interfaceId);
                    }
                }
            }
        } else if (this.Name.startsWith("StorageBus.")) {
            if (this.Name.equals("StorageBus.Action")) {
                if (this.Value.equals("Partition")) {
                    if (c instanceof ContainerStorageBus) {
                        ((ContainerStorageBus) c).partition();
                    } else if (c instanceof ContainerFluidStorageBus) {
                        ((ContainerFluidStorageBus) c).partition();
                    } else if (c instanceof ContainerOreDictStorageBus) {
                        ((ContainerOreDictStorageBus) c).partition();
                        ((ContainerOreDictStorageBus) c).sendRegex();
                    }
                } else if (this.Value.equals("Clear")) {
                    if (c instanceof ContainerStorageBus) {
                        ((ContainerStorageBus) c).clear();
                    } else if (c instanceof ContainerFluidStorageBus) {
                        ((ContainerFluidStorageBus) c).clear();
                    }
                }
            }
        } else if (this.Name.startsWith("OreDictStorageBus")) {
            if (c instanceof ContainerOreDictStorageBus) {
                if (this.Name.equals("OreDictStorageBus.save")) {
                    ((ContainerOreDictStorageBus) c).saveOreMatch(this.Value);
                }
                if (this.Name.equals("OreDictStorageBus.getRegex")) {
                    ((ContainerOreDictStorageBus) c).sendRegex();
                }
            }
        } else if (this.Name.startsWith("CellWorkbench.") && c instanceof ContainerCellWorkbench) {
            final ContainerCellWorkbench ccw = (ContainerCellWorkbench) c;
            if (this.Name.equals("CellWorkbench.Action")) {
                if (this.Value.equals("CopyMode")) {
                    ccw.nextWorkBenchCopyMode();
                } else if (this.Value.equals("Partition")) {
                    ccw.partition();
                } else if (this.Value.equals("Clear")) {
                    ccw.clear();
                }
            } else if (this.Name.equals("CellWorkbench.Fuzzy")) {
                ccw.setFuzzy(FuzzyMode.valueOf(this.Value));
            }
        } else if (this.Name.equals("UniversalTerminal.SwitchMode")) {
            // 通用无线终端模式切换
            ItemStack mainHand = player.getHeldItem(EnumHand.MAIN_HAND);
            ItemStack offHand = player.getHeldItem(EnumHand.OFF_HAND);
            ItemStack wutStack = null;
            if (mainHand.getItem() instanceof ToolWirelessUniversalTerminal) {
                wutStack = mainHand;
            } else if (offHand.getItem() instanceof ToolWirelessUniversalTerminal) {
                wutStack = offHand;
            }
            if (wutStack != null) {
                WirelessTerminalMode targetMode = WirelessTerminalMode.fromId(Byte.parseByte(this.Value));
                if (ToolWirelessUniversalTerminal.hasMode(wutStack, targetMode)) {
                    ToolWirelessUniversalTerminal.setMode(wutStack, targetMode);
                    // 重新打开 GUI
                    AEApi.instance().registries().wireless().openWirelessTerminalGui(wutStack, player.world, player);
                }
            }
        } else if (c instanceof ContainerNetworkTool) {
            if (this.Name.equals("NetworkTool") && this.Value.equals("Toggle")) {
                ((ContainerNetworkTool) c).toggleFacadeMode();
            }
        } else if (c instanceof IConfigurableObject) {
            final IConfigManager cm = ((IConfigurableObject) c).getConfigManager();

            for (final Settings e : cm.getSettings()) {
                if (e.name().equals(this.Name)) {
                    final Enum<?> def = cm.getSetting(e);

                    try {
                        cm.putSetting(e, Enum.valueOf(def.getClass(), this.Value));
                    } catch (final IllegalArgumentException err) {
                        // :P
                    }

                    break;
                }
            }
        }
    }

    @Override
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;

        if (this.Name.equals("CustomName") && c instanceof AEBaseContainer) {
            ((AEBaseContainer) c).setCustomName(this.Value);
        } else if (this.Name.startsWith("SyncDat.")) {
            ((AEBaseContainer) c).stringSync(Integer.parseInt(this.Name.substring(8)), this.Value);
        } else if (this.Name.equals("CraftingStatus") && this.Value.equals("Clear")) {
            final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
            if (gs instanceof GuiCraftingCPU) {
                ((GuiCraftingCPU) gs).clearItems();
            }
        } else if (this.Name.equals("OreDictStorageBus.sendRegex")) {
            final GuiScreen gs = Minecraft.getMinecraft().currentScreen;
            if (gs instanceof GuiOreDictStorageBus) {
                ((GuiOreDictStorageBus) gs).fillRegex(this.Value);
            }
        } else if (c instanceof IConfigurableObject) {
            final IConfigManager cm = ((IConfigurableObject) c).getConfigManager();

            for (final Settings e : cm.getSettings()) {
                if (e.name().equals(this.Name)) {
                    final Enum<?> def = cm.getSetting(e);

                    try {
                        cm.putSetting(e, Enum.valueOf(def.getClass(), this.Value));
                    } catch (final IllegalArgumentException err) {
                        // :P
                    }

                    break;
                }
            }
        }
    }
}

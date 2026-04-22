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

package appeng.client.mui;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.GuiScrollbar;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotDisconnected;
import appeng.client.me.SlotME;
import appeng.client.render.StackSizeRenderer;
import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngCraftingSlot;
import appeng.container.slot.AppEngSlot;
import appeng.container.slot.AppEngSlot.hasCalculatedValidness;
import appeng.container.slot.IOptionalSlot;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotDisabled;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotOutput;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.core.AELog;
import appeng.core.AppEng;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.core.sync.packets.PacketSwapSlots;
import appeng.fluids.client.render.FluidStackSizeRenderer;
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.fluids.items.ItemFluidDrop;
import appeng.fluids.util.AEFluidStack;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.util.item.AEItemStack;

/**
 * 基于 MUI 的 AE2 基础面板。
 * <p>
 * 提供统一的布局框架：
 * <ul>
 *   <li>标准化的初始化流程（initGui）</li>
 *   <li>主题色和贴图管理（通过 {@link AEMUITheme}）</li>
 *   <li>可组合的子面板系统（{@link IMUIWidget}）</li>
 *   <li>统一的绘制管线（背景 → 控件 → 前景）</li>
 *   <li>旧式 drawBG/drawFG 钩子（兼容旧 GUI 移植）</li>
 *   <li>滚动条集成（{@link GuiScrollbar}）</li>
 *   <li>GuiCustomSlot 绘制和交互</li>
 *   <li>特殊槽位渲染（SlotME、流体槽、SlotFake 等）</li>
 *   <li>滚轮事件分发</li>
 *   <li>热键映射和双击逻辑</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public abstract class AEBasePanel extends GuiContainer {

    // ========== MUI 控件系统 ==========

    /** 子控件列表 */
    protected final List<IMUIWidget> widgets = new ArrayList<>();

    /** 面板标题（显示在顶部） */
    protected String panelTitle = "";

    /** 背景贴图（默认使用主题的通用面板贴图） */
    protected ResourceLocation backgroundTexture = AEMUITheme.TEX_PANEL_BG;

    // ========== 旧 GUI 兼容层 ==========

    /** 自定义槽位列表（兼容旧 GuiCustomSlot 体系） */
    protected final List<GuiCustomSlot> guiSlots = new ArrayList<>();

    /** ME 内部槽位列表 */
    private final List<InternalSlotME> meSlots = new ArrayList<>();

    /** 滚动条 */
    private GuiScrollbar myScrollBar = null;

    /** 物品数量渲染器 */
    private final StackSizeRenderer stackSizeRenderer = new StackSizeRenderer();

    /** 流体数量渲染器 */
    private final FluidStackSizeRenderer fluidStackSizeRenderer = new FluidStackSizeRenderer();

    /** 拖拽点击槽位记录（防止重复触发） */
    private final Set<Slot> dragClick = new HashSet<>();

    /** 是否正在处理 GuiCustomSlot 点击 */
    private boolean handlingCustomSlotClick = false;

    /** 双击逻辑相关 */
    private boolean disableShiftClick = false;
    private Stopwatch dblClickTimer = Stopwatch.createStarted();
    private ItemStack dblWhichItem = ItemStack.EMPTY;
    private Slot blClicked;

    /** 键盘事件是否已被处理（阻止 Forge 继续传播） */
    protected boolean keyHandled = false;

    /** JEI 书签覆盖层当前悬停的物品（供 ClientHelper 事件取消逻辑使用） */
    private Object bookmarkedIngredient;

    public Object getBookmarkedIngredient() {
        return this.bookmarkedIngredient;
    }

    public AEBasePanel(Container container) {
        super(container);
    }

    public AEBasePanel(Container container, int xSize, int ySize) {
        super(container);
        this.xSize = xSize;
        this.ySize = ySize;
    }

    // ========== 初始化 ==========

    @Override
    public void initGui() {
        super.initGui();

        // 清理旧的 SlotME 并重建
        final List<Slot> slots = this.getInventorySlots();
        final Iterator<Slot> it = slots.iterator();
        while (it.hasNext()) {
            if (it.next() instanceof SlotME) {
                it.remove();
            }
        }
        for (final InternalSlotME me : this.meSlots) {
            slots.add(new SlotME(me));
        }

        this.widgets.clear();
        this.setupWidgets();
    }

    /**
     * 子类覆写此方法来添加子控件。
     * 在 {@link #initGui()} 中被调用。
     */
    protected abstract void setupWidgets();

    /**
     * 添加一个 MUI 控件。
     */
    protected <T extends IMUIWidget> T addWidget(T widget) {
        this.widgets.add(widget);
        if (widget instanceof GuiButton) {
            this.buttonList.add((GuiButton) widget);
        }
        return widget;
    }

    // ========== 绘制管线 ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // 绘制 GuiCustomSlot（在面板坐标系内）
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.guiLeft, this.guiTop, 0.0F);
        GlStateManager.enableDepth();
        for (final GuiCustomSlot c : this.guiSlots) {
            this.drawGuiSlot(c, mouseX, mouseY, partialTicks);
        }
        GlStateManager.disableDepth();
        for (final GuiCustomSlot c : this.guiSlots) {
            this.drawTooltip(c, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
        GlStateManager.popMatrix();

        // 悬停工具提示（MC 原生槽位）
        this.renderHoveredToolTip(mouseX, mouseY);

        // 按钮和标签的工具提示
        for (final Object c : this.buttonList) {
            if (c instanceof ITooltip) {
                this.drawTooltip((ITooltip) c, mouseX, mouseY);
            }
        }
        for (final Object o : this.labelList) {
            if (o instanceof ITooltip) {
                this.drawTooltip((ITooltip) o, mouseX, mouseY);
            }
        }
        GlStateManager.enableDepth();

        // 更新 JEI 书签悬停物品（供 ClientHelper 事件取消逻辑使用）
        if (appeng.util.Platform.isModLoaded("jei") && !appeng.client.ClientHelper.isHei) {
            updateBookmarkedIngredient();
        }
    }

    @net.minecraftforge.fml.common.Optional.Method(modid = "jei")
    private void updateBookmarkedIngredient() {
        var rt = appeng.integration.modules.jei.JEIPlugin.runtime;
        if (rt != null) {
            this.bookmarkedIngredient = rt.getBookmarkOverlay().getIngredientUnderMouse();
        }
    }

    @Override
    protected final void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        final int ox = this.guiLeft;
        final int oy = this.guiTop;
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 背景贴图
        if (this.backgroundTexture != null) {
            this.mc.getTextureManager().bindTexture(this.backgroundTexture);
            this.drawTexturedModalRect(ox, oy, 0, 0, this.xSize, this.ySize);
        }

        // 旧式 drawBG 钩子
        this.drawBG(ox, oy, mouseX, mouseY);

        // 可选槽位背景渲染
        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            if (slot instanceof IOptionalSlot) {
                final IOptionalSlot optionalSlot = (IOptionalSlot) slot;
                if (optionalSlot.isRenderDisabled()) {
                    final AppEngSlot aeSlot = (AppEngSlot) slot;
                    if (aeSlot.isSlotEnabled()) {
                        this.drawTexturedModalRect(ox + aeSlot.xPos - 1, oy + aeSlot.yPos - 1,
                                optionalSlot.getSourceX() - 1, optionalSlot.getSourceY() - 1, 18, 18);
                    } else {
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.4F);
                        GlStateManager.enableBlend();
                        this.drawTexturedModalRect(ox + aeSlot.xPos - 1, oy + aeSlot.yPos - 1,
                                optionalSlot.getSourceX() - 1, optionalSlot.getSourceY() - 1, 18, 18);
                        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    }
                }
            }
        }

        // GuiCustomSlot 背景绘制
        for (final GuiCustomSlot slot : this.guiSlots) {
            slot.drawBackground(ox, oy);
        }

        // MUI 控件背景层
        for (IMUIWidget widget : this.widgets) {
            widget.drawBackground(this, this.guiLeft, this.guiTop, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        final int ox = this.guiLeft;
        final int oy = this.guiTop;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // 滚动条绘制
        if (this.myScrollBar != null) {
            this.myScrollBar.draw(this);
        }

        // 面板标题
        if (this.panelTitle != null && !this.panelTitle.isEmpty()) {
            this.fontRenderer.drawString(this.panelTitle,
                    AEMUITheme.PANEL_PADDING,
                    AEMUITheme.PANEL_PADDING / 2,
                    AEMUITheme.COLOR_TITLE);
        }

        // 旧式 drawFG 钩子
        this.drawFG(ox, oy, mouseX, mouseY);

        // MUI 控件前景层
        for (IMUIWidget widget : this.widgets) {
            widget.drawForeground(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    /**
     * 旧式背景绘制钩子。子类可覆写。
     * <p>
     * 坐标参数说明：
     * offsetX/offsetY 是面板左上角的屏幕坐标。
     *
     * @param offsetX 面板左上角屏幕 X（= guiLeft）
     * @param offsetY 面板左上角屏幕 Y（= guiTop）
     * @param mouseX  鼠标屏幕 X
     * @param mouseY  鼠标屏幕 Y
     */
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    /**
     * 旧式前景绘制钩子。子类可覆写。
     * <p>
     * 绘制坐标系已经被 MC 平移到面板内部（左上角为 0,0）。
     *
     * @param offsetX 面板左上角屏幕 X（= guiLeft）
     * @param offsetY 面板左上角屏幕 Y（= guiTop）
     * @param mouseX  鼠标屏幕 X
     * @param mouseY  鼠标屏幕 Y
     */
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    // ========== GuiCustomSlot 绘制 ==========

    /**
     * 绘制单个 GuiCustomSlot（内容 + 悬停高亮）。
     */
    protected void drawGuiSlot(GuiCustomSlot slot, int mouseX, int mouseY, float partialTicks) {
        if (slot.isSlotEnabled()) {
            final int left = slot.xPos();
            final int top = slot.yPos();
            final int right = left + slot.getWidth();
            final int bottom = top + slot.getHeight();

            slot.drawContent(this.mc, mouseX, mouseY, partialTicks);

            if (this.isPointInRegion(left, top, slot.getWidth(), slot.getHeight(), mouseX, mouseY)
                    && slot.canClick(this.mc.player)) {
                GlStateManager.disableLighting();
                GlStateManager.colorMask(true, true, true, false);
                this.drawGradientRect(left, top, right, bottom, -2130706433, -2130706433);
                GlStateManager.colorMask(true, true, true, true);
                GlStateManager.enableLighting();
            }
        }
    }

    // ========== 特殊槽位渲染 ==========

    /**
     * 覆写 MC 的槽位渲染，处理 SlotME、流体槽、SlotFake(FluidDrop) 等特殊类型。
     */
    @Override
    public void drawSlot(Slot s) {
        if (s instanceof SlotME) {
            try {
                this.zLevel = 100.0F;
                this.itemRender.zLevel = 100.0F;

                if (!this.isPowered()) {
                    drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                }

                this.zLevel = 0.0F;
                this.itemRender.zLevel = 0.0F;

                super.drawSlot(new appeng.client.gui.Size1Slot((SlotME) s));
                this.stackSizeRenderer.renderStackSize(this.fontRenderer, ((SlotME) s).getAEStack(), s.xPos, s.yPos);
            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
            }
            return;
        } else if (s instanceof IMEFluidSlot && ((IMEFluidSlot) s).shouldRenderAsFluid()) {
            final IMEFluidSlot slot = (IMEFluidSlot) s;
            final IAEFluidStack fs = slot.getAEFluidStack();

            if (fs != null && this.isPowered()) {
                GlStateManager.disableLighting();
                GlStateManager.disableBlend();
                final Fluid fluid = fs.getFluid();
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                final TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                        .getAtlasSprite(fluid.getStill().toString());

                float red = (fluid.getColor() >> 16 & 255) / 255.0F;
                float green = (fluid.getColor() >> 8 & 255) / 255.0F;
                float blue = (fluid.getColor() & 255) / 255.0F;
                GlStateManager.color(red, green, blue);

                this.drawTexturedModalRect(s.xPos, s.yPos, sprite, 16, 16);
                GlStateManager.enableLighting();
                GlStateManager.enableBlend();

                this.fluidStackSizeRenderer.renderStackSize(this.fontRenderer, fs, s.xPos, s.yPos);
            } else if (!this.isPowered()) {
                drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
            }
            return;
        } else if (s instanceof SlotFake && !s.getStack().isEmpty()
                && s.getStack().getItem() instanceof ItemFluidDrop) {
            // SlotFake 中的 ItemFluidDrop：渲染为流体纹理 + 流体数量
            final ItemStack stack = s.getStack();
            final FluidStack fluidStack = ItemFluidDrop.getFluidStack(stack);

            if (fluidStack != null) {
                GlStateManager.disableLighting();
                GlStateManager.disableBlend();
                final Fluid fluid = fluidStack.getFluid();
                Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
                final TextureAtlasSprite sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                        .getAtlasSprite(fluid.getStill().toString());

                float red = (fluid.getColor() >> 16 & 255) / 255.0F;
                float green = (fluid.getColor() >> 8 & 255) / 255.0F;
                float blue = (fluid.getColor() & 255) / 255.0F;
                GlStateManager.color(red, green, blue);

                this.drawTexturedModalRect(s.xPos, s.yPos, sprite, 16, 16);
                GlStateManager.enableLighting();
                GlStateManager.enableBlend();

                IAEFluidStack aeFluid = AEFluidStack.fromFluidStack(fluidStack);
                if (aeFluid != null) {
                    aeFluid.setStackSize(stack.getCount());
                    this.fluidStackSizeRenderer.renderStackSize(this.fontRenderer, aeFluid, s.xPos, s.yPos);
                }
            } else {
                super.drawSlot(s);
            }
            return;
        } else {
            try {
                final ItemStack is = s.getStack();
                if (s instanceof AppEngSlot && (((AppEngSlot) s).renderIconWithItem() || is.isEmpty())
                        && (((AppEngSlot) s).shouldDisplay())) {
                    final AppEngSlot aes = (AppEngSlot) s;
                    if (aes.getIcon() >= 0) {
                        this.bindTexture("guis/states.png");

                        try {
                            final int uvY = (int) Math.floor(aes.getIcon() / 16);
                            final int uvX = aes.getIcon() - uvY * 16;

                            GlStateManager.enableBlend();
                            GlStateManager.disableLighting();
                            GlStateManager.enableTexture2D();
                            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                            final float par1 = aes.xPos;
                            final float par2 = aes.yPos;
                            final float par3 = uvX * 16;
                            final float par4 = uvY * 16;

                            final Tessellator tessellator = Tessellator.getInstance();
                            final BufferBuilder vb = tessellator.getBuffer();

                            vb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

                            final float f1 = 0.00390625F;
                            final float f = 0.00390625F;
                            final float par6 = 16;
                            vb.pos(par1 + 0, par2 + par6, this.zLevel)
                                    .tex((par3 + 0) * f, (par4 + par6) * f1)
                                    .color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            final float par5 = 16;
                            vb.pos(par1 + par5, par2 + par6, this.zLevel)
                                    .tex((par3 + par5) * f, (par4 + par6) * f1)
                                    .color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            vb.pos(par1 + par5, par2 + 0, this.zLevel)
                                    .tex((par3 + par5) * f, (par4 + 0) * f1)
                                    .color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            vb.pos(par1 + 0, par2 + 0, this.zLevel)
                                    .tex((par3 + 0) * f, (par4 + 0) * f1)
                                    .color(1.0f, 1.0f, 1.0f, aes.getOpacityOfIcon()).endVertex();
                            tessellator.draw();
                        } catch (final Exception err) {
                            // 忽略图标渲染错误
                        }
                    }
                }

                if (!is.isEmpty() && s instanceof AppEngSlot) {
                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.NotAvailable) {
                        boolean isValid = s.isItemValid(is) || s instanceof SlotOutput
                                || s instanceof AppEngCraftingSlot || s instanceof SlotDisabled
                                || s instanceof SlotInaccessible || s instanceof SlotFake
                                || s instanceof SlotRestrictedInput || s instanceof SlotDisconnected;
                        if (isValid && s instanceof SlotRestrictedInput) {
                            try {
                                isValid = ((SlotRestrictedInput) s).isValid(is, this.mc.world);
                            } catch (final Exception err) {
                                AELog.debug(err);
                            }
                        }
                        ((AppEngSlot) s)
                                .setIsValid(isValid ? hasCalculatedValidness.Valid : hasCalculatedValidness.Invalid);
                    }

                    if (((AppEngSlot) s).getIsValid() == hasCalculatedValidness.Invalid) {
                        this.zLevel = 100.0F;
                        this.itemRender.zLevel = 100.0F;

                        GlStateManager.disableLighting();
                        drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66ff6666);
                        GlStateManager.enableLighting();

                        this.zLevel = 0.0F;
                        this.itemRender.zLevel = 0.0F;
                    }
                }
                if (s instanceof SlotPlayerInv || s instanceof SlotPlayerHotBar) {
                    if (!is.isEmpty() && is.getItem() instanceof ItemEncodedPattern) {
                        final ItemEncodedPattern iep = (ItemEncodedPattern) is.getItem();
                        final ItemStack out = iep.getOutput(is);
                        if (!out.isEmpty()) {
                            AppEngSlot appEngSlot = ((AppEngSlot) s);
                            appEngSlot.setDisplay(true);
                            appEngSlot.setReturnAsSingleStack(true);

                            this.zLevel = 100.0F;
                            this.itemRender.zLevel = 100.0F;
                            if (!this.isPowered()) {
                                drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                            }
                            this.zLevel = 0.0F;
                            this.itemRender.zLevel = 0.0F;

                            super.drawSlot(s);
                            if (isShiftKeyDown()) {
                                this.stackSizeRenderer.renderStackSize(this.fontRenderer,
                                        AEItemStack.fromItemStack(out), s.xPos, s.yPos);
                            } else {
                                super.drawSlot(s);
                            }
                            return;
                        }
                    } else {
                        super.drawSlot(s);
                    }
                } else if (s instanceof AppEngSlot) {
                    AppEngSlot appEngSlot = ((AppEngSlot) s);
                    if (s.getStack().isEmpty()) {
                        super.drawSlot(s);
                        return;
                    }
                    appEngSlot.setDisplay(true);
                    appEngSlot.setReturnAsSingleStack(true);

                    this.zLevel = 100.0F;
                    this.itemRender.zLevel = 100.0F;
                    if (!this.isPowered()) {
                        drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
                    }
                    this.zLevel = 0.0F;
                    this.itemRender.zLevel = 0.0F;

                    boolean wasDragSplitting = this.dragSplitting;
                    this.dragSplitting = false;
                    super.drawSlot(s);

                    ItemStack stackInSlot = appEngSlot.getDisplayStack();
                    ItemStack stackUnderCursor = this.mc.player.inventory.getItemStack();

                    if (wasDragSplitting
                            && this.dragSplittingSlots.contains(s)
                            && this.dragSplittingSlots.size() > 1
                            && !stackUnderCursor.isEmpty()) {
                        if (Container.canAddItemToSlot(s, stackUnderCursor, true)
                                && this.inventorySlots.canDragIntoSlot(s)) {
                            drawRect(s.xPos, s.yPos, s.xPos + 16, s.yPos + 16, -2130706433);
                            stackInSlot = stackUnderCursor.copy();
                            Container.computeStackSize(this.dragSplittingSlots, this.dragSplittingLimit,
                                    stackInSlot,
                                    s.getStack().isEmpty() ? 0 : s.getStack().getCount());
                            int k = Math.min(stackInSlot.getMaxStackSize(), s.getItemStackLimit(stackInSlot));
                            if (stackInSlot.getCount() > k) {
                                stackInSlot.setCount(k);
                            }
                        } else {
                            this.dragSplittingSlots.remove(s);
                            this.updateDragSplitting();
                        }
                    }

                    this.dragSplitting = wasDragSplitting;
                    this.stackSizeRenderer.renderStackSize(this.fontRenderer, AEItemStack.fromItemStack(stackInSlot),
                            s.xPos, s.yPos);
                    return;
                } else {
                    super.drawSlot(s);
                }
                return;
            } catch (final Exception err) {
                AELog.warn("[AppEng] AE prevented crash while drawing slot: " + err);
            }
        }
        super.drawSlot(s);
    }

    // ========== 输入事件 ==========

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.dragClick.clear();
        this.handlingCustomSlotClick = false;

        // MUI 控件优先
        for (IMUIWidget widget : this.widgets) {
            if (widget.mouseClicked(mouseX - this.guiLeft, mouseY - this.guiTop, mouseButton)) {
                return;
            }
        }

        // 右键触发按钮
        if (mouseButton == 1) {
            for (final Object o : this.buttonList) {
                final GuiButton guibutton = (GuiButton) o;
                if (guibutton.mousePressed(this.mc, mouseX, mouseY)) {
                    super.mouseClicked(mouseX, mouseY, 0);
                    return;
                }
            }
        }

        // GuiCustomSlot 点击
        for (GuiCustomSlot slot : this.guiSlots) {
            if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), mouseX, mouseY)
                    && slot.canClick(this.mc.player)) {
                this.handlingCustomSlotClick = true;
                slot.slotClicked(this.mc.player.inventory.getItemStack(), mouseButton);
                return;
            }
        }

        // 滚动条点击
        if (this.myScrollBar != null) {
            this.myScrollBar.click(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        this.dragClick.clear();

        if (this.handlingCustomSlotClick) {
            this.handlingCustomSlotClick = false;
            return;
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void mouseClickMove(final int x, final int y, final int c, final long d) {
        if (this.handlingCustomSlotClick) {
            return;
        }

        final Slot slot = this.getSlot(x, y);
        final ItemStack itemstack = this.mc.player.inventory.getItemStack();

        if (this.myScrollBar != null) {
            this.myScrollBar.click(this, x - this.guiLeft, y - this.guiTop);
        }

        if (slot instanceof SlotFake && !itemstack.isEmpty()) {
            if (this.dragClick.add(slot)) {
                final PacketInventoryAction p = new PacketInventoryAction(
                        c == 0 ? InventoryAction.PICKUP_OR_SET_DOWN : InventoryAction.PLACE_SINGLE,
                        slot.slotNumber, 0);
                NetworkHandler.instance().sendToServer(p);
            }
        } else if (slot instanceof SlotDisconnected) {
            if (this.dragClick.add(slot)) {
                if (!itemstack.isEmpty()) {
                    if (slot.getStack().isEmpty()) {
                        InventoryAction action;
                        if (slot.getSlotStackLimit() == 1) {
                            action = InventoryAction.SPLIT_OR_PLACE_SINGLE;
                        } else {
                            action = InventoryAction.PICKUP_OR_SET_DOWN;
                        }
                        final PacketInventoryAction p = new PacketInventoryAction(action, slot.getSlotIndex(),
                                ((SlotDisconnected) slot).getSlot().getId());
                        NetworkHandler.instance().sendToServer(p);
                    }
                }
            } else if (isShiftKeyDown()) {
                for (final Slot dr : this.dragClick) {
                    InventoryAction action = null;
                    if (!slot.getStack().isEmpty()) {
                        action = InventoryAction.SHIFT_CLICK;
                    }
                    if (action != null) {
                        final PacketInventoryAction p = new PacketInventoryAction(action, dr.getSlotIndex(),
                                ((SlotDisconnected) slot).getSlot().getId());
                        NetworkHandler.instance().sendToServer(p);
                    }
                }
            }
        } else {
            super.mouseClickMove(x, y, c, d);
        }
    }

    @Override
    protected void handleMouseClick(final Slot slot, final int slotIdx, final int mouseButton,
            final ClickType clickType) {
        final EntityPlayer player = Minecraft.getMinecraft().player;

        if (slot instanceof SlotFake) {
            final InventoryAction action;
            if (isCtrlKeyDown()) {
                if (isShiftKeyDown()) {
                    action = InventoryAction.PICKUP_ALL_FLUID_FROM_CONTAINER;
                } else if (mouseButton == 1) {
                    action = InventoryAction.PLACE_SINGLE_FLUID_FROM_CONTAINER;
                } else {
                    action = InventoryAction.PICKUP_FLUID_FROM_CONTAINER;
                }
            } else {
                action = mouseButton == 1
                        ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                        : InventoryAction.PICKUP_OR_SET_DOWN;
            }

            if (this.dragClick.size() > 1) {
                return;
            }

            PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
            NetworkHandler.instance().sendToServer(p);
            return;
        }

        if (slot instanceof SlotPatternTerm) {
            if (mouseButton == 6) {
                return;
            }
            try {
                NetworkHandler.instance().sendToServer(((SlotPatternTerm) slot).getRequest(isShiftKeyDown()));
            } catch (final IOException e) {
                AELog.debug(e);
            }
        } else if (slot instanceof SlotCraftingTerm) {
            if (mouseButton == 6) {
                return;
            }
            InventoryAction action;
            if (isShiftKeyDown()) {
                action = InventoryAction.CRAFT_SHIFT;
            } else {
                action = (mouseButton == 1) ? InventoryAction.CRAFT_STACK : InventoryAction.CRAFT_ITEM;
            }
            final PacketInventoryAction p = new PacketInventoryAction(action, slotIdx, 0);
            NetworkHandler.instance().sendToServer(p);
            return;
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_SPACE)) {
            if (this.enableSpaceClicking()) {
                IAEItemStack stack = null;
                if (slot instanceof SlotME) {
                    stack = ((SlotME) slot).getAEStack();
                }
                int slotNum = this.getInventorySlots().size();
                if (!(slot instanceof SlotME) && slot != null) {
                    slotNum = slot.slotNumber;
                }
                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                final PacketInventoryAction p = new PacketInventoryAction(InventoryAction.MOVE_REGION, slotNum, 0);
                NetworkHandler.instance().sendToServer(p);
                return;
            }
        }

        if (slot instanceof SlotDisconnected) {
            if (this.dragClick.size() >= 1) {
                return;
            }
            InventoryAction action = null;
            switch (clickType) {
                case PICKUP:
                    action = mouseButton == 1
                            ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                            : InventoryAction.PICKUP_OR_SET_DOWN;
                    break;
                case QUICK_MOVE:
                    action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    break;
                case CLONE:
                    if (player.capabilities.isCreativeMode) {
                        action = InventoryAction.CREATIVE_DUPLICATE;
                    }
                    break;
                default:
                case THROW:
                    break;
            }
            if (action != null) {
                final PacketInventoryAction p = new PacketInventoryAction(action, slot.getSlotIndex(),
                        ((SlotDisconnected) slot).getSlot().getId());
                NetworkHandler.instance().sendToServer(p);
            }
            return;
        }

        if (slot instanceof SlotME) {
            InventoryAction action = null;
            IAEItemStack stack = null;
            switch (clickType) {
                case PICKUP:
                    action = (mouseButton == 1) ? InventoryAction.SPLIT_OR_PLACE_SINGLE
                            : InventoryAction.PICKUP_OR_SET_DOWN;
                    stack = ((SlotME) slot).getAEStack();
                    if (stack != null
                            && action == InventoryAction.PICKUP_OR_SET_DOWN
                            && (stack.getStackSize() == 0 || GuiScreen.isAltKeyDown())
                            && player.inventory.getItemStack().isEmpty()) {
                        action = InventoryAction.AUTO_CRAFT;
                    }
                    break;
                case QUICK_MOVE:
                    action = (mouseButton == 1) ? InventoryAction.PICKUP_SINGLE : InventoryAction.SHIFT_CLICK;
                    stack = ((SlotME) slot).getAEStack();
                    break;
                case CLONE:
                    stack = ((SlotME) slot).getAEStack();
                    if (stack != null && stack.isCraftable()) {
                        action = InventoryAction.AUTO_CRAFT;
                    } else if (player.capabilities.isCreativeMode) {
                        final IAEItemStack slotItem = ((SlotME) slot).getAEStack();
                        if (slotItem != null) {
                            action = InventoryAction.CREATIVE_DUPLICATE;
                        }
                    }
                    break;
                default:
                case THROW:
                    break;
            }
            if (action != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(stack);
                final PacketInventoryAction p = new PacketInventoryAction(action, this.getInventorySlots().size(), 0);
                NetworkHandler.instance().sendToServer(p);
            }
            return;
        }

        if (!this.disableShiftClick && isShiftKeyDown() && mouseButton == 0) {
            this.disableShiftClick = true;

            if (this.dblWhichItem.isEmpty() || this.blClicked != slot
                    || this.dblClickTimer.elapsed(TimeUnit.MILLISECONDS) > 250) {
                this.blClicked = slot;
                this.dblClickTimer = Stopwatch.createStarted();
                if (slot != null) {
                    this.dblWhichItem = slot.getHasStack() ? slot.getStack().copy() : ItemStack.EMPTY;
                } else {
                    this.dblWhichItem = ItemStack.EMPTY;
                }
            } else if (!this.dblWhichItem.isEmpty()) {
                final List<Slot> slots = this.getInventorySlots();
                for (final Slot inventorySlot : slots) {
                    if (inventorySlot != null && inventorySlot.canTakeStack(this.mc.player)
                            && inventorySlot.getHasStack() && inventorySlot.isSameInventory(slot)
                            && Container.canAddItemToSlot(inventorySlot, this.dblWhichItem, true)) {
                        this.handleMouseClick(inventorySlot, inventorySlot.slotNumber, 0, ClickType.QUICK_MOVE);
                    }
                }
                this.dblWhichItem = ItemStack.EMPTY;
            }

            this.disableShiftClick = false;
        }

        super.handleMouseClick(slot, slotIdx, mouseButton, clickType);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // MUI 控件优先
        for (IMUIWidget widget : this.widgets) {
            if (widget.keyTyped(typedChar, keyCode)) {
                return;
            }
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected boolean checkHotbarKeys(final int keyCode) {
        final Slot theSlot = this.getSlotUnderMouse();

        if (this.mc.player.inventory.getItemStack().isEmpty() && theSlot != null) {
            for (int j = 0; j < 9; ++j) {
                if (keyCode == this.mc.gameSettings.keyBindsHotbar[j].getKeyCode()) {
                    final List<Slot> slots = this.getInventorySlots();
                    for (final Slot s : slots) {
                        if (s.getSlotIndex() == j) {
                            if (s.inventory == ((AEBaseContainer) this.inventorySlots).getPlayerInv() ||
                                    (s instanceof AppEngSlot app
                                            && (app.getItemHandler() instanceof PlayerInvWrapper))) {
                                if (!s.canTakeStack(
                                        ((AEBaseContainer) this.inventorySlots).getPlayerInv().player)) {
                                    return false;
                                }
                            }
                        }
                    }

                    if (theSlot.getSlotStackLimit() == 64) {
                        this.handleMouseClick(theSlot, theSlot.slotNumber, j, ClickType.SWAP);
                        return true;
                    } else {
                        for (final Slot s : slots) {
                            if (s.getSlotIndex() == j
                                    && s.inventory == ((AEBaseContainer) this.inventorySlots).getPlayerInv()) {
                                NetworkHandler.instance()
                                        .sendToServer(new PacketSwapSlots(s.slotNumber, theSlot.slotNumber));
                                return true;
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    // ========== 鼠标滚轮 ==========

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        final int i = Mouse.getEventDWheel();
        if (i != 0 && isShiftKeyDown()) {
            final int x = Mouse.getEventX() * this.width / this.mc.displayWidth;
            final int y = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            this.mouseWheelEvent(x, y, i / Math.abs(i));
        } else if (i != 0 && this.myScrollBar != null) {
            this.myScrollBar.wheel(i);
        }
    }

    /**
     * 鼠标滚轮事件钩子。子类可覆写以实现滚轮操作 ME 槽位等功能。
     *
     * @param x     鼠标屏幕 X
     * @param y     鼠标屏幕 Y
     * @param wheel 滚轮方向（+1 或 -1）
     */
    protected void mouseWheelEvent(final int x, final int y, final int wheel) {
        final Slot slot = this.getSlot(x, y);
        if (slot instanceof SlotME) {
            final IAEItemStack item = ((SlotME) slot).getAEStack();
            if (item != null) {
                ((AEBaseContainer) this.inventorySlots).setTargetStack(item);
                final InventoryAction direction = wheel > 0 ? InventoryAction.ROLL_DOWN : InventoryAction.ROLL_UP;
                final int times = Math.abs(wheel);
                final int inventorySize = this.getInventorySlots().size();
                for (int h = 0; h < times; h++) {
                    final PacketInventoryAction p = new PacketInventoryAction(direction, inventorySize, 0);
                    NetworkHandler.instance().sendToServer(p);
                }
            }
        }
        if (slot instanceof SlotFake) {
            final ItemStack stack = slot.getStack();
            if (stack != ItemStack.EMPTY) {
                final PacketInventoryAction p;
                if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
                    InventoryAction direction = wheel > 0 ? InventoryAction.DOUBLE : InventoryAction.HALVE;
                    p = new PacketInventoryAction(direction, slot.slotNumber, 0);
                } else {
                    InventoryAction direction = wheel > 0 ? InventoryAction.PLACE_SINGLE
                            : InventoryAction.PICKUP_SINGLE;
                    p = new PacketInventoryAction(direction, slot.slotNumber, 0);
                }
                NetworkHandler.instance().sendToServer(p);
            }
        }
    }

    // ========== 工具提示 ==========

    /**
     * 绘制 {@link ITooltip} 控件的工具提示。
     */
    protected void drawTooltip(ITooltip tooltip, int mouseX, int mouseY) {
        final int tx = tooltip.xPos();
        int ty = tooltip.yPos();

        if (tx < mouseX && tx + tooltip.getWidth() > mouseX && tooltip.isVisible()) {
            if (ty < mouseY && ty + tooltip.getHeight() > mouseY) {
                if (ty < 15) {
                    ty = 15;
                }
                final String msg = tooltip.getMessage();
                if (msg != null) {
                    this.drawTooltip(tx + 11, ty + 4, msg);
                }
            }
        }
    }

    /**
     * 绘制字符串工具提示（支持换行符 \n）。
     */
    protected void drawTooltip(int x, int y, String message) {
        String[] lines = message.split("\n");
        this.drawTooltip(x, y, Arrays.asList(lines));
    }

    /**
     * 绘制多行工具提示（带颜色格式化）。
     */
    protected void drawTooltip(int x, int y, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }

        lines = Lists.newArrayList(lines);
        lines.set(0, TextFormatting.WHITE + lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            lines.set(i, TextFormatting.GRAY + lines.get(i));
        }

        this.drawHoveringText(lines, x, y, this.fontRenderer);
    }

    // ========== 贴图绑定 ==========

    /**
     * 绑定 AE2 贴图资源。
     *
     * @param file 相对于 textures/ 目录的文件路径，如 "guis/terminal.png"
     */
    public void bindTexture(final String file) {
        final ResourceLocation loc = new ResourceLocation(AppEng.MOD_ID, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    /**
     * 绑定指定模组的贴图资源。
     *
     * @param base 模组 ID
     * @param file 相对于 textures/ 目录的文件路径
     */
    public void bindTexture(final String base, final String file) {
        final ResourceLocation loc = new ResourceLocation(base, "textures/" + file);
        this.mc.getTextureManager().bindTexture(loc);
    }

    // ========== 槽位定位 ==========

    /**
     * 重新定位 AppEngSlot 的 Y 坐标（用于动态大小的 GUI）。
     * <p>
     * 子类可覆写以提供自定义的槽位定位逻辑。
     * 默认实现将槽位 Y 设为 originalY + ySize - 78 - 5。
     *
     * @param s 要重新定位的 AE 槽位
     */
    protected void repositionSlot(final AppEngSlot s) {
        s.yPos = s.getY() + this.ySize - 78 - 5;
    }

    // ========== 便利方法 ==========

    /**
     * @return 关联的 AE 容器，如果不是 AEBaseContainer 则返回 null
     */
    protected AEBaseContainer getAEContainer() {
        return this.inventorySlots instanceof AEBaseContainer ? (AEBaseContainer) this.inventorySlots : null;
    }

    /**
     * @return 面板左上角在屏幕中的 X 坐标
     */
    public int getGuiLeft() {
        return this.guiLeft;
    }

    /**
     * @return 面板左上角在屏幕中的 Y 坐标
     */
    public int getGuiTop() {
        return this.guiTop;
    }

    /**
     * @return 面板宽度
     */
    public int getXSize() {
        return this.xSize;
    }

    /**
     * @return 面板高度
     */
    public int getYSize() {
        return this.ySize;
    }

    /**
     * 获取 GUI 的显示名称。如果容器有自定义名称则使用自定义名称，否则使用默认名称。
     *
     * @param defaultName 默认显示名称
     * @return 实际显示名称
     */
    protected String getGuiDisplayName(final String defaultName) {
        return this.hasCustomInventoryName() ? this.getInventoryName() : defaultName;
    }

    /**
     * 在指定位置绘制物品图标（16x16）。
     */
    protected void drawItem(final int x, final int y, final ItemStack is) {
        this.zLevel = 100.0F;
        this.itemRender.zLevel = 100.0F;

        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        this.itemRender.renderItemAndEffectIntoGUI(is, x, y);
        GlStateManager.disableDepth();

        this.itemRender.zLevel = 0.0F;
        this.zLevel = 0.0F;
    }

    /**
     * 是否有电力供应。子类可覆写。
     * <p>
     * 影响槽位渲染（无电力时显示暗色遮罩）。
     */
    protected boolean isPowered() {
        return true;
    }

    /**
     * 是否允许空格键点击（区域移动）。子类可覆写。
     */
    protected boolean enableSpaceClicking() {
        return true;
    }

    /**
     * 从按钮的 displayString 解析增减数量。
     */
    protected int getQty(final GuiButton btn) {
        try {
            final DecimalFormat df = new DecimalFormat("+#;-#");
            return df.parse(btn.displayString).intValue();
        } catch (final ParseException e) {
            return 0;
        }
    }

    // ========== 滚动条 ==========

    /**
     * @return 当前滚动条实例，可能为 null
     */
    protected GuiScrollbar getScrollBar() {
        return this.myScrollBar;
    }

    /**
     * 设置滚动条实例。
     */
    protected void setScrollBar(final GuiScrollbar scrollBar) {
        this.myScrollBar = scrollBar;
    }

    // ========== ME 槽位 ==========

    /**
     * @return ME 内部槽位列表
     */
    protected List<InternalSlotME> getMeSlots() {
        return this.meSlots;
    }

    /**
     * @return GuiCustomSlot 列表
     */
    public List<GuiCustomSlot> getGuiSlots() {
        return this.guiSlots;
    }

    // ========== 内部方法 ==========

    /**
     * 获取容器的槽位列表引用。
     */
    protected List<Slot> getInventorySlots() {
        return this.inventorySlots.inventorySlots;
    }

    /**
     * 根据鼠标屏幕坐标查找槽位。
     */
    protected Slot getSlot(final int mouseX, final int mouseY) {
        final List<Slot> slots = this.getInventorySlots();
        for (final Slot slot : slots) {
            if (this.isPointInRegion(slot.xPos, slot.yPos, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    private boolean hasCustomInventoryName() {
        if (this.inventorySlots instanceof AEBaseContainer) {
            return ((AEBaseContainer) this.inventorySlots).getCustomName() != null;
        }
        return false;
    }

    private String getInventoryName() {
        return ((AEBaseContainer) this.inventorySlots).getCustomName();
    }

    /**
     * 更新拖拽分割计算。
     */
    private void updateDragSplitting() {
        ItemStack itemstack = this.mc.player.inventory.getItemStack();

        if (!itemstack.isEmpty() && this.dragSplitting) {
            if (this.dragSplittingLimit == 2) {
                this.dragSplittingRemnant = itemstack.getMaxStackSize();
            } else {
                this.dragSplittingRemnant = itemstack.getCount();

                for (Slot slot : this.dragSplittingSlots) {
                    ItemStack itemstack1 = itemstack.copy();
                    ItemStack itemstack2 = slot.getStack();
                    int i = itemstack2.isEmpty() ? 0 : itemstack2.getCount();
                    Container.computeStackSize(this.dragSplittingSlots, this.dragSplittingLimit, itemstack1, i);
                    int j = Math.min(itemstack1.getMaxStackSize(), slot.getItemStackLimit(itemstack1));

                    if (itemstack1.getCount() > j) {
                        itemstack1.setCount(j);
                    }

                    this.dragSplittingRemnant -= itemstack1.getCount() - i;
                }
            }
        }
    }
}

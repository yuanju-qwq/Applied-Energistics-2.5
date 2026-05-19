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
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.GuiCustomSlot;
import appeng.client.gui.widgets.ITooltip;
import appeng.client.me.InternalSlotME;
import appeng.client.me.SlotDisconnected;
import appeng.client.me.SlotME;
import appeng.client.mui.widgets.MUIButtonPool;
import appeng.client.mui.widgets.MUIScrollBar;
import appeng.client.render.StackSizeRenderer;
import appeng.client.render.stack.AEStackTypeRendererRegistry;
import appeng.client.render.stack.IAEStackTypeRenderer;
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
import appeng.fluids.container.slots.IMEFluidSlot;
import appeng.helpers.InventoryAction;
import appeng.items.misc.ItemEncodedPattern;
import appeng.util.Platform;
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
 *   <li>Scrollbar集成（{@link MUIScrollBar}）</li>
 *   <li>GuiCustomSlot 绘制和交互</li>
 *   <li>特殊槽位渲染（SlotME、流体槽、SlotFake 等）</li>
 *   <li>滚轮事件分发</li>
 *   <li>热键映射和双击逻辑</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public abstract class AEBasePanel extends GuiContainer {

    // ========== MUI widget system ==========

    /** Child widget list */
    protected final List<IMUIWidget> widgets = new ArrayList<>();

    /** Panel title (displayed at top) */
    protected String panelTitle = "";

    /** Background texture (defaults to theme's common panel texture) */
    protected ResourceLocation backgroundTexture = AEMUITheme.TEX_PANEL_BG;

    // ========== Legacy GUI compatibility layer ==========

    /** Custom slot list (compatible with legacy GuiCustomSlot system) */
    protected final List<GuiCustomSlot> guiSlots = new ArrayList<>();

    /** ME internal slot list */
    private final List<InternalSlotME> meSlots = new ArrayList<>();

    /** Scrollbar */
    private MUIScrollBar myScrollBar = null;

    /** Item quantity renderer */
    private final StackSizeRenderer stackSizeRenderer = new StackSizeRenderer();

    /** Drag-click slot record (prevents duplicate triggers) */
    private final Set<Slot> dragClick = new HashSet<>();

    /** Whether GuiCustomSlot click is being processed */
    private boolean handlingCustomSlotClick = false;

    /** Double-click logic related */
    private boolean disableShiftClick = false;
    private Stopwatch dblClickTimer = Stopwatch.createStarted();
    private ItemStack dblWhichItem = ItemStack.EMPTY;
    private Slot blClicked;

    /** Whether keyboard event has been handled (prevents Forge propagation) */
    protected boolean keyHandled = false;

    /** JEI bookmark overlay currently hovered item (used by ClientHelper event cancellation logic) */
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

    // ========== Initialization ==========

    /**
     * MUI 面板初始化入口。
     * <p>
     * <b>生命周期规范（子类必须遵守）：</b>
     * <ol>
     *   <li>子类可在 {@code super.initGui()} 之前计算依赖屏幕尺寸的布局参数（行数、ySize、guiTop 等）。</li>
     *   <li>{@code super.initGui()} 内部会：清理旧 SlotME → 重建 SlotME → 清空 widgets → 调用 {@link #setupWidgets()}。</li>
     *   <li>子类可在 {@code super.initGui()} 之后执行以下操作：
     *       <ul>
     *         <li>Scrollbar的位置和范围设置</li>
     *         <li>槽位重新定位（{@link #repositionSlot}）</li>
     *         <li>guiTop/ySize 等布局坐标修正</li>
     *       </ul>
     *   </li>
     * </ol>
     * <p>
     * <b>禁止在 initGui 中做的事情：</b>
     * <ul>
     *   <li>创建搜索框、输入框等文本控件 — 应放入 {@link #setupWidgets()}</li>
     *   <li>创建功能按钮（如排序、过滤、终端样式等） — 应放入 {@link #setupWidgets()}</li>
     *   <li>注册 MUI widget — 必须通过 {@link #setupWidgets()} 和 {@link #addWidget(IMUIWidget)}</li>
     * </ul>
     * <p>
     * 注意：由于 MC 会在窗口缩放时反复调用 initGui()，所有在此方法中的操作必须是幂等的。
     */
    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        // Clean up old SlotME and rebuild
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
     * 子类覆写此方法来创建和注册 UI 控件。
     * <p>
     * <b>生命周期规范：</b>
     * <ul>
     *   <li>此方法在每次 {@link #initGui()} 时被调用（包括窗口缩放）。</li>
     *   <li>调用时 widgets 列表已被清空，子类无需手动清理。</li>
     *   <li>子类应在此方法中创建并注册所有 MUI 控件、搜索框、按钮等。</li>
     *   <li>通过 {@link #addWidget(IMUIWidget)} 注册 MUI 控件。</li>
     *   <li>通过 {@code this.buttonList.add(...)} 注册旧式按钮（暂时兼容）。</li>
     *   <li>不应在此方法中执行依赖 guiLeft/guiTop 绝对坐标的布局定位
     *       （因为某些子类会在 super.initGui() 之后修改 guiTop）。
     *       按钮的绝对坐标定位应延迟到 initGui() 中 super.initGui() 之后。</li>
     * </ul>
     * <p>
     * 在 {@link #initGui()} 中被调用。
     */
    protected abstract void setupWidgets();

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

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

    // ========== Rendering pipeline ==========

    @Override
    public void drawScreen(final int mouseX, final int mouseY, final float partialTicks) {
        super.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // Draw GuiCustomSlot (in panel coordinate space)
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

        // Hover tooltip (MC native slots)
        this.renderHoveredToolTip(mouseX, mouseY);

        // Button and label tooltips
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
        // MUI widget tooltips (for widgets implementing ITooltip but not in buttonList)
        for (final IMUIWidget w : this.widgets) {
            if (w instanceof ITooltip && !(w instanceof net.minecraft.client.gui.GuiButton)) {
                this.drawTooltip((ITooltip) w, mouseX, mouseY);
            }
            // Draw tooltips for active buttons inside MUIButtonPool
            if (w instanceof MUIButtonPool) {
                ((MUIButtonPool) w).drawTooltips(this, mouseX, mouseY);
            }
        }
        GlStateManager.enableDepth();

        // Update JEI bookmark hovered item (for ClientHelper event cancellation logic)
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

        // Background texture
        if (this.backgroundTexture != null) {
            this.mc.getTextureManager().bindTexture(this.backgroundTexture);
            this.drawTexturedModalRect(ox, oy, 0, 0, this.xSize, this.ySize);
        }

        // Legacy drawBG hook
        this.drawBG(ox, oy, mouseX, mouseY);

        // Optional slot background rendering
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

        // GuiCustomSlot background drawing
        for (final GuiCustomSlot slot : this.guiSlots) {
            slot.drawBackground(ox, oy);
        }

        // MUI widget background layer
        for (IMUIWidget widget : this.widgets) {
            widget.drawBackground(this, this.guiLeft, this.guiTop, mouseX, mouseY, partialTicks);
        }
    }

    @Override
    protected final void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        final int ox = this.guiLeft;
        final int oy = this.guiTop;
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        // Scrollbar绘制
        if (this.myScrollBar != null) {
            this.myScrollBar.draw(this);
        }

        // Panel title
        if (this.panelTitle != null && !this.panelTitle.isEmpty()) {
            this.fontRenderer.drawString(this.panelTitle,
                    AEMUITheme.PANEL_PADDING,
                    AEMUITheme.PANEL_PADDING / 2,
                    AEMUITheme.COLOR_TITLE);
        }

        // Legacy drawFG hook
        this.drawFG(ox, oy, mouseX, mouseY);

        // MUI widget foreground layer
        for (IMUIWidget widget : this.widgets) {
            widget.drawForeground(this, mouseX - this.guiLeft, mouseY - this.guiTop);
        }
    }

    /**
     * 背景绘制钩子。子类覆写以绘制面板背景内容。
     * <p>
     * <b>职责规范：</b>
     * <ul>
     *   <li>绘制面板底板贴图（bindTexture + drawTexturedModalRect）</li>
     *   <li>绘制分区背景、列表底图、输入框背景承载区等稳定背景内容</li>
     *   <li>绘制 view cell 变化检测等与布局相关的低频更新逻辑</li>
     * </ul>
     * <p>
     * <b>不应在此方法中做的事情：</b>
     * <ul>
     *   <li>创建或注册控件（应在 setupWidgets 中）</li>
     *   <li>绘制文本标题或动态 tooltip（应在 drawFG 中）</li>
     *   <li>修改 buttonList 或 inventorySlots（不属于绘制职责）</li>
     * </ul>
     * <p>
     * 坐标参数说明：offsetX/offsetY 是面板左上角的屏幕坐标。
     *
     * @param offsetX 面板左上角屏幕 X（= guiLeft）
     * @param offsetY 面板左上角屏幕 Y（= guiTop）
     * @param mouseX  鼠标屏幕 X
     * @param mouseY  鼠标屏幕 Y
     */
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
    }

    /**
     * 前景绘制钩子。子类覆写以绘制面板前景内容。
     * <p>
     * <b>职责规范：</b>
     * <ul>
     *   <li>绘制Panel title文字</li>
     *   <li>绘制轻量 overlay、局部提示文本</li>
     *   <li>绘制动态列表的文字标签</li>
     *   <li>可创建动态 SlotDisconnected（因为需要每帧基于滚动位置重建）</li>
     * </ul>
     * <p>
     * <b>不应在此方法中做的事情：</b>
     * <ul>
     *   <li>绘制底板贴图或大面积背景（应在 drawBG 中）</li>
     *   <li>创建或注册持久控件（应在 setupWidgets 中）</li>
     * </ul>
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

    // ========== GuiCustomSlot rendering ==========

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

    // ========== Special slot rendering ==========

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
            // Fluid ME terminal slot — render via generic IAEStackTypeRenderer
            final IMEFluidSlot slot = (IMEFluidSlot) s;
            final IAEFluidStack fs = slot.getAEFluidStack();

            if (fs != null && this.isPowered()) {
                final IAEStackTypeRenderer renderer = AEStackTypeRendererRegistry.getRenderer(fs);
                renderer.renderIcon(Minecraft.getMinecraft(), fs, s.xPos, s.yPos);
                renderer.renderStackSize(this.fontRenderer, fs, s.xPos, s.yPos);
            } else if (!this.isPowered()) {
                drawRect(s.xPos, s.yPos, 16 + s.xPos, 16 + s.yPos, 0x66111111);
            }
            return;
        } else if (s instanceof SlotFake && !s.getStack().isEmpty()) {
            // SlotFake may contain a FluidDummyItem (or other non-item representation):
            // try to convert to a generic IAEStack and render via the appropriate renderer
            final IAEStack<?> converted = Platform.convertSlotStackToAEStack(s.getStack());
            if (converted != null && !converted.isItem()) {
                final IAEStackTypeRenderer renderer = AEStackTypeRendererRegistry.getRenderer(converted);
                renderer.renderIcon(Minecraft.getMinecraft(), converted, s.xPos, s.yPos);
                renderer.renderStackSize(this.fontRenderer, converted, s.xPos, s.yPos);
                return;
            }
            // Fall through to default rendering for regular items in SlotFake
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
                            // Ignore icon rendering error
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

    // ========== Input events ==========

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        this.dragClick.clear();
        this.handlingCustomSlotClick = false;

        // MUI widgets have priority
        for (IMUIWidget widget : this.widgets) {
            if (widget.mouseClicked(mouseX - this.guiLeft, mouseY - this.guiTop, mouseButton)) {
                return;
            }
        }

        // Right-click triggers button
        if (mouseButton == 1) {
            for (final Object o : this.buttonList) {
                final GuiButton guibutton = (GuiButton) o;
                if (guibutton.mousePressed(this.mc, mouseX, mouseY)) {
                    super.mouseClicked(mouseX, mouseY, 0);
                    return;
                }
            }
        }

        // GuiCustomSlot click
        for (GuiCustomSlot slot : this.guiSlots) {
            if (this.isPointInRegion(slot.xPos(), slot.yPos(), slot.getWidth(), slot.getHeight(), mouseX, mouseY)
                    && slot.canClick(this.mc.player)) {
                this.handlingCustomSlotClick = true;
                slot.slotClicked(this.mc.player.inventory.getItemStack(), mouseButton);
                return;
            }
        }

        // Scrollbar点击
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
        // MUI widgets have priority
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

    // ========== Mouse wheel ==========

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

    // ========== Tooltips ==========

    /**
     * 绘制 {@link ITooltip} 控件的工具提示。
     */
    public void drawTooltip(ITooltip tooltip, int mouseX, int mouseY) {
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

    // ========== Texture binding ==========

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

    // ========== Slot positioning ==========

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

    /**
     * 遍历容器中所有 AppEngSlot 并调用 {@link #repositionSlot(AppEngSlot)} 重新定位。
     * <p>
     * 子类在 initGui() 末尾调用此方法即可批量重定位所有槽位，无需手写循环。
     * 可通过覆写 {@link #repositionSlot(AppEngSlot)} 来自定义每个槽位的定位逻辑。
     */
    protected void repositionAllSlots() {
        for (final Slot slot : this.getInventorySlots()) {
            if (slot instanceof AppEngSlot appEngSlot) {
                this.repositionSlot(appEngSlot);
            }
        }
    }

    // ========== Layout utility methods ==========

    /**
     * 根据终端样式设置和最大可用行数，计算实际应显示的行数。
     * <p>
     * 消除各面板中重复的 TerminalStyle if-else 分支。
     *
     * @param maxRows 屏幕可容纳的最大行数
     * @return 根据终端样式计算的实际行数
     */
    protected static int computeTerminalRows(int maxRows) {
        final Enum<?> terminalStyle = appeng.core.AEConfig.instance().getConfigManager()
                .getSetting(appeng.api.config.Settings.TERMINAL_STYLE);

        if (terminalStyle == appeng.api.config.TerminalStyle.FULL) {
            return maxRows;
        } else if (terminalStyle == appeng.api.config.TerminalStyle.TALL) {
            return (int) Math.ceil(maxRows * 0.75);
        } else if (terminalStyle == appeng.api.config.TerminalStyle.MEDIUM) {
            return (int) Math.ceil(maxRows * 0.5);
        } else if (terminalStyle == appeng.api.config.TerminalStyle.SMALL) {
            return (int) Math.ceil(maxRows * 0.25);
        } else {
            return maxRows;
        }
    }

    /**
     * 根据当前面板高度 (ySize) 和屏幕高度 (height) 计算垂直居中位置并设置 guiTop。
     * <p>
     * 采用非对称居中策略：当面板比屏幕高时，偏向顶部显示（除数 3.8f），否则正常居中（除数 2.0f）。
     */
    protected void centerVertically() {
        final int unusedSpace = this.height - this.ySize;
        this.guiTop = (int) Math.floor(unusedSpace / (unusedSpace < 0 ? 3.8f : 2.0f));
    }

    // ========== Convenience methods ==========

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

    // ========== Scrollbar ==========

    /**
     * @return 当前Scrollbar实例，可能为 null
     */
    protected MUIScrollBar getScrollBar() {
        return this.myScrollBar;
    }

    /**
     * 设置Scrollbar实例。
     */
    protected void setScrollBar(final MUIScrollBar scrollBar) {
        this.myScrollBar = scrollBar;
    }

    // ========== ME slots ==========

    /**
     * @return ME internal slot list
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

    /**
     * @return JEI exclusion areas for this panel. Subclasses should override to provide exclusion zones.
     */
    public List<java.awt.Rectangle> getJEIExclusionArea() {
        return java.util.Collections.emptyList();
    }

    // ========== Internal methods ==========

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

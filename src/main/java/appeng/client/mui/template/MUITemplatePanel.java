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

package appeng.client.mui.template;

import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.client.me.ItemRepo;
import appeng.client.mui.AEBaseMEPanel;
import appeng.client.mui.AEBasePanel;
import appeng.client.mui.AEMUITheme;
import appeng.client.mui.module.DynamicListModule;
import appeng.client.mui.widgets.IMUISortSource;
import appeng.client.mui.widgets.MUIScrollBar;

/**
 * MUI 标准列表面板模板 —— 新 GUI 的标准基类。
 *
 * <p>所有基于 resource list 的新 GUI 面板应继承此模板而非直接继承
 * {@link AEBasePanel} 或 {@link AEBaseMEPanel}，
 * 以确保统一的生命周期管理、控件注册规范和数据输入约定。
 *
 * <h3>模板约定</h3>
 * <ul>
 *   <li>控件注册统一在 {@link #setupWidgets()} 中通过 {@code addWidget(IMUIWidget)} 完成，
 *       不在 {@code initGui()} 或 {@code drawBG/drawFG} 中创建控件</li>
 *   <li>公共能力通过组合 {@code module} 包下的可复用模块实现
 *       （如 {@link DynamicListModule}、{@link appeng.client.mui.module.SearchBarModule} 等）</li>
 *   <li>子类仅保留必要的业务编排逻辑（容器交互、网络包收发），不自行管理控件创建</li>
 *   <li>数据输入使用 {@link GenericStack} / {@link KeyCounter} / {@link AEKey}，
 *       避免引入 {@link appeng.api.storage.data.IAEStack}</li>
 * </ul>
 *
 * <h3>标准初始化流程</h3>
 * <ol>
 *   <li>{@code initGui()} 开始：子类计算依赖屏幕尺寸的布局参数（行数、ySize、guiTop）</li>
 *   <li>{@code super.initGui()}：清理旧 slot → 重建 slot → 清空 widgets → 调用 {@link #setupWidgets()}</li>
 *   <li>{@code initGui()} 结束：调用 {@link #initModules()} 初始化模块</li>
 * </ol>
 *
 * <h3>标准数据更新流程</h3>
 * <ol>
 *   <li>容器收到网络包后调用 {@link #postUpdate(GenericStack, boolean)} 或
 *       {@link #postKeyCounterUpdate(KeyCounter)} 传入数据</li>
 *   <li>模板内部通过 {@link DynamicListModule} 写入 {@link ItemRepo}</li>
 *   <li>调用 {@link #refreshList()} 刷新视图和 scrollbar</li>
 * </ol>
 *
 * <h3>新面板实现示例</h3>
 * <pre>
 * public class MUIMyNewPanel extends MUITemplatePanel {
 *
 *     public MUIMyNewPanel(Container container) {
 *         super(container, 9, 6);
 *         this.ySize = 200;
 *     }
 *
 *     {@literal @}Override
 *     protected String getPanelTitle() {
 *         return "My Panel";
 *     }
 *
 *     {@literal @}Override
 *     protected String getBackgroundTexture() {
 *         return "guis/my_panel.png";
 *     }
 * }
 * </pre>
 */
@SideOnly(Side.CLIENT)
public abstract class MUITemplatePanel extends AEBaseMEPanel
        implements DynamicListModule.Host, IMUISortSource {

    // ========== 可复用模块 ==========

    /** 资源列表模块：虚拟槽网格 + 滚动 + 高亮 */
    protected final DynamicListModule listModule;

    /** 滚动条 */
    protected final MUIScrollBar scrollBar;

    // ========== 数据源 ==========

    /** 客户端资源仓库，使用 AEKey/KeyCounter/GenericStack 存储数据 */
    protected final ItemRepo repo;

    // ========== 布局参数 ==========

    /** 网格列数 */
    protected final int columns;

    /** 网格显示行数 */
    protected int rows;

    // ========== ISortSource 默认实现 ==========

    /** 排序方式 */
    protected Enum<?> sortBy = SortOrder.NAME;

    /** 排序方向 */
    protected Enum<?> sortDir = SortDir.ASCENDING;

    /** 显示模式 */
    protected Enum<?> sortDisplay = ViewItems.STORED;

    // ========== 构造 ==========

    /**
     * @param container 关联的 AE 容器
     * @param columns   资源网格列数
     * @param gridRows  资源网格初始行数（可在 initGui 中重新计算）
     */
    public MUITemplatePanel(Container container, int columns, int gridRows) {
        super(container);

        this.columns = columns;
        this.rows = gridRows;

        // 滚动条
        this.scrollBar = new MUIScrollBar();
        this.setScrollBar(this.scrollBar);

        // 数据仓库（IMUIScrollSource + IMUISortSource）
        this.repo = new ItemRepo(
                () -> this.scrollBar.getCurrentScroll(),
                this);

        // 动态列表模块
        this.listModule = new DynamicListModule(this, 8, 18, columns);
        this.listModule.setRows(gridRows);
    }

    // ========== 控件注册（模板方法） ==========

    /**
     * 子类覆写此方法来注册业务特定的控件。
     *
     * <p>模板已预留 {@link DynamicListModule} 等核心模块的初始化入口，
     * 子类可通过 {@code super.setupWidgets()} + 额外 {@code addWidget(...)} 来扩展。
     * 注意：DynamicListModule 的 init() 需在 super.initGui() 之后通过 {@link #initModules()} 调用，
     * 不要在此方法中调用。
     */
    @Override
    protected void setupWidgets() {
    }

    /**
     * 完成模块初始化。子类在 {@code super.initGui()} 之后调用。
     * <p>
     * 标准用法：
     * <pre>
     * super.initGui();
     * this.initModules();
     * </pre>
     */
    protected void initModules() {
        this.listModule.setRows(this.rows);
        this.listModule.init();
        this.listModule.updateScrollBar();
    }

    // ========== 数据更新（仅 GenericStack / KeyCounter / AEKey 输入） ==========

    /**
     * 更新单个资源条目。
     *
     * @param stack     资源标识+数量
     * @param craftable 是否可合成
     */
    public void postUpdate(GenericStack stack, boolean craftable) {
        this.listModule.postUpdate(stack, craftable);
    }

    /**
     * 批量更新资源条目。
     *
     * @param stacks 资源列表
     */
    public void postGenericStackUpdate(List<GenericStack> stacks) {
        this.listModule.postGenericStackUpdate(stacks);
    }

    /**
     * 通过 KeyCounter 批量更新资源条目。
     * <p>
     * 推荐方式：服务端通过 {@code KeyCounter} 推送完整状态，
     * 客户端直接传入刷新。
     *
     * @param counter   KeyCounter 中的资源数据
     * @param craftable 所有条目是否可合成
     */
    public void postKeyCounterUpdate(KeyCounter counter, boolean craftable) {
        for (var entry : counter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            this.repo.postUpdate(key, amount, craftable);
        }
    }

    /**
     * 刷新列表视图和 scrollbar 范围。
     * 在数据更新后调用。
     */
    public void refreshList() {
        this.listModule.refreshView();
    }

    /**
     * 清空所有数据。
     */
    public void clearData() {
        this.listModule.clear();
    }

    // ========== 绘制 ==========

    /**
     * 子类可覆写此方法返回自定义背景贴图路径（相对于 textures/ 目录）。
     * 返回 null 则使用默认主题背景。
     */
    protected String getBackgroundTexture() {
        return null;
    }

    @Override
    protected void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        String tex = getBackgroundTexture();
        if (tex != null) {
            this.bindTexture(tex);
            this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
        }
    }

    @Override
    protected void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        String title = getPanelTitle();
        if (title != null && !title.isEmpty()) {
            this.fontRenderer.drawString(title,
                    AEMUITheme.PANEL_PADDING,
                    AEMUITheme.PANEL_PADDING / 2,
                    AEMUITheme.COLOR_TITLE);
        }
    }

    /**
     * @return 面板标题，显示在左上角
     */
    protected abstract String getPanelTitle();

    // ========== DynamicListModule.Host 实现 ==========

    @Override
    public AEBasePanel getPanel() {
        return this;
    }

    @Override
    public int getGuiLeft() {
        return this.guiLeft;
    }

    @Override
    public int getGuiTop() {
        return this.guiTop;
    }

    @Override
    public ItemRepo getRepo() {
        return this.repo;
    }

    @Override
    public MUIScrollBar getScrollBar() {
        return this.scrollBar;
    }

    @Override
    public boolean hasPower() {
        return true;
    }

    // ========== ISortSource 实现 ==========

    @Override
    public Enum getSortBy() {
        return this.sortBy;
    }

    @Override
    public Enum getSortDir() {
        return this.sortDir;
    }

    @Override
    public Enum getSortDisplay() {
        return this.sortDisplay;
    }

    // ========== 便捷方法 ==========

    /**
     * 根据可用像素高度计算最大行数。
     *
     * @param availableHeight 可用像素高度
     * @param slotSize        每个槽位占用的像素（含间距），默认 18
     * @return 可容纳的最大行数
     */
    protected static int computeMaxRows(int availableHeight, int slotSize) {
        return Math.max(1, availableHeight / slotSize);
    }
}

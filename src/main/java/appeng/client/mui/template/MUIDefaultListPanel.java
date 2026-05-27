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

import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.client.mui.AEBaseMEPanel;
import appeng.client.mui.AEBasePanel;

/**
 * 通用列表面板 —— {@link MUITemplatePanel} 的默认非抽象实现。
 *
 * <p>当业务面板不需要额外的 {@link #setupWidgets()} 重写时，
 * 直接使用此类即可快速创建一个带滚动列表的 MUI 面板。
 *
 * <p>示例 — 新 GUI 面板的实现不再复制旧代码结构：
 * <pre>
 * // 服务端注册：
 * AEMUIGuiFactory.register(MyGuiKey.INSTANCE,
 *     (ip, host) -&gt; new MyContainer(ip, host),
 *     (ip, host) -&gt; new MUIDefaultListPanel(
 *         new MyContainer(ip, host), 9, 6, "My Title", "guis/my_panel.png"));
 *
 * // 数据更新（客户端收到网络包后）：
 * panel.postKeyCounterUpdate(counter, false);
 * panel.refreshList();
 * </pre>
 */
@SideOnly(Side.CLIENT)
public class MUIDefaultListPanel extends MUITemplatePanel {

    private final String panelTitle;
    private final String backgroundTexture;

    /**
     * @param container          关联的 AE 容器
     * @param columns            资源网格列数
     * @param gridRows           资源网格初始行数
     * @param panelTitle         面板标题
     * @param backgroundTexture  背景贴图路径（相对于 textures/ 目录，为 null 则使用默认主题背景）
     */
    public MUIDefaultListPanel(Container container, int columns, int gridRows,
            String panelTitle, String backgroundTexture) {
        super(container, columns, gridRows);
        this.panelTitle = panelTitle;
        this.backgroundTexture = backgroundTexture;
    }

    @Override
    protected String getPanelTitle() {
        return this.panelTitle;
    }

    @Override
    protected String getBackgroundTexture() {
        return this.backgroundTexture;
    }
}

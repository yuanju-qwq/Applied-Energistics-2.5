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

package appeng.client.mui.config;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import appeng.core.AEConfig;
import appeng.core.AppEng;

/**
 * MUI 版 AE2 配置 GUI。
 * <p>
 * 替代旧的 {@link appeng.client.gui.config.AEConfigGui}，
 * 基于 Forge {@link GuiConfig} 框架，展示 AE2 配置文件中的各配置类别。
 * <p>
 * 过滤掉 {@code versionchecker} 和 {@code settings} 类别，
 * 以及所有子类别（由父类别递归展示）。
 */
public class MUIConfigGui extends GuiConfig {

    public MUIConfigGui(final GuiScreen parent) {
        super(parent, getConfigElements(), AppEng.MOD_ID, false, false,
                GuiConfig.getAbridgedConfigPath(AEConfig.instance().getFilePath()));
    }

    /**
     * 收集所有顶级配置类别并转换为 Forge 配置元素列表。
     */
    private static List<IConfigElement> getConfigElements() {
        final List<IConfigElement> list = new ArrayList<>();

        for (final String cat : AEConfig.instance().getCategoryNames()) {
            // 跳过内部类别
            if (cat.equals("versionchecker")) {
                continue;
            }

            if (cat.equals("settings")) {
                continue;
            }

            final ConfigCategory cc = AEConfig.instance().getCategory(cat);

            // 子类别由父类别递归处理，不单独添加
            if (cc.isChild()) {
                continue;
            }

            final ConfigElement ce = new ConfigElement(cc);
            list.add(ce);
        }

        return list;
    }
}

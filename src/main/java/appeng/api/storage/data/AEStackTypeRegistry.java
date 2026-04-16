/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.storage.data;

import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 全局注册表，管理所有 {@link IAEStackType} 实例。
 * <p>
 * 内建注册了 ITEM 和 FLUID 两种类型。第三方 mod 可在 preInit 阶段注册额外类型。
 * <p>
 * 每种类型同时分配一个网络 ID (byte)，用于在数据包中标识栈的类型。
 */
public final class AEStackTypeRegistry {

    /** 网络包中用于表示 null 栈的 ID */
    public static final byte NULL_NETWORK_ID = 0;

    private static final int MINIMUM_NETWORK_ID = 1;

    private static final Map<String, IAEStackType<?>> registry = new LinkedHashMap<>();
    private static final Map<IAEStackType<?>, Byte> typeToNetworkIdMap = new IdentityHashMap<>();
    private static final Map<Byte, IAEStackType<?>> networkIdToTypeMap = new HashMap<>();

    private AEStackTypeRegistry() {}

    /**
     * 注册一种新的栈类型。
     * <p>
     * 应在 mod 的 preInit 阶段调用。在 preInit 之后注册可能导致网络序列化问题。
     *
     * @param type 要注册的类型
     */
    public static void register(@Nonnull IAEStackType<?> type) {
        registry.put(type.getId(), type);
    }

    /**
     * 初始化网络 ID。在所有类型注册完毕后调用一次。
     */
    public static void initNetworkIds() {
        typeToNetworkIdMap.clear();
        networkIdToTypeMap.clear();
        byte id = MINIMUM_NETWORK_ID;
        for (IAEStackType<?> type : getSortedTypes()) {
            typeToNetworkIdMap.put(type, id);
            networkIdToTypeMap.put(id, type);
            id++;
        }
    }

    /**
     * 获取指定类型的网络 ID。
     *
     * @throws IllegalStateException 如果类型未注册或网络 ID 尚未初始化
     */
    public static byte getNetworkId(@Nonnull IAEStackType<?> type) {
        Byte id = typeToNetworkIdMap.get(type);
        if (id == null || id < MINIMUM_NETWORK_ID) {
            throw new IllegalStateException(
                    "Cannot get network id for stack type " + type.getId()
                            + " because it is not registered or not initialized yet.");
        }
        return id;
    }

    /**
     * 按字符串 ID 查找类型。
     */
    @Nullable
    public static IAEStackType<?> getType(@Nonnull String id) {
        return registry.get(id);
    }

    /**
     * 按网络 ID 查找类型。
     */
    @Nullable
    public static IAEStackType<?> getTypeFromNetworkId(byte id) {
        return networkIdToTypeMap.get(id);
    }

    /**
     * @return 所有已注册的类型（无序）
     */
    @Nonnull
    public static Collection<IAEStackType<?>> getAllTypes() {
        return registry.values();
    }

    /**
     * 返回排序后的类型列表：ITEM 和 FLUID 排在最前，其余按 ID 字母序。
     */
    @Nonnull
    public static List<IAEStackType<?>> getSortedTypes() {
        List<IAEStackType<?>> result = new ArrayList<>();
        List<IAEStackType<?>> others = new ArrayList<>();

        IAEStackType<?> itemType = registry.get("item");
        IAEStackType<?> fluidType = registry.get("fluid");

        for (IAEStackType<?> type : registry.values()) {
            if (type == itemType || type == fluidType) {
                continue;
            }
            others.add(type);
        }

        others.sort(Comparator.comparing(IAEStackType::getId));

        if (itemType != null) {
            result.add(itemType);
        }
        if (fluidType != null) {
            result.add(fluidType);
        }
        result.addAll(others);

        return result;
    }
}

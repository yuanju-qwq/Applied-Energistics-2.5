package appeng.crafting.v2.resolvers;

import java.util.List;

import javax.annotation.Nonnull;

import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;

/**
 * 合成请求解析器接口。实现类负责提供解决合成请求的可能方案。
 *
 * @see CraftingRequestResolver#provideCraftingRequestResolvers(CraftingRequest, CraftingContext)
 */
@FunctionalInterface
public interface CraftingRequestResolver {

    /**
     * 提供解决一个合成步骤的可能方案列表。更高优先级 = 先尝试。
     *
     * @param request 正在解析的请求
     * @param context ME 系统、任务队列和 pattern 缓存
     * @return 可能的方案列表 — 无方案则返回空列表
     */
    @Nonnull
    List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest request,
            @Nonnull CraftingContext context);
}

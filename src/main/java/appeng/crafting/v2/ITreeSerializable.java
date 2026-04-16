package appeng.crafting.v2;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * v2 合成树的可序列化节点接口。
 * 所有参与合成树序列化/反序列化的类都需要实现此接口。
 */
public interface ITreeSerializable {

    /**
     * 序列化此节点并返回子节点列表。
     */
    List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException;

    /**
     * 反序列化后，用反序列化出来的子节点列表填充此节点。
     */
    default void loadChildren(List<ITreeSerializable> children) throws IOException {}

    /**
     * @return 序列化子节点时，作为 parent 传入的对象。默认为 this。
     */
    default ITreeSerializable getSerializationParent() {
        return this;
    }
}

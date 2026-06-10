# Applied Energistics 2.5 ModularUI 与 AEKey 体系迁移方案

## 1. 文档目标
两条迁移主线相互关联但可独立推进：

- **ModularUI 迁移**：将客户端 GUI 从旧式大类写法迁移为 MUI 结构化面板体系
- **AEKey 体系迁移**：将数据模型从旧 `IAEStack<T>` / `IItemList<T>` 泛型体系迁移为不可变 `AEKey` + `KeyCounter` 体系



流体容器交互（drainFromContainer/fillToContainer, 46处调用）	~7个	高
网络序列化（PacketMEInventoryUpdate, PacketTargetStack等）	~6个	高
显示/渲染（AEBasePanel, MUIStackRenderer, ItemRepo等）	~10个	中
getStorageList()（NetworkMonitor, MEMonitorPassThrough, ContainerPatternEncoder）	3个	中
AEStackTypeRegistry（byte ID分配，被AEKeyType桥接调用）	1个	中
IItemContainer/IItemList（ItemModList唯一实现者）	2个	低
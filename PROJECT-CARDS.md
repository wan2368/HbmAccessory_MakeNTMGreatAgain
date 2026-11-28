<!-- deep-ptc:project-cards v1 next=R6,F7,Q8 -->
# Project cards

> Maintained by Deep PTC in DeepSeek Harness: edit in a Deep PTC conversation, view 特点卡片 / Project cards.
> User-approved settings guide this project. Features and requirements are records that can be checked and corrected.

## User requirements / 用户要求
<!-- card id=requirements kind=requirements rev=6 updated=2026-11-27T16:11:32.203Z -->

- **Q1** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q2** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q3** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q4** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q5** 本阶段仅实现大型火力发电站 GUI 与基础状态，不执行消耗或发电逻辑；兼容 Java 8 / Forge 1.7.10，验证只运行 compileJava，不运行完整 build 或打包。
- **Q6** 高压聚爆釜定位在早期工业阶段，机器本体配方以铸造钢、聚合物等同阶段材料为主。
- **Q7** 高压聚爆釜仅用于加工，不能产生或输出电力。


## Implemented features / 已实现功能
<!-- card id=features kind=features rev=8 updated=2026-11-28T08:17:22.997Z -->

- **F1** 大型火力发电站四个端口使用专用 TileEntityProxyCombo 桥接类；构造和读档启用库存及流体代理能力，提供只输出电力的 provider 接口，并安全委托核心能力。真实物品/流体/电力传输仍依赖核心实现对应库存、流体和发电接口。
- **F2** 大型火力发电站 GUI 使用 8 个真实容器槽位，显示持久化基础热值/流体/电力状态，开关经服务端校验并同步；燃烧贴图在机器运行逻辑实现前始终隐藏。
- **F3** 量子脉冲元件使用 v4 贴图；非 528 在装配机产出，528 在精密装配机成品／破损各 50%，破损包装与回收沿用 HBM registerPair（每单位材料普通 75%、昂贵 50%）。入口为 ModRecipes.addQuantumPulseAssemblyRecipe / addQuantumPulsePrecAssRecipe，挂载配方加载监听并按名判重。已通过 compileJava，未做实机验证。
- **F4** 穿戴装有复合材料储罐的护腿时，CompositeTankHazardGuard 拦截背包物品的易燃易爆、遇水反应、自燃和致盲词条。未装备时由 HBM 原版处理，其他来源的失明不受影响；中英文提示已更新。验证：compileJava 通过，未进行游戏内验证。
- **F5** 流体传送器和物品传送器顶部三行控件共用左右边界，六个频率按钮等宽排列，绘制、标签、悬停和点击使用同组坐标；按钮操作和传送逻辑不变。入口：GUIXenFluidTransceiver / GUIXenItemTransceiver。已静态核对坐标及通过 compileJava，未进行游戏内验证。
- **F6** 投送探头区块重载时重新登记中继，路径缓存随拓扑和探头朝向变化失效；发送端有可达目标时不被其他不可达目标的距离错误覆盖。连接列表独立显示路径距离和实际中继数，悬停显示完整名称与坐标。入口：DeliveryRelayNetwork、TileEntityAtomicProbe、GUIAtomicProbe。静态核对及 compileJava 通过，未做游戏内或性能测试。 探头 XBX/HVH/XBX 产出 32 个，节点 XBX/BVB/XBX 产出 8 个。X 铸造钨板、V 不稳态 X 晶体、H Desh 马达、B 偏光镜片。连接器由集成电路板和 Desh 螺丝刀无序合成 1 个，螺丝刀按原版规则返还且不损耗。配方入口：ModRecipes.addAtomicDeliveryCraftingRecipes。


## 技术栈与源码基线
<!-- card id=c1 kind=setting category=stack rev=1 updated=2026-11-27T16:00:12.655Z -->

- **R1** 模组代码必须兼容 Java 8 与 Minecraft Forge 1.7.10。
- **R2** 以原版 HBM 源码作为开发和编译基线；航天版用于运行兼容性验证，不在源码中直接引用其独有字段。


## 配方注册约定
<!-- card id=c2 kind=setting category=conventions rev=1 updated=2026-11-27T16:00:28.752Z -->

- **R3** 给现有 HBM 机器新增附属配方时，沿用现有注册入口，确保配方重载后仍存在且不重复注册。


## 第三方硬依赖
<!-- card id=c3 kind=setting category=constraints rev=1 updated=2026-11-27T16:00:42.528Z -->

- **R4** 是否引入第三方模组硬依赖按功能决定；新增硬依赖前必须先取得用户确认。


## 默认验证范围
<!-- card id=c4 kind=setting category=workflow rev=1 updated=2026-11-27T16:06:45.435Z -->

- **R5** 普通代码改动默认只运行 compileJava；完整构建或实机测试须另行确认。

<!-- deep-ptc:project-cards v1 next=R1,F3,Q6 -->
# Project cards

> Maintained by Deep PTC in DeepSeek Harness: edit in a Deep PTC conversation, view 特点卡片 / Project cards.
> User-approved settings guide this project. Features and requirements are records that can be checked and corrected.

## User requirements / 用户要求
<!-- card id=requirements kind=requirements rev=5 updated=2026-11-21T11:03:33.769Z -->

- **Q1** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q2** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q3** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q4** Java 8 与 Forge 1.7.10 兼容性是硬约束。
- **Q5** 本阶段仅实现大型火力发电站 GUI 与基础状态，不执行消耗或发电逻辑；兼容 Java 8 / Forge 1.7.10，验证只运行 compileJava，不运行完整 build 或打包。


## Implemented features / 已实现功能
<!-- card id=features kind=features rev=2 updated=2026-11-21T11:03:33.758Z -->

- **F1** 大型火力发电站四个端口使用专用 TileEntityProxyCombo 桥接类；构造和读档启用库存及流体代理能力，提供只输出电力的 provider 接口，并安全委托核心能力。真实物品/流体/电力传输仍依赖核心实现对应库存、流体和发电接口。
- **F2** 大型火力发电站 GUI 使用 8 个真实容器槽位，显示持久化基础热值/流体/电力状态，开关经服务端校验并同步；燃烧贴图在机器运行逻辑实现前始终隐藏。

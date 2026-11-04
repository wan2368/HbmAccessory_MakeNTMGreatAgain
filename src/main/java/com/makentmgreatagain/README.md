# 包结构

| 包 | 作用 |
|---|---|
| init | 物品/方块/流体/配方/成就/网络初始化 |
| items | 自定义 Item |
| blocks | 自定义 Block |
| tileentity | 自定义 TileEntity |
| container | GUI 容器 |
| gui | GUI 界面 |
| client | 纯客户端逻辑（tooltip 等） |
| render | TESR / 模型渲染 |
| compat | 其它模组兼容 |
| recipes | 配方注册、砧→装配模板 |
| config | 配置读写 |
| world | 世界生成 |
| core | coremod / ASM |
| handler | 事件与行为 |
| packet | 网络包 |
| proxy | 客户端/服务端代理 |
| util | 通用工具（拼音搜索） |
| hook | 对原版/HBM 的钩子 |

详细说明见 `docs/00-项目结构.md`。

# Make NTM Great Again

HBM（Hbm's Nuclear Tech Mod，1.7.10）的附属模组。

## 功能

- 含盐方块世界生成（含盐沙子 / 红沙 / 沙砾）
- 盐水、热盐水、海水等自定义 HBM 流体
- 砧配方 → 装配机模板系统
- 装配机 / 化工厂等 GUI 的拼音搜索
- 高级粉碎机、碳化钨/碳化钴等扩展内容

## 构建

```powershell
.\gradlew.bat --stop
.\gradlew.bat buildAddonJar reobf
```

产物在 `build/libs/`。

## 文档

- [项目结构](docs/00-项目结构.md)
- [物品与方块](docs/01-物品与方块.md)
- [资源与本地化](docs/02-资源与本地化.md)
- [配方与砧模板](docs/03-配方与砧模板.md)
- [配置与命令](docs/04-配置与命令.md)
- [构建与调试](docs/05-构建与调试.md)
- [代码模板](docs/templates/)

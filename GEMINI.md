# GEMINI.md — HBM's Nuclear Tech Mod (1.7.10) 源码工作区

本仓库是 **HBM's Nuclear Tech Mod** 的完整源码（含外部附属模组 MakeNTMGreatAgain）。
你在这个仓库里工作时请遵守以下约定。

## 工程事实

- Minecraft **1.7.10** + **Forge 10.13.4.1614**，ForgeGradle 1.2（`com.anatawa12.forge:ForgeGradle:1.2-1.0.+`）。
- 源码根目录 `src/main/java`，资源 `src/main/resources`；主包 `com.hbm`，附属模组包 `com.makentmgreatagain`。
- **Java 8**：`gradle.properties` 里 `org.gradle.java.home=D:\JDK8`，`sourceCompatibility/targetCompatibility = 1.8`。
  → 禁止使用 Java 9+ 语法/API（无 var、无 List.of、无 records、无 switch 表达式）。
- 源码编码 **UTF-8**（`compileJava.options.encoding = 'UTF-8'`），注释/本地化大量使用中文。
- 版本：`mod_version=1.0.27`、`mod_build_number=5860`（`gradle.properties`）。

## 构建与验证

- 只做语法/类型检查时运行：
  ```
  .\gradlew.bat compileJava --offline --console=plain
  ```
- **不要**在未确认的情况下运行 `build` / `jar` / `curseforge` 等打包任务：产出会覆盖 `build/libs`，用户多半正在用那个 jar 实测。
- Gradle 用户目录是 `D:\GradleHome`（在工作区之外）。若 wrapper 报
  `FileNotFoundException: D:\GradleHome\wrapper\dists\...\*.lck (拒绝访问)`，说明该目录没有写权限，
  需要放行 `D:\GradleHome`，而不是重装 Gradle。
- 编译出现 deprecation / unchecked 警告属正常，仓库大量老代码如此。

## 代码约定

- 新增机器/配方时沿用既有注册入口（`ModBlocks`/`ModItems`/`ModRecipes`、`IRecipeRegisterListener`），
  保证配方重载后仍存在且不重复注册（按名判重）。
- 不要随手升级依赖或引入第三方模组硬依赖；确实需要时先问用户。
- 修改渲染/模型时优先复用现有渲染器与 `IIcon` 采样方式，注意原版贴图边缘常有 1 像素全透明边。
- 提交风格：跟随现有代码，避免大规模格式化；不要改动无关文件。

## 目录速查

- `src/main/java/com/hbm/`：本体（blocks / items / tileentity / client / inventory / recipes ...）
- `src/main/java/com/makentmgreatagain/`：附属模组（工业种植站、大型火力发电站等）
- `src/main/resources/assets/hbm/`：贴图、模型（OBJ）、语言文件、配方 JSON

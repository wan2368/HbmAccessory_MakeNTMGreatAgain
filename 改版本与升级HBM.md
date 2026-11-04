# 改版本 & 升级 HBM 源码

三个工具，各管一件事：

| 工具 | 干什么 |
|---|---|
| `一键打包附属.bat` | 编译 + 打包 + 重混淆，出 `build\libs\MakeNTMGreatAgain-<版本>.jar` |
| `改版本.bat` | 附属版本号四处同步（不用手改） |
| `升级HBM.bat <新源码目录>` | 用上游新源码覆盖 HBM 部分，不动附属 |

---

## 一、日常开发

```
改代码 → IDEA 里点 Run Client（自动重打 jar）→ 进游戏测
```

几个必须记住的点：

- **编译只走 Gradle**。IDEA 里用 Gradle 面板的 `build`，或者直接双击 bat。不要用 Build → Rebuild Project，它不走 ForgeGradle，会报一堆 `net.minecraft.*` 找不到。
- **改了 lang / mcmod.info 之后必须重新构建**。客户端读的是 `build/resources/main/` 里的副本，只跑 `compileJava` 不会同步。
- **旧存档要删 `config/hbmRecipes/_hbmChemicalPlant.json`**，否则 `hasChemRecipe` 命中旧配方，新配方不注册。
- **矿脉、掉落只影响新生区块**，老区块不会补。
- `GenericRecipe.power` 是**每 tick** 的耗电，不是总量。

### 在 IDEA 里跑游戏测试

运行配置已经建好（`.idea\runConfigurations\`），Run 下拉框里能看到两条：

| 配置 | 实际执行 | 什么时候用 |
|---|---|---|
| **Run Client** | `runClient --offline` | 日常测试。`runClient` 依赖 `jar`，会自动增量重打，不用先手动 build |
| **Build + Run Client** | `build` + `runClient --offline` | 要连附属包（含 reobf）一起重打时 |

为什么不用 IDEA 的 Application 配置：

- 这个 ForgeGradle 版本（1.2）**不提供 `genIntellijRuns`**，只有老的 `idea` / `cleanIdea` / `openIdea`（IPR 格式），不会生成带 classpath 的运行配置。
- 1.7.10 的入口是 `LaunchWrapper + FMLTweaker`，classpath 要挂 60 多个 jar + `build\dirtyArtifacts\forgeSrc-*.jar` + AT，还要 `-Dfml.coreMods.load=...`。普通 Application 配置凑不出来，必然 `ClassNotFoundException`。
- 项目 SDK 现在是 **JDK 21**（`.idea/misc.xml` 的 `project-jdk-name`），而 Gradle 用的是 JDK 8（`.idea/gradle.xml` 的 `gradleJvm`）。真要走 Application 路线，得先把项目 SDK 换成 8，否则 Forge 1.7.10 起不来。

两个坑：

- **必须带 `--offline`**。ForgeGradle 的 classpath 是 `changing = true`，联网会挨个仓库审计（jitpack / cursemaven / ntmr / gregtech），实测卡几分钟没动静。
- **dev 加载的是 `build\libs` 的 jar，不是 `build\classes`**。日志实证：启动 classpath 末尾是 `build\libs\HBM-NTM-[...].jar`，整份日志里 `build\classes` 出现 0 次。所以改动没生效时，先看 Gradle 面板里 `:jar` 是不是被判定成 UP-TO-DATE。

断点调试：`runClient` 里已经写死 `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`。先用 Run Client 起游戏，再 `Run → Attach to Process`（或 Remote JVM Debug 连 `localhost:5005`）。

### 改版本号

双击 `改版本.bat`，或者：

```
改版本.bat 1.0.0-beta.4
```

它会同时改这四处并交叉校验（以前就是这里漏过一个，bat 里还印着 beta.1）：

1. `build.gradle` → `ext.addon_version`
2. `MakeNTMGreatAgain.java` → `static final String VERSION`
3. `src/addon/resources/mcmod.info` → 独立包的简介
4. `src/main/resources/mcmod.info` → **第 2 条**（hbm 那条用的是 `${version}`，不会被误改）

不带参数运行 = 只看当前版本 + 检查四处是否一致。

改完记得重新构建。

### 改本体版本号（跟着上游走）

```
python tools\bump_version.py --hbm 1.0.28 --build 5900
```

改的是 `gradle.properties` 的 `mod_version` / `mod_build_number`，产物名随之变成
`HBM-NTM-[1.0.28_X5900].jar`。升级上游之后先跑这个，再跑 `升级HBM.bat`。

---

## 二、HBM 源码更新了怎么办

### 结论

**能直接替换**，但只能替换「HBM 自己的那部分」，不能整个目录覆盖。
附属代码全部在 `com\makentmgreatagain` / `assets\makentmgreatagain` 下，
HBM 源码里**没有任何一行**引用附属（已 grep 确认），所以换源码不会牵连附属逻辑。

真正会丢的是三类**挂在 HBM 命名空间下的附属资产**：

| 资产 | 位置 | 丢了会怎样 |
|---|---|---|
| 4 个 QMAW 手册页 | `src\main\resources\assets\hbm\manual\makentmgreatagain\*.json` | 游戏里 F1 手册条目整批消失，**不报错** |
| 本体 jar 里的第二条 mcmod.info | `src\main\resources\mcmod.info` | 开发环境模组列表显示「No mod information found」 |
| 附属的物品/流体中文名 | `assets\hbm\lang\zh_CN.lang`（如果有本地改动） | 中文变英文 key |

### 步骤

**0. 先提交**

```
git add -A
git commit -m "升级前存档"
```

脚本会检查，工作区不干净直接拒绝执行。

**1. 跑脚本**

把新源码文件夹拖到 `升级HBM.bat` 上，或：

```
升级HBM.bat D:\HBM-new-1.0.28
```

脚本做的事：新建分支 `upgrade-<时间戳>` → 用 robocopy `/E` 覆盖这六处
（只覆盖、不删除，所以手册页天然保住）→ 跑 `compileJava` → 打印改动清单。

覆盖范围：`src\main\java\com\hbm`、`api`、`cofh`、
`assets\hbm`、`assets\minecraft`、`META-INF\HBM_at.cfg`。

**2. 编译报错就逐个修**

这是升级的**主要工作量**，也是「不失去适配机会」的关键 —— 上游改了方法签名、
挪了包、删了字段，都会在这一步暴露。修的时候用：

```
git diff HEAD -- src/main/java/com/hbm
```

能看到上游这一版到底动了什么。

**3. 检查「上游删了、本地还留着」的文件**

robocopy `/E` 不删文件。上游删掉的类会留在你这里，**照样能编过**，
但行为是旧的，可能悄悄失效。对着新源码树核一遍：

```
git status --short
```

只显示改动过的文件；怀疑某个包时直接跟新源码目录比目录列表。

**4. 进游戏验证**

- 模组列表里附属的版本号对不对
- 海水 / 铅锌 / 硫代硫酸盐三条线还能不能跑通
- F1 手册里那 4 页还在不在
- 铅锌矿、含砷金矿、地下含盐沙砾的刷新是否正常（配置在 `config\MNGA\worldgen.cfg`）

**5. 提交**

```
git add -A
git commit -m "sync upstream 1.0.28"
```

出问题想退回升级前：

```
git checkout main
```

### 绝对不要覆盖的东西

| 路径 | 为什么 |
|---|---|
| `build.gradle` | 附属的 `buildAddonJar` / `reobf.reobf(buildAddonJar)` / `runClient` 参数全在里面 |
| `gradle.properties` | 本体版本号、`gradle.daemon`、超时设置 |
| `src\addon\**` | 独立附属 jar 的 `mcmod.info` |
| `src\main\java\com\makentmgreatagain\**` | 附属全部代码 |
| `src\main\resources\assets\makentmgreatagain\**` | 附属贴图、模型、中英 lang |
| `src\main\resources\mcmod.info` | 手工维护的**双条目**，上游那份只有 hbm 一条 |
| `eclipse\config\**` | 开发存档和配置，覆盖了要重新调 |

### 上游更新频繁时

允许「隔几版再同步」，但要满足两条：

1. **不要跨大版本**（比如 1.0.24 → 1.0.30 一次跳完），中间跳太多，`git diff` 出来的
   改动量会大到没法读，建议每 2～3 个小版本同步一次。
2. **每次同步都单独一个 commit**，标题写清上游版本号。这样任何一次回归都能定位到
   是哪一版带进来的。

---

## 三、出问题怎么查

| 现象 | 原因 | 处理 |
|---|---|---|
| 打包报 `FileNotFoundException: HBM-NTM-xxx.jar` | `build\libs` 里没有本体 jar（clean 过 / 全新克隆） | 新版 `一键打包附属.bat` 会自动补建，老版本手动跑 `gradlew build` |
| 附属 jar 进整合包 `AbstractMethodError` | 忘了 `reobf`，或者 jar 是未混淆的 | 打包必须 `buildAddonJar reobf` 一起跑，bat 已经带上了 |
| 模组列表显示 No mod information | `src\main\resources\mcmod.info` 少了第二条 | 参照 `src\addon\resources\mcmod.info` 补一条 |
| 中文变成 `item.xxx.name` | lang 没重新构建，或 key 拼错 | 先 `gradlew build`；key 规则见下 |
| 改完 lang 游戏里没变 | 客户端读 `build/resources/main` | 必须构建，不能只编译 |

lang key 规则：流体是 `hbmfluid.` + `FluidType` 定义时**第一个参数**的小写，
跟贴图名（第四个参数）无关。新流体定义叫 `SODIUM_THIOSULFATE`，
key 就是 `hbmfluid.sodium_thiosulfate`，写成 `thiosulphate` 就查不到。

package com.makentmgreatagain.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置，文件在 config/MNGA/worldgen.cfg（首次启动自动生成）。
 *
 * <p>默认值只有一个出处：本文件顶部的 {@code DEF_*} 常量，字段初始值与 {@code getInt}
 * 默认参数都引用它，调默认值只改那一行。</p>
 *
 * <p>Forge 的 Configuration 只要文件里已有该键，代码里的默认值就永远不生效。所以这里用
 * {@link #CONFIG_VERSION} 兜住：改过任何一个 {@code DEF_*} 就把它 +1，下次启动发现版本
 * 对不上会丢掉整份配置重建，全部回到源码默认值；版本号没动时手改的值正常保留。
 * 不想被重置就把 cfg 里的 {@code I:configVersion} 改成与源码一致。</p>
 *
 * <p><b>配置保护开关</b>：global.cfg 的 {@code preserveConfigOnUpdate=true} 时，
 * 升版不再无条件重建——先对比「上一版默认值快照（worldgen.defaults.txt）」，
 * 差异只有新增键就保留玩家的手调值（新键由 Forge 自动补全）；发现旧键被移除
 * 或默认值被改动才重建（有备份）。开关默认 false，即老行为。</p>
 *
 * <p><b>版本号是逐行扫文本读出来的</b>（见 {@link #readVersion(File)}），不能用
 * {@link java.util.Properties}——它把 {@code :} 当分隔符，{@code I:configVersion=7} 会被
 * 拆成键 {@code I}，导致版本号永远读不到、每次启动都判定「版本不符」而重置玩家配置。
 * 重建前旧文件会备份成 {@code worldgen.cfg.v<旧版本>.bak}。</p>
 */
public final class ModConfig {

    private static final Logger LOG = LogManager.getLogger("MNGA-Worldgen");

    private static final String CAT = "worldgen";
    private static final String CAT_META = "meta";

    /** 配置文件里记录版本号用的键名（Forge 会写成 {@code I:configVersion}）。 */
    private static final String VERSION_KEY = "configVersion";

    /**
     * 配置版本号。<b>改过任何一个 {@code DEF_*} 之后必须 +1</b>，
     * 否则老存档目录里的 worldgen.cfg 还会拿旧值，代码里改了也看不出效果。
     */
    public static final int CONFIG_VERSION = 10;

    // =====================================================================================
    //  默认值（唯一真源）
    // =====================================================================================

    // ---- 盐：总开关与地表三种生成器 ----
    public static final boolean DEF_SALT_ENABLED           = true;
    public static final int     DEF_PATCH_CHANCE           = 20;
    public static final int     DEF_PATCH_RADIUS_MIN       = 6;
    public static final int     DEF_PATCH_RADIUS_MAX       = 15;
    public static final int     DEF_PATCH_REPLACE          = 25;
    public static final int     DEF_HILLS_SCATTER          = 8;
    public static final int     DEF_CAVE_GRAVEL            = 25;

    // ---- 盐：地下矿脉（含盐沙砾）----
    public static final boolean DEF_SALT_VEIN_ENABLED      = true;
    public static final int     DEF_SALT_VEIN_REGION       = 7;
    public static final int     DEF_SALT_VEIN_CHANCE       = 70;
    public static final int     DEF_SALT_VEIN_THICK_MIN    = 3;
    public static final int     DEF_SALT_VEIN_THICK_MAX    = 12;
    public static final int     DEF_SALT_VEIN_MIN_Y        = 20;
    public static final int     DEF_SALT_VEIN_MAX_Y        = 52;
    public static final int     DEF_SALT_VEIN_FILL         = 94;

    // ---- 铅锌矿（ISP 法提镉的起点）----
    public static final boolean DEF_LEADZINC_ENABLED       = true;
    public static final int     DEF_LEADZINC_CHANCE        = 70;
    public static final int     DEF_LEADZINC_VEINS_MIN     = 1;
    public static final int     DEF_LEADZINC_VEINS_MAX     = 2;
    public static final int     DEF_LEADZINC_RADIUS        = 2;
    public static final int     DEF_LEADZINC_ATTEMPTS_MIN  = 16;
    public static final int     DEF_LEADZINC_ATTEMPTS_MAX  = 26;
    public static final int     DEF_LEADZINC_MIN_Y         = 20;
    public static final int     DEF_LEADZINC_MAX_Y         = 45;

    // ---- 含砷金矿（硫代硫酸盐浸金的原料）----
    public static final boolean DEF_AGOLD_ENABLED          = true;
    public static final int     DEF_AGOLD_CHANCE           = 60;
    public static final int     DEF_AGOLD_VEINS_MIN        = 1;
    public static final int     DEF_AGOLD_VEINS_MAX        = 2;
    public static final int     DEF_AGOLD_RADIUS           = 2;
    public static final int     DEF_AGOLD_ATTEMPTS_MIN     = 10;
    public static final int     DEF_AGOLD_ATTEMPTS_MAX     = 18;
    public static final int     DEF_AGOLD_MIN_Y            = 15;
    public static final int     DEF_AGOLD_MAX_Y            = 40;

    // ---- 白钨矿（湿法炼钨的起点，只注册 oreScheelite）----
    public static final boolean DEF_SCHEELITE_ENABLED      = true;
    public static final int     DEF_SCHEELITE_CHANCE       = 8;
    public static final int     DEF_SCHEELITE_VEINS_MIN    = 1;
    public static final int     DEF_SCHEELITE_VEINS_MAX    = 1;
    public static final int     DEF_SCHEELITE_RADIUS       = 6;
    public static final int     DEF_SCHEELITE_MIN_Y        = 10;
    public static final int     DEF_SCHEELITE_MAX_Y        = 48;

    // ---- Duna（火星）变体：宿主岩为 hbm:duna_rock，只在航天版 Duna 维度生成 ----
    /** HBM 航天版 SpaceConfig.dunaDimension 的出厂默认值；被改过要在本配置里同步。 */
    public static final int     DEF_DUNA_DIM_ID           = 413016;
    public static final boolean DEF_DUNA_LEADZINC_ENABLED = true;
    public static final int     DEF_DUNA_LEADZINC_CHANCE  = 45;
    public static final int     DEF_DUNA_LEADZINC_MIN_Y   = 20;
    public static final int     DEF_DUNA_LEADZINC_MAX_Y   = 45;
    public static final boolean DEF_DUNA_AGOLD_ENABLED    = true;
    public static final int     DEF_DUNA_AGOLD_CHANCE     = 40;
    public static final int     DEF_DUNA_AGOLD_MIN_Y      = 15;
    public static final int     DEF_DUNA_AGOLD_MAX_Y      = 40;
    public static final boolean DEF_DUNA_SCHEELITE_ENABLED = true;
    public static final int     DEF_DUNA_SCHEELITE_CHANCE = 6;
    public static final int     DEF_DUNA_SCHEELITE_MIN_Y  = 14;
    public static final int     DEF_DUNA_SCHEELITE_MAX_Y  = 50;

    // ---- 深海结核混合物（海床表层，成片生成）----
    public static final boolean DEF_NODULE_ENABLED         = true;
    public static final int     DEF_NODULE_CHANCE          = 2;
    public static final int     DEF_NODULE_PATCHES_MIN     = 1;
    public static final int     DEF_NODULE_PATCHES_MAX     = 1;
    public static final int     DEF_NODULE_RADIUS_MIN      = 6;
    public static final int     DEF_NODULE_RADIUS_MAX      = 7;
    public static final int     DEF_NODULE_FILL            = 33;
    public static final int     DEF_NODULE_MIN_DEPTH       = 20;

    // ---- 自然遗迹（NBT 结构，走 HBM 的 NBTStructure 系统）----
    /** 结构文件缺失时的开关默认值：有文件才真正注册，所以默认可以开。 */
    public static final boolean DEF_STRUCTURE_ENABLED       = true;
    /** 生成权重。和同一群系里其它结构抢同一个槽位，不是「生成概率」。 */
    public static final int     DEF_STRUCTURE_WEIGHT        = 6;
    /** 自定义生成间距（区块）。11 → 每 121 区块一处，和 HBM 的全局结构网格无关。 */
    public static final int     DEF_STRUCTURE_GRID          = 11;
    /** 维度 ID。-1 = 下界（地心实验室就生成在这里）。 */
    public static final int     DEF_STRUCTURE_DIM           = -1;
    /** 结构底座被 clamp 到的高度下限。地心实验室固定贴在 0~3 这一层。 */
    public static final int     DEF_STRUCTURE_MIN_Y         = 0;
    public static final int     DEF_STRUCTURE_MAX_Y         = 3;
    /** 高度偏移：-1 让结构最底层与地面齐平（地板沉进地里一格）。clamp 到固定层位时无影响。 */
    public static final int     DEF_STRUCTURE_HEIGHT_OFFSET = 0;
    /** 箱子内容随机化：按稀有度/数量决定每格留不留，并把物品打散到随机格子。 */
    public static final boolean DEF_STRUCTURE_LOOT_RANDOMIZE  = true;

    // ---- 自然遗迹：选址（群系 / 格内落点）----
    /** 允许生成的地形起伏上限（BiomeGenBase.heightVariation）。0.35 是原版「平缓」的口径。 */
    public static final float   DEF_STRUCTURE_MAX_VARIATION = 0.35F;
    /** 是否允许在海洋/深海/河流里生成。默认 false：水面下建筑看不见，箱子也打不开。 */
    public static final boolean DEF_STRUCTURE_ALLOW_OCEAN   = false;
    /** 格内落点两侧留边（区块）。2 → N×N 格子的最外 2 区块里不放，避免相邻两座贴脸。 */
    public static final int     DEF_STRUCTURE_GRID_INSET    = 2;

    // ---- 自然遗迹：方块劣化 ----
    /** 方块劣化（概率替换方块）总开关。 */
    public static final boolean DEF_STRUCTURE_DECAY         = true;
    /** 劣化强度倍率（%）。100 = 完全按源码 DECAY_TABLE 里写死的概率。 */
    public static final int     DEF_STRUCTURE_DECAY_SCALE   = 100;

    // ---- 自然遗迹：战利品 ----
    /** 各稀有度的基础保留率（%）。配置里的真正取值，源码 Rarity 只保留相对关系。 */
    public static final int     DEF_LOOT_KEEP_COMMON        = 90;
    public static final int     DEF_LOOT_KEEP_UNCOMMON      = 78;
    public static final int     DEF_LOOT_KEEP_RARE          = 62;
    public static final int     DEF_LOOT_KEEP_EPIC          = 45;
    public static final int     DEF_LOOT_KEEP_LEGENDARY     = 30;
    /** 留下的物品数量下限（占模板数量的百分比）。40 = 四成。 */
    public static final int     DEF_LOOT_COUNT_MIN_PERCENT  = 40;
    /** 是否把留下的物品打散到随机格子。false = 从第 0 格起顺序摆放。 */
    public static final boolean DEF_LOOT_SCATTER            = true;

    /** 是否打印结构「生成后处理」日志（劣化数 / 随机化箱数）。默认关，排查时再开。 */
    public static final boolean DEF_STRUCTURE_DEBUG_LOG     = false;

    /** 结构 NBT 路径：相对 {@code assets/makentmgreatagain/}，默认就是自带的那个文件。 */
    public static final String  DEF_STRUCTURE_NBT           = "structures/mnga_structure_geocentric_laboratory.nbt";

    // ---- 大型异虫巢（第二个 NBT 结构，主世界，复刻 HBM 小虫巢的生成条件）----
    /** 总开关。默认开；结构文件不存在时自动无效。 */
    public static final boolean DEF_ALIEN_HIVE_ENABLED      = true;
    /** 默认结构文件。 */
    public static final String  DEF_ALIEN_HIVE_NBT          = "structures/mnga_structure_large_alien_insec.nbt";
    /** 维度：0 = 主世界（HBM 的小虫巢也只在主世界生成）。 */
    public static final int     DEF_ALIEN_HIVE_DIM          = 0;
    /**
     * 比 HBM 小虫巢稀有多少倍。小虫巢是「每 hiveSpawn 区块一个」（默认 256），
     * 这里 20 → 平均 5120 区块一个，正好是小虫巢的 1/20。
     */
    public static final int     DEF_ALIEN_HIVE_RARITY_MULT  = 20;
    /** 直接指定「每 N 区块一个」。-1 = 跟随 HBM 配置（hiveSpawn × 倍数）。 */
    public static final int     DEF_ALIEN_HIVE_DENOMINATOR  = -1;
    /** 高度偏移。0 = 结构几何中心落在地表（与小虫巢同口径）；负值往下埋。 */
    public static final int     DEF_ALIEN_HIVE_HEIGHT_OFFSET = 0;
    /** 底座高度下限（clamp 用）。 */
    public static final int     DEF_ALIEN_HIVE_MIN_Y        = 1;
    /** 底座高度上限（clamp 用）。 */
    public static final int     DEF_ALIEN_HIVE_MAX_Y        = 250;
    /** 地形起伏上限。2.0 = 不限（小虫巢本身不看地形平坦度）。 */
    public static final float   DEF_ALIEN_HIVE_MAX_VARIATION = 2.0F;
    /** 是否允许海洋群系。小虫巢不筛群系，海底也会刷，所以默认开。 */
    public static final boolean DEF_ALIEN_HIVE_ALLOW_OCEAN  = true;
    /** 是否跟随 HBM 的地牢/虫巢总开关（enableDungeons、enableHives、地图特性）。 */
    public static final boolean DEF_ALIEN_HIVE_FOLLOW_HBM   = true;
    /** 内部空腔挖空：NBT 里没记录的格子，属于封闭内腔的清成空气，外腔维持地形。 */
    public static final boolean DEF_ALIEN_HIVE_CARVE        = true;
    /** 箱子战利品随机化：按地心实验室同一套稀有度掷骰。 */
    public static final boolean DEF_ALIEN_HIVE_LOOT         = true;
    /** 独立日志开关（[structure-hive] 前缀），默认关。 */
    public static final boolean DEF_ALIEN_HIVE_DEBUG_LOG    = false;

    // ---- 日落六号（第三个 NBT 结构，下界，每世界仅一个，埋在岩浆里且上方不暴露）----
    /** 总开关。默认开；结构文件不存在时自动无效。 */
    public static final boolean DEF_SUNSET_SIX_ENABLED      = true;
    /** 默认结构文件（9×30×10 高塔）。 */
    public static final String  DEF_SUNSET_SIX_NBT          = "structures/mnga_structure_sunset_six.nbt";
    /** 维度：-1 = 下界。 */
    public static final int     DEF_SUNSET_SIX_DIM          = -1;
    /** 底座高度下限（clamp 用）。下界岩浆海主要在 y≈31 以下，留出顶部覆盖余量。 */
    public static final int     DEF_SUNSET_SIX_MIN_Y        = 1;
    /** 底座高度上限（clamp 用）。结构高 30 + 顶部覆盖 8，故底座最高约 218。 */
    public static final int     DEF_SUNSET_SIX_MAX_Y        = 120;
    /** 箱子战利品随机化：按前两个遗迹同一套稀有度掷骰。 */
    public static final boolean DEF_SUNSET_SIX_LOOT         = true;
    /** 独立日志开关（[structure-sunset] 前缀）。默认关：按用户要求去掉生成日志。 */
    public static final boolean DEF_SUNSET_SIX_DEBUG_LOG    = false;
    /** 测试模式：固定刷在 (0,0)、跳过岩浆深度校验。默认关（正式逻辑：种子锚点 ±5000 + 至少 40 格岩浆）。 */
    public static final boolean DEF_SUNSET_SIX_TEST_MODE    = false;
    /** 测试模式底座高度。默认 2（最初指定的高度）。 */
    public static final int     DEF_SUNSET_SIX_TEST_BASE_Y  = 2;

    // =====================================================================================
    //  运行时字段（初始值也取自上面，保证两者永不脱节）
    // =====================================================================================

    // ---- 自然遗迹 ----
    /** 自然遗迹总开关 */
    public static boolean structureWorldgenEnabled = DEF_STRUCTURE_ENABLED;
    /** 生成权重（与同群系其它结构竞争） */
    public static int structureSpawnWeight = DEF_STRUCTURE_WEIGHT;
    /** 自定义生成间距（区块，N² 区块一处） */
    public static int structureGridChunks = DEF_STRUCTURE_GRID;
    /** 维度 ID（0 主世界） */
    public static int structureDimensionId = DEF_STRUCTURE_DIM;
    /** 结构高度下限（clamp 用） */
    public static int structureMinHeight = DEF_STRUCTURE_MIN_Y;
    /** 结构高度上限（clamp 用；与下限相等即固定在那一层） */
    public static int structureMaxHeight = DEF_STRUCTURE_MAX_Y;
    /** 高度偏移（-1 = 地板与地面齐平） */
    public static int structureHeightOffset = DEF_STRUCTURE_HEIGHT_OFFSET;
    /** 箱子内容随机化（关掉则完全按结构 NBT 里摆的原样生成） */
    public static boolean structureLootRandomize = DEF_STRUCTURE_LOOT_RANDOMIZE;

    // ---- 自然遗迹：选址 ----
    /** 地形起伏上限（heightVariation），越小越只挑平地 */
    public static float structureMaxHeightVariation = DEF_STRUCTURE_MAX_VARIATION;
    /** 是否允许在海洋/水域群系生成 */
    public static boolean structureAllowOcean = DEF_STRUCTURE_ALLOW_OCEAN;
    /** 格内落点两侧留边（区块） */
    public static int structureGridInset = DEF_STRUCTURE_GRID_INSET;

    // ---- 自然遗迹：方块劣化 ----
    /** 劣化总开关 */
    public static boolean structureDecayEnabled = DEF_STRUCTURE_DECAY;
    /** 劣化强度倍率（%），100 = 按源码原值 */
    public static int structureDecayScalePercent = DEF_STRUCTURE_DECAY_SCALE;

    // ---- 自然遗迹：战利品 ----
    /** 常见物品保留率（%） */
    public static int lootKeepCommonPercent = DEF_LOOT_KEEP_COMMON;
    /** 较稀有物品保留率（%） */
    public static int lootKeepUncommonPercent = DEF_LOOT_KEEP_UNCOMMON;
    /** 稀有物品保留率（%） */
    public static int lootKeepRarePercent = DEF_LOOT_KEEP_RARE;
    /** 极稀有物品保留率（%） */
    public static int lootKeepEpicPercent = DEF_LOOT_KEEP_EPIC;
    /** 传说物品保留率（%） */
    public static int lootKeepLegendaryPercent = DEF_LOOT_KEEP_LEGENDARY;
    /** 数量下限（占模板数量的百分比） */
    public static int structureLootCountMinPercent = DEF_LOOT_COUNT_MIN_PERCENT;
    /** 是否把战利品打散到随机格子 */
    public static boolean structureLootScatter = DEF_LOOT_SCATTER;

    /** 结构后处理日志开关 */
    public static boolean structureDebugLog = DEF_STRUCTURE_DEBUG_LOG;
    /** 结构 NBT 路径（相对 assets/makentmgreatagain/） */
    public static String structureNbtFile = DEF_STRUCTURE_NBT;

    // ---- 大型异虫巢 ----
    /** 大型异虫巢总开关 */
    public static boolean structureAlienHiveEnabled = DEF_ALIEN_HIVE_ENABLED;
    /** 大型异虫巢结构文件 */
    public static String structureAlienHiveNbtFile = DEF_ALIEN_HIVE_NBT;
    /** 生成维度（0 = 主世界） */
    public static int structureAlienHiveDimensionId = DEF_ALIEN_HIVE_DIM;
    /** 比小虫巢稀有多少倍（默认 20） */
    public static int structureAlienHiveRarityMultiplier = DEF_ALIEN_HIVE_RARITY_MULT;
    /** 直接指定「每 N 区块一个」；-1 = 跟随 HBM（hiveSpawn × 倍数） */
    public static int structureAlienHiveDenominator = DEF_ALIEN_HIVE_DENOMINATOR;
    /** 高度偏移（结构中心相对地表） */
    public static int structureAlienHiveHeightOffset = DEF_ALIEN_HIVE_HEIGHT_OFFSET;
    /** 底座高度下限 */
    public static int structureAlienHiveMinHeight = DEF_ALIEN_HIVE_MIN_Y;
    /** 底座高度上限 */
    public static int structureAlienHiveMaxHeight = DEF_ALIEN_HIVE_MAX_Y;
    /** 地形起伏上限（2.0 = 不限） */
    public static float structureAlienHiveMaxHeightVariation = DEF_ALIEN_HIVE_MAX_VARIATION;
    /** 是否允许海洋群系 */
    public static boolean structureAlienHiveAllowOcean = DEF_ALIEN_HIVE_ALLOW_OCEAN;
    /** 是否跟随 HBM 的地牢/虫巢开关 */
    public static boolean structureAlienHiveFollowHbm = DEF_ALIEN_HIVE_FOLLOW_HBM;
    /** 内部空腔挖空开关 */
    public static boolean structureAlienHiveCarveInterior = DEF_ALIEN_HIVE_CARVE;
    /** 箱子战利品随机化开关 */
    public static boolean structureAlienHiveLootRandomize = DEF_ALIEN_HIVE_LOOT;
    /** 大型异虫巢日志开关 */
    public static boolean structureAlienHiveDebugLog = DEF_ALIEN_HIVE_DEBUG_LOG;

    // ---- 日落六号 ----
    /** 日落六号总开关 */
    public static boolean structureSunsetSixEnabled = DEF_SUNSET_SIX_ENABLED;
    /** 日落六号结构文件 */
    public static String structureSunsetSixNbtFile = DEF_SUNSET_SIX_NBT;
    /** 生成维度（-1 = 下界） */
    public static int structureSunsetSixDimensionId = DEF_SUNSET_SIX_DIM;
    /** 底座高度下限 */
    public static int structureSunsetSixMinHeight = DEF_SUNSET_SIX_MIN_Y;
    /** 底座高度上限 */
    public static int structureSunsetSixMaxHeight = DEF_SUNSET_SIX_MAX_Y;
    /** 箱子战利品随机化开关 */
    public static boolean structureSunsetSixLootRandomize = DEF_SUNSET_SIX_LOOT;
    /** 日落六号日志开关 */
    public static boolean structureSunsetSixDebugLog = DEF_SUNSET_SIX_DEBUG_LOG;
    /** 测试模式：固定刷在 (0,0)、跳过岩浆深度校验 */
    public static boolean structureSunsetSixTestMode = DEF_SUNSET_SIX_TEST_MODE;
    /** 测试模式底座高度 */
    public static int structureSunsetSixTestBaseY = DEF_SUNSET_SIX_TEST_BASE_Y;

    /** 世界生成总开关 */
    public static boolean saltWorldgenEnabled = DEF_SALT_ENABLED;
    /** 含盐小片：每 3x3 区块格子触发一次的概率（百分比） */
    public static int patchChancePercent = DEF_PATCH_CHANCE;
    /** 含盐小片：半径下限（格） */
    public static int patchRadiusMin = DEF_PATCH_RADIUS_MIN;
    /** 含盐小片：半径上限（格） */
    public static int patchRadiusMax = DEF_PATCH_RADIUS_MAX;
    /** 含盐小片：片内每列替换概率（百分比） */
    public static int patchReplacePercent = DEF_PATCH_REPLACE;
    /** 丘陵地表：每列砾石被换成盐砾石的概率（百分比） */
    public static int hillsScatterPercent = DEF_HILLS_SCATTER;
    /** 丘陵/峡谷：裸露砾石被换掉的概率（百分比） */
    public static int caveGravelPercent = DEF_CAVE_GRAVEL;

    /** 盐砾石矿脉开关 */
    public static boolean saltGravelVeinEnabled = DEF_SALT_VEIN_ENABLED;
    /** 矿脉区域边长（区块）。区域被切成 NxN 一格，每格最多生成 1~3 处。越小越密。 */
    public static int saltVeinRegionSize = DEF_SALT_VEIN_REGION;
    /** 每个区域内出现矿脉的概率（%） */
    public static int saltVeinChancePercent = DEF_SALT_VEIN_CHANCE;
    /** 单处矿脉厚度下限（层） */
    public static int saltVeinThicknessMin = DEF_SALT_VEIN_THICK_MIN;
    /** 单处矿脉厚度上限（层） */
    public static int saltVeinThicknessMax = DEF_SALT_VEIN_THICK_MAX;
    /** 矿脉中心高度下限（含） */
    public static int saltVeinMinY = DEF_SALT_VEIN_MIN_Y;
    /** 矿脉中心高度上限（含） */
    public static int saltVeinMaxY = DEF_SALT_VEIN_MAX_Y;
    /** 椭圆范围内的填充率（%） */
    public static int saltVeinFillPercent = DEF_SALT_VEIN_FILL;

    /** 铅锌矿世界生成开关 */
    public static boolean leadzincWorldgenEnabled = DEF_LEADZINC_ENABLED;
    /** 铅锌矿：每区块触发一次的概率（百分比） */
    public static int leadzincChunkChancePercent = DEF_LEADZINC_CHANCE;
    /** 铅锌矿：触发时生成几处矿囊，下限 */
    public static int leadzincVeinsPerChunkMin = DEF_LEADZINC_VEINS_MIN;
    /** 铅锌矿：触发时生成几处矿囊，上限 */
    public static int leadzincVeinsPerChunkMax = DEF_LEADZINC_VEINS_MAX;
    /** 铅锌矿：单处矿囊半径（格，球体） */
    public static int leadzincVeinRadius = DEF_LEADZINC_RADIUS;
    /** 铅锌矿：单处矿囊撒点次数下限 */
    public static int leadzincClusterMin = DEF_LEADZINC_ATTEMPTS_MIN;
    /** 铅锌矿：单处矿囊撒点次数上限 */
    public static int leadzincClusterMax = DEF_LEADZINC_ATTEMPTS_MAX;
    /** 铅锌矿：生成高度下限（含） */
    public static int leadzincMinY = DEF_LEADZINC_MIN_Y;
    /** 铅锌矿：生成高度上限（含） */
    public static int leadzincMaxY = DEF_LEADZINC_MAX_Y;

    /** 含砷金矿世界生成开关 */
    public static boolean refractoryGoldWorldgenEnabled = DEF_AGOLD_ENABLED;
    /** 含砷金矿：每区块触发一次的概率（百分比） */
    public static int refractoryGoldChunkChancePercent = DEF_AGOLD_CHANCE;
    /** 含砷金矿：触发时生成几处矿囊，下限 */
    public static int refractoryGoldVeinsPerChunkMin = DEF_AGOLD_VEINS_MIN;
    /** 含砷金矿：触发时生成几处矿囊，上限 */
    public static int refractoryGoldVeinsPerChunkMax = DEF_AGOLD_VEINS_MAX;
    /** 含砷金矿：单处矿囊半径（格，球体） */
    public static int refractoryGoldVeinRadius = DEF_AGOLD_RADIUS;
    /** 含砷金矿：单处矿囊撒点次数下限 */
    public static int refractoryGoldClusterMin = DEF_AGOLD_ATTEMPTS_MIN;
    /** 含砷金矿：单处矿囊撒点次数上限 */
    public static int refractoryGoldClusterMax = DEF_AGOLD_ATTEMPTS_MAX;
    /** 含砷金矿：生成高度下限（含） */
    public static int refractoryGoldMinY = DEF_AGOLD_MIN_Y;
    /** 含砷金矿：生成高度上限（含） */
    public static int refractoryGoldMaxY = DEF_AGOLD_MAX_Y;

    /** 白钨矿世界生成开关 */
    public static boolean scheeliteWorldgenEnabled = DEF_SCHEELITE_ENABLED;
    /** 白钨矿：每区块触发一次的概率（百分比） */
    public static int scheeliteChunkChancePercent = DEF_SCHEELITE_CHANCE;
    /** 白钨矿：触发时生成几处矿囊，下限 */
    public static int scheeliteVeinsPerChunkMin = DEF_SCHEELITE_VEINS_MIN;
    /** 白钨矿：触发时生成几处矿囊，上限 */
    public static int scheeliteVeinsPerChunkMax = DEF_SCHEELITE_VEINS_MAX;
    /** 白钨矿：单处矿囊半径（格，实心球体） */
    public static int scheeliteVeinRadius = DEF_SCHEELITE_RADIUS;
    /** 白钨矿：生成高度下限（含） */
    public static int scheeliteMinY = DEF_SCHEELITE_MIN_Y;
    /** 白钨矿：生成高度上限（含） */
    public static int scheeliteMaxY = DEF_SCHEELITE_MAX_Y;

    // ---- Duna 变体 ----
    /** Duna 维度 ID（要和航天版 SpaceConfig 的 dunaDimension 对上，改过那边要同步这里）。 */
    public static int dunaDimensionId = DEF_DUNA_DIM_ID;
    /** Duna 铅锌矿开关 */
    public static boolean dunaLeadzincEnabled = DEF_DUNA_LEADZINC_ENABLED;
    /** Duna 铅锌矿：每区块触发概率（%），矿囊大小沿用主世界的 veins/半径/撒点参数 */
    public static int dunaLeadzincChunkChancePercent = DEF_DUNA_LEADZINC_CHANCE;
    /** Duna 铅锌矿：生成高度下限（含） */
    public static int dunaLeadzincMinY = DEF_DUNA_LEADZINC_MIN_Y;
    /** Duna 铅锌矿：生成高度上限（含） */
    public static int dunaLeadzincMaxY = DEF_DUNA_LEADZINC_MAX_Y;
    /** Duna 含砷金矿开关 */
    public static boolean dunaRefractoryGoldEnabled = DEF_DUNA_AGOLD_ENABLED;
    /** Duna 含砷金矿：每区块触发概率（%） */
    public static int dunaRefractoryGoldChunkChancePercent = DEF_DUNA_AGOLD_CHANCE;
    /** Duna 含砷金矿：生成高度下限（含） */
    public static int dunaRefractoryGoldMinY = DEF_DUNA_AGOLD_MIN_Y;
    /** Duna 含砷金矿：生成高度上限（含） */
    public static int dunaRefractoryGoldMaxY = DEF_DUNA_AGOLD_MAX_Y;
    /** Duna 白钨矿开关 */
    public static boolean dunaScheeliteEnabled = DEF_DUNA_SCHEELITE_ENABLED;
    /** Duna 白钨矿：每区块触发概率（%），矿囊大小沿用主世界的半径参数 */
    public static int dunaScheeliteChunkChancePercent = DEF_DUNA_SCHEELITE_CHANCE;
    /** Duna 白钨矿：生成高度下限（含） */
    public static int dunaScheeliteMinY = DEF_DUNA_SCHEELITE_MIN_Y;
    /** Duna 白钨矿：生成高度上限（含） */
    public static int dunaScheeliteMaxY = DEF_DUNA_SCHEELITE_MAX_Y;

    // ---- 深海结核混合物 ----
    /** 深海结核混合物世界生成开关 */
    public static boolean deepSeaNoduleWorldgenEnabled = DEF_NODULE_ENABLED;
    /** 每个海洋区块触发一次的概率（%）。默认 2，约 1/50 的海洋区块。 */
    public static int deepSeaNoduleChancePercent = DEF_NODULE_CHANCE;
    /** 触发时生成几片，下限 */
    public static int deepSeaNodulePatchesMin = DEF_NODULE_PATCHES_MIN;
    /** 触发时生成几片，上限 */
    public static int deepSeaNodulePatchesMax = DEF_NODULE_PATCHES_MAX;
    /** 单处小片半径下限（格） */
    public static int deepSeaNoduleRadiusMin = DEF_NODULE_RADIUS_MIN;
    /** 单处小片半径上限（格） */
    public static int deepSeaNoduleRadiusMax = DEF_NODULE_RADIUS_MAX;
    /** 每片结核的格数占所在圆面积的比例（%），决定矿区多密；整片始终连通 */
    public static int deepSeaNoduleFillPercent = DEF_NODULE_FILL;
    /** 最小水深（格） */
    public static int deepSeaNoduleMinWaterDepth = DEF_NODULE_MIN_DEPTH;

    private static Configuration config;

    /** worldgen.cfg 的路径，init 与 loadWorldgen 之间共享。 */
    private static File worldgenFile;
    /** worldgen.cfg 所在目录（备份/快照也放这里）。 */
    private static File worldgenDir;
    /** 文件里读到的旧版本号；文件不存在时为 -1。 */
    private static int oldFileVersion;
    /** 本次启动是否发现「文件版本 ≠ 源码版本」，由 init 在 DEFAULTS 收集完之后判决。 */
    private static boolean versionMismatch;
    /** 本次启动是否真的走了重建（只用于日志措辞）。 */
    private static boolean rebuilt;

    /**
     * 本轮所有 cfgXxx 调用登记的「键 → 源码默认值」（字符串形式）。
     *
     * <p>两个用途：</p>
     * <ol>
     *   <li>启动结束时写进快照文件 {@code worldgen.defaults.txt}；</li>
     *   <li>下次升版时和上一份快照对比，判断差异是否「只有新增」。</li>
     * </ol>
     */
    private static final java.util.LinkedHashMap<String, String> DEFAULTS =
            new java.util.LinkedHashMap<String, String>();

    /** 默认值快照文件名（与 worldgen.cfg 同目录）。 */
    private static final String SNAPSHOT_NAME = "worldgen.defaults.txt";

    private ModConfig() {
    }

    /** 在 preInit 调用，event.getModConfigurationDirectory() 传进来。 */
    public static void init(File configDir) {

        File dir = new File(configDir, "MNGA");
        if(!dir.exists() && !dir.mkdirs()) {
            dir = configDir; // 极端情况下退化为 config 根目录
        }

        worldgenDir = dir;
        worldgenFile = new File(dir, "worldgen.cfg");

        // 第一遍：按现有文件读。版本号在这里只记账不判决——
        // 要等所有键收集完（DEFAULTS 填满），才能对比快照判断「是否只有新增」。
        loadWorldgen();

        if(versionMismatch && worldgenFile.isFile()) {

            // global.cfg 的 preserveConfigOnUpdate=true 且新默认相对旧默认只有新增 →
            // 保住玩家的手调值。Forge 的 Configuration 会在 save() 时自动把缺失的
            // 新键按默认值补进文件，所以「保留」不需要我们做任何事，不删文件就行。
            if(GlobalConfig.preserveConfigOnUpdate && isDefaultsSnapshotAdditive()) {
                LOG.info("worldgen.cfg v" + oldFileVersion + " -> v" + CONFIG_VERSION
                        + "：新配置相对旧配置只有新增项，按 preserveConfigOnUpdate=true"
                        + " 保留玩家手调值（新增键自动补全）");
                versionMismatch = false;
                // 版本号必须显式写回：Forge 的 Configuration 只要键已存在就返回文件旧值，
                // 上面的 cfgInt(VERSION_KEY, ...) 写不进去，不手动 set 的话
                // 文件里的 I:configVersion 永远停在旧版，每次启动都会重跑一遍增量判定
                if(config.hasCategory(CAT) && config.getCategory(CAT).containsKey(VERSION_KEY)) {
                    config.getCategory(CAT).get(VERSION_KEY).set(String.valueOf(CONFIG_VERSION));
                    config.save();
                }
            } else {
                // 重建：Forge 的 Configuration 一旦发现键已存在，代码里的默认值就再也不起
                // 作用，所以只能从文件层面清掉，没有别的办法。旧文件先备份成 .bak。
                if(oldFileVersion > 0) backup(worldgenFile, worldgenDir, oldFileVersion);
                boolean deleted = worldgenFile.delete();
                LOG.info("worldgen.cfg version " + oldFileVersion + " -> " + CONFIG_VERSION
                        + ", rebuilding with source defaults (deleted=" + deleted + ")");
                loadWorldgen();   // 文件已删，这一遍全部取默认值
                rebuilt = true;
            }
        }

        // 快照永远按「本次的默认值」刷新：下次升版时，它就是对比里的「旧版默认」一侧。
        writeDefaultsSnapshot();

        LOG.info("MNGA worldgen config v" + CONFIG_VERSION + (rebuilt ? " (rebuilt)" : "")
                + " -> " + worldgenFile.getAbsolutePath());
    }

    /** worldgen.cfg 的完整加载过程。重建时会被调第二遍（文件已删，全部取默认值）。 */
    private static void loadWorldgen() {

        oldFileVersion = readVersion(worldgenFile);
        versionMismatch = worldgenFile.exists() && oldFileVersion != CONFIG_VERSION;

        config = new Configuration(worldgenFile);
        config.load();

        // ---------------- 盐 ----------------
        saltWorldgenEnabled = cfgBool("enableSaltWorldgen", DEF_SALT_ENABLED,
                "总开关：关闭后三种含盐方块（盐斑/地表散点/地下矿脉）都不会自然生成。");
        patchChancePercent = cfgInt("patchChancePercent", DEF_PATCH_CHANCE, 0, 100,
                "每 3x3 区块格子触发含盐小片的概率（%）。0 = 不生成小片。");
        patchRadiusMin = cfgInt("patchRadiusMin", DEF_PATCH_RADIUS_MIN, 2, 32,
                "含盐小片半径下限（格）。");
        patchRadiusMax = cfgInt("patchRadiusMax", DEF_PATCH_RADIUS_MAX, 2, 32,
                "含盐小片半径上限（格）。");
        patchReplacePercent = cfgInt("patchReplacePercent", DEF_PATCH_REPLACE, 0, 100,
                "小片内每个地表列被替换成含盐方块的概率（%）。");
        hillsScatterPercent = cfgInt("hillsScatterPercent", DEF_HILLS_SCATTER, 0, 100,
                "丘陵散点区内的替换强度（%）。注意它现在只作用于「区域内」：\n"
                + "每 4x4 区块选一块半径 10~20 的圆，圆外的山体一块盐都不放。\n"
                + "圆内砾石按本值的 4 倍概率替换，石/土按本值（比例与旧版一致）。\n"
                + "草方块永不替换。0 = 关闭丘陵地表散点。");
        caveGravelPercent = cfgInt("caveGravelPercent", DEF_CAVE_GRAVEL, 0, 100,
                "丘陵/峡谷里裸露砾石变成盐砾石的概率（%）。\n"
                + "同样只作用于「区域内」：每 4x4 区块选一块半径 6~14 的圆，圆外不撒。\n"
                + "该列若检出 HBM 原油（地下 ore_oil / 基岩油 / 油沙）会整列跳过。");

        saltGravelVeinEnabled = cfgBool("enableSaltGravelVein", DEF_SALT_VEIN_ENABLED,
                "是否生成地下盐砾石矿脉。这是「地下那层含盐沙砾」的唯一来源，关掉后只能靠地表散点。");
        saltVeinRegionSize = cfgInt("saltVeinRegionSize", DEF_SALT_VEIN_REGION, 3, 32,
                "矿脉区域边长（区块）。世界被切成 NxN 区块一个格子，每格独立掷一次「有没有矿脉」。\n"
                + "【调密度首选这个】数字越小越密，密度与 N 的平方成反比：\n"
                + "  11 → 121 区块一格（旧版，很稀：平均每 192 区块才 1 处）\n"
                + "   7 → 49 区块一格（默认，平均每 42 区块 1 处）\n"
                + "   5 → 25 区块一格（很密，适合当主矿）");
        saltVeinChancePercent = cfgInt("saltVeinChancePercent", DEF_SALT_VEIN_CHANCE, 0, 100,
                "每个格子里出现矿脉的概率（%）。\n"
                + "出现时再掷 1~3 处（15% 三处、35% 两处、50% 一处）。\n"
                + "换算：默认 7x7 + 70% ≈ 每 42 个区块有 1 处；改成 100 约每 30 区块 1 处。");
        saltVeinThicknessMin = cfgInt("saltVeinThicknessMin", DEF_SALT_VEIN_THICK_MIN, 1, 32,
                "单处矿脉最薄几层。椭圆中心最厚、边缘收成 1 层。");
        saltVeinThicknessMax = cfgInt("saltVeinThicknessMax", DEF_SALT_VEIN_THICK_MAX, 1, 32,
                "单处矿脉最厚几层。小于下限时自动等于下限。");
        saltVeinMinY = cfgInt("saltVeinMinY", DEF_SALT_VEIN_MIN_Y, 1, 250,
                "矿脉中心高度下限（含）。");
        saltVeinMaxY = cfgInt("saltVeinMaxY", DEF_SALT_VEIN_MAX_Y, 1, 250,
                "矿脉中心高度上限（含）。矿体始终被夹在地表以下 3 格，不会露出地面。");
        saltVeinFillPercent = cfgInt("saltVeinFillPercent", DEF_SALT_VEIN_FILL, 10, 100,
                "椭圆范围内的填充率（%）。100 = 实心，80 以下会碎成散点。");

        // ---------------- 铅锌矿 ----------------
        leadzincWorldgenEnabled = cfgBool("enableLeadZincWorldgen", DEF_LEADZINC_ENABLED,
                "是否自然生成铅锌矿（ISP 法提镉的矿石来源）。");
        leadzincChunkChancePercent = cfgInt("leadzincChunkChancePercent", DEF_LEADZINC_CHANCE, 0, 100,
                "每区块生成铅锌矿囊的概率（%）。默认 70。\n"
                + "【调密度优先改这里】只改出现频率，不改矿囊大小，最不容易调坏。\n"
                + "  100 = 每区块都有，35 = 砍半，0 = 不生成。");
        leadzincVeinsPerChunkMin = cfgInt("leadzincVeinsPerChunkMin", DEF_LEADZINC_VEINS_MIN, 1, 8,
                "触发时一个区块里生成几处矿囊，下限。默认 1。\n"
                + "【第二好用的旋钮】调成 2~3 相当于把密度成倍放大，但矿囊还是原来那么小。");
        leadzincVeinsPerChunkMax = cfgInt("leadzincVeinsPerChunkMax", DEF_LEADZINC_VEINS_MAX, 1, 8,
                "触发时一个区块里生成几处矿囊，上限。默认 2（即 1~2 处，平均 1.5）。\n"
                + "小于下限时自动等于下限。");
        leadzincVeinRadius = cfgInt("leadzincVeinRadius", DEF_LEADZINC_RADIUS, 1, 6,
                "单处矿囊的半径（格）。矿囊是一个球，半径 2 = 跨 5x5x5 的一坨（57 个可用格）。\n"
                + "半径 1 = 跨 3x3x3 的小疙瘩，3 = 跨 7x7x7 的大矿囊。\n"
                + "注意半径是按体积三次方涨的，3 以上很容易挖出一整片，慎调。");
        leadzincClusterMin = cfgInt("leadzincClusterMin", DEF_LEADZINC_ATTEMPTS_MIN, 1, 128,
                "单处矿囊撒点次数下限。球里每个点随机落一次方块（重复落同一格只算一次）。\n"
                + "半径 2 的球共 57 格，撒 16~26 次大约落下 14~19 格。\n"
                + "超过球内格数纯属浪费，想更大请先加 leadzincVeinRadius。");
        leadzincClusterMax = cfgInt("leadzincClusterMax", DEF_LEADZINC_ATTEMPTS_MAX, 1, 128,
                "单处矿囊撒点次数上限。默认 26。小于下限时自动等于下限。");
        leadzincMinY = cfgInt("leadzincMinY", DEF_LEADZINC_MIN_Y, 1, 250,
                "铅锌矿生成高度下限（含）。默认 20，正好在地表以下的铁矿层附近。");
        leadzincMaxY = cfgInt("leadzincMaxY", DEF_LEADZINC_MAX_Y, 1, 250,
                "铅锌矿生成高度上限（含）。默认 45。矿囊整体被夹在 [MinY, MaxY] 内，不会溢出。");

        // ---------------- 含砷金矿 ----------------
        refractoryGoldWorldgenEnabled = cfgBool("enableRefractoryGoldWorldgen", DEF_AGOLD_ENABLED,
                "是否自然生成含砷金矿（硫代硫酸盐浸金的矿石来源）。");
        refractoryGoldChunkChancePercent = cfgInt("refractoryGoldChunkChancePercent", DEF_AGOLD_CHANCE, 0, 100,
                "每区块生成含砷金矿囊的概率（%）。默认 60。\n"
                + "【调密度优先改这里】只改出现频率，不改矿囊大小。");
        refractoryGoldVeinsPerChunkMin = cfgInt("refractoryGoldVeinsPerChunkMin", DEF_AGOLD_VEINS_MIN, 1, 8,
                "触发时一个区块里生成几处矿囊，下限。默认 1。");
        refractoryGoldVeinsPerChunkMax = cfgInt("refractoryGoldVeinsPerChunkMax", DEF_AGOLD_VEINS_MAX, 1, 8,
                "触发时一个区块里生成几处矿囊，上限。默认 2。小于下限时自动等于下限。");
        refractoryGoldVeinRadius = cfgInt("refractoryGoldVeinRadius", DEF_AGOLD_RADIUS, 1, 6,
                "单处矿囊的半径（格）。默认 2（跨 5x5x5）。");
        refractoryGoldClusterMin = cfgInt("refractoryGoldClusterMin", DEF_AGOLD_ATTEMPTS_MIN, 1, 128,
                "单处矿囊撒点次数下限。默认 10（半径 2 的球里大约落下 9 格）。");
        refractoryGoldClusterMax = cfgInt("refractoryGoldClusterMax", DEF_AGOLD_ATTEMPTS_MAX, 1, 128,
                "单处矿囊撒点次数上限。默认 18。小于下限时自动等于下限。");
        refractoryGoldMinY = cfgInt("refractoryGoldMinY", DEF_AGOLD_MIN_Y, 1, 250,
                "含砷金矿生成高度下限（含）。默认 15，比铅锌矿浅，方便前期就撞见。");
        refractoryGoldMaxY = cfgInt("refractoryGoldMaxY", DEF_AGOLD_MAX_Y, 1, 250,
                "含砷金矿生成高度上限（含）。默认 40。");

        // ---------------- 白钨矿 ----------------
        scheeliteWorldgenEnabled = cfgBool("enableScheeliteWorldgen", DEF_SCHEELITE_ENABLED,
                "是否自然生成白钨矿（湿法炼钨的矿石来源）。\n"
                + "注意：关掉之后白钨矿只能靠创造模式获取，整条湿法链也就没法正常起头。");
        scheeliteChunkChancePercent = cfgInt("scheeliteChunkChancePercent", DEF_SCHEELITE_CHANCE, 0, 100,
                "每区块生成白钨矿囊的概率（%）。默认 8。\n"
                + "【调密度优先改这里】平均间隔 ≈ 100 ÷ 本值 个区块：\n"
                + "    8 → 平均 12~13 区块 1 条（默认，实际间隔多落在 8~16 区块之间）\n"
                + "    6 → 平均 17 区块 1 条（更稀）\n"
                + "   12 → 平均 8 区块 1 条（更密）\n"
                + "    0 → 不生成。\n"
                + "它是几何分布，所以偶尔会连着两三个区块都出、也会十几个区块一个都没有，这是正常的。");
        scheeliteVeinsPerChunkMin = cfgInt("scheeliteVeinsPerChunkMin", DEF_SCHEELITE_VEINS_MIN, 1, 4,
                "触发时一个区块里生成几处矿囊，下限。默认 1。\n"
                + "白钨矿单条本来就很大，调成 2 以上会让一个区块里叠出上千格矿石，几乎肯定要削概率。");
        scheeliteVeinsPerChunkMax = cfgInt("scheeliteVeinsPerChunkMax", DEF_SCHEELITE_VEINS_MAX, 1, 4,
                "触发时一个区块里生成几处矿囊，上限。默认 1。小于下限时自动等于下限。");
        scheeliteVeinRadius = cfgInt("scheeliteVeinRadius", DEF_SCHEELITE_RADIUS, 1, 10,
                "单处矿囊的半径（格）。默认 6，跨 13×13×13 的实心矿体。\n"
                + "【注意】白钨矿是「填满」而不是撒点：球内每一个石头格子都会变成矿石，\n"
                + "所以格数就是球内格数，改半径等于直接改产量，按半径三次方涨：\n"
                + "   4 → 257 格\n"
                + "   6 → 925 格（默认，大约 1480 锭钨，一整片连着挖）\n"
                + "   8 → 2109 格（已经是巨型矿洞，慎用）\n"
                + "改大之前先确认 scheeliteMaxY - scheeliteMinY 至少是半径的两倍，\n"
                + "否则球体会被高度区间裁掉，实际产量对不上预期。");
        scheeliteMinY = cfgInt("scheeliteMinY", DEF_SCHEELITE_MIN_Y, 1, 250,
                "白钨矿生成高度下限（含）。默认 10，比铅锌矿深，和矽卡岩型矿床的产出层位一致。");
        scheeliteMaxY = cfgInt("scheeliteMaxY", DEF_SCHEELITE_MAX_Y, 1, 250,
                "白钨矿生成高度上限（含）。默认 48，还在地表以下，不会露出地面。\n"
                + "矿囊中心会被自动收进 [下限+半径, 上限-半径]，保证球体完整不被裁掉。");

        // ---------------- Duna 变体 ----------------
        dunaDimensionId = cfgInt("dunaDimensionId", DEF_DUNA_DIM_ID, -2147483648, 2147483647,
                "Duna 维度 ID。默认 413016（航天版 SpaceConfig 的出厂值）。\n"
                + "如果整合包/存档里改过航天配置的 17.02_dunaDimension，必须同步改这里，\n"
                + "否则 Duna 矿石不会生成（判定靠维度 ID 对上）。");
        dunaLeadzincEnabled = cfgBool("enableDunaLeadZinc", DEF_DUNA_LEADZINC_ENABLED,
                "是否在 Duna 生成铅锌矿（变体方块，掉粉与主世界一致）。\n"
                + "没装航天版或找不到 hbm:duna_rock 时该项自动无效。");
        dunaLeadzincChunkChancePercent = cfgInt("dunaLeadzincChunkChancePercent", DEF_DUNA_LEADZINC_CHANCE, 0, 100,
                "Duna 每区块生成铅锌矿囊的概率（%）。默认 45，比主世界 70 稀。\n"
                + "矿囊大小（几处/半径/撒点次数）沿用主世界的 leadzinc* 参数。");
        dunaLeadzincMinY = cfgInt("dunaLeadzincMinY", DEF_DUNA_LEADZINC_MIN_Y, 1, 250,
                "Duna 铅锌矿生成高度下限（含）。默认 20。");
        dunaLeadzincMaxY = cfgInt("dunaLeadzincMaxY", DEF_DUNA_LEADZINC_MAX_Y, 1, 250,
                "Duna 铅锌矿生成高度上限（含）。默认 45。");
        dunaRefractoryGoldEnabled = cfgBool("enableDunaRefractoryGold", DEF_DUNA_AGOLD_ENABLED,
                "是否在 Duna 生成含砷金矿（变体方块）。");
        dunaRefractoryGoldChunkChancePercent = cfgInt("dunaRefractoryGoldChunkChancePercent", DEF_DUNA_AGOLD_CHANCE, 0, 100,
                "Duna 每区块生成含砷金矿囊的概率（%）。默认 40，比主世界 60 稀。");
        dunaRefractoryGoldMinY = cfgInt("dunaRefractoryGoldMinY", DEF_DUNA_AGOLD_MIN_Y, 1, 250,
                "Duna 含砷金矿生成高度下限（含）。默认 15。");
        dunaRefractoryGoldMaxY = cfgInt("dunaRefractoryGoldMaxY", DEF_DUNA_AGOLD_MAX_Y, 1, 250,
                "Duna 含砷金矿生成高度上限（含）。默认 40。");
        dunaScheeliteEnabled = cfgBool("enableDunaScheelite", DEF_DUNA_SCHEELITE_ENABLED,
                "是否在 Duna 生成白钨矿（变体方块，实心大矿囊）。");
        dunaScheeliteChunkChancePercent = cfgInt("dunaScheeliteChunkChancePercent", DEF_DUNA_SCHEELITE_CHANCE, 0, 100,
                "Duna 每区块生成白钨矿囊的概率（%）。默认 6（约 17 区块一条），比主世界 8 略稀。\n"
                + "矿囊半径沿用主世界的 scheeliteVeinRadius。");
        dunaScheeliteMinY = cfgInt("dunaScheeliteMinY", DEF_DUNA_SCHEELITE_MIN_Y, 1, 250,
                "Duna 白钨矿生成高度下限（含）。默认 14，避开 y12 以下的玄武岩层。");
        dunaScheeliteMaxY = cfgInt("dunaScheeliteMaxY", DEF_DUNA_SCHEELITE_MAX_Y, 1, 250,
                "Duna 白钨矿生成高度上限（含）。默认 50。\n"
                + "中心高度会自动收进 [下限+半径, 上限-半径]，保证球体完整。");

        // ---------------- 深海结核混合物 ----------------
        deepSeaNoduleWorldgenEnabled = cfgBool("enableDeepSeaNoduleWorldgen", DEF_NODULE_ENABLED,
                "是否自然生成深海结核混合物（海床表层矿，后续由海底采集机处理）。");
        deepSeaNoduleChancePercent = cfgInt("deepSeaNoduleChancePercent", DEF_NODULE_CHANCE, 0, 100,
                "每个海洋区块触发一次的概率（%）。默认 2，约 1/50 的海洋区块；\n"
                + "【调密度首选这里】只改出现频率，不改矿区大小。\n"
                + "  1 = 约 1/100 区块，非常稀\n"
                + "  2 = 默认（约 1/50）\n"
                + "  5 = 约 1/20 区块，比较常见\n"
                + "  0 = 不生成。");
        deepSeaNodulePatchesMin = cfgInt("deepSeaNodulePatchesMin", DEF_NODULE_PATCHES_MIN, 1, 4,
                "触发时一个区块里生成几片，下限。默认 1。");
        deepSeaNodulePatchesMax = cfgInt("deepSeaNodulePatchesMax", DEF_NODULE_PATCHES_MAX, 1, 4,
                "触发时一个区块里生成几片，上限。默认 1。小于下限时自动等于下限。");
        deepSeaNoduleRadiusMin = cfgInt("deepSeaNoduleRadiusMin", DEF_NODULE_RADIUS_MIN, 1, 7,
                "单处小片半径下限（格）。默认 6，最大跨度 13×13。\n"
                + "半径只是矿区能长到的最远范围；每片结核从圆心附近连通生长，实际是一整块圆润的矿区。\n"
                + "半径越大，单个矿区越大；上限 7（跨 15×15），保证整个圆能落在一个 16×16 区块内。\n"
                + "半径 6~7 + 33% 密度 ≈ 每片 30~48 格结核。");
        deepSeaNoduleRadiusMax = cfgInt("deepSeaNoduleRadiusMax", DEF_NODULE_RADIUS_MAX, 1, 7,
                "单处小片半径上限（格）。默认 7，最大跨度 15×15。小于下限时自动等于下限。");
        deepSeaNoduleFillPercent = cfgInt("deepSeaNoduleFillPercent", DEF_NODULE_FILL, 0, 100,
                "每片结核的格数占所在圆面积的比例（%）。默认 33。\n"
                + "整片始终连通（从中心向四邻生长），填得越高矿区越密、越圆；填得低则更瘦更不规则。\n"
                + "半径 6~7 + 33% ≈ 每片 30~48 格结核；\n"
                + "100 = 整片圆内全是结核；25 = 更稀疏但依然连通的矿区。");
        deepSeaNoduleMinWaterDepth = cfgInt("deepSeaNoduleMinWaterDepth", DEF_NODULE_MIN_DEPTH, 1, 60,
                "最小水深（格）。默认 20，保证只在深海生成；浅海/河流会被跳过。");

        // ---------------- 自然遗迹 ----------------
        structureWorldgenEnabled = cfgBool("enableStructureWorldgen", DEF_STRUCTURE_ENABLED,
                "是否生成自然遗迹（NBT 结构）。结构文件不存在时该项自动无效，不会报错。");
        structureSpawnWeight = cfgInt("structureSpawnWeight", DEF_STRUCTURE_WEIGHT, 1, 100,
                "结构生成权重。注意它不控制「多久见一个」，而是和同一个群系里的其它结构\n"
                + "抢同一个生成槽位（HBM 每 4~12 区块才有一个槽位）。\n"
                + "参照：HBM 的 ruinA~J 都是 10，desert_shack 5~7，lighthouse/oil_rig 用独立开关。\n"
                + "想要比遗迹更稀就设 2~4，想常见设 10 以上。\n"
                + "⚠️ 本模组的遗迹走自定义坐标通道（见下一项），这个权重实际不参与抽签。");
        structureGridChunks = cfgInt("structureGridChunks", DEF_STRUCTURE_GRID, 4, 64,
                "自定义生成间距（区块）。世界被切成 N×N 区块的格子，每格只生成一处，\n"
                + "所以平均密度是每 N² 区块一个：11 → 121 区块一个（默认），\n"
                + "12 → 144，10 → 100，16 → 256（很稀）。\n"
                + "这条通道独立于 HBM 的全局结构网格，改它不会影响主世界的废墟密度。");
        structureDimensionId = cfgInt("structureDimensionId", DEF_STRUCTURE_DIM, -2147483648, 2147483647,
                "在哪个维度生成。0 = 主世界；-1 = 下界；1 = 末地；其它数字是模组维度。\n"
                + "地心实验室默认 -1（下界）。");
        structureMinHeight = cfgInt("structureMinHeight", DEF_STRUCTURE_MIN_Y, 0, 250,
                "结构底座高度的下限。地表遗迹填 1；下界填 0 与填 1 结果相同——\n"
                + "NBTStructure.build 里有 `if(ry < 1) continue`，y<1 的方块直接跳过，基岩不会被替换。\n"
                + "把本项与「高度上限」设成同一个数字（如 30/30）就是固定层位。");
        structureMaxHeight = cfgInt("structureMaxHeight", DEF_STRUCTURE_MAX_Y, 0, 250,
                "结构底座高度的上限。与下限相等时固定在那一层，否则按地形平均高度 clamp。");
        structureHeightOffset = cfgInt("structureHeightOffset", DEF_STRUCTURE_HEIGHT_OFFSET, -32, 32,
                "高度偏移。-1 = 结构最底层沉进地里一格，地板与地面齐平（HBM 多数地表遗迹用 -1）。\n"
                + "算法是「平均地表高度 + 本项」再 clamp 到上下限，所以固定层位时它仍会把结果往上/下推，\n"
                + "只是推出去的会被上下限挡住。");
        structureLootRandomize = cfgBool("structureLootRandomize", DEF_STRUCTURE_LOOT_RANDOMIZE,
                "箱子内容是否随机化。\n"
                + "开（默认）：结构 NBT 里摆好的物品只当模板——每格按物品稀有度和原有数量掷骰，\n"
                + "决定留不留，留下的数量在「数量下限 ~ 原数量」之间重掷，最后打散到随机格子。\n"
                + "关：每个遗迹的箱子内容与导出时完全一致（同一坐标永远同一件东西）。\n"
                + "分档保留率见下面的 structureLootKeep* 五项。");

        // ---- 自然遗迹：选址 ----
        structureMaxHeightVariation = cfgFloat("structureMaxHeightVariation", DEF_STRUCTURE_MAX_VARIATION, 0.0F, 2.0F,
                "允许生成的地形起伏上限（原版 BiomeGenBase.heightVariation）。默认 0.35。\n"
                + "这个值不看海拔，只看「这一带有多不平」，越大越容易落进山地。\n"
                + "参照：平原 0.05、森林 0.2、丘陵 0.3、山地 0.5~0.7、峭壁 1.0+。\n"
                + "0.35 → 平原/森林/丘陵能落，山地及更陡的一律跳过（默认）；\n"
                + "1.0  → 几乎任何陆地群系都行，但结构会卡在陡坡上，一半埋一半悬空；\n"
                + "0.1  → 只在真正平的地方出现。");
        structureAllowOcean = cfgBool("structureAllowOcean", DEF_STRUCTURE_ALLOW_OCEAN,
                "是否允许在海洋/深海群系里生成。默认关。\n"
                + "关：WATER、OCEAN 两类群系一律跳过（河滩、沙滩不在其中，仍会生成）。\n"
                + "开：海底也会刷，但结构底座会被平均地表高度拉到水面以下，\n"
                + "绝大多数情况下整个建筑泡在水里，只能靠水下呼吸去探索。");
        structureGridInset = cfgInt("structureGridInset", DEF_STRUCTURE_GRID_INSET, 0, 16,
                "格内落点的留边（区块）。默认 2。\n"
                + "世界被切成 N×N 区块的格子，落点只能在「去掉四边各留边」之后的中间区域里挑。\n"
                + "结构本体约 3×3 区块，留边 2 能保证相邻两格的落点至少隔 5 区块（80 格），不会互相切进去。\n"
                + "0 = 允许贴格子边缘（结构可能跨过格子线，和隔壁那格的落点挤在一起）；\n"
                + "4 = 更居中但选择更少（间距 11 时中间只剩 3 格可挑）。\n"
                + "留边超过 (间距-1)/2 会被自动压回去，不会把可选位置压成 0。");

        // ---- 自然遗迹：方块劣化 ----
        structureDecayEnabled = cfgBool("structureDecayEnabled", DEF_STRUCTURE_DECAY,
                "方块劣化（概率替换方块）总开关。默认开。\n"
                + "开：按 world/AddonStructures.java 的 DECAY_TABLE 掷骰，\n"
                + "把强化萤石敲成空气、混凝土砖裂成破碎砖、黑曜石砖掉成碎黑曜石之类，\n"
                + "让同一座遗迹每次生成都破得不一样。\n"
                + "关：结构完全按 NBT 原样落地，一处破口都没有。");
        structureDecayScalePercent = cfgInt("structureDecayScalePercent", DEF_STRUCTURE_DECAY_SCALE, 0, 400,
                "劣化强度倍率（%）。默认 100 = 完全按 DECAY_TABLE 里写死的概率。\n"
                + "乘在每条规则的概率上，所以是整体缩放，不用逐条改源码：\n"
                + "    0   → 等同于关掉劣化（保留开关，方便临时对比）\n"
                + "    50  → 破败程度减半，多数墙壁完好\n"
                + "    100 → 默认，约三成混凝土砖开裂/破碎、三成萤石熄灭\n"
                + "    200 → 加倍，基本只剩框架，地板大面积塌成空气\n"
                + "    400 → 上限，几乎每条规则必定命中（超过 100% 一律按 100% 算）\n"
                + "注意倍率作用于「顺序判定」的每一条，所以空气类结果也会一起放大，\n"
                + "调高时留意别把承重墙也砸没了。");

        // ---- 自然遗迹：战利品 ----
        lootKeepCommonPercent = cfgInt("structureLootKeepCommon", DEF_LOOT_KEEP_COMMON, 0, 100,
                "常见物品的保留率（%）。默认 90。\n"
                + "这一类是工业耗材、建材、普通食物、核废料（完整清单见 AddonStructures 的 rate(COMMON, ...)）。\n"
                + "同一个箱子里每格独立掷骰，所以 90% 意味着「原来 10 格大概留 9 格」。");
        lootKeepUncommonPercent = cfgInt("structureLootKeepUncommon", DEF_LOOT_KEEP_UNCOMMON, 0, 100,
                "较稀有物品的保留率（%）。默认 78。\n"
                + "这一类是加工品、防护服、医疗、弹药。");
        lootKeepRarePercent = cfgInt("structureLootKeepRare", DEF_LOOT_KEEP_RARE, 0, 100,
                "稀有物品的保留率（%）。默认 62。\n"
                + "这一类是核材料、机器方块、军械、机器升级。");
        lootKeepEpicPercent = cfgInt("structureLootKeepEpic", DEF_LOOT_KEEP_EPIC, 0, 100,
                "极稀有物品的保留率（%）。默认 45。\n"
                + "这一类是动力装甲、蒸汽服。想要「十次九空」就压到 15~25。");
        lootKeepLegendaryPercent = cfgInt("structureLootKeepLegendary", DEF_LOOT_KEEP_LEGENDARY, 0, 100,
                "传说物品的保留率（%）。默认 30。\n"
                + "目前只有附魔金苹果（Items.golden_apple, meta 1）走这一档。\n"
                + "它不享受「堆得越多越容易留下」的加成，1 个就是 1 次掷骰。");
        structureLootCountMinPercent = cfgInt("structureLootCountMinPercent", DEF_LOOT_COUNT_MIN_PERCENT, 0, 100,
                "留下的物品数量下限（占模板数量的百分比）。默认 40。\n"
                + "例：模板里一格有 8 个钢锭，实际数量在 3（8×40%）~ 8 之间均匀重掷。\n"
                + "100 = 数量原样不动，只决定留不留；0 = 可能只剩 1 个（下限仍是 1，不会出现 0）。\n"
                + "单格的堆叠上限、以及模板本来就只放 1 个的（枪、护甲），不受本项影响。");
        structureLootScatter = cfgBool("structureLootScatter", DEF_LOOT_SCATTER,
                "是否把留下的战利品打散到随机格子。默认开。\n"
                + "开：整箱清空后随机挑空格，物品位置每次都不一样。\n"
                + "关：从第 0 格开始按原顺序摆放——保留模板的排列（比如「第一格永远是医疗包」），\n"
                + "只让存活与数量随机。想做「固定位置的剧情箱」就关掉它。\n"
                + "本项只在 structureLootRandomize 开着时有意义。");

        // ---- 自然遗迹：日志 ----
        structureDebugLog = cfgBool("structureDebugLog", DEF_STRUCTURE_DEBUG_LOG,
                "是否打印遗迹的详细日志。默认「关」，正常游玩时服务器日志里不会出现 [structure] 行。\n"
                + "关：注册、生成、劣化、箱子随机化全部静默（文件缺失之类的 WARN 仍然会打）。\n"
                + "开：会输出这几类 INFO——\n"
                + "  [structure] 结构工具方块 ID（创世神填充用，每次启动一次）\n"
                + "  [structure] 遗迹已注册: dim/nbt/size/y/grid/留边/劣化/战利品（每次启动一次）\n"
                + "  [structure] 地心实验室已生成: 底座 y=…（每座一次）\n"
                + "  [structure] 生成后处理 @ 区块(x,z) … → 方块劣化 N 个，战利品箱随机化 M 个（每个区块一次）\n"
                + "本项只管地心实验室；大型异虫巢有自己的 structureAlienHiveDebugLog（前缀 [structure-hive]）。\n"
                + "排查「遗迹到底生成了没有 / 劣化和箱子随机化有没有生效」时临时打开，"
                + "看完再关掉即可，不影响任何生成结果。");
        structureNbtFile = cfgStr("structureNbtFile", DEF_STRUCTURE_NBT,
                "要生成的建筑文件，写相对 assets/makentmgreatagain/ 的路径。\n"
                + "默认 structures/mnga_structure_geocentric_laboratory.nbt（自带的地心实验室）。\n"
                + "换成自己导出的结构时：把 .nbt 丢进 assets/makentmgreatagain/structures/，\n"
                + "这里写 structures/你的文件名.nbt 即可，不用改代码、不用重新编译。\n"
                + "注意三点：\n"
                + "  1) 必须是 HBM 的 hbm:tile.wand_structure 导出的格式（普通 WorldEdit .schematic 不认）；\n"
                + "  2) 找不到文件时只打一条 WARN 并跳过注册，不会崩，但也不会生成任何遗迹；\n"
                + "  3) 换文件后新建筑的尺寸会变，structureMaxY 之类的层位设置要自己跟着调，\n"
                + "     否则可能出现「整座建筑被压进基岩」或「悬在半空」。");

        // ---------------- 大型异虫巢 ----------------
        structureAlienHiveEnabled = cfgBool("enableStructureAlienHive", DEF_ALIEN_HIVE_ENABLED,
                "是否生成大型异虫巢（第二个 NBT 结构）。默认开。\n"
                + "结构文件不存在时只打一条 WARN 并跳过，不会崩。\n"
                + "它的生成条件是按 HBM 的「小虫巢」（GlyphidHive，主世界地表附近的异虫巢）复刻的：\n"
                + "主世界、跟随 HBM 的地牢/虫巢开关、贴地生成、平均每 5120 区块一座（小虫巢的 1/20）。");
        structureAlienHiveNbtFile = cfgStr("structureAlienHiveNbtFile", DEF_ALIEN_HIVE_NBT,
                "大型异虫巢的结构文件，写相对 assets/makentmgreatagain/ 的路径。\n"
                + "默认 structures/mnga_structure_large_alien_insec.nbt（自带的 31×13×31 虫巢）。\n"
                + "换建筑时把 .nbt 丢进 assets/makentmgreatagain/structures/ 再改这里即可，不用重编译。\n"
                + "⚠️ 换文件后尺寸会变，structureAlienHiveHeightOffset 需要自己跟着调，\n"
                + "否则可能出现「整座虫巢悬在半空」或「整个埋进地下」。");
        structureAlienHiveDimensionId = cfgInt("structureAlienHiveDimensionId", DEF_ALIEN_HIVE_DIM,
                -2147483648, 2147483647,
                "在哪个维度生成。默认 0 = 主世界。\n"
                + "HBM 的小虫巢只生成在主世界，改到别的维度就不再是 1/20 的关系了。");
        structureAlienHiveRarityMultiplier = cfgInt("structureAlienHiveRarityMultiplier", DEF_ALIEN_HIVE_RARITY_MULT,
                1, 1000,
                "比 HBM 小虫巢稀有多少倍。默认 20。\n"
                + "小虫巢的概率是「每 hiveSpawn 区块一个」（HBM 配置 MobConfig，默认 256），\n"
                + "所以本项 = 20 时，大虫巢平均 256×20 = 5120 区块一座，正好是小虫巢的 1/20。\n"
                + "  10  → 1/10，比较常见\n"
                + "  20  → 默认（1/20）\n"
                + "  50  → 1/50，非常难遇到\n"
                + "⚠️ 只在本文件下面的 structureAlienHiveDenominator = -1 时生效；\n"
                + "填了正数就直接用那个数字，倍数被忽略。");
        structureAlienHiveDenominator = cfgInt("structureAlienHiveDenominator", DEF_ALIEN_HIVE_DENOMINATOR,
                -1, 1000000,
                "直接指定「平均每 N 区块一座」。默认 -1 = 不指定，跟随 HBM 配置。\n"
                + "-1：分母 = HBM 的 hiveSpawn × structureAlienHiveRarityMultiplier。\n"
                + "    好处是玩家改了 HBM 的虫巢密度，大虫巢会自动保持 1/20 的关系。\n"
                + ">0 ：写死分母。例如 4096 → 平均每 4096 区块一座，与原版虫巢配置无关。\n"
                + "    适合整合包里 HBM 配置被锁死、只想单独调这一个结构的情况。");
        structureAlienHiveHeightOffset = cfgInt("structureAlienHiveHeightOffset", DEF_ALIEN_HIVE_HEIGHT_OFFSET,
                -32, 32,
                "高度偏移（格）。默认 0 = 结构几何中心落在地表（HBM 小虫巢的口径）。\n"
                + "算法：先取结构中心那一列的地表高度，把「结构几何中心」对齐到地表，再加本项。\n"
                + "自带虫巢高 13 格，所以 0 时是「地上露 6 格、地下埋 6 格」的一个大土包：\n"
                + "  0   → 默认，和小虫巢一样的半露状态\n"
                + "  -3  → 再往下压 3 格，只露顶盖\n"
                + "  -6  → 顶面与地面齐平，整座全埋（需要挖开才能进）\n"
                + "  +4  → 整体抬高，会明显鼓出地面\n"
                + "换过 structureAlienHiveNbtFile 之后一定要重算：新结构多高，就以 0 为中心自己试。");
        structureAlienHiveMinHeight = cfgInt("structureAlienHiveMinHeight", DEF_ALIEN_HIVE_MIN_Y, 0, 250,
                "结构底座高度的下限（clamp 用）。默认 1。\n"
                + "算出来的高度低于本项时会被抬到这里，防止整座虫巢掉进虚空或基岩层。\n"
                + "NBTStructure 对 y<1 的方块直接跳过，所以填 0 与填 1 结果相同。");
        structureAlienHiveMaxHeight = cfgInt("structureAlienHiveMaxHeight", DEF_ALIEN_HIVE_MAX_Y, 0, 250,
                "结构底座高度的上限（clamp 用）。默认 250。\n"
                + "正常地表（y≈60~120）远低于本项，一般不会触发；\n"
                + "只有在放大化/自定义地形里地表超高时才会把虫巢压下来。");
        structureAlienHiveMaxHeightVariation = cfgFloat("structureAlienHiveMaxHeightVariation",
                DEF_ALIEN_HIVE_MAX_VARIATION, 0.0F, 2.0F,
                "允许生成的地形起伏上限（原版 BiomeGenBase.heightVariation）。默认 2.0 = 不限。\n"
                + "HBM 的小虫巢本身不看地形平坦度，所以这里默认放开，保持「生成逻辑一模一样」。\n"
                + "想只让它在平缓地带出现就压到 0.35（平原/森林/丘陵），山地会被跳过。\n"
                + "参照：平原 0.05、森林 0.2、丘陵 0.3、山地 0.5~0.7、峭壁 1.0+。");
        structureAlienHiveAllowOcean = cfgBool("structureAlienHiveAllowOcean", DEF_ALIEN_HIVE_ALLOW_OCEAN,
                "是否允许在海洋/深海群系生成。默认开（和小虫巢一致：它不筛群系，海底也会刷）。\n"
                + "关：WATER、OCEAN 两类群系跳过。水下虫巢的箱子要水下呼吸才能开，\n"
                + "觉得烦就关掉这一项。");
        structureAlienHiveFollowHbm = cfgBool("structureAlienHiveFollowHbm", DEF_ALIEN_HIVE_FOLLOW_HBM,
                "是否跟随 HBM 的地牢/虫巢开关。默认开。\n"
                + "开：只有 HBM 那边允许生成地牢（enableDungeons / 地图特性）且启用虫巢（enableHives）时，\n"
                + "    大虫巢才会生成——这样「1/20」的比例在任何配置下都成立。\n"
                + "关：只看本模组自己的开关，即使 HBM 关了虫巢，大虫巢照样按分母刷。\n"
                + "⚠️ 关掉之后「1/20」就不再准确，因为小虫巢可能一个都不生成。");
        structureAlienHiveCarveInterior = cfgBool("structureAlienHiveCarveInterior", DEF_ALIEN_HIVE_CARVE,
                "内部空腔挖空。默认开。\n"
                + "虫巢 NBT 只记录实体方块，不记录空气。HBM 落方块时跳过没记录的格子，\n"
                + "于是内部巢室的空腔会被天然地形（石头/泥土）原样填死。\n"
                + "开：生成后把「封闭内腔」里的地形挖空成空气——判定方式是从包围盒边界\n"
                + "    泛洪填充，只挖与外界完全隔离的空腔（自带虫巢是个封闭穹顶，挖 3614 格）。\n"
                + "关：内腔保持地形填充（等于没有这个功能）。\n"
                + "壳体之外的格子永远不动：穹顶和地形之间的缝隙维持原样，不会悬空。");
        structureAlienHiveLootRandomize = cfgBool("structureAlienHiveLootRandomize", DEF_ALIEN_HIVE_LOOT,
                "箱子战利品随机化。默认开。\n"
                + "和地心实验室同一套机制：把 NBT 里预置的物品当模板，按稀有度掷保留率、\n"
                + "重掷数量、随机散布——保留率/散布等参数共用 structureLoot* 那几项配置。\n"
                + "关：箱子保持结构 NBT 里的原样（腺体、Spawn 蛋、U235/Pu239 板、燃料棒、弹药等）。\n"
                + "⚠️ 本项同时要求上面的 structureLootRandomize 为开，两项都开才生效。");
        structureAlienHiveDebugLog = cfgBool("structureAlienHiveDebugLog", DEF_ALIEN_HIVE_DEBUG_LOG,
                "是否打印大型异虫巢的详细日志（前缀 [structure-hive]）。默认关。\n"
                + "开：注册时打印分母/维度/层位，每次生成打印「虫巢已生成: 底座 y=…」，\n"
                + "    以及「空腔挖空 N 格，箱子随机化 M 个」。\n"
                + "排查「到底刷没刷 / 内腔有没有挖开」时临时打开。");

        // ---------------- 日落六号 ----------------
        structureSunsetSixEnabled = cfgBool("enableStructureSunsetSix", DEF_SUNSET_SIX_ENABLED,
                "是否生成日落六号（第三个 NBT 结构）。默认开。\n"
                + "结构文件不存在时只打一条 WARN 并跳过，不会崩。\n"
                + "生成条件：下界、每世界仅一个、以 (0,0) 为中心 x/z ±10000 内、\n"
                + "整座结构埋在岩浆里且上方不暴露。结构原样落地（不替换方块、不劣化、不挖空）。");
        structureSunsetSixNbtFile = cfgStr("structureSunsetSixNbtFile", DEF_SUNSET_SIX_NBT,
                "日落六号的结构文件，写相对 assets/makentmgreatagain/ 的路径。\n"
                + "默认 structures/mnga_structure_sunset_six.nbt（自带的 9×30×10 高塔）。\n"
                + "换建筑时把 .nbt 丢进 assets/makentmgreatagain/structures/ 再改这里即可，不用重编译。\n"
                + "⚠️ 换文件后高度会变，选址的岩浆浸没校验按 SunsetSixSite.STRUCT_HEIGHT 计算，\n"
                + "换更高的结构需要同步改那个常量。");
        structureSunsetSixDimensionId = cfgInt("structureSunsetSixDimensionId", DEF_SUNSET_SIX_DIM,
                -2147483648, 2147483647,
                "在哪个维度生成。默认 -1 = 下界。\n"
                + "「每世界仅一个」按维度独立计数：改到别的维度，那个维度也会各刷一座。");
        structureSunsetSixMinHeight = cfgInt("structureSunsetSixMinHeight", DEF_SUNSET_SIX_MIN_Y, 0, 250,
                "结构底座高度的下限（clamp 用）。默认 1。\n"
                + "选址时从这一层往上找「能埋进岩浆且上方不暴露」的位置。");
        structureSunsetSixMaxHeight = cfgInt("structureSunsetSixMaxHeight", DEF_SUNSET_SIX_MAX_Y, 0, 250,
                "结构底座高度的上限（clamp 用）。默认 120。\n"
                + "结构高 30 + 顶部至少 8 格覆盖，底座最高约 218；下界岩浆海主要在 y≈31 以下，\n"
                + "默认 120 已留足余量。");
        structureSunsetSixLootRandomize = cfgBool("structureSunsetSixLootRandomize", DEF_SUNSET_SIX_LOOT,
                "箱子战利品随机化。默认开。\n"
                + "和前两个遗迹同一套机制：把 NBT 里预置的物品当模板，按稀有度掷保留率、\n"
                + "重掷数量、随机散布——保留率/散布等参数共用 structureLoot* 那几项配置。\n"
                + "关：箱子保持结构 NBT 里的原样。\n"
                + "⚠️ 本项同时要求上面的 structureLootRandomize 为开，两项都开才生效。");
        structureSunsetSixDebugLog = cfgBool("structureSunsetSixDebugLog", DEF_SUNSET_SIX_DEBUG_LOG,
                "是否打印日落六号的详细日志（前缀 [structure-sunset]）。默认关。\n"
                + "开：注册时打印维度/尺寸等摘要。生成坐标日志已按用户要求移除，不再打印。");
        structureSunsetSixTestMode = cfgBool("structureSunsetSixTestMode", DEF_SUNSET_SIX_TEST_MODE,
                "测试模式。默认关。\n"
                + "开：固定刷在 (0,0) 区块、跳过岩浆深度校验，进下界即可直接验证。\n"
                + "关（正式逻辑）：由种子算唯一锚点（±5000 内）、要求底座起至少 40 格连续岩浆。\n"
                + "⚠️ 测试模式仍受「每世界仅一个」限制：同一个世界刷过一次后，要换新世界或删存档标记才会再刷。");
        structureSunsetSixTestBaseY = cfgInt("structureSunsetSixTestBaseY", DEF_SUNSET_SIX_TEST_BASE_Y, 1, 200,
                "测试模式的底座高度。默认 2（最初指定的高度）。\n"
                + "只在 structureSunsetSixTestMode 开着时生效；正式逻辑用岩浆扫描自动定层。");

        // ---------------- 区间自愈 ----------------
        if(patchRadiusMax < patchRadiusMin) patchRadiusMax = patchRadiusMin;
        if(saltVeinThicknessMax < saltVeinThicknessMin) saltVeinThicknessMax = saltVeinThicknessMin;
        if(saltVeinMaxY < saltVeinMinY) saltVeinMaxY = saltVeinMinY;
        if(leadzincVeinsPerChunkMax < leadzincVeinsPerChunkMin) leadzincVeinsPerChunkMax = leadzincVeinsPerChunkMin;
        if(leadzincClusterMax < leadzincClusterMin) leadzincClusterMax = leadzincClusterMin;
        if(leadzincMaxY < leadzincMinY) leadzincMaxY = leadzincMinY;
        if(refractoryGoldVeinsPerChunkMax < refractoryGoldVeinsPerChunkMin) refractoryGoldVeinsPerChunkMax = refractoryGoldVeinsPerChunkMin;
        if(refractoryGoldClusterMax < refractoryGoldClusterMin) refractoryGoldClusterMax = refractoryGoldClusterMin;
        if(refractoryGoldMaxY < refractoryGoldMinY) refractoryGoldMaxY = refractoryGoldMinY;
        if(scheeliteVeinsPerChunkMax < scheeliteVeinsPerChunkMin) scheeliteVeinsPerChunkMax = scheeliteVeinsPerChunkMin;
        if(scheeliteMaxY < scheeliteMinY) scheeliteMaxY = scheeliteMinY;
        if(dunaLeadzincMaxY < dunaLeadzincMinY) dunaLeadzincMaxY = dunaLeadzincMinY;
        if(dunaRefractoryGoldMaxY < dunaRefractoryGoldMinY) dunaRefractoryGoldMaxY = dunaRefractoryGoldMinY;
        if(dunaScheeliteMaxY < dunaScheeliteMinY) dunaScheeliteMaxY = dunaScheeliteMinY;
        if(deepSeaNodulePatchesMax < deepSeaNodulePatchesMin) deepSeaNodulePatchesMax = deepSeaNodulePatchesMin;
        if(deepSeaNoduleRadiusMax < deepSeaNoduleRadiusMin) deepSeaNoduleRadiusMax = deepSeaNoduleRadiusMin;
        if(structureMaxHeight < structureMinHeight) structureMaxHeight = structureMinHeight;
        if(structureAlienHiveMaxHeight < structureAlienHiveMinHeight) {
            structureAlienHiveMaxHeight = structureAlienHiveMinHeight;
        }
        if(structureSunsetSixMaxHeight < structureSunsetSixMinHeight) {
            structureSunsetSixMaxHeight = structureSunsetSixMinHeight;
        }
        // 留边不能吃掉整个格子：至少要给中间留 1 个可选的落点
        if(structureGridInset < 0) structureGridInset = 0;
        if(structureGridInset * 2 >= structureGridChunks) {
            structureGridInset = Math.max(0, (structureGridChunks - 1) / 2);
            LOG.warn("structureGridInset 对 structureGridChunks=" + structureGridChunks
                    + " 太大，已压到 " + structureGridInset + "（格内至少保留 1 个落点）");
        }

        // 版本号最后写，写的是源码里的版本 —— 于是本次启动之后手改的值会一直保留到下次升版
        cfgInt(VERSION_KEY, CONFIG_VERSION, 0, Integer.MAX_VALUE,
                "配置版本号（由代码写入，请勿手改）。\n"
                + "只有作者主动改过默认值并升版本时，本文件才会被整份重建、所有项回到默认值；\n"
                + "重建前旧文件会备份成同目录下的 worldgen.cfg.v<旧版本>.bak。\n"
                + "例外：global.cfg 的 preserveConfigOnUpdate=true 时，若新配置相对旧配置\n"
                + "只有新增键（见 worldgen.defaults.txt 快照对比），手调值会保留、不重建。");

        config.save();   // 首次启动时确保文件落盘

        LOG.info("  salt: enabled=" + saltWorldgenEnabled
                + ", patch=" + patchChancePercent + "% r" + patchRadiusMin + "~" + patchRadiusMax
                + ", hills=" + hillsScatterPercent + "%, cave=" + caveGravelPercent + "%"
                + ", vein=" + saltGravelVeinEnabled + " region=" + saltVeinRegionSize
                + " chance=" + saltVeinChancePercent + "%");
        LOG.info("  leadzinc: enabled=" + leadzincWorldgenEnabled
                + ", chance=" + leadzincChunkChancePercent + "%"
                + ", veins=" + leadzincVeinsPerChunkMin + "~" + leadzincVeinsPerChunkMax
                + ", r=" + leadzincVeinRadius
                + ", attempts=" + leadzincClusterMin + "~" + leadzincClusterMax
                + ", y=" + leadzincMinY + "~" + leadzincMaxY);
        LOG.info("  refractoryGold: enabled=" + refractoryGoldWorldgenEnabled
                + ", chance=" + refractoryGoldChunkChancePercent + "%"
                + ", veins=" + refractoryGoldVeinsPerChunkMin + "~" + refractoryGoldVeinsPerChunkMax
                + ", r=" + refractoryGoldVeinRadius
                + ", attempts=" + refractoryGoldClusterMin + "~" + refractoryGoldClusterMax
                + ", y=" + refractoryGoldMinY + "~" + refractoryGoldMaxY);
        LOG.info("  scheelite: enabled=" + scheeliteWorldgenEnabled
                + ", chance=" + scheeliteChunkChancePercent + "% (avg " + avgVeinSpacing() + " chunks)"
                + ", veins=" + scheeliteVeinsPerChunkMin + "~" + scheeliteVeinsPerChunkMax
                + ", r=" + scheeliteVeinRadius + " (filled, " + scheeliteVeinBlocks() + " blocks/vein)"
                + ", y=" + scheeliteMinY + "~" + scheeliteMaxY);
        LOG.info("  duna: dim=" + dunaDimensionId
                + ", leadzinc=" + dunaLeadzincEnabled + " " + dunaLeadzincChunkChancePercent + "% y" + dunaLeadzincMinY + "~" + dunaLeadzincMaxY
                + ", refractoryGold=" + dunaRefractoryGoldEnabled + " " + dunaRefractoryGoldChunkChancePercent + "% y" + dunaRefractoryGoldMinY + "~" + dunaRefractoryGoldMaxY
                + ", scheelite=" + dunaScheeliteEnabled + " " + dunaScheeliteChunkChancePercent + "% y" + dunaScheeliteMinY + "~" + dunaScheeliteMaxY);
        LOG.info("  deepSeaNodule: enabled=" + deepSeaNoduleWorldgenEnabled
                + ", chance=" + deepSeaNoduleChancePercent + "%"
                + ", patches=" + deepSeaNodulePatchesMin + "~" + deepSeaNodulePatchesMax
                + ", r=" + deepSeaNoduleRadiusMin + "~" + deepSeaNoduleRadiusMax
                + ", fill=" + deepSeaNoduleFillPercent + "%, minDepth=" + deepSeaNoduleMinWaterDepth);
        LOG.info("  structure: enabled=" + structureWorldgenEnabled
                + ", dim=" + structureDimensionId
                + ", grid=" + structureGridChunks + " (1 per " + (structureGridChunks * structureGridChunks) + " chunks)"
                + ", inset=" + structureGridInset
                + ", y=" + structureMinHeight + "~" + structureMaxHeight
                + " offset=" + structureHeightOffset
                + ", variation<=" + structureMaxHeightVariation
                + (structureAllowOcean ? ", ocean=allowed" : ", ocean=skipped"));
        LOG.info("  structure hive: enabled=" + structureAlienHiveEnabled
                + ", dim=" + structureAlienHiveDimensionId
                + ", rarity=" + (structureAlienHiveDenominator > 0
                        ? ("1/" + structureAlienHiveDenominator + " (fixed)")
                        : ("HBM hiveSpawn x" + structureAlienHiveRarityMultiplier))
                + ", y=" + structureAlienHiveMinHeight + "~" + structureAlienHiveMaxHeight
                + " offset=" + structureAlienHiveHeightOffset
                + ", variation<=" + structureAlienHiveMaxHeightVariation
                + (structureAlienHiveAllowOcean ? ", ocean=allowed" : ", ocean=skipped")
                + (structureAlienHiveFollowHbm ? ", followHBM" : ", standalone"));
        LOG.info("  structure sunset six: enabled=" + structureSunsetSixEnabled
                + ", dim=" + structureSunsetSixDimensionId
                + ", 每世界仅一个, radius=" + com.makentmgreatagain.world.SunsetSixSite.RANGE
                + ", y=" + structureSunsetSixMinHeight + "~" + structureSunsetSixMaxHeight
                + ", loot=" + structureSunsetSixLootRandomize
                + ", debugLog=" + structureSunsetSixDebugLog
                + (structureSunsetSixTestMode
                        ? ", TEST MODE @ (0,0) baseY=" + structureSunsetSixTestBaseY
                        : ", normal spawn"));
        LOG.info("  structure decay: enabled=" + structureDecayEnabled
                + ", scale=" + structureDecayScalePercent + "%"
                + (structureDecayEnabled && structureDecayScalePercent == 0 ? " (= disabled)" : "")
                + ", debugLog=" + structureDebugLog);
        if(structureLootRandomize) {
            // 顺序固定为 常见/较稀有/稀有/极稀有/传说，方便和 structureLootKeep* 五项对照
            LOG.info("  structure loot: keep=" + lootKeepCommonPercent + "/" + lootKeepUncommonPercent
                    + "/" + lootKeepRarePercent + "/" + lootKeepEpicPercent + "/" + lootKeepLegendaryPercent + "%"
                    + " (common/uncommon/rare/epic/legendary)"
                    + ", count>=" + structureLootCountMinPercent + "%"
                    + ", scatter=" + structureLootScatter);
        } else {
            LOG.info("  structure loot: randomize=false（箱子完全按结构 NBT 原样生成）");
        }
    }

    /** 把「每区块概率」换算成「平均多少个区块一条」，只用于启动日志，方便对配置。 */
    private static String avgVeinSpacing() {
        if(scheeliteChunkChancePercent <= 0) return "never";
        return String.format("%.1f", 100.0 / scheeliteChunkChancePercent);
    }

    /** 实心矿囊的格数（球内整数点个数），只用于启动日志。 */
    private static int scheeliteVeinBlocks() {
        int r = Math.max(scheeliteVeinRadius, 1);
        int r2 = r * r;
        int n = 0;
        for(int x = -r; x <= r; x++)
            for(int y = -r; y <= r; y++)
                for(int z = -r; z <= r; z++)
                    if(x * x + y * y + z * z <= r2) n++;
        return n;
    }

    // =====================================================================================
    //  小工具
    // =====================================================================================

    private static int cfgInt(String key, int def, int min, int max, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getInt(key, CAT, def, min, max, comment);
    }

    private static float cfgFloat(String key, float def, float min, float max, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getFloat(key, CAT, def, min, max, comment);
    }

    private static boolean cfgBool(String key, boolean def, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getBoolean(key, CAT, def, comment);
    }

    /** 字符串项。空串/全空白一律退回默认值，免得有人在 cfg 里留一行空的把结构关掉。 */
    private static String cfgStr(String key, String def, String comment) {
        DEFAULTS.put(key, def == null ? "" : def);
        String value = config.getString(key, CAT, def, comment);
        if(value == null || value.trim().isEmpty()) return def;
        return value.trim();
    }

    /**
     * 直接从 cfg 文本里读版本号，不经过 Forge 的 Configuration。
     *
     * <p>必须在 {@code new Configuration(...)} 之前读 —— 因为读过一次之后 Forge 就会把
     * 默认值和已有值混在一起，再也分不清文件里原本写的是几了。</p>
     *
     * <p><b>不要用 {@link java.util.Properties} 解析这个文件</b>：Properties 把
     * {@code :} 也当键值分隔符，Forge 写的 {@code I:configVersion=7} 会被拆成
     * 键 {@code I} / 值 {@code configVersion=7}，于是 {@code getProperty("I:configVersion")}
     * 永远拿不到东西、版本号恒为 -1，结果是<b>每次启动都判定版本不符、把玩家的配置删掉重建</b>
     * （这个坑真实发生过：玩家改的设置每局都回到默认）。所以这里老老实实逐行扫文本。</p>
     *
     * <p>认的写法：可选类型前缀（{@code I:} / {@code S:}）、可选引号、{@code =} 或 {@code :}
     * 分隔、前后空白、CRLF；注释行（{@code #} / {@code !} 开头）一律跳过。
     * 文件不存在、读不出来、找不到键，一律返回 -1。</p>
     */
    private static int readVersion(File file) {
        if(!file.isFile()) return -1;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while((line = reader.readLine()) != null) {
                String s = line.trim();
                if(s.isEmpty()) continue;
                if(s.charAt(0) == '#' || s.charAt(0) == '!') continue;   // Forge 的注释

                int at = s.indexOf(VERSION_KEY);
                if(at < 0) continue;

                String tail = s.substring(at + VERSION_KEY.length()).trim();
                if(tail.isEmpty()) continue;
                char sep = tail.charAt(0);
                if(sep != '=' && sep != ':') continue;
                tail = tail.substring(1).trim();

                int i = 0;
                boolean negative = false;
                if(i < tail.length() && (tail.charAt(i) == '"' || tail.charAt(i) == '\'')) i++;   // S: 类型会带引号
                if(i < tail.length() && (tail.charAt(i) == '-' || tail.charAt(i) == '+')) {
                    negative = tail.charAt(i) == '-';
                    i++;
                }
                int digitsFrom = i;
                while(i < tail.length() && Character.isDigit(tail.charAt(i))) i++;
                if(i == digitsFrom) continue;

                try {
                    int v = Integer.parseInt(tail.substring(digitsFrom, i));
                    return negative ? -v : v;
                } catch(NumberFormatException e) {
                    return -1;
                }
            }
            return -1;
        } catch(Exception e) {
            LOG.warn("Could not read " + file.getName() + ": " + e);
            return -1;
        } finally {
            if(reader != null) {
                try { reader.close(); } catch(Exception ignored) { }
            }
        }
    }

    /**
     * 版本对不上、真的要重建之前，先把玩家那份存成 {@code worldgen.cfg.v<旧版本>.bak}。
     *
     * <p>删文件是唯一能让 Forge 重新吐默认值的办法（键一旦存在，代码默认值就永久失效），
     * 但删掉玩家的手调值太粗暴——留个备份，至少能翻出来抄回去。</p>
     */
    private static void backup(File file, File dir, int oldVersion) {
        File target = new File(dir, file.getName() + ".v" + oldVersion + ".bak");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(file);
            out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) out.write(buf, 0, n);
            LOG.info("旧配置已备份: " + target.getName());
        } catch(Exception e) {
            LOG.warn("备份旧配置失败（不影响重建）: " + e);
        } finally {
            if(in != null) { try { in.close(); } catch(Exception ignored) { } }
            if(out != null) { try { out.close(); } catch(Exception ignored) { } }
        }
    }

    // =====================================================================================
    //  默认值快照（preserveConfigOnUpdate 的对比基准）
    // =====================================================================================

    /**
     * 本次启动的默认值全部收集完之后调用：把 {@link #DEFAULTS} 写进快照文件。
     *
     * <p>快照永远是「上一次运行时源码的默认值集合」。下次升版时拿它和新的
     * {@link #DEFAULTS} 对比，就能回答「新配置相对旧配置是不是只有新增」。</p>
     */
    private static void writeDefaultsSnapshot() {

        File snapshot = new File(worldgenDir, SNAPSHOT_NAME);
        java.io.Writer writer = null;
        try {
            writer = new java.io.OutputStreamWriter(new FileOutputStream(snapshot), "UTF-8");
            writer.write("# MNGA worldgen.cfg 默认值快照（由模组每次启动自动写入，请勿手改）\n");
            writer.write("# 用途：配合 global.cfg 的 preserveConfigOnUpdate=true，判断新版本配置\n");
            writer.write("# 相对旧版本是否「只有新增」——是则保留玩家手调值，否则整份重建。\n");
            writer.write("# 删掉本文件 = 下次升版无法确认增量兼容，按「重建」处理。\n");
            for(Map.Entry<String, String> e : DEFAULTS.entrySet()) {
                writer.write(e.getKey() + "=" + e.getValue() + "\n");
            }
        } catch(Exception e) {
            LOG.warn("默认值快照写入失败（不影响本次运行）: " + e);
        } finally {
            if(writer != null) { try { try { writer.close(); } catch(Exception ignored) { } } catch(Throwable ignored) { } }
        }
    }

    /** 读上一份快照。文件不存在或解析失败返回空表（调用方按「无法确认增量」处理）。 */
    private static java.util.LinkedHashMap<String, String> readDefaultsSnapshot() {

        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<String, String>();
        File snapshot = new File(worldgenDir, SNAPSHOT_NAME);
        if(!snapshot.isFile()) return result;

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(snapshot), "UTF-8"));
            String line;
            while((line = reader.readLine()) != null) {
                line = line.trim();
                if(line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue;
                int eq = line.indexOf('=');
                if(eq <= 0) continue;
                result.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch(Exception e) {
            LOG.warn("默认值快照读取失败，按「无快照」处理: " + e);
            result.clear();
        } finally {
            if(reader != null) { try { reader.close(); } catch(Exception ignored) { } }
        }
        return result;
    }

    /**
     * 判断「新默认相对旧快照是否只有新增」。
     *
     * <p>判据（对旧快照里的每个键）：</p>
     * <ul>
     *   <li>新代码里没有这个键了 → 不是新增，是移除 → false；</li>
     *   <li>默认值变了 → false；</li>
     *   <li>{@code configVersion} 本身不参与对比——它就是触发重建的开关，
     *       每次升版必然「变化」，把它算进去这条机制就永远走不通了。</li>
     * </ul>
     *
     * <p>新代码多出来的键不在旧快照里，天然通过——那正是「新增」，Forge 会
     * 在 save() 时把它们按默认值补进玩家的 cfg。手改出来的野键两边都不管。</p>
     */
    private static boolean isDefaultsSnapshotAdditive() {

        java.util.LinkedHashMap<String, String> old = readDefaultsSnapshot();
        if(old.isEmpty()) {
            LOG.info("没有可用的上一版默认值快照（" + SNAPSHOT_NAME
                    + " 缺失或为空），无法确认增量兼容，按重建处理");
            return false;
        }

        boolean additive = true;
        for(Map.Entry<String, String> e : old.entrySet()) {
            if(e.getKey().equals(VERSION_KEY)) continue;

            String current = DEFAULTS.get(e.getKey());
            if(current == null) {
                LOG.warn("配置增量检查：键 " + e.getKey() + " 在新版本中已被移除 → 不满足「只有新增」");
                additive = false;
            } else if(!current.equals(e.getValue())) {
                LOG.warn("配置增量检查：键 " + e.getKey() + " 的默认值变了（"
                        + e.getValue() + " -> " + current + "）→ 不满足「只有新增」");
                additive = false;
            }
        }
        return additive;
    }
}

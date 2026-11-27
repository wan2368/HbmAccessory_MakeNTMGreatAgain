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

    private static final String CAT_META = "00_meta";
    private static final String CAT_SALT = "01_salt";
    private static final String CAT_LEADZINC = "02_ore_leadzinc";
    private static final String CAT_REFRACTORY_GOLD = "03_ore_refractory_gold";
    private static final String CAT_SCHEELITE = "04_ore_scheelite";
    private static final String CAT_DUNA = "05_ore_duna";
    private static final String CAT_DEEP_SEA_NODULE = "06_ore_deep_sea_nodule";
    private static final String CAT_STRUCT_LAB = "07_structure_geocentric_lab";
    private static final String CAT_STRUCT_HIVE = "08_structure_alien_hive";
    private static final String CAT_STRUCT_SUNSET_SIX = "09_structure_sunset_six";

    /** structure_loot.cfg 分类 */
    private static final String CAT_DECAY = "decay";
    private static final String CAT_LOOT = "loot";

    /** 配置文件里记录版本号用的键名（Forge 会写成 {@code I:configVersion}）。 */
    private static final String VERSION_KEY = "configVersion";

    /**
     * 配置版本号。<b>改过任何一个 {@code DEF_*} 之后必须 +1</b>，
     * 否则老存档目录里的 worldgen.cfg 还会拿旧值，代码里改了也看不出效果。
     */
    public static final int CONFIG_VERSION = 11;

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
                if(config.hasCategory(CAT_META) && config.getCategory(CAT_META).containsKey(VERSION_KEY)) {
                    config.getCategory(CAT_META).get(VERSION_KEY).set(String.valueOf(CONFIG_VERSION));
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

        // 加载独立的遗迹战利品与劣化配置（不属于世界生成）
        loadStructureLoot(dir);

        // 同步已迁移到 global.cfg 的调试项
        structureSunsetSixTestMode = GlobalConfig.structureSunsetSixTestMode;
        structureSunsetSixTestBaseY = GlobalConfig.structureSunsetSixTestBaseY;

        // 快照永远按「本次的默认值」刷新：下次升版时，它就是对比里的「旧版默认」一侧。
        writeDefaultsSnapshot();

        LOG.info("MNGA worldgen config v" + CONFIG_VERSION + (rebuilt ? " (rebuilt)" : "")
                + " -> " + worldgenFile.getAbsolutePath());
    }

    /** 加载 config/MNGA/structure_loot.cfg（遗迹战利品随机化与方块劣化，独立于 worldgen.cfg）。 */
    private static void loadStructureLoot(File dir) {
        File lootFile = new File(dir, "structure_loot.cfg");
        Configuration lootCfg = new Configuration(lootFile);
        lootCfg.load();

        structureDecayEnabled = lootCfg.getBoolean("structureDecayEnabled", CAT_DECAY, DEF_STRUCTURE_DECAY,
                "遗迹方块劣化总开关（关闭则完全按 NBT 原样生成）。");
        structureDecayScalePercent = lootCfg.getInt("structureDecayScalePercent", CAT_DECAY, DEF_STRUCTURE_DECAY_SCALE, 0, 400,
                "遗迹方块劣化强度倍率（%，100=默认，0=不劣化）。");

        structureLootRandomize = lootCfg.getBoolean("structureLootRandomize", CAT_LOOT, DEF_STRUCTURE_LOOT_RANDOMIZE,
                "箱子战利品随机化总开关（关闭则所有遗迹箱子保持 NBT 原样）。");
        structureAlienHiveLootRandomize = lootCfg.getBoolean("structureAlienHiveLootRandomize", CAT_LOOT, DEF_ALIEN_HIVE_LOOT,
                "大型异虫巢箱子随机化（需同时开启 structureLootRandomize）。");
        structureSunsetSixLootRandomize = lootCfg.getBoolean("structureSunsetSixLootRandomize", CAT_LOOT, DEF_SUNSET_SIX_LOOT,
                "日落六号箱子随机化（需同时开启 structureLootRandomize）。");
        structureLootScatter = lootCfg.getBoolean("structureLootScatter", CAT_LOOT, DEF_LOOT_SCATTER,
                "是否将留下的战利品随机打散到箱子空格（关闭则按原槽位顺序摆放）。");
        structureLootCountMinPercent = lootCfg.getInt("structureLootCountMinPercent", CAT_LOOT, DEF_LOOT_COUNT_MIN_PERCENT, 0, 100,
                "保留物品的数量下限（占模板数量百分比，至少保留 1 个）。");
        lootKeepCommonPercent = lootCfg.getInt("structureLootKeepCommon", CAT_LOOT, DEF_LOOT_KEEP_COMMON, 0, 100,
                "常见物品保留率（%）：工业耗材、建材、普通食物、核废料。");
        lootKeepUncommonPercent = lootCfg.getInt("structureLootKeepUncommon", CAT_LOOT, DEF_LOOT_KEEP_UNCOMMON, 0, 100,
                "较稀有物品保留率（%）：加工品、防护服、医疗、弹药。");
        lootKeepRarePercent = lootCfg.getInt("structureLootKeepRare", CAT_LOOT, DEF_LOOT_KEEP_RARE, 0, 100,
                "稀有物品保留率（%）：核材料、机器方块、军械、升级件。");
        lootKeepEpicPercent = lootCfg.getInt("structureLootKeepEpic", CAT_LOOT, DEF_LOOT_KEEP_EPIC, 0, 100,
                "极稀有物品保留率（%）：动力装甲、蒸汽服。");
        lootKeepLegendaryPercent = lootCfg.getInt("structureLootKeepLegendary", CAT_LOOT, DEF_LOOT_KEEP_LEGENDARY, 0, 100,
                "传说物品保留率（%）：附魔金苹果等。");

        lootCfg.save();
    }

    /** worldgen.cfg 的完整加载过程。重建时会被调第二遍（文件已删，全部取默认值）。 */
    private static void loadWorldgen() {

        DEFAULTS.clear();
        oldFileVersion = readVersion(worldgenFile);
        versionMismatch = worldgenFile.exists() && oldFileVersion != CONFIG_VERSION;

        config = new Configuration(worldgenFile);
        config.load();

        // ---------------- 00. 元信息 ----------------
        cfgInt(CAT_META, VERSION_KEY, CONFIG_VERSION, 0, Integer.MAX_VALUE,
                "配置版本号（由代码自动维护，请勿手改）。");

        // ---------------- 01. 盐 ----------------
        saltWorldgenEnabled = cfgBool(CAT_SALT, "enableSaltWorldgen", DEF_SALT_ENABLED,
                "盐矿世界生成总开关（盐斑/地表散点/地下矿脉）。");
        patchChancePercent = cfgInt(CAT_SALT, "patchChancePercent", DEF_PATCH_CHANCE, 0, 100,
                "每 3x3 区块生成地表盐斑的概率（%）。");
        patchRadiusMin = cfgInt(CAT_SALT, "patchRadiusMin", DEF_PATCH_RADIUS_MIN, 2, 32,
                "地表盐斑半径下限（格）。");
        patchRadiusMax = cfgInt(CAT_SALT, "patchRadiusMax", DEF_PATCH_RADIUS_MAX, 2, 32,
                "地表盐斑半径上限（格）。");
        patchReplacePercent = cfgInt(CAT_SALT, "patchReplacePercent", DEF_PATCH_REPLACE, 0, 100,
                "盐斑内地表列替换为含盐方块的概率（%）。");
        hillsScatterPercent = cfgInt(CAT_SALT, "hillsScatterPercent", DEF_HILLS_SCATTER, 0, 100,
                "丘陵散点区内石/土替换概率（%，砾石为 4 倍，0=关闭）。");
        caveGravelPercent = cfgInt(CAT_SALT, "caveGravelPercent", DEF_CAVE_GRAVEL, 0, 100,
                "丘陵/峡谷裸露砾石替换为盐砾石的概率（%，避开原油列）。");
        saltGravelVeinEnabled = cfgBool(CAT_SALT, "enableSaltGravelVein", DEF_SALT_VEIN_ENABLED,
                "是否生成地下盐砾石矿脉。");
        saltVeinRegionSize = cfgInt(CAT_SALT, "saltVeinRegionSize", DEF_SALT_VEIN_REGION, 3, 32,
                "地下盐矿脉网格边长（区块，越小越密）。");
        saltVeinChancePercent = cfgInt(CAT_SALT, "saltVeinChancePercent", DEF_SALT_VEIN_CHANCE, 0, 100,
                "每个网格生成地下盐矿脉的概率（%，触发时生成 1~3 处）。");
        saltVeinThicknessMin = cfgInt(CAT_SALT, "saltVeinThicknessMin", DEF_SALT_VEIN_THICK_MIN, 1, 32,
                "单处地下盐矿脉最薄层数。");
        saltVeinThicknessMax = cfgInt(CAT_SALT, "saltVeinThicknessMax", DEF_SALT_VEIN_THICK_MAX, 1, 32,
                "单处地下盐矿脉最厚层数。");
        saltVeinMinY = cfgInt(CAT_SALT, "saltVeinMinY", DEF_SALT_VEIN_MIN_Y, 1, 250,
                "地下盐矿脉中心高度下限。");
        saltVeinMaxY = cfgInt(CAT_SALT, "saltVeinMaxY", DEF_SALT_VEIN_MAX_Y, 1, 250,
                "地下盐矿脉中心高度上限（始终低于地表 3 格）。");
        saltVeinFillPercent = cfgInt(CAT_SALT, "saltVeinFillPercent", DEF_SALT_VEIN_FILL, 10, 100,
                "地下盐矿脉椭圆内填充率（%）。");

        // ---------------- 02. 铅锌矿 ----------------
        leadzincWorldgenEnabled = cfgBool(CAT_LEADZINC, "enableLeadZincWorldgen", DEF_LEADZINC_ENABLED,
                "是否自然生成铅锌矿。");
        leadzincChunkChancePercent = cfgInt(CAT_LEADZINC, "leadzincChunkChancePercent", DEF_LEADZINC_CHANCE, 0, 100,
                "每区块生成铅锌矿囊的概率（%）。");
        leadzincVeinsPerChunkMin = cfgInt(CAT_LEADZINC, "leadzincVeinsPerChunkMin", DEF_LEADZINC_VEINS_MIN, 1, 8,
                "触发时每区块矿囊数下限。");
        leadzincVeinsPerChunkMax = cfgInt(CAT_LEADZINC, "leadzincVeinsPerChunkMax", DEF_LEADZINC_VEINS_MAX, 1, 8,
                "触发时每区块矿囊数上限。");
        leadzincVeinRadius = cfgInt(CAT_LEADZINC, "leadzincVeinRadius", DEF_LEADZINC_RADIUS, 1, 6,
                "单处矿囊球体半径（格）。");
        leadzincClusterMin = cfgInt(CAT_LEADZINC, "leadzincClusterMin", DEF_LEADZINC_ATTEMPTS_MIN, 1, 128,
                "单处矿囊撒点次数下限。");
        leadzincClusterMax = cfgInt(CAT_LEADZINC, "leadzincClusterMax", DEF_LEADZINC_ATTEMPTS_MAX, 1, 128,
                "单处矿囊撒点次数上限。");
        leadzincMinY = cfgInt(CAT_LEADZINC, "leadzincMinY", DEF_LEADZINC_MIN_Y, 1, 250,
                "生成高度下限。");
        leadzincMaxY = cfgInt(CAT_LEADZINC, "leadzincMaxY", DEF_LEADZINC_MAX_Y, 1, 250,
                "生成高度上限。");

        // ---------------- 03. 含砷金矿 ----------------
        refractoryGoldWorldgenEnabled = cfgBool(CAT_REFRACTORY_GOLD, "enableRefractoryGoldWorldgen", DEF_AGOLD_ENABLED,
                "是否自然生成含砷金矿。");
        refractoryGoldChunkChancePercent = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldChunkChancePercent", DEF_AGOLD_CHANCE, 0, 100,
                "每区块生成含砷金矿囊的概率（%）。");
        refractoryGoldVeinsPerChunkMin = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldVeinsPerChunkMin", DEF_AGOLD_VEINS_MIN, 1, 8,
                "触发时每区块矿囊数下限。");
        refractoryGoldVeinsPerChunkMax = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldVeinsPerChunkMax", DEF_AGOLD_VEINS_MAX, 1, 8,
                "触发时每区块矿囊数上限。");
        refractoryGoldVeinRadius = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldVeinRadius", DEF_AGOLD_RADIUS, 1, 6,
                "单处矿囊球体半径（格）。");
        refractoryGoldClusterMin = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldClusterMin", DEF_AGOLD_ATTEMPTS_MIN, 1, 128,
                "单处矿囊撒点次数下限。");
        refractoryGoldClusterMax = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldClusterMax", DEF_AGOLD_ATTEMPTS_MAX, 1, 128,
                "单处矿囊撒点次数上限。");
        refractoryGoldMinY = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldMinY", DEF_AGOLD_MIN_Y, 1, 250,
                "生成高度下限。");
        refractoryGoldMaxY = cfgInt(CAT_REFRACTORY_GOLD, "refractoryGoldMaxY", DEF_AGOLD_MAX_Y, 1, 250,
                "生成高度上限。");

        // ---------------- 04. 白钨矿 ----------------
        scheeliteWorldgenEnabled = cfgBool(CAT_SCHEELITE, "enableScheeliteWorldgen", DEF_SCHEELITE_ENABLED,
                "是否自然生成白钨矿（湿法炼钨起点）。");
        scheeliteChunkChancePercent = cfgInt(CAT_SCHEELITE, "scheeliteChunkChancePercent", DEF_SCHEELITE_CHANCE, 0, 100,
                "每区块生成白钨矿囊的概率（%，平均间隔约 100/本值 区块）。");
        scheeliteVeinsPerChunkMin = cfgInt(CAT_SCHEELITE, "scheeliteVeinsPerChunkMin", DEF_SCHEELITE_VEINS_MIN, 1, 4,
                "触发时每区块矿囊数下限。");
        scheeliteVeinsPerChunkMax = cfgInt(CAT_SCHEELITE, "scheeliteVeinsPerChunkMax", DEF_SCHEELITE_VEINS_MAX, 1, 4,
                "触发时每区块矿囊数上限。");
        scheeliteVeinRadius = cfgInt(CAT_SCHEELITE, "scheeliteVeinRadius", DEF_SCHEELITE_RADIUS, 1, 10,
                "单处实心矿球半径（格，默认 6 约 925 格）。");
        scheeliteMinY = cfgInt(CAT_SCHEELITE, "scheeliteMinY", DEF_SCHEELITE_MIN_Y, 1, 250,
                "生成高度下限。");
        scheeliteMaxY = cfgInt(CAT_SCHEELITE, "scheeliteMaxY", DEF_SCHEELITE_MAX_Y, 1, 250,
                "生成高度上限。");

        // ---------------- 05. Duna 矿物变体 ----------------
        dunaDimensionId = cfgInt(CAT_DUNA, "dunaDimensionId", DEF_DUNA_DIM_ID, -2147483648, 2147483647,
                "Duna 维度 ID（需与航天版 SpaceConfig 保持一致）。");
        dunaLeadzincEnabled = cfgBool(CAT_DUNA, "enableDunaLeadZinc", DEF_DUNA_LEADZINC_ENABLED,
                "是否在 Duna 生成铅锌矿（矿囊尺寸沿用主世界参数）。");
        dunaLeadzincChunkChancePercent = cfgInt(CAT_DUNA, "dunaLeadzincChunkChancePercent", DEF_DUNA_LEADZINC_CHANCE, 0, 100,
                "Duna 铅锌矿每区块生成概率（%）。");
        dunaLeadzincMinY = cfgInt(CAT_DUNA, "dunaLeadzincMinY", DEF_DUNA_LEADZINC_MIN_Y, 1, 250,
                "Duna 铅锌矿高度下限。");
        dunaLeadzincMaxY = cfgInt(CAT_DUNA, "dunaLeadzincMaxY", DEF_DUNA_LEADZINC_MAX_Y, 1, 250,
                "Duna 铅锌矿高度上限。");
        dunaRefractoryGoldEnabled = cfgBool(CAT_DUNA, "enableDunaRefractoryGold", DEF_DUNA_AGOLD_ENABLED,
                "是否在 Duna 生成含砷金矿。");
        dunaRefractoryGoldChunkChancePercent = cfgInt(CAT_DUNA, "dunaRefractoryGoldChunkChancePercent", DEF_DUNA_AGOLD_CHANCE, 0, 100,
                "Duna 含砷金矿每区块生成概率（%）。");
        dunaRefractoryGoldMinY = cfgInt(CAT_DUNA, "dunaRefractoryGoldMinY", DEF_DUNA_AGOLD_MIN_Y, 1, 250,
                "Duna 含砷金矿高度下限。");
        dunaRefractoryGoldMaxY = cfgInt(CAT_DUNA, "dunaRefractoryGoldMaxY", DEF_DUNA_AGOLD_MAX_Y, 1, 250,
                "Duna 含砷金矿高度上限。");
        dunaScheeliteEnabled = cfgBool(CAT_DUNA, "enableDunaScheelite", DEF_DUNA_SCHEELITE_ENABLED,
                "是否在 Duna 生成白钨矿（矿囊半径沿用主世界参数）。");
        dunaScheeliteChunkChancePercent = cfgInt(CAT_DUNA, "dunaScheeliteChunkChancePercent", DEF_DUNA_SCHEELITE_CHANCE, 0, 100,
                "Duna 白钨矿每区块生成概率（%）。");
        dunaScheeliteMinY = cfgInt(CAT_DUNA, "dunaScheeliteMinY", DEF_DUNA_SCHEELITE_MIN_Y, 1, 250,
                "Duna 白钨矿高度下限。");
        dunaScheeliteMaxY = cfgInt(CAT_DUNA, "dunaScheeliteMaxY", DEF_DUNA_SCHEELITE_MAX_Y, 1, 250,
                "Duna 白钨矿高度上限。");

        // ---------------- 06. 深海结核混合物 ----------------
        deepSeaNoduleWorldgenEnabled = cfgBool(CAT_DEEP_SEA_NODULE, "enableDeepSeaNoduleWorldgen", DEF_NODULE_ENABLED,
                "是否自然生成深海结核混合物（海床表层矿）。");
        deepSeaNoduleChancePercent = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNoduleChancePercent", DEF_NODULE_CHANCE, 0, 100,
                "每个海洋区块生成结核的概率（%）。");
        deepSeaNodulePatchesMin = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNodulePatchesMin", DEF_NODULE_PATCHES_MIN, 1, 4,
                "触发时每区块生成片数下限。");
        deepSeaNodulePatchesMax = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNodulePatchesMax", DEF_NODULE_PATCHES_MAX, 1, 4,
                "触发时每区块生成片数上限。");
        deepSeaNoduleRadiusMin = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNoduleRadiusMin", DEF_NODULE_RADIUS_MIN, 1, 7,
                "单处结核片半径下限（格）。");
        deepSeaNoduleRadiusMax = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNoduleRadiusMax", DEF_NODULE_RADIUS_MAX, 1, 7,
                "单处结核片半径上限（格）。");
        deepSeaNoduleFillPercent = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNoduleFillPercent", DEF_NODULE_FILL, 0, 100,
                "圆面积内连通结核填充率（%）。");
        deepSeaNoduleMinWaterDepth = cfgInt(CAT_DEEP_SEA_NODULE, "deepSeaNoduleMinWaterDepth", DEF_NODULE_MIN_DEPTH, 1, 60,
                "生成所需最小水深（格）。");

        // ---------------- 07. 遗迹：地心实验室 ----------------
        structureWorldgenEnabled = cfgBool(CAT_STRUCT_LAB, "enableStructureWorldgen", DEF_STRUCTURE_ENABLED,
                "是否生成地心实验室遗迹。");
        structureNbtFile = cfgStr(CAT_STRUCT_LAB, "structureNbtFile", DEF_STRUCTURE_NBT,
                "结构 NBT 路径（相对 assets/makentmgreatagain/）。");
        structureDimensionId = cfgInt(CAT_STRUCT_LAB, "structureDimensionId", DEF_STRUCTURE_DIM, -2147483648, 2147483647,
                "生成维度 ID（0=主世界，-1=下界）。");
        structureGridChunks = cfgInt(CAT_STRUCT_LAB, "structureGridChunks", DEF_STRUCTURE_GRID, 4, 64,
                "自定义网格间距（区块，每 N×N 区块生成一处）。");
        structureGridInset = cfgInt(CAT_STRUCT_LAB, "structureGridInset", DEF_STRUCTURE_GRID_INSET, 0, 16,
                "网格内落点四边留边（区块，防相邻结构重叠）。");
        structureMinHeight = cfgInt(CAT_STRUCT_LAB, "structureMinHeight", DEF_STRUCTURE_MIN_Y, 0, 250,
                "底座高度下限（与上限相等即固定层位）。");
        structureMaxHeight = cfgInt(CAT_STRUCT_LAB, "structureMaxHeight", DEF_STRUCTURE_MAX_Y, 0, 250,
                "底座高度上限。");
        structureHeightOffset = cfgInt(CAT_STRUCT_LAB, "structureHeightOffset", DEF_STRUCTURE_HEIGHT_OFFSET, -32, 32,
                "相对地表高度偏移（-1=地板与地面齐平）。");
        structureMaxHeightVariation = cfgFloat(CAT_STRUCT_LAB, "structureMaxHeightVariation", DEF_STRUCTURE_MAX_VARIATION, 0.0F, 2.0F,
                "允许的地形起伏上限（BiomeGenBase.heightVariation）。");
        structureAllowOcean = cfgBool(CAT_STRUCT_LAB, "structureAllowOcean", DEF_STRUCTURE_ALLOW_OCEAN,
                "是否允许在海洋/水域群系生成。");
        structureSpawnWeight = cfgInt(CAT_STRUCT_LAB, "structureSpawnWeight", DEF_STRUCTURE_WEIGHT, 1, 100,
                "结构权重（自定义网格模式下仅作预留）。");

        // ---------------- 08. 遗迹：大型异虫巢 ----------------
        structureAlienHiveEnabled = cfgBool(CAT_STRUCT_HIVE, "enableStructureAlienHive", DEF_ALIEN_HIVE_ENABLED,
                "是否生成大型异虫巢。");
        structureAlienHiveNbtFile = cfgStr(CAT_STRUCT_HIVE, "structureAlienHiveNbtFile", DEF_ALIEN_HIVE_NBT,
                "结构 NBT 路径（相对 assets/makentmgreatagain/）。");
        structureAlienHiveDimensionId = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveDimensionId", DEF_ALIEN_HIVE_DIM,
                -2147483648, 2147483647,
                "生成维度 ID（默认 0=主世界）。");
        structureAlienHiveRarityMultiplier = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveRarityMultiplier", DEF_ALIEN_HIVE_RARITY_MULT,
                1, 1000,
                "相对 HBM 小虫巢的稀有倍数（仅 Denominator=-1 时生效）。");
        structureAlienHiveDenominator = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveDenominator", DEF_ALIEN_HIVE_DENOMINATOR,
                -1, 1000000,
                "直接指定每 N 区块生成一座（-1=跟随 HBM hiveSpawn×倍数）。");
        structureAlienHiveHeightOffset = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveHeightOffset", DEF_ALIEN_HIVE_HEIGHT_OFFSET,
                -32, 32,
                "结构几何中心相对地表的高度偏移（格）。");
        structureAlienHiveMinHeight = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveMinHeight", DEF_ALIEN_HIVE_MIN_Y, 0, 250,
                "底座高度下限。");
        structureAlienHiveMaxHeight = cfgInt(CAT_STRUCT_HIVE, "structureAlienHiveMaxHeight", DEF_ALIEN_HIVE_MAX_Y, 0, 250,
                "底座高度上限。");
        structureAlienHiveMaxHeightVariation = cfgFloat(CAT_STRUCT_HIVE, "structureAlienHiveMaxHeightVariation",
                DEF_ALIEN_HIVE_MAX_VARIATION, 0.0F, 2.0F,
                "允许的地形起伏上限（2.0=不限）。");
        structureAlienHiveAllowOcean = cfgBool(CAT_STRUCT_HIVE, "structureAlienHiveAllowOcean", DEF_ALIEN_HIVE_ALLOW_OCEAN,
                "是否允许在海洋群系生成。");
        structureAlienHiveFollowHbm = cfgBool(CAT_STRUCT_HIVE, "structureAlienHiveFollowHbm", DEF_ALIEN_HIVE_FOLLOW_HBM,
                "是否跟随 HBM 的地牢与虫巢总开关。");
        structureAlienHiveCarveInterior = cfgBool(CAT_STRUCT_HIVE, "structureAlienHiveCarveInterior", DEF_ALIEN_HIVE_CARVE,
                "生成后是否挖空封闭内腔里的自然地形。");

        // ---------------- 09. 遗迹：日落六号 ----------------
        structureSunsetSixEnabled = cfgBool(CAT_STRUCT_SUNSET_SIX, "enableStructureSunsetSix", DEF_SUNSET_SIX_ENABLED,
                "是否生成日落六号（每世界仅一个，埋于下界岩浆中）。");
        structureSunsetSixNbtFile = cfgStr(CAT_STRUCT_SUNSET_SIX, "structureSunsetSixNbtFile", DEF_SUNSET_SIX_NBT,
                "结构 NBT 路径（相对 assets/makentmgreatagain/）。");
        structureSunsetSixDimensionId = cfgInt(CAT_STRUCT_SUNSET_SIX, "structureSunsetSixDimensionId", DEF_SUNSET_SIX_DIM,
                -2147483648, 2147483647,
                "生成维度 ID（默认 -1=下界）。");
        structureSunsetSixMinHeight = cfgInt(CAT_STRUCT_SUNSET_SIX, "structureSunsetSixMinHeight", DEF_SUNSET_SIX_MIN_Y, 0, 250,
                "底座高度下限。");
        structureSunsetSixMaxHeight = cfgInt(CAT_STRUCT_SUNSET_SIX, "structureSunsetSixMaxHeight", DEF_SUNSET_SIX_MAX_Y, 0, 250,
                "底座高度上限。");

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

        config.save();   // 首次启动时确保文件落盘
    }

    // =====================================================================================
    //  小工具
    // =====================================================================================

    private static int cfgInt(String cat, String key, int def, int min, int max, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getInt(key, cat, def, min, max, comment);
    }

    private static float cfgFloat(String cat, String key, float def, float min, float max, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getFloat(key, cat, def, min, max, comment);
    }

    private static boolean cfgBool(String cat, String key, boolean def, String comment) {
        DEFAULTS.put(key, String.valueOf(def));
        return config.getBoolean(key, cat, def, comment);
    }

    /** 字符串项。空串/全空白一律退回默认值，免得有人在 cfg 里留一行空的把结构关掉。 */
    private static String cfgStr(String cat, String key, String def, String comment) {
        DEFAULTS.put(key, def == null ? "" : def);
        String value = config.getString(key, cat, def, comment);
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

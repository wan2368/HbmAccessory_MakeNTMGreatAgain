package com.makentmgreatagain.config;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;

/**
 * 模组配置。
 *
 * <p>文件位置：config/MNGA/worldgen.cfg（首次启动自动生成）。</p>
 *
 * <h3>默认值只有一个出处</h3>
 * <p>所有默认值都写成本文件顶部的 {@code DEF_*} 常量，字段初始值和 {@code getInt} 的
 * 默认参数两处<b>都引用常量</b>，不存在「代码里改了一个数、配置里还写着另一个数」的情况。
 * 想调默认值，只改 {@code DEF_*} 那一行。</p>
 *
 * <h3>改了默认值不用再手动改配置文件</h3>
 * <p>Forge 的 Configuration 有个老毛病：只要配置文件里已经有这个键，代码里的默认值就永远
 * 不起作用 —— 所以以前每次调完参数都得手动去 cfg 里改一遍，或者干脆把文件删了重建。</p>
 *
 * <p>这里加了一个 {@link #CONFIG_VERSION}：</p>
 * <ol>
 *   <li>改了任何一个 {@code DEF_*}，把 {@code CONFIG_VERSION} 加 1；</li>
 *   <li>下次启动时发现文件里记的版本对不上，就把整份世界生成配置<b>丢掉重建</b>，
 *       所有项立刻回到源码里的默认值；</li>
 *   <li>重建完文件里的版本号等同于源码版本，之后你手改的值会正常保留，
 *       直到下一次版本号变动。</li>
 * </ol>
 * <p>换句话说：<b>版本号没动 = 你的手改有效；版本号动了 = 一次性的强制重置。</b>
 * 如果你不想被重置，把 cfg 里的 {@code I:configVersion} 改成和源码一致即可跳过。</p>
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
    public static final int CONFIG_VERSION = 2;

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

    // =====================================================================================
    //  运行时字段（初始值也取自上面，保证两者永不脱节）
    // =====================================================================================

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

    private static Configuration config;

    private ModConfig() {
    }

    /** 在 preInit 调用，event.getModConfigurationDirectory() 传进来。 */
    public static void init(File configDir) {

        File dir = new File(configDir, "MNGA");
        if(!dir.exists() && !dir.mkdirs()) {
            dir = configDir; // 极端情况下退化为 config 根目录
        }

        File file = new File(dir, "worldgen.cfg");

        // 版本对不上就整份丢掉重建：Forge 的 Configuration 一旦发现键已存在，
        // 代码里的默认值就再也不起作用，所以只能从文件层面清掉，没有别的办法。
        int fileVersion = readVersion(file);
        boolean rebuilt = false;
        if(file.exists() && fileVersion != CONFIG_VERSION) {
            rebuilt = file.delete();
            LOG.info("worldgen.cfg version " + fileVersion + " -> " + CONFIG_VERSION
                    + ", rebuilding with source defaults (deleted=" + rebuilt + ")");
        }

        config = new Configuration(file);
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
                "丘陵地表每列砾石变成盐砾石的概率（%）。");
        caveGravelPercent = cfgInt("caveGravelPercent", DEF_CAVE_GRAVEL, 0, 100,
                "丘陵/峡谷里裸露砾石变成盐砾石的概率（%）。");

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

        // 版本号最后写，写的是源码里的版本 —— 于是本次启动之后手改的值会一直保留到下次升版
        cfgInt(VERSION_KEY, CONFIG_VERSION, 0, Integer.MAX_VALUE,
                "配置版本号（由代码写入，请勿手改）。\n"
                + "源码里的 CONFIG_VERSION 一旦变化，本文件会被整份重建、所有项回到默认值。\n"
                + "想保留自己的改动，就在启动前把这里改成和源码一致的数字。");

        config.save();   // 首次启动时确保文件落盘

        LOG.info("MNGA worldgen config v" + CONFIG_VERSION + (rebuilt ? " (rebuilt)" : "")
                + " -> " + file.getAbsolutePath());
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
    }

    // =====================================================================================
    //  小工具
    // =====================================================================================

    private static int cfgInt(String key, int def, int min, int max, String comment) {
        return config.getInt(key, CAT, def, min, max, comment);
    }

    private static boolean cfgBool(String key, boolean def, String comment) {
        return config.getBoolean(key, CAT, def, comment);
    }

    /**
     * 直接从 cfg 文本里读版本号，不经过 Forge 的 Configuration。
     *
     * <p>必须在 {@code new Configuration(...)} 之前读 —— 因为读过一次之后 Forge 就会把
     * 默认值和已有值混在一起，再也分不清文件里原本写的是几了。</p>
     *
     * <p>Forge 写出来的是 {@code I:configVersion=2}（类型前缀 + 冒号），
     * 所以两种写法都要认。文件不存在或读不出来一律返回 -1，走「重建」分支。</p>
     */
    private static int readVersion(File file) {
        if(!file.isFile()) return -1;

        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            props.load(in);
        } catch(Exception e) {
            LOG.warn("Could not read " + file.getName() + ": " + e);
            return -1;
        } finally {
            if(in != null) {
                try { in.close(); } catch(Exception ignored) { }
            }
        }

        String raw = props.getProperty("I:" + VERSION_KEY);
        if(raw == null) raw = props.getProperty(VERSION_KEY);
        if(raw == null) return -1;

        try {
            return Integer.parseInt(raw.trim());
        } catch(NumberFormatException e) {
            return -1;
        }
    }
}

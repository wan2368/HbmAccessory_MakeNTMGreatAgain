package com.makentmgreatagain.config;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.config.Configuration;

/**
 * 全局玩法配置，文件在 config/MNGA/global.cfg（首次启动自动生成）。
 *
 * <p>放「跨系统」的玩法开关：不属于世界生成（worldgen.cfg）也不属于某个机器的
 * 全局性选项。以后新增全局开关一律进这里，不要再往 worldgen.cfg 里塞 gameplay
 * 分类。</p>
 *
 * <p>longProcessMode 旧版本曾寄居在 worldgen.cfg 的 {@code gameplay} 分类，
 * 现已迁到本文件；worldgen.cfg 里的残留键不会被读取（无害），
 * 想开长流程模式请在本文件里设置。</p>
 */
public final class GlobalConfig {

    private static final Logger LOG = LogManager.getLogger("MNGA-Global");

    private static final String CAT_GAMEPLAY = "gameplay";
    private static final String CAT_UPDATE = "update";

    /** 默认值唯一出处。改默认值时注意同步字段初始值（同一个常量，不会脱节）。 */
    public static final boolean DEF_LONG_PROCESS_MODE = false;
    /** 默认值唯一出处。 */
    public static final boolean DEF_PRESERVE_CONFIG_ON_UPDATE = false;

    /**
     * 长流程模式。
     * 开启后所有维度不再自然生成基岩矿石（浮冰基岩矿除外）；默认关。
     * 判定入口是 {@code LongProcessHook.allowBedrockOre}，由 coremod 注入到
     * {@code BedrockOre} 的放置点上；关掉时那个方法直接返回 true，等于不干预。
     */
    public static boolean longProcessMode = DEF_LONG_PROCESS_MODE;

    /**
     * 配置保护开关（世界生成配置 worldgen.cfg 专用）。
     * <p>true：附属更新后，如果 worldgen.cfg 相对上一版「只有新增键」（旧键没删、
     * 默认值没变），就保留玩家手调值，新键自动补进文件。</p>
     * <p>false（默认）：附属更新升版本 → worldgen.cfg 整份重建，全部回模组新默认
     * （旧文件备份成 .bak）。</p>
     */
    public static boolean preserveConfigOnUpdate = DEF_PRESERVE_CONFIG_ON_UPDATE;

    private static Configuration config;

    private GlobalConfig() {
    }

    /** 在 preInit 调用，与 ModConfig.init 同目录（config/MNGA/）。 */
    public static void init(File configDir) {

        File dir = new File(configDir, "MNGA");
        if(!dir.exists() && !dir.mkdirs()) {
            dir = configDir; // 极端情况下退化为 config 根目录
        }

        File file = new File(dir, "global.cfg");

        config = new Configuration(file);
        config.load();

        longProcessMode = config.getBoolean("longProcessMode", CAT_GAMEPLAY, DEF_LONG_PROCESS_MODE,
                "长流程模式。默认 false（关）。\n"
                + "开启后，所有世界（含航天版各星球）都不再自然生成基岩矿石，\n"
                + "唯一例外是航天版星球的浮冰基岩矿，照常生成。\n"
                + "关掉时不做任何干预，基岩矿按 HBM 原版规则生成。\n"
                + "只对新生成的区块生效，已经生成的基岩矿不会被清除。\n"
                + "实现方式：coremod 在 BedrockOre 放置 ore_bedrock 的那一处插闸门，\n"
                + "所以地球、下界、所有星球一次全覆盖，不需要逐个维度改。\n"
                + "注意：本选项旧版本在 worldgen.cfg 的 gameplay 分类，已迁到本文件，\n"
                + "旧文件里的设置不会被读取，需要在这里重新打开。");

        preserveConfigOnUpdate = config.getBoolean("preserveConfigOnUpdate", CAT_UPDATE, DEF_PRESERVE_CONFIG_ON_UPDATE,
                "配置保护开关（只作用于 worldgen.cfg）。默认 false。\n"
                + "false：附属模组更新、配置版本号升上去之后，worldgen.cfg 整份重建、\n"
                + "    所有项回到模组新默认（旧文件会备份成 worldgen.cfg.vN.bak）。\n"
                + "true：启动时对比「本版本默认值快照 vs 上一版快照」，如果差异只有新增键\n"
                + "    （旧键一个没删、默认值一个没变），就保留你的手调值不重建；\n"
                + "    新增的键会自动补进文件。一旦发现旧键被移除或默认值被改动，\n"
                + "    就视为不满足条件，照旧整份重建（有备份）。\n"
                + "快照存在同目录的 worldgen.defaults.txt，删掉它 = 下次升版按 false 处理。\n"
                + "configVersion 键本身不参与对比（它就是触发器）。\n"
                + "注意：本开关只管 worldgen.cfg；配方玩家的改动由配方账本\n"
                + "（config/MNGA/recipe_ledger.json）单独按条目保护，与这里无关。");

        config.save();   // 首次启动时确保文件落盘

        LOG.info("MNGA global config -> " + file.getAbsolutePath()
                + ", longProcessMode=" + longProcessMode
                + (longProcessMode ? " (bedrock ores suppressed; packed-ice bedrock ore exempt)" : " (vanilla bedrock ore behavior)")
                + ", preserveConfigOnUpdate=" + preserveConfigOnUpdate);
    }
}

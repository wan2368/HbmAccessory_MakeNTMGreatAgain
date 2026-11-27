package com.makentmgreatagain.config;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.hbm.config.GeneralConfig;

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
    private static final String CAT_DEBUG = "debug";

    /** 默认值唯一出处。改默认值时注意同步字段初始值（同一个常量，不会脱节）。 */
    public static final boolean DEF_LONG_PROCESS_MODE = false;
    /** 默认值唯一出处。 */
    public static final boolean DEF_PRESERVE_CONFIG_ON_UPDATE = false;
    /** 日落六号测试模式默认值。 */
    public static final boolean DEF_SUNSET_SIX_TEST_MODE = false;
    /** 日落六号测试模式底座高度默认值。 */
    public static final int DEF_SUNSET_SIX_TEST_BASE_Y = 2;

    /**
     * 长流程模式。
     * 开启后所有维度不再自然生成基岩矿石（浮冰基岩矿除外）；默认关。
     */
    public static boolean longProcessMode = DEF_LONG_PROCESS_MODE;

    /**
     * 配置保护开关（世界生成配置 worldgen.cfg 专用）。
     */
    public static boolean preserveConfigOnUpdate = DEF_PRESERVE_CONFIG_ON_UPDATE;

    /** 日落六号测试模式：固定刷在 (0,0) 并跳过岩浆深度校验。 */
    public static boolean structureSunsetSixTestMode = DEF_SUNSET_SIX_TEST_MODE;
    /** 日落六号测试模式底座高度。 */
    public static int structureSunsetSixTestBaseY = DEF_SUNSET_SIX_TEST_BASE_Y;

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
                + "注意：若 HBM 本体开启了 528 模式（enable528=true），附属将强制开启长流程模式，\n"
                + "此时修改本选项无效。");

        preserveConfigOnUpdate = config.getBoolean("preserveConfigOnUpdate", CAT_UPDATE, DEF_PRESERVE_CONFIG_ON_UPDATE,
                "配置保护开关（仅 worldgen.cfg）：true 且升级仅有新增键时保留玩家手调值；false 则升版重建并备份。");

        structureSunsetSixTestMode = config.getBoolean("structureSunsetSixTestMode", CAT_DEBUG, DEF_SUNSET_SIX_TEST_MODE,
                "日落六号测试模式：开启后固定刷在 (0,0) 区块并跳过岩浆深度校验（仍受每世界仅一个限制）。");
        structureSunsetSixTestBaseY = config.getInt("structureSunsetSixTestBaseY", CAT_DEBUG, DEF_SUNSET_SIX_TEST_BASE_Y, 1, 200,
                "日落六号测试模式的底座高度（仅测试模式开启时生效）。");

        config.save();   // 首次启动时确保文件落盘

        boolean forcedBy528 = GeneralConfig.enable528;
        if(forcedBy528) {
            longProcessMode = true;
        }

        LOG.info("MNGA global config -> " + file.getAbsolutePath()
                + ", longProcessMode=" + longProcessMode
                + (forcedBy528 ? " (FORCED by HBM 528 mode; global.cfg longProcessMode ignored)"
                        : (longProcessMode ? " (bedrock ores suppressed; packed-ice bedrock ore exempt)" : " (vanilla bedrock ore behavior)"))
                + ", preserveConfigOnUpdate=" + preserveConfigOnUpdate);
    }
}

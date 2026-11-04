package com.makentmgreatagain.init;

/**
 * 已废弃：本模组的成就已整体迁到 com.makentmgreatagain.achievement 包，
 * 不再注册 Minecraft 原生成就（不进原生成就页、不占 StatList）。
 *
 * 文件保留为空壳，只是为了防止旧版本升级上来的玩家的整合包脚本或
 * 其它模组的硬引用在这里直接 NoClassDefFoundError。新代码不要再引用它。
 *
 * @deprecated 改用 {@link com.makentmgreatagain.achievement.AchManager}
 */
@Deprecated
public class ModAchievements {
}

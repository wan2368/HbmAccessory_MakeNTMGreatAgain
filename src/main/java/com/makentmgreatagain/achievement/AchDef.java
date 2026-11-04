package com.makentmgreatagain.achievement;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * 一条成就的静态定义。
 *
 * 本模组的成就完全不走 Minecraft 原生的 Achievement / StatList，
 * 因此这里只是一个纯数据对象：id、分类、图标和 GUI 里的行列位置。
 * 名字与描述都从语言文件取，key 形如 mntga.ach.&lt;id&gt;.title / .desc。
 */
public class AchDef {

    /** 稳定 id：一旦发布就不能改，存档里存的就是它 */
    public final String id;

    /** 分类，决定 GUI 里的排布与分隔 */
    public final EnumAchCategory category;

    /** GUI 网格列 / 行（从 0 开始，分类内部坐标） */
    public final int col;
    public final int row;

    /** 图标。null 时 GUI 画一个占位问号 */
    private final ItemStack icon;

    public AchDef(String id, EnumAchCategory category, int col, int row, ItemStack icon) {
        this.id = id;
        this.category = category;
        this.col = col;
        this.row = row;
        this.icon = icon;
    }

    public ItemStack getIcon() {
        return icon;
    }

    public String getTitle() {
        return StatCollector.translateToLocal("mntga.ach." + id + ".title");
    }

    public String getDesc() {
        return StatCollector.translateToLocal("mntga.ach." + id + ".desc");
    }

    public enum EnumAchCategory {

        /** 白钨矿湿法炼钨 */
        TUNGSTEN("tungsten"),
        /** 海水与冶金 */
        INDUSTRY("industry"),
        /** 杂项与彩蛋 */
        MISC("misc");

        public final String key;

        EnumAchCategory(String key) {
            this.key = key;
        }

        public String getTitle() {
            return StatCollector.translateToLocal("mntga.ach.cat." + key);
        }
    }
}

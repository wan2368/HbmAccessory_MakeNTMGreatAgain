package com.makentmgreatagain.achievement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.makentmgreatagain.achievement.AchDef.EnumAchCategory;
import com.makentmgreatagain.init.ModBlocks;
import com.makentmgreatagain.init.ModFluids;

import net.minecraft.item.ItemStack;

/**
 * 成就总表。
 *
 * 全部 15 条在这里一次性声明，id 一经发布不要再改——玩家存档里存的是 id 字符串。
 * 图标在 init() 里构建，必须等物品和方块都注册完（CommonProxy.init 阶段）。
 */
public class AchRegistry {

    private static final Map<String, AchDef> BY_ID = new LinkedHashMap<String, AchDef>();
    private static final List<AchDef> ALL = new ArrayList<AchDef>();

    /* ---- id 常量：触发点太多，集中在这里免得手打错字 ---- */
    public static final String SCHEELITE_MINED = "scheelite_mined";
    public static final String SCHEELITE_ACID  = "scheelite_acid";
    public static final String BERYLLIUM_CONC  = "beryllium_conc";
    public static final String ARSENIC_CAKE    = "arsenic_cake";
    public static final String ARSENIC_NUGGET  = "arsenic_nugget";
    public static final String NOTHING_SMELT   = "nothing_smelt";
    public static final String WASTE_ACID      = "waste_acid";
    public static final String SCHEELITE_300   = "scheelite_300";
    public static final String TUNGSTEN_CARBIDE = "tungsten_carbide";
    public static final String SEAWATER        = "seawater";
    public static final String CHLORINE        = "chlorine";
    public static final String LITHIUM         = "lithium";
    public static final String ZINC            = "zinc";
    public static final String HOVER           = "hover";
    public static final String ESCAPE_ABYSS    = "escape_abyss";

    private static boolean initialized = false;

    public static void init() {

        if (initialized) return;
        initialized = true;

        // ---- 白钨矿湿法（3 x 3）----
        add(SCHEELITE_MINED, EnumAchCategory.TUNGSTEN, 0, 0, icon(ModBlocks.ore_scheelite));
        add(SCHEELITE_ACID, EnumAchCategory.TUNGSTEN, 1, 0, item(com.makentmgreatagain.init.ModItems.tungstic_acid));
        add(BERYLLIUM_CONC, EnumAchCategory.TUNGSTEN, 2, 0, item(com.makentmgreatagain.init.ModItems.beryllium_concentrate));
        add(ARSENIC_CAKE, EnumAchCategory.TUNGSTEN, 0, 1, item(com.makentmgreatagain.init.ModItems.arsenic_trioxide));
        add(ARSENIC_NUGGET, EnumAchCategory.TUNGSTEN, 1, 1, item(com.hbm.items.ModItems.nugget_arsenic));
        add(NOTHING_SMELT, EnumAchCategory.TUNGSTEN, 2, 1, item(com.hbm.items.ModItems.nothing));
        add(WASTE_ACID, EnumAchCategory.TUNGSTEN, 0, 2, tank(ModFluids.WASTE_ACID.id));
        add(SCHEELITE_300, EnumAchCategory.TUNGSTEN, 1, 2, icon(ModBlocks.ore_scheelite));
        add(TUNGSTEN_CARBIDE, EnumAchCategory.TUNGSTEN, 2, 2, item(com.hbm.items.ModItems.ingot_tungsten_carbide));

        // ---- 海水与冶金（2 x 2）----
        add(SEAWATER, EnumAchCategory.INDUSTRY, 0, 0, icon(ModBlocks.environment_pump));
        add(CHLORINE, EnumAchCategory.INDUSTRY, 1, 0, tank(com.hbm.inventory.fluid.Fluids.CHLORINE.getID()));
        add(LITHIUM, EnumAchCategory.INDUSTRY, 0, 1, item(com.hbm.items.ModItems.powder_lithium));
        add(ZINC, EnumAchCategory.INDUSTRY, 1, 1, item(com.makentmgreatagain.init.ModItems.powder_zinc_isp));

        // ---- 杂项（2 x 1）----
        add(HOVER, EnumAchCategory.MISC, 0, 0, item(com.makentmgreatagain.init.ModItems.hover_jetpack));
        add(ESCAPE_ABYSS, EnumAchCategory.MISC, 1, 0, item(com.makentmgreatagain.init.ModItems.cigarette_drx));
    }

    private static void add(String id, EnumAchCategory cat, int col, int row, ItemStack icon) {
        AchDef def = new AchDef(id, cat, col, row, icon);
        BY_ID.put(id, def);
        ALL.add(def);
    }

    private static ItemStack item(net.minecraft.item.Item it) {
        return it == null ? null : new ItemStack(it);
    }

    private static ItemStack icon(net.minecraft.block.Block block) {
        return block == null ? null : new ItemStack(block);
    }

    /** 流体罐图标：HBM 的 fluid_tank_full 用 meta 存流体 id */
    private static ItemStack tank(int fluidId) {
        if (com.hbm.items.ModItems.fluid_tank_full == null) return null;
        return new ItemStack(com.hbm.items.ModItems.fluid_tank_full, 1, fluidId);
    }

    public static AchDef get(String id) {
        return BY_ID.get(id);
    }

    public static List<AchDef> all() {
        return ALL;
    }

    public static int size() {
        return ALL.size();
    }

    /** 某一分类下的全部成就，按行优先排序 */
    public static List<AchDef> byCategory(EnumAchCategory cat) {

        List<AchDef> list = new ArrayList<AchDef>();
        for (AchDef def : ALL) {
            if (def.category == cat) list.add(def);
        }
        java.util.Collections.sort(list, new java.util.Comparator<AchDef>() {
            public int compare(AchDef a, AchDef b) {
                if (a.row != b.row) return a.row - b.row;
                return a.col - b.col;
            }
        });
        return list;
    }
}

package com.makentmgreatagain.init;

import net.minecraft.item.Item;
import net.minecraft.item.Item.ToolMaterial;
import net.minecraft.item.ItemArmor.ArmorMaterial;
import net.minecraftforge.common.util.EnumHelper;

public class MNGAMaterials {

    /** 高级合金全系工具的耐久。HBM 原版 tMatAlloy 是 2000，这里统一提到 4500。 */
    public static final int ALLOY_DURABILITY = 4500;

    /** 工具材质：采掘等级 / 效率 / 伤害加成 / 附魔性与 HBM 的 tMatAlloy 一致，只把耐久提到 {@link #ALLOY_DURABILITY} */
    public static final ToolMaterial TOOL_ALLOY =
            EnumHelper.addToolMaterial("MNGA_ALLOY", 3, ALLOY_DURABILITY, 15.0F, 5.0F, 5);

    /**
     * 把 HBM 遗留的高级合金五件套耐久从 2000 提到 {@link #ALLOY_DURABILITY}。
     *
     * <p><b>为什么不直接改本体的 {@code tMatAlloy}</b>：ToolMaterial 的 maxUses 只在物品
     * 构造那一刻写进 {@code Item.maxDamage}，改材质影响不到已经建好的物品；而且发布附属时
     * {@code buildAddonJar} 只打 {@code com/makentmgreatagain/**}，改 com/hbm 的源码不会
     * 进附属 jar —— 结果是开发环境（build/libs 那个含本体的整包）能看到 4500、发布包里
     * 还是 2000。这里在 postInit 直接覆盖物品字段，两种环境行为一致。</p>
     *
     * <p>调用时机：必须晚于 HBM 的 {@code ModItems} 建物品（HBM 在 preInit 里做），
     * 因此放在 {@code CommonProxy.postInit()}。只改 maxDamage，不碰 Damage 值，
     * 老存档里用旧的物品会平白多出 2500 点耐久余量，属预期。</p>
     */
    public static void raiseAlloyToolDurability() {
        // 用全限定名：本包也有一个 ModItems，简单名会解析到附属那个。
        // 逐个判 null：这几个字段是 @Deprecated，HBM 哪天删物品时不该连带把启动搞崩。
        Item[] tools = {
                com.hbm.items.ModItems.alloy_pickaxe,
                com.hbm.items.ModItems.alloy_axe,
                com.hbm.items.ModItems.alloy_shovel,
                com.hbm.items.ModItems.alloy_hoe,
                com.hbm.items.ModItems.alloy_sword,
        };
        for (Item tool : tools) {
            if (tool != null) tool.setMaxDamage(ALLOY_DURABILITY);
        }
    }

    /**
     * 耐摔合金工具材质（镐/斧共用）。
     *
     * <p>参数照 HBM 的 tMatMESE：harvestLevel 5、maxUses 0（0 = 永不掉耐久）、
     * efficiency 150、enchantability 200。
     *
     * <p><b>名字必须自己起一个</b>，不能复用 {@code "HBM_MESE"}：Forge 1.7.10 的
     * {@code EnumHelper.addEnum} 不查重，同名的第二次调用会再造一个影子常量追加进
     * {@code ToolMaterial.$VALUES}，{@code ToolMaterial.valueOf("HBM_MESE")} 却仍返回
     * HBM 本体那个（ordinal 更小），于是"明明写了 5 级却是 4 级"这类鬼故事就出现了。
     */
    public static final ToolMaterial TOOL_HELICOPTER =
            EnumHelper.addToolMaterial("MNGA_HELICOPTER", 5, 0, 150.0F, 0.0F, 200);

    /** 护甲材质：参数与 HBM 的 aMatAlloy 完全一致 */
    public static final ArmorMaterial ARMOR_ALLOY =
            EnumHelper.addArmorMaterial("MNGA_ALLOY", 40, new int[] { 3, 8, 6, 3 }, 12);
	/** 护甲材质：参数与 HBM 的 aMatAlloy 完全一致 */
	public static final ArmorMaterial ARMOR_CT=
		EnumHelper.addArmorMaterial("MNGA_CT", 40, new int[] { 3, 8, 6, 3 }, 12);
	/** 耐摔合金护甲材质：护甲点与耐久沿用同档 {3,8,6,3}；套装免伤由 HelicopterArmorHandler 判定这个实例 */
	public static final ArmorMaterial ARMOR_HELICOPTER =
		EnumHelper.addArmorMaterial("MNGA_HELICOPTER_ARMOR", 40, new int[] { 3, 8, 6, 3 }, 12);
}

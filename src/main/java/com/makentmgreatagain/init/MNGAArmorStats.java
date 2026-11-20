package com.makentmgreatagain.init;

import com.hbm.handler.HazmatRegistry;
import com.hbm.util.DamageResistanceHandler;
import com.hbm.util.DamageResistanceHandler.ResistanceStats;

import net.minecraft.util.DamageSource;

/**
 * 护甲的「数值型」属性表：单件辐射抗性 + 单件/全套 DT、DR。
 *
 * <p>这些数不能写在 {@code ItemArmorAlloyReborn} 上，统一在这里注册。
 * 辐射抗性也不要再用 {@code ArmorFSB.setRadResist()}：那条走 HazmatRegistry.external，
 * 与本类的直注册写同一批物品，实际生效值取决于 HBM postInit 与本站的先后。
 * 新增一套护甲照抄 {@link #registerCobaltTungsten()}，换物品字段与数值即可。</p>
 *
 * <h3>别把「辐射抗性」和「放射源」搞混（HBM 里是两套系统）</h3>
 * <ul>
 *   <li><b>抗性</b>：{@code HazmatRegistry.registerHazmat(item, res)}（{@code ArmorFSB.setRadResist}
 *       只是它的护甲分部位包装）。{@code res} 是指数不是百分比，
 *       受辐系数 = {@code 10^(-res)}：1 → 减 90%，2 → 减 99%，10 ≈ 全免。
 *       <b>只有四个护甲槽会累加</b>（{@code HazmatRegistry.getResistance(EntityPlayer)}
 *       遍历 {@code armorInventory}），普通物品注册了只影响 tooltip。
 *       tooltip 自动显示「辐射抗性: xxx」。</li>
 *   <li><b>放射源</b>：{@code HazardSystem.register(item, new HazardData().addEntry(RADIATION, level))}。
 *       携带时每 tick 自动辐照，tooltip 自动显示绿字 {@code [放射性]} + 黄字 {@code N RAD/s}
 *       —— 原版铀锭、钋插件就是这条。详见 {@code ItemRadioactiveConverter}。</li>
 * </ul>
 * <p>顺带：HBM 里没有 {@code Item.setResistance(...)}，那个是 {@code Block} 的抗爆值。</p>
 */
public final class MNGAArmorStats {

    private MNGAArmorStats() { }

    /**
     * 全套辐射抗性系数，减伤 = 1 - 10^(-res)：0.75 → 82.2%，介于黄防化服 0.6 与红防化服 1.0 之间。
     * 全模组只此一处来源：不要在 ModItems 里再叠 {@code ArmorFSB.setRadResist()}，
     * 那条路写的是 HazmatRegistry.external，和这里直注册同一批物品会互相覆盖、结果看加载顺序。
     */
    public static final double CT_RAD = 0.75D;

    /**
     * 耐摔合金全套辐射抗性：给到 1e6，换算 mod = 10^(-1e6) 严格为 0，即"一点辐射都不进"。
     * 远超 HBM 最高档 euphemium 的 5D(99.999%)。四件按部位权重拆，合计正好 1e6。
     */
    public static final double HELI_RAD = 1_000_000D;

    /** 由 CommonProxy.postInit 调用，只调用一次。 */
    public static void register() {
        registerCobaltTungsten();
        registerHelicopter();
    }

    /**
     * 耐摔合金全套：只灌辐射全免。
     *
     * <p>不注册任何 DT/DR —— 本套的免伤是在 {@code HelicopterArmorHandler} 里
     * 对 LivingAttackEvent 全源取消实现的，比减伤更彻底；这里若再叠 DamageResistanceHandler
     * 的 other/exact 减伤，会给"摔落照常结算"错误地加上减免，与需求（摔落减免为 0）冲突。</p>
     *
     * <p>辐射走本类直灌 {@code registerHazmat}，与碳化钨同一条路；
     * 别在 ModItems 上用 {@code ArmorFSB.setRadResist}，那条走 external，
     * 会被玩家版 hbmRadResist.json 的 entries.clear() 清掉（见类头注释）。</p>
     */
    private static void registerHelicopter() {

        HazmatRegistry.registerHazmat(ModItems.helicopter_helmet, HELI_RAD * HazmatRegistry.helmet);
        HazmatRegistry.registerHazmat(ModItems.helicopter_plate, HELI_RAD * HazmatRegistry.chest);
        HazmatRegistry.registerHazmat(ModItems.helicopter_legs, HELI_RAD * HazmatRegistry.legs);
        HazmatRegistry.registerHazmat(ModItems.helicopter_boots, HELI_RAD * HazmatRegistry.boots);
    }

    private static void registerCobaltTungsten() {

        // 单件：辐射抗性。按 HBM 部位权重 头.2 / 胸.4 / 腿.3 / 靴.1 拆分，四件齐全合计 = CT_RAD
        HazmatRegistry.registerHazmat(ModItems.cobalt_tungsten_helmet, CT_RAD * HazmatRegistry.helmet);
        HazmatRegistry.registerHazmat(ModItems.cobalt_tungsten_plate, CT_RAD * HazmatRegistry.chest);
        HazmatRegistry.registerHazmat(ModItems.cobalt_tungsten_legs, CT_RAD * HazmatRegistry.legs);
        HazmatRegistry.registerHazmat(ModItems.cobalt_tungsten_boots, CT_RAD * HazmatRegistry.boots);

        // 单件：DT / DR。碳化钨是硬质合金，抗动能不抗爆，所以只加 PHYS
        DamageResistanceHandler.itemStats.put(ModItems.cobalt_tungsten_helmet, new ResistanceStats()
                .addCategory(DamageResistanceHandler.CATEGORY_PHYSICAL, 1F, 0.05F));
        DamageResistanceHandler.itemStats.put(ModItems.cobalt_tungsten_plate, new ResistanceStats()
                .addCategory(DamageResistanceHandler.CATEGORY_PHYSICAL, 2F, 0.10F));
        DamageResistanceHandler.itemStats.put(ModItems.cobalt_tungsten_legs, new ResistanceStats()
                .addCategory(DamageResistanceHandler.CATEGORY_PHYSICAL, 1F, 0.10F));
        DamageResistanceHandler.itemStats.put(ModItems.cobalt_tungsten_boots, new ResistanceStats()
                .addCategory(DamageResistanceHandler.CATEGORY_PHYSICAL, 1F, 0.05F));

        // 全套：DT / DR。对照 HBM 档位（钢 2/0.10、星辰 3/0.25、防化 5/0.5），这里 PHYS 累计 7/0.40；
        // exact fall 是「重甲摔不死」，other 10% 是保底通用减伤
        DamageResistanceHandler.registerSet(
                ModItems.cobalt_tungsten_helmet,
                ModItems.cobalt_tungsten_plate,
                ModItems.cobalt_tungsten_legs,
                ModItems.cobalt_tungsten_boots,
                new ResistanceStats()
                        .addCategory(DamageResistanceHandler.CATEGORY_PHYSICAL, 4F, 0.45F)
                        .addCategory(DamageResistanceHandler.CATEGORY_EXPLOSION, 2F, 0.20F)
                        .addCategory(DamageResistanceHandler.CATEGORY_FIRE, 1F, 0.20F)
                        .addExact(DamageSource.fall.damageType, 3F, 0.15F)
                        .setOther(1F, 0.10F));
    }
}

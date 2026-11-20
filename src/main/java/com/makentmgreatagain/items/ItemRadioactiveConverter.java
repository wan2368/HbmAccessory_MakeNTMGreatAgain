package com.makentmgreatagain.items;

import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;

import net.minecraft.item.Item;

/**
 * 带放射性的催化转换器。
 *
 * <p>放射源走 {@code HazardSystem}，不是 {@code HazmatRegistry}（后者是<b>抗性</b>/防护）。
 * 注册一条 {@code HazardRegistry.RADIATION} 之后，HBM 自动做两件事：玩家背包每 tick 按其
 * {@code level / 20F} 辐照（即 level = RAD/s；箱子、机器槽、掉落物都不辐照），
 * 以及 tooltip 自动打出 {@code [放射性] N RAD/s}。</p>
 *
 * <p>因此<b>不要</b>再写 {@code onUpdate} / {@code addInformation}：会叠加成双倍剂量 + 重复提示。
 * 同款写法见 {@code HazardRegistry.registerItems()} 里的 {@code insert_polonium}。</p>
 */
public class ItemRadioactiveConverter extends Item {

    /** 每秒辐射剂量（RAD/s）。HazardSystem 的口径是「每 tick level/20」，故 level 直接等于 RAD/s。 */
    public static final float RAD_PER_SECOND = 380F;

    public ItemRadioactiveConverter() {
        this.setMaxStackSize(1);
    }

    /**
     * 把物品注册成放射源。必须在物品构造完成、{@code GameRegistry.registerItem} 之后调用。
     *
     * <p>{@code HazardSystem.itemMap} 是一张普通 HashMap，没有任何「重载时清表」的逻辑，
     * 所以注册早晚都不会丢；这里仍然放在 HBM 的 {@code HazardRegistry.registerItems()}
     * 之后（本模组声明了 {@code required-after:hbm}），避免被同 key 的默认项覆盖。</p>
     */
    public static void registerRadiationHazard(Item item, float radPerSecond) {
        HazardSystem.register(item, new HazardData().addEntry(HazardRegistry.RADIATION, radPerSecond));
    }
}

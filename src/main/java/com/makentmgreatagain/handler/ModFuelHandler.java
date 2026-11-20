package com.makentmgreatagain.handler;

import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.common.IFuelHandler;
import net.minecraft.item.ItemStack;

/**
 * 附属自己的燃料表。
 *
 * <p>HBM 的 FuelHandler 是硬编码白名单，不认识的物品直接返回 0，所以新增可燃物必须在这里补一行；
 * Forge 会合并所有 IFuelHandler 的结果（1.7.10 取最大值），HBM 返回 0 不影响本表。</p>
 *
 * <p>不要给这些物品挂 Coal / Coke 矿辞：HBM 的 {@code ModuleBurnTime.getMod()} 和
 * {@code getAshFromFuel()} 都按矿辞名做字符串匹配，挂上会额外吃到焦炭的时间倍率，
 * 灰渣也会记成煤灰而不是杂项灰。</p>
 *
 * <h3>新增一种可燃物（照抄）</h3>
 * <pre>
 * private static final int XXX_BURN_TIME = 3_200;
 * ...
 * if(fuel.getItem() == ModItems.xxx) return XXX_BURN_TIME;
 * </pre>
 */
public class ModFuelHandler implements IFuelHandler {

	/** 高纯炭黑：3200 tick（煤炭 2 倍）。按天然气 + 化工厂成本算约 3.0 MHE，回收不到一半，不构成循环。 */
	private static final int CARBON_BLACK_BURN_TIME = 3_200;

	/** 高级固态燃料：12000 tick，介于 solid_fuel_presto（8000）与 presto_triplet（40000）之间。 */
	private static final int ADV_SOLID_FUEL_BURN_TIME = 12_000;

	@Override
	public int getBurnTime(ItemStack fuel) {

		if(fuel == null || fuel.getItem() == null) return 0;

		if(fuel.getItem() == ModItems.carbon_black_pure) return CARBON_BLACK_BURN_TIME;
		if(fuel.getItem() == ModItems.solid_fuel_adv) return ADV_SOLID_FUEL_BURN_TIME;

		return 0;
	}
}

package com.makentmgreatagain.handler;

import com.makentmgreatagain.init.ModItems;

import cpw.mods.fml.common.IFuelHandler;
import net.minecraft.item.ItemStack;

/**
 * 附属模组自己的燃料表。
 *
 * HBM 的 FuelHandler（com.hbm.handler.FuelHandler）是一张硬编码白名单，遇到不认识的物品
 * 直接 return 0，所以附属新增的可燃物品必须在这里单独注册。Forge 会把所有已注册的
 * IFuelHandler 结果合并（1.7.10 取最大值），HBM 返回 0 不影响本表的数值。
 *
 * 注意：这里只按物品对象判断，不给物品挂 Coal / Coke 类矿辞。
 * HBM 的 ModuleBurnTime.getMod() 与 TileEntityFireboxBase.getAshFromFuel() 都是按矿辞名
 * 做字符串匹配的，一旦挂上 Coke，燃烧室会额外吃到焦炭的时间/热量倍率（默认 1.0，但可被
 * 服务器配置改写），灰渣也会记成煤灰而不是杂项灰。
 */
public class ModFuelHandler implements IFuelHandler {

	/**
	 * 高纯炭黑：3200 tick，与 HBM 焦炭（coke）、固体燃料（solid_fuel）同级，是原版煤炭的两倍，
	 * 一个物品可烧 16 次。
	 *
	 * 依据：炭黑是纯度 95% 以上的元素碳，低位热值 30~33 MJ/kg，与焦炭（29~31）同级、高于烟煤
	 * （24~27）；HBM 对粉末燃料从不打折（煤粉 1600 = 煤 1600，褐煤粉 1200 = 褐煤 1200）。
	 *
	 * 能量核算（防止形成循环）：1 个炭黑需要可燃冰泵抽出的天然气 2000（≈1.40 MHE）+ 化工厂
	 * 400t × 4000 HE/t（1.60 MHE），合计约 3.0 MHE；按 HBM 自己的换算（SolidificationRecipes
	 * 注释把 3200 燃烧时间记作 1.44 MTU），回收上限也只有成本的一半，不存在净收益。
	 */
	private static final int CARBON_BLACK_BURN_TIME = 3_200;

	/**
	 * 高级固态燃料：12000 tick（1 个可烧 10 分钟）。
	 *
	 * 定位介于 HBM 的 solid_fuel_presto（8000）与 solid_fuel_presto_triplet（40000）之间：
	 * 硝基甲烷自带氧、能量密度远高于普通固体燃料，但还没到 presto triplet 那种"三份 presto
	 * 加一管炸药"的复合装药级别。
	 *
	 * 成本核算：一条配方吃硝基甲烷 400（≈0.3 MHE 的燃值）+ 硝酸 100 + 固体燃料 2（6400 tick），
	 * 出 4 个共 48000 tick，与它替代的原版 rocket_fuel 路线（4 个共 25600 tick）同一量级，
	 * 但需要三期的硝基甲烷产能，不构成早期刷燃料的口子。
	 */
	private static final int ADV_SOLID_FUEL_BURN_TIME = 12_000;

	@Override
	public int getBurnTime(ItemStack fuel) {

		if(fuel == null || fuel.getItem() == null) return 0;

		if(fuel.getItem() == ModItems.carbon_black_pure) return CARBON_BLACK_BURN_TIME;
		if(fuel.getItem() == ModItems.solid_fuel_adv) return ADV_SOLID_FUEL_BURN_TIME;

		return 0;
	}
}

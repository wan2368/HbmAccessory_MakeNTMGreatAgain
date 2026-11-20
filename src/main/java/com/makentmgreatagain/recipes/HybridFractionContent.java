package com.makentmgreatagain.recipes;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.ItemEnums;
import com.hbm.items.ModItems;

import net.minecraft.item.ItemStack;

/**
 * 混合催化分馏塔的全部自定义配方（铂催化 30 条 + CMB 催化 3 条）。
 *
 * <p>由 {@link HybridFractionRecipes#addRecipes()} 在 preInit 阶段调用一次。
 * 表结构（匹配、扣料、出料罐分配）在 {@link HybridFractionRecipes} 与
 * {@code TileEntityHybridCatalyticFractionTower} 里，本类只放配方数据。</p>
 *
 * <h2>配方清单来源</h2>
 * <p>设计文档：{@code docs/混合催化分馏塔_配方总表.md}。编号（S3 / A1 / P1 / M4 …）
 * 与文档一一对应，改数值时两边要同步。</p>
 *
 * <h2>关于压力：本机只能用常压（重要）</h2>
 * <p>本机的两个进料罐在构造时是 {@code new FluidTank(Fluids.NONE, CAPACITY)}，
 * <b>没有</b> {@code withPressure(...)}，所以 {@code getPressure()} 恒为 0。而 HBM 的流体网络
 * 在 {@code IFluidStandardReceiverMK2#transferFluid} 里是按
 * {@code tank.getPressure() == pressure} <b>精确匹配</b>才注入的 ——
 * 罐压 0 意味着加压流体根本送不进来，{@code getDemand} 对加压请求也返回 0。</p>
 *
 * <p>对照 HBM 本体：加氢机是构造时 {@code new FluidTank(Fluids.HYDROGEN, 64_000).withPressure(1)}、
 * 减压塔是 {@code withPressure(2)} —— 原版机器<b>把压力写死在罐上</b>，玩家必须喂对应压力的流体。
 * 本机没有这层设定，所以下面所有配方一律按常压（第三参数省略）注册。</p>
 *
 * <p><b>不要</b>在这里写 {@code new FluidStack(type, fill, 1)} 或 {@code 2}：
 * 那会让配方永远匹配不上、机器看着能跑但什么都不产（{@code mergeOutput} 不会报错，
 * {@code craft()} 直接走原版分馏兜底）。若以后要给本机加压力玩法，需要改的是机器
 * （构造时给罐设压力，或覆写 {@code getReceivingTanks}/{@code transferFluid} 让罐接受任意压力并记录），
 * 不是这张表。</p>
 *
 * <h2>B4 / B5 互斥</h2>
 * <p>逆水煤气变换（B4）与 Sabatier 甲烷化（B5）吃的是同一对进料（二氧化碳 + 液氢）。
 * {@link HybridFractionRecipes#get} 按注册顺序返回<b>第一条</b>匹配项，两者无法共存，
 * 所以只注册 B4（合成气的下游比天然气多）。想换成 B5 就把 B4 那条注释掉、把 B5 放开。</p>
 */
public final class HybridFractionContent {

	/** 铂合金催化转换器 */
	private static final int PLATINUM = 2;
	/** CMB 催化剂 */
	private static final int CMB = 3;

	private HybridFractionContent() {
	}

	/** 注册全部配方；重复调用只生效一次 */
	public static void register() {
		registerCatalytic();
		registerExotic();
		registerSchrabidic();
	}

	/* ============================ 铂 · 炼厂系（21 条） ============================ */

	/**
	 * 真实炼厂里铂催化对应的工艺：加氢裂化、选择性加氢、催化重整、加氢精制、
	 * 加氢脱氧、蒸汽重整、逆变换、加氢脱烷基。
	 */
	private static void registerCatalytic() {

		// ---- S 级：填补死端 ----

		// S3 裂解汽油选择性加氢（SHP，Pd/Pt）。不饱和烃在 HBM 里只能做塑料，这条给它开燃料出口。
		rec(PLATINUM, f(Fluids.UNSATURATEDS, 1000), f(Fluids.HYDROGEN, 400), null,
				f(Fluids.PETROLEUM, 600), f(Fluids.LPG, 500), f(Fluids.REFORMGAS, 250));

		// S4 VGO 加氢裂化。减压重油原版只能出工业油 + 重质燃油，或去焦化。
		// 4 路流体已占满出料罐，不要再加水或蒸汽。
		rec(PLATINUM, f(Fluids.HEAVYOIL_VACUUM, 1000), f(Fluids.HYDROGEN, 200), sulfur(),
				f(Fluids.NAPHTHA, 300), f(Fluids.KEROSENE, 300), f(Fluids.DIESEL, 400), f(Fluids.HEATINGOIL, 200));

		// S6 HVO 加氢脱氧（Neste NEXBTL）。两种油脂同配方，产物一致。
		rec(PLATINUM, f(Fluids.SUNFLOWEROIL, 1000), f(Fluids.HYDROGEN, 400), null,
				f(Fluids.DIESEL, 700), f(Fluids.LPG, 200), f(Fluids.WATER, 250));
		rec(PLATINUM, f(Fluids.FISHOIL, 1000), f(Fluids.HYDROGEN, 400), null,
				f(Fluids.DIESEL, 700), f(Fluids.LPG, 200), f(Fluids.WATER, 250));

		// S7 重整油抽提 + 加氢精制。单流体配方 → 必须放 0 号进料罐（1 号罐要空），
		// 否则 matches 不成立、机器退回原版分馏（芳香烃 400 + 轻质芳烃 600）。
		rec(PLATINUM, f(Fluids.REFORMATE, 1000), null, null,
				f(Fluids.XYLENE, 450), f(Fluids.AROMATICS, 350), f(Fluids.HYDROGEN, 100), f(Fluids.REFORMGAS, 80));

		// S8 催化除氧 DeOxo（2H2 + O2 → 2H2O）。氢氧混合气是全模组最标准的死端之一。
		rec(PLATINUM, f(Fluids.OXYHYDROGEN, 1000), null, null,
				f(Fluids.WATER, 900), f(Fluids.STEAM, 100));

		// ---- A 级：工艺正确，但会接管原有路径（放铂后原版分馏对同种进料不再生效）----

		// A1 催化重整 Platforming。脱硫石脑油是唯一漏在 ReformingRecipes 之外的石脑油。
		rec(PLATINUM, f(Fluids.NAPHTHA_DS, 1000), null, null,
				f(Fluids.REFORMATE, 500), f(Fluids.AROMATICS, 200), f(Fluids.HYDROGEN, 150), f(Fluids.REFORMGAS, 100));

		// A2 轻油芳构化制 BTX。
		rec(PLATINUM, f(Fluids.LIGHTOIL_DS, 1000), null, null,
				f(Fluids.XYLENE, 400), f(Fluids.AROMATICS, 250), f(Fluids.REFORMATE, 200), f(Fluids.HYDROGEN, 120));

		// A3 浆态床渣油加氢裂化。沥青原版只能裂化成原油 + 芳香烃，或去焦化。
		rec(PLATINUM, f(Fluids.BITUMEN, 1000), f(Fluids.HYDROGEN, 300), petCoke(),
				f(Fluids.HEATINGOIL, 400), f(Fluids.NAPHTHA, 300), f(Fluids.DIESEL, 300), f(Fluids.GAS, 200));

		// A4 加氢异构脱蜡 Isodewaxing。润滑油收率从原版分馏的 40% 提到 50%。
		rec(PLATINUM, f(Fluids.SMEAR, 1000), f(Fluids.HYDROGEN, 150), null,
				f(Fluids.LUBRICANT, 500), f(Fluids.HEATINGOIL, 300), f(Fluids.DIESEL, 150));

		// A5 胺洗 + Claus 脱硫。高硫天然气原版只能去重整炉或焦化。
		rec(PLATINUM, f(Fluids.SOURGAS, 1000), f(Fluids.HYDROGEN, 100), sulfur(),
				f(Fluids.PETROLEUM, 800), f(Fluids.SPENTSTEAM, 100));

		// A6 蒸汽重整 SMR / 自热重整 ATR。PETROLEUM 的中文名是「石油气」，
		// 是 HBM 里唯一适合做重整原料的轻质气态烃（GAS 已有裂化配方，不重复覆盖）。
		rec(PLATINUM, f(Fluids.PETROLEUM, 1000), f(Fluids.STEAM, 2000), null,
				f(Fluids.SYNGAS, 1500), f(Fluids.HYDROGEN, 800), f(Fluids.CARBONDIOXIDE, 500));

		// A8 煤焦油加氢。煤焦杂酚油原版只能出煤液化油 10% + 沥青 90%，收率极差。
		rec(PLATINUM, f(Fluids.COALCREOSOTE, 1000), f(Fluids.HYDROGEN, 300), null,
				f(Fluids.COALOIL, 500), f(Fluids.NAPHTHA, 300), f(Fluids.HEATINGOIL, 200));

		// A9 生物油加氢脱氧 HDO。
		rec(PLATINUM, f(Fluids.WOODOIL, 1000), f(Fluids.HYDROGEN, 350), ash(ItemEnums.EnumAshType.WOOD),
				f(Fluids.HEATINGOIL, 500), f(Fluids.PETROLEUM, 200), f(Fluids.WATER, 200));

		// A10 加氢脱烷基 HDA。XYLENE 的中文名是「轻质芳烃（BTX）」，不是二甲苯。
		rec(PLATINUM, f(Fluids.XYLENE, 1000), f(Fluids.HYDROGEN, 300), null,
				f(Fluids.AROMATICS, 600), f(Fluids.GAS, 350));

		// ---- B 级：可选，收益有限 ----

		// B3 加氢精制 + 稳定塔。含铅汽油基油脱铅后仍是同一物品，属语义补全。
		rec(PLATINUM, f(Fluids.GASOLINE, 1000), f(Fluids.HYDROGEN, 150), null,
				f(Fluids.GASOLINE, 900), f(Fluids.LPG, 100));

		// B4 逆水煤气变换 RWGS。二氧化碳在 HBM 里只有 GAS_COKER 产出、没有任何下游。
		// ※ 与 B5 互斥（同进料），只注册这一条，理由见类注释。
		rec(PLATINUM, f(Fluids.CARBONDIOXIDE, 1000), f(Fluids.HYDROGEN, 1000), null,
				f(Fluids.SYNGAS, 900), f(Fluids.WATER, 900));

		// B5 Sabatier 甲烷化 CO2 + 4H2 → CH4 + 2H2O。与 B4 吃同一对进料，不能共存：
		// rec(PLATINUM, f(Fluids.CARBONDIOXIDE, 1000), f(Fluids.HYDROGEN, 2000), null,
		// 		f(Fluids.GAS, 900), f(Fluids.WATER, 1800));

		// B6 废润滑油加氢再生。再生油目前只有焦化一条路。
		rec(PLATINUM, f(Fluids.RECLAIMED, 1000), f(Fluids.HYDROGEN, 200), null,
				f(Fluids.LUBRICANT, 500), f(Fluids.HEATINGOIL, 300), f(Fluids.DIESEL, 150));

		// B7 煤直接液化一段加氢。
		rec(PLATINUM, f(Fluids.COALOIL, 1000), f(Fluids.HYDROGEN, 250), sulfur(),
				f(Fluids.NAPHTHA, 350), f(Fluids.DIESEL, 350), f(Fluids.HEATINGOIL, 200));

		// B8 轻油加氢裂化。与 A2 同为 LIGHTOIL_DS，但 A2 是单流体（1 号罐要空），
		// 这条吃液氢；两者靠「1 号罐有没有东西」区分，不会互相抢。
		rec(PLATINUM, f(Fluids.LIGHTOIL_DS, 1000), f(Fluids.HYDROGEN, 150), null,
				f(Fluids.DIESEL, 450), f(Fluids.KEROSENE, 300), f(Fluids.NAPHTHA, 200));

		// B9 焦化油加氢精制。4 路流体已占满出料罐。
		rec(PLATINUM, f(Fluids.OIL_COKER, 1000), f(Fluids.HYDROGEN, 200), null,
				f(Fluids.HEATINGOIL, 400), f(Fluids.DIESEL, 300), f(Fluids.NAPHTHA, 200), f(Fluids.SPENTSTEAM, 100));
	}

	/* ============================ 铂 · 奇特种（9 条） ============================ */

	/**
	 * HBM 里一大批「有产出、没去处」的异类流体 —— 血、信息素、炸药、毒气、核废液、赤泥。
	 * 铂催化在这里干的是降解与提纯的活，所以全部净亏体积，不会变成刷材料机。
	 */
	private static void registerExotic() {

		// P1 血液热裂化。对比压缩机压榨（250 重油 / 1000），这条收率高但耗蒸汽，1500 进 900 出。
		rec(PLATINUM, f(Fluids.BLOOD, 1000), f(Fluids.STEAM, 500), ash(ItemEnums.EnumAshType.MISC),
				f(Fluids.HEAVYOIL, 400), f(Fluids.HEATINGOIL, 200), f(Fluids.WATER, 300));

		// P2 增强异虫信息素脱烷基，拆回普通信息素 + 芳烃。1300 进 950 出。
		rec(PLATINUM, f(Fluids.PHEROMONE_M, 1000), f(Fluids.HYDROGEN, 300), null,
				f(Fluids.AROMATICS, 400), f(Fluids.HEATINGOIL, 350), f(Fluids.PHEROMONE, 200));

		// P3 硝酸甘油催化水解销毁。1500 进 1200 出。
		rec(PLATINUM, f(Fluids.NITROGLYCERIN, 1000), f(Fluids.WATER, 500), null,
				f(Fluids.NITRIC_ACID, 500), f(Fluids.SYNGAS, 400), f(Fluids.WATER, 300));

		// P4 光气碱洗 COCl2 + 2KOH → K2CO3 + 2KCl + H2O。毒气销毁的标准路线。
		rec(PLATINUM, f(Fluids.PHOSGENE, 1000), f(Fluids.LYE, 500), null,
				f(Fluids.POTASSIUM_CHLORIDE, 500), f(Fluids.CARBONDIOXIDE, 400), f(Fluids.WATER, 300));

		// P5 芥子气氧化水解，《化学武器公约》指定销毁工艺。1800 进 1400 出。
		rec(PLATINUM, f(Fluids.MUSTARDGAS, 1000), f(Fluids.PEROXIDE, 800), null,
				f(Fluids.SULFURIC_ACID, 500), f(Fluids.WATER, 600), f(Fluids.CARBONDIOXIDE, 300));

		// P6 乙醇脱水制乙烯 C2H5OH → C2H4 + H2O。单流体，严格守恒。
		rec(PLATINUM, f(Fluids.ETHANOL, 1000), null, null,
				f(Fluids.UNSATURATEDS, 600), f(Fluids.WATER, 400));

		// P8 汞齐分解 + 油品精制。汞净消耗 300 mB，润滑油收率 40% → 50%。
		rec(PLATINUM, f(Fluids.MERCURY, 500), f(Fluids.SMEAR, 1000), null,
				f(Fluids.LUBRICANT, 500), f(Fluids.HEATINGOIL, 300), f(Fluids.MERCURY, 200));
		

		// P12 拜耳法赤泥酸浸回收。赤泥原版只有焦化（→ 铁 + 汞）一条路。
		rec(PLATINUM, f(Fluids.REDMUD, 1000), f(Fluids.SULFURIC_ACID, 500), sulfur(),
				f(Fluids.BAUXITE_SOLUTION, 400), f(Fluids.SODIUM_ALUMINATE, 300), f(Fluids.WATER, 300));

		// P14 混合油脂共裂化。与 S6 互补：S6 走加氢，这条走裂化，1000 进 1000 出。
		rec(PLATINUM, f(Fluids.FISHOIL, 500), f(Fluids.SUNFLOWEROIL, 500), null,
				f(Fluids.DIESEL, 600), f(Fluids.LPG, 250), f(Fluids.WATER, 150));
	}

	/* ============================ CMB · 奇特种（3 条） ============================ */

	/**
	 * CMB 催化剂是强放射性物品（{@code ItemRadioactiveConverter}，380 Rad/s），
	 * 配方可以名正言顺地涉及放射性、野火、凝胶这类 HBM 独有的怪东西。
	 *
	 * <p>平衡红线：{@code STELLAR_FLUX} / {@code AMAT} / {@code ASCHRAB} 只消耗、不产出，
	 * 也不能出现「A + B → C，C 又能变回 A」的循环增殖。剩下的一条也只出普通化工品。</p>
	 */
	private static void registerSchrabidic() {

		// M4 野火淬灭、M5 毒泥浆热处理已经迁移到大型催化化工厂（需要高温），塔里不再保留。

		// M9 双凝胶共裂化，纯科幻。1000 进 800 出。
		rec(CMB, f(Fluids.CRYOGEL, 500), f(Fluids.IONGEL, 500), null,
				f(Fluids.PERFLUOROMETHYL_COLD, 400), f(Fluids.HELIUM4, 200), f(Fluids.WATER, 200));
	}

	/* ============================ 构造辅助 ============================ */

	/**
	 * 登记一条配方。
	 *
	 * @param tier 催化剂等级：{@link #PLATINUM} 或 {@link #CMB}
	 * @param in1  一号进料（必填）
	 * @param in2  二号进料，单流体配方传 {@code null}（此时 1 号进料罐必须为空）
	 * @param item 物品输出，没有传 {@code null}
	 * @param outs 最多 4 路出料，按出料罐顺序排列；某项传 {@code null} 表示跳过该罐
	 */
	private static void rec(int tier, FluidStack in1, FluidStack in2, ItemStack item, FluidStack... outs) {

		HybridFractionRecipes.Recipe recipe = new HybridFractionRecipes.Recipe().in1(in1);

		if(in2 != null) recipe.in2(in2);
		recipe.catalyst(tier == CMB ? cmb() : platinum());
		if(item != null) recipe.item(item);

		// 超出 4 路直接丢弃：机器只有 4 个出料罐，多出来的那路无论如何都产不出来。
		// 真要加第 5 种，得先改机器（mergeOutput 上限写死在 4）。
		for(int i = 0; i < outs.length && i < 4; i++) {
			if(outs[i] != null) recipe.out(i, outs[i]);
		}

		HybridFractionRecipes.register(recipe);
	}

	/** 常压流体栈；第三参数（压力）一律省略，原因见类注释 */
	private static FluidStack f(FluidType type, int amount) {
		return new FluidStack(type, amount);
	}

	private static ItemStack platinum() {
		return new ItemStack(com.makentmgreatagain.init.ModItems.platinum_converter);
	}

	private static ItemStack cmb() {
		return new ItemStack(com.makentmgreatagain.init.ModItems.cmb_converter);
	}

	/** 硫粉 */
	private static ItemStack sulfur() {
		return new ItemStack(ModItems.sulfur, 1);
	}

	/** 石油焦炭 */
	private static ItemStack petCoke() {
		return new ItemStack(ModItems.coke, 1, ItemEnums.EnumCokeType.PETROLEUM.ordinal());
	}

	/** 灰烬粉 */
	private static ItemStack ash(ItemEnums.EnumAshType type) {
		return new ItemStack(ModItems.powder_ash, 1, type.ordinal());
	}
}

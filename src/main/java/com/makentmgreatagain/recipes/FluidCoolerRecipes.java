package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Coolable.CoolingType;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;

/**
 * 流体冷却机的配对表。
 *
 * <h3>为什么不再直接读 trait</h3>
 * <p>HBM 的 {@link FT_Heatable} / {@link FT_Coolable} 是给<b>反应堆</b>准备的，
 * 覆盖不到全部「换热」场合，直接用会有两个硬伤：</p>
 * <ol>
 *   <li><b>缺配对</b>：四氟甲烷（{@code PERFLUOROMETHYL}）只登记了「加热 → 热四氟甲烷」，
 *       没有「冷却 → 冷四氟甲烷（{@code PERFLUOROMETHYL_COLD}）」。而冷四氟甲烷在 HBM
 *       里是真实存在的深冷介质（-150°C，靠 PA 反推回常温），本机把它补成可冷却产物。</li>
 *   <li><b>效率为 0</b>：液态钠、铅、重水、钍盐只登记了 PWR / ICF 效率，
 *       {@code HEATEXCHANGER} 是 0。这里一律按 100% 兜底，否则它们进不了换热器。</li>
 * </ol>
 *
 * <p>所以本表在开机时（postInit）扫描一次 {@link Fluids#metaOrder}，
 * 把 HBM 现有配对全部收录，再用 {@link #addCooling} / {@link #addHeating} 做本模组的补充。
 * 玩家或其它附属要加配对，只改这里，机器逻辑不用动。</p>
 *
 * <h3>两边的语义</h3>
 * <pre>
 *   COOLING  被冷却流体：吸收不了/放出热量后变冷 → 槽 2 进、槽 3 出
 *   HEATING  冷却剂：吸收热量后变热          → 槽 1 进、槽 4 出
 * </pre>
 */
public class FluidCoolerRecipes {

	/** 被冷却侧的一条配对 */
	public static class Cooling {
		public final FluidType input;
		public final FluidType output;
		/** 每消耗 {@link #amountReq} mB 放出的热量（TU） */
		public final int heatEnergy;
		public final int amountReq;
		public final int amountProduced;
		/** 换热器效率，已做「0 按 1.0」兜底 */
		public final double efficiency;

		public Cooling(FluidType input, FluidType output, int heatEnergy, int amountReq, int amountProduced, double efficiency) {
			this.input = input;
			this.output = output;
			this.heatEnergy = heatEnergy;
			this.amountReq = amountReq;
			this.amountProduced = amountProduced;
			this.efficiency = efficiency;
		}
	}

	/** 冷却剂侧的一条配对 */
	public static class Heating {
		public final FluidType input;
		public final FluidType output;
		/** 每消耗 {@link #amountReq} mB 吸收的热量（TU） */
		public final int heatReq;
		public final int amountReq;
		public final int amountProduced;
		/** 换热器效率，已做「0 按 1.0」兜底 */
		public final double efficiency;

		public Heating(FluidType input, FluidType output, int heatReq, int amountReq, int amountProduced, double efficiency) {
			this.input = input;
			this.output = output;
			this.heatReq = heatReq;
			this.amountReq = amountReq;
			this.amountProduced = amountProduced;
			this.efficiency = efficiency;
		}
	}

	private static final Map<FluidType, Cooling> COOLING = new HashMap<FluidType, Cooling>();
	private static final Map<FluidType, Heating> HEATING = new HashMap<FluidType, Heating>();

	/** 是否已经扫描过。{@code init()} 之前访问会返回空表，机器只是不工作，不会崩 */
	private static boolean initialized;

	/**
	 * 扫描 HBM 的全部流体并建立配对表。必须在 {@code Fluids.init()} 之后调用，
	 * 本模组放在 {@code CommonProxy.postInit}。
	 *
	 * <p><b>为什么要扫三个列表</b>：{@code Fluids.init()} 跑在 HBM 自己的 preInit，
	 * 那时附属的 {@code foreignFluids} 还是空的，所以 metaOrder 里没有本模组的盐水系列；
	 * 而并入 metaOrder 只在 {@code /ntmreload} 时发生。三个列表合起来才不会漏。</p>
	 */
	public static void init() {

		COOLING.clear();
		HEATING.clear();

		List<FluidType> all = new ArrayList<FluidType>();
		all.addAll(Fluids.metaOrder);
		all.addAll(Fluids.foreignFluids);
		all.addAll(Fluids.customFluids);

		// 三个列表之间有重叠，HashMap.put 是幂等的，重复遍历没有副作用
		for(FluidType type : all) {
			if(type == null) continue;

			// ---- 被冷却侧 ----
			if(type.hasTrait(FT_Coolable.class)) {
				FT_Coolable trait = type.getTrait(FT_Coolable.class);

				if(trait.coolsTo != null && trait.coolsTo != Fluids.NONE
						&& trait.amountReq > 0 && trait.amountProduced > 0 && trait.heatEnergy > 0) {
					COOLING.put(type, new Cooling(type, trait.coolsTo, trait.heatEnergy,
							trait.amountReq, trait.amountProduced, eff(trait.getEfficiency(CoolingType.HEATEXCHANGER))));
				}
			}

			// ---- 冷却剂侧 ----
			if(type.hasTrait(FT_Heatable.class)) {
				FT_Heatable trait = type.getTrait(FT_Heatable.class);
				FT_Heatable.HeatingStep step = trait.getFirstStep();

				if(step != null && step.typeProduced != null && step.typeProduced != Fluids.NONE
						&& step.amountReq > 0 && step.amountProduced > 0 && step.heatReq > 0) {
					HEATING.put(type, new Heating(type, step.typeProduced, step.heatReq,
							step.amountReq, step.amountProduced, eff(trait.getEfficiency(HeatingType.HEATEXCHANGER))));
				}
			}
		}

		// ===== 本模组补充的配对 =====
		// HBM 只登记了「四氟甲烷 → 热四氟甲烷」，缺「四氟甲烷 → 冷四氟甲烷」。
		// 冷四氟甲烷（PERFLUOROMETHYL_COLD）是 HBM 原有的深冷介质（-150°C），
		// 只是原版只允许用 PA 把它加热回常温，没有反向的冷却入口。
		addCooling(Fluids.PERFLUOROMETHYL, Fluids.PERFLUOROMETHYL_COLD, 300, 1, 1);

		initialized = true;
	}

	/** 效率兜底：HBM 里 0 表示「该用途没有登记」，不是「完全不能换热」 */
	private static double eff(double value) {
		return value > 0D ? value : 1.0D;
	}

	/**
	 * 新增/覆盖一条被冷却配对。
	 *
	 * @param heatEnergy 每 amountReq mB 放出的热量（TU），用来和冷却剂侧配平
	 */
	public static void addCooling(FluidType input, FluidType output, int heatEnergy, int amountReq, int amountProduced) {
		if(input == null || output == null) return;
		COOLING.put(input, new Cooling(input, output, heatEnergy, amountReq, amountProduced, 1.0D));
	}

	/** 新增/覆盖一条冷却剂配对。 */
	public static void addHeating(FluidType input, FluidType output, int heatReq, int amountReq, int amountProduced) {
		if(input == null || output == null) return;
		HEATING.put(input, new Heating(input, output, heatReq, amountReq, amountProduced, 1.0D));
	}

	public static Cooling cooling(FluidType type) {
		return type == null ? null : COOLING.get(type);
	}

	public static Heating heating(FluidType type) {
		return type == null ? null : HEATING.get(type);
	}

	public static boolean isCoolable(FluidType type) {
		return cooling(type) != null;
	}

	public static boolean isCoolant(FluidType type) {
		return heating(type) != null;
	}

	public static int coolingCount() {
		return COOLING.size();
	}

	public static int heatingCount() {
		return HEATING.size();
	}

	public static boolean isInitialized() {
		return initialized;
	}
}

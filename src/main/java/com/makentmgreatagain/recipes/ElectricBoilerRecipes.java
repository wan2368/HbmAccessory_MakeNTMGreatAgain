package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Heatable.HeatingType;
import com.hbm.inventory.fluid.trait.FT_PWRModerator;

/**
 * 电锅炉配方表：HBM 与附属全部「可加热流体」都能烧。
 *
 * <p>原版电锅炉只硬编码 OIL→HOTOIL、CRACKOIL→HOTCRACKOIL 两条。
 * 本表在 postInit 扫描一次 {@link Fluids#metaOrder} / {@code foreignFluids} /
 * {@code customFluids}（与 {@link FluidCoolerRecipes} 同时机、同套路），
 * 凡带 {@link FT_Heatable} 特性且首步有效的流体全部收录——
 * 以及附属自己的盐水等自定义流体。</p>
 *
 * <p>每条取 trait 的<b>首步</b>（低热档产物，如水→蒸汽）；多级链
 * （蒸汽→过热蒸汽→超热蒸汽）属于专用锅炉/热交换的领域，电锅炉只烧第一级。</p>
 *
 * <p>沸点热值沿用原版电锅炉的 35000（决定每 tick 迭代 heat/35000 次），
 * 每次迭代按配方的 amountReq→amountProduced 换算，首次耗热 35、后续 50。</p>
 */
public class ElectricBoilerRecipes {

	/** 一条沸腾配方 */
	public static class Entry {
		public final FluidType input;
		public final FluidType output;
		/** 每次迭代消耗的输入 mB */
		public final int amountReq;
		/** 每次迭代产出的输出 mB */
		public final int amountProduced;
		/** 沸点热值（原版统一 35000） */
		public final int boilingHeat;

		public Entry(FluidType input, FluidType output, int amountReq, int amountProduced, int boilingHeat) {
			this.input = input;
			this.output = output;
			this.amountReq = amountReq;
			this.amountProduced = amountProduced;
			this.boilingHeat = boilingHeat;
		}
	}

	/** 原版沸点热值 */
	public static final int DEFAULT_BOILING_HEAT = 35_000;

	private static final Map<FluidType, Entry> RECIPES = new HashMap<FluidType, Entry>();

	/** 是否已扫描。init() 之前 get() 走原版两条硬编码兜底，机器不会罢工 */
	private static boolean initialized;

	/**
	 * 扫描全部流体建立配方表。必须在 HBM 的 Fluids.init() 与附属 ModFluids
	 * 都跑完之后调用（本模组挂在 CommonProxy.postInit，与 FluidCoolerRecipes.init() 同点）。
	 */
	public static void init() {

		RECIPES.clear();

		List<FluidType> all = new ArrayList<FluidType>();
		all.addAll(Fluids.metaOrder);
		all.addAll(Fluids.foreignFluids);
		all.addAll(Fluids.customFluids);

		// 三个列表有重叠，HashMap.put 幂等，重复遍历没有副作用
		for(FluidType type : all) {
			if(type == null || type == Fluids.NONE) continue;
			if(!type.hasTrait(FT_Heatable.class)) continue;

			FT_Heatable trait = type.getTrait(FT_Heatable.class);
			FT_Heatable.HeatingStep step;
			try {
				step = trait.getFirstStep();
			} catch(IndexOutOfBoundsException e) {
				continue;	// 有 trait 却没有步骤的畸形注册，跳过
			}

			if(step == null || step.typeProduced == null || step.typeProduced == Fluids.NONE) continue;
			if(step.amountReq <= 0 || step.amountProduced <= 0 || step.heatReq <= 0) continue;

			// 反应堆专用冷却剂（带 PWR 通量调节，或登记了 PWR/ICF 堆内效率）不在电锅炉加热，
			// 必须走 PWR 或 ICF；判定规则见 ElectricBoilerFilterSpec（有测试覆盖）。
			if(ElectricBoilerFilterSpec.isReactorCoolant(type.hasTrait(FT_PWRModerator.class),
					trait.getEfficiency(HeatingType.PWR), trait.getEfficiency(HeatingType.ICF))) continue;

			RECIPES.put(type, new Entry(type, step.typeProduced, step.amountReq, step.amountProduced, DEFAULT_BOILING_HEAT));
		}

		// 原版两条硬编码兜底（trait 万一被玩家 json 改掉也不至于退功能）
		add(Fluids.OIL, Fluids.HOTOIL, 5, 5);
		add(Fluids.CRACKOIL, Fluids.HOTCRACKOIL, 5, 5);

		initialized = true;
	}

	/** 新增/覆盖一条配方 */
	public static void add(FluidType input, FluidType output, int amountReq, int amountProduced) {
		if(input == null || output == null) return;
		RECIPES.put(input, new Entry(input, output, amountReq, amountProduced, DEFAULT_BOILING_HEAT));
	}

	/**
	 * 查一条配方。init() 之前返回原版两条硬编码（OIL/CRACKOIL），
	 * 保证任何时序下机器行为都不比原版差。
	 */
	public static Entry get(FluidType type) {

		if(type == null || type == Fluids.NONE) return null;

		Entry entry = RECIPES.get(type);
		if(entry != null) return entry;

		if(!initialized) {
			if(type == Fluids.OIL) return new Entry(type, Fluids.HOTOIL, 5, 5, DEFAULT_BOILING_HEAT);
			if(type == Fluids.CRACKOIL) return new Entry(type, Fluids.HOTCRACKOIL, 5, 5, DEFAULT_BOILING_HEAT);
		}

		return null;
	}

	public static int count() {
		return RECIPES.size();
	}

	public static boolean isInitialized() {
		return initialized;
	}
}

package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;

import net.minecraft.item.ItemStack;

/**
 * 大型离心机配方。
 *
 * <p>与化工厂那套 GenericRecipe 不同：离心机没有配方选择界面，靠输入自动匹配。
 * 一条配方可以同时要求最多 2 种物品和 1 种流体，产出最多 8 个物品、4 种流体
 * （界面里能显示的是 8 个物品槽和 4 个输出罐）。</p>
 *
 * <p>基础工时 180 tick；速度升级按原版离心机的比例缩短。</p>
 */
public class CentrifugeRecipe {

	/** 物品进料（0~2 种） */
	public final List<AStack> inputItems = new ArrayList<AStack>();
	/** 流体进料（可为 null） */
	public FluidStack inputFluid;
	/** 物品产物（最多 8 个） */
	public final List<ItemStack> outputItems = new ArrayList<ItemStack>();
	/** 流体产物（最多 4 种） */
	public final List<FluidStack> outputFluids = new ArrayList<FluidStack>();

	/** 单条产线每 tick 的耗电 */
	public long power = 1_000L;
	/** 基础工时（tick） */
	public int duration = 180;

	public CentrifugeRecipe items(AStack... stacks) {
		for(AStack stack : stacks) if(stack != null) this.inputItems.add(stack);
		return this;
	}

	public CentrifugeRecipe fluid(FluidStack stack) {
		this.inputFluid = stack;
		return this;
	}

	/**
	 * 要求进料罐达到指定压力（PU）才能开跑，0 = 不限压。
	 *
	 * <p>压力记在 {@link FluidStack#pressure} 上，与 HBM 其它带压设备同一套语义：
	 * 进料罐的 {@code getPressure()} 必须<b>精确等于</b>该值，不是「不低于」。
	 * 玩家用泵把流体加压到对应 PU 后送进来即可。</p>
	 *
	 * <p>注意压力只影响<b>能否开工</b>，不改变消耗量；配方本身仍按 {@code fill} 扣料。</p>
	 */
	public CentrifugeRecipe pressure(int pressure) {
		if(this.inputFluid != null) this.inputFluid.pressure = pressure;
		return this;
	}

	public CentrifugeRecipe outItems(ItemStack... stacks) {
		for(ItemStack stack : stacks) if(stack != null && stack.getItem() != null) this.outputItems.add(stack);
		return this;
	}

	public CentrifugeRecipe outFluids(FluidStack... stacks) {
		for(FluidStack stack : stacks) if(stack != null) this.outputFluids.add(stack);
		return this;
	}

	public CentrifugeRecipe power(long power) {
		this.power = power;
		return this;
	}

	public CentrifugeRecipe duration(int duration) {
		this.duration = duration;
		return this;
	}

	/** 这条配方是否吃这个物品 */
	public boolean matchesItem(ItemStack stack) {
		if(stack == null || this.inputItems.isEmpty()) return false;

		for(AStack input : this.inputItems) {
			if(input.matchesRecipe(stack, true)) return true;
		}

		return false;
	}

	/** 这条配方是否吃这种流体，并且量够 */
	public boolean matchesFluid(FluidType type, int amount) {
		if(this.inputFluid == null || this.inputFluid.type != type) return false;
		return amount >= this.inputFluid.fill;
	}
}

package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import net.minecraft.item.ItemStack;

/**
 * 混合催化分馏塔的配方表。
 *
 * <p>模板固定为「两路不同流体输入 → 最多四种流体输出 + 一个物品输出」。
 * 混合催化分馏塔的 6 个储罐里，0/1 是进料、2~5 是出料，
 * 所以 {@link (int, FluidStack)} 的序号直接对应出料罐编号：
 * 0 → 2 号罐、1 → 3 号罐、2 → 4 号罐、3 → 5 号罐。</p>
 *
 * <h3>注册模板</h3>
 * <pre>{@code
 * // 实际配方写在 HybridFractionContent 里，用它的 rec(...) 辅助方法
 * rec(PLATINUM,
 *     f(Fluids.UNSATURATEDS, 1000),                    // 一号进料：类型 + 每周期用量
 *     f(Fluids.HYDROGEN, 400),                         // 二号进料（可 null）
 *     null,                                            // 物品输出（可 null）
 *     f(Fluids.PETROLEUM, 600),                        // 出料罐 2
 *     f(Fluids.LPG, 500),                              // 出料罐 3
 *     f(Fluids.REFORMGAS, 250));                       // 出料罐 4
 * }</pre>
 *
 * <h3>催化剂</h3>
 * <p>机器 6 号槽是催化剂槽，目前认三种：</p>
 * <ul>
 *   <li>{@code com.hbm.items.ModItems.catalytic_converter} —— 原版催化转换器</li>
 *   <li>{@code ModItems.platinum_converter} —— 铂催化剂</li>
 *   <li>{@code ModItems.cmb_converter} —— CMB 催化剂</li>
 * </ul>
 * <p>配方里 {@code .catalyst(...)} 写哪个，就必须放哪个；不写 {@code .catalyst(...)} 的配方
 * 不需要催化剂，槽里放不放都能跑。</p>
 *
 * <h3>规则</h3>
 * <ul>
 *   <li>两路进料的顺序无所谓：0 号罐放哪一路都能匹配，机器自己认。</li>
 *   <li>只想用一路流体时省略 {@code in2(...)}，此时<b>另一个</b>进料罐必须是空的（类型 NONE）；
 *       放进 0 号罐或 1 号罐都行，机器按实际罐型自己认。</li>
 *   <li><b>压力字段在本机不可用</b>：两个进料罐的 {@code pressure} 恒为 0，而 HBM 的流体网络
 *       按 {@code tank.getPressure() == pressure} 精确匹配才注入，加压流体根本送不进来。
 *       所以所有配方一律按常压（省略第三参数）注册；写 1 / 2 会让配方永远匹配不上、
 *       机器静默退回原版分馏。详见 {@link HybridFractionContent} 类注释。</li>
 *   <li>每个周期（10 tick）消耗一次 {@code in1}/{@code in2} 的量，产出一次 {@code out} 的量。</li>
 *   <li>出料罐放不下的整条配方都不执行，不会只产出一半。</li>
 * </ul>
 *
 * <h3>兜底</h3>
 * <p>表里查不到时，机器会退回原版 {@code FractionRecipes}（1 进 2 出）继续工作，
 * 所以老的分馏链不受影响。</p>
 */
public final class HybridFractionRecipes {

	/** 所有已注册配方 */
	public static final List<Recipe> RECIPES = new ArrayList<Recipe>();

	private HybridFractionRecipes() {
	}

	/* ========================== 注册入口 ========================== */

	/**
	 * 登记配方。实际数据全部在 {@link HybridFractionContent#register()} 里。
	 *
	 * <p>要加新材料时按下面的写法写进 {@code HybridFractionContent}：</p>
	 *
	 * <pre>{@code
	 * // 例：不饱和烃 + 液氢，需要铂催化剂 → 石油气 + 液化石油气 + 重整气
	 * rec(PLATINUM,
	 *     f(Fluids.UNSATURATEDS, 1000),
	 *     f(Fluids.HYDROGEN, 400),
	 *     null,
	 *     f(Fluids.PETROLEUM, 600), f(Fluids.LPG, 500), f(Fluids.REFORMGAS, 250));
	 * }</pre>
	 *
	 * <p><b>不要写压力</b>：本机两个进料罐的压力恒为 0，加压流体送不进来，
	 * 写了 1 或 2 的配方会永远匹配不上。详见 {@link HybridFractionContent} 类注释。</p>
	 */
	public static void addRecipes() {
		// 全部配方数据在 HybridFractionContent 里（铂 30 条 + CMB 1 条；
		// 原来的野火淬灭、毒泥浆热处理已迁到大型催化化工厂），
		// 本类只负责匹配、扣料与出料罐分配。改数值请去那边，编号与
		// docs/混合催化分馏塔_配方总表.md 一一对应。
		HybridFractionContent.register();
	}

	/**
	 * 催化剂的等级：0 不是催化剂 / 1 原版催化转换器 / 2 铂 / 3 CMB。
	 *
	 * <p>等级高的催化剂能顶替等级低的：配方要 1 级，放 2、3 级也能跑；
	 * 配方要 3 级，就只有 CMB 能跑。</p>
	 */
	public static int catalystTier(ItemStack stack) {

		if(stack == null) return 0;

		net.minecraft.item.Item item = stack.getItem();

		if(item == com.hbm.items.ModItems.catalytic_converter) return 1;
		if(item == com.makentmgreatagain.init.ModItems.platinum_converter) return 2;
		if(item == com.makentmgreatagain.init.ModItems.cmb_converter) return 3;

		return 0;
	}

	/* ========================== 查询 ========================== */

	/**
	 * 按两路进料的类型、压力和催化剂查配方，顺序无关。
	 *
	 * @return 匹配到的配方，没有则 null
	 */
	public static Recipe get(FluidType typeA, int pressureA, FluidType typeB, int pressureB, ItemStack catalyst) {
		for(Recipe recipe : RECIPES) {
			if(recipe.matches(typeA, pressureA, typeB, pressureB) && recipe.matchesCatalyst(catalyst)) {
				return recipe;
			}
		}
		return null;
	}

	public static Recipe get(FluidType typeA, int pressureA, FluidType typeB, int pressureB) {
		return get(typeA, pressureA, typeB, pressureB, null);
	}

	public static void register(Recipe recipe) {
		RECIPES.add(recipe);
	}

	/* ========================== 配方 ========================== */

	public static class Recipe {

		/** 一号进料（必填） */
		public FluidStack in1;
		/** 二号进料（可空：单流体配方） */
		public FluidStack in2;
		/** 四路出料，序号对应 2~5 号储罐 */
		public final FluidStack[] out = new FluidStack[4];
		/** 物品输出（可空） */
		public ItemStack item;
		/** 需要的催化剂（可空：不需要催化剂） */
		public ItemStack catalyst;

		public Recipe in1(FluidStack stack) {
			this.in1 = stack;
			return this;
		}

		public Recipe in2(FluidStack stack) {
			this.in2 = stack;
			return this;
		}

		public Recipe catalyst(ItemStack stack) {
			this.catalyst = stack;
			return this;
		}

		/**
		 * 催化剂槽里放的东西能不能满足本配方。
		 *
		 * <p>配方没指定催化剂时永远返回 true；指定了就按等级比：放进去的等级
		 * ≥ 配方要求的等级即可（高级催化剂兼容低级配方）。</p>
		 */
		public boolean matchesCatalyst(ItemStack stack) {

			if(catalyst == null) return true;

			int need = HybridFractionRecipes.catalystTier(catalyst);
			if(need <= 0) return true;

			return HybridFractionRecipes.catalystTier(stack) >= need;
		}

		public Recipe out(int index, FluidStack stack) {
			this.out[index] = stack;
			return this;
		}

		public Recipe item(ItemStack stack) {
			this.item = stack;
			return this;
		}

		/**
		 * 两路进料能否匹配本配方（顺序无关）。
		 *
		 * <p>单流体配方（{@code in2 == null}）也<b>不挑罐位</b>：放进 0 号罐、1 号罐都能认，
		 * 只要另一个罐是空的就行 —— 早期版本要求必须放 0 号罐，玩家很容易放错边还看不出来。</p>
		 *
		 * @param pressureA A 罐压力
		 * @param pressureB B 罐压力
		 */
		public boolean matches(FluidType typeA, int pressureA, FluidType typeB, int pressureB) {

			if(in1 == null) return false;

			// 正向：in1 在 A 罐
			if(typeA == in1.type && pressureA >= in1.pressure) {
				if(in2 == null) {
					if(typeB == Fluids.NONE) return true;
				} else if(typeB == in2.type && pressureB >= in2.pressure) {
					return true;
				}
			}

			// 反向：in2 在 A 罐
			if(in2 != null && typeA == in2.type && pressureA >= in2.pressure) {
				if(typeB == in1.type && pressureB >= in1.pressure) return true;
			}

			// 单流体配方放在 B 罐：A 罐空着，同样成立
			if(in2 == null && typeA == Fluids.NONE && typeB == in1.type && pressureB >= in1.pressure) return true;

			return false;
		}

		/**
		 * 把本配方的两路需求映射到两个进料罐上。
		 *
		 * <p>返回长度 2 的数组：{@code [0]} 对应 0 号进料罐、{@code [1]} 对应 1 号进料罐，
		 * 不需要的那一路是 {@code null}。顺序无关 —— 单流体配方放进哪个罐都行，
		 * 扣料时按这个映射走，不再依赖「in1 一定在 0 号罐」这个假设。</p>
		 */
		public FluidStack[] assignTo(FluidType typeA, FluidType typeB) {

			FluidStack[] need = new FluidStack[2];

			if(in1 != null) {
				if(typeA == in1.type) need[0] = in1;
				else if(typeB == in1.type) need[1] = in1;
			}

			if(in2 != null) {
				if(typeA == in2.type && need[0] == null) need[0] = in2;
				else if(typeB == in2.type && need[1] == null) need[1] = in2;
			}

			return need;
		}
	}
}

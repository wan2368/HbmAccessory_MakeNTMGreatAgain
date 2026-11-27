package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.List;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * MF（多配方）模式：把已选的多个配方<b>合并成一个虚拟配方</b>。
 *
 * <h2>为什么用合并而不是多模块</h2>
 * <p>模块整套逻辑（并行份数、扣料、产出、罐型）都只认 {@code getRecipe()} 返回的那一个
 * {@link GenericRecipe}。把 5 个配方合成 1 个喂进去，就能沿用全部现成逻辑，
 * 不必新建 5 个模块、也不必扩槽位。</p>
 *
 * <h2>合并规则</h2>
 * <ul>
 *   <li><b>同种物品累加、共用一份</b>：同 item + 同 meta + 同 NBT 视为同种；</li>
 *   <li>同种流体累加 mB（压力不同视为不同种，避免带压与不带压混在一起）；</li>
 *   <li>输出同样按种类累加；</li>
 *   <li><b>耗时与耗电取各配方相加后的中间值</b>（合计 ÷ 配方数量，整数截断），不与数量相乘。</li>
 * </ul>
 *
 * <h2>不会串味</h2>
 * <p>合并结果是精确清单，扣料时逐项 {@code matchesRecipe}，
 * <b>不会回头查表去匹配别的配方</b>。所以即使槽里的料还能凑出玩家没选的配方，
 * 也只消耗选定的、只产出选定的。</p>
 */
public final class MergedRecipe {

	/** MF 模式的种类上限：输入物品 16 格、输入流体 8 罐、输出物品 8 格、输出流体 8 罐 */
	public static final int MAX_IN_ITEMS = 16;
	public static final int MAX_IN_FLUIDS = 8;
	public static final int MAX_OUT_ITEMS = 8;
	public static final int MAX_OUT_FLUIDS = 8;

	private MergedRecipe() { }

	/** 合成用的虚拟配方名，不会进任何配方表 */
	public static final String NAME = "makentmgreatagain.mf.merged";

	/**
	 * 把选中的配方合并成一个虚拟配方。
	 *
	 * @param set     配方表（用来按名取配方）
	 * @param names   已选配方名，空串/null 表示空位
	 * @return 合并结果；一个都没选时返回 null
	 */
	public static GenericRecipe merge(GenericRecipes set, String[] names) {

		List<GenericRecipe> picked = resolve(set, names);
		if(picked.isEmpty()) return null;

		List<AStack> inItems = new ArrayList<AStack>();
		List<FluidStack> inFluids = new ArrayList<FluidStack>();
		List<IOutput> outItems = new ArrayList<IOutput>();
		List<FluidStack> outFluids = new ArrayList<FluidStack>();

		int duration = 0;
		long power = 0;
		int heat = 0;
		int catalyst = 0;

		for(GenericRecipe recipe : picked) {
			// 固体燃料合成没有输入清单，产量靠逐罐读可燃流体折算，
			// 与 MF 的「按清单均分」模型冲突：当成普通配方合并会变成无需求、凭空产出。
			// 这类配方在 MF 下一律跳过（选择器也会拦下不让选）。
			if(recipe instanceof SolidFuelRecipe) continue;

			// 耗时与耗电先累加，最后除以配方数量取中间值（整数截断）
			duration += recipe.duration;
			power += recipe.power;

			// 热量取最大值、催化剂取最高等级：机器靠 instanceof HeatChemicalRecipe
			// 读这两个值，合并时丢了就永远加热不到目标、也不显示热量需求
			if(recipe instanceof HeatChemicalRecipe) {
				HeatChemicalRecipe hot = (HeatChemicalRecipe) recipe;
				heat = Math.max(heat, hot.requiredHeat);
				catalyst = Math.max(catalyst, hot.requiredCatalyst);
			}

			if(recipe.inputItem != null) {
				for(AStack in : recipe.inputItem) {
					if(in == null) continue;
					addItem(inItems, in);
				}
			}
			if(recipe.inputFluid != null) {
				for(FluidStack in : recipe.inputFluid) {
					if(in == null || in.type == null || in.fill <= 0) continue;
					addFluid(inFluids, in);
				}
			}
			if(recipe.outputItem != null) {
				for(IOutput out : recipe.outputItem) {
					if(out == null) continue;
					addOutput(outItems, out);
				}
			}
			if(recipe.outputFluid != null) {
				for(FluidStack out : recipe.outputFluid) {
					if(out == null || out.type == null || out.fill <= 0) continue;
					addFluid(outFluids, out);
				}
			}
		}

		// 兜底：没有任何输入需求就不构成有效配方。
		// 否则模块会判「输入充足」而凭空产出（固体燃料那类无清单配方就会这样）。
		if(inItems.isEmpty() && inFluids.isEmpty()) return null;

		// 整条产线：耗时与耗电都是「各配方相加后取中间值」= 合计 ÷ 配方数量，
		// 整数截断不含小数点；不再与配方数量相乘
		int count = picked.size();
		MergedHeatRecipe merged = new MergedHeatRecipe(NAME, heat, catalyst, picked);
		merged.setup(Math.max(1, duration / count), power / count);
		merged.inputItem = inItems.toArray(new AStack[inItems.size()]);
		merged.inputFluid = inFluids.toArray(new FluidStack[inFluids.size()]);
		merged.outputItem = outItems.toArray(new IOutput[outItems.size()]);
		merged.outputFluid = outFluids.toArray(new FluidStack[outFluids.size()]);

		// 图标取第一个配方的，选择器与机器界面用它显示
		ItemStack icon = picked.get(0).getIcon();
		if(icon != null) merged.setIcon(icon);

		return merged;
	}

	/** 按名取配方，跳过空位与查不到的名字 */
	public static List<GenericRecipe> resolve(GenericRecipes set, String[] names) {
		List<GenericRecipe> picked = new ArrayList<GenericRecipe>();
		if(set == null || names == null) return picked;

		for(String name : names) {
			if(name == null || name.isEmpty() || "null".equals(name)) continue;
			Object obj = set.recipeNameMap.get(name);
			if(!(obj instanceof GenericRecipe)) continue;

			// 去重兜底：同一配方只算一次，避免重复项抬高合计与数量
			boolean dup = false;
			for(GenericRecipe exist : picked) {
				if(exist.getInternalName().equals(name)) { dup = true; break; }
			}
			if(dup) continue;

			picked.add((GenericRecipe) obj);
		}
		return picked;
	}

	/**
	 * 试算「已选 + 追加这个」是否超出 MF 的种类上限。
	 *
	 * @return null = 可以选；否则为可直接显示给玩家的中文原因
	 */
	public static String checkLimits(GenericRecipes set, String[] names, String extra) {

		// 同一配方只能选一次：重复选会让耗时耗电按数量翻倍，输入输出却没变化
		if(extra != null && !extra.isEmpty() && names != null) {
			for(String name : names) {
				if(extra.equals(name)) return "该配方已选择";
			}
		}

		// 固体燃料合成走的是「逐罐折算」的专用机制，与 MF 的按清单均分不兼容
		if(extra != null && !extra.isEmpty()) {
			Object obj = set != null ? set.recipeNameMap.get(extra) : null;
			if(obj instanceof SolidFuelRecipe) {
				return "多配方模式不支持固体燃料合成";
			}
		}

		String[] trial = new String[names.length + 1];
		for(int i = 0; i < names.length; i++) trial[i] = names[i];
		trial[names.length] = extra;

		GenericRecipe merged = merge(set, trial);
		if(merged == null) return null;

		int inI = merged.inputItem != null ? merged.inputItem.length : 0;
		int inF = merged.inputFluid != null ? merged.inputFluid.length : 0;
		int outI = merged.outputItem != null ? merged.outputItem.length : 0;
		int outF = merged.outputFluid != null ? merged.outputFluid.length : 0;

		if(inI > MAX_IN_ITEMS) return "超出上限：输入物品 " + inI + " 种（最多 " + MAX_IN_ITEMS + " 种）";
		if(inF > MAX_IN_FLUIDS) return "超出上限：输入流体 " + inF + " 种（最多 " + MAX_IN_FLUIDS + " 种）";
		if(outI > MAX_OUT_ITEMS) return "超出上限：输出物品 " + outI + " 种（最多 " + MAX_OUT_ITEMS + " 种）";
		if(outF > MAX_OUT_FLUIDS) return "超出上限：输出流体 " + outF + " 种（最多 " + MAX_OUT_FLUIDS + " 种）";

		return null;
	}

	/* ============================== 合并工具 ============================== */

	/** 同种物品累加数量；不同种追加一项 */
	private static void addItem(List<AStack> list, AStack in) {
		for(int i = 0; i < list.size(); i++) {
			AStack exist = list.get(i);
			if(sameItem(exist, in)) {
				list.set(i, withSize(exist, exist.stacksize + in.stacksize));
				return;
			}
		}
		list.add(copyOf(in));
	}

	/** 同种流体累加 mB（压力不同算不同种） */
	private static void addFluid(List<FluidStack> list, FluidStack in) {
		for(int i = 0; i < list.size(); i++) {
			FluidStack exist = list.get(i);
			if(exist.type == in.type && exist.pressure == in.pressure) {
				list.set(i, new FluidStack(exist.type, exist.fill + in.fill, exist.pressure));
				return;
			}
		}
		list.add(new FluidStack(in.type, in.fill, in.pressure));
	}

	/**
	 * 输出累加。
	 *
	 * <p>概率产物/多产物（{@code possibleMultiOutput}）无法安全累加，各自独立占一项，
	 * 交给模块按原样处理。</p>
	 */
	private static void addOutput(List<IOutput> list, IOutput out) {
		if(!out.possibleMultiOutput()) {
			ItemStack single = out.getSingle();
			if(single != null) {
				for(int i = 0; i < list.size(); i++) {
					IOutput exist = list.get(i);
					if(exist.possibleMultiOutput()) continue;
					ItemStack es = exist.getSingle();
					if(es == null) continue;
					if(stackKey(es).equals(stackKey(single))) {
						ItemStack sum = es.copy();
						sum.stackSize = es.stackSize + single.stackSize;
						list.set(i, new ChanceOutput(sum));
						return;
					}
				}
				list.add(new ChanceOutput(single.copy()));
				return;
			}
		}
		list.add(out);
	}

	/** 物品同种判定：item + meta + NBT */
	private static boolean sameItem(AStack a, AStack b) {
		ItemStack sa = single(a);
		ItemStack sb = single(b);
		if(sa == null || sb == null) return false;
		return stackKey(sa).equals(stackKey(sb));
	}

	private static ItemStack single(AStack stack) {
		List<ItemStack> all = stack.extractForNEI();
		return all != null && !all.isEmpty() ? all.get(0) : null;
	}

	/**
	 * 合并只在「确定物品」之间进行。
	 *
	 * <p>矿辞输入（一个 AStack 对应多种物品）无法确定玩家会放哪一种，
	 * 直接保留原样、不参与累加，避免把不同物品错误合并。</p>
	 */
	private static AStack copyOf(AStack in) {
		if(in instanceof ComparableStack) {
			ComparableStack comp = (ComparableStack) in;
			return new ComparableStack(comp.item, in.stacksize, comp.meta);
		}
		return in;
	}

	private static AStack withSize(AStack in, int size) {
		if(in instanceof ComparableStack) {
			ComparableStack comp = (ComparableStack) in;
			return new ComparableStack(comp.item, size, comp.meta);
		}
		return in;
	}

	/** item + meta + NBT 的唯一键 */
	private static String stackKey(ItemStack stack) {
		String nbt = "";
		NBTTagCompound tag = stack.getTagCompound();
		if(tag != null) nbt = tag.toString();
		return Item.getIdFromItem(stack.getItem()) + ":" + stack.getItemDamage() + ":" + nbt;
	}
}
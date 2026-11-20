package com.makentmgreatagain.recipes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes.PyroOvenRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.special.ItemBedrockOreNew;

import net.minecraft.item.ItemStack;

/**
 * 大型催化化工厂专用的配方表。
 *
 * <p>在本体化工厂全部配方的基础上，额外加入热解炉的每一条配方
 * （本体 {@link PyroOvenRecipes#recipes} 里的内容，包含其他附属通过该列表注册的配方）。
 * 热解炉配方在这里统一转成 {@link HeatChemicalRecipe}，需要很高的内部热量才能开工。</p>
 *
 * <p>独立于 {@link ChemicalPlantRecipes#INSTANCE}，所以本体化工厂看不到这些热解配方。</p>
 */
public class LargeChemicalFactoryRecipes extends ChemicalPlantRecipes {

	public static final LargeChemicalFactoryRecipes INSTANCE = new LargeChemicalFactoryRecipes();

	/** 热解配方的基础热量需求（Tu） */
	public static final int PYRO_BASE_HEAT = 250_000;
	/** 每 tick 工序时长额外增加的热量需求 */
	public static final int PYRO_HEAT_PER_TICK = 500;
	/** 转换后的热解配方热量上限（Tu） */
	public static final int PYRO_MAX_HEAT = 800_000;
	/** 热解配方的等效耗电（EU/t） */
	public static final int PYRO_POWER = 1_000;

	/** 搅拌机转换配方的热量需求（Tu）：不需要加热 */
	public static final int MIXER_HEAT = 0;
	/** 搅拌机转换配方的等效耗电（EU/t） */
	public static final int MIXER_POWER = 500;

	/** Threx 锭的配方内部名 */
	public static final String THREX_RECIPE = "makentmgreatagain.threx_ingot";
	/** Threx 锭需要的内部热量（Tu），机器上限 MAX_HEAT = 1,000,000 */
	public static final int THREX_HEAT = 750_000;
	/** Threx 锭的工序时长（tick） */
	public static final int THREX_DURATION = 1_200;
	/** Threx 锭的等效耗电（HE/t） */
	public static final long THREX_POWER = 200_000L;

	private boolean built;

	/** 只给大型机用的配方：不进本体化工厂的表 */
	private static final Map<String, GenericRecipe> EXCLUSIVE = new LinkedHashMap<String, GenericRecipe>();

	/** 被合并配方收起来的逐条热解配方：不进机器选择列表，NEI 仍逐条展示 */
	private final List<GenericRecipe> hiddenPyro = new ArrayList<GenericRecipe>();

	/** 旧版本里逐条固体燃料配方的内部名 → 合并配方，兼容老存档选中的配方 */
	private final Map<String, String> legacySolidFuelNames = new LinkedHashMap<String, String>();

	/** NEI 展示用：机器表 + 被合并收起来的逐条热解配方 */
	public List<GenericRecipe> neiRecipes() {
		List<GenericRecipe> all = new ArrayList<GenericRecipe>(this.recipeOrderedList);
		all.addAll(this.hiddenPyro);
		return all;
	}

	/** 老存档选中的逐条固体燃料配方名映射到合并配方；其他名字原样返回 */
	public String resolveSelection(String name) {
		String mapped = this.legacySolidFuelNames.get(name);
		return mapped != null ? mapped : name;
	}

	private LargeChemicalFactoryRecipes() { }

	/** 登记一条大型机独占配方（本体化工厂看不到） */
	public static void registerExclusive(GenericRecipe recipe) {
		EXCLUSIVE.put(recipe.getInternalName(), recipe);
	}

	/** 该名字是否已经登记为独占配方 */
	public static boolean hasExclusive(String name) {
		return EXCLUSIVE.containsKey(name);
	}

	/** 配方表重载后清掉缓存，下次访问时重建 */
	public static void invalidate() {
		INSTANCE.built = false;
	}

	/** 本机支持 4 类输入/输出，比本体化工厂的 3 类更宽 */
	@Override public int inputItemLimit() { return 4; }
	@Override public int inputFluidLimit() { return 4; }
	@Override public int outputItemLimit() { return 4; }
	@Override public int outputFluidLimit() { return 4; }

	/** 首次使用时才构建，保证其他配方（包括其它模组后注册的）都已经进表 */
	public static LargeChemicalFactoryRecipes get() {
		if(!INSTANCE.built) INSTANCE.rebuild();
		return INSTANCE;
	}

	public synchronized void rebuild() {

		if(this.built) return;
		this.built = true;

		this.recipeOrderedList.clear();
		this.recipeNameMap.clear();
		this.hiddenPyro.clear();
		this.legacySolidFuelNames.clear();

		// 必须走 register()，否则 recipeNameMap 里没有条目，界面选中后查不到配方。
		// 但要用 RecipeRegistrar.add() 而不是 this.register()：本体表在某些启动顺序下
		// 可能已经带了同名重复条目（HBM 的 register 是先入列表再查重，撞名被上层吃掉后
		// 就会留下幽灵条目），直接 register 会在第二条上抛异常把游戏打崩。
		int skipped = 0;
		for(GenericRecipe recipe : ChemicalPlantRecipes.INSTANCE.recipeOrderedList) {
			// 独占配方不在本体表里，这里只是防止过渡期重复
			if(EXCLUSIVE.containsKey(recipe.getInternalName())) continue;
			if(!RecipeRegistrar.add(this, recipe)) skipped++;
		}

		// 大型机独占配方
		for(GenericRecipe recipe : EXCLUSIVE.values()) {
			if(!RecipeRegistrar.add(this, recipe)) skipped++;
		}

		// Threx 锭：不在 EXCLUSIVE 静态表里，因为那张表可能在 ModItems 还没注册的
		// 时机被读到；放在 rebuild 里构建，此时物品一定已经就绪。
		// 返回 false 只是说明同名配方已存在（invalidate 后重建的第二次），无害。
		RecipeRegistrar.add(this, buildThrexRecipe());

		if(skipped > 0) {
			com.makentmgreatagain.MakeNTMGreatAgain.LOG.warn("[大型催化化工厂] 有 " + skipped
					+ " 条配方与本表重名（本体表里存在重复条目），已跳过而没有崩游戏。"
					+ "这通常说明有配方走了两条注册通路且没判重，搜 RecipeRegistrar 的类注释。");
		}

		int index = 0;
		List<SolidFuelRecipe.Entry> fuelEntries = new ArrayList<SolidFuelRecipe.Entry>();
		int mergedDuration = 60;
		for(PyroOvenRecipe pyro : PyroOvenRecipes.recipes) {
			// 基岩矿的热解不做适配
			if(isBedrockOre(pyro.inputItem)) continue;

			// “单流体 → 一块普通固体燃料”的配方收进合并条目：
			// 机器界面只显示一条，NEI 仍逐条展示（hiddenPyro）
			if(isSolidFuelRecipe(pyro)) {
				GenericRecipe converted = convert(pyro, index);
				this.hiddenPyro.add(converted);
				this.legacySolidFuelNames.put(converted.getInternalName(), SolidFuelRecipe.NAME);
				fuelEntries.add(new SolidFuelRecipe.Entry(pyro.inputFluid.type, pyro.inputFluid.fill));
				mergedDuration = Math.max(mergedDuration, pyro.duration);
				index++;
				continue;
			}

			if(!RecipeRegistrar.add(this, convert(pyro, index++))) skipped++;
		}

		// 合并配方：热量/耗电与其他热解配方同一套规则，时长取各原料配方里的最大值
		if(!fuelEntries.isEmpty()) {
			int heat = Math.min(PYRO_MAX_HEAT, PYRO_BASE_HEAT + mergedDuration * PYRO_HEAT_PER_TICK);
			SolidFuelRecipe merged = new SolidFuelRecipe(heat);
			merged.setup(mergedDuration, PYRO_POWER).setNamed().setIcon(com.hbm.items.ModItems.solid_fuel);
			merged.outputItems(new ItemStack(com.hbm.items.ModItems.solid_fuel));
			merged.setEntries(fuelEntries);
			if(!RecipeRegistrar.add(this, merged)) skipped++;
		}

		// 搅拌机配方也并入：整个大化工可以直接跑搅拌机的活。
		// 实时读表，后续新增的搅拌机配方下次重建时自动出现。
		int mixIndex = 0;
		for(java.util.Map.Entry<FluidType, com.hbm.inventory.recipes.MixerRecipes.MixerRecipe[]> entry
				: com.hbm.inventory.recipes.MixerRecipes.recipes.entrySet()) {
			if(entry.getValue() == null) continue;
			for(com.hbm.inventory.recipes.MixerRecipes.MixerRecipe mix : entry.getValue()) {
				if(mix == null) continue;
				if(!RecipeRegistrar.add(this, convertMixer(entry.getKey(), mix, mixIndex++))) skipped++;
			}
		}

		com.makentmgreatagain.MakeNTMGreatAgain.LOG.info("[大型催化化工厂] 配方表重建完成，共 "
				+ this.recipeOrderedList.size() + " 条（独占 " + EXCLUSIVE.size() + " 条）");
	}

	/** 基岩矿（ItemBedrockOreNew）相关配方跳过 */
	private static boolean isBedrockOre(AStack stack) {
		if(!(stack instanceof ComparableStack)) return false;
		return ((ComparableStack) stack).item instanceof ItemBedrockOreNew;
	}

	/**
	 * 是否是“单流体进、一块普通固体燃料出”的热解配方。
	 * 野火燃料产的是 solid_fuel_bf，不满足条件，保持独立配方。
	 */
	private static boolean isSolidFuelRecipe(PyroOvenRecipe pyro) {
		if(pyro.inputItem != null || pyro.inputFluid == null) return false;
		if(pyro.outputFluid != null || pyro.outputItem == null) return false;
		if(pyro.inputFluid.fill <= 0) return false;
		return pyro.outputItem.getItem() == com.hbm.items.ModItems.solid_fuel
				&& pyro.outputItem.stackSize == 1;
	}

	private static GenericRecipe convert(PyroOvenRecipe pyro, int index) {

		int heat = Math.min(PYRO_MAX_HEAT, PYRO_BASE_HEAT + pyro.duration * PYRO_HEAT_PER_TICK);

		HeatChemicalRecipe recipe = new HeatChemicalRecipe("makentmgreatagain.pyro." + index, heat);
		recipe.setup(Math.max(1, pyro.duration), PYRO_POWER);

		if(pyro.inputItem != null) recipe.inputItems(pyro.inputItem);
		if(pyro.inputFluid != null) recipe.inputFluids(pyro.inputFluid);
		if(pyro.outputItem != null) recipe.outputItems(pyro.outputItem);
		if(pyro.outputFluid != null) recipe.outputFluids(pyro.outputFluid);

		return recipe;
	}

	/**
	 * 搅拌机配方 → 大化工配方。
	 *
	 * <p>搅拌机是「两流体 + 一固体 → 一流体」，正好落在大化工的
	 * 2 流体输入 + 1 物品输入 + 1 流体输出范围内。转换后<b>不需要热量</b>，
	 * 只耗电，与搅拌机本体的行为一致。</p>
	 */
	private static GenericRecipe convertMixer(FluidType outputType,
			com.hbm.inventory.recipes.MixerRecipes.MixerRecipe mix, int index) {

		HeatChemicalRecipe recipe = new HeatChemicalRecipe("makentmgreatagain.mixer." + index, MIXER_HEAT);
		recipe.setup(Math.max(1, mix.processTime), MIXER_POWER);

		if(mix.solidInput != null) recipe.inputItems(mix.solidInput);

		java.util.List<com.hbm.inventory.FluidStack> ins = new java.util.ArrayList<com.hbm.inventory.FluidStack>();
		if(mix.input1 != null) ins.add(mix.input1);
		if(mix.input2 != null) ins.add(mix.input2);
		if(!ins.isEmpty()) recipe.inputFluids(ins.toArray(new com.hbm.inventory.FluidStack[ins.size()]));

		recipe.outputFluids(new com.hbm.inventory.FluidStack(outputType, mix.output));
		recipe.setIcon(com.hbm.items.machine.ItemFluidIcon.make(new com.hbm.inventory.FluidStack(outputType, mix.output)));

		return recipe;
	}

	/**
	 * Threx 锭：四相复合。
	 *
	 * <p>3 能量粉 + 2 Desh 锭 + 2 高强度碳化钨锭 + 2 星辉锭 → 3 Threx 锭。</p>
	 *
	 * <p>这条配方<b>只存在于大型催化化工厂</b>：750,000 Tu 的内部热量已经逼近机器上限
	 * （{@code MAX_HEAT = 1,000,000}），工作台那条同名配方已移除，没有别的捷径。</p>
	 *
	 * <p>四种原料正好占满机器的 4 类输入槽（{@code inputItemLimit() == 4}），
	 * 每类内部 4 格可堆叠，所以 3+2+2+2 不会挤占其它配方。</p>
	 *
	 * <p>原料里只有 Desh 有矿辞（{@code WorkersAlloy}），星辉与附属的高强度碳化钨锭都没有，
	 * 为了四条腿一致，这里统一用 {@link ComparableStack} 按 {@code ItemStack} 精确匹配。</p>
	 */
	private static GenericRecipe buildThrexRecipe() {

		HeatChemicalRecipe recipe = new HeatChemicalRecipe(THREX_RECIPE, THREX_HEAT);
		recipe.setup(THREX_DURATION, THREX_POWER);

		recipe.inputItems(
				new ComparableStack(new ItemStack(com.hbm.items.ModItems.powder_power, 3)),
				new ComparableStack(new ItemStack(com.hbm.items.ModItems.ingot_desh, 2)),
				new ComparableStack(new ItemStack(com.makentmgreatagain.init.ModItems.powder_cobalt_tungsten_ingot, 2)),
				new ComparableStack(new ItemStack(com.hbm.items.ModItems.ingot_starmetal, 2)));

		recipe.outputItems(new ItemStack(com.makentmgreatagain.init.ModItems.ingot_threx, 3));
		recipe.setIcon(new ItemStack(com.makentmgreatagain.init.ModItems.ingot_threx));

		return recipe;
	}
}

package com.makentmgreatagain.init;
// 原版配方类
import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.items.machine.ItemBatteryPack;
import com.hbm.util.Tuple.Pair;
import com.hbm.inventory.recipes.SolidificationRecipes;

import net.minecraft.enchantment.Enchantment;
import static com.hbm.inventory.OreDictManager.AL;
import static com.hbm.inventory.OreDictManager.ANY_RESISTANTALLOY;
import static com.hbm.inventory.OreDictManager.CU;
import static com.hbm.inventory.OreDictManager.DESH;
import static com.hbm.inventory.OreDictManager.FERRO;
import static com.hbm.inventory.OreDictManager.RUBBER;
import static com.hbm.inventory.OreDictManager.STEEL;
import static com.hbm.inventory.OreDictManager.TCALLOY;
import static com.hbm.inventory.OreDictManager.CDALLOY;
import static com.hbm.inventory.OreDictManager.KNO;
import static com.hbm.inventory.OreDictManager.W;
import static com.hbm.inventory.OreDictManager.ANY_COKE;
import static com.hbm.inventory.OreDictManager.ANY_PLASTIC;
import static com.hbm.inventory.OreDictManager.IRON;
import static com.hbm.inventory.OreDictManager.GOLD;
import static com.hbm.inventory.OreDictManager.NB;
import static com.hbm.inventory.OreDictManager.TA;
import static com.hbm.inventory.OreDictManager.LI;
import static com.hbm.inventory.OreDictManager.NA;
import static com.hbm.inventory.OreDictManager.SA326;
import static com.hbm.inventory.OreDictManager.SBD;
import static com.hbm.inventory.OreDictManager.REDSTONE;
import static com.hbm.inventory.OreDictManager.KEY_SAND;
import static com.hbm.inventory.OreDictManager.S;
import static com.hbm.inventory.OreDictManager.CO;
import static com.hbm.inventory.OreDictManager.ANY_PLASTIC;
import static com.hbm.inventory.OreDictManager.ANY_HARDPLASTIC;
import static com.hbm.inventory.OreDictManager.ANY_BISMOIDBRONZE;
import static com.hbm.inventory.OreDictManager.BSCCO;
import static com.hbm.inventory.OreDictManager.MAGTUNG;
import static com.hbm.inventory.OreDictManager.CMB;
import static com.hbm.inventory.OreDictManager.TH232;

import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.OutgasserRecipes;
import com.hbm.inventory.recipes.CombinationRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.PUREXRecipe;
import com.hbm.inventory.recipes.PUREXRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes;
import com.hbm.inventory.recipes.RotaryFurnaceRecipes;
import com.hbm.util.CompatRecipeRegistry;
import com.hbm.inventory.recipes.FractionRecipes;
import com.hbm.inventory.recipes.ElectrolyserMetalRecipes;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.PrecAssRecipes;
import com.hbm.inventory.recipes.BlastFurnaceRecipesNT;
import com.hbm.inventory.recipes.GenericRecipeNoPower;
import com.hbm.inventory.recipes.CrackingRecipes;
import com.hbm.inventory.recipes.HydrotreatingRecipes;
import com.hbm.inventory.recipes.ReformingRecipes;
import com.hbm.inventory.recipes.VacuumRefineryRecipes;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.util.Tuple.Triplet;
import com.hbm.config.GeneralConfig;
import com.hbm.inventory.recipes.AnnihilatorRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.items.ItemEnums.EnumChunkType;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import static com.hbm.inventory.material.Mats.MAT_IRON;
import static com.hbm.inventory.material.MaterialShapes.INGOT;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.blocks.BatteryCellSpec;
import com.makentmgreatagain.recipes.RecipeRegistrar;
import com.makentmgreatagain.recipes.HeatChemicalRecipe;
import com.makentmgreatagain.recipes.LargeCentrifugeRecipes;
import com.makentmgreatagain.recipes.CentrifugeRecipe;
import com.makentmgreatagain.recipes.LargeChemicalFactoryRecipes;
import com.makentmgreatagain.recipes.BioOilChainSpec;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import com.hbm.items.ItemEnums.EnumExpensiveType;
import com.hbm.items.ItemGenericPart.EnumPartType;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 本模组配方总表：原版工作台 / 熔炉 + 全部 HBM 机器配方。
 *
 * 原版三种写法：addShapelessRecipe(输出, 输入...) / addShapedRecipe(输出, "图案", 键值对...) / addSmelting(输入, 输出, 经验)。
 * HBM 机器一律「hasGenericRecipe 判重 -> register -> 一行产物注释」，照抄 addHeavyOilChemicalRecipes() 即可。
 *
 * 新增一条配方的完整流程：
 *   ① 写 addXxx()；② injectAllFallback() 加一行 step("xxx", ModRecipes::addXxx)；
 *   ③ MakeNTMGreatAgain.additionalListeners 对应表的分支挂同一个方法；
 *   ④ zh_CN.lang / en_US.lang 补中文名与英文名（拼音搜索依赖中文名）；
 *   ⑤ 产物若带机器 / 装配机，别忘了 NEI 是靠配方表自动生成的，不需要额外注册。
 *
 * ★ 配方名必须带 {@code makentmgreatagain.} 前缀（唯一历史例外是 crucible.heliumalloy）。
 *   recipes/RecipeLedger 就是靠这个前缀认领「哪些配方是我的」：玩家启用过
 *   config/hbmRecipes 的 json 之后，附属配方会被那份快照冻在旧版本上，
 *   每次启动由账本比对内容指纹后自动升级；玩家自己改过的表则整表保持原样、不动。
 *   改配方名前先读那个类的注释，不要随手换前缀。
 */
public class ModRecipes {

    public static void init() {
		// 空模板
		addBlankTemplateRecipe();
		// 高级合金锭
		addAdvancedAlloyRecipe();
		// Threx 建材（屏蔽砖 / 屏蔽玻璃）
		addThrexBuildingRecipes();
		// 高级合金装备、高强度碳化钨装备
		addAlloyRebornGearRecipes();
		// '中华牌'香烟
		addCigaretteDrxRecipe();
		// 环境地下泵
		addEnvironmentPumpAssemblyRecipe();
		// 睡眠
		addSeaRiseRigRecipe();
		// 集成式换热加热器
		addHeatexRebornAssemblyRecipe();
		// 小型电解机
		addElectrolyzerRebornAssemblyRecipe();
		// 电弧加热器
		addArcHeaterAssemblyRecipe();
		// 铁质大规模存储单元（1000）
		addIronMassStorageRecipe();
		// 白色染料
		addGypsumDyeRecipe();
		// 高级合金锭
		GameRegistry.addSmelting(ModItems.scrap_blades_alloy_reborn,
				new ItemStack(ModItems.ingot_advanced_alloy, 1), 1.0F);
		// 铁锭
		GameRegistry.addSmelting(ModItems.powder_oxidate_iron,
				new ItemStack(Items.iron_ingot, 1), 1.0F);
		// 铒粉 → 铒锭
		GameRegistry.addSmelting(ModItems.powder_erbium,
				new ItemStack(ModItems.ingot_erbium, 1), 1.0F);
		// 铂粉 → 铂锭
		GameRegistry.addSmelting(ModItems.powder_platinum,
				new ItemStack(ModItems.ingot_platinum, 1), 1.0F);
		// 耐摔合金粉 → 耐摔合金锭
		GameRegistry.addSmelting(ModItems.powder_helicopter,
				new ItemStack(ModItems.ingot_helicopter, 1), 1.0F);
		// 氧化锶
		addStrontiumSmeltingRecipe();
		// 电力悬浮作业背包
		addHoverJetpackRecipe();
		// 电叉
		addElectricForkRecipe();
		// 动力装甲电池组 Mk4
		addArmorBatteryMk4Recipe();
		// 动力装甲电池组 Mk5
		addArmorBatteryMk5Recipe();
		// 复合材料储罐
		addCompositeTankRecipe();
		// 自动灭火器
		addAutoExtinguisherRecipe();
		// Threx 锭（四系复合锭）
		// Threx 锭的工作台配方已停用：改由大型催化化工厂独占合成
		// （750,000 Tu / 1200 tick / 200k HE/t，见 LargeChemicalFactoryRecipes#buildThrexRecipe）。
		// 方法体保留在下方，需要恢复工作台捷径时把这一行注释放开即可。
		// addThrexIngotRecipe();
		// 大型催化化工厂的高温裂解配方
		addHeatedCrackingRecipe();
		// 从混合催化分馏塔移植的两条高温配方
		addLargeChemicalFactoryTowerRecipes();
		// 大型催化化工厂装配
		addLargeCatalyticChemicalFactoryRecipe();
		// 次临界核爆炸发生器装配
		addNuclearExplosionGeneratorRecipe();
		// 高级管锚
		addAdvancePointRecipe();
		// 弃用单方块电池/电容升级链
		addBatteryCellUpgradeRecipes();
		// 电池组结构件：外壳 / 端口 / 控制器
		addBatteryArrayStructureRecipes();
		// 地核线（附属两表：催化化工厂 + 大型离心机）
		addCoreLineAddonRecipes();
		// 流体冷却机
		addFluidCoolerRecipe();
		// 排液管
		addMachineDrainRecipe();
		// 大型离心机
		addLargeCentrifugeAssemblyRecipe();
		// 电锅炉（移植原版工作台配方）
		addElectricBoilerRecipe();

		// 深海精矿与浆体处理产线（设计 3 · 分馏塔深档）
		// 尾氯化气、灰尘、铁粉、盐酸、重砂精矿、四氯化钛、纯钽铁矿粉、铌粉、粗铂液、金粉、氯铂酸钾、钍精矿、混合稀土氢氧化物、盐水、小撮钕粉、小撮镧粉、铒粉、钴料液、铜废料、氢氧化钴、二氧化硫、二氧化碳
		addDeepSeaChemicalRecipes();
		// 水、氢气、重砂精矿、阳极泥
		addDeepSeaFluidElectrolyserRecipes();
		// 碱熔砂、蒸汽、稀土熟料、铂粉、氯气
		addDeepSeaPyroRecipes();
		// 重砂混粉、稀土混粉、多金属混粉
		addDeepSeaShredderRecipes();
		// 锆粉、铌粉、铂族精矿、铀粉、小撮钕粉、小撮镧粉、铒富集粉、灰尘、钴粉、铜粉
		addDeepSeaCentrifugeRecipes();
		// 钍晶体、钴晶体
		addDeepSeaCrystallizerRecipes();
		// 钍精矿、混合稀土氢氧化物
		addDeepSeaPUREXRecipes();
		// 四氯化钛、尾氯重馏分
		addDeepSeaFractionRecipe();
		// 铂熔体、金粉
		addDeepSeaMetalElectrolyserRecipe();
		// PEEK 特种工程塑料线：氢氟酸、氟苯、PEEK 锭与粉末
		addPeekChemicalRecipes();
		addPeekShredderRecipes();

		// 流体传送器 / 物品传送器（工作台有序合成）
		addXenTransceiverRecipes();
		addAtomicDeliveryCraftingRecipes();

		// 大型离心机：灰尘、铁粉、铀粉、水、重砂精矿、铂族精矿、氯气、盐酸、四氯化钛、钛粉、锆粉、铍粉、小撮钕粉、小撮镧粉、铈粉、钍粉、铀粉、金粉、铋粉、铋粒、硫粉、粗铂液、镉粉、铜粉、硫酸锌溶液、铂粉、硫酸、氢氧化钴、石膏、盐水、白砷、硫酸钠
		LargeCentrifugeRecipes.registerRecipes();

        MakeNTMGreatAgain.LOG.info("ModRecipes: vanilla recipes registered.");
    }

	/**
	 * Threx 屏蔽砖与屏蔽玻璃。
	 *
	 * <p>屏蔽砖以一锭 Threx 和八块加固 CMB 砖为芯材，压制成 32 块；
	 * 屏蔽玻璃以一锭 Threx 和八块硼玻璃为边框料，制成 16 块观察窗。</p>
	 */
	public static void addThrexBuildingRecipes() {
		// Threx 屏蔽砖：8 × 加固高密度贫铀混凝土 + 1 × Threx 锭 → 32 块
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_shield_brick, 32),
			"CCC",
			"CTC",
			"CCC",
			'C', new ItemStack(com.hbm.blocks.ModBlocks.reinforced_ducrete),
			'T', new ItemStack(ModItems.ingot_threx));

		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_shield_glass, 16),
			"GGG",
			"GTG",
			"GGG",
			'G', new ItemStack(com.hbm.blocks.ModBlocks.glass_boron),
			'T', new ItemStack(ModItems.ingot_threx));

		// Threx 台阶（3 砖 → 6 台阶；2 台阶合回 1 砖）
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_slab, 6),
			"BBB",
			'B', new ItemStack(ModBlocks.threx_shield_brick));
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_shield_brick, 1),
			"S",
			"S",
			'S', new ItemStack(ModBlocks.threx_slab));

		// Threx 楼梯（正反两种摆法，6 砖 → 4 楼梯）
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_stairs, 4),
			"B  ",
			"BB ",
			"BBB",
			'B', new ItemStack(ModBlocks.threx_shield_brick));
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_stairs, 4),
			"  B",
			" BB",
			"BBB",
			'B', new ItemStack(ModBlocks.threx_shield_brick));

		// Threx 瓷砖（2×2 屏蔽砖 → 4 瓷砖）
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_tile, 4),
			"BB",
			"BB",
			'B', new ItemStack(ModBlocks.threx_shield_brick));

		// Threx 装饰块（2×2 瓷砖 → 4 装饰块）
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.threx_deco, 4),
			"TT",
			"TT",
			'T', new ItemStack(ModBlocks.threx_tile));
	}

	/** Threx 容器壁与 Threx 外壳（装配机配方，按名判重） */
	public static void addThrexStructuralAssemblyRecipes() {
		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.threxVessel")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.threxVessel")
				.setup(200, 5_000)
				.outputItems(new ItemStack(ModBlocks.threx_vessel, 1))
				.inputItems(
					new ComparableStack(ModItems.ingot_threx, 3),
					new OreDictStack(MAGTUNG.ingot(), 4),
					new ComparableStack(ModItems.ingot_helium, 4)));
		}

		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.threxCasing")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.threxCasing")
				.setup(200, 5_000)
				.outputItems(new ItemStack(ModBlocks.threx_casing, 1))
				.inputItems(
					new ComparableStack(ModItems.ingot_threx, 2),
					new OreDictStack(W.plateWelded(), 4),
					new ComparableStack(ModItems.ingot_peek, 4)));
		}
	}

	/**
	 * 铁质大规模存储单元（1000）。
	 */
	public static void addIronMassStorageRecipe() {
		GameRegistry.addShapedRecipe(
			new ItemStack(ModBlocks.iron_mass_storage, 1),
			" B ",
			"IAI",
			" I ",
			'B', new ItemStack(com.hbm.items.ModItems.plate_iron, 1),
			'I', new ItemStack(Items.iron_ingot, 1),
			'A', new ItemStack(com.hbm.blocks.ModBlocks.crate_iron, 1)
		);
	}

	/**
	 * Threx 锭：3 能量粉 + 2 Desh 锭 + 2 高强度碳化钨锭 + 2 星辉锭 → 3 个。
	 *
	 * <p>正好占满 3×3：顶行三撮能量粉是「焊剂」，下两行 D/S/T 三列交替，
	 * 摆位本身就是四相复合的意思，所以用有序配方而不是无序。</p>
	 *
	 * <p>四种原料里只有 Desh 有矿辞（{@code WorkersAlloy}），星辉与附属碳化钨锭
	 * 都没有登记矿辞，为了四条腿一致，这里统一写 {@code ItemStack}，
	 * 不混用 {@code DESH.ingot()}。</p>
	 *
	 * <p>产物按需求<b>不带说明文字</b>，只在语言文件里给了名字，
	 * 15 m 抗常规核爆 / 隔绝辐射 / 抗腐蚀这三条目前纯属设定，没有挂载任何判定。</p>
	 */
	public static void addThrexIngotRecipe() {
		// 注意：当前未从 init() 调用。Threx 锭已改为大型催化化工厂独占配方，
		// 这里是保留下来的工作台版本，仅用于需要恢复捷径时快速启用。
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModItems.ingot_threx, 3),
			"PPP",
			"DST",
			"DST",
			'P', new ItemStack(com.hbm.items.ModItems.powder_power, 1),
			'D', new ItemStack(com.hbm.items.ModItems.ingot_desh, 1),
			'S', new ItemStack(ModItems.powder_cobalt_tungsten_ingot, 1),
			'T', new ItemStack(com.hbm.items.ModItems.ingot_starmetal, 1)));
	}

	/**
	 * 电锅炉（移植原版 1.0.27 工作台配方）：
	 * S=Desh 锭、P=铜电路板(PCB)、T=钢流体罐、F=电炉。
	 * 原版 board_copper 在当前本体对应 circuit 的 PCB 元数据。
	 */
	public static void addElectricBoilerRecipe() {
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.machine_electric_boiler_off, 1),
			"SPS", "TFT", "SPS",
			'S', DESH.ingot(),
			'P', new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.PCB.ordinal()),
			'T', new ItemStack(com.hbm.items.ModItems.tank_steel, 1),
			'F', new ItemStack(com.hbm.blocks.ModBlocks.machine_electric_furnace_off, 1)));
	}

	/** 高级管锚 */
	public static void addAdvancePointRecipe() {

		GameRegistry.addShapedRecipe(new ItemStack(ModBlocks.pipe_anchor_advanced, 2),
			" S ",
			"SCS",
			" S ",
			'S', com.hbm.blocks.ModBlocks.pipe_anchor,                  // 电棍
			'C', com.hbm.items.ModItems.tank_steel);              // 聚合物锭
	}

	/**
	 * 弃用单方块电池 / 电容的合成。
	 */
	public static void addBatteryCellUpgradeRecipes() {

		ItemStack redstone = new ItemStack(Items.redstone);
		ItemStack copperCoil = new ItemStack(com.hbm.items.ModItems.coil_copper);
		ItemStack capacitorCircuit = new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CAPACITOR.ordinal());

		// ---- 基础级：工作台有序合成 ----
		// 普通蓄电池：钢板外壳 + 铜线圈 + 红石
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.batteryCells.get(BatteryCellSpec.BATTERY.name)),
			"PCP", "PRP", "PCP",
			'P', STEEL.plate(), 'C', copperCoil, 'R', redstone));
		// 金电容：金板 + 电容电路 + 红石
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.batteryCells.get(BatteryCellSpec.GOLD_CAPACITOR.name)),
			"GCG", "CRC", "GCG",
			'G', GOLD.plate(), 'C', capacitorCircuit, 'R', redstone));

		// ---- 高级：装配机 ----
		// 不在这里注册：HBM 的装配机表会在 init 之后从 JSON 重建，
		// init 阶段注册的条目会被冲掉（这就是「其他电池没有配方」的原因）。
		// 统一走 injectAllFallback() 的 step("batteryCellAssembly", ...) 兜底通路。
	}

	/**
	 * 高级电池 / 电容的装配机配方（造价在原版基础上略微提高）。
	 * public：装配机重载监听也要调（/ntmreload 后不消失）。
	 */
	public static void addBatteryCellAssemblyRecipes() {

		// 锂电池：原版 锂粉12+钴粉8+聚合物4 → 提到 16+8+8，另需普通电池
		registerCellAssembly("batteryLithium", BatteryCellSpec.LITHIUM_BATTERY, 200, 2_000,
			new OreDictStack(LI.dust(), 16),
			new OreDictStack(CO.dust(), 8),
			new OreDictStack(ANY_PLASTIC.ingot(), 8),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.BATTERY.name), 1));

		// 钠离子：原版 钠粉24+铁粉24+硬塑料12 → 提到 30+30+16，另需锂电池 + 金线圈
		registerCellAssembly("batterySodium", BatteryCellSpec.SODIUM_BATTERY, 300, 20_000,
			new OreDictStack(NA.dust(), 24),
			new OreDictStack(IRON.dust(), 24),
			new OreDictStack(ANY_HARDPLASTIC.ingot(), 16),
			new ComparableStack(com.hbm.items.ModItems.coil_gold, 8),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.LITHIUM_BATTERY.name), 1));

		// Sa326：原版 Sa326粉24+铋青铜铸板8 → 提到 30+8，另需钠离子 + 钨线圈
		registerCellAssembly("batterySchrabidium", BatteryCellSpec.SCHRABIDIUM_BATTERY, 400, 50_000,
			new OreDictStack(SA326.dust(), 24),
			new OreDictStack(ANY_BISMOIDBRONZE.plateCast(), 8),
			new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 8),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.SODIUM_BATTERY.name), 1));

		// 量子：原版 超导线24+带电粒子32+CFT锭16 → 提到 30+36+20，另需 Sa326
		registerCellAssembly("batteryQuantum", BatteryCellSpec.QUANTUM_BATTERY, 600, 200_000,
			new OreDictStack(BSCCO.wireDense(), 24),
			new ComparableStack(com.hbm.items.ModItems.pellet_charged, 36),
			new ComparableStack(com.hbm.items.ModItems.ingot_cft, 16),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.SCHRABIDIUM_BATTERY.name), 1));

		// 铌电容：原版 聚合物12+铌密线24 → 提到 16+30，另需金电容
		registerCellAssembly("capacitorNiobium", BatteryCellSpec.NIOBIUM_CAPACITOR, 200, 2_000,
			new OreDictStack(ANY_PLASTIC.ingot(), 16),
			new OreDictStack(NB.wireDense(), 24),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.GOLD_CAPACITOR.name), 1));

		// 钽电容：原版 硬塑料16+钽锭24 → 提到 20+30，另需铌电容
		registerCellAssembly("capacitorTantalium", BatteryCellSpec.TANTALIUM_CAPACITOR, 300, 20_000,
			new OreDictStack(ANY_HARDPLASTIC.ingot(), 16),
			new OreDictStack(TA.ingot(), 24),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.NIOBIUM_CAPACITOR.name), 1));

		// Sa酸铁电容：原版无配方，按钽档再上一级定价，另需钽电容
		registerCellAssembly("capacitorSchrabidate", BatteryCellSpec.SCHRABIDATE_CAPACITOR, 400, 50_000,
			new OreDictStack(SBD.ingot(), 24),
			new OreDictStack(ANY_BISMOIDBRONZE.plateCast(), 16),
			new ComparableStack(ModBlocks.batteryCells.get(BatteryCellSpec.TANTALIUM_CAPACITOR.name), 1));
	}

	/** 注册一条元件装配机配方（统一命名前缀，避免与原版冲突）。 */
	private static void registerCellAssembly(String key, BatteryCellSpec spec,
			int ticks, int power, com.hbm.inventory.RecipesCommon.AStack... inputs) {

		String name = "makentmgreatagain.cell." + key;
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, name)) return;

		GenericRecipe recipe = new GenericRecipe(name)
			.setup(ticks, power)
			.outputItems(new ItemStack(ModBlocks.batteryCells.get(spec.name)))
			.inputItems(inputs);

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/**
	 * 电池组结构件：外壳 / 端口 / 控制器，全部有序合成。
	 */
	public static void addBatteryArrayStructureRecipes() {

		ItemStack capacitorCircuit = new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CAPACITOR.ordinal());
		ItemStack analogCircuit = new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.ANALOG.ordinal());
		ItemStack copperCoil = new ItemStack(com.hbm.items.ModItems.coil_copper);

		// 外壳：铝板围一圈 + 钢板做芯（一次出 4 个，铺满一个面够用）
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.battery_array_casing, 4),
			"PPP", "PIP", "PPP",
			'P', AL.plate(), 'I', STEEL.plate()));

		// 端口：外壳 + 钨线导电 + 铜线圈 + 电容电路稳压（一次出 2 个）
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.battery_array_port, 2),
			"WCW", "CSC", "WCW",
			'W', new ItemStack(com.hbm.items.ModItems.wire_fine, 1, Mats.MAT_TUNGSTEN.id), 'C', copperCoil,
			'S', ModBlocks.battery_array_casing));

		// 控制器：外壳 + 两个模拟电路板 + 电容电路 + 红石
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.battery_array_controller, 1),
			"ARA", "SCS", "ARA",
			'A', analogCircuit, 'R', new ItemStack(Items.redstone),
			'S', ModBlocks.battery_array_casing, 'C', capacitorCircuit));
	}
	
	// ==================== 地核开采产线 ====================
	//
	// 单台开采机满负荷：原浆 12,000 mB/min + 熔体 6,000 mB/min（合计 15 mB/t）。
	// 下面所有配方的批量都按这个流量配平。
	//
	// 注册通路分两种，别搞混：
	//   · 附属两表（大型催化化工厂 / 大型离心机）不读 JSON，init 阶段注册即可；
	//     化工厂若表已构建过，需要 invalidate() + get() 重建。
	//   · 原版四表（固化 / 流体电解 / 金属电解 / 混合）会被 HBM 从 JSON 重建，
	//     必须走 postInit 的 injectAllFallback()，否则注册完就被冲掉。

	/**
	 * 地核线 · 附属表部分：熔开 + 2PU 离心 + 铁系/铂系催化 + 锇酸。
	 *
	 * <p>全部用 {@link LargeChemicalFactoryRecipes#hasExclusive} 判重，可重复调用。</p>
	 */
	public static void addCoreLineAddonRecipes() {

		FluidType slurry = addonFluid(ModFluids.CORE_SLURRY);
		FluidType melt = addonFluid(ModFluids.CORE_MELT);
		FluidType ferro = addonFluid(ModFluids.FERRO_MELT);
		FluidType pgm = addonFluid(ModFluids.PGM_MELT);
		FluidType slag = addonFluid(ModFluids.HEAVY_SLAG);
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);

		// 附属流体没注册成功就整条跳过，避免写出 type=NONE 的死配方
		if(slurry == Fluids.NONE || melt == Fluids.NONE || ferro == Fluids.NONE
				|| pgm == Fluids.NONE || slag == Fluids.NONE) {
			MakeNTMGreatAgain.LOG.warn("地核线流体未就绪，跳过附属配方注册");
			return;
		}

		// ---- 阶段 0：原浆熔开（100t/批）：原浆1000 → 熔体1000 + SO₂200 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.core.smelt")) {
			LargeChemicalFactoryRecipes.registerExclusive(
				new HeatChemicalRecipe("makentmgreatagain.core.smelt", 500_000)
					.setup(100, 20_000)
					.inputFluids(new FluidStack(slurry, 1_000))
					.outputFluids(new FluidStack(melt, 1_000), new FluidStack(so2, 200)));
		}

		// ---- A1 铁系磁化（200t/批）：铁系500 + 三硫化Sa200 → 磁化钨粉1 + 高钴精矿2 + 铁晶体2 ----
		// 受 B1 的 Sa 产量限制，实际只跑 18 批/min（需 Sa 4,320 但只产 3,600）
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.core.ferroMag")) {
			LargeChemicalFactoryRecipes.registerExclusive(
				new HeatChemicalRecipe("makentmgreatagain.core.ferroMag", 400_000, 3)
					.setup(200, 40_000)
					.inputFluids(new FluidStack(ferro, 500), new FluidStack(Fluids.SAS3, 200))
					.outputItems(
						new ItemStack(com.hbm.items.ModItems.powder_magnetized_tungsten, 1),
						new ItemStack(ModItems.cobalt_polymetallic_concentrate, 2),
						new ItemStack(com.hbm.items.ModItems.crystal_iron, 2)));
		}

		// ---- B1 铂系酸化（200t/批）：铂系300 + Sa酸500 → 三硫化Sa200 + Sa酸铁锭1 + 铂残渣2 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.core.pgmAcid")) {
			HeatChemicalRecipe acid = new HeatChemicalRecipe("makentmgreatagain.core.pgmAcid", 400_000, 3);
			acid.setup(200, 60_000);
			acid.inputFluids(new FluidStack(pgm, 300), new FluidStack(Fluids.SCHRABIDIC, 500));
			acid.outputFluids(new FluidStack(Fluids.SAS3, 200));
			acid.outputItems(
				new ItemStack(com.hbm.items.ModItems.ingot_schrabidate, 1),
				new ItemStack(ModItems.core_platinum_residue, 2));
			LargeChemicalFactoryRecipes.registerExclusive(acid);
		}

		// ---- B2 铂系氯化（200t/批，替代B1）：铂系300 + 氯气500 → 氯铂酸钾4 + 铂残渣3 + 盐酸300 ----
		FluidType hcl = HBMCompat.hcl();
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.core.pgmChlor")) {
			HeatChemicalRecipe chlor = new HeatChemicalRecipe("makentmgreatagain.core.pgmChlor", 250_000, 2);
			chlor.setup(200, 40_000);
			chlor.inputFluids(new FluidStack(pgm, 300), new FluidStack(Fluids.CHLORINE, 500));
			chlor.outputItems(
				new ItemStack(ModItems.potassium_chloroplatinate, 4),
				new ItemStack(ModItems.core_platinum_residue, 3));
			if(hcl != Fluids.NONE) chlor.outputFluids(new FluidStack(hcl, 300));
			LargeChemicalFactoryRecipes.registerExclusive(chlor);
		}

		// ---- C 锇酸解锁（200t/批，唯一消耗星流浆）：铂残渣4 + 星流浆100 → 锇酸250 ----
		// 热量拉满到化工厂上限 1,000,000 TU（MAX_HEAT），这是整条线最苛刻的一步
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.core.osmic")) {
			LargeChemicalFactoryRecipes.registerExclusive(
				new HeatChemicalRecipe("makentmgreatagain.core.osmic", 1_000_000, 3)
					.setup(200, 100_000)
					.inputItems(new ComparableStack(ModItems.core_platinum_residue, 4))
					.inputFluids(new FluidStack(Fluids.STELLAR_FLUX, 100))
					.outputFluids(new FluidStack(Fluids.DEATH, 250)));
		}

		addCoreCentrifugeRecipes(melt, ferro, pgm, slag);
	}

	/**
	 * 地核线的离心机配方。
	 *
	 * <p>主分离要求进料罐 <b>2PU</b>（{@code .pressure(2)}）；渣浆与残渣回收两条不限压。</p>
	 */
	private static void addCoreCentrifugeRecipes(FluidType melt, FluidType ferro, FluidType pgm, FluidType slag) {

		// 主分离：熔体1000 → 铁系600 + 铂系300 + 渣浆100 + 钍精矿1
		if(!hasCentrifugeFluid(melt, 2)) {
			LargeCentrifugeRecipes.RECIPES.add(new CentrifugeRecipe()
				.fluid(new FluidStack(melt, 1_000)).pressure(2)
				.outFluids(new FluidStack(ferro, 600), new FluidStack(pgm, 300), new FluidStack(slag, 100))
				.outItems(new ItemStack(ModItems.thorium_concentrate, 1))
				.power(50_000L).duration(200));
		}

		// D 渣浆：渣浆500 → 铋粉2 + 铅粉2 + 石膏1
		if(!hasCentrifugeFluid(slag, 0)) {
			LargeCentrifugeRecipes.RECIPES.add(new CentrifugeRecipe()
				.fluid(new FluidStack(slag, 500))
				.outItems(
					new ItemStack(com.hbm.items.ModItems.powder_bismuth, 2),
					new ItemStack(com.hbm.items.ModItems.powder_lead, 2),
					new ItemStack(ModItems.gypsum, 1))
				.power(20_000L).duration(100));
		}

		// 残渣回收（替代C，放弃锇酸）：铂残渣4 → 炉渣锭2 + 铅粉1
		if(!hasCentrifugeItem(ModItems.core_platinum_residue)) {
			LargeCentrifugeRecipes.RECIPES.add(new CentrifugeRecipe()
				.items(new ComparableStack(ModItems.core_platinum_residue, 4))
				.outItems(
					new ItemStack(com.hbm.items.ModItems.ingot_raw, 2, Mats.MAT_SLAG.id),
					new ItemStack(com.hbm.items.ModItems.powder_lead, 1))
				.power(20_000L).duration(100));
		}
	}

	/** 离心机是否已有「吃这种流体且压力要求相同」的配方（判重，防 /ntmreload 重复注册）。 */
	private static boolean hasCentrifugeFluid(FluidType type, int pressure) {
		for(CentrifugeRecipe rec : LargeCentrifugeRecipes.RECIPES) {
			if(rec.inputFluid != null && rec.inputFluid.type == type
					&& rec.inputFluid.pressure == pressure) return true;
		}
		return false;
	}

	/** 离心机是否已有吃这个物品的配方。 */
	private static boolean hasCentrifugeItem(Item item) {
		for(CentrifugeRecipe rec : LargeCentrifugeRecipes.RECIPES) {
			if(rec.matchesItem(new ItemStack(item))) return true;
		}
		return false;
	}

	/**
	 * 地核线 · 原版四表部分（必须走 postInit 兜底，否则被 JSON 重建冲掉）。
	 */
	public static void addCoreLineVanillaRecipes() {

		FluidType slurry = addonFluid(ModFluids.CORE_SLURRY);
		FluidType ferro = addonFluid(ModFluids.FERRO_MELT);
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType cobalt = addonFluid(ModFluids.COBALT_LIQUOR);
		FluidType hcl = HBMCompat.hcl();

		// ---- A2 固化：铁系1000 → 铁晶体2（耗时耗电由机器固定，配方不可指定）----
		if(ferro != Fluids.NONE) {
			SolidificationRecipes.recipes.put(ferro,
				new Pair<Integer, ItemStack>(1_000, new ItemStack(com.hbm.items.ModItems.crystal_iron, 2)));
		}

		// ---- A3 金属电解：铁晶体1 → 熔融铁12锭当量 + 熔融钛2 + 小撮锂粉3 ----
		// 原版是 6 锭铁 + 2 锭钛，这里翻倍以体现「A3 产铁远多于 A1」。
		// INGOT.q(1) = 72 单位，q(12) 即 12 锭当量。
		ElectrolyserMetalRecipes.recipes.put(new ComparableStack(com.hbm.items.ModItems.crystal_iron),
			new ElectrolyserMetalRecipes.ElectrolysisMetalRecipe(
				new MaterialStack(Mats.MAT_IRON, INGOT.q(12)),
				new MaterialStack(Mats.MAT_TITANIUM, INGOT.q(2)),
				new ItemStack(com.hbm.items.ModItems.powder_lithium_tiny, 3)));

		if(slurry == Fluids.NONE) return;

		// ---- 原浆替代一：流体电解（100t/批）：原浆1000 → SO₂200 + 盐酸200 + 铁粉4 + 阳极泥2 ----
		ElectrolyserFluidRecipes.recipes.put(slurry,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1_000,
				new FluidStack(so2, 200),
				new FluidStack(hcl == Fluids.NONE ? Fluids.WATER : hcl, 200),
				100,
				new ItemStack(com.hbm.items.ModItems.powder_iron, 4),
				new ItemStack(ModItems.anode_mud, 2)));

		// ---- 原浆替代二：混合浸出（100t/批）：原浆1000 + 盐酸500 → 钴料液600 ----
		// 混合机只能输出流体（表键即产物 FluidType），所以没有「铁粉」副产。
		if(cobalt != Fluids.NONE && hcl != Fluids.NONE) {
			AddonMixerRecipe mix = new AddonMixerRecipe(600, 100);
			mix.input1 = new FluidStack(slurry, 1_000);
			mix.input2 = new FluidStack(hcl, 500);
			MixerRecipes.register(cobalt, mix);
		}
	}

	/** 电力高炉（装配机，双模式） */
	public static void addElectricBlastFurnaceRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.electricBlastFurnace")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.electricBlastFurnace")
			.setup(1200, 50_000)
			.outputItems(new ItemStack(ModBlocks.machine_electric_blast_furnace))
			.inputItems(
				new ComparableStack(ModItems.powder_cobalt_tungsten_ingot, 32),
				new OreDictStack(W.plateCast(), 16),
				new ComparableStack(com.hbm.blocks.ModBlocks.machine_transformer, 4),
				new ComparableStack(com.hbm.items.ModItems.coil_gold_torus, 16),
				new ComparableStack(com.hbm.items.ModItems.circuit, 16, EnumCircuitType.BASIC),
				new OreDictStack(CU.plate(), 64),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 64));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new ComparableStack(ModItems.powder_cobalt_tungsten_ingot, 32),
				new OreDictStack(W.plateCast(), 16),
				new ComparableStack(com.hbm.blocks.ModBlocks.machine_transformer, 8),
				new ComparableStack(com.hbm.items.ModItems.coil_gold_torus, 16),
				new OreDictStack(CU.plate(), 64),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 64),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.CIRCUIT),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/**
	 * 地核熔岩开采机（装配机，双模式）。
	 *
	 * <p>正常：铸造CMB板4 + 致密BSCCO线64 + 焊接钨板16 + CMB激光晶体4
	 * + 高级控制单元2 + 采矿激光1 + 铋马达8。</p>
	 *
	 * <p>昂贵：CMB板翻到16、焊接板换成锝钢/镉钢等位组、控制单元4，另加青铜结构件3。</p>
	 */
	public static void addCoreLavaExtractorRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.coreLavaExtractor")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.coreLavaExtractor")
			.setup(1200, 50_000)
			// 528 模式：需先烧 1024 铂锭拿到蓝图才能装配（见 addCoreLava528Annihilator）
			.setPools528(GenericRecipes.POOL_PREFIX_528 + "corelava")
			.outputItems(new ItemStack(ModBlocks.core_lava_extractor))
			.inputItems(
				new OreDictStack(CMB.plateCast(), 4),
				new OreDictStack(BSCCO.wireDense(), 64),
				new OreDictStack(W.plateWelded(), 16),
				// 激光晶体 setMaxStackSize(1)，配方里最多只能要 1 个，写多了注册会抛异常
				new ComparableStack(com.hbm.items.ModItems.laser_crystal_cmb, 1),
				new ComparableStack(com.hbm.items.ModItems.circuit, 2, EnumCircuitType.CONTROLLER_ADVANCED),
				new ComparableStack(com.hbm.blocks.ModBlocks.machine_mining_laser, 1),
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 8));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new OreDictStack(CMB.plateCast(), 16),
				new OreDictStack(BSCCO.wireDense(), 64),
				// 锝钢与镉钢等位替代，原版就是这个焊接板矿辞组
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 16),
				new ComparableStack(com.hbm.items.ModItems.laser_crystal_cmb, 1),
				new ComparableStack(com.hbm.items.ModItems.circuit, 4, EnumCircuitType.CONTROLLER_ADVANCED),
				new ComparableStack(com.hbm.blocks.ModBlocks.machine_mining_laser, 1),
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 8),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 3, EnumExpensiveType.BRONZE_TUBES));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: core lava extractor");
	}

	/**
	 * 528 模式：地核开采机蓝图里程碑。
	 *
	 * <p>湮灭炉累计烧掉 1024 个铂锭 → 发 {@code 528corelava} 蓝图，
	 * 与 {@link #addCoreLavaExtractorRecipe()} 的 {@code setPools528} 对应。
	 * 非 528 不注册（装配配方也无门槛）。挂在 AnnihilatorRecipes 的
	 * onRecipeLoad 监听里，保证 /ntmreload 重建默认表后仍在。</p>
	 */
	public static void addCoreLava528Annihilator() {
		if(!GeneralConfig.enable528) return;
		String pool = GenericRecipes.POOL_PREFIX_528 + "corelava";
		AnnihilatorRecipes.recipes.put(ModItems.ingot_platinum,
			new AnnihilatorRecipes.AnnihilatorRecipe(
				new Pair<java.math.BigInteger, ItemStack>(new java.math.BigInteger("1024"), ItemBlueprints.make(pool))));
		MakeNTMGreatAgain.LOG.info("Added 528 annihilator milestone: 1024x platinum ingot -> core lava extractor blueprint");
	}

	/** 仿星器（装配机，528 模式下需先烧 1024 个反Sa326单元获取蓝图） */
	public static void addStellaratorRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.stellarator")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.stellarator")
			.setup(1200, 100_000)
			.setPools528(GenericRecipes.POOL_PREFIX_528 + "stellarator")
			.outputItems(new ItemStack(ModBlocks.stellarator))
			.inputItems(
				new ComparableStack(ModBlocks.threx_casing, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.fusion_component, 64),
				new OreDictStack(com.hbm.inventory.OreDictManager.MAGTUNG.wireDense(), 64),
				new ComparableStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CONTROLLER_QUANTUM.ordinal()),
				new ComparableStack(ModCoatings.get("threx"), 1),
				new ComparableStack(ModBlocks.threx_vessel, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.cm_flux, 32))
			.inputItemsEx(
				new ComparableStack(ModBlocks.threx_casing, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.fusion_component, 64),
				new OreDictStack(com.hbm.inventory.OreDictManager.MAGTUNG.wireDense(), 64),
				new ComparableStack(com.hbm.items.ModItems.circuit, 16, EnumCircuitType.CONTROLLER_QUANTUM.ordinal()),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 16, EnumExpensiveType.BRONZE_TUBES.ordinal()),
				new ComparableStack(ModCoatings.get("threx"), 1),
				new ComparableStack(ModBlocks.threx_vessel, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.cm_flux, 32));

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/** 等离子加热器（装配机，528 模式下与仿星器共用同一张蓝图） */
	public static void addPlasmaHeaterRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.plasmaHeater")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.plasmaHeater")
			.setup(800, 50_000)
			.setPools528(GenericRecipes.POOL_PREFIX_528 + "stellarator")
			.outputItems(new ItemStack(ModBlocks.plasma_heater))
			.inputItems(
				new ComparableStack(ModBlocks.threx_casing, 16),
				new ComparableStack(com.hbm.items.ModItems.pa_coil, 1, com.hbm.items.machine.ItemPACoil.EnumCoilType.BSCCO.ordinal()),
				new ComparableStack(com.hbm.items.ModItems.magnetron, 64),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 64))
			.inputItemsEx(
				new ComparableStack(ModBlocks.threx_casing, 16),
				new ComparableStack(com.hbm.items.ModItems.pa_coil, 1, com.hbm.items.machine.ItemPACoil.EnumCoilType.BSCCO.ordinal()),
				new ComparableStack(com.hbm.items.ModItems.magnetron, 64),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.machine_microwave, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.pa_rfc, 16));

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/**
	 * 528 模式：仿星器 & 等离子加热器共用蓝图里程碑。
	 *
	 * <p>湮灭炉累计烧掉 1024 个反Sa326单元 → 发放 {@code 528stellarator} 蓝图。</p>
	 */
	public static void addStellarator528Annihilator() {
		if(!GeneralConfig.enable528) return;
		String pool = GenericRecipes.POOL_PREFIX_528 + "stellarator";
		AnnihilatorRecipes.recipes.put(com.hbm.items.ModItems.cell_anti_schrabidium,
			new AnnihilatorRecipes.AnnihilatorRecipe(
				new Pair<java.math.BigInteger, ItemStack>(new java.math.BigInteger("1024"), ItemBlueprints.make(pool))));
		MakeNTMGreatAgain.LOG.info("Added 528 annihilator milestone: 1024x anti-schrabidium cell -> stellarator blueprint");
	}

	/**
	 * 粉碎的魔法（酸化机 / 矿物酸化器）。
	 *
	 * <p>末影果汁 1000mB + 青金石粉 1 → 粉碎的魔法 1。
	 * 走 {@link CrystallizerRecipes}，与末影珍珠那条同一台机器。</p>
	 */
	public static void addMagicPowderRecipe() {
		CrystallizerRecipes.CrystallizerRecipe recipe =
			new CrystallizerRecipes.CrystallizerRecipe(new ItemStack(com.hbm.items.ModItems.powder_magic, 1), 600);

		CrystallizerRecipes.registerRecipe(new ComparableStack(com.hbm.items.ModItems.powder_lapis), recipe,
			new FluidStack(Fluids.ENDERJUICE, 1_000));

		MakeNTMGreatAgain.LOG.info("Added crystallizer recipe: ender juice + lapis powder -> magic powder");
	}

	/** 稳定X晶体 + 经验汁 → 人造X晶体（矿物酸化器）。registerRecipe 按(输入,酸类型)put，天然幂等 */
	public static void addXenCrystalAcidRecipe() {
		CrystallizerRecipes.CrystallizerRecipe recipe =
			new CrystallizerRecipes.CrystallizerRecipe(new ItemStack(com.hbm.items.ModItems.crystal_xen, 1), 600);
		CrystallizerRecipes.registerRecipe(new ComparableStack(ModItems.crystal_xen), recipe,
			new FluidStack(Fluids.XPJUICE, 1_000));
	}

	/**
	 * 稳定X晶体 + 可见光 → 概率分离（SILEX）。
	 * 权重 10/4/1/10（总25）：不稳态40% / 亚稳态16% / 乱码4% / 灰尘40%。
	 * 失败项出「灰尘」而不是回料稳定晶体 —— 否则分离机可以自循环，一份晶体反复喂进去直到出好东西。
	 * SILEX 表为 private 且无公开 register，反射 put；乱码晶体的 NEI 隐藏由
	 * {@code compat/nei/SILEXHiddenHandler} 负责，机器分离不受影响。
	 */
	@SuppressWarnings("unchecked")
	public static void addXenCrystalSILEXRecipe() {
		try {
			java.lang.reflect.Field f = com.hbm.inventory.recipes.SILEXRecipes.class.getDeclaredField("recipes");
			f.setAccessible(true);
			java.util.HashMap<Object, com.hbm.inventory.recipes.SILEXRecipes.SILEXRecipe> map =
				(java.util.HashMap<Object, com.hbm.inventory.recipes.SILEXRecipes.SILEXRecipe>) f.get(null);
			ComparableStack key = new ComparableStack(ModItems.crystal_xen);
			if(map.containsKey(key)) return;
			map.put(key, new com.hbm.inventory.recipes.SILEXRecipes.SILEXRecipe(900, 100,
					com.hbm.items.machine.ItemFELCrystal.EnumWavelengths.GAMMA)
				.addOut(new ItemStack(ModItems.crystal_xen_unstable), 10)
				.addOut(new ItemStack(ModItems.crystal_xen_metastable), 4)
				.addOut(new ItemStack(ModItems.crystal_xen_glitch), 1)
				.addOut(new ItemStack(com.hbm.items.ModItems.dust, 1), 10));
		} catch(Exception e) {
			MakeNTMGreatAgain.LOG.warn("[配方] SILEX 稳定X晶体配方反射失败", e);
		}
	}

	/** 电叉 */
	public static void addElectricForkRecipe() {
		
		ItemStack boltHighSpeed = new ItemStack(com.hbm.items.ModItems.bolt, 1, Mats.MAT_DURA.id);
		ItemStack wireCopper = new ItemStack(com.hbm.items.ModItems.wire_fine, 1, Mats.MAT_MINGRADE.id);
		ItemStack capacitor = new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CAPACITOR.ordinal());

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.electric_fork, 1),
				"XAX",
				"BCB",
				"DSD",
				'X', boltHighSpeed,                                    // 高速钢螺栓
				'A', wireCopper,                                        // 紫铜线
				'B', com.hbm.items.ModItems.coil_gold,                  // 金线圈
				'C', com.hbm.items.ModItems.elec_sword,                  // 电棍
				'D', capacitor,                                          // 电容器
				'S', com.hbm.items.ModItems.ingot_polymer);              // 聚合物锭
	}

	/** 动力装甲电池组 Mk4 */
	public static void addArmorBatteryMk4Recipe() {
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.armor_battery_mk4, 1),
			"XAX",
			"XBX",
			"XCX",
			'X', com.hbm.items.ModItems.plate_saturnite,
			'A', com.hbm.items.ModItems.armor_battery_mk2,
			'B', new ItemStack(com.hbm.items.ModItems.battery_pack, 1, com.hbm.items.machine.ItemBatteryPack.EnumBatteryPack.BATTERY_SODIUM.ordinal()),
			'C', com.hbm.items.ModItems.fusion_core);
	}
	
	/** 动力装甲电池组 Mk5 */
	public static void addArmorBatteryMk5Recipe() {
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.armor_battery_mk5, 1),
			"XAX",
			"XBX",
			"XAX",
			'X', com.hbm.items.ModItems.plate_combine_steel,
			'A', ModItems.armor_battery_mk4,
			'B', new ItemStack(com.hbm.items.ModItems.battery_pack, 1, ItemBatteryPack.EnumBatteryPack.CAPACITOR_SPARK.ordinal()));
	}
	/** 复合材料储罐（ingotPolymer / ingotBakelite 两条图案） */
	public static void addCompositeTankRecipe() {

		ItemStack tungstenCrate = new ItemStack(com.hbm.blocks.ModBlocks.crate_tungsten, 1);
		ItemStack reacher = new ItemStack(com.hbm.items.ModItems.reacher, 1);
		ItemStack fiberglass = new ItemStack(com.hbm.items.ModItems.ingot_fiberglass, 1);

		String[] plastics = new String[] { "ingotPolymer", "ingotBakelite" };

		for(String plastic : plastics) {
			GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.composite_tank, 1),
					"%+%",
					"-@-",
					"%-%",
					'%', plastic,
					'+', reacher,
					'-', fiberglass,
					'@', tungstenCrate));
		}
	}

	/** 自动灭火器 */
	public static void addAutoExtinguisherRecipe() {

		ItemStack steelTank = new ItemStack(com.hbm.items.ModItems.tank_steel, 1);
		ItemStack aluPlate = new ItemStack(com.hbm.items.ModItems.plate_aluminium, 1);
		ItemStack barrel = new ItemStack(com.hbm.items.ModItems.fluid_barrel_empty, 1);
		ItemStack thruster = new ItemStack(com.hbm.items.ModItems.thruster_small, 1);

		for(String plastic : new String[] { "ingotPolymer", "ingotBakelite" }) {
			GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModItems.auto_extinguisher, 1),
					"%+%",
					"-@-",
					"%!%",
					'%', plastic,
					'+', steelTank,
					'-', aluPlate,
					'@', barrel,
					'!', thruster));
		}
	}

	/** 电力悬浮作业背包 */
	public static void addHoverJetpackRecipe() {
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.hover_jetpack, 1),
				"+-+",
				"%#%",
				"%~%",
				'+', com.hbm.items.ModItems.motor_desh,
				'-', new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.ADVANCED.ordinal()),
				'%', com.hbm.items.ModItems.turbine_titanium,
				'#', com.hbm.items.ModItems.jetpack_vector,
				'~', com.hbm.items.ModItems.upgrade_speed_2);
	}
	/** 空模板 */
	public static void addBlankTemplateRecipe() {
		GameRegistry.addRecipe(new ShapelessOreRecipe(
				new ItemStack(ModItems.blank_template, 1),
				new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.ANALOG.ordinal()),
				new ItemStack(Items.paper),
				"dye"));
	}

	/** 高级合金锭 */
	public static void addAdvancedAlloyRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.advancedAlloy")) return;

		RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.advancedAlloy")
			.setDuration(400)
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.ingot_red_copper),
				new ComparableStack(com.hbm.items.ModItems.ingot_steel))
			.outputItems(new ItemStack(ModItems.ingot_advanced_alloy, 2)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: red copper + steel -> 2x advanced alloy");
	}

	/** 石墨粉 */
	public static void addGraphiteShredderRecipe() {
		ComparableStack in = new ComparableStack(new ItemStack(com.hbm.items.ModItems.ingot_graphite)).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_graphite));
		ShredderRecipes.neiShredderRecipes = null;   // 让 NEI 下次查询时重建映射

		MakeNTMGreatAgain.LOG.info("Added shredder recipe: graphite ingot -> graphite powder");
	}
	
	/** 盐粉 */
	public static void addSaltSandShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Sand).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,2));
		ShredderRecipes.neiShredderRecipes = null;
	}
	/** 盐粉 */
	public static void addSaltRedSandShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Red_Sand).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,1));
		ShredderRecipes.neiShredderRecipes = null;
	}
	/** 盐粉 */
	public static void addSaltGravelShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Gravel).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,2));
		ShredderRecipes.neiShredderRecipes = null;
	}

	/** 碳化钨锭 */
	public static void addTungstenCarbideArcRecipe() {
		ArcFurnaceRecipes.register(
			new ComparableStack(ModItems.powder_carbon_tungsten_mixture),
			new ArcFurnaceRecipes.ArcFurnaceRecipe().solid(new ItemStack(com.hbm.items.ModItems.ingot_tungsten_carbide)));

		MakeNTMGreatAgain.LOG.info("Added arc furnace recipe: tungsten carbide powder -> tungsten carbide ingot");
	}
	
	/** 高强度碳化钨锭 */
	public static void addTungstencobaltArcRecipe() {
		ArcFurnaceRecipes.register(
			new ComparableStack(ModItems.powder_cobalt_tungsten_mixture),
			new ArcFurnaceRecipes.ArcFurnaceRecipe().solid(new ItemStack(ModItems.powder_cobalt_tungsten_ingot)));

		MakeNTMGreatAgain.LOG.info("Added arc furnace recipe: tungsten carbide powder -> tungsten carbide ingot");
	}
	
	
	/** 雪球 */
	public static void addWaterToSnowballRecipe() {
		// 已有配方就不覆盖：表键是流体对象，put 会顶掉宿主那条（对照海水盐粉的写法）
		if(SolidificationRecipes.getOutput(Fluids.WATER) != null) return;

		SolidificationRecipes.recipes.put(Fluids.WATER,
			new Pair<Integer, ItemStack>(125, new ItemStack(Items.snowball , 1)));
	}
	/** 盐粉 */
	public static void addSreaWaterToSaltRecipe() {
		FluidType seaWater = ModFluids.SEA_WATER.type;
		if (seaWater == null || seaWater == Fluids.NONE) return;
		if (SolidificationRecipes.getOutput(seaWater) != null) return;

		SolidificationRecipes.recipes.put(seaWater,
			new Pair<Integer, ItemStack>(750, new ItemStack(ModItems.powder_salt , 1)));

		MakeNTMGreatAgain.LOG.info("Added solidifier recipe: sea water -> salt powder");
	}
	/** 高强度碳化钨粉 */
	public static void addCobaltCarbideRecipe() {
		if(LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.cobaltCarbide")) return;
		LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.cobaltCarbide", 500_000)
			.setup(900, 50_000)
			.inputItems(
				new ComparableStack(ModItems.powder_carbon_tungsten_mixture),
				new ComparableStack(com.hbm.items.ModItems.powder_cobalt))
			.inputFluids(new FluidStack(Fluids.ETHANOL, 2000, 1))
			.outputItems(new ItemStack(ModItems.powder_cobalt_tungsten_mixture, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: pressurized ethanol + WC powder + cobalt powder -> 2x high-strength WC powder");
	}
	
	/** 大型催化化工厂 **/
	public static void addLargeCatalyticChemicalFactoryRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.largeCatalyticChemicalFactory")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.largeCatalyticChemicalFactory")
			.setup(1200, 5_000)
			.outputItems(new ItemStack(ModBlocks.machine_large_catalytic_chemical_factory))
			.inputItems(
				new OreDictStack(FERRO.plateCast(), 24),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_grate, 32),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 8),
				new OreDictStack(CU.plateWelded(), 16));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new OreDictStack(FERRO.plateCast(), 24),
				new ComparableStack(com.hbm.items.ModItems.coil_tungsten, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_grate, 32),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 8),
				new OreDictStack(CU.plateWelded(), 64),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME),
				new OreDictStack(TCALLOY.plateWelded(), 32));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: large catalytic chemical factory");
	}

	/**
	 * 次临界核爆炸发生器。
	 *
	 * <p>装配机配方：焊接钢板 64 + Threx锭 32 + 石墨块 32 + 铜管 64 + 铸造锝钢板 32
	 * + 高级控制单元 4 + Desh马达 16 → 1 台。</p>
	 *
	 * <p>七种原料用 7 个输入槽，装配机的 {@code inputItemLimit() == 12} 装得下。
	 * 三种板材与铜管走矿辞（{@code WELDEDPLATESteel} / {@code CASTPLATEAnyResistantAlloy} / {@code PIPECopper}，
	 * 由 {@code OreDictManager} 按 {@code MaterialShapes} 前缀 + {@code DictFrame} 的 mats 名拼成），
	 * 方便整合包用等价材料替换；Threx 锭与石墨块没有矿辞，按 {@code ItemStack} 精确匹配。</p>
	 *
	 * <p>「铸造锝钢板」用 {@code ANY_RESISTANTALLOY} 而不是 {@code TCALLOY}：HBM 把锝钢与镉钢
	 * 归在同一个 {@code DictGroup} 里（名字叫 AnyResistantAlloy），且 {@code OreDictManager}
	 * 对 CASTPLATE 做过 {@code addPrefix} 重注册，所以这条矿辞同时收
	 * {@code CASTPLATETcAlloy} 与 {@code CASTPLATECdAlloy}，两种都能喂进去。</p>
	 */
	public static void addNuclearExplosionGeneratorRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.nuclearExplosionGenerator")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.nuclearExplosionGenerator")
			.setup(2400, 50_000)
			.outputItems(new ItemStack(ModBlocks.machine_nuclear_explosion_generator))
			.inputItems(
				new OreDictStack(STEEL.plateWelded(), 64),
				new ComparableStack(ModBlocks.threx_vessel, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.block_graphite, 32),
				new OreDictStack(CU.pipe(), 64),
				new OreDictStack(ANY_RESISTANTALLOY.plateCast(), 32),
				new ComparableStack(com.hbm.items.ModItems.circuit, 4, EnumCircuitType.CONTROLLER_ADVANCED),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 16));

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: subcritical nuclear explosion generator");
	}

	/** 流体冷却机 **/
	public static void addFluidCoolerRecipe() {
		// 产物曾误写成大型催化化工厂，配置里留下的那条会让下面的判重一直命中，先摘掉
		dropWrongOutput(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.addFluidCoolerRecipe",
				Item.getItemFromBlock(ModBlocks.machine_fluid_cooler));
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.addFluidCoolerRecipe")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.addFluidCoolerRecipe")
			.setup(600, 10_000)
			.outputItems(new ItemStack(ModBlocks.machine_fluid_cooler))
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.coil_copper_torus, 32),
				new ComparableStack(com.hbm.items.ModItems.plate_gold, 16),
				new ComparableStack(com.hbm.items.ModItems.motor, 8),
				new OreDictStack(CU.plateWelded(), 16));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new ComparableStack(com.hbm.items.ModItems.coil_copper_torus, 64),
				new ComparableStack(com.hbm.items.ModItems.plate_gold, 64),
				new ComparableStack(com.hbm.items.ModItems.motor, 16),
				new OreDictStack(CU.plateWelded(), 64),
				new ComparableStack(com.hbm.items.ModItems.ingot_cft, 4));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/** 小型排液管：工作台有序合成 S=铸造板、X=钢罐（钢制 S=铸造钢，钨制 S=铸造钨） **/
	public static void addMachineDrainRecipe() {
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.machine_drain),
			"S",
			"X",
			"S",
			'S', STEEL.plateCast(),
			'X', new ItemStack(com.hbm.items.ModItems.tank_steel, 1)));

		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.machine_drain_tungsten),
			"S",
			"X",
			"S",
			'S', W.plateCast(),
			'X', new ItemStack(com.hbm.items.ModItems.tank_steel, 1)));

		// 可调节钢/钨排液管：对应排液管 + 红石（升级加装限流阀）
		GameRegistry.addShapelessRecipe(
			new ItemStack(ModBlocks.machine_drain_adjustable_steel),
			new ItemStack(ModBlocks.machine_drain),
			new ItemStack(net.minecraft.init.Items.redstone));

		GameRegistry.addShapelessRecipe(
			new ItemStack(ModBlocks.machine_drain_adjustable_tungsten),
			new ItemStack(ModBlocks.machine_drain_tungsten),
			new ItemStack(net.minecraft.init.Items.redstone));
	}

	/** 锝钢/镉钢铸造板共用的矿辞：两种材料互为等位替代 */
	private static final String PLATE_TC_OR_CD = "plateCastTechnetiumOrCadmium";
	private static boolean tcOrCdPlateRegistered;

	/** 大型离心机 */
	public static void addLargeCentrifugeAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.largeCentrifuge")) return;

		// 锝钢板与镉钢板等位替代，合成一个矿辞供配方匹配
		if(!tcOrCdPlateRegistered) {
			OreDictionary.registerOre(PLATE_TC_OR_CD, new ItemStack(com.hbm.items.ModItems.plate_cast, 1, Mats.MAT_TCALLOY.id));
			OreDictionary.registerOre(PLATE_TC_OR_CD, new ItemStack(com.hbm.items.ModItems.plate_cast, 1, Mats.MAT_CDALLOY.id));
			tcOrCdPlateRegistered = true;
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.largeCentrifuge")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.large_centrifuge))
			.inputItems( // 普通模式
				new ComparableStack(com.hbm.items.ModItems.centrifuge_element, 32),
				new OreDictStack(PLATE_TC_OR_CD, 16),
				new ComparableStack(com.hbm.items.ModItems.battery_pack, 1, ItemBatteryPack.EnumBatteryPack.BATTERY_SODIUM),
				new ComparableStack(com.hbm.items.ModItems.plate_saturnite, 16),
				new OreDictStack(CU.plateWelded(), 8))
			.inputItemsEx( // 昂贵模式：覆盖上面的普通配方
				new ComparableStack(com.hbm.items.ModItems.centrifuge_element, 32),
				new OreDictStack(PLATE_TC_OR_CD, 32),
				new ComparableStack(com.hbm.items.ModItems.battery_pack, 1, ItemBatteryPack.EnumBatteryPack.BATTERY_SODIUM),
				new ComparableStack(com.hbm.items.ModItems.plate_saturnite, 32),
				new OreDictStack(CU.plateWelded(), 16),
				new ComparableStack(com.hbm.items.ModItems.ingot_cft, 4),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 12, EnumExpensiveType.LEAD_PLATING)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: large centrifuge");
	}

	/** 高温裂解重油：大型催化化工厂专用，需要 60000 Tu 才会开始加工 **/
	public static void addHeatedCrackingRecipe() {
		if(LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.hotcracking")) return;

		LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.hotcracking", 60_000)
			.setup(600, 50_000)
			.inputFluids(new FluidStack(Fluids.HEAVYOIL, 8_000))
			.outputFluids(new FluidStack(Fluids.BITUMEN, 4_000), new FluidStack(Fluids.SMEAR, 2_000)));

		MakeNTMGreatAgain.LOG.info("Added heated chemical plant recipe: heavy oil cracking at 60000 TU");
	}

	/** 从混合催化分馏塔移植过来的两条高温配方，只保留在大型催化化工厂里 */
	public static void addLargeChemicalFactoryTowerRecipes() {

		// M4 野火淬灭：野火遇水淬成弱化版 NITAN，同时放出超热蒸汽，需要 CMB 催化剂
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.balefireQuench")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.balefireQuench", 600_000, 3)
				.setup(200, 20_000).setNamed()
				.inputFluids(new FluidStack(Fluids.BALEFIRE, 1_000), new FluidStack(Fluids.WATER, 1_000))
				.outputFluids(new FluidStack(Fluids.NITAN, 500), new FluidStack(Fluids.SUPERHOTSTEAM, 400),
							  new FluidStack(Fluids.HYDROGEN, 300)));
		}

		// M5 毒泥浆热处理：把酸和重金属从洗矿废液里分出来，需要 CMB 催化剂
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.watzTreatment")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.watzTreatment", 450_000, 3)
				.setup(300, 20_000).setNamed()
				.inputFluids(new FluidStack(Fluids.WATZ, 1_000), new FluidStack(Fluids.STEAM, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.nuclear_waste, 1))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 300), new FluidStack(Fluids.VITRIOL, 300),
							  new FluidStack(Fluids.MERCURY, 200), new FluidStack(Fluids.REDMUD, 200)));
		}
	}

	/** 野火 **/
	public static void addBaltFireRecipe() {
		if(LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.BaltFire")) return;
		LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.BaltFire", 600_000)
			.setup(400, 70_000)
			.inputItems(new ComparableStack(com.hbm.items.ModItems.egg_balefire_shard))
			.inputFluids(new FluidStack(ModFluids.TBP_KERO.type, 6000))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_balefire, 2))
			.outputFluids(new FluidStack(Fluids.BALEFIRE, 10000)));
		
	}
	/**
	 * 工业合成乙醇两条路线（参照现实工艺，化工厂+搅拌机双注册）：
	 * 简单·乙烯直接水合：600 不饱和烃+1400 水→2000 乙醇（体积守恒）；
	 * 难·合成气两步法：① 500 甲醇+500 合成气→1000 乙酸，② 1000 乙酸+500 氢→1500 乙醇。
	 *   上游甲醇复用 petro.methanol（合成气+氢），整链煤→合成气→甲醇→乙酸→乙醇。
	 */
	public static void addSyntheticEthanolRecipes() {
		FluidType methanol = addonFluid(ModFluids.METHANOL);
		FluidType acetic = addonFluid(ModFluids.ACETIC_ACID);

		// 简单：乙烯水合（化工厂，无催化剂门槛）
		if(!hasChemRecipe("makentmgreatagain.ethanol.hydration")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ethanol.hydration")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(Fluids.ETHANOL, 2000))
				.setup(100, 1_000)
				.inputFluids(new FluidStack(Fluids.UNSATURATEDS, 600), new FluidStack(Fluids.WATER, 1_400))
				.outputFluids(new FluidStack(Fluids.ETHANOL, 2_000)));
		}

		if(methanol == Fluids.NONE || acetic == Fluids.NONE) return;

		// 难①：甲醇羰基化制乙酸（合成气提供 CO）
		if(!hasChemRecipe("makentmgreatagain.ethanol.carbonylation")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ethanol.carbonylation")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(acetic, 1000))
				.setup(200, 2_000)
				.inputFluids(new FluidStack(methanol, 500), new FluidStack(Fluids.SYNGAS, 500))
				.outputFluids(new FluidStack(acetic, 1_000)));
		}

		// 难②：乙酸加氢制乙醇（1500 进 1500 出，不产水）
		if(!hasChemRecipe("makentmgreatagain.ethanol.acetic_hydrogenation")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ethanol.acetic_hydrogenation")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(Fluids.ETHANOL, 1500))
				.setup(200, 2_000)
				.inputFluids(new FluidStack(acetic, 1_000), new FluidStack(Fluids.HYDROGEN, 500))
				.outputFluids(new FluidStack(Fluids.ETHANOL, 1_500)));
		}
	}

	/** 合成乙醇三条配方的搅拌机版本，与化工厂同配比；追加时保留原有条目（register 是整组覆盖） */
	public static void addSyntheticEthanolMixerRecipes() {
		FluidType methanol = addonFluid(ModFluids.METHANOL);
		FluidType acetic = addonFluid(ModFluids.ACETIC_ACID);

		// 乙烯水合：600 不饱和烃 + 1400 水 → 2000 乙醇
		AddonMixerRecipe hydration = new AddonMixerRecipe(2_000, 100);
		hydration.input1 = new FluidStack(Fluids.UNSATURATEDS, 600);
		hydration.input2 = new FluidStack(Fluids.WATER, 1_400);
		appendMixerRecipe(Fluids.ETHANOL, hydration);

		if(methanol == Fluids.NONE || acetic == Fluids.NONE) return;

		// 甲醇羰基化：500 甲醇 + 500 合成气 → 1000 乙酸
		AddonMixerRecipe carbonylation = new AddonMixerRecipe(1_000, 200);
		carbonylation.input1 = new FluidStack(methanol, 500);
		carbonylation.input2 = new FluidStack(Fluids.SYNGAS, 500);
		appendMixerRecipe(acetic, carbonylation);

		// 乙酸加氢：1000 乙酸 + 500 氢 → 1500 乙醇
		AddonMixerRecipe hydrogenation = new AddonMixerRecipe(1_500, 200);
		hydrogenation.input1 = new FluidStack(acetic, 1_000);
		hydrogenation.input2 = new FluidStack(Fluids.HYDROGEN, 500);
		appendMixerRecipe(Fluids.ETHANOL, hydrogenation);
	}

	/** 将化工厂的单流体产物路线同步添加到搅拌机，按原配比并合并同产物配方表。 */
	public static void addChemicalPlantMixerRecipes() {
		FluidType acetic = addonFluid(ModFluids.ACETIC_ACID);
		FluidType po = addonFluid(ModFluids.PROPYLENE_OXIDE);
		FluidType methanol = addonFluid(ModFluids.METHANOL);
		FluidType ammonia = addonFluid(ModFluids.AMMONIA);
		FluidType sodiumSulphite = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType goldThiosulfate = addonFluid(ModFluids.GOLD_THIOSULFATE);
		FluidType hf = addonFluid(ModFluids.HYDROFLUORIC_ACID);

		if(acetic != Fluids.NONE) {
			AddonMixerRecipe r = new AddonMixerRecipe(6_000, 240);
			r.input1 = new FluidStack(Fluids.ETHANOL, 2_000);
			r.input2 = new FluidStack(Fluids.WATER, 4_000);
			appendMixerRecipe(acetic, r);
		}
		if(po != Fluids.NONE && methanol != Fluids.NONE) {
			AddonMixerRecipe r = new AddonMixerRecipe(2_000, 150);
			r.input1 = new FluidStack(po, 500);
			r.input2 = new FluidStack(methanol, 1_500);
			appendMixerRecipe(Fluids.SOLVENT, r);
		}
		if(ammonia != Fluids.NONE) {
			AddonMixerRecipe r = new AddonMixerRecipe(1_000, 100);
			r.input1 = new FluidStack(ammonia, 1_000);
			r.input2 = new FluidStack(Fluids.WATER, 500);
			appendMixerRecipe(Fluids.NITRIC_ACID, r);
		}

		if(hf != Fluids.NONE) {
			AddonMixerRecipe uf6 = new AddonMixerRecipe(2_000, 100);
			uf6.input1 = new FluidStack(hf, 1_500);
			uf6.solidInput = new ComparableStack(com.hbm.items.ModItems.powder_yellowcake, 1);
			appendMixerRecipe(Fluids.UF6, uf6);

			AddonMixerRecipe puf6 = new AddonMixerRecipe(1_500, 160);
			puf6.input1 = new FluidStack(hf, 1_500);
			puf6.solidInput = new OreDictStack(com.hbm.inventory.OreDictManager.PU.dust(), 1);
			appendMixerRecipe(Fluids.PUF6, puf6);
		}

		if(po != Fluids.NONE) {
			AddonMixerRecipe coolant = new AddonMixerRecipe(4_000, 100);
			coolant.input1 = new FluidStack(po, 500);
			coolant.input2 = new FluidStack(Fluids.WATER, 1_500);
			coolant.solidInput = new OreDictStack(KNO.dust(), 1);
			appendMixerRecipe(Fluids.COOLANT, coolant);
		}
	}

	/** 搅拌机追加配方：register 会整组覆盖，先判重再合并原数组，重载监听重复调用不叠加 */
	private static void appendMixerRecipe(FluidType out, MixerRecipes.MixerRecipe add) {
		MixerRecipes.MixerRecipe[] existing = MixerRecipes.getOutput(out);
		if(existing != null) {
			for(MixerRecipes.MixerRecipe r : existing) {
				if(r.output == add.output && sameMixerInput(r.input1, add.input1) && sameMixerInput(r.input2, add.input2)
						&& sameMixerSolid(r.solidInput, add.solidInput)) return;
			}
			MixerRecipes.MixerRecipe[] merged = new MixerRecipes.MixerRecipe[existing.length + 1];
			System.arraycopy(existing, 0, merged, 0, existing.length);
			merged[existing.length] = add;
			MixerRecipes.register(out, merged);
		} else {
			MixerRecipes.register(out, add);
		}
	}

	private static boolean sameMixerInput(FluidStack a, FluidStack b) {
		if(a == null || b == null) return a == b;
		return a.type == b.type && a.fill == b.fill;
	}

	private static boolean sameMixerSolid(AStack a, AStack b) {
		if(a == null || b == null) return a == b;
		if(a.stacksize != b.stacksize) return false;
		if(a instanceof ComparableStack && b instanceof ComparableStack) {
			ComparableStack left = (ComparableStack)a;
			ComparableStack right = (ComparableStack)b;
			return left.item == right.item && left.meta == right.meta;
		}
		if(a instanceof OreDictStack && b instanceof OreDictStack)
			return ((OreDictStack)a).name.equals(((OreDictStack)b).name);
		return false;
	}

	/** 正电子：氢胶囊×2 低动量对撞（简单配方），产物正电子胶囊 */
	public static void addPositronAcceleratorRecipe() {
		for(com.hbm.inventory.recipes.ParticleAcceleratorRecipes.ParticleAcceleratorRecipe r
				: com.hbm.inventory.recipes.ParticleAcceleratorRecipes.recipes) {
			if(r.output1 != null && r.output1.getItem() == ModItems.particle_positron) return;
		}
		com.hbm.inventory.recipes.ParticleAcceleratorRecipes.recipes.add(
			new com.hbm.inventory.recipes.ParticleAcceleratorRecipes.ParticleAcceleratorRecipe(
				new ComparableStack(com.hbm.items.ModItems.particle_hydrogen),
				new ComparableStack(com.hbm.items.ModItems.particle_hydrogen),
				100,
				new ItemStack(ModItems.particle_positron),
				null));
	}

	/** 乙酸 **/
	public static void addAceticAcidRecipe() {
		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.AceticAcid")
			.setup(240, 1_000)
			.inputFluids(new FluidStack(Fluids.ETHANOL, 2000),new FluidStack(Fluids.WATER, 4000))
			.outputFluids(new FluidStack(ModFluids.ACETIC_ACID.type, 6000)));

	}
	/** 有机溶剂 **/
	public static void addEthylAcetateRecipe() {
		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.EthylAcetateR")
			.setup(240, 1_000)
			.inputFluids(new FluidStack(Fluids.ETHANOL, 4000),new FluidStack(ModFluids.ACETIC_ACID.type, 1500),new FluidStack(Fluids.ACID, 1500))
			.outputFluids(new FluidStack(Fluids.SOLVENT, 6000)));

	}

	/** 环氧丙烷、水、盐酸、高性能溶剂、有机溶剂、冷却液、聚合物锭、硬质塑料锭 */
	public static void addPropyleneOxideRecipes() {

		FluidType po = ModFluids.PROPYLENE_OXIDE.type;
		if(po == null || po == Fluids.NONE) return;

		FluidType acetic = ModFluids.ACETIC_ACID.type;
		FluidType methanol = ModFluids.METHANOL.type;
		FluidType glycerol = ModFluids.GLYCEROL.type;
		FluidType hcl = HBMCompat.hcl();

		// 环氧丙烷、水、盐酸
		if(!hasChemRecipe("makentmgreatagain.po.chlorohydrin")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.chlorohydrin")
				.setup(300, 2_000)
				.inputFluids(new FluidStack(Fluids.PETROLEUM, 2_000), new FluidStack(Fluids.CHLORINE, 500),
							 new FluidStack(Fluids.WATER, 3_000))
				.outputFluids(hcl == Fluids.NONE
					? new FluidStack[] { new FluidStack(po, 4_000), new FluidStack(Fluids.WATER, 1_000) }
					: new FluidStack[] { new FluidStack(po, 4_000), new FluidStack(Fluids.WATER, 1_000), new FluidStack(hcl, 500) }));
		}

		// 环氧丙烷、水
		if(!hasChemRecipe("makentmgreatagain.po.hppo")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.hppo")
				.setup(240, 1_500)
				.inputFluids(new FluidStack(Fluids.UNSATURATEDS, 2_000), new FluidStack(Fluids.PEROXIDE, 2_000),
							 new FluidStack(Fluids.WATER, 1_000))
				.outputFluids(new FluidStack(po, 2_000), new FluidStack(Fluids.WATER, 1_000)));
		}

		// 高性能溶剂、水
		if(acetic != Fluids.NONE && methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.po.glycolEtherEster")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.glycolEtherEster")
				.setup(200, 1_200)
				.inputFluids(new FluidStack(po, 1_000), new FluidStack(methanol, 1_000), new FluidStack(acetic, 500))
				.outputFluids(new FluidStack(Fluids.RADIOSOLVENT, 2_000), new FluidStack(Fluids.WATER, 500)));
		}

		// 有机溶剂
		if(methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.po.glycolEther")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.glycolEther")
				.setup(150, 1_000)
				.inputFluids(new FluidStack(po, 500), new FluidStack(methanol, 1_500))
				.outputFluids(new FluidStack(Fluids.SOLVENT, 2_000)));
		}

		// 冷却液
		if(!hasChemRecipe("makentmgreatagain.po.coolant")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.coolant")
				.setup(100, 600)
				.inputItems(new OreDictStack(KNO.dust()))
				.inputFluids(new FluidStack(po, 500), new FluidStack(Fluids.WATER, 1_500))
				.outputFluids(new FluidStack(Fluids.COOLANT, 4_000)));
		}

		// 聚合物锭
		if(glycerol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.po.polyurethane")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.polyurethane")
				.setup(120, 800)
				.inputFluids(new FluidStack(po, 800), new FluidStack(glycerol, 200), new FluidStack(Fluids.PHOSGENE, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_polymer, 12)));
		}

		// 硬质塑料锭
		if(!hasChemRecipe("makentmgreatagain.po.polycarbonate")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.po.polycarbonate")
				.setup(100, 1_000)
				.inputFluids(new FluidStack(po, 500), new FluidStack(Fluids.CARBONDIOXIDE, 500),
							 new FluidStack(Fluids.XYLENE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_pc, 1)));
		}
	}


	/** 矿典 dustSalt；没有就返回 null，调用方跳过整条支线。 */
	private static AStack findSalt() {
		if(OreDictionary.getOres("dustSalt").isEmpty()) {
			MakeNTMGreatAgain.LOG.warn("矿典里没有 dustSalt，跳过需要盐粉的配方");
			return null;
		}
		return new OreDictStack("dustSalt");
	}

	/** 盐水 */
	public static void addSaltWaterRecipe() {
		AStack salt = findSalt();
		if(salt == null) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.saltWater")
			.setup(60, 500)
			.inputItems(salt)
			.inputFluids(new FluidStack(Fluids.WATER, 1000))
			.outputFluids(new FluidStack(ModFluids.SALT_WATER.type, 1000)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: water + dustSalt -> salt water");
	}

	/** 盐酸 */
	public static void addHCLChemicalPlantRecipe() {
		FluidType hcl = HBMCompat.hcl();
		if(hcl == Fluids.NONE) return;
		if(hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.hcl")) return;
		// 宿主已有盐酸配方时不重复注册
		if(hostProducesFluid(ChemicalPlantRecipes.INSTANCE, hcl)) {
			MakeNTMGreatAgain.LOG.info("宿主已有盐酸配方，附属不再注册");
			return;
		}

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.hcl")
			.setup(250, 100)
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 300), new FluidStack(Fluids.CHLORINE, 1_000))
			.outputFluids(new FluidStack(hcl, 400)));
	}
	
	/** 盐粉 */
	public static void addSaltFromSandRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Sand),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 4) });
	}

	/** 盐粉、氧化铁粉 */
	public static void addSaltFromRedSandRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Red_Sand),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 2) ,
				new ItemStack(ModItems.powder_oxidate_iron, 1)
			});
	}

	/** 氧化铁粉 */
	public static void addIronFromRedSandRecipe() {
		if(CentrifugeRecipes.recipes.containsKey(new ComparableStack(Blocks.sand, 1, 1))) return;

		CentrifugeRecipes.recipes.put(
			new ComparableStack(Blocks.sand, 1, 1),     // 1 个红沙,
			new ItemStack[] {
				new ItemStack(ModItems.powder_oxidate_iron, 1)
			});
	}

	/** 盐粉 */
	public static void addSaltFromGravelRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Gravel),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 4) });
	}
	
	// 已有 HCL 电解配方就不覆盖：表键是流体对象，put 会顶掉宿主那条
	public static void addHCLElectrolyserRecipe() {
		FluidType hcl = HBMCompat.hcl();
		if (hcl == Fluids.NONE) {
			MakeNTMGreatAgain.LOG.warn("盐酸(HCL)尚未就绪，电解槽 HCL 配方本次跳过");
			return;
		}
		if (ElectrolyserFluidRecipes.getRecipe(hcl) != null) {
			MakeNTMGreatAgain.LOG.info("此 HBM 版本已有盐酸电解配方，附属不再覆盖");
			return;
		}

		ElectrolyserFluidRecipes.recipes.put(hcl,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1000,
				new FluidStack(Fluids.CHLORINE, 500),
				new FluidStack(Fluids.HYDROGEN, 500)));
	}
	/** 氯气、液氢 */
	public static void addSaltWaterElectrolyserRecipe() {
		FluidType brine = ModFluids.SALT_WATER.type;
		if (brine == null || brine == Fluids.NONE) return;
		if (ElectrolyserFluidRecipes.getRecipe(brine) != null) return;

		ElectrolyserFluidRecipes.recipes.put(brine,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1000,
				new FluidStack(Fluids.CHLORINE, 250),
				new FluidStack(Fluids.HYDROGEN, 450)));
	}
	/** 盐水 */
	public static void addSaltWaterMixerRecipe() {
		AStack salt = findSalt();
		if(salt == null) return;
		// 搅拌机表键是产物流体，register 是整数组 put：已有同产物配方（含其它模组的）不覆盖
		if(ModFluids.SALT_WATER.type == null || MixerRecipes.getOutput(ModFluids.SALT_WATER.type) != null) return;

		AddonMixerRecipe recipe = new AddonMixerRecipe(1000, 100);
		recipe.input1 = new FluidStack(Fluids.WATER, 1000);
		recipe.solidInput = salt;

		MixerRecipes.register(ModFluids.SALT_WATER.type, recipe);
		MakeNTMGreatAgain.LOG.info("Added mixer recipe: water + dustSalt -> salt water");
	}

	/** 盐酸 */
	public static void addHCLMixerRecipe() {
		FluidType hcl = HBMCompat.hcl();
		if(hcl == Fluids.NONE) return;
		if(MixerRecipes.getOutput(hcl) != null) return;

		AddonMixerRecipe recipe = new AddonMixerRecipe(400, 50);
		recipe.input1 = new FluidStack(Fluids.HYDROGEN, 300);
		recipe.input2 = new FluidStack(Fluids.CHLORINE, 1000);

		MixerRecipes.register(hcl, recipe);
	}

	/** 子类只为了拿到 protected 构造器。 */
	private static class AddonMixerRecipe extends MixerRecipes.MixerRecipe {
		AddonMixerRecipe(int output, int duration) { super(output, duration); }
	}

	/** 碳化钨粉 */
	public static void addTungstenCarbideRecipe() {
		if(LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.tungstenCarbide")) return;
		LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.tungstenCarbide", 400_000)
			.setup(1200, 100_00)
			.inputItems(
				new ComparableStack(ModItems.powder_graphite),
				new ComparableStack(com.hbm.items.ModItems.powder_tungsten))
			.inputFluids(new FluidStack(Fluids.OXYHYDROGEN, 2000))
			.outputItems(new ItemStack(ModItems.powder_carbon_tungsten_mixture, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: ethanol + graphite + tungsten powder -> 2x tungsten carbide powder");
	}
	/** 高级粉碎机 */
	public static void addShredderAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.alloyShredder")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.alloyShredder")
			.setup(900, 5_000)
			.outputItems(new ItemStack(com.makentmgreatagain.init.ModBlocks.block_alloy_shredder))
			.inputItems(
				new OreDictStack(ANY_RESISTANTALLOY.ingot(), 8),
				new OreDictStack(DESH.ingot(), 4),
				new OreDictStack(STEEL.plateCast(), 4),
				new OreDictStack(W.bolt(), 16),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 4)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: 8x resistant alloy ingot + 4x desh ingot + 4x cast steel plate + 16x tungsten bolt -> alloy shredder");
	}

	/** 工业种植站：土壤基座、铸钢/铝框架与聚合物舱体；昂贵版增加铆接钢板。 */
	public static void addIndustrialCultivationStationAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.industrialCultivationStation")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.industrialCultivationStation")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.industrial_cultivation_station))
			.inputItems(
				new ComparableStack(Blocks.dirt, 24),
				new OreDictStack(STEEL.plateCast(), 16),
				new OreDictStack(AL.plate(), 32),
				new OreDictStack(ANY_PLASTIC.ingot(), 16))
			.inputItemsEx(
				new ComparableStack(Blocks.dirt, 24),
				new OreDictStack(STEEL.plateCast(), 32),
				new OreDictStack(AL.plate(), 64),
				new OreDictStack(ANY_PLASTIC.ingot(), 32),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.STEEL_PLATING)));
	}

	/** 工业燃气轮机：照工业内燃机(ass.combustiongen)的料单大幅加码（×4），另加 4 个钛汽轮转子 */
	public static void addGasTurbineAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.gasTurbine")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.gasTurbine")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.industrial_gas_turbine))
			.inputItems(
				new OreDictStack(STEEL.plate(), 64),
				new OreDictStack(CU.ingot(), 48),
				new OreDictStack(GOLD.wireDense(), 32),
				new ComparableStack(com.hbm.items.ModItems.canister_empty, 16),
				new ComparableStack(com.hbm.items.ModItems.circuit, 4, EnumCircuitType.BASIC),
				new ComparableStack(com.hbm.items.ModItems.turbine_titanium, 4),
				new ComparableStack(com.hbm.items.ModItems.ingot_rubber, 16))
			.inputItemsEx( // 昂贵模式：照内燃机替换思路（重型框架+大型电路板、金线翻倍），钛转子保留
				new OreDictStack(STEEL.plate(), 64),
				new OreDictStack(CU.ingot(), 48),
				new OreDictStack(GOLD.wireDense(), 64),
				new ComparableStack(com.hbm.items.ModItems.canister_empty, 16),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 8, EnumExpensiveType.HEAVY_FRAME),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.CIRCUIT),
				new ComparableStack(com.hbm.items.ModItems.turbine_titanium, 4),
				new ComparableStack(com.hbm.items.ModItems.ingot_rubber, 32)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: industrial gas turbine (combustion engine x4 materials + 4x titanium turbine rotor)");
	}

	/** 超临界磁流体膨胀发电机：threx块+铸造铋/砷青铜板+量子处理单元+致密BSCCO线圈+致密磁化钨线；昂贵加塑料板/青铜结构件 */
	public static void addSupercriticalMHDAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.supercriticalMHD")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.supercriticalMHD")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.supercritical_mhd_generator))
			.inputItems(
				new ComparableStack(ModBlocks.threx_casing, 16),
				new OreDictStack(ANY_BISMOIDBRONZE.plateCast(), 32),
				new ComparableStack(com.hbm.items.ModItems.circuit, 4, EnumCircuitType.QUANTUM),
				new OreDictStack(BSCCO.wireDense(), 64),
				new OreDictStack(MAGTUNG.wireDense(), 64))
			.inputItemsEx( // 昂贵模式：threx/量子翻倍，另加塑料板+青铜结构件
				new ComparableStack(ModBlocks.threx_casing, 32),
				new OreDictStack(ANY_BISMOIDBRONZE.plateCast(), 32),
				new ComparableStack(com.hbm.items.ModItems.circuit, 16, EnumCircuitType.QUANTUM),
				new OreDictStack(BSCCO.wireDense(), 64),
				new OreDictStack(MAGTUNG.wireDense(), 64),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 32, EnumExpensiveType.PLASTIC),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 16, EnumExpensiveType.BRONZE_TUBES)));
	}

	/** 大型火力发电站：正常 16铸造钢板+8焊接钢板+16聚合物(或电木)+32耐火砖；昂贵翻倍另加4铆接固定钢板 */
	public static void addThermalStationAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.thermalStation")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.thermalStation")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.large_thermal_power_station))
			.inputItems(
				new OreDictStack(STEEL.plateCast(), 16),
				new OreDictStack(STEEL.plateWelded(), 8),
				new OreDictStack(ANY_PLASTIC.ingot(), 16),
				new ComparableStack(com.hbm.items.ModItems.ingot_firebrick, 32))
			.inputItemsEx( // 昂贵模式：板材/聚合物/耐火砖翻倍，另加 4 铆接固定钢板
				new OreDictStack(STEEL.plateCast(), 32),
				new OreDictStack(STEEL.plateWelded(), 16),
				new OreDictStack(ANY_PLASTIC.ingot(), 32),
				new ComparableStack(com.hbm.items.ModItems.ingot_firebrick, 64),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.STEEL_PLATING)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: large thermal power station (normal / expensive)");
	}

	/** 化学工厂：石油/煤/褐煤焦炭 + 2000mb氢气 + 2000mb水 → 4000mb合成气（仅限这三种焦炭） */
	public static void addCokeSyngasRecipes() {
		addCokeSyngas("makentmgreatagain.syngas.petcoke", new ComparableStack(com.hbm.items.ModItems.coke, 1, com.hbm.items.ItemEnums.EnumCokeType.PETROLEUM));
		addCokeSyngas("makentmgreatagain.syngas.coalcoke", new ComparableStack(com.hbm.items.ModItems.coke, 1, com.hbm.items.ItemEnums.EnumCokeType.COAL));
		addCokeSyngas("makentmgreatagain.syngas.lignitecoke", new ComparableStack(com.hbm.items.ModItems.coke, 1, com.hbm.items.ItemEnums.EnumCokeType.LIGNITE));
	}

	private static void addCokeSyngas(String name, ComparableStack coke) {
		if(hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, name)) return;
		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe(name)
			.setup(200, 1_000)
			.inputItems(coke)
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 2_000), new FluidStack(Fluids.WATER, 2_000))
			.outputFluids(new FluidStack(Fluids.SYNGAS, 4_000)));
	}

	/** 海面立管钻机 */
	public static void addSeaRiseRigRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.riserrig")) return;
		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.riserrig")
			.setup(1200, 20_000)
			.outputItems(new ItemStack(ModBlocks.riser_rig))
			.inputItems(
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 16),
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 2),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_poles, 32),
				new ComparableStack(com.hbm.blocks.ModBlocks.concrete_rebar, 64),
				new ComparableStack(com.hbm.items.ModItems.drill_titanium, 4));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 32),
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 2),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_poles, 64),
				new ComparableStack(com.hbm.blocks.ModBlocks.concrete_rebar, 64),
				new ComparableStack(com.hbm.items.ModItems.drill_titanium, 4),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.STEEL_PLATING));
			
		}
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: riser rig");
	}
		
	/** 环境地下泵 */
	public static void addEnvironmentPumpAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.environmentPump")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.environmentPump")
			.setup(1100, 10_000)
			.outputItems(new ItemStack(ModBlocks.environment_pump))
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 2),
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 8),
				new ComparableStack(com.hbm.items.ModItems.flywheel_beryllium, 2),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_poles, 8),
				new ComparableStack(com.hbm.items.ModItems.plate_dura_steel, 16));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 //
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 2),
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 8),
				new ComparableStack(com.hbm.items.ModItems.flywheel_beryllium, 2),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_poles, 8),
				new ComparableStack(com.hbm.items.ModItems.plate_dura_steel, 16),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: environment pump");
	}

	/** 大型储罐 */
	public static void addBat9000AssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.bat9000Reborn")) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.bat9000Reborn")
			.setup(200, 100)
			.outputItems(new ItemStack(com.makentmgreatagain.init.ModBlocks.block_bat9000_reborn))
			.inputItems(
				new OreDictStack(ANY_RESISTANTALLOY.plateCast(), 4),
				new OreDictStack(STEEL.plate(), 16),
				new OreDictStack(STEEL.shell(), 4),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 16),
				new ComparableStack(com.hbm.blocks.ModBlocks.glass_quartz, 4));

		if(GeneralConfig.enableExpensiveMode) {
			recipe.inputItems( // 昂贵模式 / 
				new OreDictStack(ANY_RESISTANTALLOY.plateCast(), 4),
				new OreDictStack(STEEL.plate(), 16),
				new OreDictStack(STEEL.shell(), 4),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 16),
				new ComparableStack(com.hbm.blocks.ModBlocks.glass_quartz, 4),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME));
		}

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}

	/** 集成式换热加热器 */
	public static void addHeatexRebornAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.heatexReborn")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.heatexReborn")
			.setup(600, 5_000)
			.outputItems(new ItemStack(ModBlocks.heatex_reborn))
			.inputItems(
				new OreDictStack(CU.plate(), 16),
				new OreDictStack(STEEL.plateCast(), 8),
				new OreDictStack(STEEL.shell(), 6),
				new OreDictStack(CU.plateCast(), 8),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 4),
				new OreDictStack(W.bolt(), 32)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: heatex reborn");
	}

	/** 小型电解机 */
	public static void addElectrolyzerRebornAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.electrolyzerReborn")) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.electrolyzerReborn")
			.setup(600, 5_000)
			.outputItems(new ItemStack(ModBlocks.electrolyzer_reborn))
			.inputItems( // 普通模式
				new OreDictStack(STEEL.plateCast(), 4),
				new OreDictStack(RUBBER.ingot(), 4),
				new ComparableStack(com.hbm.items.ModItems.coil_gold, 16),
				new ComparableStack(com.hbm.items.ModItems.circuit, 16, EnumCircuitType.ANALOG),
				new OreDictStack(AL.shell(), 4))
			.inputItemsEx( // 昂贵模式：覆盖上面的普通配方
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 8, EnumExpensiveType.STEEL_PLATING),
				new OreDictStack(RUBBER.ingot(), 16),
				new ComparableStack(com.hbm.items.ModItems.coil_gold, 16),
				new ComparableStack(com.hbm.items.ModItems.circuit, 64, EnumCircuitType.ANALOG),
				new OreDictStack(AL.shell(), 4)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: electrolyzer reborn");
	}

	/**
	 * 混合催化分馏塔。
	 *
	 * <p>普通模式：8 焊接锝钢合金板 + 32 焊接高速钢板 + 64 钨螺栓 ×2 + 32 黏土催化剂；
	 * 昂贵模式在此基础上再加 8 重型框架 + 3 防辐射镀层（铅镀层）。</p>
	 */
	public static void addHybridFractionTowerAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.hybridFractionTower")) return;

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.hybridFractionTower")
			.setup(1200, 10_000)
			.outputItems(new ItemStack(ModBlocks.machine_hybrid_catalytic_fraction_tower))
			.inputItems( // 普通模式
				new OreDictStack(TCALLOY.plateWelded(), 8),
				new OreDictStack(STEEL.plateWelded(), 32),
				new OreDictStack(W.bolt(), 64),
				new OreDictStack(W.bolt(), 64),
				new ComparableStack(com.hbm.items.ModItems.catalyst_clay, 32))
			.inputItemsEx( // 昂贵模式：覆盖上面的普通配方
				new OreDictStack(TCALLOY.plateWelded(), 8),
				new OreDictStack(STEEL.plateWelded(), 32),
				new OreDictStack(W.bolt(), 64),
				new OreDictStack(W.bolt(), 64),
				new ComparableStack(com.hbm.items.ModItems.catalyst_clay, 32),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 8, EnumExpensiveType.HEAVY_FRAME),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 3, EnumExpensiveType.LEAD_PLATING)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: hybrid catalytic fractionating tower");
	}

	/** 电弧加热器 */
	public static void addArcHeaterAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.arcHeater")) return;
		
		ComparableStack electrode1 = new ComparableStack(com.hbm.items.ModItems.arc_electrode, 1, com.hbm.items.machine.ItemArcElectrode.EnumElectrodeType.SATURNITE.ordinal());
		ComparableStack electrode2 = new ComparableStack(com.hbm.items.ModItems.arc_electrode, 1, com.hbm.items.machine.ItemArcElectrode.EnumElectrodeType.SATURNITE.ordinal());
		ComparableStack electrode3 = new ComparableStack(com.hbm.items.ModItems.arc_electrode, 1, com.hbm.items.machine.ItemArcElectrode.EnumElectrodeType.SATURNITE.ordinal());
		ComparableStack electrode4 = new ComparableStack(com.hbm.items.ModItems.arc_electrode, 1, com.hbm.items.machine.ItemArcElectrode.EnumElectrodeType.SATURNITE.ordinal());

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.arcHeater")
			.setup(600, 5_000)
			.outputItems(new ItemStack(ModBlocks.arc_heater))
			.inputItems( // 普通模式
				new OreDictStack(W.plateWelded(), 8),
				new OreDictStack(CU.plateWelded(), 4),
				new ComparableStack(com.hbm.items.ModItems.circuit, 4, EnumCircuitType.ADVANCED),
				electrode1, electrode2, electrode3, electrode4,
				new OreDictStack(STEEL.plate(), 64))
			.inputItemsEx( // 昂贵模式：覆盖上面的普通配方
				new OreDictStack(W.plateWelded(), 8),
				new OreDictStack(CU.plateWelded(), 4),
				new ComparableStack(com.hbm.items.ModItems.circuit, 16, EnumCircuitType.ADVANCED),
				electrode1, electrode2, electrode3, electrode4,
				new OreDictStack(STEEL.plate(), 64),
				new ComparableStack(com.hbm.items.ModItems.item_expensive, 4, EnumExpensiveType.HEAVY_FRAME)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: arc heater");
	}

	/** '中华牌'香烟 */
	private static void addCigaretteDrxRecipe() {
		GameRegistry.addShapelessRecipe(
			new ItemStack(ModItems.cigarette_drx, 8),                    // 输出：你的 DRX 香烟
			new ItemStack(com.hbm.items.ModItems.cigarette, 1),          // 输入1：HBM 的 FFI 牌香烟
			new ItemStack(com.hbm.items.ModItems.particle_digamma));  // 输入2：HBM 的迪伽马粒子胶囊
	}
	/** 史诗无限水罐、小型销毁罐、大型销毁罐、史诗销毁罐、高级合金块、高级合金锭、高级合金刀片、高强度碳化钨剑、高级合金剑、高级合金镐、高级合金锹、高级合金斧、高级合金锄、高级合金头盔、高级合金胸甲、高级合金护腿、高级合金靴子、高强度碳化钨镐、高强度碳化钨斧、高强度碳化钨锹、高强度碳化钨锄、高强度碳化钨头盔、高强度碳化钨胸甲、高强度碳化钨护腿、高强度碳化钨靴子 */
	private static void addAlloyRebornGearRecipes() {
		ItemStack alloy = new ItemStack(ModItems.ingot_advanced_alloy);
		ItemStack stick = new ItemStack(Items.stick);
		ItemStack steelblades = new ItemStack(com.hbm.items.ModItems.blades_steel);
		ItemStack alloyblades = new ItemStack(ModItems.blades_alloy_reborn);
		ItemStack alloyblock = new ItemStack(ModBlocks.block_advanced_alloy);
		ItemStack cobalttungsten = new ItemStack(ModItems.powder_cobalt_tungsten_ingot);

		// 史诗无限水罐
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_water_mk3),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(com.hbm.items.ModItems.inf_water_mk2), '#', com.hbm.blocks.ModBlocks.machine_fluidtank, 'B', new ItemStack(com.hbm.items.ModItems.ingot_ferrouranium));
		
		// 小型销毁罐
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy),
			"XXX", "B#B", "XXX",
			'X', new ItemStack(com.hbm.items.ModItems.plate_aluminium), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor));
		// 大型销毁罐
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy_mk2),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(ModItems.inf_destroy), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor));
		// 史诗销毁罐
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy_mk3),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(ModItems.inf_destroy_mk2), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor_desh));

		// 高级合金块
		GameRegistry.addShapedRecipe(new ItemStack(ModBlocks.block_advanced_alloy),
			"AAA", "AAA", "AAA",
			'A', alloy);

		// 高级合金锭
		GameRegistry.addShapelessRecipe(new ItemStack(ModItems.ingot_advanced_alloy, 9),
			new ItemStack(ModBlocks.block_advanced_alloy));

		// 高级合金刀片
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.blades_alloy_reborn),
			" X ", "X#X", " X ",
			'X', alloy, '#', steelblades);
		
		// 高级合金剑
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_sword_reborn),
				"X", "X", "#",
				'X', alloy, '#', stick);
		
		//耐摔镐
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_pickaxe),
			"AXA", "V#V", " D ",
			'X', ModItems.plate_welded_helicopter, '#', com.hbm.items.ModItems.mese_pickaxe, 'V', com.hbm.items.ModItems.ingot_chainsteel, 'D' ,com.hbm.blocks.ModBlocks.block_dineutronium, 'A' ,com.hbm.items.ModItems.billet_yharonite);
		//耐摔斧
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_axe),
			"AXA", "V# ", " D ",
			'X', ModItems.plate_welded_helicopter, '#', com.hbm.items.ModItems.mese_axe, 'V', com.hbm.items.ModItems.ingot_chainsteel, 'D' ,com.hbm.blocks.ModBlocks.block_dineutronium, 'A' ,com.hbm.items.ModItems.billet_yharonite);

		// 高强度碳化钨镐、斧、锹、锄、剑
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_pickaxe),
			"XXX", " # ", " # ",
			'X', cobalttungsten, '#', stick);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_axe),
			"XX ", "X# ", " # ",
			'X', cobalttungsten, '#', stick);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_shovel),
			" X ", " # ", " # ",
			'X', cobalttungsten, '#', stick);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_hoe),
			"XX ", " # ", " # ",
			'X', cobalttungsten, '#', stick);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_sword),
			" X ", " X ", " # ",
			'X', cobalttungsten, '#', stick);
		// 高级合金镐、锹、斧、锄
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_pickaxe_reborn),
				"XBX", " # ", " # ",
				'X', alloyblades, '#', stick, 'B', alloyblock);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_shovel_reborn),
				"X", "#", "#",
				'X', alloy, '#', stick);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_axe_reborn),
				"XB", "X#", " #",
				'X', alloy, '#', stick, 'B', alloyblock);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_hoe_reborn),
				"XB", " #", " #",
				'X', alloy, '#', stick, 'B', alloyblock);

		// 高级合金头盔、胸甲、护腿、靴子
		ItemStack helmet = new ItemStack(ModItems.alloy_helmet_reborn);
		helmet.addEnchantment(Enchantment.protection, 1);
		GameRegistry.addShapedRecipe(helmet,
			"XXX", "X X",
			'X', alloy);

		ItemStack plate = new ItemStack(ModItems.alloy_plate_reborn);
		plate.addEnchantment(Enchantment.protection, 1);
		GameRegistry.addShapedRecipe(plate,
			"X X", "XXX", "XXX",
			'X', alloy);

		ItemStack legs = new ItemStack(ModItems.alloy_legs_reborn);
		legs.addEnchantment(Enchantment.protection, 1);
		GameRegistry.addShapedRecipe(legs,
			"XXX", "X X", "X X",
			'X', alloy);

		ItemStack boots = new ItemStack(ModItems.alloy_boots_reborn);
		boots.addEnchantment(Enchantment.protection, 1);
		GameRegistry.addShapedRecipe(boots,
			"X X", "X X",
			'X', alloy);

		// 高强度碳化钨头盔、胸甲、护腿、靴子
		ItemStack tungsten_helmet = new ItemStack(ModItems.cobalt_tungsten_helmet);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_helmet),
			"XXX", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_plate = new ItemStack(ModItems.cobalt_tungsten_plate);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_plate),
			"X X", "XXX", "XXX",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_legs = new ItemStack(ModItems.cobalt_tungsten_legs);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_legs),
			"XXX", "X X", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_boots = new ItemStack(ModItems.cobalt_tungsten_boots);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_boots),
			"X X", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		// ---- 耐摔合金护甲四件 ----
		// B 焊接耐摔合金板 | X 核子可乐瓶盖方块(meta0=NUKA) | C 螺旋钻 | A 核子可乐
		// T/H/K/M 高强度碳化钨四件 | G 万金罗宾航天飞机
		ItemStack capNukaBlock = new ItemStack(com.hbm.blocks.ModBlocks.block_cap, 1, 0);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_helmet),
			"CBC", "XTX",
			'C', com.hbm.items.ModItems.elec_shovel,
			'B', ModItems.plate_welded_helicopter,
			'X', capNukaBlock,
			'T', new ItemStack(ModItems.cobalt_tungsten_helmet));

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_plate),
			"C C", "XHX", "ABA",
			'C', com.hbm.items.ModItems.elec_shovel,
			'X', capNukaBlock,
			'H', new ItemStack(ModItems.cobalt_tungsten_plate),
			'A', com.hbm.items.ModItems.bottle_nuka,
			'B', ModItems.plate_welded_helicopter);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_legs),
			"CBC", "XKX", "A A",
			'C', com.hbm.items.ModItems.elec_shovel,
			'B', ModItems.plate_welded_helicopter,
			'X', capNukaBlock,
			'K', new ItemStack(ModItems.cobalt_tungsten_legs),
			'A', com.hbm.items.ModItems.bottle_nuka);

		GameRegistry.addShapedRecipe(new ItemStack(ModItems.helicopter_boots),
			"   ", "B B", "GMG",
			'B', ModItems.plate_welded_helicopter,
			'G', com.hbm.items.ModItems.missile_shuttle,
			'M', new ItemStack(ModItems.cobalt_tungsten_boots));

		// 铂合金催化转换器：原版转换器做骨架，铂铸造板收率、铒板增益、铌锭固持
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.platinum_converter),
			"ABA", "CGC", "ABA",
			'A', com.hbm.items.ModItems.catalytic_converter,
			'B', ModItems.plate_cast_platinum,
			'C', ModItems.plate_erbium,
			'G', com.hbm.items.ModItems.ingot_niobium);

		// CMB 催化转换器：铂催化剂做骨架，CMB 钢锭、Sa 酸铁锭夹持，野火蛋碎片供辐射场
		// （成品带 380 RAD/s 放射性，注册在 HBM 危害系统里，背包内即辐照，见 ItemRadioactiveConverter）
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cmb_converter),
			"ABA", "CGC", "ABA",
			'A', ModItems.platinum_converter,
			'B', com.hbm.items.ModItems.ingot_combine_steel,
			'C', com.hbm.items.ModItems.ingot_schrabidate,
			'G', com.hbm.items.ModItems.egg_balefire_shard);

		MakeNTMGreatAgain.LOG.info("Added crafting recipes for 12 alloy reborn gear items.");
	}

	// =====================================================================================
	//  铅锌冶炼 · ISP 法提镉
	// =====================================================================================

	/** 铅锌精矿、硫粉、铅锌烧结块、二氧化硫、含镉浸出液、硫酸锌溶液、镉粉、硫酸、锌粉、白砷、亚硫酸钠溶液 */
	public static void addLeadZincChemicalRecipes() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType cdLiq = addonFluid(ModFluids.CADMIUM_LIQUOR);
		FluidType znSol = addonFluid(ModFluids.ZINC_SULFATE);
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType nitrobenzene = addonFluid(ModFluids.NITROBENZENE);
		FluidType aniline = addonFluid(ModFluids.ANILINE);
		FluidType arsine = addonFluid(ModFluids.ARSINE);
		// 缺流体就整段跳过，不留半条链
		if(so2 == Fluids.NONE || cdLiq == Fluids.NONE || znSol == Fluids.NONE || na2so3 == Fluids.NONE) return;

		// 铅锌精矿、硫粉
		if(!hasChemRecipe("makentmgreatagain.leadzinc.float")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.float")
				.setupNamed(600, 2_500)
				.inputItems(new OreDictStack("oreLeadzinc", 1))
				.inputFluids(new FluidStack(Fluids.WATER, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.leadzinc_concentrate, 1),
							 new ItemStack(com.hbm.items.ModItems.sulfur, 1)));
		}

		// 铅锌烧结块、二氧化硫
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.leadzinc.roast")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.leadzinc.roast", 250_000)
				.setupNamed(600, 3_000)
				.inputItems(new ComparableStack(ModItems.leadzinc_concentrate, 2))
				.inputFluids(new FluidStack(Fluids.OXYGEN, 500))
				.outputItems(new ItemStack(ModItems.leadzinc_sinter, 2))
				.outputFluids(new FluidStack(so2, 600)));
		}

		// 含镉浸出液、硫酸锌溶液
		if(!hasChemRecipe("makentmgreatagain.leadzinc.leach")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.leach")
				.setupNamed(500, 2_500)
				.inputItems(new ComparableStack(ModItems.bp_blue_powder, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500), new FluidStack(nitrobenzene, 500))
				.outputFluids(new FluidStack(cdLiq, 500), new FluidStack(znSol, 500)));
		}

		// 镉粉、硫酸
		if(!hasChemRecipe("makentmgreatagain.leadzinc.cadmiumCell")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.cadmiumCell")
				.setupNamed(600, 4_000)
				.inputFluids(new FluidStack(cdLiq, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_cadmium, 1))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 200)));
		}

		// 锌粉、硫酸
		if(!hasChemRecipe("makentmgreatagain.leadzinc.zincCell")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.zincCell")
				.setupNamed(600, 4_000)
				.inputFluids(new FluidStack(znSol, 500))
				.outputItems(new ItemStack(ModItems.powder_zinc_isp, 1))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 200)));
		}

		// 硫酸
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.leadzinc.acidPlant")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.leadzinc.acidPlant", 150_000)
				.setupNamed(200, 1_200)
				.inputFluids(new FluidStack(so2, 1_000), new FluidStack(Fluids.OXYGEN, 500), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_600)));
		}

		// 镉粉、硫酸锌溶液
		if(!hasChemRecipe("makentmgreatagain.leadzinc.cementation")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.cementation")
				.setupNamed(400, 1_500)
				.inputItems(new ComparableStack(ModItems.powder_zinc_isp, 1))
				.inputFluids(new FluidStack(cdLiq, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_cadmium, 2))
				.outputFluids(new FluidStack(znSol, 300)));
		}

		// 亚硫酸钠溶液
		if(!hasChemRecipe("makentmgreatagain.leadzinc.sulphite")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.sulphite")
				.setupNamed(600, 2_500)
				.inputFluids(new FluidStack(so2, 1_000), new FluidStack(Fluids.LYE, 400), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(na2so3, 1_000)));
		}

		// 白砷、含镉浸出液、硫酸锌溶液
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.leadzinc.dearsenify")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.leadzinc.dearsenify", 200_000)
				.setupNamed(800, 4_000)
				.inputItems(new ComparableStack(ModItems.bp_blue_powder, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500), new FluidStack(na2so3, 500), new FluidStack(aniline, 250))
				.outputItems(new ItemStack(ModItems.arsenic_trioxide, 3))
				.outputFluids(new FluidStack(cdLiq, 500), new FluidStack(znSol, 400)));
		}

		// 砷化氢、硫酸锌溶液
		if(arsine != Fluids.NONE && !hasChemRecipe("makentmgreatagain.leadzinc.arsine")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.leadzinc.arsine")
				.setupNamed(600, 3_000)
				.inputItems(new ComparableStack(ModItems.arsenic_trioxide, 2), new OreDictStack("dustZinc", 12))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 4_000))
				.outputFluids(new FluidStack(arsine, 4_000), new FluidStack(znSol, 2_400)));
		}

		MakeNTMGreatAgain.LOG.info("Added lead-zinc (ISP) chemical plant recipes: float/roast/leach/Cd cell/Zn cell/acid/cementation/sulphite/dearsenify/arsine");
	}

	/** 二氧化硫、硫酸、锌粉 */
	public static void addSulfurDioxideUpstreamRecipes() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType znSol = addonFluid(ModFluids.ZINC_SULFATE);
		if(so2 == Fluids.NONE || znSol == Fluids.NONE) return;

		// 二氧化硫：硫磺→SO2 由 addSulfurCombinationRecipe 统一注册（矿辞 dustSulfur 通道），
		// 这里不再重复注册物品键的条目，否则组合炉 NEI 会出现两条输入相同、数值相同的配方

		// 二氧化硫
		boolean hasPyro = false;
		for(PyroOvenRecipes.PyroOvenRecipe recipe : PyroOvenRecipes.recipes) {
			if(recipe.inputItem instanceof ComparableStack) {
				if(((ComparableStack) recipe.inputItem).item == com.hbm.items.ModItems.sulfur) {
					hasPyro = true;
					break;
				}
			}
		}
		if(!hasPyro) {
			PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(200);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(com.hbm.items.ModItems.sulfur, 2));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.OXYGEN, 1_000));
			recipe.out(new FluidStack(so2, 3_000));
			PyroOvenRecipes.recipes.add(recipe);
		}

		// 硫酸、锌粉
		if(!ElectrolyserFluidRecipes.recipes.containsKey(znSol)) {
			ElectrolyserFluidRecipes.recipes.put(znSol, new ElectrolyserFluidRecipes.ElectrolysisRecipe(
				750,
				new FluidStack(Fluids.SULFURIC_ACID, 300),
				new FluidStack(Fluids.NONE, 0),
				200,
				new ItemStack(ModItems.powder_zinc_isp, 2)));
		}

		MakeNTMGreatAgain.LOG.info("Added SO2 upstream (combination oven / pyro oven) and zinc sulphate electrolysis");
	}

	/**
	 * 硫磺石（stone_resource:0）双路线化工深加工：
	 * 1) 氧化焙烧：4×硫磺石 + 2000mB 氧气 → 4000mB 二氧化硫 + 1×铁粉；
	 * 2) 接触法制酸：2000mB 二氧化硫 + 1000mB 氧气 + 2000mB 水 → 2000mB 硫酸；
	 * 3) 弗拉施法蒸汽热熔：4×硫磺石 + 2000mB 蒸汽 → 12×硫粉 + 2×石英沙 + 1000mB 二氧化硫。
	 */
	public static void addBrimstoneChemicalRecipes() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		if(so2 == Fluids.NONE) return;

		int sulfurMeta = com.hbm.blocks.BlockEnums.EnumStoneType.SULFUR.ordinal();

		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.brimstoneRoast")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.brimstoneRoast")
				.setup(100, 500)
				.inputItems(new ComparableStack(com.hbm.blocks.ModBlocks.stone_resource, 4, sulfurMeta))
				.inputFluids(new FluidStack(Fluids.OXYGEN, 2_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_iron, 1))
				.outputFluids(new FluidStack(so2, 4_000)));
		}

		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.contactSulfuricAcid")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.contactSulfuricAcid")
				.setup(80, 800)
				.inputFluids(
					new FluidStack(so2, 2_000),
					new FluidStack(Fluids.OXYGEN, 1_000),
					new FluidStack(Fluids.WATER, 2_000))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 2_000)));
		}

		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.brimstoneFrasch")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.brimstoneFrasch")
				.setup(100, 600)
				.inputItems(new ComparableStack(com.hbm.blocks.ModBlocks.stone_resource, 4, sulfurMeta))
				.inputFluids(new FluidStack(Fluids.STEAM, 2_000))
				.outputItems(
					new ItemStack(com.hbm.items.ModItems.sulfur, 12),
					new ItemStack(com.hbm.items.ModItems.powder_quartz, 2))
				.outputFluids(new FluidStack(so2, 1_000)));
		}
	}

	/** 铅锭、蓝粉、铁粉、白砷 */
	public static void addLeadZincBlastRecipe() {

		// 铅锭、蓝粉
		if(!hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.leadzinc.shaft")) {
			RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.leadzinc.shaft")
				.setNamed()
				.setDuration(1_200)
				.inputItems(new ComparableStack(ModItems.leadzinc_sinter, 2), new OreDictStack(ANY_COKE.gem()))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_lead, 3),
							 new ItemStack(ModItems.bp_blue_powder, 2)));
		}

		// 砷粒只能由砷化氢热分解产出（高炉碳热还原已移除）

		// 铁粉、白砷
		if(ModItems.arsenical_residue != null
				&& !hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.gold.residueRoast")) {
			RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.gold.residueRoast")
				.setNamed()
				.setDuration(900)
				.inputItems(new ComparableStack(ModItems.arsenical_residue, 2), new OreDictStack(ANY_COKE.gem()))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_iron, 3),
							 new ItemStack(ModItems.arsenic_trioxide, 1)));
		}

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipes: lead-zinc sinter -> lead + blue powder; arsenic trioxide + coke -> arsenic nuggets; arsenical residue -> iron + arsenic trioxide");
	}

	/** 硫酸钠、盐酸 */
	public static void addChlorAlkaliDechlorRecipe() {
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType hcl = HBMCompat.hcl();
		if(na2so3 == Fluids.NONE || hcl == Fluids.NONE) return;
		if(ModItems.powder_sodium_sulfate == null) return;

		if(!hasChemRecipe("makentmgreatagain.chloralkali.dechlor")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chloralkali.dechlor")
				.setupNamed(600, 3_000)
				.inputFluids(new FluidStack(na2so3, 500),
							 new FluidStack(Fluids.CHLORINE, 500),
							 new FluidStack(Fluids.WATER, 500))
				.outputItems(new ItemStack(ModItems.powder_sodium_sulfate, 1))
				.outputFluids(new FluidStack(hcl, 400)));
		}

		MakeNTMGreatAgain.LOG.info("Added chlor-alkali recipe: sodium sulphite + chlorine -> hydrochloric acid + sodium sulphate");
	}

	// =====================================================================================
	//  无氰浸金：硫代硫酸盐法

	/** 硫代硫酸钠溶液、砷浸出渣、硫粉、金硫代硫酸溶液、金粉、铋粒、硫酸、盐酸 */
	public static void addThiosulfateGoldRecipes() {
		FluidType thio = addonFluid(ModFluids.SODIUM_THIOSULFATE);
		FluidType auThio = addonFluid(ModFluids.GOLD_THIOSULFATE);
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType hcl = HBMCompat.hcl();

		if(thio == Fluids.NONE || auThio == Fluids.NONE) return;
		if(ModItems.arsenical_residue == null || ModBlocks.ore_refractory_gold == null) return;

		// 硫代硫酸钠溶液
		if(na2so3 != Fluids.NONE && !hasChemRecipe("makentmgreatagain.gold.thiosulfate")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.gold.thiosulfate")
				.setupNamed(600, 3_000)
				.inputItems(new OreDictStack(S.dust(), 1))
				.inputFluids(new FluidStack(na2so3, 500))
				.outputFluids(new FluidStack(thio, 550)));
		}

		// 砷浸出渣、硫粉、金硫代硫酸溶液
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.gold.leach")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.gold.leach", 120_000)
				.setupNamed(2_400, 3_000)
				.inputItems(new ComparableStack(ModBlocks.ore_refractory_gold, 1), new OreDictStack(CU.dust(), 1))
				.inputFluids(new FluidStack(thio, 1_000), new FluidStack(Fluids.OXYGEN, 500))
				.outputItems(new ItemStack(ModItems.arsenical_residue, 1),
							 new ItemStack(com.hbm.items.ModItems.sulfur, 2))
				.outputFluids(new FluidStack(auThio, 1_000)));
		}

		// Duna 变体：ComparableStack 精确匹配方块，同参数另登记一条，产物与主世界完全一致
		if(ModBlocks.ore_refractory_gold_duna != null && !LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.gold.leachDuna")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.gold.leachDuna", 120_000)
				.setupNamed(2_400, 3_000)
				.inputItems(new ComparableStack(ModBlocks.ore_refractory_gold_duna, 1), new OreDictStack(CU.dust(), 1))
				.inputFluids(new FluidStack(thio, 1_000), new FluidStack(Fluids.OXYGEN, 500))
				.outputItems(new ItemStack(ModItems.arsenical_residue, 1),
							 new ItemStack(com.hbm.items.ModItems.sulfur, 2))
				.outputFluids(new FluidStack(auThio, 1_000)));
		}

		// 金粉、铋粒、硫代硫酸钠溶液
		if(!hasChemRecipe("makentmgreatagain.gold.cementation")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.gold.cementation")
				.setupNamed(600, 2_500)
				.inputItems(new ComparableStack(ModItems.powder_zinc_isp, 1))
				// inputFluids 是赋值语义，多流体必须放进同一次调用
				.inputFluids(new FluidStack(auThio, 500), new FluidStack(Fluids.GAS_COKER, 2_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 4),
							 new ItemStack(com.hbm.items.ModItems.nugget_bismuth, 1))
				.outputFluids(new FluidStack(thio, 350)));
		}

		// 硫酸、盐酸
		if(hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.chloralkali.thiosulfateDechlor")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chloralkali.thiosulfateDechlor")
				.setupNamed(700, 3_500)
				.inputFluids(new FluidStack(thio, 500), new FluidStack(Fluids.CHLORINE, 1_000), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 400), new FluidStack(hcl, 400)));
		}

		MakeNTMGreatAgain.LOG.info("Added thiosulphate gold recipes: synth/leach/cementation/thiosulphate-dechlor");
	}

	/** 金粒 */
	public static void addRefractoryGoldSmelting() {
		if(ModBlocks.ore_refractory_gold == null) return;
		GameRegistry.addSmelting(ModBlocks.ore_refractory_gold, new ItemStack(Items.gold_nugget, 1), 0.1F);
		if(ModBlocks.ore_refractory_gold_duna != null) {
			GameRegistry.addSmelting(ModBlocks.ore_refractory_gold_duna, new ItemStack(Items.gold_nugget, 1), 0.1F);
		}
	}


	// ==================== 硫与锌的出口 ====================

	/** 二氧化硫 */
	public static void addSulfurCombinationRecipe() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		if(so2 == Fluids.NONE) return;

		// 表键是 AStack（物品键 / 矿辞键都可能是），不能拿 S.dust() 这个 String 当键去查；
		// 用「有没有键能匹配硫磺物品」判重，同时兜住宿主自带的同类配方。
		// recipes 是 raw Map，迭代按 Object 走
		ItemStack sulfur = new ItemStack(com.hbm.items.ModItems.sulfur);
		for(Object keyObj : CombinationRecipes.recipes.keySet()) {
			if(keyObj instanceof AStack && ((AStack) keyObj).matchesRecipe(sulfur, true)) return;
		}

		CompatRecipeRegistry.registerCombination(
			new OreDictStack(S.dust(), 1),
			null,
			new FluidStack(so2, 1_400));

		MakeNTMGreatAgain.LOG.info("Added combination furnace recipe: 1 sulfur dust -> 1400 SO2");
	}

	/** 二氧化硫 */
	public static void addSulfurPyroRecipe() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		if(so2 == Fluids.NONE) return;

		// 热解炉配方表是 List 不是 Map，只能遍历查重
		for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
			if(rec.inputItem instanceof OreDictStack && S.dust().equals(((OreDictStack) rec.inputItem).name)) return;
		}

		CompatRecipeRegistry.registerPyro(
			new FluidStack(Fluids.OXYGEN, 500),
			new OreDictStack(S.dust(), 2),
			new FluidStack(so2, 3_000),
			null,
			200);

		MakeNTMGreatAgain.LOG.info("Added pyro oven recipe: 2 sulfur dust + 500 O2 -> 3000 SO2");
	}

	/** 砷化氢、砷粒 */
	public static void addArsineCrackingRecipe() {
		FluidType arsine = addonFluid(ModFluids.ARSINE);
		if(arsine == Fluids.NONE) return;

		// 热解炉配方表是 List 不是 Map，只能遍历查重
		for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
			if(rec.inputFluid != null && rec.inputFluid.type == arsine) return;
		}

		// 砷粒、液氢
		CompatRecipeRegistry.registerPyro(
			new FluidStack(arsine, 1_000),
			null,
			new FluidStack(Fluids.HYDROGEN, 1_500),
			new ItemStack(com.hbm.items.ModItems.nugget_arsenic, 1),
			300);

		MakeNTMGreatAgain.LOG.info("Added pyro oven recipe: 1000 arsine -> 1 arsenic nugget + 1500 hydrogen");
	}

	/** 硫酸、锌粉 */
	public static void addZincElectrolyserRecipe() {
		FluidType znSol = addonFluid(ModFluids.ZINC_SULFATE);
		if(znSol == Fluids.NONE) return;
		if(ElectrolyserFluidRecipes.getRecipe(znSol) != null) return;

		ElectrolyserFluidRecipes.recipes.put(znSol,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(750,
				new FluidStack(Fluids.SULFURIC_ACID, 300),
				new FluidStack(Fluids.NONE, 0),
				200,
				new ItemStack(ModItems.powder_zinc_isp, 2)));

		MakeNTMGreatAgain.LOG.info("Added electrolyser recipe: 750 zinc sulphate -> 300 acid + 2 zinc dust");
	}

	// ==================== 海水处理 ====================

	/** 取附属流体；没注册成功返回 NONE，调用方据此跳过整条支线。 */
	private static FluidType addonFluid(ModFluids.FluidDef def) {
		if(def == null || def.type == null) return Fluids.NONE;
		return def.type;
	}
	/** 是否已经注册过这条化工厂配方。 */
	private static boolean hasChemRecipe(String name) {
		return hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, name);
	}
	/** 热解炉配方表是 List 不是 Map，按输入物品查重。 */
	private static boolean hasPyroRecipe(Item item) {
		for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
			if(rec.inputItem instanceof ComparableStack && ((ComparableStack) rec.inputItem).item == item) return true;
		}
		return false;
	}

	/** 浓缩海水、水、碱液、盐水 */
	public static void addSeawaterConcentrationRecipes() {
		FluidType sea = addonFluid(ModFluids.SEA_WATER);
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(sea == Fluids.NONE || conc == Fluids.NONE || brine == Fluids.NONE) return;

		// 浓缩海水、水
		if(!hasChemRecipe("makentmgreatagain.sea.concentrate")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.concentrate")
				.setup(600, 2_000)
				.inputFluids(new FluidStack(sea, 2_000))
				.outputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.WATER, 900)));
		}

		// 碱液、盐水
		if(!hasChemRecipe("makentmgreatagain.sea.mercurycell")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.mercurycell")
				.setup(600, 3_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.MERCURY, 100))
				.outputFluids(new FluidStack(Fluids.LYE, 800), new FluidStack(brine, 200)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: concentrate + mercury cell");
	}

	/** 氯气、碱液 */
	public static void addSaltSeaWaterElectrolyserRecipe() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		if(conc == Fluids.NONE) return;
		if(ElectrolyserFluidRecipes.getRecipe(conc) != null) return;

		// 氯气、碱液
		ElectrolyserFluidRecipes.recipes.put(conc,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1000,
				new FluidStack(Fluids.CHLORINE, 1_000),
				new FluidStack(Fluids.LYE, 400)));

		MakeNTMGreatAgain.LOG.info("Added electrolyser recipe: salt sea water -> chlorine + lye");
	}

	/** 氯化钾溶液、氯化钙溶液、硝石粉、盐酸、溴粉、盐水 */
	public static void addSeawaterSaltRecipes() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		if(conc == Fluids.NONE || brine == Fluids.NONE) return;

		// 氯化钾溶液、氯化钙溶液
		if(!hasChemRecipe("makentmgreatagain.sea.saltSplit")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.saltSplit")
				.setup(500, 4_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 300))
				.outputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 350), new FluidStack(Fluids.CALCIUM_CHLORIDE, 300)));
		}

		// 硝石粉、盐酸
		if(hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.sea.niter")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.niter")
				.setup(400, 3_000)
				.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 300), new FluidStack(Fluids.NITRIC_ACID, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.niter, 2))
				.outputFluids(new FluidStack(hcl, 250)));
		}

		// 溴粉、盐水
		if(!hasChemRecipe("makentmgreatagain.sea.bromine")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.bromine")
				.setup(500, 5_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.CHLORINE, 300), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_bromine, 2))
				.outputFluids(new FluidStack(brine, 700)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: salt split + niter + bromine");
	}

	/** 石膏、盐酸、液态混凝土、水、钙溶液 */
	public static void addSeawaterGypsumRecipes() {
		FluidType sea = addonFluid(ModFluids.SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		if(sea == Fluids.NONE || hcl == Fluids.NONE) return;

		// 石膏、盐酸
		if(!hasChemRecipe("makentmgreatagain.sea.gypsum")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.gypsum")
				.setup(500, 4_000)
				.inputFluids(new FluidStack(sea, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.gypsum, 2))
				.outputFluids(new FluidStack(hcl, 400)));
		}

		// 液态混凝土
		if(!hasChemRecipe("makentmgreatagain.sea.concrete")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.concrete")
				.setup(150, 100)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_cement, 4),
							new ComparableStack(ModItems.gypsum, 1),
							new OreDictStack(KEY_SAND, 8))
				.inputFluids(new FluidStack(Fluids.WATER, 2_000))
				.outputFluids(new FluidStack(Fluids.CONCRETE, 20_000)));
		}

		// 水、钙溶液
		if(brine != Fluids.NONE && !hasChemRecipe("makentmgreatagain.sea.neutralize")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.neutralize")
				.setup(300, 2_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_limestone, 1))
				.inputFluids(new FluidStack(brine, 1_000))
				.outputFluids(new FluidStack(Fluids.WATER, 800), new FluidStack(Fluids.CALCIUM_SOLUTION, 100)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: gypsum + concrete + neutralise");
	}

	/** 硫粉、钙粉 */
	public static void addGypsumBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.sea.gypsumToSulfur")) return;

		// 硫粉、钙粉
		RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.sea.gypsumToSulfur")
			.setDuration(400)
			.inputItems(new ComparableStack(ModItems.gypsum, 2), new OreDictStack(ANY_COKE.gem()))
			.outputItems(new ItemStack(com.hbm.items.ModItems.sulfur, 2),
						 new ItemStack(com.hbm.items.ModItems.powder_calcium, 1)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: gypsum + coke -> sulfur + calcium powder");
	}
	

	/** 白色染料 */
	public static void addGypsumDyeRecipe() {
		// 白色染料
		GameRegistry.addShapelessRecipe(new ItemStack(Items.dye, 2, 15), ModItems.gypsum, ModItems.gypsum);

		MakeNTMGreatAgain.LOG.info("Added crafting recipe: gypsum x2 -> white dye x2");
	}

	/** 骨粉 */
	public static void addGypsumBoneMealRecipe() {
		if(!hasChemRecipe("makentmgreatagain.gypsum.bonemeal")) {
			// 骨粉
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.gypsum.bonemeal")
				.setup(300, 2_000)
				.inputItems(new ComparableStack(ModItems.gypsum, 1), new ComparableStack(Items.dye, 1, 15))
				.inputFluids(new FluidStack(Fluids.WATER, 500))
				.outputItems(new ItemStack(Items.dye, 5, 15)));
		}

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: gypsum + bone meal -> bone meal x5");
	}

	/** 砖块 */
	public static void addGypsumBrickBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.gypsum.brick")) return;

		// 砖块
		RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.gypsum.brick")
			.setDuration(400)
			.inputItems(new ComparableStack(Items.clay_ball, 2), new ComparableStack(ModItems.gypsum, 1))
			.outputItems(new ItemStack(Items.brick, 3)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: clay + gypsum -> brick x3");
	}

	/** 含矿液、盐水、锂粉、铀粉、硼粉 */
	public static void addSeawaterAdsorptionRecipes() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(conc == Fluids.NONE || liquor == Fluids.NONE || brine == Fluids.NONE) return;

		// 含矿液、盐水
		if(!hasChemRecipe("makentmgreatagain.sea.adsorb")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.adsorb")
				.setup(800, 8_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.ball_resin, 1))
				.inputFluids(new FluidStack(conc, 4_000))
				.outputFluids(new FluidStack(liquor, 500), new FluidStack(brine, 3_400)));
		}

		// 锂粉、盐水
		if(!hasChemRecipe("makentmgreatagain.sea.lithium")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.lithium")
				.setup(600, 6_000)
				.inputFluids(new FluidStack(liquor, 500), new FluidStack(Fluids.LYE, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_lithium, 2))
				.outputFluids(new FluidStack(brine, 200)));
		}

		// 铀粉
		if(!hasChemRecipe("makentmgreatagain.sea.uranium")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.uranium")
				.setup(1_200, 10_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.NITRIC_ACID, 500), new FluidStack(Fluids.KEROSENE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 3)));
		}

		// 硼粉、盐水
		if(!hasChemRecipe("makentmgreatagain.sea.boron")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.boron")
				.setup(900, 8_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_boron, 1))
				.outputFluids(new FluidStack(brine, 600)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: adsorb + lithium + uranium + boron");
	}

	/** 铀粉、硝酸 */
	public static void addSeawaterPUREXRecipe() {
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		if(liquor == Fluids.NONE) return;
		if(hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.sea.purex")) return;

		// 铀粉、硝酸
		RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.sea.purex")
			.setup(1_200, 10_000)
			.inputFluids(new FluidStack(liquor, 1_000),
						 new FluidStack(Fluids.NITRIC_ACID, 500),
						 new FluidStack(Fluids.KEROSENE, 500))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 6))
			.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 200)));

		MakeNTMGreatAgain.LOG.info("Added PUREX recipe: ore liquor -> uranium powder x6 + nitric acid recycle");
	}

	// ==================== 锶线 ====================
	public static void addStrontiumChemicalRecipe() {
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(brine == Fluids.NONE) return;
		if(hasChemRecipe("makentmgreatagain.sea.strontium")) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.strontium")
			.setup(600, 5_000)
			.inputFluids(new FluidStack(brine, 6_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
			.outputItems(new ItemStack(ModItems.celestite, 1),
						 new ItemStack(ModItems.gypsum, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: brine + sulfuric acid -> celestite + gypsum");
	}


	/** 氧化锶 */
	public static void addStrontiumSmeltingRecipe() {
		GameRegistry.addSmelting(ModItems.celestite, new ItemStack(ModItems.strontium_oxide, 1), 0.35F);

		MakeNTMGreatAgain.LOG.info("Added furnace recipe: celestite -> strontium oxide");
	}

	/** 锶粉、炉渣锭 */
	public static void addStrontiumBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.sea.strontiumReduce")) return;

		RecipeRegistrar.add(BlastFurnaceRecipesNT.INSTANCE, (GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.sea.strontiumReduce")
			.setDuration(400)
			.inputItems(new ComparableStack(ModItems.strontium_oxide, 3),
						new ComparableStack(com.hbm.items.ModItems.powder_aluminium, 2))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_strontium, 3),
						 new ItemStack(com.hbm.items.ModItems.ingot_raw, 1, Mats.MAT_SLAG.id)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: strontium oxide + aluminium -> strontium powder + slag");
	}

	/** 小撮钕粉 */
	public static void addRareEarthNeodymiumRecipe() {
		if(hasChemRecipe("makentmgreatagain.sea.rareEarthNd")) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.sea.rareEarthNd")
			.setup(600, 6_000)
			.inputItems(new ComparableStack(com.hbm.items.ModItems.chunk_ore, 6, EnumChunkType.RARE))
			.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 3)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: rare earth chunks + sulfuric acid -> neodymium x1 ingot");
	}

	/** 氦-4、二氧化碳 */
	public static void addGasHeliumRecipe() {
		if(hasChemRecipe("makentmgreatagain.gas.helium")) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.gas.helium")
			.setupNamed(400, 5_000)
			.setIcon(com.hbm.items.ModItems.gas_full, Fluids.HELIUM4.getID())
			.inputFluids(new FluidStack(Fluids.GAS, 20_000), new FluidStack(Fluids.IONGEL, 500))
			.outputFluids(new FluidStack(Fluids.HELIUM4, 100), new FluidStack(Fluids.CARBONDIOXIDE, 1_000)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: natural gas -> helium-4 + carbon dioxide");
	}

	/** 甘油、生物燃料、硝基苯、水、硝基甲烷、二氧化碳、二硝基苯、高辛烷航空汽油、硝酸甘油、硝化煤油萃取剂、四氧化二氮、甲醇、氨、硝酸 */
	public static void addPetrochemRecipes() {
		FluidType glycerol = addonFluid(ModFluids.GLYCEROL);
		FluidType nitrobenzene = addonFluid(ModFluids.NITROBENZENE);
		FluidType dinitrobenzene = addonFluid(ModFluids.DINITROBENZENE);
		FluidType nitromethane = addonFluid(ModFluids.NITROMETHANE);
		FluidType tbpkero = addonFluid(ModFluids.TBP_KERO);
		FluidType avgas = addonFluid(ModFluids.AVGAS_RU);
		FluidType n2o4 = addonFluid(ModFluids.N2O4);
		FluidType methanol = addonFluid(ModFluids.METHANOL);
		FluidType ammonia = addonFluid(ModFluids.AMMONIA);
		if(glycerol == Fluids.NONE || nitrobenzene == Fluids.NONE || dinitrobenzene == Fluids.NONE
			|| nitromethane == Fluids.NONE || tbpkero == Fluids.NONE || avgas == Fluids.NONE || n2o4 == Fluids.NONE) {
			MakeNTMGreatAgain.LOG.warn("[配方] 石油三期流体未注册，跳过整组石油配方");
			return;
		}
		int pu = GeneralConfig.enable528PressurizedRecipes ? 1 : 0;

		// 甘油、生物燃料
		if(!hasChemRecipe("makentmgreatagain.petro.glycerol")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.glycerol")
				.setup(250, 400)
				.inputFluids(new FluidStack(Fluids.SUNFLOWEROIL, 1_000), new FluidStack(Fluids.ETHANOL, 300), new FluidStack(Fluids.LYE, 200))
				.outputFluids(new FluidStack(glycerol, 500), new FluidStack(Fluids.BIOFUEL, 800)));
		}

		// 甘油、生物燃料
		if(!hasChemRecipe("makentmgreatagain.petro.glycerol.fish")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.glycerol.fish")
				.setup(250, 400)
				.inputFluids(new FluidStack(Fluids.FISHOIL, 1_000), new FluidStack(Fluids.ETHANOL, 300), new FluidStack(Fluids.LYE, 200))
				.outputFluids(new FluidStack(glycerol, 400), new FluidStack(Fluids.BIOFUEL, 700)));
		}

		// 硝基苯、水
		if(!hasChemRecipe("makentmgreatagain.petro.nitrobenzene.coal")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.nitrobenzene.coal")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.COALOIL, 500), new FluidStack(Fluids.NITRIC_ACID, 300), new FluidStack(Fluids.SULFURIC_ACID, 1_200))
				.outputFluids(new FluidStack(nitrobenzene, 1_600), new FluidStack(Fluids.WATER, 400)));
		}

		// 硝基甲烷、水、二氧化碳
		if(!hasChemRecipe("makentmgreatagain.petro.nitromethane")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.nitromethane")
				.setup(400, 1_000)
				.inputFluids(new FluidStack(Fluids.GAS, 500, pu), new FluidStack(Fluids.NITRIC_ACID, 300), new FluidStack(Fluids.WATER, 1_200))
				.outputFluids(new FluidStack(nitromethane, 1_500), new FluidStack(Fluids.CARBONDIOXIDE, 500)));
		}

		// 硝基苯、水
		if(!hasChemRecipe("makentmgreatagain.petro.nitrobenzene.aromatic")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.nitrobenzene.aromatic")
				.setup(280, 800)
				.inputFluids(new FluidStack(Fluids.AROMATICS, 500), new FluidStack(Fluids.NITRIC_ACID, 250), new FluidStack(Fluids.SULFURIC_ACID, 750))
				.outputFluids(new FluidStack(nitrobenzene, 1_500), new FluidStack(Fluids.WATER, 500)));
		}

		// 二硝基苯、水
		if(!hasChemRecipe("makentmgreatagain.petro.dinitrobenzene")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.dinitrobenzene")
				.setup(450, 1_000)
				.inputFluids(new FluidStack(nitrobenzene, 350), new FluidStack(Fluids.NITRIC_ACID, 850), new FluidStack(Fluids.SULFURIC_ACID, 1_300))
				.outputFluids(new FluidStack(dinitrobenzene, 2_000), new FluidStack(Fluids.WATER, 500)));
		}

		// 高辛烷航空汽油
		if(!hasChemRecipe("makentmgreatagain.petro.avgas.blend")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.avgas.blend")
				.setup(150, 600)
				.inputFluids(new FluidStack(Fluids.GASOLINE, 1_200), new FluidStack(Fluids.AROMATICS, 200), new FluidStack(Fluids.XYLENE, 100))
				.outputFluids(new FluidStack(avgas, 1_500)));
		}

		// 硝酸甘油、水
		if(!hasChemRecipe("makentmgreatagain.petro.nitroglycerin")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.nitroglycerin")
				.setup(200, 700)
				.inputFluids(new FluidStack(glycerol, 800), new FluidStack(Fluids.NITRIC_ACID, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
				.outputFluids(new FluidStack(Fluids.NITROGLYCERIN, 1_000), new FluidStack(Fluids.WATER, 400)));
		}

		// 硝化煤油萃取剂、水
		if(!hasChemRecipe("makentmgreatagain.petro.tbpkero")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.tbpkero")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.KEROSENE, 500), new FluidStack(Fluids.NITRIC_ACID, 300), new FluidStack(Fluids.SULFURIC_ACID, 500))
				.outputFluids(new FluidStack(tbpkero, 1_000), new FluidStack(Fluids.WATER, 300)));
		}

		// 四氧化二氮、水
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.petro.n2o4")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.petro.n2o4", 300_000)
				.setup(350, 1_000)
				.inputFluids(new FluidStack(Fluids.NITRIC_ACID, 1_000, pu), new FluidStack(Fluids.OXYGEN, 2_000))
				.outputFluids(new FluidStack(n2o4, 2_500), new FluidStack(Fluids.WATER, 500)));
		}

		// 高辛烷航空汽油
		if(!hasChemRecipe("makentmgreatagain.petro.avgas.reform")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.avgas.reform")
				.setup(180, 800)
				.inputFluids(new FluidStack(Fluids.GASOLINE, 1_500), new FluidStack(Fluids.REFORMATE, 300), new FluidStack(Fluids.AROMATICS, 200))
				.outputFluids(new FluidStack(avgas, 2_000)));
		}

		// 甲醇
		if(methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.petro.methanol")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.methanol")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.SYNGAS, 1_000), new FluidStack(Fluids.HYDROGEN, 2_000))
				.outputFluids(new FluidStack(methanol, 3_000)));
		}

		// 航天版可能复用同一氨流体，但其配方 ID/工艺不同，不代表附属产线已存在。
		if(ammonia != Fluids.NONE && !hasChemRecipe("makentmgreatagain.petro.ammonia")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.ammonia")
				.setup(250, 900)
				.inputFluids(new FluidStack(Fluids.AIR, 1_500), new FluidStack(Fluids.HYDROGEN, 600))
				.outputFluids(new FluidStack(ammonia, 800), new FluidStack(Fluids.WATER, 200)));
		}
		// 下游独立判重；允许宿主氨配方与附属硝酸配方并存。
		if(ammonia != Fluids.NONE && !hasChemRecipe("makentmgreatagain.petro.nitricfromammonia")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.petro.nitricfromammonia")
				.setup(100, 100)
				.inputFluids(new FluidStack(ammonia, 1_000), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 1_000)));
		}

		// 528 加压：天然气 / 硝酸必须先过压缩机
		if(pu == 1) {
			CompressorRecipes.recipes.put(new Pair(Fluids.GAS, 0),
				new CompressorRecipes.CompressorRecipe(1_000, new FluidStack(Fluids.GAS, 1_000, 1), 20));
			CompressorRecipes.recipes.put(new Pair(Fluids.NITRIC_ACID, 0),
				new CompressorRecipes.CompressorRecipe(1_000, new FluidStack(Fluids.NITRIC_ACID, 1_000, 1), 20));
		}

		MakeNTMGreatAgain.LOG.info("Added 11 chemical plant recipes: oil phase 1-3 petrochemicals");
	}

	/** 苯胺、水 */
	public static void addAnilineHydrotreatingRecipe() {
		FluidType nb = addonFluid(ModFluids.NITROBENZENE);
		FluidType an = addonFluid(ModFluids.ANILINE);
		if(nb == Fluids.NONE || an == Fluids.NONE) return;
		if(HydrotreatingRecipes.recipes.containsKey(nb)) return;

		HydrotreatingRecipes.recipes.put(nb, new Triplet<FluidStack, FluidStack, FluidStack>(
			new FluidStack(Fluids.HYDROGEN, 15, 1),
			new FluidStack(an, 95),
			new FluidStack(Fluids.WATER, 5)));

		MakeNTMGreatAgain.LOG.info("Added hydrotreating recipe: nitrobenzene + hydrogen -> aniline");
	}

	/** 减压重油、重整油、减压轻油、重整气、芳香烃、高硫天然气、液氢 */
	public static void addBTXUpstreamRecipes() {

		// 减压重油、重整油、减压轻油、重整气
		if(!VacuumRefineryRecipes.recipes.containsKey(Fluids.CRACKOIL_DS)) {
			VacuumRefineryRecipes.recipes.put(Fluids.CRACKOIL_DS, new VacuumRefineryRecipes.VacuumRefineryRecipe(
				new FluidStack(Fluids.HEAVYOIL_VACUUM, 30),
				new FluidStack(Fluids.REFORMATE, 35),
				new FluidStack(Fluids.LIGHTOIL_VACUUM, 25),
				new FluidStack(Fluids.REFORMGAS, 10)));
		}

		// 芳香烃、高硫天然气
		if(!HydrotreatingRecipes.recipes.containsKey(Fluids.COALGAS)) {
			HydrotreatingRecipes.recipes.put(Fluids.COALGAS, new Triplet<FluidStack, FluidStack, FluidStack>(
				new FluidStack(Fluids.HYDROGEN, 10, 1),
				new FluidStack(Fluids.AROMATICS, 20),
				new FluidStack(Fluids.SOURGAS, 10)));
		}

		// 重整油、芳香烃、液氢
		if(!ReformingRecipes.recipes.containsKey(Fluids.NAPHTHA_DS)) {
			ReformingRecipes.recipes.put(Fluids.NAPHTHA_DS, new Triplet<FluidStack, FluidStack, FluidStack>(
				new FluidStack(Fluids.REFORMATE, 50),
				new FluidStack(Fluids.AROMATICS, 20),
				new FluidStack(Fluids.HYDROGEN, 10)));
		}

		MakeNTMGreatAgain.LOG.info("[BTX] 真空炼油厂 / 加氢 / 重整 上游注入完成，真空表当前 " + VacuumRefineryRecipes.recipes.size()
			+ " 条，含脱硫裂化油：" + VacuumRefineryRecipes.recipes.containsKey(Fluids.CRACKOIL_DS));
	}

	/** 柴油、高硫天然气 */
	public static void addHeavyOilHydrocrackingRecipe() {

		if(!HydrotreatingRecipes.recipes.containsKey(Fluids.HEAVYOIL)) {
			HydrotreatingRecipes.recipes.put(Fluids.HEAVYOIL, new Triplet<FluidStack, FluidStack, FluidStack>(
				new FluidStack(Fluids.HYDROGEN, 12, 1),
				new FluidStack(Fluids.DIESEL, 55),
				new FluidStack(Fluids.SOURGAS, 20)));
			MakeNTMGreatAgain.LOG.info("[重油] 加氢表注入重油加氢裂化，当前 " + HydrotreatingRecipes.recipes.size() + " 条");
		}
	}

	/** 裂化油、石油气、芳香烃 */
	public static void addHeavyOilCrackingRecipes() {

		if(!CrackingRecipes.cracking.containsKey(Fluids.HEAVYOIL)) {
			CrackingRecipes.cracking.put(Fluids.HEAVYOIL, new Pair<FluidStack, FluidStack>(
				new FluidStack(Fluids.CRACKOIL, 70), new FluidStack(Fluids.PETROLEUM, 30)));
		}

		if(!CrackingRecipes.cracking.containsKey(Fluids.HEAVYOIL_VACUUM)) {
			CrackingRecipes.cracking.put(Fluids.HEAVYOIL_VACUUM, new Pair<FluidStack, FluidStack>(
				new FluidStack(Fluids.CRACKOIL, 55), new FluidStack(Fluids.AROMATICS, 30)));
		}

		MakeNTMGreatAgain.LOG.info("[重油] 裂化表注入重油 / 减压重油，当前 " + CrackingRecipes.cracking.size()
			+ " 条，含重油：" + CrackingRecipes.cracking.containsKey(Fluids.HEAVYOIL)
			+ "，含减压重油：" + CrackingRecipes.cracking.containsKey(Fluids.HEAVYOIL_VACUUM));
	}

	/** 润滑油、沥青、高纯炭黑、水 */
	public static void addHeavyOilChemicalRecipes() {

		// 润滑油、沥青
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.heavy.deasphalt")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.heavy.deasphalt", 120_000)
				.setup(300, 2_000)
				.inputFluids(new FluidStack(Fluids.HEAVYOIL_VACUUM, 1_000), new FluidStack(Fluids.LPG, 500))
				.outputFluids(new FluidStack(Fluids.LUBRICANT, 500), new FluidStack(Fluids.BITUMEN, 300)));
		}

		// 高纯炭黑、水
		if(ModItems.carbon_black_pure != null && !LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.heavy.carbonblack")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.heavy.carbonblack", 150_000)
				.setup(400, 4_500)
				.inputFluids(new FluidStack(Fluids.HEAVYOIL, 1_000), new FluidStack(Fluids.OXYGEN, 1_000),
							 new FluidStack(Fluids.CRYOGEL, 40))
				.outputItems(new ItemStack(ModItems.carbon_black_pure, 4))
				.outputFluids(new FluidStack(Fluids.WATER, 600)));
		}
	}

	/** 重油下游汇总入口 */
	public static void addHeavyOilDownstreamRecipes() {
		addHeavyOilHydrocrackingRecipe();
		addHeavyOilCrackingRecipes();
		addHeavyOilChemicalRecipes();
	}

	/** 聚合物锭、电木锭、高辛烷航空汽油、高级固态燃料、二硝基苯、水、铀粉、硝酸、生物燃料、甘油、苯胺、有机溶剂 */
	public static void addPetrochemDownstreamRecipes() {
		FluidType glycerol = addonFluid(ModFluids.GLYCEROL);
		FluidType nitrobenzene = addonFluid(ModFluids.NITROBENZENE);
		FluidType dinitrobenzene = addonFluid(ModFluids.DINITROBENZENE);
		FluidType nitromethane = addonFluid(ModFluids.NITROMETHANE);
		FluidType aniline = addonFluid(ModFluids.ANILINE);
		FluidType tbpkero = addonFluid(ModFluids.TBP_KERO);
		FluidType avgas = addonFluid(ModFluids.AVGAS_RU);
		FluidType n2o4 = addonFluid(ModFluids.N2O4);
		FluidType methanol = addonFluid(ModFluids.METHANOL);

		// 聚合物锭
		if(aniline != Fluids.NONE && glycerol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.polyurethane")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.polyurethane")
				.setup(100, 600)
				.inputFluids(new FluidStack(aniline, 400), new FluidStack(Fluids.PHOSGENE, 400),
							 new FluidStack(glycerol, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_polymer, 8)));
		}

		// 电木锭
		if(methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.bakelite")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.bakelite")
				.setup(150, 800)
				.inputFluids(new FluidStack(methanol, 600), new FluidStack(Fluids.AROMATICS, 600))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_bakelite, 8)));
		}

		// 高辛烷航空汽油
		if(methanol != Fluids.NONE && avgas != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.avgas.mtbe")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.avgas.mtbe")
				.setup(150, 500)
				.inputFluids(new FluidStack(methanol, 1_000), new FluidStack(Fluids.UNSATURATEDS, 500),
							 new FluidStack(Fluids.GASOLINE, 500))
				.outputFluids(new FluidStack(avgas, 2_000)));
		}

		// 高级固态燃料
		if(nitromethane != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.advfuel")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.advfuel")
				.setup(120, 400)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.solid_fuel, 2))
				.inputFluids(new FluidStack(nitromethane, 400), new FluidStack(Fluids.NITRIC_ACID, 100))
				.outputItems(new ItemStack(com.makentmgreatagain.init.ModItems.solid_fuel_adv, 4)));
		}

		// 二硝基苯、水
		if(n2o4 != Fluids.NONE && nitrobenzene != Fluids.NONE && dinitrobenzene != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.dinitro.n2o4")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.dinitro.n2o4")
				.setup(400, 900)
				.inputFluids(new FluidStack(nitrobenzene, 500), new FluidStack(n2o4, 500),
							 new FluidStack(Fluids.WATER, 1_500))
				.outputFluids(new FluidStack(dinitrobenzene, 2_500)));
		}

		// 铀粉、硝酸
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		if(tbpkero != Fluids.NONE && liquor != Fluids.NONE
			&& !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.down.tbp.purex")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.down.tbp.purex")
				.setup(1_200, 12_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.NITRIC_ACID, 500),
							 new FluidStack(tbpkero, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 9))
				.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 300)));
		}

		// 生物燃料、甘油
		if(methanol != Fluids.NONE && glycerol != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.biodiesel")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.biodiesel")
				.setup(80, 400)
				.inputFluids(new FluidStack(methanol, 300), new FluidStack(Fluids.SUNFLOWEROIL, 1_000),
							 new FluidStack(Fluids.LYE, 100))
				.outputFluids(new FluidStack(Fluids.BIOFUEL, 1_000), new FluidStack(glycerol, 250)));
		}

		// 苯胺、水
		FluidType hcl = HBMCompat.hcl();
		if(nitrobenzene != Fluids.NONE && aniline != Fluids.NONE && hcl != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.aniline.bechamp")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.aniline.bechamp")
				.setup(120, 5_000)
				.inputItems(new OreDictStack("dustIron", 1))
				.inputFluids(new FluidStack(nitrobenzene, 1_000), new FluidStack(hcl, 200), new FluidStack(Fluids.SULFURIC_ACID, 1_300))
				.outputFluids(new FluidStack(aniline, 2_000), new FluidStack(Fluids.WATER, 500)));
		}

		// 有机溶剂
		if(tbpkero != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.solvent")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.down.solvent")
				.setup(40, 300)
				.inputFluids(new FluidStack(tbpkero, 200), new FluidStack(Fluids.KEROSENE, 400), new FluidStack(Fluids.WATER, 400))
				.outputFluids(new FluidStack(Fluids.SOLVENT, 1_000)));
		}

		MakeNTMGreatAgain.LOG.info("Added 9 petrochemical downstream recipes (PU/bakelite/MTBE/advfuel/N2O4/TBP-PUREX/biodiesel/bechamp/solvent)");
	}

	/** 氦-3
	public static void addFusionHelium3Recipe() {
		if(hasGenericRecipe(FusionRecipes.INSTANCE, "makentmgreatagain.fus.dd3")) return;

		RecipeRegistrar.add(FusionRecipes.INSTANCE, (FusionRecipe) new FusionRecipe("makentmgreatagain.fus.dd3")
			.setInputEnergy(500_000).setOutputEnergy(1_000_000).setOutputFlux(0)
			.setRGB(0.2F, 0.2F, 1F) // 氦等离子体的蓝
			.setNamed().setIcon(com.hbm.items.ModItems.gas_full, Fluids.HELIUM3.getID())
			.setPower(25_000).setDuration(100)
			.inputFluids(new FluidStack(Fluids.DEUTERIUM, 40))
			.outputFluids(new FluidStack(Fluids.HELIUM3, 1_000)));

		MakeNTMGreatAgain.LOG.info("Added fusion recipe: deuterium plasma -> helium-3 (aneutronic branch)");
	}
	**/

	/** 铋粒 */
	public static void addBismuthRecoveryRecipes() {
		// 铋粒
		String[] technetiumRecipes = {
			"purex.zirnoxmeu", "purex.zirnoxmox", "purex.zirnoxmep",
			"purex.zirnoxheu233", "purex.zirnoxheu235", "purex.zirnoxzfbmox"
		};
		for(String name : technetiumRecipes) appendBismuth(name, 3);

		// 铋粒
		appendBismuth("purex.zirnoxnu", 1);

		MakeNTMGreatAgain.LOG.info("Added bismuth byproduct to ZIRNOX PUREX recipes (3 per technetium recipe, 1 for natural uranium)");
	}

	/** 给指定 PUREX 配方追加铋粒输出；配方不存在或已追加过则什么也不做。 */
	private static void appendBismuth(String recipeName, int amount) {
		GenericRecipe recipe = PUREXRecipes.INSTANCE.recipeNameMap.get(recipeName);
		if(recipe == null) return;

		IOutput[] current = recipe.outputItem;
		if(current != null) {
			for(IOutput out : current) {
				if(out instanceof ChanceOutput) {
					ItemStack stack = ((ChanceOutput) out).stack;
					if(stack != null && stack.getItem() == com.hbm.items.ModItems.nugget_bismuth) return;
				}
			}
		}

		int base = current == null ? 0 : current.length;
		// outputItemLimit() == 6，最多加到 6 格
		IOutput[] extended = new IOutput[base + 1];
		if(current != null) System.arraycopy(current, 0, extended, 0, base);
		extended[base] = new ChanceOutput(new ItemStack(com.hbm.items.ModItems.nugget_bismuth, amount));
		recipe.outputItem = extended;
	}

	// ==================== 木浆路线 ====================

	/** 木框架、钢脚手架 */
	public static void addWoodFrameRecipe() {
		if(hasChemRecipe("makentmgreatagain.wood.frame")) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.wood.frame")
			.setup(200, 3_000)
			.inputItems(new ComparableStack(Items.stick, 16),
						new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 4))
			.inputFluids(new FluidStack(Fluids.WATER, 4_000))
			.outputItems(new ItemStack(ModItems.wood_frame, 1),
						 new ItemStack(com.hbm.blocks.ModBlocks.steel_scaffold, 4)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: wood frame (scaffold returned)");
	}

	/** 木质浆料 */
	public static void addWoodMashMixerRecipe() {
		FluidType mash = addonFluid(ModFluids.WOOD_MASH);
		if(mash == Fluids.NONE) return;
		if(MixerRecipes.getOutput(mash) != null) return;

		AddonMixerRecipe recipe = new AddonMixerRecipe(1_000, 100);
		recipe.input1 = new FluidStack(Fluids.WOODOIL, 2_000);
		recipe.input2 = new FluidStack(Fluids.WATER, 1_000);

		MixerRecipes.register(mash, recipe);
		MakeNTMGreatAgain.LOG.info("Added mixer recipe: wood oil + water -> wood mash");
	}

	/** 木质浆料 */
	public static void addWoodMashChemicalRecipe() {
		FluidType mash = addonFluid(ModFluids.WOOD_MASH);
		if(mash == Fluids.NONE) return;
		if(hasChemRecipe("makentmgreatagain.wood.mash")) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.wood.mash")
			.setup(200, 2_500)
			.inputFluids(new FluidStack(Fluids.WOODOIL, 2_000),
						 new FluidStack(Fluids.WATER, 1_000),
						 new FluidStack(Fluids.LYE, 100))
			.outputFluids(new FluidStack(mash, 1_500)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: wood oil + water + lye -> wood mash");
	}

	/** 白桦浆、金合欢浆、云杉浆、橡木浆、丛林浆、深色橡木浆 */
	public static void addWoodPulpRecipes() {
		// 云杉走亚硫酸盐法：亚硫酸钠 + SO2 > 单 SO2 > 硫酸兜底
		FluidType sulfite = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType sulphiteSalt = addonFluid(ModFluids.SODIUM_SULPHITE);

		FluidStack[] spruceExtras;
		int spruceOutput = 1_500;
		if(sulphiteSalt != Fluids.NONE && sulfite != Fluids.NONE) {
			spruceExtras = new FluidStack[] {
				new FluidStack(sulphiteSalt, 600),
				new FluidStack(sulfite, 200)
			};
			spruceOutput = 1_600;
		} else if(sulfite != Fluids.NONE) {
			spruceExtras = new FluidStack[] {
				new FluidStack(sulfite, 300),
				new FluidStack(Fluids.WATER, 500)
			};
		} else {
			spruceExtras = new FluidStack[] {
				new FluidStack(Fluids.SULFURIC_ACID, 300),
				new FluidStack(Fluids.WATER, 500)
			};
		}

		addWoodPulp("makentmgreatagain.pulp.birch",    ModFluids.BIRCH_PULP,    1_800, 800, null,
			new FluidStack(Fluids.WATER, 800));
		addWoodPulp("makentmgreatagain.pulp.acacia",   ModFluids.ACACIA_PULP,   1_600, 500, null,
			new FluidStack(Fluids.LYE, 200), new FluidStack(Fluids.PEROXIDE, 150));
		addWoodPulp("makentmgreatagain.pulp.spruce",   ModFluids.SPRUCE_PULP,   spruceOutput, 500, null,
			spruceExtras);
		addWoodPulp("makentmgreatagain.pulp.oak",      ModFluids.OAK_PULP,      1_500, 450, null,
			new FluidStack(Fluids.LYE, 250), new FluidStack(Fluids.WATER, 300));
		addWoodPulp("makentmgreatagain.pulp.jungle",   ModFluids.JUNGLE_PULP,   1_450, 600,
			new ComparableStack(com.hbm.items.ModItems.sulfur, 1),
			new FluidStack(Fluids.LYE, 300));

		// 丛林浆：硫酸钠路线
		if(ModItems.powder_sodium_sulfate != null) {
			addWoodPulp("makentmgreatagain.pulp.jungle.sulfate", ModFluids.JUNGLE_PULP, 1_450, 620,
				new ComparableStack(ModItems.powder_sodium_sulfate, 1),
				new FluidStack(Fluids.LYE, 350));
		}

		addWoodPulp("makentmgreatagain.pulp.dark_oak", ModFluids.DARK_OAK_PULP, 1_300, 700, null,
			new FluidStack(Fluids.LYE, 350), new FluidStack(Fluids.STEAM, 500));
	}

	/** 单条制浆配方：木质浆料 1000 + 助剂 → 该种木浆。item 可为 null（纯流体工艺）。 */
	private static void addWoodPulp(String name, ModFluids.FluidDef pulpDef, int output,
			int duration, AStack item, FluidStack... extras) {
		FluidType mash = addonFluid(ModFluids.WOOD_MASH);
		FluidType pulp = addonFluid(pulpDef);
		if(mash == Fluids.NONE || pulp == Fluids.NONE) return;
		if(hasChemRecipe(name)) return;

		FluidStack[] in = new FluidStack[extras.length + 1];
		in[0] = new FluidStack(mash, 1_000);
		System.arraycopy(extras, 0, in, 1, extras.length);

		// 只有 jungle.sulfate 有中文名，其余不加 setNamed()
		GenericRecipe recipe = new GenericRecipe(name);
		if(name.equals("makentmgreatagain.pulp.jungle.sulfate")) recipe.setNamed();
		recipe.setup(duration, 3_000)
			.inputFluids(in)
			.outputFluids(new FluidStack(pulp, output));
		if(item != null) recipe.inputItems(item);

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, recipe);
	}

	/** 橡木原木、云杉原木、白桦原木、丛林木、金合欢原木、深色橡木原木 */
	public static void addWoodPulpCrystallizerRecipes() {
		if(ModItems.wood_frame == null) return;

		// 原版原木：log 前 4 种，log2 放金合欢 / 深色橡木
		crystallize(ModFluids.OAK_PULP,      Blocks.log,  0);   // 橡木
		crystallize(ModFluids.SPRUCE_PULP,   Blocks.log,  1);   // 云杉
		crystallize(ModFluids.BIRCH_PULP,    Blocks.log,  2);   // 白桦
		crystallize(ModFluids.JUNGLE_PULP,   Blocks.log,  3);   // 丛林
		crystallize(ModFluids.ACACIA_PULP,   Blocks.log2, 0);   // 金合欢
		crystallize(ModFluids.DARK_OAK_PULP, Blocks.log2, 1);   // 深色橡木

		MakeNTMGreatAgain.LOG.info("Added crystallizer recipes: wood frame + pulp -> 4 logs");
	}

	/** 单条结晶：registerRecipe 的键是 (固体, 流体类型)，同键覆盖，重复调用幂等。 */
	private static void crystallize(ModFluids.FluidDef def, net.minecraft.block.Block log, int logMeta) {
		FluidType pulp = addonFluid(def);
		if(pulp == Fluids.NONE) return;

		CrystallizerRecipes.CrystallizerRecipe recipe =
			new CrystallizerRecipes.CrystallizerRecipe(new ItemStack(log, 4, logMeta), 600);

		CrystallizerRecipes.registerRecipe(new ComparableStack(ModItems.wood_frame), recipe,
			new FluidStack(pulp, 4_000));
	}

	/** 删除钍232→钍燃料棒的除气机配方（锭/粒/坯料三种形态，用户要求）。
	 *  挂 OutgasserRecipes 重载监听 + postInit 兜底，/ntmreload 重建后仍会再删一次。 */
	public static void removeThoriumFuelOutgasserRecipes() {
		OutgasserRecipes.recipes.remove(new OreDictStack(TH232.ingot()));
		OutgasserRecipes.recipes.remove(new OreDictStack(TH232.nugget()));
		OutgasserRecipes.recipes.remove(new OreDictStack(TH232.billet()));
	}

	/** 木浆制油三段链（数值定稿见 BioOilChainSpec）：
	 *  ① 化学工厂：木浆2000+蒸汽1500+氢500 → 生物原油2000+水1000（六种木浆各一条）
	 *  ② 大化工：生物原油1000+氢1500 → 粗合成油1500+CO₂500（TU20万，催化剂1级）
	 *  ③ 大化工：粗合成油1000+乙醇1000+氢1000+氧1000 → 原油2500+水1000（TU35万，催化剂2级） */
	public static void addBioOilChainRecipes() {
		FluidType bio = addonFluid(ModFluids.BIO_CRUDE);
		FluidType synth = addonFluid(ModFluids.RAW_SYNTH_OIL);
		if(bio == Fluids.NONE || synth == Fluids.NONE) return;

		// ① 浆料解聚：六种木浆各一条
		bioDepoly("makentmgreatagain.biooil.depoly.birch",    ModFluids.BIRCH_PULP,    bio);
		bioDepoly("makentmgreatagain.biooil.depoly.oak",      ModFluids.OAK_PULP,      bio);
		bioDepoly("makentmgreatagain.biooil.depoly.spruce",   ModFluids.SPRUCE_PULP,   bio);
		bioDepoly("makentmgreatagain.biooil.depoly.jungle",   ModFluids.JUNGLE_PULP,   bio);
		bioDepoly("makentmgreatagain.biooil.depoly.acacia",   ModFluids.ACACIA_PULP,   bio);
		bioDepoly("makentmgreatagain.biooil.depoly.dark_oak", ModFluids.DARK_OAK_PULP, bio);

		// 木油直制生物原油（大化工独占，跳过制浆，催化剂1级=催化转化器）
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.biooil.wood")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.biooil.wood",
					BioOilChainSpec.WOOD_HEAT, BioOilChainSpec.WOOD_CATALYST)
				.setup(BioOilChainSpec.WOOD_DURATION, BioOilChainSpec.WOOD_POWER)
				.inputFluids(new FluidStack(Fluids.WOODOIL, BioOilChainSpec.WOOD_IN[0]),
							 new FluidStack(Fluids.HYDROGEN, BioOilChainSpec.WOOD_IN[1]),
							 new FluidStack(Fluids.STEAM, BioOilChainSpec.WOOD_IN[2]))
				.outputFluids(new FluidStack(bio, BioOilChainSpec.WOOD_OUT[0]),
							  new FluidStack(Fluids.WATER, BioOilChainSpec.WOOD_OUT[1]),
							  new FluidStack(Fluids.CARBONDIOXIDE, BioOilChainSpec.WOOD_OUT[2])));
		}

		// ② 加氢脱氧（大化工独占，催化剂1级=催化转化器）
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.biooil.hydro")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.biooil.hydro",
					BioOilChainSpec.HYDRO_HEAT, BioOilChainSpec.HYDRO_CATALYST)
				.setup(BioOilChainSpec.HYDRO_DURATION, BioOilChainSpec.HYDRO_POWER)
				.inputFluids(new FluidStack(bio, BioOilChainSpec.HYDRO_IN[0]),
							 new FluidStack(Fluids.HYDROGEN, BioOilChainSpec.HYDRO_IN[1]))
				.outputFluids(new FluidStack(synth, BioOilChainSpec.HYDRO_OUT[0]),
							  new FluidStack(Fluids.CARBONDIOXIDE, BioOilChainSpec.HYDRO_OUT[1])));
		}

		// ③ 异构重整（大化工独占，催化剂2级=铂合金转换器）
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.biooil.reform")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.biooil.reform",
					BioOilChainSpec.REFORM_HEAT, BioOilChainSpec.REFORM_CATALYST)
				.setup(BioOilChainSpec.REFORM_DURATION, BioOilChainSpec.REFORM_POWER)
				.inputFluids(new FluidStack(synth, BioOilChainSpec.REFORM_IN[0]),
							 new FluidStack(Fluids.ETHANOL, BioOilChainSpec.REFORM_IN[1]),
							 new FluidStack(Fluids.HYDROGEN, BioOilChainSpec.REFORM_IN[2]),
							 new FluidStack(Fluids.OXYGEN, BioOilChainSpec.REFORM_IN[3]))
				.outputFluids(new FluidStack(Fluids.OIL, BioOilChainSpec.REFORM_OUT[0]),
							  new FluidStack(Fluids.WATER, BioOilChainSpec.REFORM_OUT[1])));
		}

		MakeNTMGreatAgain.LOG.info("Added bio-oil chain: 6x pulp depolymerization + hydrodeoxygenation + reforming -> crude oil");
	}

	/** 单条浆料解聚配方。 */
	private static void bioDepoly(String name, ModFluids.FluidDef pulpDef, FluidType bio) {
		FluidType pulp = addonFluid(pulpDef);
		if(pulp == Fluids.NONE || hasChemRecipe(name)) return;

		RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe(name)
			.setup(BioOilChainSpec.DEPOLY_DURATION, BioOilChainSpec.DEPOLY_POWER)
			.inputFluids(new FluidStack(pulp, BioOilChainSpec.DEPOLY_IN[0]),
						 new FluidStack(Fluids.STEAM, BioOilChainSpec.DEPOLY_IN[1]),
						 new FluidStack(Fluids.HYDROGEN, BioOilChainSpec.DEPOLY_IN[2]))
			.outputFluids(new FluidStack(bio, BioOilChainSpec.DEPOLY_OUT[0]),
						  new FluidStack(Fluids.WATER, BioOilChainSpec.DEPOLY_OUT[1])));
	}

	// =====================================================================================
	//  后期支线 · 自由资源深加工（设计方案见根目录《后期支线-设计方案.md》）

	/** 生胶、水、橡胶锭 */
	public static void addRubberBranchRecipes() {
		if(ModItems.raw_rubber == null) return;

		// 生胶、水
		if(!hasChemRecipe("makentmgreatagain.branch.rubber.coagulate")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.branch.rubber.coagulate")
				.setup(400, 3_000)
				.inputFluids(new FluidStack(Fluids.COLLOID, 2_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.raw_rubber, 2))
				.outputFluids(new FluidStack(Fluids.WATER, 1_000)));
		}

		// 橡胶锭
		if(!hasChemRecipe("makentmgreatagain.branch.rubber.vulcanize")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.branch.rubber.vulcanize")
				.setup(200, 1_500)
				.inputItems(new ComparableStack(ModItems.raw_rubber, 2),
							new ComparableStack(com.hbm.items.ModItems.sulfur, 1))
				.inputFluids(new FluidStack(Fluids.STEAM, 1_000), new FluidStack(Fluids.UNSATURATEDS, 150))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_rubber, 3)));
		}

		MakeNTMGreatAgain.LOG.info("Added branch recipes: colloid -> raw rubber -> rubber");
	}

	/** 石灰石粉、石膏、水 */
	public static void addCalciumBranchRecipes() {
		// 石灰石粉
		if(!hasChemRecipe("makentmgreatagain.branch.calcium.carbonate")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.branch.calcium.carbonate")
				.setup(300, 2_500)
				.inputFluids(new FluidStack(Fluids.CALCIUM_SOLUTION, 1_000), new FluidStack(Fluids.CARBONDIOXIDE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_limestone, 3)));
		}

		// 石膏、水
		if(!hasChemRecipe("makentmgreatagain.branch.calcium.gypsum")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.branch.calcium.gypsum")
				.setup(300, 3_000)
				.inputFluids(new FluidStack(Fluids.CALCIUM_SOLUTION, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
				.outputItems(new ItemStack(ModItems.gypsum, 2))
				.outputFluids(new FluidStack(Fluids.WATER, 500)));
		}

		MakeNTMGreatAgain.LOG.info("Added branch recipes: calcium solution -> limestone / gypsum");
	}

	/** 末影珍珠 */
	public static void addEnderBranchRecipe() {
		CrystallizerRecipes.CrystallizerRecipe recipe =
			new CrystallizerRecipes.CrystallizerRecipe(new ItemStack(Items.ender_pearl, 1), 600);

		CrystallizerRecipes.registerRecipe(new ComparableStack(Blocks.end_stone), recipe,
			new FluidStack(Fluids.ENDERJUICE, 6_000));

		MakeNTMGreatAgain.LOG.info("Added branch recipe: ender juice + end stone -> ender pearl");
	}

	/** 高纯炭黑、水、富勒烯溶液、石墨电极、碳粉盒、橡胶锭、石墨锭 */
	public static void addPolarBranchRecipes() {
		if(ModItems.carbon_black_pure == null) return;

		// 高纯炭黑、水
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.carbon.black")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.carbon.black", 150_000)
				.setup(400, 4_000)
				.inputFluids(new FluidStack(Fluids.GAS, 2_000), new FluidStack(Fluids.OXYGEN, 1_000),
							new FluidStack(Fluids.CRYOGEL, 100))
				.outputItems(new ItemStack(ModItems.carbon_black_pure, 4))
				.outputFluids(new FluidStack(Fluids.WATER, 1_000)));
		}

		// 富勒烯溶液
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.carbon.fullerene")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.carbon.fullerene", 300_000)
				.setup(400, 1_000)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 1))
				.inputFluids(new FluidStack(Fluids.RADIOSOLVENT, 500), new FluidStack(Fluids.CRYOGEL, 250))
				.outputFluids(new FluidStack(Fluids.FULLERENE, 750)));
		}

		// 石墨电极
		if(!hasChemRecipe("makentmgreatagain.carbon.electrode")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.carbon.electrode")
				.setup(600, 200)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 2))
				.inputFluids(new FluidStack(Fluids.HEATINGOIL, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.arc_electrode, 1)));
		}

		// 碳粉盒
		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.carbon.part")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.carbon.part")
				.setup(40, 100)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 1))
				.outputItems(new ItemStack(com.hbm.items.ModItems.part_carbon, 24)));
		}

		// 橡胶锭
		if(!hasChemRecipe("makentmgreatagain.carbon.rubber")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.carbon.rubber")
				.setup(60, 150)
				.inputItems(new ComparableStack(ModItems.raw_rubber, 2),
							new ComparableStack(com.hbm.items.ModItems.sulfur, 1),
							new ComparableStack(ModItems.carbon_black_pure, 1))
				.inputFluids(new FluidStack(Fluids.STEAM, 600), new FluidStack(Fluids.UNSATURATEDS, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_rubber, 6)));
		}

		// 石墨锭
		PressRecipes.makeRecipe(com.hbm.items.machine.ItemStamp.StampType.FLAT,
				new ComparableStack(ModItems.carbon_black_pure, 1),
				new ItemStack(com.hbm.items.ModItems.ingot_graphite, 2));

		MakeNTMGreatAgain.LOG.info("Added polar branch recipes: gas hydrate -> carbon black -> fullerene / electrode / part / rubber");
	}

	/** 合成气、氢气、二氧化碳、石墨粉、铁锭、不饱和烃 */
	public static void addCokerGasDownstreamRecipes() {

		// 合成气
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.cokergas.reform")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.cokergas.reform", 250_000)
				.setup(200, 1_200)
				.inputFluids(new FluidStack(Fluids.GAS_COKER, 2_000), new FluidStack(Fluids.STEAM, 1_000))
				.outputFluids(new FluidStack(Fluids.SYNGAS, 1_600)));
		}

		// 氢气、二氧化碳
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.cokergas.h2")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.cokergas.h2", 150_000)
				.setup(150, 800)
				.inputFluids(new FluidStack(Fluids.GAS_COKER, 1_000), new FluidStack(Fluids.STEAM, 500))
				.outputFluids(new FluidStack(Fluids.HYDROGEN, 800), new FluidStack(Fluids.CARBONDIOXIDE, 500)));
		}

		// 石墨粉
		boolean hasCarbonBlack = false;
		for(PyroOvenRecipes.PyroOvenRecipe recipe : PyroOvenRecipes.recipes) {
			if(recipe.outputItem != null && recipe.outputItem.getItem() == ModItems.powder_graphite) {
				hasCarbonBlack = true;
				break;
			}
		}
		if(!hasCarbonBlack) {
			PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(100);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.GAS_COKER, 5_000));
			recipe.out(new FluidStack(Fluids.HYDROGEN, 2_000))
				.out(new ItemStack(ModItems.powder_graphite, 2));
			PyroOvenRecipes.recipes.add(recipe);
		}

		// 铁锭
		boolean hasDri = false;
		for(RotaryFurnaceRecipes.RotaryFurnaceRecipe recipe : RotaryFurnaceRecipes.recipes) {
			if(recipe.fluid != null && recipe.fluid.type == Fluids.GAS_COKER
					&& recipe.output != null && recipe.output.material == MAT_IRON) {
				hasDri = true;
				break;
			}
		}
		if(!hasDri) {
			RotaryFurnaceRecipes.recipes.add(new RotaryFurnaceRecipes.RotaryFurnaceRecipe(
					new Mats.MaterialStack(MAT_IRON, INGOT.q(3)), 200, 200,
					new FluidStack(Fluids.GAS_COKER, 300),
					new OreDictStack(IRON.fragment(), 9)));
		}

		// 不饱和烃
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.cokergas.olefin")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.cokergas.olefin", 250_000)
				.setup(200, 1_500)
				.inputFluids(new FluidStack(Fluids.GAS_COKER, 3_000))
				.outputFluids(new FluidStack(Fluids.UNSATURATEDS, 300)));
		}

		MakeNTMGreatAgain.LOG.info("Added coker gas downstream: reform / shift-H2 / carbon black / DRI / olefin");
	}

	// =====================================================================================
	//  深海精矿与浆体处理产线（设计 3 · 分馏塔深档）
	// =====================================================================================

	/** 尾氯化气、灰尘、铁粉、盐酸、重砂精矿、四氯化钛、纯钽铁矿粉、铌粉、粗铂液、金粉、氯铂酸钾、稀土母液、钍精矿、混合稀土氢氧化物、盐水、小撮钕粉、小撮镧粉、铒粉、钴料液、铜废料、氢氧化钴、二氧化硫、二氧化碳 */
	public static void addDeepSeaChemicalRecipes() {
		FluidType tailings = addonFluid(ModFluids.TAILINGS_SLURRY);
		FluidType heavy = addonFluid(ModFluids.HEAVY_SAND_SLURRY);
		FluidType chloride = addonFluid(ModFluids.TAILINGS_CHLORIDE);
		FluidType fraction = addonFluid(ModFluids.TAILINGS_HEAVY_FRACTION);
		FluidType crudePt = addonFluid(ModFluids.CRUDE_PLATINUM);
		FluidType coLiquor = addonFluid(ModFluids.COBALT_LIQUOR);
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		// 四氯化钛：新版 HBM 原生有，旧版由 HBMCompat.ensureTitaniumTetrachloride 补注册；
		// 一律经兼容层取用，未就绪时跳过含它的配方（与盐酸同一套兜底思路）
		FluidType ticl4 = com.makentmgreatagain.compat.HBMVersionCompat.titaniumTetrachloride();

		// 尾氯化气、灰尘
		if(tailings != Fluids.NONE && chloride != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.a2")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.a2")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(chloride, 1_200))
				.setup(300, 5_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_coal, 1))
				.inputFluids(new FluidStack(tailings, 1_500), new FluidStack(Fluids.CHLORINE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.dust, 2))
				.outputFluids(new FluidStack(chloride, 1_200)));
		}

		// 四氯化钛、尾氯重馏分
		if(chloride != Fluids.NONE && fraction != Fluids.NONE && ticl4 != Fluids.NONE && !LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.deepsea.a3b")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.deepsea.a3b", 250_000)
				.setup(300, 5_000)
				.inputFluids(new FluidStack(chloride, 1_000), new FluidStack(Fluids.RADIOSOLVENT, 600))
				.outputFluids(new FluidStack(ticl4, 800), new FluidStack(fraction, 200)));
		}

		// 铁粉、盐酸
		if(fraction != Fluids.NONE && hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.a5")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.a5")
				.setup(300, 5_000)
				.inputFluids(new FluidStack(fraction, 1_200), new FluidStack(Fluids.HYDROGEN, 750))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_iron, 1))
				.outputFluids(new FluidStack(hcl, 1_500)));
		}

		// 铁粉、盐酸、二氧化硫
		if(fraction != Fluids.NONE && hcl != Fluids.NONE && so2 != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.a5b")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.a5b")
				.setup(300, 5_000)
				.inputFluids(new FluidStack(fraction, 800), new FluidStack(Fluids.SOLVENT, 800))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_iron, 2))
				.outputFluids(new FluidStack(hcl, 900), new FluidStack(so2, 150)));
		}

		// 重砂精矿、四氯化钛、灰尘
		if(heavy != Fluids.NONE && ticl4 != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.b1")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.b1")
				.setup(300, 5_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_coal, 1))
				.inputFluids(new FluidStack(heavy, 1_000), new FluidStack(Fluids.CHLORINE, 400))
				.outputItems(new ItemStack(ModItems.heavy_sand_concentrate, 1), new ItemStack(com.hbm.items.ModItems.dust, 1))
				.outputFluids(new FluidStack(ticl4, 500)));
		}

		// 重砂精矿、氢气、灰尘
		if(heavy != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.b1b")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.b1b")
				.setup(300, 5_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_coal, 1))
				.inputFluids(new FluidStack(heavy, 1_000), new FluidStack(Fluids.SOLVENT, 800))
				.outputItems(new ItemStack(ModItems.heavy_sand_concentrate, 2), new ItemStack(com.hbm.items.ModItems.dust, 1))
				.outputFluids(new FluidStack(Fluids.HYDROGEN, 150)));
		}

		// 纯钽铁矿粉、铌粉、灰尘、水
		if(!hasChemRecipe("makentmgreatagain.deepsea.c4")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.c4")
				.setup(60, 500)
				.inputItems(new ComparableStack(ModItems.heavy_sand_mix, 2), new ComparableStack(com.hbm.items.ModItems.powder_coal, 1))
				.inputFluids(new FluidStack(Fluids.PEROXIDE, 250), new FluidStack(Fluids.HYDROGEN, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_coltan, 1),
							 new ItemStack(com.hbm.items.ModItems.powder_niobium, 1),
							 new ItemStack(com.hbm.items.ModItems.dust, 1))
				.outputFluids(new FluidStack(Fluids.WATER, 500)));
		}

		// 纯钽铁矿粉、铌粉、二氧化硫
		if(so2 != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.c4b")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.c4b")
				.setup(300, 500)
				.inputItems(new ComparableStack(ModItems.heavy_sand_mix, 2))
				.inputFluids(new FluidStack(Fluids.RADIOSOLVENT, 800), new FluidStack(Fluids.SULFURIC_ACID, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_coltan, 2),
							 new ItemStack(com.hbm.items.ModItems.powder_niobium, 2))
				.outputFluids(new FluidStack(so2, 200)));
		}

		// 粗铂液、金粉
		if(crudePt != Fluids.NONE && hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.c6")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.c6")
				.setup(240, 3_000)
				.inputItems(new ComparableStack(ModItems.pgm_concentrate, 1))
				.inputFluids(new FluidStack(hcl, 400), new FluidStack(Fluids.NITRIC_ACID, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 1))
				.outputFluids(new FluidStack(crudePt, 500)));
		}

		// 粗铂液、金粉、二氧化碳
		if(crudePt != Fluids.NONE && hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.c6c")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.c6c")
				.setup(240, 3_000)
				.inputItems(new ComparableStack(ModItems.pgm_concentrate, 1))
				.inputFluids(new FluidStack(Fluids.SOLVENT, 800), new FluidStack(hcl, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 2))
				.outputFluids(new FluidStack(crudePt, 700), new FluidStack(Fluids.CARBONDIOXIDE, 150)));
		}

		// 氯铂酸钾、盐酸
		if(crudePt != Fluids.NONE && hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.c7")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.c7")
				.setup(240, 3_000)
				.inputFluids(new FluidStack(crudePt, 500), new FluidStack(Fluids.POTASSIUM_CHLORIDE, 300))
				.outputItems(new ItemStack(ModItems.potassium_chloroplatinate, 1))
				.outputFluids(new FluidStack(hcl, 350)));
		}

		// D5/D7/D8/D9、C6b 已移到 PUREX（钚铀还原提取设备），见 addDeepSeaPUREXRecipes()

		// 钴料液、灰尘
		if(coLiquor != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.e3")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.e3")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(coLiquor, 800))
				.setup(300, 5_000)
				.inputItems(new ComparableStack(ModItems.polymetallic_mix, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 600))
				.outputItems(new ItemStack(com.hbm.items.ModItems.dust, 2))
				.outputFluids(new FluidStack(coLiquor, 800)));
		}

		// 钴料液、铜废料、氢气
		if(coLiquor != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.e3b")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.e3b")
				.setNamed()
				.setIcon(com.hbm.items.machine.ItemFluidIcon.make(coLiquor, 1_200))
				.setup(300, 5_000)
				.inputItems(new ComparableStack(ModItems.cobalt_polymetallic_concentrate, 1))
				.inputFluids(new FluidStack(Fluids.SOLVENT, 800), new FluidStack(Fluids.SULFURIC_ACID, 600))
				.outputItems(new ItemStack(com.hbm.items.ModItems.scraps, 1, Mats.MAT_COPPER.id))
				.outputFluids(new FluidStack(coLiquor, 1_200), new FluidStack(Fluids.HYDROGEN, 150)));
		}

		// 氢氧化钴、盐水
		if(coLiquor != Fluids.NONE && brine != Fluids.NONE && !hasChemRecipe("makentmgreatagain.deepsea.e4")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.deepsea.e4")
				.setup(300, 5_000)
				.inputFluids(new FluidStack(coLiquor, 800), new FluidStack(Fluids.LYE, 400))
				.outputItems(new ItemStack(ModItems.cobalt_hydroxide, 1))
				.outputFluids(new FluidStack(brine, 500)));
		}

		MakeNTMGreatAgain.LOG.info("Added deep-sea chemical plant recipes: A2/A5/B1/C4/C6/C7/D5/D7/D8/D9/E3/E4 + optional branches");
	}

	/** 水、氢气、重砂精矿、阳极泥 */
	public static void addDeepSeaFluidElectrolyserRecipes() {
		FluidType tailings = addonFluid(ModFluids.TAILINGS_SLURRY);
		FluidType heavy = addonFluid(ModFluids.HEAVY_SAND_SLURRY);

		// 水、氢气、阳极泥
		if(tailings != Fluids.NONE && ElectrolyserFluidRecipes.getRecipe(tailings) == null) {
			ElectrolyserFluidRecipes.recipes.put(tailings,
				new ElectrolyserFluidRecipes.ElectrolysisRecipe(3_000,
					new FluidStack(Fluids.WATER, 2_400),
					new FluidStack(Fluids.HYDROGEN, 100),
					450,
					new ItemStack(ModItems.anode_mud, 1)));
		}

		// 水、重砂精矿、阳极泥
		if(heavy != Fluids.NONE && ElectrolyserFluidRecipes.getRecipe(heavy) == null) {
			ElectrolyserFluidRecipes.recipes.put(heavy,
				new ElectrolyserFluidRecipes.ElectrolysisRecipe(1_000,
					new FluidStack(Fluids.WATER, 700),
					new FluidStack(Fluids.NONE, 0),
					300,
					new ItemStack(ModItems.heavy_sand_concentrate, 1),
					new ItemStack(ModItems.anode_mud, 1)));
		}

		MakeNTMGreatAgain.LOG.info("Added deep-sea fluid electrolyser recipes: tailings slurry / heavy sand slurry");
	}

	/** 碱熔砂、蒸汽、稀土熟料、铂粉、氯气 */
	public static void addDeepSeaPyroRecipes() {
		// 碱熔砂、蒸汽（in() 走兼容层：旧版 HBM 的 in() 返回 void，不能链式）
		if(!hasPyroRecipe(ModItems.heavy_sand_concentrate)) {
			PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(200);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.LYE, 300));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.heavy_sand_concentrate));
			recipe.out(new FluidStack(Fluids.STEAM, 200))
				.out(new ItemStack(ModItems.alkali_fused_sand, 1));
			PyroOvenRecipes.recipes.add(recipe);
		}

		// 稀土熟料、蒸汽
		if(!hasPyroRecipe(ModItems.rare_earth_concentrate)) {
			PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(200);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.SULFURIC_ACID, 400));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.rare_earth_concentrate));
			recipe.out(new FluidStack(Fluids.STEAM, 300))
				.out(new ItemStack(ModItems.rare_earth_calcine, 1));
			PyroOvenRecipes.recipes.add(recipe);
		}

		// 铂粉、氯气
		if(!hasPyroRecipe(ModItems.potassium_chloroplatinate)) {
			PyroOvenRecipes.PyroOvenRecipe recipe = new PyroOvenRecipes.PyroOvenRecipe(200);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new FluidStack(Fluids.HYDROGEN, 300));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(recipe, new ComparableStack(ModItems.potassium_chloroplatinate));
			recipe.out(new FluidStack(Fluids.CHLORINE, 200))
				.out(new ItemStack(ModItems.powder_platinum, 1));
			PyroOvenRecipes.recipes.add(recipe);
		}

		MakeNTMGreatAgain.LOG.info("Added deep-sea pyro oven recipes: alkali fusion / sulfuric roast / platinum reduction");
	}

	/** 重砂混粉、稀土混粉、多金属混粉 */
	public static void addDeepSeaShredderRecipes() {
		// 重砂混粉
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.alkali_fused_sand), new ItemStack(ModItems.heavy_sand_mix, 2));
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.heavy_sand_concentrate), new ItemStack(ModItems.heavy_sand_mix, 1));

		// 稀土混粉
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.rare_earth_calcine), new ItemStack(ModItems.rare_earth_mix, 2));
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.rare_earth_concentrate), new ItemStack(ModItems.rare_earth_mix, 1));

		// 多金属混粉
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.cobalt_polymetallic_concentrate), new ItemStack(ModItems.polymetallic_mix, 2));

		ShredderRecipes.neiShredderRecipes = null;

		MakeNTMGreatAgain.LOG.info("Added deep-sea shredder recipes: fused sand / calcine / cobalt concentrate");
	}

	/** 锆粉、铌粉、铂族精矿、铀粉、小撮钕粉、小撮镧粉、铒富集粉、灰尘、钴粉、铜粉 */
	public static void addDeepSeaCentrifugeRecipes() {
		// 锆粉、钽粉、铂族精矿、铀粉
		CentrifugeRecipes.recipes.put(new ComparableStack(ModItems.heavy_sand_mix), new ItemStack[] {
			new ItemStack(com.hbm.items.ModItems.powder_zirconium, 1),
			new ItemStack(com.hbm.items.ModItems.powder_tantalium, 1),
			new ItemStack(ModItems.pgm_concentrate, 1),
			new ItemStack(com.hbm.items.ModItems.powder_uranium, 1) });

		// 小撮钕粉、小撮镧粉、铒富集粉、灰尘
		CentrifugeRecipes.recipes.put(new ComparableStack(ModItems.rare_earth_mix), new ItemStack[] {
			new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 2),
			new ItemStack(com.hbm.items.ModItems.powder_lanthanium_tiny, 1),
			new ItemStack(ModItems.erbium_enriched_powder, 1),
			new ItemStack(com.hbm.items.ModItems.dust, 1) });

		// 钴粉、铜粉、灰尘
		CentrifugeRecipes.recipes.put(new ComparableStack(ModItems.polymetallic_mix, 2), new ItemStack[] {
			new ItemStack(com.hbm.items.ModItems.powder_cobalt, 2),
			new ItemStack(com.hbm.items.ModItems.powder_copper, 1),
			new ItemStack(com.hbm.items.ModItems.dust, 2) });

		MakeNTMGreatAgain.LOG.info("Added deep-sea centrifuge recipes: heavy sand / rare earth / polymetallic mix");
	}

	/** 钍晶体、钴晶体 */
	public static void addDeepSeaCrystallizerRecipes() {
		// 钍晶体
		CompatRecipeRegistry.registerCrystallizer(new ComparableStack(ModItems.thorium_concentrate),
			new ItemStack(com.hbm.items.ModItems.crystal_thorium, 1), 300, 0F,
			new FluidStack(Fluids.SULFURIC_ACID, 500));

		// 钴晶体
		CompatRecipeRegistry.registerCrystallizer(new ComparableStack(ModItems.cobalt_hydroxide),
			new ItemStack(com.hbm.items.ModItems.crystal_cobalt, 1), 300, 0F,
			new FluidStack(Fluids.SULFURIC_ACID, 500));

		MakeNTMGreatAgain.LOG.info("Added deep-sea crystallizer recipes: thorium / cobalt crystal");
	}

	/** 钍精矿、混合稀土氢氧化物、盐水、小撮钕粉、灰尘、铒粉、粗铂液、金粉、氢氧化钴 */
	public static void addDeepSeaPUREXRecipes() {
		FluidType tbpkero = addonFluid(ModFluids.TBP_KERO);
		FluidType coLiquor = addonFluid(ModFluids.COBALT_LIQUOR);
		FluidType crudePt = addonFluid(ModFluids.CRUDE_PLATINUM);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		if(tbpkero == Fluids.NONE) return;

		// 钍精矿、灰尘（稀土母液已删除，不再产出）
		if(!hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.d5")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.d5")
				.setup(300, 6_000)
				.inputItems(new ComparableStack(ModItems.rare_earth_mix, 4))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_000))
				.outputItems(new ItemStack(ModItems.thorium_concentrate, 1),
							 new ItemStack(com.hbm.items.ModItems.dust, 2)));
		}

		// 钍精矿（TBP-煤油萃取，稀土母液已删除，不再产出）
		if(!hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.d5b")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.d5b")
				.setup(300, 3_000)
				.inputItems(new ComparableStack(ModItems.rare_earth_mix, 4))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_000),
							 new FluidStack(tbpkero, 1_000))
				.outputItems(new ItemStack(ModItems.thorium_concentrate, 2)));
		}

		// 混合稀土氢氧化物、盐水（稀土改直接沉淀：稀土混粉+碱液，不再经母液）
		if(brine != Fluids.NONE && !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.d7")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.d7")
				.setup(300, 6_000)
				.inputItems(new ComparableStack(ModItems.rare_earth_mix, 4))
				.inputFluids(new FluidStack(Fluids.LYE, 500))
				.outputItems(new ItemStack(ModItems.mixed_re_hydroxide, 2))
				.outputFluids(new FluidStack(brine, 800)));
		}

		// 小撮钕粉、灰尘
		if(hcl != Fluids.NONE && !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.d8")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.d8")
				.setup(300, 6_000)
				.inputItems(new ComparableStack(ModItems.mixed_re_hydroxide, 2))
				.inputFluids(new FluidStack(hcl, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 1),
							 new ItemStack(com.hbm.items.ModItems.dust, 2)));
		}

		// 铒粉、小撮钕粉、灰尘
		if(!hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.d9")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.d9")
				.setup(300, 6_000)
				.inputItems(new ComparableStack(ModItems.erbium_enriched_powder, 2))
				.inputFluids(new FluidStack(Fluids.RADIOSOLVENT, 2400))
				.outputItems(new ItemStack(ModItems.powder_erbium, 1),
							 new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 1),
							 new ItemStack(com.hbm.items.ModItems.dust, 1)));
		}

		// 粗铂液、金粉
		if(crudePt != Fluids.NONE && hcl != Fluids.NONE
				&& !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.c6b")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.c6b")
				.setup(240, 3_000)
				.inputItems(new ComparableStack(ModItems.anode_mud, 4))
				.inputFluids(new FluidStack(hcl, 400), new FluidStack(Fluids.NITRIC_ACID, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 1))
				.outputFluids(new FluidStack(crudePt, 500)));
		}

		// 氢氧化钴、盐水
		if(coLiquor != Fluids.NONE && brine != Fluids.NONE
				&& !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.deepsea.e4b")) {
			RecipeRegistrar.add(PUREXRecipes.INSTANCE, (PUREXRecipe) new PUREXRecipe("makentmgreatagain.deepsea.e4b")
				.setup(300, 5_000)
				.inputFluids(new FluidStack(coLiquor, 1_200), new FluidStack(Fluids.LYE, 400))
				.outputItems(new ItemStack(ModItems.cobalt_hydroxide, 2))
				.outputFluids(new FluidStack(brine, 600)));
		}

		MakeNTMGreatAgain.LOG.info("Added deep-sea PUREX recipes: D5/D5b/D7/D8/D9/C6b/E4b");
	}

	/** 四氯化钛、尾氯重馏分 */
	public static void addDeepSeaFractionRecipe() {
		FluidType chloride = addonFluid(ModFluids.TAILINGS_CHLORIDE);
		FluidType fraction = addonFluid(ModFluids.TAILINGS_HEAVY_FRACTION);
		FluidType ticl4 = com.makentmgreatagain.compat.HBMVersionCompat.titaniumTetrachloride();
		if(chloride == Fluids.NONE || fraction == Fluids.NONE || ticl4 == Fluids.NONE) return;
		if(FractionRecipes.fractions.containsKey(chloride)) return;

		FractionRecipes.fractions.put(chloride,
			new Pair(new FluidStack(ticl4, 55), new FluidStack(fraction, 45)));

		MakeNTMGreatAgain.LOG.info("Added fraction tower recipe: tailings chloride -> 55 TiCl4 + 45 heavy fraction");
	}

	/** 铂熔体、金粉、锆熔体、阳极泥、钴熔体、铜熔体 */
	public static void addDeepSeaMetalElectrolyserRecipe() {
		if(MNGAMetals.MAT_PLATINUM == null) return;

		ComparableStack in = new ComparableStack(ModItems.anode_mud);
		if(!ElectrolyserMetalRecipes.recipes.containsKey(in)) {
			ElectrolyserMetalRecipes.recipes.put(in,
				new ElectrolyserMetalRecipes.ElectrolysisMetalRecipe(
					new MaterialStack(MNGAMetals.MAT_PLATINUM, INGOT.q(1)),
					null,
					300,
					new ItemStack(com.hbm.items.ModItems.powder_gold, 1)));
		}

		// 锆熔体、阳极泥
		ComparableStack zr = new ComparableStack(ModItems.heavy_sand_mix);
		if(!ElectrolyserMetalRecipes.recipes.containsKey(zr)) {
			ElectrolyserMetalRecipes.recipes.put(zr,
				new ElectrolyserMetalRecipes.ElectrolysisMetalRecipe(
					new MaterialStack(Mats.MAT_ZIRCONIUM, INGOT.q(3)),
					null,
					300,
					new ItemStack(ModItems.anode_mud, 1)));
		}

		// 钴熔体、铜熔体、阳极泥
		ComparableStack co = new ComparableStack(ModItems.polymetallic_mix);
		if(!ElectrolyserMetalRecipes.recipes.containsKey(co)) {
			ElectrolyserMetalRecipes.recipes.put(co,
				new ElectrolyserMetalRecipes.ElectrolysisMetalRecipe(
					new MaterialStack(Mats.MAT_COBALT, INGOT.q(2)),
					new MaterialStack(Mats.MAT_COPPER, INGOT.q(1)),
					300,
					new ItemStack(ModItems.anode_mud, 1)));
		}

		MakeNTMGreatAgain.LOG.info("Added metal electrolyser recipes: anode mud / heavy sand mix / polymetallic mix");
	}

	// ==================== postInit 兜底注入 ====================

	/**
	 * 兜底注入：HBM 的 onRecipeLoad 只在「配置文件不存在」那条分支触发，
	 * config/hbmRecipes/ 一旦有玩家版 json，监听器就不跑，注入的配方会静默消失，
	 * 所以 postInit 再把所有 addXxx 跑一遍。
	 *
	 * 每个方法必须幂等：getRecipe / containsKey 判重，register 重名会抛异常。
	 * 新增配方时在这里加一行 step("名字", ModRecipes::addXxx) 即可。
	 */
	public static void injectAllFallback() {
		// 逐条兜底：单条失败不影响后续
		// 三材质的砧 / 锻压机 / 电弧焊机 / 粉碎机 / 螺栓 / 储存块
		// ===== [坩埚/铸造] 下面这段里「没有」坩埚熔炼配方与铸造浇模配方 =====
		// 坩埚熔炼（物品 → 熔体）与铸造浇模（熔体 → 物品）全是 HBM 现场查矿物词典得出的，
		// 不存配方表，所以这里只挂「非铸造类」配方。铸造侧要改，去 init/MNGAMetals.java：
		//   registerOreDict()【矿辞】 → registerMaterials()【材质】 → refreshCastingTable()【铸造表】
		// 例外有两个，它们确实是配方表，所以挂在这里（见 MNGAMetals §④）：
		//   metalSolidify / metalLiquefy —— 固化机与液化机的「氦-3 ↔ 氦气凝锭」；
		//   heliumAlloy —— 坩埚「合金」配方（按熔体量匹配，不是按物品）。
		// =====================================================================
		step("metalSmithing",        MNGAMetals::addSmithingRecipes);
		step("metalPress",           MNGAMetals::addPressRecipes);
		step("metalArcWelder",       MNGAMetals::addArcWelderRecipes);
		step("metalShredder",        MNGAMetals::addShredderRecipes);
		step("metalBolts",           MNGAMetals::addBoltRecipes);
		step("metalBlocks",          MNGAMetals::addBlockRecipes);
		step("metalSolidify",        MNGAMetals::addSolidificationRecipe);
		step("metalLiquefy",         MNGAMetals::addLiquefactionRecipe);
		step("heliumAlloy",          MNGAMetals::addHeliumAlloyRecipe);
		step("advancedAlloy",        ModRecipes::addAdvancedAlloyRecipe);
		step("tungstenCarbideArc",   ModRecipes::addTungstenCarbideArcRecipe);
		step("cobaltTungstenArc",    ModRecipes::addTungstencobaltArcRecipe);
		step("hclElectrolyser",      ModRecipes::addHCLElectrolyserRecipe);
		step("saltWaterElectrolyser",ModRecipes::addSaltWaterElectrolyserRecipe);
		step("tungstenCarbide",      ModRecipes::addTungstenCarbideRecipe);
		step("cobaltCarbide",        ModRecipes::addCobaltCarbideRecipe);
		step("saltWater",            ModRecipes::addSaltWaterRecipe);
		step("hclChemicalPlant",     ModRecipes::addHCLChemicalPlantRecipe);
		step("saltWaterMixer",       ModRecipes::addSaltWaterMixerRecipe);
		step("hclMixer",             ModRecipes::addHCLMixerRecipe);
		step("graphiteShredder",     ModRecipes::addGraphiteShredderRecipe);
		step("saltSandShredder",     ModRecipes::addSaltSandShredderRecipe);
		step("saltRedSandShredder",  ModRecipes::addSaltRedSandShredderRecipe);
		step("saltGravelShredder",   ModRecipes::addSaltGravelShredderRecipe);
		step("saltFromSand",         ModRecipes::addSaltFromSandRecipe);
		step("saltFromRedSand",      ModRecipes::addSaltFromRedSandRecipe);
		step("saltFromGravel",       ModRecipes::addSaltFromGravelRecipe);
		step("ironFromRedSand",      ModRecipes::addIronFromRedSandRecipe);
		step("shredderAssembly",     ModRecipes::addShredderAssemblyRecipe);
		step("bat9000Assembly",      ModRecipes::addBat9000AssemblyRecipe);
		step("heatexAssembly",       ModRecipes::addHeatexRebornAssemblyRecipe);
		step("electrolyzerAssembly", ModRecipes::addElectrolyzerRebornAssemblyRecipe);
		step("hybridFractionTowerAssembly", ModRecipes::addHybridFractionTowerAssemblyRecipe);
		step("arcHeaterAssembly",   ModRecipes::addArcHeaterAssemblyRecipe);
		step("environmentPumpAssembly", ModRecipes::addEnvironmentPumpAssemblyRecipe);
		step("seaRiseRigAssembly",  ModRecipes::addSeaRiseRigRecipe);
		step("largeCatChemFactoryAssembly", ModRecipes::addLargeCatalyticChemicalFactoryRecipe);
		step("nuclearExplosionGeneratorAssembly", ModRecipes::addNuclearExplosionGeneratorRecipe);
		step("addFluidCoolerRecipe", ModRecipes::addFluidCoolerRecipe);
		step("largeCentrifugeAssembly", ModRecipes::addLargeCentrifugeAssemblyRecipe);
		// 核装药核心（gadget_core 同型球）：装配机配方，同样必须在 postInit 兜底注册
		step("nukeCores", ModNukeCores::addRecipes);
		// 钍232→钍燃料棒除气机配方删除（用户要求），兜底通路同样执行
		step("removeThoriumFuelOutgasser", ModRecipes::removeThoriumFuelOutgasserRecipes);
		// ===== 监听器已有、兜底缺失的补齐（玩家已有 JSON 时监听不触发，全靠这里）=====
		step("bioOilChain",          ModRecipes::addBioOilChainRecipes);
		step("cokeSyngas",           ModRecipes::addCokeSyngasRecipes);
		step("scheeliteChemical",    com.makentmgreatagain.recipes.ScheeliteRecipes::addChemicalPlantRecipes);
		step("scheeliteShredder",    com.makentmgreatagain.recipes.ScheeliteRecipes::addShredderRecipes);
		step("gasTurbineAssembly",   ModRecipes::addGasTurbineAssemblyRecipe);
		step("thermalStationAssembly", ModRecipes::addThermalStationAssemblyRecipe);
		step("industrialCultivationStationAssembly", ModRecipes::addIndustrialCultivationStationAssemblyRecipe);
		step("supercriticalMHDAssembly", ModRecipes::addSupercriticalMHDAssemblyRecipe);
		step("waterToSnowball",      ModRecipes::addWaterToSnowballRecipe);
		step("sreaWaterToSalt",      ModRecipes::addSreaWaterToSaltRecipe);
		step("coreLava528Annihilator", ModRecipes::addCoreLava528Annihilator);
		step("stellarator528Annihilator", ModRecipes::addStellarator528Annihilator);
		// 氢-氢聚变（等离子加热器 + 仿星器）：HBM 的聚变表同样会按 json 重建，走兜底
		step("hydrogenFusion", com.makentmgreatagain.recipes.HydrogenFusion::addRecipe);
		// 五级机器覆层：装配机配方，同样必须在 postInit 兜底注册
		step("coatings", ModCoatings::addRecipes);
		// 核心的危险数据要在 HBM 的 HazardRegistry/OreDictManager 跑完之后才能求和，
		// 放在这里而不是 ModItems.init()：那边可能早于 HBM 的 preInit。
		step("nukeCoreHazards", ModNukeCores::applyHazards);
		// 电池/电容元件的装配机配方（必须在 postInit 兜底注册，init 阶段会被 HBM 重建冲掉）
		step("batteryCellAssembly", ModRecipes::addBatteryCellAssemblyRecipes);
		// 地核线的原版四表（固化/流体电解/金属电解/混合），必须在 postInit 注册
		step("coreLineVanilla", ModRecipes::addCoreLineVanillaRecipes);
		// 地核开采机本体（装配机）与粉碎的魔法（酸化机），同属原版表，走兜底
		step("coreLavaExtractorAssembly", ModRecipes::addCoreLavaExtractorRecipe);
		step("stellaratorAssembly",       ModRecipes::addStellaratorRecipe);
		step("plasmaHeaterAssembly",      ModRecipes::addPlasmaHeaterRecipe);
		step("threxStructuralAssembly",   ModRecipes::addThrexStructuralAssemblyRecipes);
		step("brimstoneChemical",         ModRecipes::addBrimstoneChemicalRecipes);
		step("electricBlastFurnaceAssembly", ModRecipes::addElectricBlastFurnaceRecipe);
		step("parallelUpgradeAssembly",   ModRecipes::addParallelUpgradeAssemblyRecipes);
		step("parallelUpgradePrecAss",    ModRecipes::addParallelUpgradePrecAssRecipes);
		step("quantumPulseAssembly", ModRecipes::addQuantumPulseAssemblyRecipe);
		step("quantumPulsePrecAss", ModRecipes::addQuantumPulsePrecAssRecipe);
		step("magicPowder", ModRecipes::addMagicPowderRecipe);
		step("xenCrystalAcid", ModRecipes::addXenCrystalAcidRecipe);
		step("xenCrystalSILEX", ModRecipes::addXenCrystalSILEXRecipe);
		// 乙酸 / 有机溶剂 / 环氧丙烷 / 野火（监听器 MakeNTMGreatAgain 里同名分支的兜底对）
		step("baltFire",            ModRecipes::addBaltFireRecipe);
		step("aceticAcid",          ModRecipes::addAceticAcidRecipe);
		step("syntheticEthanol",    ModRecipes::addSyntheticEthanolRecipes);
		step("syntheticEthanolMixer", ModRecipes::addSyntheticEthanolMixerRecipes);
		step("chemicalPlantMixer", ModRecipes::addChemicalPlantMixerRecipes);
		step("positronAccelerator", ModRecipes::addPositronAcceleratorRecipe);
		step("ethylAcetate",        ModRecipes::addEthylAcetateRecipe);
		step("propyleneOxide",      ModRecipes::addPropyleneOxideRecipes);
		// SO2 组合 / 锌电解（监听器分支的兜底对）
		step("sulfurCombination",   ModRecipes::addSulfurCombinationRecipe);
		step("zincElectrolyser",    ModRecipes::addZincElectrolyserRecipe);
		step("hotBrineRefinery",     HBMCompat::addHotBrineRefineryRecipe);
		// 海水处理路线
		step("seawaterConcentration", ModRecipes::addSeawaterConcentrationRecipes);
		step("saltSeaWaterElectrolyser", ModRecipes::addSaltSeaWaterElectrolyserRecipe);
		step("seawaterSalt",         ModRecipes::addSeawaterSaltRecipes);
		step("seawaterGypsum",       ModRecipes::addSeawaterGypsumRecipes);
		step("gypsumBlast",          ModRecipes::addGypsumBlastRecipe);
		step("seawaterAdsorption",   ModRecipes::addSeawaterAdsorptionRecipes);
		step("seawaterPUREX",        ModRecipes::addSeawaterPUREXRecipe);
		// 锶线 / 稀土钕 / 铋副产
		step("strontiumChemical",    ModRecipes::addStrontiumChemicalRecipe);
		step("strontiumBlast",       ModRecipes::addStrontiumBlastRecipe);
		step("rareEarthNeodymium",   ModRecipes::addRareEarthNeodymiumRecipe);
		step("bismuthRecovery",      ModRecipes::addBismuthRecoveryRecipes);
		// 石膏 · 原版联动
		step("gypsumBoneMeal",       ModRecipes::addGypsumBoneMealRecipe);
		step("gypsumBrickBlast",     ModRecipes::addGypsumBrickBlastRecipe);
		// 木浆路线：木油 → 木质浆料 → 六种木浆 → 结晶还原原木
		step("woodFrame",            ModRecipes::addWoodFrameRecipe);
		step("woodMashMixer",        ModRecipes::addWoodMashMixerRecipe);
		step("woodMashChemical",     ModRecipes::addWoodMashChemicalRecipe);
		step("woodPulp",             ModRecipes::addWoodPulpRecipes);
		step("woodPulpCrystallizer", ModRecipes::addWoodPulpCrystallizerRecipes);
		// 后期支线：胶体→橡胶 / 钙溶液→建材 / 末影果汁→末影珍珠
		step("branchRubber",         ModRecipes::addRubberBranchRecipes);
		step("branchCalcium",        ModRecipes::addCalciumBranchRecipes);
		step("branchEnder",          ModRecipes::addEnderBranchRecipe);
		step("polarCarbonBlack",     ModRecipes::addPolarBranchRecipes);
		step("gasHelium",            ModRecipes::addGasHeliumRecipe);
		step("petrochem",            ModRecipes::addPetrochemRecipes);
		step("anilineHydro",         ModRecipes::addAnilineHydrotreatingRecipe);
		step("btxUpstream",          ModRecipes::addBTXUpstreamRecipes);
		step("heavyOilDownstream",   ModRecipes::addHeavyOilDownstreamRecipes);
		step("petrochemDownstream",  ModRecipes::addPetrochemDownstreamRecipes);
		// 铅锌线（ISP 法提镉）
		step("leadzincChemical",     ModRecipes::addLeadZincChemicalRecipes);
		step("leadzincBlast",        ModRecipes::addLeadZincBlastRecipe);
		// SO2 上游（复式炼焦炉 / 热解炉烧硫磺）+ 硫酸锌液进电解机
		step("leadzincAux",          ModRecipes::addSulfurDioxideUpstreamRecipes);
		// 砷化氢热分解 -> 砷粒
		step("arsineCracking",       ModRecipes::addArsineCrackingRecipe);
		// 热解炉：硫磺烧 SO2、白钨矿三件套（钨酸焙烧 / WO3 还原 / 砷渣焙烧）
		step("sulfurPyro",           ModRecipes::addSulfurPyroRecipe);
		step("scheelitePyro",        com.makentmgreatagain.recipes.ScheeliteRecipes::addPyroOvenRecipes);
		// 氯碱线：亚硫酸钠脱氯 → 盐酸 + 硫酸钠
		step("chloralkaliDechlor",   ModRecipes::addChlorAlkaliDechlorRecipe);
		// 无氰浸金：硫代硫酸盐合成/浸出/置换/强效脱氯，外加含砷金矿的熔炉
		step("thiosulfateGold",      ModRecipes::addThiosulfateGoldRecipes);
		step("refractoryGoldSmelt",  ModRecipes::addRefractoryGoldSmelting);
		//step("fusionHelium3",        ModRecipes::addFusionHelium3Recipe);
		// 焦化气下游
		step("cokerGasDownstream",   ModRecipes::addCokerGasDownstreamRecipes);
		// 深海精矿与浆体处理产线（设计 3 · 分馏塔深档）
		step("deepSeaChemical",      ModRecipes::addDeepSeaChemicalRecipes);
		step("deepSeaFluidElectrolyser", ModRecipes::addDeepSeaFluidElectrolyserRecipes);
		step("deepSeaPyro",          ModRecipes::addDeepSeaPyroRecipes);
		step("deepSeaShredder",      ModRecipes::addDeepSeaShredderRecipes);
		step("deepSeaCentrifuge",    ModRecipes::addDeepSeaCentrifugeRecipes);
		step("deepSeaCrystallizer",  ModRecipes::addDeepSeaCrystallizerRecipes);
		step("deepSeaPUREX",         ModRecipes::addDeepSeaPUREXRecipes);
		step("deepSeaFraction",      ModRecipes::addDeepSeaFractionRecipe);
		step("deepSeaMetalElectrolyser", ModRecipes::addDeepSeaMetalElectrolyserRecipe);
		// PEEK 特种工程塑料线
		step("peekChemical",         ModRecipes::addPeekChemicalRecipes);
		step("peekShredder",         ModRecipes::addPeekShredderRecipes);
		// 铂细线替代：金/铅细线 → 铂细线、产出翻倍（原配方保留）+ 电弧焊机马达
		// 详情见 init/ModPlatinumRecipes。这里走「派生」而不是抄数值，所以 HBM 改配方它自动跟随。
		step("platinumSwap",         ModPlatinumRecipes::inject);
		// 石油耦合孔雀石 / 赤铁矿双阶高产精炼线
		step("petroOrePyro",         ModRecipes::addPetroOrePyroRecipes);
		step("petroOreChemical",     ModRecipes::addPetroOreChemicalRecipes);
		step("petroOreCentrifuge",   ModRecipes::addPetroOreCentrifugeRecipes);

		MakeNTMGreatAgain.LOG.info("postInit 兜底注入完成（若 config/hbmRecipes 存在玩家版配置，本次注入即为生效来源）");
	}

	/** 单条兜底注入：已注册就当作成功，抛异常只记 warn，不影响后续条目。 */
	private static void step(String name, Runnable action) {
		try {
			action.run();
		} catch (Throwable ex) {
			// 带堆栈：只有 toString 时，撞名以外的错误（NPE、字段缺失）没法定位
			MakeNTMGreatAgain.LOG.warn("兜底注入跳过 " + name + "：" + ex, ex);
		}
	}

	/** recipeNameMap 是 public：按内部名判重（含 json 读回的）。 */
	private static boolean hasGenericRecipe(com.hbm.inventory.recipes.loader.GenericRecipes<?> holder, String name) {
		return holder.recipeNameMap.containsKey(name);
	}

	/**
	 * 同名条目已存在但产物不是预期物品时，先摘掉再重注册。
	 * HBM 的 register 同时写 recipeNameMap 与 recipeOrderedList，两处都得删，
	 * 只删 map 会让条目留在列表里继续被 NEI / 机器读到。
	 */
	private static void dropWrongOutput(com.hbm.inventory.recipes.loader.GenericRecipes<?> holder, String name, Item expected) {
		Object old = holder.recipeNameMap.get(name);
		if(!(old instanceof GenericRecipe)) return;

		com.hbm.inventory.recipes.loader.GenericRecipes.IOutput[] out = ((GenericRecipe) old).outputItem;
		if(out != null && out.length > 0 && out[0] != null) {
			ItemStack first = out[0].getSingle();
			if(first != null && first.getItem() == expected) return;
		}

		holder.recipeNameMap.remove(name);
		holder.recipeOrderedList.remove(old);
	}

	/** 宿主配方表里是否已有产出该流体的配方（装了航天版时避免两套数值）。 */
	private static boolean hostProducesFluid(com.hbm.inventory.recipes.loader.GenericRecipes<?> holder, FluidType fluid) {
		if(fluid == null || fluid == Fluids.NONE) return false;

		java.util.Set<Object> own = new java.util.HashSet<Object>();
		for(java.util.Map.Entry<String, ?> e : holder.recipeNameMap.entrySet()) {
			if(e.getKey() != null && e.getKey().startsWith("makentmgreatagain.")) own.add(e.getValue());
		}

		for(Object o : holder.recipeOrderedList) {
			if(!(o instanceof GenericRecipe)) continue;
			if(own.contains(o)) continue;
			FluidStack[] out = ((GenericRecipe) o).outputFluid;
			if(out == null) continue;
			for(FluidStack fs : out) {
				if(fs != null && fs.type == fluid) return true;
			}
		}
		return false;
	}

	/**
	 * PEEK（聚醚醚酮）产线：
	 * <ul>
	 *   <li>化工厂：氟石粉 ×2 + 硫酸 1500 → 氢氟酸 1500 + 石膏 ×2</li>
	 *   <li>化工厂：苯胺 250 + 氢氟酸 500 + 硝酸 750 → 氟苯 1500</li>
	 *   <li>大型催化化工厂（独家）：氟苯 1000 + 光气 1000 + 苯胺 250 + 硝化煤油萃取剂 250
	 *       + 钠 ×1 + 铝粉 ×1 → PEEK 锭 ×2 + 硝化煤油萃取剂 250</li>
	 * </ul>
	 */
	public static void addPeekChemicalRecipes() {
		FluidType hf = ModFluids.HYDROFLUORIC_ACID.type;
		FluidType fb = ModFluids.FLUOROBENZENE.type;
		FluidType aniline = ModFluids.ANILINE.type;
		FluidType tbpKero = ModFluids.TBP_KERO.type;
		FluidType wasteAcid = ModFluids.WASTE_ACID.type;
		if(hf == null || hf == Fluids.NONE || fb == null || fb == Fluids.NONE
				|| aniline == null || aniline == Fluids.NONE || tbpKero == null || tbpKero == Fluids.NONE) {
			return;
		}

		// ---- 上游 1：萤石粉 + 硫酸 → 氢氟酸 + 石膏 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hydrofluoric_acid")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hydrofluoric_acid")
				.setup(100, 200)
				.inputItems(new ComparableStack(net.minecraft.init.Items.glowstone_dust, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_500))
				.outputFluids(new FluidStack(hf, 1_500))
				.outputItems(new ItemStack(ModItems.gypsum, 2)));
		}

		// ---- 上游 2A：萤石粉 + 废酸 → 氢氟酸 + 石膏 ----
		if(wasteAcid != null && wasteAcid != Fluids.NONE
				&& !hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_waste_acid")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_waste_acid")
				.setup(120, 300)
				.inputItems(new ComparableStack(net.minecraft.init.Items.glowstone_dust, 3))
				.inputFluids(new FluidStack(wasteAcid, 2_000))
				.outputFluids(new FluidStack(hf, 1_500))
				.outputItems(new ItemStack(ModItems.gypsum, 2)));
		}

		// ---- 上游 2C：氟石粉 + 硫酸 → 氢氟酸（高产） + 石膏 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_fluorite")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_fluorite")
				.setup(100, 200)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.fluorite, 1))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_500))
				.outputFluids(new FluidStack(hf, 2_500))
				.outputItems(new ItemStack(ModItems.gypsum, 2)));
		}

		// ---- 上游 2B：冰晶石块 + 硫酸 → 氢氟酸 + 铝粉 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_cryolite")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_cryolite")
				.setup(100, 300)
				.inputItems(new ComparableStack(com.hbm.inventory.OreDictManager.DictFrame.fromOne(
					com.hbm.items.ModItems.chunk_ore, com.hbm.items.ItemEnums.EnumChunkType.CRYOLITE)))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_500))
				.outputFluids(new FluidStack(hf, 2_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_aluminium, 1)));
		}

		// ---- 中游：苯胺 + 氢氟酸 + 硝酸 → 氟苯 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.fluorobenzene")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.fluorobenzene")
				.setup(120, 500)
				.inputFluids(
					new FluidStack(aniline, 250),
					new FluidStack(hf, 500),
					new FluidStack(Fluids.NITRIC_ACID, 750))
				.outputFluids(new FluidStack(fb, 1_500)));
		}

		// ---- 主产品（大型催化化工厂独家）：氟苯 + 光气 + 苯胺 + 硝化煤油萃取剂 + 钠 + 铝粉 → PEEK 锭×2 + 萃取剂回收 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.peek")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.peek", 400_000)
				.setup(200, 20_000)
				.inputFluids(
					new FluidStack(fb, 1_000),
					new FluidStack(Fluids.PHOSGENE, 1_000),
					new FluidStack(aniline, 250),
					new FluidStack(tbpKero, 250))
				.inputItems(
					new ComparableStack(com.hbm.items.ModItems.powder_sodium, 1),
					new ComparableStack(com.hbm.items.ModItems.powder_aluminium, 1))
				.outputItems(new ItemStack(ModItems.ingot_peek, 2))
				.outputFluids(new FluidStack(tbpKero, 250)));
		}

		// ---- HF 下游 1：黄饼 + 氢氟酸 → UF6（核燃料氟化增产） ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_uf6")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_uf6")
				.setup(100, 500)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_yellowcake, 1))
				.inputFluids(new FluidStack(hf, 1_500))
				.outputFluids(new FluidStack(Fluids.UF6, 2_000)));
		}

		// ---- HF 下游 1B：钚粉 + 氢氟酸 → PUF6（核燃料氟化增产） ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_puf6")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_puf6")
				.setup(160, 500)
				.inputItems(new OreDictStack(com.hbm.inventory.OreDictManager.PU.dust(), 1))
				.inputFluids(new FluidStack(hf, 1_500))
				.outputFluids(new FluidStack(Fluids.PUF6, 1_500)));
		}

		// ---- HF 下游（大型催化化工厂独家）：重砂混粉 + 氢氟酸 + 硝化煤油萃取剂 → 纯钽铁矿粉×3 + 铌粉×3 + 萃取剂回收 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.hf_coltan_niobium")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.hf_coltan_niobium", 200_000, 1)
				.setup(180, 1_000)
				.inputItems(new ComparableStack(ModItems.heavy_sand_mix, 2))
				.inputFluids(new FluidStack(hf, 800), new FluidStack(tbpKero, 250))
				.outputItems(
					new ItemStack(com.hbm.items.ModItems.powder_coltan, 3),
					new ItemStack(com.hbm.items.ModItems.powder_niobium, 3))
				.outputFluids(new FluidStack(tbpKero, 250)));
		}

		// ---- HF 下游（大型催化化工厂独家）：铍精矿 + 氢氟酸 → 铍锭×2 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.hf_beryllium")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.hf_beryllium", 250_000, 1)
				.setup(100, 400)
				.inputItems(new ComparableStack(ModItems.beryllium_concentrate, 1))
				.inputFluids(new FluidStack(hf, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_beryllium, 2)));
		}

		// ---- HF 下游（大型催化化工厂独家）：锆精矿 + 氢氟酸 → 锆锭×2 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.hf_zirconium")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.hf_zirconium", 300_000, 1)
				.setup(100, 400)
				.inputItems(new ComparableStack(ModItems.zirconium_concentrate, 1))
				.inputFluids(new FluidStack(hf, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_zirconium, 2)));
		}

		// ---- HF 下游（大型催化化工厂独家）：稀土熟料 + 氢氟酸 → 钕粉×1 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.hf_neodymium")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.hf_neodymium", 400_000, 2)
				.setup(160, 800)
				.inputItems(new ComparableStack(ModItems.rare_earth_calcine, 1))
				.inputFluids(new FluidStack(hf, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_neodymium, 1)));
		}

		// ---- HF 下游（大型催化化工厂独家）：石英粉 + 氢氟酸 + 硝酸 → 高纯硅锭×2 ----
		if(!LargeChemicalFactoryRecipes.hasExclusive("makentmgreatagain.large.hf_silicon")) {
			LargeChemicalFactoryRecipes.registerExclusive(new HeatChemicalRecipe("makentmgreatagain.large.hf_silicon", 150_000)
				.setup(120, 400)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_quartz, 2))
				.inputFluids(new FluidStack(hf, 500), new FluidStack(Fluids.NITRIC_ACID, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_silicon, 2)));
		}

		// ---- 上游新增：方钠石 + 硫酸 → 氢氟酸 + 钠粉 + 铝粉 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_sodalite")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_sodalite")
				.setup(120, 300)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.gem_sodalite, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_500))
				.outputFluids(new FluidStack(hf, 1_500))
				.outputItems(
					new ItemStack(com.hbm.items.ModItems.powder_sodium, 1),
					new ItemStack(com.hbm.items.ModItems.powder_aluminium, 1)));
		}

		// ---- 上游新增：铝土矿残渣（红泥） + 硫酸 → 氢氟酸 + 铁粉×2 + 钛粉 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_redmud")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_redmud")
				.setup(140, 400)
				.inputFluids(
					new FluidStack(Fluids.REDMUD, 1_000),
					new FluidStack(Fluids.SULFURIC_ACID, 1_000))
				.outputFluids(new FluidStack(hf, 1_000))
				.outputItems(
					new ItemStack(com.hbm.items.ModItems.powder_iron, 2),
					new ItemStack(com.hbm.items.ModItems.powder_titanium, 1)));
		}

		// ---- 下游新增：四氯化钛 + 氢氟酸 + 氢气 → 钛锭×6 + 氯气回收 ----
		if(com.makentmgreatagain.compat.HBMVersionCompat.hasTitaniumTetrachloride()
				&& !hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_titanium")) {
			FluidType ticl4 = com.makentmgreatagain.compat.HBMVersionCompat.titaniumTetrachloride();
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_titanium")
				.setup(100, 600)
				.inputFluids(
					new FluidStack(ticl4, 1_000),
					new FluidStack(hf, 1_000),
					new FluidStack(Fluids.HYDROGEN, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_titanium, 6))
				.outputFluids(new FluidStack(Fluids.CHLORINE, 250)));
		}

		// ---- 下游新增（兼作 ALUMINA 氟石并行替代）：铝酸钠 + 氢氟酸 → 氧化铝 + 钠粉 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_alumina")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_alumina")
				.setup(80, 300)
				.inputFluids(
					new FluidStack(Fluids.SODIUM_ALUMINATE, 300),
					new FluidStack(hf, 500))
				.outputFluids(new FluidStack(Fluids.ALUMINA, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_sodium, 1)));
		}

		// ---- 并行替代：煤粉 + 石油 + 氢氟酸 → 聚合物锭×6 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_polymer")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_polymer")
				.setup(100, 150)
				.inputItems(new OreDictStack(com.hbm.inventory.OreDictManager.COAL.dust(), 2))
				.inputFluids(
					new FluidStack(Fluids.PETROLEUM, 1_000, com.hbm.config.GeneralConfig.enable528PressurizedRecipes ? 1 : 0),
					new FluidStack(hf, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_polymer, 6)));
		}

		// ---- 并行替代：钽铁矿粉 + 氢氟酸 + 天然气 + 氧气 → 混酸(PAIN) ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_coltanpain")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_coltanpain")
				.setup(100, 150)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_coltan))
				.inputFluids(
					new FluidStack(hf, 500),
					new FluidStack(Fluids.GAS, 500),
					new FluidStack(Fluids.OXYGEN, 500))
				.outputFluids(new FluidStack(Fluids.PAIN, 1_500)));
		}

		// ---- 并行替代：古生矿粉 + 铋粒×4 + 氢氟酸 + 过氧化氢 → 死酸(DEATH) ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_osmiridiumdeath")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_osmiridiumdeath")
				.setup(200, 1_000)
				.inputItems(
					new ComparableStack(com.hbm.items.ModItems.powder_paleogenite),
					new ComparableStack(com.hbm.items.ModItems.nugget_bismuth, 4))
				.inputFluids(
					new FluidStack(hf, 2_000),
					new FluidStack(Fluids.PEROXIDE, 1_000, 5))
				.outputFluids(new FluidStack(Fluids.DEATH, 1_500, 0)));
		}

		// ---- 并行替代：石油 + 不饱和烃 + 氢氟酸 → 四氟甲烷 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_perfluoromethyl")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_perfluoromethyl")
				.setup(20, 150)
				.inputFluids(
					new FluidStack(Fluids.PETROLEUM, 1_000),
					new FluidStack(Fluids.UNSATURATEDS, 500),
					new FluidStack(hf, 500))
				.outputFluids(new FluidStack(Fluids.PERFLUOROMETHYL, 1_500)));
		}

		// ---- 并行替代：氯化方解石溶液 + 硫酸 + 氢氟酸 → 氯化方解石混合液 ----
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.hf_chlorocalcite_mix")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.hf_chlorocalcite_mix")
				.setup(60, 100)
				.inputFluids(
					new FluidStack(Fluids.CHLOROCALCITE_SOLUTION, 500),
					new FluidStack(Fluids.SULFURIC_ACID, 500),
					new FluidStack(hf, 250))
				.outputFluids(new FluidStack(Fluids.CHLOROCALCITE_MIX, 1_250)));
		}
	}

	/** 粉碎机：PEEK 锭 → PEEK 粉 */
	public static void addPeekShredderRecipes() {
		ShredderRecipes.setRecipe(new ComparableStack(ModItems.ingot_peek), new ItemStack(ModItems.powder_peek, 1));
		ShredderRecipes.neiShredderRecipes = null;
	}

	private static final String PLATE_WELDED_TC_OR_CD = "plateWeldedTechnetiumOrCadmium";
	private static boolean tcOrCdWeldedPlateRegistered;

	/** 流体传送器 / 物品传送器（工作台有序合成，四角焊接锝-钢板/焊接镉钢板通用） */
	public static void addXenTransceiverRecipes() {
		if(!tcOrCdWeldedPlateRegistered) {
			OreDictionary.registerOre(PLATE_WELDED_TC_OR_CD, new ItemStack(com.hbm.items.ModItems.plate_welded, 1, Mats.MAT_TCALLOY.id));
			OreDictionary.registerOre(PLATE_WELDED_TC_OR_CD, new ItemStack(com.hbm.items.ModItems.plate_welded, 1, Mats.MAT_CDALLOY.id));
			tcOrCdWeldedPlateRegistered = true;
		}

		// 流体传送器：中间不稳定X晶体，四周锝-钢合金桶，四角焊接锝-钢板（或焊接镉钢板）
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.xen_fluid_transceiver, 1),
			"PBP",
			"BXB",
			"PBP",
			'P', PLATE_WELDED_TC_OR_CD,
			'B', com.hbm.blocks.ModBlocks.barrel_tcalloy,
			'X', ModItems.crystal_xen_unstable));

		// 物品传送器：中间不稳定X晶体，四周DESH板条箱，四角焊接锝-钢板（或焊接镉钢板）
		GameRegistry.addRecipe(new ShapedOreRecipe(
			new ItemStack(ModBlocks.xen_item_transceiver, 1),
			"PCP",
			"CXC",
			"PCP",
			'P', PLATE_WELDED_TC_OR_CD,
			'C', com.hbm.blocks.ModBlocks.crate_desh,
			'X', ModItems.crystal_xen_unstable));
	}

	/** 投送探头、中继节点和绑定器的工作台配方。 */
	private static void addAtomicDeliveryCraftingRecipes() {
		ItemStack lens = new ItemStack(com.hbm.items.ModItems.part_generic, 1, EnumPartType.GLASS_POLARIZED.ordinal());
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.atomic_probe, 32),
			"XBX", "HVH", "XBX",
			'X', W.plateCast(), 'V', ModItems.crystal_xen_unstable,
			'H', com.hbm.items.ModItems.motor_desh, 'B', lens));
		GameRegistry.addRecipe(new ShapedOreRecipe(new ItemStack(ModBlocks.delivery_relay, 8),
			"XBX", "BVB", "XBX",
			'X', W.plateCast(), 'V', ModItems.crystal_xen_unstable, 'B', lens));
		GameRegistry.addRecipe(new ShapelessOreRecipe(new ItemStack(ModItems.atomic_linker),
			new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.BASIC.ordinal()),
			com.hbm.items.ModItems.screwdriver_desh));
	}

	/* =====================================================================================
	 *  石油耦合 · 孔雀石 / 赤铁矿双阶高产精炼线
	 *
	 *  一阶（0 新增物品/流体，3 机串联：热解炉 → 化工厂 → 原生离心机，单矿 8x 粉）：
	 *    · 孔雀石原石 + 石油气 2000 → 2× 孔雀石矿块 + CO2 250；2× 孔雀石矿块 + 石脑油 2000 + 芳香烃 1500 → 2× 铜晶体（离心 8× 铜粉）
	 *    · 赤铁矿石 + 石油气 2000 → 2× 氧化铁粉 + CO2 250；2× 氧化铁粉 + 石脑油 2000 + 芳香烃 1500 → 2× 铁晶体（离心 8× 铁粉）
	 *
	 *  二阶（重整油 + BTX + 氨 → 精制萃矿剂，3 机串联，单矿 18x 粉 + 高阶副产）：
	 *    · 孔雀石原石/矿块 + 精制萃矿剂 2500 + 重整油 1500 → 富铜萃液 1500 + CO2 500 → 热解 4× 精炼铜团 → 化工厂 18× 铜粉 + 阳极泥/氢氧化钴
	 *    · 赤铁矿石 + 精制萃矿剂 2500 + 重整油 1500 → 富铁萃液 1500 → 热解 3× 精炼铁团 + CO2 500 → 离心机 18× 铁粉 + 钛粉
	 * ===================================================================================== */

	private static boolean hasPyroRecipeForFluid(FluidType type) {
		if(type == null || type == Fluids.NONE) return true;
		for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
			if(rec.inputFluid != null && rec.inputFluid.type == type) return true;
		}
		return false;
	}

	private static boolean hasPyroRecipeForStack(ItemStack target) {
		if(target == null) return true;
		for(PyroOvenRecipes.PyroOvenRecipe rec : PyroOvenRecipes.recipes) {
			if(rec.inputItem != null && rec.inputItem.matchesRecipe(target, true)) return true;
		}
		return false;
	}

	/** 石油耦合双阶精炼线 · 热解炉配方（一阶石油气焙烧 + 二阶萃液浓缩） */
	public static void addPetroOrePyroRecipes() {
		ItemStack malachiteOre = new ItemStack(com.hbm.blocks.ModBlocks.stone_resource, 1,
				com.hbm.blocks.BlockEnums.EnumStoneType.MALACHITE.ordinal());
		ItemStack malachiteChunk = new ItemStack(com.hbm.items.ModItems.chunk_ore, 6, EnumChunkType.MALACHITE.ordinal());
		ItemStack hematiteOre = new ItemStack(com.hbm.blocks.ModBlocks.stone_resource, 1,
				com.hbm.blocks.BlockEnums.EnumStoneType.HEMATITE.ordinal());

		// 一阶孔雀石 ①：孔雀石原石 + 石油气 2000 mB → 6× 孔雀石矿块（显著高于手挖 2~4 个） + CO2 250 mB
		if(!hasPyroRecipeForStack(malachiteOre)) {
			PyroOvenRecipes.PyroOvenRecipe r = new PyroOvenRecipes.PyroOvenRecipe(100);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new FluidStack(Fluids.PETROLEUM, 2_000));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new ComparableStack(malachiteOre));
			r.out(malachiteChunk).out(new FluidStack(Fluids.CARBONDIOXIDE, 250));
			PyroOvenRecipes.recipes.add(r);
		}

		// 一阶赤铁矿 ①：赤铁矿石 + 石油气 2000 mB → 2× 氧化铁粉 + CO2 250 mB
		if(!hasPyroRecipeForStack(hematiteOre)) {
			PyroOvenRecipes.PyroOvenRecipe r = new PyroOvenRecipes.PyroOvenRecipe(100);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new FluidStack(Fluids.PETROLEUM, 2_000));
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new ComparableStack(hematiteOre));
			r.out(new ItemStack(ModItems.powder_oxidate_iron, 2)).out(new FluidStack(Fluids.CARBONDIOXIDE, 250));
			PyroOvenRecipes.recipes.add(r);
		}

		// 二阶孔雀石 ②：富铜萃液 1500 mB → 4× 精炼铜团
		FluidType cuLiquor = addonFluid(ModFluids.COPPER_EXTRACT_LIQUOR);
		if(cuLiquor != Fluids.NONE && !hasPyroRecipeForFluid(cuLiquor)) {
			PyroOvenRecipes.PyroOvenRecipe r = new PyroOvenRecipes.PyroOvenRecipe(120);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new FluidStack(cuLiquor, 1_500));
			r.out(new ItemStack(ModItems.refined_copper_clump, 4));
			PyroOvenRecipes.recipes.add(r);
		}

		// 二阶赤铁矿 ②：富铁萃液 1500 mB → 3× 精炼铁团 + CO2 500 mB
		FluidType feLiquor = addonFluid(ModFluids.IRON_EXTRACT_LIQUOR);
		if(feLiquor != Fluids.NONE && !hasPyroRecipeForFluid(feLiquor)) {
			PyroOvenRecipes.PyroOvenRecipe r = new PyroOvenRecipes.PyroOvenRecipe(120);
			com.makentmgreatagain.compat.HBMVersionCompat.pyroIn(r, new FluidStack(feLiquor, 1_500));
			r.out(new ItemStack(ModItems.refined_iron_clump, 3)).out(new FluidStack(Fluids.CARBONDIOXIDE, 500));
			PyroOvenRecipes.recipes.add(r);
		}
	}

	/** 石油耦合双阶精炼线 · 化工厂配方（一阶石脑油/芳香烃结晶 + 二阶溶剂合成/萃取/铜精炼） */
	public static void addPetroOreChemicalRecipes() {
		ItemStack malachiteOre = new ItemStack(com.hbm.blocks.ModBlocks.stone_resource, 1,
				com.hbm.blocks.BlockEnums.EnumStoneType.MALACHITE.ordinal());
		ItemStack malachiteChunk3 = new ItemStack(com.hbm.items.ModItems.chunk_ore, 3, EnumChunkType.MALACHITE.ordinal());
		ItemStack malachiteChunk2 = new ItemStack(com.hbm.items.ModItems.chunk_ore, 2, EnumChunkType.MALACHITE.ordinal());
		ItemStack hematiteOre = new ItemStack(com.hbm.blocks.ModBlocks.stone_resource, 1,
				com.hbm.blocks.BlockEnums.EnumStoneType.HEMATITE.ordinal());

		// 一阶孔雀石 ②：2× 孔雀石矿块 + 石脑油 2000 + 芳香烃 1500 → 2× 铜晶体（离心 8× 铜粉）
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t1_malachite_leach")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t1_malachite_leach")
				.setup(120, 1_000)
				.inputItems(new ComparableStack(malachiteChunk2))
				.inputFluids(
					new FluidStack(Fluids.NAPHTHA, 2_000),
					new FluidStack(Fluids.AROMATICS, 1_500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.crystal_copper, 2)));
		}

		// 一阶赤铁矿 ②：2× 氧化铁粉 + 石脑油 2000 + 芳香烃 1500 → 2× 铁晶体（离心 8× 铁粉）
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t1_hematite_reduce")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t1_hematite_reduce")
				.setup(120, 1_000)
				.inputItems(new ComparableStack(ModItems.powder_oxidate_iron, 2))
				.inputFluids(
					new FluidStack(Fluids.NAPHTHA, 2_000),
					new FluidStack(Fluids.AROMATICS, 1_500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.crystal_iron, 2)));
		}

		FluidType nh3 = addonFluid(ModFluids.AMMONIA);
		FluidType solvent = addonFluid(ModFluids.REFORM_SOLVENT);
		FluidType cuLiquor = addonFluid(ModFluids.COPPER_EXTRACT_LIQUOR);
		FluidType feLiquor = addonFluid(ModFluids.IRON_EXTRACT_LIQUOR);
		if(nh3 == Fluids.NONE || solvent == Fluids.NONE || cuLiquor == Fluids.NONE || feLiquor == Fluids.NONE) return;

		// 二阶前置：重整油 2500 + 轻质芳烃(BTX) 1500 + 氨 500 → 精制萃矿剂 2000 mB
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.reform_solvent")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.reform_solvent")
				.setup(100, 1_500)
				.inputFluids(
					new FluidStack(Fluids.REFORMATE, 2_500),
					new FluidStack(Fluids.XYLENE, 1_500),
					new FluidStack(nh3, 500))
				.outputFluids(new FluidStack(solvent, 2_000)));
		}

		// 二阶孔雀石 ①（原石）：1× 孔雀石原石 + 精制萃矿剂 3500 + 重整油 2000 → 富铜萃液 3000 + CO2 750（精准采集原矿双倍萃液）
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t2_malachite_ore")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t2_malachite_ore")
				.setup(160, 2_500)
				.inputItems(new ComparableStack(malachiteOre))
				.inputFluids(
					new FluidStack(solvent, 3_500),
					new FluidStack(Fluids.REFORMATE, 2_000))
				.outputFluids(
					new FluidStack(cuLiquor, 3_000),
					new FluidStack(Fluids.CARBONDIOXIDE, 750)));
		}

		// 二阶孔雀石 ①b（矿块）：3× 孔雀石矿块 + 精制萃矿剂 2500 + 重整油 1500 → 富铜萃液 1500 + CO2 500
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t2_malachite_chunk")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t2_malachite_chunk")
				.setup(140, 2_000)
				.inputItems(new ComparableStack(malachiteChunk3))
				.inputFluids(
					new FluidStack(solvent, 2_500),
					new FluidStack(Fluids.REFORMATE, 1_500))
				.outputFluids(
					new FluidStack(cuLiquor, 1_500),
					new FluidStack(Fluids.CARBONDIOXIDE, 500)));
		}

		// 二阶孔雀石 ③a（纯铜）：4× 精炼铜团 + 水 1000 → 18× 铜粉（不产副产物，防堵槽）
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t2_copper_refine_pure")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t2_copper_refine_pure")
				.setup(100, 2_000)
				.inputItems(new ComparableStack(ModItems.refined_copper_clump, 4))
				.inputFluids(new FluidStack(Fluids.WATER, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_copper, 18)));
		}

		// 二阶孔雀石 ③b（含副产批量精炼）：8× 精炼铜团 + 水 2000 → 36× 铜粉 + 2× 阳极泥 + 1× 氢氧化钴（纯固定产出，无概率随机）
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t2_copper_refine")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t2_copper_refine")
				.setup(160, 2_500)
				.inputItems(new ComparableStack(ModItems.refined_copper_clump, 8))
				.inputFluids(new FluidStack(Fluids.WATER, 2_000))
				.outputItems(
					new ItemStack(com.hbm.items.ModItems.powder_copper, 36),
					new ItemStack(ModItems.anode_mud, 2),
					new ItemStack(ModItems.cobalt_hydroxide, 1)));
		}

		// 二阶赤铁矿 ①：1× 赤铁矿石 + 精制萃矿剂 2500 + 重整油 1500 → 富铁萃液 1500
		if(!hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.chem.t2_hematite_ore")) {
			RecipeRegistrar.add(ChemicalPlantRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.chem.t2_hematite_ore")
				.setup(140, 2_000)
				.inputItems(new ComparableStack(hematiteOre))
				.inputFluids(
					new FluidStack(solvent, 2_500),
					new FluidStack(Fluids.REFORMATE, 1_500))
				.outputFluids(new FluidStack(feLiquor, 1_500)));
		}
	}

	/** 石油耦合双阶精炼线 · 离心机配方（二阶精炼铁团 → 6× 铁粉 + 钛粉，3 团合 18× 铁粉） */
	public static void addPetroOreCentrifugeRecipes() {
		ComparableStack ironClumpKey = new ComparableStack(ModItems.refined_iron_clump, 1);
		if(!CentrifugeRecipes.recipes.containsKey(ironClumpKey)) {
			CentrifugeRecipes.recipes.put(ironClumpKey, new ItemStack[] {
				new ItemStack(com.hbm.items.ModItems.powder_iron, 4),
				new ItemStack(com.hbm.items.ModItems.powder_iron, 2),
				new ItemStack(com.hbm.items.ModItems.powder_titanium, 1),
				new ItemStack(com.hbm.items.ModItems.powder_lithium_tiny, 1)
			});
		}
		if(!hasCentrifugeItem(ModItems.refined_iron_clump)) {
			LargeCentrifugeRecipes.RECIPES.add(new CentrifugeRecipe()
				.items(new ComparableStack(ModItems.refined_iron_clump, 3))
				.outItems(
					new ItemStack(com.hbm.items.ModItems.powder_iron, 18),
					new ItemStack(com.hbm.items.ModItems.powder_titanium, 3),
					new ItemStack(com.hbm.items.ModItems.powder_lithium_tiny, 3))
				.power(15_000L).duration(100));
		}
	}

	public static void addQuantumPulseAssemblyRecipe() {
		if(GeneralConfig.enable528 || ModItems.quantum_pulse_component == null) return;
		String name = "makentmgreatagain.ass.quantum_pulse_component";
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, name)) return;
		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, quantumPulseRecipe(name)
			.outputItems(new ItemStack(ModItems.quantum_pulse_component)));
	}

	public static void addQuantumPulsePrecAssRecipe() {
		if(!GeneralConfig.enable528 || ModItems.quantum_pulse_component == null) return;
		String name = "makentmgreatagain.precass.quantum_pulse_component";
		if(hasGenericRecipe(PrecAssRecipes.INSTANCE, name)) return;
		int reclaim = GeneralConfig.enableExpensiveMode ? 50 : 75;
		PrecAssRecipes.INSTANCE.registerPair(quantumPulseRecipe(name),
			new ItemStack(ModItems.quantum_pulse_component), 50, reclaim);
	}

	private static GenericRecipe quantumPulseRecipe(String name) {
		return new GenericRecipe(name).setup(400, 20_000L)
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CHIP_QUANTUM),
				new ComparableStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.CONTROLLER_ADVANCED),
				new ComparableStack(com.hbm.items.ModItems.circuit, 8, EnumCircuitType.CAPACITOR_BOARD),
				new ComparableStack(ModItems.wire_platinum, 16),
				new ComparableStack(ModItems.ingot_peek, 2));
	}

	/**
	 * 1~3 级并行升级（正常模式：装配机，耗时与耗电均为同级超速传动的 1.5 倍）。
	 */
	public static void addParallelUpgradeAssemblyRecipes() {
		if(com.hbm.config.GeneralConfig.enable528) return;
		if(ModItems.upgrade_parallel_1 == null || ModItems.upgrade_parallel_2 == null || ModItems.upgrade_parallel_3 == null) return;

		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.ass.parallel1")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ass.parallel1")
				.setup(300, 150)
				.outputItems(new ItemStack(ModItems.upgrade_parallel_1, 1))
				.inputItems(
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(ModItems.ingot_peek, 16),
					new ComparableStack(ModItems.wire_platinum, 32),
					new ComparableStack(com.hbm.items.ModItems.circuit, 4, com.hbm.items.machine.ItemCircuit.EnumCircuitType.BISMOID)));
		}

		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.ass.parallel2")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ass.parallel2")
				.setup(900, 150)
				.outputItems(new ItemStack(ModItems.upgrade_parallel_2, 1))
				.inputItems(
					new ComparableStack(ModItems.upgrade_parallel_1, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(ModItems.ingot_peek, 16),
					new ComparableStack(com.hbm.items.ModItems.ingot_combine_steel, 16),
					new ComparableStack(com.hbm.items.ModItems.circuit, 16, com.hbm.items.machine.ItemCircuit.EnumCircuitType.CAPACITOR_BOARD)));
		}

		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.ass.parallel3")) {
			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, new GenericRecipe("makentmgreatagain.ass.parallel3")
				.setup(1_800, 150)
				.outputItems(new ItemStack(ModItems.upgrade_parallel_3, 1))
				.inputItems(
					new ComparableStack(ModItems.upgrade_parallel_2, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(com.hbm.items.ModItems.ingot_bscco, 8),
					new ComparableStack(ModItems.ingot_erbium, 16),
					new ComparableStack(ModItems.ingot_helium, 16)));
		}
	}

	/**
	 * 1~3 级并行升级（528 模式：精密装配机，耗时与耗电为同级超速传动的 1.5 倍；
	 * 损坏概率 1~3 级分别为 25% / 50% / 75%，废品回收走 registerPair，回收率与超速传动一致）。
	 */
	public static void addParallelUpgradePrecAssRecipes() {
		if(!com.hbm.config.GeneralConfig.enable528) return;
		if(ModItems.upgrade_parallel_1 == null || ModItems.upgrade_parallel_2 == null || ModItems.upgrade_parallel_3 == null) return;

		com.hbm.inventory.recipes.PrecAssRecipes table = com.hbm.inventory.recipes.PrecAssRecipes.INSTANCE;
		int reclaim = com.hbm.config.GeneralConfig.enableExpensiveMode ? 50 : 75;

		if(!table.recipeNameMap.containsKey("makentmgreatagain.precass.parallel1")) {
			table.registerPair(new GenericRecipe("makentmgreatagain.precass.parallel1")
				.setup(300, 1_500)
				.inputItems(
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(ModItems.ingot_peek, 16),
					new ComparableStack(ModItems.wire_platinum, 32),
					new ComparableStack(com.hbm.items.ModItems.circuit, 4, com.hbm.items.machine.ItemCircuit.EnumCircuitType.BISMOID)),
				new ItemStack(ModItems.upgrade_parallel_1), 75, reclaim);
		}

		if(!table.recipeNameMap.containsKey("makentmgreatagain.precass.parallel2")) {
			table.registerPair(new GenericRecipe("makentmgreatagain.precass.parallel2")
				.setup(900, 7_500)
				.inputItems(
					new ComparableStack(ModItems.upgrade_parallel_1, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(ModItems.ingot_peek, 16),
					new ComparableStack(com.hbm.items.ModItems.ingot_combine_steel, 16),
					new ComparableStack(com.hbm.items.ModItems.circuit, 16, com.hbm.items.machine.ItemCircuit.EnumCircuitType.CAPACITOR_BOARD)),
				new ItemStack(ModItems.upgrade_parallel_2), 50, reclaim);
		}

		if(!table.recipeNameMap.containsKey("makentmgreatagain.precass.parallel3")) {
			table.registerPair(new GenericRecipe("makentmgreatagain.precass.parallel3")
				.setup(1_800, 150_000)
				.inputItems(
					new ComparableStack(ModItems.upgrade_parallel_2, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_speed_3, 1),
					new ComparableStack(com.hbm.items.ModItems.upgrade_effect_3, 1),
					new ComparableStack(com.hbm.items.ModItems.ingot_bscco, 8),
					new ComparableStack(ModItems.ingot_erbium, 16),
					new ComparableStack(ModItems.ingot_helium, 16)),
				new ItemStack(ModItems.upgrade_parallel_3), 25, reclaim);
		}
	}
}

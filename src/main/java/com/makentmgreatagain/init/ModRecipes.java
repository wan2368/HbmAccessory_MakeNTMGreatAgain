package com.makentmgreatagain.init;
//原版属性

import com.hbm.inventory.recipes.CentrifugeRecipes;
import com.hbm.inventory.recipes.CompressorRecipes;
import com.hbm.inventory.recipes.ElectrolyserFluidRecipes;
import com.hbm.inventory.recipes.FusionRecipe;
import com.hbm.inventory.recipes.FusionRecipes;
import com.hbm.items.machine.ItemBatteryPack;
import com.hbm.util.Tuple.Pair;
import com.hbm.inventory.recipes.SolidificationRecipes;

import net.minecraft.enchantment.Enchantment;
import static com.hbm.inventory.OreDictManager.ANY_RESISTANTALLOY;
import static com.hbm.inventory.OreDictManager.CU;
import static com.hbm.inventory.OreDictManager.DESH;
import static com.hbm.inventory.OreDictManager.STEEL;
import static com.hbm.inventory.OreDictManager.W;
import static com.hbm.inventory.OreDictManager.ANY_COKE;
import static com.hbm.inventory.OreDictManager.KEY_SAND;
import static com.hbm.inventory.OreDictManager.S;

import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.CombinationRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.PUREXRecipe;
import com.hbm.inventory.recipes.PUREXRecipes;
import com.hbm.inventory.recipes.PyroOvenRecipes;
import com.hbm.util.CompatRecipeRegistry;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.recipes.ArcFurnaceRecipes;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.BlastFurnaceRecipesNT;
import com.hbm.inventory.recipes.GenericRecipeNoPower;
import com.hbm.inventory.recipes.HydrotreatingRecipes;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.util.Tuple.Triplet;
import com.hbm.config.GeneralConfig;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.items.ItemEnums.EnumChunkType;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.makentmgreatagain.MakeNTMGreatAgain;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Blocks;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * 本模组自己的合成配方（原版工作台配方）。
 *
 * 与 HBM 机器配方分开放：接入 HBM 压机/化工厂的配方在 compat/NTMCompat.java 里，
 * 因为那些必须等 HBM 加载完（postInit 阶段）才能注册；
 * 原版配方不依赖别的模组，init 阶段就能注册。
 *
 * 1.7.10 注册配方的三种常见写法：
 *   addShapelessRecipe(输出, 输入1, 输入2...)        无序合成，材料摆放位置无所谓
 *   addShapedRecipe(输出, "图案行", 键值对...)        有序合成，需要按图案摆放
 *   addSmelting(输入, 输出, 经验值)                   熔炉烧炼
 */

public class ModRecipes {

    public static void init() {
		// 空装配模板：模拟电路板 + 纸 + 任意染料
		addBlankTemplateRecipe();
		//高和
		addAdvancedAlloyRecipe();
		//高和装备
		addAlloyRebornGearRecipes();
		//drx香烟
		addCigaretteDrxRecipe();
		//环境地下泵（装配机）
		addEnvironmentPumpAssemblyRecipe();
		//重生版换热器（装配机）
		addHeatexRebornAssemblyRecipe();
		//石膏 → 白色染料（工作台）
		addGypsumDyeRecipe();
		// 损坏的高级合金刀片(scrap_blade) → 熔炉 → 1 个高级合金锭
		// 只烧损坏形态：完好刀片不能直接熔炼，必须先磨坏变成 scrap_blade
		GameRegistry.addSmelting(ModItems.scrap_blades_alloy_reborn,
				new ItemStack(ModItems.ingot_advanced_alloy, 1), 1.0F);
		//氧化铁
		GameRegistry.addSmelting(ModItems.powder_oxidate_iron,
				new ItemStack(Items.iron_ingot, 1), 1.0F);
		//锶线第 2 步：天青石 → 氧化锶（单输入煅烧，只能挂原版熔炉）
		addStrontiumSmeltingRecipe();
		//电力悬浮作业背包（工作台 3×3）
		addHoverJetpackRecipe();
		//动力装甲电池组 Mk4（工作台 3×3）
		addArmorBatteryMk4Recipe();
		//动力装甲电池组 Mk5（工作台 3×3
		addArmorBatteryMk5Recipe();

        MakeNTMGreatAgain.LOG.info("ModRecipes: vanilla recipes registered.");
    }
	/**
	 */
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
	
	public static void addArmorBatteryMk5Recipe() {
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.armor_battery_mk5, 1),
			"XAX",
			"XBX",
			"XAX",
			'X', com.hbm.items.ModItems.plate_combine_steel,
			'A', ModItems.armor_battery_mk4,
			'B', new ItemStack(com.hbm.items.ModItems.battery_pack, 1, ItemBatteryPack.EnumBatteryPack.CAPACITOR_SPARK.ordinal()));
	}
	/**
	 * 电力悬浮作业背包（原版工作台有序配方）：
	 */
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
	/** 空装配模板：1 模拟电路板 + 1 纸 + 任意染料（无序） */
	public static void addBlankTemplateRecipe() {
		GameRegistry.addRecipe(new ShapelessOreRecipe(
				new ItemStack(ModItems.blank_template, 1),
				new ItemStack(com.hbm.items.ModItems.circuit, 1, EnumCircuitType.ANALOG.ordinal()),
				new ItemStack(Items.paper),
				"dye"));
	}

	/** 高炉配方：1 紫铜锭 + 1 钢锭 → 2 高级合金锭 */
	public static void addAdvancedAlloyRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.advancedAlloy")) return;

		BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.advancedAlloy")
			.setDuration(400)
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.ingot_red_copper),
				new ComparableStack(com.hbm.items.ModItems.ingot_steel))
			.outputItems(new ItemStack(ModItems.ingot_advanced_alloy, 2)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: red copper + steel -> 2x advanced alloy");
	}

	/**
	 * 粉碎配方：1 石墨锭 → 1 石墨粉。
	 * 写进 HBM 的粉碎总表（ShredderRecipes.shredderRecipes），
	 * 原版粉碎机和附属高级粉碎机共用这张表，两边同时生效。
	 * 直接 put 覆盖（不用 setRecipe——它遇到自动生成过的键会跳过），
	 * 并清空 NEI 缓存强制重建，保证配方列表里能看到。
	 */
	public static void addGraphiteShredderRecipe() {
		ComparableStack in = new ComparableStack(new ItemStack(com.hbm.items.ModItems.ingot_graphite)).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_graphite));
		ShredderRecipes.neiShredderRecipes = null;   // 让 NEI 下次查询时重建映射

		MakeNTMGreatAgain.LOG.info("Added shredder recipe: graphite ingot -> graphite powder");
	}
	
	//粉碎含盐：
	public static void addSaltSandShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Sand).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,2));
		ShredderRecipes.neiShredderRecipes = null;
	}
	public static void addSaltRedSandShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Red_Sand).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,1));
		ShredderRecipes.neiShredderRecipes = null;
	}
	public static void addSaltGravelShredderRecipe() {
		ComparableStack in = new ComparableStack(ModBlocks.Block_Salt_Contain_Gravel).makeSingular();
		ShredderRecipes.shredderRecipes.put(in, new ItemStack(ModItems.powder_salt,2));
		ShredderRecipes.neiShredderRecipes = null;
	}

	/**
	 * 电弧炉配方：碳化钨粉 → 碳化钨锭（solid 路线，出物品不出熔融金属）
	 * ArcFurnaceRecipes.register 是 public static；占用检查会自动跳过重复输入，
	 * 我们的粉是新物品不会被 HBM autogen 占用，注册必定生效
	 */
	public static void addTungstenCarbideArcRecipe() {
		ArcFurnaceRecipes.register(
			new ComparableStack(ModItems.powder_carbon_tungsten_mixture),
			new ArcFurnaceRecipes.ArcFurnaceRecipe().solid(new ItemStack(com.hbm.items.ModItems.ingot_tungsten_carbide)));

		MakeNTMGreatAgain.LOG.info("Added arc furnace recipe: tungsten carbide powder -> tungsten carbide ingot");
	}
	
	//高强度
	public static void addTungstencobaltArcRecipe() {
		ArcFurnaceRecipes.register(
			new ComparableStack(ModItems.powder_cobalt_tungsten_mixture),
			new ArcFurnaceRecipes.ArcFurnaceRecipe().solid(new ItemStack(ModItems.powder_cobalt_tungsten_ingot)));

		MakeNTMGreatAgain.LOG.info("Added arc furnace recipe: tungsten carbide powder -> tungsten carbide ingot");
	}
	
	
	//固化
	public static void addWaterToSnowballRecipe() {
		SolidificationRecipes.recipes.put(Fluids.WATER,
			new Pair<Integer, ItemStack>(125, new ItemStack(Items.snowball , 1)));
	}
	/**
	 * 固化配方：125mB 海水 → 1 盐粉。
	 *
	 * ★ SolidificationRecipes.recipes 的键类型是 FluidType，而 ModFluids.SEA_WATER 是
	 * FluidDef（注册用的定义包装），不能直接当键，必须取注册后才赋值的 .type 字段。
	 * 照 addSaltWaterElectrolyserRecipe 的写法先判空，避免拿到 null 塞进配方表。
	 */
	public static void addSreaWaterToSaltRecipe() {
		FluidType seaWater = ModFluids.SEA_WATER.type;
		if (seaWater == null || seaWater == Fluids.NONE) return;
		if (SolidificationRecipes.getOutput(seaWater) != null) return;

		SolidificationRecipes.recipes.put(seaWater,
			new Pair<Integer, ItemStack>(750, new ItemStack(ModItems.powder_salt , 1)));

		MakeNTMGreatAgain.LOG.info("Added solidifier recipe: sea water -> salt powder");
	}
	/**
	 * 化工厂配方：2000mB@1P 乙醇 + 1 碳化钨粉 + 1 钴粉 → 2 高强度碳化钨粉
	 * setup(900, 50_000)：900tick=45秒；每tick 50,000 HE（一次配方共 4500万 HE）
	 * 注意配方名必须与上一条不同——重名 HBM 会直接抛 IllegalStateException
	 */
	public static void addCobaltCarbideRecipe() {
		if(hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.cobaltCarbide")) return;
		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.cobaltCarbide")
			.setup(900, 50_000)
			.inputItems(
				new ComparableStack(ModItems.powder_carbon_tungsten_mixture),
				new ComparableStack(com.hbm.items.ModItems.powder_cobalt))
			.inputFluids(new FluidStack(Fluids.ETHANOL, 2000, 1))
			.outputItems(new ItemStack(ModItems.powder_cobalt_tungsten_mixture, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: pressurized ethanol + WC powder + cobalt powder -> 2x high-strength WC powder");
	}

	/** 盐粉是否可用：只看矿典 dustSalt 有没有内容（附属自己的盐粉或他模组的都算）。 */
	private static AStack findSalt() {
		if(OreDictionary.getOres("dustSalt").isEmpty()) {
			MakeNTMGreatAgain.LOG.warn("矿典里没有 dustSalt，跳过需要盐粉的配方");
			return null;
		}
		return new OreDictStack("dustSalt");
	}

	/**
	 * 化工厂配方：1000mB 水 + 1 盐粉（矿典 dustSalt） → 1000mB 盐水
	 * 走矿典而不直接引用任何模组的类，盐粉来源是附属自己注册的 powder_salt。
	 */
	public static void addSaltWaterRecipe() {
		AStack salt = findSalt();
		if(salt == null) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.saltWater")
			.setup(60, 500)
			.inputItems(salt)
			.inputFluids(new FluidStack(Fluids.WATER, 1000))
			.outputFluids(new FluidStack(ModFluids.SALT_WATER.type, 1000)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: water + dustSalt -> salt water");
	}

	//化工厂：300mB 氢 + 1000mB 氯气 → 400mB 盐酸
	// 数值照抄航天版 ChemplantRecipes id 103：HCL / 250 tick / H2 300 + Cl2 1000 → HCL 400
	public static void addHCLChemicalPlantRecipe() {
		FluidType hcl = HBMCompat.hcl();
		if(hcl == Fluids.NONE) return;
		if(hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.hcl")) return;
		// 航天版自带 id 103 那条，别再加第二条
		if(hostProducesFluid(ChemicalPlantRecipes.INSTANCE, hcl)) {
			MakeNTMGreatAgain.LOG.info("宿主已有盐酸配方，附属不再注册");
			return;
		}

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.hcl")
			.setup(250, 100)
			.inputFluids(new FluidStack(Fluids.HYDROGEN, 300), new FluidStack(Fluids.CHLORINE, 1_000))
			.outputFluids(new FluidStack(hcl, 400)));
	}
	
	//离心机配方
	//
	// 注意：输出数组千万不要用 null 占位补到 4 格！
	// HBM 的 NEI 通用处理器（NEIUniversalHandler.loadCraftingRecipes）在匹配配方时会无条件
	// 对每个输出槽调用 stack.getItem()，遇到 null 直接空指针闪退。离心机 TileEntity 那边
	// 是按 Math.min(4, out.length) 读数组的，给 1 个元素就只占 1 格，完全正常。
	// （HBM 自带的离心机配方也从来不写 null，四条输出全是实物品。）
	public static void addSaltFromSandRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Sand),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 4) });
	}

	public static void addSaltFromRedSandRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Red_Sand),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 2) ,
				new ItemStack(ModItems.powder_oxidate_iron, 1)
			});
	}

	public static void addIronFromRedSandRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(Blocks.sand, 1, 1),     // 1 个红沙,
			new ItemStack[] {
				new ItemStack(ModItems.powder_oxidate_iron, 1)
			});
	}

	public static void addSaltFromGravelRecipe() {
		CentrifugeRecipes.recipes.put(
			new ComparableStack(ModBlocks.Block_Salt_Contain_Gravel),
			new ItemStack[] {
				new ItemStack(ModItems.powder_salt, 4) });
	}
	
	//HBMCompat.hcl()
	//电解hcl
	//
	// 防冲突：配方表是 HashMap<FluidType, ElectrolysisRecipe>，key 是流体对象本身。
	// 航天版原生就有 HCL 电解配方，直接 put 同 key 会「顶掉」它（不是两份并存，也不会串到别的流体上）。
	// 这里跟 HBMCompat.addHotBrineRefineryRecipe 保持同一套策略：已有配方就不动，让原版优先。
	// 真想覆盖原版（比如嫌它耗时/产出比例不对），把下面 getRecipe != null 那段删掉即可。
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
	//电解盐水机配方
	// 同上：盐水是本模组自己的流体，正常不会和 HBM 冲突，但 /ntmreload 或重载后重复注入时
	// 也走「已有就跳过」，避免日志噪音和多余的 put。
	public static void addSaltWaterElectrolyserRecipe() {
		FluidType brine = ModFluids.SALT_WATER.type;
		if (brine == null || brine == Fluids.NONE) return;
		if (ElectrolyserFluidRecipes.getRecipe(brine) != null) return;

		ElectrolyserFluidRecipes.recipes.put(brine,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1000,
				new FluidStack(Fluids.CHLORINE, 250),
				new FluidStack(Fluids.HYDROGEN, 450)));
		//new ItemStack(ModItems.powder_carbon_tungsten_mixture, 2);
	}
	/**
	 * 搅拌机配方：1000mB 水 + 1 盐粉 → 1000mB 盐水
	 *
	 * 注意：HBM 的 MixerRecipes.MixerRecipe 构造器和 setStack1/setStack2/setSolid 都是 protected，
	 * 附属在别的包里不能直接 new，也不能链式调用；但字段是 public、protected 构造器对子类可见。
	 * 所以这里用一个子类 + 直接给字段赋值的方式绕过（见 AddonMixerRecipe）。
	 */
	public static void addSaltWaterMixerRecipe() {
		AStack salt = findSalt();
		if(salt == null) return;

		AddonMixerRecipe recipe = new AddonMixerRecipe(1000, 100);
		recipe.input1 = new FluidStack(Fluids.WATER, 1000);
		recipe.solidInput = salt;

		MixerRecipes.register(ModFluids.SALT_WATER.type, recipe);
		MakeNTMGreatAgain.LOG.info("Added mixer recipe: water + dustSalt -> salt water");
	}

	//搅拌机：300mB 氢 + 1000mB 氯气 → 400mB 盐酸（比例与化工厂、航天版一致，不做更省料的旁路）
	public static void addHCLMixerRecipe() {
		FluidType hcl = HBMCompat.hcl();
		if(hcl == Fluids.NONE) return;
		if(MixerRecipes.getOutput(hcl) != null) return;

		AddonMixerRecipe recipe = new AddonMixerRecipe(400, 50);
		recipe.input1 = new FluidStack(Fluids.HYDROGEN, 300);
		recipe.input2 = new FluidStack(Fluids.CHLORINE, 1000);

		MixerRecipes.register(hcl, recipe);
	}

	/** 子类只为了拿到 protected 构造器；字段本身是 public，直接赋值即可。 */
	private static class AddonMixerRecipe extends MixerRecipes.MixerRecipe {
		AddonMixerRecipe(int output, int duration) { super(output, duration); }
	}

	/**
	 * 化工厂配方：2000mB 氢氧化合物 + 1 石墨粉 + 1 钨粉 → 2 碳化钨粉
	 * setup(1200, 100_000)：1200tick=60秒；每 tick 耗电 100,000 HE（一次配方共 1.2 亿 HE）
	 * 化工厂/大型化工厂共用 ChemicalPlantRecipes 这张表，注册一次两边生效
	 */
	public static void addTungstenCarbideRecipe() {
		if(hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, "makentmgreatagain.tungstenCarbide")) return;
		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.tungstenCarbide")
			.setup(1200, 100_00)
			.inputItems(
				new ComparableStack(ModItems.powder_graphite),
				new ComparableStack(com.hbm.items.ModItems.powder_tungsten))
			.inputFluids(new FluidStack(Fluids.OXYHYDROGEN, 2000))
			.outputItems(new ItemStack(ModItems.powder_carbon_tungsten_mixture, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: ethanol + graphite + tungsten powder -> 2x tungsten carbide powder");
	}
	/**
	 * 高级粉碎机本体配方（装配机 / 大型装配机共用同一张表）：
	 * 8 锝钢合金锭 + 4 DESH锭 + 4 铸造钢板 + 16 钨螺栓 → 1 高级粉碎机
	 * setup(900, 500)：900 tick = 45 秒；500 HE/tick = 10000 HE/秒。
	 */
	public static void addShredderAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.alloyShredder")) return;
		AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.alloyShredder")
			.setup(900, 50000)
			.outputItems(new ItemStack(com.makentmgreatagain.init.ModBlocks.block_alloy_shredder))
			.inputItems(
				new OreDictStack(ANY_RESISTANTALLOY.ingot(), 8),
				new OreDictStack(DESH.ingot(), 4),
				new OreDictStack(STEEL.plateCast(), 4),
				new OreDictStack(W.bolt(), 16),
				new ComparableStack(com.hbm.items.ModItems.motor_desh, 4)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: 8x resistant alloy ingot + 4x desh ingot + 4x cast steel plate + 16x tungsten bolt -> alloy shredder");
	}

	/**
	 * 环境地下泵配方（装配机，55 秒，10,000 HE/t）：
	 * 2 铋马达 + 8 焊接锝钢板 + 1 铍制飞轮 + 8 钢杆 + 16 高速钢板。
	 */
	public static void addEnvironmentPumpAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.environmentPump")) return;
		AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.environmentPump")
			.setup(1100, 10_000)
			.outputItems(new ItemStack(ModBlocks.environment_pump))
			.inputItems(
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 2),
				new OreDictStack(ANY_RESISTANTALLOY.plateWelded(), 8),
				new ComparableStack(com.hbm.items.ModItems.flywheel_beryllium, 1),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_poles, 8),
				new ComparableStack(com.hbm.items.ModItems.plate_dura_steel, 16)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: environment pump");
	}

	/**
	 * 「巨尻-9000」储罐（重生版）配方。
	 * 原版 machine_bat9000 在 HBM 里已经没有正向合成，只剩 AnvilRecipes 那条拆解配方
	 * setup(200, 100) 与 HBM 的 ass.tank / ass.bigasstank 保持同一档耗时与能耗。
	 */
	public static void addBat9000AssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.bat9000Reborn")) return;
		AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.bat9000Reborn")
			.setup(200, 100)
			.outputItems(new ItemStack(com.makentmgreatagain.init.ModBlocks.block_bat9000_reborn))
			.inputItems(
				new OreDictStack(ANY_RESISTANTALLOY.plateCast(), 4),
				new OreDictStack(STEEL.plate(), 16),
				new OreDictStack(STEEL.shell(), 4),
				new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 16),
				new ComparableStack(com.hbm.blocks.ModBlocks.glass_quartz, 4)));
	}

	/**
	 * 重生版换热器配方（装配机，30 秒，5,000 HE/t）：
	 */
	public static void addHeatexRebornAssemblyRecipe() {
		if(hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.heatexReborn")) return;
		AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.heatexReborn")
			.setup(600, 5_000)
			.outputItems(new ItemStack(ModBlocks.heatex_reborn))
			.inputItems(
				new OreDictStack(CU.plate(), 16),
				new OreDictStack(STEEL.plateCast(), 8),
				new OreDictStack(STEEL.shell(), 6),
				new OreDictStack(CU.plateCast(), 8),
				new ComparableStack(com.hbm.items.ModItems.motor_bismuth, 4),
				new OreDictStack(W.bolt(), 32)));

		MakeNTMGreatAgain.LOG.info("Added assembly recipe: heatex reborn");
	}

	/** 无序合成：FFI香烟 + 迪伽马粒子胶囊 = DRX香烟 */
	private static void addCigaretteDrxRecipe() {
		GameRegistry.addShapelessRecipe(
			new ItemStack(ModItems.cigarette_drx, 8),                    // 输出：你的 DRX 香烟
			new ItemStack(com.hbm.items.ModItems.cigarette, 1),          // 输入1：HBM 的 FFI 牌香烟
			new ItemStack(com.hbm.items.ModItems.particle_digamma));  // 输入2：HBM 的迪伽马粒子胶囊
	}
	/**
	 * 八件高级合金装备的工作台配方。
	 */
	private static void addAlloyRebornGearRecipes() {
		ItemStack alloy = new ItemStack(ModItems.ingot_advanced_alloy);
		ItemStack stick = new ItemStack(Items.stick);
		ItemStack steelblades = new ItemStack(com.hbm.items.ModItems.blades_steel);
		ItemStack alloyblades = new ItemStack(ModItems.blades_alloy_reborn);
		ItemStack alloyblock = new ItemStack(ModBlocks.block_advanced_alloy);
		ItemStack cobalttungsten = new ItemStack(ModItems.powder_cobalt_tungsten_ingot);

		// 史诗级无限
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_water_mk3),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(com.hbm.items.ModItems.inf_water_mk2), '#', com.hbm.blocks.ModBlocks.machine_fluidtank, 'B', new ItemStack(com.hbm.items.ModItems.ingot_ferrouranium));
		
		// 小型销毁：
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy),
			"XXX", "B#B", "XXX",
			'X', new ItemStack(com.hbm.items.ModItems.plate_aluminium), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor));
		// 大型销毁：
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy_mk2),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(ModItems.inf_destroy), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor));
		// mk3销毁：
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.inf_destroy_mk3),
			"XBX", "B#B", "XBX",
			'X', new ItemStack(ModItems.inf_destroy_mk2), '#', com.hbm.blocks.ModBlocks.machine_drain, 'B', new ItemStack(com.hbm.items.ModItems.motor_desh));

		// 9 个高级合金锭 → 1 个高级合金块
		GameRegistry.addShapedRecipe(new ItemStack(ModBlocks.block_advanced_alloy),
			"AAA", "AAA", "AAA",
			'A', alloy);

		// 1 个高级合金块 → 9 个高级合金锭（可逆，参照原版矿物块惯例）
		GameRegistry.addShapelessRecipe(new ItemStack(ModItems.ingot_advanced_alloy, 9),
			new ItemStack(ModBlocks.block_advanced_alloy));

		// ---- 刀片 ----
		// 图案与原版钻石剑一致，来源 RecipesWeapons.java 第 10 行： "X","X","#"
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.blades_alloy_reborn),
			" X ", "X#X", " X ",
			'X', alloy, '#', steelblades);
		
		// ---- 剑 ----
		// 图案与原版钻石剑一致，来源 RecipesWeapons.java 第 10 行： "X","X","#"
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.alloy_sword_reborn),
				"X", "X", "#",
				'X', alloy, '#', stick);

		// ---- 碳化钨工具四件 ----
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
		// ---- 工具四件 ----
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

		// ---- 高级护甲四件 ----
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

		// ---- 碳化钨护甲四件 ----
		ItemStack tungsten_helmet = new ItemStack(ModItems.cobalt_tungsten_helmet);
		plate.addEnchantment(Enchantment.protection, 2);
		plate.addEnchantment(Enchantment.thorns, 2);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_helmet),
			"XXX", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_plate = new ItemStack(ModItems.cobalt_tungsten_plate);
		plate.addEnchantment(Enchantment.protection, 2);
		plate.addEnchantment(Enchantment.thorns, 2);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_plate),
			"X X", "XXX", "XXX",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_legs = new ItemStack(ModItems.cobalt_tungsten_legs);
		plate.addEnchantment(Enchantment.protection, 2);
		plate.addEnchantment(Enchantment.thorns, 2);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_legs),
			"XXX", "X X", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		ItemStack tungsten_boots = new ItemStack(ModItems.cobalt_tungsten_boots);
		plate.addEnchantment(Enchantment.protection, 2);
		plate.addEnchantment(Enchantment.thorns, 2);
		GameRegistry.addShapedRecipe(new ItemStack(ModItems.cobalt_tungsten_boots),
			"X X", "X X",
			'X', new ItemStack(ModItems.powder_cobalt_tungsten_ingot));

		MakeNTMGreatAgain.LOG.info("Added crafting recipes for 8 alloy reborn gear items.");
	}

	// =====================================================================================
	//  铅锌冶炼 · ISP 法提镉
	// =====================================================================================

	/**
	 * 铅锌线的化工厂部分：浮选 → 焙烧 → 浸出 → 镉电积 → 锌电积 → 制酸，
	 */
	public static void addLeadZincChemicalRecipes() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType cdLiq = addonFluid(ModFluids.CADMIUM_LIQUOR);
		FluidType znSol = addonFluid(ModFluids.ZINC_SULFATE);
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		// 三种流体缺一条整条线就跑不通，宁可整段跳过，也不要在存档里留半条链
		if(so2 == Fluids.NONE || cdLiq == Fluids.NONE || znSol == Fluids.NONE || na2so3 == Fluids.NONE) return;

		// ① 浮选：抑锌浮铅。脉石进尾矿，硫化物以硫粉形式带出来。
		//    1 矿 → 1 精矿：矿方块现在必须精准采集才拿得到，再按 2:1 卡一道就太狠了。
		if(!hasChemRecipe("makentmgreatagain.leadzinc.float")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.float")
				.setupNamed(600, 2_500)
				.inputItems(new OreDictStack("oreLeadzinc", 1))
				.inputFluids(new FluidStack(Fluids.WATER, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.leadzinc_concentrate, 1),
							 new ItemStack(com.hbm.items.ModItems.sulfur, 1)));
		}

		// ② 氧化焙烧：PbS/ZnS/CdS 一并变氧化物，硫全进烟气
		if(!hasChemRecipe("makentmgreatagain.leadzinc.roast")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.roast")
				.setupNamed(600, 3_000)
				.inputItems(new ComparableStack(ModItems.leadzinc_concentrate, 2))
				.inputFluids(new FluidStack(Fluids.OXYGEN, 500))
				.outputItems(new ItemStack(ModItems.leadzinc_sinter, 2))
				.outputFluids(new FluidStack(so2, 600)));
		}

		// ④ 浸出：蓝粉加硫酸，锌镉一起溶，铅留渣
		if(!hasChemRecipe("makentmgreatagain.leadzinc.leach")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.leach")
				.setupNamed(500, 2_500)
				.inputItems(new ComparableStack(ModItems.bp_blue_powder, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500))
				.outputFluids(new FluidStack(cdLiq, 500), new FluidStack(znSol, 500)));
		}

		// ⑤ 镉电积：阴极析镉，阳极再生硫酸
		if(!hasChemRecipe("makentmgreatagain.leadzinc.cadmiumCell")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.cadmiumCell")
				.setupNamed(600, 4_000)
				.inputFluids(new FluidStack(cdLiq, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_cadmium, 1))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 200)));
		}

		// ⑥ 锌电积：同一套流程的另一半
		if(!hasChemRecipe("makentmgreatagain.leadzinc.zincCell")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.zincCell")
				.setupNamed(600, 4_000)
				.inputFluids(new FluidStack(znSol, 500))
				.outputItems(new ItemStack(ModItems.powder_zinc_isp, 1))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 200)));
		}

		// ⑦ 接触法制酸（极简版）

		if(!hasChemRecipe("makentmgreatagain.leadzinc.acidPlant")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.acidPlant")
				.setupNamed(200, 1_200)
				.inputFluids(new FluidStack(so2, 1_000), new FluidStack(Fluids.OXYGEN, 500), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 1_600)));
		}

		// ⑧ 锌粉置换快线：产率是电积的两倍，代价是吃掉一份锌
		if(!hasChemRecipe("makentmgreatagain.leadzinc.cementation")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.cementation")
				.setupNamed(400, 1_500)
				.inputItems(new ComparableStack(ModItems.powder_zinc_isp, 1))
				.inputFluids(new FluidStack(cdLiq, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_cadmium, 2))
				.outputFluids(new FluidStack(znSol, 300)));
		}

		// ⑨ 前置：亚硫酸钠溶液 —— 脱砷真正缺的就是这个还原剂。
		//    反应：SO2 + 2NaOH → Na2SO3 + H2O，化工厂里就是"碱液吸收烟气"。
		//    为什么不做成直接吃 SO2 气体：气体只要有硫就能烧，太便宜；而这里的碱液要
		//    海水 → 浓缩海水 → 电解（1000 浓缩海水 → 1000 氯气 + 400 碱液）才拿得到。
		//    也就是每做一批亚硫酸钠，玩家都得先把整条氯碱线转起来。
		if(!hasChemRecipe("makentmgreatagain.leadzinc.sulphite")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.sulphite")
				.setupNamed(600, 2_500)
				.inputFluids(new FluidStack(so2, 1_000), new FluidStack(Fluids.LYE, 400), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(na2so3, 1_000)));
		}

		// ⑩ 脱砷浸出：酸浸把锌镉砷一起打进溶液，亚硫酸钠把五价砷还原成三价，析出白砷（As2O3）。

		if(!hasChemRecipe("makentmgreatagain.leadzinc.dearsenify")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.leadzinc.dearsenify")
				.setupNamed(800, 4_000)
				.inputItems(new ComparableStack(ModItems.bp_blue_powder, 2))
				.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500), new FluidStack(na2so3, 500))
				.outputItems(new ItemStack(ModItems.arsenic_trioxide, 3))
				.outputFluids(new FluidStack(cdLiq, 500), new FluidStack(znSol, 400)));
		}

		MakeNTMGreatAgain.LOG.info("Added lead-zinc (ISP) chemical plant recipes: float/roast/leach/Cd cell/Zn cell/acid/cementation/sulphite/dearsenify");
	}

	/**
	 * SO2 的两条新上游（复式炼焦炉焙烧、热解炉氧化）与硫酸锌液的电解精炼。
	 */
	public static void addSulfurDioxideUpstreamRecipes() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType znSol = addonFluid(ModFluids.ZINC_SULFATE);
		if(so2 == Fluids.NONE || znSol == Fluids.NONE) return;

		ItemStack sulfur = new ItemStack(com.hbm.items.ModItems.sulfur);

		// ① 复式炼焦炉：硫磺在炉膛里焙烧，硫全部进烟气，不留固体产物。
		//    这台炉子每次只消耗 1 个物品（TileEntityFurnaceCombination 的 decrStackSize(0,1)），
		//    所以配比就是 1 硫 → 1400 SO2，写 2 也没用。
		//    注意 ComparableStack.hashCode() 把 stacksize 算进去了，而 getOutput() 用 stacksize=1 查表，
		//    所以这里的 key 必须是 1 个，写成 new ComparableStack(sulfur, 2) 会静默查不到。
		ComparableStack sulfurKey = new ComparableStack(sulfur);
		if(!CombinationRecipes.recipes.containsKey(sulfurKey)) {
			CombinationRecipes.recipes.put(sulfurKey, new Pair((ItemStack) null, new FluidStack(so2, 1_400)));
		}

		// ② 热解炉：隔绝空气加热，但要通氧才能把硫氧化成 SO2。
		//    配方表是 1 物品 + 1 流体进、1 物品 + 1 流体出，这条正好占两进一出。
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
			PyroOvenRecipes.recipes.add(new PyroOvenRecipes.PyroOvenRecipe(200)
				.in(new ComparableStack(com.hbm.items.ModItems.sulfur, 2))
				.in(new FluidStack(Fluids.OXYGEN, 1_000))
				.out(new FluidStack(so2, 3_000)));
		}

		// ③ 硫酸锌液电解：阴极析锌，阳极再生硫酸。
		//    电解机的流体配方表是「一种流体一条」，用 containsKey 判重即可（HashMap，重复 put 也是幂等）。
		//    构造器有 (amount, out1, out2, ItemStack...) 和 (amount, out1, out2, int duration, ItemStack...)
		//    两个重载，这里用带 duration 的那个。
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
	 * 铅锌线的鼓风炉段：① 烧结块 + 焦炭 → 铅锭 + 蓝粉；② 白砷 + 焦炭 → 砷粒。
	 */
	public static void addLeadZincBlastRecipe() {

		// ① 烧结块还原熔炼。两条配方各自判重：以前是一进方法就 return，
		//    加了第二条之后那样写会让第二条永远进不来。
		if(!hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.leadzinc.shaft")) {
			BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.leadzinc.shaft")
				.setNamed()
				.setDuration(1_200)
				.inputItems(new ComparableStack(ModItems.leadzinc_sinter, 2), new OreDictStack(ANY_COKE.gem()))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_lead, 3),
							 new ItemStack(ModItems.bp_blue_powder, 2)));
		}

		// ② 白砷碳热还原：碳把三氧化二砷还原成单质砷，砷以蒸气逸出后冷凝成粒。
		//    焦炭用 ANY_COKE.gem()，和上面那条保持同一套矿辞，GT 的焦炭也能进。
		if(!hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.leadzinc.arsenicReduction")) {
			BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.leadzinc.arsenicReduction")
				.setNamed()
				.setDuration(800)
				.inputItems(new ComparableStack(ModItems.arsenic_trioxide, 3), new OreDictStack(ANY_COKE.gem()))
				.outputItems(new ItemStack(com.hbm.items.ModItems.nugget_arsenic, 2)));
		}

		// ③ 砷浸出渣还原焙烧：渣的主要成分是氧化铁和砷酸铁，加碳后铁被还原、

		if(ModItems.arsenical_residue != null
				&& !hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.gold.residueRoast")) {
			BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.gold.residueRoast")
				.setNamed()
				.setDuration(900)
				.inputItems(new ComparableStack(ModItems.arsenical_residue, 2), new OreDictStack(ANY_COKE.gem()))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_iron, 3),
							 new ItemStack(ModItems.arsenic_trioxide, 1)));
		}

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipes: lead-zinc sinter -> lead + blue powder; arsenic trioxide + coke -> arsenic nuggets; arsenical residue -> iron + arsenic trioxide");
	}

	/**
	 * 氯碱线的氯气出口：亚硫酸钠脱氯。
	 */
	public static void addChlorAlkaliDechlorRecipe() {
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType hcl = HBMCompat.hcl();
		if(na2so3 == Fluids.NONE || hcl == Fluids.NONE) return;
		if(ModItems.powder_sodium_sulfate == null) return;

		if(!hasChemRecipe("makentmgreatagain.chloralkali.dechlor")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.chloralkali.dechlor")
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

	/**
	 * 硫代硫酸盐浸金整条链。
	 */
	public static void addThiosulfateGoldRecipes() {
		FluidType thio = addonFluid(ModFluids.SODIUM_THIOSULFATE);
		FluidType auThio = addonFluid(ModFluids.GOLD_THIOSULFATE);
		FluidType na2so3 = addonFluid(ModFluids.SODIUM_SULPHITE);
		FluidType hcl = HBMCompat.hcl();
		// 缺任何一环就整段跳过，不要在存档里留半条链
		if(thio == Fluids.NONE || auThio == Fluids.NONE) return;
		if(ModItems.arsenical_residue == null || ModBlocks.ore_refractory_gold == null) return;

		// ① 硫代硫酸钠合成：Na2SO3 + S → Na2S2O3。
		//    现实是 80~100 ℃ 微沸 2~4 小时、pH 6~8、收率八成，这里给 10% 损耗。
		if(na2so3 != Fluids.NONE && !hasChemRecipe("makentmgreatagain.gold.thiosulfate")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.gold.thiosulfate")
				.setupNamed(600, 3_000)
				.inputItems(new OreDictStack(S.dust(), 1))
				.inputFluids(new FluidStack(na2so3, 500))
				.outputFluids(new FluidStack(thio, 550)));
		}

		// ② 浸出：硫代硫酸根把金氧化成络阴离子。
		if(!hasChemRecipe("makentmgreatagain.gold.leach")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.gold.leach")
				.setupNamed(2_400, 3_000)
				.inputItems(new ComparableStack(ModBlocks.ore_refractory_gold, 1), new OreDictStack(CU.dust(), 1))
				.inputFluids(new FluidStack(thio, 1_000), new FluidStack(Fluids.OXYGEN, 500))
				.outputItems(new ItemStack(ModItems.arsenical_residue, 1),
							 new ItemStack(com.hbm.items.ModItems.sulfur, 2))
				.outputFluids(new FluidStack(auThio, 1_000)));
		}

		// ③ 锌粉置换：Merrill-Crowe 法，和 ⑧ 镉置换同机理。

		if(!hasChemRecipe("makentmgreatagain.gold.cementation")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.gold.cementation")
				.setupNamed(600, 2_500)
				.inputItems(new ComparableStack(ModItems.powder_zinc_isp, 1))
				.inputFluids(new FluidStack(auThio, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_gold, 4),
							 new ItemStack(com.hbm.items.ModItems.nugget_bismuth, 1))
				.outputFluids(new FluidStack(thio, 700)));
		}

		// ④ 强效脱氯：Na2S2O3 + 4Cl2 + 5H2O → 2H2SO4 + 2HCl。
		
		if(hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.chloralkali.thiosulfateDechlor")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.chloralkali.thiosulfateDechlor")
				.setupNamed(700, 3_500)
				.inputFluids(new FluidStack(thio, 500), new FluidStack(Fluids.CHLORINE, 1_000), new FluidStack(Fluids.WATER, 500))
				.outputFluids(new FluidStack(Fluids.SULFURIC_ACID, 400), new FluidStack(hcl, 400)));
		}

		MakeNTMGreatAgain.LOG.info("Added thiosulphate gold recipes: synth/leach/cementation/thiosulphate-dechlor");
	}

	/**
	 * 含砷金矿直接进熔炉：1 个金粒。

	 */
	public static void addRefractoryGoldSmelting() {
		if(ModBlocks.ore_refractory_gold == null) return;
		GameRegistry.addSmelting(ModBlocks.ore_refractory_gold, new ItemStack(Items.gold_nugget, 1), 0.1F);
	}


	// =====================================================================================
	//  硫与锌的出口：复式炼焦炉 / 热解炉烧硫、电解机提锌

	/**
	 * 复式炼焦炉（Combination Furnace）烧硫磺：硫粉 1 → 二氧化硫 1400。
	 */
	public static void addSulfurCombinationRecipe() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		if(so2 == Fluids.NONE) return;

		// 表里已有硫粉作输入的配方就不动（reload 时整张表会先被清空，正常这里是空的）
		if(CombinationRecipes.recipes.containsKey(S.dust())) return;

		CompatRecipeRegistry.registerCombination(
			new OreDictStack(S.dust(), 1),
			null,
			new FluidStack(so2, 1_400));

		MakeNTMGreatAgain.LOG.info("Added combination furnace recipe: 1 sulfur dust -> 1400 SO2");
	}

	/**
	 * 热解炉（Pyro Oven）烧硫磺：硫粉 2 + 氧 500 → 二氧化硫 3000。
	 */
	public static void addSulfurPyroRecipe() {
		FluidType so2 = addonFluid(ModFluids.SULFUR_DIOXIDE);
		if(so2 == Fluids.NONE) return;

		// 热解炉的配方表是 List 不是 Map，只能遍历查重
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

	/**
	 * 电解机（Electrolyser）锌电积：硫酸锌液 750 → 硫酸 300 + 锌粉 2。
	 */
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

	// =====================================================================================
	//  海水处理路线

	/** 取附属流体；没注册成功返回 NONE，调用方据此跳过整条支线。 */
	private static FluidType addonFluid(ModFluids.FluidDef def) {
		if(def == null || def.type == null) return Fluids.NONE;
		return def.type;
	}
	/** 是否已经注册过这条化工厂配方。 */
	private static boolean hasChemRecipe(String name) {
		return hasGenericRecipe(ChemicalPlantRecipes.INSTANCE, name);
	}

	/** 批次1 · 浓缩：海水熬成浓缩海水；副线走汞阴极，只出浓碱不出来氯气。 */
	public static void addSeawaterConcentrationRecipes() {
		FluidType sea = addonFluid(ModFluids.SEA_WATER);
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(sea == Fluids.NONE || conc == Fluids.NONE || brine == Fluids.NONE) return;

		// 海水 2000 → 浓缩海水 1000 + 水 900
		if(!hasChemRecipe("makentmgreatagain.sea.concentrate")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.concentrate")
				.setup(600, 2_000)
				.inputFluids(new FluidStack(sea, 2_000))
				.outputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.WATER, 900)));
		}

		// 浓缩海水 1000 + 水银 100 → 碱液 800 + 盐水 200（汞阴极法，不出氯气）
		if(!hasChemRecipe("makentmgreatagain.sea.mercurycell")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.mercurycell")
				.setup(600, 3_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.MERCURY, 100))
				.outputFluids(new FluidStack(Fluids.LYE, 800), new FluidStack(brine, 200)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: concentrate + mercury cell");
	}

	/** 批次1 · 氯碱：浓缩海水进电解机，出氯气和碱液。 */
	public static void addSaltSeaWaterElectrolyserRecipe() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		if(conc == Fluids.NONE) return;
		if(ElectrolyserFluidRecipes.getRecipe(conc) != null) return;

		// 1000 浓缩海水 → 1000 氯气 + 400 碱液
		ElectrolyserFluidRecipes.recipes.put(conc,
			new ElectrolyserFluidRecipes.ElectrolysisRecipe(1000,
				new FluidStack(Fluids.CHLORINE, 1_000),
				new FluidStack(Fluids.LYE, 400)));

		MakeNTMGreatAgain.LOG.info("Added electrolyser recipe: salt sea water -> chlorine + lye");
	}

	/** 批次2 · 分盐与提溴。 */
	public static void addSeawaterSaltRecipes() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		if(conc == Fluids.NONE || brine == Fluids.NONE) return;

		// 浓缩海水 1000 + 硫酸 300 → 氯化钾 350 + 氯化钙 300（两者电解配方 HBM 自带）
		if(!hasChemRecipe("makentmgreatagain.sea.saltSplit")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.saltSplit")
				.setup(500, 4_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 300))
				.outputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 350), new FluidStack(Fluids.CALCIUM_CHLORIDE, 300)));
		}

		// 氯化钾 300 + 硝酸 200 → 硝石 x2 + 盐酸 250（硝石+硫+煤 = 火药）
		if(hcl != Fluids.NONE && !hasChemRecipe("makentmgreatagain.sea.niter")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.niter")
				.setup(400, 3_000)
				.inputFluids(new FluidStack(Fluids.POTASSIUM_CHLORIDE, 300), new FluidStack(Fluids.NITRIC_ACID, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.niter, 2))
				.outputFluids(new FluidStack(hcl, 250)));
		}

		// 浓缩海水 1000 + 氯气 300 + 硫酸 200 → 溴粉 x2 + 盐水 700
		if(!hasChemRecipe("makentmgreatagain.sea.bromine")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.bromine")
				.setup(500, 5_000)
				.inputFluids(new FluidStack(conc, 1_000), new FluidStack(Fluids.CHLORINE, 300), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_bromine, 2))
				.outputFluids(new FluidStack(brine, 700)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: salt split + niter + bromine");
	}

	/** 批次3 · 石膏：不入浓缩罐，海水直接加硫酸。 */
	public static void addSeawaterGypsumRecipes() {
		FluidType sea = addonFluid(ModFluids.SEA_WATER);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		FluidType hcl = HBMCompat.hcl();
		if(sea == Fluids.NONE || hcl == Fluids.NONE) return;

		// 海水 1000 + 硫酸 200 → 石膏 x2 + 盐酸 400
		if(!hasChemRecipe("makentmgreatagain.sea.gypsum")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.gypsum")
				.setup(500, 4_000)
				.inputFluids(new FluidStack(sea, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.gypsum, 2))
				.outputFluids(new FluidStack(hcl, 400)));
		}

		// 水泥 4 + 石膏 1 + 砂 8 + 水 2000 → 混凝土 20000（石膏当缓凝剂，出料比原版多）
		if(!hasChemRecipe("makentmgreatagain.sea.concrete")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.concrete")
				.setup(150, 100)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_cement, 4),
							new ComparableStack(ModItems.gypsum, 1),
							new OreDictStack(KEY_SAND, 8))
				.inputFluids(new FluidStack(Fluids.WATER, 2_000))
				.outputFluids(new FluidStack(Fluids.CONCRETE, 20_000)));
		}

		// 盐水 1000 + 石灰石粉 1 → 水 800 + 钙溶液 100（中和，省得直接排污）
		if(brine != Fluids.NONE && !hasChemRecipe("makentmgreatagain.sea.neutralize")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.neutralize")
				.setup(300, 2_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.powder_limestone, 1))
				.inputFluids(new FluidStack(brine, 1_000))
				.outputFluids(new FluidStack(Fluids.WATER, 800), new FluidStack(Fluids.CALCIUM_SOLUTION, 100)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: gypsum + concrete + neutralise");
	}

	/** 批次3 · 石膏还原：高炉里配焦炭，出硫和钙粉。 */
	public static void addGypsumBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.sea.gypsumToSulfur")) return;

		// 石膏 x2 + 焦炭 x1 → 硫 x2 + 钙粉 x1
		BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.sea.gypsumToSulfur")
			.setDuration(400)
			.inputItems(new ComparableStack(ModItems.gypsum, 2), new OreDictStack(ANY_COKE.gem()))
			.outputItems(new ItemStack(com.hbm.items.ModItems.sulfur, 2),
						 new ItemStack(com.hbm.items.ModItems.powder_calcium, 1)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: gypsum + coke -> sulfur + calcium powder");
	}
	
	//  石膏

	/** A · 工作台：石膏磨细当白色颜料。原版白色染料与骨粉是同一个物品，等于绕开了刷骷髅。 */
	public static void addGypsumDyeRecipe() {
		// 石膏 x2 → 白色染料 x2
		GameRegistry.addShapelessRecipe(new ItemStack(Items.dye, 2, 15), ModItems.gypsum, ModItems.gypsum);

		MakeNTMGreatAgain.LOG.info("Added crafting recipe: gypsum x2 -> white dye x2");
	}

	/** B · 化工厂：石膏拌骨粉做土壤改良剂。石膏调 pH 把磷固定住，骨粉才不白撒。 */
	public static void addGypsumBoneMealRecipe() {
		if(!hasChemRecipe("makentmgreatagain.gypsum.bonemeal")) {
			// 石膏 x1 + 骨粉 x1 + 水 500 → 骨粉 x5
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.gypsum.bonemeal")
				.setup(300, 2_000)
				.inputItems(new ComparableStack(ModItems.gypsum, 1), new ComparableStack(Items.dye, 1, 15))
				.inputFluids(new FluidStack(Fluids.WATER, 500))
				.outputItems(new ItemStack(Items.dye, 5, 15)));
		}

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: gypsum + bone meal -> bone meal x5");
	}

	/** C · 高炉：石膏掺进黏土烧砖，当助熔剂降低烧成温度，同样的黏土多出一块砖。 */
	public static void addGypsumBrickBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.gypsum.brick")) return;

		// 黏土球 x2 + 石膏 x1 → 砖 x3（原本 2 黏土只能烧 2 砖）
		BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.gypsum.brick")
			.setDuration(400)
			.inputItems(new ComparableStack(Items.clay_ball, 2), new ComparableStack(ModItems.gypsum, 1))
			.outputItems(new ItemStack(Items.brick, 3)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: clay + gypsum -> brick x3");
	}

	/** 批次4 · 吸附：浓缩海水过树脂，富集成含矿液，再分出锂、铀、硼。 */
	public static void addSeawaterAdsorptionRecipes() {
		FluidType conc = addonFluid(ModFluids.SALT_SEA_WATER);
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(conc == Fluids.NONE || liquor == Fluids.NONE || brine == Fluids.NONE) return;

		// 浓缩海水 4000 + 树脂球 1 → 含矿液 500 + 盐水 3400
		if(!hasChemRecipe("makentmgreatagain.sea.adsorb")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.adsorb")
				.setup(800, 8_000)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.ball_resin, 1))
				.inputFluids(new FluidStack(conc, 4_000))
				.outputFluids(new FluidStack(liquor, 500), new FluidStack(brine, 3_400)));
		}

		// 含矿液 500 + 碱液 400 → 锂粉 x2 + 盐水 200
		if(!hasChemRecipe("makentmgreatagain.sea.lithium")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.lithium")
				.setup(600, 6_000)
				.inputFluids(new FluidStack(liquor, 500), new FluidStack(Fluids.LYE, 400))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_lithium, 2))
				.outputFluids(new FluidStack(brine, 200)));
		}

		// 含矿液 1000 + 硝酸 500 + 煤油 500 → 铀粉 x3
		if(!hasChemRecipe("makentmgreatagain.sea.uranium")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.uranium")
				.setup(1_200, 10_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.NITRIC_ACID, 500), new FluidStack(Fluids.KEROSENE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 3)));
		}

		// 含矿液 1000 + 硫酸 300 → 硼粉 x1 + 盐水 600
		if(!hasChemRecipe("makentmgreatagain.sea.boron")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.boron")
				.setup(900, 8_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_boron, 1))
				.outputFluids(new FluidStack(brine, 600)));
		}

		MakeNTMGreatAgain.LOG.info("Added seawater recipes: adsorb + lithium + uranium + boron");
	}

	/**
	 * 批次4+ · PUREX：六出物品/一出流体
	 */
	public static void addSeawaterPUREXRecipe() {
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		if(liquor == Fluids.NONE) return;
		if(hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.sea.purex")) return;

		// 含矿液 1000 + 硝酸 500 + 煤油 500 → 铀粉 x6 + 硝酸 200
		PUREXRecipes.INSTANCE.register((PUREXRecipe) new PUREXRecipe("makentmgreatagain.sea.purex")
			.setup(1_200, 10_000)
			.inputFluids(new FluidStack(liquor, 1_000),
						 new FluidStack(Fluids.NITRIC_ACID, 500),
						 new FluidStack(Fluids.KEROSENE, 500))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 6))
			.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 200)));

		MakeNTMGreatAgain.LOG.info("Added PUREX recipe: ore liquor -> uranium powder x6 + nitric acid recycle");
	}

	// =====================================================================================
	//  锶线
	/**
	 * 锶 ① · 化工厂沉淀：盐水 6000 + 硫酸 400 → 天青石 x1 + 石膏 x2。
	 */
	public static void addStrontiumChemicalRecipe() {
		FluidType brine = addonFluid(ModFluids.SALT_WATER);
		if(brine == Fluids.NONE) return;
		if(hasChemRecipe("makentmgreatagain.sea.strontium")) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.strontium")
			.setup(600, 5_000)
			.inputFluids(new FluidStack(brine, 6_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
			.outputItems(new ItemStack(ModItems.celestite, 1),
						 new ItemStack(ModItems.gypsum, 2)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: brine + sulfuric acid -> celestite + gypsum");
	}


	public static void addStrontiumSmeltingRecipe() {
		GameRegistry.addSmelting(ModItems.celestite, new ItemStack(ModItems.strontium_oxide, 1), 0.35F);

		MakeNTMGreatAgain.LOG.info("Added furnace recipe: celestite -> strontium oxide");
	}

	public static void addStrontiumBlastRecipe() {
		if(hasGenericRecipe(BlastFurnaceRecipesNT.INSTANCE, "makentmgreatagain.sea.strontiumReduce")) return;

		BlastFurnaceRecipesNT.INSTANCE.register((GenericRecipeNoPower) new GenericRecipeNoPower("makentmgreatagain.sea.strontiumReduce")
			.setDuration(400)
			.inputItems(new ComparableStack(ModItems.strontium_oxide, 3),
						new ComparableStack(com.hbm.items.ModItems.powder_aluminium, 2))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_strontium, 3),
						 new ItemStack(com.hbm.items.ModItems.ingot_raw, 1, Mats.MAT_SLAG.id)));

		MakeNTMGreatAgain.LOG.info("Added blast furnace recipe: strontium oxide + aluminium -> strontium powder + slag");
	}

	/**
	 * 稀土 · 化工厂酸浸：稀土矿碎块 x6 + 硫酸 500 → 钕粉(tiny) x3
	 */
	public static void addRareEarthNeodymiumRecipe() {
		if(hasChemRecipe("makentmgreatagain.sea.rareEarthNd")) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.sea.rareEarthNd")
			.setup(600, 6_000)
			.inputItems(new ComparableStack(com.hbm.items.ModItems.chunk_ore, 6, EnumChunkType.RARE))
			.inputFluids(new FluidStack(Fluids.SULFURIC_ACID, 500))
			.outputItems(new ItemStack(com.hbm.items.ModItems.powder_neodymium_tiny, 3)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: rare earth chunks + sulfuric acid -> neodymium x1 ingot");
	}

	/**
	 * 天然气提氦 
	 */
	public static void addGasHeliumRecipe() {
		if(hasChemRecipe("makentmgreatagain.gas.helium")) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.gas.helium")
			.setupNamed(400, 5_000)
			.setIcon(com.hbm.items.ModItems.gas_full, Fluids.HELIUM4.getID())
			.inputFluids(new FluidStack(Fluids.GAS, 20_000), new FluidStack(Fluids.IONGEL, 500))
			.outputFluids(new FluidStack(Fluids.HELIUM4, 100), new FluidStack(Fluids.CARBONDIOXIDE, 1_000)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: natural gas -> helium-4 + carbon dioxide");
	}

	// =====================================================================================
	//  石油三期：八种新流体的合成路线（详见 docs/06-石油三期配方方案.md）
	//  一期基础炼油 → 二期催化裂化 → 三期真空 + 加氢。
	//  528 开启时硝基甲烷、四氧化二氮的原料改成加压供料（1 PU），必须走管道，不能手动灌桶。
	// =====================================================================================
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

		// 一期 · 甘油：葵花油酯交换，副产生物燃料
		if(!hasChemRecipe("makentmgreatagain.petro.glycerol")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.glycerol")
				.setup(250, 400)
				.inputFluids(new FluidStack(Fluids.SUNFLOWEROIL, 1_000), new FluidStack(Fluids.ETHANOL, 300), new FluidStack(Fluids.LYE, 200))
				.outputFluids(new FluidStack(glycerol, 500), new FluidStack(Fluids.BIOFUEL, 800)));
		}

		// 一期 · 甘油（鱼油变体）：多不饱和、皂化慢，收率低一档
		if(!hasChemRecipe("makentmgreatagain.petro.glycerol.fish")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.glycerol.fish")
				.setup(250, 400)
				.inputFluids(new FluidStack(Fluids.FISHOIL, 1_000), new FluidStack(Fluids.ETHANOL, 300), new FluidStack(Fluids.LYE, 200))
				.outputFluids(new FluidStack(glycerol, 400), new FluidStack(Fluids.BIOFUEL, 700)));
		}

		// 一期 · 硝基苯：煤焦油里的苯 + 混酸，苯含量低所以只出 600
		if(!hasChemRecipe("makentmgreatagain.petro.nitrobenzene.coal")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.nitrobenzene.coal")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.COALOIL, 1_000), new FluidStack(Fluids.NITRIC_ACID, 600), new FluidStack(Fluids.SULFURIC_ACID, 600))
				.outputFluids(new FluidStack(nitrobenzene, 600), new FluidStack(Fluids.WATER, 800)));
		}

		// 一期 · 硝基甲烷：甲烷气相硝化，528 开启时天然气需加压供料
		if(!hasChemRecipe("makentmgreatagain.petro.nitromethane")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.nitromethane")
				.setup(400, 1_000)
				.inputFluids(new FluidStack(Fluids.GAS, 1_000, pu), new FluidStack(Fluids.NITRIC_ACID, 600))
				.outputFluids(new FluidStack(nitromethane, 500), new FluidStack(Fluids.WATER, 700), new FluidStack(Fluids.CARBONDIOXIDE, 200)));
		}

		// 二期 · 硝基苯：芳烃路线，苯浓度高，同样的酸量出 800
		if(!hasChemRecipe("makentmgreatagain.petro.nitrobenzene.aromatic")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.nitrobenzene.aromatic")
				.setup(280, 800)
				.inputFluids(new FluidStack(Fluids.AROMATICS, 1_000), new FluidStack(Fluids.NITRIC_ACID, 500), new FluidStack(Fluids.SULFURIC_ACID, 500))
				.outputFluids(new FluidStack(nitrobenzene, 800), new FluidStack(Fluids.WATER, 600)));
		}

		// 二期 · 二硝基苯：二次硝化，发烟硝酸用双倍酸量代替，全程最贵的一条
		if(!hasChemRecipe("makentmgreatagain.petro.dinitrobenzene")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.dinitrobenzene")
				.setup(450, 1_000)
				.inputFluids(new FluidStack(nitrobenzene, 1_000), new FluidStack(Fluids.NITRIC_ACID, 1_200), new FluidStack(Fluids.SULFURIC_ACID, 1_200))
				.outputFluids(new FluidStack(dinitrobenzene, 800), new FluidStack(Fluids.WATER, 1_000)));
		}

		// 二期 · 航空汽油（调和）：裂化芳烃凑辛烷值
		if(!hasChemRecipe("makentmgreatagain.petro.avgas.blend")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.avgas.blend")
				.setup(150, 600)
				.inputFluids(new FluidStack(Fluids.GASOLINE, 800), new FluidStack(Fluids.AROMATICS, 400), new FluidStack(Fluids.XYLENE, 200))
				.outputFluids(new FluidStack(avgas, 1_200)));
		}

		// 二期 · 硝化甘油：甘油 + 混酸，比原版"石油气直接硝化"更贵但更像真的
		if(!hasChemRecipe("makentmgreatagain.petro.nitroglycerin")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.nitroglycerin")
				.setup(200, 700)
				.inputFluids(new FluidStack(glycerol, 800), new FluidStack(Fluids.NITRIC_ACID, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
				.outputFluids(new FluidStack(Fluids.NITROGLYCERIN, 1_000), new FluidStack(Fluids.WATER, 400)));
		}

		// 三期 · 硝化煤油：只吃减压煤油，硝酸量刻意压得很低，是精制不是硝成炸药
		if(!hasChemRecipe("makentmgreatagain.petro.tbpkero")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.tbpkero")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.KEROSENE, 1_000), new FluidStack(Fluids.NITRIC_ACID, 200), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputFluids(new FluidStack(tbpkero, 1_000), new FluidStack(Fluids.WATER, 200)));
		}

		// 三期 · 四氧化二氮：硝酸热分解 + 氧化，528 开启时硝酸需加压
		if(!hasChemRecipe("makentmgreatagain.petro.n2o4")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.n2o4")
				.setup(350, 1_000)
				.inputFluids(new FluidStack(Fluids.NITRIC_ACID, 1_000, pu), new FluidStack(Fluids.OXYGEN, 700))
				.outputFluids(new FluidStack(n2o4, 700), new FluidStack(Fluids.WATER, 500)));
		}

		// 三期 · 航空汽油（重整）：真空塔直出的重整油，收率满、少用汽油
		if(!hasChemRecipe("makentmgreatagain.petro.avgas.reform")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.avgas.reform")
				.setup(180, 800)
				.inputFluids(new FluidStack(Fluids.GASOLINE, 500), new FluidStack(Fluids.REFORMATE, 600), new FluidStack(Fluids.AROMATICS, 200))
				.outputFluids(new FluidStack(avgas, 1_400)));
		}

		// 甲醇合成：合成气补氢调比例后经铜基催化剂合成
		if(methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.petro.methanol")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.methanol")
				.setup(300, 800)
				.inputFluids(new FluidStack(Fluids.SYNGAS, 1_000), new FluidStack(Fluids.HYDROGEN, 300))
				.outputFluids(new FluidStack(methanol, 800)));
		}

		// 氨：航天版自带氨与硝酸两条路线，若氨由它提供就整组让给它，避免同一产物出现两套不同数值
		boolean hostOwnsAmmonia = ammonia != Fluids.NONE
			&& (ModFluids.AMMONIA.isExternal() || hostProducesFluid(ChemicalPlantRecipes.INSTANCE, ammonia));
		if(ammonia != Fluids.NONE && !hostOwnsAmmonia) {
			if(!hasChemRecipe("makentmgreatagain.petro.ammonia")) {
				ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.ammonia")
					.setup(250, 900)
					.inputFluids(new FluidStack(Fluids.AIR, 1_500), new FluidStack(Fluids.HYDROGEN, 600))
					.outputFluids(new FluidStack(ammonia, 800), new FluidStack(Fluids.WATER, 200)));
			}
			// 氨氧化制硝酸：数值与航天版 NITROACID 逐项一致 —— 氨 1000 + 水 500 → 硝酸 1000，100 tick
			if(!hasChemRecipe("makentmgreatagain.petro.nitricfromammonia")) {
				ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.petro.nitricfromammonia")
					.setup(100, 100)
					.inputFluids(new FluidStack(ammonia, 1_000), new FluidStack(Fluids.WATER, 500))
					.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 1_000)));
			}
		}

		// 528 加压：天然气管网的流体压力是 0，而加压槽只收同压力的液，所以原料必须先过压缩机。
		// 原版压缩机只认石油气、血液、全氟甲基，不补这两条，硝基甲烷和四氧化二氮就永远做不出来。
		if(pu == 1) {
			CompressorRecipes.recipes.put(new Pair(Fluids.GAS, 0),
				new CompressorRecipes.CompressorRecipe(1_000, new FluidStack(Fluids.GAS, 1_000, 1), 20));
			CompressorRecipes.recipes.put(new Pair(Fluids.NITRIC_ACID, 0),
				new CompressorRecipes.CompressorRecipe(1_000, new FluidStack(Fluids.NITRIC_ACID, 1_000, 1), 20));
		}

		MakeNTMGreatAgain.LOG.info("Added 11 chemical plant recipes: oil phase 1-3 petrochemicals");
	}

	/**
	 * 三期 · 苯胺：硝基苯催化加氢。走加氢处理机的表，一进（+氢）两出正好装得下。
	 * 每处理 100 mB 硝基苯扣 15 mB 氢（NEI 按 1000 mB 显示成 150）。
	 */
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

	// =====================================================================================
	//  石油三期 · 下游：10 条去向
	//
	//  甘油 → 双基火药，二硝基苯 → 芳纶，苯胺 → 聚氨酯，甲醇 → 酚醛/MTBE，
	//  硝基甲烷 → 火箭燃料，N2O4 → 强化硝化，氨 → 铵油炸药，TBP → 改进型 PUREX。

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

		// ① 苯胺 → 聚氨酯：苯胺缩合光气化得 MDI，甘油作多元醇。
		//    把二期的苯胺和一期的甘油串起来，替代原版"煤粉 + 萤石 + 石油"的聚合物路线。
		if(aniline != Fluids.NONE && glycerol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.polyurethane")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.polyurethane")
				.setup(100, 600)
				.inputFluids(new FluidStack(aniline, 400), new FluidStack(Fluids.PHOSGENE, 400),
							 new FluidStack(glycerol, 200))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_polymer, 6)));
		}

		// ② 甲醇 → 酚醛树脂：甲醇氧化成甲醛，与芳烃里的苯酚缩合。
		//    芳烃是二期产物，600 芳烃的成本远高于 500 石油，所以产量提到 8 块才有意义
		//    （原版 chem.bakelite 是 500 芳烃 + 500 石油 出 1 块）。
		if(methanol != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.bakelite")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.bakelite")
				.setup(150, 800)
				.inputFluids(new FluidStack(methanol, 600), new FluidStack(Fluids.AROMATICS, 600))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_bakelite, 8)));
		}

		// ③ 甲醇 → 航空汽油：MTBE 路线，甲醇 + 异丁烯（不饱和烃）是现实最主流的辛烷值提升剂。
		//    对比既有两条：调和 800 汽油 + 600 芳烃 → 1200；重整 500 汽油 + 600 重整油 → 1400。
		if(methanol != Fluids.NONE && avgas != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.avgas.mtbe")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.avgas.mtbe")
				.setup(150, 500)
				.inputFluids(new FluidStack(methanol, 400), new FluidStack(Fluids.UNSATURATEDS, 500),
							 new FluidStack(Fluids.GASOLINE, 700))
				.outputFluids(new FluidStack(avgas, 1_300)));
		}

		// ④ 硝基甲烷 → 高级固态燃料：硝基甲烷自带氧，用固体燃料做粘结剂压成富氧药柱。
		//    单个燃烧 12000 tick（普通固体燃料的 4 倍、火箭燃料的近 2 倍），一条出 4 个。
		if(nitromethane != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.advfuel")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.advfuel")
				.setup(120, 400)
				.inputItems(new ComparableStack(com.hbm.items.ModItems.solid_fuel, 2))
				.inputFluids(new FluidStack(nitromethane, 400), new FluidStack(Fluids.NITRIC_ACID, 100))
				.outputItems(new ItemStack(com.makentmgreatagain.init.ModItems.solid_fuel_adv, 4)));
		}

		// ⑤ 四氧化二氮 → 强化硝化：N2O4 就是硝酸的脱水形式，直接当硝化剂比稀硝酸效率高。
		//    原路线 1000 硝基苯 + 1200 硝酸 + 1200 硫酸 出 800，这条少用一半硝酸、出 900。
		if(n2o4 != Fluids.NONE && nitrobenzene != Fluids.NONE && dinitrobenzene != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.dinitro.n2o4")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.dinitro.n2o4")
				.setup(400, 900)
				.inputFluids(new FluidStack(nitrobenzene, 1_000), new FluidStack(n2o4, 500),
							 new FluidStack(Fluids.SULFURIC_ACID, 600))
				.outputFluids(new FluidStack(dinitrobenzene, 1200), new FluidStack(Fluids.WATER, 400)));
		}

		// ⑥ TBP_KERO → 改进型 PUREX：TBP 是真实的铀萃取剂，30% TBP + 70% 煤油就是 PUREX 的工作液。
		//     本模组原有的海水 PUREX 用煤油 500 出铀 6，这条用萃取剂出 9 并多回收硝酸。
		FluidType liquor = addonFluid(ModFluids.ORE_LIQUOR);
		if(tbpkero != Fluids.NONE && liquor != Fluids.NONE
			&& !hasGenericRecipe(PUREXRecipes.INSTANCE, "makentmgreatagain.down.tbp.purex")) {
			PUREXRecipes.INSTANCE.register((PUREXRecipe) new PUREXRecipe("makentmgreatagain.down.tbp.purex")
				.setup(1_200, 12_000)
				.inputFluids(new FluidStack(liquor, 1_000), new FluidStack(Fluids.NITRIC_ACID, 500),
							 new FluidStack(tbpkero, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_uranium, 9))
				.outputFluids(new FluidStack(Fluids.NITRIC_ACID, 300)));
		}

		// ⑦ 甲醇 + 葵花油 → 生物燃料（酯交换）：脂肪酸甲酯 + 甘油副产，甘油又回到①的多元醇
		//    和硝化甘油线，形成"甲醇 ↔ 甘油"的闭环。原版 chem.biofuel 走沼气 1500 + 乙醇 250。
		if(methanol != Fluids.NONE && glycerol != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.biodiesel")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.biodiesel")
				.setup(80, 400)
				.inputFluids(new FluidStack(methanol, 300), new FluidStack(Fluids.SUNFLOWEROIL, 1_000),
							 new FluidStack(Fluids.LYE, 100))
				.outputFluids(new FluidStack(Fluids.BIOFUEL, 1_000), new FluidStack(glycerol, 250)));
		}

		// ⑧ 硝基苯 → 苯胺（Béchamp 还原）：铁粉 + 盐酸还原，不需要加氢机，二期就能跑。
		//    与加氢路线的区别是耗铁和盐酸、不耗氢，适合氢不够时的并行路线。
		FluidType hcl = HBMCompat.hcl();
		if(nitrobenzene != Fluids.NONE && aniline != Fluids.NONE && hcl != Fluids.NONE
			&& !hasChemRecipe("makentmgreatagain.down.aniline.bechamp")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.aniline.bechamp")
				.setup(60, 500)
				.inputItems(new OreDictStack("dustIron", 2))
				.inputFluids(new FluidStack(nitrobenzene, 800), new FluidStack(hcl, 300))
				.outputFluids(new FluidStack(aniline, 800), new FluidStack(Fluids.WATER, 300)));
		}

		// ⑨ 硝化煤油萃取剂 → 工业溶剂：30% TBP + 70% 煤油，正是 PUREX 工作液，也是最通用的
		//    有机溶剂。原版 SOLVENT 走混合器，这条给三期萃取剂开一条万金油出口。
		if(tbpkero != Fluids.NONE && !hasChemRecipe("makentmgreatagain.down.solvent")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.down.solvent")
				.setup(40, 300)
				.inputFluids(new FluidStack(tbpkero, 300), new FluidStack(Fluids.KEROSENE, 700))
				.outputFluids(new FluidStack(Fluids.SOLVENT, 1_000)));
		}

		MakeNTMGreatAgain.LOG.info("Added 9 petrochemical downstream recipes (PU/bakelite/MTBE/advfuel/N2O4/TBP-PUREX/biodiesel/bechamp/solvent)");
	}

	/**
	 * 氘-氘聚变的中子支路：D + D → ³He + n。
	 */
	public static void addFusionHelium3Recipe() {
		if(hasGenericRecipe(FusionRecipes.INSTANCE, "makentmgreatagain.fus.dd3")) return;

		FusionRecipes.INSTANCE.register((FusionRecipe) new FusionRecipe("makentmgreatagain.fus.dd3")
			.setInputEnergy(500_000).setOutputEnergy(1_000_000).setOutputFlux(0)
			.setRGB(0.2F, 0.2F, 1F) // 氦等离子体的蓝
			.setNamed().setIcon(com.hbm.items.ModItems.gas_full, Fluids.HELIUM3.getID())
			.setPower(25_000).setDuration(100)
			.inputFluids(new FluidStack(Fluids.DEUTERIUM, 40))
			.outputFluids(new FluidStack(Fluids.HELIUM3, 1_000)));

		MakeNTMGreatAgain.LOG.info("Added fusion recipe: deuterium plasma -> helium-3 (aneutronic branch)");
	}

	/**
	 * 铋 · PUREX
	 */
	public static void addBismuthRecoveryRecipes() {
		// 产锝的六条 ZIRNOX 乏燃料配方（meu / mox / mep / heu233 / heu235 / zfbmox）
		String[] technetiumRecipes = {
			"purex.zirnoxmeu", "purex.zirnoxmox", "purex.zirnoxmep",
			"purex.zirnoxheu233", "purex.zirnoxheu235", "purex.zirnoxzfbmox"
		};
		for(String name : technetiumRecipes) appendBismuth(name, 3);

		// 天然铀乏燃料：不产锝，只给 1 粒，作为不依赖浓缩铀的第二通道
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
		// PUREXRecipes.outputItemLimit() == 6，现有配方最多用到 4 格，加一格仍然放得下
		IOutput[] extended = new IOutput[base + 1];
		if(current != null) System.arraycopy(current, 0, extended, 0, base);
		extended[base] = new ChanceOutput(new ItemStack(com.hbm.items.ModItems.nugget_bismuth, amount));
		recipe.outputItem = extended;
	}

	// =====================================================================================
	//  木浆路线：木油 → 木质浆料 → 六种木浆 → 结晶器还原原木

	/** 木框架：16 木棍 + 4 钢脚手架 + 水 4000 → 木框架 x1 + 返还 4 钢脚手架。 */
	public static void addWoodFrameRecipe() {
		if(hasChemRecipe("makentmgreatagain.wood.frame")) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.wood.frame")
			.setup(200, 3_000)
			.inputItems(new ComparableStack(Items.stick, 16),
						new ComparableStack(com.hbm.blocks.ModBlocks.steel_scaffold, 4))
			.inputFluids(new FluidStack(Fluids.WATER, 4_000))
			.outputItems(new ItemStack(ModItems.wood_frame, 1),
						 new ItemStack(com.hbm.blocks.ModBlocks.steel_scaffold, 4)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: wood frame (scaffold returned)");
	}

	/** 搅拌机：木油 2000 + 水 1000 → 木质浆料 1000。主路线，5 秒一批。 */
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

	/** 化工厂：木油 2000 + 水 1000 + 碱液 100 → 木质浆料 1500。碱液皂化树脂酸，多得 50%。 */
	public static void addWoodMashChemicalRecipe() {
		FluidType mash = addonFluid(ModFluids.WOOD_MASH);
		if(mash == Fluids.NONE) return;
		if(hasChemRecipe("makentmgreatagain.wood.mash")) return;

		ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.wood.mash")
			.setup(200, 2_500)
			.inputFluids(new FluidStack(Fluids.WOODOIL, 2_000),
						 new FluidStack(Fluids.WATER, 1_000),
						 new FluidStack(Fluids.LYE, 100))
			.outputFluids(new FluidStack(mash, 1_500)));

		MakeNTMGreatAgain.LOG.info("Added chemical plant recipe: wood oil + water + lye -> wood mash");
	}

	public static void addWoodPulpRecipes() {
		// 云杉走亚硫酸盐法：蒸煮液里真正起作用的是亚硫酸氢钠/亚硫酸，不是硫酸。
		// 硫酸是硫酸盐法用的，而硫酸盐法在这套配方里已经是丛林浆（碱液 + 硫粉/硫酸钠）。
		// 改成 SO2 之后，碱法（橡木/深色橡木）、亚硫酸盐法（云杉）、过氧化氢法（金合欢）
		// 三条路线各归各位，不再把两种酸法混成一条。
		// 万一 SO2 没注册上，退回原来的硫酸版，别让制浆线因为一种流体少一种木头。
		FluidType sulfite = addonFluid(ModFluids.SULFUR_DIOXIDE);
		FluidType sulphiteSalt = addonFluid(ModFluids.SODIUM_SULPHITE);

		// 云杉的蒸煮液按优先级选：亚硫酸钠 + SO2（真·亚硫酸氢钠）> 单 SO2 > 硫酸兜底。
		// 亚硫酸钠版省下 100 SO2、多出 100 浆 —— 这就是硫酸钠/亚硫酸钠线给制浆带来的好处。
		FluidStack[] spruceExtras;
		int spruceOutput = 1_500;
		if(sulphiteSalt != Fluids.NONE && sulfite != Fluids.NONE) {
			// Na2SO3 + SO2 + H2O → 2NaHSO3，这才是酸性亚硫酸盐法真正下锅的东西
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

		// 硫酸盐法的另一条补硫路线：靠硫酸钠（kraft 厂叫 salt cake）而不是硫粉。
		// 现实里硫酸钠是在回收炉里被碳还原成硫化钠再回用的，这里简化成直接入锅，
		// 代价是多耗 50 碱液、慢 20 tick —— 换来的是脱氯副产的硫酸钠有了稳定去处。
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

		// 只有硫酸盐法这一条写了 lang；其余木浆配方没有中文名，
		// 盲加 setNamed() 会让它们显示成 makentmgreatagain.pulp.xxx 这种生 key，
		// 比现在显示产物名还难看。所以按名字单独开。
		GenericRecipe recipe = new GenericRecipe(name);
		if(name.equals("makentmgreatagain.pulp.jungle.sulfate")) recipe.setNamed();
		recipe.setup(duration, 3_000)
			.inputFluids(in)
			.outputFluids(new FluidStack(pulp, output));
		if(item != null) recipe.inputItems(item);

		ChemicalPlantRecipes.INSTANCE.register(recipe);
	}

	/**
	 * 结晶器：木框架 x1 + 该种木浆 4000 → 该种原木 x4（600t / 1000 HE/t，一次 60 万 HE）。
	 */
	public static void addWoodPulpCrystallizerRecipes() {
		if(ModItems.wood_frame == null) return;

		// 原版原木分两个方块：Blocks.log 只有前 4 种，金合欢与深色橡木在 Blocks.log2 里。
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

	// =====================================================================================
	//  后期支线 · 自由资源深加工（设计方案见根目录《后期支线-设计方案.md》）

	/** 支线① · 合成橡胶：沼泽泵的胶体 → 生胶 → 硫化 → 橡胶。 */
	public static void addRubberBranchRecipes() {
		if(ModItems.raw_rubber == null) return;

		// 胶乳凝聚：胶体 2000 + 硫酸 200 → 生胶 x2 + 水 1000
		if(!hasChemRecipe("makentmgreatagain.branch.rubber.coagulate")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.branch.rubber.coagulate")
				.setup(400, 3_000)
				.inputFluids(new FluidStack(Fluids.COLLOID, 2_000), new FluidStack(Fluids.SULFURIC_ACID, 200))
				.outputItems(new ItemStack(ModItems.raw_rubber, 2))
				.outputFluids(new FluidStack(Fluids.WATER, 1_000)));
		}

		// 硫化：生胶 x2 + 硫粉 x1 + 蒸汽 1000 + 不饱和烃 150 → 橡胶 x3
		// 不饱和烃是石油裂解/重整的产物。原版 chem.rubber 是烯烃 500 → 橡胶 2（250/块），
		// 这条是烯烃 150 → 橡胶 3（50/块），只要原版两成的烯烃，但必须真的开了石油线，
		// 没有石油线时一滴橡胶也拿不到，堵死"沼泽抽胶体白嫖橡胶"。
		// 蒸汽同时从 250 提到 1000。算总账：凝聚 3000 HE + 硫化 1500 HE = 4500 HE 出 3 块橡胶，
		// 即每块 1500 HE，是原版（200 HE 出 2 块 = 100 HE/块）的十五倍。
		if(!hasChemRecipe("makentmgreatagain.branch.rubber.vulcanize")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.branch.rubber.vulcanize")
				.setup(200, 1_500)
				.inputItems(new ComparableStack(ModItems.raw_rubber, 2),
							new ComparableStack(com.hbm.items.ModItems.sulfur, 1))
				.inputFluids(new FluidStack(Fluids.STEAM, 1_000), new FluidStack(Fluids.UNSATURATEDS, 150))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_rubber, 3)));
		}

		MakeNTMGreatAgain.LOG.info("Added branch recipes: colloid -> raw rubber -> rubber");
	}

	/** 支线② · 建材：山地泵的钙溶液碳化成石灰石粉，接原版既有链（石灰石粉+黏土球=水泥）。 */
	public static void addCalciumBranchRecipes() {
		// 碳酸化：钙溶液 1000 + 二氧化碳 500 → 石灰石粉 x3
		// 石灰石粉是原版水泥（粉煤 x4 = 石灰石粉 + 黏土球 x3）与组合机制生石灰的共同原料，
		// 所以这里只做到石灰石粉为止，后面的水泥/混凝土继续走 HBM 自己的配方，不重复造轮子。
		if(!hasChemRecipe("makentmgreatagain.branch.calcium.carbonate")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.branch.calcium.carbonate")
				.setup(300, 2_500)
				.inputFluids(new FluidStack(Fluids.CALCIUM_SOLUTION, 1_000), new FluidStack(Fluids.CARBONDIOXIDE, 500))
				.outputItems(new ItemStack(com.hbm.items.ModItems.powder_limestone, 3)));
		}

		// 硫酸化：钙溶液 1000 + 硫酸 400 → 石膏 x2 + 水 500
		// 与海水线的「海水 + 硫酸 → 石膏」产物相同但原料与群系不同：海边玩家走海水，山地玩家走钙溶液。
		if(!hasChemRecipe("makentmgreatagain.branch.calcium.gypsum")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.branch.calcium.gypsum")
				.setup(300, 3_000)
				.inputFluids(new FluidStack(Fluids.CALCIUM_SOLUTION, 1_000), new FluidStack(Fluids.SULFURIC_ACID, 400))
				.outputItems(new ItemStack(ModItems.gypsum, 2))
				.outputFluids(new FluidStack(Fluids.WATER, 500)));
		}

		MakeNTMGreatAgain.LOG.info("Added branch recipes: calcium solution -> limestone / gypsum");
	}

	/** 支线③ · 末影制品：末地泵的末影果汁在结晶器里绕着末地石长成末影珍珠。 */
	public static void addEnderBranchRecipe() {
		CrystallizerRecipes.CrystallizerRecipe recipe =
			new CrystallizerRecipes.CrystallizerRecipe(new ItemStack(Items.ender_pearl, 1), 600);

		// 结晶器键是 (固体, 流体)，同键覆盖，重复注入天然幂等
		CrystallizerRecipes.registerRecipe(new ComparableStack(Blocks.end_stone), recipe,
			new FluidStack(Fluids.ENDERJUICE, 6_000));

		MakeNTMGreatAgain.LOG.info("Added branch recipe: ender juice + end stone -> ender pearl");
	}

	/**
	 * 极地产线 · 高纯炭黑。
	 */
	public static void addPolarBranchRecipes() {
		if(ModItems.carbon_black_pure == null) return;

		// ① 气相法制炭黑：天然气 2,000 + 氧 1,000 + 冷凝胶 100 → 高纯炭黑 ×4 + 水 1,000
		if(!hasChemRecipe("makentmgreatagain.carbon.black")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.carbon.black")
				.setup(400, 4_000)
				.inputFluids(new FluidStack(Fluids.GAS, 2_000), new FluidStack(Fluids.OXYGEN, 1_000),
							new FluidStack(Fluids.CRYOGEL, 100))
				.outputItems(new ItemStack(ModItems.carbon_black_pure, 4))
				.outputFluids(new FluidStack(Fluids.WATER, 1_000)));
		}

		// ② 富勒烯：高性能溶剂 500 + 冷凝胶 250 + 高纯炭黑 ×1 → 富勒烯 750
		//    原版搅拌机是溶剂 500 + 细烟灰 ×1 → 250。这里靠冷凝胶的低温场做结晶析出，
		//    同样一份溶剂出三倍，代价是要同时有炭黑和冷媒两样。
		if(!hasChemRecipe("makentmgreatagain.carbon.fullerene")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.carbon.fullerene")
				.setup(400, 1_000)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 1))
				.inputFluids(new FluidStack(Fluids.RADIOSOLVENT, 500), new FluidStack(Fluids.CRYOGEL, 250))
				.outputFluids(new FluidStack(Fluids.FULLERENE, 750)));
		}

		// ③ 石墨电极：高纯炭黑 ×2 + 燃油 1,000 → 石墨电极 ×1
		if(!hasChemRecipe("makentmgreatagain.carbon.electrode")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.carbon.electrode")
				.setup(600, 200)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 2))
				.inputFluids(new FluidStack(Fluids.HEATINGOIL, 1_000))
				.outputItems(new ItemStack(com.hbm.items.ModItems.arc_electrode, 1)));
		}

		// ④ 碳粉盒：高纯炭黑 ×1 → 碳粉盒 ×24
		if(!hasGenericRecipe(AssemblyMachineRecipes.INSTANCE, "makentmgreatagain.carbon.part")) {
			AssemblyMachineRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.carbon.part")
				.setup(40, 100)
				.inputItems(new ComparableStack(ModItems.carbon_black_pure, 1))
				.outputItems(new ItemStack(com.hbm.items.ModItems.part_carbon, 24)));
		}

		// ⑤ 补强橡胶：生胶 ×2 + 硫粉 ×1 + 高纯炭黑 ×1 + 蒸汽 600 + 不饱和烃 300 → 橡胶锭 ×6
		//    比普通硫化（生胶 2 + 硫 1 + 蒸汽 1000 + 烯烃 150 → 橡胶 3）产量翻倍，
		//    代价是额外的高纯炭黑和双倍烯烃。原先挂在装配机上、既不耗蒸汽也不耗烯烃，
		//    等于给生胶留了一条绕开石油线的后门，现在挪进化工厂，两处同标准。
		if(!hasChemRecipe("makentmgreatagain.carbon.rubber")) {
			ChemicalPlantRecipes.INSTANCE.register(new GenericRecipe("makentmgreatagain.carbon.rubber")
				.setup(60, 150)
				.inputItems(new ComparableStack(ModItems.raw_rubber, 2),
							new ComparableStack(com.hbm.items.ModItems.sulfur, 1),
							new ComparableStack(ModItems.carbon_black_pure, 1))
				.inputFluids(new FluidStack(Fluids.STEAM, 600), new FluidStack(Fluids.UNSATURATEDS, 300))
				.outputItems(new ItemStack(com.hbm.items.ModItems.ingot_rubber, 6)));
		}

		// ⑥ 石墨锭：高纯炭黑 ×1 压成石墨锭 ×2
		PressRecipes.makeRecipe(com.hbm.items.machine.ItemStamp.StampType.FLAT,
				new ComparableStack(ModItems.carbon_black_pure, 1),
				new ItemStack(com.hbm.items.ModItems.ingot_graphite, 2));

		MakeNTMGreatAgain.LOG.info("Added polar branch recipes: gas hydrate -> carbon black -> fullerene / electrode / part / rubber");
	}

	// =====================================================================================
	//  postInit 兜底注入
	// =====================================================================================

	/**
	 * 兜底注入：HBM 的 onRecipeLoad 回调只在「配方配置文件不存在 → registerDefaults」这条分支里触发
	 * （见 SerializableRecipe.initialize()）。一旦 config/hbmRecipes/ 下出现玩家版 hbmXxx.json
	 * （不带下划线前缀），HBM 改走读文件，监听器永远不跑，附属所有注入配方静默消失。
	 *
	 * 这里在 postInit（HBM 自己的 PostLoad 已跑完、配方表已建立）把所有注入再执行一遍。
	 * 每条 addXxx 都必须幂等——重复调用要么跳过、要么无害覆盖，绝不抛异常：
	 *   - 电解槽/炼油厂/搅拌机：getRecipe != null 就 return；
	 *   - 粉碎机：setRecipe 本身 containsKey 就不覆盖，NEI 缓存置 null 无害；
	 *   - 离心机/固化：put 同 key 等价覆盖，内容一样；
	 *   - 化工厂/装配机：register 遇重名会抛 IllegalStateException，靠 hasGenericRecipe 先挡；
	 *   - 电弧炉：register 内部有 occupiedSolid/occupiedLiquid 占用检查，自动跳过。
	 *
	 * 顺序也安全：SerializableRecipe.initialize() 在 HBM 的 PostLoad 里，本模组 postInit 必晚于它，
	 * 不会被 autogen（ShredderRecipes.registerPost 等）顶掉或再次覆盖。
	 */
	public static void injectAllFallback() {
		// 逐条兜底：一条重复注册不能连累后面所有配方，否则整段 fallback 等于没跑
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
		step("shredderAssembly",     ModRecipes::addShredderAssemblyRecipe);
		step("bat9000Assembly",      ModRecipes::addBat9000AssemblyRecipe);
		step("heatexAssembly",       ModRecipes::addHeatexRebornAssemblyRecipe);
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
		step("petrochemDownstream",  ModRecipes::addPetrochemDownstreamRecipes);
		// 铅锌线（ISP 法提镉）
		step("leadzincChemical",     ModRecipes::addLeadZincChemicalRecipes);
		step("leadzincBlast",        ModRecipes::addLeadZincBlastRecipe);
		// SO2 上游（复式炼焦炉 / 热解炉烧硫磺）+ 硫酸锌液进电解机
		step("leadzincAux",          ModRecipes::addSulfurDioxideUpstreamRecipes);
		// 氯碱线：亚硫酸钠脱氯 → 盐酸 + 硫酸钠
		step("chloralkaliDechlor",   ModRecipes::addChlorAlkaliDechlorRecipe);
		// 无氰浸金：硫代硫酸盐合成/浸出/置换/强效脱氯，外加含砷金矿的熔炉
		step("thiosulfateGold",      ModRecipes::addThiosulfateGoldRecipes);
		step("refractoryGoldSmelt",  ModRecipes::addRefractoryGoldSmelting);
		step("fusionHelium3",        ModRecipes::addFusionHelium3Recipe);

		MakeNTMGreatAgain.LOG.info("postInit 兜底注入完成（若 config/hbmRecipes 存在玩家版配置，本次注入即为生效来源）");
	}

	/** 单条兜底注入：已注册就当作成功，抛异常只记 warn，不影响后续条目。 */
	private static void step(String name, Runnable action) {
		try {
			action.run();
		} catch (Throwable ex) {
			MakeNTMGreatAgain.LOG.warn("兜底注入跳过 " + name + "：" + ex);
		}
	}

	/** GenericRecipes 的 recipeNameMap 是 public 字段：按内部名判断配方是否已注册（含 JSON 读回来的）。 */
	private static boolean hasGenericRecipe(com.hbm.inventory.recipes.loader.GenericRecipes<?> holder, String name) {
		return holder.recipeNameMap.containsKey(name);
	}

	/**
	 * 宿主的化工厂配方表里是否已经有一条产出该流体的配方。
	 * 用来判断"这条线是不是已经由航天版提供了"——装了航天版时它自带 HCL / AMMONIA / NITRIC_ACID，
	 * 我们再注册一条就会让玩家看到两份不同数值，正是要避免的割裂。
	 */
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
}

package com.makentmgreatagain.init;

import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.material.MaterialShapes;
import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.Mats.MaterialStack;
import com.hbm.inventory.material.NTMMaterial;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.inventory.recipes.CrucibleRecipe;
import com.hbm.inventory.recipes.CrucibleRecipes;
import com.hbm.inventory.recipes.LiquefactionRecipes;
import com.hbm.inventory.recipes.PressRecipes;
import com.hbm.inventory.recipes.ShredderRecipes;
import com.hbm.inventory.recipes.SolidificationRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilConstructionRecipe;
import com.hbm.inventory.recipes.anvil.AnvilRecipes.AnvilOutput;
import com.hbm.items.machine.ItemMold;
import com.hbm.items.machine.ItemMold.Mold;
import com.hbm.items.machine.ItemStamp.StampType;
import com.hbm.util.Tuple.Pair;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.recipes.RecipeRegistrar;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 三材质（耐摔合金 / 铒 / 铂）与氦气凝锭，与 HBM 材料体系的对接。
 *
 * <h2>【坩埚 / 铸造】速查 —— 想改坩埚相关的东西，只读这一段就够</h2>
 *
 * <p><b>怎么找到相关代码</b>：全文搜「坩埚」两个字。本文件有 4 处
 * {@code // ===== [坩埚/铸造] ... =====} 横幅注释：①②③ 是坩埚 → 铸造链路，
 * ④ 是固化机 / 液化机 / 坩埚合金这三个真正写了配方的。</p>
 *
 * <p><b>游戏内怎么用（三步，全是 HBM 原版机器，本模组没有新增机器）</b>：</p>
 * <ol>
 * <li>把锭 / 粉 / 板 / 块丢进<b>坩埚（Crucible）</b> → 烧到半热以上自动熔成对应的熔融金属，
 * 液面颜色 = {@link #registerMaterials()} 里写的 moltenColor。</li>
 * <li>熔融金属用管道或坩埚出料口送进<b>铸造机 / 铸造盆（Foundry Mold / Foundry Basin）</b>。</li>
 * <li>铸造机上插对应<b>模具（Mold）</b>，攒够液量就浇出该形态的物品。
 * 消耗量由 HBM 的 {@code MaterialShapes} 固定：锭 72、板 72、铸造板 216、焊接板 432、
 * 壳 288、管 216、细线 72（一次出 8 根）、致密线 72、块 648（单位是 quanta，1 锭 = 72）。</li>
 * </ol>
 *
 * <p><b>除了「矿辞驱动」的坩埚 / 铸造，本文件还有三个真正写出来的配方</b>
 * （氦气凝锭这条线，搜「固化机」可直接跳到 §④）：</p>
 * <ul>
 * <li>{@link #addSolidificationRecipe()} —— <b>固化机</b>：2000 mB 氦-3 → 1 个氦气凝锭</li>
 * <li>{@link #addLiquefactionRecipe()} —— <b>液化机</b>：1 个氦气凝锭 → 2000 mB 氦-3</li>
 * <li>{@link #addHeliumAlloyRecipe()} —— <b>坩埚合金</b>：
 * 1 氦气凝锭 + 3 锂粒 + 6 钴粒 + 3 铂粒 + 1 铒粒 → 2 耐摔合金锭 + 4 粒炉渣。
 * 注意坩埚合金配方是按<b>熔体量</b>匹配的，不是按物品，所以不写「物品 → 物品」。</li>
 * </ul>
 *
 * <p><b>为什么整个坩埚 → 铸造链路一行配方都不用写</b>（这是最容易被误解的地方）：</p>
 * <ul>
 * <li>坩埚判断「这物品能不能熔、熔成什么」走的是
 * {@code TileEntityCrucible.isItemSmeltable()} → {@code Mats.getSmeltingMaterialsFromItem()}
 * → 取物品的矿辞名 → 剥掉形态前缀 → {@code Mats.matByName.get(材质名)}。
 * 也就是说：<b>矿辞名 + 材质名就是唯一凭据</b>，没有配方表可写。</li>
 * <li>铸造机出什么物品走 {@code MoldShape.getOutput(mat)}，同样拿
 * {@code 形态前缀 + 材质名} 去 {@code OreDictionary} 里查，查到就出。
 * 因为矿辞里只有本模组的物品，所以浇出来的一定是
 * {@code makentmgreatagain:plate_platinum} 这类本模组物品，不会串到 HBM 的物品上。</li>
 * </ul>
 *
 * <p><b>新增一个可熔材质的完整清单（只有 2 步要改，另 2 步是已经接好的挂载点）</b>：</p>
 * <ol>
 * <li>{@link #registerOreDict()} —— 加一行 {@code registerOne(...)}。
 * <b>形态前缀必须逐字对齐 {@code MaterialShapes.name()}</b>，三个反直觉的：
 * 铸造板是 {@code plateTriple}（不是 castPlate）、焊接板是 {@code plateSextuple}、
 * 管是 {@code ntmpipe}（不是 pipe）。</li>
 * <li>{@link #registerMaterials()} —— {@code Mats.makeSmeltable(新id, Mats.df("名字"), 亮色, 暗色, 熔融色)}。
 * 「名字」必须与矿辞里 {@code ingot} 后面那一段<b>逐字相同且大小写敏感</b>，
 * 否则坩埚查 {@code matByName} 查不到，表现就是「物品放进去不熔」。</li>
 * <li>挂载点已接好，不用动：主类 preInit 末尾的 {@code MNGAMetals.registerOreDict()}。</li>
 * <li>挂载点已接好，不用动：{@code CommonProxy.postInit} 末尾的 {@link #refreshCastingTable()}。</li>
 * </ol>
 *
 * <p><b>时机为什么不能合并</b>：{@link #registerMaterials()} 必须在 postInit，
 * 因为 HBM 有 16 处 {@code for(NTMMaterial mat : Mats.orderedList)} 的自动注册循环，
 * 会拿 {@code mat.id} 去索引 HBM 自己的物品 metadata；本模组的材质在那儿没有对应物品，
 * 早注册就会让那些循环去索引不存在的东西。矿辞反而要早（preInit 末尾），
 * 因为坩埚/NEI 在 postInit 里就会开始读它。</p>
 *
 * <p><b>出问题怎么查</b>（按这个顺序看 {@code logs/latest.log}）：
 * 搜 {@code MNGAMetals: ore dict registered} 确认矿辞挂上了 →
 * 搜 {@code MNGAMetals: castable} 看每个材质能浇几种模具（三材质正常是 13 种，
 * 见 {@link #refreshCastingTable()} 的说明）→
 * 若坩埚「能熔但浇不出」，基本就是铸造表缓存没刷新，见 {@link #refreshCastingTable()}。</p>
 */
public class MNGAMetals {

	/**
	 * 材质 id 用 21000 段，避开 HBM 本体（最大 12836）与 hbmMats.json 的 DemoMat（20006）。
	 * 必须 ≤ {@code Short.MAX_VALUE}（32767）：ID 会被塞进 ItemStack 的 short 型 metadata。
	 * 坩埚 / NEI 材质页 / {@code hbmMats.json} 里引用材质时用的都是这些 id。
	 */
	private static final int ID_HELICOPTER = 21001;
	private static final int ID_ERBIUM = 21002;
	private static final int ID_PLATINUM = 21003;
	/** 氦气凝锭。非金属（固化气体），但仍按 SMELTABLE 注册，这样坩埚能熔、锭模能浇回。 */
	private static final int ID_HELIUM = 21004;

	/** 三个坩埚材质句柄。{@link #registerMaterials()} 之后才非 null，别在 preInit 阶段碰它们。 */
	public static NTMMaterial MAT_HELICOPTER;
	public static NTMMaterial MAT_ERBIUM;
	public static NTMMaterial MAT_PLATINUM;
	/** 「氦」熔体。只用于氦气凝锭熔炼与坩埚合金配方，没有粉 / 板 / 管等形态。 */
	public static NTMMaterial MAT_HELIUM;

	// =========================================================================================
	// ===== [坩埚/铸造] ① 矿物词典 —— 坩埚「认物品」、铸造机「出物品」的唯一依据 =====
	// =========================================================================================

	/**
	 * 注册矿物词典。<b>这是坩埚链路的根</b>：坩埚靠它判断「这物品能熔、熔成什么」，
	 * 铸造模具靠它判断「这个材质能浇成什么形状、浇出来是哪个物品」。
	 *
	 * <p>时机：preInit 末尾（物品与方块都注册完之后），由主类
	 * {@code MakeNTMGreatAgain.preInit} 调用。</p>
	 *
	 * <p>名称必须严格对齐 {@code MaterialShapes.name()}，写错一个，对应的铸造模具就
	 * 悄悄出不了东西（不报错，只是在 NEI 里查不到、模具插进去没反应）。三个最容易错的：</p>
	 * <ul>
	 * <li>铸造板 = {@code plateTriple}，不是 castPlate</li>
	 * <li>焊接板 = {@code plateSextuple}</li>
	 * <li>管 = {@code ntmpipe}，不是 pipe</li>
	 * </ul>
	 *
	 * <p>新增材质就在下面照抄一行 {@code registerOne(...)} 即可，参数顺序见
	 * {@link #registerOne(String, Item, Item, Item, Item, Item, Item, Item, Item, Item, Item, Item, Block)}。</p>
	 */
	public static void registerOreDict() {
		registerOne("Helicopter", ModItems.ingot_helicopter, ModItems.nugget_helicopter, ModItems.powder_helicopter,
				ModItems.plate_helicopter, ModItems.plate_cast_helicopter, ModItems.plate_welded_helicopter,
				ModItems.wire_helicopter, ModItems.wire_dense_helicopter, ModItems.bolt_helicopter,
				ModItems.pipe_helicopter, ModItems.shell_helicopter, ModBlocks.block_helicopter);

		registerOne("Erbium", ModItems.ingot_erbium, ModItems.nugget_erbium, ModItems.powder_erbium,
				ModItems.plate_erbium, ModItems.plate_cast_erbium, ModItems.plate_welded_erbium,
				ModItems.wire_erbium, ModItems.wire_dense_erbium, ModItems.bolt_erbium,
				ModItems.pipe_erbium, ModItems.shell_erbium, ModBlocks.block_erbium);

		registerOne("Platinum", ModItems.ingot_platinum, ModItems.nugget_platinum, ModItems.powder_platinum,
				ModItems.plate_platinum, ModItems.plate_cast_platinum, ModItems.plate_welded_platinum,
				ModItems.wire_platinum, ModItems.wire_dense_platinum, ModItems.bolt_platinum,
				ModItems.pipe_platinum, ModItems.shell_platinum, ModBlocks.block_platinum);

		// 氦气凝锭：只有锭形态。登记进矿辞有两个作用——
		// ① 坩埚能认出它（ingot 前缀 + 材质名 Helium → MAT_HELIUM）；
		// ② 锭模（Mold ingot）能把它浇回来，形成「锭 → 熔体 → 锭」的闭环。
		OreDictionary.registerOre("ingotHelium", ModItems.ingot_helium);

		// HBM 的钴粒（ModItems.nugget_cobalt）本体没有登记任何矿辞，
		// 而坩埚合金配方要按「钴」来认物品，所以这里补一条。
		// 补的是 HBM 的物品、走的是全局矿辞表，不会改到 HBM 的代码。
		OreDictionary.registerOre("nuggetCobalt", com.hbm.items.ModItems.nugget_cobalt);
		// 锂粒不用补：HBM 的 MAT_LITHIUM 自带 NUGGET autogen，已有 nuggetLithium。

		MakeNTMGreatAgain.LOG.info("MNGAMetals: ore dict registered for Helium (ingot only) + "
				+ "nuggetCobalt (HBM item, was missing)");
	}

	/**
	 * 一个材质的全部矿辞前缀。{@code name} 必须与 {@link #registerMaterials()} 里的材质名
	 * <b>逐字一致且大小写敏感</b>（坩埚是拿矿辞名去掉前缀后直接查 {@code Mats.matByName}）。
	 *
	 * <p>每行右边的注释标了两件事：<b>熔进坩埚能产出多少液态量</b>（单位 quanta，1 锭 = 72），
	 * 以及<b>对应哪个铸造模具</b>。写「无模具」的形态，说明 HBM 的 {@code ItemMold.molds}
	 * 里没有对应模具，那个形态只能靠砧 / 锻压机 / 电弧焊机 / 工作台获得；
	 * 矿辞仍然要记，因为 NEI 与后续配方都按统一前缀找物品。</p>
	 */
	private static void registerOne(String name, Item ingot, Item nugget, Item dust,
			Item plate, Item castPlate, Item weldedPlate,
			Item wire, Item denseWire, Item bolt, Item pipe, Item shell, Block block) {

		//        矿辞前缀             物品            液态量 / 对应模具
		OreDictionary.registerOre("ingot" + name, ingot);              // 72；模具 ingot
		OreDictionary.registerOre("nugget" + name, nugget);            // 8；模具 nugget
		OreDictionary.registerOre("dust" + name, dust);                // 72；无模具（粉不能浇，只能熔）
		OreDictionary.registerOre("plate" + name, plate);              // 72；模具 plate
		OreDictionary.registerOre("plateTriple" + name, castPlate);    // 216；模具 plate_cast ← 铸造板
		OreDictionary.registerOre("plateSextuple" + name, weldedPlate);// 432；无模具，电弧焊机焊出来的
		OreDictionary.registerOre("wireFine" + name, wire);            // 72；模具 wire（一次浇 8 根）
		OreDictionary.registerOre("wireDense" + name, denseWire);      // 72；模具 wire_dense
		OreDictionary.registerOre("bolt" + name, bolt);                // 9；无模具，工作台出
		OreDictionary.registerOre("ntmpipe" + name, pipe);             // 216；模具 pipe
		OreDictionary.registerOre("shell" + name, shell);              // 288；模具 shell
		OreDictionary.registerOre("block" + name, block);              // 648；模具 block（大模具）

		MakeNTMGreatAgain.LOG.info("MNGAMetals: ore dict registered for " + name
				+ " (crucible can smelt ingot/dust/plate/plateTriple/wireFine/wireDense/ntmpipe/shell/block)");
	}

	// =========================================================================================
	// ===== [坩埚/铸造] ② 熔融材质 —— 决定「熔成什么液体」和「液面什么颜色」 =====
	// =========================================================================================

	/**
	 * 注册三个熔融材质（每个材质对应坩埚里的一种液体）。
	 *
	 * <p>时机：postInit，由 {@code CommonProxy.postInit} 调用，<b>必须晚于 HBM 全部配方注册</b>
	 * （理由见类头「时机为什么不能合并」）。</p>
	 *
	 * <p>参数含义（{@code Mats.makeSmeltable} 的四个数）：</p>
	 * <ol>
	 * <li>{@code id} —— 材质 id。本模组统一用 21000 段，见文件顶部常量。</li>
	 * <li>{@code Mats.df("名字")} —— <b>材质名，坩埚认物品全靠它</b>。必须与
	 * {@code OreDictionary} 里 {@code ingot} 后面那一段逐字相同（大小写敏感）。
	 * 这个名字同时是 NEI 材质页与坩埚界面显示的名字，也是 {@code hbmMats.json} / 配方 JSON
	 * 里引用该材质的键。</li>
	 * <li>{@code solidColorLight / solidColorDark} —— 物品的亮/暗色，与贴图重映射色板同值，
	 * 让 Tooltip 与 NEI 里的色块和物品贴图对得上。</li>
	 * <li>{@code moltenColor} —— <b>坩埚 / 铸造机里液面的颜色</b>，取三种色的中间调。</li>
	 * </ol>
	 *
	 * <p>后面缀的 {@code .m()} 是标记「金属」（{@code .n()} 是非金属），影响坩埚与铸造机的
	 * 部分表现，金属材质一律用 {@code .m()}。</p>
	 *
	 * <p>刻意不调用 {@code setAutogen(...)}：autogen 是让 HBM 按材质去<b>自动生成</b>
	 * {@code ItemAutogen} 物品的开关；本模组已经有独立物品，开了只会让 HBM 拿本模组的
	 * {@code mat.id} 去索引它自己那边不存在的 metadata。代价是 HBM 那些「按 autogen
	 * 自动补螺栓/线材配方」的循环会跳过本模组材质，所以螺栓等配方需要手写
	 * （见 {@link #addBoltRecipes()}）。</p>
	 */
	public static void registerMaterials() {
		// 名字（第二个参数）必须与 registerOreDict 里 ingot 后面的名字逐字一致
		// 金属/非金属标记走兼容层（旧版 HBM 没有 .m()/.n()，setTraits 两版通用）
		MAT_HELICOPTER = com.makentmgreatagain.compat.HBMVersionCompat.markMetal(Mats.makeSmeltable(ID_HELICOPTER, Mats.df("Helicopter"),
				0xF2DE7E, 0x4C4022, 0xC0A84E)); // 亮 0xF2DE7E / 暗 0x4C4022 / 液态 0xC0A84E
		MAT_ERBIUM = com.makentmgreatagain.compat.HBMVersionCompat.markMetal(Mats.makeSmeltable(ID_ERBIUM, Mats.df("Erbium"),
				0x5CB866, 0x1C3A1C, 0x408644));
		MAT_PLATINUM = com.makentmgreatagain.compat.HBMVersionCompat.markMetal(Mats.makeSmeltable(ID_PLATINUM, Mats.df("Platinum"),
				0xEEF4FF, 0x425068, 0x8E9CB6));

		// 氦：名字必须是 "Helium"，否则 ingotHelium 认不出（见 registerOreDict）。
		// 标记 NONMETAL 而不是 METAL：它是固化气体，不是金属。这不影响坩埚——
		// 坩埚只看 smeltable 枚举，不看 METAL 标记。
		MAT_HELIUM = com.makentmgreatagain.compat.HBMVersionCompat.markNonMetal(Mats.makeSmeltable(ID_HELIUM, Mats.df("Helium"),
				0xE9FDFF, 0x2C6B7C, 0x7BC5D4)); // 熔融色取贴图的冰蓝中间调

		MakeNTMGreatAgain.LOG.info("MNGAMetals: molten materials registered "
				+ "(Helicopter/Erbium/Platinum/Helium)");
	}

	/**
	 * 铁砧。比例照抄 HBM：
	 * 1 锭 → 1 板（tier 3）、1 锭 → 8 线（tier 4）、4 板 → 1 壳（tier 1）、3 板 → 1 管（tier 1）。
	 */
	public static void addSmithingRecipes() {
		smithToPlate(ModItems.ingot_helicopter, ModItems.plate_helicopter);
		smithToPlate(ModItems.ingot_erbium, ModItems.plate_erbium);
		smithToPlate(ModItems.ingot_platinum, ModItems.plate_platinum);

		smithToWire(ModItems.ingot_helicopter, ModItems.wire_helicopter);
		smithToWire(ModItems.ingot_erbium, ModItems.wire_erbium);
		smithToWire(ModItems.ingot_platinum, ModItems.wire_platinum);

		smithPlateToPart(ModItems.plate_helicopter, ModItems.shell_helicopter, 4, ModItems.pipe_helicopter, 3);
		smithPlateToPart(ModItems.plate_erbium, ModItems.shell_erbium, 4, ModItems.pipe_erbium, 3);
		smithPlateToPart(ModItems.plate_platinum, ModItems.shell_platinum, 4, ModItems.pipe_platinum, 3);

		MakeNTMGreatAgain.LOG.info("MNGAMetals: anvil recipes added");
	}

	private static void smithToPlate(Item ingot, Item plate) {
		AnvilRecipes.constructionRecipes.add(new AnvilConstructionRecipe(
				new ComparableStack(new ItemStack(ingot)),
				new AnvilOutput(new ItemStack(plate))).setTier(3));
	}

	private static void smithToWire(Item ingot, Item wire) {
		AnvilRecipes.constructionRecipes.add(new AnvilConstructionRecipe(
				new ComparableStack(new ItemStack(ingot)),
				new AnvilOutput(new ItemStack(wire, 8))).setTier(4));
	}

	private static void smithPlateToPart(Item plate, Item shell, int shellPlates,
			Item pipe, int pipePlates) {
		AnvilRecipes.constructionRecipes.add(new AnvilConstructionRecipe(
				new ComparableStack(plate, shellPlates),
				new AnvilOutput(new ItemStack(shell))).setTier(1));
		AnvilRecipes.constructionRecipes.add(new AnvilConstructionRecipe(
				new ComparableStack(plate, pipePlates),
				new AnvilOutput(new ItemStack(pipe))).setTier(1));
	}

	/** 锻压机：板模板 1 锭 → 1 板；线模板 1 锭 → 8 线。 */
	public static void addPressRecipes() {
		pressPair(ModItems.ingot_helicopter, ModItems.plate_helicopter, ModItems.wire_helicopter);
		pressPair(ModItems.ingot_erbium, ModItems.plate_erbium, ModItems.wire_erbium);
		pressPair(ModItems.ingot_platinum, ModItems.plate_platinum, ModItems.wire_platinum);

		MakeNTMGreatAgain.LOG.info("MNGAMetals: press recipes added");
	}

	private static void pressPair(Item ingot, Item plate, Item wire) {
		ComparableStack in = new ComparableStack(new ItemStack(ingot));
		PressRecipes.makeRecipe(StampType.PLATE, in, new ItemStack(plate));
		PressRecipes.makeRecipe(StampType.WIRE, in, new ItemStack(wire, 8));
	}

	/**
	 * 电弧焊机：2 铸造板 → 1 焊接板。
	 *
	 * <p>HBM 的焊接板没有铸造模具，本体全部靠这条配方产出
	 * （{@code ArcWelderRecipes} 里 {@code MAT_TITANIUM} 等条目即为此形态）。
	 * 材质必须自己补，否则八件部件里唯独焊接板无来源。</p>
	 */
	public static void addArcWelderRecipes() {
		// 消耗参照 HBM 同档金属：钛 600t/50k，铝 300t/10k
		weldPair("plateTripleHelicopter", ModItems.plate_welded_helicopter, 900, 10_000_000L);
		weldPair("plateTripleErbium", ModItems.plate_welded_erbium, 400, 20_000L);
		weldPair("plateTriplePlatinum", ModItems.plate_welded_platinum, 600, 50_000L);

		// 致密线：8 细线 → 1，与 HBM 的铜 / 混合 / 金三种完全同款
		densePair("wireFineHelicopter", ModItems.wire_dense_helicopter);
		densePair("wireFineErbium", ModItems.wire_dense_erbium);
		densePair("wireFinePlatinum", ModItems.wire_dense_platinum);

		MakeNTMGreatAgain.LOG.info("MNGAMetals: arc welder recipes added");
	}

	private static void weldPair(String castOre, Item weldedPlate, int duration, long consumption) {
		ArcWelderRecipes.recipes.add(new ArcWelderRecipe(
				new ItemStack(weldedPlate), duration, consumption,
				new OreDictStack(castOre, 2)));
	}

	private static void densePair(String wireOre, Item denseWire) {
		ArcWelderRecipes.recipes.add(new ArcWelderRecipe(
				new ItemStack(denseWire), 100, 10_000L,
				new OreDictStack(wireOre, 8)));
	}

	/**
	 * 工作台：2 锭 → 16 螺栓，与 HBM 的 {@code "#"/"#"} 一致。
	 *
	 * <p>螺栓是唯一没有铸造模具的形态（{@code ItemMold} 里没有 bolt），
	 * HBM 本体也是纯工作台配方，所以这里同样走工作台。</p>
	 */
	public static void addBoltRecipes() {
		boltPair(ModItems.ingot_helicopter, ModItems.bolt_helicopter);
		boltPair(ModItems.ingot_erbium, ModItems.bolt_erbium);
		boltPair(ModItems.ingot_platinum, ModItems.bolt_platinum);

		MakeNTMGreatAgain.LOG.info("MNGAMetals: bolt recipes added");
	}

	private static void boltPair(Item ingot, Item bolt) {
		GameRegistry.addShapedRecipe(new ItemStack(bolt, 16),
				"#",
				"#",
				'#', ingot);
	}

	// =========================================================================================
	// ===== [坩埚/铸造] ③ 铸造表刷新 —— 决定「三材质能不能在 NEI/铸造机里浇出来」 =====
	// =========================================================================================

	/**
	 * 重建铸造表并自检。<b>这一步不做，「坩埚能熔但模具浇不出」就是必然。</b>
	 *
	 * <p>原因：{@code CrucibleRecipes.getMoldRecipes()} 是「懒加载 + 一次性缓存」——
	 * 首次被调用时，把<b>当时</b>的 {@code Mats.orderedList} 与全部模具做一次笛卡尔积，
	 * 结果存进静态字段 {@code moldRecipes}，之后永不重算。若这张表在本模组注册材质
	 * <b>之前</b>就被别处触发过（整合包里其它模组提前访问 NEI 页、或被配方查看器的
	 * 预扫描触发，都很常见），三个新材质就永远进不了铸造表，表现正好是：
	 * <b>坩埚能熔，但模具浇不出来、NEI 里也查不到</b>。</p>
	 *
	 * <p>做法：用反射把 {@code moldRecipes} 清空，下一次 {@code getMoldRecipes()} 会重新
	 * 遍历一遍（此时三材质已经在 {@code Mats.orderedList} 里了）。字段是 HBM 自己的
	 * （不是 Minecraft 的），reobf 不会改名，所以正式包里同样有效。</p>
	 *
	 * <p><b>日志怎么读</b>（搜 {@code MNGAMetals: castable}）：</p>
	 * <ul>
	 * <li>{@code castable [Platinum] 13/26 -> ...} —— <b>正常</b>。分子是「能浇的模具数」；
	 * 分母是 HBM 当前注册的模具总数（1.0.27 内置 26 个，若 {@code config/hbmShapes.json}
	 * 里加了自定义形状会更大，所以只看分子别记分母）。
	 * 三材质能浇的 <b>13</b> 个是：nugget、ingot、plate、wire、plate_cast、wire_dense、
	 * shell、pipe、block，加它们的 L 号多件模具（ingots ×9 / plates ×9 / plates_cast ×3 /
	 * wires_dense ×9）。剩下的 billet、枪械基础件（barrel/receiver/mechanism/stock/grip）
	 * 以及 blade/stamp/c9/c50 这几类 {@code MoldMulti} 白名单里没有本模组材质，浇不出是正常的。</li>
	 * <li>{@code castable [Helium] 2/26} —— 正常，氦只有 {@code ingotHelium} 一条矿辞，
	 * 所以只有 ingot 与 ingots ×9 能浇。</li>
	 * <li>分子变小（比如 8）—— 某几个矿辞前缀写错了，对照
	 * {@link #registerOne(String, Item, Item, Item, Item, Item, Item, Item, Item, Item, Item, Item, Block)}
	 * 右边注释逐个核。</li>
	 * <li>分子为 {@code 0} —— 材质名没对上（{@code Mats.df("...")} 与矿辞 {@code ingot...} 后面的
	 * 名字大小写/拼写不一致），或者 postInit 里这个方法没被调用。</li>
	 * <li>日志里完全没有 {@code castable} —— {@code CommonProxy.postInit} 末尾的调用被注释掉
	 * 或抛异常了。</li>
	 * </ul>
	 */
	public static void refreshCastingTable() {
		// 1) 反射清空 HBM 的懒加载缓存（清空后 getMoldRecipes() 会重新建表）
		try {
			java.lang.reflect.Field field = CrucibleRecipes.class.getDeclaredField("moldRecipes");
			field.setAccessible(true);
			((java.util.List<?>) field.get(null)).clear();
		} catch (Throwable t) {
			MakeNTMGreatAgain.LOG.warn("MNGAMetals: failed to reset casting recipe cache", t);
		}

		// 2) 触发重建，日志里会看到全表条目数
		MakeNTMGreatAgain.LOG.info("MNGAMetals: casting table rebuilt, "
				+ CrucibleRecipes.getMoldRecipes().size() + " entries");

		// 3) 自检：逐个材质跑一遍全部模具，把能浇的模具名打到日志里
		for (NTMMaterial mat : new NTMMaterial[] { MAT_HELICOPTER, MAT_ERBIUM, MAT_PLATINUM, MAT_HELIUM }) {
			StringBuilder sb = new StringBuilder();
			int ok = 0;
			for (Mold mold : ItemMold.molds) {
				if (mold.getOutput(mat) != null) {
					ok++;
					sb.append(mold.name).append(' ');
				}
			}
			MakeNTMGreatAgain.LOG.info("MNGAMetals: castable [" + mat.names[0] + "] "
					+ ok + "/" + ItemMold.molds.size() + " -> " + sb.toString().trim());

			if (ok == 0) {
				MakeNTMGreatAgain.LOG.warn("MNGAMetals: [" + mat.names[0] + "] casts NOTHING - "
						+ "check that ore dict names match MaterialShapes.name() and that the "
						+ "material name in Mats.df() equals the ore dict suffix");
			}
		}
	}

	/** 粉碎机：1 锭 → 1 粉；1 块 → 9 粉。 */
	public static void addShredderRecipes() {
		shredPair(ModItems.ingot_helicopter, ModItems.powder_helicopter, ModBlocks.block_helicopter);
		shredPair(ModItems.ingot_erbium, ModItems.powder_erbium, ModBlocks.block_erbium);
		shredPair(ModItems.ingot_platinum, ModItems.powder_platinum, ModBlocks.block_platinum);
		// Threx 不在材质表里（没有熔体），但粉末与储存块的粉碎关系照旧
		shredPair(ModItems.ingot_threx, ModItems.powder_threx, ModBlocks.block_threx);

		// 配方表变了，NEI 的映射要重建
		ShredderRecipes.neiShredderRecipes = null;
		MakeNTMGreatAgain.LOG.info("MNGAMetals: shredder recipes added");
	}

	private static void shredPair(Item ingot, Item powder, Block block) {
		ShredderRecipes.setRecipe(ingot, new ItemStack(powder, 1));
		ShredderRecipes.setRecipe(block, new ItemStack(powder, 9));
	}

	/** 工作台：9 锭 ↔ 1 块，与 HBM 本体一致。 */
	public static void addBlockRecipes() {
		blockPair(ModItems.ingot_helicopter, ModBlocks.block_helicopter);
		blockPair(ModItems.ingot_erbium, ModBlocks.block_erbium);
		blockPair(ModItems.ingot_platinum, ModBlocks.block_platinum);
		blockPair(ModItems.ingot_threx, ModBlocks.block_threx);

		MakeNTMGreatAgain.LOG.info("MNGAMetals: storage block recipes added");
	}

	private static void blockPair(Item ingot, Block block) {
		GameRegistry.addShapedRecipe(new ItemStack(block),
				"III", "III", "III", 'I', ingot);
		GameRegistry.addShapelessRecipe(new ItemStack(ingot, 9), block);
	}

	// =========================================================================================
	// ===== [坩埚/铸造] ④ 固化机 / 液化机 / 坩埚合金 —— 这三个是真的「写了配方」的 =====
	// =========================================================================================

	/** 防重入：{@code GenericRecipes.register} 遇到重名会抛异常，兜底注入跑两遍就炸。 */
	private static boolean crucibleAlloyAdded = false;

	/**
	 * <b>固化机（Solidifier）</b>：2000 mB 氦-3 → 1 个氦气凝锭。
	 *
	 * <p>HBM 的固化配方表是 {@code SolidificationRecipes.recipes}，键是<b>流体类型</b>、
	 * 值是 {@code (毫桶数, 产物 ItemStack)} 一对。表里是「一个流体最多一个产物」，
	 * 原版没有 {@code HELIUM3} 条目，所以直接 put 不会覆盖任何东西。</p>
	 *
	 * <p>2000 mB 是刻意定的：原版金属是 100 mB/锭，但氦是气体，量给小了不科幻。</p>
	 */
	public static void addSolidificationRecipe() {
		SolidificationRecipes.recipes.put(Fluids.HELIUM3,
				new Pair<Integer, ItemStack>(2000, new ItemStack(ModItems.ingot_helium, 1)));
		MakeNTMGreatAgain.LOG.info("MNGAMetals: solidifier 2000 mB HELIUM3 -> 1 ingot_helium");
	}

	/**
	 * <b>液化机（Liquefactor）</b>：1 个氦气凝锭 → 2000 mB 氦-3，与固化机严格互逆。
	 *
	 * <p>{@code LiquefactionRecipes.recipes} 的键有两种：{@code String}（矿辞名）或
	 * {@code ComparableStack}（具体物品）。这里用后者。</p>
	 *
	 * <p><b>坑</b>：{@code ComparableStack.equals()} 会比较 {@code stacksize}，
	 * 而查表时 HBM 用的是「1 个」的 stack，所以这里也必须显式建成 1 个，
	 * 否则 {@code recipes.get()} 永远返回 null，表现为「机器转但不出液体」。</p>
	 */
	public static void addLiquefactionRecipe() {
		LiquefactionRecipes.recipes.put(
				new ComparableStack(new ItemStack(ModItems.ingot_helium, 1)),
				new FluidStack(Fluids.HELIUM3, 2000));
		MakeNTMGreatAgain.LOG.info("MNGAMetals: liquefactor 1 ingot_helium -> 2000 mB HELIUM3");
	}

	/**
	 * <b>坩埚合金</b>：1 氦气凝锭 + 3 锂粒 + 6 钴粒 + 3 铂粒 + 1 铒粒
	 * → 2 耐摔合金锭 + 4 粒炉渣。
	 *
	 * <p><b>这是整条链路里最容易写错的一处</b>：{@code CrucibleRecipe} 的
	 * {@code inputs/outputs} 是 {@code MaterialStack}，也就是「<b>熔体量</b>」，
	 * 不是物品。三条规矩：</p>
	 * <ul>
	 * <li>数字一律用 {@code MaterialShapes.XXX.q(n)} 算（锭 72、粒 8）。
	 * 直接写 1、3、6 会变成「1 quanta = 1/72 个锭」，配方永远凑不满。</li>
	 * <li>物品是玩家自己丢进坩埚的：坩埚先按 {@code Mats.getMaterialsFromItem()}
	 * 把每个物品熔成熔体，再按本配方的熔体量扣。所以矿辞必须齐 ——
	 * 锂粒走 HBM 自带的 {@code nuggetLithium}、钴粒靠 {@link #registerOreDict()} 补的
	 * {@code nuggetCobalt}、铂粒/铒粒是本模组的 {@code nugget<材质>}。</li>
	 * <li>总量必须守恒：72 + 24 + 48 + 24 + 8 = 176，输出 144 + 32 = 176。
	 * HBM 的类注释特意警告过「输出不能比输入多」，否则坩埚会溢出。</li>
	 * </ul>
	 *
	 * <p>名字 {@code crucible.heliumalloy} 必须全局唯一。直接调
	 * {@code CrucibleRecipes.INSTANCE.register()} 撞名会抛 {@code IllegalStateException}，
	 * 而且 HBM 是「先入列表再查重」，异常被吃掉后表里会留下幽灵条目；
	 * 所以统一走 {@link RecipeRegistrar#add}（已存在就跳过）。</p>
	 */
	public static void addHeliumAlloyRecipe() {
		if (crucibleAlloyAdded) return;
		crucibleAlloyAdded = true;

		int i = MaterialShapes.INGOT.q(1);   // 72 quanta
		int n = MaterialShapes.NUGGET.q(1);  // 8 quanta

		RecipeRegistrar.add(CrucibleRecipes.INSTANCE, new CrucibleRecipe("crucible.heliumalloy")
				.setup(20, new ItemStack(ModItems.ingot_helicopter))
				.inputs(
						new MaterialStack(MAT_HELIUM, i),                // 1 氦气凝锭 = 72
						new MaterialStack(Mats.MAT_LITHIUM, n * 3),      // 3 锂粒  = 24
						new MaterialStack(Mats.MAT_COBALT, n * 6),       // 6 钴粒  = 48
						new MaterialStack(MAT_PLATINUM, n * 3),          // 3 铂粒  = 24
						new MaterialStack(MAT_ERBIUM, n))                // 1 铒粒  = 8
				.outputs(
						new MaterialStack(MAT_HELICOPTER, i * 2),        // 2 耐摔合金锭 = 144
						new MaterialStack(Mats.MAT_SLAG, n * 4)));       // 4 粒炉渣     = 32

		MakeNTMGreatAgain.LOG.info("MNGAMetals: crucible alloy "
				+ "1 He ingot + 3 Li + 6 Co + 3 Pt + 1 Er -> 2 Helicopter ingots + 4 slag");
	}
}

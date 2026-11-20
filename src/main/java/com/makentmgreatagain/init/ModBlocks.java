package com.makentmgreatagain.init;

import com.hbm.blocks.ModSoundTypes;
import com.hbm.items.block.ItemBlockBase;
import com.hbm.items.block.ItemBlockBlastInfo;
import com.hbm.lib.RefStrings;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.block.BlockSaltContainGravel;
import com.makentmgreatagain.block.BlockSaltContainRedSand;
import com.makentmgreatagain.block.BlockSaltContainSand;
import com.makentmgreatagain.blocks.BlockAdvancedAlloy;
import com.makentmgreatagain.blocks.BlockFluidCooler;
import com.makentmgreatagain.blocks.BlockMachineDrain;
import com.makentmgreatagain.blocks.BlockAlloyShredder;
import com.makentmgreatagain.blocks.BlockCoreFissure;
import com.makentmgreatagain.blocks.BlockCoreLavaExtractor;
import com.makentmgreatagain.blocks.BlockIndustrialGasTurbine;
import com.makentmgreatagain.blocks.BlockLargeCentrifuge;
import com.makentmgreatagain.blocks.BlockThrex;
import com.makentmgreatagain.blocks.BlockThrexShieldBrick;
import com.makentmgreatagain.blocks.BlockThrexShieldGlass;
import com.makentmgreatagain.blocks.BlockWirelessStation;
import com.makentmgreatagain.blocks.BlockArcHeater;
import com.makentmgreatagain.blocks.BlockBat9000Reborn;
import com.makentmgreatagain.blocks.BlockLargeCatalyticChemicalFactory;
import com.makentmgreatagain.blocks.BlockDeepSeaNodule;
import com.makentmgreatagain.blocks.BlockElectrolyzerReborn;
import com.makentmgreatagain.blocks.BlockElectricBlastFurnace;
import com.makentmgreatagain.blocks.BlockElectricBoiler;
import com.makentmgreatagain.blocks.BlockEmptyDeepSeaNodule;
import com.makentmgreatagain.blocks.BlockEnvironmentPump;
import com.makentmgreatagain.blocks.BlockHeatexReborn;
import com.makentmgreatagain.blocks.BlockHybridCatalyticFractionTower;
import com.makentmgreatagain.blocks.BlockIronMassStorage;
import com.makentmgreatagain.blocks.BlockNuclearExplosionGenerator;
import com.makentmgreatagain.blocks.BlockOreLeadZinc;
import com.makentmgreatagain.blocks.BlockOreRefractoryGold;
import com.makentmgreatagain.blocks.BlockOreScheelite;
import com.makentmgreatagain.blocks.BlockPipeAnchorAdvanced;
import com.makentmgreatagain.blocks.BlockRiserPipe;
import com.makentmgreatagain.blocks.BlockRiserRig;
import com.makentmgreatagain.blocks.BlockStorageMetal;
import com.makentmgreatagain.blocks.BlockAddonBattery;
import com.makentmgreatagain.blocks.BlockAddonCapacitor;
import com.makentmgreatagain.blocks.BatteryCellSpec;
import com.makentmgreatagain.items.ItemBlockHelicopter;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityFluidCooler;
import com.makentmgreatagain.tileentity.TileEntityMachineDrain;
import com.makentmgreatagain.tileentity.TileEntityArcHeater;
import com.makentmgreatagain.tileentity.TileEntityWirelessStation;
import com.makentmgreatagain.tileentity.TileEntityCoreLavaExtractor;
import com.makentmgreatagain.tileentity.TileEntityIndustrialGasTurbine;
import com.makentmgreatagain.tileentity.TileEntityLargeCentrifuge;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactory;
import com.makentmgreatagain.tileentity.TileEntityLargeCatalyticChemicalFactoryPort;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnace;
import com.makentmgreatagain.tileentity.TileEntityElectricBlastFurnacePort;
import com.makentmgreatagain.tileentity.TileEntityElectricBoiler;
import com.makentmgreatagain.tileentity.TileEntityDeepSeaNodule;
import com.makentmgreatagain.tileentity.TileEntityElectrolyzerReborn;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;
import com.makentmgreatagain.tileentity.TileEntityHybridCatalyticFractionTower;
import com.makentmgreatagain.tileentity.TileEntityIronMassStorage;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGenerator;
import com.makentmgreatagain.tileentity.TileEntityNuclearExplosionGeneratorPort;
import com.makentmgreatagain.tileentity.TileEntityPipeAnchorAdvanced;
import com.makentmgreatagain.tileentity.TileEntityRiserRig;
import com.makentmgreatagain.tileentity.TileEntityRiserPipe;

import java.util.HashMap;
import java.util.Map;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;

/**
 * 方块注册表（含多方块机器）。
 *
 * <h3>新增一个方块（照抄这段）</h3>
 * <pre>
 * // 1) 字段区
 * public static Block block_xxx;
 *
 * // 2) init() 里
 * block_xxx = new BlockXxx()
 *     .setBlockName("xxx")
 *     .setBlockTextureName("makentmgreatagain:xxx")
 *     .setHardness(5.0F)
 *     .setResistance(10.0F)
 *     .setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
 * block_xxx.setHarvestLevel("pickaxe", 2);   // 返回 void，不能链式
 * GameRegistry.registerBlock(block_xxx, "xxx");
 *
 * // 3) 有 TileEntity 就再注册一行
 * GameRegistry.registerTileEntity(TileEntityXxx.class, "makentmgreatagain.xxx");
 *
 * // 4) lang 用 tile.xxx.name（1.7.10 方块前缀是 tile.）+ 贴图 textures/blocks/xxx.png
 * </pre>
 *
 * <p>机器方块两种注册方式：默认 ItemBlock（普通）；{@code ItemBlockBase}
 * （掉落物要保留 NBT / 提示要显示存量时用，如储罐）。</p>
 *
 * <p>矿石只注册自己的独占矿辞，导出的 {@code oreLead} / {@code oreGold} / {@code oreTungsten}
 * 一律不注册 —— 一旦注册，别的模组和 HBM 离心机就会按普通矿消耗它，附属那条湿法线被直接跳过。</p>
 */
public class ModBlocks {

	public static Block block_advanced_alloy;
	/** 三材质的金属储存块（9 锭 = 1 块），耐摔合金块带四臂旋翼纹 */
	public static Block block_helicopter, block_erbium, block_platinum;
	/** Threx 块：抗爆 450000、硬度 100（黑曜石两倍）、钻石镐等级；物品态在背包内给缓慢 III */
	public static Block block_threx;
	/** Threx 建材：高产量屏蔽砖与连接式屏蔽玻璃 */
	public static Block threx_shield_brick, threx_shield_glass;
	public static Block block_alloy_shredder;
	/** 大型储罐（重生版） */
	public static Block block_bat9000_reborn;
	/** 环境地下泵 */
	public static Block environment_pump;
	/** 无线充电站（一格、可旋转、背面接电） */
	public static Block wireless_station;
	/** 集成式换热加热器 */
	public static Block heatex_reborn;
	/** 流体冷却机（3×3×7 多方块，6 个电力/流体通用接口；目前只有外壳、储罐与接口，内部逻辑待实现） */
	public static Block machine_fluid_cooler;
	/** 混合催化分馏塔（5×5×6，8 个流体口） */
	public static Block machine_hybrid_catalytic_fraction_tower;
	/** 大型催化化工厂（8×4×8，36 个「流体 + 物品 + 电力」通用接口） */
	public static Block machine_large_catalytic_chemical_factory;
	/** 电力高炉（5×5×7，10 个「库存 + 电力 + 流体」通用接口） */
	public static Block machine_electric_blast_furnace;
	public static Block machine_nuclear_explosion_generator;
	/** 小型钢排液管（单方块，容量 1000 mB，四侧可接流体，顶底不可接） */
	public static Block machine_drain;
	/** 小型钨排液管（单方块，容量 2000 mB，钨色加深贴图） */
	public static Block machine_drain_tungsten;
	/** 电锅炉（单方块，off/on 双态，移植自原版 1.0.27） */
	public static Block machine_electric_boiler_off, machine_electric_boiler_on;
	/** 小型电解机 */
	public static Block electrolyzer_reborn;
	/** 电弧加热器 */
	public static Block arc_heater;
	/** 大型离心机（5×5×3，沿用旧版回旋加速器模型） */
	public static Block large_centrifuge;
	public static Block core_lava_extractor;
	/** 工业燃气轮机（4×13×4 外观多方块，左右各 4 个通用接口：电力输出 + 流体收发） */
	public static Block industrial_gas_turbine;
	public static Block core_fissure;
	/** 高级管锚（25m） */
	public static Block pipe_anchor_advanced;
	/** 海面立管钻机（3×3×8 多方块，6 个通用接口） */
	public static Block riser_rig;
	public static Block riser_pipe;
	/** 铁质大规模存储单元（1000） */
	public static Block iron_mass_storage;
	/** 铅锌矿 */
	public static Block ore_leadzinc;
	/** 含砷金矿 */
	public static Block ore_refractory_gold;
	/** 白钨矿 */
	public static Block ore_scheelite;
	/** 铅锌矿（Duna 变体，只在火星 duna_rock 中生成） */
	public static Block ore_leadzinc_duna;
	/** 含砷金矿（Duna 变体） */
	public static Block ore_refractory_gold_duna;
	/** 白钨矿（Duna 变体） */
	public static Block ore_scheelite_duna;
	/** 深海结核混合物 */
	public static Block deep_sea_nodule;
	/** 空深海结核混合物（采空后留下） */
	public static Block empty_deep_sea_nodule;
	/** 含盐方块 */
	public static Block Block_Salt_Contain_Sand;
	public static Block Block_Salt_Contain_Red_Sand;
	public static Block Block_Salt_Contain_Gravel;

	/**
	 * 弃用单方块元件（电池 / 电容 / 电容总线），从 HBM 复制而来。
	 *
	 * <p>用 spec.name → Block 的映射持有：多方块成型时要按「结构里是哪块元件」
	 * 反查 {@link BatteryCellSpec} 拿容量与速率，所以保留一个可查的索引，
	 * 而不是散落成十几个 public static 字段。</p>
	 *
	 * <p>这些方块<b>没有 TE、不存电、无 GUI</b>，纯粹是携带数值的「铭牌」，
	 * 详见各自方块类的注释。</p>
	 */
	public static final Map<String, Block> batteryCells = new HashMap<String, Block>();

	/** 大型电池组：摆放外壳 / 端口 / 中心控制器 / 成型壳 */
	public static Block battery_array_casing;
	public static Block battery_array_port;
	public static Block battery_array_controller;
	public static Block battery_array_shell;

	public static void init() {
		block_advanced_alloy = new BlockAdvancedAlloy()
			.setBlockName("advanced_block_alloy")
			.setBlockTextureName("makentmgreatagain:advanced_block_alloy")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式，必须单独一行
		block_advanced_alloy.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(block_advanced_alloy, "advanced_block_alloy");

		// 三材质金属储存块
		// 耐摔合金块用专门的 ItemBlock：被破坏后的掉落物会自旋、到 120 转/分后升空
		block_helicopter = registerStorageBlock("block_helicopter", true);
		block_erbium = registerStorageBlock("block_erbium");
		block_platinum = registerStorageBlock("block_platinum");

		// Threx 块：数值写在 BlockThrex 里（抗爆 450000 / 硬度 100），这里只补挖掘等级。
		// 黑曜石要钻石镐，两倍硬度同样定在 3 级，否则木镐能挖动、时间就不成立。
		block_threx = new BlockThrex()
			.setBlockName("block_threx")
			.setBlockTextureName(MakeNTMGreatAgain.MODID + ":block_threx")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式
		block_threx.setHarvestLevel("pickaxe", 3);
		// 用专门 ItemBlock：在背包任意一格给缓慢 III（锭、粉是 II）。反射构造，必须单参 Block 构造
		GameRegistry.registerBlock(block_threx, com.makentmgreatagain.items.ItemBlockThrex.class, "block_threx");

		// Threx 屏蔽砖：以少量 Threx 芯材压制成高产量建材，辐射阻力低于实心块。
		threx_shield_brick = new BlockThrexShieldBrick()
			.setBlockName("threx_shield_brick")
			.setBlockTextureName(MakeNTMGreatAgain.MODID + ":threx_shield_brick")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		threx_shield_brick.setHarvestLevel("pickaxe", 3);
		GameRegistry.registerBlock(threx_shield_brick, ItemBlockBlastInfo.class, "threx_shield_brick");

		// Threx 屏蔽玻璃：复用 HBM 的 CT 连接渲染，玻璃声效与原版玻璃一致。
		threx_shield_glass = new BlockThrexShieldGlass()
			.setBlockName("threx_shield_glass")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		GameRegistry.registerBlock(threx_shield_glass, ItemBlockBlastInfo.class, "threx_shield_glass");

		// 铅锌矿（ISP 法提镉的起点）
		ore_leadzinc = new BlockOreLeadZinc()
			.setBlockName("ore_leadzinc")
			.setBlockTextureName("makentmgreatagain:ore_leadzinc")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_leadzinc.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_leadzinc, "ore_leadzinc");
		// 独占矿辞：注册 oreLead 会被别的模组当普通铅矿吃掉，镉就出不来
		net.minecraftforge.oredict.OreDictionary.registerOre("oreLeadzinc", ore_leadzinc);

		// 含砷金矿（毒砂型难处理矿，硫代硫酸盐浸金的原料）
		ore_refractory_gold = new BlockOreRefractoryGold()
			.setBlockName("ore_refractory_gold")
			.setBlockTextureName("makentmgreatagain:ore_refractory_gold")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_refractory_gold.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_refractory_gold, "ore_refractory_gold");
		// 独占矿辞：注册 oreGold 的话 HBM 离心机按普通金矿给金粉，这条湿法线就没意义
		net.minecraftforge.oredict.OreDictionary.registerOre("oreRefractoryGold", ore_refractory_gold);

		// 白钨矿（湿法炼钨的起点）
		ore_scheelite = new BlockOreScheelite()
			.setBlockName("ore_scheelite")
			.setBlockTextureName("makentmgreatagain:ore_scheelite")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_scheelite.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_scheelite, "ore_scheelite");
		// 独占矿辞：注册 oreTungsten 的话结晶器直接出钨晶、离心机给 4 份钨粉，整条链被跳过
		net.minecraftforge.oredict.OreDictionary.registerOre("oreScheelite", ore_scheelite);

		// ---------------- Duna（火星）变体 ----------------
		// 三个变体复用主世界方块类，掉落、硬度、精准采集行为完全一致，
		// 差别只在贴图（原石背景换成 duna_rock 的铁锈红）与生成维度。
		// 矿辞注册成同一个名字，走 OreDictStack 的配方（如 ModRecipes 的 oreLeadzinc 那条）自动覆盖；
		// 走 ComparableStack / addSmelting 精确方块的配方在 ScheeliteRecipes、ModRecipes 里另补。
		ore_leadzinc_duna = new BlockOreLeadZinc()
			.setBlockName("ore_leadzinc_duna")
			.setBlockTextureName("makentmgreatagain:ore_leadzinc_duna")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_leadzinc_duna.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_leadzinc_duna, "ore_leadzinc_duna");
		net.minecraftforge.oredict.OreDictionary.registerOre("oreLeadzinc", ore_leadzinc_duna);

		ore_refractory_gold_duna = new BlockOreRefractoryGold()
			.setBlockName("ore_refractory_gold_duna")
			.setBlockTextureName("makentmgreatagain:ore_refractory_gold_duna")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_refractory_gold_duna.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_refractory_gold_duna, "ore_refractory_gold_duna");
		net.minecraftforge.oredict.OreDictionary.registerOre("oreRefractoryGold", ore_refractory_gold_duna);

		ore_scheelite_duna = new BlockOreScheelite()
			.setBlockName("ore_scheelite_duna")
			// 白钨矿原石背景本来就是浅米色，和 Duna 铁锈红差距小，暂用主世界贴图
			.setBlockTextureName("makentmgreatagain:ore_scheelite")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		ore_scheelite_duna.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_scheelite_duna, "ore_scheelite_duna");
		net.minecraftforge.oredict.OreDictionary.registerOre("oreScheelite", ore_scheelite_duna);

		// 深海结核混合物：只在深海海床表层生成，后续由海底采集机处理
		deep_sea_nodule = new BlockDeepSeaNodule()
			.setBlockName("deep_sea_nodule")
			.setBlockTextureName("makentmgreatagain:deep_sea_nodule")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		deep_sea_nodule.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(deep_sea_nodule, "deep_sea_nodule");
		GameRegistry.registerTileEntity(TileEntityDeepSeaNodule.class, "makentmgreatagain.deep_sea_nodule");
		// 采空后留下的空结核（机器会继续开采它周围的结核）
		empty_deep_sea_nodule = new BlockEmptyDeepSeaNodule()
			.setBlockName("empty_deep_sea_nodule")
			.setBlockTextureName("makentmgreatagain:empty_deep_sea_nodule")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		empty_deep_sea_nodule.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(empty_deep_sea_nodule, "empty_deep_sea_nodule");
		// 自定义矿辞，避免 HBM 机器把它当普通矿吃掉；后续采集机直接判方块
		net.minecraftforge.oredict.OreDictionary.registerOre("oreDeepSeaNodule", deep_sea_nodule);

		// 含盐沙子
		Block_Salt_Contain_Sand = ((Block) new BlockSaltContainSand())
				.setBlockName("Block_Salt_Contain_Sand")
				.setBlockTextureName("makentmgreatagain:salt_contain_sand")
				.setHardness(0.5F)
				.setResistance(1.0F)
				.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		Block_Salt_Contain_Sand.setHarvestLevel("shovel", 0);
			GameRegistry.registerBlock(Block_Salt_Contain_Sand, "Block_Salt_Contain_Sand");
		// 含盐红沙
		Block_Salt_Contain_Red_Sand = ((Block) new BlockSaltContainRedSand())
			.setBlockName("Block_Salt_Contain_Red_Sand")
			.setBlockTextureName("makentmgreatagain:salt_contain_red_sand")
			.setHardness(0.5F)
			.setResistance(1.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		Block_Salt_Contain_Red_Sand.setHarvestLevel("shovel", 0);
		GameRegistry.registerBlock(Block_Salt_Contain_Red_Sand, "Block_Salt_Contain_Red_Sand");
		// 含盐沙砾
		Block_Salt_Contain_Gravel = ((Block) new BlockSaltContainGravel())
			.setBlockName("Block_Salt_Contain_Gravel")
			.setBlockTextureName("makentmgreatagain:salt_contain_gravel")
			.setHardness(0.5F)
			.setResistance(1.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		Block_Salt_Contain_Gravel.setHarvestLevel("shovel", 0);
		GameRegistry.registerBlock(Block_Salt_Contain_Gravel, "Block_Salt_Contain_Gravel");

		// 高级粉碎机（2×2×2 多方块）
		block_alloy_shredder = ((Block) new BlockAlloyShredder())
			.setBlockName("alloy_shredder")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		GameRegistry.registerBlock(block_alloy_shredder, "alloy_shredder");
		GameRegistry.registerTileEntity(TileEntityAlloyShredder.class, "makentmgreatagain.alloy_shredder");

		// 大型储罐：5×5 占地、高 4，容量 2,048,000 mB
		block_bat9000_reborn = ((Block) new BlockBat9000Reborn())
			.setBlockName("bat9000_reborn")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		// 用 ItemBlockBase 注册：掉落物保留液体 NBT、提示显示存量（IPersistentInfoProvider）
		GameRegistry.registerBlock(block_bat9000_reborn, ItemBlockBase.class, "bat9000_reborn");
		GameRegistry.registerTileEntity(TileEntityBat9000Reborn.class, "makentmgreatagain.bat9000_reborn");

		// 环境地下泵（3×1×1 多方块，四面可接电与流体）
		environment_pump = ((Block) new BlockEnvironmentPump())
			.setBlockName("environment_pump")
			.setBlockTextureName("makentmgreatagain:environment_pump")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		GameRegistry.registerBlock(environment_pump, "environment_pump");
		GameRegistry.registerTileEntity(TileEntityEnvironmentPump.class, "makentmgreatagain.environment_pump");

		// 无线充电站：一格机器，背面接电网，正面铜线圈 + 状态灯
		wireless_station = ((Block) new BlockWirelessStation())
			.setBlockName("wireless_station")
			.setBlockTextureName("makentmgreatagain:wstation_side")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		GameRegistry.registerBlock(wireless_station, "wireless_station");
		GameRegistry.registerTileEntity(TileEntityWirelessStation.class, "makentmgreatagain.wireless_station");

		// 配方：铜包钢外壳 + 电路板，中心一颗亚历山大变石当谐振核（无线输电的钥匙）
		GameRegistry.addRecipe(new ItemStack(wireless_station),
			"CPC",
			"PAP",
			"CBC",
			'P', com.hbm.items.ModItems.plate_steel,
			'C', com.hbm.items.ModItems.ingot_copper,
			'B', com.hbm.items.ModItems.circuit,
			'A', com.hbm.items.ModItems.gem_alexandrite);

		// 连接器：铜 + 电路板 + 红石 + 亚历山大变石，无序合成
		GameRegistry.addShapelessRecipe(new ItemStack(ModItems.wireless_connector),
			com.hbm.items.ModItems.ingot_copper,
			com.hbm.items.ModItems.circuit,
			com.hbm.items.ModItems.gem_alexandrite,
			net.minecraft.init.Items.redstone);

		// 集成式换热加热器（5×3×1 多方块，顶部 3×3 热面）
		heatex_reborn = ((Block) new BlockHeatexReborn())
			.setBlockName("heatex_reborn")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		heatex_reborn.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(heatex_reborn, "heatex_reborn");
		GameRegistry.registerTileEntity(TileEntityHeatexReborn.class, "makentmgreatagain.heatex_reborn");

		// 流体冷却机（3×3×7 多方块，六个电力/流体通用接口）：外形与接口照抄本体紧凑型压缩机。
		// 骨架阶段：只有外壳、模型、储罐与接口，没有配方与 GUI。
		machine_fluid_cooler = ((Block) new BlockFluidCooler())
			.setBlockName("machine_fluid_cooler")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_fluid_cooler.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_fluid_cooler, "machine_fluid_cooler");
		GameRegistry.registerTileEntity(TileEntityFluidCooler.class, "makentmgreatagain.machine_fluid_cooler");

		// 小型钢排液管（单方块，容量 1000 mB，四侧可接流体，顶底不可接）
		machine_drain = ((Block) new BlockMachineDrain())
			.setBlockName("machine_drain_steel")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_drain.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_drain, "machine_drain_steel");

		// 小型钨排液管（容量 2000 mB，贴图为按钨色加深的排液管贴图）
		machine_drain_tungsten = ((Block) new BlockMachineDrain(2000))
			.setBlockName("machine_drain_tungsten")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(8.0F)
			.setResistance(20.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_drain_tungsten.setHarvestLevel("pickaxe", 3);

		GameRegistry.registerBlock(machine_drain_tungsten, "machine_drain_tungsten");
		GameRegistry.registerTileEntity(TileEntityMachineDrain.class, "makentmgreatagain.machine_drain");

		// 混合催化分馏塔（5×5×6 多方块，每面 2 个流体口，共 8 个；可向上叠放）
		machine_hybrid_catalytic_fraction_tower = ((Block) new BlockHybridCatalyticFractionTower())
			.setBlockName("machine_hybrid_catalytic_fraction_tower")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_hybrid_catalytic_fraction_tower.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_hybrid_catalytic_fraction_tower, "machine_hybrid_catalytic_fraction_tower");
		GameRegistry.registerTileEntity(TileEntityHybridCatalyticFractionTower.class, "makentmgreatagain.machine_hybrid_catalytic_fraction_tower");
		GameRegistry.registerTileEntity(com.makentmgreatagain.tileentity.TileEntityHybridTowerPort.class, "makentmgreatagain.hybrid_tower_port");

		// 大型催化化工厂（8×4×8 多方块，顶面 12 个 + 前后各两排共 24 个通用接口，共 36 个）
		machine_large_catalytic_chemical_factory = ((Block) new BlockLargeCatalyticChemicalFactory())
			.setBlockName("machine_large_catalytic_chemical_factory")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_large_catalytic_chemical_factory.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_large_catalytic_chemical_factory, "machine_large_catalytic_chemical_factory");
		GameRegistry.registerTileEntity(TileEntityLargeCatalyticChemicalFactory.class, "makentmgreatagain.machine_large_catalytic_chemical_factory");
		GameRegistry.registerTileEntity(TileEntityLargeCatalyticChemicalFactoryPort.class, "makentmgreatagain.machine_large_catalytic_chemical_factory_port");

		// 电力高炉（5×5×7 多方块，底面外圈 8 个 + 背面烟道 1 个 + 顶部上料口 1 个通用接口）
		machine_electric_blast_furnace = ((Block) new BlockElectricBlastFurnace())
			.setBlockName("machine_electric_blast_furnace")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_electric_blast_furnace.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_electric_blast_furnace, "machine_electric_blast_furnace");
		GameRegistry.registerTileEntity(TileEntityElectricBlastFurnace.class, "makentmgreatagain.machine_electric_blast_furnace");
		GameRegistry.registerTileEntity(TileEntityElectricBlastFurnacePort.class, "makentmgreatagain.machine_electric_blast_furnace_port");

		// 可控核爆炸发生器（13×13×17 多方块，四个侧面各 5 个通用接口，共 20 个）
		machine_nuclear_explosion_generator = ((Block) new BlockNuclearExplosionGenerator())
			.setBlockName("machine_nuclear_explosion_generator")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_nuclear_explosion_generator.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_nuclear_explosion_generator, "machine_nuclear_explosion_generator");
		GameRegistry.registerTileEntity(TileEntityNuclearExplosionGenerator.class, "makentmgreatagain.machine_nuclear_explosion_generator");
		GameRegistry.registerTileEntity(TileEntityNuclearExplosionGeneratorPort.class, "makentmgreatagain.machine_nuclear_explosion_generator_port");

		// 电锅炉（单方块 off/on 双态，完整移植自原版 1.0.27：硬度5/抗爆10，on 态亮度1）
		machine_electric_boiler_off = ((Block) new BlockElectricBoiler(false))
			.setBlockName("machine_boiler_electric_off")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		machine_electric_boiler_off.setHarvestLevel("pickaxe", 2);

		machine_electric_boiler_on = ((Block) new BlockElectricBoiler(true))
			.setBlockName("machine_boiler_electric_on")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setLightLevel(1.0F);
		machine_electric_boiler_on.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(machine_electric_boiler_off, "machine_electric_boiler_off");
		GameRegistry.registerBlock(machine_electric_boiler_on, "machine_electric_boiler_on");
		GameRegistry.registerTileEntity(TileEntityElectricBoiler.class, "makentmgreatagain.electric_boiler");

		// 小型电解机（2×2×3 多方块，底部四个插座）
		electrolyzer_reborn = ((Block) new BlockElectrolyzerReborn())
			.setBlockName("electrolyzer_reborn")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		electrolyzer_reborn.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(electrolyzer_reborn, "electrolyzer_reborn");
		GameRegistry.registerTileEntity(TileEntityElectrolyzerReborn.class, "makentmgreatagain.electrolyzer_reborn");

		// 电弧加热器（3×2×3 多方块，3.5 格高的立式机柜，背部中间层接线）
		arc_heater = ((Block) new BlockArcHeater())
			.setBlockName("arc_heater")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		arc_heater.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(arc_heater, "arc_heater");
		GameRegistry.registerTileEntity(TileEntityArcHeater.class, "makentmgreatagain.arc_heater");

		// 大型离心机（5×5×3 多方块，沿用旧版回旋加速器模型，无右键交互）
		large_centrifuge = ((Block) new BlockLargeCentrifuge())
			.setBlockName("large_centrifuge")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(20.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		large_centrifuge.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(large_centrifuge, "large_centrifuge");
		GameRegistry.registerTileEntity(TileEntityLargeCentrifuge.class, "makentmgreatagain.large_centrifuge");

		// 地核熔岩开采机（旧版 AMS发射极移植；5×5×6 多方块，顶层中心+相邻4格共 5 个物品/流体/电力三合一接口，无 GUI）
		core_lava_extractor = ((Block) new BlockCoreLavaExtractor())
			.setBlockName("core_lava_extractor")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(20.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		core_lava_extractor.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(core_lava_extractor, "core_lava_extractor");
		GameRegistry.registerTileEntity(TileEntityCoreLavaExtractor.class, "makentmgreatagain.core_lava_extractor");

		// 工业燃气轮机（4×13×4 外观多方块，左右各 4 个通用接口：电力输出 + 流体收发）
		industrial_gas_turbine = ((Block) new BlockIndustrialGasTurbine())
			.setBlockName("industrial_gas_turbine")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(20.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		industrial_gas_turbine.setHarvestLevel("pickaxe", 2);

		// 用 HBM ItemBlockBase 注册：它会把方块的 ITooltipProvider.addInformation
		// 转发到物品提示（默认 ItemBlock 不转发，导致燃料效率简介不显示）
		GameRegistry.registerBlock(industrial_gas_turbine, com.hbm.items.block.ItemBlockBase.class, "industrial_gas_turbine");
		GameRegistry.registerTileEntity(TileEntityIndustrialGasTurbine.class, "makentmgreatagain.industrial_gas_turbine");

		// 地核熔岩裂隙（下界基岩层 3×3 生成，不可破坏无掉落，地核熔岩开采机的开采对象）
		core_fissure = ((Block) new BlockCoreFissure())
			.setBlockName("core_fissure")
			.setBlockTextureName("makentmgreatagain:core_fissure");

		GameRegistry.registerBlock(core_fissure, "core_fissure");

		// 海面立管钻机（3×3×8 多方块，±X 两面各 3 个电力/流体通用接口）
		riser_rig = ((Block) new BlockRiserRig())
			.setBlockName("riser_rig")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(20.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		riser_rig.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(riser_rig, "riser_rig");
		GameRegistry.registerTileEntity(TileEntityRiserRig.class, "makentmgreatagain.riser_rig");

		// 钻机泵送时在世界里逐格落下的输送管道（内部方块，不在创造模式物品栏里）
		riser_pipe = new BlockRiserPipe();
		GameRegistry.registerBlock(riser_pipe, "riser_pipe");
		GameRegistry.registerTileEntity(TileEntityRiserPipe.class, "makentmgreatagain.riser_pipe");

		// 铁质大规模存储单元：UI/功能照抄本体，容量 1000
		iron_mass_storage = new BlockIronMassStorage()
			.setBlockName("iron_mass_storage")
			.setStepSound(Block.soundTypeMetal)
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		// ItemBlockBase 会把方块实现的 ITooltipProvider 接到物品提示上
		GameRegistry.registerBlock(iron_mass_storage, ItemBlockBase.class, "iron_mass_storage");
		GameRegistry.registerTileEntity(TileEntityIronMassStorage.class, "makentmgreatagain.iron_mass_storage");

		// 高级管锚：属性照抄本体，只把最大连接距离从 10m 提到 25m
		pipe_anchor_advanced = new BlockPipeAnchorAdvanced()
			.setBlockName("pipe_anchor_advanced")
			.setStepSound(ModSoundTypes.pipe)
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES)
			// 粒子和原版管锚一样使用 HBM 的钢板贴图；外观差异交给连接处的 3D 法兰/铆钉
			.setBlockTextureName(RefStrings.MODID + ":block_steel");
		// 必须和本体一样用 ItemBlockBase 注册，否则方块实现的 ITooltipProvider 不会被物品提示调用
		GameRegistry.registerBlock(pipe_anchor_advanced, ItemBlockBase.class, "pipe_anchor_advanced");
		GameRegistry.registerTileEntity(TileEntityPipeAnchorAdvanced.class, "makentmgreatagain.pipe_anchor_advanced");

		// ---------------- 弃用单方块元件（电池 / 电容 / 电容总线）----------------
		// 从 HBM 复制而来：无 TE、不存电、无 GUI，纯粹携带数值，供大型多方块电池组
		// 成型时反查容量与放电速率。方块名统一加 mnga_ 前缀，避免和 HBM 原版撞名。
		// 注册名 = "mnga_" + spec.name；反查索引 batteryCells 用 spec.name 作键。
		registerBatteryCells();

		// 大型电池组（PWR 式成型）
		registerBatteryArray();

	}

	/** 大型电池组：外壳/端口/控制器/成型壳 + 两个 TE。 */
	private static void registerBatteryArray() {
		battery_array_casing = new com.makentmgreatagain.blocks.BlockBatteryArrayPart(false)
			.setBlockName("mnga_battery_array_casing");
		GameRegistry.registerBlock(battery_array_casing, com.hbm.items.block.ItemBlockBase.class, "mnga_battery_array_casing");

		battery_array_port = new com.makentmgreatagain.blocks.BlockBatteryArrayPart(true)
			.setBlockName("mnga_battery_array_port");
		GameRegistry.registerBlock(battery_array_port, com.hbm.items.block.ItemBlockBase.class, "mnga_battery_array_port");

		battery_array_controller = new com.makentmgreatagain.blocks.BlockBatteryArrayController()
			.setBlockName("mnga_battery_array_controller");
		GameRegistry.registerBlock(battery_array_controller, "mnga_battery_array_controller");
		GameRegistry.registerTileEntity(com.makentmgreatagain.tileentity.TileEntityBatteryArray.class, "makentmgreatagain.battery_array_controller");

		battery_array_shell = new com.makentmgreatagain.blocks.BlockBatteryArrayShell()
			.setBlockName("mnga_battery_array_shell");
		GameRegistry.registerBlock(battery_array_shell, "mnga_battery_array_shell");
		GameRegistry.registerTileEntity(com.makentmgreatagain.tileentity.TileEntityBatteryArrayShell.class, "makentmgreatagain.battery_array_shell");
	}

	/**
	 * 注册所有弃用单方块元件（4 电池 + 4 电容）。
	 *
	 * <p>两种方块类：</p>
	 * <ul>
	 *   <li>{@link BlockAddonBattery} —— 电池，普通方块带三面贴图；</li>
	 *   <li>{@link BlockAddonCapacitor} —— 储能电容，OBJ 模型渲染，底座恒朝下。</li>
	 * </ul>
	 *
	 * <p>都登记进 {@link #batteryCells}（spec.name → Block），多方块成型时按结构里
	 * 出现的是哪种元件反查 {@link BatteryCellSpec} 拿数值。</p>
	 *
	 * <p><b>必须用 {@code ItemBlockBase} 注册</b>：默认 ItemBlock 不会调用方块的
	 * {@code addInformation}，那样提示里的容量/速率就一个字都不显示。
	 * {@code ItemBlockBase} 会把 {@code ITooltipProvider} 接到物品提示上
	 * （与本体 pipe_anchor_advanced / iron_mass_storage 同一做法）。</p>
	 */
	private static void registerBatteryCells() {

		// 电池：4 种
		for(BatteryCellSpec spec : BatteryCellSpec.batteries()) {
			Block block = new BlockAddonBattery(spec).setBlockName("mnga_" + spec.name);
			GameRegistry.registerBlock(block, ItemBlockBase.class, "mnga_" + spec.name);
			batteryCells.put(spec.name, block);
		}

		// 电容：4 种（总线已删除）
		for(BatteryCellSpec spec : BatteryCellSpec.capacitors()) {
			Block block = new BlockAddonCapacitor(spec).setBlockName("mnga_" + spec.name);
			GameRegistry.registerBlock(block, ItemBlockBase.class, "mnga_" + spec.name);
			batteryCells.put(spec.name, block);
		}
	}

	/**
	 * 注册一块金属储存块。方块名与贴图名统一为 {@code block_<材质>}，
	 * 贴图对应 {@code textures/blocks/block_<材质>.png}。
	 *
	 * <p>方块同样要登记矿物词典（{@code block<材质>}）才能进坩埚熔成 648 quanta 的液体，
	 * 登记位置见 {@link MNGAMetals#registerOreDict()}。</p>
	 */
	private static Block registerStorageBlock(String name) {
		return registerStorageBlock(name, false);
	}

	/**
	 * 注册一块金属储存块。方块名与贴图名统一为 {@code block_<材质>}，
	 * 贴图对应 {@code textures/blocks/block_<材质>.png}。
	 *
	 * <p>方块同样要登记矿物词典（{@code block<材质>}）才能进坩埚熔成 648 quanta 的液体，
	 * 登记位置见 {@link MNGAMetals#registerOreDict()}。</p>
	 *
	 * @param spinning 是否改用 {@link ItemBlockHelicopter} 注册。改了之后掉落物是
	 *                 {@code EntityItemHelicopter}，落地会自旋、120 转/分后升空；
	 *                 保持 false 就是普通 ItemBlock，掉落物原地不动。
	 *                 Forge 通过反射调用 {@code ItemBlockHelicopter(Block)} 构造，
	 *                 所以那个类必须有单参 Block 构造。
	 */
	private static Block registerStorageBlock(String name, boolean spinning) {
		Block block = new BlockStorageMetal()
			.setBlockName(name)
			// 必须用本模组的 modid；RefStrings.MODID 是 HBM 的 "hbm"，
			// 写成它会把贴图指到 hbm:block_xxx，找不到就是紫黑方块
			.setBlockTextureName(MakeNTMGreatAgain.MODID + ":" + name)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式
		block.setHarvestLevel("pickaxe", 2);
		if (spinning) {
			GameRegistry.registerBlock(block, ItemBlockHelicopter.class, name);
		} else {
			GameRegistry.registerBlock(block, name);
		}
		return block;
	}
}

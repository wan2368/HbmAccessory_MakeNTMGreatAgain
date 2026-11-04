package com.makentmgreatagain.init;

import com.hbm.items.block.ItemBlockBase;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.block.BlockSaltContainGravel;
import com.makentmgreatagain.block.BlockSaltContainRedSand;
import com.makentmgreatagain.block.BlockSaltContainSand;
import com.makentmgreatagain.blocks.BlockAdvancedAlloy;
import com.makentmgreatagain.blocks.BlockAlloyShredder;
import com.makentmgreatagain.blocks.BlockBat9000Reborn;
import com.makentmgreatagain.blocks.BlockEnvironmentPump;
import com.makentmgreatagain.blocks.BlockHeatexReborn;
import com.makentmgreatagain.blocks.BlockOreLeadZinc;
import com.makentmgreatagain.blocks.BlockOreRefractoryGold;
import com.makentmgreatagain.tileentity.TileEntityAlloyShredder;
import com.makentmgreatagain.tileentity.TileEntityBat9000Reborn;
import com.makentmgreatagain.tileentity.TileEntityEnvironmentPump;
import com.makentmgreatagain.tileentity.TileEntityHeatexReborn;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class ModBlocks {

	public static Block block_advanced_alloy;
	public static Block block_alloy_shredder;
	/* ---- 「巨尻-9000」储罐（重生版）：复制自 HBM 已 @Deprecated 的 machine_bat9000 ---- */
	public static Block block_bat9000_reborn;
	/* ---- 环境地下泵：按群系产出不同流体 ---- */
	public static Block environment_pump;
	/* ---- 重生版换热器：热冷却液进、冷冷却液出，顶部 3×3 热面供热管抽热 ---- */
	public static Block heatex_reborn;
	/* ---- 铅锌矿：ISP 法提镉的起点，只注册 oreLeadzinc 矿辞 ---- */
	public static Block ore_leadzinc;
	/* ---- 含砷金矿：难处理矿，只注册 oreRefractoryGold，绝不注册 oreGold ---- */
	public static Block ore_refractory_gold;
	//含盐物品
	public static Block Block_Salt_Contain_Sand;
	public static Block Block_Salt_Contain_Red_Sand;
	public static Block Block_Salt_Contain_Gravel;

	public static void init() {
		block_advanced_alloy = new BlockAdvancedAlloy()
			.setBlockName("advanced_block_alloy")
			.setBlockTextureName("makentmgreatagain:advanced_block_alloy")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		block_advanced_alloy.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(block_advanced_alloy, "advanced_block_alloy");

		// 铅锌矿（PbS·ZnS 共生矿，镉富集在锌端，是 ISP 法提镉的起点）
		ore_leadzinc = new BlockOreLeadZinc()
			.setBlockName("ore_leadzinc")
			.setBlockTextureName("makentmgreatagain:ore_leadzinc")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		ore_leadzinc.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_leadzinc, "ore_leadzinc");
		// 独占矿辞：绝不注册 oreLead，否则会被别的模组当普通铅矿消耗，镉就出不来
		net.minecraftforge.oredict.OreDictionary.registerOre("oreLeadzinc", ore_leadzinc);

		// 含砷金矿（毒砂型难处理矿，硫代硫酸盐浸金的原料）
		ore_refractory_gold = new BlockOreRefractoryGold()
			.setBlockName("ore_refractory_gold")
			.setBlockTextureName("makentmgreatagain:ore_refractory_gold")
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		ore_refractory_gold.setHarvestLevel("pickaxe", 2);
		GameRegistry.registerBlock(ore_refractory_gold, "ore_refractory_gold");
		// 独占矿辞：一旦注册 oreGold，HBM 离心机就会按普通金矿给它 2~3 个金粉，
		// 这条湿法线当场失去意义。这里的"没人认"是设计的一部分。
		net.minecraftforge.oredict.OreDictionary.registerOre("oreRefractoryGold", ore_refractory_gold);

		//含盐沙子
		Block_Salt_Contain_Sand = ((Block) new BlockSaltContainSand())
				.setBlockName("Block_Salt_Contain_Sand")
				.setBlockTextureName("makentmgreatagain:salt_contain_sand")
				.setHardness(0.5F)
				.setResistance(1.0F)
				.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
			// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		Block_Salt_Contain_Sand.setHarvestLevel("shovel", 0);
			GameRegistry.registerBlock(Block_Salt_Contain_Sand, "Block_Salt_Contain_Sand");
		//含盐红沙
		Block_Salt_Contain_Red_Sand = ((Block) new BlockSaltContainRedSand())
			.setBlockName("Block_Salt_Contain_Red_Sand")
			.setBlockTextureName("makentmgreatagain:salt_contain_red_sand")
			.setHardness(0.5F)
			.setResistance(1.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		Block_Salt_Contain_Red_Sand.setHarvestLevel("shovel", 0);
		GameRegistry.registerBlock(Block_Salt_Contain_Red_Sand, "Block_Salt_Contain_Red_Sand");
		//含盐沙砾
		Block_Salt_Contain_Gravel = ((Block) new BlockSaltContainGravel())
			.setBlockName("Block_Salt_Contain_Gravel")
			.setBlockTextureName("makentmgreatagain:salt_contain_gravel")
			.setHardness(0.5F)
			.setResistance(1.0F)
			.setCreativeTab(ModCreativeTabs.TAB_BLOCKS);
		// setHarvestLevel 返回 void，不能链式调用，必须单独一行
		Block_Salt_Contain_Gravel.setHarvestLevel("shovel", 0);
		GameRegistry.registerBlock(Block_Salt_Contain_Gravel, "Block_Salt_Contain_Gravel");

		// 高级粉碎机（2×2×2 多块机器）
		block_alloy_shredder = ((Block) new BlockAlloyShredder())
			.setBlockName("alloy_shredder")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		GameRegistry.registerBlock(block_alloy_shredder, "alloy_shredder");
		GameRegistry.registerTileEntity(TileEntityAlloyShredder.class, "makentmgreatagain.alloy_shredder");

		// 「巨尻-9000」储罐（重生版）：5x5 占地、高 4 的多方块液体储罐，容量 2,048,000 mB
		block_bat9000_reborn = ((Block) new BlockBat9000Reborn())
			.setBlockName("bat9000_reborn")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		// 用 HBM 的 ItemBlockBase 注册：储罐掉落物保留液体 NBT、且物品提示能显示存量，
		// 这两点都依赖 ItemBlockBase.addInformation 里的 IPersistentInfoProvider 分支
		GameRegistry.registerBlock(block_bat9000_reborn, ItemBlockBase.class, "bat9000_reborn");
		GameRegistry.registerTileEntity(TileEntityBat9000Reborn.class, "makentmgreatagain.bat9000_reborn");

		// 环境地下泵：3×1×1 多方块，四面均可接电与流体
		environment_pump = ((Block) new BlockEnvironmentPump())
			.setBlockName("environment_pump")
			.setBlockTextureName("makentmgreatagain:environment_pump")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);

		GameRegistry.registerBlock(environment_pump, "environment_pump");
		GameRegistry.registerTileEntity(TileEntityEnvironmentPump.class, "makentmgreatagain.environment_pump");

		// 集成式换热加热器：5×3×1 多方块，两端各三个流体口，顶部 3×3 热面
		heatex_reborn = ((Block) new BlockHeatexReborn())
			.setBlockName("heatex_reborn")
			.setBlockTextureName("minecraft:iron_block")
			.setHardness(5.0F)
			.setResistance(10.0F)
			.setCreativeTab(ModCreativeTabs.TAB_MACHINES);
		heatex_reborn.setHarvestLevel("pickaxe", 2);

		GameRegistry.registerBlock(heatex_reborn, "heatex_reborn");
		GameRegistry.registerTileEntity(TileEntityHeatexReborn.class, "makentmgreatagain.heatex_reborn");

	}
}

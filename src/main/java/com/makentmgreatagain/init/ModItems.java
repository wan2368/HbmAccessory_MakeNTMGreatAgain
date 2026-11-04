package com.makentmgreatagain.init;
import com.hbm.handler.ability.IToolHarvestAbility;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.makentmgreatagain.items.ItemBladesAlloyReborn;

import com.hbm.handler.ability.IToolAreaAbility;
import com.hbm.handler.ability.IWeaponAbility;
import com.hbm.items.tool.ItemSwordAbility;
import com.hbm.items.tool.ItemToolAbility;
import com.hbm.items.tool.ItemToolAbility.EnumToolType;
import com.makentmgreatagain.items.ItemAdvancedAlloy;
import com.makentmgreatagain.items.ItemArmorAlloyReborn;
import com.makentmgreatagain.items.ItemHoeReborn;
import cpw.mods.fml.common.registry.GameRegistry;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.items.ItemCigaretteDrx;
import com.makentmgreatagain.items.ItemFluidDestroyer;
import com.makentmgreatagain.items.ItemMeth;
import com.makentmgreatagain.items.ItemWithDesc;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;

public class ModItems {
    
    public static Item ingot_advanced_alloy;
    public static Item cmb_electrode;
	/* ---- 石墨粉（HBM石墨锭粉碎产物）---- */
	public static Item powder_graphite;
	/* ---- 盐粉 ---- */
	public static Item powder_salt;
	/* ---- 铅锌精矿：铅锌矿浮选后的混合精矿，ISP 法进烧结机的原料 ---- */
	public static Item leadzinc_concentrate;
	/* ---- 铅锌烧结块：精矿氧化焙烧后的产物，硫进烟气、金属变氧化物 ---- */
	public static Item leadzinc_sinter;
	/* ---- 蓝粉：鼓风炉炉顶没能冷凝成液锌的细粉，镉真正富集的地方 ---- */
	public static Item bp_blue_powder;
	/* ---- 锌粉（ISP）：蓝粉浸出液电积出来的锌，注册 dustZinc 但不注册锌锭 ---- */
	public static Item powder_zinc_isp;
	/* ---- 白砷（三氧化二砷）：脱砷浸出液用 SO2 还原出来的中间体，要再经碳热还原才成砷粒 ---- */
	public static Item arsenic_trioxide;
	/* ---- 硫酸钠（元明粉）：亚硫酸钠给氯气脱氯后的钠盐出口，也是硫酸盐法制浆的补硫剂 ---- */
	public static Item powder_sodium_sulfate;
	/* ---- 砷浸出渣：硫代硫酸盐浸金后的废渣，砷与硫富集在这里，回 ⑨ 脱砷再提一次 ---- */
	public static Item arsenical_residue;

	/* ==================================================================================
	 *  白钨矿湿法链（CaWO₄ → 钨粉）
	 *
	 *  这条链和 HBM 原版钨矿的定位正好相反：原版钨矿丢熔炉直接出锭（SmeltingRecipes:38），
	 *  白钨矿丢熔炉什么都不会发生。它必须先粉碎，再走碱浸—酸化—焙烧—氢还原四道工序。
	 *  所以下面这套中间品不是"好看但没用"的过渡物，每一个都是链上不可跳过的实体。
	 * ================================================================================== */

	/* ---- 白钨矿粉：白钨矿粉碎后的解离产物，湿法链唯一的入口 ---- */
	public static Item powder_scheelite;
	/* ---- 白钨精矿：重选富集产物，当前链走的是全粉碱浸，这张先注册留作待用 ---- */
	public static Item scheelite_concentrate;
	/* ---- 白钨浸出渣：碱浸后剩下的不溶物，铍、锆、砷全在这里，本身不是废料而是矿源 ---- */
	public static Item scheelite_leach_residue;
	/* ---- 钨酸：酸化沉钨得到的黄色水合物 H₂WO₄，焙烧脱水后才是三氧化钨 ---- */
	public static Item tungstic_acid;
	/* ---- 三氧化钨：钨酸焙烧产物 WO₃，氢还原的直接原料 ---- */
	public static Item tungsten_trioxide;
	/* ---- 砷渣：浸出渣分选后的富砷尾渣，只有热解炉氧化焙烧才出白砷 ---- */
	public static Item arsenic_cake;
	/* ---- 铍精矿：浸出渣分选出的绿柱石类精矿，熔炉一比一出铍锭 ---- */
	public static Item beryllium_concentrate;
	/* ---- 锆精矿：轻质芳烃萃取才拿得到的锆英石精矿，熔炉一比一出锆锭 ---- */
	public static Item zirconium_concentrate;
	/* ---- 石膏 ---- */
	public static Item gypsum;
	/* ---- 天青石：盐水加硫酸沉出来的锶矿，硫酸锶。锶线的唯一矿源 ---- */
	public static Item celestite;
	/* ---- 氧化锶：天青石煅烧后的中间体，铝热还原前驱 ---- */
	public static Item strontium_oxide;
	/* ---- 木框架：结晶器把木浆凝成原木用的通用模具 ---- */
	public static Item wood_frame;
	/* ---- 生胶：胶体凝聚出来的未硫化胶块，硫化后才成橡胶 ---- */
	public static Item raw_rubber;
	/* ---- 高纯炭黑：可燃冰天然气不完全燃烧的气相产物，粒径与灰分都远优于细烟灰 ---- */
	public static Item carbon_black_pure;
	/* ---- 氧化铁粉 ---- */
	public static Item powder_oxidate_iron;
	/* ---- 碳化钨粉---- */
	public static Item powder_carbon_tungsten_mixture;
	/* ---- 高强度碳化钨粉---- */
	public static Item powder_cobalt_tungsten_mixture;
	/* ---- 高强度碳化钨锭---- */
	public static Item powder_cobalt_tungsten_ingot;
	/* ---- 高级合金刀片（重生版）*/
	public static Item blades_alloy_reborn;
	/* ---- 高级合金刀片破碎（重生版）*/
	public static Item scrap_blades_alloy_reborn;
	/* ---- 史诗无限水罐*/
	public static Item inf_water_mk3;
	/* ---- 空装配模板（用于砧合成等级模板）---- */
	public static Item blank_template;
		
    /* ---- 高级合金装备（重生版）：复制自 HBM 中已标记 @Deprecated 的 alloy 系列 ---- */
    public static Item alloy_sword_reborn;
    public static Item alloy_helmet_reborn;
    public static Item alloy_plate_reborn;
    public static Item alloy_legs_reborn;
    public static Item alloy_boots_reborn;
    public static Item alloy_pickaxe_reborn;
    public static Item alloy_axe_reborn;
    public static Item alloy_shovel_reborn;
    public static Item alloy_hoe_reborn;
	//drx香烟
	public static Item cigarette_drx;

	/* ---- 流体销毁罐 ---- */
	public static Item inf_destroy;
	public static Item inf_destroy_mk2;
	public static Item inf_destroy_mk3;

	/* ---- 甲基苯丙胺产线 ---- */
	/** 甲基苯丙胺粗品：还原胺化直接得到的未提纯晶体 */
	public static Item meth_impure;
	/** 甲基苯丙胺晶体：溶剂重结晶后的纯品 */
	public static Item meth_crystal;
	/** 成品药片：(2R)-N-甲基-1-苯基丙-2-胺，可食用 */
	public static Item meth_pill;
	
	/* ----高强度碳化钨镐---- */
	public static Item cobalt_tungsten_pickaxe;
	/* ----高强度碳化钨铲---- */
	public static Item cobalt_tungsten_shovel;
	/* ----高强度碳化钨斧---- */
	public static Item cobalt_tungsten_axe;
	/* ----高强度碳化钨锄---- */
	public static Item cobalt_tungsten_hoe;
	/* ----高强度碳化钨剑---- */
	public static Item cobalt_tungsten_sword;
	/* ----碳化钨护甲----*/
	public static Item cobalt_tungsten_helmet;
	public static Item cobalt_tungsten_plate;
	public static Item cobalt_tungsten_legs;
	public static Item cobalt_tungsten_boots;

	/* ---- 高级固态燃料：三期硝基甲烷线的高能固体推进剂，燃烧 12000 tick ---- */
	public static Item solid_fuel_adv;

	/* ---- 电力悬浮作业背包（矢量/悬浮双模式喷气背包） ---- */
	public static Item hover_jetpack;

	/* ---- 动力装甲电池组 Mk4：装进盔甲后电量上限 ×3（即 +200%） ---- */
	public static Item armor_battery_mk4;
	/* ---- 动力装甲电池组 Mk4：装进盔甲后电量上限 ×3（即 +200%） ---- */
	public static Item armor_battery_mk5;
	
    public static void init() {
        System.out.println("[MakeNTMGreatAgain] ModItems initialized.");

        // 动力装甲电池组 Mk4：与 HBM Mk3 同款 ItemModBattery，只是倍率 2D → 3D
        // （HBM 里 mod 是乘数：maxPower * mod；Mk3 的 2D = 上限翻倍 = +100%，
        //   所以"+200%"= 三倍上限 = 3D）
        armor_battery_mk4 = new com.hbm.items.armor.ItemModBattery(3D);
        armor_battery_mk4.setUnlocalizedName("armor_battery_mk4");
        armor_battery_mk4.setTextureName("makentmgreatagain:armor_battery_mk4");
        armor_battery_mk4.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(armor_battery_mk4, "armor_battery_mk4");

		armor_battery_mk5 = new com.hbm.items.armor.ItemModBattery(5D);
		armor_battery_mk5.setUnlocalizedName("armor_battery_mk5");
		armor_battery_mk5.setTextureName("makentmgreatagain:armor_battery_mk5");
		armor_battery_mk5.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(armor_battery_mk5, "armor_battery_mk5");

        // 电力悬浮作业背包（构造里已设名称/贴图/创造栏）
        hover_jetpack = new com.makentmgreatagain.items.ItemHoverJetPack();
        GameRegistry.registerItem(hover_jetpack, "hover_jetpack");
		

		// 空装配模板：贴图直接复用 HBM 的装配机模板
		blank_template = new Item();
		blank_template.setUnlocalizedName("blank_template");
		blank_template.setTextureName("hbm:blueprints");
		blank_template.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(blank_template, "blank_template");

		// 高级固态燃料：硝基甲烷 + 硝酸 + 固体燃料 压制而成，燃烧时间 12000 tick（在 ModFuelHandler 里登记）
		solid_fuel_adv = new Item();
		solid_fuel_adv.setUnlocalizedName("solid_fuel_adv");
		solid_fuel_adv.setTextureName("makentmgreatagain:solid_fuel_adv");
		solid_fuel_adv.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(solid_fuel_adv, "solid_fuel_adv");

		// 史诗级无限
		inf_water_mk3 = new ItemInfiniteFluid(Fluids.WATER, 5000);
		inf_water_mk3.setUnlocalizedName("inf_water_mk3");
		inf_water_mk3.setMaxStackSize(1);
		inf_water_mk3.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		inf_water_mk3.setTextureName("makentmgreatagain:inf_water_mk3");
		GameRegistry.registerItem(inf_water_mk3, inf_water_mk3.getUnlocalizedName());

		// 流体销毁罐
		// 参数：每tick销毁量, 普通腐蚀自毁, 强腐蚀自毁, 可销毁反物质
		inf_destroy = new ItemFluidDestroyer(25, true, false, false);
		inf_destroy.setUnlocalizedName("inf_destroy");
		inf_destroy.setTextureName("makentmgreatagain:inf_destroy");
		inf_destroy.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(inf_destroy, "inf_destroy");

		inf_destroy_mk2 = new ItemFluidDestroyer(50, false, true, false);
		inf_destroy_mk2.setUnlocalizedName("inf_destroy_mk2");
		inf_destroy_mk2.setTextureName("makentmgreatagain:inf_destroy_mk2");
		inf_destroy_mk2.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(inf_destroy_mk2, "inf_destroy_mk2");

		inf_destroy_mk3 = new ItemFluidDestroyer(200, false, false, true);
		inf_destroy_mk3.setUnlocalizedName("inf_destroy_mk3");
		inf_destroy_mk3.setTextureName("makentmgreatagain:inf_destroy_mk3");
		inf_destroy_mk3.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(inf_destroy_mk3, "inf_destroy_mk3");
        
        // 高级合金锭：稀有度设为 epic，显示紫名
        ingot_advanced_alloy = new ItemAdvancedAlloy().setRarity(EnumRarity.epic);
        ingot_advanced_alloy.setUnlocalizedName("ingot_advanced_alloy");
        ingot_advanced_alloy.setTextureName("makentmgreatagain:ingot_advanced_alloy");
        ingot_advanced_alloy.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(ingot_advanced_alloy, "ingot_advanced_alloy");

		// 高级合金刀片
		blades_alloy_reborn = new ItemBladesAlloyReborn();
		blades_alloy_reborn.setUnlocalizedName("alloy_blades_reborn");
		blades_alloy_reborn.setTextureName("makentmgreatagain:blades_advanced_alloy");
		blades_alloy_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		blades_alloy_reborn.setMaxStackSize(1);
		GameRegistry.registerItem(blades_alloy_reborn, "alloy_blades_reborn");

		// 高级合金刀片破损
		scrap_blades_alloy_reborn = new Item();
		scrap_blades_alloy_reborn.setUnlocalizedName("scrap_blades_alloy_reborn");
		scrap_blades_alloy_reborn.setTextureName("makentmgreatagain:scrap_advanced");
		scrap_blades_alloy_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(scrap_blades_alloy_reborn, "scrap_blades_alloy_reborn");

		// 石墨粉：HBM 石墨锭进粉碎机产出（配方注册在 ModRecipes.addGraphiteShredderRecipe）
		powder_graphite = new Item();
		powder_graphite.setUnlocalizedName("powder_graphite");
		powder_graphite.setTextureName("makentmgreatagain:powder_graphite");
		powder_graphite.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_graphite, "powder_graphite");

		
		// 氧化铁粉
		powder_oxidate_iron = new Item();
		powder_oxidate_iron.setUnlocalizedName("powder_oxidate_iron");
		powder_oxidate_iron.setTextureName("makentmgreatagain:powder_oxidate_iron");
		powder_oxidate_iron.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_oxidate_iron, "powder_oxidate_iron");

		// 碳化钨粉
		powder_carbon_tungsten_mixture = new Item();
		powder_carbon_tungsten_mixture.setUnlocalizedName("powder_carbon_tungsten_mixture");
		powder_carbon_tungsten_mixture.setTextureName("makentmgreatagain:powder_carbon_tungsten_mixture");
		powder_carbon_tungsten_mixture.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_carbon_tungsten_mixture, "powder_carbon_tungsten_mixture");
		
		// 盐粉
		powder_salt = new Item();
		powder_salt.setUnlocalizedName("powder_salt");
		powder_salt.setTextureName("makentmgreatagain:powder_salt");
		powder_salt.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_salt, "powder_salt");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustSalt", powder_salt);

		// 铅锌精矿：浮选产物，灰色矿粉团
		// 用 ItemWithDesc：现在没配 .desc 条目，显示与普通 Item 完全一致；
		// 以后想加说明或改名字颜色，语言文件加一行 / 这里加一行 setNameColor 即可
		leadzinc_concentrate = new ItemWithDesc();
		leadzinc_concentrate.setUnlocalizedName("leadzinc_concentrate");
		leadzinc_concentrate.setTextureName("makentmgreatagain:leadzinc_concentrate");
		leadzinc_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(leadzinc_concentrate, "leadzinc_concentrate");

		// 铅锌烧结块：氧化焙烧产物，多孔灰块
		leadzinc_sinter = new ItemWithDesc();
		leadzinc_sinter.setUnlocalizedName("leadzinc_sinter");
		leadzinc_sinter.setTextureName("makentmgreatagain:leadzinc_sinter");
		leadzinc_sinter.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(leadzinc_sinter, "leadzinc_sinter");

		// 蓝粉：ISP 鼓风炉的含镉细粉，蓝灰色
		bp_blue_powder = new ItemWithDesc();
		bp_blue_powder.setUnlocalizedName("bp_blue_powder");
		bp_blue_powder.setTextureName("makentmgreatagain:bp_blue_powder");
		bp_blue_powder.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(bp_blue_powder, "bp_blue_powder");

		// 锌粉（ISP）：注册 dustZinc —— 二期原版没有锌，不注册等于没有产出；
		// 注册后 GT 那边的黄铜、镀锌类配方也能直接吃这一份
		powder_zinc_isp = new ItemWithDesc();
		powder_zinc_isp.setUnlocalizedName("powder_zinc_isp");
		powder_zinc_isp.setTextureName("makentmgreatagain:powder_zinc_isp");
		powder_zinc_isp.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_zinc_isp, "powder_zinc_isp");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustZinc", powder_zinc_isp);

		// 白砷（As2O3）：脱砷浸出液通 SO2 还原析出的白色晶体粉末，剧毒，必须再过一道碳热还原
		arsenic_trioxide = new ItemWithDesc();
		arsenic_trioxide.setUnlocalizedName("arsenic_trioxide");
		arsenic_trioxide.setTextureName("makentmgreatagain:arsenic_trioxide");
		arsenic_trioxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenic_trioxide, "arsenic_trioxide");

		// 硫酸钠（元明粉）：白色结晶粉末。脱氯的钠盐出口 + 硫酸盐法制浆的补硫剂
		powder_sodium_sulfate = new ItemWithDesc();
		powder_sodium_sulfate.setUnlocalizedName("powder_sodium_sulfate");
		powder_sodium_sulfate.setTextureName("makentmgreatagain:powder_sodium_sulfate");
		powder_sodium_sulfate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_sodium_sulfate, "powder_sodium_sulfate");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustSodiumSulfate", powder_sodium_sulfate);

		// 砷浸出渣：硫代硫酸盐浸金后的残渣，灰绿偏土色
		arsenical_residue = new ItemWithDesc();
		arsenical_residue.setUnlocalizedName("arsenical_residue");
		arsenical_residue.setTextureName("makentmgreatagain:arsenical_residue");
		arsenical_residue.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenical_residue, "arsenical_residue");

		// ---------------- 白钨矿湿法链 ----------------
		// 白钨矿粉：只注册 dustScheelite。
		// 【红线】绝不注册 dustTungsten —— 一旦注册，玩家就能拿它直接顶替钨粉，
		// 碱浸、酸化、焙烧、氢还原四道工序全部作废，白钨矿退化成"多一步粉碎的普通钨矿"。
		powder_scheelite = new ItemWithDesc();
		powder_scheelite.setUnlocalizedName("powder_scheelite");
		powder_scheelite.setTextureName("makentmgreatagain:powder_scheelite");
		powder_scheelite.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_scheelite, "powder_scheelite");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustScheelite", powder_scheelite);

		// 白钨精矿：浮选/重选的富集产物。当前配方链走全粉碱浸，这张先留着，
		// 以后要做"精矿再富集"或者给别家模组当中间体时不必再动物品表。
		scheelite_concentrate = new ItemWithDesc();
		scheelite_concentrate.setUnlocalizedName("scheelite_concentrate");
		scheelite_concentrate.setTextureName("makentmgreatagain:scheelite_concentrate");
		scheelite_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(scheelite_concentrate, "scheelite_concentrate");

		// 浸出渣：碱浸的不溶物。它不是废物——铍、锆、砷都在这一堆里，
		// 三级处理（水洗 / 轻质芳烃萃取 / 氧化焙烧）决定你能从中拿走什么。
		scheelite_leach_residue = new ItemWithDesc();
		scheelite_leach_residue.setUnlocalizedName("scheelite_leach_residue");
		scheelite_leach_residue.setTextureName("makentmgreatagain:scheelite_leach_residue");
		scheelite_leach_residue.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(scheelite_leach_residue, "scheelite_leach_residue");

		// 钨酸：酸化沉钨的产物，黄色水合物
		tungstic_acid = new ItemWithDesc();
		tungstic_acid.setUnlocalizedName("tungstic_acid");
		tungstic_acid.setTextureName("makentmgreatagain:tungstic_acid");
		tungstic_acid.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(tungstic_acid, "tungstic_acid");

		// 三氧化钨：焙烧脱水后的 WO₃，整条链里颜色最醒目的一步
		tungsten_trioxide = new ItemWithDesc();
		tungsten_trioxide.setUnlocalizedName("tungsten_trioxide");
		tungsten_trioxide.setTextureName("makentmgreatagain:tungsten_trioxide");
		tungsten_trioxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(tungsten_trioxide, "tungsten_trioxide");

		// 砷渣：注意和上面的 arsenical_residue（含砷金矿的浸出渣）不是同一个东西。
		// 那个是硫代硫酸盐浸金的废渣，这个专指白钨矿浸出渣分选出来的富砷尾料。
		arsenic_cake = new ItemWithDesc();
		arsenic_cake.setUnlocalizedName("arsenic_cake");
		arsenic_cake.setTextureName("makentmgreatagain:arsenic_cake");
		arsenic_cake.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenic_cake, "arsenic_cake");

		// 铍精矿 / 锆精矿：都能直接进熔炉烧成锭，1:1，和 HBM 自己的铍矿、锆矿一致
		beryllium_concentrate = new ItemWithDesc();
		beryllium_concentrate.setUnlocalizedName("beryllium_concentrate");
		beryllium_concentrate.setTextureName("makentmgreatagain:beryllium_concentrate");
		beryllium_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(beryllium_concentrate, "beryllium_concentrate");

		zirconium_concentrate = new ItemWithDesc();
		zirconium_concentrate.setUnlocalizedName("zirconium_concentrate");
		zirconium_concentrate.setTextureName("makentmgreatagain:zirconium_concentrate");
		zirconium_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(zirconium_concentrate, "zirconium_concentrate");

		// 石膏：海水 + 硫酸 的产物，水泥缓凝剂、回转炉制硫的原料
		gypsum = new Item();
		gypsum.setUnlocalizedName("gypsum");
		gypsum.setTextureName("makentmgreatagain:gypsum");
		gypsum.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(gypsum, "gypsum");

		// 天青石（SrSO4）
		celestite = new Item();
		celestite.setUnlocalizedName("celestite");
		celestite.setTextureName("makentmgreatagain:celestite");
		celestite.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(celestite, "celestite");

		// 氧化锶（SrO）
		strontium_oxide = new Item();
		strontium_oxide.setUnlocalizedName("strontium_oxide");
		strontium_oxide.setTextureName("makentmgreatagain:strontium_oxide");
		strontium_oxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(strontium_oxide, "strontium_oxide");

		// 木框架：16 木棍 + 4 钢脚手架 压出来的通用模具，结晶器里配任何一种木浆凝成原木
		wood_frame = new Item();
		wood_frame.setUnlocalizedName("wood_frame");
		wood_frame.setTextureName("makentmgreatagain:wood_frame");
		wood_frame.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(wood_frame, "wood_frame");

		// 生胶：胶体 + 硫酸 凝聚出来的未硫化胶块，再掺硫粉硫化才成橡胶
		raw_rubber = new Item();
		raw_rubber.setUnlocalizedName("raw_rubber");
		raw_rubber.setTextureName("makentmgreatagain:raw_rubber");
		raw_rubber.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(raw_rubber, "raw_rubber");

		// 高纯炭黑
		carbon_black_pure = new Item();
		carbon_black_pure.setUnlocalizedName("carbon_black_pure");
		carbon_black_pure.setTextureName("makentmgreatagain:carbon_black_pure");
		carbon_black_pure.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(carbon_black_pure, "carbon_black_pure");

		// 高强度碳化钨粉
		powder_cobalt_tungsten_mixture = new Item();
		powder_cobalt_tungsten_mixture.setUnlocalizedName("powder_cobalt_tungsten_mixture");
		powder_cobalt_tungsten_mixture.setTextureName("makentmgreatagain:powder_cobalt_tungsten_mixture");
		powder_cobalt_tungsten_mixture.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_cobalt_tungsten_mixture, "powder_cobalt_tungsten_mixture");

		// 高强度碳化钨锭
		powder_cobalt_tungsten_ingot = new Item();
		powder_cobalt_tungsten_ingot.setUnlocalizedName("powder_cobalt_tungsten_ingot");
		powder_cobalt_tungsten_ingot.setTextureName("makentmgreatagain:powder_cobalt_tungsten_ingot");
		powder_cobalt_tungsten_ingot.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_cobalt_tungsten_ingot, "powder_cobalt_tungsten_ingot");
		
        // CMB 电极：dineutronium 属于 HBM 终局材料，稀有度用 rare（青名）比较相称
        cmb_electrode = new ItemAdvancedAlloy().setRarity(EnumRarity.rare);
        cmb_electrode.setUnlocalizedName("cmb_.electrode");
        cmb_electrode.setTextureName("makentmgreatagain:cmb_.electrode");
        cmb_electrode.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		cmb_electrode.setMaxStackSize(1);
        GameRegistry.registerItem(cmb_electrode, "cmb_.electrode");

		// DRX 香烟
		cigarette_drx = new ItemCigaretteDrx();
		cigarette_drx.setUnlocalizedName("cigarette_drx");
		cigarette_drx.setTextureName("makentmgreatagain:cigarette_drx");
		cigarette_drx.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(cigarette_drx, "cigarette_drx");

		// 甲基苯丙胺产线：粗品 → 晶体 → 药片
		meth_impure = new Item();
		meth_impure.setUnlocalizedName("meth_impure");
		meth_impure.setTextureName("makentmgreatagain:meth_impure");
		meth_impure.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(meth_impure, "meth_impure");

		meth_crystal = new Item();
		meth_crystal.setUnlocalizedName("meth_crystal");
		meth_crystal.setTextureName("makentmgreatagain:meth_crystal");
		meth_crystal.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(meth_crystal, "meth_crystal");

		meth_pill = new ItemMeth();
		meth_pill.setUnlocalizedName("meth_pill");
		meth_pill.setTextureName("makentmgreatagain:meth_pill");
		meth_pill.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(meth_pill, "meth_pill");
		

        // 高级合金装备（重生版）
        registerAlloyReborn();
    }


    private static void registerAlloyReborn() {
        // ---- 剑 ----
        alloy_sword_reborn = new ItemSwordAbility(8F, 0, MNGAMaterials.TOOL_ALLOY)
                .addAbility(IWeaponAbility.STUN, 0);
        alloy_sword_reborn.setUnlocalizedName("alloy_sword_reborn");
        alloy_sword_reborn.setTextureName("makentmgreatagain:alloy_sword_reborn");
        alloy_sword_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_sword_reborn, "alloy_sword_reborn");
		

		//碳化钨斧
		cobalt_tungsten_axe = new ItemToolAbility(14F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.AXE)
			.addAbility(IToolAreaAbility.RECURSION, 4)
			.addAbility(IWeaponAbility.BEHEADER, 2);
		cobalt_tungsten_axe.setUnlocalizedName("cobalt_tungsten_axe");
		cobalt_tungsten_axe.setMaxDamage(6000);
		cobalt_tungsten_axe.setTextureName("makentmgreatagain:cobalt_tungsten_axe");
		cobalt_tungsten_axe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_axe, "cobalt_tungsten_axe");

		//碳化钨铲
		cobalt_tungsten_shovel = new ItemToolAbility(7F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.SHOVEL)
			.addAbility(IToolAreaAbility.RECURSION, 4)
			.addAbility(IToolAreaAbility.HAMMER, 2)
			.addAbility(IToolHarvestAbility.SILK, 0);
		cobalt_tungsten_shovel.setUnlocalizedName("cobalt_tungsten_shovel");
		cobalt_tungsten_shovel.setMaxDamage(6000);
		cobalt_tungsten_shovel.setTextureName("makentmgreatagain:cobalt_tungsten_shovel");
		cobalt_tungsten_shovel.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_shovel, "cobalt_tungsten_shovel");

		//碳化钨锄
		cobalt_tungsten_hoe = new ItemHoeReborn(MNGAMaterials.TOOL_ALLOY);
		cobalt_tungsten_hoe.setUnlocalizedName("cobalt_tungsten_hoe");
		cobalt_tungsten_hoe.setMaxDamage(6000);
		cobalt_tungsten_hoe.setTextureName("makentmgreatagain:cobalt_tungsten_hoe");
		cobalt_tungsten_hoe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_hoe, "cobalt_tungsten_hoe");

		// ---- 碳化钨剑 ----
		cobalt_tungsten_sword = new ItemSwordAbility(18F, 0, MNGAMaterials.TOOL_ALLOY)
			.addAbility(IWeaponAbility.STUN, 1)
		    .addAbility(IWeaponAbility.BEHEADER, 0)
			.addAbility(IWeaponAbility.BOBBLE, 0);
		cobalt_tungsten_sword.setUnlocalizedName("cobalt_tungsten_sword");
		cobalt_tungsten_sword.setMaxDamage(6000);
		cobalt_tungsten_sword.setTextureName("makentmgreatagain:cobalt_tungsten_sword");
		cobalt_tungsten_sword.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_sword, "cobalt_tungsten_sword");

		//碳化钨镐
		cobalt_tungsten_pickaxe = new ItemToolAbility(9F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.PICKAXE)
			.addAbility(IToolAreaAbility.RECURSION, 2)
			.addAbility(IToolAreaAbility.HAMMER, 1)
			.addAbility(IToolHarvestAbility.SILK, 0)
			.addAbility(IToolHarvestAbility.LUCK, 1);
		cobalt_tungsten_pickaxe.setUnlocalizedName("cobalt_tungsten_pickaxe");
		cobalt_tungsten_pickaxe.setMaxDamage(6000);
		cobalt_tungsten_pickaxe.setTextureName("makentmgreatagain:cobalt_tungsten_pickaxe");
		cobalt_tungsten_pickaxe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_pickaxe, "cobalt_tungsten_pickaxe");
		
		// 碳化钨护甲
		// ---- 护甲四件 ----
		// 第三个参数是穿上后渲染的护甲模型贴图，指向已复制到本模组资源目录的 alloy_1/alloy_2.png
		cobalt_tungsten_helmet = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 0, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png");
		cobalt_tungsten_helmet.setUnlocalizedName("cobalt_tungsten_helmet");
		cobalt_tungsten_helmet.setMaxDamage(4500);
		cobalt_tungsten_helmet.setTextureName("makentmgreatagain:cobalt_tungsten_helmet");
		cobalt_tungsten_helmet.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_helmet, "cobalt_tungsten_helmet");

		cobalt_tungsten_plate = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 1, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png");
		cobalt_tungsten_plate.setUnlocalizedName("cobalt_tungsten_plate");
		cobalt_tungsten_plate.setMaxDamage(4500);
		cobalt_tungsten_plate.setTextureName("makentmgreatagain:cobalt_tungsten_plate");
		cobalt_tungsten_plate.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_plate, "cobalt_tungsten_plate");

		cobalt_tungsten_legs = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 2, "makentmgreatagain:textures/armor/cobalt_tungsten_2.png");
		cobalt_tungsten_legs.setUnlocalizedName("cobalt_tungsten_legs");
		cobalt_tungsten_legs.setMaxDamage(4500);
		cobalt_tungsten_legs.setTextureName("makentmgreatagain:cobalt_tungsten_legs");
		cobalt_tungsten_legs.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_legs, "cobalt_tungsten_legs");

		cobalt_tungsten_boots = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 3, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png");
		cobalt_tungsten_boots.setUnlocalizedName("cobalt_tungsten_boots");
		cobalt_tungsten_boots.setMaxDamage(4500);
		cobalt_tungsten_boots.setTextureName("makentmgreatagain:cobalt_tungsten_boots");
		cobalt_tungsten_boots.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_boots, "cobalt_tungsten_boots");
		
		// ---- 工具四件 ----
        alloy_pickaxe_reborn = new ItemToolAbility(5F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.PICKAXE)
                .addAbility(IToolAreaAbility.RECURSION, 0)
                .addAbility(IToolAreaAbility.HAMMER, 0);
        alloy_pickaxe_reborn.setUnlocalizedName("alloy_pickaxe_reborn");
        alloy_pickaxe_reborn.setTextureName("makentmgreatagain:alloy_pickaxe_reborn");
        alloy_pickaxe_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_pickaxe_reborn, "alloy_pickaxe_reborn");

        alloy_axe_reborn = new ItemToolAbility(7F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.AXE)
                .addAbility(IToolAreaAbility.RECURSION, 0)
                .addAbility(IWeaponAbility.BEHEADER, 0);
        alloy_axe_reborn.setUnlocalizedName("alloy_axe_reborn");
        alloy_axe_reborn.setTextureName("makentmgreatagain:alloy_axe_reborn");
        alloy_axe_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_axe_reborn, "alloy_axe_reborn");

        alloy_shovel_reborn = new ItemToolAbility(4F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.SHOVEL)
                .addAbility(IToolAreaAbility.RECURSION, 0);
        alloy_shovel_reborn.setUnlocalizedName("alloy_shovel_reborn");
        alloy_shovel_reborn.setTextureName("makentmgreatagain:alloy_shovel_reborn");
        alloy_shovel_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_shovel_reborn, "alloy_shovel_reborn");

        alloy_hoe_reborn = new ItemHoeReborn(MNGAMaterials.TOOL_ALLOY);
        alloy_hoe_reborn.setUnlocalizedName("alloy_hoe_reborn");
        alloy_hoe_reborn.setTextureName("makentmgreatagain:alloy_hoe_reborn");
        alloy_hoe_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_hoe_reborn, "alloy_hoe_reborn");

        // ---- 护甲四件 ----
        // 第三个参数是穿上后渲染的护甲模型贴图，指向已复制到本模组资源目录的 alloy_1/alloy_2.png
        alloy_helmet_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 0, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_helmet_reborn.setUnlocalizedName("alloy_helmet_reborn");
        alloy_helmet_reborn.setTextureName("makentmgreatagain:alloy_helmet_reborn");
        alloy_helmet_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_helmet_reborn, "alloy_helmet_reborn");

        alloy_plate_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 1, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_plate_reborn.setUnlocalizedName("alloy_plate_reborn");
        alloy_plate_reborn.setTextureName("makentmgreatagain:alloy_plate_reborn");
        alloy_plate_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_plate_reborn, "alloy_plate_reborn");

        alloy_legs_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 2, "makentmgreatagain:textures/armor/alloy_2.png");
        alloy_legs_reborn.setUnlocalizedName("alloy_legs_reborn");
        alloy_legs_reborn.setTextureName("makentmgreatagain:alloy_legs_reborn");
        alloy_legs_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_legs_reborn, "alloy_legs_reborn");

        alloy_boots_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 3, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_boots_reborn.setUnlocalizedName("alloy_boots_reborn");
        alloy_boots_reborn.setTextureName("makentmgreatagain:alloy_boots_reborn");
        alloy_boots_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_boots_reborn, "alloy_boots_reborn");

        System.out.println("[MakeNTMGreatAgain] Alloy Reborn gear registered (8 items).");
    }
}

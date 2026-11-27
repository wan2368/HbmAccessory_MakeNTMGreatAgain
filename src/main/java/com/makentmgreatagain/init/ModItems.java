package com.makentmgreatagain.init;
import com.hbm.handler.ability.IToolHarvestAbility;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.ItemInfiniteFluid;
import com.hbm.lib.RefStrings;
import com.makentmgreatagain.items.ItemBladesAlloyReborn;

import com.hbm.handler.ability.IToolAreaAbility;
import com.hbm.handler.ability.IWeaponAbility;
import com.hbm.items.tool.ItemSwordAbility;
import com.hbm.items.tool.ItemToolAbility;
import com.hbm.items.tool.ItemToolAbility.EnumToolType;
import com.makentmgreatagain.items.ItemArmorAlloyReborn;
import com.makentmgreatagain.items.ItemArmorHelicopter;
import com.makentmgreatagain.items.ItemRadioactiveConverter;
import com.makentmgreatagain.items.ItemAutoExtinguisher;
import com.makentmgreatagain.items.ItemHoeReborn;
import cpw.mods.fml.common.registry.GameRegistry;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.items.ItemCigaretteDrx;
import com.makentmgreatagain.items.ItemCompositeTank;
import com.makentmgreatagain.items.ItemFluidDestroyer;
import com.makentmgreatagain.items.ItemHelicopterDrop;
import com.makentmgreatagain.items.ItemToolAbilityHelicopter;
import com.makentmgreatagain.items.ItemMeth;
import com.makentmgreatagain.items.ItemNeutrinoGoggles;
import com.makentmgreatagain.items.ItemWithDesc;
import com.makentmgreatagain.items.ItemWirelessConnector;
import com.hbm.util.ArmorRegistry.HazardClass;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

/**
 * 物品注册表。
 *
 * <h3>新增一个物品（照抄这段）</h3>
 * <pre>
 * // 1) 字段区
 * public static Item powder_xxx;
 *
 * // 2) init() 里
 * powder_xxx = new ItemWithDesc();
 * powder_xxx.setUnlocalizedName("powder_xxx");
 * powder_xxx.setTextureName("makentmgreatagain:powder_xxx");
 * powder_xxx.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
 * GameRegistry.registerItem(powder_xxx, "powder_xxx");
 *
 * // 3) zh_CN.lang / en_US.lang 各一条 item.powder_xxx.name=中文名  ← 缺中文名拼音就搜不到
 * // 4) 贴图 assets/makentmgreatagain/textures/items/powder_xxx.png
 * // 5) 需要矿辞再加 OreDictionary.registerOre("dustXxx", powder_xxx)
 * </pre>
 *
 * <p>基类怎么挑：普通物品 {@code Item}；要写说明或名字颜色用 {@link ItemWithDesc}；
 * 护甲改装件参考 {@link ItemCompositeTank} / {@link ItemAutoExtinguisher}；
 * 工具与装备统一在 {@link #registerAlloyReborn()} 里，构造链照抄。</p>
 */
public class ModItems {

    public static Item ingot_advanced_alloy;
    public static Item cmb_electrode;
	/** 石墨粉 */
	public static Item powder_graphite;
	/** 盐粉 */
	public static Item powder_salt;
	/** 铅锌精矿 */
	public static Item leadzinc_concentrate;
	/** 铅锌烧结块 */
	public static Item leadzinc_sinter;
	/** 蓝粉 */
	public static Item bp_blue_powder;
	/** 锌粉，注册 dustZinc */
	public static Item powder_zinc_isp;
	/** 白砷 */
	public static Item arsenic_trioxide;
	/** 硫酸钠 */
	public static Item powder_sodium_sulfate;
	/** 砷浸出渣 */
	public static Item arsenical_residue;
	/** 铂合金催化转换器：混合催化分馏塔的催化剂，属性同原版催化转换器（堆叠1、无耐久） */
	public static Item platinum_converter;
	/** CMB 催化转换器：同上，深蓝紫配色 */
	public static Item cmb_converter;
	/** helicopter */
	public static Item ingot_helicopter;
	/** er */
	public static Item ingot_erbium;
	/** pt */
	public static Item ingot_platinum;
	/** 耐摔镐 */
	public static Item helicopter_pickaxe;
	/** 耐摔斧 */
	public static Item helicopter_axe;

	/* ---- HeLiCoPtEr / Pt / Er 三材质的八类部件，贴图单独放在 textures/items/parts/ ---- */

	/** 耐摔合金（HeLiCoPtEr）部件：板 / 铸造板 / 焊接板 / 线 / 致密线 / 螺栓 / 管 / 壳体 */
	public static Item plate_helicopter, plate_cast_helicopter, plate_welded_helicopter,
			wire_helicopter, wire_dense_helicopter, bolt_helicopter, pipe_helicopter, shell_helicopter;
	/** 铂部件 */
	public static Item plate_platinum, plate_cast_platinum, plate_welded_platinum,
			wire_platinum, wire_dense_platinum, bolt_platinum, pipe_platinum, shell_platinum;
	/** 铒部件 */
	public static Item plate_erbium, plate_cast_erbium, plate_welded_erbium,
			wire_erbium, wire_dense_erbium, bolt_erbium, pipe_erbium, shell_erbium;

	/* ---- 同上三材质的粒（nugget），贴图在 textures/items/parts/nugget_<材质>.png ---- */

	/** 耐摔合金粒 / 铒粒 / 铂粒。坩埚合金配方与 NEI 用，见 MNGAMetals#addHeliumAlloyRecipe */
	public static Item nugget_helicopter, nugget_erbium, nugget_platinum;

	/**
	 * 氦气凝锭。唯一来源是<b>固化机</b>：2000 mB 氦-3 → 1 个；
	 * 反向由<b>液化机</b>熔回 2000 mB 氦-3。也可以丢进坩埚熔成「氦」熔体
	 * （材质 id 21004，见 {@link MNGAMetals}）。
	 */
	public static Item ingot_helium;

	/**
	 * Threx 锭。四系复合锭，工作台配方见
	 * {@link com.makentmgreatagain.init.ModRecipes#addThrexIngotRecipe()}：
	 * 3 能量粉 + 2 Desh 锭 + 2 高强度碳化钨锭 + 2 星辉锭 → 3 个。
	 *
	 * <p>不配说明文字，语言文件里只有 {@code item.ingot_threx.name}；
	 * 基类是 {@link com.makentmgreatagain.items.ItemThrexSlowdown}（它又继承
	 * {@link ItemWithDesc}），将来要加 {@code .desc} 时不用换类。</p>
	 */
	public static Item ingot_threx;

	/** Threx 粉。粉碎机：1 锭 → 1 粉，1 块 → 9 粉（见 MNGAMetals#addShredderRecipes）。 */
	public static Item powder_threx;

	/* ---- 同上三材质的粉末，贴图在 textures/items/powder_<材质>.png ---- */

	/** 耐摔合金粉 / 铒粉 / 铂粉 */
	public static Item powder_helicopter, powder_erbium, powder_platinum;

	/* ---- 白钨矿湿法链：矿 → 粉 → 碱浸 → 酸化 → 焙烧 → 氢还原 → 钨粉 ---- */

	/** 白钨矿粉 */
	public static Item powder_scheelite;
	/** 白钨精矿（当前走全粉碱浸，先留作待用） */
	public static Item scheelite_concentrate;
	/** 白钨浸出渣 */
	public static Item scheelite_leach_residue;
	/** 钨酸 */
	public static Item tungstic_acid;
	/** 三氧化钨 */
	public static Item tungsten_trioxide;
	/** 砷渣（白钨浸出渣分选的富砷尾料，别和上面的砷浸出渣混） */
	public static Item arsenic_cake;
	/** 铍精矿 */
	public static Item beryllium_concentrate;
	/** 锆精矿 */
	public static Item zirconium_concentrate;
	/** 高钴多金属精矿（深海结核开采产物） */
	public static Item cobalt_polymetallic_concentrate;
	/** 重砂精矿（深海结核开采产物） */
	public static Item heavy_sand_concentrate;
	/** 稀土精矿（深海结核开采产物） */
	public static Item rare_earth_concentrate;

	/* =====================================================================================
	 *  [化冶线] 12 个中间产物 —— 深海结核 → 重砂/稀土/铂族/钴 四条湿法支线
	 *
	 *  搜「化冶线」可定位：本字段块 + init() 里同名横幅的注册块 + ModFluids 里
	 *  「化冶线」段的 5 个流体 + 两个 lang 文件末尾的条目。
	 *
	 *  这些东西目前只是「注册好了、能在创造模式里拿到」，还没有对应的加工配方；
	 *  配方要等机器链路（浸出/沉淀/煅烧）落地后再接。
	 * ===================================================================================== */

	/** 重砂混粉：结核洗选出的重矿物混合粉，钛锆稀土的共同原料 */
	public static Item heavy_sand_mix;
	/** 钍精矿：重砂磁选/重选后的钍富集物（独居石类） */
	public static Item thorium_concentrate;
	/** 铂族精矿：重砂里的铂族金属富集物 */
	public static Item pgm_concentrate;
	/** 稀土熟料：稀土精矿碱熔煅烧后的熟料，酸浸的前一步 */
	public static Item rare_earth_calcine;
	/** 碱熔砂：稀土精矿与碱烧结成的砂状结壳 */
	public static Item alkali_fused_sand;
	/** 稀土混粉：稀土熟料浸出、沉淀得到的混合稀土粉 */
	public static Item rare_earth_mix;
	/** 混合稀土氢氧化物：混合稀土溶液碱沉淀的滤饼，分离单一稀土的起点 */
	public static Item mixed_re_hydroxide;
	/** 铒富集粉：混合稀土经萃取分组后的铒富集物 */
	public static Item erbium_enriched_powder;
	/** 多金属混粉：结核里的镍铜钴锰混合硫化物/氢氧化物 */
	public static Item polymetallic_mix;
	/** 氢氧化钴：钴料液碱沉淀得到的蓝紫色滤饼 */
	public static Item cobalt_hydroxide;
	/** 阳极泥：粗铂电解精炼后沉在槽底的贵金属泥 */
	public static Item anode_mud;
	/** 氯铂酸钾：铂溶于王水后加氯化钾得到的黄色沉淀，铂精炼的中间体 */
	public static Item potassium_chloroplatinate;
	/** 高密度地核铂残渣：铂系熔体提铂后剩下的难溶渣，锇铱都锁在里面，需星流浆才能打开 */
	public static Item core_platinum_residue;
	/** 石膏 */
	public static Item gypsum;
	/** 天青石 */
	public static Item celestite;
	/** 氧化锶 */
	public static Item strontium_oxide;
	/** 木框架 */
	public static Item wood_frame;
	/** 生胶 */
	public static Item raw_rubber;
	/** 高纯炭黑 */
	public static Item carbon_black_pure;
	/** 氧化铁粉 */
	public static Item powder_oxidate_iron;
	/** 碳化钨粉 */
	public static Item powder_carbon_tungsten_mixture;
	/** 高强度碳化钨粉 */
	public static Item powder_cobalt_tungsten_mixture;
	/** 高强度碳化钨锭 */
	public static Item powder_cobalt_tungsten_ingot;
	/** 正电子胶囊（旧版 HBM 移除后由附属补回） */
	public static Item particle_positron;
	/** 正电子单元：聚变配方「正-反物质等离子体」在配方界面上的图标（cell 家族） */
	public static Item cell_positron;
	/** 高级合金刀片（重生版） */
	public static Item blades_alloy_reborn;
	/** 破损的高级合金刀片（重生版） */
	public static Item scrap_blades_alloy_reborn;
	/** 史诗无限水罐 */
	public static Item inf_water_mk3;
	/** 空模板（砧合成等级模板用） */
	public static Item blank_template;

    /** 高级合金装备（重生版）：HBM 已 @Deprecated 的 alloy 系列 */
    public static Item alloy_sword_reborn;
    public static Item alloy_helmet_reborn;
    public static Item alloy_plate_reborn;
    public static Item alloy_legs_reborn;
    public static Item alloy_boots_reborn;
    public static Item alloy_pickaxe_reborn;
    public static Item alloy_axe_reborn;
    public static Item alloy_shovel_reborn;
    public static Item alloy_hoe_reborn;
	/** '中华牌'香烟 */
	public static Item cigarette_drx;

	/** PEEK 特种工程塑料（聚醚醚酮）锭与粉末 */
	public static Item ingot_peek;
	public static Item powder_peek;

	/** PEEK 并行运算升级芯片（1~3 级，可叠加至最高 4x 并行） */
	public static Item upgrade_parallel_1;
	public static Item upgrade_parallel_2;
	public static Item upgrade_parallel_3;

	/** 流体销毁罐 */
	public static Item inf_destroy;
	public static Item inf_destroy_mk2;
	public static Item inf_destroy_mk3;

	/** 甲基苯丙胺粗品 */
	public static Item meth_impure;
	/** 甲基苯丙胺晶体 */
	public static Item meth_crystal;
	/** 药片 */
	public static Item meth_pill;

	/** 高强度碳化钨镐 */
	public static Item cobalt_tungsten_pickaxe;
	/** 高强度碳化钨锹 */
	public static Item cobalt_tungsten_shovel;
	/** 高强度碳化钨斧 */
	public static Item cobalt_tungsten_axe;
	/** 高强度碳化钨锄 */
	public static Item cobalt_tungsten_hoe;
	/** 高强度碳化钨剑 */
	public static Item cobalt_tungsten_sword;
	/** 高强度碳化钨护甲 */
	public static Item cobalt_tungsten_helmet;
	public static Item cobalt_tungsten_plate;
	public static Item cobalt_tungsten_legs;
	public static Item cobalt_tungsten_boots;
	/** 耐摔合金护甲（全套免伤但摔落除外，见 hook.HelicopterArmorHandler） */
	public static Item helicopter_helmet;
	public static Item helicopter_plate;
	public static Item helicopter_legs;
	public static Item helicopter_boots;
	/** 无线充电连接器：装进靴子的靴子专用槽 */
	public static Item wireless_connector;
	/** 稳定X晶体：稳定X晶体簇被破坏后的掉落物 */
	public static Item crystal_xen;
	/** 不稳态X晶体：SILEX 可见光分离产物（33%） */
	public static Item crystal_xen_unstable;
	/** 亚稳态X晶体：SILEX 可见光分离产物（13.3%） */
	public static Item crystal_xen_metastable;
	/** 乱码X晶体：SILEX 可见光分离稀有产物（3.3%），NEI 不显示但可正常分离 */
	public static Item crystal_xen_glitch;

	/** 高级固态燃料 */
	public static Item solid_fuel_adv;

	/** 电力悬浮作业背包 */
	public static Item hover_jetpack;

	/** 动力装甲电池组 Mk4（装进盔甲后电量上限 ×3） */
	public static Item armor_battery_mk4;
	/** 动力装甲电池组 Mk5（装进盔甲后电量上限 ×5） */
	public static Item armor_battery_mk5;

	/** 中微子眼镜（遗迹专属头部改装件） */
	public static Item neutrino_goggles;

	/** 电叉 */
	public static Item electric_fork;

	/** 复合材料储罐（护腿槽改装件） */
	public static Item composite_tank;

	/** 自动灭火器（装胸甲占护腿槽的改装件，16000 mB 二氧化碳） */
	public static Item auto_extinguisher;

	/** 丰饶之角：只能从战利品中极低概率开出的永久遗物食物 */
	public static Item horn_of_plenty;

    public static void init() {
        System.out.println("[MakeNTMGreatAgain] ModItems initialized.");

		//ingot_platinum
		ingot_platinum = new ItemWithDesc();
		ingot_platinum.setUnlocalizedName("ingot_platinum");
		ingot_platinum.setTextureName("makentmgreatagain:ingot_platinum");
		ingot_platinum.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(ingot_platinum, "ingot_platinum");
		
		//ingot_erbium
		ingot_erbium = new ItemWithDesc();
		ingot_erbium.setUnlocalizedName("ingot_erbium");
		ingot_erbium.setTextureName("makentmgreatagain:ingot_erbium");
		ingot_erbium.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(ingot_erbium, "ingot_erbium");
		
		// helicopter
		// 整个耐摔合金系列都用 ItemHelicopterDrop：掉落物会自旋、到 120 转/分后升空
		// （行为本体在 entity/EntityItemHelicopter，选类规则见下面的 materialItem）
		ingot_helicopter = new ItemHelicopterDrop();
		ingot_helicopter.setUnlocalizedName("ingot_helicopter");
		ingot_helicopter.setTextureName("makentmgreatagain:ingot_helicopter");
		ingot_helicopter.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(ingot_helicopter, "ingot_helicopter");

		// ---- HeLiCoPtEr / Pt / Er 的八类部件：每材质每形态各一件独立物品 ----
		// 贴图走独立子目录 textures/items/parts/，不与根目录的锭混放
		// 无下游配方的部件仅从创造模式物品栏隐藏（保留注册、矿辞与原有加工配方）
		plate_helicopter = part("helicopter", "plate", false);
		plate_cast_helicopter = part("helicopter", "plate_cast");
		plate_welded_helicopter = part("helicopter", "plate_welded");
		wire_helicopter = part("helicopter", "wire", false);
		wire_dense_helicopter = part("helicopter", "wire_dense", false);
		bolt_helicopter = part("helicopter", "bolt", false);
		pipe_helicopter = part("helicopter", "pipe", false);
		shell_helicopter = part("helicopter", "shell", false);

		plate_platinum = part("platinum", "plate");
		plate_cast_platinum = part("platinum", "plate_cast");
		plate_welded_platinum = part("platinum", "plate_welded", false);
		wire_platinum = part("platinum", "wire");
		wire_dense_platinum = part("platinum", "wire_dense", false);
		bolt_platinum = part("platinum", "bolt", false);
		pipe_platinum = part("platinum", "pipe", false);
		shell_platinum = part("platinum", "shell", false);

		plate_erbium = part("erbium", "plate");
		plate_cast_erbium = part("erbium", "plate_cast", false);
		plate_welded_erbium = part("erbium", "plate_welded", false);
		wire_erbium = part("erbium", "wire", false);
		wire_dense_erbium = part("erbium", "wire_dense", false);
		bolt_erbium = part("erbium", "bolt", false);
		pipe_erbium = part("erbium", "pipe", false);
		shell_erbium = part("erbium", "shell", false);

		// 三材质的粉末，贴图直接在 textures/items/ 根目录
		powder_helicopter = powder("helicopter");
		powder_erbium = powder("erbium");
		powder_platinum = powder("platinum");

		// 三材质的粒：注册名 nugget_<材质>，贴图在 parts/ 下（与八类部件同目录）
		nugget_helicopter = part("helicopter", "nugget");
		nugget_erbium = part("erbium", "nugget");
		nugget_platinum = part("platinum", "nugget");

		// 氦气凝锭：固化机产物 / 液化机原料，贴图在 textures/items/ 根目录
		ingot_helium = new ItemWithDesc();
		ingot_helium.setUnlocalizedName("ingot_helium");
		ingot_helium.setTextureName("makentmgreatagain:ingot_helium");
		ingot_helium.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(ingot_helium, "ingot_helium");

		// Threx 锭：四系复合锭，配方见 ModRecipes#addThrexIngotRecipe
		// 稀有度必须在赋值链里设，字段声明成 Item，赋值后就没有 setRarity 了
		// 基类用 ItemThrexSlowdown：在背包任意一格就持续给缓慢 II（amplifier 1）
		ingot_threx = new com.makentmgreatagain.items.ItemThrexSlowdown(1).setRarity(EnumRarity.epic);
		ingot_threx.setUnlocalizedName("ingot_threx");
		ingot_threx.setTextureName("makentmgreatagain:ingot_threx");
		ingot_threx.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(ingot_threx, "ingot_threx");
		net.minecraftforge.oredict.OreDictionary.registerOre("ingotThrex", ingot_threx);

		// Threx 粉：贴图 textures/items/powder_threx.png，不参与坩埚（本模组没有 Threx 材质）
		// 与锭同级：在背包任意一格就持续给缓慢 II（amplifier 1）
		powder_threx = new com.makentmgreatagain.items.ItemThrexSlowdown(1);
		powder_threx.setUnlocalizedName("powder_threx");
		powder_threx.setTextureName("makentmgreatagain:powder_threx");
		powder_threx.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_threx, "powder_threx");

		// 核装药核心：gadget_core 同型球，每种可制造核燃料 / 主要裂变产物各一个
		// 物品与贴图统一在 ModNukeCores 里循环注册，配方走 injectAllFallback 的 nukeCores 步骤
		ModNukeCores.register();

		// 五级机器覆层：接手 HBM 那四个 fusion_shield_* 遗留物的位置
		// 物品在这里注册，配方走 injectAllFallback 的 coatings 步骤
		ModCoatings.register();

		// 塔可夫食物与杂项：17 件罐头 / 巧克力 / 酒 / 面包，工作台配方随注册一起走
		// 战利品注入必须在 postInit（ItemPool 在 HBM 的 PostLoad 里才建表）
		ModTarkovFood.register();

		// 丰饶之角：只能从战利品中极低概率开出
		horn_of_plenty = new com.makentmgreatagain.items.ItemHornOfPlenty();
		GameRegistry.registerItem(horn_of_plenty, "horn_of_plenty");

        // 动力装甲电池组 Mk4：HBM 的 mod 是乘数（maxPower * mod），3D = 上限 ×3
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

		neutrino_goggles = new ItemNeutrinoGoggles();
		neutrino_goggles.setUnlocalizedName("neutrino_goggles");
		neutrino_goggles.setTextureName("makentmgreatagain:neutrino_goggles");
		neutrino_goggles.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(neutrino_goggles, "neutrino_goggles");

        // 电力悬浮作业背包（构造里已设名称/贴图/创造栏）
        hover_jetpack = new com.makentmgreatagain.items.ItemHoverJetPack();
        GameRegistry.registerItem(hover_jetpack, "hover_jetpack");

		// 电叉（构造里已设名称/创造栏/稀有度，贴图走 registerIcons）
		electric_fork = new com.makentmgreatagain.items.ItemElectricFork();
		GameRegistry.registerItem(electric_fork, "electric_fork");

		// 复合材料储罐：只是承载物，效果由 CompositeTankHazardGuard 提供
		composite_tank = new ItemCompositeTank();
		composite_tank.setUnlocalizedName("composite_tank");
		composite_tank.setTextureName("makentmgreatagain:composite_tank");
		composite_tank.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(composite_tank, "composite_tank");

		// 自动灭火器：逻辑全在 ItemAutoExtinguisher
		auto_extinguisher = new ItemAutoExtinguisher();
		auto_extinguisher.setUnlocalizedName("auto_extinguisher");
		auto_extinguisher.setTextureName("makentmgreatagain:auto_extinguisher");
		auto_extinguisher.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(auto_extinguisher, "auto_extinguisher");

		// 空模板：贴图复用 HBM 的 blueprints
		blank_template = new Item();
		blank_template.setUnlocalizedName("blank_template");
		blank_template.setTextureName("hbm:blueprints");
		blank_template.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(blank_template, "blank_template");

		// 高级固态燃料：燃烧时间在 ModFuelHandler 里登记
		solid_fuel_adv = new Item();
		solid_fuel_adv.setUnlocalizedName("solid_fuel_adv");
		solid_fuel_adv.setTextureName("makentmgreatagain:solid_fuel_adv");
		solid_fuel_adv.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(solid_fuel_adv, "solid_fuel_adv");

		// 无限水罐
		inf_water_mk3 = new ItemInfiniteFluid(Fluids.WATER, 5000);
		inf_water_mk3.setUnlocalizedName("inf_water_mk3");
		inf_water_mk3.setMaxStackSize(1);
		inf_water_mk3.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		inf_water_mk3.setTextureName("makentmgreatagain:inf_water_mk3");
		GameRegistry.registerItem(inf_water_mk3, inf_water_mk3.getUnlocalizedName());

		// 流体销毁罐
		// 参数：每 tick 销毁量, 普通腐蚀自毁, 强腐蚀自毁, 可销毁反物质
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

        // 高级合金锭（稀有度 epic = 紫名）
        ingot_advanced_alloy = new ItemWithDesc().setRarity(EnumRarity.epic);
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

		// 破损的高级合金刀片
		scrap_blades_alloy_reborn = new Item();
		scrap_blades_alloy_reborn.setUnlocalizedName("scrap_blades_alloy_reborn");
		scrap_blades_alloy_reborn.setTextureName("makentmgreatagain:scrap_advanced");
		scrap_blades_alloy_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(scrap_blades_alloy_reborn, "scrap_blades_alloy_reborn");

		// 石墨粉（配方在 ModRecipes.addGraphiteShredderRecipe）
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

		// 盐粉，注册 dustSalt
		powder_salt = new Item();
		powder_salt.setUnlocalizedName("powder_salt");
		powder_salt.setTextureName("makentmgreatagain:powder_salt");
		powder_salt.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_salt, "powder_salt");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustSalt", powder_salt);

		// 铅锌精矿
		leadzinc_concentrate = new ItemWithDesc();
		leadzinc_concentrate.setUnlocalizedName("leadzinc_concentrate");
		leadzinc_concentrate.setTextureName("makentmgreatagain:leadzinc_concentrate");
		leadzinc_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(leadzinc_concentrate, "leadzinc_concentrate");

		// 铅锌烧结块
		leadzinc_sinter = new ItemWithDesc();
		leadzinc_sinter.setUnlocalizedName("leadzinc_sinter");
		leadzinc_sinter.setTextureName("makentmgreatagain:leadzinc_sinter");
		leadzinc_sinter.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(leadzinc_sinter, "leadzinc_sinter");

		// 蓝粉
		bp_blue_powder = new ItemWithDesc();
		bp_blue_powder.setUnlocalizedName("bp_blue_powder");
		bp_blue_powder.setTextureName("makentmgreatagain:bp_blue_powder");
		bp_blue_powder.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(bp_blue_powder, "bp_blue_powder");

		// 锌粉：注册 dustZinc，原版没有锌，不注册等于没有产出
		powder_zinc_isp = new ItemWithDesc();
		powder_zinc_isp.setUnlocalizedName("powder_zinc_isp");
		powder_zinc_isp.setTextureName("makentmgreatagain:powder_zinc_isp");
		powder_zinc_isp.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_zinc_isp, "powder_zinc_isp");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustZinc", powder_zinc_isp);

		// 白砷
		arsenic_trioxide = new ItemWithDesc();
		arsenic_trioxide.setUnlocalizedName("arsenic_trioxide");
		arsenic_trioxide.setTextureName("makentmgreatagain:arsenic_trioxide");
		arsenic_trioxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenic_trioxide, "arsenic_trioxide");

		// 硫酸钠，注册 dustSodiumSulfate
		powder_sodium_sulfate = new ItemWithDesc();
		powder_sodium_sulfate.setUnlocalizedName("powder_sodium_sulfate");
		powder_sodium_sulfate.setTextureName("makentmgreatagain:powder_sodium_sulfate");
		powder_sodium_sulfate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_sodium_sulfate, "powder_sodium_sulfate");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustSodiumSulfate", powder_sodium_sulfate);

		// 砷浸出渣
		arsenical_residue = new ItemWithDesc();
		arsenical_residue.setUnlocalizedName("arsenical_residue");
		arsenical_residue.setTextureName("makentmgreatagain:arsenical_residue");
		arsenical_residue.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenical_residue, "arsenical_residue");

		// 白钨矿湿法链
		powder_scheelite = new ItemWithDesc();
		powder_scheelite.setUnlocalizedName("powder_scheelite");
		powder_scheelite.setTextureName("makentmgreatagain:powder_scheelite");
		powder_scheelite.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_scheelite, "powder_scheelite");
		net.minecraftforge.oredict.OreDictionary.registerOre("dustScheelite", powder_scheelite);

		// 白钨精矿（当前走全粉碱浸，先留着）
		scheelite_concentrate = new ItemWithDesc();
		scheelite_concentrate.setUnlocalizedName("scheelite_concentrate");
		scheelite_concentrate.setTextureName("makentmgreatagain:scheelite_concentrate");
		scheelite_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(scheelite_concentrate, "scheelite_concentrate");

		// 浸出渣：铍、锆、砷都在这一堆里，不是废料
		scheelite_leach_residue = new ItemWithDesc();
		scheelite_leach_residue.setUnlocalizedName("scheelite_leach_residue");
		scheelite_leach_residue.setTextureName("makentmgreatagain:scheelite_leach_residue");
		scheelite_leach_residue.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(scheelite_leach_residue, "scheelite_leach_residue");

		// 钨酸
		tungstic_acid = new ItemWithDesc();
		tungstic_acid.setUnlocalizedName("tungstic_acid");
		tungstic_acid.setTextureName("makentmgreatagain:tungstic_acid");
		tungstic_acid.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(tungstic_acid, "tungstic_acid");

		// 三氧化钨
		tungsten_trioxide = new ItemWithDesc();
		tungsten_trioxide.setUnlocalizedName("tungsten_trioxide");
		tungsten_trioxide.setTextureName("makentmgreatagain:tungsten_trioxide");
		tungsten_trioxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(tungsten_trioxide, "tungsten_trioxide");

		// 砷渣：白钨浸出渣分选的富砷尾料，不是上面的砷浸出渣
		arsenic_cake = new ItemWithDesc();
		arsenic_cake.setUnlocalizedName("arsenic_cake");
		arsenic_cake.setTextureName("makentmgreatagain:arsenic_cake");
		arsenic_cake.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(arsenic_cake, "arsenic_cake");

		// 铍精矿、锆精矿：都能直接进熔炉 1:1 出锭
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

		// 深海结核开采产物：三种精矿
		cobalt_polymetallic_concentrate = new ItemWithDesc();
		cobalt_polymetallic_concentrate.setUnlocalizedName("cobalt_polymetallic_concentrate");
		cobalt_polymetallic_concentrate.setTextureName("makentmgreatagain:cobalt_polymetallic_concentrate");
		cobalt_polymetallic_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(cobalt_polymetallic_concentrate, "cobalt_polymetallic_concentrate");

		heavy_sand_concentrate = new ItemWithDesc();
		heavy_sand_concentrate.setUnlocalizedName("heavy_sand_concentrate");
		heavy_sand_concentrate.setTextureName("makentmgreatagain:heavy_sand_concentrate");
		heavy_sand_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(heavy_sand_concentrate, "heavy_sand_concentrate");

		rare_earth_concentrate = new ItemWithDesc();
		rare_earth_concentrate.setUnlocalizedName("rare_earth_concentrate");
		rare_earth_concentrate.setTextureName("makentmgreatagain:rare_earth_concentrate");
		rare_earth_concentrate.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(rare_earth_concentrate, "rare_earth_concentrate");

		// 地核线：铂系提铂后的难溶渣，锇铱的载体
		core_platinum_residue = resource("core_platinum_residue");

		// 石膏
		gypsum = new Item();
		gypsum.setUnlocalizedName("gypsum");
		gypsum.setTextureName("makentmgreatagain:gypsum");
		gypsum.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(gypsum, "gypsum");

		// 天青石
		celestite = new Item();
		celestite.setUnlocalizedName("celestite");
		celestite.setTextureName("makentmgreatagain:celestite");
		celestite.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(celestite, "celestite");

		// 氧化锶
		strontium_oxide = new Item();
		strontium_oxide.setUnlocalizedName("strontium_oxide");
		strontium_oxide.setTextureName("makentmgreatagain:strontium_oxide");
		strontium_oxide.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(strontium_oxide, "strontium_oxide");

		// 木框架（结晶器凝木浆用）
		wood_frame = new Item();
		wood_frame.setUnlocalizedName("wood_frame");
		wood_frame.setTextureName("makentmgreatagain:wood_frame");
		wood_frame.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(wood_frame, "wood_frame");

		// 生胶（掺硫粉硫化才成橡胶）
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

		// 高强度碳化钨锭（物品栏图标放大 20% 由 client/BigIconRender 替换渲染实例实现）
		powder_cobalt_tungsten_ingot = new Item();
		powder_cobalt_tungsten_ingot.setUnlocalizedName("powder_cobalt_tungsten_ingot");
		powder_cobalt_tungsten_ingot.setTextureName("makentmgreatagain:powder_cobalt_tungsten_ingot");
		powder_cobalt_tungsten_ingot.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		GameRegistry.registerItem(powder_cobalt_tungsten_ingot, "powder_cobalt_tungsten_ingot");

		// 正电子胶囊：旧版 HBM 的 particle_aelectron 在 5852 移除，附属补回；空胶囊返还
		particle_positron = new Item();
		particle_positron.setUnlocalizedName("particle_positron");
		particle_positron.setTextureName("makentmgreatagain:particle_positron");
		particle_positron.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		particle_positron.setContainerItem(com.hbm.items.ModItems.particle_empty);
		GameRegistry.registerItem(particle_positron, "particle_positron");

		// 正电子单元：cell 家族（斜胶囊造型，同 HBM 的 cell_deuterium），内容带为正电子青蓝。
		// 用途：聚变配方「正-反物质等离子体」在加热器/仿星器配方界面上的图标，
		// 之前错用了 cell_antimatter，导致与「反物质等离子体」那条配方图标重样。
		cell_positron = new Item();
		cell_positron.setUnlocalizedName("cell_positron");
		cell_positron.setTextureName("makentmgreatagain:cell_positron");
		cell_positron.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		cell_positron.setContainerItem(com.hbm.items.ModItems.cell_empty);
		GameRegistry.registerItem(cell_positron, "cell_positron");

        // CMB 电极（稀有度 rare = 青名）
        cmb_electrode = new ItemWithDesc().setRarity(EnumRarity.rare);
        cmb_electrode.setUnlocalizedName("cmb_.electrode");
        cmb_electrode.setTextureName("makentmgreatagain:cmb_.electrode");
        cmb_electrode.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
		cmb_electrode.setMaxStackSize(1);
        GameRegistry.registerItem(cmb_electrode, "cmb_.electrode");

		// 两种催化转换器：属性对齐原版 hbm:catalytic_converter（普通 Item、堆叠 1、无耐久），
		// 与原版等效插进混合催化分馏塔的催化剂槽，见 TileEntityHybridCatalyticFractionTower.hasCatalyst
		platinum_converter = new Item();
		platinum_converter.setUnlocalizedName("platinum_converter");
		platinum_converter.setTextureName("makentmgreatagain:platinum_converter");
		platinum_converter.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		platinum_converter.setMaxStackSize(1);
		GameRegistry.registerItem(platinum_converter, "platinum_converter");

		cmb_converter = new ItemRadioactiveConverter();
		cmb_converter.setUnlocalizedName("cmb_converter");
		cmb_converter.setTextureName("makentmgreatagain:cmb_converter");
		cmb_converter.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cmb_converter, "cmb_converter");
		// 放射源走 HBM 危害系统：简介自动得到绿字 [放射性] + 黄字 380.0RAD/s（和原版铀锭同款），
		// 携带时由 HazardSystem 每 tick 自动辐照，本类不再自己写 onUpdate / addInformation。
		// 详见 ItemRadioactiveConverter 类注释。
		ItemRadioactiveConverter.registerRadiationHazard(cmb_converter, ItemRadioactiveConverter.RAD_PER_SECOND);

		// '中华牌'香烟
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

		// ==============================================================================
		//  [化冶线] 12 个中间产物：注册名 = 物品名 = 贴图名 = textures/items/<名>.png
	

		// ---- ① 重砂分选：结核洗出来的重矿物，磁选分钍、重选分铂族 ----
		heavy_sand_mix             = resource("heavy_sand_mix");             
		thorium_concentrate        = resource("thorium_concentrate");        
		pgm_concentrate            = resource("pgm_concentrate");            

		// ---- ② 稀土线：碱熔 → 煅烧 → 浸出 → 沉淀 → 分组 ----
		alkali_fused_sand          = resource("alkali_fused_sand");          
		rare_earth_calcine         = resource("rare_earth_calcine");         
		rare_earth_mix             = resource("rare_earth_mix");             
		mixed_re_hydroxide         = resource("mixed_re_hydroxide");         
		erbium_enriched_powder     = resource("erbium_enriched_powder");     

		// ---- ③ 多金属线：浸出 → 除杂 → 钴沉淀 ----
		polymetallic_mix           = resource("polymetallic_mix");           
		cobalt_hydroxide           = resource("cobalt_hydroxide");           

		// ---- ④ 铂族线：溶解 → 沉淀 / 电解 ----
		potassium_chloroplatinate  = resource("potassium_chloroplatinate");  
		anode_mud                  = resource("anode_mud");                  

		// ---- PEEK 特种工程塑料 ----
		ingot_peek  = resource("ingot_peek");
		powder_peek = resource("powder_peek");

		// ---- PEEK 并行运算升级芯片（1~3 级） ----
		upgrade_parallel_1 = parallelUpgrade(1);
		upgrade_parallel_2 = parallelUpgrade(2);
		upgrade_parallel_3 = parallelUpgrade(3);


        // 高级合金装备（重生版）
        registerAlloyReborn();
    }

    /**
     * 注册一个纯资源物品：注册名 = 物品名 = 贴图名，进「资源」创造标签页。
     */
    private static Item resource(String name) {
        Item item = new ItemWithDesc();
        item.setUnlocalizedName(name);
        item.setTextureName("makentmgreatagain:" + name);
        item.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(item, name);
        return item;
    }

    private static Item parallelUpgrade(int tier) {
        String name = "upgrade_parallel_" + tier;
        Item item = new com.makentmgreatagain.items.ItemParallelUpgrade(tier);
        item.setUnlocalizedName(name);
        item.setTextureName("makentmgreatagain:" + name);
        item.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(item, name);
        return item;
    }

    /**
     * 注册一个材质部件。物品名与注册名统一为 {@code <形态>_<材质>}。
     * 贴图路径相对 textures/items/，因此写成 {@code parts/<形态>_<材质>}，
     * 对应文件 {@code textures/items/parts/<形态>_<材质>.png}（与 HBM 的
     * {@code ColorUtil} 拼 "textures/items/" + iconName 的口径一致）。
     *
     * <p>注册名不是终点：{@link MNGAMetals#registerOreDict()} 会把这些物品登记进矿物词典，
     * <b>坩埚熔炼与铸造浇模全靠那一步识别</b>。新增部件形态时，除了这里加字段，
     * 还要去 MNGAMetals 的 {@code registerOne(...)} 里补对应矿辞前缀（见该类类头的速查）。</p>
     *
     * @param mat   材质后缀，如 helicopter / platinum / erbium
     * @param shape 形态前缀，如 plate / plate_cast / wire_dense
     */
    private static Item part(String mat, String shape) {
        return part(mat, shape, true);
    }

    private static Item part(String mat, String shape, boolean showInCreative) {
        String name = shape + "_" + mat;
        Item item = materialItem(mat);
        item.setUnlocalizedName(name);
        item.setTextureName("makentmgreatagain:parts/" + name);
        item.setCreativeTab(showInCreative ? ModCreativeTabs.TAB_RESOURCES : null);
        GameRegistry.registerItem(item, name);
        return item;
    }

    /**
     * 按材质选物品基类。
     *
     * <p>耐摔合金（{@code helicopter}）全系列要「掉落物自旋 + 升空」，所以换成
     * {@link ItemHelicopterDrop}；其它材质仍是普通 {@link ItemWithDesc}，行为不变。</p>
     *
     * <p>要让别的材质也有这个特性：把材质名加进下面这行的判断即可，
     * 锭、粒、粉、八类部件会自动跟上。方块是另一条路 —— 还要把
     * {@code ModBlocks.registerStorageBlock(name, true)} 的第二个参数改成 true。</p>
     */
    private static Item materialItem(String mat) {
        return "helicopter".equals(mat) ? new ItemHelicopterDrop() : new ItemWithDesc();
    }

    /**
     * 注册一份金属粉末。注册名 {@code powder_<材质>}，
     * 贴图 {@code textures/items/powder_<材质>.png}。
     *
     * <p>粉末同样要进矿物词典（{@code dust<材质>}）才能被坩埚熔掉，
     * 登记位置见 {@link MNGAMetals#registerOreDict()}。</p>
     */
    private static Item powder(String mat) {
        String name = "powder_" + mat;
        // 耐摔合金粉末同样会自旋升空，选类规则见 materialItem
        Item item = materialItem(mat);
        item.setUnlocalizedName(name);
        item.setTextureName("makentmgreatagain:" + name);
        item.setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(item, name);
        return item;
    }

    private static void registerAlloyReborn() {
        // ---- 剑 ----
        alloy_sword_reborn = new ItemSwordAbility(8F, 0, MNGAMaterials.TOOL_ALLOY)
                .addAbility(IWeaponAbility.STUN, 0);
        alloy_sword_reborn.setUnlocalizedName("alloy_sword_reborn");
        alloy_sword_reborn.setTextureName("makentmgreatagain:alloy_sword_reborn");
        alloy_sword_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(alloy_sword_reborn, "alloy_sword_reborn");

		// 碳化钨斧
		cobalt_tungsten_axe = new ItemToolAbility(14F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.AXE)
			.addAbility(IToolAreaAbility.RECURSION, 4)
			.addAbility(IWeaponAbility.BEHEADER, 2);
		cobalt_tungsten_axe.setUnlocalizedName("cobalt_tungsten_axe");
		cobalt_tungsten_axe.setMaxDamage(6000);
		cobalt_tungsten_axe.setTextureName("makentmgreatagain:cobalt_tungsten_axe");
		cobalt_tungsten_axe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_axe, "cobalt_tungsten_axe");

		// 碳化钨锹
		cobalt_tungsten_shovel = new ItemToolAbility(7F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.SHOVEL)
			.addAbility(IToolAreaAbility.RECURSION, 4)
			.addAbility(IToolAreaAbility.HAMMER, 2)
			.addAbility(IToolHarvestAbility.SILK, 0);
		cobalt_tungsten_shovel.setUnlocalizedName("cobalt_tungsten_shovel");
		cobalt_tungsten_shovel.setMaxDamage(9500);
		cobalt_tungsten_shovel.setTextureName("makentmgreatagain:cobalt_tungsten_shovel");
		cobalt_tungsten_shovel.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_shovel, "cobalt_tungsten_shovel");

		// 碳化钨锄
		cobalt_tungsten_hoe = new ItemHoeReborn(MNGAMaterials.TOOL_ALLOY);
		cobalt_tungsten_hoe.setUnlocalizedName("cobalt_tungsten_hoe");
		cobalt_tungsten_hoe.setMaxDamage(7500);
		cobalt_tungsten_hoe.setTextureName("makentmgreatagain:cobalt_tungsten_hoe");
		cobalt_tungsten_hoe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_hoe, "cobalt_tungsten_hoe");

		// ---- 碳化钨剑 ----
		cobalt_tungsten_sword = new ItemSwordAbility(18F, 0, MNGAMaterials.TOOL_ALLOY)
			.addAbility(IWeaponAbility.STUN, 1) 
		    .addAbility(IWeaponAbility.BEHEADER, 0)
			.addAbility(IWeaponAbility.BOBBLE, 0);
		cobalt_tungsten_sword.setUnlocalizedName("cobalt_tungsten_sword");
		cobalt_tungsten_sword.setMaxDamage(10000);
		cobalt_tungsten_sword.setTextureName("makentmgreatagain:cobalt_tungsten_sword");
		cobalt_tungsten_sword.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_sword, "cobalt_tungsten_sword");

		// 碳化钨镐
		cobalt_tungsten_pickaxe = new ItemToolAbility(9F, 0, MNGAMaterials.TOOL_ALLOY, EnumToolType.PICKAXE)
			.addAbility(IToolAreaAbility.RECURSION, 2)
			.addAbility(IToolAreaAbility.HAMMER, 1)
			.addAbility(IToolHarvestAbility.SILK, 0)
			.addAbility(IToolHarvestAbility.LUCK, 1);
		cobalt_tungsten_pickaxe.setUnlocalizedName("cobalt_tungsten_pickaxe");
		cobalt_tungsten_pickaxe.setMaxDamage(8500);
		cobalt_tungsten_pickaxe.setTextureName("makentmgreatagain:cobalt_tungsten_pickaxe");
		cobalt_tungsten_pickaxe.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_pickaxe, "cobalt_tungsten_pickaxe");
		
		// ---- 耐摔合金镐 / 斧 ----
		helicopter_pickaxe =
			new ItemToolAbilityHelicopter(65F, 0, MNGAMaterials.TOOL_HELICOPTER, EnumToolType.MINER)
			.addAbility(IToolAreaAbility.HAMMER, 5)
			.addAbility(IToolAreaAbility.HAMMER_FLAT, 5)
			.addAbility(IToolAreaAbility.RECURSION, 10)
			.addAbility(IToolHarvestAbility.CRYSTALLIZER, 0)
			.addAbility(IToolHarvestAbility.SILK, 0)
			.addAbility(IToolHarvestAbility.LUCK, 7)
			.addAbility(IToolAreaAbility.EXPLOSION, 5)
			.addAbility(IWeaponAbility.STUN, 5)
			.addAbility(IWeaponAbility.PHOSPHORUS, 0)
			.addAbility(IWeaponAbility.BEHEADER, 0)
			.addAbility(IToolHarvestAbility.SMELTER, 0)
			.addAbility(IToolHarvestAbility.SHREDDER, 0)
			.setDepthRockBreaker()
			.setUnlocalizedName("helicopter_pickaxe")
			.setTextureName("makentmgreatagain:helicopter_pickaxe")
			.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(helicopter_pickaxe, "helicopter_pickaxe");

		helicopter_axe =
			new ItemToolAbilityHelicopter(150F, 0, MNGAMaterials.TOOL_HELICOPTER, EnumToolType.AXE)
			.addAbility(IToolAreaAbility.HAMMER, 4)
			.addAbility(IToolAreaAbility.HAMMER_FLAT, 4)
			.addAbility(IToolAreaAbility.RECURSION, 4)
			.addAbility(IToolHarvestAbility.SILK, 0)
			.addAbility(IToolHarvestAbility.LUCK, 7)
			.addAbility(IToolAreaAbility.EXPLOSION, 4)
			.addAbility(IWeaponAbility.STUN, 5)
			.addAbility(IWeaponAbility.PHOSPHORUS, 2)
			.addAbility(IWeaponAbility.BEHEADER, 0)
			.setUnlocalizedName("helicopter_axe")
			.setTextureName("makentmgreatagain:helicopter_axe")
			.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(helicopter_axe, "helicopter_axe");

		// 碳化钨护甲四件：第 3 参数是护甲模型贴图（_1 头盔/胸甲/靴，_2 护腿）
		ItemArmorAlloyReborn ctHelmet = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_CT, 0, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png");
		ctHelmet
				// 全套：耐高温
				.addEffect(new PotionEffect(Potion.fireResistance.id, 20, 0))
				// 全套：重甲落地
				.setHasHardLanding(true)
				// 单件：危险防护，只有头盔判得到，写在别的件上等于没写
				.setHazardClass(HazardClass.SAND, HazardClass.PARTICLE_COARSE, HazardClass.PARTICLE_FINE);
		// 全套：移动速度（ArmorFSB 没有这个字段，值存本类，由 ArmorSpeedHandler 读）
		ctHelmet.setSetSpeed(-0.10D);

		cobalt_tungsten_helmet = ctHelmet
				.setUnlocalizedName("cobalt_tungsten_helmet")
				.setMaxDamage(12000)
				.setTextureName("makentmgreatagain:cobalt_tungsten_helmet")
				.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_helmet, "cobalt_tungsten_helmet");

		cobalt_tungsten_plate = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_CT, 1, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png")
				.cloneStats(ctHelmet)
				.setUnlocalizedName("cobalt_tungsten_plate")
				.setMaxDamage(15500)
				.setTextureName("makentmgreatagain:cobalt_tungsten_plate")
				.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_plate, "cobalt_tungsten_plate");

		cobalt_tungsten_legs = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_CT, 2, "makentmgreatagain:textures/armor/cobalt_tungsten_2.png")
				.cloneStats(ctHelmet)
				.setUnlocalizedName("cobalt_tungsten_legs")
				.setMaxDamage(12000)
				.setTextureName("makentmgreatagain:cobalt_tungsten_legs")
				.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		GameRegistry.registerItem(cobalt_tungsten_legs, "cobalt_tungsten_legs");

		cobalt_tungsten_boots = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_CT, 3, "makentmgreatagain:textures/armor/cobalt_tungsten_1.png")
				.cloneStats(ctHelmet)
				.setUnlocalizedName("cobalt_tungsten_boots")
				.setMaxDamage(11000)
				.setTextureName("makentmgreatagain:cobalt_tungsten_boots")
				.setCreativeTab(ModCreativeTabs.TAB_GEAR);
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
        // 第 3 参数是护甲模型贴图（_1 头盔/胸甲/靴，_2 护腿）
        alloy_helmet_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 0, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_helmet_reborn.setUnlocalizedName("alloy_helmet_reborn");
        alloy_helmet_reborn.setTextureName("makentmgreatagain:alloy_helmet_reborn");
        alloy_helmet_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		alloy_helmet_reborn.setMaxDamage(2500);
        GameRegistry.registerItem(alloy_helmet_reborn, "alloy_helmet_reborn");

        alloy_plate_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 1, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_plate_reborn.setUnlocalizedName("alloy_plate_reborn");
        alloy_plate_reborn.setTextureName("makentmgreatagain:alloy_plate_reborn");
        alloy_plate_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		alloy_plate_reborn.setMaxDamage(2500);
        GameRegistry.registerItem(alloy_plate_reborn, "alloy_plate_reborn");

        alloy_legs_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 2, "makentmgreatagain:textures/armor/alloy_2.png");
        alloy_legs_reborn.setUnlocalizedName("alloy_legs_reborn");
        alloy_legs_reborn.setTextureName("makentmgreatagain:alloy_legs_reborn");
        alloy_legs_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		alloy_legs_reborn.setMaxDamage(2500);
        GameRegistry.registerItem(alloy_legs_reborn, "alloy_legs_reborn");

        alloy_boots_reborn = new ItemArmorAlloyReborn(MNGAMaterials.ARMOR_ALLOY, 3, "makentmgreatagain:textures/armor/alloy_1.png");
        alloy_boots_reborn.setUnlocalizedName("alloy_boots_reborn");
        alloy_boots_reborn.setTextureName("makentmgreatagain:alloy_boots_reborn");
        alloy_boots_reborn.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		alloy_boots_reborn.setMaxDamage(2500);
        GameRegistry.registerItem(alloy_boots_reborn, "alloy_boots_reborn");

        // ---- 耐摔合金护甲四件 ----
        // 效果全部写在头盔上，另三件 cloneStats；免伤/摔死音效/空手震撼在 hook.HelicopterArmorHandler
        ItemArmorAlloyReborn heliHelmet = new ItemArmorHelicopter(MNGAMaterials.ARMOR_HELICOPTER, 0, "makentmgreatagain:textures/armor/helicopter_1.png");
        heliHelmet
                // 全套：急迫 amplifier 3 + 力量 amplifier 10（按最初需求数字，即 effect 的 level 参数）
                .addEffect(new PotionEffect(Potion.digSpeed.id, 20, 3))
                .addEffect(new PotionEffect(Potion.damageBoost.id, 20, 10))
                // 全套：硬着陆
                .setHasHardLanding(true)
                // 单件：头盔免疫 HazardClass 全部污染类（含尘埃/沙/菌/腐蚀气体等）。
                // 辐射全免见 MNGAArmorStats.registerHelicopter（直灌 registerHazmat，
                // 不在此叠 setRadResist —— 该类注释：external 与直注册会互相覆盖）；
                // 负面效果清除见 HelicopterArmorHandler。
                .setHazardClass(HazardClass.values());
        // 耐久锁 24 且永不消耗：数字统一取 ItemArmorHelicopter.DURABILITY，
        // 「不掉」由该类在 onArmorTick / onUpdate 每 tick 擦回 0 实现（见该类类注释）
        heliHelmet.setMaxDamage(ItemArmorHelicopter.DURABILITY);

        helicopter_helmet = heliHelmet
                .setUnlocalizedName("helicopter_helmet")
                .setTextureName("makentmgreatagain:helicopter_helmet")
                .setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(helicopter_helmet, "helicopter_helmet");

        helicopter_plate = new ItemArmorHelicopter(MNGAMaterials.ARMOR_HELICOPTER, 1, "makentmgreatagain:textures/armor/helicopter_1.png")
                .cloneStats(heliHelmet)
                .setUnlocalizedName("helicopter_plate")
                .setMaxDamage(ItemArmorHelicopter.DURABILITY)
                .setTextureName("makentmgreatagain:helicopter_plate")
                .setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(helicopter_plate, "helicopter_plate");

        helicopter_legs = new ItemArmorHelicopter(MNGAMaterials.ARMOR_HELICOPTER, 2, "makentmgreatagain:textures/armor/helicopter_2.png")
                .cloneStats(heliHelmet)
                .setUnlocalizedName("helicopter_legs")
                .setMaxDamage(ItemArmorHelicopter.DURABILITY)
                .setTextureName("makentmgreatagain:helicopter_legs")
                .setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(helicopter_legs, "helicopter_legs");

        helicopter_boots = new ItemArmorHelicopter(MNGAMaterials.ARMOR_HELICOPTER, 3, "makentmgreatagain:textures/armor/helicopter_1.png")
                .cloneStats(heliHelmet)
                .setUnlocalizedName("helicopter_boots")
                .setMaxDamage(ItemArmorHelicopter.DURABILITY)
                .setTextureName("makentmgreatagain:helicopter_boots")
                .setCreativeTab(ModCreativeTabs.TAB_GEAR);
        GameRegistry.registerItem(helicopter_boots, "helicopter_boots");

        // 无线充电连接器：继承 HBM 的 ItemArmorMod，装进靴子的 boots_only 槽
        wireless_connector = new ItemWirelessConnector();
        GameRegistry.registerItem(wireless_connector, "wireless_connector");

        // 稳定X晶体：晶体簇的掉落物，本身也是合成材料
        // 注意不能用 "crystal_xen"：HBM 原版已有同名物品（人造X晶体），
        // lang 键 item.crystal_xen.name 会被 HBM 那条覆盖，游戏里就会显示成「人造X晶体」
        crystal_xen = new Item()
                .setUnlocalizedName("stable_crystal_xen")
                .setTextureName("makentmgreatagain:crystal_xen")
                .setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(crystal_xen, "stable_crystal_xen");

        // 不稳态X晶体：SILEX 40%，右键末地石可种下幼苗期X晶簇
        crystal_xen_unstable = new UnstableXenCrystalItem()
                .setUnlocalizedName("crystal_xen_unstable")
                .setTextureName("makentmgreatagain:crystal_xen_unstable")
                .setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(crystal_xen_unstable, "crystal_xen_unstable");

        // 亚稳态X晶体：SILEX 可见光分离 13.3%（携带有衰变倒计时与核爆机制，不可堆叠）
        crystal_xen_metastable = new MetastableCrystalItem()
                .setMaxStackSize(1)
                .setUnlocalizedName("crystal_xen_metastable")
                .setTextureName("makentmgreatagain:crystal_xen_metastable")
                .setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(crystal_xen_metastable, "crystal_xen_metastable");

        // 乱码X晶体：4 个乱码字符、颜色字母来回换（OBFUSCATED），NEI 配方不显示但机器可分离
        crystal_xen_glitch = new GlitchCrystalItem()
                .setUnlocalizedName("crystal_xen_glitch")
                .setTextureName("makentmgreatagain:crystal_xen_glitch")
                .setCreativeTab(ModCreativeTabs.TAB_RESOURCES);
        GameRegistry.registerItem(crystal_xen_glitch, "crystal_xen_glitch");

        System.out.println("[MakeNTMGreatAgain] Alloy Reborn gear registered (22 items).");
    }

	/**
	 * 乱码X晶体：显示名整串用 {@code §k} 遮挡（渲染成每帧都不同的随机字符），
	 * 只轮流放出一个可读字，像信号扫描 —— 参考 HBM 迪伽马系列
	 * （{@code ItemFELCrystal} 的 {@code OBFUSCATED + "THERADIANCEOFATHOUSANDSUNS"}、
	 * {@code rbmk_pellet_drx} 的 {@code OBFUSCATED + "can't you hear..."}）的做法，
	 * 但这里用在 displayName 上而不是 lore。
	 *
	 * <p>名字长度取自 lang（中文「乱码X晶体」5 字 / 英文 19 字符），先把可能残留的
	 * 格式码剥掉，避免 lang 里再带颜色时套两层。周期按长度自适应，固定约 1.5 秒扫完一轮。</p>
	 */
	private static class GlitchCrystalItem extends Item {

		/** 直接写字面量格式码，不依赖 EnumChatFormatting.toString() 的实现细节 */
		private static final String OBF = "\u00a7k";
		private static final String RESET = "\u00a7r";
		private static final String DARK_PURPLE = "\u00a75";

		/**
		 * 仿照 HBM 原版 {@code ItemCustomLore} 中 {@code ModItems.undefined}（形态未定义物质）的简介效果：
		 * 1/10 概率（或异常时）显示暗红色 {@code UNDEFINED}；
		 * 其余 9/10 概率每 0.5 秒从物品注册表随机抽取一个 ID，显示对应物品名或 {@code ERROR #ID}。
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list, boolean advanced) {
			try {
				if(player != null && player.worldObj != null && player.worldObj.rand.nextInt(10) == 0) {
					list.add(net.minecraft.util.EnumChatFormatting.DARK_RED + "UNDEFINED");
				} else {
					java.util.Random rand = new java.util.Random(System.currentTimeMillis() / 500);
					int setSize = Item.itemRegistry.getKeys().size();
					if(setSize <= 0) {
						list.add(net.minecraft.util.EnumChatFormatting.DARK_RED + "UNDEFINED");
						return;
					}
					int r = rand.nextInt(setSize);
					Item item = Item.getItemById(r);
					if(item != null) {
						list.add(new ItemStack(item).getDisplayName());
					} else {
						list.add(net.minecraft.util.EnumChatFormatting.RED + "ERROR #" + r);
					}
				}
			} catch(Exception ex) {
				list.add(net.minecraft.util.EnumChatFormatting.DARK_RED + "UNDEFINED");
			}
		}

		@Override
		public String getItemStackDisplayName(ItemStack stack) {
			String key = this.getUnlocalizedName() + ".name";
			String base = net.minecraft.util.StatCollector.translateToLocal(key);
			if(base == null || base.equals(key)) base = "Glitch Crystal";
			base = base.replaceAll("\u00a7.", "").trim();
			if(base.isEmpty()) base = "Glitch Crystal";

			int n = base.length();
			// 一轮约 1.5 秒；英文字符多、单步就短一些，保证中英观感一致
			long step = Math.max(60L, 1500L / (n + 2));
			int lit = (int) ((System.currentTimeMillis() / step) % (long) (n + 2));

			StringBuilder sb = new StringBuilder(n * 3);
			for(int i = 0; i < n; i++) {
				if(i == lit) {
					// 被扫到的那一个字露出来，其余整串保持乱码
					sb.append(RESET).append(base.charAt(i));
				} else {
					sb.append(OBF).append(base.charAt(i));
				}
			}
			sb.append(RESET);
			return sb.toString();
		}
	}

	/**
	 * 亚稳态X晶体：仿照原版 {@link com.hbm.items.special.ItemUnstable} 的衰变倒计时与引爆机制。
	 * 携带在物品栏中时每 tick 累加衰变计时（200 tick / 10 秒），满 100% 时触发半径 700 核爆、
	 * 造成 30000 点核爆伤害并销毁自身。
	 */
	private static class MetastableCrystalItem extends Item {

		private static final int RADIUS = 700;
		private static final int TIMER = 200;
		private static final float DAMAGE = 30000.0F;

		@Override
		@SuppressWarnings("rawtypes")
		public void addInformation(ItemStack stack, net.minecraft.entity.player.EntityPlayer player, java.util.List list, boolean advanced) {
			list.add("Decay: " + (getTimer(stack) * 100 / TIMER) + "%");
		}

		@Override
		public void onUpdate(ItemStack stack, net.minecraft.world.World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
			int next = getTimer(stack) + 1;
			setTimer(stack, next);

			if(next >= TIMER && !world.isRemote) {
				world.spawnEntityInWorld(com.hbm.entity.logic.EntityNukeExplosionMK5.statFac(world, RADIUS, entity.posX, entity.posY, entity.posZ));
				world.playSoundAtEntity(entity, "hbm:entity.oldExplosion", 1.0F, 1.0F);
				entity.attackEntityFrom(com.hbm.lib.ModDamageSource.nuclearBlast, DAMAGE);
				stack.stackSize = 0;
			}
		}

		private static int getTimer(ItemStack stack) {
			if(!stack.hasTagCompound()) {
				stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
			}
			return stack.stackTagCompound.getInteger("timer");
		}

		private static void setTimer(ItemStack stack, int time) {
			if(!stack.hasTagCompound()) {
				stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
			}
			stack.stackTagCompound.setInteger("timer", time);
		}
	}

	/**
	 * 不稳态X晶体：对末地石顶面右键可种下幼苗期X晶簇（{@code meta = 1}）。
	 */
	private static class UnstableXenCrystalItem extends Item {

		@Override
		public boolean onItemUse(ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
				net.minecraft.world.World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ) {
			if(side != 1) return false;
			if(world.getBlock(x, y, z) != net.minecraft.init.Blocks.end_stone) return false;
			if(!world.isAirBlock(x, y + 1, z)) return false;
			if(!player.canPlayerEdit(x, y + 1, z, side, stack)) return false;

			if(!world.isRemote) {
				world.setBlock(x, y + 1, z, ModBlocks.xen_crystal_cluster,
					com.makentmgreatagain.blocks.BlockXenCrystal.META_SEED, 2);
				world.playSoundEffect(x + 0.5D, y + 1.5D, z + 0.5D,
					net.minecraft.block.Block.soundTypeGlass.getStepResourcePath(), 1.0F, 1.0F);
				if(!player.capabilities.isCreativeMode) {
					stack.stackSize--;
				}
			}
			return true;
		}
	}

}

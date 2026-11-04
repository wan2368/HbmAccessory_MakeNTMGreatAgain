package com.makentmgreatagain.init;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemFluidIDMulti;
import com.hbm.main.CraftingManager;
import com.hbm.handler.pollution.PollutionHandler.PollutionType;
import com.hbm.inventory.fluid.trait.FT_Combustible;
import com.hbm.inventory.fluid.trait.FT_Combustible.FuelGrade;
import com.hbm.inventory.fluid.trait.FT_Coolable;
import com.hbm.inventory.fluid.trait.FT_Corrosive;
import com.hbm.inventory.fluid.trait.FT_Flammable;
import com.hbm.inventory.fluid.trait.FT_Heatable;
import com.hbm.inventory.fluid.trait.FT_Pheromone;
import com.hbm.inventory.fluid.trait.FT_Polluting;
import com.hbm.inventory.fluid.trait.FT_Poison;
import com.hbm.inventory.fluid.trait.FT_PWRModerator;
import com.hbm.inventory.fluid.trait.FT_Toxin;
import com.hbm.inventory.fluid.trait.FT_VentRadiation;
import com.hbm.inventory.fluid.trait.FluidTrait;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Amat;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Delicious;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_NoID;
import com.hbm.inventory.fluid.trait.FluidTraitSimple.FT_Unsiphonable;
import com.hbm.util.ArmorRegistry.HazardClass;
import com.hbm.render.util.EnumSymbol;
import com.hbm.util.CompatFluidRegistry;
import com.makentmgreatagain.MakeNTMGreatAgain;

import api.hbm.fluidmk2.IFluidRegisterListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 本模组的 HBM 流体注册中心。
 *
 * <p>设计目标：<b>加一种新流体只需要写一行 def(...)</b>，不用再碰 init()、监听器、metaOrder、
 * 容器、燃料特性这些样板代码 —— 全部由下面的 FluidDef 统一处理。</p>
 *
 * <h3>加一种新流体（三步，其中只有第 1 步是必须的）</h3>
 * <pre>
 * // 1) 在下面的常量区加一行：
 * public static final FluidDef BRINE = def("BRINE", 2002, 0x1DBBF5, "water").liquid().canister(0x1DBBF5);
 *
 * // 2) 想让它能烧（可选）：
 * public static final FluidDef MY_FUEL = def("MY_FUEL", 2003, 0xFFAA00, "fuel")
 *         .liquid()
 *         .fuel(120_000L, FuelGrade.MEDIUM, 2.5D)   // 每桶 120k 热量，燃烧值 = 120k*2.5
 *         .canister(0xFFAA00);
 *
 * // 3) 用的时候：ModFluids.BRINE.type / ModFluids.BRINE.stack(1000)
 * </pre>
 *
 * <h3>燃料特性对应哪些机器（源码依据）</h3>
 * <ul>
 *   <li>{@link FT_Flammable}：产热（TU）。加热器 TileEntityHeaterOilburner 每 mB 得到
 *       heatEnergy/1000 点热量；燃气火炬 TileEntityMachineGasFlare 拿它发电；火焰炮塔也读它。</li>
 *   <li>{@link FT_Combustible}：产电（HE）。内燃机 TileEntityMachineCombustionEngine
 *       每 mB 得到 combustionEnergy/10000 * 效率 的 HE；柴油发电机、燃气轮机、涡轮风扇同理。</li>
 *   <li>FuelGrade 决定“哪类机器愿意烧”：LOW=加热油/工业油，MEDIUM=汽油，HIGH=柴油，
 *       AERO=煤油/航空燃料，GAS=天然气/可燃气。</li>
 * </ul>
 *
 * <p>注意：ID 与内部名一旦发布就不能改（存档、管道、容器 meta、配方 JSON 都按它们记录）。</p>
 */
public final class ModFluids {
    private static final List<FluidDef> ALL = new ArrayList<FluidDef>();

    /**
     * 流体 -> 储罐模型贴图。
     *
     * 为什么需要这张表：HBM 的 FluidType 里只存了“GUI 里罐子的液体贴图”，
     * 储罐模型贴图是 RenderFluidTank 按 hbm 命名空间 + 流体内部名拼出来的
     * （hbm:textures/models/tank/tank_<NAME>.png），附属的图它永远找不到。
     * 所以这里给每个流体单独记一张，客户端渲染器 RenderModFluidTank 按 FluidType 查。
     */
    private static final Map<FluidType, ResourceLocation> TANK_TEXTURES = new HashMap<FluidType, ResourceLocation>();

    /** 芳环硝基物燃烧：烟尘 4 倍于精炼油，另释毒。 */
    private static final FT_Polluting SOOT_AROMATIC = new FT_Polluting()
            .burn(PollutionType.SOOT, 0.004F)
            .release(PollutionType.POISON, 0.0002F);

    /** 苯胺燃烧：浓黑烟 + 强毒性排放。 */
    private static final FT_Polluting SOOT_ANILINE = new FT_Polluting()
            .burn(PollutionType.SOOT, 0.005F)
            .release(PollutionType.POISON, 0.0004F);

    // =====================================================================================
    //  流体定义区：加新流体就在这里加一行
    // =====================================================================================

    /**
     * 热盐水：淡蓝色、液体、可装铁罐。
     *
     * 注意：它必须定义在 SALT_WATER 前面 —— SALT_WATER 的“可被锅炉加热”特性要引用它。
     * 但即便如此，也不能在字段链式调用里直接写 .heatable(..., HOT_SALT_WATER.type, ...)：
     * 那时静态初始化还没跑完，.type 还是 null。正确做法是等 init() 里注册完再接线，
     * 见下面的 linkFluids()。
     */
    public static final FluidDef HOT_SALT_WATER = def("HOT_SALT_WATER", 2002, 0x0A9BF5, "hot_salt_water")
            .liquid()
            .canister(0x0A9BF5)
            .temp(100)
            .tankTexture("tank_HOT_SALT_WATER");

    /** 盐水：淡蓝色、液体、可装铁罐；加热后变热盐水（linkFluids 里接线）。 */
    public static final FluidDef SALT_WATER = def("SALT_WATER", 2001, 0x0A9BF5, "salt_water")
            .liquid()
            .canister(0x0A9BF5)
            .temp(20)
            .tankTexture("tank_SALT_WATER");
	
	public static final FluidDef SEA_WATER = def("SEA_WATER", 2003, 0x0A9BF5, "sea_water")
			.liquid()
			.temp(15)
			.canister(0x0400d9)
			.tankTexture("tank_SEA_WATER");

	/** 浓缩海水：沿海群系抽上来再浓缩的高浓度海水，氯碱、分盐、提溴、吸附的共同原料。 */
	public static final FluidDef SALT_SEA_WATER = def("SALT_SEA_WATER", 2010, 0x008DB6, "salt_sea_water")
			.liquid()
			.temp(20)
			.canister(0x008DB6)
			.tankTexture("tank_SALT_SEA_WATER");

	/** 含矿液：树脂吸附海水后富集出来的混合矿液，锂、铀、硼的原料。 */
	public static final FluidDef ORE_LIQUOR = def("ORE_LIQUOR", 2011, 0x9A6B1E, "ore_liquor")
			.liquid()
			.temp(20)
			.canister(0x9A6B1E)
			.tankTexture("tank_ORE_LIQUOR");

	// =====================================================================================
	//  木浆 · 按树种分的六种

	/** 白桦浆：软木，树皮含桦木油，烧得最干净，热值中等偏低。 */
	public static final FluidDef BIRCH_PULP = def("BIRCH_PULP", 2012, 0xF0E4B4, "birch_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(15_000L, FuelGrade.LOW, 2.0D)   // 15,000 TU + 30,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0028F))   // 烟尘 0.0028/mB，烧油的 70%
			.canister(0xF0E4B4)
			.tankTexture("tank_BIRCH_PULP");

	/** 橡木浆：标准硬木，密度中等，作为六种的基准值。 */
	public static final FluidDef OAK_PULP = def("OAK_PULP", 2013, 0xC09A62, "oak_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(17_000L, FuelGrade.LOW, 2.0D)   // 17,000 TU + 34,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0025F))   // 烟尘 0.0025/mB，烧油的 62.5%
			.canister(0xC09A62)
			.tankTexture("tank_OAK_PULP");

	/** 云杉浆：软木、树脂多，燃点低但热值最低，烧得最快。 */
	public static final FluidDef SPRUCE_PULP = def("SPRUCE_PULP", 2014, 0x6E452F, "spruce_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(12_000L, FuelGrade.LOW, 2.0D)   // 12,000 TU + 24,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0032F))   // 烟尘 0.0032/mB，烧油的 80%，六种里最脏
			.canister(0x6E452F)
			.tankTexture("tank_SPRUCE_PULP");

	/** 丛林浆：热带硬木，木质致密，六种里第二耐烧。 */
	public static final FluidDef JUNGLE_PULP = def("JUNGLE_PULP", 2015, 0x9E5F72, "jungle_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(21_000L, FuelGrade.LOW, 2.0D)   // 21,000 TU + 42,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0021F))   // 烟尘 0.0021/mB，烧油的 52.5%
			.canister(0x9E5F72)
			.tankTexture("tank_JUNGLE_PULP");

	/** 金合欢浆：高密度硬木，耐烧，介于橡木与丛林之间。 */
	public static final FluidDef ACACIA_PULP = def("ACACIA_PULP", 2016, 0xD4702A, "acacia_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(19_000L, FuelGrade.LOW, 2.0D)   // 19,000 TU + 38,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0023F))   // 烟尘 0.0023/mB，烧油的 57.5%
			.canister(0xD4702A)
			.tankTexture("tank_ACACIA_PULP");

	/** 深色橡木浆：心材最密实，六种里热值最高。 */
	public static final FluidDef DARK_OAK_PULP = def("DARK_OAK_PULP", 2017, 0x462F1F, "dark_oak_pulp")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(24_000L, FuelGrade.LOW, 2.0D)   // 24,000 TU + 48,000 HE 每桶
			.polluting(new FT_Polluting().burn(PollutionType.SOOT, 0.0019F))   // 烟尘 0.0019/mB，烧油的 47.5%，六种里最干净
			.canister(0x462F1F)
			.tankTexture("tank_DARK_OAK_PULP");

	// =====================================================================================
	//  木质浆料（id 2018）—— 六种木浆的公共前体

	/** 木质浆料：木油 + 水乳化后的中间体。 */
	public static final FluidDef WOOD_MASH = def("WOOD_MASH", 2018, 0xB2AC90, "wood_mash")
			.liquid()
			.viscous()
			.temp(20)
			.hazard(0, 0, 0, EnumSymbol.NONE)
			.canister(0xB2AC90)
			.tankTexture("tank_WOOD_MASH");


	// =====================================================================================
	//  硝化 · 芳烃 · 推进剂线（id 2021 ~ 2028）

	/** 甘油：无色黏稠、微甜，可燃性低。硝化甘油与树脂类工艺的前体。 */
	public static final FluidDef GLYCEROL = def("GLYCEROL", 2021, 0xE8E4D2, "glycerol")
			.liquid()
			.viscous()                                    // 黏稠：不能喷雾/雾化
			.temp(20)
			.hazard(0, 1, 0, EnumSymbol.NONE)
			.fuel(100_000L, FuelGrade.LOW, 2.0D)          // 甘油：10万 TU / 20万 HE，闪点 160°C 只配当低级油
			.canister(0xE8E4D2)
			.tankTexture("tank_GLYCEROL");

	/** 硝基甲烷：无色油状，淡黄；本身是成熟的赛车/模型机燃料。 */
	public static final FluidDef NITROMETHANE = def("NITROMETHANE", 2022, 0xE0D48E, "nitromethane")
			.liquid()
			.temp(20)
			.hazard(1, 3, 2, EnumSymbol.NONE)             // 可燃 3：闪点低、蒸气易爆
			.fuel(430_000L, FuelGrade.HIGH, 5.4D)         // 硝基甲烷：40万 TU / 200万 HE，加压制得，自带氧可极富油燃烧
			.trait(Fluids.P_FUEL)                         // 燃烧生 NOx：按精炼油排放
			.canister(0xE0D48E)
			.tankTexture("tank_NITROMETHANE");

	/** 硝基苯：淡黄绿色油状、苦杏仁味。苯胺与二硝基苯的前体。 */
	public static final FluidDef NITROBENZENE = def("NITROBENZENE", 2023, 0xC6C266, "nitrobenzene")
			.liquid()
			.temp(20)
			.hazard(2, 2, 0, EnumSymbol.NONE)             // 毒 2：经皮吸收，伤血红素
			.fuel(550_000L, FuelGrade.MEDIUM, 5.5D)       // 硝基苯：32万 TU / 160万 HE，芳环燃烧冒黑烟
			.trait(SOOT_AROMATIC)
			.canister(0xC6C266)
			.tankTexture("tank_NITROBENZENE");

	/** 二硝基苯：淡黄结晶（间位为主），工业品偏金黄。炸药与染料中间体。 */
	public static final FluidDef DINITROBENZENE = def("DINITROBENZENE", 2024, 0xD2A63C, "dinitrobenzene")
			.liquid()
			.temp(20)
			.hazard(3, 1, 2, EnumSymbol.NONE)             // 毒 3 / 反应 2：受热或混入还原剂会剧烈分解
			.fuel(610_000L, FuelGrade.HIGH, 9.3D)         // 二硝基苯：48万 TU / 336万 HE，富氧爆燃做功强
			.trait(SOOT_AROMATIC)
			.canister(0xD2A63C)
			.tankTexture("tank_DINITROBENZENE");

	/** 苯胺：无色但见光氧化变棕，工业品琥珀至深褐。染料、橡胶助剂、MDI 的原料。 */
	public static final FluidDef ANILINE = def("ANILINE", 2025, 0xA86632, "aniline")
			.liquid()
			.temp(20)
			.hazard(3, 1, 0, EnumSymbol.NONE)             // 毒 3：血液毒，HBM 里按高毒处理
			.fuel(720_000L, FuelGrade.MEDIUM, 15.0D)      // 苯胺：35万 TU / 350万 HE，压过煤油，三期主力
			.trait(SOOT_ANILINE)
			.canister(0xA86632)
			.tankTexture("tank_ANILINE");

	/** 硝化煤油萃取剂：煤油载 TBP（磷酸三丁酯），无色至淡稻草。PUREX 流程的萃取相。 */
	public static final FluidDef TBP_KERO = def("TBP_KERO", 2026, 0xBEC49A, "tbp_kero")
			.liquid()
			.temp(20)
			.hazard(1, 2, 0, EnumSymbol.NONE)
			.fuel(660_000L, FuelGrade.AERO, 17.0D)        
			.trait(Fluids.P_FUEL)
			.canister(0xBEC49A)
			.tankTexture("tank_TBP_KERO");

	/** 高辛烷航空汽油：按辛烷值染色的航空汽油，高标号取蓝绿。活塞式航空发动机用。 */
	public static final FluidDef AVGAS_RU = def("AVGAS_RU", 2027, 0x5FA8BE, "avgas_ru")
			.liquid()
			.temp(20)
			.hazard(1, 3, 0, EnumSymbol.NONE)
			.fuel(950_000L, FuelGrade.HIGH, 19.0D) 
			.trait(Fluids.P_FUEL)
			.canister(0x3F8AA0)                           // 铁罐压深一档，免得罐身与液体同色看不清
			.tankTexture("tank_AVGAS_RU");

	/** 四氧化二氮：与二氧化氮平衡，呈红棕色。强氧化剂，与肼类自燃，剧毒且强腐蚀。不给燃烧值——它助燃，自身不烧。 */
	public static final FluidDef N2O4 = def("N2O4", 2028, 0x9E4636, "n2o4")
			.liquid()
			.temp(20)
			.hazard(3, 0, 4, EnumSymbol.OXIDIZER)         // 反应 4 + 氧化剂符号
			.corrosive(2)
			.canister(0x9E4636)
			.tankTexture("tank_N2O4");

	/**
	 * 甲醇：无色透明液体，略有酒精味。
	 */
	public static final FluidDef METHANOL = def("METHANOL", 2029, 0x88739F, "methanol")
			.liquid()
			.gaseous()                                    // 航天版同时挂了 LIQUID 与 GASEOUS，照抄
			.temp(20)
			.hazard(3, 4, 0, EnumSymbol.NONE)             // 毒3 / 可燃4 / 反应0：与航天版一致
			.fuel(400_000L, FuelGrade.HIGH, 1.5D)         // 40万 TU / 60万 HE，与航天版一致
			.canister(0x88739F)
			.gasTank(0x88739F, 0xFFFFFF)
			.tankTexture("tank_METHANOL");

	/**
	 * 氨：无色刺激性气体，NFPA 2-0-1。整行数值（颜色 0x00A0F7、危险度 2/0/1、窒息符号、
	 * FT_Poison(4)）与航天版 Fluids.java 里的 AMMONIA 完全一致，便于两边存档互通。
	 * 若玩家已装航天版，register() 会检测到同名流体并直接复用，不会再注册一份覆盖它。
	 */
	public static final FluidDef AMMONIA = def("AMMONIA", 2030, 0x00A0F7, "ammonia")
			.gaseous()
			.temp(20)
			.hazard(2, 0, 1, EnumSymbol.ASPHYXIANT)
			.trait(new FT_Poison(true, 4))
			.gasTank(0x00A0F7, 0xFFFFFF)
			.tankTexture("tank_AMMONIA");

	// =====================================================================================
	//  甲基苯丙胺产线 · id 2031 ~ 2032
	// =====================================================================================

	/** 甲胺：无色刺激性气体，甲醇氨化制得；还原胺化反应的胺源。 */
	public static final FluidDef METHYLAMINE = def("METHYLAMINE", 2031, 0x9FC7D8, "methylamine")
			.gaseous()
			.temp(20)
			.hazard(2, 4, 0, EnumSymbol.NONE)             // 毒 2 / 可燃 4：极易燃气体
			.trait(new FT_Poison(true, 3))
			.gasTank(0x9FC7D8, 0xFFFFFF)
			.tankTexture("tank_METHYLAMINE");

	/** 苯基丙酮：淡黄色液体，P2P；甲基苯丙胺的直接前体，有杏仁味。 */
	public static final FluidDef PHENYLACETONE = def("PHENYLACETONE", 2032, 0xD8C078, "phenylacetone")
			.liquid()
			.temp(20)
			.hazard(2, 2, 0, EnumSymbol.NONE)
			.fuel(300_000L, FuelGrade.MEDIUM, 6.0D)       // 30万 TU / 180万 HE，芳酮类溶剂
			.trait(SOOT_AROMATIC)
			.canister(0xD8C078)
			.tankTexture("tank_PHENYLACETONE");


	//注意从2005开始：
    /**
     * 【全属性示例流体】—— 把所有能加的属性都加了一遍，用来对照“写法 ↔ 实际效果”。
     *
     * 这是真会注册的流体（id 2999），不需要就整段删掉/注释掉。
     * 其中“互斥/极端”的属性（反物质、纯铅罐、隐藏 ID、禁止容器）用注释给出写法，默认不启用，
     * 否则会盖掉普通容器的行为。
     *
     * 具体每个属性的效果见下方 FluidDef 各方法的注释；游戏内可用流体识别码选“示例流体”验证。
     */
    public static final FluidDef EXAMPLE = def("EXAMPLE_FLUID", 2999, 0xFF00FF, "water")
            .liquid()                 // 液体：喷雾/雾化、多数机器只收液体
            .viscous()                // 粘稠：不能喷雾/雾化（EntityMist 会拒绝）
            .temp(120)                // 温度 120°C：热流体判定（部分配方/机器会看）
            .hazard(2, 3, 1, EnumSymbol.ACID)  // 毒2/可燃3/反应1 + 酸符号：储罐侧面菱形 & tooltip
            .fuel(50_000L, FuelGrade.HIGH, 4.0D) // 每桶 5万 TU；燃烧值 20万 HE，HIGH 级（柴油机/HP引擎）
            .canister(0xFF00FF)       // 铁罐 canister_full，罐身染色
            // .tankTexture("tank_EXAMPLE") // 专用储罐贴图：assets/.../textures/tank/tank_EXAMPLE.png（不写则 tank_NONE+染色）
            .gasTank(0xFF00FF, 0xFFFFFF) // 气体罐 gas_full 也允许（演示用；液体一般不需要）
            .corrosive(2)             // 腐蚀等级 2：喷雾/化学弹伤害、容器限制
            .toxin(new FT_Toxin.ToxinEffects(HazardClass.GAS_LUNG, false)
                    .add(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.poison.id, 100, 0)))
                                      // 中毒：按 HazardClass 判防护，防毒面具可挡
            .ventRadiation(0.05F)     // 每 mB 释放 0.05 rad（罐子破裂/排放时）
            .polluting(new FT_Polluting()
                    .burn(PollutionType.SOOT, 100F)
                    .release(PollutionType.POISON, 10F))
                                      // 燃烧产生烟尘、泄漏产生毒污染
            .coolable(Fluids.WATER, 1_000, 1_000, 100, FT_Coolable.CoolingType.HEATEXCHANGER)
                                      // 冷却：1000 mB 吸 100 TU 变成 1000 mB 水（热交换器效率 100%）
            .heatable(100, 1_000, Fluids.HOTSTEAM, 1_000, FT_Heatable.HeatingType.BOILER)
                                      // 加热：1000 mB + 100 TU 变成 1000 mB 热蒸汽（锅炉效率 100%）
            .pheromone(1)             // 信息素：吸引异虫
            .pwrModerator(1.5D)       // PWR 中子通量倍率 1.5
            .delicious()              // 可饮用标记
            .unsiphonable();          // 虹吸器吸不出来
            // .amat()                // 反物质：储罐强制 DANGER 贴图、特殊处理
            // .noID()                // 不出现在流体识别码列表（想隐藏就打开）
            // .leadContainer()       // 只允许铅罐（危险流体）
            // .noContainer()         // 禁止一切通用容器（含 32k 包装）
            // .noContainer() 与 .leadContainer() 互斥，二选一

    // =====================================================================================
    //  铅锌冶炼（ISP 法）· id 2040 ~ 2042

    /**
     * 二氧化硫：烧结机烟气，无色但带刺激性臭味，NFPA 3-0-1。
     *
     * 它是这条线里硫的唯一气态出口：精矿氧化焙烧时 PbS/ZnS/CdS 的硫全进这里，
     * 再接下面那条接触法（极简版）制酸，硫就重新闭回硫酸，整条线不必外购酸。
     */
    public static final FluidDef SULFUR_DIOXIDE = def("SULFUR_DIOXIDE", 2040, 0xC9C24E, "sulfur_dioxide")
            .gaseous()
            .temp(20)
            .hazard(3, 0, 1, EnumSymbol.ASPHYXIANT)
            .trait(new FT_Poison(true, 3))
            .corrosive(2)
            .gasTank(0xC9C24E, 0xFFFFFF)
            .tankTexture("tank_SULFUR_DIOXIDE");

    /**
     * 含镉浸出液：蓝粉加硫酸浸出后富集了镉的那一半，暗金黄。
     *
     * 电积时镉在阴极析出、硫酸在阳极再生，所以这一步只吃液、回酸，不需要额外补酸。
     */
    public static final FluidDef CADMIUM_LIQUOR = def("CADMIUM_LIQUOR", 2041, 0xA8853F, "cadmium_liquor")
            .liquid()
            .temp(40)
            .hazard(4, 0, 1, EnumSymbol.NONE)
            .trait(new FT_Poison(true, 4))
            .corrosive(2)
            .canister(0xA8853F)
            .tankTexture("tank_CADMIUM_LIQUOR");

    /**
     * 硫酸锌溶液：同一批浸出液里锌的那一半，无色微浊。
     *
     * 单独分出来是为了让锌有回收去处——电积成锌粉后既能在本模组里置换镉，
     * 也能以 dustZinc 的身份被 GT 的黄铜、镀锌配方直接吃掉。
     */
    public static final FluidDef ZINC_SULFATE = def("ZINC_SULFATE", 2042, 0xBFC9C4, "zinc_sulfate")
            .liquid()
            .temp(40)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .corrosive(1)
            .canister(0xBFC9C4)
            .tankTexture("tank_ZINC_SULFATE");

    /**
     * 亚硫酸钠溶液：碱液吸收二氧化硫得到的还原剂，无色到淡米色、弱碱性。
     *
     * <p>它存在的唯一理由是给脱砷提供一个"不是随手就能拿到"的还原剂：
     * 气体 SO2 只要能烧硫就有，而亚硫酸钠溶液要先把浓缩海水电解出碱液，
     * 再把 SO2 用碱液吸收 —— 两级上游，卡住了砷的产出速度。
     * 化学上这是湿法炼锌净化段的标准药剂（Na2SO3 把 As(V) 还原成 As(III) 析出白砷）。</p>
     */
    public static final FluidDef SODIUM_SULPHITE = def("SODIUM_SULPHITE", 2043, 0xE3DCB6, "sodium_sulphite")
            .liquid()
            .temp(30)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .corrosive(1)
            .canister(0xE3DCB6)
            .tankTexture("tank_SODIUM_SULPHITE");

    /**
     * 硫代硫酸钠溶液（大苏打）：无色透明，微凉感，工业上最常用的无氰还原剂。
     *
     * <p>制法是 {@code Na2SO3 + S → Na2S2O3}，现实里硫粉要在 80~100 ℃ 微沸 2~4 小时。
     * 在本包里它是"硫的第二种形态"：硫粉走复式炼焦炉或热解炉只能变成 SO2 气体，
     * 而 SO2 一离开设备就得靠罐子装，不方便堆存；变成硫代硫酸钠液之后，
     * 硫就能以液体形式进管道、进配方，还能拿去浸金。</p>
     *
     * <p>它在浸出里会被氧化降解成连四硫酸盐和硫酸盐，这是该工艺最大的成本项，
     * 所以置换之后只回得来七成，缺的那三成得现补。</p>
     */
    public static final FluidDef SODIUM_THIOSULFATE = def("SODIUM_THIOSULFATE", 2044, 0xDCE4E0, "sodium_thiosulphate")
            .liquid()
            .temp(30)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .canister(0xDCE4E0)
            .tankTexture("tank_SODIUM_THIOSULFATE");

    /**
     * 金硫代硫酸溶液：含金络合物 {@code [Au(S2O3)2]3-} 的淡金色浸出液。
     *
     * <p>硫代硫酸根把金氧化溶解成络阴离子，这一步是"无氰"的全部意义所在 ——
     * 全程不碰氰化物，浸出液本身无毒，尾液可以直接排。
     * 代价是络合物比氰化体系不稳定，必须靠铜离子催化、而且只能在弱碱性下工作。</p>
     *
     * <p>出来后用锌粉置换，机理和铅锌线的镉置换完全一样，只是被置换的金属更贵。</p>
     */
    public static final FluidDef GOLD_THIOSULFATE = def("GOLD_THIOSULFATE", 2045, 0xD9A93C, "gold_thiosulphate")
            .liquid()
            .temp(40)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .canister(0xD9A93C)
            .tankTexture("tank_GOLD_THIOSULFATE");

    /**
     * 最简示例（复制这一行改参数即可）：
     * 一种可燃烧的燃料，每桶 120,000 热量，燃烧值 300,000，MEDIUM 级。
     */
    // public static final FluidDef MY_FUEL = def("MY_FUEL", 2003, 0xFFAA00, "fuel")
    //         .liquid()
    //         .fuel(120_000L, FuelGrade.MEDIUM, 2.5D)
    //         .canister(0xFFAA00);

    private ModFluids() {
    }

    /**
     * 当前是否正处在 Fluids.reloadFluids() 的回调里。
     *
     * 这个回调是 HBM 唯一会“先清空外来流体、再回调、最后统一把 foreignFluids 加回
     * metaOrder”的路径。回调期间任何一方都不该再往 metaOrder 里塞东西，否则收尾时
     * 会再塞一次，同名条目重复 → 装配机的装罐配方 duplicate ID 崩档。
     */
    private static boolean duringReload = false;

    public static boolean isDuringReload() {
        return duringReload;
    }

    /** 在 preInit 里调用一次。 */
    public static void init() {
        registerAll(false);
        HBMCompat.ensureHCL();   // 必须直接调用：HBM 的 Fluids.init() 早于附属 preInit，
                                 // 首次 onFluidsLoad 回调早已跑完，只挂监听器永远不会触发
        Fluids.additionalListeners.add(new IFluidRegisterListener() {
            @Override
            public void onFluidsLoad() {
                // 这一次只会被 Fluids.reloadFluids() 触发（HBM PostLoad 与 /ntmreload）。
                // 它清空 foreignFluids 后回调我们，回调返回之后还会执行
                //     for(FluidType custom : foreignFluids) metaOrder.add(custom);
                // 所以这里**不能**再往 metaOrder 里加，否则每个流体会出现两次，
                // 装配机的装罐配方就会撞上面那个 duplicate ID。
                duringReload = true;
                try {
                    registerAll(true);
                    HBMCompat.ensureHCL();   // /ntmreload 重载流体后再次补注册
                } finally {
                    duringReload = false;
                }
            }
        });

        MakeNTMGreatAgain.LOG.info("Registered " + ALL.size() + " HBM fluid(s):");
        for (FluidDef def : ALL) MakeNTMGreatAgain.LOG.info("  - " + def.describe());

        audit();
        dumpOrder();
    }

    /**
     * 自检：宿主用 metaOrder 生成装配机的“装罐”配方（配方名 = "ass.package" + 流体本地化名），
     * 所以 metaOrder 里一旦出现两个同名流体，HBM 的 PostLoad 就会抛 duplicate ID 直接崩游戏。
     * 这里把实际状态打出来，便于定位是谁多加了。
     */
    public static void audit() {
        try {
            int metaSize = Fluids.metaOrder.size();
            int idSize = -1;
            try { idSize = Fluids.getAll().length; } catch (Throwable ignored) { }
            MakeNTMGreatAgain.LOG.info("[审计] idMapping=" + idSize + " metaOrder=" + metaSize
                    + " foreignFluids=" + Fluids.foreignFluids.size());

            java.util.HashMap<String, Integer> first = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < metaSize; i++) {
                FluidType t = Fluids.metaOrder.get(i);
                if (t == null) { MakeNTMGreatAgain.LOG.warn("[审计] metaOrder[" + i + "] = null"); continue; }
                String key = t.getUnlocalizedName();
                Integer prev = first.get(key);
                if (prev == null) {
                    first.put(key, Integer.valueOf(i));
                } else {
                    FluidType p = Fluids.metaOrder.get(prev.intValue());
                    MakeNTMGreatAgain.LOG.warn("[审计] 重名 " + key
                            + " → #" + prev + "(id=" + p.getID() + ", key=" + p.getName() + ", obj=" + System.identityHashCode(p) + ")"
                            + " / #" + i + "(id=" + t.getID() + ", key=" + t.getName() + ", obj=" + System.identityHashCode(t) + ")"
                            + (p == t ? " 同一个对象被加了两次" : " 两个不同对象"));
                }
            }
        } catch (Throwable e) {
            MakeNTMGreatAgain.LOG.warn("[审计] 执行失败", e);
        }
    }

    /** 把 metaOrder 全文与 foreignFluids 打印出来，每行 12 条，便于对照。 */
    public static void dumpOrder() {
        try {
            StringBuilder sb = new StringBuilder();
            int n = Fluids.metaOrder.size();
            for (int i = 0; i < n; i++) {
                FluidType t = Fluids.metaOrder.get(i);
                sb.append(i).append(':').append(t == null ? "null" : t.getName()).append(' ');
                if ((i + 1) % 12 == 0) { MakeNTMGreatAgain.LOG.info("[审计/metaOrder] " + sb); sb.setLength(0); }
            }
            if (sb.length() > 0) MakeNTMGreatAgain.LOG.info("[审计/metaOrder] " + sb);

            sb.setLength(0);
            for (FluidType t : Fluids.foreignFluids) {
                sb.append(t.getName()).append('(').append(t.getID()).append(')').append(' ');
            }
            MakeNTMGreatAgain.LOG.info("[审计/foreign] " + sb);
        } catch (Throwable e) {
            MakeNTMGreatAgain.LOG.warn("[审计] dump 失败", e);
        }
    }

    private static void registerAll(boolean duringReload) {
        for (FluidDef def : ALL) def.register();
        // reloadFluids 的收尾动作就是 `for(foreignFluids) metaOrder.add(...)`，
        // 那条路上由它负责入列，我们再插手就会重复。
        if (!duringReload) {
            for (FluidDef def : ALL) def.enterMetaOrderOnce();
        }
        linkFluids();
        addConduitRecipes();
    }

    /** 已经补过管道配方的流体 id，避免重载时重复注册。 */
    private static final Set<Integer> CONDUIT_DONE = new HashSet<Integer>();

    /**
     * 补流体识别码 / 流体管道的合成配方。
     *
     * HBM 在它自己的 preInit 里（MainRegistry.PreLoad → CraftingManager.mainRegistry）
     * 用 `for(int i = 1; i < Fluids.getAll().length; i++)` 生成这批配方。附属的 preInit
     * 排在其后（实测 hbm 13:10:21、附属 13:10:30），那时数组长度还停在原版流体的数量上，
     * 我们 2021~2030 这些 id 完全不在循环范围内 —— 后果是做不出带自家流体染色参
     * 的管道，玩家在 NEI 和机器里都"找不到"这些新流体。
     *
     * 这里按 HBM 原版那四条的写法逐条补上（流体管道 1 个 / 8 个，分别从空管道和
     * 任意已有管道转换）。用 CONDUIT_DONE 防重，重载回调时不会重复注册。
     */
    private static void addConduitRecipes() {
        for (FluidDef def : ALL) {
            FluidType type = def.type;
            if (type == null || type == Fluids.NONE) continue;
            int id = type.getID();
            if (id <= 0 || type.hasNoID()) continue;
            if (!CONDUIT_DONE.add(id)) continue;

            ItemStack ident = new ItemStack(ModItems.fluid_identifier_multi, 1, id);
            ItemFluidIDMulti.setType(ident, type, true);

            // 空管道 → 指定流体的管道
            CraftingManager.addShapelessAuto(new ItemStack(ModItems.fluid_duct, 1, id),
                    new Object[] { new ItemStack(ModBlocks.fluid_duct_neo, 1), ident });
            Object[] eightNeo = new Object[9];
            for (int i = 0; i < 8; i++) eightNeo[i] = new ItemStack(ModBlocks.fluid_duct_neo, 1);
            eightNeo[8] = ident;
            CraftingManager.addShapelessAuto(new ItemStack(ModItems.fluid_duct, 8, id), eightNeo);

            // 已有管道 → 改造成指定流体的管道
            CraftingManager.addShapelessAuto(new ItemStack(ModItems.fluid_duct, 1, id),
                    new Object[] { new ItemStack(ModItems.fluid_duct, 1, OreDictionary.WILDCARD_VALUE), ident });
            Object[] eightAny = new Object[9];
            for (int i = 0; i < 8; i++) eightAny[i] = new ItemStack(ModItems.fluid_duct, 1, OreDictionary.WILDCARD_VALUE);
            eightAny[8] = ident;
            CraftingManager.addShapelessAuto(new ItemStack(ModItems.fluid_duct, 8, id), eightAny);
        }
    }

    /**
     * 跨流体接线：等所有 FluidDef 的 .type 都赋好值之后，再挂需要“引用另一个流体”的特性。
     * 这些特性是幂等的（addTraits 按类名覆盖），所以 /ntmreload 重注册时重复调用没问题。
     */
    private static void linkFluids() {

        // 盐水 --锅炉(100 TU/1mB)--> 热盐水
        SALT_WATER.type.addTraits(new FT_Heatable()
                .setEff(FT_Heatable.HeatingType.BOILER, 1.0D)
                .addStep(300, 1, HOT_SALT_WATER.type, 1000));

        // 热盐水 --涡轮/热交换(放热 100 TU/1000mB)--> 盐水（不想要可以删掉）
        HOT_SALT_WATER.type.addTraits(new FT_Coolable(SALT_WATER.type, 1, 1_000, 300)
                .setEff(FT_Coolable.CoolingType.HEATEXCHANGER, 1.0D));
    }
	
    private static FluidDef def(String name, int id, int color, String textureName) {
        FluidDef def = new FluidDef(name, id, color, textureName);
        ALL.add(def);
        return def;
    }

    /** 客户端渲染器用：取某个流体的“储罐模型贴图”，没定义返回 null（走 HBM 默认逻辑）。 */
    public static ResourceLocation getTankTexture(FluidType type) {
        return type == null ? null : TANK_TEXTURES.get(type);
    }

    /**
     * 给一个已在 FluidDef 列表之外注册的流体（如跨版本补注册的盐酸）登记储罐模型贴图。
     * fileName 不含路径与后缀，实际指向 assets/makentmgreatagain/textures/tank/&lt;fileName&gt;.png。
     */
    public static void addTankTexture(FluidType type, String fileName) {
        if (type == null || fileName == null) return;
        TANK_TEXTURES.put(type, new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/tank/" + fileName + ".png"));
    }

    /** 兼容旧写法：判断是不是盐水。 */
    public static boolean isSaltWater(FluidType type) {
        return type != null && type == SALT_WATER.type;
    }

    /**
     * 一个流体的完整定义：颜色、贴图、特性、容器、燃料参数。
     * 所有 setter 都返回 this，方便链式写。
     */
    public static final class FluidDef {

        public final String name;
        public final int id;
        public final int color;
        private ResourceLocation texture;

        private int poison;
        private int flammability;
        private int reactivity;
        private EnumSymbol symbol = EnumSymbol.NONE;
        private int temperature = 20;

        private final List<FluidTrait> traits = new ArrayList<FluidTrait>();

        /** FT_Flammable 的热量；<0 表示不加 */
        private long flammableHeat = -1L;
        /** FT_Combustible 的等级与燃烧值；grade == null 表示不加 */
        private FuelGrade grade;
        private long combustionEnergy;

        /** 储罐模型贴图文件名（不含路径/后缀），null = 用 HBM 默认逻辑 */
        private String tankTextureName;
        private Integer canisterColor;
        private int[] gasTankColors;
        private boolean noContainer;
        private boolean leadContainer;

        /** 注册完成后指向真正的 FluidType */
        public FluidType type;

        /** true = 同名流体已由别的模组（典型是航天版）注册，本模组只是复用，不拥有它 */
        private boolean external;

        /** 该流体是否由其他模组提供。是的话，它的配方也归对方，本模组不应重复注册。 */
        public boolean isExternal() { return this.external; }

        private FluidDef(String name, int id, int color, String textureName) {
            this.name = name;
            this.id = id;
            this.color = color;
            // 贴图固定放在 assets/makentmgreatagain/textures/gui/fluids/<名字>.png
            this.texture = new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/fluids/" + textureName + ".png");
        }

        // ----- 基础属性 -------------------------------------------------------------

        /** 按字段名取 Fluids 里的 trait 常量，绕开版本间类名变动导致的二进制不兼容 */
        private static FluidTrait traitByName(String field) {
            try {
                Field f = Fluids.class.getField(field);
                Object v = f.get(null);
                return v instanceof FluidTrait ? (FluidTrait) v : null;
            } catch(Exception ex) {
                MakeNTMGreatAgain.LOG.warn("[流体] 取不到 Fluids." + field + "，该 trait 已跳过");
                return null;
            }
        }

        /** 液体（必须加，否则不算液体，很多机器不收） */
        public FluidDef liquid() { traits.add(Fluids.LIQUID); return this; }
        /** 气体 */
        public FluidDef gaseous() {
            // Fluids.GASEOUS 的类型在 HBM 各版本间换过类（旧版是独立的 FT_Gaseous），
            // 直接引用字段会因描述符不符抛 NoSuchFieldError，按名字反射取就没这问题。
            FluidTrait gas = traitByName("GASEOUS");
            if(gas != null) traits.add(gas);
            return this;
        }
        /** 粘稠 */
        public FluidDef viscous() { traits.add(Fluids.VISCOUS); return this; }

        /** 温度（常温 20；热流体可设 100+） */
        public FluidDef temp(int celsius) { this.temperature = celsius; return this; }

        /** 危险信息：毒 / 可燃 / 反应（0~4）+ 危险符号（会画在储罐侧面） */
        public FluidDef hazard(int poison, int flammability, int reactivity, EnumSymbol symbol) {
            this.poison = poison;
            this.flammability = flammability;
            this.reactivity = reactivity;
            this.symbol = symbol;
            return this;
        }

        /** 自定义特性（HBM 的 FluidTrait 实例，例如 FT_Viscous、FT_Toxin） */
        public FluidDef trait(FluidTrait trait) { traits.add(trait); return this; }

        // ----- 燃料（TU / HE）-------------------------------------------------------

        /** 只产热：加热器、燃气火炬、火焰武器读这个。参数是“每 1000 mB 的热量(TU)”。 */
        public FluidDef flammable(long heatPerBucket) { this.flammableHeat = heatPerBucket; return this; }

        /** 只产电：内燃机/柴油机/燃气轮机读这个。参数是“每 1000 mB 的燃烧值(HE)”。 */
        public FluidDef combustible(FuelGrade grade, long energyPerBucket) {
            this.grade = grade;
            this.combustionEnergy = energyPerBucket;
            return this;
        }

        /**
         * 同时产热和产电（和 HBM 自带流体一样）：燃烧值 = 热量 × mult。
         * 一般 mult 取 2~10，取决于燃料等级。
         */
        public FluidDef fuel(long heatPerBucket, FuelGrade grade, double mult) {
            this.flammableHeat = heatPerBucket;
            this.grade = grade;
            this.combustionEnergy = (long) (heatPerBucket * mult);
            return this;
        }

        /**
         * GUI 里罐子/桶的液体贴图：等价于 assets/<modid>/textures/gui/fluids/<名字>.png。
         * 其实就是 def(...) 第 4 个参数的另一种写法；一般不用调，
         * 只有“同一个流体想换 GUI 图”时才用。
         */
        public FluidDef guiTexture(String fileName) {
            this.texture = new ResourceLocation(MakeNTMGreatAgain.MODID, "textures/gui/fluids/" + fileName + ".png");
            return this;
        }

        // ----- 其它特性（每条都对应一个 FluidTrait）---------------------------------

        /** 腐蚀性：等级 0~3。影响喷雾/化学弹伤害；高腐蚀流体储罐会用 DANGER 贴图。 */
        public FluidDef corrosive(int rating) { return trait(new FT_Corrosive(rating)); }

        /** 毒性：接触/背包中按 HazardClass 判定防护后中毒（可给直接伤害或药水效果）。 */
        public FluidDef toxin(FT_Toxin.ToxinEntry... entries) {
            FT_Toxin toxin = new FT_Toxin();
            for (FT_Toxin.ToxinEntry entry : entries) toxin.addEntry(entry);
            return trait(toxin);
        }

        /** 排放辐射：每 mB 释放多少 rad（罐子破裂、机器排气时生效）。 */
        public FluidDef ventRadiation(float radPerMB) { return trait(new FT_VentRadiation(radPerMB)); }

        /** 污染：燃烧/泄漏时给区块加污染（SOOT / POISON / HEAVYMETAL / FALLOUT）。 */
        public FluidDef polluting(FT_Polluting pollution) { return trait(pollution); }

        /** 可冷却：req mB 吸收 heat TU，变成 prod mB 的 coolsTo；给定类型的换热器效率 100%。 */
        public FluidDef coolable(FluidType coolsTo, int req, int prod, int heat, FT_Coolable.CoolingType... types) {
            FT_Coolable coolable = new FT_Coolable(coolsTo, req, prod, heat);
            for (FT_Coolable.CoolingType type : types) coolable.setEff(type, 1.0D);
            return trait(coolable);
        }

        /** 可加热：req mB 吸收 heat TU，变成 prod mB 的 product；给定类型的加热器效率 100%。 */
        public FluidDef heatable(int heat, int req, FluidType product, int prod, FT_Heatable.HeatingType... types) {
            FT_Heatable heatable = new FT_Heatable();
            heatable.addStep(heat, req, product, prod);
            for (FT_Heatable.HeatingType type : types) heatable.setEff(type, 1.0D);
            return trait(heatable);
        }

        /** 信息素：type 1 = 吸引异虫。 */
        public FluidDef pheromone(int type) { return trait(new FT_Pheromone(type)); }

        /** PWR 中子通量倍率（做 PWR 冷却剂用）。 */
        public FluidDef pwrModerator(double multiplier) { return trait(new FT_PWRModerator(multiplier)); }

        /** 可饮用标记。 */
        public FluidDef delicious() { return trait(new FT_Delicious()); }

        /** 不可虹吸：便携虹吸器/泵吸不出来。 */
        public FluidDef unsiphonable() { return trait(new FT_Unsiphonable()); }

        /** 反物质标记：储罐强制 DANGER 贴图，部分机器特殊处理。 */
        public FluidDef amat() { return trait(new FT_Amat()); }

        /** 不在流体识别码列表里显示（但仍可通过 ID/名字使用）。 */
        public FluidDef noID() { return trait(new FT_NoID()); }

        // ----- 容器 -----------------------------------------------------------------

        /**
         * 储罐模型贴图：等价于 assets/<modid>/textures/tank/<名字>.png。
         * 不写则按 HBM 默认逻辑（自带流体用 hbm 的图，自定义流体用 tank_NONE + 染色）。
         */
        public FluidDef tankTexture(String fileName) { this.tankTextureName = fileName; return this; }

        /** 铁罐（canister_full，1000 mB）；参数是罐身颜色 */
        public FluidDef canister(int color) { this.canisterColor = color; return this; }
        /** 气体罐（gas_full，1000 mB）；瓶身色 + 标签色 */
        public FluidDef gasTank(int bottleColor, int labelColor) { this.gasTankColors = new int[] { bottleColor, labelColor }; return this; }
        /** 禁止一切通用容器（也禁止 32k 包装） */
        public FluidDef noContainer() { this.noContainer = true; return this; }
        /** 只允许铅罐（危险流体） */
        public FluidDef leadContainer() { this.leadContainer = true; return this; }

        // ----- 内部 ----------------------------------------------------------------

        void register() {

            // 幂等，但判据不能只是 type != null。
            //
            // HBM 的 MainRegistry.PostLoad → Fluids.reloadFluids() 会先把 foreignFluids 里
            // 每一个外来流体从 idMapping / nameMapping / registerOrder / metaOrder 中移除，
            // 清空 foreignFluids，然后才回调 onFluidsLoad()，最后再把 foreignFluids 加回
            // metaOrder。我们这 21 个注册成功的流体全都在 foreignFluids 里。
            // 如果回调时只看 type != null 就 return，那么 foreignFluids 依然是空的，
            // 最后那一步加回 0 个 —— 流体就此从注册表消失：NEI 里没有、管道和储罐都不认、
            // 化工厂配方引用的是孤儿对象。所以必须确认它此刻真的还在表里。
            //
            // 仍然复用同一个 FluidType 对象（CompatFluidRegistry 会先查 fluidMigration），
            // 不会产生第二个同名对象，也就不存在 metaOrder 重名导致的装罐配方崩溃。
            if(this.type != null && Fluids.fromName(this.name) == this.type) return;

            // 走到这里且 type 非 null，说明是 reloadFluids 把我们从注册表里清掉之后的重登记
            boolean reregister = this.type != null;

            // 同名流体已被别的模组（典型是航天版 NTM: Space 的 AMMONIA）注册时，直接复用，绝不覆盖。
            // 原因：CompatFluidRegistry → FluidType.setupForeign → Fluids.register() 内部是
            // nameMapping.put(getName(), fluid)，后注册者静默顶掉先注册者；一旦顶掉，对方已存档的
            // 管道/储罐按名字查表会查到我们这份，ID 与特性全变，等于毁档。
            FluidType sameName = Fluids.fromName(this.name);
            if(this.type == null && sameName != null && sameName != Fluids.NONE) {
                MakeNTMGreatAgain.LOG.info("[流体] " + this.name + " 已由其他模组注册，复用其定义，不重复注册");
                this.type = sameName;
                this.external = true;
                return;
            }

            FluidType existing = Fluids.fromID(this.id);
            if (existing != null && existing != Fluids.NONE && existing != this.type) {
                MakeNTMGreatAgain.LOG.warn("流体 ID " + this.id + " 已被 " + existing.getName()
                        + " 占用，" + this.name + " 会覆盖它！请换一个 ID。");
            }

            FluidType fluid = CompatFluidRegistry.registerFluid(
                    this.name, this.id, this.color,
                    this.poison, this.flammability, this.reactivity,
                    this.symbol, this.texture);

            fluid.setTemp(this.temperature);

            for (FluidTrait trait : this.traits) fluid.addTraits(trait);

            if (this.flammableHeat >= 0L) fluid.addTraits(new FT_Flammable(this.flammableHeat));
            if (this.grade != null) fluid.addTraits(new FT_Combustible(this.grade, this.combustionEnergy));
            if (this.noContainer) fluid.addTraits(Fluids.NOCON);
            if (this.leadContainer) fluid.addTraits(Fluids.LEADCON);

            if (this.canisterColor != null) fluid.addContainers(new Fluids.CD_Canister(this.canisterColor));
            if (this.gasTankColors != null) fluid.addContainers(new Fluids.CD_Gastank(this.gasTankColors[0], this.gasTankColors[1]));

            this.type = fluid;
            if (reregister) {
                MakeNTMGreatAgain.LOG.info("[流体] " + this.name + " 已被 reloadFluids 清出注册表，现已重新登记（id=" + this.id + "）");
            }

            if (this.tankTextureName != null) {
                TANK_TEXTURES.put(fluid, new ResourceLocation(MakeNTMGreatAgain.MODID,
                        "textures/tank/" + this.tankTextureName + ".png"));
            }
        }

        void enterMetaOrderOnce() {
            if (this.type == null) return;

            // 按“本地化名”查重，而不是按对象身份（List.contains 用的就是身份）。
            // 宿主（NTM: Space H261）里 METHANOL 存在两个不同的 FluidType 对象：
            //   - metaOrder 里放的是其中一个（第 137 位）
            //   - nameMapping 按名字查到的是另一个
            // 只按身份查重会漏掉，把第二个也塞进 metaOrder，于是装配机的装罐配方
            // "ass.package" + "hbmfluid.methanol" 被注册两次，HBM PostLoad 直接崩游戏。
            String u = this.type.getUnlocalizedName();
            for (FluidType t : Fluids.metaOrder) {
                if (t == this.type || (t != null && u.equals(t.getUnlocalizedName()))) {
                    if (t != this.type) {
                        MakeNTMGreatAgain.LOG.info("[流体] metaOrder 已有同名的 " + u + "（id=" + t.getID()
                                + "），本次不再重复加入（我方同名字体 id=" + this.type.getID() + "）");
                    }
                    return;
                }
            }
            Fluids.metaOrder.add(this.type);
        }

        /** 生成一行日志/说明：名字、ID、颜色、燃料信息。 */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append(this.name).append(" (id=").append(this.id)
              .append(", color=#").append(String.format("%06X", this.color)).append(")");
            if (this.flammableHeat >= 0L) sb.append(" flammable=").append(this.flammableHeat).append("TU/bucket");
            if (this.grade != null) sb.append(" combustible=").append(this.combustionEnergy).append("HE/bucket(").append(this.grade.name()).append(")");
            return sb.toString();
        }

        /** 便捷：直接得到一个 FluidStack。 */
        public com.hbm.inventory.FluidStack stack(int millibuckets) {
            return new com.hbm.inventory.FluidStack(this.type, millibuckets);
        }
    }
}

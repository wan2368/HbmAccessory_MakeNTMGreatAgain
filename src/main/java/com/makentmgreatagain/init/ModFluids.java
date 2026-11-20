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
import com.makentmgreatagain.fluid.FT_CoreTank;

import api.hbm.fluidmk2.IFluidRegisterListener;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

/**
 * 本模组的 HBM 流体注册中心。加一种新流体只需在常量区写一行 def(...)，
 * 容器、metaOrder、燃料特性、储罐贴图都由下面的 FluidDef 统一处理。
 *
 * <pre>
 * public static final FluidDef BRINE = def("BRINE", 2002, 0x1DBBF5, "water").liquid().canister(0x1DBBF5);
 * // 能烧的再加 .fuel(120_000L, FuelGrade.MEDIUM, 2.5D)
 * // 用的时候：ModFluids.BRINE.type / ModFluids.BRINE.stack(1000)
 * </pre>
 *
 * <p>{@link FT_Flammable} 产热（TU，加热器与火炬读它）；{@link FT_Combustible} 产电（HE，
 * 内燃机、柴油机、燃气轮机读它）。FuelGrade 决定哪类机器愿意烧：
 * LOW 加热油/工业油，MEDIUM 汽油，HIGH 柴油，AERO 煤油，GAS 天然气。</p>
 *
 * <p>ID 与内部名一旦发布就不能改（存档、管道、容器 meta、配方 JSON 都按它们记录）。</p>
 */
public final class ModFluids {
    private static final List<FluidDef> ALL = new ArrayList<FluidDef>();

    /**
     * 流体 -> 储罐模型贴图。
     *
     * <p>HBM 的 RenderFluidTank 按 hbm 命名空间拼 {@code tank_<NAME>.png}，找不到附属的图，
     * 所以这里单独记一张，由 RenderModFluidTank 按 FluidType 查。</p>
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

	/** 乙酸 */
	public static final FluidDef ACETIC_ACID = def("ACETIC_ACID", 2049, 0xdddddd, "acetic_acid")
		.liquid()
		.canister(0xdddddd)
		.temp(20)
		.tankTexture("tank_ACETIC_ACID");

	/** 环氧丙烷 */
	public static final FluidDef PROPYLENE_OXIDE = def("PROPYLENE_OXIDE", 2050, 0x8FD4C4, "propylene_oxide")
		.liquid()
		.canister(0x8FD4C4)
		.temp(20)
		.flammable(120_000L)
		.tankTexture("tank_PROPYLENE_OXIDE");

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
			.fuel(140_000L, FuelGrade.LOW, 2.0D)          // 甘油：14万 TU / 28万 HE，闪点 160°C 只配当低级油
			.canister(0xE8E4D2)
			.tankTexture("tank_GLYCEROL");

	/** 硝基甲烷：无色油状，淡黄；本身是成熟的赛车/模型机燃料。 */
	public static final FluidDef NITROMETHANE = def("NITROMETHANE", 2022, 0xE0D48E, "nitromethane")
			.liquid()
			.temp(20)
			.hazard(1, 3, 2, EnumSymbol.NONE)             // 可燃 3：闪点低、蒸气易爆
			.fuel(780_000L, FuelGrade.HIGH, 5.4D)         // 硝基甲烷：78万 TU / 421.2万 HE，加压制得，自带氧可极富油燃烧
			.trait(Fluids.P_FUEL)                         // 燃烧生 NOx：按精炼油排放
			.canister(0xE0D48E)
			.tankTexture("tank_NITROMETHANE");

	/** 硝基苯：淡黄绿色油状、苦杏仁味。苯胺与二硝基苯的前体。 */
	public static final FluidDef NITROBENZENE = def("NITROBENZENE", 2023, 0xC6C266, "nitrobenzene")
			.liquid()
			.temp(20)
			.hazard(2, 2, 0, EnumSymbol.NONE)             // 毒 2：经皮吸收，伤血红素
			.fuel(854_000L, FuelGrade.MEDIUM, 5.5D)       // 硝基苯：85.4万 TU / 469.7万 HE，芳环燃烧冒黑烟
			.trait(SOOT_AROMATIC)
			.canister(0xC6C266)
			.tankTexture("tank_NITROBENZENE");

	/** 二硝基苯：淡黄结晶（间位为主），工业品偏金黄。炸药与染料中间体。 */
	public static final FluidDef DINITROBENZENE = def("DINITROBENZENE", 2024, 0xD2A63C, "dinitrobenzene")
			.liquid()
			.temp(20)
			.hazard(3, 1, 2, EnumSymbol.NONE)             // 毒 3 / 反应 2：受热或混入还原剂会剧烈分解
			.fuel(960_000L, FuelGrade.HIGH, 9.3D)         // 二硝基苯：96万 TU / 892.8万 HE，富氧爆燃做功强
			.trait(SOOT_AROMATIC)
			.canister(0xD2A63C)
			.tankTexture("tank_DINITROBENZENE");

	/** 苯胺：无色但见光氧化变棕，工业品琥珀至深褐。染料、橡胶助剂、MDI 的原料。 */
	public static final FluidDef ANILINE = def("ANILINE", 2025, 0xA86632, "aniline")
			.liquid()
			.temp(20)
			.hazard(3, 1, 0, EnumSymbol.NONE)             // 毒 3：血液毒，HBM 里按高毒处理
			.fuel(1350_000L, FuelGrade.MEDIUM, 15.0D)      // 苯胺：135万 TU / 2025万 HE，压过煤油，三期主力
			.trait(SOOT_ANILINE)
			.canister(0xA86632)
			.tankTexture("tank_ANILINE");

	/** 硝化煤油萃取剂：煤油载 TBP（磷酸三丁酯），无色至淡稻草。PUREX 流程的萃取相。 */
	public static final FluidDef TBP_KERO = def("TBP_KERO", 2026, 0xBEC49A, "tbp_kero")
			.liquid()
			.temp(20)
			.hazard(1, 2, 0, EnumSymbol.NONE)
			.fuel(975_000L, FuelGrade.AERO, 17.0D)        
			.trait(Fluids.P_FUEL)
			.canister(0xBEC49A)
			.tankTexture("tank_TBP_KERO");

	/** 高辛烷航空汽油：按辛烷值染色的航空汽油，高标号取蓝绿。活塞式航空发动机用。 */
	public static final FluidDef AVGAS_RU = def("AVGAS_RU", 2027, 0x5FA8BE, "avgas_ru")
			.liquid()
			.temp(20)
			.hazard(1, 3, 0, EnumSymbol.NONE)
			.fuel(2750_000L, FuelGrade.HIGH, 11.0D) 
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
			.fuel(430_000L, FuelGrade.MEDIUM, 6.0D)       // 43万 TU / 258万 HE，芳酮类溶剂
			.trait(SOOT_AROMATIC)
			.canister(0xD8C078)
			.tankTexture("tank_PHENYLACETONE");


	//注意从2005开始：
    /**
     * 【全属性示例流体】把所有能加的属性都写了一遍，当作「写法 ↔ 实际效果」的对照表。
     * 真正会注册（id 2999），不需要就整段删掉；互斥/极端的属性在下面用注释给出写法。
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
     */
    public static final FluidDef SODIUM_THIOSULFATE = def("SODIUM_THIOSULFATE", 2044, 0xDCE4E0, "sodium_thiosulfate")
            .liquid()
            .temp(30)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .canister(0xDCE4E0)
            .tankTexture("tank_SODIUM_THIOSULFATE");

    /**
     * 金硫代硫酸溶液：含金络合物 {@code [Au(S2O3)2]3-} 的淡金色浸出液。
     */
    public static final FluidDef GOLD_THIOSULFATE = def("GOLD_THIOSULFATE", 2045, 0xD9A93C, "gold_thiosulfate")
            .liquid()
            .temp(40)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .canister(0xD9A93C)
            .tankTexture("tank_GOLD_THIOSULFATE");

    /**
     * 钨酸钠液：白钨矿碱浸出来的清液，Na₂WO₄。
     *
     * <p>整条湿法链的中间载体：碱浸段产出，酸化段消耗（2500 mB 一批，出 4 份钨酸），
     * 浸出渣的三级处理也各回一点（R1 500、R2 1000）。所以它的总量是守恒的，
     * 玩家只要不往外倒，系统里循环的就是固定那几千 mB。</p>
     *
     * <p>温度标 60：碱浸是热料，出锅就是这个温度，用热交换器还能捞一点余热回来。
     * 不标腐蚀 —— 它要进铜管、进储罐，标了腐蚀会让前期的管线成本凭空翻倍。</p>
     */
    public static final FluidDef SODIUM_TUNGSTATE = def("SODIUM_TUNGSTATE", 2046, 0xC6D2A2, "sodium_tungstate")
            .liquid()
            .temp(60)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .canister(0xC6D2A2)
            .tankTexture("tank_SODIUM_TUNGSTATE");

    /**
     * 废酸：酸化沉钨之后的母液，硫酸钠 + 残余游离酸。
     *
     * <p>这是白钨矿能"零外排"的关键。L2 每批吐 2500 mB 废酸，而 L1 每浸一次
     * 要 500 mB 工艺水，五次正好 2500 —— 废酸直接顶替工艺水回到碱浸段。
     * 于是全程只有硫酸是净投入，没有一股废液需要往外倒（也就不会触发污染系统）。</p>
     *
     * <p>标腐蚀但伤害低（20）：它确实是酸，进不了普通铁罐，但也不至于像硝酸那样
     * 一碰就烂。这样玩家在前期就得给它准备耐酸容器，是个合理的小门槛。</p>
     */
    public static final FluidDef WASTE_ACID = def("WASTE_ACID", 2047, 0x8E7B39, "waste_acid")
            .liquid()
            .temp(40)
            .hazard(2, 0, 0, EnumSymbol.ACID)
            .canister(0x8E7B39)
            .tankTexture("tank_WASTE_ACID");

    /**
     * 砷化氢 AsH₃：白砷被锌粉在硫酸里还原出来的气体，是"白砷 → 砷粒"唯一通道。
     *
     * <p>现实里 As₂O₃ + 3Zn + 3H₂SO₄ → 2AsH₃ + 3ZnSO₄ + 3H₂O，砷以胘化物形式逸出后
     * 再热分解才得到单质砷。走这条线而不是高炉碳热还原，是因为它必须消耗锌粉，
     * 而锌粉只从本模组 ISP 铅锌线来 —— 砷是 HBM 的 RBMK 之后门控材料
     * （{@code OreDictManager.ANY_BISMOID}），不该被白钨矿副产物绕过去。</p>
     *
     * <p>毒性照 HBM 自己的氯气/光气同一口径：{@code ToxinDirectDamage(cloud, 2F, 20, GAS_LUNG)}，
     * 按防毒面具判定，无防护时每 20 tick 扣 2 点血。泄漏记重金属污染（砷是类金属毒物，
     * 语义上比 POISON 更贴切），量级取 HBM "剧毒"档 POISON_EXTREME 的同等系数 0.025。</p>
     *
     * <p>不给燃烧值：砷化氢现实中极易燃甚至常温自燃，但一旦挂上 FT_Flammable/FT_Combustible，
     * 玩家就会把它当燃料烧掉来"处理"，这条链的门槛就没了。所以刻意做成不可燃。</p>
     */
    public static final FluidDef ARSINE = def("ARSINE", 2048, 0x5E8C7A, "arsine")
            .gaseous()
            .temp(-60)                                       // 沸点 -62.5°C
            .hazard(4, 4, 0, EnumSymbol.NONE)                 // NFPA 健康 4 / 易燃 4 / 反应 0
            .toxin(new FT_Toxin.ToxinDirectDamage(com.hbm.lib.ModDamageSource.cloud, 2F, 20, HazardClass.GAS_LUNG, false))
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.025F))
            .gasTank(0x5E8C7A, 0xFFFFFF)
            .tankTexture("tank_ARSINE");

    // =====================================================================================
    //  深海结核开采 · id 2051 ~ 2052（湿法线的入口：两股矿浆）
    //
    //  这两个是「浆」不是「液」，所以都带 .viscous()：管道流速减半、不能喷雾，
    //  和化冶线那 5 个料液（刻意不加 viscous）正好形成对照 —— 浆要慢慢走，料液要快。
    //
    //  属性参照对象是 HBM 自己的赤泥 REDMUD（拜耳法废渣，同一类东西）：
    //    FluidType("REDMUD", 0xD85638, 3, 0, 4).addTraits(LIQUID, VISCOUS, LEADCON,
    //        new FT_Corrosive(60), new FT_Polluting().release(POISON, POISON_EXTREME))
    //    + FT_Toxin(ToxinEffects(GAS_BLISTERING) + 凋零 II 30s)
    //  赤泥是 pH 12~13 的强碱渣，我们这两股只是洗选/重选加水磨出来的矿浆（近中性偏碱），
    //  所以腐蚀只给 15~20，毒素也削一档 —— 不要照抄 60 和凋零，那是拜耳法的量级。
    //
    //  FT_Toxin 的生效场合要留意：只有 EntityMist（雾）和 EntityChemical（化学弹）会调
    //  FT_Toxin.affect()，站在液体里本身不会中毒。也就是说这两条毒素实际生效的场景是
    //  「罐子被打破溅一身 / 挨了化学弹」，而不是「掉进尾矿池就掉血」。
    //
    //  两股都不给 FT_Flammable：矿浆烧不起来，写了也只会误导玩家拿去烧。
    // =====================================================================================

    /**
     * 尾砂浆：结核洗选后剩下的细粒尾矿浆，灰褐色悬浮液。
     *
     * <p>选矿废水，pH 8~9（弱碱），带走的细泥里有镍铜钴锰的氢氧化物胶体，所以：
     * 腐蚀 20（弱碱，是赤泥 60 的三分之一）、毒污染 0.15×POISON_EXTREME、重金属 0.06。
     *
     * <p>毒素只给「皮肤刺激 + 短暂挖掘疲劳」：泥浆糊手上会打滑，但不至于中毒 ——
     * 尾砂浆在整条链里的定位恰恰是「好东西已经被选走了」，毒性比后面任何一股料液都低。
     * 挖掘疲劳取 120 tick（6 秒）、等级 I，够让玩家意识到该洗个手，不影响正常干活。</p>
     */
    public static final FluidDef TAILINGS_SLURRY = def("TAILINGS_SLURRY", 2051, 0x8C8577, "tailings_slurry")
            .liquid()
            .viscous()
            .temp(20)
            .hazard(1, 0, 0, EnumSymbol.NONE)         // 健康 1：弱碱废水，不燃不爆
            .corrosive(20)                            // 弱碱，赤泥(60)的三分之一
            .toxin(new FT_Toxin.ToxinEffects(HazardClass.GAS_BLISTERING, false)
                    .add(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.digSlowdown.id, 120, 0)))
            .polluting(new FT_Polluting()
                    .release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.15F)
                    .release(PollutionType.HEAVYMETAL,
                            com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.06F))
            .canister(0x8C8577)
            .tankTexture("tank_TAILINGS_SLURRY");

    /**
     * 重砂浆：重选富集出来的重矿物浓浆（锆石、钛铁矿、独居石、磷钇矿），深褐色。
     *
     * <p>这是两股里重金属浓度最高的一股：独居石本身就是铈镧系与钍的磷酸盐，锆石里还
     * 常带铀钍，所以重金属污染给 0.12 —— 尾砂浆的两倍、钴料液的 1.5 倍。腐蚀只给 15，
     * 重选全程是清水介质，比洗选废水还温和。
     *
     * <p><b>刻意不给 FALLOUT（核沉降污染）</b>：独居石确实含 ThO₂（通常 5~10%），
     * 但矿浆阶段钍还锁在磷酸盐晶格里，既不挥发也不溶出；判成核污染会让整片矿区变成
     * 辐射区，手感和平衡都不对。钍的放射性留到「钍精矿 → 酸浸」那一步再单独处理。
     *
     * <p>毒素给「皮肤刺激 + 轻度中毒」：重金属细颗粒沾在皮肤上会起疹、吸进去会中毒，
     * 中毒取 100 tick（5 秒）、等级 I（不会致死，最低留 1 点血）。</p>
     */
    public static final FluidDef HEAVY_SAND_SLURRY = def("HEAVY_SAND_SLURRY", 2052, 0x6B5A44, "heavy_sand_slurry")
            .liquid()
            .viscous()
            .temp(20)
            .hazard(2, 0, 0, EnumSymbol.NONE)         // 健康 2：重矿物 + 伴生钍，仍不可燃
            .corrosive(15)                            // 清水介质，比尾砂浆还温和
            .toxin(new FT_Toxin.ToxinEffects(HazardClass.GAS_BLISTERING, false)
                    .add(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.poison.id, 100, 0)))
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.12F))
            .canister(0x6B5A44)
            .tankTexture("tank_HEAVY_SAND_SLURRY");

    // =====================================================================================
    //  化冶线 · id 2053 ~ 2057（深海结核湿法支线的 5 个中间流体）
    //
    //  贴图已落盘，路径固定：
    //    GUI 小图   assets/makentmgreatagain/textures/gui/fluids/<textureName>.png
    //    储罐模型   assets/makentmgreatagain/textures/tank/tank_<NAME>.png
    //  罐子必须逐条 .tankTexture()：HBM 的 RenderFluidTank 按 hbm 命名空间拼路径，
    //  只把文件放进去不登记，是找不到这张图的。
    //
    //  属性口径（照 HBM 本体那套，不要自己另发明一套）：
    //    · 腐蚀 FT_Corrosive 是 **0~100**，不是 0~3。HBM 本体参考值：
    //      溶剂 30 / 硫酸 50 / 硝酸 60 / WATZ 60 / 最毒 80；>50 才算「强腐蚀」，
    //      并且会实际影响喷雾、化学弹和容器限制，填 1、2 等于几乎没有腐蚀。
    //    · 毒素用 FT_Toxin（FT_Poison 在 HBM 里已标 @Deprecated）：
    //      气体伤肺用 HazardClass.GAS_LUNG（它的 lang 键就是「氯气」），
    //      酸液溅皮肤用 HazardClass.GAS_BLISTERING（lang 键「腐蚀性物质」）。
    //    · 污染 FT_Polluting：.release() 是泄漏/排放时加的污染，.burn() 只在
    //      有 FT_Flammable/FT_Combustible 时才生效，没有燃料属性的流体写了也白写。
    //      量级基准 Fluids.POISON_EXTREME = POISON_PER_SECOND * 0.025。
    // =====================================================================================

    /**
     * 尾氯化气：氯化焙烧 / 氯化浸出之后排出的尾气，黄绿色，带游离氯。
     *
     * <p>按气体处理（GUI 图 alpha 150 半透明）。含氯 → 呛肺、湿气下强腐蚀，
     * 所以挂了 {@link FT_Toxin}（GAS_LUNG 正是「氯气」这个危险类别）+ 腐蚀 40。
     * 毒伤取 1.5 点/秒（delay 20），戴防毒面具可挡；比 HBM 的芥子气（4 点/10t）轻得多，
     * 免得玩家在产线旁边路过一次就掉半管血。</p>
     */
    public static final FluidDef TAILINGS_CHLORIDE = def("TAILINGS_CHLORIDE", 2053, 0xB4CC48, "tailings_chloride")
            .gaseous()
            .temp(60)
            .hazard(3, 0, 1, EnumSymbol.ASPHYXIANT)   // 健康 3 / 反应 1：氯气是窒息性刺激气
            .corrosive(40)                            // 湿氯气腐蚀，介于溶剂 30 与硫酸 50 之间
            .toxin(new FT_Toxin.ToxinDirectDamage(com.hbm.lib.ModDamageSource.cloud, 1.5F, 20, HazardClass.GAS_LUNG, false))
            .polluting(new FT_Polluting().release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.6F))
            .gasTank(0xB4CC48, 0xFFFFFF)
            .tankTexture("tank_TAILINGS_CHLORIDE");

    /**
     * 尾氯重馏分：尾氯化气冷凝下来的重馏分，深褐色，氯化有机物为主。
     *
     * <p>重馏分 → {@code .viscous()}（流速慢、不能喷雾），这点和同组其余四个料液不一样。
     * 有毒（氯化烃）但不算强酸，腐蚀只给 25。NFPA 可燃标 1 只是标识，
     * <b>没有给 FT_Flammable</b>，所以它烧不起来、也不进燃气火炬。</p>
     */
    public static final FluidDef TAILINGS_HEAVY_FRACTION = def("TAILINGS_HEAVY_FRACTION", 2054, 0x6B4414, "tailings_heavy_fraction")
            .liquid()
            .viscous()
            .temp(40)
            .hazard(2, 1, 0, EnumSymbol.NONE)         // 有毒、微可燃
            .corrosive(25)
            .polluting(new FT_Polluting().release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.5F))
            .canister(0x6B4414)
            .tankTexture("tank_TAILINGS_HEAVY_FRACTION");

    /**
     * 粗铂液：铂族精矿用王水/盐酸-氯气体系溶解后得到的含铂贵液，琥珀金。
     *
     * <p>这套溶解体系本身就是强酸强氧化，所以腐蚀给到 60（与 HBM 硝酸同级），
     * 泄漏时既算毒污染也算重金属污染 —— 溶着的铂、钯、铑都是重金属。</p>
     */
    public static final FluidDef CRUDE_PLATINUM = def("CRUDE_PLATINUM", 2055, 0xE0A840, "crude_platinum")
            .liquid()
            .temp(40)
            .hazard(3, 0, 2, EnumSymbol.ACID)         // 强酸 + 反应 2
            .corrosive(60)                            // 王水级，等同 HBM 硝酸
            .toxin(new FT_Toxin.ToxinDirectDamage(com.hbm.lib.ModDamageSource.cloud, 1.0F, 20, HazardClass.GAS_BLISTERING, false))
            .polluting(new FT_Polluting()
                    .release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.4F)
                    .release(PollutionType.HEAVYMETAL, com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.10F))
            .canister(0xE0A840)
            .tankTexture("tank_CRUDE_PLATINUM");

    /**
     * 稀土母液：混合稀土氢氧化物沉淀之后剩下的母液，浅琥珀，还溶着可回收的重稀土。
     *
     * <p>碱性盐溶液，本身不烈：腐蚀 15（弱碱）、健康标识 1。但母液里带着钍和重稀土，
     * 泄漏算重金属污染 —— 这也是现实里稀土厂最头疼的那一项。</p>
     */
    public static final FluidDef RE_MOTHER_LIQUOR = def("RE_MOTHER_LIQUOR", 2056, 0xDCAE80, "re_mother_liquor")
            .liquid()
            .temp(30)
            .hazard(1, 0, 0, EnumSymbol.NONE)
            .corrosive(15)
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.04F))
            .canister(0xDCAE80)
            .tankTexture("tank_RE_MOTHER_LIQUOR");

    /**
     * 钴料液：多金属混粉浸出、除杂之后的含钴溶液，玫瑰红；碱沉淀即得氢氧化钴。
     *
     * <p>钴盐是重金属毒，接触会眩晕恶心（GAS_BLISTERING 这个危险类别在 HBM 里的
     * 含义就是「腐蚀性物质，同时伤皮肤」，戴全套防护可挡）。腐蚀 30，与 HBM 溶剂同级。</p>
     */
    public static final FluidDef COBALT_LIQUOR = def("COBALT_LIQUOR", 2057, 0xC44468, "cobalt_liquor")
            .liquid()
            .temp(30)
            .hazard(3, 0, 0, EnumSymbol.NONE)         // 重金属毒，不可燃
            .corrosive(30)
            .toxin(new FT_Toxin.ToxinEffects(HazardClass.GAS_BLISTERING, false)
                    .add(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.confusion.id, 120, 0)))
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.08F))
            .canister(0xC44468)
            .tankTexture("tank_COBALT_LIQUOR");

    // =====================================================================================
    //  地核线：从地狱深处抽上来的熔核，一路熔融、分离成铁族与铂族两股
    // =====================================================================================

    /**
     * 高密度地核原浆：刚抽上来、还没熔开的核物质，暗褐紫，稠得几乎不流动。
     *
     * <p>它不是「热液体」而是「带压的核废浆」，所以四条都拉满：毒 4、反应 3（遇水即爆）、
     * 腐蚀 75（比王水还狠，HBM 里 50 以上就算强腐蚀）、放热辐射 0.25/mB。
     * 泄漏时按重金属 + 剧毒双算污染。</p>
     *
     * <p>容器门槛由 {@link FT_CoreTank} 标记 + {@code CoreFluidTankHook} 实现：<b>放置在地上的</b>
     * 储罐只认大型储罐、「巨尻」、重型反物质储罐与磁约束反物质容器，普通储罐会被溶到破裂、
     * 小桶直接溶穿。它<b>不是</b>反物质（不挂 FT_Amat），所以面板上没有「反物质」条目，
     * 也不会触发反物质爆炸。</p>
     *
     * <p><b>手持容器</b>另走一套：{@code leadContainer()} 只放行危险品罐（铅罐），
     * 普通流体罐 / 流体桶 / 32k 流体包全部挡掉 —— 铅罐本身就是为强腐蚀危险流体设计的，
     * 与「地上容器只认超大型」并不冲突：一个管搬运，一个管储存。</p>
     */
    public static final FluidDef CORE_SLURRY = def("CORE_SLURRY", 2060, 0x4A1A2E, "core_slurry")
            .liquid()
            .viscous()
            .temp(1200)
            .hazard(4, 0, 3, EnumSymbol.RADIATION)
            .corrosive(75)
            .ventRadiation(0.25F)
            .polluting(new FT_Polluting()
                    .release(PollutionType.HEAVYMETAL,
                            com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.20F)
                    .release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.5F))
            .trait(new FT_CoreTank())
            .leadContainer();

    /**
     * 熔融地核混合物：原浆熔开之后的铁镍核熔体，白热。
     *
     * <p>温度 2400（HBM 岩浆 1200、铅热 1500，这里取「地核」量级），反应 4 —— 熔体和
     * 水/冷却液直接接触就是蒸汽爆炸。腐蚀降到 60：熔体对容器是「溶」而不是「酸蚀」。
     * 容器门槛同原浆：{@link FT_CoreTank} + {@code CoreFluidTankHook} 管地上储罐，
     * {@code leadContainer()} 管手持容器（只放行危险品罐），不是反物质。</p>
     */
    public static final FluidDef CORE_MELT = def("CORE_MELT", 2061, 0xFF8C24, "core_melt")
            .liquid()
            .temp(2400)
            .hazard(4, 0, 4, EnumSymbol.RADIATION)
            .corrosive(60)
            .ventRadiation(0.35F)
            .polluting(new FT_Polluting()
                    .release(PollutionType.HEAVYMETAL,
                            com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.30F)
                    .release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.6F))
            .trait(new FT_CoreTank())
            .leadContainer();

    /**
     * 重核渣浆：熔融地核混合物在大型离心机里甩出来的渣相 —— 甩不下去的难熔相、
     * 铂族重砂与锕系核素全在这一股里，暗橄榄褐、近黑、带砂。
     *
     * <p>属性照原浆（{@code CORE_SLURRY}）改三处，逻辑都是「同一锅料，更浓缩的那一半」：</p>
     * <ul>
     *   <li>温度 2200 —— 刚从 2400 的熔体里分出来，还没凉；</li>
     *   <li>辐射 0.30（原浆 0.25）—— 锕系核素在渣里富集，比原浆更烫手；</li>
     *   <li>腐蚀 65（原浆 75）—— 渣相是「磨」不是「酸蚀」，但仍是强腐蚀档（HBM 50 以上算强）。</li>
     * </ul>
     *
     * <p>毒 4 / 反应 3 / 重金属 0.25 / 剧毒 0.55 与原浆同档略高。容器门槛完全一致：
     * {@link FT_CoreTank} + {@code CoreFluidTankHook} 管地上储罐（只认大型储罐、「巨尻」、
     * 重型反物质储罐与反物质容器），{@code leadContainer()} 管手持容器，**不配储罐贴图**。</p>
     */
    public static final FluidDef HEAVY_SLAG = def("HEAVY_SLAG", 2064, 0x453C28, "heavy_slag")
            .liquid()
            .viscous()
            .temp(2200)
            .hazard(4, 0, 3, EnumSymbol.RADIATION)
            .corrosive(65)
            .ventRadiation(0.30F)
            .polluting(new FT_Polluting()
                    .release(PollutionType.HEAVYMETAL,
                            com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.25F)
                    .release(PollutionType.POISON, Fluids.POISON_EXTREME * 0.55F))
            .trait(new FT_CoreTank())
            .leadContainer();

    /**
     * 铁系熔融混合物：地核熔体里分出来的铁族（铁/钴/镍）那一股，钢蓝，冷下来就是铁锭。
     *
     * <p>1550℃ 恰好是铁的熔点量级，所以只标 NOWATER（碰水炸），不算腐蚀也不算毒；
     * 泄漏按重金属轻污染。和 HBM 的热流体（LEAD_HOT / SODIUM_HOT）一样不给铁罐，
     * 只能用储罐 + 管道搬 —— 1550 度的东西装铁罐本来就不合理。</p>
     */
    public static final FluidDef FERRO_MELT = def("FERRO_MELT", 2062, 0xA8B2BE, "ferro_melt")
            .liquid()
            .temp(1550)
            .hazard(1, 0, 0, EnumSymbol.NOWATER)
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.10F))
            .tankTexture("tank_FERRO_MELT");

    /**
     * 铂系熔融混合物：同一锅熔体里的铂族（铂/锇/铱/钌/铑）那一股，淡银紫、带金色结晶。
     *
     * <p>1800℃（铂熔点 1768℃），比铁系更高，所以保持 NOWATER。铂族蒸气是重金属毒，
     * 健康标识给 2、泄漏重金属污染比铁系重一档；本身不腐蚀、不可燃、同样不给铁罐。</p>
     */
    public static final FluidDef PGM_MELT = def("PGM_MELT", 2063, 0xD9D2E4, "pgm_melt")
            .liquid()
            .temp(1800)
            .hazard(2, 0, 0, EnumSymbol.NOWATER)
            .polluting(new FT_Polluting().release(PollutionType.HEAVYMETAL,
                    com.hbm.handler.pollution.PollutionHandler.HEAVY_METAL_PER_SECOND * 0.18F))
            .tankTexture("tank_PGM_MELT");

    // =====================================================================================
    //  木浆制油线 · id 2065 ~ 2066（三段链规格见 BioOilChainSpec）

    /** 生物原油：木浆解聚后的含氧粗油，深棕黏稠。加氢脱氧后进粗合成油。LOW 级可燃，堵线时可当低级燃料。 */
    public static final FluidDef BIO_CRUDE = def("BIO_CRUDE", 2065, 0x4A3520, "bio_crude")
            .liquid()
            .viscous()
            .temp(20)
            .hazard(0, 2, 0, EnumSymbol.NONE)
            .fuel(30_000L, FuelGrade.LOW, 2.0D)   // 3万 TU / 6万 HE 每桶
            .canister(0x4A3520)
            .tankTexture("tank_BIO_CRUDE");

    /** 粗合成油：生物原油加氢脱氧后的烃类混合物，琥珀色。异构重整后得原油。LOW 级可燃。 */
    public static final FluidDef RAW_SYNTH_OIL = def("RAW_SYNTH_OIL", 2066, 0x8A6A2F, "raw_synth_oil")
            .liquid()
            .temp(20)
            .hazard(0, 2, 0, EnumSymbol.NONE)
            .fuel(60_000L, FuelGrade.LOW, 2.0D)   // 6万 TU / 12万 HE 每桶
            .canister(0x8A6A2F)
            .tankTexture("tank_RAW_SYNTH_OIL");

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
     * 回调收尾会把 foreignFluids 统一塞进 metaOrder，期间谁再插手就会条目重复，
     * 装配机的装罐配方撞 duplicate ID 直接崩档。
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
        HBMCompat.ensureTitaniumTetrachloride();   // 旧版 HBM 没有四氯化钛，同点补注册
        Fluids.additionalListeners.add(new IFluidRegisterListener() {
            @Override
            public void onFluidsLoad() {
                // 只会在 Fluids.reloadFluids() 时触发，收尾由它把 foreignFluids 加进 metaOrder，
                // 所以这里不能再入列，否则每个流体会出现两次。
                duringReload = true;
                try {
                    registerAll(true);
                    HBMCompat.ensureHCL();   // /ntmreload 重载流体后再次补注册
                    HBMCompat.ensureTitaniumTetrachloride();   // 重载会清空 foreignFluids，四氯化钛同样要补
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
        addLeadContainerRecipes();
    }

    /** 已经补过危险品罐容器的流体 id，避免重载时重复注册。 */
    private static final Set<Integer> LEAD_CON_DONE = new HashSet<Integer>();

    /**
     * 补「危险品罐」容器映射（只给带 {@code leadContainer()} 的附属流体）。
     *
     * <p>HBM 在 {@code MainRegistry} 里一次性遍历 {@code Fluids.getAll()} 生成整张容器表，
     * 而附属流体注册在其 preInit 之后 —— 那一轮遍历里还没有我们的 id，
     * 不补的话 {@code getFullContainer} 永远返回 null，危险品罐装不进也倒不出。
     * 这里照 {@code addConduitRecipes} 的同一套做法补上，LEAD_CON_DONE 防重。</p>
     *
     * <p>只注册危险品罐（铅罐）这一种：普通流体罐 / 流体桶 / 32k 流体包都不给，
     * 与 {@code leadContainer()} 的语义一致 —— 强腐蚀流体只准铅罐搬运。</p>
     */
    private static void addLeadContainerRecipes() {
        for (FluidDef def : ALL) {
            FluidType type = def.type;
            if (type == null || type == Fluids.NONE) continue;
            if (!type.needsLeadContainer()) continue;
            int id = type.getID();
            if (id <= 0 || type.hasNoID()) continue;
            if (!LEAD_CON_DONE.add(id)) continue;

            com.hbm.inventory.FluidContainerRegistry.registerContainer(
                    new com.hbm.inventory.FluidContainer(
                            new ItemStack(ModItems.fluid_tank_lead_full, 1, id),
                            new ItemStack(ModItems.fluid_tank_lead_empty), type, 1000));
        }
    }

    /** 已经补过管道配方的流体 id，避免重载时重复注册。 */
    private static final Set<Integer> CONDUIT_DONE = new HashSet<Integer>();

    /**
     * 补流体识别码 / 流体管道配方。
     *
     * <p>HBM 在它自己的 preInit 里按 {@code Fluids.getAll().length} 生成这批配方，而附属的
     * preInit 排在其后，那时数组里还没有我们的 id —— 不补就做不出带自家流体染色的管道，
     * NEI 和机器里也「找不到」这些流体。CONDUIT_DONE 防重。</p>
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

        // 盐水 --锅炉(300 TU/1mB)--> 热盐水，1:1 转换。
        SALT_WATER.type.addTraits(new FT_Heatable()
                .setEff(FT_Heatable.HeatingType.BOILER, 1.0D)
                .addStep(300, 1, HOT_SALT_WATER.type, 1));

        // 热盐水 --热交换(放热 300 TU/1mB)--> 盐水，1:1 等量回冷。
        // 热盐水与盐水同物质冷热回路按 1:1 转换；热量参数与上面的加热步骤一致。
        HOT_SALT_WATER.type.addTraits(new FT_Coolable(SALT_WATER.type, 1, 1, 300)
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

        /**
         * 腐蚀性：量程 <b>0~100</b>（不是 0~3）。影响喷雾/化学弹伤害与容器限制，
         * {@code >50} 才算「强腐蚀」；填 1、2 基本等于没有腐蚀。
         *
         * <p>HBM 本体参考值：溶剂 30、硫酸 50、硝酸 60、WATZ 60、最高 80。
         * 本模组早期几处写成 1/2 的（含镉浸出液、硫酸锌、亚硫酸钠等）其实是按 0~3
         * 的误解填的，效果约等于无腐蚀 —— 要真起作用得改成 20~60 这个量级。</p>
         */
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

            // 幂等判据不能只看 type != null：reloadFluids 会先把我们踢出注册表再回调，
            // 那时 type 还在但表里已经没有，必须确认它此刻真的能在表里查到。
            if(this.type != null && Fluids.fromName(this.name) == this.type) return;

            // 走到这里且 type 非 null，说明是被 reloadFluids 清掉后的重登记
            boolean reregister = this.type != null;

            // 同名流体已被别的模组（典型是航天版的 AMMONIA）注册时直接复用，绝不覆盖：
            // Fluids.register 内部是 nameMapping.put，后注册者会静默顶掉先注册者，等于毁对方的档。
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

            // 按本地化名查重而不是对象身份：宿主里 METHANOL 存在两个不同的 FluidType 对象，
            // 只比身份会漏掉，第二个进 metaOrder 后装罐配方重名，HBM PostLoad 直接崩游戏。
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

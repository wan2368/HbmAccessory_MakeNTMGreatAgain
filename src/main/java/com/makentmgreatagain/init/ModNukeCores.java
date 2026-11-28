package com.makentmgreatagain.init;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.HazardTypeBase;
import com.hbm.inventory.FluidStack;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.RecipesCommon.OreDictStack;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.items.ItemEdibleCore;
import com.makentmgreatagain.items.ItemWithDesc;
import com.makentmgreatagain.recipes.RecipeRegistrar;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import static com.hbm.inventory.OreDictManager.BE;
import static com.hbm.inventory.OreDictManager.PB;

/**
 * 核装药核心：仿 HBM 的 {@code gadget_core}（钚核心）做出来的同型装药球。
 *
 * <h3>贴图</h3>
 * <p>形状与明暗完全取自 {@code gadget_core}：16×16 金属球、7 级色阶、右侧轮廓收边。
 * 只把色阶换成对应原料的配色，左下角的角标（洋红 {@code F} = 燃料、{@code I} = 同位素）
 * 从原料贴图（粒 / 粉）上原样搬过来——所以球体形状和原版 100% 一致，
 * 各核心之间只有颜色不同。配方用锭不影响贴图：取色用的是粒 / 粉，只有它们带角标。</p>
 *
 * <h3>配方（装配机）</h3>
 * <ul>
 *   <li>燃料 / 纯核素：<b>1 × 对应锭 + 1 × 铍锭</b>（用户定稿 1:1，原 7:3）；</li>
 *   <li>裂变产物：<b>1 × 对应粉 + 1 × 铅板</b>（没有临界价值，用铅做屏蔽层）；</li>
 *   <li>自定义：氘化锂（4000 mB 氘 + 2 锂锭）、气凝氦（1 氦气凝锭 + 1 铍锭）、
 *       野火（1 钋锭 + 2 野火蛋碎片）；曼巴核心没有配方，只能靠创造模式或 /give 拿。</li>
 * </ul>
 *
 * <p>配方必须在 {@code injectAllFallback()} 的 {@code step("nukeCores", ...)} 里注册：
 * HBM 的装配机表在 init 之后会按 JSON 重建，init 阶段塞进去的会被冲掉。</p>
 *
 * <h3>危险数据</h3>
 * <p>不另编数值，直接<b>按配方原料累加</b>：数量是乘数，辐射 / 灼热 / 致盲分别求和，
 * 运行时向 {@link HazardSystem} 取值。所以改配方就等于改危险，不用维护第二张表。
 * 「与水反应」是例外——那个 level 是爆炸强度而不是剂量，只取单件最大值，
 * 另加一张手工表补给碱金属 / 碱土金属的那几个核心。</p>
 */
public final class ModNukeCores {

	/** 燃料类（3）：1 × 锭 + 1 × 铍锭。 */
	private static final String[] FUEL = {
		"uranium_fuel", "thorium_fuel", "plutonium_fuel"
	};

	/** 裂变产物（4）：1 × 粉 + 1 × 铅板。 */
	private static final String[] FISSION = {
		"sr90", "cs137", "i131", "co60"
	};

	/**
	 * 纯核素（9）：1 × 锭 + 1 × 铍锭。
	 *
	 * <p>{@code australium_greater}（富集奥斯）在 HBM 里没有锭，只有粒和坯料，
	 * 主料取坯料 {@code billet_australium_greater}（1 坯料 = 6 粒），数量不变。</p>
	 */
	private static final String[] NUCLIDE = {
		"u233", "u235", "pu239", "pu241", "am241", "am242",
		"schrabidium", "sa327", "australium_greater"
	};

	/** id → 已注册的核心物品。 */
	private static final Map<String, Item> CORES = new LinkedHashMap<String, Item>();
	/** id → 贴图源（带角标的粒 / 粉，只用于取色，不参与配方）。 */
	private static final Map<String, Item> SOURCES = new LinkedHashMap<String, Item>();
	/** id → 配方主料（锭）。裂变产物组不在这里，直接用 {@link #SOURCES} 的粉。 */
	private static final Map<String, Item> MAIN = new LinkedHashMap<String, Item>();

	/** 自定义核心：不属于任何核素组，配方单独给（见 {@link #bindCustom()}）。 */
	private static final Map<String, Custom> CUSTOM = new LinkedHashMap<String, Custom>();

	/** 一个自定义核心的输入与工时；{@code duration <= 0} 表示没有配方。 */
	private static final class Custom {
		final ItemStack[] items;
		final FluidStack[] fluids;
		final int duration;
		final int power;

		Custom(int duration, int power, ItemStack[] items, FluidStack[] fluids) {
			this.duration = duration;
			this.power = power;
			this.items = items;
			this.fluids = fluids;
		}
	}

	/** 配方里主原料的个数（用户定稿 1:1，原 7）。 */
	private static final int MAIN_COUNT = com.makentmgreatagain.recipes.NukeCoreBalanceSpec.MAIN_COUNT;
	/** 配方里辅料的个数（铍锭 / 铅板，用户定稿 1:1，原 3）。 */
	private static final int AUX_COUNT = com.makentmgreatagain.recipes.NukeCoreBalanceSpec.AUX_COUNT;

	/** id → 食用设定。表里没有的核心就是普通物品。 */
	private static final Map<String, Meal> MEALS = new LinkedHashMap<String, Meal>();

	/** 一条食用设定：加多少饥饿、给哪些效果、有没有代价。 */
	private static final class Meal {
		int heal;
		float saturation;
		/** 吃完自伤（点）。 */
		float damage;
		/** 吃完自燃秒数。 */
		int fire;
		/** 吃完点燃周围生物的半径，0 = 不点。 */
		int burn;
		String particle = "happyVillager";
		boolean edible = true;

		/** 一次性剂量（RAD）。 */
		float radiation;
		/** 污染脚下区块的量。 */
		float chunkRad;
		/** 爆炸强度，0 = 不炸。 */
		float explosion;
		boolean explosionFlame;
		/** 向上弹射速度。 */
		double launchSpeed;

		final List<PotionEffect> main = new ArrayList<PotionEffect>();
		List<PotionEffect> alternative;

		Meal hurt(float amount) {
			this.damage = amount;
			return this;
		}

		Meal burn(int seconds) {
			this.fire = seconds;
			return this;
		}

		Meal areaFire(int radius) {
			this.burn = radius;
			return this;
		}

		Meal particles(String name) {
			this.particle = name;
			return this;
		}

		Meal rad(float amount) {
			this.radiation = amount;
			return this;
		}

		Meal pollute(float amount) {
			this.chunkRad = amount;
			return this;
		}

		Meal boom(float strength, boolean flaming) {
			this.explosion = strength;
			this.explosionFlame = flaming;
			return this;
		}

		Meal launch(double speed) {
			this.launchSpeed = speed;
			return this;
		}

		/** 赌一把：吃完有概率改吃下面这一组效果。 */
		Meal gamble(PotionEffect... fallback) {
			this.alternative = new ArrayList<PotionEffect>();
			for(PotionEffect effect : fallback) this.alternative.add(effect);
			return this;
		}

		ItemEdibleCore build() {
			ItemEdibleCore item = new ItemEdibleCore(this.heal, this.saturation);
			item.setRarity(EnumRarity.uncommon);
			item.setEdible(this.edible);
			item.setSelfDamage(this.damage);
			item.setFireSeconds(this.fire);
			item.setBurnRadius(this.burn);
			item.setParticle(this.particle);
			item.setRadiation(this.radiation);
			item.setChunkRadiation(this.chunkRad);
			item.setLaunch(this.launchSpeed);
			if(this.explosion > 0F) item.setExplosion(this.explosion, this.explosionFlame);

			for(PotionEffect effect : this.main) item.addEffect(effect);
			if(this.alternative != null) {
				item.setAlternative(this.alternative.toArray(new PotionEffect[this.alternative.size()]));
			}
			return item;
		}
	}

	private static boolean registered;

	private ModNukeCores() { }

	/** 取某个核心，未注册时返回 null。 */
	public static Item get(String id) {
		return CORES.get(id);
	}

	/** 这个物品栈是不是本模组注册的核装药核心（核心槽的准入判定）。 */
	public static boolean isCore(ItemStack stack) {
		return stack != null && CORES.containsValue(stack.getItem());
	}

	/** 返回核心物品对应的注册 id（如 "u235"、"schrabidium"），非核心返回 ""。 */
	public static String getCoreId(ItemStack stack) {
		if(stack == null || stack.getItem() == null) return "";
		Item target = stack.getItem();
		for(Map.Entry<String, Item> entry : CORES.entrySet()) {
			if(entry.getValue() == target) return entry.getKey();
		}
		return "";
	}

	/* ------------------------------------------------------------------ 注册 */

	/** 注册入口：物品。危险数据与配方都在 postInit 阶段补（见 {@link #applyHazards()}）。 */
	public static void register() {
		if(registered) return;
		registered = true;

		bindSources();
		bindCustom();
		bindMeals();

		for(String id : FUEL)    registerCore(id);
		for(String id : FISSION) registerCore(id);
		for(String id : NUCLIDE) registerCore(id);
		for(String id : CUSTOM.keySet()) registerCore(id);

		System.out.println("[MakeNTMGreatAgain] Nuke cores registered (" + CORES.size() + " items).");
	}

	/**
	 * 显式绑定 HBM 物品。
	 *
	 * <p>HBM 没有按名字取物品的公开入口，只能逐个字段引用；写死在方法里比反射稳，
	 * 也方便编译期就发现字段改名。</p>
	 *
	 * <p>两个映射用途不同：{@link #SOURCES} 是贴图取色来源（粒 / 粉，只有它们带角标），
	 * {@link #MAIN} 是配方主料（锭）。分开维护，改配方不会连带改掉贴图。</p>
	 */
	private static void bindSources() {
		// 燃料（角标 F）
		SOURCES.put("uranium_fuel",     com.hbm.items.ModItems.nugget_uranium_fuel);
		MAIN.put("uranium_fuel",        com.hbm.items.ModItems.ingot_uranium_fuel);

		SOURCES.put("thorium_fuel",     com.hbm.items.ModItems.nugget_thorium_fuel);
		MAIN.put("thorium_fuel",        com.hbm.items.ModItems.ingot_thorium_fuel);

		SOURCES.put("plutonium_fuel",   com.hbm.items.ModItems.nugget_plutonium_fuel);
		MAIN.put("plutonium_fuel",      com.hbm.items.ModItems.ingot_plutonium_fuel);

		// 裂变产物（角标 I）：主料就是粉，不进 MAIN
		SOURCES.put("sr90",       com.hbm.items.ModItems.powder_sr90);
		SOURCES.put("cs137",      com.hbm.items.ModItems.powder_cs137);
		SOURCES.put("i131",       com.hbm.items.ModItems.powder_i131);
		SOURCES.put("co60",       com.hbm.items.ModItems.powder_co60);

		// 纯核素（角标按辐射等级着色）
		SOURCES.put("u233",        com.hbm.items.ModItems.nugget_u233);
		MAIN.put("u233",           com.hbm.items.ModItems.ingot_u233);

		SOURCES.put("u235",        com.hbm.items.ModItems.nugget_u235);
		MAIN.put("u235",           com.hbm.items.ModItems.ingot_u235);

		SOURCES.put("pu239",       com.hbm.items.ModItems.nugget_pu239);
		MAIN.put("pu239",          com.hbm.items.ModItems.ingot_pu239);

		SOURCES.put("pu241",       com.hbm.items.ModItems.nugget_pu241);
		MAIN.put("pu241",          com.hbm.items.ModItems.ingot_pu241);

		SOURCES.put("am241",       com.hbm.items.ModItems.nugget_am241);
		MAIN.put("am241",          com.hbm.items.ModItems.ingot_am241);

		SOURCES.put("am242",       com.hbm.items.ModItems.nugget_am242);
		MAIN.put("am242",          com.hbm.items.ModItems.ingot_am242);

		SOURCES.put("schrabidium", com.hbm.items.ModItems.nugget_schrabidium);
		MAIN.put("schrabidium",    com.hbm.items.ModItems.ingot_schrabidium);

		// Sa327 = Solinium
		SOURCES.put("sa327",       com.hbm.items.ModItems.nugget_solinium);
		MAIN.put("sa327",          com.hbm.items.ModItems.ingot_solinium);

		// 富集奥斯：HBM 里只有粒和坯料，没有锭
		SOURCES.put("australium_greater", com.hbm.items.ModItems.nugget_australium_greater);
		MAIN.put("australium_greater",    com.hbm.items.ModItems.billet_australium_greater);
	}

	/**
	 * 四个自定义核心的输入清单。
	 *
	 * <ul>
	 *   <li><b>氘化锂核心</b>：4000 mB 氘 + 2 锂锭。装配机本身支持流体输入（原版
	 *       {@code mike_deut} 就是钢板 + 10000 mB 氘），所以不必换机器。</li>
	 *   <li><b>气凝氦核心</b>：1 氦气凝锭 + 1 铍锭。</li>
	 *   <li><b>野火核心</b>：1 钋锭 + 2 野火蛋碎片，工期取 600（与 {@code fleija_core} 同档）。</li>
	 *   <li><b>曼巴核心</b>：没有配方、没有危险、不进创造栏。{@code duration = 0}
	 *       就是「无配方」的标记，{@link #addRecipes()} 会跳过它。</li>
	 * </ul>
	 */
	private static void bindCustom() {
		CUSTOM.put("lithium_deuteride", new Custom(1_200, 100,
			new ItemStack[] { new ItemStack(com.hbm.items.ModItems.lithium, 2) },
			new FluidStack[] { new FluidStack(Fluids.DEUTERIUM, 4_000) }));

		CUSTOM.put("helium", new Custom(1_200, 100,
			new ItemStack[] {
				new ItemStack(ModItems.ingot_helium, MAIN_COUNT),
				new ItemStack(com.hbm.items.ModItems.ingot_beryllium, AUX_COUNT)
			},
			new FluidStack[0]));

		CUSTOM.put("balefire", new Custom(600, 100,
			new ItemStack[] {
				new ItemStack(com.hbm.items.ModItems.ingot_polonium, 1),
				new ItemStack(com.hbm.items.ModItems.egg_balefire_shard, 2)
			},
			new FluidStack[0]));

		CUSTOM.put("mamba", new Custom(0, 0, new ItemStack[0], new FluidStack[0]));
	}

	/* ------------------------------------------------------------------ 食用设定 */

	/**
	 * 只有 10 个核心能吃——吃核装药球本来就该是件出事故的事，不是加 buff。
	 *
	 * <p>全部回 4~6 点饱食度，代价分三类：纯剂量（钍 / 铀 / 钚燃料）、
	 * 临界事故（铀-235 / 钚-239，嚼碎相当于把慢化剂和裂变材料拌匀，当场炸一个小坑）、
	 * 污染环境（锶-90 / 铯-137 / 钴-60，把脚下区块也污染掉）。</p>
	 *
	 * <p>野火（爆炸 + 点燃周围五格 + 自燃）和气凝氦（把你顶上天）不辐射但更离谱。
	 * 剂量走 {@code HbmLivingProps.incrementRadiation}（上限 2500），区块污染走
	 * {@code ChunkRadiationManager.proxy.incrementRad}；爆炸强度 2 大约是「脚下炸个坑、
	 * 人重伤但能活」，TNT 是 4 作参照。</p>
	 */
	private static void bindMeals() {
		// ---------------------------------------------------------- 燃料：能吃，但要结账
		MEALS.put("thorium_fuel", meal(6, 0.80F,
			fx(Potion.regeneration, 15, 0), fx(Potion.resistance, 30, 0))
			.rad(80F));

		MEALS.put("uranium_fuel", meal(5, 0.70F,
			fx(Potion.damageBoost, 30, 0), fx(Potion.poison, 10, 0))
			.rad(150F));

		MEALS.put("plutonium_fuel", meal(5, 0.60F,
			fx(Potion.damageBoost, 30, 1), fx(Potion.confusion, 20, 0))
			.rad(350F));

		// 嚼碎 = 把慢化剂（你的口水）和裂变材料混到一起，这就是一次临界事故
		MEALS.put("u235", meal(4, 0.60F,
			fx(Potion.blindness, 10, 0), fx(Potion.confusion, 20, 0))
			.rad(700F).boom(2.0F, false).hurt(2F));

		// 武器级钚：内爆透镜不用设计，胃替你算好了
		MEALS.put("pu239", meal(4, 0.60F,
			fx(Potion.blindness, 15, 0), fx(Potion.poison, 20, 1))
			.rad(500F).boom(2.5F, false).hurt(4F));

		// ---------------------------------------------------------- 裂变产物：吃完你就是污染源
		// 污染量对齐 HBM 自己的量级：小型核爆中心那格是 50，辐射兽是 100
		MEALS.put("sr90", meal(4, 0.60F,
			fx(Potion.resistance, 60, 1))
			.rad(200F).pollute(25F));

		MEALS.put("cs137", meal(4, 0.60F,
			fx(Potion.moveSpeed, 30, 1))
			.rad(300F).pollute(50F));

		MEALS.put("co60", meal(4, 0.60F,
			fx(Potion.blindness, 10, 0))
			.rad(400F).pollute(80F));

		// ---------------------------------------------------------- 自定义：不辐射，但更离谱
		// 野火：胃里开篝火，脚下炸个坑，周围五格一起点着
		MEALS.put("balefire", meal(6, 0.80F,
			fx(Potion.fireResistance, 120, 0), fx(Potion.damageBoost, 30, 1))
			.boom(3.0F, true).areaFire(5).hurt(4F).particles("flame"));

		// 气凝氦：加压气凝胶在胃里膨胀，把你顶上天
		MEALS.put("helium", meal(4, 0.60F,
			fx(Potion.jump, 60, 2), fx(Potion.confusion, 10, 0))
			.launch(1.6D).particles("cloud"));
	}

	/**
	 * 一条食用设定。{@code heal} 是饥饿值点数（1 点 = 半格），
	 * 所有可食用核心都给到 4 以上，回复效果能吃出来。
	 */
	private static Meal meal(int heal, float saturation, PotionEffect... effects) {
		Meal meal = new Meal();
		meal.heal = heal;
		meal.saturation = saturation;
		for(PotionEffect effect : effects) meal.main.add(effect);
		return meal;
	}

	private static PotionEffect fx(Potion potion, int seconds, int amplifier) {
		return new PotionEffect(potion.id, seconds * 20, amplifier);
	}

	/* ------------------------------------------------------------------ 危险数据 */

	/**
	 * 「遇水反应」特殊词条：原料本身是碱金属 / 碱土金属的那些核心。
	 *
	 * <p>HBM 只给 {@code powder_caesium} 与 {@code crystal_lithium} 注册了
	 * {@link HazardRegistry#HYDROACTIVE}，锂锭、铯-137、锶-90 这些反而没有。
	 * 这里按化学性质手动补，数值就是<b>爆炸强度</b>（TNT 记 4）——故意压得比 TNT 小：
	 * 代价是「核心没了 + 挨一发近距离爆炸」，不至于把家平了。</p>
	 */
	private static final Map<String, Float> HYDRO = new LinkedHashMap<String, Float>();

	static {
		HYDRO.put("lithium_deuteride", 2F); // 锂遇水放氢，氘化锂同理
		HYDRO.put("cs137",             2F); // 铯，碱金属里最活泼的几个之一
		HYDRO.put("sr90",              1F); // 锶，碱土金属，缓慢放氢
	}

	/** 注册危险数据：燃料 / 纯核素 / 裂变产物 / 自定义。 */
	public static void applyHazards() {
		for(String id : FUEL)    applyHazard(id, MAIN.get(id), com.hbm.items.ModItems.ingot_beryllium);
		for(String id : FISSION) applyHazard(id, SOURCES.get(id), com.hbm.items.ModItems.plate_lead);
		for(String id : NUCLIDE) applyHazard(id, MAIN.get(id), com.hbm.items.ModItems.ingot_beryllium);

		for(Map.Entry<String, Custom> entry : CUSTOM.entrySet()) {
			applyHazard(entry.getKey(), entry.getValue().items);
		}
	}

	/** 标准组：1 × 主料 + 1 × 辅料。 */
	private static void applyHazard(String id, Item main, Item aux) {
		if(main == null) return;

		applyHazard(id, new ItemStack[] {
			new ItemStack(main, MAIN_COUNT),
			new ItemStack(aux, AUX_COUNT)
		});
	}

	/**
	 * 按输入清单累加。
	 *
	 * <p>注意 {@code level()} 拿的是「单件」数值，{@code HazardTypeRadiation} 才会乘堆叠数，
	 * 所以这里必须自己乘 {@code stackSize}，否则 7 份和 1 份算出来一样。</p>
	 */
	private static void applyHazard(String id, ItemStack[] inputs) {
		Item core = CORES.get(id);
		if(core == null) return;

		float rad = 0F, hot = 0F, blind = 0F, hydro = 0F;

		// 手填的化学活性垫底，原料自带的再盖上去（取大值：hydro 的 level 是爆炸强度，不是剂量）
		if(HYDRO.containsKey(id)) hydro = HYDRO.get(id);

		for(ItemStack stack : inputs) {
			if(stack == null || stack.getItem() == null || stack.stackSize <= 0) continue;

			rad   += stack.stackSize * level(stack, HazardRegistry.RADIATION);
			hot   += stack.stackSize * level(stack, HazardRegistry.HOT);
			blind += stack.stackSize * level(stack, HazardRegistry.BLINDING);
			hydro = Math.max(hydro, level(stack, HazardRegistry.HYDROACTIVE));
		}

		// 完全惰性的核心（气凝氦、曼巴）不注册空数据，免得 tooltip 里留一条空行
		if(rad <= 0F && hot <= 0F && blind <= 0F && hydro <= 0F) return;

		HazardData data = new HazardData();
		if(rad > 0F)   data.addEntry(HazardRegistry.RADIATION, rad);
		if(hot > 0F)   data.addEntry(HazardRegistry.HOT, hot);
		if(blind > 0F) data.addEntry(HazardRegistry.BLINDING, blind);
		if(hydro > 0F) data.addEntry(HazardRegistry.HYDROACTIVE, hydro);

		HazardSystem.register(core, data);
	}

	/** 单项危险的数值，没有这一项时返回 0。 */
	private static float level(ItemStack stack, HazardTypeBase type) {
		return HazardSystem.getHazardLevelFromStack(stack, type);
	}

	/**
	 * 单个核心：有食用设定的走 {@link ItemEdibleCore}，否则退回普通 {@link ItemWithDesc}。
	 * 两者都是不可堆叠 + {@code uncommon}，与 {@code gadget_core} 一致。
	 *
	 * <p>曼巴核心是隐藏物品：不设创造栏，只能靠 /give 拿到。</p>
	 */
	private static void registerCore(String id) {
		Meal meal = MEALS.get(id);
		Item item;

		if(meal != null) {
			item = meal.build();
		} else {
			ItemWithDesc plain = new ItemWithDesc();
			plain.setRarity(EnumRarity.uncommon);
			plain.setMaxStackSize(1);
			item = plain;
		}

		item.setUnlocalizedName("core_" + id);
		item.setTextureName("makentmgreatagain:core_" + id);

		if(!"mamba".equals(id)) {
			item.setCreativeTab(ModCreativeTabs.TAB_NUCLEAR);
		}

		GameRegistry.registerItem(item, "core_" + id);
		CORES.put(id, item);
	}

	/* ------------------------------------------------------------------ 配方 */

	/** 装配机配方：燃料/纯核素 1 锭 + 1 铍锭、裂变产物 1 粉 + 1 铅板。由 injectAllFallback 调用。 */
	public static void addRecipes() {
		for(String id : FUEL)    addStandardRecipe(id, MAIN.get(id), BE.ingot());
		for(String id : NUCLIDE) addStandardRecipe(id, MAIN.get(id), BE.ingot());
		for(String id : FISSION) addStandardRecipe(id, SOURCES.get(id), PB.plate());

		// 自定义组：输入清单直接从 CUSTOM 读，和危险累加共用同一份数据；duration = 0 表示无配方
		for(Map.Entry<String, Custom> entry : CUSTOM.entrySet()) {
			Custom custom = entry.getValue();
			if(custom.duration <= 0) continue;

			AStack[] items = new AStack[custom.items.length];
			for(int i = 0; i < custom.items.length; i++) {
				ItemStack stack = custom.items[i];
				items[i] = new ComparableStack(stack.getItem(), stack.stackSize);
			}

			GenericRecipe recipe = new GenericRecipe("makentmgreatagain.core." + entry.getKey())
				.setup(custom.duration, custom.power)
				.outputItems(new ItemStack(CORES.get(entry.getKey())))
				.inputItems(items);

			if(custom.fluids.length > 0) recipe.inputFluids(custom.fluids);

			RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
		}
	}

	/** 1 × 主料 + 1 × 辅料，工期 1,200 tick / 100 TU，与原版核心一致。 */
	private static void addStandardRecipe(String id, Item main, String auxOre) {
		if(main == null) return;

		GenericRecipe recipe = new GenericRecipe("makentmgreatagain.core." + id)
			.setup(1_200, 100)
			.outputItems(new ItemStack(CORES.get(id)))
			.inputItems(new ComparableStack(main, MAIN_COUNT), new OreDictStack(auxOre, AUX_COUNT));

		RecipeRegistrar.add(AssemblyMachineRecipes.INSTANCE, recipe);
	}
}

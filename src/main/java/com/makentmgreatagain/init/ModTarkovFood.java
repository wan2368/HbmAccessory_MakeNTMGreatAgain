package com.makentmgreatagain.init;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hbm.itempool.ItemPool;
import com.hbm.itempool.ItemPoolsPile;
import com.hbm.itempool.ItemPoolsSingle;
import com.hbm.lib.HbmChestContents;
import com.makentmgreatagain.ModCreativeTabs;
import com.makentmgreatagain.items.ItemTarkovFood;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

/**
 * 塔可夫食物与杂项：17 件罐头 / 巧克力 / 糖 / 蛋黄酱 / 酒 / 面包。
 *
 * <h3>贴图</h3>
 * <p>全部 16×16，<b>骨架直接取自 HBM 原版食物</b>（{@code canned_beef} / {@code canned_tuna} /
 * {@code canned_pashtet} / {@code canned_milk} / {@code chocolate} / {@code bottle_nuka}），
 * 只替换调色板与中央那几行图案。所以圆角、顶盖高光点、底部卷边与原版逐像素一致，
 * 不存在「自己排坐标导致罐子像纸盒」的问题。</p>
 *
 * <h3>可合成与战利品</h3>
 * <p>17 件里 9 件可合成（工作台无序配方），8 件纯补给品。战利品只进
 * {@link ItemPoolsPile#POOL_PILE_SUPPLIES}（{@code LootGenerator.LOOT_SUPPLIES} 摆出的物资堆）：
 * 8 件不可合成的全部进箱，可合成的按 50% 比例抽 4 件（啤酒 / 威士忌 / 士腻架 / 面包块）——
 * 总计 12 件可开出，与「半数合成、半数只能捡」的定位一致。</p>
 *
 * <h3>负面效果口径</h3>
 * <p>一律不给反胃（{@code confusion}）。醉酒走<b>缓慢 I + 挖掘疲劳 I，10~15 秒</b>；
 * 高脂食品（蛋黄酱）只有 10 秒缓慢。所有负面都控制在十几秒，不叠加成长时间惩罚。</p>
 *
 * <h3>份量与时长</h3>
 * <p>罐头是铁皮封的，开罐统一 3 秒（{@code eat(3)}），其余食物沿用原版 1.6 秒。
 * 方糖一包五块（{@code uses(5)}）、酒一瓶三口（{@code uses(3)}）：这类物品用耐久记剩余次数，
 * 每吃一口磨损一格，见 {@code ItemTarkovFood#wearOut}。酒不顶饿——饥饿值给 0，
 * 改为直接补 {@code extraSaturation}（饱和翻倍），喝下去压得住饱腹感但不当饭吃。</p>
 */
public final class ModTarkovFood {

	/** id → 已注册物品。 */
	private static final Map<String, Item> FOODS = new LinkedHashMap<String, Item>();

	/** 一件食物的全部设定。 */
	private static final class Spec {
		final String id;
		final int heal;
		final float saturation;
		EnumRarity rarity = EnumRarity.common;
		/** 瓶装饮料：可随时饮用、喝完还空瓶。 */
		boolean drink;
		/** 工作台配方；{@code null} 表示只能捡。 */
		Object[] recipe;
		/** 战利品权重，0 表示不进补给堆。 */
		int lootWeight;
		int lootMin = 1;
		int lootMax = 1;
		/** 近战伤害（0 = 不是武器）。 */
		float attackDamage;
		/** 吃完即死的概率（0 = 不赌命）。 */
		float deathChance;
		/** 进食时长（tick），原版食物是 32。 */
		int eatTicks = 32;
		/** 一件能吃几次，大于 1 时靠耐久记剩余次数。 */
		int uses = 1;
		/** 不占饥饿值、直接补的饱和度（酒用）。 */
		float extraSaturation;
		final List<PotionEffect> effects = new ArrayList<PotionEffect>();

		Spec(String id, int heal, float saturation) {
			this.id = id;
			this.heal = heal;
			this.saturation = saturation;
		}

		Spec rare(EnumRarity rarity) {
			this.rarity = rarity;
			return this;
		}

		Spec drink() {
			this.drink = true;
			return this;
		}

		Spec craft(Object... inputs) {
			this.recipe = inputs;
			return this;
		}

		Spec loot(int weight, int min, int max) {
			this.lootWeight = weight;
			this.lootMin = min;
			this.lootMax = max;
			return this;
		}

		Spec fx(Potion potion, int seconds, int amplifier) {
			this.effects.add(new PotionEffect(potion.id, seconds * 20, amplifier));
			return this;
		}

		/** 当武器用，{@code damage} 为最终伤害点数。 */
		Spec weapon(float damage) {
			this.attackDamage = damage;
			return this;
		}

		/** 吃完有 {@code chance} 概率直接死亡。 */
		Spec fatal(float chance) {
			this.deathChance = chance;
			return this;
		}

		/** 进食时长，按秒写，内部换 tick。 */
		Spec eat(double seconds) {
			this.eatTicks = (int) Math.round(seconds * 20.0D);
			return this;
		}

		/** 分几次吃完，每次磨损一格耐久。 */
		Spec uses(int count) {
			this.uses = count;
			return this;
		}

		/** 不占饥饿值、直接补的饱和度。 */
		Spec sat(float saturation) {
			this.extraSaturation = saturation;
			return this;
		}
	}

	/** 按显示顺序排列：罐头 → 巧克力 / 糖 / 蛋黄酱 → 酒 → 面包。 */
	private static final List<Spec> SPECS = new ArrayList<Spec>();

	static {
		// ---------------------------------------------------------------- 罐头（7）
		// 全部只能捡：塔可夫里的罐头就是「从物资点翻出来的」，自己灌装没有意义。
		// 加热过的铁罐比面包难对付，统一给 3 秒开罐时间
		SPECS.add(new Spec("can_gigabeef", 10, 0.90F)
			.rare(EnumRarity.uncommon).eat(3)
			.fx(Potion.damageBoost, 60, 0)               // 高蛋白：一分钟力量
			.fx(Potion.resistance, 30, 0)
			.loot(8, 1, 2));

		SPECS.add(new Spec("can_condensed_milk", 6, 0.60F)
			.eat(3)
			.fx(Potion.moveSpeed, 60, 0)                 // 一罐糖 + 脂肪，跑得快
			.fx(Potion.field_76443_y, 10, 0)
			.loot(10, 1, 2));

		SPECS.add(new Spec("can_tushonka", 14, 0.70F)     // 全场最高饥饿值
			.rare(EnumRarity.uncommon).eat(3)
			.fx(Potion.resistance, 90, 0)
			.fx(Potion.regeneration, 15, 0)
			.loot(6, 1, 1));

		SPECS.add(new Spec("can_green_peas", 8, 0.65F)
			.eat(3)
			.fx(Potion.moveSpeed, 45, 0)
			.fx(Potion.jump, 30, 0)
			.loot(12, 1, 3));

		SPECS.add(new Spec("can_sprats", 9, 0.65F)
			.eat(3)
			.fx(Potion.waterBreathing, 180, 0)           // 油浸鱼，一口气顶三分钟
			.fx(Potion.nightVision, 45, 0)
			.loot(10, 1, 2));

		SPECS.add(new Spec("can_duck_pate", 10, 1.00F)
			.eat(3)
			.fx(Potion.field_76443_y, 20, 0)             // 纯脂肪，压饿
			.fx(Potion.resistance, 60, 0)
			.loot(8, 1, 2));

		SPECS.add(new Spec("can_squash", 8, 0.65F)
			.eat(3)
			.fx(Potion.regeneration, 20, 0)
			.fx(Potion.resistance, 45, 0)
			.loot(10, 1, 3));

		// ---------------------------------------------------------------- 巧克力 / 糖 / 蛋黄酱（4）
		SPECS.add(new Spec("bar_alyonka", 6, 0.45F)
			.fx(Potion.moveSpeed, 60, 1)
			.fx(Potion.jump, 45, 0)
			.loot(12, 1, 3));

		SPECS.add(new Spec("bar_slickers", 7, 0.50F)
			.rare(EnumRarity.uncommon)
			.fx(Potion.moveSpeed, 90, 1)
			.fx(Potion.jump, 45, 1)
			.craft(new ItemStack(Items.dye, 1, 3), new ItemStack(Items.sugar, 2),
				new ItemStack(Items.wheat, 1))
			.loot(10, 1, 2));

		// 一包五块：每次只吃一块，耐久一格 = 一块糖
		SPECS.add(new Spec("pack_sugar", 1, 0.20F)
			.uses(5)
			.fx(Potion.moveSpeed, 45, 1)
			.fx(Potion.digSpeed, 30, 0)
			.craft(new ItemStack(Items.sugar, 1)));

		SPECS.add(new Spec("jar_devildog", 9, 0.90F)     // 一整罐蛋黄酱，高脂
			.fx(Potion.field_76443_y, 15, 0)
			.fx(Potion.resistance, 60, 0)
			.fx(Potion.moveSlowdown, 10, 0)              // 唯一的负面，压到 10 秒
			.craft(new ItemStack(Items.egg, 1), new ItemStack(Items.glass_bottle, 1),
				new ItemStack(Items.sugar, 1)));

		// ---------------------------------------------------------------- 酒（4，瓶装）
		// 一瓶三口。不顶饿：饥饿值给 0，改为直接补饱和（原来那份口粮折成两倍饱和）。
		// 醉酒口径仍是缓慢 I + 挖掘疲劳 I，15 秒以内，不给反胃
		SPECS.add(new Spec("bottle_moonshine", 0, 0F)
			.rare(EnumRarity.uncommon).drink().uses(3).sat(4.0F)
			.fx(Potion.damageBoost, 90, 1)
			.fx(Potion.resistance, 60, 0)
			.fx(Potion.moveSlowdown, 15, 0)
			.fx(Potion.digSlowdown, 15, 0)
			.fatal(0.01F)                                // 1% 直接送走
			.craft(new ItemStack(Items.sugar, 4), new ItemStack(Items.glass_bottle, 1)));

		SPECS.add(new Spec("bottle_vodka", 0, 0F)
			.rare(EnumRarity.uncommon).drink().uses(3).sat(4.0F)
			.fx(Potion.resistance, 120, 1)
			.fx(Potion.damageBoost, 60, 0)
			.fx(Potion.moveSlowdown, 15, 0)
			.craft(new ItemStack(Items.potato, 2), new ItemStack(Items.glass_bottle, 1)));

		SPECS.add(new Spec("bottle_whiskey", 0, 0F)
			.rare(EnumRarity.uncommon).drink().uses(3).sat(6.0F)
			.fx(Potion.damageBoost, 60, 1)
			.fx(Potion.resistance, 90, 0)
			.fx(Potion.digSlowdown, 15, 0)
			.craft(new ItemStack(Items.wheat, 2), new ItemStack(Items.glass_bottle, 1))
			.loot(8, 1, 1));

		SPECS.add(new Spec("bottle_beer", 0, 0F)
			.drink().uses(3).sat(6.0F)
			.fx(Potion.field_76443_y, 20, 0)
			.fx(Potion.resistance, 45, 0)
			.fx(Potion.moveSlowdown, 12, 0)
			.craft(new ItemStack(Items.wheat, 1), new ItemStack(Items.sugar, 1),
				new ItemStack(Items.glass_bottle, 1))
			.loot(15, 1, 1));                            // 不可堆叠，箱子里一次只出一瓶

		// ---------------------------------------------------------------- 面包（2）
		SPECS.add(new Spec("croutons_emelya", 6, 0.55F)
			.fx(Potion.moveSpeed, 30, 0)
			.craft(new ItemStack(Items.bread, 1))
			.loot(15, 1, 4));

		SPECS.add(new Spec("bread_baguette", 10, 0.80F)   // 创造栏图标；硬面包当棍子使
			.fx(Potion.damageBoost, 30, 0)
			.weapon(9F)
			.craft(new ItemStack(Items.wheat, 3)));
	}

	private static boolean registered;
	private static boolean looted;

	private ModTarkovFood() { }

	/** 取某件食物，未注册时返回 null。 */
	public static Item get(String id) {
		return FOODS.get(id);
	}

	/* ------------------------------------------------------------------ 注册 */

	/** 注册入口：物品 + 工作台配方。战利品由 postInit 的 {@link #addLoot()} 补。 */
	public static void register() {
		if(registered) return;
		registered = true;

		for(Spec spec : SPECS) {
			ItemTarkovFood item = new ItemTarkovFood(spec.heal, spec.saturation);
			item.setRarity(spec.rarity);
			// 描述统一走 desc.<id>，与 lang 里的键一一对应
			item.setDescription("desc." + spec.id);
			if(spec.drink) item.drink();
			if(spec.attackDamage > 0F) item.setAttackDamage(spec.attackDamage);
			if(spec.deathChance > 0F) item.setDeathChance(spec.deathChance);
			if(spec.eatTicks != 32) item.setEatTicks(spec.eatTicks);
			if(spec.uses > 1) item.setUses(spec.uses);
			if(spec.extraSaturation > 0F) item.setExtraSaturation(spec.extraSaturation);
			for(PotionEffect effect : spec.effects) item.addEffect(effect);

			item.setUnlocalizedName(spec.id);
			item.setTextureName("makentmgreatagain:" + spec.id);
			item.setCreativeTab(ModCreativeTabs.TAB_FOOD);
			GameRegistry.registerItem(item, spec.id);

			FOODS.put(spec.id, item);
		}

		addRecipes();

		System.out.println("[MakeNTMGreatAgain] Tarkov food registered (" + FOODS.size() + " items).");
	}

	/**
	 * 工作台无序配方。
	 *
	 * <p>工作台配方表是原版 {@code CraftingManager}，HBM 的 JSON 重建不碰它，
	 * 所以和 {@code ModRecipes.init()} 里的其他工作台配方一样，只在 init 阶段注册一次即可。</p>
	 */
	private static void addRecipes() {
		for(Spec spec : SPECS) {
			if(spec.recipe == null) continue;
			GameRegistry.addShapelessRecipe(new ItemStack(FOODS.get(spec.id)), spec.recipe);
		}
	}

	/**
	 * 战利品注入：追加到补给堆（{@code POOL_PILE_SUPPLIES}）。
	 *
	 * <p>必须在 postInit 之后执行：{@code ItemPool.initialize()} 由 HBM 自己的
	 * {@code MainRegistry.PostLoad} 调用，那之前 {@code pools} 是空的；而且
	 * {@code ItemPoolConfigJSON.initialize()} 会用 json 重建 {@code pools}，
	 * 早注入会被整表替换掉。</p>
	 *
	 * <p>{@code pool} 是公开数组，只能整体替换（{@code Arrays.copyOf} 扩容后回填），
	 * 不能往里追加。幂等标记防止重复调用时把权重翻倍。</p>
	 */
	public static void addLoot() {
		if(looted) return;
		looted = true;

		ItemPool pool = ItemPool.pools.get(ItemPoolsPile.POOL_PILE_SUPPLIES);
		if(pool == null) {
			System.out.println("[MakeNTMGreatAgain] Loot pool " + ItemPoolsPile.POOL_PILE_SUPPLIES
				+ " not found, tarkov food skipped.");
			return;
		}

		List<WeightedRandomChestContent> entries =
			new ArrayList<WeightedRandomChestContent>(Arrays.asList(pool.pool));
		int added = 0;

		for(Spec spec : SPECS) {
			if(spec.lootWeight <= 0) continue;
			Item item = FOODS.get(spec.id);
			if(item == null) continue;

			entries.add(HbmChestContents.weighted(item, 0, spec.lootMin, spec.lootMax, spec.lootWeight));
			added++;
		}

		pool.pool = entries.toArray(new WeightedRandomChestContent[entries.size()]);

		// 丰饶之角：极低概率（权重 1）注入补给堆、保险库及原版地牢/神庙战利品箱
		if(ModItems.horn_of_plenty != null) {
			injectRareItem(ItemPoolsPile.POOL_PILE_SUPPLIES, ModItems.horn_of_plenty, 1);
			injectRareItem(ItemPoolsSingle.POOL_VAULT_REINFORCED, ModItems.horn_of_plenty, 1);
			injectRareItem(ItemPoolsSingle.POOL_VAULT_UNBREAKABLE, ModItems.horn_of_plenty, 1);
			WeightedRandomChestContent chestLoot = new WeightedRandomChestContent(new ItemStack(ModItems.horn_of_plenty), 1, 1, 1);
			ChestGenHooks.addItem(ChestGenHooks.DUNGEON_CHEST, chestLoot);
			ChestGenHooks.addItem(ChestGenHooks.PYRAMID_DESERT_CHEST, chestLoot);
			ChestGenHooks.addItem(ChestGenHooks.PYRAMID_JUNGLE_CHEST, chestLoot);
		}

		System.out.println("[MakeNTMGreatAgain] Tarkov food injected into supplies pool (" + added + " entries).");
	}

	private static void injectRareItem(String poolName, Item item, int weight) {
		ItemPool target = ItemPool.pools.get(poolName);
		if(target == null || target.pool == null) return;
		List<WeightedRandomChestContent> list =
			new ArrayList<WeightedRandomChestContent>(Arrays.asList(target.pool));
		list.add(HbmChestContents.weighted(item, 0, 1, 1, weight));
		target.pool = list.toArray(new WeightedRandomChestContent[list.size()]);
	}
}

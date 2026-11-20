package com.makentmgreatagain.items;

import java.util.ArrayList;
import java.util.List;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.radiation.ChunkRadiationManager;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * 可以吃的核装药核心。
 *
 * <h3>为什么是 {@link ItemFood}</h3>
 * <p>1.7.10 的进食链路是 {@code EntityPlayer#onItemUseFinish} → {@code ItemStack#onFoodEaten}
 * → 仅当物品是 {@code ItemFood} 时才转调 {@code onEaten}；普通 {@code Item} 拿不到那个回调，
 * 只靠 {@code onItemRightClick} 是吃不出动作和音效的。所以这里直接继承 {@code ItemFood}，
 * 把饥饿值交给父类，自己只负责「吃完之后发生什么」。</p>
 *
 * <h3>三种吃法</h3>
 * <ul>
 *   <li><b>可食用</b>：正常加饥饿值 + 一串药水效果；</li>
 *   <li><b>不可食用</b>：饥饿值设 0，啃完照样消耗掉，但换来自伤 + 反胃 + 崩牙音效；</li>
 *   <li><b>赌一把</b>：{@link #setAlternative} 配一份备用效果表，吃完按概率二选一。</li>
 * </ul>
 *
 * <p>tooltip 只有一行「可食用」，不列效果也不预告后果——这些核心的乐趣在于吃之前不知道会怎么样。</p>
 */
public class ItemEdibleCore extends ItemFood {

	/** 必定施加的效果。 */
	private final List<PotionEffect> effects = new ArrayList<PotionEffect>();
	/** 赌一把时的另一组效果，null 表示不赌。 */
	private List<PotionEffect> alternative;
	/** 命中备用组的概率。 */
	private float gambleChance = 0.5F;

	private EnumRarity rarity = EnumRarity.uncommon;

	/** 吃完自伤（点）。 */
	private float selfDamage;
	/** 吃完点燃自己多少秒。 */
	private int fireSeconds;
	/** 吃完点燃周围生物，半径（0 = 不点）。 */
	private int burnRadius;
	/** 吃完冒的粒子类型，null 表示不冒。 */
	private String particle = "happyVillager";
	/** 是否可食用。 */
	private boolean edible = true;

	/** 一次性吃进去的剂量（RAD），直接记在玩家身上。 */
	private float radiation;
	/** 吃完污染所在区块的量（区块辐射，会随时间扩散并衰减）。 */
	private float chunkRadiation;
	/** 吃完在脚下炸一下的强度，0 = 不炸。TNT 是 4。 */
	private float explosion;
	/** 爆炸是否带火。 */
	private boolean explosionFlame;
	/** 吃完把自己向上弹起来的速度，0 = 不弹。 */
	private double launch;

	public ItemEdibleCore(int heal, float saturation) {
		super(heal, saturation, false);
		this.setAlwaysEdible();
		this.setMaxStackSize(1);
	}

	/* ------------------------------------------------------------------ 链式配置 */

	public ItemEdibleCore setRarity(EnumRarity rarity) {
		this.rarity = rarity;
		return this;
	}

	public ItemEdibleCore addEffect(PotionEffect effect) {
		this.effects.add(effect);
		return this;
	}

	public ItemEdibleCore setAlternative(PotionEffect... fallback) {
		this.alternative = new ArrayList<PotionEffect>();
		for(PotionEffect effect : fallback) this.alternative.add(effect);
		return this;
	}

	public ItemEdibleCore setGambleChance(float chance) {
		this.gambleChance = chance;
		return this;
	}

	public ItemEdibleCore setSelfDamage(float damage) {
		this.selfDamage = damage;
		return this;
	}

	public ItemEdibleCore setFireSeconds(int seconds) {
		this.fireSeconds = seconds;
		return this;
	}

	public ItemEdibleCore setBurnRadius(int radius) {
		this.burnRadius = radius;
		return this;
	}

	public ItemEdibleCore setParticle(String particle) {
		this.particle = particle;
		return this;
	}

	public ItemEdibleCore setEdible(boolean edible) {
		this.edible = edible;
		return this;
	}

	/** 一次性剂量（RAD）：1000 以上基本就是躺着等死了。 */
	public ItemEdibleCore setRadiation(float rad) {
		this.radiation = rad;
		return this;
	}

	/** 污染脚下区块：数值会按 1%/秒 衰减并向邻区块扩散，还会触发雾效与世界侵蚀。 */
	public ItemEdibleCore setChunkRadiation(float rad) {
		this.chunkRadiation = rad;
		return this;
	}

	public ItemEdibleCore setExplosion(float strength, boolean flaming) {
		this.explosion = strength;
		this.explosionFlame = flaming;
		return this;
	}

	public ItemEdibleCore setLaunch(double speed) {
		this.launch = speed;
		return this;
	}

	/* ------------------------------------------------------------------ 食用 */

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return this.rarity != null ? this.rarity : super.getRarity(stack);
	}

	/**
	 * 父类（{@code ItemFood}）在这里已经扣了物品、加了饥饿值、放了打嗝音效，
	 * 并且只在服务端跑到这；客户端不用管任何逻辑。
	 */
	@Override
	public void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if(world.isRemote) return;

		if(!this.edible) {
			// 啃不动：自伤 + 反胃 + 崩牙，饥饿值构造时已经给 0，白吃
			player.attackEntityFrom(DamageSource.generic, Math.max(1F, this.selfDamage));
			player.addPotionEffect(new PotionEffect(Potion.confusion.id, 200, 0));
			world.playSoundAtEntity(player, "random.break", 1F, 0.6F);
			return;
		}

		List<PotionEffect> applied = this.effects;
		if(this.alternative != null && !this.alternative.isEmpty()
				&& world.rand.nextFloat() < this.gambleChance) {
			applied = this.alternative;
		}

		// 复制一份再施加：PotionEffect 会被玩家实例持有，直接塞原对象容易串
		for(PotionEffect effect : applied) {
			player.addPotionEffect(new PotionEffect(effect));
		}

		// 吃进去的剂量直接记在玩家身上（HBM 的剂量上限是 2500，超过的部分会被截）
		if(this.radiation > 0F) {
			HbmLivingProps.incrementRadiation(player, this.radiation);
		}

		// 污染脚下区块：这团东西会留在世界里扩散、衰减，还会引发放射性雾
		if(this.chunkRadiation > 0F) {
			ChunkRadiationManager.proxy.incrementRad(world,
				MathHelper.floor_double(player.posX),
				MathHelper.floor_double(player.posY),
				MathHelper.floor_double(player.posZ),
				this.chunkRadiation);
		}

		if(this.selfDamage > 0F) {
			player.attackEntityFrom(DamageSource.generic, this.selfDamage);
		}

		if(this.fireSeconds > 0) {
			player.setFire(this.fireSeconds);
		}

		if(this.burnRadius > 0) {
			igniteAround(world, player);
		}

		// 爆炸放最后：它会把玩家掀飞，前面那些以玩家坐标为基准的操作得先做完
		if(this.explosion > 0F) {
			world.newExplosion(player, player.posX, player.posY + 0.5D, player.posZ,
				this.explosion, this.explosionFlame, true);
		}

		if(this.launch > 0D) {
			player.addVelocity(0D, this.launch, 0D);
			player.velocityChanged = true;
		}

		if(this.particle != null) {
			world.spawnParticle(this.particle,
				player.posX, player.posY + 1.2D, player.posZ,
				0D, 0.1D, 0D);
		}
	}

	/**
	 * 野火那一套：把周围的活物点着，并在地面上种下<b>真正的野火火焰</b>。
	 *
	 * <p>不是 {@code Blocks.fire}，而是 HBM 的 {@code ModBlocks.balefire}——那种紫白色、
	 * 亮度拉满、踩上去烧 10 秒并灌 10 级辐射的方块。它继承自原版 {@code BlockFire}，
	 * 所以照样会顺着可燃物蔓延，把周围一片木屋啃掉是预期行为。</p>
	 *
	 * <p>撒点有节制：只取圆内的格子（方形会烧出个十字），且避开玩家脚下那一格，
	 * 一次最多 10 处。要不要连自己一起烧，交给后面那个 {@code hurt} 决定。</p>
	 */
	@SuppressWarnings("unchecked")
	private void igniteAround(World world, EntityPlayer player) {
		AxisAlignedBB box = player.boundingBox.expand(this.burnRadius, 3D, this.burnRadius);
		List<EntityLivingBase> targets = world.getEntitiesWithinAABB(EntityLivingBase.class, box);

		for(EntityLivingBase target : targets) {
			if(target == player) continue;
			target.setFire(5);
		}

		int px = MathHelper.floor_double(player.posX);
		int py = MathHelper.floor_double(player.posY);
		int pz = MathHelper.floor_double(player.posZ);
		int r = this.burnRadius;

		// 候选点：自己是空气、脚下是实心方块（野火需要落在地面上，否则 tick 一下就没了）
		List<int[]> spots = new ArrayList<int[]>();

		for(int dx = -r; dx <= r; dx++) {
			for(int dz = -r; dz <= r; dz++) {
				if(dx * dx + dz * dz > r * r) continue;

				for(int dy = -1; dy <= 1; dy++) {
					int x = px + dx;
					int y = py + dy;
					int z = pz + dz;

					if(x == px && y == py && z == pz) continue;

					if(world.isAirBlock(x, y, z)
							&& World.doesBlockHaveSolidTopSurface(world, x, y - 1, z)) {
						spots.add(new int[] { x, y, z });
					}
				}
			}
		}

		int count = Math.min(10, Math.max(3, spots.size() / 2));

		for(int i = 0; i < count && !spots.isEmpty(); i++) {
			int[] spot = spots.remove(world.rand.nextInt(spots.size()));
			world.setBlock(spot[0], spot[1], spot[2], com.hbm.blocks.ModBlocks.balefire, 0, 3);
		}
	}

	/* ------------------------------------------------------------------ tooltip */

	/**
	 * tooltip 只给一句「可食用」/「不可食用」。
	 *
	 * <p>不列效果、不列剂量、不预告后果：这些东西吃下去会发生什么，让玩家自己撞一次才知道，
	 * 写出来反而像说明书。药水效果在物品栏里本来就会显示图标，重复列一遍没有意义。</p>
	 */
	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		list.add(StatCollector.translateToLocal(this.edible
			? "tooltip.makentmgreatagain.edible"
			: "tooltip.makentmgreatagain.inedible"));
	}
}

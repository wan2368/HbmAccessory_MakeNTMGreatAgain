package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.handler.ability.IWeaponAbility;
import com.hbm.items.tool.ItemSwordAbilityPower;
import com.hbm.main.MainRegistry;
import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.init.ModAchievements;
import com.makentmgreatagain.ModCreativeTabs;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * 电叉：近战带电的叉，可在水中右键电鱼。
 *
 * 继承 HBM 的 ItemSwordAbilityPower（= ItemSword + IBatteryItem），因此免费拿到：
 *  - 电量存 NBT 的 "charge"、耐久条按电量显示、充电站可充；
 *  - hitEntity → canOperate(charge >= consumption) → 施加武器能力 → damageItem(1)，
 *    基类 setDamage 被重写成"扣 damage*consumption 的电"，所以近战每次命中正好 500 HE。
 *
 * 数值：容量 100k HE；近战 9 点伤害（武器 attackDamage 8 + 基础 1）+ 2 级震撼 + 500 HE；
 * 电鱼 5000 HE、冷却 20 秒、随机 1~3 条鱼。
 *
 * 代价：人泡在水里、又朝自己身边的水（轴向距离不超过 SELF_SHOCK_RANGE 格）放电时，
 * 电流顺着水回灌到自己身上 —— 一次穿甲伤害 SELF_SHOCK_DAMAGE 点（1.7.10 两点点一滴血，
 * 即整整 3 颗心），并解锁成就「酥酥麻麻」。
 */
public class ItemElectricFork extends ItemSwordAbilityPower {

	public static final int FISH_COST = 5_000;
	public static final int FISH_COOLDOWN = 400;         // 20 秒

	/** 自伤半径：目标水方块与玩家所在格的最近轴距离不超过这么多格，才算"电的是自己泡着的那摊水" */
	public static final int SELF_SHOCK_RANGE = 1;
	/** 自伤量：6 点 = 3 颗心 */
	public static final float SELF_SHOCK_DAMAGE = 6.0F;

	/**
	 * 水中放电回灌到玩家自己身上的伤害源。
	 * setDamageBypassesArmor() = 无视护甲减免（1.7.10 没有 setMagicDamage，穿甲就靠这个）。
	 */
	private static final DamageSource FORK_SELF_ZAP =
			new DamageSource("electricFork").setDamageBypassesArmor();

	private static final String TAG_FISH_CD_END = "forkFishCdEnd";

	@SideOnly(Side.CLIENT)
	private IIcon iconBase;

	public ItemElectricFork() {

		// 伤害 8 → 玩家实际出手 9（1.7.10 武器 attackDamage 额外 +1 基础）
		// 容量 100k，充/放电速率 1k，单次近战消耗 500
		super(8.0F, 0.0D, MainRegistry.tMatElec, 100_000L, 1_000L, 500L);

		this.setUnlocalizedName("electric_fork");
		this.setCreativeTab(ModCreativeTabs.TAB_GEAR);
		this.setRarity(EnumRarity.rare);
		// IWeaponAbility.STUN 的 level 下标即"级"：2 → durationAtLevel[2]=5 秒、减速+虚弱 amplifier 4
		this.addAbility(IWeaponAbility.STUN, 2);
	}

	/* ================= 右键：电鱼 ================= */

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {

		if (canFish(stack, world, player)) {
			doFish(stack, world, player);
		}
		return stack;
	}

	/* ================= 电鱼 ================= */

	/** 冷却中、电量不足、或准星方向找不到水方块 → false */
	private boolean canFish(ItemStack stack, World world, EntityPlayer player) {

		if (getFishCooldown(stack, world) > 0) return false;
		if (getCharge(stack) < FISH_COST) return false;

		return findWaterInSight(world, player) != null;
	}

	private void doFish(ItemStack stack, World world, EntityPlayer player) {

		if (world.isRemote) return;                       // 只服务端结算

		int[] water = findWaterInSight(world, player);
		if (water == null) return;

		dischargeBattery(stack, FISH_COST);
		startFishCooldown(stack, world);

		// 先把自己电一下。放在抓鱼之前：就算被这一下电死，鱼照样到手，
		// 避免出现"扣了电、进了冷却、什么也没捞着"的双重惩罚。
		if (isZappingOwnWater(world, player, water)) {
			shockSelf(player);
		}

		int count = 1 + world.rand.nextInt(3);
		for (int i = 0; i < count; i++) {

			ItemStack fish = randomFish(world.rand);
			if (fish == null) continue;

			if (!player.inventory.addItemStackToInventory(fish.copy())) {
				player.dropPlayerItemWithRandomChoice(fish.copy(), false);
			}
		}

		world.playSoundEffect(water[0] + 0.5, water[1] + 0.5, water[2] + 0.5, "random.fizz", 1.0F, 0.6F);
	}

	/**
	 * 玩家是否正泡在自己电的那摊水里。两个条件都要满足：
	 * ① 身体浸在水中（isInWater）；② 准星命中的那个水方块离玩家所在格很近。
	 *
	 * 条件②是关键：坐在浴缸里朝窗外的海放电不该电到自己，只有"把手伸进自己泡着的这摊水"
	 * 才会。距离用三轴最大差（切比雪夫距离），所以斜着挨着的那格水也算"身边"。
	 */
	private boolean isZappingOwnWater(World world, EntityPlayer player, int[] water) {

		if (!player.isInWater()) return false;

		int dx = Math.abs(MathHelper.floor_double(player.posX) - water[0]);
		int dy = Math.abs(MathHelper.floor_double(player.posY) - water[1]);
		int dz = Math.abs(MathHelper.floor_double(player.posZ) - water[2]);

		return Math.max(dx, Math.max(dy, dz)) <= SELF_SHOCK_RANGE;
	}

	/** 回灌自伤：穿甲扣 3 颗心 + 一声"兹啦" + 解锁「酥酥麻麻」。只在服务端调用。 */
	private void shockSelf(EntityPlayer player) {

		player.attackEntityFrom(FORK_SELF_ZAP, SELF_SHOCK_DAMAGE);

		player.worldObj.playSoundEffect(player.posX, player.posY + 0.5D, player.posZ, "random.fizz", 1.0F, 1.6F);

		ModAchievements.unlock(player, ModAchievements.fork_shock);
	}

	/** 沿视线取样找水方块；找不到返回 null。玩家站在水里时以自身格为准。 */
	private int[] findWaterInSight(World world, EntityPlayer player) {

		int bx = MathHelper.floor_double(player.posX);
		int bz = MathHelper.floor_double(player.posZ);
		int by = MathHelper.floor_double(player.posY);

		if (world.getBlock(bx, by, bz).getMaterial() == Material.water) return new int[] { bx, by, bz };

		float yaw = player.rotationYaw;
		float pitch = player.rotationPitch;
		double ex = player.posX;
		double ey = player.posY + player.getEyeHeight();
		double ez = player.posZ;

		float f2 = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
		float f3 = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
		float f4 = -MathHelper.cos(-pitch * 0.017453292F);
		float f5 = MathHelper.sin(-pitch * 0.017453292F);
		double vx = f3 * f4;
		double vy = f5;
		double vz = f2 * f4;

		for (int step = 1; step <= 50; step++) {           // 0.25 格步进，共约 5 格射程

			double d = step * 0.25D;
			int sx = MathHelper.floor_double(ex + vx * d);
			int sy = MathHelper.floor_double(ey + vy * d);
			int sz = MathHelper.floor_double(ez + vz * d);

			if (world.getBlock(sx, sy, sz).getMaterial() == Material.water) return new int[] { sx, sy, sz };
			if (world.getBlock(sx, sy, sz).isOpaqueCube()) return null;   // 被实心方块挡住
		}
		return null;
	}

	/** 随机一条鱼（生鱼/生鲑鱼/小丑鱼/河豚），河豚稀有 */
	private ItemStack randomFish(java.util.Random rand) {

		int roll = rand.nextInt(100);
		int meta;
		if (roll < 40) meta = 0;          // 生鱼
		else if (roll < 75) meta = 1;    // 生鲑鱼
		else if (roll < 95) meta = 2;    // 小丑鱼
		else meta = 3;                    // 河豚

		return new ItemStack(Items.fish, 1, meta);
	}

	/* ================= 电鱼冷却 =================
	 *
	 * 冷却用"结束时刻"表示，只在放电那一 tick 写一次标签，之后靠世界时间差推算剩余量。
	 *
	 * 为什么不能每 tick 递减：Item.onUpdate 会对玩家背包（含快捷栏、副手）里的每个物品
	 * 每 tick 调一次。若在此处写 stackTagCompound，服务端的 Container.detectAndSendChanges
	 * 会因为 ItemStack.matches 含标签比较而每 tick 重发该槽位物品，客户端每 tick 换一个
	 * ItemStack 实例，手持物品的摆动/位置插值被反复打断 —— 表现就是拿在手里上下抽搐抖动。
	 * 顺带好处：倒计时与是否手持无关，收进背包、丢地上再捡起来都照常走。
	 */

	/** 剩余冷却 tick，由世界时间推算；未在冷却中返回 0 */
	public static int getFishCooldown(ItemStack stack, World world) {

		if (!stack.hasTagCompound() || world == null) return 0;

		long remain = stack.stackTagCompound.getLong(TAG_FISH_CD_END) - world.getTotalWorldTime();
		if (remain <= 0) return 0;

		// 钳制上限：换维度/读档后时间基准可能比结束时刻小很多，避免算出离谱的剩余值
		return (int) Math.min(remain, FISH_COOLDOWN);
	}

	/** 上锁：记录冷却结束的世界时刻。整个冷却期只写这一次标签。 */
	public static void startFishCooldown(ItemStack stack, World world) {

		if (stack.stackTagCompound == null) stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
		stack.stackTagCompound.setLong(TAG_FISH_CD_END, world.getTotalWorldTime() + FISH_COOLDOWN);
	}

	/* ================= 提示 ================= */

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {

		//list.add(BobMathUtil.getShortNumber(getCharge(stack)) + " / " + BobMathUtil.getShortNumber(maxPower) + " HE");
		list.add("对水方块右键电鱼，冷却 20 秒");

		int cd = getFishCooldown(stack, player.worldObj);
		if (cd > 0) list.add("  §e电鱼冷却剩余 " + (cd / 20) + " 秒");

		super.addInformation(stack, player, list, ext);
	}

	/* ================= 图标 ================= */

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister reg) {

		this.iconBase = reg.registerIcon(MakeNTMGreatAgain.MODID + ":Electric_fork");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIconIndex(ItemStack stack) { return this.iconBase; }

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(ItemStack stack, int pass) { return this.iconBase; }

	@Override
	public boolean hasEffect(ItemStack stack) {
		return false;
	}
}

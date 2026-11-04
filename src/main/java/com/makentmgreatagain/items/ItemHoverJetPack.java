package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.handler.ArmorModHandler;
import com.hbm.items.armor.JetpackBase;
import com.hbm.util.ArmorUtil;
import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.MakeNTMGreatAgain;
import com.makentmgreatagain.ModCreativeTabs;

import api.hbm.energymk2.IBatteryItem;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * 电力悬浮作业背包：喷气（矢量）与悬浮二合一。
 *
 * 自身不储电、不可被充电站充电——飞行耗的是身上动力盔甲（HBM 动力装甲那类
 * 实现 IBatteryItem 的盔甲）的电：矢量 1 HE/tick、悬浮 2 HE/tick，直接从盔甲扣。
 * 没穿带电池的盔甲（或盔甲电量不足）时背包完全不工作。
 *
 * 抽电部位优先级：胸甲 → 头盔 → 护腿 → 靴子，取第一件带电池的。
 * 背包单穿在胸甲槽时，胸甲槽被自己占用，自然从头盔等其它部位抽。
 *
 * 继承 JetpackBase：装进盔甲改造台（plate_only 槽）后由 HBM 的 modUpdate 每 tick
 * 驱动；单穿由附属的 HoverJetpackStandaloneHandler 驱动。
 * 总开关沿用 HBM 的 C 键（props）；模式切换用附属自定义按键（默认左 Ctrl）。
 */
public class ItemHoverJetPack extends JetpackBase {

	public static final int MODE_VECTORIZED = 0;       // 默认：矢量喷气
	public static final int MODE_HOVER = 1;            // 悬浮

	private static final String TAG_MODE = "hjpMode";

	/** 抽电优先级：胸→头→腿→靴（getCurrentArmor 索引） */
	private static final int[] ARMOR_ORDER = { 2, 3, 1, 0 };

	public ItemHoverJetPack() {
		super();
		this.setUnlocalizedName("hover_jetpack");
		this.setTextureName(MakeNTMGreatAgain.MODID + ":hover_jetpack");
		this.setCreativeTab(ModCreativeTabs.TAB_GEAR);
	}

	/* ================= 模式 ================= */

	public static int getMode(ItemStack stack) {
		if (stack.hasTagCompound()) return stack.stackTagCompound.getInteger(TAG_MODE);
		return MODE_VECTORIZED;
	}

	public static void setMode(ItemStack stack, int mode) {
		if (stack.stackTagCompound == null) stack.stackTagCompound = new NBTTagCompound();
		stack.stackTagCompound.setInteger(TAG_MODE, mode);
	}

	/* ================= 盔甲供电 ================= */

	/** 找玩家身上第一件带电池的盔甲（返回盔甲 ItemStack；背包自身不是 IBatteryItem 不会误中） */
	public static ItemStack findPowerArmor(EntityPlayer player) {
		for (int i : ARMOR_ORDER) {
			ItemStack a = player.getCurrentArmor(i);
			if (a != null && a.getItem() instanceof IBatteryItem) return a;
		}
		return null;
	}

	/** 从供电盔甲扣 amount 电；扣不起（没盔甲/电量不足）返回 false */
	private static boolean drainArmor(EntityPlayer player, long amount) {
		ItemStack armor = findPowerArmor(player);
		if (armor == null) return false;
		IBatteryItem bat = (IBatteryItem) armor.getItem();
		if (bat.getCharge(armor) < amount) return false;
		bat.dischargeBattery(armor, amount);
		return true;
	}

	/* ================= 主逻辑（装入胸甲 / 单穿共用） ================= */

	/** 装入胸甲时由 JetpackBase.modUpdate 每 tick 调用。 */
	public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
		runJetpack(world, player, stack);
	}

	/**
	 * 飞行主逻辑（服务端；客户端由单穿驱动同逻辑保手感）。
	 *
	 * 按键语义（全部复用 HBM 体系）：
	 *  - props.enableBackpack：HBM C 键总开关；
	 *  - props.isJetpackActive()：总开关开 && 按住空格（HBM 同步到服务端）。
	 * 矢量：按住空格推进；悬浮：总开关开即工作，空格升、Shift 降（正常潜行）、否则定高。
	 */
	public void runJetpack(World world, EntityPlayer player, ItemStack stack) {

		com.hbm.extprop.HbmPlayerProps props = com.hbm.extprop.HbmPlayerProps.getData(player);
		if (!props.enableBackpack) return;                       // C 总开关：关掉就彻底停——悬浮也不能升/降/定高

		boolean hover = getMode(stack) == MODE_HOVER;
		long cost = hover ? 2 : 1;

		boolean firing = hover || props.isJetpackActive();       // 悬浮常开；矢量要按住空格
		if (!firing) return;

		if (!drainArmor(player, cost)) return;                   // 从供电盔甲扣电，扣不动就不飞

		if (hover)
			hoverTick(world, player, props.isJetpackActive());
		else
			vectorizedTick(world, player);

		// 真正离地了才算「我飞了」：站着按空格不算
		if (!world.isRemote && !player.onGround) {
			com.makentmgreatagain.achievement.AchManager.unlock(
					player, com.makentmgreatagain.achievement.AchRegistry.HOVER);
		}

		ArmorUtil.resetFlightTime(player);
	}

	private void vectorizedTick(World world, EntityPlayer player) {

		if (player.motionY < 0.4D)
			player.motionY += 0.1D;

		Vec3 look = player.getLookVec();

		if (Vec3.createVectorHelper(player.motionX, player.motionY, player.motionZ).lengthVector() < 2) {
			player.motionX += look.xCoord * 0.1;
			player.motionY += look.yCoord * 0.1;
			player.motionZ += look.zCoord * 0.1;

			if (look.yCoord > 0)
				player.fallDistance = 0;
		}

		if (!world.isRemote)
			world.playSoundEffect(player.posX, player.posY, player.posZ, "hbm:weapon.flamethrowerShoot", 0.25F, 1.5F);
	}

	private void hoverTick(World world, EntityPlayer player, boolean jetpackKeyHeld) {

		player.fallDistance = 0;

		if (player.isSneaking()) {
			if (player.motionY > -0.3D) player.motionY -= 0.05D;
		} else if (jetpackKeyHeld) {
			if (player.motionY < 0.4D) player.motionY += 0.1D;
		} else {
			if (player.motionY < -0.1D) player.motionY += 0.2D;
			else if (player.motionY < 0D) player.motionY = 0D;
		}

		if (!player.onGround) {
			player.motionX *= 1.025D;
			player.motionZ *= 1.025D;
		}

		if (!world.isRemote)
			world.playSoundEffect(player.posX, player.posY, player.posZ, "hbm:weapon.flamethrowerShoot", 0.15F, 1.2F);
	}

	/* ================= 外观/提示 ================= */

	@Override
	public String getArmorTexture(ItemStack stack, net.minecraft.entity.Entity entity, int slot, String type) {
		return MakeNTMGreatAgain.MODID + ":textures/models/hover_jetpack.png";
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean ext) {

		list.add(EnumChatFormatting.GRAY + "模式: " + (getMode(stack) == MODE_HOVER ? "悬浮（空格升/Shift降）" : "矢量（沿视线机动）"));
		list.add(EnumChatFormatting.DARK_GRAY + "切换模式按键: " + com.makentmgreatagain.client.HoverJetpackKeys.getBoundKeyDisplay());

		if (player != null) {
			ItemStack armor = findPowerArmor(player);
			if (armor != null) {
				IBatteryItem bat = (IBatteryItem) armor.getItem();
				list.add(EnumChatFormatting.BLUE + "供能盔甲: " + armor.getDisplayName()
						+ " " + BobMathUtil.getShortNumber(bat.getCharge(armor))
						+ "/" + BobMathUtil.getShortNumber(bat.getMaxCharge(armor)) + "HE");
			} else {
				list.add(EnumChatFormatting.DARK_RED + "需要穿着带电力的动力装甲");
			}
		}
		list.add("");

		super.addInformation(stack, player, list, ext);
	}

	/** 装入胸甲后，改造台/装甲提示里追加一行状态 */
	@Override
	public void addDesc(List list, ItemStack stack, ItemStack armor) {

		ItemStack jetpack = ArmorModHandler.pryMods(armor)[ArmorModHandler.plate_only];
		if (jetpack == null) return;

		String power = "";
		if (armor.getItem() instanceof IBatteryItem) {
			IBatteryItem bat = (IBatteryItem) armor.getItem();
			power = " (" + BobMathUtil.getShortNumber(bat.getCharge(armor)) + "HE)";
		}
		list.add(EnumChatFormatting.RED + "  " + jetpack.getDisplayName() + power);
	}
}

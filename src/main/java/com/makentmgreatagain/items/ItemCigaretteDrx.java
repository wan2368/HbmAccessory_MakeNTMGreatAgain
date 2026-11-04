package com.makentmgreatagain.items;

import java.util.Random;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.handler.threading.PacketThreading;
import com.hbm.packet.toclient.AuxParticlePacketNT;
import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.CigaretteDrxEffectPacket;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * DRX 香烟。
 *
 * 使用手感复刻 HBM 原版香烟（ItemCigarette）：拉弓姿势、抽完触发 onEaten。
 * 在此基础上新增：抽完瞬间喷出一股红色烟雾、使用后切换贴图、自定义描述文本。
 *
 * 【联机与专用服务端安全说明】
 * 本类混用了服务端逻辑（属性增减、网络广播）与客户端逻辑（粒子、贴图）。
 * 两类代码的隔离靠两道防线，缺一不可：
 *   1) @SideOnly(Side.CLIENT)：专用服务端启动时 FML 会物理删除被标注的方法，
 *      从根本上避免服务端加载 net.minecraft.client.* 里的类而报 NoClassDefFoundError。
 *   2) world.isRemote 判断：单人游戏里内置服务端与客户端跑在同一个 JVM，
 *      物理端是 CLIENT，@SideOnly 不会删除方法，此时内置服务端也会调用它。
 *      只有 isRemote 判断能拦住这一次重复执行（否则粒子会生成两遍）。
 * 所以两个方法都同时用了这两道防线。
 */
public class ItemCigaretteDrx extends ItemWithDesc {

	/** 抽烟（使用中）时显示的图标 */
	@SideOnly(Side.CLIENT)
	private IIcon iconUsing;

	public ItemCigaretteDrx() {
		this.setMaxStackSize(16);
	}

	/* ---- 使用手感：与 HBM 原版香烟一致 ---- */

	@Override
	public EnumAction getItemUseAction(ItemStack stack) {
		return EnumAction.bow;      // 拉弓姿势，原版香烟就是这个
	}

	@Override
	public int getMaxItemUseDuration(ItemStack stack) {
		return 60;                  // 3 秒抽完
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		player.setItemInUse(stack, this.getMaxItemUseDuration(stack));
		return stack;
	}

	/**
	 * 说明文字由 {@link ItemWithDesc} 统一处理：读 item.cigarette_drx.desc，
	 * 按 $ 分行。语言文件里每行都带 §8/§c，基类看到行内已有颜色代码就不再套默认灰，
	 * 所以显示效果和以前一样。
	 */

	/**
	 * 抽完时触发（服务端可靠执行）。
	 *
	 * 注意：onEaten 只在服务端稳定调用。原版 EntityPlayer.onUpdate 里
	 * `if (--itemInUseCount == 0 && !worldObj.isRemote) onItemUseFinish()`，
	 * 客户端那条路径依赖库存同步时序，不可靠。所以所有游戏逻辑都放这里。
	 */
	@Override
	public ItemStack onEaten(ItemStack stack, World world, EntityPlayer player) {
		stack.stackSize--;

		if (!world.isRemote) {
			HbmLivingProps.incrementBlackLung(player, 2000);
			HbmLivingProps.incrementAsbestos(player, 2000);
			HbmLivingProps.incrementRadiation(player, 100F);
			HbmLivingProps.incrementDigamma(player, 1.0F);

			world.playSoundEffect(player.posX, player.posY, player.posZ,
				"hbm:player.cough", 1.0F, 1.0F);

			spawnBlackSmoke(world, player);

			// DRX 专属：按"本赛季（两次死亡之间）抽烟根数"触发全屏效果。
			// “1”=复活后第 1 根，“2”=第 2 根……“5”=第 5 根及以后都按第 5 档算。
			int stage = getNextStage(player);
			sendScreenEffect(player, stage);

			// 只有抽完第五次（stage>=5）才处决：全黑 + "Am I still alive？"持续放大，
			// 约 5 秒后击杀（含创造模式，服务端事件驱动，见 CigaretteKillHandler）。
			// 前四次只放警告黑幕，不击杀。
			if (stage >= 5) {
				com.makentmgreatagain.hook.CigaretteKillHandler.scheduleKill(player, 5000L);
			}
		}

		return stack;
	}

	/**
	 * 读取并递增"本赛季抽烟计数"，返回下一个要展示的档位（1~5）。
	 * 计数挂在玩家实体 NBT，死亡时由 CigaretteDeathHandler 清零。
	 */
	private int getNextStage(EntityPlayer player) {
		net.minecraft.nbt.NBTTagCompound data = player.getEntityData();
		int count = data.getInteger("mntga.cigCount");
		data.setInteger("mntga.cigCount", count + 1);
		return Math.min(count + 1, 5);
	}

	/** 把效果档位发给该玩家（仅服务端调用）。 */
	private void sendScreenEffect(EntityPlayer player, int stage) {
		if (stage <= 0) return;
		if (player instanceof EntityPlayerMP) {
			ModNetwork.NETWORK.sendTo(new CigaretteDrxEffectPacket(stage), (EntityPlayerMP) player);
		}
	}

	private void spawnBlackSmoke(World world, EntityPlayer player) {
		Random rand = world.rand;

		for (int i = 0; i < 16; i++) {
			NBTTagCompound data = new NBTTagCompound();
			data.setString("type", "vanillaExt");
			data.setString("mode", "smoke");

			// 起点在嘴边附近随机散布
			double sx = player.posX + rand.nextGaussian() * 0.15;
			double sy = player.posY + player.getEyeHeight() + rand.nextGaussian() * 0.10;
			double sz = player.posZ + rand.nextGaussian() * 0.15;

			// 三个方向独立随机 -> 真正四散，不集中朝视线方向
			data.setDouble("mX", rand.nextGaussian() * 0.06);
			data.setDouble("mY", rand.nextGaussian() * 0.03);
			data.setDouble("mZ", rand.nextGaussian() * 0.06);

			PacketThreading.createAllAroundThreadedPacket(
				new AuxParticlePacketNT(data, sx, sy, sz),
				new TargetPoint(player.dimension, player.posX, player.posY, player.posZ, 25));
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void onUsingTick(ItemStack stack, EntityPlayer player, int count) {
		if (!player.worldObj.isRemote) return;   // 拦住单人游戏里的内置服务端
		if (count != 1) return;                  // 只在抽完那一 tick 触发

		Random rand = player.worldObj.rand;
		Vec3 look = player.getLookVec();

		double baseX = player.posX + look.xCoord * 0.4;
		double baseY = player.posY + player.getEyeHeight() + 0.15;   // 抬高到眼睛上方一点
		double baseZ = player.posZ + look.zCoord * 0.4;

		for (int i = 0; i < 20; i++) {
			// 沿视线铺开 0~1 格，形成一股向前延展的烟柱
			double ix = baseX + look.xCoord * i * 0.05 + rand.nextGaussian() * 0.05;
			double iy = baseY + rand.nextGaussian() * 0.05;
			double iz = baseZ + look.zCoord * i * 0.05 + rand.nextGaussian() * 0.05;

			// 速度调小 -> 慢飘；主向前 + 高斯扰动 -> 左右散开，极少向后
			double mX = look.xCoord * 0.03 + rand.nextGaussian() * 0.012;
			double mY = rand.nextGaussian() * 0.006;
			double mZ = look.zCoord * 0.03 + rand.nextGaussian() * 0.012;

			EntitySmokeFX smoke = new EntitySmokeFX(player.worldObj, ix, iy, iz, mX, mY, mZ);
			smoke.setRBGColorF(1.0F, 0.15F, 0.15F);   // 染成红色
			Minecraft.getMinecraft().effectRenderer.addEffect(smoke);
		}
	}

	/* ---- 贴图：默认图 + 使用中切换 ---- */

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		super.registerIcons(register);   // 注册 setTextureName 指定的 cigarette_drx
		this.iconUsing = register.registerIcon("makentmgreatagain:cigarette_drx_e");
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IIcon getIcon(ItemStack stack, int renderPass, EntityPlayer player,
						 ItemStack usingItem, int useRemaining) {
		if (usingItem != null && usingItem.getItem() == this) {
			return this.iconUsing;       // 正在抽 → 换成 _e 贴图
		}
		return super.getIcon(stack, renderPass, player, usingItem, useRemaining);
	}
}

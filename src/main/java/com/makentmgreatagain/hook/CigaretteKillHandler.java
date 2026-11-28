package com.makentmgreatagain.hook;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;

public class CigaretteKillHandler {

	private static final String NBT_PENDING_ESCAPE_ABYSS = "mntgaPendingEscapeAbyss";

	/** 待击杀表：玩家 -> 到点时刻（毫秒）。弱引用 key 防泄漏。 */
	private static final Map<EntityPlayer, Long> PENDING = new WeakHashMap<EntityPlayer, Long>();

	@SubscribeEvent
	public void onServerTick(TickEvent.ServerTickEvent event) {
		if (event.phase != Phase.END) return;

		long now = System.currentTimeMillis();
		Iterator<Map.Entry<EntityPlayer, Long>> it = PENDING.entrySet().iterator();

		while (it.hasNext()) {
			Map.Entry<EntityPlayer, Long> entry = it.next();
			EntityPlayer player = entry.getKey();

			// 玩家已消失/已死：不再击杀，清理掉
			if (player == null || player.worldObj == null || player.isDead || player.getHealth() <= 0.0F) {
				it.remove();
				continue;
			}

			if (now >= entry.getValue()) {
				it.remove();
				killPlayer(player);
			}
		}
	}

	/**
	 * 对玩家施加必死伤害。
	 *
	 * <p>用 outOfWorld 虚空伤害（自带 canHarmInCreative），数值用有限大数 1_000_000F 而非
	 * Float.MAX_VALUE，避免某些伤害/统计/护甲监听做乘加时溢出成 Infinity/NaN 引发异常卡顿。</p>
	 */
	private static void killPlayer(EntityPlayer player) {
		if (player.isDead || player.getHealth() <= 0.0F) return;
		player.getEntityData().setInteger("mntga.cigCount", 0);
		player.getEntityData().removeTag(com.makentmgreatagain.items.ItemCigaretteDrx.NBT_CIG_LOCK_UNTIL);
		boolean killed = player.attackEntityFrom(DamageSource.outOfWorld, 1_000_000.0F);
		// 伤害被事件取消时（如其他模组的保护）不算"逃离深渊"，不解锁成就。
		// 延迟到玩家重生后再解锁成就：在死亡瞬间直接解锁会触发 EnderCore 的成就烟花，
		// 玩家死亡 1 秒后脱离区块追踪导致客户端收不到烟花销毁包，客户端烟花按 1.15^t
		// 指数加速后在 World.getCollidingBoundingBoxes 里扫数百万格，卡未响应 ~5 秒。
		if (killed && (player.isDead || player.getHealth() <= 0.0F)) {
			NBTTagCompound persisted = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
			persisted.setBoolean(NBT_PENDING_ESCAPE_ABYSS, true);
			player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
		}
	}

	/** 玩家重生后发放「逃离深渊」成就，避免在死亡态触发第三方成就庆祝实体。 */
	@SubscribeEvent
	public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
		EntityPlayer player = event.player;
		if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
		NBTTagCompound data = player.getEntityData();
		if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) return;
		NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
		if (!persisted.getBoolean(NBT_PENDING_ESCAPE_ABYSS)) return;
		persisted.removeTag(NBT_PENDING_ESCAPE_ABYSS);
		com.makentmgreatagain.init.ModAchievements.unlock(
				player, com.makentmgreatagain.init.ModAchievements.escape_abyss);
	}

	/** 预约 delayMs 毫秒后击杀该玩家（服务端主线程调用）。 */
	public static void scheduleKill(EntityPlayer player, long delayMs) {
		if (player == null || player.worldObj == null) return;
		PENDING.put(player, System.currentTimeMillis() + delayMs);
	}
}

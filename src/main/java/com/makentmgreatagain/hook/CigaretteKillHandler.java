package com.makentmgreatagain.hook;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

public class CigaretteKillHandler {

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
			if (player == null || player.worldObj == null || player.isDead) {
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
	 * <p>用 outOfWorld 虚空伤害：忽略护甲且为绝对伤害，Float.MAX_VALUE 保证一击必死。
	 * outOfWorld 自带 canHarmInCreative，创造玩家不需要临时关 disableDamage（原版判定会放行）。</p>
	 */
	private static void killPlayer(EntityPlayer player) {
		boolean killed = player.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
		// 伤害被事件取消时（如其他模组的保护）不算"逃离深渊"，不解锁成就
		if (killed) {
			com.makentmgreatagain.init.ModAchievements.unlock(
					player, com.makentmgreatagain.init.ModAchievements.escape_abyss);
		}
	}

	/** 预约 delayMs 毫秒后击杀该玩家（服务端主线程调用）。 */
	public static void scheduleKill(EntityPlayer player, long delayMs) {
		if (player == null || player.worldObj == null) return;
		PENDING.put(player, System.currentTimeMillis() + delayMs);
	}
}

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
	 * 对玩家施加必死伤害（含创造模式）。
	 *
	 * <p>用 outOfWorld 虚空伤害：忽略护甲且为绝对伤害，Float.MAX_VALUE 保证一击必死。
	 * 创造模式需临时关闭 disableDamage，之后立即恢复；该调用同步完成，无竞态窗口。</p>
	 */
	private static void killPlayer(EntityPlayer player) {
		boolean creativeImmune = player.capabilities.disableDamage;
		if (creativeImmune) {
			player.capabilities.disableDamage = false;
		}
		try {
			player.attackEntityFrom(DamageSource.outOfWorld, Float.MAX_VALUE);
			// 处决后解锁成就"逃离深渊"。走本模组自己的成就系统，
			// 不进 Minecraft 原生成就页（见 Achievement 包）。
			// 注意：不能等 player.isDead —— 死亡动画约20tick后才设该标记，立即判断永远为false。
			com.makentmgreatagain.achievement.AchManager.unlock(
					player, com.makentmgreatagain.achievement.AchRegistry.ESCAPE_ABYSS);
		} finally {
			if (creativeImmune) {
				player.capabilities.disableDamage = true;
			}
		}
	}

	/** 预约 delayMs 毫秒后击杀该玩家（服务端主线程调用）。 */
	public static void scheduleKill(EntityPlayer player, long delayMs) {
		if (player == null || player.worldObj == null) return;
		PENDING.put(player, System.currentTimeMillis() + delayMs);
	}
}

package com.makentmgreatagain.render;

import java.util.Random;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;

/**
 * 核爆的屏幕抖动。
 *
 * <p>服务端通过 {@link com.makentmgreatagain.packet.NukeShakePacket} 发来一次触发，
 * 客户端每 tick 给玩家视角叠一个小偏移，幅度随时间衰减，时长由当量决定（2~5 秒）。
 * 1.7.10 没有相机事件，只能改 rotation；每 tick 先把上一 tick 的偏移减掉，
 * 否则随机偏移会累积成镜头漂移。</p>
 */
public class ClientScreenShake {

	private static final Random RAND = new Random();

	private static long endTime;
	private static long duration;
	private static float intensity;

	private static float appliedPitch;
	private static float appliedYaw;

	public static void start(int durationMs, float degrees) {
		long now = Minecraft.getSystemTime();
		duration = Math.max(1, durationMs);
		endTime = now + duration;
		intensity = degrees;
	}

	@SubscribeEvent
	public void onClientTick(TickEvent.ClientTickEvent event) {

		if(event.phase != TickEvent.Phase.END) return;

		EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
		if(player == null) {
			appliedPitch = 0F;
			appliedYaw = 0F;
			return;
		}

		// 撤销上一 tick 的抖动
		player.rotationPitch -= appliedPitch;
		player.prevRotationPitch -= appliedPitch;
		player.rotationYaw -= appliedYaw;
		player.prevRotationYaw -= appliedYaw;
		appliedPitch = 0F;
		appliedYaw = 0F;

		long now = Minecraft.getSystemTime();
		if(now >= endTime) return;

		float left = (endTime - now) / (float) duration;
		float amp = intensity * (0.35F + 0.65F * left);

		appliedPitch = (RAND.nextFloat() - 0.5F) * 2F * amp;
		appliedYaw = (RAND.nextFloat() - 0.5F) * 2F * amp;

		// prev 一起改，渲染插值时才是整个 tick 都在晃，而不是从旧视角慢慢滑过去
		player.rotationPitch += appliedPitch;
		player.prevRotationPitch += appliedPitch;
		player.rotationYaw += appliedYaw;
		player.prevRotationYaw += appliedYaw;
	}
}

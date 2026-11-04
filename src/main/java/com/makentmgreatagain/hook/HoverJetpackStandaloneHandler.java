package com.makentmgreatagain.hook;

import com.makentmgreatagain.items.ItemHoverJetPack;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.PlayerTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * 悬浮作业背包"单穿"驱动。
 *
 * HBM 只对"装在胸甲里的模组"跑 modUpdate；直接单穿在胸甲槽（胸甲本身没装模组）
 * 时 HBM 只渲染不跑逻辑，这段补齐。
 *
 * 注意：PlayerTickEvent 属于 FML 事件总线，注册必须用
 * FMLCommonHandler.instance().bus()（见 CommonProxy），用 Forge 总线收不到事件。
 *
 * 只处理"单穿"形态；装入胸甲模组的情况交给 HBM 的 modUpdate，避免双驱动。
 * 双端都跑（与 HBM 自带背包一致）：客户端保证手感即时，服务端保证权威判定。
 */
public class HoverJetpackStandaloneHandler {

	@SubscribeEvent
	public void onPlayerTick(PlayerTickEvent event) {

		if (event.phase != Phase.START) return;

		EntityPlayer player = event.player;
		if (player == null || player.worldObj == null) return;

		ItemStack chest = player.getCurrentArmor(2);          // 2 = 胸甲槽
		if (!(chest != null && chest.getItem() instanceof ItemHoverJetPack)) return;

		((ItemHoverJetPack) chest.getItem()).runJetpack(player.worldObj, player, chest);
	}
}

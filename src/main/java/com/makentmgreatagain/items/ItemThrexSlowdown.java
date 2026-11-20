package com.makentmgreatagain.items;

import com.makentmgreatagain.hazard.CompositeTankHazardGuard;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.world.World;

/**
 * 携带 Threx 材料时持续施加减速。
 *
 * <p>锭与粉直接继承本类；块因为必须是 {@code ItemBlock}，不能继承，改写在自己的
 * {@code ItemBlockThrex} 里，但逻辑复用 {@link #tickSlowdown}，避免两处判定分叉。</p>
 *
 * <h2>为什么写在 onUpdate 而不是 PlayerTickEvent</h2>
 * 1.7.10 的 {@code EntityPlayer#onUpdate} 每 tick 遍历整个主物品栏调
 * {@code ItemStack#updateAnimation} → {@code Item#onUpdate}，所以东西只要在背包
 * 36 格里的任意一格，本方法就会被调到；不需要额外维护玩家状态，也不需要自己扫背包。
 * 丢掉之后效果自然在 2 秒内过期。
 *
 * <p>{@code isHeld} 参数在这里<b>不用</b>：它只表示「是不是当前选中的快捷栏格」，
 * 而需求是「背包任意一格都算」。</p>
 *
 * <p>持续时间给 40 tick 而不是 20：客户端与服务端各算一遍，40 tick 能容忍
 * 一次掉 tick，不会在两 tick 之间闪烁。</p>
 *
 * <p>药水等级：amplifier n 对应界面上的「缓慢 (n+1)」。</p>
 */
public class ItemThrexSlowdown extends ItemWithDesc {

	/** 效果时长（tick）。每次 onUpdate 重刷，实际是常驻。 */
	public static final int DURATION = 40;

	/** 药水等级：0 = 缓慢 I。 */
	private final int amplifier;

	public ItemThrexSlowdown(int amplifier) {
		this.amplifier = amplifier;
	}

	@Override
	public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean isHeld) {
		tickSlowdown(world, entity, this.amplifier);
	}

	/**
	 * 拦截判定与施药。供 ItemBlock 子类复用。
	 *
	 * @param amplifier 药水等级，1 即界面「缓慢 II」
	 */
	public static void tickSlowdown(World world, Entity entity, int amplifier) {
		// 药水效果必须由服务端施加（客户端加了会被服务端同步覆盖）
		if (world == null || world.isRemote) return;
		if (!(entity instanceof EntityPlayer)) return;

		EntityPlayer player = (EntityPlayer) entity;

		// 复合材料储罐（护腿的护甲改装件）可以把这条减速整个拦下：它本身就是
		// 「把危险部件的外泄彻底封住」的设定，Threx 的重量压迫也一并被它吸收
		if (CompositeTankHazardGuard.isProtected(player)) return;

		// 已有更强的缓慢就不重刷，避免每 tick 覆盖玩家身上别处来的长时效果
		PotionEffect current = player.getActivePotionEffect(Potion.moveSlowdown);
		if (current != null && current.getAmplifier() > amplifier) return;

		player.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, DURATION, amplifier));
	}
}

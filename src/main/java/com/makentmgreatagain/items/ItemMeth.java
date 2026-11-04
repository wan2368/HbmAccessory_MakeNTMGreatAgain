package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.extprop.HbmLivingProps;
import com.hbm.potion.HbmPotion;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

/**
 * (2R)-N-甲基-1-苯基丙-2-胺：产线终产品，可直接食用。
 *
 * 食用后立刻清空自身辐射（含迪伽马与沾染），并给出抗辐射、急迫、速度与虚弱效果。
 */
public class ItemMeth extends ItemFood {

	public ItemMeth() {
		super(6, 0.8F, false);
		this.setAlwaysEdible();
		this.setMaxStackSize(16);
	}

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return EnumRarity.rare;
	}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {

		if(world.isRemote) return;

		// 清除自身全部辐射：普通辐射、缓冲区、迪伽马与沾染
		HbmLivingProps.setRadiation(player, 0F);
		HbmLivingProps.setRadBuf(player, 0F);
		HbmLivingProps.setDigamma(player, 0F);
		HbmLivingProps.getCont(player).clear();

		player.addPotionEffect(new PotionEffect(HbmPotion.radx.id, 3 * 60 * 20, 0));   // 抗辐射 3 分钟
		player.addPotionEffect(new PotionEffect(Potion.digSpeed.id, 3 * 60 * 20, 1));  // 急迫 II
		player.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 3 * 60 * 20, 0)); // 速度 I
		player.addPotionEffect(new PotionEffect(Potion.weakness.id, 5 * 60 * 20, 1));  // 虚弱 II 5 分钟
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("item.meth_pill.desc.radiation"));
		list.add(EnumChatFormatting.BLUE + StatCollector.translateToLocal("item.meth_pill.desc.effects"));
		list.add(EnumChatFormatting.RED + StatCollector.translateToLocal("item.meth_pill.desc.weakness"));
	}
}

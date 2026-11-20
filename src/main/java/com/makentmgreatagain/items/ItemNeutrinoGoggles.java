package com.makentmgreatagain.items;

import com.hbm.handler.ArmorModHandler;
import com.hbm.items.armor.ItemArmorMod;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;

public class ItemNeutrinoGoggles extends ItemArmorMod {

	public ItemNeutrinoGoggles() {
		super(ArmorModHandler.helmet_only, true, false, false, false);
	}

	@Override
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
		list.add(EnumChatFormatting.AQUA + I18nUtil.resolveKey("item.neutrino_goggles.desc"));
		list.add(I18nUtil.resolveKey("item.neutrino_goggles.toggle"));
		list.add(I18nUtil.resolveKey("item.neutrino_goggles.locator"));
		list.add("");
		super.addInformation(stack, player, list, advanced);
	}
}

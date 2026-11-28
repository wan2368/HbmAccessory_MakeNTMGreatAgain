package com.makentmgreatagain.items;

import java.util.List;

import com.hbm.handler.ArmorModHandler;
import com.hbm.items.armor.ItemArmorMod;
import com.hbm.util.i18n.I18nUtil;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;

/**
 * 复合材料储罐：护腿槽的护甲改装件。
 *
 * 它本身不产生任何效果——真正干活的是 {@link com.makentmgreatagain.hazard.CompositeTankHazardGuard}，
 * 那个类在 postInit 里把 HBM 的「易燃易爆 / 遇水反应 / 自燃 / 致盲」四种 hazard 类型换成了会先检查
 * 「护腿里有没有这件东西」的包装类。放在这里不写逻辑，是因为 ItemArmorMod.modUpdate 只对
 * 盔甲穿戴者本人生效，而 hazard 是全局按物品触发的，接不上。
 */
public class ItemCompositeTank extends ItemArmorMod {

    public ItemCompositeTank() {
        // type = legs_only（护腿专用槽），只允许装在护腿上
        super(ArmorModHandler.legs_only, false, false, true, false);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean bool) {
        super.addInformation(stack, player, list, bool);

        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.composite_tank.1"));
        list.add(EnumChatFormatting.GRAY + I18nUtil.resolveKey("desc.composite_tank.2"));
    }
}

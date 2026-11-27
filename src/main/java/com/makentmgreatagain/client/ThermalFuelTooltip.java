package com.makentmgreatagain.client;

import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * 大型火力发电站燃料简介：背包与 NEI 的物品提示统一追加「热值: XXX」。
 * 映射表与机器燃料槽校验共用 {@link TileEntityLargeThermalPowerStation#getHeatValue}，
 * 改热值只需改一处。HBM 物品自身的 addInformation 先执行，本事件在其后叠加，互不覆盖。
 */
@SideOnly(Side.CLIENT)
public final class ThermalFuelTooltip {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        int heat = TileEntityLargeThermalPowerStation.getHeatValue(event.itemStack);
        if(heat <= 0) return;
        event.toolTip.add(EnumChatFormatting.GOLD
                + StatCollector.translateToLocalFormatted("mnga.fuel.heat", String.format("%,d", heat)));
    }
}
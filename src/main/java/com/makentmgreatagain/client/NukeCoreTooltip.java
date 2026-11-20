package com.makentmgreatagain.client;

import java.util.List;

import org.lwjgl.input.Keyboard;

import com.hbm.util.BobMathUtil;
import com.makentmgreatagain.init.ModNukeCores;
import com.makentmgreatagain.recipes.NukeGeneratorData;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/**
 * 核装药核心的 hover 信息，仿 HBM 的危险防护提示：
 * 默认只有一行「按住 LSHIFT」，按住后才列出中子通量 / 耗电量 / 消耗液态钠。
 *
 * <p>核心有两种物品类（{@code ItemEdibleCore} 继承 {@code ItemFood}，
 * 其余是 {@code ItemWithDesc}），逐个覆写 {@code addInformation} 会重复两遍，
 * 所以统一挂在 {@link ItemTooltipEvent} 上——背包与 NEI 的提示都走这条链路。</p>
 */
@SideOnly(Side.CLIENT)
public final class NukeCoreTooltip {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {

        ItemStack stack = event.itemStack;
        if(stack == null || !ModNukeCores.isCore(stack)) return;

        NukeGeneratorData.CoreStat stat = NukeGeneratorData.getCore(stack);
        if(stat == null) return;

        List<String> list = event.toolTip;

        // 禁爆核心：只标明不能裂变核爆，不显示 Shift 提示与下部通量/耗电/钠数据
        if(NukeGeneratorData.isNonExplosive(stack)) {
            list.add(EnumChatFormatting.RED + StatCollector.translateToLocal("info.core.non_explosive"));
            return;
        }

        if(!Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add(EnumChatFormatting.DARK_GRAY + "" + EnumChatFormatting.ITALIC
                    + StatCollector.translateToLocal("info.core.shift"));
            return;
        }

        list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocal("info.core.header"));
        list.add(EnumChatFormatting.YELLOW + "  " + StatCollector.translateToLocalFormatted(
                "info.core.flux", String.format("%,d", stat.flux)));
        list.add(EnumChatFormatting.YELLOW + "  " + StatCollector.translateToLocalFormatted(
                "info.core.power", BobMathUtil.getShortNumber(NukeGeneratorData.powerCost(stat))));
        list.add(EnumChatFormatting.YELLOW + "  " + StatCollector.translateToLocalFormatted(
                "info.core.sodium", String.format("%,d", stat.sodium)));
    }
}

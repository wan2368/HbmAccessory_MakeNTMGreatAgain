package com.makentmgreatagain.init;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.Achievement;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

/**
 * 「隐藏成就」：没解锁时，成就界面里的<b>标题和描述都是 ???</b>，解锁后才显示真名。
 *
 * <h2>为什么不用改原版 / 不用字节码补丁</h2>
 * 1.7.10 的成就界面取文案只有两个出口，而且<b>都是可覆写的实例方法</b>：
 * <ul>
 *   <li>标题：{@code StatBase.func_150951_e()} 返回一个 {@code IChatComponent}，
 *       界面直接 {@code getUnformattedText()} 拿去画；</li>
 *   <li>描述：{@code Achievement.getDescription()}（{@code @SideOnly(CLIENT)}）。</li>
 * </ul>
 * 所以子类覆写这两个方法就够了，比给 {@code GuiAchievements} 下 ASM 补丁干净得多，
 * 也不会因为换了 MC 版本 / 映射而静默失效。
 *
 * <h2>怎么判断「解锁了没」</h2>
 * 客户端本地就有统计文件：{@code Minecraft.getMinecraft().thePlayer.statFileWriter}。
 * 服务端解锁后会同步给客户端，所以这里读到的状态与界面显示一致。
 *
 * <h2>取不到状态时一律按「已解锁」处理</h2>
 * 单人游戏标题界面、玩家还没进世界、专用服务端误调等情况下拿不到统计文件。
 * 那时显示真名而不是 ??? —— 宁可提前露出文案，也不要让一条成就永远显示问号。
 */
public class HiddenAchievement extends Achievement {

    /** 没解锁时显示的占位文案。 */
    private static final String MASK = "???";

    public HiddenAchievement(String name, int col, int row, ItemStack icon, Achievement parent) {
        super("achievement." + name, name, col, row, icon, parent);
    }

    /**
     * 标题：没解锁就是 ???。
     *
     * <p>颜色沿用原版口径（特殊成就深紫、普通绿），否则隐藏成就在界面里会一眼被颜色认出来。</p>
     */
    @Override
    public IChatComponent func_150951_e() {
        if (isUnlocked()) return super.func_150951_e();
        IChatComponent masked = new ChatComponentText(MASK);
        masked.getChatStyle().setColor(getSpecial() ? EnumChatFormatting.DARK_PURPLE : EnumChatFormatting.GREEN);
        return masked;
    }

    /** 描述：没解锁就是 ???。原版界面对特殊成就会把描述染成深紫，这里跟着走。 */
    @Override
    @SideOnly(Side.CLIENT)
    public String getDescription() {
        if (isUnlocked()) return super.getDescription();
        return MASK;
    }

    /**
     * 本地统计里这条是否已解锁。
     *
     * <p>任何一步取不到（没进世界 / 没有统计文件 / 专用服务端）都返回 true，
     * 让文案正常显示——见类注释「取不到状态时一律按已解锁处理」。</p>
     */
    private boolean isUnlocked() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc == null) return true;
            EntityClientPlayerMP player = mc.thePlayer;
            if (player == null) return true;
            // 统计文件字段是 private，走公开取值方法（getStatFileWriter）
            net.minecraft.stats.StatFileWriter stats = player.getStatFileWriter();
            if (stats == null) return true;
            return stats.hasAchievementUnlocked(this);
        } catch (Throwable ignored) {
            // 专用服务端上 Minecraft 类不存在（@SideOnly 已被剥离），会抛 NoClassDefFoundError。
            // 服务端根本不需要文案，按已解锁返回即可。
            return true;
        }
    }
}
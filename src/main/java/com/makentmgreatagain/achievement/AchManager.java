package com.makentmgreatagain.achievement;

import com.makentmgreatagain.init.ModNetwork;
import com.makentmgreatagain.packet.AchievementPacket;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 成就系统的统一入口。
 *
 * 触发点一律调用 unlock()，不要自己去改 PlayerAchievements ——
 * 这里会同时处理「是否新解锁」和「要不要通知客户端弹提示」。
 */
public class AchManager {

    /**
     * 解锁一条成就。只在服务端有效，客户端调用会被忽略（权威在服务端）。
     *
     * @return 是否本次新解锁
     */
    public static boolean unlock(EntityPlayer player, String id) {

        if (player == null || player.worldObj == null || player.worldObj.isRemote) return false;

        PlayerAchievements props = PlayerAchievements.get(player);
        if (props == null) return false;

        if (!props.unlock(id)) return false;

        if (player instanceof EntityPlayerMP) {
            ModNetwork.NETWORK.sendTo(new AchievementPacket(id), (EntityPlayerMP) player);
        }

        return true;
    }

    /** 登录时把整份进度推给客户端 */
    public static void syncFull(EntityPlayerMP player) {

        if (player == null) return;

        PlayerAchievements props = PlayerAchievements.get(player);
        if (props == null) return;

        ModNetwork.NETWORK.sendTo(new AchievementPacket(props.snapshot()), player);
    }

    /** 只读查询，双端都可用 */
    public static boolean isUnlocked(EntityPlayer player, String id) {

        PlayerAchievements props = PlayerAchievements.get(player);
        return props != null && props.has(id);
    }

    public static int unlockedCount(EntityPlayer player) {

        PlayerAchievements props = PlayerAchievements.get(player);
        return props == null ? 0 : props.unlockedCount();
    }
}

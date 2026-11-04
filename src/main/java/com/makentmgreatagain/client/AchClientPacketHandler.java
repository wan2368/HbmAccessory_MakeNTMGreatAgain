package com.makentmgreatagain.client;

import com.makentmgreatagain.achievement.PlayerAchievements;
import com.makentmgreatagain.packet.AchievementPacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

/**
 * 成就包在客户端的落地。
 *
 * 单独成类并整体标 {@link SideOnly}，专用服务端加载 AchievementPacket 时
 * 不会碰到 Minecraft 客户端类。
 */
@SideOnly(Side.CLIENT)
public class AchClientPacketHandler {

    public static void handle(AchievementPacket msg) {

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null) return;

        PlayerAchievements props = PlayerAchievements.get(mc.thePlayer);
        if (props == null) return;

        if (msg.mode == AchievementPacket.MODE_FULL) {
            props.applySnapshot(msg.snapshot);
            return;
        }

        if (msg.id == null || msg.id.isEmpty()) return;

        // 只有本地还没记过才弹提示，避免重连后重复刷屏
        if (props.unlock(msg.id)) {
            AchToast.push(msg.id);
        }
    }
}

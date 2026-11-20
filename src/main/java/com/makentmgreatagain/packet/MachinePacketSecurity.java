package com.makentmgreatagain.packet;

import com.hbm.tileentity.TileEntityMachineBase;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

/** Shared server-side authorization for machine control packets. */
public final class MachinePacketSecurity {

    /**
     * 兜底半径：与 HBM 机器基类 getUseRange() 的 12 格对齐。
     * 旧值 8 格比"界面能开的距离"还小：大型机核心格内缩于机身，玩家贴着机身远端
     * 开界面时距核心格 9~11 格，界面能开但控制包被静默丢弃，客户端预显约 1 秒后
     * 被服务端同步弹回（燃气轮机阀门"怎么调都回 0"的根因）。
     */
    private static final double MAX_DISTANCE_SQ = 144.0D;

    private MachinePacketSecurity() {
    }

    public static boolean canOperate(EntityPlayer player, TileEntity tile) {
        if(player == null || tile == null || player.worldObj == null) return false;
        if(tile.getWorldObj() != player.worldObj || tile.getWorldObj().isRemote) return false;
        if(!player.worldObj.blockExists(tile.xCoord, tile.yCoord, tile.zCoord)) return false;

        // 控制包的有效范围必须与"界面能开的范围"一致：HBM 基类按 getUseRange() 判，
        // 这里复用同一判定，避免两套距离标准互相打架
        double distanceSq = player.getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D,
                tile.zCoord + 0.5D);
        if(tile instanceof TileEntityMachineBase) {
            if(!((TileEntityMachineBase) tile).isUseableByPlayer(player)) return false;
        } else if(distanceSq > MAX_DISTANCE_SQ) {
            return false;
        }

        return player.canPlayerEdit(tile.xCoord, tile.yCoord, tile.zCoord, 0, null);
    }
}

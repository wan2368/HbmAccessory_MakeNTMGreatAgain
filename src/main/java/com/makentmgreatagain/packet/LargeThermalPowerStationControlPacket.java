package com.makentmgreatagain.packet;

import com.makentmgreatagain.tileentity.TileEntityLargeThermalPowerStation;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class LargeThermalPowerStationControlPacket implements IMessage {
    public int x, y, z;
    public boolean enabled;
    public LargeThermalPowerStationControlPacket() { }
    public LargeThermalPowerStationControlPacket(int x, int y, int z, boolean enabled) {
        this.x=x; this.y=y; this.z=z; this.enabled=enabled;
    }
    @Override public void fromBytes(ByteBuf buf) { x=buf.readInt(); y=buf.readInt(); z=buf.readInt(); enabled=buf.readBoolean(); }
    @Override public void toBytes(ByteBuf buf) { buf.writeInt(x); buf.writeInt(y); buf.writeInt(z); buf.writeBoolean(enabled); }

    public static class Handler implements IMessageHandler<LargeThermalPowerStationControlPacket, IMessage> {
        @Override public IMessage onMessage(final LargeThermalPowerStationControlPacket msg, final MessageContext ctx) {
            if(ctx.side != Side.SERVER) return null;
            final EntityPlayer player = ctx.getServerHandler().playerEntity;
            if(player == null) return null;
            com.makentmgreatagain.packet.ExtractorSwitchPacket.schedule(new Runnable() {
                @Override public void run() {
                    World world = player.worldObj;
                    if(world == null) return;
                    TileEntity tile = world.getTileEntity(msg.x, msg.y, msg.z);
                    if(tile instanceof TileEntityLargeThermalPowerStation
                            && MachinePacketSecurity.canOperate(player, tile))
                        ((TileEntityLargeThermalPowerStation) tile).setEnabled(msg.enabled);
                }
            });
            return null;
        }
    }
}
